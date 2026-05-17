package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.icons.UserIcon;
import com.atakmap.android.icons.UserIconSet;
import java.util.Objects;

/**
 * Immutable record of the operator's currently-picked custom icon. Carries everything the view
 * layer needs to render the picker preview (thumbnail-fetch id, iconset display name) plus the
 * canonical iconset-path string consumed by {@link
 * com.atakmap.android.user.PlacePointTool.MarkerCreator#setIconPath(String)} at submit time.
 *
 * <p>See [contracts/icon-resolver.md § resolveSelection] and data-model.md §1.2. Equality and hash
 * key off {@link #iconsetPath()} alone — that string uniquely identifies the icon within the host's
 * iconset library.
 */
public final class IconSelection {

  private final String iconsetPath;
  private final String iconsetUid;
  private final String iconsetName;
  private final String iconFileName;
  private final int iconId;

  public IconSelection(
      String iconsetPath, String iconsetUid, String iconsetName, String iconFileName, int iconId) {
    this.iconsetPath = Objects.requireNonNull(iconsetPath, "iconsetPath");
    this.iconsetUid = Objects.requireNonNull(iconsetUid, "iconsetUid");
    this.iconsetName = Objects.requireNonNull(iconsetName, "iconsetName");
    this.iconFileName = Objects.requireNonNull(iconFileName, "iconFileName");
    if (iconId < 0) {
      throw new IllegalArgumentException("iconId must be >= 0: " + iconId);
    }
    this.iconId = iconId;
  }

  public String iconsetPath() {
    return iconsetPath;
  }

  public String iconsetUid() {
    return iconsetUid;
  }

  public String iconsetName() {
    return iconsetName;
  }

  public String iconFileName() {
    return iconFileName;
  }

  public int iconId() {
    return iconId;
  }

  /** Construct from the typed value-class pair (test-friendly; no Android dependency). */
  public static IconSelection from(IconRow row, IconsetSummary iconset) {
    Objects.requireNonNull(row, "row");
    Objects.requireNonNull(iconset, "iconset");
    if (!row.iconsetUid().equals(iconset.uid())) {
      throw new IllegalArgumentException(
          "row.iconsetUid (" + row.iconsetUid() + ") != iconset.uid (" + iconset.uid() + ")");
    }
    return new IconSelection(
        row.iconsetPath(), row.iconsetUid(), iconset.name(), row.fileName(), row.id());
  }

  /** Construct directly from SDK objects — used by {@link IconResolver#resolveSelection}. */
  public static IconSelection from(UserIcon icon, UserIconSet iconset) {
    Objects.requireNonNull(icon, "icon");
    Objects.requireNonNull(iconset, "iconset");
    return new IconSelection(
        icon.getIconsetPath(),
        icon.getIconsetUid(),
        iconset.getName(),
        icon.getFileName(),
        icon.getId());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IconSelection)) return false;
    return iconsetPath.equals(((IconSelection) o).iconsetPath);
  }

  @Override
  public int hashCode() {
    return iconsetPath.hashCode();
  }

  @Override
  public String toString() {
    return "IconSelection{path=" + iconsetPath + ", iconsetName=" + iconsetName + "}";
  }
}
