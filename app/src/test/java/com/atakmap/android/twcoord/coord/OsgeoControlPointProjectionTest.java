package com.atakmap.android.twcoord.coord;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.Test;

public class OsgeoControlPointProjectionTest {

  private static final Offset<Double> TWD97_TOLERANCE_M = Offset.offset(0.10);
  private static final Offset<Double> WGS84_TOLERANCE_DEG = Offset.offset(0.000_000_5);

  @Test
  public void fixture_has_stratified_main_and_outer_island_coverage() {
    assertThat(OsgeoControlPointVectors.ALL).hasSize(88);
    assertThat(OsgeoControlPointVectors.MAIN_ISLAND).hasSize(33);
    assertThat(OsgeoControlPointVectors.PENGHU).hasSize(42);
    assertThat(OsgeoControlPointVectors.KINMEN).hasSize(5);
    assertThat(OsgeoControlPointVectors.MATSU).hasSize(8);
  }

  @Test
  public void wgs84_to_twd97_matches_all_control_points() {
    for (OsgeoControlPointVectors.Point point : OsgeoControlPointVectors.ALL) {
      Wgs84 wgs84 = new Wgs84(point.latDeg, point.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
      Twd97Tm2 actual = Projections.wgs84ToTwd97(wgs84);

      assertThat(actual.zone()).as("%s zone", point.id).isEqualTo(point.zone);
      assertThat(actual.eastingMetres())
          .as("%s TWD97 easting", point.id)
          .isCloseTo(point.twd97E, TWD97_TOLERANCE_M);
      assertThat(actual.northingMetres())
          .as("%s TWD97 northing", point.id)
          .isCloseTo(point.twd97N, TWD97_TOLERANCE_M);
    }
  }

  @Test
  public void twd97_to_wgs84_matches_all_control_points() {
    for (OsgeoControlPointVectors.Point point : OsgeoControlPointVectors.ALL) {
      Twd97Tm2 t97 = new Twd97Tm2(point.twd97E, point.twd97N, point.zone);
      Wgs84 actual = Projections.twd97ToWgs84(t97, 1L);

      assertThat(actual.latitudeDeg())
          .as("%s latitude", point.id)
          .isCloseTo(point.latDeg, WGS84_TOLERANCE_DEG);
      assertThat(actual.longitudeDeg())
          .as("%s longitude", point.id)
          .isCloseTo(point.lonDeg, WGS84_TOLERANCE_DEG);
    }
  }

  @Test
  public void matsu_points_east_of_120_degrees_still_use_zone_119() {
    int checked = 0;
    for (OsgeoControlPointVectors.Point point : OsgeoControlPointVectors.MATSU) {
      if (point.lonDeg > 120.0) {
        assertThat(Projections.pickZoneForLocation(point.latDeg, point.lonDeg))
            .as("%s location-aware zone", point.id)
            .isEqualTo(119);
        checked++;
      }
    }
    assertThat(checked).isGreaterThan(0);
  }
}
