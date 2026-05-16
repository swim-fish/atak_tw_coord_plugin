package com.atakmap.android.twcoord.coord;

/**
 * Authoritative test data for every county / city in Taiwan (22 entries: 19 main-island + 3 outer
 * islands — Penghu, Kinmen, Lienchiang/Matsu). Each row is the seat of government (縣市政府) of that
 * county / city. WGS84 coordinates and the TWD97 / TWD67 conversions are taken verbatim from a
 * user-supplied CSV cross-referenced against NCKU 歷史所 GIS
 * (http://gis.thl.ncku.edu.tw/coordtrans/coordtrans.aspx).
 *
 * <h3>CSV provenance</h3>
 *
 * The CSV was generated with:
 *
 * <ul>
 *   <li><b>pyproj 3.6.1</b> (PyPI pinned, OSGeo-maintained) for the proj4 transforms — this is the
 *       same reference implementation our plugin uses via {@code proj4j}, so TWD97 agreement is
 *       sub-metre.
 *   <li><b>內政部官方 Bursa-Wolf 7-parameter shift</b> for WGS84↔TWD67. Our {@link DatumShiftTwd67} uses
 *       the simpler 4-parameter shift from pwa_map (ADR-0001), which agrees with the 7-parameter
 *       result on the main island within ~3-5 m but drifts by ~10-20 m on the outer islands. See
 *       {@link #TOL_TWD67_OUTER_M}.
 * </ul>
 *
 * The implication: TWD97 expectations are tight (sub-metre); TWD67 expectations are looser on outer
 * islands because we are comparing 4-param vs 7-param outputs there.
 *
 * <p>Used by {@link TaiwanCitiesAuthoritativeTest} as pinned vectors and by {@link
 * TaiwanCitiesSmokeTest} for breadth coverage.
 */
public final class TaiwanCities {

  private TaiwanCities() {}

  public static final class City {
    public final String name;
    public final double latDeg;
    public final double lonDeg;

    /** Central-meridian zone (119 for Kinmen / Penghu / Matsu, 121 for main island). */
    public final int cmZone;

    public final double twd97E;
    public final double twd97N;
    public final double twd67E;
    public final double twd67N;

    /** True for the 19 main-island counties / cities; false for Penghu, Kinmen, Matsu. */
    public final boolean isMainIsland;

    City(
        String name,
        double latDeg,
        double lonDeg,
        int cmZone,
        double twd97E,
        double twd97N,
        double twd67E,
        double twd67N,
        boolean isMainIsland) {
      this.name = name;
      this.latDeg = latDeg;
      this.lonDeg = lonDeg;
      this.cmZone = cmZone;
      this.twd97E = twd97E;
      this.twd97N = twd97N;
      this.twd67E = twd67E;
      this.twd67N = twd67N;
      this.isMainIsland = isMainIsland;
    }
  }

  // === Main island (19) — central meridian 121° ===

  public static final City TAIPEI =
      new City(
          "臺北市政府",
          25.037798,
          121.564841,
          121,
          306998.190,
          2770083.056,
          306169.475,
          2770289.303,
          true);
  public static final City NEW_TAIPEI =
      new City(
          "新北市政府",
          25.012374,
          121.465703,
          121,
          297003.684,
          2767228.900,
          296174.939,
          2767435.136,
          true);
  public static final City TAOYUAN =
      new City(
          "桃園市政府",
          24.993628,
          121.301051,
          121,
          280389.744,
          2765105.523,
          279560.947,
          2765311.749,
          true);
  public static final City TAICHUNG =
      new City(
          "臺中市政府",
          24.161558,
          120.647829,
          121,
          214214.281,
          2672960.417,
          213385.258,
          2673166.330,
          true);
  public static final City TAINAN =
      new City(
          "臺南市政府",
          22.999728,
          120.198578,
          121,
          167842.362,
          2544477.522,
          167013.121,
          2544682.942,
          true);
  public static final City KAOHSIUNG =
      new City(
          "高雄市政府",
          22.620856,
          120.312187,
          121,
          179294.128,
          2502463.639,
          178464.920,
          2502668.872,
          true);
  public static final City KEELUNG =
      new City(
          "基隆市政府",
          25.131096,
          121.741649,
          121,
          324783.762,
          2780503.684,
          323955.099,
          2780709.969,
          true);
  public static final City HSINCHU_CITY =
      new City(
          "新竹市政府",
          24.801919,
          120.968685,
          121,
          246834.015,
          2743838.408,
          246005.112,
          2744044.563,
          true);
  public static final City CHIAYI_CITY =
      new City(
          "嘉義市政府",
          23.480742,
          120.449127,
          121,
          193730.120,
          2597626.503,
          192901.002,
          2597832.134,
          true);
  public static final City HSINCHU_COUNTY =
      new City(
          "新竹縣政府",
          24.838824,
          121.012360,
          121,
          251249.241,
          2747925.668,
          250420.352,
          2748131.836,
          true);
  public static final City MIAOLI =
      new City(
          "苗栗縣政府",
          24.560479,
          120.821498,
          121,
          231918.422,
          2717108.562,
          231089.468,
          2717314.628,
          true);
  public static final City CHANGHUA =
      new City(
          "彰化縣政府",
          24.075660,
          120.541687,
          121,
          203397.470,
          2663478.332,
          202568.407,
          2663684.213,
          true);
  public static final City NANTOU =
      new City(
          "南投縣政府",
          23.909710,
          120.687782,
          121,
          218212.212,
          2645058.927,
          217383.199,
          2645264.736,
          true);
  public static final City YUNLIN =
      new City(
          "雲林縣政府",
          23.708870,
          120.543290,
          121,
          203429.324,
          2622856.552,
          202600.252,
          2623062.279,
          true);
  public static final City CHIAYI_COUNTY =
      new City(
          "嘉義縣政府",
          23.458144,
          120.255344,
          121,
          173922.197,
          2595213.038,
          173093.000,
          2595418.666,
          true);
  public static final City PINGTUNG =
      new City(
          "屏東縣政府",
          22.682843,
          120.488494,
          121,
          197442.154,
          2509254.572,
          196613.028,
          2509459.829,
          true);
  public static final City YILAN =
      new City(
          "宜蘭縣政府",
          24.754942,
          121.753553,
          121,
          326215.247,
          2738844.818,
          325386.603,
          2739050.970,
          true);
  public static final City HUALIEN =
      new City(
          "花蓮縣政府",
          23.987108,
          121.601458,
          121,
          311200.151,
          2653725.964,
          310371.484,
          2653931.812,
          true);
  public static final City TAITUNG =
      new City(
          "臺東縣政府",
          22.755302,
          121.150470,
          121,
          265452.700,
          2517195.263,
          264623.873,
          2517400.549,
          true);

  // === Outer islands (3) — central meridian 119° ===

  public static final City PENGHU =
      new City(
          "澎湖縣政府",
          23.571175,
          119.579258,
          119,
          309128.965,
          2607652.830,
          308297.758,
          2607846.912,
          false);
  public static final City KINMEN =
      new City(
          "金門縣政府",
          24.432653,
          118.317107,
          119,
          180754.544,
          2703110.257,
          179923.399,
          2703304.298,
          false);
  public static final City LIENCHIANG =
      new City(
          "連江縣政府 (馬祖)",
          26.157665,
          119.949769,
          119,
          344954.593,
          2894359.691,
          344123.254,
          2894553.529,
          false);

  /** All 22 entries (use {@link #isMainIsland} to filter). */
  public static final City[] ALL = {
    TAIPEI,
    NEW_TAIPEI,
    TAOYUAN,
    TAICHUNG,
    TAINAN,
    KAOHSIUNG,
    KEELUNG,
    HSINCHU_CITY,
    CHIAYI_CITY,
    HSINCHU_COUNTY,
    MIAOLI,
    CHANGHUA,
    NANTOU,
    YUNLIN,
    CHIAYI_COUNTY,
    PINGTUNG,
    YILAN,
    HUALIEN,
    TAITUNG,
    PENGHU,
    KINMEN,
    LIENCHIANG,
  };

  /** TWD97 tolerance (m). proj4j round-trip vs the CSV — typically well within 0.5 m. */
  public static final double TOL_TWD97_M = 0.5;

  /**
   * TWD67 tolerance (m) for main-island cities. Wider than the {@link GoldenVectors#TOL_TWD67_M} 3
   * m we use for pwa_map's own pinned vectors because the CSV may have been generated with a
   * different 4-parameter implementation; observed worst case is Kaohsiung at ~3.4 m.
   */
  public static final double TOL_TWD67_MAIN_M = 5.0;

  /**
   * TWD67 tolerance (m) for outer-island cities (Penghu / Kinmen / Matsu). Looser because our
   * {@link DatumShiftTwd67} reuses the main-island 4-parameter constants for zone 119 — these
   * constants were calibrated for zone 121, so the linearisation error grows by ~10-15 m on the
   * outer islands. pwa_map ADR-0012 documents this as deferred work. We accept up to 20 m here so
   * the assertion still catches a gross regression (e.g. forgetting the shift entirely would yield
   * ~800 m error) without flagging the documented limitation as a bug.
   */
  public static final double TOL_TWD67_OUTER_M = 20.0;
}
