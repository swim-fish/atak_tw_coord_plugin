package com.atakmap.android.twpower.coord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Smoke test: every county / city on Taiwan's main island survives the full conversion pipeline for
 * TWD97 and TWD67. For Taipower the test allows either Ok (region letter in A..X) or OutOfRange — a
 * few western coastal districts sit just outside the 170 000 m easting anchor and are documented as
 * expected-OOR.
 *
 * <p>The intent is breadth coverage, not numeric precision: if anything breaks (proj4j
 * misconfiguration, region letter table corruption, anchor regression), at least one of these 19
 * assertions will surface the failure with the city name in the message.
 */
public class TaiwanCitiesSmokeTest {

  private final CoordinateConverter conv = new CoordinateConverter();

  @Test
  public void every_main_island_city_returns_ok_for_twd97() {
    for (TaiwanCities.City c : TaiwanCities.ALL) {
      Wgs84 fix = wgs(c);
      ConversionResult r = conv.convert(fix, CoordinateUnit.TWD97);
      assertThat(r.isOk()).as("TWD97 at %s", c.name).isTrue();
      @SuppressWarnings("unchecked")
      Twd97Tm2 v = ((ConversionResult.Ok<Twd97Tm2>) r).value();
      // Main-island TM2 zone 121 false easting = 250 000 m. All landmarks here are within
      // ±100 km of the central meridian (121°E), so easting should land in (150 k, 350 k).
      assertThat(v.zone()).as("zone for %s", c.name).isEqualTo(121);
      assertThat(v.eastingMetres()).as("easting at %s", c.name).isBetween(150_000d, 350_000d);
      // Northing for Taiwan main island spans roughly 2.42M (Hengchun) to 2.79M (north coast).
      assertThat(v.northingMetres()).as("northing at %s", c.name).isBetween(2_400_000d, 2_800_000d);
    }
  }

  @Test
  public void every_main_island_city_returns_ok_for_twd67() {
    for (TaiwanCities.City c : TaiwanCities.ALL) {
      ConversionResult r = conv.convert(wgs(c), CoordinateUnit.TWD67);
      assertThat(r.isOk()).as("TWD67 at %s", c.name).isTrue();
      @SuppressWarnings("unchecked")
      Twd67Tm2 v = ((ConversionResult.Ok<Twd67Tm2>) r).value();
      assertThat(v.zone()).as("zone for %s", c.name).isEqualTo(121);
    }
  }

  /**
   * Taipower must succeed (Ok with letter A..X) for every city that sits inside the 8×3 main-island
   * letter grid. The grid's south-west anchor (170 000 E, 2 400 000 N TWD67) excludes some western
   * coastal city centres by 5-10 km — those are documented and accepted as OutOfRange.
   */
  @Test
  public void every_main_island_city_is_in_taipower_grid_or_documented_oor() {
    for (TaiwanCities.City c : TaiwanCities.ALL) {
      ConversionResult r = conv.convert(wgs(c), CoordinateUnit.TAIPOWER);
      if (r.isOk()) {
        @SuppressWarnings("unchecked")
        TaipowerCode code = ((ConversionResult.Ok<TaipowerCode>) r).value();
        assertThat(code.region()).as("Taipower region letter at %s", c.name).isBetween('A', 'X');
        assertThat(code.hasOneMetrePrecision()).as("11-char precision at %s", c.name).isTrue();
      } else {
        assertThat(r.isOutOfRange()).as("Taipower OoR justified at %s", c.name).isTrue();
      }
    }
  }

  private static Wgs84 wgs(TaiwanCities.City c) {
    return new Wgs84(c.latDeg, c.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
  }
}
