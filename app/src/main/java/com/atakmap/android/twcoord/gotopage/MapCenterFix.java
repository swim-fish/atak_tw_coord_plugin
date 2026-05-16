package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Wgs84;

/**
 * Snapshot of the current map-centre signal consumed by Auto Fill. Carries the underlying {@link
 * Wgs84} plus pre-computed booleans for each tab so the view can flip the per-tab Auto Fill
 * button's enabled state in constant time without re-running the converter on every redraw.
 */
public final class MapCenterFix {

  private final Wgs84 wgs84;
  private final boolean taipowerOk;
  private final boolean twd97Ok;
  private final boolean twd67Ok;

  public MapCenterFix(Wgs84 wgs84, boolean taipowerOk, boolean twd97Ok, boolean twd67Ok) {
    this.wgs84 = wgs84;
    this.taipowerOk = taipowerOk;
    this.twd97Ok = twd97Ok;
    this.twd67Ok = twd67Ok;
  }

  /**
   * Build a MapCenterFix from a freshly-sampled map-centre {@link Wgs84}. Uses feature 001's
   * forward {@link CoordinateConverter} as the oracle for "can this tab express this coordinate" —
   * keeps the readout widget's "out of range" rules and Auto Fill's "disabled" rules in lockstep.
   */
  public static MapCenterFix of(Wgs84 wgs84, CoordinateConverter converter) {
    if (wgs84 == null) {
      return new MapCenterFix(null, false, false, false);
    }
    ConversionResult taipower = converter.convert(wgs84, CoordinateUnit.TAIPOWER);
    ConversionResult twd97 = converter.convert(wgs84, CoordinateUnit.TWD97);
    ConversionResult twd67 = converter.convert(wgs84, CoordinateUnit.TWD67);
    return new MapCenterFix(wgs84, taipower.isOk(), twd97.isOk(), twd67.isOk());
  }

  public Wgs84 wgs84() {
    return wgs84;
  }

  public boolean taipowerOk() {
    return taipowerOk;
  }

  public boolean twd97Ok() {
    return twd97Ok;
  }

  public boolean twd67Ok() {
    return twd67Ok;
  }

  public boolean okForUnit(CoordinateUnit unit) {
    switch (unit) {
      case TAIPOWER:
        return taipowerOk;
      case TWD97:
        return twd97Ok;
      case TWD67:
        return twd67Ok;
      default:
        return false;
    }
  }
}
