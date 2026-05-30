package com.atakmap.android.twcoord.address.boundary;

import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import java.util.ArrayList;
import java.util.List;

/**
 * Production {@link TownshipBoundaryFacade} backed by ATAK's native SQLite ({@link DatabaseIface} /
 * {@link CursorIface}), which ships R*Tree compiled in (same reason {@link
 * com.atakmap.android.twcoord.address.AtakDatabasesAddressDatabase} exists — the host's stock
 * {@code android.database.sqlite} lacks the rtree module on some Samsung One UI builds). Shares the
 * tested {@link BoundaryResolver} algorithm with the {@code android.database.Cursor} sibling.
 *
 * <p>Every method swallows {@link Throwable} → safe default + {@code Log.w} (Constitution VI).
 */
public final class AtakDatabasesTownshipBoundary implements TownshipBoundaryFacade {

  private static final String TAG = "AtakTownshipBoundary";

  private final DatabaseIface db;

  public AtakDatabasesTownshipBoundary(DatabaseIface db) {
    if (db == null) throw new IllegalArgumentException("db");
    this.db = db;
  }

  @Override
  public LocalityResult localityAt(double lat, double lon, double snapMeters) {
    return BoundaryResolver.resolve(this::rowsInBbox, lat, lon, snapMeters);
  }

  private List<BoundaryResolver.Row> rowsInBbox(
      int adminLevel, double lat, double lon, double pad) {
    List<BoundaryResolver.Row> out = new ArrayList<>();
    try (CursorIface c =
        db.query(
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
    try (CursorIface c = db.query(sql, args)) {
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
