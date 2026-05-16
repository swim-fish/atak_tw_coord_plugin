package com.atakmap.android.twcoord.coord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class ProjectionsTest {

  @Test
  public void wgs84_to_twd97_matches_pwa_map_golden_vectors() {
    for (GoldenVectors.Point p : GoldenVectors.ALL) {
      Wgs84 in = new Wgs84(p.latDeg, p.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
      Twd97Tm2 out = Projections.wgs84ToTwd97(in);
      assertThat(out.zone()).isEqualTo(121);
      assertThat(out.eastingMetres())
          .as("%s easting", p.name)
          .isCloseTo(p.twd97E, withinM(GoldenVectors.TOL_TWD97_M));
      assertThat(out.northingMetres())
          .as("%s northing", p.name)
          .isCloseTo(p.twd97N, withinM(GoldenVectors.TOL_TWD97_M));
    }
  }

  private static org.assertj.core.data.Offset<Double> withinM(double m) {
    return org.assertj.core.data.Offset.offset(m);
  }
}
