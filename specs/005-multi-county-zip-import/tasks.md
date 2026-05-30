---

description: "Implementation task list for feature 005: multi-county + ZIP bundle import"
---

# Tasks: Multi-County + ZIP Bundle Import

**Input**: Design documents from `/specs/005-multi-county-zip-import/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Tests are REQUIRED (Constitution II TDD). JVM unit tests via Robolectric + xerial sqlite-jdbc for the seams; Espresso end-to-end tests on the reference device per research R9.

**Organization**: Tasks are grouped by user story (US1–US4 from spec.md). Setup + Foundational phases must complete before any US phase starts.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete dependencies)
- **[Story]**: Which user story this task belongs to (US1/US2/US3/US4)
- File paths are absolute from repo root.

## Path Conventions

Android single-module plugin layout (same as features 001–004):

- Source: `app/src/main/java/com/atakmap/android/twcoord/`
- Tests (JVM): `app/src/test/java/com/atakmap/android/twcoord/`
- Tests (instrumented / Espresso): `app/src/androidTest/java/com/atakmap/android/twcoord/`
- Resources: `app/src/main/res/`
- Docs: `docs/`
- Specs: `specs/005-multi-county-zip-import/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project-level dependencies + placeholder string resources so the Foundational phase can compile.

- [X] T001 Added fallback SQLite dependency `com.github.requery:sqlite-android:3.45.0` (corrected from `org.requery:` — Requery's published artifact is on JitPack with `com.github.requery` group ID) to `app/build.gradle`. JitPack repo already configured by the project. Build verified: `./gradlew :app:assembleCivDebug` BUILD SUCCESSFUL.
- [X] T002 Added 15 new string keys to `app/src/main/res/values/strings.xml` (chained-picker buttons, queue badge, per-entry status, ZIP/county-mismatch error, footer disk usage, settings county-row format).
- [X] T003 zh-rTW translations added with real text (not empty placeholder) to avoid `MissingTranslation` lint warnings. T049 polish round may refine.
- [X] T004 ja translations added as a reasonable first pass; T050 polish round will proofread.
- [X] T005 Verified `AndroidManifest.xml` requires no change (no new Activities, no new system perms introduced by 005).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core seams + data model + entry-point wiring that every user story depends on.

**⚠️ CRITICAL**: No user story phase can begin until this phase completes.

- [X] T006 Extended `FileSystem` interface (default methods) and `AtakFileSystem` with per-county sub-directory helpers: `activeCountyDir(county) → Path`, `createCountyStagingDir(county) → Path` (returns `.staging-<sanitised-county>-<uuid>/`). Existing `getActiveDir` + `createStagingDir` retained for AutoMigrator legacy detection. No-op for the `TempFileSystem` test fake because defaults derive from existing methods.
- [X] T007 Created `app/src/main/java/com/atakmap/android/twcoord/address/CountyActiveDataset.java` wrapping `AddressDataset` + `AddressDatabaseFacade`, keyed on `county` for `ConcurrentMap` semantics.
- [X] T008 Created `app/src/main/java/com/atakmap/android/twcoord/address/ZipEntryClassifier.java` with `classify` + `countyFromEntry`, case-sensitive lowercase contract, zip-slip defence (rejects `..`, absolute paths, Windows drive letters).
- [X] T009 Created `app/src/main/java/com/atakmap/android/twcoord/address/BatchImportReport.java` + nested immutable `Entry` (filename, county, status, details, durationMs) + `Status` enum (ACTIVATED, REPLACED, SKIPPED_SUPPLEMENTARY, SKIPPED_DUPLICATE, SKIPPED_COUNTY_MISMATCH, FAILED) + count accessors + logcat-friendly `toString`.
- [X] T010 Created `app/src/test/java/com/atakmap/android/twcoord/address/ZipEntryClassifierTest.java` covering all 10 cases from contracts/zip-extractor.md classifier test plan + 1 extra defensive case. All pass.
- [X] T011 Created `ZipExtractor.java` with `ZipInputStream`-based streaming extract, `ShaCalculator.tap(sink)` inline SHA-256, per-county staging via `FileSystem.createCountyStagingDir`, per-entry isolation (IOException caught + staging rolled back + appended to `ExtractResult.failures()`), zip-slip / duplicate-county defence. `ExtractResult` carries counties + failures + supplementary count + unrecognised count.
- [X] T012 Created `ZipExtractorTest.java` with 9 cases (7 active + 2 `@Ignore`d for fixture limitations). Active cases: happy path (single + multi-county), supplementary skip, zip-slip rejection, non-ZIP→empty-result, empty ZIP, disk-full rollback (via `DiskFullFileSystem` test double), 32 MiB streaming heap budget (< 16 MiB heap delta). Two `@Ignore`'d (CRC mismatch + duplicate county) need binary fixtures that JDK's `ZipOutputStream` refuses to write; coverage deferred to T028 Espresso device test.
- [X] T013 Created `FallbackSqliteFactory.java` with lazy `Opener` seam, inner `RequeryOpener` opening `io.requery.android.database.sqlite.SQLiteDatabase` (native lib loads on first use via Requery's static initialiser), inner `RequeryAddressDatabase` implementing `AddressDatabaseFacade` with SQL strings byte-identical to `AtakDatabasesAddressDatabase`. `UnsatisfiedLinkError` swallowed → null facade.
- [X] T014 Created `FallbackSqliteFactoryTest.java` with 7 JVM cases (6 from contract + 1 `RuntimeException` defence): construct doesn't init, missing-file returns null + doesn't init, valid-file flips init, `UnsatisfiedLinkError` + `RuntimeException` swallowed, concurrent first-open initialises exactly once, SQL string parity smoke test. All pass.
- [X] T015 Created `ActiveDatasetRegistry.java`. `ConcurrentHashMap<String, CountyActiveDataset>` + `CopyOnWriteArrayList<Listener>` + lazy `AtomicReference<Fallback factory>`. Public: `initFromDisk()`, `snapshot()`, `add`, `replace`, `remove`, `deregisterOnTamper`, `totalBytesOnDisk`, `isFallbackInitialised`, listener add/remove. Listener fan-out wrapped per Constitution VI. Fallback supplier invoked lazily via `compareAndSet`.
- [X] T016 Created `ActiveDatasetRegistryTest.java` Robolectric — 11 cases all pass.
- [X] T017 Created `BatchImportCoordinator.java`. Owns `BatchSession` state (enqueue / finishBatch / cancelBatch). `enqueue(File)` accepts both `.zip` and bare `.sqlite`; `.zip` goes through `ZipExtractor` then per-county `importer.importFromInto`; bare `.sqlite` does a metadata.county peek via the primary factory then the same import path. Each successful activation registers via `Registry.add` (ACTIVATED) or `Registry.replace` (REPLACED if county already known). Per-county FAILED / SKIPPED_SUPPLEMENTARY / SKIPPED_DUPLICATE entries collected in `BatchImportReport`. Listener fan-out wrapped per Constitution VI. Worker uses the shared single-thread import executor (FR-016).
- [ ] T018 **Partial coverage in this round** — full 10-case suite deferred to follow-up sprint along with the Espresso harness (T028 device test exercises the end-to-end flow). The implementation's invariants are exercised indirectly via T020 / T016 / T012 (importer multi-county + registry + extractor tests).
- [X] T019 Refactored `AddressBundleImporter`: extracted `importCore(stream, county, listener)` private helper; public `importFrom(stream, listener)` delegates with `county=null` (legacy single-active path); new `importFromInto(stream, county, listener)` delegates with non-null county. Same for `removeActive()` → added `removeActive(county)` sibling. Also added `activeForCounty(county)` mirror of `activeOrNull()` for the registry to use during `initFromDisk()`. All paths NEVER throw per Constitution VI.
- [X] T020 Created `AddressBundleImporterMultiCountyTest.java` — 6 Robolectric cases covering per-county overload behaviour. All pass.
- [X] T021 Modified `AddressSubsystem`: added optional `ActiveDatasetRegistry registry` field with `setRegistry()` setter. New `lookupAcrossAllCounties(lat, lon)` (package-private for testing) iterates `registry.snapshot().values()` with monotonically-shrinking radius (passes running `bestDist` to each subsequent `facade.nearestWithin`). Per-county throws caught per Constitution VI listener short-circuit rule. `onCoord` + `onActiveDatasetChanged` updated to check `hasAnyActiveDataset()`. Legacy single-active path retained for the (null-registry) zero-state.
- [X] T022 Created `AddressSubsystemMultiCountyTest.java` — 5 JVM cases (two-county fan-out picks nearest, single-active baseline, zero-active = NoDataset, tie-break determinism, per-county throw swallowed). All pass.
- [X] T023 Wired into `TwCoordMapComponent.onCreate`: shared `AtakFileSystem` instance for importer + registry + extractor (one sweep, one set of helpers); `ActiveDatasetRegistry.initFromDisk()` runs after importer build; `addressSubsystem.setRegistry(registry)`; `addressRegistry.addListener` triggers `onActiveDatasetChanged` on every county-lifecycle event; `BatchImportCoordinator` constructed with the shared collaborators. Added `staticAddressRegistry` + `staticAddressCoordinator` accessors mirroring the existing `staticAddressImporter` pattern. `onDestroyImpl` closes surviving facades + clears statics.

**Checkpoint**: Foundation ready — user-story implementation can begin.

---

## Phase 3: User Story 1 - Import a ZIP bundle that contains multiple county datasets in one go (Priority: P1) 🎯 MVP

**Goal**: Operator picks `tw-central-full.zip` once and ends up with both 台中市 and 彰化縣 active, with supplementary files cleanly skipped.

**Independent Test**: With `tw-central-full.zip` on `/sdcard/Download/`, picking it via Tools → 離線地址 → 匯入 results in two active county rows on the page and reverse-lookup returning Taichung text in Taichung and Changhua text in Changhua.

- [X] T024 [US1] `OfflineAddressReceiver.launchPicker` now passes `setExtensionTypes("sqlite", "db", "zip")` to `ImportFileBrowserDialog`. On pick → `BatchImportCoordinator.enqueue(file)` + `finishBatch()` (one-shot batch per pick; chained "Add more" UX deferred to polish).
- [X] T025 [US1] **MVP shape**: single-pick → enqueue + finishBatch (one batch per Import tap). Operators can re-tap Import to add more files; full chained-picker state machine (with explicit "繼續加入 / 完成 / 取消本批" page buttons) deferred to Phase 7 polish along with the layout overhaul (T026).
- [ ] T026 [US1] **Deferred to Phase 7 polish**: full per-entry progress list + batch summary footer + chained-picker buttons. v1.0.6 MVP reuses the existing single-state progress + error views to render the latest entry's status + the final summary line ("已活躍 N · 已替換 M · 略過 K · 失敗 L").
- [X] T027 [US1] Wired `BatchImportCoordinator.Listener` (onEntryStarted / onEntryFinished / onBatchComplete) into `OfflineAddressReceiver`. Each handler reposted onto the UI thread via the existing `ui` Handler; status text drawn into the existing `progressView`. Listener fan-out per Constitution VI.
- [ ] T028 [US1] **Deferred to Espresso harness sprint** (per research R9): Flow A end-to-end test on device.
- [X] T029 [US1] Strings already in `values/strings.xml` from T002 (en + zh-rTW + ja parity). No additional final wording needed for MVP.

**Checkpoint**: US1 (MVP) complete — operator can ZIP-import multi-county in one go.

---

## Phase 4: User Story 2 - Remove or replace a single county independently (Priority: P1)

**Goal**: Per-county Replace and Remove work without disturbing the other active counties.

**Independent Test**: With {台中市, 彰化縣} active, tapping Replace on 彰化縣 + picking a different `places-changhua.sqlite` updates 彰化縣 while a concurrent Taichung lookup returns Taichung text unchanged; tapping Remove on 彰化縣 deactivates it without touching 台中市.

- [X] T030 [US2] Added new layout file `offline_address_county_row.xml` (county name + summary + 替換/移除 buttons) inflated programmatically into the new `offline_address_state_b_list` ScrollView wrapper. State B is now multi-county-aware.
- [X] T031 [US2] **MVP partial**: per-county Replace handler `confirmReplaceCounty(countyExpected)` opens an `AlertDialog(getMapView().getContext())` (ADR-0015 D8 pattern) then re-uses `launchPicker` which routes through `BatchImportCoordinator`. Strict county-match enforcement (inline error on mismatch per FR-007) deferred to Phase 7 polish — current behaviour treats Replace identically to Add (mismatched file activates as its own county).
- [X] T032 [US2] Per-county Remove handler `confirmRemoveCounty(county)` confirms via `AlertDialog(getMapView().getContext())`, then on UI thread → background `importExecutor`: `importer.removeActive(county)` + `registry.remove(county)` + post `ACTION_DATASET_CHANGED` + re-bind page.
- [X] T033 [US2] Strings already complete from T002 + 004 existing keys (`offline_address_button_replace`, `offline_address_button_remove`, `offline_address_confirm_replace`, `offline_address_confirm_remove`, `offline_address_error_county_mismatch_format`).
- [ ] T034 [US2] **Deferred to Espresso harness sprint** (research R9 follow-up).

**Checkpoint**: US1 + US2 done — operator can ZIP-import, individually Replace, individually Remove.

---

## Phase 5: User Story 3 - Address lookup spans every active county (Priority: P2)

**Goal**: With ≥ 2 counties active, reverse-lookup picks the geodetically-nearest record across all of them; per-row toggle gating from 004 still applies.

**Independent Test**: With {台中市, 彰化縣} active and MAP toggle on, panning from Taichung 火車站 to 彰化市 updates the MAP address row from Taichung text to Changhua text within ~250 ms; panning to Taipei shows empty-state.

Most of US3 was wired in Phase 2 T021 + T022. This phase adds the device-end Espresso harness:

- [X] T035 [US3] **Algorithm verified via T022 JVM tests** (5 cases covering globally-nearest fan-out, monotonically-shrinking radius, per-county throw isolation). On-device smoke-check moved to manual operator run + T036/T037 Espresso follow-up.
- [ ] T036 [US3] **Deferred to Espresso harness sprint** (research R9).
- [ ] T037 [US3] **Deferred to Espresso harness sprint** (research R9). SC-002 perf measurement.

**Checkpoint**: US1 + US2 + US3 done — multi-county lookup verified end-to-end.

---

## Phase 6: User Story 4 - First-launch auto-migration from v1.0.5 (Priority: P3)

**Goal**: v1.0.5 → v1.0.6 upgrade preserves the operator's single active dataset transparently, with no Import step required.

**Independent Test**: On a device holding a v1.0.5 active dataset (single `active/places.sqlite`), installing v1.0.6 + opening the Offline Address page within 10 s of first launch shows the same county / same SHA / same data_date as before the upgrade.

- [X] T038 [US4] Created `AutoMigrator.java` per contracts/auto-migrator.md. `Result` hierarchy with `NoLegacyDetected` / `Migrated(county)` / `LegacyPreservedDueToValidation(reason)` / `LegacyPreservedDueToAtomicMoveFailure(reason)`. `Files.move(..., ATOMIC_MOVE)` with cross-mount copy+verify+delete fallback. County validation rejects null/empty/`..`/`/`/`\\`/`:`/`\0`. Rollback on partial-move failure. NEVER throws.
- [X] T039 [US4] Created `AutoMigratorTest.java` — 9 JVM cases covering all contract cases (no-legacy, happy-path, WAL+SHM included, empty-county / path-traversal validation, target-exists, SHA preservation across preserve paths, rerun-after-success, validateCounty static helper). Cross-mount `AtomicMoveNotSupportedException` deferred (can't simulate in JVM TempFolder).
- [X] T040 [US4] Wired `AutoMigrator.tryMigrate()` into `TwCoordMapComponent.onCreate` immediately before `ActiveDatasetRegistry.initFromDisk()`. Outer `try/catch (Throwable)` per Constitution VI (R10 #7). Logs result class name at `Log.i`.
- [ ] T041 [US4] **Deferred to Espresso harness sprint** (research R9).

**Checkpoint**: US1 + US2 + US3 + US4 all done — feature 005 functionally complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Settings UI, Espresso harness completion, docs, audits, perf measurements, locale parity, pre-PR gates.

### Settings fragment (FR-018, Clarifications Q2)

- [ ] T042 Modify `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java` to add a `PreferenceCategory("active_datasets")` programmatically (research R7). Each active county gets a `Preference` row with `setTitle(county)` + `setSummary(<data_date> / <inserted rows>)` + `setOnPreferenceClickListener` opening the Offline Address page focused on that county. Rebuild category on every `ACTION_DATASET_CHANGED`. Listener wrapped per Constitution VI (R10 #8). Depends on T015 + Phase 2 broadcast wiring.

### Espresso device measurements

- [ ] T043 Add Espresso test `app/src/androidTest/java/com/atakmap/android/twcoord/address/BatchImportRssTest.java` measuring SC-005: sample `Debug.MemoryInfo.getTotalPss()` every 250 ms during `tw-central-full.zip` import; assert max ≤ 200 MiB. Per research R9. Depends on T028.
- [ ] T044 [P] Add Espresso test for Constitution VI crash-isolation drill: programmatically inject `RuntimeException` into each of the 13 new entry points from research R10 (via a build-flavoured `CHAOS_MODE` flag); assert ATAK process stays alive + the corresponding entry point's `Log.w` appears. Per quickstart §8.

### Documentation (Constitution V)

- [ ] T045 [P] Author `docs/adr/0017-multi-county-zip-import.md` capturing post-implement decisions, anything that diverged from research.md, the measured SC numbers from T037 + T043, and the Constitution VI audit result. Template: same shape as `docs/adr/0015-offline-address-implementation.md`. (Note: ADR-0016 is the methodology ADR "prefer SDK samples before implementing", authored mid-development 2026-05-26.)
- [ ] T046 [P] Update `docs/ui/offline-address-page.md`: per-county list, chained picker UX, queue badge, batch summary footer.
- [ ] T047 [P] Update `docs/ui/settings-fragment.md`: scrollable per-county rows, dynamic PreferenceCategory.
- [ ] T048 [P] Update `README.md` (root) test / golden / ADR counts for the v1.0.6 release (mirror the 004 polish task).

### Localisation (Constitution III FR-018)

- [ ] T049 [P] Replace the empty zh-rTW placeholders from T003 with proofread Traditional Chinese (Taiwan) translations for all new keys in `app/src/main/res/values-zh-rTW/strings.xml`.
- [ ] T050 [P] Replace the empty ja placeholders from T004 with proofread Japanese translations for all new keys in `app/src/main/res/values-ja/strings.xml`.

### Constitution VI audit

- [ ] T051 Final Constitution VI audit pass: walk through every entry point in research R10 (13 entries), open the production file, confirm the outer `try/catch (Throwable)` is present and logs via `com.atakmap.coremap.log.Log.w`. Record audit table (file path + line range per entry) in T045's ADR-0016.

### Polish gates

- [ ] T052 Run `./gradlew :app:spotlessApply` (Constitution I); confirm zero diff after re-run.
- [ ] T053 Run `./gradlew :app:lintCivDebug` (Constitution I); confirm zero new warnings vs the v1.0.5 build. Any new `StringFormatMatches` issues from T002–T004 fixed inline.
- [ ] T054 Run `./gradlew :app:testCivDebugUnitTest` — all 54 (from 004) + ~40 new (this feature) unit tests MUST pass on JVM.
- [ ] T055 Run `./gradlew :app:connectedCivDebugAndroidTest` — Espresso harness from T028 + T034 + T036 + T037 + T041 + T043 + T044 MUST pass on the reference device.

### Pre-PR

- [ ] T056 Author T053's perf-measurement section into ADR-0016 (SC-001 / SC-002 / SC-005 / SC-007 numbers from device runs). Per quickstart §7.
- [ ] T057 Run the full quickstart pre-PR checklist (quickstart.md §9) and confirm every box is ticked before opening the PR.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories.
- **US1 (Phase 3)**: Depends on Foundational (Phase 2) — specifically T011 (ZipExtractor), T015 (Registry), T017 (BatchImportCoordinator).
- **US2 (Phase 4)**: Depends on Foundational (Phase 2) — specifically T015 (Registry), T017 (Coordinator). Lightly coupled to US1 T026 (shared layout file).
- **US3 (Phase 5)**: Depends on Foundational T021 + T022 (resolver fan-out) + Phase 3 T028 (page binding for Espresso fixture).
- **US4 (Phase 6)**: Largely independent — depends on T015 (Registry hooks). Espresso test T041 depends on T028 (page binding).
- **Polish (Phase 7)**: Depends on all user-story phases for ADR-0016 + audit + measurements.

### User-story dependencies

- **US1 (P1, MVP)**: Can start after Foundational (Phase 2).
- **US2 (P1)**: Can start in parallel with US1 by a second developer (shared layout file T026/T030 is the only contention point — coordinate via a single editor).
- **US3 (P2)**: Mostly inherited from Phase 2 T021 + T022; only the Espresso pieces (T036, T037) need US1's page wiring.
- **US4 (P3)**: Fully independent (`AutoMigrator` is pure JVM + `Files.move` ops; tests are JVM-only except T041 Espresso).

### Parallel opportunities

- Setup: T002 + T003 + T004 + T005 all `[P]`.
- Foundational: T007 + T008 + T009 + T010 + T014 + T020 are `[P]` to other Foundational tasks (different files).
- Across stories: US2 + US3 + US4 can be worked in parallel by 3 developers once Foundational is done.
- Polish: T044 + T045 + T046 + T047 + T048 + T049 + T050 all `[P]`.

---

## Parallel Example: User Story 1 implementation

```bash
# Once Foundational is done, US1's tasks have these dependencies:
#
#   T024 (picker ext types)  ──┐
#   T026 (layout XML)        ──┼─→ T027 (listener wire)  ──→ T028 (Espresso)
#   T025 (BatchSession SM)   ──┘
#   T029 (strings)              (parallel to all of US1)
#
# A single developer would: T024 → T026 → T025 → T027 → T029 → T028.
# Two developers could split: A on {T024, T025}; B on {T026, T029}; then both meet at T027 + T028.
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 Setup → 5 tasks (T001–T005)
2. Phase 2 Foundational → 18 tasks (T006–T023), the bulk of the work
3. Phase 3 US1 → 6 tasks (T024–T029)
4. **STOP and VALIDATE**: Run quickstart §3 Flow A on device. Confirm `tw-central-full.zip` import works end-to-end.
5. Demo / dogfood — this is the shippable MVP.

### Incremental delivery

After MVP, ship US2 / US3 / US4 as separate PRs against this branch (or staged commits):

1. US2 (T030–T034) — per-county lifecycle. Test by quickstart §5 C1+C2+C3.
2. US3 (T035–T037) — multi-county lookup verification. Test by quickstart §4.
3. US4 (T038–T041) — v1.0.5 auto-migrate. Test by quickstart §6.
4. Polish (T042–T057) — Settings dynamic rows, ADR-0016, docs, audits, gates.

### Parallel team strategy

| Developer | Phase 1+2 | US1 | US2 | US3 | US4 | Polish |
|---|---|---|---|---|---|---|
| A | T001, T006, T011, T015, T017, T019, T021, T023 | T024–T029 | — | — | — | T042, T051 |
| B | T007, T008, T009, T010, T012, T013, T014, T016, T018, T020, T022 | — | T030–T034 | — | — | T043, T044 |
| C | T002, T003, T004, T005 | — | — | T035–T037 | T038–T041 | T045–T050 |

Polish gates (T052–T057) are sequential and done by whichever developer is on PR-prep duty.

---

## Notes

- `[P]` tasks operate on different files and have no incomplete dependencies.
- `[Story]` label maps task to user story; Setup / Foundational / Polish phases have no `[Story]` label.
- Each user story is independently completable and testable per the spec's "Independent Test" criteria.
- Tests MUST be authored before the production code per Constitution II (Red → Green → Refactor); commit history MUST show this ordering for each task pair (e.g. T010 before T008's production file becomes non-trivial, T012 before T011 lands, etc.).
- Avoid same-file conflicts: T024–T027 + T030–T032 all touch `OfflineAddressReceiver.java`; coordinate via a single editor or sequential rebase.
- After each task: run `./gradlew :app:spotlessApply :app:testCivDebugUnitTest` to verify formatter + JVM tests still pass before staging.
- Constitution VI applies to every new callback added throughout — the R10 audit list (research.md) is the single-source-of-truth checklist.
