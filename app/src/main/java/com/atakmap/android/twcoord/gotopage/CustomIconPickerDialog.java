package com.atakmap.android.twcoord.gotopage;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.log.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Two-step picker dialog (iconset list → icon grid) per {@code contracts/custom-icon-picker.md}.
 *
 * <p>Constructed lazily by {@link TwCoordGotoView} on first open; re-used across opens within a
 * session; force-dismissed in {@link #dismissIfShowing()} on drop-down close so the dialog never
 * outlives its host page.
 *
 * <p>Constitution VI: every host-callable callback body is wrapped in {@code try/catch (Throwable)}
 * — item-click on both list and grid, back button, dialog cancel, adapter {@code getView}, and
 * worker {@code Runnable.run}.
 */
public final class CustomIconPickerDialog {

  private static final String TAG = "TwCoordCustomIconPicker";

  /** Single listener interface the view layer implements. */
  public interface Listener {
    void onIconPicked(IconSelection selection);

    void onCancelled();
  }

  private final Context themedContext;
  private final IconResolver iconResolver;
  private final ExecutorService worker;
  private final Handler mainThreadHandler;
  private final Listener listener;

  private AlertDialog dialog;
  private View dialogRoot;
  private TextView titleView;
  private View backButton;
  private ListView iconsetListView;
  private GridView iconGridView;
  private TextView emptyView;

  /** Dialog state. Null while dialog is closed. */
  private Step currentStep = null;

  /** When at step 2, the iconset whose icons are showing. */
  private IconsetSummary currentIconset;

  public CustomIconPickerDialog(
      Context themedContext,
      IconResolver iconResolver,
      ExecutorService worker,
      Handler mainThreadHandler,
      Listener listener) {
    this.themedContext = Objects.requireNonNull(themedContext, "themedContext");
    this.iconResolver = Objects.requireNonNull(iconResolver, "iconResolver");
    this.worker = Objects.requireNonNull(worker, "worker");
    this.mainThreadHandler = Objects.requireNonNull(mainThreadHandler, "mainThreadHandler");
    this.listener = Objects.requireNonNull(listener, "listener");
  }

  // ---------------- public API ----------------

  /**
   * Open the dialog. {@code current == null} ⇒ step 1; {@code current != null && iconset resolves}
   * ⇒ step 2 of that iconset; {@code current != null && iconset gone} ⇒ step 1 silently.
   */
  public void show(IconSelection current) {
    try {
      ensureDialog();
      if (current == null) {
        transitionToIconsetList();
      } else {
        IconsetSummary match = findIconsetByUid(current.iconsetUid());
        if (match == null) {
          transitionToIconsetList();
        } else {
          transitionToIconList(match);
        }
      }
      if (!dialog.isShowing()) dialog.show();
    } catch (Throwable t) {
      Log.w(TAG, "show(" + current + ") failed", t);
    }
  }

  /** Force-dismiss the dialog if showing — does NOT fire {@link Listener#onCancelled()}. */
  public void dismissIfShowing() {
    try {
      if (dialog != null && dialog.isShowing()) {
        // Detach the cancel listener temporarily so dialog.dismiss() doesn't fire onCancelled.
        dialog.setOnCancelListener(null);
        dialog.dismiss();
      }
      currentStep = null;
      currentIconset = null;
    } catch (Throwable t) {
      Log.w(TAG, "dismissIfShowing failed", t);
    }
  }

  /** Notification from the view layer that the iconset DB has changed. */
  public void onIconsetsChanged() {
    try {
      iconResolver.invalidateCaches();
      if (dialog == null || !dialog.isShowing()) return;
      if (currentStep == Step.ICONSET_LIST) {
        refreshIconsetList();
      } else if (currentStep == Step.ICON_LIST && currentIconset != null) {
        // If the iconset we're showing has just disappeared, fall back to step 1.
        IconsetSummary still = findIconsetByUid(currentIconset.uid());
        if (still == null) {
          transitionToIconsetList();
        } else {
          transitionToIconList(still); // re-fetch icons; iconset still valid
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "onIconsetsChanged failed", t);
    }
  }

  // ---------------- internals ----------------

  private void ensureDialog() {
    if (dialog != null) return;
    LayoutInflater inflater = LayoutInflater.from(themedContext);
    dialogRoot = inflater.inflate(R.layout.custom_icon_picker_dialog, null, false);
    titleView = dialogRoot.findViewById(R.id.custom_icon_picker_title);
    backButton = dialogRoot.findViewById(R.id.custom_icon_picker_back);
    iconsetListView = dialogRoot.findViewById(R.id.custom_icon_picker_iconset_list);
    iconGridView = dialogRoot.findViewById(R.id.custom_icon_picker_icon_grid);
    emptyView = dialogRoot.findViewById(R.id.custom_icon_picker_empty);

    backButton.setOnClickListener(
        v -> {
          try {
            transitionToIconsetList();
          } catch (Throwable t) {
            Log.w(TAG, "back-button click handler failed", t);
          }
        });

    iconsetListView.setOnItemClickListener(
        (parent, view, position, id) -> {
          try {
            @SuppressWarnings("unchecked")
            IconsetSummary picked = (IconsetSummary) parent.getItemAtPosition(position);
            if (picked != null) transitionToIconList(picked);
          } catch (Throwable t) {
            Log.w(TAG, "iconset list item click failed", t);
          }
        });

    iconGridView.setOnItemClickListener(
        (parent, view, position, id) -> {
          try {
            @SuppressWarnings("unchecked")
            IconRow row = (IconRow) parent.getItemAtPosition(position);
            if (row != null && currentIconset != null) {
              IconSelection sel = IconSelection.from(row, currentIconset);
              dismissForCommit();
              listener.onIconPicked(sel);
            }
          } catch (Throwable t) {
            Log.w(TAG, "icon grid item click failed", t);
          }
        });

    dialog =
        new AlertDialog.Builder(themedContext)
            .setView(dialogRoot)
            .setCancelable(true)
            .setOnCancelListener(
                new DialogInterface.OnCancelListener() {
                  @Override
                  public void onCancel(DialogInterface d) {
                    try {
                      currentStep = null;
                      currentIconset = null;
                      listener.onCancelled();
                    } catch (Throwable t) {
                      Log.w(TAG, "onCancel handler failed", t);
                    }
                  }
                })
            .create();
  }

  /** Dismiss the dialog after a successful pick — no cancel callback fires. */
  private void dismissForCommit() {
    try {
      if (dialog != null && dialog.isShowing()) {
        dialog.setOnCancelListener(null);
        dialog.dismiss();
      }
      currentStep = null;
      currentIconset = null;
    } catch (Throwable t) {
      Log.w(TAG, "dismissForCommit failed", t);
    }
  }

  private void transitionToIconsetList() {
    currentStep = Step.ICONSET_LIST;
    currentIconset = null;
    titleView.setText(themedContext.getString(R.string.goto_custom_icon_dialog_title_iconsets));
    backButton.setVisibility(View.GONE);
    iconsetListView.setVisibility(View.VISIBLE);
    iconGridView.setVisibility(View.GONE);
    emptyView.setVisibility(View.GONE);
    refreshIconsetList();
  }

  private void transitionToIconList(IconsetSummary iconset) {
    currentStep = Step.ICON_LIST;
    currentIconset = iconset;
    titleView.setText(
        String.format(
            themedContext.getString(R.string.goto_custom_icon_dialog_title_icons), iconset.name()));
    backButton.setVisibility(View.VISIBLE);
    iconsetListView.setVisibility(View.GONE);
    iconGridView.setVisibility(View.VISIBLE);
    emptyView.setVisibility(View.GONE);
    refreshIconList(iconset.uid());
  }

  private void refreshIconsetList() {
    // Worker-thread fetch, main-thread bind.
    worker.submit(
        () -> {
          try {
            List<IconsetSummary> sets = iconResolver.listIconsets();
            mainThreadHandler.post(
                () -> {
                  try {
                    if (sets.isEmpty()) {
                      iconsetListView.setVisibility(View.GONE);
                      emptyView.setText(
                          themedContext.getString(R.string.goto_custom_icon_empty_iconsets));
                      emptyView.setVisibility(View.VISIBLE);
                    } else {
                      iconsetListView.setAdapter(new IconsetListAdapter(sets));
                      iconsetListView.setVisibility(View.VISIBLE);
                      emptyView.setVisibility(View.GONE);
                    }
                  } catch (Throwable t) {
                    Log.w(TAG, "refreshIconsetList bind failed", t);
                  }
                });
          } catch (Throwable t) {
            Log.w(TAG, "refreshIconsetList worker failed", t);
          }
        });
  }

  private void refreshIconList(String iconsetUid) {
    worker.submit(
        () -> {
          try {
            List<IconRow> rows = iconResolver.listIcons(iconsetUid);
            List<IconRow> immutable =
                Collections.unmodifiableList(filterRenderable(rows, iconResolver));
            mainThreadHandler.post(
                () -> {
                  try {
                    if (immutable.isEmpty()) {
                      iconGridView.setVisibility(View.GONE);
                      emptyView.setText(
                          themedContext.getString(R.string.goto_custom_icon_empty_icons));
                      emptyView.setVisibility(View.VISIBLE);
                    } else {
                      iconGridView.setAdapter(new IconGridAdapter(immutable));
                      iconGridView.setVisibility(View.VISIBLE);
                      emptyView.setVisibility(View.GONE);
                    }
                  } catch (Throwable t) {
                    Log.w(TAG, "refreshIconList bind failed", t);
                  }
                });
          } catch (Throwable t) {
            Log.w(TAG, "refreshIconList worker failed", t);
          }
        });
  }

  /**
   * Filter out rows whose bitmap fails to decode (FR-010a). This is the adapter-layer silent-skip
   * per [contracts/custom-icon-picker.md § Test contract item 12]. We do the filter once at bind
   * time rather than per-cell so {@code getCount()} reflects the actually-renderable set and {@code
   * getView()} is never called for a skipped row. Package-private + static so JVM unit tests can
   * exercise it without dialog UI / Robolectric.
   */
  static List<IconRow> filterRenderable(List<IconRow> rows, IconResolver iconResolver) {
    if (rows == null || rows.isEmpty()) return Collections.emptyList();
    List<IconRow> renderable = new ArrayList<>(rows.size());
    for (IconRow r : rows) {
      if (iconResolver.loadBitmap(r.id()) != null) {
        renderable.add(r);
      } else {
        Log.w(
            TAG,
            "loadBitmap returned null; skipping iconsetUid="
                + r.iconsetUid()
                + " fileName="
                + r.fileName());
      }
    }
    return renderable;
  }

  private IconsetSummary findIconsetByUid(String uid) {
    if (uid == null) return null;
    try {
      for (IconsetSummary s : iconResolver.listIconsets()) {
        if (uid.equals(s.uid())) return s;
      }
    } catch (Throwable t) {
      Log.w(TAG, "findIconsetByUid failed for " + uid, t);
    }
    return null;
  }

  private enum Step {
    ICONSET_LIST,
    ICON_LIST
  }

  // ---------------- adapters ----------------

  private final class IconsetListAdapter extends BaseAdapter {
    private final List<IconsetSummary> items;

    IconsetListAdapter(List<IconsetSummary> items) {
      this.items = items;
    }

    @Override
    public int getCount() {
      return items.size();
    }

    @Override
    public IconsetSummary getItem(int position) {
      return items.get(position);
    }

    @Override
    public long getItemId(int position) {
      return items.get(position).uid().hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      try {
        View v = convertView;
        if (v == null) {
          v =
              LayoutInflater.from(parent.getContext())
                  .inflate(R.layout.custom_icon_picker_iconset_row, parent, false);
        }
        IconsetSummary item = items.get(position);
        TextView text = v.findViewById(R.id.custom_icon_picker_iconset_row_text);
        text.setText(
            item.name()
                + String.format(
                    themedContext.getString(R.string.goto_custom_icon_iconset_count_suffix),
                    item.iconCount()));
        return v;
      } catch (Throwable t) {
        Log.w(TAG, "IconsetListAdapter.getView failed at position=" + position, t);
        // Defensive: return a minimal non-null view so ListView doesn't crash.
        TextView fallback = new TextView(parent.getContext());
        fallback.setText("");
        return fallback;
      }
    }
  }

  private final class IconGridAdapter extends BaseAdapter {
    private final List<IconRow> rows;

    IconGridAdapter(List<IconRow> rows) {
      this.rows = rows;
    }

    @Override
    public int getCount() {
      return rows.size();
    }

    @Override
    public IconRow getItem(int position) {
      return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
      return rows.get(position).id();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      try {
        View v = convertView;
        if (v == null) {
          v =
              LayoutInflater.from(parent.getContext())
                  .inflate(R.layout.custom_icon_picker_icon_cell, parent, false);
        }
        IconRow row = rows.get(position);
        ImageView thumb = v.findViewById(R.id.custom_icon_picker_icon_cell_thumb);
        TextView label = v.findViewById(R.id.custom_icon_picker_icon_cell_label);
        // Bitmap was confirmed non-null at filter time in refreshIconList; fetch is fast
        // (LRU-cacheable extension point) and safe to do synchronously on the main thread here.
        Bitmap bmp = iconResolver.loadBitmap(row.id());
        if (bmp != null) thumb.setImageBitmap(bmp);
        label.setText(row.displayName());
        thumb.setContentDescription(
            currentIconset == null
                ? row.displayName()
                : currentIconset.name() + " " + row.displayName());
        return v;
      } catch (Throwable t) {
        Log.w(TAG, "IconGridAdapter.getView failed at position=" + position, t);
        TextView fallback = new TextView(parent.getContext());
        fallback.setText("");
        return fallback;
      }
    }
  }
}
