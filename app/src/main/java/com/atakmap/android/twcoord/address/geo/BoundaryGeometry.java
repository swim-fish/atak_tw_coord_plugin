package com.atakmap.android.twcoord.address.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable parsed (multi)polygon in WGS84 lon/lat with a cached bounding box, exposing a single
 * {@link #covers(double, double)} predicate. Built by {@link WkbMultiPolygonParser}.
 *
 * <p>Each polygon is an exterior ring plus zero or more interior (hole) rings. {@link #covers} does
 * a bbox fast-reject, then for each polygon tests "inside exterior AND not inside any hole". The
 * representation is parser-agnostic: if a future build needs JTS (research R1 reserve), only {@link
 * WkbMultiPolygonParser} changes — this type's contract is stable.
 */
public final class BoundaryGeometry {

  /** One polygon = exterior ring + hole rings. Rings are parallel lon[]/lat[] arrays. */
  static final class Polygon {
    final double[] extLon;
    final double[] extLat;
    final double[][] holeLon;
    final double[][] holeLat;

    Polygon(double[] extLon, double[] extLat, double[][] holeLon, double[][] holeLat) {
      this.extLon = extLon;
      this.extLat = extLat;
      this.holeLon = holeLon;
      this.holeLat = holeLat;
    }
  }

  private final List<Polygon> polygons;
  private final double minLat;
  private final double maxLat;
  private final double minLon;
  private final double maxLon;
  private final int vertexCount;

  BoundaryGeometry(List<Polygon> polygons) {
    this.polygons = polygons;
    double mnLat = Double.POSITIVE_INFINITY;
    double mxLat = Double.NEGATIVE_INFINITY;
    double mnLon = Double.POSITIVE_INFINITY;
    double mxLon = Double.NEGATIVE_INFINITY;
    int verts = 0;
    for (Polygon p : polygons) {
      for (int i = 0; i < p.extLon.length; i++) {
        double lon = p.extLon[i];
        double lat = p.extLat[i];
        if (lat < mnLat) mnLat = lat;
        if (lat > mxLat) mxLat = lat;
        if (lon < mnLon) mnLon = lon;
        if (lon > mxLon) mxLon = lon;
      }
      verts += p.extLon.length;
      if (p.holeLon != null) {
        for (double[] h : p.holeLon) verts += h.length;
      }
    }
    this.minLat = mnLat;
    this.maxLat = mxLat;
    this.minLon = mnLon;
    this.maxLon = mxLon;
    this.vertexCount = verts;
  }

  public double minLat() {
    return minLat;
  }

  public double maxLat() {
    return maxLat;
  }

  public double minLon() {
    return minLon;
  }

  public double maxLon() {
    return maxLon;
  }

  /** Number of polygons (diagnostics / tests). */
  public int polygonCount() {
    return polygons.size();
  }

  /** Total vertex count across all rings (diagnostics / tests). */
  public int vertexCount() {
    return vertexCount;
  }

  /**
   * @param lat query latitude
   * @param lon query longitude
   * @return {@code true} iff the point is inside any polygon's exterior ring and not inside one of
   *     that polygon's holes. bbox-rejects before any ray cast.
   */
  public boolean covers(double lat, double lon) {
    if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) {
      return false;
    }
    for (Polygon p : polygons) {
      if (!PointInPolygon.inRing(lat, lon, p.extLon, p.extLat)) {
        continue;
      }
      boolean inHole = false;
      if (p.holeLon != null) {
        for (int h = 0; h < p.holeLon.length; h++) {
          if (PointInPolygon.inRing(lat, lon, p.holeLon[h], p.holeLat[h])) {
            inHole = true;
            break;
          }
        }
      }
      if (!inHole) {
        return true;
      }
    }
    return false;
  }

  /** ~111.32 km per degree of latitude (mean over Taiwan). */
  private static final double METRES_PER_DEGREE_LAT = 111_320.0;

  /**
   * Minimum great-circle-ish distance (metres) from {@code (lat, lon)} to any exterior-ring vertex
   * of any polygon. An over-estimate of the true nearest-edge distance, but exterior rings here are
   * dense coastlines/borders, so vertex distance ≈ edge distance well within the ~1 km coastal snap
   * tolerance. Uses an equirectangular approximation (cheap; adequate at Taiwan scale). Returns
   * {@link Double#POSITIVE_INFINITY} for an empty geometry.
   */
  public double nearestVertexDistanceMeters(double lat, double lon) {
    double cosLat = Math.cos(Math.toRadians(lat));
    double best = Double.POSITIVE_INFINITY;
    for (Polygon p : polygons) {
      for (int i = 0; i < p.extLon.length; i++) {
        double dLat = (p.extLat[i] - lat) * METRES_PER_DEGREE_LAT;
        double dLon = (p.extLon[i] - lon) * METRES_PER_DEGREE_LAT * cosLat;
        double d2 = dLat * dLat + dLon * dLon;
        if (d2 < best) best = d2;
      }
    }
    return best == Double.POSITIVE_INFINITY ? best : Math.sqrt(best);
  }

  /** Builder used by {@link WkbMultiPolygonParser}. */
  static final class Builder {
    private final List<Polygon> polys = new ArrayList<>();

    void addPolygon(double[] extLon, double[] extLat, double[][] holeLon, double[][] holeLat) {
      polys.add(new Polygon(extLon, extLat, holeLon, holeLat));
    }

    boolean isEmpty() {
      return polys.isEmpty();
    }

    BoundaryGeometry build() {
      return new BoundaryGeometry(polys);
    }
  }
}
