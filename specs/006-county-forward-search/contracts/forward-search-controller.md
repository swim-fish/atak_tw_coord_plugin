# Contract: ForwardSearchController (+ StreetTextNormaliser)

**Modules** (NEW):
- `app/src/main/java/com/atakmap/android/twcoord/address/forward/ForwardSearchController.java`
- `app/src/main/java/com/atakmap/android/twcoord/address/forward/StreetTextNormaliser.java`
- value types: `CountySource`, `ForwardSearchQuery`, `AddressCandidate`

Pure-logic core of the funnel (data-model §3). No Android/ATAK — the
`ForwardSearchReceiver` (DropDownReceiver) is a thin view over this. JVM-testable.

## Interface

```java
public final class ForwardSearchController {

  public ForwardSearchController(
      TownshipBoundaryFacade boundary,
      java.util.function.Function<String, AddressDatabaseFacade> facadeForCounty); // registry lookup

  // Stage ① — seed + select county.
  /** Seed from current anchors; returns the default county (MAP_CENTER pref) + a SELF alt. */
  CountySeed seedCounty(double mapLat, double mapLon, Double selfLat, Double selfLon);
  java.util.List<String> countyList();               // FR-006 (from boundary)
  void chooseCounty(String county, CountySource src);

  // Stage ② — districts.
  java.util.List<String> districts();                // for the chosen county
  String suggestedDistrict();                         // pre-highlight when SELF/MAP_CENTER
  void chooseDistrict(String district);

  // Stage ③ — street match (opens the county place DB for the first time).
  java.util.List<AddressCandidate> search(String streetFragment, int limit);

  // Stage ④ — pin.
  java.util.List<AddressCandidate> withHouseNumber(String houseNumber, int limit); // blank ⇒ nearest
  AddressCandidate confirm(AddressCandidate chosen);  // returns the GoTo target (no pan here)

  ForwardSearchQuery state();
}

public final class StreetTextNormaliser {
  /** 臺→台, fullwidth digits→halfwidth, 之→-, trim. Applied to query AND candidate. */
  public static String fold(String s);
}
```

`CountySeed`: `{ String defaultCounty, CountySource defaultSource,
String selfCounty (nullable), String mapCenterCounty (nullable) }`.

## Invariants

1. **Map-centre default.** When `selfCounty != mapCenterCounty`,
   `seedCounty` returns `defaultCounty = mapCenterCounty`,
   `defaultSource = MAP_CENTER`; `selfCounty` offered as the one-tap alt (spec
   clarification / FR-005).
2. **County list from data.** `countyList()` == `boundary.counties()`; never a
   hard-coded 22-county table (FR-006).
3. **District pre-highlight.** `suggestedDistrict()` is non-null only when the
   county was chosen via SELF/MAP_CENTER and the anchor's locality district is in
   the chosen county (FR-007).
4. **Place DB opened lazily at ③.** `search` is the first call that resolves a
   `AddressDatabaseFacade` for the county (FR-008/SC-007). `seedCounty`/
   `districts` touch only the boundary facade.
5. **Substring incl. `段`.** Matching uses `street LIKE fragment%` / `%fragment%`,
   never `=` (FR-009). Verified: `向上路` `=`→0, `LIKE`→1,645.
6. **Glyph/width fold.** `fold` applied to the fragment AND the candidate before
   compare (FR-010). Verified: 4,873 `臺…` street rows; `台灣大道` must match
   `臺灣大道`.
7. **Distance rank.** Candidates sorted ascending by haversine to the query
   anchor; `limit` caps the list (FR-011).
8. **House number optional.** `withHouseNumber("")` ⇒ nearest-by-distance
   (FR-012).
9. **`confirm` does not pan.** It returns the chosen `AddressCandidate`; the
   receiver triggers GoTo only on the explicit confirm tap (FR-013).
10. **No throw.** Any facade error ⇒ empty candidate list + safe state
    (Constitution VI).

## Test plan (`ForwardSearchControllerTest`, `StreetTextNormaliserTest`, JVM)

| # | Scenario | Expected |
|---|---|---|
| 1 | self in 台中市, map-centre in 彰化縣 | seed default=彰化縣/MAP_CENTER; selfCounty=台中市 |
| 2 | self == map-centre county | default=that county; no conflicting alt |
| 3 | countyList | == boundary.counties() (12 in fixture), no hard-coded names |
| 4 | choose county via MAP_CENTER, anchor district known | suggestedDistrict == that district |
| 5 | choose county via LIST | suggestedDistrict == null |
| 6 | search "中山路" in 大甲區 | candidates all in 大甲區, street starts 中山路, incl. 一段/二段 |
| 7 | search "向上路" (exists only as segments) | non-empty (proves substring, not `=`) |
| 8 | search "台灣大道" | matches 臺灣大道 rows (glyph fold) |
| 9 | fullwidth digits "２之３" | folds to "2-3" before match |
| 10 | candidates ordered by distance to anchor | ascending; limit respected |
| 11 | withHouseNumber blank | nearest-by-distance |
| 12 | search before county place DB exists in registry | empty list + safe; no throw |
| 13 | seedCounty/districts open no place DB | place-DB function spy: 0 calls until search() |

## Anchors

- Folding + substring rules: research note §3.1/§3.2, verified by
  `scripts/verify_research_claims.py` V6/V8.
- Distance ranking reuses `haversineMeters` (same formula as `AddressSubsystem`).
- GoTo handoff reuses the existing `TwCoordGotoView` path (which pans via
  `com.atakmap.map.CameraController$Programmatic.panTo` at `TwCoordGotoView.java:783`);
  feature 006 adds no new camera call site.
