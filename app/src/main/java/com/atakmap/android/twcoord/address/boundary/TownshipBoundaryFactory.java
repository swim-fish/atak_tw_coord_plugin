package com.atakmap.android.twcoord.address.boundary;

import android.database.Cursor;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import java.io.File;

/**
 * Production {@link TownshipBoundaryFacade.Factory}: opens {@code townships.sqlite} via ATAK's
 * native SQLite ({@link Databases#openDatabase}) — R*Tree-enabled, same primary path as {@link
 * com.atakmap.android.twcoord.address.AtakDatabasesAddressDatabase}. After opening it probes
 * {@code townships_rtree}; if the probe fails (a host whose SQLite lacks the rtree module), it
 * escalates to the Requery fallback (research R3 / 005 R5), wrapping that database's {@code
 * android.database.Cursor} via {@link SqliteTownshipBoundaryFacade}.
 *
 * <p>Returns {@code null} if the file is missing or neither backend can open + probe it (boundary
 * data effectively absent → forward search shows the "import base data" state, FR-017). Never throws
 * (Constitution VI).
 */
public final class TownshipBoundaryFactory implements TownshipBoundaryFacade.Factory {

  private static final String TAG = "TownshipBoundaryFactory";

  /** Probe SQL: compiles + runs against townships_rtree without returning rows. */
  private static final String RTREE_PROBE = "SELECT 1 FROM townships_rtree LIMIT 0";

  @Override
  public TownshipBoundaryFacade open(File townshipsDbFile) {
    if (townshipsDbFile == null || !townshipsDbFile.isFile()) return null;

    // 1) Primary: ATAK-native SQLite.
    try {
      DatabaseIface db = Databases.openDatabase(townshipsDbFile.getAbsolutePath(), true);
      if (db != null && probeNative(db)) {
        return new AtakDatabasesTownshipBoundary(db);
      }
      if (db != null) {
        try {
          db.close();
        } catch (Throwable ignored) {
          // fall through to fallback
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "primary open/probe threw for " + townshipsDbFile, t);
    }

    // 2) Fallback: Requery-backed SQLite (R*Tree compiled in). Lazy native load.
    try {
      io.requery.android.database.sqlite.SQLiteDatabase rdb =
          io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
              townshipsDbFile.getAbsolutePath(),
              null,
              io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
      if (rdb == null) return null;
      if (!probeRequery(rdb)) {
        try {
          rdb.close();
        } catch (Throwable ignored) {
          // nothing more to try
        }
        return null;
      }
      return new SqliteTownshipBoundaryFacade(
          new SqliteTownshipBoundaryFacade.CursorQuerier() {
            @Override
            public Cursor rawQuery(String sql, String[] args) {
              return rdb.rawQuery(sql, args);
            }

            @Override
            public void close() {
              rdb.close();
            }
          });
    } catch (UnsatisfiedLinkError e) {
      Log.w(TAG, "fallback native library missing for this ABI; cannot open " + townshipsDbFile, e);
      return null;
    } catch (Throwable t) {
      Log.w(TAG, "fallback open/probe threw for " + townshipsDbFile, t);
      return null;
    }
  }

  private static boolean probeNative(DatabaseIface db) {
    try (com.atakmap.database.CursorIface c = db.query(RTREE_PROBE, null)) {
      return true; // compiled + executed without "no such module: rtree"
    } catch (Throwable t) {
      Log.w(TAG, "native rtree probe failed; will try fallback", t);
      return false;
    }
  }

  private static boolean probeRequery(io.requery.android.database.sqlite.SQLiteDatabase db) {
    try (Cursor c = db.rawQuery(RTREE_PROBE, null)) {
      return true;
    } catch (Throwable t) {
      Log.w(TAG, "fallback rtree probe failed", t);
      return false;
    }
  }
}
