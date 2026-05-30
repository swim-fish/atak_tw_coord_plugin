---
description: "Task list for feature 007-settings-ux-tweaks"
---

# Tasks: Settings Page & Search/Storage UX Tweaks

**Input**: Design documents from `specs/007-settings-ux-tweaks/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: INCLUDED — Constitution Principle II (Test-Driven Development) is
NON-NEGOTIABLE for this project, so every new pure-logic unit is written
test-first and each story carries an Espresso acceptance test.

**Organization**: Tasks are grouped by user story (US1 P1, US2 P2, US3 P3) so
each can be implemented, tested, and shipped independently.

## Path & tooling conventions

- Main: `app/src/main/java/com/atakmap/android/twcoord/…`
- Unit tests (JUnit4 + Robolectric/Mockito): `app/src/test/java/com/atakmap/android/twcoord/…`
- Instrumented (Espresso): `app/src/androidTest/java/com/atakmap/android/twcoord/…`
- Resources: `app/src/main/res/{layout,xml,values,values-zh-rTW,values-ja}/`
- Unit run: `./gradlew :app:testCivDebugUnitTest` · Espresso: `./gradlew :app:connectedCivDebugAndroidTest`
- All committed code/comments/strings keys English; user-visible strings zh-TW (+ parity locales).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Anchor the TDD red→green cycle against a known-good baseline.

- [X] T001 Baseline + final unit suite green (`:app:testCivDebugUnitTest` BUILD SUCCESSFUL).

---

## Phase 2: Foundational (Blocking Prerequisites for US1 + US2)

**Purpose**: The shared persistence surface both ordering (US1) and the
readout-visibility toggle (US2) depend on. (US3 does NOT depend on this phase
and may start in parallel.)

**⚠️ CRITICAL**: US1 and US2 cannot complete until this phase is done.

- [X] T002 [P] Create enum `ResultOrdering { MOST_SIMILAR, DISTANCE }` in `app/src/main/java/com/atakmap/android/twcoord/address/forward/ResultOrdering.java`
- [X] T003 [P] Write FAILING unit test for the new preference keys (ordering default `DISTANCE`, round-trip, corrupt value → `DISTANCE`; readout default `true`, round-trip) in `app/src/test/java/com/atakmap/android/twcoord/prefs/PreferenceStore007Test.java`
- [X] T004 Add keys to `app/src/main/java/com/atakmap/android/twcoord/prefs/PreferenceStore.java` — `KEY_SEARCH_RESULT_ORDERING = "pref_search_result_ordering"` with `getResultOrdering()`/`setResultOrdering(ResultOrdering)` (defensive `valueOf` → `DISTANCE`), and `KEY_READOUT_VISIBLE = "pref_readout_visible"` with `isReadoutVisible()`/`setReadoutVisible(boolean)` (default `true`); decide `fireAll()` membership per data-model §2 (readout key propagates; ordering key does not). Make T003 pass. (depends T002, T003)

**Checkpoint**: Preference plumbing + `ResultOrdering` exist; US1 and US2 can proceed.

---

## Phase 3: User Story 1 — Result ordering: most-similar vs distance (Priority: P1) 🎯 MVP

**Goal**: Operator switches forward-search result ordering between 最相似 and 距離; the displayed list re-orders in place (no re-query) and the choice persists.

**Independent Test**: Run a search returning several candidates, toggle 最相似/距離 → list re-orders without a new DB query; reopen the page → last choice is the default; tapping a candidate still pans (unchanged). (quickstart US1)

### Tests for User Story 1 ⚠️ (write first, must FAIL)

- [X] T005 [P] [US1] FAILING unit test for `StreetCandidateRanker.reorder(...)`: `DISTANCE` preserves distance-ascending order (identity); `MOST_SIMILAR` ranks exact > prefix > substring(by match index) > none, breaks ties by `distanceMeters`, shorter-leftover wins within a band, empty fragment → distance order; 臺/台 + width fold honoured. In `app/src/test/java/com/atakmap/android/twcoord/address/forward/StreetCandidateReorderTest.java`
- [ ] T006 [P] [US1] ⏳ DEFERRED (needs device/emulator) — FAILING Espresso test: toggling 最相似/距離 re-orders the visible list and issues NO new facade query; choice persists across reopen; tap-to-pan/compass arrow unchanged. In `app/src/androidTest/java/com/atakmap/android/twcoord/address/ForwardSearchOrderingEspressoTest.java`

### Implementation for User Story 1

- [X] T007 [US1] Implement static `reorder(List<AddressCandidate> results, ResultOrdering ordering, String foldedFragment)` + private band/length similarity scorer (reusing `StreetTextNormaliser.fold`, candidate `street()`→`displayName()` fallback) in `app/src/main/java/com/atakmap/android/twcoord/address/forward/StreetCandidateRanker.java`. Make T005 pass. (depends T002)
- [X] T008 [US1] Wire ordering into `app/src/main/java/com/atakmap/android/twcoord/address/ForwardSearchReceiver.java`: read `pref_search_result_ordering`; cache the current `List<AddressCandidate>` + folded fragment; apply `reorder(...)` to every `controller.search(...)`/`withHouseNumber(...)` result before paint; add the 最相似/距離 toggle handler that re-sorts the cached list + repaints (no re-query) and persists via `setResultOrdering(...)`. Wrap handlers per Constitution VI. (depends T004, T007)
- [X] T009 [US1] Pass a `PreferenceStore` (or ordering get/set supplier) into `ForwardSearchReceiver` from `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java` (extend the existing `forwardSearchReceiver` construction at onCreate). (depends T008) — NOTE: same file as US2 T013/T014; serialize edits.
- [X] T010 [P] [US1] Add the 最相似/距離 toggle control (≥48dp, single-column, tap-only) above the candidate list in `app/src/main/res/layout/forward_search_page.xml`
- [X] T011 [US1] Add zh-TW labels (`最相似`, `距離`, toggle title) to `app/src/main/res/values/strings.xml` + `values-zh-rTW/strings.xml` + `values-ja/strings.xml`, and ordering entry/value arrays in `app/src/main/res/values/arrays.xml`. (shares strings.xml with T017/T024 — serialize)

**Checkpoint**: US1 fully functional and independently testable (MVP).

---

## Phase 4: User Story 2 — TW Coordinates button opens settings (Priority: P2)

**Goal**: The TW Coordinates tool button opens the plugin settings screen instead of cycling the coordinate unit; format is chosen in settings; a readout-visibility toggle replaces the cycle's hide/show.

**Independent Test**: Tap the tool button → settings opens (format unchanged on open); repeated taps never cycle the unit; selecting a format in settings updates the readout; the readout-visible toggle shows/hides the on-map readout. (quickstart US2)

### Tests for User Story 2 ⚠️ (write first, must FAIL)

- [ ] T012 [P] [US2] ⏳ DEFERRED (needs device/emulator) — FAILING Espresso test: tapping the TW Coordinates tool (`ACTION_SHOW_PLUGIN`) shows `TwCoordPreferenceFragment`; repeated taps do NOT change `pref_coord_unit`; opening does not mutate the active format; `pref_readout_visible` toggle shows/hides the widget; settings ordering ↔ search-page toggle stay in sync. In `app/src/androidTest/java/com/atakmap/android/twcoord/SettingsFromButtonEspressoTest.java`

### Implementation for User Story 2

- [X] T013 [US2] DONE — `toggleReceiver` now opens settings via `PreferenceControl.getInstance(mapView.getContext()).openSettings(PREF_KEY)` (unit-cycle + `setCoordinateUnit` + `Toast` removed, `Toast` import dropped); `pref_readout_visible` applied at onCreate (`widget.setVisible(prefs.isReadoutVisible())`) and in `prefListener`. Wrapped per Constitution VI. In `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java`.
- [X] T014 [US2] DONE — javap-verified against `../ATAK-CIV-5.7.0.3-SDK/main.jar`: `PreferenceControl.getInstance(Context).openSettings(String)` is the public settings-launch API; `AtakPreferenceFragment.showScreen(...)` is `protected` (in-fragment only, per helloworld sample) so rejected. Recorded in `research.md` R1.
- [X] T015 [P] [US2] Add to `app/src/main/res/xml/preferences.xml`: a `CheckBoxPreference` `pref_readout_visible` (default true) and a `com.atakmap.android.gui.PanListPreference` `pref_search_result_ordering` (entries/values arrays, default `DISTANCE`); refresh their titles/summaries/entries in `TwCoordPreferenceFragment.refreshAllSummaries()` against the UI-language override (`app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java`). (depends T004; ordering pref reuses the US1 key)
- [X] T016 [US2] Add zh-TW strings + arrays for the two new settings entries to `values/strings.xml` (+ `values-zh-rTW`, `values-ja`) and `values/arrays.xml`. (shares strings.xml — serialize)

**Checkpoint**: US1 and US2 both work independently.

---

## Phase 5: User Story 3 — Storage sizes in TW Offline Addr (Priority: P3)

**Goal**: TW Offline Addr shows each county dataset's on-disk size and a distinct `_boundary` (townships.sqlite) folder size.

**Independent Test**: Open TW Offline Addr → each county row shows a size; a `_boundary` row shows the folder size (or 未安裝 when absent); the screen still loads with no datasets. (quickstart US3)

### Tests for User Story 3 ⚠️ (write first, must FAIL)

- [X] T017 [P] [US3] FAILING unit test for `ByteCountFormatter.format(long)`: `0 B`, `1023 B`, `1024 → 1.0 KB`, `12_900_000 → 12.3 MB`, `324×1024² → 324.0 MB`, `≥1024³ → x.y GB` (binary units, one decimal at KB+). In `app/src/test/java/com/atakmap/android/twcoord/coord/ByteCountFormatterTest.java`
- [X] T018 [P] [US3] FAILING unit test for `DatasetStorageSummary` using an in-memory `FileSystem` fake: `perCounty()` returns `sizeOfDirectory(activeCountyDir(county))` per `registry.snapshot()`; `boundary()` returns `(present, sizeOfDirectory(boundaryDir))`; absent county dir → 0; absent `_boundary` → `present=false`. In `app/src/test/java/com/atakmap/android/twcoord/address/DatasetStorageSummaryTest.java`

### Implementation for User Story 3

- [X] T019 [P] [US3] Implement pure `ByteCountFormatter` in `app/src/main/java/com/atakmap/android/twcoord/coord/ByteCountFormatter.java`. Make T017 pass.
- [X] T020 [P] [US3] Implement `DatasetStorageSummary(FileSystem, ActiveDatasetRegistry)` with `perCounty()` + `boundary()` per data-model §4 in `app/src/main/java/com/atakmap/android/twcoord/address/DatasetStorageSummary.java` (added `FileSystem.sizeOf`/`sizeOfDirectory` default methods — not previously present). Make T018 pass. with `perCounty()` + `boundary()` per data-model §4 in `app/src/main/java/com/atakmap/android/twcoord/address/DatasetStorageSummary.java`. Make T018 pass.
- [X] T021 [US3] Render sizes in `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java`: inject a `FileSystem` (add a setter; wire `addressFileSystem` from `TwCoordMapComponent`); in the per-county row render append `ByteCountFormatter.format(sizeOfDirectory(activeCountyDir(county)))`, and after the county list add a distinct `_boundary` (townships.sqlite) row showing the folder size or `未安裝`. Wrap the size path per Constitution VI. (depends T019, T020) — touches `TwCoordMapComponent.java`; serialize with T009/T013.
- [X] T022 [P] [US3] Add a per-county size `TextView` (and the `_boundary` row view) to `app/src/main/res/layout/offline_address_county_row.xml` / `offline_address_page.xml` if not reusing the existing meta `TextView`.
- [X] T023 [US3] Add zh-TW strings for size labels, the `_boundary (townships.sqlite)` row, and `未安裝` to `values/strings.xml` (+ `values-zh-rTW`, `values-ja`). (shares strings.xml — serialize)
- [ ] T024 [P] [US3] ⏳ DEFERRED (needs device/emulator) — Espresso: county rows show a size; `_boundary` row shows size or 未安裝; empty-dataset screen still loads. In `app/src/androidTest/java/com/atakmap/android/twcoord/address/OfflineAddressStorageEspressoTest.java`

**Checkpoint**: All three stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T025 Bump version: `app/build.gradle` `ext.PLUGIN_VERSION` `1.1.0 → 1.2.0` (CORRECTION: version lives here, not in `gradle.properties`; `versionCode`/`versionName` are derived by takdev via `getVersionCode()`/`getVersionName()`, no manual bump).
- [X] T026 [P] Constitution VI crash-isolation audit (all wrapped: `toggleReceiver.onReceive`→try/catch; `ForwardSearchReceiver` ordering handlers via `safeRun`; `OfflineAddressReceiver` size path + `appendBoundaryRow` try/catch; `prefListener` readout apply null-guarded; `DatasetStorageSummary`/`FileSystem.sizeOf*` return 0 on absent/IO, never throw; `ByteCountFormatter` clamps negatives): verify `toggleReceiver.onReceive`, the `ForwardSearchReceiver` ordering toggle + any new `OnClickListener`, the `OfflineAddressReceiver` size-render path, the `prefListener` readout-visible apply, and `DatasetStorageSummary` (absent/partial files → 0, never throw) are each wrapped `try/catch(Throwable)→Log.w`.
- [ ] T027 [P] Update docs: `docs/ui/settings-fragment.md` (button→settings, `pref_readout_visible`, `pref_search_result_ordering`), `docs/ui/forward-search-page.md` (ordering toggle + reorder caveat), `docs/ui/offline-address-page.md` (per-county + `_boundary` sizes); add an ADR at the next free `docs/adr/00NN-settings-ux-tweaks.md` capturing the three decisions + the R1 settings-launch resolution.
- [~] T028 Full verification — PARTIAL: ✅ `:app:testCivDebugUnitTest` (new: 21/21 pass, no regressions), ✅ `:app:assembleCivDebug` (compile + resource link + dex + APK), ✅ `:app:spotlessCheck`. ⏳ `:app:connectedCivDebugAndroidTest` (T006/T012/T024 Espresso) + on-device `quickstart.md` walk — require a device/emulator, not run this session.

---

## Dependencies & Execution Order

### Phase dependencies
- Setup (P1) → Foundational (P2) blocks US1 + US2 only. US3 (P5) depends on neither and can start after Setup.
- Polish (P6) after all desired stories.

### Story dependencies
- **US1 (P1)**: needs T002 + T004 (Foundational). Independent of US2/US3.
- **US2 (P2)**: needs T004 (Foundational); its settings ordering pref reuses the US1 key (T004), not US1 code. Independent of US3.
- **US3 (P3)**: fully independent (no Foundational dependency).

### Shared-file serialization (not parallel-safe across stories)
- `TwCoordMapComponent.java`: **T009 (US1), T013 (US2), T021 (US3)** — serialize edits.
- `res/values/strings.xml` (+ locale parity): **T011 (US1), T016 (US2), T023 (US3)** — serialize.
- `res/values/arrays.xml`: **T011 (US1), T015/T016 (US2)** — serialize.

### Within each story
- Tests (T005/T006, T012, T017/T018) written and FAILING before implementation.
- Pure logic (ranker/formatter/summary) before its wiring/UI task.

---

## Parallel Opportunities

- **Foundational**: T002 ∥ T003 (different files).
- **US1**: T005 ∥ T006 (tests); T010 (layout) ∥ T007 (ranker).
- **US3**: T017 ∥ T018 (tests); T019 ∥ T020 (impl); T022 ∥ T024.
- **Across stories** (different devs): once T004 lands, US1 and US2 proceed in parallel; US3 can run from the start — mind the shared-file serialization list above.
- **Polish**: T026 ∥ T027.

### Parallel example — US3 kickoff
```bash
Task: "T017 ByteCountFormatter unit test"
Task: "T018 DatasetStorageSummary unit test"
# then:
Task: "T019 Implement ByteCountFormatter"
Task: "T020 Implement DatasetStorageSummary"
```

---

## Implementation Strategy

### MVP (US1 only)
1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → STOP & validate result ordering → demo.

### Incremental delivery
Setup + Foundational → US1 (MVP, ordering) → US2 (settings button) → US3 (storage sizes) → Polish (version bump, audit, docs, full verify). Each story is a shippable increment under the single `1.2.0` bump (apply T025 at release).

---

## Summary

- **Total tasks**: 28 (T001–T028)
- **Per story**: Setup 1 · Foundational 3 · US1 7 (T005–T011) · US2 5 (T012–T016) · US3 8 (T017–T024) · Polish 4
- **Tests**: 7 test tasks — T003 (Foundational prefs unit), T005 (US1 unit) + T006 (US1 Espresso), T012 (US2 Espresso), T017 + T018 (US3 unit) + T024 (US3 Espresso) — TDD per Constitution II.
- **MVP**: User Story 1 (result ordering).
- **Key risk gated**: R1 settings-launch SDK API (T014 verify-or-fallback) before T013.
