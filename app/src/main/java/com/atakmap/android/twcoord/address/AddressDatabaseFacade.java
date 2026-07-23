package com.atakmap.android.twcoord.address;

import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressDraft;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.ForwardCandidatePool;
import com.atakmap.android.twcoord.address.lookup.ForwardCandidateShortlist;
import com.atakmap.android.twcoord.address.lookup.ResultOrdering;
import com.atakmap.android.twcoord.address.lookup.StreetCandidateRanker;
import com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * SDK seam over the runtime SQLite reader the resolver consumes. Wraps the {@code
 * android.database.sqlite.SQLiteDatabase} opened against the active dataset's {@code
 * places-&lt;county&gt;.sqlite} file. JVM unit tests inject a mock; production code uses {@code
 * SqliteAddressDatabase}.
 *
 * <p>Per {@code contracts/address-database-facade.md} the facade has two public methods plus {@link
 * AutoCloseable#close()}. Implementations MUST NOT throw out of any public method — IO / SQL
 * failures return safe defaults (null from {@link #nearestWithin}, an empty-but-valid {@link
 * GeneratorMetadata} from {@link #readMetadata}) after logging at {@code Log.w} — so a corrupt
 * dataset cannot crash the host process (Constitution VI).
 */
public interface AddressDatabaseFacade extends AutoCloseable {

  /** Read the {@code metadata} table verbatim. Used by the Offline Address page. */
  GeneratorMetadata readMetadata();

  /**
   * Return the single nearest address record within {@code radiusMeters} of {@code (lat, lon)}, or
   * {@code null} if no record falls inside the radius. Implementation runs the R*Tree bbox query
   * joined to {@code places} then refines by haversine distance (per {@code
   * contracts/address-database-facade.md}).
   */
  AddressRecord nearestWithin(double lat, double lon, double radiusMeters);

  /**
   * Feature 006 (T022): district-scoped, distance-ranked street lookup for forward search. Filters
   * {@code places} to {@code district} (matched against the {@code township} column),
   * street-matches {@code foldedFragment} as a prefix/substring spanning the {@code 段} suffix
   * (never exact {@code =}, FR-009), folds {@code 臺}↔{@code 台} + digit width on both sides
   * (FR-010), ranks ascending by haversine distance to the anchor, and returns at most {@code
   * limit} rows.
   *
   * <p>Empty-street rows ({@code places.street} NULL/blank — located by a named 巷/莊/新村 in {@code
   * places.area}) match on and return their {@code area} as the locator, so they surface under
   * their locality name instead of vanishing from the funnel.
   *
   * <p>Default implementation returns an empty list so test doubles and the legacy single-active
   * facades that don't implement forward search keep compiling. Production facades override it.
   * Never throws (Constitution VI) — SQL/IO faults yield an empty list.
   *
   * @param district the 鄉鎮市區 name (already {@code 臺}→{@code 台} normalised, matches {@code
   *     places.township})
   * @param foldedFragment the street fragment AFTER {@link
   *     com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser#fold}
   */
  default java.util.List<com.atakmap.android.twcoord.address.lookup.AddressCandidate>
      streetCandidates(
          String district, String foldedFragment, double anchorLat, double anchorLon, int limit) {
    return java.util.Collections.emptyList();
  }

  /**
   * Returns one deterministically ordered retrieval category. Production SQLite implementations
   * override this method so every category is bounded by SQL. The default keeps injected facades
   * source-compatible and applies the same pool ordering in memory.
   */
  default List<AddressCandidate> forwardCandidatePool(
      AddressDraft draft,
      String foldedStreetFragment,
      Wgs84 anchorPoint,
      ForwardCandidatePool pool,
      int limit) {
    if (draft == null || pool == null || limit <= 0) {
      return java.util.Collections.emptyList();
    }
    double anchorLat = anchorPoint != null ? anchorPoint.latitudeDeg() : 0.0;
    double anchorLon = anchorPoint != null ? anchorPoint.longitudeDeg() : 0.0;
    List<AddressCandidate> rows =
        streetCandidates(
            draft.components().districtTownship(), foldedStreetFragment, anchorLat, anchorLon, 0);
    return StreetCandidateRanker.reorderForPool(
        rows,
        pool,
        draft.components().roadLocality(),
        draft.components().tail(),
        anchorPoint != null,
        Math.min(limit, ForwardCandidateShortlist.SQL_POOL_LIMIT));
  }

  /**
   * Bounded full-address lookup built on the established street-then-house-number funnel. Exactness
   * accepts either canonical full-address equality or an exact street/section/tail match when the
   * operator omits a TGOS village/neighbourhood prefix. A nearby house number remains PARTIAL.
   */
  default List<AddressCandidate> fullAddressCandidates(
      AddressDraft draft, Wgs84 anchorPoint, int limit) {
    return fullAddressCandidates(draft, anchorPoint, ResultOrdering.DISTANCE, limit);
  }

  /**
   * Applies the operator-selected ordering before the visible candidate limit. The legacy
   * three-argument method remains the retrieval/classification seam so existing database and test
   * implementations do not need a parallel override.
   */
  default List<AddressCandidate> fullAddressCandidates(
      AddressDraft draft, Wgs84 anchorPoint, ResultOrdering ordering, int limit) {
    if (draft == null
        || limit <= 0
        || draft.components().districtTownship().isEmpty()
        || draft.components().roadLocality().isEmpty()) {
      return java.util.Collections.emptyList();
    }
    int visibleLimit = Math.min(limit, ForwardCandidateShortlist.SQL_POOL_LIMIT);
    int poolLimit = ForwardCandidateShortlist.SQL_POOL_LIMIT;
    String streetFragment =
        StreetTextNormaliser.fold(
            streetFamilyFragment(streetFragmentAfterLocality(draft.components().roadLocality())));
    TaiwanAddressParser parser = new TaiwanAddressParser();

    List<AddressCandidate> exact =
        exactOnly(
            classify(
                forwardCandidatePool(
                    draft, streetFragment, anchorPoint, ForwardCandidatePool.EXACT, poolLimit),
                draft,
                parser));
    if (!exact.isEmpty()) {
      return ForwardCandidateShortlist.select(
          exact,
          java.util.Collections.emptyList(),
          java.util.Collections.emptyList(),
          java.util.Collections.emptyList(),
          java.util.Collections.emptyList(),
          visibleLimit);
    }

    if (!containsDigit(draft.components().tail())) {
      ForwardCandidatePool pool =
          ordering == ResultOrdering.DISTANCE && anchorPoint != null
              ? ForwardCandidatePool.DISTANCE
              : ForwardCandidatePool.TEXT_PREFIX;
      List<AddressCandidate> roadCandidates =
          classify(
              forwardCandidatePool(draft, streetFragment, anchorPoint, pool, poolLimit),
              draft,
              parser);
      return pool == ForwardCandidatePool.DISTANCE
          ? ForwardCandidateShortlist.select(
              java.util.Collections.emptyList(),
              java.util.Collections.emptyList(),
              java.util.Collections.emptyList(),
              roadCandidates,
              java.util.Collections.emptyList(),
              visibleLimit)
          : ForwardCandidateShortlist.select(
              java.util.Collections.emptyList(),
              roadCandidates,
              java.util.Collections.emptyList(),
              java.util.Collections.emptyList(),
              java.util.Collections.emptyList(),
              visibleLimit);
    }

    List<AddressCandidate> text =
        classify(
            forwardCandidatePool(
                draft, streetFragment, anchorPoint, ForwardCandidatePool.TEXT_PREFIX, poolLimit),
            draft,
            parser);
    List<AddressCandidate> numeric =
        classify(
            forwardCandidatePool(
                draft,
                streetFragment,
                anchorPoint,
                ForwardCandidatePool.NUMERIC_NEAREST,
                poolLimit),
            draft,
            parser);
    List<AddressCandidate> distance =
        anchorPoint == null
            ? java.util.Collections.emptyList()
            : classify(
                forwardCandidatePool(
                    draft, streetFragment, anchorPoint, ForwardCandidatePool.DISTANCE, poolLimit),
                draft,
                parser);
    List<AddressCandidate> fallback =
        classify(
            forwardCandidatePool(
                draft, streetFragment, anchorPoint, ForwardCandidatePool.FALLBACK, poolLimit),
            draft,
            parser);

    List<AddressCandidate> exactFromSecondary = new ArrayList<>();
    collectExact(exactFromSecondary, text);
    collectExact(exactFromSecondary, numeric);
    collectExact(exactFromSecondary, distance);
    collectExact(exactFromSecondary, fallback);
    return ForwardCandidateShortlist.select(
        exactFromSecondary, text, numeric, distance, fallback, visibleLimit);
  }

  /**
   * TGOS display names place village/neighbourhood text before the database's separate street
   * value. Keep the original fragment first so real street names containing 里/村 are unaffected; use
   * this suffix only when that direct query has no result.
   */
  private static String streetFragmentAfterLocality(String roadLocality) {
    if (roadLocality == null || roadLocality.isEmpty()) return "";
    int localityEnd =
        Math.max(
            roadLocality.lastIndexOf('鄰'),
            Math.max(roadLocality.lastIndexOf('里'), roadLocality.lastIndexOf('村')));
    if (localityEnd < 0 || localityEnd + 1 >= roadLocality.length()) return roadLocality;
    String suffix = roadLocality.substring(localityEnd + 1);
    return suffix.contains("大道") || suffix.contains("路") || suffix.contains("街")
        ? suffix
        : roadLocality;
  }

  /**
   * Match the established house-number stage. Narrow only when a candidate's complete parsed tail
   * equals the requested tail; a substring such as {@code 9號} must not select {@code 99號} or {@code
   * 609號}. Retain the road candidates when no exact tail exists so the semantic ranker can order
   * nearby house numbers.
   */
  private static List<AddressCandidate> classify(
      List<AddressCandidate> rows, AddressDraft draft, TaiwanAddressParser parser) {
    List<AddressCandidate> classified = new ArrayList<>();
    if (rows == null) return classified;
    for (AddressCandidate row : rows) {
      String normalized = parser.normalize(row.displayAddress());
      AddressMatchKind kind =
          normalized.equals(draft.normalizedAddress())
                  || matchesStreetAndTailWithoutLocality(draft, row, parser)
              ? AddressMatchKind.EXACT
              : AddressMatchKind.PARTIAL;
      classified.add(row.withMatch(normalized, kind));
    }
    return classified;
  }

  private static List<AddressCandidate> exactOnly(List<AddressCandidate> candidates) {
    List<AddressCandidate> exact = new ArrayList<>();
    collectExact(exact, candidates);
    return exact;
  }

  private static void collectExact(
      List<AddressCandidate> destination, List<AddressCandidate> candidates) {
    for (AddressCandidate candidate : candidates) {
      if (candidate.matchKind() == AddressMatchKind.EXACT) destination.add(candidate);
    }
  }

  private static String streetFamilyFragment(String street) {
    String family = street == null ? "" : street;
    String previous;
    do {
      previous = family;
      family = family.replaceFirst("\\d+(?:段|路|街)$", "");
    } while (!family.equals(previous));
    return family.isEmpty() ? street : family;
  }

  private static boolean containsDigit(String value) {
    if (value == null) return false;
    for (int index = 0; index < value.length(); index++) {
      if (Character.isDigit(value.charAt(index))) return true;
    }
    return false;
  }

  /**
   * Treat the TGOS village/neighbourhood prefix as optional only when every operator-supplied
   * locator after the district still matches: exact street/section, exact parsed tail, and exact
   * unclassified suffix. Duplicate semantic matches remain multiple EXACT candidates, so the
   * controller requires explicit selection.
   */
  private static boolean matchesStreetAndTailWithoutLocality(
      AddressDraft query, AddressCandidate candidate, TaiwanAddressParser parser) {
    String candidateStreet = parser.normalize(candidate.street());
    if (candidateStreet.isEmpty()
        || !candidateStreet.equals(parser.normalize(query.components().roadLocality()))) {
      return false;
    }
    AddressDraft candidateDraft = parser.parse(candidate.displayAddress(), 0L, query.mode());
    return candidateDraft.components().tail().equals(query.components().tail())
        && candidateDraft.unclassifiedText().equals(query.unclassifiedText());
  }

  /**
   * Feature 006 — county-wide street lookup: same fold/rank contract as {@link #streetCandidates}
   * but WITHOUT the {@code township} filter, so the operator can search the whole county when they
   * don't know the 鄉鎮市區 ("全部" / All districts). Distance ranking keeps near matches first, which is
   * what disambiguates same-named streets across districts. Never throws (Constitution VI).
   *
   * @param foldedFragment the street fragment AFTER {@link
   *     com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser#fold}
   */
  default java.util.List<com.atakmap.android.twcoord.address.lookup.AddressCandidate>
      streetCandidatesCountyWide(
          String foldedFragment, double anchorLat, double anchorLon, int limit) {
    return java.util.Collections.emptyList();
  }

  @Override
  void close();

  /**
   * Opens fresh {@link AddressDatabaseFacade} instances for active datasets. {@link
   * AddressSubsystem} holds a {@link Factory} so it can re-open the facade on {@code
   * ACTION_DATASET_CHANGED} without importing {@code Context} directly.
   */
  interface Factory {
    /**
     * Open the DB file in read-only mode. Returns {@code null} if the file is missing or
     * unopenable.
     */
    AddressDatabaseFacade open(File dbFile);
  }
}
