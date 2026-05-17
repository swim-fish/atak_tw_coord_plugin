package com.atakmap.android.twcoord.gotopage;

import android.content.Context;
import android.graphics.Bitmap;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconDatabase;
import com.atakmap.android.icons.UserIconSet;
import com.atakmap.coremap.log.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The single seam between the plugin and the host's icon database. Every other class in this
 * feature consumes {@code IconResolver}'s typed value-class API instead of touching {@link
 * com.atakmap.android.icons.UserIconDatabase} directly — so the rest of the code stays JVM-mockable
 * and Constitution VI guards live at exactly one boundary.
 *
 * <p>See {@code contracts/icon-resolver.md} for the full contract. Threading: every method but
 * {@link #isValidIconsetPath(String)} and {@link #invalidateCaches()} executes synchronous SQLite
 * I/O and MUST be called off the main thread (Constitution Principle IV); callers own the executor.
 *
 * <p>Constitution VI: every public method body is wrapped in {@code try/catch (Throwable)} so an
 * SDK fault returns the safe default (empty list / null / false) instead of escaping into the host
 * process.
 */
public final class IconResolver {

  private static final String TAG = "TwCoordIconResolver";

  private final IconDatabaseFacade facade;
  private final AtomicReference<List<IconsetSummary>> iconsetCache = new AtomicReference<>(null);

  /** Production constructor — wraps {@code UserIconDatabase.instance(ctx)}. */
  public IconResolver(Context pluginContext) {
    this(adapt(Objects.requireNonNull(pluginContext, "pluginContext")));
  }

  /** Test-friendly constructor — accept any {@link IconDatabaseFacade} (typically a mock). */
  public IconResolver(IconDatabaseFacade facade) {
    this.facade = Objects.requireNonNull(facade, "facade");
  }

  private static IconDatabaseFacade adapt(Context pluginContext) {
    final UserIconDatabase db = UserIconDatabase.instance(pluginContext);
    return new IconDatabaseFacade() {
      @Override
      public List<UserIconSet> getIconSets(boolean withIcons, boolean withBitmaps) {
        return db.getIconSets(withIcons, withBitmaps);
      }

      @Override
      public UserIconSet getIconSet(String uid, boolean withIcons, boolean withBitmaps) {
        return db.getIconSet(uid, withIcons, withBitmaps);
      }

      @Override
      public Bitmap getIconBitmap(int iconId) {
        return db.getIconBitmap(iconId);
      }

      @Override
      public UserIcon getIcon(String iconsetUid, String fileName, boolean withBitmap) {
        return db.getIcon(iconsetUid, fileName, withBitmap);
      }
    };
  }

  /** Enumerate every iconset, alphabetical by name. Cached until {@link #invalidateCaches()}. */
  public List<IconsetSummary> listIconsets() {
    try {
      List<IconsetSummary> cached = iconsetCache.get();
      if (cached != null) return cached;

      List<UserIconSet> sets = facade.getIconSets(true, false);
      if (sets == null) {
        iconsetCache.set(Collections.emptyList());
        return Collections.emptyList();
      }
      List<IconsetSummary> out = new ArrayList<>(sets.size());
      for (UserIconSet s : sets) {
        if (s == null) continue;
        String uid = s.getUid();
        String name = s.getName();
        if (uid == null || name == null) continue;
        int count = 0;
        List<UserIcon> icons = s.getIcons();
        if (icons != null) {
          for (UserIcon icon : icons) {
            if (icon != null && icon.isValid()) count++;
          }
        }
        out.add(new IconsetSummary(uid, name, count));
      }
      Collections.sort(
          out,
          (a, b) -> a.name().toLowerCase(Locale.ROOT).compareTo(b.name().toLowerCase(Locale.ROOT)));
      List<IconsetSummary> immutable = Collections.unmodifiableList(out);
      iconsetCache.set(immutable);
      return immutable;
    } catch (Throwable t) {
      Log.w(TAG, "listIconsets failed", t);
      return Collections.emptyList();
    }
  }

  /** Enumerate one iconset's icons, alphabetical by displayName. Skips invalid rows. */
  public List<IconRow> listIcons(String iconsetUid) {
    try {
      if (iconsetUid == null || iconsetUid.isEmpty()) return Collections.emptyList();
      UserIconSet set = facade.getIconSet(iconsetUid, true, false);
      if (set == null) return Collections.emptyList();
      List<UserIcon> icons = set.getIcons();
      if (icons == null) return Collections.emptyList();
      List<IconRow> out = new ArrayList<>(icons.size());
      for (UserIcon i : icons) {
        if (i == null || !i.isValid()) continue;
        try {
          out.add(new IconRow(i.getId(), i.getIconsetUid(), i.getGroup(), i.getFileName()));
        } catch (RuntimeException ex) {
          Log.w(TAG, "listIcons: dropping invalid row id=" + i.getId(), ex);
        }
      }
      Collections.sort(
          out,
          (a, b) ->
              a.displayName()
                  .toLowerCase(Locale.ROOT)
                  .compareTo(b.displayName().toLowerCase(Locale.ROOT)));
      return Collections.unmodifiableList(out);
    } catch (Throwable t) {
      Log.w(TAG, "listIcons failed for " + iconsetUid, t);
      return Collections.emptyList();
    }
  }

  /** Single-icon bitmap fetch. Returns null on miss / decode failure / any fault. */
  public Bitmap loadBitmap(int iconId) {
    try {
      return facade.getIconBitmap(iconId);
    } catch (Throwable t) {
      Log.w(TAG, "loadBitmap failed for id=" + iconId, t);
      return null;
    }
  }

  /**
   * Resolve a persisted iconset path back to a full {@link IconSelection}, looking up both the icon
   * and its parent iconset's display name. Returns null if the path is malformed, the icon is
   * missing, or the iconset is missing. See FR-009 / R5 — this is the bind-path validity probe.
   */
  public IconSelection resolveSelection(String iconsetPath) {
    try {
      if (iconsetPath == null || iconsetPath.isEmpty() || !iconsetPath.contains("/")) {
        return null;
      }
      String[] tokens = iconsetPath.split("/");
      if (tokens.length < 3) return null;
      String uid = tokens[0];
      String fileName = tokens[tokens.length - 1];
      if (uid.isEmpty() || fileName.isEmpty()) return null;
      UserIcon icon = facade.getIcon(uid, fileName, false);
      if (icon == null || !icon.isValid()) return null;
      UserIconSet set = facade.getIconSet(uid, false, false);
      if (set == null || set.getName() == null) return null;
      return IconSelection.from(icon, set);
    } catch (Throwable t) {
      Log.w(TAG, "resolveSelection failed for " + iconsetPath, t);
      return null;
    }
  }

  /** Cheap validity probe; main-thread permitted. */
  public boolean isValidIconsetPath(String iconsetPath) {
    return resolveSelection(iconsetPath) != null;
  }

  /** Called by the view layer's ICONSET_ADDED / ICONSET_REMOVED listener. */
  public void invalidateCaches() {
    iconsetCache.set(null);
  }
}
