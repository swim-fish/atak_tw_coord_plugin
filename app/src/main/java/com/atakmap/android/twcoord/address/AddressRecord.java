package com.atakmap.android.twcoord.address;

import java.util.Objects;

/**
 * One reverse-lookup hit returned by {@link AddressDatabaseFacade#nearestWithin}. Carries the
 * record's WGS-84 location plus both the fullwidth Taiwan-style address ({@link #displayName}) and
 * the halfwidth normalised form ({@link #displayNameHalfwidth}). The widget renders {@link
 * #displayName}; the halfwidth form is reserved for future forward-search use and is carried so the
 * resolver does not need a second DB round-trip if a later feature consumes it.
 */
public final class AddressRecord {

  private final double lat;
  private final double lon;
  private final String displayName;
  private final String displayNameHalfwidth;

  public AddressRecord(double lat, double lon, String displayName, String displayNameHalfwidth) {
    this.lat = lat;
    this.lon = lon;
    this.displayName = Objects.requireNonNull(displayName, "displayName");
    this.displayNameHalfwidth =
        Objects.requireNonNull(displayNameHalfwidth, "displayNameHalfwidth");
  }

  public double lat() {
    return lat;
  }

  public double lon() {
    return lon;
  }

  public String displayName() {
    return displayName;
  }

  public String displayNameHalfwidth() {
    return displayNameHalfwidth;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AddressRecord)) return false;
    AddressRecord that = (AddressRecord) o;
    return Double.compare(that.lat, lat) == 0
        && Double.compare(that.lon, lon) == 0
        && displayName.equals(that.displayName)
        && displayNameHalfwidth.equals(that.displayNameHalfwidth);
  }

  @Override
  public int hashCode() {
    return Objects.hash(lat, lon, displayName, displayNameHalfwidth);
  }
}
