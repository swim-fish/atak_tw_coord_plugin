package com.atakmap.android.twpower.coord;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.Test;

/**
 * Value-pinned test: every county / city in {@link TaiwanCities} must round-trip through our {@link
 * Projections} / {@link DatumShiftTwd67} pipeline to within the published TWD97 / TWD67 tolerances
 * when compared against the CSV-supplied authoritative values (cross-referenced with NCKU 歷史所 GIS).
 */
public class TaiwanCitiesAuthoritativeTest {

  private final CoordinateConverter conv = new CoordinateConverter();

  @Test
  public void twd97_matches_csv_for_every_city() {
    for (TaiwanCities.City c : TaiwanCities.ALL) {
      Wgs84 fix = new Wgs84(c.latDeg, c.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
      ConversionResult r = conv.convert(fix, CoordinateUnit.TWD97);
      assertThat(r.isOk()).as("TWD97 Ok at %s", c.name).isTrue();
      @SuppressWarnings("unchecked")
      Twd97Tm2 v = ((ConversionResult.Ok<Twd97Tm2>) r).value();
      assertThat(v.zone()).as("zone at %s", c.name).isEqualTo(c.cmZone);
      assertThat(v.eastingMetres())
          .as("TWD97 easting at %s", c.name)
          .isCloseTo(c.twd97E, Offset.offset(TaiwanCities.TOL_TWD97_M));
      assertThat(v.northingMetres())
          .as("TWD97 northing at %s", c.name)
          .isCloseTo(c.twd97N, Offset.offset(TaiwanCities.TOL_TWD97_M));
    }
  }

  @Test
  public void twd67_matches_csv_for_every_city() {
    for (TaiwanCities.City c : TaiwanCities.ALL) {
      Wgs84 fix = new Wgs84(c.latDeg, c.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
      ConversionResult r = conv.convert(fix, CoordinateUnit.TWD67);
      assertThat(r.isOk()).as("TWD67 Ok at %s", c.name).isTrue();
      @SuppressWarnings("unchecked")
      Twd67Tm2 v = ((ConversionResult.Ok<Twd67Tm2>) r).value();
      assertThat(v.zone()).as("zone at %s", c.name).isEqualTo(c.cmZone);
      double tolerance =
          c.isMainIsland ? TaiwanCities.TOL_TWD67_MAIN_M : TaiwanCities.TOL_TWD67_OUTER_M;
      assertThat(v.eastingMetres())
          .as("TWD67 easting at %s", c.name)
          .isCloseTo(c.twd67E, Offset.offset(tolerance));
      assertThat(v.northingMetres())
          .as("TWD67 northing at %s", c.name)
          .isCloseTo(c.twd67N, Offset.offset(tolerance));
    }
  }
}
