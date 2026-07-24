package com.atakmap.android.twcoord.coord;

/**
 * Datum shift between TWD97 and TWD67 in TM2 coordinates.
 *
 * <p>The main-island path preserves the established four-parameter conformal transform from
 * ADR-0001, but uses the exact inverse matrix instead of the former first-order approximation. The
 * Penghu path uses a regional two-dimensional similarity transform fitted to 42 published
 * TWD97/TWD67 common points. Outside those calibrated areas, the legacy four-parameter model is
 * retained for backward compatibility; that fallback is not an accuracy claim for Kinmen or Matsu.
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
  private static final double PENGHU_DETERMINANT =
      PENGHU_P * PENGHU_P + PENGHU_Q * PENGHU_Q;

  // Conservative TWD97 coordinate envelope for the Penghu archipelago in TM2 zone 119. Both
  // directions use this same calibration domain: the inverse path first calculates its regional
  // TWD97 candidate, then checks that candidate here. This avoids switching models merely because
  // equivalent TWD97 and TWD67 coordinates have different numeric envelope bounds.
  private static final Envelope PENGHU_TWD97_DOMAIN =
      new Envelope(270_000.0, 340_000.0, 2_550_000.0, 2_650_000.0);

  // Allows floating-point noise when an inverse candidate falls exactly on a calibrated boundary.
  private static final double DOMAIN_EPSILON_M = 0.001;

  private DatumShiftTwd67() {}

  public static Twd67Tm2 twd97ToTwd67(Twd97Tm2 t97) {
    double e97 = t97.eastingMetres();
    double n97 = t97.northingMetres();
    if (usesPenghuRegionalModel(t97.zone(), e97, n97, 0.0)) {
      double e67 = PENGHU_TX + PENGHU_P * e97 - PENGHU_Q * n97;
      double n67 = PENGHU_TY + PENGHU_Q * e97 + PENGHU_P * n97;
      return new Twd67Tm2(e67, n67, t97.zone());
    }

    double e67 = C * e97 - B * n97 - DELTA_X;
    double n67 = -B * e97 + C * n97 + DELTA_Y;
    return new Twd67Tm2(e67, n67, t97.zone());
  }

  public static Twd97Tm2 twd67ToTwd97(Twd67Tm2 t67) {
    double e67 = t67.eastingMetres();
    double n67 = t67.northingMetres();

    if (t67.zone() == 119) {
      double shiftedE = e67 - PENGHU_TX;
      double shiftedN = n67 - PENGHU_TY;
      double candidateE97 =
          (PENGHU_P * shiftedE + PENGHU_Q * shiftedN) / PENGHU_DETERMINANT;
      double candidateN97 =
          (-PENGHU_Q * shiftedE + PENGHU_P * shiftedN) / PENGHU_DETERMINANT;
      if (usesPenghuRegionalModel(
          t67.zone(), candidateE97, candidateN97, DOMAIN_EPSILON_M)) {
        return new Twd97Tm2(candidateE97, candidateN97, t67.zone());
      }
    }

    double shiftedE = e67 + DELTA_X;
    double shiftedN = n67 - DELTA_Y;
    double e97 = (C * shiftedE + B * shiftedN) / MAIN_DETERMINANT;
    double n97 = (B * shiftedE + C * shiftedN) / MAIN_DETERMINANT;
    return new Twd97Tm2(e97, n97, t67.zone());
  }

  private static boolean usesPenghuRegionalModel(
      int zone, double easting, double northing, double toleranceMetres) {
    return zone == 119 && PENGHU_TWD97_DOMAIN.contains(easting, northing, toleranceMetres);
  }

  private static final class Envelope {
    private final double eastingMin;
    private final double eastingMax;
    private final double northingMin;
    private final double northingMax;

    private Envelope(
        double eastingMin, double eastingMax, double northingMin, double northingMax) {
      this.eastingMin = eastingMin;
      this.eastingMax = eastingMax;
      this.northingMin = northingMin;
      this.northingMax = northingMax;
    }

    private boolean contains(double easting, double northing, double toleranceMetres) {
      return easting >= eastingMin - toleranceMetres
          && easting <= eastingMax + toleranceMetres
          && northing >= northingMin - toleranceMetres
          && northing <= northingMax + toleranceMetres;
    }
  }
}
