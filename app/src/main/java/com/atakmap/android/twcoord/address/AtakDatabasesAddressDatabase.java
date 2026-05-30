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
 * native SQLite ({@link Databases#openDatabase}) bundles a full-featured build with R*Tree because
 * ATAK itself relies on R*Tree for its own spatial-index queries, so we reuse that runtime here.
 * {@link SqliteAddressDatabase} stays alive only for JVM/Robolectric unit tests which run against
 * {@code xerial sqlite-jdbc} (also R*Tree-enabled).
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
  public java.util.List<com.atakmap.android.twcoord.address.forward.AddressCandidate>
      streetCandidates(
          String district,
          String foldedFragment,
          double anchorLat,
          double anchorLon,
          int limit) {
    java.util.List<com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.Raw> raw =
        new java.util.ArrayList<>();
    if (district == null || district.isEmpty()) {
      return java.util.Collections.emptyList();
    }
    try {
      String frag = foldedFragment == null ? "" : foldedFragment;
      String tai = com.atakmap.android.twcoord.address.forward.StreetTextNormaliser.taiVariant(frag);
      // Prefix match on both glyph variants (FR-009 substring incl. 段, never `=`); the app-side
      // re-fold in StreetCandidateRanker confirms the 臺↔台 equivalence (FR-010).
      raw = queryRows(district, frag + "%", tai + "%");
      if (raw.isEmpty() && !frag.isEmpty()) {
        // Substring fallback for a typed inner token.
        raw = queryRows(district, "%" + frag + "%", "%" + tai + "%");
      }
    } catch (Throwable t) {
      Log.w(TAG, "streetCandidates threw", t);
      return java.util.Collections.emptyList();
    }
    return com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.rank(
        raw, foldedFragment, anchorLat, anchorLon, limit);
  }

  @Override
  public java.util.List<com.atakmap.android.twcoord.address.forward.AddressCandidate>
      streetCandidatesCountyWide(
          String foldedFragment, double anchorLat, double anchorLon, int limit) {
    java.util.List<com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.Raw> raw =
        new java.util.ArrayList<>();
    try {
      String frag = foldedFragment == null ? "" : foldedFragment;
      if (frag.isEmpty()) return java.util.Collections.emptyList();
      String tai = com.atakmap.android.twcoord.address.forward.StreetTextNormaliser.taiVariant(frag);
      raw = queryRowsCountyWide(frag + "%", tai + "%");
      if (raw.isEmpty()) {
        raw = queryRowsCountyWide("%" + frag + "%", "%" + tai + "%");
      }
    } catch (Throwable t) {
      Log.w(TAG, "streetCandidatesCountyWide threw", t);
      return java.util.Collections.emptyList();
    }
    return com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.rank(
        raw, foldedFragment, anchorLat, anchorLon, limit);
  }

  /** Bounds the whole-county scan; the app-side ranker caps further to the display limit. */
  private static final int COUNTY_WIDE_SQL_LIMIT = 5000;

  private java.util.List<com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.Raw>
      queryRowsCountyWide(String like1, String like2) {
    java.util.List<com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.Raw> out =
        new java.util.ArrayList<>();
    try (CursorIface c =
        db.query(
            // Same street→area coalescing as queryRows, minus the township filter (county-wide).
            "SELECT p.lat, p.lon, p.display_name, p.display_name_halfwidth,"
                + " COALESCE(NULLIF(p.street, ''), p.area) AS street, p.number"
                + "  FROM places p"
                + " WHERE COALESCE(NULLIF(p.street, ''), p.area) LIKE ?"
                + "    OR COALESCE(NULLIF(p.street, ''), p.area) LIKE ?"
                + " LIMIT " + COUNTY_WIDE_SQL_LIMIT,
            new String[] {like1, like2})) {
      while (c.moveToNext()) {
        out.add(
            new com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.Raw(
                c.getDouble(0),
                c.getDouble(1),
                c.isNull(2) ? "" : c.getString(2),
                c.isNull(3) ? "" : c.getString(3),
                c.isNull(4) ? "" : c.getString(4),
                c.isNull(5) ? "" : c.getString(5)));
      }
    }
    return out;
  }

  private java.util.List<com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.Raw>
      queryRows(String district, String like1, String like2) {
    java.util.List<com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.Raw> out =
        new java.util.ArrayList<>();
    try (CursorIface c =
        db.query(
            // Empty-street addresses (p.street NULL/'': ~1.9% of Taichung, ~10% of Changhua) are
            // located by their named 巷/莊/新村 in p.area, not a 路/街 — see the generator's
            // data-contract §5.5 / address-search-guide §3. Coalescing street→area in BOTH the
            // matched value and the returned locator slot lets those rows surface under their area
            // name and feed StreetCandidateRanker's fold-check unchanged. p.area is a base-table
            // column since schema v1 (v3 only added it to places_fts, which this LIKE path skips),
            // so this works on every shipped dataset. Streeted rows are unaffected.
            "SELECT p.lat, p.lon, p.display_name, p.display_name_halfwidth,"
                + " COALESCE(NULLIF(p.street, ''), p.area) AS street, p.number"
                + "  FROM places p"
                + " WHERE p.township = ?"
                + "   AND (COALESCE(NULLIF(p.street, ''), p.area) LIKE ?"
                + "     OR COALESCE(NULLIF(p.street, ''), p.area) LIKE ?)",
            new String[] {district, like1, like2})) {
      while (c.moveToNext()) {
        out.add(
            new com.atakmap.android.twcoord.address.forward.StreetCandidateRanker.Raw(
                c.getDouble(0),
                c.getDouble(1),
                c.isNull(2) ? "" : c.getString(2),
                c.isNull(3) ? "" : c.getString(3),
                c.isNull(4) ? "" : c.getString(4),
                c.isNull(5) ? "" : c.getString(5)));
      }
    }
    return out;
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
