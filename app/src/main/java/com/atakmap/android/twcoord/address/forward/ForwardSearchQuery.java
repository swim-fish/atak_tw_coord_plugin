package com.atakmap.android.twcoord.address.forward;

/**
 * Accumulated funnel state (data-model §2.4): county → district → street fragment → optional house
 * number, plus the distance-ranking anchor. Immutable; the controller produces a new instance per
 * stage transition via the {@code with*} methods.
 */
public final class ForwardSearchQuery {

  private final String county; // nullable until ① done
  private final CountySource countySource; // nullable until ① done
  private final String district; // nullable until ② done
  private final String streetFragment; // nullable until ③
  private final String houseNumber; // nullable, optional ④
  private final double anchorLat;
  private final double anchorLon;

  private ForwardSearchQuery(
      String county,
      CountySource countySource,
      String district,
      String streetFragment,
      String houseNumber,
      double anchorLat,
      double anchorLon) {
    this.county = county;
    this.countySource = countySource;
    this.district = district;
    this.streetFragment = streetFragment;
    this.houseNumber = houseNumber;
    this.anchorLat = anchorLat;
    this.anchorLon = anchorLon;
  }

  /** Empty query carrying only the distance anchor. */
  public static ForwardSearchQuery initial(double anchorLat, double anchorLon) {
    return new ForwardSearchQuery(null, null, null, null, null, anchorLat, anchorLon);
  }

  public ForwardSearchQuery withCounty(String county, CountySource source) {
    // Changing county clears the downstream district/street/number.
    return new ForwardSearchQuery(county, source, null, null, null, anchorLat, anchorLon);
  }

  /**
   * Re-point the distance-ranking anchor (everything else preserved). Used when the operator taps
   * 地圖中心 / 所在地 so subsequent candidate distances are relative to that reference point.
   */
  public ForwardSearchQuery withAnchor(double anchorLat, double anchorLon) {
    return new ForwardSearchQuery(
        county, countySource, district, streetFragment, houseNumber, anchorLat, anchorLon);
  }

  public ForwardSearchQuery withDistrict(String district) {
    // Changing district clears the downstream street/number.
    return new ForwardSearchQuery(
        county, countySource, district, null, null, anchorLat, anchorLon);
  }

  public ForwardSearchQuery withStreetFragment(String fragment) {
    return new ForwardSearchQuery(
        county, countySource, district, fragment, null, anchorLat, anchorLon);
  }

  public ForwardSearchQuery withHouseNumber(String houseNumber) {
    return new ForwardSearchQuery(
        county, countySource, district, streetFragment, houseNumber, anchorLat, anchorLon);
  }

  public String county() {
    return county;
  }

  public CountySource countySource() {
    return countySource;
  }

  public String district() {
    return district;
  }

  public String streetFragment() {
    return streetFragment;
  }

  public String houseNumber() {
    return houseNumber;
  }

  public double anchorLat() {
    return anchorLat;
  }

  public double anchorLon() {
    return anchorLon;
  }
}
