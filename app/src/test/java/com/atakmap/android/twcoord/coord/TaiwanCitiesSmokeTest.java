package com.atakmap.android.twcoord.coord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Breadth smoke test on top of the value-pinned {@link TaiwanCitiesAuthoritativeTest}: this one
 * focuses on zone selection (main-island → 121, outer-islands → 119) and the Taipower grid status
 * across the 22 cities. Taipower is main-island-only by design (ADR-0001), so outer-island cities
 * MUST return OutOfRange and main-island cities MUST either return Ok with a valid region letter OR
 * a documented OutOfRange (city seats that land in a blank cell of the 8×4 letter table — I
 * underwater, S = Matsu offshore — are not codable in v1).
 */
public class TaiwanCitiesSmokeTest {

  private final CoordinateConverter conv = new CoordinateConverter();

  @Test
  public void main_island_cities_use_zone_121_outer_islands_use_zone_119() {
    for (TaiwanCities.City c : TaiwanCities.ALL) {
      Wgs84 fix = new Wgs84(c.latDeg, c.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
      ConversionResult r = conv.convert(fix, CoordinateUnit.TWD97);
      assertThat(r.isOk()).as("TWD97 Ok at %s", c.name).isTrue();
      @SuppressWarnings("unchecked")
      Twd97Tm2 v = ((ConversionResult.Ok<Twd97Tm2>) r).value();
      int expected = c.isMainIsland ? 121 : 119;
      assertThat(v.zone()).as("zone at %s", c.name).isEqualTo(expected);
    }
  }

  @Test
  public void outer_island_cities_are_out_of_range_for_taipower() {
    for (TaiwanCities.City c : TaiwanCities.ALL) {
      if (c.isMainIsland) continue;
      Wgs84 fix = new Wgs84(c.latDeg, c.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
      ConversionResult r = conv.convert(fix, CoordinateUnit.TAIPOWER);
      assertThat(r.isOutOfRange())
          .as("Taipower MUST be OutOfRange at outer-island %s", c.name)
          .isTrue();
    }
  }

  @Test
  public void main_island_cities_in_taipower_grid_or_documented_oor() {
    for (TaiwanCities.City c : TaiwanCities.ALL) {
      if (!c.isMainIsland) continue;
      Wgs84 fix = new Wgs84(c.latDeg, c.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
      ConversionResult r = conv.convert(fix, CoordinateUnit.TAIPOWER);
      if (r.isOk()) {
        @SuppressWarnings("unchecked")
        TaipowerCode code = ((ConversionResult.Ok<TaipowerCode>) r).value();
        assertThat(code.region()).as("Taipower region letter at %s", c.name).isBetween('A', 'X');
        assertThat(code.hasOneMetrePrecision()).as("11-char precision at %s", c.name).isTrue();
      } else {
        assertThat(r.isOutOfRange()).as("Taipower OoR at %s", c.name).isTrue();
      }
    }
  }
}
