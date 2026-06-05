# Implementation Plan: Search & Storage Page UI Redesign

**Branch**: `008-search-settings-ui` | **Date**: 2026-06-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-search-settings-ui/spec.md`

## Summary

A presentation-only redesign of the two operator-facing pages, with **no change
to search, ranking, import, registry, or geocoding behaviour**. The concrete
design is already worked out under `docs/design/search_settings/` (receiver
change notes, full page layouts, county-row layout, string additions, and
drawables); this plan adopts those as the authoritative design input and grounds
them against the shipped code seams and the ATAK SDK sample dialog patterns.

1. **Forward search page** (`ForwardSearchReceiver` + `forward_search_page.xml`)
   — replace the always-visible township `GridLayout` (`fs_district_list`) and
   the always-visible numeric `keypad` (`fs_keypad` / `fs_house_value`) with: a
   two-state **scope control** (`RadioGroup`: whole-county / specific-township),
   one **township button**, and one **house-number field**. The township grid
   and the numeric keypad move into on-demand `AlertDialog` pop-ups
   (`showDistrictDialog()` / `showHouseDialog()`). Default scope after a county
   is chosen is **whole-county**, so a street can be searched immediately. The
   `ForwardSearchController` API (`districts()`, `suggestedDistrict()`,
   `chooseDistrict()`, `chooseAllDistricts()`, `withHouseNumber()`) and the
   `StreetCandidateRanker.reorder(...)` ordering are unchanged.

2. **Offline address (storage) page** (`OfflineAddressReceiver` +
   `offline_address_page.xml` + `offline_address_county_row.xml`) — replace
   007's flat per-county size list with: a **total usage** figure, a single
   **stacked usage bar** (one weighted segment per county + one for the shared
   boundary layer) with a colour **legend**, **compact county rows** whose
   per-row **replace/remove** actions move into an overflow (⋮) `PopupMenu`, an
   **import-in-progress card** with a `ProgressBar` (determinate during
   COPYING / BUILDING_INDEX, indeterminate otherwise), and a dismissible
   **failure banner** (retry = re-open picker / dismiss). The importer,
   `ActiveDatasetRegistry`, `FileSystem.sizeOfDirectory/activeCountyDir/
   boundaryDir/boundaryDbFile`, and the existing `confirmReplaceCounty` /
   `confirmRemoveCounty` confirm-then-act flows are reused unchanged.

3. **Dialog/menu reliability** — every new `AlertDialog` / `PopupMenu` is built
   with the **host ATAK Activity context** (`getMapView().getContext()`) while
   views are inflated and strings resolved against **`pluginContext`**, matching
   the shipped code (`OfflineAddressReceiver` L722/737/796/815) and the ATAK SDK
   samples (`helloworld` `new AlertDialog.Builder(mapView.getContext())`;
   `meshtastic_atak` `MapView.getMapView().getContext()` idiom). This is the
   user's explicit "follow the SDK samples or you debug for a long time"
   constraint, captured as FR-017 and contract `dialog-context.md`.

All changes are additive view/receiver edits behind existing seams, shipped
together under one version bump (`1.2.1` → `1.3.0`, MINOR — operator-visible UX
redesign, no API change). No data-schema or generator change.

## Technical Context

**Language/Version**: Java 17 (Android, ATAK-CIV plugin; `app/build.gradle`
`sourceCompatibility VERSION_17`)
**Primary Dependencies**: ATAK-CIV SDK (`MapView`, `DropDownReceiver`,
`AbstractPlugin`), Android framework UI (`AlertDialog`, `PopupMenu`,
`RadioGroup`/`RadioButton`, `GridLayout`, `ScrollView`, `ProgressBar`,
`SpannableString`/`ForegroundColorSpan`, `GradientDrawable`); **no new
third-party libraries**
**Storage**: read-only `FileSystem.sizeOfDirectory(...)` over existing per-county
active dirs and the `_boundary` folder — already used by 007; no new sizing
primitive, no DB opens added
**Testing**: JUnit4 + Robolectric (unit — unchanged controller/importer/ranker
logic stays green); Espresso (instrumented — existing offline-address suite,
updated to drive the new overflow menu / scope control instead of removed ids)
**Target Platform**: Android (ATAK-CIV 5.7.0.3 host SDK), API 21+
**Project Type**: single Android plugin module under `app/`
**Performance Goals**: dialogs open within one frame; usage-bar render is
O(counties) `sizeOfDirectory` calls (a handful, already done by 007); no UI-jank
regression (Constitution IV, 60 fps)
**Constraints**: fully offline; glove-operable single-column ≥48dp targets;
zh-TW + en + ja strings (Constitution V); every host→plugin entry point wrapped
in `try/catch(Throwable)→Log.w` (Constitution VI); presentation-only — identical
search/import outcomes (FR-016)
**Scale/Scope**: 2 receivers edited; 3 layouts rewritten (per design docs); 1
county-row layout extended; 5 new drawables; ~14 new strings × 3 locales; ~6 new
methods per receiver (dialog builders + scope helpers + usage-bar/legend +
overflow menu + progress/error card toggles). No new DropDownReceiver, no new
Tools page, no controller/importer change.

## Constitution Check

*GATE: must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
|---|---|---|
| **I. Code Quality & Formatting** | Edits are small single-responsibility methods (`showDistrictDialog`, `showHouseDialog`, `applyAll`/`applySpecific`, `renderUsageBar`/`addBarSegment`/`addLegend`, `showCountyMenu`); removed dead members (`districtList`, `keypad`, `houseValue`, `buildKeypad()`). No commented-out blocks. | ✅ PASS |
| **II. Test-Driven Development** | Controller/importer/ranker behaviour is unchanged, so their unit suites must stay green unmodified (a regression guard — FR-016/FR-019). New *pure* logic is minimal (colour-index assignment, weighted-segment sizing, determinate-stage predicate) and is unit-tested; view wiring covered by updated Espresso (open district dialog→pick; open house keypad→type; overflow→remove confirm; usage total = Σ sizes). | ✅ PASS |
| **III. UX Consistency** | Reuses the page's existing single-column glove UI, `fs_grid_cell_bg`, tap-only ①②, and the established cross-context dialog rule; new patterns (scope `RadioGroup`, overflow `PopupMenu`, usage bar) are recorded under `docs/ui/` per Principle III. Empty/edge states defined (many townships → scrollable; unresolved coord → whole-county; zero-byte folder; many counties → legend wraps). | ✅ PASS |
| **IV. Performance** | No new DB opens; size reads are the same O(counties) calls 007 already makes; dialogs build lazily on tap. No UI-thread blocking added. | ✅ PASS |
| **V. Localization (zh-TW primary)** | All new strings (`fs_scope_all/specific`, `fs_district_whole_county`, `fs_district_choose_title`, `fs_house_dialog_*`, `fs_clear/done/cancel`, `offline_address_importing_label`, `_error_title`, `_action_retry/dismiss`) added to `values-zh-rTW` + `values` (en) + `values-ja`; committed code/comments English. | ✅ PASS |
| **VI. Host-Process Isolation (NON-NEGOTIABLE)** | Every new/changed host→plugin entry point is wrapped: all dialog/menu `OnClickListener`s and `setOnMenuItemClickListener` go through the existing `safeRun(...)`; `RadioGroup.OnCheckedChangeListener` (`onScopeChanged`) via `safeRun`; `postProgress`/`renderUsageBar`/`bindStateBMultiCounty` keep their `try/catch(Throwable)→Log.w`; resource lookups null-checked (design uses `if (view != null)` guards); `AlertDialog.Builder` uses the Activity context (no `BadTokenException`); `setBackgroundResource` uses concrete drawables (no `android.R.attr.*`). A Polish-phase crash-isolation audit task confirms it. | ✅ PASS |

**Result**: PASS — no violations; Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```text
specs/008-search-settings-ui/
├── plan.md              # This file
├── research.md          # Phase 0 — SDK-anchored decisions (R1–R6)
├── data-model.md        # Phase 1 — view-model entities & state transitions
├── quickstart.md        # Phase 1 — on-device manual verification
├── contracts/
│   ├── forward-search-page.md   # scope control + district/house dialogs contract
│   ├── storage-page.md          # usage bar/legend + overflow + progress/error contract
│   └── dialog-context.md        # cross-context dialog/menu reliability contract (FR-017)
└── checklists/
    └── requirements.md          # spec quality checklist (from /speckit-specify)
```

### Source Code (repository root)

```text
app/src/main/java/com/atakmap/android/twcoord/address/
├── ForwardSearchReceiver.java          # remove districtList/keypad/houseValue/buildKeypad;
│                                        # add scopeRow/scopeGroup/scopeAll/scopeSpecific/
│                                        # btnDistrict/houseField + chosenDistrict; new
│                                        # showDistrictDialog()/showHouseDialog()/applyAll()/
│                                        # applySpecific()/onScopeChanged()/reflectHouseField()
└── OfflineAddressReceiver.java          # usageTotal/usageBar/usageLegend fields + bind;
                                         # renderUsageBar()/addBarSegment()/addLegend();
                                         # per-row colour swatch + overflow → showCountyMenu();
                                         # progressCard/progressBar + showProgress/postProgress;
                                         # errorCard/errorRetry/errorDismiss + showError/hideError

app/src/main/res/
├── layout/forward_search_page.xml       # replace grid+keypad rows with scope row +
│                                         # district button + house field (per design)
├── layout/offline_address_page.xml       # add usage card (total+bar+legend), progress card,
│                                         # error banner (per design)
├── layout/offline_address_county_row.xml  # compact row: name, date·rows, size, colour swatch,
│                                           # overflow ⋮, divider (per design)
├── drawable/oa_usage_card_bg.xml          # NEW (from docs/design/search_settings/drawable/)
├── drawable/oa_usage_track_bg.xml         # NEW
├── drawable/oa_boundary_block_bg.xml      # NEW
├── drawable/oa_progress_card_bg.xml       # NEW
├── drawable/oa_error_card_bg.xml          # NEW
├── values/strings.xml                     # + en base strings
├── values-zh-rTW/strings.xml              # + zh-TW strings (primary)
└── values-ja/strings.xml                  # + ja strings

app/build.gradle                           # PLUGIN_VERSION 1.2.1 → 1.3.0

app/src/test/java/.../                      # new pure-logic unit tests (colour index,
│                                           # segment weight, determinate-stage predicate)
app/src/androidTest/java/.../               # Espresso updated: district dialog, house keypad,
                                            # overflow menu, usage total
```

**Structure decision**: single Android plugin module (unchanged from 004–007).
No new packages or seams — only two receivers, three layouts, one row layout,
five drawables, and string/version edits. The design docs under
`docs/design/search_settings/` are the line-level reference; this plan maps them
to the verified shipped symbols and records the SDK anchoring.

## Phase 0 — Research

See [research.md](research.md). Decisions resolved there (each code-anchored per
the project's plan-phase discipline — `javap -public` of
`ATAK-CIV-5.7.0.3-SDK/main.jar` **and** ATAK SDK sample / upstream cross-check):

- **R1** — Cross-context dialog/menu construction: `AlertDialog.Builder` /
  `PopupMenu` take the host Activity context (`getMapView().getContext()`);
  views/strings use `pluginContext`. Anchored to shipped
  `OfflineAddressReceiver` + `helloworld`/`meshtastic_atak` samples.
- **R2** — Scope control model: `RadioGroup` two-state (all / specific) replacing
  the inline grid; default whole-county after county chosen; re-entrancy handled
  by detach/attach of the `OnCheckedChangeListener` when setting `check()`
  programmatically.
- **R3** — Township chooser dialog: `GridLayout`(3-col) in a height-bounded
  `ScrollView`, glove cells via existing `gridCell(...)`, suggested district
  marked; "whole county" cell reverts scope.
- **R4** — House-number keypad dialog: `GridLayout` of digit + 巷/弄/號/之/⌫
  buttons reusing `onKeypad(...)`; live re-query via existing `withHouseNumber`;
  clear/done semantics.
- **R5** — Usage bar/legend: weighted `LinearLayout` segments
  (`layout_weight = bytes`), shared `OA_PALETTE` colour index aligned to
  `snap.values()` iteration order, boundary folder as a grey segment folded into
  the total; legend degrades (wrap/scroll) for many counties.
- **R6** — Overflow menu + progress/error cards: per-row `PopupMenu` (remove
  styled destructive via `SpannableString`+`ForegroundColorSpan`) delegating to
  the existing confirm flows; `ProgressBar` determinate only for COPYING /
  BUILDING_INDEX stages; failure banner leaves installed data untouched.

## Phase 1 — Design & Contracts

- [data-model.md](data-model.md) — view-model entities (`ScopeSelection`,
  `TownshipChoice`, `HouseNumberEntry`, `CountyStorageEntry`, `StorageSummary`,
  `ImportStatus`) with fields, defaults, and state transitions; no persisted
  schema change.
- [contracts/forward-search-page.md](contracts/forward-search-page.md),
  [contracts/storage-page.md](contracts/storage-page.md),
  [contracts/dialog-context.md](contracts/dialog-context.md) — each FR mapped to
  an observable, test-backed behaviour.
- [quickstart.md](quickstart.md) — on-device manual verification for all five
  user stories.
- Agent context (`CLAUDE.md` SPECKIT block) updated to point at this plan.

## Complexity Tracking

No Constitution violations — table intentionally omitted.
