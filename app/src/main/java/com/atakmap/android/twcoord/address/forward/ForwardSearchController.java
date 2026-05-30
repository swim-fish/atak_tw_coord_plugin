package com.atakmap.android.twcoord.address.forward;

import com.atakmap.android.twcoord.address.AddressDatabaseFacade;
import com.atakmap.android.twcoord.address.boundary.LocalityResult;
import com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade;
import com.atakmap.coremap.log.Log;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Pure-logic core of the forward-search funnel (county → 鄉鎮市區 → street → pin), per {@code
 * contracts/forward-search-controller.md}. The {@code ForwardSearchReceiver} DropDownReceiver is a
 * thin view over this; all the funnel rules live here so they are JVM-testable.
 *
 * <p>Key invariants: county defaults to the map centre (FR-005); the county list comes from the
 * boundary data (FR-006); the district is pre-highlighted only for SELF/MAP_CENTER (FR-007); the
 * single county's place DB is opened lazily, only at {@link #search} (FR-008 / SC-007); matching is
 * substring incl. {@code 段} with 臺↔台/width folding (FR-009/010); candidates rank by distance
 * (FR-011). Every method swallows {@link Throwable} → safe default (Constitution VI).
 */
public final class ForwardSearchController {

  private static final String TAG = "ForwardSearchController";

  /** Coastline tolerance for seeding the locality (metres). */
  private static final double SEED_SNAP_M = 1000.0;

  private final TownshipBoundaryFacade boundary; // nullable ⇒ boundary data absent
  private final Function<String, AddressDatabaseFacade> facadeForCounty;

  private ForwardSearchQuery query;
  private String suggestedDistrict; // set during seedCounty/chooseCounty for SELF/MAP_CENTER

  public ForwardSearchController(
      TownshipBoundaryFacade boundary, Function<String, AddressDatabaseFacade> facadeForCounty) {
    this.boundary = boundary;
    this.facadeForCounty = facadeForCounty;
    this.query = ForwardSearchQuery.initial(0, 0);
  }

  // ----------------------------------------------------------------------
  // Stage ① — seed + select county
  // ----------------------------------------------------------------------

  /**
   * Seed the funnel from the current anchors. Resolves the map-centre and self localities from the
   * boundary layer (no place DB opened), defaults to the map-centre county, and records the
   * map-centre district as the suggestion. The distance anchor is the map centre.
   *
   * @param selfLat self-marker latitude, or {@code null} if no self-marker
   * @param selfLon self-marker longitude, or {@code null} if no self-marker
   */
  public CountySeed seedCounty(double mapLat, double mapLon, Double selfLat, Double selfLon) {
    query = ForwardSearchQuery.initial(mapLat, mapLon);
    suggestedDistrict = null;
    try {
      LocalityResult mc = boundary != null ? boundary.localityAt(mapLat, mapLon, SEED_SNAP_M) : null;
      LocalityResult sf =
          (boundary != null && selfLat != null && selfLon != null)
              ? boundary.localityAt(selfLat, selfLon, SEED_SNAP_M)
              : null;
      String mcCounty = mc != null ? mc.county() : null;
      String mcDistrict = mc != null ? mc.district() : null;
      String sfCounty = sf != null ? sf.county() : null;
      String sfDistrict = sf != null ? sf.district() : null;

      // FR-005: default to map-centre county; fall back to self when map centre has none.
      String defCounty;
      CountySource defSource;
      if (mcCounty != null) {
        defCounty = mcCounty;
        defSource = CountySource.MAP_CENTER;
        suggestedDistrict = mcDistrict;
      } else if (sfCounty != null) {
        defCounty = sfCounty;
        defSource = CountySource.SELF;
        suggestedDistrict = sfDistrict;
      } else {
        defCounty = null;
        defSource = null;
      }
      if (defCounty != null) {
        query = query.withCounty(defCounty, defSource);
      }
      return new CountySeed(
          defCounty, defSource, sfCounty, sfDistrict, mcCounty, mcDistrict);
    } catch (Throwable t) {
      Log.w(TAG, "seedCounty threw", t);
      return new CountySeed(null, null, null, null, null, null);
    }
  }

  /** Counties present in the installed boundary data (FR-006). Empty if boundary data absent. */
  public List<String> countyList() {
    try {
      return boundary != null ? boundary.counties() : Collections.emptyList();
    } catch (Throwable t) {
      Log.w(TAG, "countyList threw", t);
      return Collections.emptyList();
    }
  }

  /** Select a county. For {@link CountySource#LIST} there is no suggested district. */
  public void chooseCounty(String county, CountySource source) {
    query = query.withCounty(county, source);
    if (source == CountySource.LIST) {
      suggestedDistrict = null;
    }
    // For SELF/MAP_CENTER the suggestion set during seedCounty is retained only if the chosen
    // county matches the seeded one; otherwise the operator switched counties so there is no
    // detected district to suggest.
    if (suggestedDistrict != null && !districts().contains(suggestedDistrict)) {
      suggestedDistrict = null;
    }
  }

  // ----------------------------------------------------------------------
  // Stage ② — districts
  // ----------------------------------------------------------------------

  /** The chosen county's 鄉鎮市區 list (FR-007). Empty if no county chosen / boundary absent. */
  public List<String> districts() {
    String county = query.county();
    if (county == null) return Collections.emptyList();
    try {
      return boundary != null ? boundary.districtsOf(county) : Collections.emptyList();
    } catch (Throwable t) {
      Log.w(TAG, "districts threw", t);
      return Collections.emptyList();
    }
  }

  /** The operator's own district to pre-highlight, or {@code null} (LIST source / no locality). */
  public String suggestedDistrict() {
    return suggestedDistrict;
  }

  public void chooseDistrict(String district) {
    query = query.withDistrict(district);
  }

  // ----------------------------------------------------------------------
  // Stage ③ — street match (opens the county place DB for the first time)
  // ----------------------------------------------------------------------

  /**
   * Street candidates for the current county+district, ranked by distance to the anchor. This is the
   * first call that resolves the county's {@link AddressDatabaseFacade} (FR-008 / SC-007). Returns
   * empty when county/district unset, the facade is unavailable, or the fragment is blank.
   */
  public List<AddressCandidate> search(String streetFragment, int limit) {
    query = query.withStreetFragment(streetFragment);
    String county = query.county();
    String district = query.district();
    if (county == null || district == null) return Collections.emptyList();
    String folded = StreetTextNormaliser.fold(streetFragment);
    if (folded.isEmpty()) return Collections.emptyList();
    try {
      AddressDatabaseFacade facade = facadeForCounty.apply(county);
      if (facade == null) return Collections.emptyList();
      return facade.streetCandidates(
          district, folded, query.anchorLat(), query.anchorLon(), limit);
    } catch (Throwable t) {
      Log.w(TAG, "search threw", t);
      return Collections.emptyList();
    }
  }

  // ----------------------------------------------------------------------
  // Stage ④ — pin
  // ----------------------------------------------------------------------

  /**
   * Re-run the street search and narrow to a house number (FR-012). A blank/unparseable number
   * falls back to the nearest-by-distance candidate list. Folds digit width on the number.
   */
  public List<AddressCandidate> withHouseNumber(String houseNumber, int limit) {
    query = query.withHouseNumber(houseNumber);
    // Pull a generous candidate set for the current street, then filter by number app-side.
    List<AddressCandidate> base = search(query.streetFragment(), 0);
    String foldedNum = StreetTextNormaliser.fold(houseNumber);
    if (foldedNum.isEmpty()) {
      return capped(base, limit);
    }
    java.util.List<AddressCandidate> filtered = new java.util.ArrayList<>();
    for (AddressCandidate c : base) {
      String foldedRowNum = StreetTextNormaliser.fold(c.number());
      // The house number the operator types is a prefix of the stored number (e.g. "123" matches
      // "123號" / "123-1號"). Fold both sides so 全形 digits + 之/- normalise.
      if (foldedRowNum.contains(foldedNum)) {
        filtered.add(c);
      }
    }
    if (filtered.isEmpty()) {
      // No exact number hit — fall back to nearest-by-distance so the operator still gets a pin.
      return capped(base, limit);
    }
    return capped(filtered, limit);
  }

  private static List<AddressCandidate> capped(List<AddressCandidate> list, int limit) {
    if (limit > 0 && list.size() > limit) {
      return new java.util.ArrayList<>(list.subList(0, limit));
    }
    return list;
  }

  /** Returns the chosen candidate unchanged — the receiver triggers GoTo; no pan here (FR-013). */
  public AddressCandidate confirm(AddressCandidate chosen) {
    return chosen;
  }

  public ForwardSearchQuery state() {
    return query;
  }
}
