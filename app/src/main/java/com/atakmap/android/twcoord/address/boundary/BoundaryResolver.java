package com.atakmap.android.twcoord.address.boundary;

import com.atakmap.android.twcoord.address.geo.BoundaryGeometry;
import com.atakmap.android.twcoord.address.geo.WkbMultiPolygonParser;
import com.atakmap.coremap.log.Log;
import java.util.List;

/**
 * The pure locality-resolution algorithm shared by both {@link TownshipBoundaryFacade}
 * implementations (the Robolectric/xerial test path and the ATAK-native production path). Keeps the
 * R*Tree→covers→snap logic in one tested place; each facade only supplies rows.
 *
 * <p>Mirrors {@code reverse_geocode.py::lookup_township} (the generator reference proven 8/8 by
 * {@code scripts/verify_polygon_in.py}): level-8 first, then level-7; {@code county_zh} comes back
 * inline; level-4 is the county fallback only when {@code county_zh} is null. Never throws.
 */
final class BoundaryResolver {

  private static final String TAG = "BoundaryResolver";

  /** ~111.32 km per degree of latitude (mean over Taiwan); used to pad the snap bbox. */
  private static final double METRES_PER_DEGREE_LAT = 111_320.0;

  /** One boundary row: bare name, inline parent county (nullable), and the geometry blob. */
  static final class Row {
    final String nameZh;
    final String countyZh; // nullable
    final byte[] wkb;

    Row(String nameZh, String countyZh, byte[] wkb) {
      this.nameZh = nameZh;
      this.countyZh = countyZh;
      this.wkb = wkb;
    }
  }

  /** Supplies candidate rows for a given admin level whose bbox overlaps the padded query point. */
  interface RowSource {
    /**
     * @param adminLevel 4, 7, or 8
     * @param lat query latitude
     * @param lon query longitude
     * @param padDeg degrees to pad the point into a bbox for the R*Tree prefilter (0 = exact point)
     */
    List<Row> rowsInBbox(int adminLevel, double lat, double lon, double padDeg);
  }

  private BoundaryResolver() {}

  /** Resolve the locality. {@code snapMeters > 0} enables the coastal nearest-polygon snap. */
  static LocalityResult resolve(RowSource src, double lat, double lon, double snapMeters) {
    try {
      // Strict cover: level 8 then level 7.
      for (int level : new int[] {8, 7}) {
        List<Row> rows = src.rowsInBbox(level, lat, lon, 0.0);
        for (Row r : rows) {
          BoundaryGeometry g = WkbMultiPolygonParser.parseOrNull(r.wkb);
          if (g == null) continue; // corrupt geometry — skip, never throw (Constitution VI)
          if (g.covers(lat, lon)) {
            String county = r.countyZh != null && !r.countyZh.isEmpty()
                ? r.countyZh
                : countyFromLevel4(src, lat, lon);
            if (county == null) {
              // District covered but county_zh null and no level-4 cover — return district-less
              // county-unknown as None rather than a half-answer.
              continue;
            }
            return LocalityResult.full(county, r.nameZh);
          }
        }
      }

      // Coastal snap: nearest level-7/8 polygon within tolerance.
      if (snapMeters > 0.0) {
        double padDeg = snapMeters / METRES_PER_DEGREE_LAT;
        Row best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int level : new int[] {8, 7}) {
          for (Row r : src.rowsInBbox(level, lat, lon, padDeg)) {
            BoundaryGeometry g = WkbMultiPolygonParser.parseOrNull(r.wkb);
            if (g == null) continue;
            double d = g.nearestVertexDistanceMeters(lat, lon);
            if (d < bestD) {
              bestD = d;
              best = r;
            }
          }
        }
        if (best != null && bestD <= snapMeters) {
          String county = best.countyZh != null && !best.countyZh.isEmpty()
              ? best.countyZh
              : countyFromLevel4(src, lat, lon);
          if (county != null) {
            return LocalityResult.snapped(county, best.nameZh);
          }
        }
      }

      // County-only fallback: a level-4 polygon covers even when no district did.
      String county = countyFromLevel4(src, lat, lon);
      if (county != null) {
        return LocalityResult.countyOnly(county);
      }
      return LocalityResult.none();
    } catch (Throwable t) {
      Log.w(TAG, "resolve threw", t);
      return LocalityResult.none();
    }
  }

  private static String countyFromLevel4(RowSource src, double lat, double lon) {
    for (Row r : src.rowsInBbox(4, lat, lon, 0.0)) {
      BoundaryGeometry g = WkbMultiPolygonParser.parseOrNull(r.wkb);
      if (g == null) continue;
      if (g.covers(lat, lon)) {
        return r.nameZh; // level-4 name IS the county
      }
    }
    return null;
  }
}
