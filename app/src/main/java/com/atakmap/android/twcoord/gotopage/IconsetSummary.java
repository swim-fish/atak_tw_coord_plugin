package com.atakmap.android.twcoord.gotopage;

import java.util.Objects;

/**
 * Lightweight, immutable summary of a {@code UserIconSet} suitable for picker step 1 (iconset
 * list). Carries only the fields the UI needs to render a row; the SDK's {@code UserIconSet} stays
 * inside {@link IconResolver}. See contracts/icon-resolver.md § IconsetSummary.
 *
 * <p>Equality / hash on {@code uid} alone — name and icon count can change across iconset versions,
 * but the UID is stable per the host's iconset model.
 */
public final class IconsetSummary {

  private final String uid;
  private final String name;
  private final int iconCount;

  public IconsetSummary(String uid, String name, int iconCount) {
    this.uid = Objects.requireNonNull(uid, "uid");
    this.name = Objects.requireNonNull(name, "name");
    if (iconCount < 0) {
      throw new IllegalArgumentException("iconCount must be >= 0: " + iconCount);
    }
    this.iconCount = iconCount;
  }

  public String uid() {
    return uid;
  }

  public String name() {
    return name;
  }

  public int iconCount() {
    return iconCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IconsetSummary)) return false;
    return uid.equals(((IconsetSummary) o).uid);
  }

  @Override
  public int hashCode() {
    return uid.hashCode();
  }

  @Override
  public String toString() {
    return "IconsetSummary{uid=" + uid + ", name=" + name + ", iconCount=" + iconCount + "}";
  }
}
