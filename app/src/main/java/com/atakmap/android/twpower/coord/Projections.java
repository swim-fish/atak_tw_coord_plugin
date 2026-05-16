package com.atakmap.android.twpower.coord;

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

  // Both CRS are built from explicit parameter strings rather than EPSG-name lookup so the
  // proj4j EPSG database file is not required at runtime. This matters on Android, where
  // resources packaged inside dependency jars are not always merged into the test classpath.
  private static final CRSFactory CRS = new CRSFactory();
  private static final CoordinateReferenceSystem WGS84 =
      CRS.createFromParameters("WGS84", WGS84_PROJ);
  private static final CoordinateReferenceSystem TWD97_Z121 =
      CRS.createFromParameters("TWD97_Z121", TWD97_Z121_PROJ);
  private static final CoordinateTransformFactory TX = new CoordinateTransformFactory();
  private static final CoordinateTransform WGS84_TO_TWD97 = TX.createTransform(WGS84, TWD97_Z121);

  private Projections() {}

  public static Twd97Tm2 wgs84ToTwd97(Wgs84 fix) {
    ProjCoordinate in = new ProjCoordinate(fix.longitudeDeg(), fix.latitudeDeg());
    ProjCoordinate out = new ProjCoordinate();
    WGS84_TO_TWD97.transform(in, out);
    return new Twd97Tm2(out.x, out.y, 121);
  }
}
