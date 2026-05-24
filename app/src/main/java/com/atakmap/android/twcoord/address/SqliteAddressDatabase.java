package com.atakmap.android.twcoord.address;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.atakmap.coremap.log.Log;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production {@link AddressDatabaseFacade}: wraps an Android {@link SQLiteDatabase} opened
 * read-only against the active dataset's {@code places-&lt;county&gt;.sqlite} file.
 *
 * <p>Per {@code contracts/address-database-facade.md} the runtime reverse-lookup query is a
 * two-stage bbox + haversine refine:
 *
 * <ol>
 *   <li>Convert the search radius (metres) to lat/lon deltas with the cos-latitude correction.
 *   <li>Query {@code places_rtree} (joined to {@code places}) for candidate records inside the
 *       bbox.
 *   <li>Compute haversine distance to each candidate and return the nearest under the radius.
 * </ol>
 *
 * <p>Constitution VI: every public method swallows {@link Throwable} and returns a safe default
 * ({@code null} from {@link #nearestWithin}, an empty-but-valid {@link GeneratorMetadata} from
 * {@link #readMetadata}). The wrapping {@link SQLiteDatabase} is closed by {@link #close()}.
 */
public final class SqliteAddressDatabase implements AddressDatabaseFacade {

  private static final String TAG = "SqliteAddressDatabase";

  /** ~111.32 km per degree of latitude (mean over Taiwan latitudes). */
  private static final double METRES_PER_DEGREE_LAT = 111_320.0;

  /** Earth mean radius in metres for haversine. */
  private static final double EARTH_R_M = 6_371_000.0;

  /** Required {@code metadata} keys (kept in sync with {@link AddressBundleImporter}). */
  private static final String[] REQUIRED_METADATA_KEYS = {"schema_version", "county", "data_date"};

  private final SQLiteDatabase db;

  public SqliteAddressDatabase(SQLiteDatabase db) {
    if (db == null) throw new IllegalArgumentException("db");
    this.db = db;
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  @Override
  public GeneratorMetadata readMetadata() {
    try {
      Map<String, String> raw = new LinkedHashMap<>();
      try (Cursor c = db.rawQuery("SELECT key, value FROM metadata", null)) {
        while (c.moveToNext()) {
          raw.put(c.getString(0), c.getString(1));
        }
      }
      if (!hasAllRequired(raw)) {
        return emptyDefault();
      }
      int schemaVersion;
      try {
        schemaVersion = Integer.parseInt(raw.get("schema_version").trim());
      } catch (NumberFormatException e) {
        return emptyDefault();
      }
      long inserted = parseLongOrDefault(raw.get("inserted"), -1L);
      return new GeneratorMetadata(
          schemaVersion,
          raw.get("source") != null ? raw.get("source") : "",
          raw.get("county"),
          raw.get("data_date"),
          raw.get("csv_sha256"),
          raw.get("csv_path"),
          raw.get("crs"),
          inserted,
          raw);
    } catch (Throwable t) {
      Log.w(TAG, "readMetadata threw", t);
      return emptyDefault();
    }
  }

  @Override
  public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
    if (radiusMeters <= 0) return null;
    try {
      double latRad = Math.toRadians(lat);
      double dLat = radiusMeters / METRES_PER_DEGREE_LAT;
      double cosLat = Math.cos(latRad);
      if (cosLat < 1e-12) cosLat = 1e-12; // guard near the poles
      double dLon = radiusMeters / (METRES_PER_DEGREE_LAT * cosLat);

      try (Cursor c =
          db.rawQuery(
              "SELECT p.lat, p.lon, p.display_name, p.display_name_halfwidth"
                  + "  FROM places_rtree r JOIN places p ON r.id = p.id"
                  + " WHERE r.min_lat <= ? AND r.max_lat >= ?"
                  + "   AND r.min_lon <= ? AND r.max_lon >= ?",
              new String[] {
                Double.toString(lat + dLat),
                Double.toString(lat - dLat),
                Double.toString(lon + dLon),
                Double.toString(lon - dLon)
              })) {
        double best = radiusMeters;
        AddressRecord winner = null;
        while (c.moveToNext()) {
          double rLat = c.getDouble(0);
          double rLon = c.getDouble(1);
          double d = haversineMeters(lat, lon, rLat, rLon);
          if (d < best) {
            best = d;
            String displayName = c.isNull(2) ? "" : c.getString(2);
            String displayNameHw = c.isNull(3) ? "" : c.getString(3);
            winner = new AddressRecord(rLat, rLon, displayName, displayNameHw);
          }
        }
        return winner;
      }
    } catch (Throwable t) {
      Log.w(TAG, "nearestWithin threw", t);
      return null;
    }
  }

  @Override
  public void close() {
    try {
      db.close();
    } catch (Throwable t) {
      Log.w(TAG, "close threw", t);
    }
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double dPhi = Math.toRadians(lat2 - lat1);
    double dLambda = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
    return 2 * EARTH_R_M * Math.asin(Math.sqrt(a));
  }

  private static boolean hasAllRequired(Map<String, String> raw) {
    for (String k : REQUIRED_METADATA_KEYS) {
      if (!raw.containsKey(k)) return false;
    }
    return true;
  }

  private static long parseLongOrDefault(String s, long fallback) {
    if (s == null) return fallback;
    try {
      return Long.parseLong(s.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  private static GeneratorMetadata emptyDefault() {
    return new GeneratorMetadata(
        0, "", "", "", null, null, null, -1L, Collections.<String, String>emptyMap());
  }

  // ----------------------------------------------------------------------
  // Factory used by TwCoordMapComponent / AddressSubsystem to open a fresh facade per dataset.
  // ----------------------------------------------------------------------

  public static final class SqliteFactory implements Factory {
    @Override
    public AddressDatabaseFacade open(File dbFile) {
      if (dbFile == null || !dbFile.isFile()) return null;
      try {
        SQLiteDatabase db =
            SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        return new SqliteAddressDatabase(db);
      } catch (Throwable t) {
        Log.w(TAG, "SqliteFactory.open(" + dbFile + ") threw", t);
        return null;
      }
    }
  }
}
