/**
 * Feature 006 geometry primitives: a dependency-free WGS84 WKB MultiPolygon parser ({@link
 * com.atakmap.android.twcoord.address.geo.WkbMultiPolygonParser}) plus ray-cast point-in-polygon
 * ({@link com.atakmap.android.twcoord.address.geo.PointInPolygon}), wrapped by {@link
 * com.atakmap.android.twcoord.address.geo.BoundaryGeometry}.
 *
 * <p>Pure Java — no Android, no ATAK, no external dependency (research R1). Used by the boundary
 * facade to test whether a coordinate falls inside a 鄉鎮市區 polygon. The algorithm is the one
 * proven 8/8 against the real {@code townships.sqlite} by {@code scripts/verify_polygon_in.py}.
 * Inputs (the {@code geometry_wkb} blob) are treated as untrusted per Constitution VI: malformed
 * bytes recover to {@code null}, never throw.
 */
package com.atakmap.android.twcoord.address.geo;
