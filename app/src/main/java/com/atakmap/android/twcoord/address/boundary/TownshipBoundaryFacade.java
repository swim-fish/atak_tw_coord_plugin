package com.atakmap.android.twcoord.address.boundary;

import java.io.File;
import java.util.List;

/**
 * Read API over the singleton {@code townships.sqlite} boundary layer (feature 006). Answers "which
 * 縣市 + 鄉鎮市區 is this coordinate in?" via an R*Tree bbox prefilter + WKB polygon-in test,
 * WITHOUT opening any per-county address database (FR-001 / SC-002). Also serves the county /
 * district pick-lists for the forward-search funnel (FR-006 / FR-007).
 *
 * <p>Per {@code contracts/township-boundary-facade.md}. Implementations MUST NOT throw out of any
 * method — a malformed geometry blob or SQL error degrades to {@link LocalityResult#none()} (or an
 * empty list) after logging at {@code Log.w}, so a corrupt boundary DB can never crash the host
 * (Constitution VI).
 */
public interface TownshipBoundaryFacade extends AutoCloseable {

  /**
   * Resolve {@code (county, district, approx)} for a coordinate.
   *
   * @param lat WGS84 latitude
   * @param lon WGS84 longitude
   * @param snapMeters coastline tolerance: when {@code > 0} and no district strictly covers the
   *     point, snap to the nearest level-7/8 polygon within this many metres and return it with
   *     {@code approx=true}. {@code 0} disables snapping (strict).
   * @return the locality; never {@code null} (use {@link LocalityResult#none()} for "outside all
   *     boundaries").
   */
  LocalityResult localityAt(double lat, double lon, double snapMeters);

  /** Level-4 縣市 names present in the data, for the funnel's manual list (FR-006). Sorted, stable. */
  List<String> counties();

  /** Level-7/8 鄉鎮市區 names for a county, for stage ② (FR-007). Sorted, stable; empty if unknown. */
  List<String> districtsOf(String county);

  @Override
  void close();

  /** Opens a {@link TownshipBoundaryFacade} over a {@code townships.sqlite} file. */
  interface Factory {
    /** Open read-only; {@code null} if the file is missing or unopenable. */
    TownshipBoundaryFacade open(File townshipsDbFile);
  }
}
