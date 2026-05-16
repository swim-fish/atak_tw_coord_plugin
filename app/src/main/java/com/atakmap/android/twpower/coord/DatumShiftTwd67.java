package com.atakmap.android.twpower.coord;

/**
 * Four-parameter datum shift between TWD97 (EPSG:3826) and TWD67 over TM2 zone 121. Constants and
 * formulas are copied verbatim from pwa_map src/coord/twd67.ts:4-14 — see ADR-0001 for provenance
 * and the silent-400-m proj4 trap that motivates this hand-rolled path.
 */
public final class DatumShiftTwd67 {

  private static final double DELTA_X = 807.8;
  private static final double DELTA_Y = 248.6;
  private static final double A = 0.000_015_49;
  private static final double B = 0.000_006_521;

  private DatumShiftTwd67() {}

  public static Twd67Tm2 twd97ToTwd67(Twd97Tm2 t97) {
    double x = t97.eastingMetres();
    double y = t97.northingMetres();
    double e = x - DELTA_X - A * x - B * y;
    double n = y + DELTA_Y - A * y - B * x;
    return new Twd67Tm2(e, n);
  }

  public static Twd97Tm2 twd67ToTwd97(Twd67Tm2 t67) {
    double x = t67.eastingMetres();
    double y = t67.northingMetres();
    double e = x + DELTA_X + A * x + B * y;
    double n = y - DELTA_Y + A * y + B * x;
    return new Twd97Tm2(e, n, 121);
  }
}
