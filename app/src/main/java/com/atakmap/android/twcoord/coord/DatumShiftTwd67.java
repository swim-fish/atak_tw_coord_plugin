package com.atakmap.android.twcoord.coord;

/**
 * Datum shift between TWD97 and TWD67 in TM2 coordinates.
 *
 * <p>The main-island path preserves the established four-parameter conformal transform from
 * ADR-0001, but uses the exact inverse matrix instead of the former first-order approximation. The
 * Penghu core uses a regional two-dimensional similarity transform fitted to 42 published
 * TWD97/TWD67 common points. A smooth transition to the compatibility model avoids a discontinuity
 * and inverse-model ambiguity at an artificial rectangular boundary. The transition band is an
 * engineering continuity measure, not an accuracy claim outside the calibrated Penghu core.
 */
public final class DatumShiftTwd67 {

  private static final double DELTA_X = 807.8;
  private static final double DELTA_Y = 248.6;
  private static final double A = 0.000_015_49;
  private static final double B = 0.000_006_521;
  private static final double C = 1.0 - A;
  private static final double MAIN_DETERMINANT = C * C - B * B;

  // Least-squares two-dimensional similarity transform fitted to the 42 Penghu common points in
  // app/src/test/resources/coord/osgeo-taiwan-control-points.csv.
  //
  //   E67 = TX + P * E97 - Q * N97
  //   N67 = TY + Q * E97 + P * N97
  //
  // Full-set residuals: mean 0.125 m, RMS 0.150 m, maximum 0.504 m.
  private static final double PENGHU_TX = -502.543_492_499;
  private static final double PENGHU_TY = 161.813_279_315;
  private static final double PENGHU_P = 0.999_998_583_003;
  private static final double PENGHU_Q = 0.000_124_634_365;

  // All 42 observed Penghu controls lie inside this core and therefore receive the unblended
  // regional transform. Between the core and outer bounds, a cubic smoothstep tapers the regional
  // correction to zero. The zero-slope endpoints preserve first-derivative continuity.
  private static final double PENGHU_OUTER_E_MIN = 270_000.0;
  private static final double PENGHU_CORE_E_MIN = 280_000.0;
  private static final double PENGHU_CORE_E_MAX = 325_000.0;
  private static final double PENGHU_OUTER_E_MAX = 340_000.0;
  private static final double PENGHU_OUTER_N_MIN = 2_550_000.0;
  private static final double PENGHU_CORE_N_MIN = 2_565_000.0;
  private static final double PENGHU_CORE_N_MAX = 2_625_000.0;
  private static final double PENGHU_OUTER_N_MAX = 2_650_000.0;

  // The blended forward mapping is close to the compatibility affine transform. Fixed-point
  // inversion converges in at most five iterations over a broad zone-119 stress grid; retain a
  // generous deterministic cap and sub-nanometre iteration threshold.
  private static final int INVERSE_MAX_ITERATIONS = 12;
  private static final double INVERSE_CONVERGENCE_M = 0.000_000_001;

  private DatumShiftTwd67() {}

  public static Twd67Tm2 twd97ToTwd67(Twd97Tm2 t97) {
    double e97 = t97.eastingMetres();
    double n97 = t97.northingMetres();
    double mainE67 = mainForwardE(e97, n97);
    double mainN67 = mainForwardN(e97, n97);
    double weight = penghuWeight(t97.zone(), e97, n97);
    if (weight == 0.0) {
      return new Twd67Tm2(mainE67, mainN67, t97.zone());
    }

    double regionalE67 = penghuForwardE(e97, n97);
    double regionalN67 = penghuForwardN(e97, n97);
    if (weight == 1.0) {
      return new Twd67Tm2(regionalE67, regionalN67, t97.zone());
    }

    double e67 = mainE67 + weight * (regionalE67 - mainE67);
    double n67 = mainN67 + weight * (regionalN67 - mainN67);
    return new Twd67Tm2(e67, n67, t97.zone());
  }

  public static Twd97Tm2 twd67ToTwd97(Twd67Tm2 t67) {
    double e67 = t67.eastingMetres();
    double n67 = t67.northingMetres();
    double candidateE97 = mainInverseE(e67, n67);
    double candidateN97 = mainInverseN(e67, n67);

    if (t67.zone() != 119) {
      return new Twd97Tm2(candidateE97, candidateN97, t67.zone());
    }

    // Solve y = M_main(x) + w(x) * (M_penghu(x) - M_main(x)). Holding w and the
    // correction at the current candidate turns each step into the exact main-model inverse. The
    // correction gradient is small relative to the 10-25 km transition widths, so this is a strong
    // contraction without a finite-difference or analytic Jacobian.
    for (int iteration = 0; iteration < INVERSE_MAX_ITERATIONS; iteration++) {
      double weight = penghuWeight(t67.zone(), candidateE97, candidateN97);
      double correctionE =
          penghuForwardE(candidateE97, candidateN97)
              - mainForwardE(candidateE97, candidateN97);
      double correctionN =
          penghuForwardN(candidateE97, candidateN97)
              - mainForwardN(candidateE97, candidateN97);
      double nextE97 = mainInverseE(e67 - weight * correctionE, n67 - weight * correctionN);
      double nextN97 = mainInverseN(e67 - weight * correctionE, n67 - weight * correctionN);

      if (Math.hypot(nextE97 - candidateE97, nextN97 - candidateN97)
          <= INVERSE_CONVERGENCE_M) {
        candidateE97 = nextE97;
        candidateN97 = nextN97;
        break;
      }
      candidateE97 = nextE97;
      candidateN97 = nextN97;
    }

    return new Twd97Tm2(candidateE97, candidateN97, t67.zone());
  }

  private static double mainForwardE(double e97, double n97) {
    return C * e97 - B * n97 - DELTA_X;
  }

  private static double mainForwardN(double e97, double n97) {
    return -B * e97 + C * n97 + DELTA_Y;
  }

  private static double mainInverseE(double e67, double n67) {
    double shiftedE = e67 + DELTA_X;
    double shiftedN = n67 - DELTA_Y;
    return (C * shiftedE + B * shiftedN) / MAIN_DETERMINANT;
  }

  private static double mainInverseN(double e67, double n67) {
    double shiftedE = e67 + DELTA_X;
    double shiftedN = n67 - DELTA_Y;
    return (B * shiftedE + C * shiftedN) / MAIN_DETERMINANT;
  }

  private static double penghuForwardE(double e97, double n97) {
    return PENGHU_TX + PENGHU_P * e97 - PENGHU_Q * n97;
  }

  private static double penghuForwardN(double e97, double n97) {
    return PENGHU_TY + PENGHU_Q * e97 + PENGHU_P * n97;
  }

  private static double penghuWeight(int zone, double e97, double n97) {
    if (zone != 119) {
      return 0.0;
    }
    double eastingWeight =
        smoothWindow(
            e97,
            PENGHU_OUTER_E_MIN,
            PENGHU_CORE_E_MIN,
            PENGHU_CORE_E_MAX,
            PENGHU_OUTER_E_MAX);
    double northingWeight =
        smoothWindow(
            n97,
            PENGHU_OUTER_N_MIN,
            PENGHU_CORE_N_MIN,
            PENGHU_CORE_N_MAX,
            PENGHU_OUTER_N_MAX);
    return eastingWeight * northingWeight;
  }

  private static double smoothWindow(
      double value, double outerMin, double coreMin, double coreMax, double outerMax) {
    if (value <= outerMin || value >= outerMax) {
      return 0.0;
    }
    if (value < coreMin) {
      return smoothstep((value - outerMin) / (coreMin - outerMin));
    }
    if (value <= coreMax) {
      return 1.0;
    }
    return smoothstep((outerMax - value) / (outerMax - coreMax));
  }

  private static double smoothstep(double value) {
    return value * value * (3.0 - 2.0 * value);
  }
}
