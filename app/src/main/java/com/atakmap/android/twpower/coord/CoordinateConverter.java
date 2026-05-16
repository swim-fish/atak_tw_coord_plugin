package com.atakmap.android.twpower.coord;

import java.util.Objects;

/**
 * Facade combining Projections → DatumShiftTwd67 → TaipowerGrid, with valid-domain guards from
 * contracts/coordinate-converter.md. Stateless, thread-safe, pure JVM.
 */
public final class CoordinateConverter {

  // Conservative TM2 z121 box around Taiwan's main island (FR-009, contracts §Behaviour matrix).
  private static final double LAT_MIN = 21.5;
  private static final double LAT_MAX = 25.5;
  private static final double LON_MIN = 120.0;
  private static final double LON_MAX = 122.5;

  public ConversionResult convert(Wgs84 fix, CoordinateUnit unit) {
    Objects.requireNonNull(fix, "fix");
    Objects.requireNonNull(unit, "unit");

    if (!insideTaiwanBox(fix)) {
      return ConversionResult.outOfRange(fix, unit);
    }

    Twd97Tm2 t97 = Projections.wgs84ToTwd97(fix);
    switch (unit) {
      case TWD97:
        return ConversionResult.ok(t97, unit);
      case TWD67:
        return ConversionResult.ok(DatumShiftTwd67.twd97ToTwd67(t97), unit);
      case TAIPOWER:
        try {
          Twd67Tm2 t67 = DatumShiftTwd67.twd97ToTwd67(t97);
          TaipowerCode code = TaipowerGrid.fromTwd67(t67, TaipowerGrid.Precision.NINE_CHAR);
          return ConversionResult.ok(code, unit);
        } catch (TaipowerGrid.OutOfCoverageException e) {
          return ConversionResult.outOfRange(fix, unit);
        }
      default:
        throw new IllegalStateException("unhandled unit: " + unit);
    }
  }

  private static boolean insideTaiwanBox(Wgs84 fix) {
    double lat = fix.latitudeDeg();
    double lon = fix.longitudeDeg();
    return lat >= LAT_MIN && lat <= LAT_MAX && lon >= LON_MIN && lon <= LON_MAX;
  }
}
