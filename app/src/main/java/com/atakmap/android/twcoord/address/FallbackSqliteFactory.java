package com.atakmap.android.twcoord.address;

import com.atakmap.coremap.log.Log;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feature 005 FR-017 fallback SQLite factory. Wraps {@link
 * io.requery.android.database.sqlite.SQLiteDatabase} in an {@link AddressDatabaseFacade} so
 * datasets that the ATAK-native primary path can't open (e.g. a host whose libsqlite lacks R*Tree
 * and whose ATAK runtime is also unusable) still resolve.
 *
 * <p>Per Assumption §11: lazy-init the Requery native library on first successful open. APK size is
 * paid (~1.5 MiB per ABI) regardless of whether the fallback is ever triggered, but the native
 * library is NOT dlopen'd into the process until the first {@link #open(File)} call that succeeds.
 *
 * <p>Per contracts/fallback-sqlite-factory.md the orchestration around primary↔fallback choice
 * lives in {@code ActiveDatasetRegistry}; this class is the dumb collaborator that knows only how
 * to turn a {@code File} into an {@link AddressDatabaseFacade}.
 */
public final class FallbackSqliteFactory implements AddressDatabaseFacade.Factory {

  private static final String TAG = "FallbackSqliteFactory";

  /**
   * Visible-for-test seam: lets unit tests inject a stub that doesn't try to load Requery's native
   * library (which is Android-only and would throw {@link UnsatisfiedLinkError} under a JVM
   * unit-test runner).
   */
  public interface Opener {
    /** Open the dataset; returns {@code null} if the file cannot be opened by the runtime. */
    AddressDatabaseFacade open(File dbFile);
  }

  private final Opener opener;
  private final AtomicBoolean initialised = new AtomicBoolean(false);

  /** Production ctor — uses the real Requery-backed opener. */
  public FallbackSqliteFactory() {
    this(new RequeryOpener());
  }

  /** Test ctor — package-private so JVM tests can inject a stub Opener. */
  FallbackSqliteFactory(Opener opener) {
    this.opener = opener;
  }

  @Override
  public AddressDatabaseFacade open(File dbFile) {
    if (dbFile == null || !dbFile.isFile()) return null;
    try {
      AddressDatabaseFacade facade = opener.open(dbFile);
      if (facade != null) {
        initialised.set(true);
      }
      return facade;
    } catch (UnsatisfiedLinkError e) {
      // Requery's .so for this ABI is not bundled. Plugin downgrades to "this county can't
      // be opened by the fallback" rather than crashing.
      Log.w(TAG, "fallback native library missing for this ABI; cannot open " + dbFile, e);
      return null;
    } catch (Throwable t) {
      Log.w(TAG, "fallback open(" + dbFile + ") threw", t);
      return null;
    }
  }

  /**
   * Diagnostic: returns whether the fallback library has been loaded into the process yet. Used by
   * tests + telemetry to assert the "opt-in, not always-on" property per Assumption §11.
   */
  public boolean isFallbackInitialised() {
    return initialised.get();
  }

  // ----------------------------------------------------------------------
  // Production-side opener — uses Requery's io.requery.android.database.sqlite.SQLiteDatabase.
  // Compiled against the JitPack dep declared in app/build.gradle; not instantiated in JVM tests.
  // ----------------------------------------------------------------------

  private static final class RequeryOpener implements Opener {
    @Override
    public AddressDatabaseFacade open(File dbFile) {
      // The first invocation of any class in io.requery.android.database.sqlite triggers
      // the native library load (via SQLiteDatabase's static initialiser).
      io.requery.android.database.sqlite.SQLiteDatabase db =
          io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
              dbFile.getAbsolutePath(),
              null,
              io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
      if (db == null) return null;
      return new RequeryAddressDatabase(db);
    }
  }

  /**
   * Bridges Requery's {@link io.requery.android.database.sqlite.SQLiteDatabase} into {@link
   * AddressDatabaseFacade}. SQL strings are bit-identical to {@link AtakDatabasesAddressDatabase}
   * so the two backends are interchangeable from the resolver's perspective.
   */
  static final class RequeryAddressDatabase implements AddressDatabaseFacade {

    private static final String TAG = "RequeryAddressDatabase";

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;
    private static final double EARTH_R_M = 6_371_000.0;
    private static final String[] REQUIRED_METADATA_KEYS = {
      "schema_version", "county", "data_date"
    };

    private final io.requery.android.database.sqlite.SQLiteDatabase db;

    RequeryAddressDatabase(io.requery.android.database.sqlite.SQLiteDatabase db) {
      if (db == null) throw new IllegalArgumentException("db");
      this.db = db;
    }

    @Override
    public GeneratorMetadata readMetadata() {
      try {
        Map<String, String> raw = new LinkedHashMap<>();
        try (android.database.Cursor c = db.rawQuery("SELECT key, value FROM metadata", null)) {
          while (c.moveToNext()) {
            raw.put(c.getString(0), c.getString(1));
          }
        }
        if (!hasAllRequired(raw)) return emptyDefault();
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
        if (cosLat < 1e-12) cosLat = 1e-12;
        double dLon = radiusMeters / (METRES_PER_DEGREE_LAT * cosLat);

        try (android.database.Cursor c =
            db.rawQuery(
                NearestAddressQuery.SQL,
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
      return new GeneratorMetadata(0, "", "", "", null, null, null, -1L, Collections.emptyMap());
    }
  }
}
