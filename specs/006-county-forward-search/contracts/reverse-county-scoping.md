# Contract: Reverse-path county scoping (AddressSubsystem modification)

**Module**: `app/src/main/java/com/atakmap/android/twcoord/address/AddressSubsystem.java` (MODIFY)

Makes the existing on-map reverse readout resolve the county via the boundary
facade first, then query only that county's facade — replacing the
`lookupAcrossAllCounties` fan-out for in-county points (FR-014). Additive: the
fan-out stays as the fallback.

## Interface (added wiring)

```java
public final class AddressSubsystem implements AutoCloseable {
  // existing: setRegistry(ActiveDatasetRegistry), setConfidenceThresholds(...), onCoord(...)

  /** Feature 006: bind the shared boundary facade. null ⇒ keep 005 fan-out behaviour. */
  void setBoundaryFacade(TownshipBoundaryFacade boundary);   // NEW
}
```

## Lookup algorithm (replaces the body of the registry branch in `runLookup`)

```
if boundary == null:                       # boundary data not installed (FR-017)
    return lookupAcrossAllCounties(lat, lon)        # exact 005 behaviour

locality = boundary.localityAt(lat, lon, SNAP_M)    # SNAP_M ≈ 1000 (coastal)
if locality.county == null:
    return lookupAcrossAllCounties(lat, lon)        # offshore / outside data: fall back

facade = registry.snapshot().get(locality.county)
if facade != null:
    rec = facade.nearestWithin(lat, lon, LOOKUP_RADIUS_M)   # ONE county
    return rec != null ? Found(rec) : Empty()
else:
    # county detected but its dataset not installed (FR-015)
    return LocalityOnly(locality.county, locality.district)
```

`LocalityOnly` maps to a row state showing `縣市 + 鄉鎮市區` (best-effort), not an
empty line.

## Invariants

1. **In-county equivalence (FR-014).** For a point inside an active county, the
   single-county result == the old globally-nearest result. (The nearest record
   lies in the county that geographically contains the point.) This is the
   correctness anchor; tested directly.
2. **No regression without boundary data.** `boundary == null` ⇒ byte-for-byte
   the 005 fan-out (FR-017).
3. **Offshore / unknown county ⇒ fan-out.** Preserves a best-effort answer when
   the point is outside all boundaries but near an active county's data.
4. **County-without-dataset ⇒ locality (FR-015).** Show 縣市+鄉鎮市區 rather than
   nothing.
5. **One boundary query per lookup.** `localityAt` is cheap (1–3 candidate
   polygons); net work drops vs querying N county facades.
6. **No throw.** Boundary or facade error ⇒ caught, fall back to fan-out or Empty
   (Constitution VI). The worker already wraps `Throwable`.
7. **Latency budget.** Median ≤ 1000 ms / p95 ≤ 2000 ms across 100 real-device
   pans (SC-003) — must not regress 005; expected to improve as active-county
   count grows.

## Test plan (`AddressSubsystemReverseScopingTest`, JVM + on-device)

| # | Scenario | Expected |
|---|---|---|
| 1 | boundary bound, point in 台中市, {台中,彰化} active | result == lookupAcrossAllCounties result; only Taichung facade queried |
| 2 | point in 彰化縣 | result == fan-out result; only Changhua facade queried |
| 3 | boundary == null | falls back to fan-out (005 behaviour) |
| 4 | county detected (雲林縣) but no places-yunlin | LocalityOnly(雲林縣, 斗六市…) |
| 5 | offshore point | fan-out fallback (or Empty if fan-out empty) |
| 6 | coastal reclaimed point (snap) | county resolved; Taichung facade queried |
| 7 | boundary throws | caught; fan-out fallback; no host crash |
| 8 (device) | 100 random pans, ≥2 counties | p50 ≤ 1000 ms, p95 ≤ 2000 ms (SC-003) |
| 9 (device) | locality-only pan | no place-DB file handle opened (SC-002) |

## Anchors

- `lookupAcrossAllCounties` + `nearestWithin` already in `AddressSubsystem`
  (005); this wraps a county-resolve in front of them.
- Boundary facade per `township-boundary-facade.md`.
- On-device harness per research R6 (extends 005 R9 Espresso harness); real-device
  gate per the feature-006 roadmap memory.
