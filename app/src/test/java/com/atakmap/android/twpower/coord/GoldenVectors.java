package com.atakmap.android.twpower.coord;

/**
 * Four reference points lifted verbatim from pwa_map's
 * tests/unit/fixtures/test-vectors.json v2.0.0 — the single source of truth for
 * coordinate-conversion accuracy (ADR-0001).
 */
public final class GoldenVectors {

  private GoldenVectors() {}

  public static final class Point {
    public final String name;
    public final double latDeg;
    public final double lonDeg;
    public final double twd97E;
    public final double twd97N;
    public final double twd67E;
    public final double twd67N;
    public final String taipower9Char;

    Point(
        String name,
        double latDeg,
        double lonDeg,
        double twd97E,
        double twd97N,
        double twd67E,
        double twd67N,
        String taipower9Char) {
      this.name = name;
      this.latDeg = latDeg;
      this.lonDeg = lonDeg;
      this.twd97E = twd97E;
      this.twd97N = twd97N;
      this.twd67E = twd67E;
      this.twd67N = twd67N;
      this.taipower9Char = taipower9Char;
    }
  }

  public static final Point TAIPEI_101 =
      new Point(
          "Taipei 101", 25.033611, 121.564472, 306962.887, 2769619.124, 306132.271, 2769822.821,
          "B7039 BD32");

  public static final Point KAOHSIUNG_85 =
      new Point(
          "Kaohsiung 85", 22.61225, 120.2867, 176669.456, 2501522.988, 175842.607, 2501731.687,
          "P0703 CC43");

  public static final Point TAICHUNG_CH =
      new Point(
          "Taichung CH", 24.1416, 120.6437, 213789.087, 2670751.115, 212960.559, 2670956.951,
          "G5341 FE65");

  public static final Point HUALIEN_STN =
      new Point(
          "Hualien Stn", 23.9932, 121.6012, 311171.020, 2654400.548, 310341.091, 2654606.002,
          "H7509 DB40");

  public static final Point[] ALL = {TAIPEI_101, KAOHSIUNG_85, TAICHUNG_CH, HUALIEN_STN};

  /** Tolerances per pwa_map test-vectors v2.0.0. */
  public static final double TOL_TWD97_M = 0.1;
  public static final double TOL_TWD67_M = 3.0;
  /** Taipower 9-char codes are quantised to 10 m so any difference > 0 metres-but-< 1-cell counts. */
  public static final double TOL_TAIPOWER_M = 10.0;
}
