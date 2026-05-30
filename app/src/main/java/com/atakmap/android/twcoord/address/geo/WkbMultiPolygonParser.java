package com.atakmap.android.twcoord.address.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal, dependency-free parser for the WGS84 OGC WKB blobs the generator emits in
 * {@code townships.geometry_wkb} (little-endian, basic 2D, type 6 MultiPolygon or type 3 Polygon).
 *
 * <p>WKB layout consumed (matches {@code shapely.geometry.MultiPolygon(...).wkb} and the proven
 * {@code scripts/verify_polygon_in.py}):
 *
 * <pre>
 *   MultiPolygon = byteOrder(1) type(4)=6 nPolys(4) [ Polygon ]*
 *   Polygon      = byteOrder(1) type(4)=3 nRings(4) [ Ring ]*
 *   Ring         = nPoints(4) [ x:double(8) y:double(8) ]*
 * </pre>
 *
 * <p>Constitution VI: the blob is UNTRUSTED (it could be truncated or tampered on disk). Any
 * malformed input — short buffer, big-endian byte order, unexpected type code, Z/M flags, absurd
 * ring/point counts — returns {@code null} rather than throwing. {@code covers} on a successfully
 * parsed geometry never throws.
 */
public final class WkbMultiPolygonParser {

  private WkbMultiPolygonParser() {}

  private static final int WKB_POLYGON = 3;
  private static final int WKB_MULTIPOLYGON = 6;
  private static final byte BYTE_ORDER_LE = 1;

  // Defensive caps so a corrupt count can't trigger a huge allocation before we notice the buffer
  // is too short. Real data: largest county 宜蘭縣 ~102 polygons / ~34k vertices.
  private static final int MAX_POLYGONS = 100_000;
  private static final int MAX_RINGS = 1_000_000;
  private static final int MAX_POINTS = 50_000_000;

  /**
   * Parse {@code wkb} into a {@link BoundaryGeometry}, or {@code null} if the bytes are not a valid
   * little-endian 2D Polygon / MultiPolygon.
   */
  public static BoundaryGeometry parseOrNull(byte[] wkb) {
    if (wkb == null || wkb.length < 5) return null;
    try {
      Cursor c = new Cursor(wkb);
      int order = c.u8();
      if (order != BYTE_ORDER_LE) {
        // Only little-endian is emitted by the generator; refuse big-endian rather than guess.
        return null;
      }
      long type = c.u32();
      BoundaryGeometry.Builder b = new BoundaryGeometry.Builder();
      if (type == WKB_MULTIPOLYGON) {
        long nPolys = c.u32();
        if (nPolys < 0 || nPolys > MAX_POLYGONS) return null;
        for (long i = 0; i < nPolys; i++) {
          // Each polygon carries its own byte-order + type header in WKB.
          int pOrder = c.u8();
          if (pOrder != BYTE_ORDER_LE) return null;
          long pType = c.u32();
          if (pType != WKB_POLYGON) return null;
          if (!readPolygon(c, b)) return null;
        }
      } else if (type == WKB_POLYGON) {
        if (!readPolygon(c, b)) return null;
      } else {
        return null;
      }
      if (b.isEmpty()) return null;
      return b.build();
    } catch (RuntimeException e) {
      // BufferUnderflow-style failures from a truncated/corrupt blob — recover to null.
      return null;
    }
  }

  /** Read one Polygon body (nRings then rings); the byte-order+type header is read by the caller. */
  private static boolean readPolygon(Cursor c, BoundaryGeometry.Builder b) {
    long nRings = c.u32();
    if (nRings < 1 || nRings > MAX_RINGS) return false;
    double[] extLon = null;
    double[] extLat = null;
    List<double[]> holeLon = new ArrayList<>();
    List<double[]> holeLat = new ArrayList<>();
    for (long r = 0; r < nRings; r++) {
      long nPts = c.u32();
      if (nPts < 0 || nPts > MAX_POINTS) return false;
      int n = (int) nPts;
      double[] lon = new double[n];
      double[] lat = new double[n];
      for (int p = 0; p < n; p++) {
        lon[p] = c.f64();
        lat[p] = c.f64();
      }
      if (r == 0) {
        extLon = lon;
        extLat = lat;
      } else {
        holeLon.add(lon);
        holeLat.add(lat);
      }
    }
    if (extLon == null) return false;
    double[][] hl = holeLon.toArray(new double[0][]);
    double[][] ha = holeLat.toArray(new double[0][]);
    b.addPolygon(extLon, extLat, hl, ha);
    return true;
  }

  /** Little-endian byte cursor over the WKB blob; throws on underflow (caught by parseOrNull). */
  private static final class Cursor {
    private final byte[] a;
    private int pos;

    Cursor(byte[] a) {
      this.a = a;
      this.pos = 0;
    }

    int u8() {
      return a[pos++] & 0xFF;
    }

    long u32() {
      long v =
          ((long) (a[pos] & 0xFF))
              | ((long) (a[pos + 1] & 0xFF) << 8)
              | ((long) (a[pos + 2] & 0xFF) << 16)
              | ((long) (a[pos + 3] & 0xFF) << 24);
      pos += 4;
      return v;
    }

    double f64() {
      long bits =
          ((long) (a[pos] & 0xFF))
              | ((long) (a[pos + 1] & 0xFF) << 8)
              | ((long) (a[pos + 2] & 0xFF) << 16)
              | ((long) (a[pos + 3] & 0xFF) << 24)
              | ((long) (a[pos + 4] & 0xFF) << 32)
              | ((long) (a[pos + 5] & 0xFF) << 40)
              | ((long) (a[pos + 6] & 0xFF) << 48)
              | ((long) (a[pos + 7] & 0xFF) << 56);
      pos += 8;
      return Double.longBitsToDouble(bits);
    }
  }
}
