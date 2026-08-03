package com.atakmap.android.twcoord.coord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/** Regression coverage for the smooth Penghu regional-model transition and iterative inverse. */
public class DatumShiftTwd67TransitionTest {

  private static final double EDGE_PROBE_M = 0.001;
  private static final double EDGE_OUTPUT_LIMIT_M = 0.01;
  private static final double ROUND_TRIP_TOLERANCE_M = 0.000_001;

  @Test
  public void transition_edges_are_continuous() {
    double[] eastingEdges = {270_000.0, 280_000.0, 325_000.0, 340_000.0};
    for (double edge : eastingEdges) {
      Twd67Tm2 before = forward(edge - EDGE_PROBE_M, 2_600_000.0);
      Twd67Tm2 after = forward(edge + EDGE_PROBE_M, 2_600_000.0);
      assertThat(distance(before, after))
          .as("easting transition at %.3f m", edge)
          .isLessThanOrEqualTo(EDGE_OUTPUT_LIMIT_M);
    }

    double[] northingEdges = {2_550_000.0, 2_565_000.0, 2_625_000.0, 2_650_000.0};
    for (double edge : northingEdges) {
      Twd67Tm2 before = forward(300_000.0, edge - EDGE_PROBE_M);
      Twd67Tm2 after = forward(300_000.0, edge + EDGE_PROBE_M);
      assertThat(distance(before, after))
          .as("northing transition at %.3f m", edge)
          .isLessThanOrEqualTo(EDGE_OUTPUT_LIMIT_M);
    }
  }

  @Test
  public void forward_and_inverse_round_trip_across_dense_transition_grid() {
    double maximumTwd97Error = 0.0;
    double maximumTwd67Error = 0.0;
    int checked = 0;

    for (double easting = 250_000.0; easting <= 360_000.0; easting += 2_500.0) {
      for (double northing = 2_520_000.0;
          northing <= 2_680_000.0;
          northing += 2_500.0) {
        Twd97Tm2 source = new Twd97Tm2(easting, northing, 119);
        Twd67Tm2 t67 = DatumShiftTwd67.twd97ToTwd67(source);
        Twd97Tm2 backTo97 = DatumShiftTwd67.twd67ToTwd97(t67);
        Twd67Tm2 backTo67 = DatumShiftTwd67.twd97ToTwd67(backTo97);

        maximumTwd97Error = Math.max(maximumTwd97Error, distance(source, backTo97));
        maximumTwd67Error = Math.max(maximumTwd67Error, distance(t67, backTo67));
        checked++;
      }
    }

    assertThat(checked).isEqualTo(2_925);
    assertThat(maximumTwd97Error).isLessThanOrEqualTo(ROUND_TRIP_TOLERANCE_M);
    assertThat(maximumTwd67Error).isLessThanOrEqualTo(ROUND_TRIP_TOLERANCE_M);
  }

  @Test
  public void arbitrary_twd67_values_round_trip_through_iterative_inverse() {
    double maximumError = 0.0;
    int checked = 0;

    for (double easting = 249_000.0; easting <= 359_000.0; easting += 2_500.0) {
      for (double northing = 2_520_000.0;
          northing <= 2_680_000.0;
          northing += 2_500.0) {
        Twd67Tm2 source = new Twd67Tm2(easting, northing, 119);
        Twd97Tm2 t97 = DatumShiftTwd67.twd67ToTwd97(source);
        Twd67Tm2 actual = DatumShiftTwd67.twd97ToTwd67(t97);

        assertThat(Double.isFinite(t97.eastingMetres()))
            .as("finite inverse easting at %.3f, %.3f", easting, northing)
            .isTrue();
        assertThat(Double.isFinite(t97.northingMetres()))
            .as("finite inverse northing at %.3f, %.3f", easting, northing)
            .isTrue();
        maximumError = Math.max(maximumError, distance(source, actual));
        checked++;
      }
    }

    assertThat(checked).isEqualTo(2_925);
    assertThat(maximumError).isLessThanOrEqualTo(ROUND_TRIP_TOLERANCE_M);
  }

  private static Twd67Tm2 forward(double easting, double northing) {
    return DatumShiftTwd67.twd97ToTwd67(new Twd97Tm2(easting, northing, 119));
  }

  private static double distance(Twd97Tm2 first, Twd97Tm2 second) {
    return Math.hypot(
        first.eastingMetres() - second.eastingMetres(),
        first.northingMetres() - second.northingMetres());
  }

  private static double distance(Twd67Tm2 first, Twd67Tm2 second) {
    return Math.hypot(
        first.eastingMetres() - second.eastingMetres(),
        first.northingMetres() - second.northingMetres());
  }
}
