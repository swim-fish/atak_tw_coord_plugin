package com.atakmap.android.twpower.coord;

/**
 * Real-world landmark for every county / city on Taiwan's main island, used by smoke tests that
 * verify TWD97 / TWD67 / Taipower behaviour across the entire main-island geography (FR-003,
 * ADR-0001). Latitudes / longitudes are common public knowledge (city halls or train stations) and
 * intentionally have only 3-4 decimal places — they are geographic anchors for the test, not golden
 * vectors for math precision.
 *
 * <p>Pinned to landmarks rather than centroid coordinates so a fail message identifies a
 * recognisable place ("Pingtung 火車站") rather than an abstract lat/lon.
 *
 * <h3>Cross-verification</h3>
 *
 * Our conversion pipeline is anchored to two authoritative sources:
 *
 * <ul>
 *   <li><b>proj4 EPSG:3826 / EPSG:3825</b> — the WGS84↔TWD97 forward/inverse used by {@link
 *       Projections} (proj-string copied verbatim from {@code pwa_map}, see ADR-0001). proj4 itself
 *       is the de-facto international reference implementation.
 *   <li><b>NCKU 歷史所 GIS — 座標系統轉換工具</b>: <a
 *       href="http://gis.thl.ncku.edu.tw/coordtrans/coordtrans.aspx">
 *       gis.thl.ncku.edu.tw/coordtrans/coordtrans.aspx</a> — the canonical online converter used by
 *       Taiwan GIS practitioners. To re-verify any landmark below, paste its lat/lon into NCKU's
 *       form and compare the TWD97 / TWD67 output against ours within ±3 m (TWD67 tolerance) / ±0.1
 *       m (TWD97 tolerance) per {@link GoldenVectors}.
 * </ul>
 *
 * The 4 {@link GoldenVectors} points (Taipei 101, Kaohsiung 85, Taichung CH, Hualien Stn) are
 * already pinned against pwa_map's published vectors; the remaining 15 cities here are smoke-
 * tested rather than value-pinned — re-verify against NCKU when adding tight bounds.
 */
public final class TaiwanCities {

  private TaiwanCities() {}

  public static final class City {
    public final String name;
    public final double latDeg;
    public final double lonDeg;

    City(String name, double latDeg, double lonDeg) {
      this.name = name;
      this.latDeg = latDeg;
      this.lonDeg = lonDeg;
    }
  }

  // North
  public static final City KEELUNG = new City("基隆港 (Keelung Port)", 25.131, 121.741);
  public static final City TAIPEI = new City("臺北 101 (Taipei 101)", 25.034, 121.564);
  public static final City NEW_TAIPEI = new City("新北市政府 (New Taipei City Hall)", 25.012, 121.466);
  public static final City TAOYUAN =
      new City("中壢火車站 (Zhongli Railway Stn, Taoyuan)", 24.953, 121.225);
  public static final City HSINCHU_CITY = new City("新竹火車站 (Hsinchu Railway Stn)", 24.802, 120.972);
  public static final City HSINCHU_COUNTY =
      new City("竹北火車站 (Zhubei Railway Stn, Hsinchu Cty)", 24.831, 121.014);
  public static final City MIAOLI = new City("苗栗火車站 (Miaoli Railway Stn)", 24.566, 120.819);

  // Central
  public static final City TAICHUNG = new City("臺中市政府 (Taichung City Hall)", 24.162, 120.644);
  public static final City CHANGHUA = new City("彰化火車站 (Changhua Railway Stn)", 24.083, 120.539);
  public static final City NANTOU = new City("南投縣政府 (Nantou County Hall)", 23.916, 120.685);
  public static final City YUNLIN = new City("斗六火車站 (Douliu Railway Stn, Yunlin)", 23.711, 120.547);

  // South
  public static final City CHIAYI_CITY = new City("嘉義火車站 (Chiayi Railway Stn)", 23.479, 120.444);
  public static final City CHIAYI_COUNTY =
      new City("太保市政府 (Taibao City Hall, Chiayi Cty)", 23.460, 120.330);
  public static final City TAINAN = new City("善化 (Shanhua, Tainan)", 23.041, 120.308);
  public static final City KAOHSIUNG = new City("85 大樓 (Kaohsiung 85)", 22.612, 120.287);
  public static final City PINGTUNG = new City("屏東火車站 (Pingtung Railway Stn)", 22.671, 120.494);

  // East
  public static final City YILAN = new City("宜蘭火車站 (Yilan Railway Stn)", 24.755, 121.756);
  public static final City HUALIEN = new City("花蓮火車站 (Hualien Railway Stn)", 23.993, 121.601);
  public static final City TAITUNG = new City("臺東火車站 (Taitung Railway Stn)", 22.793, 121.106);

  /** Every county / city on the main island. 19 entries: 7 North, 4 Central, 5 South, 3 East. */
  public static final City[] ALL = {
    KEELUNG,
    TAIPEI,
    NEW_TAIPEI,
    TAOYUAN,
    HSINCHU_CITY,
    HSINCHU_COUNTY,
    MIAOLI,
    TAICHUNG,
    CHANGHUA,
    NANTOU,
    YUNLIN,
    CHIAYI_CITY,
    CHIAYI_COUNTY,
    TAINAN,
    KAOHSIUNG,
    PINGTUNG,
    YILAN,
    HUALIEN,
    TAITUNG,
  };
}
