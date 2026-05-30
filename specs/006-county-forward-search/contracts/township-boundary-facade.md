# Contract: TownshipBoundaryFacade (+ Factory, LocalityResult)

**Modules** (NEW):
- `app/src/main/java/com/atakmap/android/twcoord/address/boundary/TownshipBoundaryFacade.java`
- `app/src/main/java/com/atakmap/android/twcoord/address/boundary/TownshipBoundaryFactory.java`
- `app/src/main/java/com/atakmap/android/twcoord/address/boundary/LocalityResult.java`

The boundary layer's read API. Wraps the singleton `townships.sqlite` and answers
"which 縣市 + 鄉鎮市區 is this coordinate in?" via R*Tree bbox → WKB `covers`,
**without opening any place DB** (FR-001/SC-002). Reused by both forward search
and the reverse-path scoping.

## Interface

```java
public interface TownshipBoundaryFacade extends AutoCloseable {

  /** Resolve (county, district, approx) for a coordinate. snapMeters=0 ⇒ strict. */
  LocalityResult localityAt(double lat, double lon, double snapMeters);

  /** Level-4 county names present in the data, for the funnel's manual list (FR-006). */
  java.util.List<String> counties();

  /** Level-7/8 district names for a county, for stage ② (sorted, stable). */
  java.util.List<String> districtsOf(String county);

  @Override void close();

  interface Factory {
    /** Open townships.sqlite read-only; null if missing/unopenable. */
    TownshipBoundaryFacade open(java.io.File dbFile);
  }
}
```

`LocalityResult`: `{ String county (nullable), String district (nullable),
boolean approx }` — states Full / Snapped / County-only / None (data-model §2.2).

## Query shape (research R2; generator data-contract §5.1)

```sql
-- level 8 first, then level 7
SELECT t.name_zh, t.county_zh, t.geometry_wkb
FROM townships t JOIN townships_rtree r ON r.id = t.id
WHERE t.admin_level = ?               -- 8 then 7
  AND r.min_lat <= ? AND ? <= r.max_lat
  AND r.min_lon <= ? AND ? <= r.max_lon;
-- app side: WkbMultiPolygonParser.parseOrNull(geometry_wkb).covers(lat, lon)
-- first covering hit wins; county_zh returned inline.
```

`counties()`: `SELECT name_zh FROM townships WHERE admin_level=4 ORDER BY name_zh`.
`districtsOf(c)`: `SELECT name_zh FROM townships WHERE county_zh=? AND admin_level IN (7,8) ORDER BY name_zh`.

## Invariants

1. **No place DB opened.** The facade touches only `townships.sqlite`. (Asserted
   in tests by spying the place-DB factory: zero opens during `localityAt`.)
2. **Inline county.** On a level-7/8 covering hit, `county` = the row's
   `county_zh`; no separate level-4 query on the happy path. Level-4 polygon-in
   is the fallback only when `county_zh` is null (defensive; never happens on MOI
   data — 136/136 non-null).
3. **Snap tolerance.** `snapMeters>0` and no strict cover ⇒ nearest level-7/8
   polygon within tolerance, `approx=true`; county from that polygon's
   `county_zh`. `snapMeters=0` ⇒ no snap (strict).
4. **Offshore ⇒ None.** A point outside every polygon (and beyond snap) ⇒
   `county=null` (verified: (24.0,119.5) → None).
5. **Never throws.** Malformed geometry or SQL error ⇒ `None` + `Log.w`
   (Constitution VI). A corrupt boundary DB cannot crash the host.
6. **Open-once.** The factory opens via `Databases.openDatabase` (primary) with
   the 005 fallback on R*Tree failure; the facade stays open for plugin lifetime.

## Test plan (`TownshipBoundaryFacadeTest`, JVM/xerial against fixture townships.sqlite)

| # | Scenario | Expected |
|---|---|---|
| 1 | 8 reference points (台中車站, 一中, 彰化市, 鹿港, 大甲, 斗六, 南投市, offshore) | 8/8 correct county+district (offshore→None) |
| 2 | counties() | exactly the 12 level-4 names in the fixture, sorted, no hard-coded extras |
| 3 | districtsOf("台中市") | the fixture's Taichung districts (e.g. 29), sorted |
| 4 | districtsOf(unknown county) | empty list |
| 5 | coastal point seaward of all polygons, snap=1000 | district snapped, approx=true, county correct |
| 6 | same point, snap=0 | County-only or None (no snap) |
| 7 | localityAt does not open any place DB | place-DB factory spy: 0 opens |
| 8 | corrupt geometry blob in a row | that row skipped; other rows still resolve; no throw |
| 9 | facade.open on missing file | returns null |

## Anchors

- `reverse_geocode.py::lookup_township` (generator) — same query shape, proven by
  `scripts/verify_polygon_in.py` (8/8).
- `Databases.openDatabase` primary path + 005 `FallbackSqliteFactory` (R*Tree).
- Mount path `active/_boundary/townships.sqlite` (research R3).
