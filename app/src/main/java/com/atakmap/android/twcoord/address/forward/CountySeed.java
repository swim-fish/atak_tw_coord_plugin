package com.atakmap.android.twcoord.address.forward;

/**
 * The result of seeding the county stage from the current anchors (data-model §2.3): the default
 * county to pre-select plus the two source localities so the page can offer a one-tap switch.
 *
 * <p>Per FR-005 the default is the map-centre county when self and map-centre disagree; when they
 * agree (or one is absent) the default is whichever is available. Any field may be {@code null}
 * (e.g. offshore self-marker, or boundary data absent).
 */
public final class CountySeed {

  private final String defaultCounty; // nullable — null ⇒ no auto-selection, prompt the list
  private final CountySource defaultSource; // nullable when defaultCounty null
  private final String selfCounty; // nullable
  private final String selfDistrict; // nullable
  private final String mapCenterCounty; // nullable
  private final String mapCenterDistrict; // nullable

  public CountySeed(
      String defaultCounty,
      CountySource defaultSource,
      String selfCounty,
      String selfDistrict,
      String mapCenterCounty,
      String mapCenterDistrict) {
    this.defaultCounty = defaultCounty;
    this.defaultSource = defaultSource;
    this.selfCounty = selfCounty;
    this.selfDistrict = selfDistrict;
    this.mapCenterCounty = mapCenterCounty;
    this.mapCenterDistrict = mapCenterDistrict;
  }

  public String defaultCounty() {
    return defaultCounty;
  }

  public CountySource defaultSource() {
    return defaultSource;
  }

  public String selfCounty() {
    return selfCounty;
  }

  public String selfDistrict() {
    return selfDistrict;
  }

  public String mapCenterCounty() {
    return mapCenterCounty;
  }

  public String mapCenterDistrict() {
    return mapCenterDistrict;
  }

  public boolean hasDefault() {
    return defaultCounty != null;
  }
}
