package com.atakmap.android.twcoord.address;

import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Production {@link AddressDatabaseFacade} backed by ATAK's native SQLite via {@link Databases}.
 *
 * <p>Why not {@code android.database.sqlite.SQLiteDatabase}? Android's stock {@link
 * android.database.sqlite.SQLiteDatabase} ships SQLite without the R*Tree extension on at least
 * Samsung One UI builds (verified on Galaxy R52X908JF0W / Android 14, 2026-05-26): the runtime
 * query {@code SELECT … FROM places_rtree …} fails with {@code no such module: rtree}. ATAK's
 * native SQLite ({@link Databases#openDatabase}) bundles a full-featured build with R*Tree
 * because ATAK itself relies on R*Tree for its own spatial-index queries, so we reuse that
 * runtime here. {@link SqliteAddressDatabase} stays alive only for JVM/Robolectric unit tests
 * which run against {@code xerial sqlite-jdbc} (also R*Tree-enabled).
 */
public final class AtakDatabasesAddressDatabase implements AddressDatabaseFacade {

  private static final String TAG = "AtakDatabasesAddressDatabase";

  /** ~111.32 km per degree of latitude (mean over Taiwan latitudes). */
  private static final double METRES_PER_DEGREE_LAT = 111_320.0;

  /** Earth mean radius in metres for haversine. */
  private static final double EARTH_R_M = 6_371_000.0;

  /** Required {@code metadata} keys (kept in sync with {@link AddressBundleImporter}). */
  private static final String[] REQUIRED_METADATA_KEYS = {"schema_version", "county", "data_date"};

  private final DatabaseIface db;

  public AtakDatabasesAddressDatabase(DatabaseIface db) {
    if (db == null) throw new IllegalArgumentException("db");
    this.db = db;
  }

  @Override
  public GeneratorMetadata readMetadata() {
    try {
      Map<String, String> raw = new LinkedHashMap<>();
      try (CursorIface c = db.query("SELECT key, value FROM metadata", null)) {
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

      try (CursorIface c =
          db.query(
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

  /** Production factory wired in {@code TwCoordMapComponent}. */
  public static final class Factory implements AddressDatabaseFacade.Factory {
    @Override
    public AddressDatabaseFacade open(File dbFile) {
      if (dbFile == null || !dbFile.isFile()) return null;
      try {
        DatabaseIface db = Databases.openDatabase(dbFile.getAbsolutePath(), true);
        if (db == null) return null;
        return new AtakDatabasesAddressDatabase(db);
      } catch (Throwable t) {
        Log.w(TAG, "Factory.open(" + dbFile + ") threw", t);
        return null;
      }
    }
  }
}
