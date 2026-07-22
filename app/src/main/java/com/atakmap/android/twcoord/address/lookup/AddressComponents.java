package com.atakmap.android.twcoord.address.lookup;

/** Immutable structured projection of a Taiwan address. */
public final class AddressComponents {
  private final String countyCity;
  private final String districtTownship;
  private final String roadLocality;
  private final String tail;

  public AddressComponents(
      String countyCity, String districtTownship, String roadLocality, String tail) {
    this.countyCity = valueOrEmpty(countyCity);
    this.districtTownship = valueOrEmpty(districtTownship);
    this.roadLocality = valueOrEmpty(roadLocality);
    this.tail = valueOrEmpty(tail);
  }

  public String countyCity() {
    return countyCity;
  }

  public String districtTownship() {
    return districtTownship;
  }

  public String roadLocality() {
    return roadLocality;
  }

  public String tail() {
    return tail;
  }

  public String compose() {
    return countyCity + districtTownship + roadLocality + tail;
  }

  private static String valueOrEmpty(String value) {
    return value != null ? value : "";
  }
}
