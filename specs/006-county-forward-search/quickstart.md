# Quickstart: County-Scoped Forward Address Search (feature 006)

Audience: a developer picking up implementation, or a reviewer validating it.
Assumes features 004/005 are understood (offline-address import + multi-county
reverse lookup).

## What this feature adds

1. **Locality detection** — coordinate → 縣市 + 鄉鎮市區 from the ~10 MB
   `townships.sqlite` boundary layer alone (no place DB opened).
2. **Forward search** — a county-first funnel page: county (所在地 / 地圖中心 /
   清單) → 鄉鎮市區 → street fragment → house-number/distance → confirm → GoTo.
3. **Reverse-path county scoping** — the existing on-map readout now resolves the
   county first and queries only that county's dataset.

## Prerequisites

- A device/emulator with ATAK-CIV 5.7.x and the plugin installed.
- `tw-central-full.zip` (or `base.zip`) imported via Tools → 離線地址 → Import.
  After import, `townships.sqlite` is consumed into `active/_boundary/` (it is no
  longer reported as a skipped supplementary file).
- At least one `places-<county>.sqlite` active for street search to return rows;
  locality detection works with `townships.sqlite` alone.

## Try it (manual)

1. **Locality**: pan the map over Taichung. Open Tools → 前向搜尋 (forward
   search). The county should pre-fill (e.g. 台中市) and the district from the map
   centre (e.g. 西區) — verify no lag and no place-DB load for this step.
2. **County sources**: pan to Changhua while your self-marker stays in Taichung.
   Reopen the page — it should seed **彰化縣** (map-centre default) with a one-tap
   〔所在地：台中市〕 alternative. Tap 〔清單〕 — the list should show only the
   counties present in the installed boundary data (12 for `tw-central`).
3. **Funnel**: choose 台中市 → 大甲區 → type `中山路`. You should get a short,
   distance-ranked candidate list including `中山路一段` / `中山路二段` rows.
4. **Glyph fold**: choose 台中市 → 西區 → type `台灣大道`. Results should include
   the gazetted `臺灣大道…` rows.
5. **Segment-only road**: choose 台中市 → 西區 → type `向上路`. Results should be
   non-empty (proves substring matching, since `向上路` exists only as 一段…九段).
6. **Pin + GoTo**: pick a candidate, review its address + distance, tap
   〔前往 / GoTo〕. The map pans there (and only on this explicit tap).
7. **Reverse scoping**: with {台中,彰化} active, watch the on-map address readout
   while panning across the county border — same text as before, no glitch.

## Run the tests

```bash
# JVM unit tests (geometry, facade, funnel, street query, reverse scoping)
./gradlew :app:testCivDebugUnitTest

# On-device measurements (SC-002/003/004/005) — real device, not emulator
./gradlew :app:connectedCivDebugAndroidTest
```

Key JVM tests:
- `address/geo/WkbMultiPolygonParserTest`, `PointInPolygonTest`
- `address/boundary/TownshipBoundaryFacadeTest` — the **8/8 reference points**
- `address/forward/ForwardSearchControllerTest`, `StreetTextNormaliserTest`
- `AddressDatabaseFacadeStreetQueryTest`
- `AddressSubsystemReverseScopingTest` — in-county result == old fan-out result

On-device:
- locality detection 8/8 (SC-005) + opens no place DB (SC-002)
- reverse p50 ≤ 1000 ms / p95 ≤ 2000 ms over 100 pans (SC-003)
- RSS ≤ 200 MiB with boundary + ≥2 counties (SC-004)

## Re-verify the data assumptions (after any generator rebuild)

```bash
python scripts/measure_tw_central.py        # counts + bbox candidate sets
python scripts/verify_research_claims.py     # counts, county_zh coverage, WKB shape
python scripts/verify_polygon_in.py          # 8/8 polygon-in (the parser algorithm)
```

If the generator ships a new `tw-central-full.zip`, re-run these and update the
fixture `townships.sqlite` used by `TownshipBoundaryFacadeTest` if the reference
districts change.

## Where things live

- Geometry: `app/src/main/java/com/atakmap/android/twcoord/address/geo/`
- Boundary facade: `.../address/boundary/`
- Forward funnel logic: `.../address/forward/`
- Page + tool: `.../address/ForwardSearchReceiver.java`,
  `.../plugin/ForwardSearchTool.java`
- Reverse scoping: `.../address/AddressSubsystem.java` (`setBoundaryFacade`)
- Design input: `docs/research/county-scoped-forward-search.md`
- UI doc: `docs/ui/forward-search-page.md`
- ADR (post-implement): `docs/adr/0018-county-forward-search.md`
