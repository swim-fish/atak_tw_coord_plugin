---
description: "Task list for GoTo Coordinate-Input Page UI Redesign"
---

# Tasks: GoTo Coordinate-Input Page UI Redesign

**Input**: Design documents from `specs/010-goto-ui-redesign/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/goto-ui-contract.md, quickstart.md

**Tests**: NOT included as separate test-writing tasks. This is a pure
presentation-layer refactor with **no behaviour change** (Constitution II refactor
exemption). The existing GoTo unit suite MUST stay green **unmodified**; no
Espresso/instrumented test references the affected view ids. Behaviour
preservation is verified by the Polish-phase gate (T015) and the SC-005 check
(T017).

**Organization**: Tasks are grouped by user story (US1–US5 from spec.md) in
priority order. Because the page is a single layout file
(`app/src/main/res/layout/tw_coord_goto.xml`) plus one view class
(`TwCoordGotoView.java`), US1's full layout rewrite (T004) is the shared visual
base; each later story owns the Java/drawable wiring and verification specific to
its section. See Dependencies for the resulting order.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1–US5 maps to the spec's user stories
- Exact file paths are included in each task

## Path Conventions

Single-module Android ATAK plugin. Sources under `app/src/main/`; design source
under `docs/design/search_settings/`.

---

## Phase 1: Setup

**Purpose**: Confirm inputs and the id surface before editing.

- [x] T001 Re-confirm the view-id surface in `specs/010-goto-ui-redesign/contracts/goto-ui-contract.md` against the current `app/src/main/res/layout/tw_coord_goto.xml` and `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoView.java` (preserved / added `goto_autofill` / removed `goto_autofill_taipower|_twd97|_twd67`); note any id drift before proceeding.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared resources every story's layout references. MUST complete before the layout rewrite (T004).

**⚠️ CRITICAL**: T004 and all story work depend on these.

- [x] T002 [P] Add the 9 `goto_*` drawables to `app/src/main/res/drawable/`, adapted from `docs/design/search_settings/drawable/`: `goto_segment_track.xml`, `goto_tab_selected.xml`, `goto_input_bg.xml`, `goto_zone_cell_bg.xml`, `goto_marker_cell_bg.xml`, `goto_autofill_bg.xml`, `goto_advisory_bg.xml`, `goto_submit_primary_bg.xml`, `goto_submit_secondary_bg.xml`. Selection colour MUST be expressed via `state_checked`/`state_selected`/`state_enabled` with concrete resource ids only (Constitution VI — no `android.R.attr.*`).
- [x] T003 [P] Apply the string changes per `contracts/goto-ui-contract.md` in `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml`: update values for `goto_marker_mode_header`, `goto_btn_submit`, `goto_btn_autofill`, `goto_btn_atak_picker`, and add the new `goto_taipower_help` (source values in `docs/design/search_settings/strings_additions_goto.xml`).

**Checkpoint**: Drawables + strings exist — the layout rewrite can compile.

---

## Phase 3: User Story 1 - Consistent, scannable single-screen layout (Priority: P1) 🎯 MVP

**Goal**: Bring GoTo into the feature-008 compact stacked look — single column, segmented coordinate-system selector, carded fields — consistent with the two sibling pages.

**Independent Test**: Open GoTo; the system selector is a segmented control and each system shows carded fields in one shorter column; switching Taipower/TWD97/TWD67 keeps consistent styling.

- [x] T004 [US1] Rewrite `app/src/main/res/layout/tw_coord_goto.xml` to the compact stacked design adapted from `docs/design/search_settings/tw_coord_goto.xml`: single-column `ScrollView`; header row with title + single `goto_autofill`; `goto_tabs` `RadioGroup` using `@drawable/goto_segment_track`; three panes (`goto_pane_taipower|twd97|twd67`) with `@drawable/goto_input_bg` carded inputs; submit area (`goto_btn_submit` → `@drawable/goto_submit_primary_bg`, `goto_btn_atak_picker` → `@drawable/goto_submit_secondary_bg`); marker grid with `@drawable/goto_marker_cell_bg`; zone selectors with `@drawable/goto_zone_cell_bg`; advisories with `@drawable/goto_advisory_bg`. Keep ALL ids in the contract's Preserved list; replace the three `goto_autofill_*` with one `goto_autofill`; wire `goto_taipower_help` under the Taipower input.
- [x] T005 [US1] Update `styleTab(RadioButton, boolean)` in `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoView.java` to apply `R.drawable.goto_tab_selected` (pill) + bold dark text on the selected tab and a transparent background on unselected tabs (per `TwCoordGotoView_changes.md` §3); `applyTabVisibility()` unchanged.

**Checkpoint**: Page renders in the new stacked style with working segmented tabs; coordinate entry still parses/validates as before.

---

## Phase 4: User Story 2 - Clear primary action, no submit-button confusion (Priority: P1)

**Goal**: One emphasised "Submit & go" vs a ghost "Use ATAK icon palette…", removing the two-equal-buttons confusion.

**Independent Test**: With a valid coordinate, the primary button is visibly dominant and the ATAK-palette button is subordinate; pressing primary pans the map.

- [x] T006 [US2] In `app/src/main/res/layout/tw_coord_goto.xml`, verify/adjust the submit area so `goto_btn_submit` is the enlarged filled primary (`goto_submit_primary_bg`, text `#06222E`) and `goto_btn_atak_picker` is the ghost secondary (`goto_submit_secondary_bg`, text `#BFBFBF`) per design (depends on T004; same file).
- [x] T007 [US2] (Optional, appearance-only) In `refreshSubmitEnabled()` of `TwCoordGotoView.java`, set `submitButton` text colour by enabled state (`coordOk ? 0xFF06222E : 0xFF5F6B70`) per `TwCoordGotoView_changes.md` §5. Enable/disable logic itself is unchanged.

**Checkpoint**: Submit hierarchy is unmistakable; submit-and-pan behaviour unchanged.

---

## Phase 5: User Story 3 - Glove-friendly marker-mode picker (Priority: P2)

**Goal**: Enlarged, evenly spaced marker grid with state-list selection styling.

**Independent Test**: Each marker cell meets the glove-friendly minimum size; selecting then submitting drops the correct marker (incl. Custom Icon).

- [x] T008 [US3] In `app/src/main/res/layout/tw_coord_goto.xml`, confirm the marker grid renders as the enlarged glove-friendly grid (≥72dp cells, `@drawable/goto_marker_cell_bg`, enlarged `drawableTop` icons) with all existing mode ids preserved (depends on T004; same file).
- [x] T009 [US3] Update `styleMarkerModeRadio(RadioButton, boolean)` in `TwCoordGotoView.java` to call `setChecked(selected)` only and **remove** the `setBackgroundColor` call (selection colour now via the `state_checked` drawable) per `TwCoordGotoView_changes.md` §4; `applyMarkerModeUI()` mutual-exclusion loop unchanged.

**Checkpoint**: Marker grid is glove-sized; selection visuals come from the drawable; marker creation on submit unchanged.

---

## Phase 6: User Story 4 - Prominent "Use map centre" auto-fill (Priority: P2)

**Goal**: Replace three per-pane Auto Fill buttons with one prominent header button that fills the active system.

**Independent Test**: One header "Use map centre" button fills the active tab's fields and is disabled (with existing toast) when the map centre isn't representable.

- [x] T010 [US4] In `TwCoordGotoView.java`, replace the fields `autoFillTaipower`/`autoFillTwd97`/`autoFillTwd67` with a single `private Button autoFill;` and bind it from `R.id.goto_autofill` in `inflate()` (null-checked), removing the three old `findViewById(R.id.goto_autofill_*)` calls (per `TwCoordGotoView_changes.md` §2). Requires `goto_autofill` to exist in the layout (T004).
- [x] T011 [US4] In `TwCoordGotoView.java`, wire one listener `autoFill.setOnClickListener(v -> safeClick("autoFill", () -> onAutoFill(activeTab)))`; rewrite `refreshAutoFillEnabled()` to set `autoFill.setEnabled(...)` from the active tab's `latestFix.taipowerOk()/twd97Ok()/twd67Ok()`; and in `refreshLocalisedStrings()` set the single `autoFill.setText(c.getString(R.string.goto_btn_autofill))`. `onAutoFill(...)`/`autoFill*FromFix(...)`/`onMapCenterFix(...)` bodies unchanged; disabled feedback stays the existing toast.

**Checkpoint**: Single header Auto Fill works across all three tabs; not-representable still toasts.

---

## Phase 7: User Story 5 - Understandable projection-zone choice with precision warning (Priority: P3)

**Goal**: 121/119 as a labelled segmented control with the immediate 119 precision advisory.

**Independent Test**: On TWD97/67, 121/119 read as a segmented control; selecting 119 shows the precision advisory; resolved coordinates unchanged.

- [x] T012 [US5] In `app/src/main/res/layout/tw_coord_goto.xml`, confirm `goto_zone_twd97`/`goto_zone_twd67` render 121/119 as a labelled segmented control (`@drawable/goto_zone_cell_bg`, state_checked colouring) and that `goto_advisory_twd97`/`goto_advisory_twd67` use `@drawable/goto_advisory_bg`; verify the existing 119-selection advisory still triggers (no Java/logic change) (depends on T004; same file).

**Checkpoint**: Zone choice is legible; 119 advisory shows; conversion unchanged.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Safety audit, docs, and the verification gate across all stories.

- [x] T013 [P] Constitution VI crash-isolation audit in `TwCoordGotoView.java`: the merged `goto_autofill` onClick is wrapped via `safeClick` (catches `Throwable`); no `android.R.attr.*` is passed to `setBackgroundResource`/`setImageResource`; every `findViewById` result used in this change is null-checked; the `submitInFlight` re-entrancy guard is intact.
- [x] T014 [P] Update `CHANGELOG.md` and the GoTo guide under `docs/` (add a docs/ui note per Constitution III) describing the redesign and the six resolved pain points; add a refreshed GoTo screenshot.
- [x] T015 Run the verification gate: `./gradlew spotlessCheck lint testCivDebugUnitTest assembleCivDebug` — zero new warnings (Constitution I) and the existing GoTo unit tests (`CoordinateParserRoundTripTest`, `TaipowerParserTest`, `TwdTm2ParserTest`, `MapCenterFixTest`, `MarkerModeTest`) pass **unmodified** (Constitution II).
- [~] T016 Install on device (`./gradlew installCivDebug`) and run the on-device acceptance steps in `specs/010-goto-ui-redesign/quickstart.md` covering US1–US5 and the in-app language toggle (SC-006).
- [x] T017 SC-005 behaviour preservation: for a fixed input set across all three systems and both projection zones, confirm the resolved coordinates are identical before vs after the redesign.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: T002, T003 are independent of each other ([P]); both block T004.
- **US1 (Phase 3)**: T004 depends on T002+T003. T005 depends on T002 (drawable) and the rewritten layout (T004).
- **US2–US5 (Phases 4–7)**: their layout-confirm tasks (T006, T008, T012) edit the same file as T004, so they run after T004 (not parallel with it or each other). Their Java tasks (T007, T009, T010, T011) depend on T004 having created the referenced ids/drawables but are otherwise independent of US2/US3/US5 Java.
- **Polish (Phase 8)**: T013/T014 after the relevant code/doc changes; T015→T016→T017 run last in order.

### User Story Dependencies

- US1 (P1) is the shared visual base (single layout file) — US2–US5 build on it.
- Given that base, US2, US3, US4, US5 are independently testable and can be completed in any order.

### Within Each User Story

- Layout (T004) before per-section confirmation/Java wiring.
- Java field/bind (T010) before listener/refresh wiring (T011).

### Parallel Opportunities

- **Phase 2**: T002 (drawables) ‖ T003 (strings) — different files.
- **Cross-story Java** (after T004): T009 (styleMarkerModeRadio) ‖ T010/T011 (autofill merge) touch the same file `TwCoordGotoView.java`, so coordinate edits to avoid conflicts — treat as sequential within that file.
- **Phase 8**: T013 (audit) ‖ T014 (docs) — different files.

---

## Parallel Example: Phase 2 (Foundational)

```text
# These two touch disjoint resource trees and can run together:
Task: "T002 Add 9 goto_* drawables in app/src/main/res/drawable/"
Task: "T003 Apply string edits in values/, values-zh-rTW/, values-ja/ strings.xml"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 Setup → Phase 2 Foundational (drawables + strings).
2. Phase 3 US1: rewrite the layout + styleTab.
3. **STOP and VALIDATE**: the GoTo page renders in the new compact stacked style
   with working segmented tabs and unchanged coordinate entry — a shippable
   visual-parity increment.

### Incremental Delivery

1. Foundation (T002–T003) → US1 (T004–T005) = MVP visual parity.
2. US2 (submit hierarchy) → US3 (marker grid) → US4 (single Auto Fill) →
   US5 (zone segmented), each independently verifiable on device.
3. Polish: VI audit, docs, verification gate, on-device acceptance, SC-005.

### Notes

- [P] = different files, no dependency. The single shared layout file and single
  view class make most story tasks sequential by nature; verification tasks keep
  each story independently *testable*.
- No new automated tests (pure refactor); existing unit suite must stay green
  unmodified, and SC-005 guards behaviour preservation.
- Commit after each task or logical group; keep the working tree clean for the
  TPP source-zip flow later.
