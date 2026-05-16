package com.atakmap.android.twpower.coord;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.Test;

public class DatumShiftTwd67Test {

  /**
   * Critical: pwa_map ADR 0004 warns that off-the-shelf proj4 EPSG:3828 omits the four-parameter
   * shift, silently producing a ~400 m error. This test exists to make that regression LOUD.
   */
  @Test
  public void twd97_forward_matches_golden_vectors_within_3m() {
    for (GoldenVectors.Point p : GoldenVectors.ALL) {
      Twd97Tm2 t97 = new Twd97Tm2(p.twd97E, p.twd97N, 121);
      Twd67Tm2 t67 = DatumShiftTwd67.twd97ToTwd67(t97);
      assertThat(t67.eastingMetres())
          .as("%s TWD67 easting", p.name)
          .isCloseTo(p.twd67E, Offset.offset(GoldenVectors.TOL_TWD67_M));
      assertThat(t67.northingMetres())
          .as("%s TWD67 northing", p.name)
          .isCloseTo(p.twd67N, Offset.offset(GoldenVectors.TOL_TWD67_M));
    }
  }

  @Test
  public void twd67_inverse_matches_golden_vectors_within_3m() {
    for (GoldenVectors.Point p : GoldenVectors.ALL) {
      Twd67Tm2 t67 = new Twd67Tm2(p.twd67E, p.twd67N);
      Twd97Tm2 t97 = DatumShiftTwd67.twd67ToTwd97(t67);
      assertThat(t97.eastingMetres())
          .as("%s TWD97 easting via inverse", p.name)
          .isCloseTo(p.twd97E, Offset.offset(GoldenVectors.TOL_TWD67_M));
      assertThat(t97.northingMetres())
          .as("%s TWD97 northing via inverse", p.name)
          .isCloseTo(p.twd97N, Offset.offset(GoldenVectors.TOL_TWD67_M));
    }
  }

  @Test
  public void forward_then_inverse_is_identity_within_centimetre() {
    Twd97Tm2 t97 =
        new Twd97Tm2(GoldenVectors.TAIPEI_101.twd97E, GoldenVectors.TAIPEI_101.twd97N, 121);
    Twd67Tm2 t67 = DatumShiftTwd67.twd97ToTwd67(t97);
    Twd97Tm2 back = DatumShiftTwd67.twd67ToTwd97(t67);
    assertThat(back.eastingMetres()).isCloseTo(t97.eastingMetres(), Offset.offset(0.05));
    assertThat(back.northingMetres()).isCloseTo(t97.northingMetres(), Offset.offset(0.05));
  }
}
