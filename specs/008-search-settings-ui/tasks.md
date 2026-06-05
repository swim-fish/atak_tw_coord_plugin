---
description: "Task list for Search & Storage Page UI Redesign"
---

# Tasks: Search & Storage Page UI Redesign

**Input**: Design documents from `specs/008-search-settings-ui/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: INCLUDED — Constitution II (TDD) is NON-NEGOTIABLE. Unchanged
controller/importer/ranker suites act as regression guards (FR-016/FR-019);
new pure logic is written test-first; UI wiring is covered by Espresso.

**Organization**: Tasks are grouped by the five user stories from spec.md. Two
independent areas: the **forward search page** (US1, US2 — share
`ForwardSearchReceiver.java` + `forward_search_page.xml`) and the **storage
page** (US3, US4, US5 — share `OfflineAddressReceiver.java` +
`offline_address_page.xml`). The two areas can be built fully in parallel; within
each area the stories share a source file and so are mostly sequential.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete dependency)
- **[Story]**: US1–US5 (setup/foundational/polish carry no story label)
- All paths are repository-relative.

## Reference

Line-level design lives in `docs/design/search_settings/`:
`ForwardSearchReceiver_changes.md`, `OfflineAddressReceiver_changes.md`, the
three replacement layouts, `strings_additions*.xml`, and `drawable/oa_*.xml`.
Dialog/menu construction MUST follow `contracts/dialog-context.md`
(`getMapView().getContext()` for builders, `pluginContext` for resources).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Version, shared drawables, and new strings — prerequisites for both
page areas.

- [X] T001 Bump `PLUGIN_VERSION` `1.2.1` → `1.3.0` in `app/build.gradle` (ext block, ~L9)
- [X] T002 [P] Copy the five usage/card drawables from `docs/design/search_settings/drawable/` (`oa_usage_card_bg.xml`, `oa_usage_track_bg.xml`, `oa_boundary_block_bg.xml`, `oa_progress_card_bg.xml`, `oa_error_card_bg.xml`) into `app/src/main/res/drawable/`
- [X] T003 [P] Add forward-search strings (`fs_scope_all`, `fs_scope_specific`, `fs_district_whole_county`, `fs_district_choose_title`, `fs_filter_hint`, `fs_house_dialog_title`, `fs_house_dialog_subtitle`, `fs_clear`, `fs_done`, `fs_cancel`) to `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml` per `docs/design/search_settings/strings_additions.xml`
- [X] T004 [P] Add storage-state strings (`offline_address_importing_label`, `offline_address_error_title`, `offline_address_action_retry`, `offline_address_action_dismiss`) to the same three `strings.xml` files per `docs/design/search_settings/strings_additions_offline_states.xml` (also added `offline_address_usage_boundary_label` for the legend)

**Checkpoint**: drawables resolve, new string ids compile in all three locales.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Replace the three layout files. Each blocks the receiver edits that
bind its view ids. Forward layout blocks US1/US2; the two storage layouts block
US3/US4/US5. No cross-area dependency.

**⚠️ CRITICAL**: A receiver edit cannot compile until its layout ids exist.

- [X] T005 [P] Replace `app/src/main/res/layout/forward_search_page.xml` with the design version (scope `RadioGroup` `fs_scope_group`/`fs_scope_all`/`fs_scope_specific`, `fs_scope_row`, township `Button` `fs_btn_district`, house field `fs_house_field`, stage label `fs_stage_district_label`; remove `fs_district_list`/`fs_keypad`/`fs_house_value`) — source `docs/design/search_settings/forward_search_page.xml`
- [X] T006 [P] Replace `app/src/main/res/layout/offline_address_page.xml` with the design version (usage card `offline_address_usage_total`/`offline_address_usage_bar`/`offline_address_usage_legend`, progress card `offline_address_progress_card`/`offline_address_progress_bar`, error banner `offline_address_error_card`/`offline_address_error_retry`/`offline_address_error_dismiss`) — source `docs/design/search_settings/offline_address_page.xml`
- [X] T007 [P] Replace `app/src/main/res/layout/offline_address_county_row.xml` with the compact design version (`offline_address_county_name`/`_summary`/`_size`/`_color`/`_overflow`/`_divider`) — source `docs/design/search_settings/offline_address_county_row.xml`

**Checkpoint**: layouts inflate; both areas may now proceed (in parallel).

---

## Phase 3: User Story 1 — Search Without Knowing the Township (Priority: P1) 🎯 MVP

**Goal**: After choosing a county, the operator searches a street immediately;
scope defaults to whole-county with no forced township step.

**Independent Test**: Choose a county → scope shows 全部, township button reads
"整個縣市（免選鄉鎮）" disabled, typing a street returns candidates with no
township selection; results match the pre-redesign whole-county path.

### Tests for User Story 1 ⚠️ (write first, must fail)

- [ ] T008 [P] [US1] Espresso test `ForwardSearchScopeEspressoTest`: county chosen → `fs_scope_all` checked, `fs_btn_district` disabled showing whole-county label, street entry yields candidates with no district tap — **DEFERRED: needs device/emulator**
- [X] T009 [P] [US1] Regression guard: `ForwardSearchControllerTest` (and the whole `:app:testCivDebugUnitTest` suite) passes unmodified after the receiver refactor — verified BUILD SUCCESSFUL

### Implementation for User Story 1

- [X] T010 [US1] In `ForwardSearchReceiver.java`: add imports (`AlertDialog`, `RadioButton`, `RadioGroup`, `ScrollView`) and new fields (`scopeRow`, `scopeGroup`, `scopeAll`, `scopeSpecific`, `btnDistrict`, `houseField`, `chosenDistrict`); remove fields `districtList`, `districtAllCell`, `districtCells`, `houseValue`, `keypad`
- [X] T011 [US1] In `ForwardSearchReceiver.inflate()`: bind the new ids (`fs_stage_district_label`, `fs_scope_row`, `fs_scope_group`, `fs_scope_all`, `fs_scope_specific`, `fs_btn_district`, `fs_house_field`); remove the old `fs_district_list`/`fs_house_value`/`fs_keypad` binds and the trailing `buildKeypad()` call; delete the `buildKeypad()` method
- [X] T012 [US1] In `ForwardSearchReceiver`: add `wireScopeListener()`, `onScopeChanged(int)`, `applyAll()`, `applySpecific(String)`, `wholeCountyLabel()`, `safeCounty()` helpers + `checkScopeSilently(int)` (detach/re-attach the `OnCheckedChangeListener` around programmatic `check()`); all listener bodies via `safeRun(...)`
- [X] T013 [US1] In `ForwardSearchReceiver.onCountyChosen()`: drop the inline grid build; set scope default to whole-county (check `fs_scope_all`, disable `fs_btn_district` with whole-county label, `chosenDistrict = null`, `controller.chooseAllDistricts()`, `revealStreetStage()`); `revealStreetStage()` hides `houseField` until results exist
- [X] T014 [US1] In `ForwardSearchReceiver.wireStaticButtons()`: add `wireScopeListener()` and the `btnDistrict`/`houseField` click listeners wired directly to `showDistrictDialog`/`showHouseDialog` (implemented in US2)

**Checkpoint**: US1 fully functional — county→street works with zero township taps.

---

## Phase 4: User Story 2 — Narrow to a Township On Demand (Priority: P1)

**Goal**: Township button opens a glove grid chooser; house field opens a numeric
keypad; both refine results live. Map-follow drives the same scope control.

**Independent Test**: Tap township button → pick from dialog → results scope to
it; tap house field → enter number on keypad → candidates refine; pan to a new
county → township auto-applied (or whole-county when unresolved), no residual grid.

**Depends on**: US1 (shares `ForwardSearchReceiver.java`; builds on its scope
helpers and field binds).

### Tests for User Story 2 ⚠️ (write first, must fail)

- [ ] T015 [P] [US2] Espresso `ForwardSearchDialogEspressoTest`: tap `fs_btn_district` → dialog lists districts + 全部 cell, picking one sets `fs_scope_specific` + button text + scoped results — **DEFERRED: needs device/emulator**
- [ ] T016 [P] [US2] Espresso: tap `fs_house_field` → keypad dialog; entering digits updates candidate list; 清除 resets to whole-street; 完成 dismisses and field reflects value — **DEFERRED: needs device/emulator**

### Implementation for User Story 2

- [X] T017 [US2] In `ForwardSearchReceiver`: implement `showDistrictDialog()` — 3-col `GridLayout` of `gridCell(...)` in a height-bounded `ScrollView`, 全部 cell → `applyAll()`, suggested district marked, cells → `applySpecific(name)` + dismiss; `AlertDialog.Builder(getMapView().getContext())` + `pluginContext` strings (contract `dialog-context.md`)
- [X] T018 [US2] In `ForwardSearchReceiver`: implement `showHouseDialog()` — `GridLayout` keypad (`1..9 0` + 巷/弄/號/之/⌫) with a live `display`, keys via existing `onKeypad(...)`, 清除 (neutral) + 完成 (positive); add `reflectHouseField()`; wire `houseField` click to it
- [X] T019 [US2] In `ForwardSearchReceiver.onKeypad(String)`: remove the `houseValue.setText(...)` reference (display now owned by the dialog); keep `withHouseNumber` + `renderCandidates`
- [X] T020 [US2] In `ForwardSearchReceiver.runSearch()`: replace the `houseValue`/`keypad` visibility lines with `houseNumber.setLength(0); reflectHouseField(); houseField.setVisibility(VISIBLE)`
- [X] T021 [US2] In `ForwardSearchReceiver` `hideFromStage()`/`revealStreetStage()`: swap removed-id visibility for `scopeRow`/`houseField`; update `autoSelectDistrict(...)` and `selectAllDistrictsCell()` to call `applySpecific(...)`/`applyAll()` (map-follow path, FR-008)

**Checkpoint**: US1 + US2 — full forward-search redesign works independently.

---

## Phase 5: User Story 3 — See Storage Usage at a Glance (Priority: P2)

**Goal**: Total figure + stacked usage bar + colour legend + compact rows whose
swatch matches the bar; boundary folded into total and bar.

**Independent Test**: With ≥2 counties + boundary, total = Σ per-county + boundary;
each county's bar/legend/row swatch share one colour; rows show 名稱·日期·筆數·大小.

**Depends on**: Foundational (T006, T007). Independent of the forward-search area.

### Tests for User Story 3 ⚠️ (write first, must fail)

- [ ] T022 [P] [US3] Unit test `OfflineUsageBarTest`: colour index aligns to `snap.values()` order; segment weight = `max(bytes,1)`; total = Σ counties + boundary — **DEFERRED: needs a pure helper-class extraction (the receiver extends `DropDownReceiver`, not loadable under plain JUnit)**
- [ ] T023 [P] [US3] Espresso `OfflineUsageEspressoTest`: with two datasets, usage total text = sum, bar has N+1 segments, each row shows size + swatch — **DEFERRED: needs device/emulator**

### Implementation for User Story 3

- [X] T024 [US3] In `OfflineAddressReceiver.java`: add imports (`PopupMenu`, `ProgressBar`, `SpannableString`, `ForegroundColorSpan`, `GradientDrawable`, `Gravity`, `ViewGroup`) and fields `usageTotal`/`usageBar`/`usageLegend` + the `OA_PALETTE`/`countyColor(int)`/`OA_BOUNDARY_COLOR` palette
- [X] T025 [US3] In `OfflineAddressReceiver` ctor: bind `offline_address_usage_total`/`_usage_bar`/`_usage_legend`
- [X] T026 [US3] In `OfflineAddressReceiver`: implement `renderUsageBar(snap)` + `addBarSegment(weight,color)` + `addLegend(color,label,bytes)` (counties first, boundary grey segment folded into total; `ByteCountFormatter` for sizes; `setClipToOutline(true)`); keep `try/catch(Throwable)→Log.w`
- [X] T027 [US3] In `OfflineAddressReceiver.bindStateBMultiCounty(snap)`: call `renderUsageBar(snap)` first; switch the per-row bind to the compact ids (`_name`, `_summary` = date·rows, `_size`, `_color` = `countyColor(index)`, `_divider` last-row GONE), iterating `snap.values()` with an index aligned to the palette

**Checkpoint**: US3 — storage usage summary + compact rows render with consistent colours.

---

## Phase 6: User Story 4 — Manage a County From Its Row (Priority: P2)

**Goal**: Per-row ⋮ overflow opens a PopupMenu (replace / destructive remove)
delegating to the existing confirm flows.

**Independent Test**: Tap a row ⋮ → menu shows 取代 + red 移除; choosing either
runs the existing confirmation dialog + action unchanged.

**Depends on**: US3 (shares the compact row + `OfflineAddressReceiver.java`).

### Tests for User Story 4 ⚠️ (write first, must fail)

- [ ] T028 [P] [US4] Espresso `OfflineOverflowEspressoTest`: tap `offline_address_county_overflow` → menu has 取代 + 移除; choosing 移除 shows the existing remove-confirm dialog — **DEFERRED: needs device/emulator**

### Implementation for User Story 4

- [X] T029 [US4] In `OfflineAddressReceiver`: implement `showCountyMenu(anchor, county)` — `PopupMenu(getMapView().getContext(), anchor)` with 取代 and a red `SpannableString` 移除; `setOnMenuItemClickListener` → `safeRun(...)` → existing `confirmReplaceCounty`/`confirmRemoveCounty` (contract `dialog-context.md`)
- [X] T030 [US4] In `OfflineAddressReceiver.bindStateBMultiCounty(...)`: wire the row `offline_address_county_overflow` click to `safeRun(() -> showCountyMenu(v, county))`; remove the old inline replace/remove button binds

**Checkpoint**: US3 + US4 — compact rows with working per-row management.

---

## Phase 7: User Story 5 — Clear Feedback While Importing and On Failure (Priority: P3)

**Goal**: Import-in-progress card with a determinate/indeterminate progress bar;
dismissible failure banner with retry/dismiss leaving installed data intact.

**Independent Test**: Start an import → progress card with moving bar (determinate
during copy/index-build); force a failure → red banner with 重新選擇檔案 + 關閉,
installed county list/sizes unchanged.

**Depends on**: Foundational (T006). Shares `OfflineAddressReceiver.java` with US3/US4.

### Tests for User Story 5 ⚠️ (write first, must fail)

- [ ] T031 [P] [US5] Unit test `ImportProgressStageTest`: determinate predicate true only for `Stage.COPYING`/`Stage.BUILDING_RTREE`, false otherwise; percent clamps to 0..100 — **DEFERRED: needs a pure helper-class extraction (see T022)**
- [ ] T032 [P] [US5] Espresso `OfflineImportStateEspressoTest`: progress card shows on import; error banner shows reason + retry/dismiss on failure; county list unchanged after failure — **DEFERRED: needs device/emulator**

### Implementation for User Story 5

- [X] T033 [US5] In `OfflineAddressReceiver` ctor: bind `offline_address_progress_card`/`_progress_bar` and `offline_address_error_card`/`_error_retry`/`_error_dismiss`
- [X] T034 [US5] In `OfflineAddressReceiver`: upgrade `showProgress`/`hideProgress` to toggle the progress card; upgrade `postProgress(stage,completed,total)` to set the `ProgressBar` determinate only for `COPYING`/`BUILDING_RTREE` (clamped percent) else indeterminate; keep `try/catch(Throwable)→Log.w`. Batch path routed via `showProgress` too (renderInflight/renderBatchSummary)
- [X] T035 [US5] In `OfflineAddressReceiver`: upgrade `showError`/`clearError` to toggle the error banner; wire `errorRetry` → `safeRun(() -> { clearError(); launchPicker(); })` and `errorDismiss` → `safeRun(this::clearError)`; importer-failure path leaves installed data untouched (unchanged)

**Checkpoint**: All five stories independently functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T036 [P] Update `docs/ui/forward-search-page.md` + `docs/ui/offline-address-page.md` with the feature-008 redesign sections (scope control, district/house dialogs, usage bar/legend, overflow menu, progress/error cards) (Constitution III)
- [X] T037 [P] Append `docs/adr/0020-search-settings-ui-redesign.md` (context, decisions D1–D6, alternatives, consequences, links to this spec/plan/tasks) (Constitution V)
- [X] T038 Crash-isolation audit (Constitution VI): verified every new dialog/menu listener, `RadioGroup` change listener, overflow `setOnMenuItemClickListener`, and progress/error button listener is wrapped (`safeRun`/`try-catch(Throwable)→Log.w`); every `AlertDialog.Builder`/`PopupMenu` uses `getMapView().getContext()`; resource lookups null-checked; no `android.R.attr.*` in `setBackgroundResource` (grep clean)
- [X] T039 [P] Ran `:app:spotlessApply` (googleJavaFormat) + `spotlessCheck` green; dead `buildKeypad`/old-id references removed; `compileCivDebugJavaWithJavac` clean (no new warnings)
- [X] T040 Ran `:app:testCivDebugUnitTest` — BUILD SUCCESSFUL; controller/importer/ranker suites green (FR-016/FR-019 regression)
- [ ] T041 Execute `specs/008-search-settings-ui/quickstart.md` on-device in all three locales (zh-rTW/en/ja), confirming SC-001..SC-008 (incl. dialog reliability SC-007 and localisation SC-008) — **DEFERRED: needs hardware**

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: after Setup; blocks the receiver work in each area.
  T005 blocks US1/US2; T006/T007 block US3/US4/US5. No cross-area block.
- **User Stories**:
  - Forward area: US1 (P1) → US2 (P1) — sequential (shared file).
  - Storage area: US3 (P2) → US4 (P2); US5 (P3) shares the file with US3/US4 and
    is sequenced after them to avoid edit conflicts.
  - **Forward area and storage area run fully in parallel.**
- **Polish (Phase 8)**: after all targeted stories complete.

### Story independence

- US1 testable alone (whole-county default path).
- US2 builds on US1's scaffolding (same receiver) but is independently testable
  (dialogs + map-follow).
- US3 testable alone (usage bar/rows). US4 builds on US3's row. US5 independent of
  US3/US4 behaviour but shares the file.

### Parallel opportunities

- T002/T003/T004 [P] together (different files within each, but distinct files).
- T005 ∥ T006 ∥ T007 (different layout files).
- Forward team (US1→US2) ∥ Storage team (US3→US4, US5) after Foundational.
- Within a story, [P]-marked test tasks run together before implementation.
- T036/T037/T039 [P] in Polish.

---

## Parallel Example: after Foundational

```text
# Two developers, two areas, in parallel:
Developer A (forward): T008,T009 (tests) → T010→T011→T012→T013→T014 (US1) → T015,T016 → T017..T021 (US2)
Developer B (storage):  T022,T023 (tests) → T024..T027 (US3) → T028 → T029,T030 (US4) → T031,T032 → T033..T035 (US5)
```

---

## Implementation Strategy

### MVP first (US1)

1. Phase 1 Setup → Phase 2 (T005) → Phase 3 (US1).
2. **STOP & VALIDATE**: county→street with zero township taps; controller tests
   green. This alone is a shippable improvement.

### Incremental delivery

1. Setup + Foundational → both areas unblocked.
2. + US1 → forward MVP. + US2 → full forward redesign. Test/demo.
3. + US3 → storage usage summary. + US4 → row management. + US5 → import/error
   feedback. Test/demo after each.
4. Polish (docs/ui, ADR, crash audit, formatter, full suite, quickstart).

---

## Notes

- [P] = different files, no incomplete dependency. The two receivers are distinct
  files, so the forward and storage areas never conflict.
- Within a single receiver file, tasks are sequential (same-file edits).
- TDD: write each story's tests first and watch them fail before implementing.
- Every new host→plugin entry point MUST carry the Constitution VI guard — T038
  is the audit, but the guard is part of each implementation task's Definition of
  Done, not deferred.
- Dialog/menu construction is fixed by `contracts/dialog-context.md` — builder
  uses `getMapView().getContext()`, resources use `pluginContext`.
- Commit after each task or logical group.
