package com.atakmap.android.twcoord.address;

import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressDraft;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
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
   * Bounded full-address lookup built on the established street-then-house-number funnel. Exactness
   * accepts either canonical full-address equality or an exact street/section/tail match when the
   * operator omits a TGOS village/neighbourhood prefix. A nearby house number remains PARTIAL.
   */
  default List<AddressCandidate> fullAddressCandidates(
      AddressDraft draft, Wgs84 anchorPoint, int limit) {
    if (draft == null || limit <= 0 || draft.components().districtTownship().isEmpty()) {
      return java.util.Collections.emptyList();
    }
    Wgs84 anchor =
        anchorPoint != null ? anchorPoint : new Wgs84(23.7, 120.9, 1L, Wgs84.Source.MAP_CENTRE);
    String district = draft.components().districtTownship();
    String roadLocality = draft.components().roadLocality();
    List<AddressCandidate> rows =
        streetCandidates(
            district,
            StreetTextNormaliser.fold(roadLocality),
            anchor.latitudeDeg(),
            anchor.longitudeDeg(),
            0);
    if (rows.isEmpty()) {
      String streetOnly = streetFragmentAfterLocality(roadLocality);
      if (!streetOnly.equals(roadLocality)) {
        rows =
            streetCandidates(
                district,
                StreetTextNormaliser.fold(streetOnly),
                anchor.latitudeDeg(),
                anchor.longitudeDeg(),
                0);
      }
      String sectionFamily = streetOnly.replaceFirst("\\d+段$", "");
      if (rows.isEmpty() && !sectionFamily.equals(streetOnly)) {
        rows =
            streetCandidates(
                district,
                StreetTextNormaliser.fold(sectionFamily),
                anchor.latitudeDeg(),
                anchor.longitudeDeg(),
                0);
      }
    }
    rows = narrowByAddressTail(rows, draft.components().tail());
    TaiwanAddressParser parser = new TaiwanAddressParser();
    List<AddressCandidate> classified = new ArrayList<>();
    for (AddressCandidate row : rows) {
      String normalized = parser.normalize(row.displayAddress());
      AddressMatchKind kind =
          normalized.equals(draft.normalizedAddress())
                  || matchesStreetAndTailWithoutLocality(draft, row, parser)
              ? AddressMatchKind.EXACT
              : AddressMatchKind.PARTIAL;
      classified.add(row.withMatch(normalized, kind));
    }
    classified.sort(
        Comparator.comparingInt(
                (AddressCandidate candidate) ->
                    candidate.matchKind() == AddressMatchKind.EXACT ? 0 : 1)
            .thenComparingDouble(AddressCandidate::distanceMeters)
            .thenComparing(AddressCandidate::normalizedAddress)
            .thenComparing(AddressCandidate::candidateId));
    if (classified.size() > limit) {
      return new ArrayList<>(classified.subList(0, limit));
    }
    return classified;
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
   * Match the established house-number stage: narrow using either the dedicated number or the full
   * half-width display address, and retain the road candidates when no tail matches.
   */
  private static List<AddressCandidate> narrowByAddressTail(
      List<AddressCandidate> rows, String addressTail) {
    String foldedTail = StreetTextNormaliser.fold(addressTail);
    if (rows == null || rows.isEmpty() || foldedTail.isEmpty()) return rows;
    List<AddressCandidate> narrowed = new ArrayList<>();
    for (AddressCandidate candidate : rows) {
      String foldedNumber = StreetTextNormaliser.fold(candidate.number());
      String display =
          candidate.displayNameHalfwidth().isEmpty()
              ? candidate.displayAddress()
              : candidate.displayNameHalfwidth();
      if (foldedNumber.contains(foldedTail)
          || StreetTextNormaliser.fold(display).contains(foldedTail)) {
        narrowed.add(candidate);
      }
    }
    return narrowed.isEmpty() ? rows : narrowed;
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
