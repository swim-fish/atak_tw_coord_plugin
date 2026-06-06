# Implementation Plan: GoTo Coordinate-Input Page UI Redesign

**Branch**: `010-goto-ui-redesign` | **Date**: 2026-06-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/010-goto-ui-redesign/spec.md`

## Summary

Apply feature 008's "compact stacked" visual language to the third Tools-menu
page — the **GoTo** coordinate-input page (`TwCoordGotoView` +
`tw_coord_goto.xml`) — resolving six usability pain points (page length,
duplicate-button confusion, small marker cells, hidden auto-fill, unclear
projection-zone choice, inconsistent system switching) **without changing any
coordinate behaviour**. The change is overwhelmingly layout + drawable work; the
only structural Java change is merging the three per-pane Auto Fill buttons
(`goto_autofill_taipower/_twd97/_twd67`) into a single header button
(`goto_autofill`) that dispatches on `activeTab`. Coordinate parsing, datum /
projection conversion, submit-and-pan, the ATAK icon-palette hand-off, input
validation, and the Recent list are untouched. The authoritative design source
is `docs/design/search_settings/` (`tw_coord_goto.xml`,
`TwCoordGotoView_changes.md`, `strings_additions_goto.xml`, `goto_*` drawables).

## Technical Context

**Language/Version**: Java 8 (Android plugin sources), Android resource XML;
built against ATAK-CIV 5.7.0.3 SDK (`takdev` plugin), targeting ATAK 5.4+.

**Primary Dependencies**: ATAK-CIV SDK (`main.jar`); Android framework (`View`,
`RadioGroup`, `GridLayout`, state-list drawables). No new third-party runtime
dependencies.

**Storage**: N/A for this feature (no persistence changes; `PreferenceStore`
marker-mode persistence and `RecentEntryStore` are reused unchanged).

**Testing**: JVM unit tests under `app/src/test/...` (JUnit) — existing
`CoordinateParserRoundTripTest`, `TaipowerParserTest`, `TwdTm2ParserTest`,
`MapCenterFixTest`, `MarkerModeTest`. No instrumented/Espresso tests reference
the affected view ids, so none need editing. `./gradlew spotlessCheck lint
testCivDebugUnitTest assembleCivDebug` is the verification gate.

**Target Platform**: ATAK-CIV plugin drop-down panel on Android tablets/phones
(reference device Galaxy Tab S10+ / SM-X826B), glove operation.

**Project Type**: Single-module Android ATAK plugin (`app/`). UI-layer change.

**Performance Goals**: Maintain 60 fps; the page is a static `ScrollView` form,
no per-frame work. No measurable change vs current page.

**Constraints**: Offline-only (manifest omits `INTERNET`); glove-friendly ≥48dp
targets (marker cells enlarged per design); must render within narrow side-panel
widths; **Host-Process Isolation (Constitution VI)** — every host→plugin entry
point wrapped.

**Scale/Scope**: One page; ~1 layout file rewrite, ~9 new drawables, 3 string
edits + 1 new string (×3 locales), and 4 localized Java edit sites
(Auto Fill 3→1 merge, `styleTab`, `styleMarkerModeRadio`, optional submit text
colour). No new screens, entities, or contracts.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Code Quality & Formatting | spotless/clang-formatted; `lint` + analysis zero new warnings; no dead code (removed `autoFill*` fields + unused `markSelected`-style leftovers) | PASS — enforced by verification gate |
| II. Test-First (TDD) | Pure refactor: **no behaviour change**, existing unit suite MUST stay green unmodified. Add a focused regression assertion only where a label/id merge could silently break (none affect unit tests). SC-005 byte-identical coordinate output is the behaviour-preservation check. | PASS — refactor exemption; suite unchanged |
| III. UX Consistency | Follows the shared "compact stacked" design system already shipped (feature 008); design recorded under `docs/design/search_settings/`; UI doc update required under `docs/` (GoTo guide / `docs/ui/`) per Principle III. | PASS — design pre-recorded; doc task included |
| IV. Performance | Static form; 60 fps preserved; no new I/O on UI thread. | PASS |
| V. Documentation | English artifacts; CHANGELOG + GoTo guide updated in the same change set; ADR appended after `/speckit-implement`. | PASS — doc tasks included |
| VI. Host-Process Isolation (NON-NEGOTIABLE) | The merged `goto_autofill` onClick routes through existing `safeClick(tag, runnable)` (catches `Throwable`). `styleMarkerModeRadio` **removes** `setBackgroundColor` in favour of a state-list drawable — eliminating, not adding, the `android.R.attr.*` misuse class that caused the 2026-05-16 crash. New drawables are concrete resource ids (no attr-ids). Deferred/optional resource lookups (title, headers, recent) stay null-checked; the single `goto_autofill` binding follows the existing constructor convention (asserted at inflate, like the other core view bindings). Re-entrant submit guarded by existing `submitInFlight` AtomicBoolean. | PASS — change reduces VI risk surface |

**Result**: No violations. No entries required in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/010-goto-ui-redesign/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output (no new entities — documents reuse)
├── quickstart.md        # Phase 1 output (build/install/verify steps)
├── contracts/
│   └── goto-ui-contract.md   # View-id + string-id + behaviour-preservation contract
└── checklists/
    └── requirements.md  # Spec quality checklist (from /speckit-specify)
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/atakmap/android/twcoord/gotopage/
│   └── TwCoordGotoView.java        # 4 localized edits (Auto Fill 3→1, styleTab,
│                                   #   styleMarkerModeRadio, optional submit colour)
├── res/layout/
│   └── tw_coord_goto.xml           # rewritten to the compact stacked layout
├── res/drawable/                   # 9 new goto_* drawables (segmented track, tab
│   ├── goto_segment_track.xml      #   selected pill, input/zone/marker/autofill bg,
│   ├── goto_tab_selected.xml       #   advisory bg, submit primary/secondary bg)
│   ├── goto_input_bg.xml
│   ├── goto_zone_cell_bg.xml
│   ├── goto_marker_cell_bg.xml
│   ├── goto_autofill_bg.xml
│   ├── goto_advisory_bg.xml
│   ├── goto_submit_primary_bg.xml
│   └── goto_submit_secondary_bg.xml
└── res/values{,-zh-rTW,-ja}/strings.xml   # 3 label edits + 1 new goto_taipower_help

app/src/test/java/com/atakmap/android/twcoord/gotopage/   # unchanged (stay green)

docs/                                # GoTo guide + CHANGELOG update (Principle III/V)
```

**Structure Decision**: Single-module Android plugin; all changes live under the
existing `app/` tree and the feature `specs/` + `docs/` dirs. No new modules,
packages, or build targets. The design artifacts in
`docs/design/search_settings/` are copied/adapted into `app/src/main/res/`.

## Complexity Tracking

> No Constitution violations — section intentionally empty.
