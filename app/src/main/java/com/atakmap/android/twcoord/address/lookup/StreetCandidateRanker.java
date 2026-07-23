package com.atakmap.android.twcoord.address.lookup;

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
  private static final TaiwanAddressParser ADDRESS_NORMALIZER = new TaiwanAddressParser();

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
    List<AddressCandidate> out = materialize(rows, foldedFragment, anchorLat, anchorLon, true, 0);
    out.sort(
        Comparator.comparingDouble(AddressCandidate::distanceMeters)
            .thenComparing(AddressCandidate::candidateId));
    if (limit > 0 && out.size() > limit) {
      return new ArrayList<>(out.subList(0, limit));
    }
    return out;
  }

  /** Fold-filters raw rows and computes optional distance without changing SQL result order. */
  public static List<AddressCandidate> materialize(
      List<Raw> rows,
      String foldedFragment,
      double anchorLat,
      double anchorLon,
      boolean anchorAvailable,
      int limit) {
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
      double d =
          anchorAvailable
              ? haversine(anchorLat, anchorLon, r.lat, r.lon)
              : Double.POSITIVE_INFINITY;
      out.add(
          new AddressCandidate(
              r.lat, r.lon, r.displayName, r.displayNameHalfwidth, r.street, r.number, d));
      if (limit > 0 && out.size() >= limit) return out;
    }
    return out;
  }

  /** In-memory compatibility ordering for facades that do not implement bounded SQL pools. */
  public static List<AddressCandidate> reorderForPool(
      List<AddressCandidate> results,
      ForwardCandidatePool pool,
      String foldedFragment,
      String foldedTail,
      boolean anchorAvailable,
      int limit) {
    List<AddressCandidate> out = new ArrayList<>();
    if (results == null || pool == null || limit <= 0) return out;
    out.addAll(results);
    String fragment = normalizeForSimilarity(foldedFragment).trim();
    String tail = normalizeForSimilarity(foldedTail).trim();
    Comparator<AddressCandidate> stable =
        Comparator.comparing(AddressCandidate::normalizedAddress)
            .thenComparing(AddressCandidate::candidateId);
    switch (pool) {
      case EXACT:
        out.sort(
            Comparator.comparingInt(
                    (AddressCandidate candidate) ->
                        candidateAddressTail(candidate).equals(tail) ? 0 : 1)
                .thenComparingInt(candidate -> -similarityBand(candidate, fragment))
                .thenComparingInt(candidate -> laneAlleyPenalty(candidate, tail))
                .thenComparing(stable));
        break;
      case TEXT_PREFIX:
        out.sort(
            Comparator.comparingInt(
                    (AddressCandidate candidate) -> -similarityBand(candidate, fragment))
                .thenComparingInt(
                    candidate ->
                        candidateAddressTail(candidate).startsWith(primaryDigits(tail)) ? 0 : 1)
                .thenComparingInt(candidate -> laneAlleyPenalty(candidate, tail))
                .thenComparingInt(candidate -> candidateAddressTail(candidate).length())
                .thenComparing(stable));
        break;
      case NUMERIC_NEAREST:
        out.sort(
            Comparator.comparingInt(
                    (AddressCandidate candidate) -> -similarityBand(candidate, fragment))
                .thenComparingInt(candidate -> laneAlleyPenalty(candidate, tail))
                .thenComparingInt(candidate -> houseNumberProximity(candidate, tail))
                .thenComparingDouble(AddressCandidate::distanceMeters)
                .thenComparing(stable));
        break;
      case DISTANCE:
        if (!anchorAvailable) return new ArrayList<>();
        out.sort(
            Comparator.comparingInt(
                    (AddressCandidate candidate) -> -similarityBand(candidate, fragment))
                .thenComparingDouble(AddressCandidate::distanceMeters)
                .thenComparing(stable));
        break;
      case FALLBACK:
        out.sort(
            Comparator.comparingInt(
                    (AddressCandidate candidate) -> -similarityBand(candidate, fragment))
                .thenComparingInt(
                    candidate -> containsLaneOrAlley(candidateAddressTail(candidate)) ? 0 : 1)
                .thenComparingDouble(AddressCandidate::distanceMeters)
                .thenComparing(stable));
        break;
    }
    return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
  }

  /**
   * Feature 007 US1 — re-sort an already-built candidate list by {@code ordering} without changing
   * which candidates are present (FR-002 / FR-005). Operates on the currently displayed list (the
   * distance-bounded top-N {@code search(...)} returned); it does NOT pull in matches outside that
   * set. Pure; never throws; returns a new list (input untouched).
   *
   * <ul>
   *   <li>{@link ResultOrdering#DISTANCE} — sort by {@code distanceMeters} ascending (the order the
   *       facade already returns; effectively identity for an already-distance-sorted input).
   *   <li>{@link ResultOrdering#MOST_SIMILAR} — sort by textual-match band of the folded candidate
   *       street (falling back to the display name when street is empty) against {@code
   *       foldedFragment}: exact &gt; prefix &gt; substring (earlier match index wins) &gt; none;
   *       within a band the shorter leftover wins; ties break by {@code distanceMeters} ascending.
   * </ul>
   *
   * @param results the candidate list to re-order (not mutated)
   * @param ordering desired ordering ({@code null} ⇒ DISTANCE)
   * @param foldedFragment the query fragment AFTER {@link StreetTextNormaliser#fold} ({@code
   *     null}/blank ⇒ every candidate scores band 1, so MOST_SIMILAR degrades to distance order)
   */
  public static List<AddressCandidate> reorder(
      List<AddressCandidate> results, ResultOrdering ordering, String foldedFragment) {
    return reorder(results, ordering, foldedFragment, null);
  }

  /**
   * House-number-aware overload. Once a house number is present, both configured orderings preserve
   * address semantics before map distance: exact full address, requested street/section, address
   * structure, numeric house-number proximity, other street/section, then distance. When the query
   * has no lane/alley, direct-road candidates rank ahead of lane/alley candidates. This keeps an
   * exact or nearby number from being hidden by a geographically nearer but textually unrelated
   * row.
   *
   * @param foldedHouseNumber the house-number tail typed on the keypad, AFTER {@link
   *     StreetTextNormaliser#fold} ({@code null}/blank ⇒ neutral, so this collapses to the
   *     street-only behaviour of the 3-arg overload)
   */
  public static List<AddressCandidate> reorder(
      List<AddressCandidate> results,
      ResultOrdering ordering,
      String foldedFragment,
      String foldedHouseNumber) {
    List<AddressCandidate> out = new ArrayList<>();
    if (results == null) return out;
    out.addAll(results);
    ResultOrdering ord = ordering == null ? ResultOrdering.DISTANCE : ordering;
    final String frag = normalizeForSimilarity(foldedFragment).trim();
    final String num = normalizeForSimilarity(foldedHouseNumber).trim();
    if (!num.isEmpty()) {
      out.sort(
          Comparator.comparingInt((AddressCandidate c) -> numberedAddressTier(c, frag))
              .thenComparingInt(c -> laneAlleyPenalty(c, num))
              .thenComparingInt(c -> sameStreetHouseNumberProximity(c, frag, num))
              .thenComparingDouble(AddressCandidate::distanceMeters)
              .thenComparing(AddressCandidate::normalizedAddress)
              .thenComparing(AddressCandidate::candidateId));
      return out;
    }
    if (ord == ResultOrdering.DISTANCE) {
      out.sort(
          Comparator.comparingDouble(AddressCandidate::distanceMeters)
              .thenComparing(AddressCandidate::normalizedAddress)
              .thenComparing(AddressCandidate::candidateId));
      return out;
    }
    if (frag.isEmpty()) {
      // No fragment to match on — every candidate is band 1 with no leftover signal, so degrade to
      // pure distance order (per this method's contract). Short-circuit rather than fall through
      // the
      // band comparator, whose leftoverLength tiebreak would otherwise sort by street-name length.
      out.sort(
          Comparator.comparingDouble(AddressCandidate::distanceMeters)
              .thenComparing(AddressCandidate::normalizedAddress)
              .thenComparing(AddressCandidate::candidateId));
      return out;
    }
    out.sort(
        Comparator.comparingInt((AddressCandidate c) -> -similarityBand(c, frag))
            .thenComparingInt(c -> houseNumberProximity(c, num))
            .thenComparingInt(c -> matchIndex(c, frag))
            .thenComparingInt(c -> leftoverLength(c, frag))
            .thenComparingDouble(AddressCandidate::distanceMeters)
            .thenComparing(AddressCandidate::normalizedAddress)
            .thenComparing(AddressCandidate::candidateId));
    return out;
  }

  /**
   * 0 = classified exact full address; 1 = requested street/section or its segment family; 2 =
   * another street/section.
   */
  private static int numberedAddressTier(AddressCandidate candidate, String foldedFragment) {
    if (candidate.matchKind() == AddressMatchKind.EXACT) return 0;
    return similarityBand(candidate, foldedFragment) >= 3 ? 1 : 2;
  }

  private static int sameStreetHouseNumberProximity(
      AddressCandidate candidate, String foldedFragment, String foldedHouseNumber) {
    return similarityBand(candidate, foldedFragment) >= 3
        ? houseNumberProximity(candidate, foldedHouseNumber)
        : 0;
  }

  /**
   * A direct-road query must not surface addresses whose matching final number belongs inside a
   * lane/alley. Queries that explicitly include a lane/alley keep every candidate neutral.
   */
  private static int laneAlleyPenalty(AddressCandidate candidate, String foldedHouseNumber) {
    if (containsLaneOrAlley(foldedHouseNumber)) return 0;
    return containsLaneOrAlley(candidateAddressTail(candidate)) ? 1 : 0;
  }

  private static boolean containsLaneOrAlley(String value) {
    return value != null && (value.indexOf('巷') >= 0 || value.indexOf('弄') >= 0);
  }

  /**
   * Numeric closeness of a candidate's house number to the typed one (smaller = better), the {@code
   * MOST_SIMILAR} secondary key. Returns {@code 0} (neutral) when nothing comparable was typed, and
   * {@link Integer#MAX_VALUE} (sorted last) when the candidate has no leading integer to compare —
   * so empty-street/area rows fall below any real number match once a number is typed.
   */
  static int houseNumberProximity(AddressCandidate c, String foldedHouseNumber) {
    Integer typed = leadingInt(foldedHouseNumber);
    if (typed == null) return 0; // nothing typed (or no digits) — neutral, distance decides
    Integer got = leadingInt(candidateAddressTail(c));
    if (got == null) return Integer.MAX_VALUE;
    return Math.abs(got - typed);
  }

  /**
   * Returns the complete tail after the stored street so a lane address such as {@code
   * 臺灣大道三段506巷9號} compares as house number 506, not the dedicated final-number column's 9.
   */
  private static String candidateAddressTail(AddressCandidate candidate) {
    String display =
        normalizeForSimilarity(
            candidate.displayNameHalfwidth().isEmpty()
                ? candidate.displayAddress()
                : candidate.displayNameHalfwidth());
    String street = similarityText(candidate);
    int streetAt = display.lastIndexOf(street);
    if (!street.isEmpty() && streetAt >= 0) {
      return display.substring(streetAt + street.length());
    }
    return normalizeForSimilarity(candidate.number());
  }

  /** The first run of decimal digits in {@code s} as an int, or {@code null} if none / overflow. */
  private static Integer leadingInt(String s) {
    if (s == null) return null;
    int n = s.length();
    int i = 0;
    while (i < n && !Character.isDigit(s.charAt(i))) i++;
    if (i >= n) return null;
    int start = i;
    while (i < n && Character.isDigit(s.charAt(i))) i++;
    try {
      return Integer.parseInt(s.substring(start, i));
    } catch (NumberFormatException e) {
      return null; // absurdly long digit run — treat as incomparable
    }
  }

  private static String primaryDigits(String value) {
    if (value == null) return "";
    int index = 0;
    while (index < value.length() && !Character.isDigit(value.charAt(index))) index++;
    int start = index;
    while (index < value.length() && Character.isDigit(value.charAt(index))) index++;
    return start < index ? value.substring(start, index) : "";
  }

  /** Folded text used for similarity: the street, or the display name when street is empty. */
  private static String similarityText(AddressCandidate c) {
    String street = c.street();
    String base = (street != null && !street.isEmpty()) ? street : c.displayName();
    return normalizeForSimilarity(base);
  }

  private static String normalizeForSimilarity(String value) {
    return StreetTextNormaliser.fold(ADDRESS_NORMALIZER.normalize(value));
  }

  /** 4 = exact, 3 = prefix, 2 = substring, 1 = none/blank-fragment. Higher is more similar. */
  static int similarityBand(AddressCandidate c, String foldedFragment) {
    if (foldedFragment == null || foldedFragment.isEmpty()) return 1;
    String t = similarityText(c);
    if (t.equals(foldedFragment)) return 4;
    if (t.startsWith(foldedFragment)) return 3;
    if (t.contains(foldedFragment)) return 2;
    return 1;
  }

  /** Match index for the substring band (earlier = better); large sentinel when no match. */
  private static int matchIndex(AddressCandidate c, String foldedFragment) {
    if (foldedFragment == null || foldedFragment.isEmpty()) return Integer.MAX_VALUE;
    int i = similarityText(c).indexOf(foldedFragment);
    return i < 0 ? Integer.MAX_VALUE : i;
  }

  /** Leftover characters after removing the fragment length (shorter = better). */
  private static int leftoverLength(AddressCandidate c, String foldedFragment) {
    int fragLen = foldedFragment == null ? 0 : foldedFragment.length();
    return Math.max(0, similarityText(c).length() - fragLen);
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
