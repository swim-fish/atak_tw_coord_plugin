package com.atakmap.android.twcoord.coord;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

/**
 * WGS84 → TWD97 (EPSG:3826) via proj4j. The proj-string is the EPSG:3826 definition as cited in
 * ADR-0001 and copied verbatim from pwa_map src/coord/twd97.ts:6-8.
 */
public final class Projections {

  private static final String WGS84_PROJ = "+proj=longlat +datum=WGS84 +no_defs";

  private static final String TWD97_Z121_PROJ =
      "+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=GRS80"
          + " +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";

  /** EPSG:3825 — TWD97 / TM2 zone 119 (covers Penghu / 澎湖). */
  private static final String TWD97_Z119_PROJ =
      "+proj=tmerc +lat_0=0 +lon_0=119 +k=0.9999 +x_0=250000 +y_0=0 +ellps=GRS80"
          + " +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";

  // Both CRS are built from explicit parameter strings rather than EPSG-name lookup so the
  // proj4j EPSG database file is not required at runtime. This matters on Android, where
  // resources packaged inside dependency jars are not always merged into the test classpath.
  private static final CRSFactory CRS = new CRSFactory();
  private static final CoordinateReferenceSystem WGS84 =
      CRS.createFromParameters("WGS84", WGS84_PROJ);
  private static final CoordinateReferenceSystem TWD97_Z121 =
      CRS.createFromParameters("TWD97_Z121", TWD97_Z121_PROJ);
  private static final CoordinateReferenceSystem TWD97_Z119 =
      CRS.createFromParameters("TWD97_Z119", TWD97_Z119_PROJ);
  private static final CoordinateTransformFactory TX = new CoordinateTransformFactory();
  private static final CoordinateTransform WGS84_TO_TWD97_Z121 =
      TX.createTransform(WGS84, TWD97_Z121);
  private static final CoordinateTransform WGS84_TO_TWD97_Z119 =
      TX.createTransform(WGS84, TWD97_Z119);

  // Feature 002 (input-page GoTo) needs the inverse direction. proj4j transformations are
  // mathematical inverses by construction; we construct dedicated reverse-direction transforms
  // rather than relying on per-call .inverse() so the hot path stays stateless.
  private static final CoordinateTransform TWD97_Z121_TO_WGS84 =
      TX.createTransform(TWD97_Z121, WGS84);
  private static final CoordinateTransform TWD97_Z119_TO_WGS84 =
      TX.createTransform(TWD97_Z119, WGS84);

  private Projections() {}

  /**
   * WGS84 → TWD97. Zone auto-picked from longitude: anything west of 120°E uses zone 119 (Penghu /
   * 澎湖), 120°E and east uses zone 121 (main island).
   */
  public static Twd97Tm2 wgs84ToTwd97(Wgs84 fix) {
    int zone = pickZoneForLongitude(fix.longitudeDeg());
    ProjCoordinate in = new ProjCoordinate(fix.longitudeDeg(), fix.latitudeDeg());
    ProjCoordinate out = new ProjCoordinate();
    CoordinateTransform tx = (zone == 119) ? WGS84_TO_TWD97_Z119 : WGS84_TO_TWD97_Z121;
    tx.transform(in, out);
    return new Twd97Tm2(out.x, out.y, zone);
  }

  /**
   * TWD97 → WGS84 (feature 002 inverse path). Used by the GoTo input page after the operator types
   * a TWD97 / TWD67 / Taipower coordinate and the parser has assembled a {@link Twd97Tm2}. The zone
   * carried on the input picks the right TM2 transform; the caller is responsible for applying the
   * TWD67→TWD97 datum shift first if their source unit was TWD67.
   *
   * @param t97 source coordinate; its zone (121 or 119) selects the matching transform.
   * @param epochMs timestamp to stamp on the produced {@link Wgs84} (caller-provided so unit tests
   *     can pin time without {@link System#currentTimeMillis()}).
   * @return WGS84 fix; source defaults to {@link Wgs84.Source#MAP_CENTRE} since the GoTo page is
   *     not a device-location source.
   */
  public static Wgs84 twd97ToWgs84(Twd97Tm2 t97, long epochMs) {
    CoordinateTransform tx = (t97.zone() == 119) ? TWD97_Z119_TO_WGS84 : TWD97_Z121_TO_WGS84;
    ProjCoordinate in = new ProjCoordinate(t97.eastingMetres(), t97.northingMetres());
    ProjCoordinate out = new ProjCoordinate();
    tx.transform(in, out);
    return new Wgs84(/*lat*/ out.y, /*lon*/ out.x, epochMs, Wgs84.Source.MAP_CENTRE);
  }

  public static int pickZoneForLongitude(double lonDeg) {
    return lonDeg < 120.0 ? 119 : 121;
  }
}
