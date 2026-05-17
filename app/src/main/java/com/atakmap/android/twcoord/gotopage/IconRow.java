package com.atakmap.android.twcoord.gotopage;

import java.util.Locale;
import java.util.Objects;

/**
 * Lightweight, immutable representation of one row in the host's icon database. The picker grid at
 * step 2 binds to a {@code List<IconRow>}; bitmaps are fetched lazily per cell via {@link
 * IconResolver#loadBitmap(int)} keyed off {@link #id()}.
 *
 * <p>Equality / hash on {@code id} alone — the host assigns row IDs from the SQLite primary key,
 * which is stable for the lifetime of the iconset.
 */
public final class IconRow {

  private final int id;
  private final String iconsetUid;
  private final String group;
  private final String fileName;
  private final String displayName;
  private final String iconsetPath;

  public IconRow(int id, String iconsetUid, String group, String fileName) {
    if (id < 0) {
      throw new IllegalArgumentException("id must be >= 0: " + id);
    }
    this.id = id;
    this.iconsetUid = Objects.requireNonNull(iconsetUid, "iconsetUid");
    this.group = Objects.requireNonNull(group, "group");
    this.fileName = Objects.requireNonNull(fileName, "fileName");
    this.displayName = stripExtension(fileName);
    this.iconsetPath = iconsetUid + "/" + group + "/" + fileName;
  }

  public int id() {
    return id;
  }

  public String iconsetUid() {
    return iconsetUid;
  }

  public String group() {
    return group;
  }

  public String fileName() {
    return fileName;
  }

  public String displayName() {
    return displayName;
  }

  public String iconsetPath() {
    return iconsetPath;
  }

  /**
   * Strips a trailing {@code .png}/{@code .jpg}/{@code .jpeg}/{@code .svg} suffix
   * (case-insensitive). Other extensions or no extension pass through unchanged. Used for grid-cell
   * caption rendering — the underlying iconset path always carries the full file name.
   */
  private static String stripExtension(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".svg")) {
      return name.substring(0, name.length() - 4);
    }
    if (lower.endsWith(".jpeg")) {
      return name.substring(0, name.length() - 5);
    }
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IconRow)) return false;
    return id == ((IconRow) o).id;
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public String toString() {
    return "IconRow{id=" + id + ", path=" + iconsetPath + "}";
  }
}
