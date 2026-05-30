package com.atakmap.android.twcoord.address.boundary;

import android.database.Cursor;
import com.atakmap.coremap.log.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link TownshipBoundaryFacade} over anything that yields an {@link android.database.Cursor} —
 * i.e. the Robolectric/xerial test path (android {@code SQLiteDatabase}) AND the Requery fallback
 * ({@code io.requery.android.database.sqlite.SQLiteDatabase}, whose {@code rawQuery} also returns
 * an {@code android.database.Cursor}). The ATAK-native production primary uses the sibling {@link
 * AtakDatabasesTownshipBoundary} ({@code CursorIface}).
 *
 * <p>All query results feed {@link BoundaryResolver}, which owns the (tested) covers/snap logic.
 * Every method swallows {@link Throwable} → safe default + {@code Log.w} (Constitution VI).
 */
public final class SqliteTownshipBoundaryFacade implements TownshipBoundaryFacade {

  private static final String TAG = "SqliteTownshipBoundary";

  /** Minimal query seam so the facade can wrap either an android or a Requery SQLiteDatabase. */
  public interface CursorQuerier extends AutoCloseable {
    Cursor rawQuery(String sql, String[] args);

    @Override
    void close();
  }

  private final CursorQuerier db;

  public SqliteTownshipBoundaryFacade(CursorQuerier db) {
    this.db = db;
  }

  @Override
  public LocalityResult localityAt(double lat, double lon, double snapMeters) {
    return BoundaryResolver.resolve(this::rowsInBbox, lat, lon, snapMeters);
  }

  private List<BoundaryResolver.Row> rowsInBbox(
      int adminLevel, double lat, double lon, double pad) {
    List<BoundaryResolver.Row> out = new ArrayList<>();
    try (Cursor c =
        db.rawQuery(
            "SELECT t.name_zh, t.county_zh, t.geometry_wkb"
                + "  FROM townships t JOIN townships_rtree r ON r.id = t.id"
                + " WHERE t.admin_level = ?"
                + "   AND r.min_lat <= ? AND ? <= r.max_lat"
                + "   AND r.min_lon <= ? AND ? <= r.max_lon",
            new String[] {
              Integer.toString(adminLevel),
              Double.toString(lat + pad),
              Double.toString(lat - pad),
              Double.toString(lon + pad),
              Double.toString(lon - pad)
            })) {
      while (c.moveToNext()) {
        String name = c.isNull(0) ? null : c.getString(0);
        String county = c.isNull(1) ? null : c.getString(1);
        byte[] wkb = c.isNull(2) ? null : c.getBlob(2);
        if (name == null || wkb == null) continue;
        out.add(new BoundaryResolver.Row(name, county, wkb));
      }
    } catch (Throwable t) {
      Log.w(TAG, "rowsInBbox(level=" + adminLevel + ") threw", t);
    }
    // Resilience fallback (research R2 note): if the R*Tree prefilter yields nothing — a host whose
    // SQLite can't read this DB's rtree shadow tables (e.g. Robolectric's xerial shadow, where the
    // rtree JOIN returns zero rows) — scan the level's polygons directly so the WKB covers() test
    // still resolves. The R*Tree is only a prefilter; covers() is the authority. In production
    // ATAK-native SQLite the prefilter works and this branch is reached only for points genuinely
    // outside every polygon at this level.
    if (out.isEmpty()) {
      out = allRowsAtLevel(adminLevel);
    }
    return out;
  }

  private List<BoundaryResolver.Row> allRowsAtLevel(int adminLevel) {
    List<BoundaryResolver.Row> out = new ArrayList<>();
    try (Cursor c =
        db.rawQuery(
            "SELECT name_zh, county_zh, geometry_wkb FROM townships WHERE admin_level = ?",
            new String[] {Integer.toString(adminLevel)})) {
      while (c.moveToNext()) {
        String name = c.isNull(0) ? null : c.getString(0);
        String county = c.isNull(1) ? null : c.getString(1);
        byte[] wkb = c.isNull(2) ? null : c.getBlob(2);
        if (name == null || wkb == null) continue;
        out.add(new BoundaryResolver.Row(name, county, wkb));
      }
    } catch (Throwable t) {
      Log.w(TAG, "allRowsAtLevel(level=" + adminLevel + ") threw", t);
    }
    return out;
  }

  @Override
  public List<String> counties() {
    return names(
        "SELECT name_zh FROM townships WHERE admin_level = 4 ORDER BY name_zh", new String[0]);
  }

  @Override
  public List<String> districtsOf(String county) {
    if (county == null || county.isEmpty()) return new ArrayList<>();
    return names(
        "SELECT name_zh FROM townships WHERE county_zh = ? AND admin_level IN (7, 8)"
            + " ORDER BY name_zh",
        new String[] {county});
  }

  private List<String> names(String sql, String[] args) {
    List<String> out = new ArrayList<>();
    try (Cursor c = db.rawQuery(sql, args)) {
      while (c.moveToNext()) {
        if (!c.isNull(0)) out.add(c.getString(0));
      }
    } catch (Throwable t) {
      Log.w(TAG, "names query threw", t);
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
}
