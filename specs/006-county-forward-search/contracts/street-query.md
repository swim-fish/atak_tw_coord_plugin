# Contract: District-scoped street query (AddressDatabaseFacade extension)

**Module**: `app/src/main/java/com/atakmap/android/twcoord/address/AddressDatabaseFacade.java` (MODIFY)
plus implementations `AtakDatabasesAddressDatabase` (production) and
`SqliteAddressDatabase` (test path).

Adds a district-scoped, distance-ranked street lookup to the existing per-county
facade. Forward search stage ③ uses it; reverse lookup is unchanged
(`nearestWithin`).

## Interface (added method)

```java
public interface AddressDatabaseFacade extends AutoCloseable {
  GeneratorMetadata readMetadata();                                   // existing
  AddressRecord nearestWithin(double lat, double lon, double r);      // existing

  /**
   * District-scoped street candidates, distance-ranked.
   *
   * @param district     the 鄉鎮市區 name (matches places.township, 臺→台 folded)
   * @param foldedFragment the street fragment AFTER StreetTextNormaliser.fold
   * @param anchorLat/anchorLon  distance-rank anchor (map centre or self)
   * @param limit        max rows returned (ranked nearest-first)
   * @return list of AddressCandidate (possibly empty); never null; never throws
   */
  java.util.List<AddressCandidate> streetCandidates(
      String district, String foldedFragment,
      double anchorLat, double anchorLon, int limit);   // NEW

  void close();
  interface Factory { AddressDatabaseFacade open(java.io.File dbFile); }
}
```

## Query shape

```sql
SELECT p.lat, p.lon, p.display_name, p.display_name_halfwidth, p.street
FROM places p
WHERE p.township = ?                       -- district scope (FR-008)
  AND p.street LIKE ? ;                     -- ? = fragment || '%' (prefix) incl. 段
-- app side: optionally re-fold p.street and substring-check (handles 臺/台 stored
-- variants that LIKE alone won't fold); haversine to anchor; sort asc; take limit.
```

Notes:
- **Prefix vs substring.** Default to prefix `fragment%` (anchored at street
  start — matches the common "type the road name" intent and uses the `段`-span).
  Fall back to `%fragment%` if the prefix yields zero (handles a typed inner
  token). Either way **never** `street = fragment` (FR-009).
- **Glyph fold across the LIKE gap.** SQLite `LIKE` won't fold 臺/台, so the
  query runs on the folded fragment AND the app re-checks `fold(p.street)`
  contains/startsWith the fragment to catch stored `臺…` rows (FR-010). Because
  the set is small (district-scoped), the app-side re-fold is cheap.
- **Distance rank** in app code via the existing haversine (same as
  `nearestWithin`).

## Invariants

1. **Never `=`.** Matching is prefix/substring (FR-009).
2. **Fold both sides.** Fragment pre-folded by the controller; candidate `street`
   re-folded app-side before the final contains-check (FR-010).
3. **District-scoped.** Only rows with `township = district` are considered
   (FR-008 / SC-007) — the query never scans the whole county.
4. **Ranked + bounded.** Nearest-first by haversine; at most `limit` rows.
5. **Never throws / never null.** SQL error ⇒ empty list + `Log.w`
   (Constitution VI).
6. **Reverse path untouched.** `nearestWithin` keeps its exact 004/005 behaviour.

## Test plan (`AddressDatabaseFacadeStreetQueryTest`, JVM/xerial against a fixture places sqlite)

| # | Scenario | Expected |
|---|---|---|
| 1 | district=大甲區, fragment=中山路 | rows all township=大甲區, street starts 中山路 incl. 一段/二段 |
| 2 | fragment=向上路 (segments only) | non-empty (LIKE prefix, not `=`) |
| 3 | fragment=台灣大道 | matches stored 臺灣大道 rows (app re-fold) |
| 4 | fullwidth/halfwidth digit fragment | folded fragment matches |
| 5 | wrong district | empty list |
| 6 | ranking | rows ascending by distance to anchor; limit respected |
| 7 | SQL/IO fault (closed db) | empty list, no throw |
| 8 | reverse `nearestWithin` regression | identical to 004/005 result |

## Anchors

- Column shapes: data-contract §3.1 (`township`, `street`, `display_name`).
- `LIKE` vs `=` and 臺/台 counts: `scripts/verify_research_claims.py` V6/V8.
- Implementation parallels existing `nearestWithin` (bbox+haversine) in
  `AtakDatabasesAddressDatabase` / `SqliteAddressDatabase`.
