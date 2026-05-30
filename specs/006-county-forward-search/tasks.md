---

description: "Implementation task list for feature 006: county-scoped forward address search"
---

# Tasks: County-Scoped Forward Address Search

**Input**: Design documents from `/specs/006-county-forward-search/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Tests are REQUIRED (Constitution II TDD). JVM unit tests via Robolectric + xerial sqlite-jdbc for the geometry / facade / funnel seams; on-device Espresso for the SC-002/003/004/005 measurements (extends the 005 R9 harness, per research R6). Write each test FIRST and watch it fail before the implementation task that satisfies it.

**Organization**: Tasks are grouped by user story (US1–US4 from spec.md). Setup + Foundational phases must complete before any US phase starts.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete dependencies)
- **[Story]**: Which user story this task belongs to (US1/US2/US3/US4)
- File paths are repo-root-relative.

## Path Conventions

Android single-module plugin layout (same as features 001–005):

- Source: `app/src/main/java/com/atakmap/android/twcoord/`
  - new sub-packages this feature adds: `address/geo/`, `address/boundary/`, `address/forward/`
- Tests (JVM): `app/src/test/java/com/atakmap/android/twcoord/`
- Tests (instrumented / Espresso): `app/src/androidTest/java/com/atakmap/android/twcoord/`
- Resources: `app/src/main/res/`
- Docs: `docs/`
- Specs: `specs/006-county-forward-search/`

## Story → priority map (from spec.md)

| Story | Priority | Theme | MVP? |
|---|---|---|---|
| US1 | P1 | Funnel: county → 鄉鎮市區 → street → pin → GoTo (near me) | 🎯 yes |
| US2 | P1 | County three ways (所在地 / 地圖中心 / 清單), map-centre default | part of MVP |
| US3 | P2 | Reverse lookup scoped to the detected county | no |
| US4 | P3 | Pin by house number (numeric keypad) or distance | no |

> Note: US1 and US2 are both P1 and tightly coupled (the funnel needs the county
> picker). They are implemented together as the MVP; tasks are still labelled per
> story for traceability.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Package skeleton, string resources, and a test fixture so later phases compile and test cleanly. No new Gradle dependency (research R1 — hand-rolled WKB parser, JTS only if ever forced).

- [ ] T001 Create the three new sub-package directories with a short `package-info.java` each documenting scope: `app/src/main/java/com/atakmap/android/twcoord/address/geo/package-info.java`, `.../address/boundary/package-info.java`, `.../address/forward/package-info.java`.
- [ ] T002 [P] Add new string keys (~20) to `app/src/main/res/values/strings.xml` for the forward-search page: county-source buttons (所在地 / 地圖中心 / 清單), district picker title, street-fragment hint, numeric-keypad labels, candidate-row format, empty-states (此鄉鎮市區查無符合的街道 / 此縣市地址資料未安裝 / 匯入 base 資料以啟用前向搜尋), approximate-locality badge, GoTo confirm button.
- [ ] T003 [P] Add zh-rTW parity translations for all T002 keys to `app/src/main/res/values-zh-rTW/strings.xml` (real Taiwan-localised text, not placeholders, to avoid `MissingTranslation` lint).
- [ ] T004 [P] Add ja parity translations for all T002 keys to `app/src/main/res/values-ja/strings.xml` (first pass; polish in T0xx).
- [ ] T005 Create a small test fixture `townships.sqlite` (the `tw-central` boundary layer, or a trimmed subset covering the 8 reference points + 大甲/西區/鹿港 districts) under `app/src/test/resources/fixtures/townships-fixture.sqlite`, plus a matching trimmed `places-taichung-fixture.sqlite` (a few hundred rows incl. 中山路一段/二段 + 向上路一段…九段 + a 臺灣大道 row) under the same dir, with a README noting provenance (regenerated via `scripts/measure_tw_central.py` source zip). Used by the boundary + street-query JVM tests.
- [ ] T006 Verify `AndroidManifest.xml` requires no change (no new Activity, no new permission — the forward-search page is a DropDownReceiver like 004/005). Record the verification in the task notes.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The geometry + boundary + import-consumption seams that BOTH the forward funnel (US1/US2/US4) and the reverse scoping (US3) depend on. Pure-logic classes are TDD'd here.

**⚠️ CRITICAL**: No user story phase can begin until this phase completes.

### Geometry primitives (WKB parser + point-in-polygon) — contract: `contracts/wkb-multipolygon-parser.md`

- [ ] T007 [P] Write `app/src/test/java/com/atakmap/android/twcoord/address/geo/PointInPolygonTest.java` (FAIL first): inside / outside / on-edge determinism / point-in-hole-excluded cases per the contract test plan.
- [ ] T008 [P] Write `app/src/test/java/com/atakmap/android/twcoord/address/geo/WkbMultiPolygonParserTest.java` (FAIL first): type-6 + type-3 parse, holes, bbox fast-reject, truncated blob → null, big-endian → null, wrong type code → null, real 麥寮鄉/宜蘭縣 blob from the fixture parses + covers a known interior point.
- [ ] T009 Implement `app/src/main/java/com/atakmap/android/twcoord/address/geo/PointInPolygon.java` (package-private ray-cast `inRing(lat, lon, lon[], lat[])`) until T007 passes.
- [ ] T010 Implement `app/src/main/java/com/atakmap/android/twcoord/address/geo/BoundaryGeometry.java` (immutable polygons + cached bbox + `covers(lat,lon)` = bbox reject → PIP incl. holes) and `app/src/main/java/com/atakmap/android/twcoord/address/geo/WkbMultiPolygonParser.java` (`parseOrNull(byte[])`, little-endian OGC type 3/6, x→lon/y→lat, never throws) until T008 passes. Treat the blob as untrusted (Constitution VI defensive-validation).

### Boundary facade — contract: `contracts/township-boundary-facade.md`

- [ ] T011 [P] Create `app/src/main/java/com/atakmap/android/twcoord/address/boundary/LocalityResult.java` (immutable `{county?, district?, approx}` + the four state accessors Full/Snapped/County-only/None).
- [ ] T012 Write `app/src/test/java/com/atakmap/android/twcoord/address/boundary/TownshipBoundaryFacadeTest.java` (FAIL first) against the T005 fixture: the **8 reference points** (台中車站→台中市西區, 一中→北區, 彰化市→彰化市, 鹿港→鹿港鎮, 大甲→大甲區, 斗六→斗六市, 南投市→南投市, offshore→None); `counties()` == fixture level-4 set (no hard-coded list); `districtsOf("台中市")`; unknown county → empty; coastal snap (approx=true) vs strict (None); `localityAt` opens no place DB (spy); corrupt geometry row skipped without throw; `open` on missing file → null.
- [ ] T013 Implement `app/src/main/java/com/atakmap/android/twcoord/address/boundary/TownshipBoundaryFacade.java` (interface + production impl): R*Tree bbox → `BoundaryGeometry.covers`, level-8-then-7, inline `county_zh`, level-4 fallback only when `county_zh` null, optional snap tolerance, `counties()` / `districtsOf()`; never throws (→ None + `Log.w`). Until T012 passes.
- [ ] T014 Implement `app/src/main/java/com/atakmap/android/twcoord/address/boundary/TownshipBoundaryFactory.java` opening `townships.sqlite` via `com.atakmap.database.Databases.openDatabase` (primary) with the 005 `FallbackSqliteFactory` R*Tree-probe fallback; returns null if missing/unopenable. (JVM test path uses xerial through the same interface, mirroring `SqliteAddressDatabase`.)

### Import-side consumption of `townships.sqlite` — research R3/R4

- [ ] T015 Extend `app/src/main/java/com/atakmap/android/twcoord/address/ZipEntryClassifier.java`: add `Classification.BOUNDARY`; `townships.sqlite` → `BOUNDARY` (was `SKIPPED_SUPPLEMENTARY`); `roads.sqlite` / `places-osm.sqlite` / `timestamp.*` / `*.manifest.txt` stay skipped; zip-slip defences unchanged.
- [ ] T016 [P] Update `app/src/test/java/com/atakmap/android/twcoord/address/ZipEntryClassifierTest.java`: add `townships.sqlite → BOUNDARY` case; assert `roads`/`places-osm` still skipped (regression). FAIL first, then T015 green.
- [ ] T017 Add a boundary-dir helper to `app/src/main/java/com/atakmap/android/twcoord/address/AtakFileSystem.java` (+ `FileSystem` default): `boundaryDir()` → `active/_boundary/` and `boundaryDbFile()` → `active/_boundary/townships.sqlite`; ensure `ActiveDatasetRegistry.initFromDisk` skips the `_boundary` dir (extend the existing dot-dir skip to also skip `_boundary`).
- [ ] T018 Route the `BOUNDARY` entry through the extract/import path so it streams into `active/_boundary/townships.sqlite` via the existing atomic-move discipline. Modify `ZipExtractor` (and the `BatchImportCoordinator` call site) to handle the BOUNDARY classification: extract to staging, SHA, atomic-move into `boundaryDir()`. Report it in `BatchImportReport` as a consumed boundary entry (not "supplementary skipped"). Add a `BatchImportReport.Status.BOUNDARY` (or reuse ACTIVATED with a boundary flag) — keep the report honest.
- [ ] T019 [P] Update `app/src/test/java/com/atakmap/android/twcoord/address/ZipExtractorTest.java` (or a new `BoundaryImportTest`) to assert a ZIP containing `townships.sqlite` lands it in `boundaryDir()` and reports it consumed; ZIP without it leaves `boundaryDir()` empty.

**Checkpoint**: geometry + boundary facade + boundary import all green on JVM; the boundary layer can be mounted and queried. User-story phases can begin.

---

## Phase 3: User Story 1 + User Story 2 — County-first funnel + three county sources (Priority: P1) 🎯 MVP

**Goal**: A field operator opens the forward-search page, the county/district pre-fill from the map centre (three sources available, map-centre default), narrows county → 鄉鎮市區 → street fragment, sees distance-ranked candidates, and GoTos one. No place DB is opened until the street stage.

**Independent Test**: With `tw-central-full.zip` imported, open 前向搜尋 at a Taichung map-centre → county/district pre-fill (台中市/西區) with NO place-DB load; pan to Changhua with self-marker in Taichung → seeds 彰化縣 (map-centre default) with one-tap 所在地; 清單 lists only the 12 installed counties; choose 台中市 → 大甲區 → type `中山路` → distance-ranked candidates incl. 一段/二段 → GoTo pans the map.

### Street normalisation + district-scoped query (shared by the funnel) — contracts: `contracts/forward-search-controller.md`, `contracts/street-query.md`

- [ ] T020 [P] [US1] Write `app/src/test/java/com/atakmap/android/twcoord/address/forward/StreetTextNormaliserTest.java` (FAIL first): 臺→台, fullwidth→halfwidth digits, 之→-, trim, idempotence.
- [ ] T021 [US1] Implement `app/src/main/java/com/atakmap/android/twcoord/address/forward/StreetTextNormaliser.java` (`fold(String)`) until T020 passes.
- [ ] T022 [P] [US1] Add `streetCandidates(district, foldedFragment, anchorLat, anchorLon, limit)` to `app/src/main/java/com/atakmap/android/twcoord/address/AddressDatabaseFacade.java` (new interface method) + value type `app/src/main/java/com/atakmap/android/twcoord/address/forward/AddressCandidate.java`.
- [ ] T023 [US1] Write `app/src/test/java/com/atakmap/android/twcoord/address/AddressDatabaseFacadeStreetQueryTest.java` (FAIL first) against the T005 places fixture: district-scoped `中山路` (all rows 大甲區, prefix incl. 一段/二段); `向上路` non-empty (proves substring not `=`); `台灣大道` matches stored `臺灣大道` (app re-fold); wrong district → empty; ranking ascending by distance + limit; closed-db → empty list no throw; `nearestWithin` regression unchanged.
- [ ] T024 [US1] Implement `streetCandidates` in `app/src/main/java/com/atakmap/android/twcoord/address/AtakDatabasesAddressDatabase.java` (native SQLite: `WHERE township=? AND street LIKE ?` prefix, fall back to `%frag%` if empty; app-side re-fold of `street` + haversine rank + limit; never throws) until the production-path assertions of T023 pass.
- [ ] T025 [US1] Implement `streetCandidates` in `app/src/main/java/com/atakmap/android/twcoord/address/SqliteAddressDatabase.java` (xerial test path, same SQL) so T023 passes on JVM.

### Funnel controller (US1 core) + county sources (US2) — contract: `contracts/forward-search-controller.md`

- [ ] T026 [P] [US1] Create value types `app/src/main/java/com/atakmap/android/twcoord/address/forward/ForwardSearchQuery.java` and `app/src/main/java/com/atakmap/android/twcoord/address/forward/CountySource.java` (enum MAP_CENTER/SELF/LIST + `CountySeed` carrying defaultCounty/defaultSource/selfCounty/mapCenterCounty).
- [ ] T027 [US1] [US2] Write `app/src/test/java/com/atakmap/android/twcoord/address/forward/ForwardSearchControllerTest.java` (FAIL first): map-centre default when SELF≠MAP_CENTER (US2); same-county no-conflict; `countyList()` == boundary.counties() (US2, no hard-coded); district pre-highlight only for SELF/MAP_CENTER (US1); search `中山路` in 大甲區 scoped+ranked; `向上路` non-empty; `台灣大道` glyph fold; place DB NOT opened until `search()` (spy → 0 opens through ①②); empty-fragment → empty; facade error → empty + no throw.
- [ ] T028 [US1] [US2] Implement `app/src/main/java/com/atakmap/android/twcoord/address/forward/ForwardSearchController.java`: `seedCounty` (map-centre default, R2 boundary calls only), `countyList`/`chooseCounty`, `districts`/`suggestedDistrict`/`chooseDistrict`, `search` (first place-DB resolve via the injected `Function<String,AddressDatabaseFacade>`), `withHouseNumber`/`confirm` (returns target, no pan), `state`. Until T027 passes. Constitution VI: never throws.

### Forward-search page (DropDownReceiver) + Tools-menu entry (US1 UI; US2 county buttons)

- [ ] T029 [US1] Create the page layout `app/src/main/res/layout/forward_search_page.xml`: single-column, ≥48dp targets (56–64dp primary), stage ① three big county-source buttons + confirm chip, stage ② scrollable district chip grid, stage ③ street fragment field + candidate list, stage ④ numeric keypad placeholder (built in US4), confirm/GoTo button. Glove + ATAK side-panel constraints per `docs/ui/forward-search-page.md`.
- [ ] T030 [US1] [US2] Create `app/src/main/java/com/atakmap/android/twcoord/address/ForwardSearchReceiver.java` (`DropDownReceiver`): inflate the page, own a `ForwardSearchController`, read map-centre via `MapView.getCenterPoint()` + self via `getSelfMarker()` for seeding (US2), bind the funnel stages to the controller, wire the county-source buttons (所在地 / 地圖中心 / 清單) and district chips (tap-only), render candidates, and route confirm → existing `TwCoordGotoView`/GoTo path (no new camera call). Every callback + lifecycle method wrapped in `try/catch(Throwable)` → `Log.w` (Constitution VI).
- [ ] T031 [US1] Create `app/src/main/java/com/atakmap/android/twcoord/plugin/ForwardSearchTool.java` (Tools-menu entry mirroring `OfflineAddressTool`/`TwCoordGotoTool`) + the broadcast action constant (add to a `ForwardSearchIntents` or reuse `OfflineAddressIntents` sibling) that opens the page; register the receiver + tool in `TwCoordMapComponent.onCreate` and tear down in `onDestroyImpl`.
- [ ] T032 [US1] [US2] Mount the boundary facade at `TwCoordMapComponent.onCreate`: build `TownshipBoundaryFactory`, open `boundaryDbFile()` once, hold for plugin lifetime, close in `onDestroyImpl`; pass it to the `ForwardSearchController` factory and (Phase 5) to `AddressSubsystem`. If the boundary DB is absent, the facade is null → forward search shows the "import base data" empty-state (FR-017).

**Checkpoint**: US1+US2 MVP — operator can funnel county→district→street→GoTo, with three county sources and map-centre default, opening no place DB before the street stage.

---

## Phase 4: User Story 3 — Reverse lookup scoped to the detected county (Priority: P2)

**Goal**: The on-map reverse readout resolves county via the boundary facade first, then queries only that county's facade — same visible result for in-county points, less work; best-effort locality when the county's dataset is absent.

**Independent Test**: With {台中,彰化} active and the boundary facade bound, a Taichung map-centre reverse readout returns the same text as today and queries only the Taichung facade; a 雲林 point with no places-yunlin shows 縣市+鄉鎮市區 locality, not blank.

- [ ] T033 [US3] Write `app/src/test/java/com/atakmap/android/twcoord/address/AddressSubsystemReverseScopingTest.java` (FAIL first): boundary-bound in-county result == `lookupAcrossAllCounties` result and only the one facade queried (spy); boundary null → exact 005 fan-out; county detected but dataset absent → LocalityOnly; offshore → fan-out fallback; coastal snap → county facade queried; boundary throws → caught, fan-out, no crash.
- [ ] T034 [US3] Add `setBoundaryFacade(TownshipBoundaryFacade)` to `app/src/main/java/com/atakmap/android/twcoord/address/AddressSubsystem.java` and rewrite the registry branch of `runLookup` per `contracts/reverse-county-scoping.md`: `localityAt(lat,lon,SNAP_M)` → query the one county facade; fall back to `lookupAcrossAllCounties` when boundary null / county null; emit a LocalityOnly state when county detected but no dataset (FR-015). Until T033 passes.
- [ ] T035 [US3] Add the `LocalityOnly` row state mapping (county + 鄉鎮市區 best-effort text, distinct from empty-state) in `AddressSubsystem.mapResultToState` (+ `AddressRowState`/`AddressLookupResult` as needed), reusing the existing confidence/empty conventions.
- [ ] T036 [US3] Bind the boundary facade into `AddressSubsystem` at `TwCoordMapComponent.onCreate` (`addressSubsystem.setBoundaryFacade(boundaryFacade)` after T032 mounts it); ensure null-safe when boundary data is absent.

**Checkpoint**: reverse path scoped to one county; no operator-visible change for in-county points; degrades to 005 behaviour without boundary data.

---

## Phase 5: User Story 4 — Pin by house number or distance (Priority: P3)

**Goal**: A numeric-keypad house-number entry narrows the final pin for long roads; blank falls back to nearest-by-distance.

**Independent Test**: county 台中市 → 西區 → 向上路 → enter a house number lands on the matching building; blank → nearest 向上路 to the anchor.

- [ ] T037 [US4] Extend `ForwardSearchControllerTest` (T027) with house-number cases (FAIL first): `withHouseNumber("123")` narrows to that number on the street; blank → nearest-by-distance; unparseable house number treated as blank.
- [ ] T038 [US4] Implement `withHouseNumber` filtering in `ForwardSearchController` (match `number` within the already street+district-scoped candidate set, fold digits via `StreetTextNormaliser`; blank/unparseable → distance pin) until T037 passes.
- [ ] T039 [US4] Build the numeric keypad UI in `forward_search_page.xml` + `ForwardSearchReceiver`: large digit buttons + `之/-` + backspace (≥56dp, no system IME), feeding `withHouseNumber`; show the resolved candidate + distance before the GoTo confirm (no auto-pan). Each button `OnClickListener` wrapped per Constitution VI.

**Checkpoint**: all four stories independently functional.

---

## Phase 6: On-device measurement (SC-002/003/004/005) — research R6

**Purpose**: The real-device performance/memory + correctness gates the feature-006 roadmap memory mandates (not emulator-only). Extends the 005 R9 Espresso harness.

- [ ] T040 [P] Create `app/src/androidTest/java/com/atakmap/android/twcoord/address/LocalityDetectionInstrumentedTest.java`: the 8 reference points resolve correctly on-device (SC-005) AND no place-DB file handle opens during a locality-only pan (SC-002).
- [ ] T041 [P] Extend the 005 Espresso perf harness with `ForwardSearchFlowTest` (US1+US2 end-to-end with `tw-central-full.zip` pre-pushed) and a reverse-scoping latency run: 100 random pans, p50 ≤ 1000 ms / p95 ≤ 2000 ms (SC-003).
- [ ] T042 [P] Add an RSS assertion (reuse 005 `BatchImportRssTest` pattern via `Debug.MemoryInfo`): boundary layer mounted + ≥ 2 counties active, RSS ≤ 200 MiB during a 5-minute panning session (SC-004).
- [ ] T043 Record the measured p50/p95/RSS + 8/8 locality numbers in ADR-0018 (created in Polish T046); if any gate regresses, prefer lazy-open of the boundary facade over forcing it open at onCreate (note the decision).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Docs, audit, i18n proofing, data re-verification.

- [ ] T044 [P] Write `docs/ui/forward-search-page.md`: the funnel stages, glove UX rules (≥48dp / 56–64dp, tap-first ①②, numeric keypad, confirm-before-GoTo), the four county-source / locality states, and screenshots/wireframes (Constitution III — new flow needs a docs/ui entry).
- [ ] T045 [P] Proofread + finalise zh-rTW (T003) and ja (T004) strings; run lint to confirm zero `MissingTranslation`.
- [ ] T046 Author `docs/adr/0018-county-forward-search.md` (post-implement): WKB parser decision (hand-rolled, 8/8, JTS reserve), boundary-facade design + mount path, reverse-path county scoping, the SDK anchoring (CameraController class-name correction), and the Constitution VI entry-point audit results.
- [ ] T047 Constitution VI entry-point audit: walk every new host-callable surface — `ForwardSearchReceiver` lifecycle + county/district/keypad/candidate `OnClickListener`s, `ForwardSearchTool` broadcast, boundary-facade query reachable from the reverse + forward workers, WKB parse on untrusted bytes — and cite file:line for each outer `try/catch(Throwable)` guard (mirrors 004 T056 / 005 ADR-0017 audit).
- [ ] T048 [P] Run `dart format`-equivalent (project Java formatter / Spotless) + `./gradlew :app:lintCivDebug` and fix any new warnings; confirm zero new analyzer findings (Constitution I).
- [ ] T049 Run the full JVM suite `./gradlew :app:testCivDebugUnitTest` and the on-device suite `./gradlew :app:connectedCivDebugAndroidTest`; confirm green and record the run in the ADR.
- [ ] T050 [P] Re-run `python scripts/measure_tw_central.py`, `python scripts/verify_research_claims.py`, `python scripts/verify_polygon_in.py` against the current `tw-central-full.zip` and confirm the fixture (T005) still matches; note any drift in the ADR.
- [ ] T051 Update `README` (v1.0.6 → v1.0.7 or the chosen version) + the feature-006 roadmap memory: mark forward search + Tier-1 townships consumption shipped; note roads.sqlite / places-osm / active-root migration still pending.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup — BLOCKS all user stories. Geometry (T007–T010) → Boundary facade (T011–T014) → Import consumption (T015–T019). The facade tests (T012) need the fixture (T005) and the geometry impl (T010).
- **US1+US2 (Phase 3)**: depend on Foundational (need the boundary facade + street query). This is the MVP.
- **US3 (Phase 4)**: depends on Foundational (boundary facade) + the `AddressSubsystem` from 005; independent of the forward page, but shares the boundary mount wired in T032.
- **US4 (Phase 5)**: depends on US1's controller + page (extends them).
- **On-device (Phase 6)**: depends on the relevant stories being implemented (US1/US2 for forward flow, US3 for reverse latency).
- **Polish (Phase 7)**: depends on all desired stories complete.

### Within each story

- Tests written and FAILING before implementation (Constitution II).
- Geometry before facade; facade before funnel/reverse; normaliser + street query before the controller; controller before the page.

### Parallel opportunities

- Setup T002/T003/T004 (different string files) and T005 (fixture) run in parallel.
- Foundational: T007 ∥ T008 (different test files); T011 ∥ (T012 after T010); T016 ∥ T019.
- US1: T020 ∥ T022 ∥ T026 (different files); T024 ∥ T025 (different impls, same contract).
- Phase 6: T040 ∥ T041 ∥ T042 (different instrumented test files).
- Polish: T044 ∥ T045 ∥ T048 ∥ T050.

---

## Parallel Example: Foundational geometry

```bash
# Write the two geometry test files together (both FAIL first):
Task: "PointInPolygonTest.java — ray-cast inside/outside/hole cases"
Task: "WkbMultiPolygonParserTest.java — type 3/6, holes, malformed→null"
# Then implement PointInPolygon (T009) and BoundaryGeometry+parser (T010) to green.
```

## Parallel Example: US1 street layer

```bash
Task: "StreetTextNormaliserTest.java (T020)"
Task: "AddressDatabaseFacade.streetCandidates + AddressCandidate (T022)"
Task: "ForwardSearchQuery + CountySource value types (T026)"
```

---

## Implementation Strategy

### MVP first (US1 + US2)

1. Phase 1 Setup → Phase 2 Foundational (geometry + boundary facade + boundary import).
2. Phase 3 US1+US2 → the funnel page with three county sources.
3. **STOP and VALIDATE**: open 前向搜尋, funnel to a GoTo; confirm no place DB opens before the street stage; confirm 清單 lists only installed counties.
4. Demo the MVP.

### Incremental delivery

1. Setup + Foundational → boundary layer mountable + queryable.
2. US1+US2 → forward search MVP → demo.
3. US3 → reverse path scoped → demo (invisible change, measure latency).
4. US4 → house-number keypad → demo.
5. Phase 6 on-device gates → record numbers in ADR-0018.
6. Phase 7 polish → docs, audit, i18n, re-verify.

---

## Notes

- [P] = different files, no incomplete dependency.
- [Story] label maps each task to US1–US4 for traceability; US1+US2 ship together as MVP.
- Every new host-callable entry point MUST have the outer `Throwable` guard (Constitution VI); T047 audits them.
- The WKB parser treats its input as untrusted (corrupt/truncated on-disk blob) and recovers to "no locality" rather than throwing.
- No new Gradle dependency unless the WKB parser is ever forced to JTS (research R1); the default path is hand-rolled (0 KB).
- On-device measurement is a release gate (SC-003/004/005), not optional polish — per the feature-006 roadmap memory.
- Commit after each task or logical group; re-verify the data fixtures (T050) after any generator rebuild.
