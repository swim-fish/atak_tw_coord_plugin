/**
 * Feature 006 administrative-boundary layer: {@link
 * com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade} answers "which 縣市 +
 * 鄉鎮市區 is this coordinate in?" from the ~10 MB {@code townships.sqlite} (MOI boundary release
 * 1140318) via an R*Tree bbox prefilter + WKB polygon-in test, WITHOUT opening any per-county
 * address database.
 *
 * <p>The facade returns a {@link com.atakmap.android.twcoord.address.boundary.LocalityResult}
 * ({county, district, approx}) and also serves the county / district pick-lists for the
 * forward-search funnel. Mounted once for the plugin lifetime (research R3). Reused by both forward
 * search and the reverse-path county scoping.
 */
package com.atakmap.android.twcoord.address.boundary;
