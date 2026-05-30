package com.atakmap.android.twcoord.address.forward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared app-side half of the district-scoped street query (T024 / T025): given the raw rows a
 * facade pulled with {@code WHERE township=? AND street LIKE ?}, it (a) re-applies {@link
 * StreetTextNormaliser#fold} to each candidate's {@code street} and confirms it contains the folded
 * fragment — catching stored {@code 臺…} rows that SQLite {@code LIKE} won't fold (FR-010) — and (b)
 * ranks ascending by haversine distance to the anchor, truncating to {@code limit} (FR-011).
 *
 * <p>Keeping this in one tested place means the ATAK-native and xerial facades produce identical
 * results. Pure logic; never throws.
 */
public final class StreetCandidateRanker {

  private static final double EARTH_R_M = 6_371_000.0;

  private StreetCandidateRanker() {}

  /** A raw row pulled by a facade before folding/ranking. */
  public static final class Raw {
    public final double lat;
    public final double lon;
    public final String displayName;
    public final String displayNameHalfwidth;
    public final String street;
    public final String number;

    public Raw(
        double lat,
        double lon,
        String displayName,
        String displayNameHalfwidth,
        String street,
        String number) {
      this.lat = lat;
      this.lon = lon;
      this.displayName = displayName;
      this.displayNameHalfwidth = displayNameHalfwidth;
      this.street = street;
      this.number = number;
    }
  }

  /**
   * Fold-filter + distance-rank.
   *
   * @param rows raw rows from the facade's {@code WHERE township=? AND street LIKE ?} query
   * @param foldedFragment the query fragment AFTER {@link StreetTextNormaliser#fold}
   * @param anchorLat anchor latitude for ranking
   * @param anchorLon anchor longitude for ranking
   * @param limit max rows to return ({@code <= 0} ⇒ no cap)
   */
  public static List<AddressCandidate> rank(
      List<Raw> rows, String foldedFragment, double anchorLat, double anchorLon, int limit) {
    List<AddressCandidate> out = new ArrayList<>();
    if (rows == null) return out;
    String frag = foldedFragment == null ? "" : foldedFragment;
    for (Raw r : rows) {
      String foldedStreet = StreetTextNormaliser.fold(r.street);
      // Defence-in-depth: the SQL LIKE already prefiltered, but it can't fold 臺↔台. Confirm the
      // folded street actually contains the folded fragment so a 台-typed fragment matches a
      // 臺-stored street and vice-versa.
      if (!frag.isEmpty() && !foldedStreet.contains(frag)) {
        continue;
      }
      double d = haversine(anchorLat, anchorLon, r.lat, r.lon);
      out.add(
          new AddressCandidate(
              r.lat, r.lon, r.displayName, r.displayNameHalfwidth, r.street, r.number, d));
    }
    out.sort(Comparator.comparingDouble(AddressCandidate::distanceMeters));
    if (limit > 0 && out.size() > limit) {
      return new ArrayList<>(out.subList(0, limit));
    }
    return out;
  }

  static double haversine(double lat1, double lon1, double lat2, double lon2) {
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double dPhi = Math.toRadians(lat2 - lat1);
    double dLambda = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
    return 2 * EARTH_R_M * Math.asin(Math.sqrt(a));
  }
}
