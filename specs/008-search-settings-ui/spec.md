# Feature Specification: Search & Storage Page UI Redesign

**Feature Branch**: `008-search-settings-ui`

**Created**: 2026-06-05

**Status**: Draft

**Input**: User description: "依照 `docs/design/search_settings` 修改 UI 設計，但是必須依照 SDK samples 範例修改，避免重複除錯；也可以參考 `<ATAK_SDK_5_7_0_3>/samples/meshtastic_atak`。"

## Overview

A presentation-only redesign of the two main operator-facing pages in the
plugin's Tools panel, with no change to underlying search, import, registry,
or geocoding behaviour:

1. **Forward address search page** — collapse the always-visible
   township grid and the always-visible house-number keypad into compact,
   on-demand pop-up choosers. The page itself shows only a two-way scope
   control (whole-county vs. a specific township), one township button, and
   one house-number field. Operators who do not know the township can search
   immediately; operators who do can narrow with two taps.

2. **Offline address (storage) page** — replace the flat per-county list
   with a storage-usage summary (a total figure, a single stacked bar showing
   each county's share plus the shared boundary layer, and a colour legend),
   compact per-county rows whose row actions (replace / remove) move into a
   per-row overflow menu, an import-in-progress card with a progress bar, and
   a dismissible failure banner with a retry action.

Both redesigns must reuse the host-application UI patterns demonstrated in the
ATAK SDK samples (e.g. `samples/meshtastic_atak`) — specifically the proven
way to raise dialogs and pop-up menus from a plugin so they appear reliably on
device rather than failing silently. Visual structure changes; the data shown
and the operations available are the same as today.

## Clarifications

### Session 2026-06-05

- Q: Should this feature change any search, ranking, import, or geocoding
  behaviour? → A: No. It is presentation-only; the controller / importer /
  registry APIs and the set of available operations are unchanged.
- Q: Should the new pop-up choosers and menus replace, or coexist with, the
  current always-visible grids/keypad? → A: Replace. The grid and keypad are
  removed from the page and re-presented on demand inside dialogs.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Search Without Knowing the Township (Priority: P1)

A field operator opens the forward address search page and has already chosen
(or auto-resolved) a county. They do not know which township the address is
in. With the redesigned page they see the scope control already set to
"whole county", so they can type a street straight away and get candidates
across the whole county, without first being forced to pick a township from a
large grid.

**Why this priority**: Removing the mandatory township step from the most
common path is the core value of the forward-search redesign; it is what makes
the page usable by operators who only know a street name.

**Independent Test**: Choose a county, confirm the scope control defaults to
whole-county and the township button is shown as not-required, type a street,
and confirm candidates appear without any township selection — independent of
the storage-page changes.

**Acceptance Scenarios**:

1. **Given** a county has just been chosen, **When** the township stage
   appears, **Then** the scope control is pre-set to whole-county, the
   township button reads as "whole county (no district)" and is inactive, and
   the operator can proceed directly to street entry.
2. **Given** the operator is in whole-county scope, **When** they enter a
   street substring, **Then** candidates are returned across the whole county
   exactly as they are today (same results, distance-anchored).
3. **Given** the operator switches the scope control to "specific township"
   without a township chosen yet, **When** the switch happens, **Then** the
   township chooser opens automatically so they can pick one.

---

### User Story 2 - Narrow to a Township On Demand (Priority: P1)

An operator who does know the township taps the township button, which opens a
pop-up chooser showing the county's townships in a large, glove-friendly grid
(with a "whole county" option and the suggested/auto-resolved township marked).
Tapping one closes the chooser, sets the scope to that township, and proceeds
to street entry. Entering a house number is done the same way: tapping the
house-number field opens a numeric keypad pop-up (digits plus 巷 / 弄 / 號 /
之 and backspace) that updates candidates live and is dismissed when done.

**Why this priority**: On-demand choosers are the other half of the redesign;
without them the operator loses the ability to narrow by township or to enter
a precise house number. Equal priority to Story 1.

**Independent Test**: Tap the township button, pick a township from the
pop-up, confirm the page reflects the choice and street results are scoped to
it; tap the house-number field, enter a number on the pop-up keypad, and
confirm candidates refine — both independent of the storage page.

**Acceptance Scenarios**:

1. **Given** the township button is tapped, **When** the chooser opens, **Then**
   it lists the county's townships plus a whole-county option, marks the
   suggested township, and is scrollable/large enough for gloved taps.
2. **Given** the chooser is open, **When** a township is tapped, **Then** the
   chooser closes, the scope control shows "specific township", the township
   button shows the chosen name, and street results are scoped to it.
3. **Given** street results are shown, **When** the house-number field is
   tapped, **Then** a numeric keypad pop-up opens; entering/removing characters
   updates the candidate list live, "clear" empties it, and "done" closes the
   pop-up while keeping the entered number reflected on the field.
4. **Given** map-follow re-resolves the county from a new map centre, **When**
   the township is auto-resolved, **Then** the page applies the resolved
   township (or whole-county when the point cannot be resolved) using the same
   scope control and button, with no leftover grid.

---

### User Story 3 - See Storage Usage at a Glance (Priority: P2)

An operator opens the offline address (storage) page with several counties
imported. Instead of a flat list, they see a total on-disk figure, a single
horizontal bar whose coloured segments show each county's relative share plus a
segment for the shared boundary layer, and a matching colour legend. Below it,
each county appears as a compact row (name, data date · row count, size) with a
colour swatch matching its bar segment.

**Why this priority**: Storage awareness helps operators manage limited device
space; it is valuable but secondary to the search flow they use on every task.

**Independent Test**: With two or more counties imported, open the page and
confirm the total equals the sum of the per-county sizes plus the boundary
layer, that bar segments and legend colours match the per-row swatches, and
that each row shows date · rows · size — independent of the forward-search
changes.

**Acceptance Scenarios**:

1. **Given** N counties plus a boundary layer are installed, **When** the page
   renders, **Then** the total figure equals the sum of all per-county folder
   sizes plus the boundary folder size, and the stacked bar has one segment per
   county sized to its share plus one boundary segment.
2. **Given** the bar and legend are shown, **When** the operator compares a
   county's bar segment, legend entry, and row swatch, **Then** all three use
   the same colour for that county.
3. **Given** the boundary layer is installed, **When** the page renders,
   **Then** the boundary layer is counted in the total and shown as its own
   bar segment and legend entry; **When** it is not installed, **Then** the
   boundary detail row indicates "not installed".

---

### User Story 4 - Manage a County From Its Row (Priority: P2)

From a compact county row, the operator taps the row's overflow (⋮) control to
open a small menu offering "replace" and "remove" (remove styled as a
destructive action). Choosing either runs the existing confirm-then-act flow
unchanged.

**Why this priority**: Per-row management must remain reachable after the rows
are made compact; moving the actions into an overflow keeps the row readable
without losing capability.

**Independent Test**: Tap a row's overflow, confirm a menu with replace and
remove appears, choose remove, and confirm the existing confirmation dialog
appears and the existing removal behaviour runs.

**Acceptance Scenarios**:

1. **Given** a compact county row, **When** its overflow control is tapped,
   **Then** a menu with "replace" and a destructively-styled "remove" appears.
2. **Given** the menu is open, **When** "replace" or "remove" is chosen,
   **Then** the existing confirmation dialog and downstream action run exactly
   as before this redesign.

---

### User Story 5 - Clear Feedback While Importing and On Failure (Priority: P3)

While a county dataset imports, the operator sees an import-in-progress card
with a progress bar that is determinate for the copying and index-building
stages and indeterminate otherwise, plus the existing progress text. If an
import fails, a dismissible failure banner appears with the reason, a
"choose file again" retry action, and a "dismiss" action; the previously
installed data is left untouched.

**Why this priority**: Better progress and failure feedback reduces confusion
during long imports, but the import itself already works; this is polish.

**Independent Test**: Start an import and confirm the progress card with a
moving bar appears; simulate a failure and confirm the banner with retry /
dismiss appears and that existing county data remains listed.

**Acceptance Scenarios**:

1. **Given** an import is running, **When** it is in the copying or
   index-building stage, **Then** the progress bar shows determinate percent
   progress; in other stages it shows indeterminate motion; progress text is
   shown throughout.
2. **Given** an import fails, **When** the failure is reported, **Then** a
   banner shows the reason with "choose file again" and "dismiss"; choosing
   retry re-opens the file picker and choosing dismiss hides the banner.
3. **Given** an import failed, **When** the banner is shown, **Then** the
   previously installed county list and sizes are unchanged.

---

### Edge Cases

- **County with many townships**: the township chooser must remain scrollable
  and height-bounded rather than overflowing the screen.
- **County cannot be resolved from a coordinate** (map-follow / re-point):
  the page falls back to whole-county scope with no township selected.
- **Empty / cleared house number**: clearing returns the candidate list to the
  whole-street result for the current scope.
- **Many counties on the storage bar**: the legend must not be clipped (it may
  wrap or scroll) and the smallest segment must remain at least minimally
  visible.
- **Zero-byte or missing folder sizes**: a county or boundary folder reporting
  zero size must not break the bar or total.
- **Single import (no cancel)**: the in-progress card offers no cancel for a
  single import; batch cancellation continues to use the existing batch flow.
- **Dialog raised with the wrong context**: a dialog or menu raised from an
  invalid UI context must not crash or silently fail to appear (the proven SDK
  sample pattern must be used).

## Requirements *(mandatory)*

### Functional Requirements

#### Forward search page

- **FR-001**: The forward search page MUST present a two-state scope control
  (whole-county and specific-township) in place of the always-visible township
  grid.
- **FR-002**: When a county is chosen, the page MUST default the scope to
  whole-county and allow the operator to proceed to street entry without
  selecting a township.
- **FR-003**: Selecting "specific township" while no township is chosen MUST
  open the township chooser automatically.
- **FR-004**: The township chooser MUST be an on-demand pop-up listing the
  county's townships plus a whole-county option, marking the suggested /
  auto-resolved township, and MUST be glove-friendly (large targets) and
  scrollable/height-bounded for counties with many townships.
- **FR-005**: Choosing a township in the chooser MUST set the scope to that
  township, reflect the name on the township button, dismiss the chooser, and
  scope street results to it; choosing the whole-county option MUST revert to
  whole-county scope.
- **FR-006**: House-number entry MUST be an on-demand numeric keypad pop-up
  (digits plus 巷 / 弄 / 號 / 之 and backspace) opened from the house-number
  field; it MUST update the candidate list live, offer "clear" and "done", and
  reflect the entered value on the field after closing.
- **FR-007**: The house-number field MUST be hidden until a street search has
  produced results.
- **FR-008**: Map-follow / re-point auto-selection MUST drive the same scope
  control and township button (resolved township → specific scope; unresolved
  → whole-county), leaving no residual grid on the page.

#### Offline address (storage) page

- **FR-009**: The storage page MUST show a total on-disk usage figure equal to
  the sum of every imported county's folder size plus the boundary layer folder
  size.
- **FR-010**: The page MUST show a single horizontal stacked bar with one
  weighted segment per county and one segment for the boundary layer, plus a
  colour legend; segment, legend, and per-row colours for a given county MUST
  match.
- **FR-011**: Each county MUST appear as a compact row showing its name, data
  date and row count, on-disk size, and a colour swatch matching its bar
  segment.
- **FR-012**: Per-county "replace" and "remove" actions MUST move into a
  per-row overflow menu, with "remove" presented as a destructive action; both
  MUST invoke the existing confirm-then-act flows unchanged.
- **FR-013**: An import-in-progress card MUST show progress text plus a
  progress bar that is determinate (percent) during copying and index-building
  stages and indeterminate otherwise.
- **FR-014**: On import failure, a dismissible banner MUST show the reason with
  a "choose file again" retry action and a "dismiss" action, and MUST leave the
  previously installed data and listing unchanged.
- **FR-015**: The boundary layer detail row MUST remain, showing the boundary
  layer details when installed and a "not installed" indication when absent.

#### Cross-cutting

- **FR-016**: This feature MUST NOT change search results, ranking, import,
  registry, or geocoding behaviour — only how the two pages present existing
  data and operations.
- **FR-017**: All new dialogs and pop-up menus MUST be raised using the
  host-application UI context pattern demonstrated in the ATAK SDK samples
  (e.g. `samples/meshtastic_atak`) so they appear reliably on device, while
  view inflation and string resources continue to resolve against the plugin's
  own localized resources.
- **FR-018**: All new operator-facing strings MUST be provided in the plugin's
  three supported locales (Traditional Chinese, English, Japanese), consistent
  with existing strings.
- **FR-019**: Existing automated tests for the unchanged controller / importer
  / registry APIs MUST continue to pass; any UI tests that targeted the removed
  grid/keypad elements MUST be updated to target the new controls.

### Key Entities

- **Scope selection**: the operator's current search breadth — whole-county or
  a single named township — surfaced by the page's scope control and township
  button.
- **Township choice**: the township currently selected (or none, meaning
  whole-county), used to scope street results.
- **House-number entry**: the operator's in-progress house number / 巷弄 tail
  used to refine candidates.
- **County storage entry**: a per-county view-model of name, data date, row
  count, on-disk size, and an assigned legend colour.
- **Storage summary**: the aggregate total and the per-segment breakdown
  (counties plus boundary layer) shown by the bar and legend.
- **Import status**: the current import state — idle, in-progress (stage +
  optional percent), or failed (reason) — driving the progress card and failure
  banner.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator who knows only a street name can obtain candidates
  after choosing a county without performing any township-selection step
  (zero extra taps before street entry on the common path).
- **SC-002**: An operator who knows the township can scope to it in at most two
  taps (open chooser, pick township) from the township stage.
- **SC-003**: On the storage page, the displayed total equals the sum of all
  per-county sizes plus the boundary size in 100% of renders (no discrepancy).
- **SC-004**: For every imported county, the colour used in the bar, the
  legend, and the row swatch is identical (100% colour consistency).
- **SC-005**: Every per-county management action available before the redesign
  (replace, remove with confirmation) remains reachable after it.
- **SC-006**: Search results, ranking, and import outcomes are byte-for-byte
  identical to the pre-redesign behaviour for the same inputs (no functional
  regression).
- **SC-007**: All new dialogs and menus appear on device on first invocation
  (0 silent-failure occurrences in device smoke testing).
- **SC-008**: All new strings render correctly in Traditional Chinese, English,
  and Japanese with no missing-resource fallbacks.

## Assumptions

- The underlying forward-search controller, address importer, active-dataset
  registry, and reverse-geocoding facade APIs are unchanged; this feature only
  re-renders their inputs/outputs.
- The detailed, concrete redesign already captured in
  `docs/design/search_settings/` (receiver change notes, page layouts, county
  row layout, string additions, and drawables) is the authoritative reference
  for the intended appearance and is treated as design input to this spec.
- The plugin already follows the cross-context dialog rule (build dialogs with
  the host Activity context; inflate views and resolve strings with the plugin
  context); the SDK sample reference reinforces this proven pattern.
- The three supported locales remain Traditional Chinese (zh-rTW), English
  (base), and Japanese (ja).
- "Glove-friendly" targets continue to mean the established large/≥48dp tap
  sizing used by the existing pages.
- The storage bar/legend is expected to handle the typical 2–4 imported
  counties cleanly; larger counts degrade gracefully (wrap/scroll) rather than
  being a primary design target.
