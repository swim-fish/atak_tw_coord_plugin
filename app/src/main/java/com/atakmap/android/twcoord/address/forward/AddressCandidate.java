package com.atakmap.android.twcoord.address.forward;

import java.util.Objects;

/**
 * One street-matched address from the selected county's dataset — the unit shown in the
 * forward-search candidate list and handed to GoTo on confirm (data-model §2.5).
 */
public final class AddressCandidate {

  private final double lat;
  private final double lon;
  private final String displayName;
  private final String displayNameHalfwidth;
  private final String street;
  private final String number;
  private final double distanceMeters;

  public AddressCandidate(
      double lat,
      double lon,
      String displayName,
      String displayNameHalfwidth,
      String street,
      String number,
      double distanceMeters) {
    this.lat = lat;
    this.lon = lon;
    this.displayName = displayName != null ? displayName : "";
    this.displayNameHalfwidth = displayNameHalfwidth != null ? displayNameHalfwidth : "";
    this.street = street != null ? street : "";
    this.number = number != null ? number : "";
    this.distanceMeters = distanceMeters;
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

  public String street() {
    return street;
  }

  public String number() {
    return number;
  }

  public double distanceMeters() {
    return distanceMeters;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AddressCandidate)) return false;
    AddressCandidate that = (AddressCandidate) o;
    return Double.compare(that.lat, lat) == 0
        && Double.compare(that.lon, lon) == 0
        && displayName.equals(that.displayName)
        && street.equals(that.street)
        && number.equals(that.number);
  }

  @Override
  public int hashCode() {
    return Objects.hash(lat, lon, displayName, street, number);
  }

  @Override
  public String toString() {
    return "AddressCandidate{" + displayName + " @" + lat + "," + lon + " " + distanceMeters + "m}";
  }
}
