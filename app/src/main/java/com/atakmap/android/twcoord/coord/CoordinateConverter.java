package com.atakmap.android.twcoord.coord;

import java.util.Objects;

/**
 * Facade combining Projections → DatumShiftTwd67 → TaipowerGrid, with valid-domain guards from
 * contracts/coordinate-converter.md. Stateless, thread-safe, pure JVM.
 */
public final class CoordinateConverter {

  // Taiwan box covering main island + Penghu + Kinmen + Matsu (Lienchiang). All four
  // territories use TM2 zone 119 or 121 selected by longitude. The widened box catches:
  //   - main island (~21.9..25.3°N, 120..122°E)
  //   - Penghu     (~23.2..23.7°N, 119.5..119.7°E)
  //   - Kinmen     (~24.4..24.5°N, 118.2..118.5°E)  → lower LON_MIN
  //   - Matsu      (~26.1..26.4°N, 119.9..120.5°E)  → higher LAT_MAX
  private static final double LAT_MIN = 21.5;
  private static final double LAT_MAX = 26.5;
  private static final double LON_MIN = 118.0;
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
        // Taipower grid is main-island only (ADR-0001). Reject anything not in TM2 zone 121
        // before attempting the grid lookup — otherwise Penghu (zone 119) easting/northing
        // happen to land in a valid main-island cell purely by numerical coincidence.
        if (t97.zone() != 121) {
          return ConversionResult.outOfRange(fix, unit);
        }
        try {
          Twd67Tm2 t67 = DatumShiftTwd67.twd97ToTwd67(t97);
          // 11-char gives 1 m precision; the trailing two digits are speculative beyond typical
          // GPS accuracy but match Taipower field-survey conventions. See FR-011.
          TaipowerCode code = TaipowerGrid.fromTwd67(t67, TaipowerGrid.Precision.ELEVEN_CHAR);
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
