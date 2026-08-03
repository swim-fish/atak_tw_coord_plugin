package com.atakmap.android.twcoord.coord;

import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.CoordinateTransform;
import org.locationtech.proj4j.CoordinateTransformFactory;
import org.locationtech.proj4j.ProjCoordinate;

/** WGS84 ↔ TWD97 TM2 projection via proj4j using explicit CRS parameter strings. */
public final class Projections {

  private static final String WGS84_PROJ = "+proj=longlat +datum=WGS84 +no_defs";

  private static final String TWD97_Z121_PROJ =
      "+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=GRS80"
          + " +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";

  /** EPSG:3825 — TWD97 / TM2 zone 119 (Penghu, Kinmen, and the Matsu archipelago). */
  private static final String TWD97_Z119_PROJ =
      "+proj=tmerc +lat_0=0 +lon_0=119 +k=0.9999 +x_0=250000 +y_0=0 +ellps=GRS80"
          + " +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";

  // Matsu includes islands east of 120°E, including Liangdao and Dongyin. Longitude-only zone
  // selection therefore assigns the wrong central meridian for some valid Matsu coordinates. Use
  // three disjoint island-group envelopes rather than one broad rectangle that would include much
  // of the nearby Fujian coast.
  private static final Envelope WESTERN_MATSU =
      new Envelope(25.90, 26.35, 119.85, 120.08);
  private static final Envelope LIANGDAO =
      new Envelope(26.29, 26.39, 120.16, 120.29);
  private static final Envelope DONGYIN =
      new Envelope(26.31, 26.42, 120.42, 120.56);

  // Both CRS are built from explicit parameter strings rather than EPSG-name lookup so the proj4j
  // EPSG database file is not required at runtime. This matters on Android, where
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
  private static final CoordinateTransform TWD97_Z121_TO_WGS84 =
      TX.createTransform(TWD97_Z121, WGS84);
  private static final CoordinateTransform TWD97_Z119_TO_WGS84 =
      TX.createTransform(TWD97_Z119, WGS84);

  private Projections() {}

  /**
   * WGS84 → TWD97. Zone 119 is selected for longitudes west of 120°E and for the complete Matsu
   * archipelago; all other supported Taiwan locations use zone 121.
   */
  public static Twd97Tm2 wgs84ToTwd97(Wgs84 fix) {
    int zone = pickZoneForLocation(fix.latitudeDeg(), fix.longitudeDeg());
    ProjCoordinate in = new ProjCoordinate(fix.longitudeDeg(), fix.latitudeDeg());
    ProjCoordinate out = new ProjCoordinate();
    CoordinateTransform tx = (zone == 119) ? WGS84_TO_TWD97_Z119 : WGS84_TO_TWD97_Z121;
    tx.transform(in, out);
    return new Twd97Tm2(out.x, out.y, zone);
  }

  /**
   * TWD97 → WGS84.
   *
   * @param t97 source coordinate; its zone selects the matching TM2 transform.
   * @param epochMs timestamp to stamp on the produced fix.
   */
  public static Wgs84 twd97ToWgs84(Twd97Tm2 t97, long epochMs) {
    CoordinateTransform tx = (t97.zone() == 119) ? TWD97_Z119_TO_WGS84 : TWD97_Z121_TO_WGS84;
    ProjCoordinate in = new ProjCoordinate(t97.eastingMetres(), t97.northingMetres());
    ProjCoordinate out = new ProjCoordinate();
    tx.transform(in, out);
    return new Wgs84(out.y, out.x, epochMs, Wgs84.Source.MAP_CENTRE);
  }

  /** Location-aware TM2 zone selection, including Matsu islands east of 120°E. */
  public static int pickZoneForLocation(double latDeg, double lonDeg) {
    if (WESTERN_MATSU.contains(latDeg, lonDeg)
        || LIANGDAO.contains(latDeg, lonDeg)
        || DONGYIN.contains(latDeg, lonDeg)) {
      return 119;
    }
    return pickZoneForLongitude(lonDeg);
  }

  /**
   * Longitude-only compatibility helper.
   *
   * <p>Call {@link #pickZoneForLocation(double, double)} when latitude is available; longitude
   * alone cannot correctly classify Dongyin, Liangdao, and other Matsu points east of 120°E.
   */
  public static int pickZoneForLongitude(double lonDeg) {
    return lonDeg < 120.0 ? 119 : 121;
  }

  private static final class Envelope {
    private final double latMin;
    private final double latMax;
    private final double lonMin;
    private final double lonMax;

    private Envelope(double latMin, double latMax, double lonMin, double lonMax) {
      this.latMin = latMin;
      this.latMax = latMax;
      this.lonMin = lonMin;
      this.lonMax = lonMax;
    }

    private boolean contains(double latDeg, double lonDeg) {
      return latDeg >= latMin && latDeg <= latMax && lonDeg >= lonMin && lonDeg <= lonMax;
    }
  }
}
