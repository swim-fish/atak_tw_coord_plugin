package com.atakmap.android.twcoord.coord;

public final class Twd97Tm2 {
  private final double eastingMetres;
  private final double northingMetres;
  private final int zone;

  public Twd97Tm2(double eastingMetres, double northingMetres, int zone) {
    if (zone != 119 && zone != 121) {
      throw new IllegalArgumentException("unsupported TWD97 zone: " + zone);
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
