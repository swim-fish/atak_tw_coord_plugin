# Contract: WkbMultiPolygonParser + PointInPolygon + BoundaryGeometry

**Modules** (NEW):
- `app/src/main/java/com/atakmap/android/twcoord/address/geo/WkbMultiPolygonParser.java`
- `app/src/main/java/com/atakmap/android/twcoord/address/geo/PointInPolygon.java`
- `app/src/main/java/com/atakmap/android/twcoord/address/geo/BoundaryGeometry.java`

Pure-Java geometry primitives. No Android, no ATAK, no external dependency
(research R1 — hand-rolled). The one genuinely new capability of feature 006.

## Interface

```java
public final class WkbMultiPolygonParser {
  /**
   * Parse a WGS84 OGC WKB blob (little-endian, type 3 Polygon or 6 MultiPolygon)
   * into a BoundaryGeometry. Returns null on any malformed input — the blob is
   * UNTRUSTED (Constitution VI defensive-validation): truncation, wrong type
   * code, big-endian, Z/M flags, or short ring counts MUST NOT throw.
   */
  public static BoundaryGeometry parseOrNull(byte[] wkb);
}

public final class BoundaryGeometry {
  public double minLat();  public double maxLat();
  public double minLon();  public double maxLon();
  /** bbox reject, then ray-cast PIP per polygon (inside exterior AND not in a hole). */
  public boolean covers(double lat, double lon);
  public int polygonCount();   // diagnostics
  public int vertexCount();    // diagnostics
}

final class PointInPolygon {           // package-private helper
  /** Ray casting; ring is parallel lon[]/lat[] arrays, implicitly closed. */
  static boolean inRing(double lat, double lon, double[] lonRing, double[] latRing);
}
```

## Invariants

1. **Never throws on bad bytes.** `parseOrNull` returns `null` for any
   non-conforming blob; `covers` on a valid geometry never throws.
2. **Coordinate order.** WKB stores `(x=lon, y=lat)`; `covers(lat, lon)` takes
   them in lat-then-lon order to match every other plugin signature
   (`nearestWithin(lat, lon, …)`). The parser maps x→lon, y→lat internally.
3. **Holes respected.** A point inside an exterior ring but inside one of its
   holes is NOT covered.
4. **bbox-first.** `covers` rejects via cached bounds before any ray cast.
5. **No mutation.** `BoundaryGeometry` is immutable after construction.

## Test plan (`WkbMultiPolygonParserTest`, `PointInPolygonTest`, JVM)

| # | Scenario | Expected |
|---|---|---|
| 1 | type-6 MultiPolygon, single polygon, point inside | covers=true |
| 2 | type-3 Polygon, point inside | covers=true |
| 3 | point inside exterior but inside a hole | covers=false |
| 4 | point outside bbox | covers=false (fast reject) |
| 5 | point on/near a shared edge between two districts | deterministic (one side) |
| 6 | truncated blob (cut mid-ring) | parseOrNull=null |
| 7 | big-endian byte0=0 | parseOrNull=null (only LE supported; matches generator) |
| 8 | unexpected type code (e.g. 1=Point) | parseOrNull=null |
| 9 | real 麥寮鄉 / 宜蘭縣 blob from fixture townships.sqlite | parses; vertexCount matches; covers a known interior point |
| 10 | the 8 reference points from verify_polygon_in.py via the facade (see facade test) | resolve to expected district |

## Anchors

- Algorithm proven by `scripts/verify_polygon_in.py` (8/8 reference points).
- WKB shape confirmed by `scripts/verify_research_claims.py` V5 (little-endian,
  type 6 MultiPolygon, WGS84 range).
- JTS swap path (research R1 reserve): replace `BoundaryGeometry` internals with
  `org.locationtech.jts.io.WKBReader` + `Geometry.covers` behind the same API.
