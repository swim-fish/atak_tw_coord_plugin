package com.atakmap.android.twcoord.gotopage;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Formatter;
import com.atakmap.android.twcoord.coord.TaipowerCode;
import com.atakmap.android.twcoord.coord.TaiwanCities;
import com.atakmap.android.twcoord.coord.TaiwanCities.City;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Locale;
import org.junit.Test;

/**
 * Round-trip tests for the inverse-converter pipeline: for each city in {@link TaiwanCities},
 * render the forward unit string, feed it back through {@link CoordinateParser}, and assert the
 * recovered {@link Wgs84} is within the per-unit tolerance band defined in
 * contracts/coordinate-parser.md.
 *
 * <p>Phase 3 (US1) Taipower section is in this file. Phase 4 (US2) extends with TWD97 / TWD67
 * sections in a separate test method group.
 */
public class CoordinateParserRoundTripTest {

  private final CoordinateConverter converter = new CoordinateConverter();
  private final Formatter formatter = new Formatter();
  private final CoordinateParser parser = new CoordinateParser();

  /** Taipower round-trip tolerance — matches TWD67 main-island tolerance from feature 001. */
  private static final double TOL_TAIPOWER_M = 5.0;

  @Test
  public void taipower_roundtrip_allMainIslandCities() {
    int covered = 0;
    for (City city : TaiwanCities.ALL) {
      if (!city.isMainIsland) continue;
      Wgs84 fixIn =
          new Wgs84(city.latDeg, city.lonDeg, /*epochMs*/ 1_000L, Wgs84.Source.MAP_CENTRE);
      ConversionResult forward = converter.convert(fixIn, CoordinateUnit.TAIPOWER);
      // Forward may return OutOfRange for a city that falls into a blank cell in the 8×4 letter
      // table (I underwater, S = Matsu offshore). The round-trip invariant does not apply there,
      // so just skip such cities; the covered>=10 guard below still keeps the test honest.
      if (!forward.isOk()) continue;
      covered++;
      @SuppressWarnings("rawtypes")
      ConversionResult.Ok ok = (ConversionResult.Ok) forward;
      TaipowerCode code = (TaipowerCode) ok.value();
      String rendered = formatTaipower(code);

      ParseResult inverse = parser.parseTaipower(rendered);
      assertThat(inverse.isOk())
          .as("inverse Taipower must succeed for %s (rendered=%s)", city.name, rendered)
          .isTrue();
      Wgs84 fixOut = ((ParseResult.Ok) inverse).wgs84();
      double metres = haversineMetres(fixIn, fixOut);
      assertThat(metres)
          .as("round-trip distance for %s (Taipower %s)", city.name, rendered)
          .isLessThanOrEqualTo(TOL_TAIPOWER_M);
    }
    // Sanity guard: at least the canonical Taipei/Taichung/Hsinchu/Hualien band MUST be covered.
    // If this count drops to zero the test is silently passing without exercising anything.
    assertThat(covered)
        .as("Taipower round-trip must cover at least 10 main-island cities")
        .isGreaterThanOrEqualTo(10);
  }

  // === TWD97 round-trip across all 22 cities ===
  private static final double TOL_TWD97_M = 1.0;

  @Test
  public void twd97_roundtrip_allCities() {
    for (City city : TaiwanCities.ALL) {
      Wgs84 fixIn =
          new Wgs84(city.latDeg, city.lonDeg, /*epochMs*/ 1_000L, Wgs84.Source.MAP_CENTRE);
      ParseResult r =
          parser.parseTwd97(
              (int) Math.round(city.twd97E), (int) Math.round(city.twd97N), city.cmZone);
      assertThat(r.isOk()).as("TWD97 must round-trip for %s", city.name).isTrue();
      Wgs84 fixOut = ((ParseResult.Ok) r).wgs84();
      double m = haversineMetres(fixIn, fixOut);
      assertThat(m)
          .as("TWD97 round-trip distance for %s", city.name)
          .isLessThanOrEqualTo(TOL_TWD97_M);
    }
  }

  // === TWD67 round-trip across all 22 cities ===
  private static final double TOL_TWD67_MAIN_M = 5.0;
  private static final double TOL_TWD67_OUTER_M = 20.0;

  @Test
  public void twd67_roundtrip_allCities() {
    for (City city : TaiwanCities.ALL) {
      Wgs84 fixIn =
          new Wgs84(city.latDeg, city.lonDeg, /*epochMs*/ 1_000L, Wgs84.Source.MAP_CENTRE);
      ParseResult r =
          parser.parseTwd67(
              (int) Math.round(city.twd67E), (int) Math.round(city.twd67N), city.cmZone);
      assertThat(r.isOk()).as("TWD67 must round-trip for %s", city.name).isTrue();
      Wgs84 fixOut = ((ParseResult.Ok) r).wgs84();
      double m = haversineMetres(fixIn, fixOut);
      double tol = city.isMainIsland ? TOL_TWD67_MAIN_M : TOL_TWD67_OUTER_M;
      assertThat(m)
          .as(
              "TWD67 round-trip distance for %s (%s)",
              city.name, city.isMainIsland ? "main" : "outer")
          .isLessThanOrEqualTo(tol);
    }
  }

  @Test
  public void taipower_outerIsland_inputsResolveToOutOfRange() {
    // Outer-island cities cannot be expressed in Taipower (main-island only). The forward path
    // returns OutOfRange; the inverse path is exercised by passing a SYNTHETIC main-island Taipower
    // string (which we already know works) and verifying the parser does NOT spuriously return Ok
    // for a code-shape that would, if decoded, fall outside the main-island TM2 zone-121 grid.
    //
    // Since every syntactically-valid Taipower code WILL decode to a main-island coordinate by
    // construction (the grid is bounded by ANCHOR_E_WEST/SOUTH + ROWS*REGION_HEIGHT), we only
    // verify that the forward conversion for outer-island cities returns OutOfRange (the existing
    // feature 001 behaviour the parser-side inherits via the Taiwan-box check).
    for (City city : TaiwanCities.ALL) {
      if (city.isMainIsland) continue;
      Wgs84 fixIn =
          new Wgs84(city.latDeg, city.lonDeg, /*epochMs*/ 1_000L, Wgs84.Source.MAP_CENTRE);
      ConversionResult forward = converter.convert(fixIn, CoordinateUnit.TAIPOWER);
      assertThat(forward.isOutOfRange())
          .as("forward Taipower for outer-island %s must be OutOfRange", city.name)
          .isTrue();
    }
  }

  // === helpers ===

  /**
   * Renders a {@link TaipowerCode} in the same `H7509 DB4016` canonical form the on-map widget uses
   * (matches {@link Formatter#formatTaipower(TaipowerCode)} exactly). Kept inline here so the
   * round-trip test has a stable rendering even if Formatter changes wording in the future.
   */
  private static String formatTaipower(TaipowerCode c) {
    if (!c.hasOneMetrePrecision()) {
      return String.format(
          Locale.ROOT,
          "%c%04d %c%c%d%d",
          c.region(),
          c.subRegion(),
          c.hundredMeterE(),
          c.hundredMeterN(),
          c.tenMeterE(),
          c.tenMeterN());
    }
    return String.format(
        Locale.ROOT,
        "%c%04d %c%c%d%d%d%d",
        c.region(),
        c.subRegion(),
        c.hundredMeterE(),
        c.hundredMeterN(),
        c.tenMeterE(),
        c.tenMeterN(),
        c.oneMeterE(),
        c.oneMeterN());
  }

  /** Haversine distance in metres between two WGS84 fixes. */
  private static double haversineMetres(Wgs84 a, Wgs84 b) {
    final double R = 6_371_000.0;
    double lat1 = Math.toRadians(a.latitudeDeg());
    double lat2 = Math.toRadians(b.latitudeDeg());
    double dLat = Math.toRadians(b.latitudeDeg() - a.latitudeDeg());
    double dLon = Math.toRadians(b.longitudeDeg() - a.longitudeDeg());
    double s =
        Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return 2 * R * Math.asin(Math.sqrt(s));
  }
}
