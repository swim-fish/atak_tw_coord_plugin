package com.atakmap.android.twcoord.nativeentry;

import android.app.AlertDialog;
import android.content.Context;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.log.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Revision-fenced candidate chooser whose window and plugin resources have separate owners. */
public final class AddressCandidateDialog {
  private static final String TAG = "AddressCandidateDialog";
  private static final int MAX_ROWS = 20;

  private final Context windowContext;
  private final Context pluginContext;
  private final AddressEntryController controller;
  private AlertDialog dialog;
  private boolean disposed;

  public AddressCandidateDialog(
      Context windowContext, Context pluginContext, AddressEntryController controller) {
    this.windowContext = Objects.requireNonNull(windowContext, "windowContext");
    this.pluginContext = Objects.requireNonNull(pluginContext, "pluginContext");
    this.controller = Objects.requireNonNull(controller, "controller");
  }

  public void show() {
    if (disposed) return;
    dismiss();
    long expectedRevision = controller.draft().draftRevision();
    List<AddressCandidate> all = controller.candidates();
    List<AddressCandidate> visible =
        new ArrayList<>(all.subList(0, Math.min(MAX_ROWS, all.size())));
    if (visible.isEmpty()) return;

    String title = pluginContext.getString(R.string.native_entry_address_candidates_title);
    CharSequence[] rows = new CharSequence[visible.size()];
    for (int i = 0; i < visible.size(); i++) {
      AddressCandidate candidate = visible.get(i);
      rows[i] =
          pluginContext.getString(
              R.string.native_entry_address_candidate_format,
              candidate.county(),
              candidate.displayAddress());
    }
    try {
      dialog =
          new AlertDialog.Builder(windowContext)
              .setTitle(title)
              .setItems(
                  rows,
                  (ignored, which) -> {
                    if (disposed
                        || which < 0
                        || which >= visible.size()
                        || controller.draft().draftRevision() != expectedRevision) return;
                    controller.selectCandidate(visible.get(which).candidateId(), true);
                  })
              .create();
      dialog.show();
    } catch (RuntimeException e) {
      Log.w(TAG, "candidate dialog show failed", e);
      dialog = null;
    }
  }

  public void dismiss() {
    AlertDialog current = dialog;
    dialog = null;
    if (current == null) return;
    try {
      current.dismiss();
    } catch (RuntimeException e) {
      Log.w(TAG, "candidate dialog dismiss failed", e);
    }
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    dismiss();
  }

  AlertDialog dialogForTest() {
    return dialog;
  }
}
