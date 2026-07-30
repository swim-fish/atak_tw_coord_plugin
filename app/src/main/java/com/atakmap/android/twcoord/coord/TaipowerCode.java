package com.atakmap.android.twcoord.coord;

/**
 * Taipower grid code over TWD67 TM2 zone 121. v1 ships 9-character precision (10 m) by default; the
 * optional 11-character (1 m) precision carries the trailing oneMeterE/N digits.
 */
public final class TaipowerCode {

  private final char region;
  private final int subRegion;
  private final char hundredMeterE;
  private final char hundredMeterN;
  private final int tenMeterE;
  private final int tenMeterN;
  private final Integer oneMeterE;
  private final Integer oneMeterN;

  public TaipowerCode(
      char region,
      int subRegion,
      char hundredMeterE,
      char hundredMeterN,
      int tenMeterE,
      int tenMeterN,
      Integer oneMeterE,
      Integer oneMeterN) {
    if (region < 'A' || region > 'X' || region == 'Y' || region == 'Z') {
      throw new IllegalArgumentException("region out of A..X (Y/Z reserved): " + region);
    }
    if (subRegion < 0 || subRegion > 9999) {
      throw new IllegalArgumentException("subRegion out of 0..9999: " + subRegion);
    }
    if (hundredMeterE < 'A' || hundredMeterE > 'H') {
      throw new IllegalArgumentException("east-west hundred-metre letter must be A..H");
    }
    if (hundredMeterN < 'A' || hundredMeterN > 'E') {
      throw new IllegalArgumentException("north-south hundred-metre letter must be A..E");
    }
    if (tenMeterE < 0 || tenMeterE > 9 || tenMeterN < 0 || tenMeterN > 9) {
      throw new IllegalArgumentException("ten-metre digits must be 0..9");
    }
    if ((oneMeterE == null) != (oneMeterN == null)) {
      throw new IllegalArgumentException("one-metre digits must be both present or both absent");
    }
    if (oneMeterE != null && (oneMeterE < 0 || oneMeterE > 9 || oneMeterN < 0 || oneMeterN > 9)) {
      throw new IllegalArgumentException("one-metre digits must be 0..9");
    }
    this.region = region;
    this.subRegion = subRegion;
    this.hundredMeterE = hundredMeterE;
    this.hundredMeterN = hundredMeterN;
    this.tenMeterE = tenMeterE;
    this.tenMeterN = tenMeterN;
    this.oneMeterE = oneMeterE;
    this.oneMeterN = oneMeterN;
  }

  public char region() {
    return region;
  }

  public int subRegion() {
    return subRegion;
  }

  public char hundredMeterE() {
    return hundredMeterE;
  }

  public char hundredMeterN() {
    return hundredMeterN;
  }

  public int tenMeterE() {
    return tenMeterE;
  }

  public int tenMeterN() {
    return tenMeterN;
  }

  public Integer oneMeterE() {
    return oneMeterE;
  }

  public Integer oneMeterN() {
    return oneMeterN;
  }

  public boolean hasOneMetrePrecision() {
    return oneMeterE != null;
  }
}
