package com.atakmap.android.twpower.coord;

public final class Twd67Tm2 {
  private final double eastingMetres;
  private final double northingMetres;
  private final int zone;

  public Twd67Tm2(double eastingMetres, double northingMetres) {
    this(eastingMetres, northingMetres, 121);
  }

  public Twd67Tm2(double eastingMetres, double northingMetres, int zone) {
    if (zone != 121) {
      throw new IllegalArgumentException("v1 supports TWD67 zone 121 only; got: " + zone);
    }
    this.eastingMetres = eastingMetres;
    this.northingMetres = northingMetres;
    this.zone = zone;
  }

  public double eastingMetres() {
    return eastingMetres;
  }

  public double northingMetres() {
    return northingMetres;
  }

  public int zone() {
    return zone;
  }
}
