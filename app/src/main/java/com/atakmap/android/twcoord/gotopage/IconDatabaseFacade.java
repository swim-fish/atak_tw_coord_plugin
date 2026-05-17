package com.atakmap.android.twcoord.gotopage;

import android.graphics.Bitmap;
import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconSet;
import java.util.List;

/**
 * JVM-mockable seam over {@link com.atakmap.android.icons.UserIconDatabase}. {@link IconResolver}
 * depends on this interface, not on the singleton directly, so JVM unit tests can inject a fake
 * without needing an Android context or the host's {@code iconsets.sqlite}.
 *
 * <p>The production adapter is a thin wrapper around {@code UserIconDatabase.instance(ctx)}; see
 * {@link IconResolver}'s {@link android.content.Context}-accepting constructor.
 */
public interface IconDatabaseFacade {

  /** Mirror of {@code UserIconDatabase.getIconSets(boolean, boolean)}. */
  List<UserIconSet> getIconSets(boolean withIcons, boolean withBitmaps);

  /** Mirror of {@code UserIconDatabase.getIconSet(String, boolean, boolean)}. */
  UserIconSet getIconSet(String iconsetUid, boolean withIcons, boolean withBitmaps);

  /** Mirror of {@code UserIconDatabase.getIconBitmap(int)}. */
  Bitmap getIconBitmap(int iconId);

  /** Mirror of {@code UserIconDatabase.getIcon(String, String, boolean)}. */
  UserIcon getIcon(String iconsetUid, String fileName, boolean withBitmap);
}
