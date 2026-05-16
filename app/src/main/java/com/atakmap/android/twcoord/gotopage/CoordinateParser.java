package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.DatumShiftTwd67;
import com.atakmap.android.twcoord.coord.Projections;
import com.atakmap.android.twcoord.coord.Twd67Tm2;
import com.atakmap.android.twcoord.coord.Twd97Tm2;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

// Easting / northing structural bounds for the TWD97 / TWD67 parse paths. 6-or-7 digit easting,
// 7-digit northing — covers the entire Taiwan TM2 grid for either zone with headroom.

/**
 * Facade for the input-page inverse-converter pipeline (string → {@code Wgs84}). Inverse of feature
 * 001's {@link com.atakmap.android.twcoord.coord.CoordinateConverter}.
 *
 * <p>Stateless and thread-safe (delegates to the immutable {@code proj4j} {@code
 * CoordinateTransform} instances on {@link com.atakmap.android.twcoord.coord.Projections}). Each
 * method validates input shape first (returning {@link ParseResult.Invalid}) then attempts the
 * conversion; if the resulting WGS84 lies outside Taiwan's coverage box, returns {@link
 * ParseResult.OutOfRange}.
 */
public final class CoordinateParser {

  // Taiwan coverage box — same constants used by feature 001's CoordinateConverter so the
  // forward and inverse paths agree on which inputs are "in range".
  private static final double LAT_MIN = 21.5;
  private static final double LAT_MAX = 26.5;
  private static final double LON_MIN = 118.0;
  private static final double LON_MAX = 122.5;

  // TM2 easting / northing bounds — 6 or 7 visible digits.
  private static final int EASTING_MIN = 100_000;
  private static final int EASTING_MAX = 9_999_999;
  private static final int NORTHING_MIN = 1_000_000;
  private static final int NORTHING_MAX = 9_999_999;

  public CoordinateParser() {}

  /** Parses a Taipower grid string (9 or 11 chars, case-insensitive, whitespace-tolerant). */
  public ParseResult parseTaipower(String rawValue) {
    Objects.requireNonNull(rawValue, "rawValue");
    TaipowerParser.ParseAttempt attempt = TaipowerParser.parse(rawValue);
    if (attempt.outcome.invalid != null) {
      return ParseResult.invalid(CoordinateUnit.TAIPOWER, attempt.outcome.invalid);
    }
    Twd67Tm2 t67 = attempt.outcome.ok;
    // Forward shift (TWD67 → TWD97) then proj4j inverse (TWD97 → WGS84).
    Twd97Tm2 t97 = DatumShiftTwd67.twd67ToTwd97(t67);
    Wgs84 wgs84 = Projections.twd97ToWgs84(t97, System.currentTimeMillis());
    if (!insideTaiwanBox(wgs84)) {
      return ParseResult.outOfRange(CoordinateUnit.TAIPOWER, wgs84);
    }
    return ParseResult.ok(wgs84, new CoordinateInput.Taipower(attempt.normalised));
  }

  /** Parses TWD97 TM2 easting/northing in metres. */
  public ParseResult parseTwd97(int easting, int northing, int zone) {
    ParseResult.Reason structural = validateTm2Bounds(easting, northing, zone);
    if (structural != null) {
      return ParseResult.invalid(CoordinateUnit.TWD97, structural);
    }
    Twd97Tm2 t97 = new Twd97Tm2(easting, northing, zone);
    Wgs84 wgs84 = Projections.twd97ToWgs84(t97, System.currentTimeMillis());
    if (!insideTaiwanBox(wgs84)) {
      return ParseResult.outOfRange(CoordinateUnit.TWD97, wgs84);
    }
    return ParseResult.ok(wgs84, new CoordinateInput.Twd97(easting, northing, zone));
  }

  /** Parses TWD67 TM2 easting/northing in metres. */
  public ParseResult parseTwd67(int easting, int northing, int zone) {
    ParseResult.Reason structural = validateTm2Bounds(easting, northing, zone);
    if (structural != null) {
      return ParseResult.invalid(CoordinateUnit.TWD67, structural);
    }
    Twd67Tm2 t67 = new Twd67Tm2(easting, northing, zone);
    Twd97Tm2 t97 = DatumShiftTwd67.twd67ToTwd97(t67);
    Wgs84 wgs84 = Projections.twd97ToWgs84(t97, System.currentTimeMillis());
    if (!insideTaiwanBox(wgs84)) {
      return ParseResult.outOfRange(CoordinateUnit.TWD67, wgs84);
    }
    return ParseResult.ok(wgs84, new CoordinateInput.Twd67(easting, northing, zone));
  }

  /** Returns the failing {@link ParseResult.Reason} if the bounds are wrong, else null. */
  private static ParseResult.Reason validateTm2Bounds(int easting, int northing, int zone) {
    if (zone != 121 && zone != 119) return ParseResult.Reason.BAD_ZONE;
    if (easting < EASTING_MIN || easting > EASTING_MAX) return ParseResult.Reason.BAD_LENGTH;
    if (northing < NORTHING_MIN || northing > NORTHING_MAX) return ParseResult.Reason.BAD_LENGTH;
    return null;
  }

  /** Dispatch helper used by the page's submit button. */
  public ParseResult parse(CoordinateInput input) {
    Objects.requireNonNull(input, "input");
    if (input instanceof CoordinateInput.Taipower) {
      return parseTaipower(((CoordinateInput.Taipower) input).rawValue());
    }
    if (input instanceof CoordinateInput.Twd97) {
      CoordinateInput.Twd97 t = (CoordinateInput.Twd97) input;
      return parseTwd97(t.easting(), t.northing(), t.zone());
    }
    if (input instanceof CoordinateInput.Twd67) {
      CoordinateInput.Twd67 t = (CoordinateInput.Twd67) input;
      return parseTwd67(t.easting(), t.northing(), t.zone());
    }
    throw new IllegalStateException("unknown CoordinateInput subtype: " + input.getClass());
  }

  private static boolean insideTaiwanBox(Wgs84 fix) {
    double lat = fix.latitudeDeg();
    double lon = fix.longitudeDeg();
    return lat >= LAT_MIN && lat <= LAT_MAX && lon >= LON_MIN && lon <= LON_MAX;
  }
}
