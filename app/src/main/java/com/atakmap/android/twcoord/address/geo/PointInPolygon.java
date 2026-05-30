package com.atakmap.android.twcoord.address.geo;

/**
 * Ray-casting point-in-polygon test for a single linear ring. Package-private helper used by {@link
 * BoundaryGeometry}; the algorithm is the standard even-odd crossing-number test, identical to the
 * one proven 8/8 against the real {@code townships.sqlite} by {@code scripts/verify_polygon_in.py}.
 *
 * <p>A ring is supplied as parallel {@code lon[]} / {@code lat[]} arrays. The ring is treated as
 * implicitly closed (the last vertex connects back to the first); a WKB ring whose first and last
 * vertices already coincide works correctly because the degenerate closing edge contributes no
 * crossing.
 */
final class PointInPolygon {

  private PointInPolygon() {}

  /**
   * @param lat query latitude (y)
   * @param lon query longitude (x)
   * @param lonRing ring x-coordinates
   * @param latRing ring y-coordinates (parallel to {@code lonRing}; same length)
   * @return {@code true} if the point is inside the ring (even-odd rule)
   */
  static boolean inRing(double lat, double lon, double[] lonRing, double[] latRing) {
    if (lonRing == null || latRing == null) return false;
    int n = Math.min(lonRing.length, latRing.length);
    if (n < 3) return false;
    boolean inside = false;
    int j = n - 1;
    for (int i = 0; i < n; i++) {
      double xi = lonRing[i];
      double yi = latRing[i];
      double xj = lonRing[j];
      double yj = latRing[j];
      // Does the horizontal ray at y=lat cross edge (j -> i)?
      if (((yi > lat) != (yj > lat))
          && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) {
        inside = !inside;
      }
      j = i;
    }
    return inside;
  }
}
