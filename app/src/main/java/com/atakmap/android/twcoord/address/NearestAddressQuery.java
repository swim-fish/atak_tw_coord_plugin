package com.atakmap.android.twcoord.address;

/** Shared reverse-lookup SQL for every SQLite backend. */
final class NearestAddressQuery {

  /**
   * Java selects the shortest haversine distance from this bounding-box result. Ordering here makes
   * equal-distance rows deterministic: shorter address numbers win, followed by the lowest stable
   * row id.
   */
  static final String SQL =
      "SELECT p.lat, p.lon, p.display_name, p.display_name_halfwidth"
          + "  FROM places_rtree r JOIN places p ON r.id = p.id"
          + " WHERE r.min_lat <= ? AND r.max_lat >= ?"
          + "   AND r.min_lon <= ? AND r.max_lon >= ?"
          + " ORDER BY LENGTH(COALESCE(p.number, '')) ASC, p.id ASC";

  private NearestAddressQuery() {}
}
