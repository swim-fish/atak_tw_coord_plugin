package com.atakmap.android.twcoord.coord;

/**
 * Four reference points lifted verbatim from pwa_map's tests/unit/fixtures/test-vectors.json v2.0.0
 * — the single source of truth for coordinate-conversion accuracy (ADR-0001).
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

    /** 11-char Taipower code if pwa_map pinned one for this point; otherwise {@code null}. */
    public final String taipower11Char;

    Point(
        String name,
        double latDeg,
        double lonDeg,
        double twd97E,
        double twd97N,
        double twd67E,
        double twd67N,
        String taipower9Char,
        String taipower11Char) {
      this.name = name;
      this.latDeg = latDeg;
      this.lonDeg = lonDeg;
      this.twd97E = twd97E;
      this.twd97N = twd97N;
      this.twd67E = twd67E;
      this.twd67N = twd67N;
      this.taipower9Char = taipower9Char;
      this.taipower11Char = taipower11Char;
    }
  }

  public static final Point TAIPEI_101 =
      new Point(
          "Taipei 101",
          25.033611,
          121.564472,
          306962.887,
          2769619.124,
          306132.271,
          2769822.821,
          "B7039 BD32",
          /* taipower11Char */ null);

  public static final Point KAOHSIUNG_85 =
      new Point(
          "Kaohsiung 85",
          22.61225,
          120.2867,
          176669.456,
          2501522.988,
          175842.607,
          2501731.687,
          // pwa_map shipped "P0703 CC43" with a 3-column letter table anchored at easting
          // 170 000 m. The corrected OSGeo / Jidanni / Sunriver layout is 4 columns anchored
          // at 90 000 m, putting Kaohsiung 85 in row 5 column 1 = Q (not P). See ADR-0001.
          "Q0703 CC43",
          /* taipower11Char */ null);

  public static final Point TAICHUNG_CH =
      new Point(
          "Taichung CH",
          24.1416,
          120.6437,
          213789.087,
          2670751.115,
          212960.559,
          2670956.951,
          "G5341 FE65",
          /* taipower11Char */ null);

  /** Hualien Stn — pwa_map pinned BOTH 9-char and 11-char for this point. */
  public static final Point HUALIEN_STN =
      new Point(
          "Hualien Stn",
          23.9932,
          121.6012,
          311171.020,
          2654400.548,
          310341.091,
          2654606.002,
          "H7509 DB40",
          "H7509 DB4016");

  /**
   * Hualien inland (Xiulin / Mugua river area) — regression test for the L region. Reported by a
   * user against the original 8×3 letter table, which placed "L" in the easting band 330 000– 410
   * 000 m (Pacific Ocean east of Taiwan). With the corrected 8×4 OSGeo / Jidanni / Sunriver layout,
   * L is row 3 column 2 (easting 250 000–330 000 m), which matches the user-supplied cell-centroid
   * lat/lon. lat/lon is the centroid of the 10 m cell {@code L0593 BA86} so the 9-char encoding
   * round-trips exactly.
   */
  public static final Point HUALIEN_INLAND_L =
      new Point(
          "Hualien inland (L region)",
          23.9217588,
          121.0492519,
          255013.996,
          2646359.053,
          254185.000,
          2646565.000,
          "L0593 BA86",
          "L0593 BA8655");

  // Additional user-supplied regression vectors. Each lat/lon is the cell centroid of its 9-char
  // code (oneE = oneN = 5), derived from running the user-supplied Taipower code through
  // TaipowerParser → DatumShiftTwd67 → Projections so 9-char encoding round-trips exactly.

  /** Yilan area, E region (row 1 column 2). Centroid of cell E9863 DE60. */
  public static final Point YILAN_E =
      new Point(
          "Yilan area (E region)",
          24.6902755,
          121.7865682,
          329595.707,
          2731700.861,
          328765.000,
          2731905.000,
          "E9863 DE60",
          /* taipower11Char */ null);

  /** Miaoli area, D region (row 1 column 1). Centroid of cell D7554 DA01. */
  public static final Point MIAOLI_D =
      new Point(
          "Miaoli area (D region)",
          24.6480634,
          120.8136260,
          231134.150,
          2726810.143,
          230305.000,
          2727015.000,
          "D7554 DA01",
          /* taipower11Char */ null);

  /** Taitung area, O region (row 4 column 2). Centroid of cell O3060 HB92. */
  public static final Point TAITUNG_O =
      new Point(
          "Taitung area (O region)",
          23.3216038,
          121.2505561,
          275623.882,
          2579918.158,
          274795.000,
          2580125.000,
          "O3060 HB92",
          /* taipower11Char */ null);

  /** Pingtung area, T region (row 6 column 1). Centroid of cell T4698 HD80. */
  public static final Point PINGTUNG_T =
      new Point(
          "Pingtung area (T region)",
          22.5914114,
          120.5955246,
          208412.313,
          2499096.468,
          207585.000,
          2499305.000,
          "T4698 HD80",
          /* taipower11Char */ null);

  public static final Point[] ALL = {
    TAIPEI_101,
    KAOHSIUNG_85,
    TAICHUNG_CH,
    HUALIEN_STN,
    HUALIEN_INLAND_L,
    YILAN_E,
    MIAOLI_D,
    TAITUNG_O,
    PINGTUNG_T,
  };

  // Real-world out-of-coverage points used by negative tests. We name and document them so
  // the failure message points at a recognisable landmark instead of a synthetic latitude.

  /** Naha Airport, Okinawa, Japan — clearly north of Taiwan (lat 26.2 > LAT_MAX 25.5). */
  public static final Point NAHA_OKINAWA =
      new Point("Naha Airport", 26.1958, 127.6463, 0, 0, 0, 0, null, null);

  /** Hong Kong IFC, Central — clearly west of Penghu (lon 114.2 < LON_MIN 119.0). */
  public static final Point HONG_KONG_IFC =
      new Point("Hong Kong IFC", 22.2858, 114.1583, 0, 0, 0, 0, null, null);

  /** Tokyo Tower — both north (lat 35.6 > 25.5) and east of Taiwan's TM2 box. */
  public static final Point TOKYO_TOWER =
      new Point("Tokyo Tower", 35.6586, 139.7454, 0, 0, 0, 0, null, null);

  /** Magong, Penghu — sits in TM2 zone 119 (lon 119.566 < 120). */
  public static final Point MAGONG_PENGHU =
      new Point("Magong (Penghu)", 23.566, 119.566, 0, 0, 0, 0, null, null);

  /** Tolerances per pwa_map test-vectors v2.0.0. */
  public static final double TOL_TWD97_M = 0.1;

  public static final double TOL_TWD67_M = 3.0;

  /**
   * Taipower 9-char codes are quantised to 10 m so any difference > 0 metres-but-< 1-cell counts.
   */
  public static final double TOL_TAIPOWER_M = 10.0;
}
