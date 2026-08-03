package com.atakmap.android.twcoord.coord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.Test;

public class DatumShiftTwd67Test {

  /**
   * Critical: using only the TWD67 ellipsoid without a datum shift silently produces a roughly
   * kilometre-scale error. Keep the original pinned vectors as a compatibility gate.
   */
  @Test
  public void twd97_forward_matches_golden_vectors_within_3m() {
    for (GoldenVectors.Point point : GoldenVectors.ALL) {
      Twd97Tm2 t97 = new Twd97Tm2(point.twd97E, point.twd97N, 121);
      Twd67Tm2 t67 = DatumShiftTwd67.twd97ToTwd67(t97);
      assertThat(t67.eastingMetres())
          .as("%s TWD67 easting", point.name)
          .isCloseTo(point.twd67E, Offset.offset(GoldenVectors.TOL_TWD67_M));
      assertThat(t67.northingMetres())
          .as("%s TWD67 northing", point.name)
          .isCloseTo(point.twd67N, Offset.offset(GoldenVectors.TOL_TWD67_M));
    }
  }

  @Test
  public void twd67_inverse_matches_golden_vectors_within_3m() {
    for (GoldenVectors.Point point : GoldenVectors.ALL) {
      Twd67Tm2 t67 = new Twd67Tm2(point.twd67E, point.twd67N);
      Twd97Tm2 t97 = DatumShiftTwd67.twd67ToTwd97(t67);
      assertThat(t97.eastingMetres())
          .as("%s TWD97 easting via inverse", point.name)
          .isCloseTo(point.twd97E, Offset.offset(GoldenVectors.TOL_TWD67_M));
      assertThat(t97.northingMetres())
          .as("%s TWD97 northing via inverse", point.name)
          .isCloseTo(point.twd97N, Offset.offset(GoldenVectors.TOL_TWD67_M));
    }
  }

  @Test
  public void main_island_transform_matches_observed_controls_within_2m() {
    assertThat(OsgeoControlPointVectors.MAIN_ISLAND).hasSize(33);
    for (OsgeoControlPointVectors.Point point : OsgeoControlPointVectors.MAIN_ISLAND) {
      assertThat(point.twd67Observed).as("%s fixture provenance", point.id).isTrue();

      Twd67Tm2 forward =
          DatumShiftTwd67.twd97ToTwd67(
              new Twd97Tm2(point.twd97E, point.twd97N, point.zone));
      assertThat(radialError(forward, point))
          .as("%s TWD97 to TWD67 radial error", point.id)
          .isLessThanOrEqualTo(2.0);

      Twd97Tm2 inverse =
          DatumShiftTwd67.twd67ToTwd97(
              new Twd67Tm2(point.twd67E, point.twd67N, point.zone));
      assertThat(radialError(inverse, point))
          .as("%s TWD67 to TWD97 radial error", point.id)
          .isLessThanOrEqualTo(2.0);
    }
  }

  @Test
  public void penghu_regional_transform_matches_42_common_points_below_55cm() {
    double forwardTotal = 0.0;
    double inverseTotal = 0.0;
    double forwardMax = 0.0;
    double inverseMax = 0.0;

    for (OsgeoControlPointVectors.Point point : OsgeoControlPointVectors.PENGHU) {
      assertThat(point.twd67Observed).as("%s fixture provenance", point.id).isTrue();

      Twd67Tm2 forward =
          DatumShiftTwd67.twd97ToTwd67(
              new Twd97Tm2(point.twd97E, point.twd97N, point.zone));
      double forwardError = radialError(forward, point);
      forwardTotal += forwardError;
      forwardMax = Math.max(forwardMax, forwardError);

      Twd97Tm2 inverse =
          DatumShiftTwd67.twd67ToTwd97(
              new Twd67Tm2(point.twd67E, point.twd67N, point.zone));
      double inverseError = radialError(inverse, point);
      inverseTotal += inverseError;
      inverseMax = Math.max(inverseMax, inverseError);
    }

    assertThat(OsgeoControlPointVectors.PENGHU).hasSize(42);
    assertThat(forwardMax).isLessThanOrEqualTo(0.55);
    assertThat(inverseMax).isLessThanOrEqualTo(0.55);
    assertThat(forwardTotal / OsgeoControlPointVectors.PENGHU.size())
        .isLessThanOrEqualTo(0.16);
    assertThat(inverseTotal / OsgeoControlPointVectors.PENGHU.size())
        .isLessThanOrEqualTo(0.16);
  }

  @Test
  public void all_penghu_controls_receive_the_unblended_regional_model() {
    for (OsgeoControlPointVectors.Point point : OsgeoControlPointVectors.PENGHU) {
      Twd67Tm2 actual =
          DatumShiftTwd67.twd97ToTwd67(
              new Twd97Tm2(point.twd97E, point.twd97N, point.zone));
      double expectedE =
          -502.543_492_499
              + 0.999_998_583_003 * point.twd97E
              - 0.000_124_634_365 * point.twd97N;
      double expectedN =
          161.813_279_315
              + 0.000_124_634_365 * point.twd97E
              + 0.999_998_583_003 * point.twd97N;

      assertThat(actual.eastingMetres())
          .as("%s full Penghu model easting", point.id)
          .isCloseTo(expectedE, Offset.offset(0.000_001));
      assertThat(actual.northingMetres())
          .as("%s full Penghu model northing", point.id)
          .isCloseTo(expectedN, Offset.offset(0.000_001));
    }
  }

  @Test
  public void penghu_similarity_model_family_passes_leave_one_out_validation() {
    double totalError = 0.0;
    double maxError = 0.0;
    int count = OsgeoControlPointVectors.PENGHU.size();

    for (int heldOut = 0; heldOut < count; heldOut++) {
      SimilarityFit fit = fitSimilarityExcluding(OsgeoControlPointVectors.PENGHU, heldOut);
      OsgeoControlPointVectors.Point point = OsgeoControlPointVectors.PENGHU.get(heldOut);
      double predictedE = fit.tx + fit.p * point.twd97E - fit.q * point.twd97N;
      double predictedN = fit.ty + fit.q * point.twd97E + fit.p * point.twd97N;
      double error = Math.hypot(predictedE - point.twd67E, predictedN - point.twd67N);
      totalError += error;
      maxError = Math.max(maxError, error);
    }

    assertThat(count).isEqualTo(42);
    assertThat(totalError / count).isLessThanOrEqualTo(0.18);
    assertThat(maxError).isLessThanOrEqualTo(0.65);
  }

  @Test
  public void penghu_transition_is_continuous_at_outer_and_core_edges() {
    double delta = 0.001;
    double[] eastingEdges = {270_000.0, 280_000.0, 325_000.0, 340_000.0};
    for (double edge : eastingEdges) {
      Twd67Tm2 before =
          DatumShiftTwd67.twd97ToTwd67(new Twd97Tm2(edge - delta, 2_600_000.0, 119));
      Twd67Tm2 after =
          DatumShiftTwd67.twd97ToTwd67(new Twd97Tm2(edge + delta, 2_600_000.0, 119));
      assertThat(distance(before, after))
          .as("continuity across Penghu easting edge %.3f", edge)
          .isLessThan(0.01);
    }

    double[] northingEdges = {2_550_000.0, 2_565_000.0, 2_625_000.0, 2_650_000.0};
    for (double edge : northingEdges) {
      Twd67Tm2 before =
          DatumShiftTwd67.twd97ToTwd67(new Twd97Tm2(300_000.0, edge - delta, 119));
      Twd67Tm2 after =
          DatumShiftTwd67.twd97ToTwd67(new Twd97Tm2(300_000.0, edge + delta, 119));
      assertThat(distance(before, after))
          .as("continuity across Penghu northing edge %.3f", edge)
          .isLessThan(0.01);
    }
  }

  @Test
  public void penghu_transition_grid_is_invertible_in_both_directions() {
    double[] eastings = {
      265_000.0,
      269_999.999,
      270_000.0,
      270_000.001,
      275_000.0,
      279_999.999,
      280_000.0,
      280_000.001,
      300_000.0,
      324_999.999,
      325_000.0,
      325_000.001,
      332_500.0,
      339_999.999,
      340_000.0,
      340_000.001,
      345_000.0
    };
    double[] northings = {
      2_545_000.0,
      2_549_999.999,
      2_550_000.0,
      2_550_000.001,
      2_557_500.0,
      2_564_999.999,
      2_565_000.0,
      2_565_000.001,
      2_600_000.0,
      2_624_999.999,
      2_625_000.0,
      2_625_000.001,
      2_637_500.0,
      2_649_999.999,
      2_650_000.0,
      2_650_000.001,
      2_655_000.0
    };

    for (double easting : eastings) {
      for (double northing : northings) {
        Twd97Tm2 source97 = new Twd97Tm2(easting, northing, 119);
        Twd67Tm2 converted67 = DatumShiftTwd67.twd97ToTwd67(source97);
        Twd97Tm2 back97 = DatumShiftTwd67.twd67ToTwd97(converted67);
        assertThat(Math.hypot(back97.eastingMetres() - easting, back97.northingMetres() - northing))
            .as("TWD97 round trip at %.3f, %.3f", easting, northing)
            .isLessThanOrEqualTo(0.000_001);

        Twd67Tm2 source67 = new Twd67Tm2(easting, northing, 119);
        Twd97Tm2 converted97 = DatumShiftTwd67.twd67ToTwd97(source67);
        Twd67Tm2 back67 = DatumShiftTwd67.twd97ToTwd67(converted97);
        assertThat(
                Math.hypot(
                    back67.eastingMetres() - easting, back67.northingMetres() - northing))
            .as("TWD67 round trip at %.3f, %.3f", easting, northing)
            .isLessThanOrEqualTo(0.000_001);
      }
    }
  }

  @Test
  public void penghu_regional_model_does_not_capture_kinmen_or_matsu() {
    for (List<OsgeoControlPointVectors.Point> region :
        List.of(OsgeoControlPointVectors.KINMEN, OsgeoControlPointVectors.MATSU)) {
      for (OsgeoControlPointVectors.Point point : region) {
        Twd67Tm2 actual =
            DatumShiftTwd67.twd97ToTwd67(
                new Twd97Tm2(point.twd97E, point.twd97N, point.zone));
        double expectedE =
            point.twd97E
                - 807.8
                - 0.000_015_49 * point.twd97E
                - 0.000_006_521 * point.twd97N;
        double expectedN =
            point.twd97N
                + 248.6
                - 0.000_015_49 * point.twd97N
                - 0.000_006_521 * point.twd97E;

        assertThat(actual.eastingMetres())
            .as("%s must use compatibility fallback easting", point.id)
            .isCloseTo(expectedE, Offset.offset(0.000_001));
        assertThat(actual.northingMetres())
            .as("%s must use compatibility fallback northing", point.id)
            .isCloseTo(expectedN, Offset.offset(0.000_001));
      }
    }
  }

  @Test
  public void forward_then_inverse_is_identity_to_micrometre_scale_for_all_regions() {
    for (OsgeoControlPointVectors.Point point : OsgeoControlPointVectors.ALL) {
      Twd97Tm2 source = new Twd97Tm2(point.twd97E, point.twd97N, point.zone);
      Twd67Tm2 t67 = DatumShiftTwd67.twd97ToTwd67(source);
      Twd97Tm2 actual = DatumShiftTwd67.twd67ToTwd97(t67);

      assertThat(actual.zone()).as("%s round-trip zone", point.id).isEqualTo(point.zone);
      assertThat(actual.eastingMetres())
          .as("%s round-trip easting", point.id)
          .isCloseTo(source.eastingMetres(), Offset.offset(0.000_001));
      assertThat(actual.northingMetres())
          .as("%s round-trip northing", point.id)
          .isCloseTo(source.northingMetres(), Offset.offset(0.000_001));
    }
  }

  private static SimilarityFit fitSimilarityExcluding(
      List<OsgeoControlPointVectors.Point> points, int excludedIndex) {
    double mean97E = 0.0;
    double mean97N = 0.0;
    double mean67E = 0.0;
    double mean67N = 0.0;
    int count = points.size() - 1;

    for (int i = 0; i < points.size(); i++) {
      if (i == excludedIndex) continue;
      OsgeoControlPointVectors.Point point = points.get(i);
      mean97E += point.twd97E;
      mean97N += point.twd97N;
      mean67E += point.twd67E;
      mean67N += point.twd67N;
    }
    mean97E /= count;
    mean97N /= count;
    mean67E /= count;
    mean67N /= count;

    double pNumerator = 0.0;
    double qNumerator = 0.0;
    double denominator = 0.0;
    for (int i = 0; i < points.size(); i++) {
      if (i == excludedIndex) continue;
      OsgeoControlPointVectors.Point point = points.get(i);
      double x = point.twd97E - mean97E;
      double y = point.twd97N - mean97N;
      double targetX = point.twd67E - mean67E;
      double targetY = point.twd67N - mean67N;
      pNumerator += x * targetX + y * targetY;
      qNumerator += x * targetY - y * targetX;
      denominator += x * x + y * y;
    }

    double p = pNumerator / denominator;
    double q = qNumerator / denominator;
    double tx = mean67E - p * mean97E + q * mean97N;
    double ty = mean67N - q * mean97E - p * mean97N;
    return new SimilarityFit(tx, ty, p, q);
  }

  private static final class SimilarityFit {
    private final double tx;
    private final double ty;
    private final double p;
    private final double q;

    private SimilarityFit(double tx, double ty, double p, double q) {
      this.tx = tx;
      this.ty = ty;
      this.p = p;
      this.q = q;
    }
  }

  private static double radialError(
      Twd67Tm2 actual, OsgeoControlPointVectors.Point expected) {
    return Math.hypot(
        actual.eastingMetres() - expected.twd67E,
        actual.northingMetres() - expected.twd67N);
  }

  private static double radialError(
      Twd97Tm2 actual, OsgeoControlPointVectors.Point expected) {
    return Math.hypot(
        actual.eastingMetres() - expected.twd97E,
        actual.northingMetres() - expected.twd97N);
  }

  private static double distance(Twd67Tm2 first, Twd67Tm2 second) {
    return Math.hypot(
        second.eastingMetres() - first.eastingMetres(),
        second.northingMetres() - first.northingMetres());
  }
}
