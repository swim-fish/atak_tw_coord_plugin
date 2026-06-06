# Feature Specification: GoTo Coordinate-Input Page UI Redesign

**Feature Branch**: `010-goto-ui-redesign`

**Created**: 2026-06-06

**Status**: Draft

**Input**: User description: "參考 docs\design\search_settings 更新 GoTo UI 設計"

## Overview

The plugin's three operator-facing Tools-menu pages are **TW Addr Search**
(forward search), **TW Offline Addr** (reverse / storage), and **GoTo**
(coordinate input → pan the map). The first two were redesigned in feature 008
into a single-column, glove-friendly "compact stacked" look (segmented scope
controls, carded fields, on-demand dialogs, primary/secondary button hierarchy).

The **GoTo** page was left on its older layout and now looks and behaves
inconsistently with its two siblings. This feature applies the same compact
stacked visual language to the GoTo page, resolving six long-standing usability
pain points, **without changing any coordinate behaviour** — coordinate parsing,
datum/projection conversion, the submit-and-pan action, the ATAK icon-palette
hand-off, the Recent list, and input validation all stay exactly as they are.
The reference design lives in `docs/design/search_settings/`
(`tw_coord_goto.xml`, `TwCoordGotoView_changes.md`, `strings_additions_goto.xml`,
and the `goto_*` drawables).

## Clarifications

### Session 2026-06-06

- Q: Should the optional inline "why is Use map centre disabled" hint replace the
  current toast? → A: No — keep the existing toast for this release; the inline
  hint is out of scope (matches design doc §2 default).
- Q: Should the Custom Icon marker mode and the full set of existing marker types
  be preserved? → A: Yes — the redesign only resizes/restyles the marker grid;
  the available marker modes and their behaviour are unchanged.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consistent, scannable single-screen layout (Priority: P1)

An operator who already uses TW Addr Search and TW Offline Addr opens the GoTo
page and finds the same visual language: a tighter single column, segmented
coordinate-system tabs, and carded input fields, so the whole page reads at a
glance instead of scrolling through a long, loosely grouped form.

**Why this priority**: This is the core of the request — bringing GoTo into
visual and interaction parity with the redesigned sibling pages. It delivers the
"updated UI" value on its own even if nothing else changed.

**Independent Test**: Open the GoTo page on a device, switch between the three
coordinate systems, and confirm the page presents as a compact single column
with segmented tabs and carded fields matching the other two pages — verifiable
purely by inspection and tab-switching, with no coordinate entry needed.

**Acceptance Scenarios**:

1. **Given** the GoTo page is open, **When** the operator views it, **Then** the
   coordinate-system selector appears as a segmented control (pill-style selected
   state) rather than three plain stacked tabs, and only the active system's
   input fields are shown.
2. **Given** the GoTo page is open, **When** the operator switches between
   Taipower / TWD97 / TWD67, **Then** the field layout, spacing, and styling stay
   consistent across all three (a unified "tab → field" rhythm).
3. **Given** the page is open on a phone-sized panel, **When** the operator
   scrolls, **Then** all controls remain reachable and the page is meaningfully
   shorter than the previous layout for the same content.

---

### User Story 2 - Clear primary action, no submit-button confusion (Priority: P1)

An operator wants to enter a coordinate and go there. The page makes the primary
"submit and pan" action unmistakable and visually distinct from the secondary
"switch to the ATAK icon palette" action, so the operator never hesitates over
which of two similar-looking buttons to press.

**Why this priority**: The two-equal-buttons confusion is a frequent,
task-blocking friction point; resolving it directly improves the page's core job.

**Independent Test**: With a valid coordinate entered, confirm there is one
emphasised primary button ("Submit & go") and one visually subordinate
(secondary/ghost) button ("Use ATAK icon palette"), and that pressing the
primary pans the map — testable with a single coordinate entry.

**Acceptance Scenarios**:

1. **Given** a valid coordinate is entered, **When** the operator looks at the
   action area, **Then** the primary submit button is larger and colour-filled
   while the ATAK-palette button is clearly secondary (ghost/outline) styling.
2. **Given** the coordinate field is empty or invalid, **When** the operator
   views the action area, **Then** the primary submit button appears disabled
   (and, optionally, its label dims) and submitting is prevented — unchanged from
   today's validation behaviour.
3. **Given** a valid coordinate is entered, **When** the operator presses the
   primary button, **Then** the map pans to the resolved location exactly as in
   the current version (X/Y only, operator zoom preserved).

---

### User Story 3 - Glove-friendly marker-mode picker (Priority: P2)

An operator wearing gloves selects which marker (if any) to drop at the resolved
coordinate. The marker-mode cells are large enough to tap reliably with a gloved
finger, laid out as an even grid with legible icons.

**Why this priority**: The marker grid is used on most submits; small targets
cause mis-taps in the field, but the page is still usable without this change, so
it ranks below the P1 layout/action fixes.

**Independent Test**: Open the page, confirm each marker-mode cell meets a
glove-friendly minimum size and the grid is evenly arranged, and that selecting a
mode still drops the correct marker on submit — testable by tapping each cell.

**Acceptance Scenarios**:

1. **Given** the page is open, **When** the operator views the marker-mode
   section, **Then** the cells are presented as an enlarged, evenly spaced grid
   with each target meeting the glove-friendly minimum touch size.
2. **Given** a marker mode is selected, **When** the operator taps a different
   mode, **Then** selection moves to the new cell (mutually exclusive) with a
   clear selected appearance.
3. **Given** any marker mode (including "Move only" and "Custom Icon"), **When**
   the operator submits a valid coordinate, **Then** the resulting marker (or
   move-only behaviour) is identical to the current version.

---

### User Story 4 - Prominent "Use map centre" auto-fill (Priority: P2)

An operator wants to seed the input from the current map centre. A single
prominent "Use map centre" action sits in the page header and fills whichever
coordinate system is active, instead of a small button hidden inside each pane.

**Why this priority**: Auto-fill is a convenience accelerator; making it
prominent and unified reduces hunting, but the page works without it.

**Independent Test**: Confirm one header-level "Use map centre" button exists,
that it fills the active system's fields, and that it is disabled when the map
centre cannot be represented in the active system — testable by switching tabs
and observing fill/disable.

**Acceptance Scenarios**:

1. **Given** the page is open, **When** the operator views the header, **Then**
   there is a single prominent "Use map centre" button (not three per-pane
   buttons).
2. **Given** Taipower / TWD97 / TWD67 is the active system, **When** the operator
   presses "Use map centre", **Then** the active system's fields are populated
   from the current map centre using the existing conversion.
3. **Given** the map centre cannot be represented in the active system (e.g.
   outside Taiwan, or an outer island for Taipower), **When** the operator views
   or presses the button, **Then** the action is unavailable and the existing
   not-representable feedback is shown (current toast behaviour retained).

---

### User Story 5 - Understandable projection-zone choice with precision warning (Priority: P3)

For TWD97 / TWD67, an operator chooses the projection zone (121 / 119). The
choice is presented as a labelled segmented control, and selecting the 119 zone
surfaces an immediate precision advisory so the operator understands the
trade-off.

**Why this priority**: This affects only the two TWD systems and a minority of
inputs (outer-island / 119 zone); valuable for correctness clarity but the
narrowest audience.

**Independent Test**: On the TWD97 or TWD67 tab, confirm 121/119 appear as a
labelled segmented control and that picking 119 shows the precision advisory —
testable without submitting.

**Acceptance Scenarios**:

1. **Given** the TWD97 or TWD67 system is active, **When** the operator views the
   zone selector, **Then** 121 and 119 appear as a segmented control with their
   meaning legible (not an ambiguous pair of plain radios).
2. **Given** the operator selects the 119 zone, **When** the selection is made,
   **Then** an immediate advisory about reduced precision is shown.
3. **Given** any zone selection, **When** the operator submits, **Then** the
   resolved coordinate is computed identically to the current version.

---

### Edge Cases

- **Empty / invalid input**: submit and (where applicable) auto-fill stay
  disabled; existing inline error messages render in the new carded styling.
- **Switching systems mid-entry**: the active pane's fields, zone selector, and
  the header auto-fill enabled-state update to the newly selected system, as
  today.
- **Map centre not representable in the active system**: auto-fill is
  unavailable and the existing feedback (toast) is shown; no inline hint added in
  this release.
- **Recent list**: the Recent entries section continues to function and is
  restyled to match; its data and behaviour are unchanged.
- **In-app UI-language override**: all GoTo strings (including the three changed
  labels and the new Taipower help text) follow the in-app language override, in
  zh-TW / English / Japanese, consistent with the sibling pages.
- **Glove + small panel**: every interactive target meets the glove-friendly
  minimum touch size even when the ATAK side panel is narrow.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The GoTo page MUST adopt the compact single-column "stacked" layout
  consistent with the redesigned TW Addr Search and TW Offline Addr pages.
- **FR-002**: The coordinate-system selector (Taipower / TWD97 / TWD67) MUST be
  presented as a segmented control with a clearly distinguished selected state,
  showing only the active system's input fields.
- **FR-003**: Coordinate-system switching MUST present a unified "tab → field"
  layout so all three systems share consistent field styling and spacing.
- **FR-004**: The action area MUST present one emphasised primary "Submit & go"
  button and one visually subordinate (secondary/ghost) "Use ATAK icon palette"
  button.
- **FR-005**: The primary submit button MUST reflect enabled/disabled state based
  on the existing coordinate-validity check, and submitting MUST remain blocked
  for empty/invalid input.
- **FR-006**: The marker-mode picker MUST be presented as an enlarged, evenly
  spaced grid in which every selectable cell meets a glove-friendly minimum touch
  size, with legible icons.
- **FR-007**: Marker-mode selection MUST remain mutually exclusive with a clear
  selected appearance, and the full set of existing marker modes (including "Move
  only" and "Custom Icon") MUST be preserved.
- **FR-008**: A single prominent "Use map centre" auto-fill control MUST be
  provided at the page header level, replacing the three per-pane auto-fill
  buttons, and MUST fill the currently active coordinate system.
- **FR-009**: The "Use map centre" control MUST be unavailable when the map
  centre cannot be represented in the active system, surfacing the existing
  not-representable feedback (toast retained for this release).
- **FR-010**: For TWD97 / TWD67, the projection zone (121 / 119) MUST be
  presented as a labelled segmented control conveying each zone's meaning.
- **FR-011**: Selecting the 119 zone MUST surface an immediate precision advisory.
- **FR-012**: Coordinate parsing, datum/projection conversion, submit-and-pan
  behaviour, the ATAK icon-palette hand-off, input validation, and the Recent
  list behaviour MUST be unchanged by this feature (visual restyle only).
- **FR-013**: The three updated button/header labels and the new Taipower help
  text MUST be available in zh-TW, English, and Japanese and MUST follow the
  in-app UI-language override.
- **FR-014**: The redesigned page MUST remain functional within the ATAK
  Tools-menu drop-down panel at its typical widths, including narrow panels.

### Key Entities

*(No new data entities. This feature changes presentation only; existing
coordinate inputs, marker-mode selection, projection-zone selection, and Recent
entries are reused unchanged.)*

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the three GoTo coordinate systems present input fields
  using the same compact carded styling and segmented tab control.
- **SC-002**: The GoTo page resolves the six identified pain points (page length,
  duplicate-button confusion, small marker cells, hidden auto-fill, unclear
  projection-zone choice, inconsistent system switching) — all six verifiably
  addressed on device.
- **SC-003**: Every interactive target on the page meets the glove-friendly
  minimum touch size at the panel's typical and narrow widths.
- **SC-004**: A returning operator can identify the primary submit action without
  hesitation — in informal testing, users correctly pick "Submit & go" over the
  ATAK-palette button on the first attempt.
- **SC-005**: Coordinate output for a fixed set of representative inputs (across
  all three systems and both projection zones) is identical before and after the
  redesign, confirming no behavioural change.
- **SC-006**: All GoTo page strings render correctly in zh-TW, English, and
  Japanese under the in-app language override, with no missing or untranslated
  labels.

## Assumptions

- The redesign mirrors the feature 008 "compact stacked" visual language already
  shipped for the two search pages; the reference artifacts in
  `docs/design/search_settings/` (`tw_coord_goto.xml`,
  `TwCoordGotoView_changes.md`, `strings_additions_goto.xml`, `goto_*` drawables)
  are the authoritative design source.
- This is a presentation-layer change only: no coordinate math, parser,
  controller, validation, submit, ATAK-picker, or Recent logic is modified.
- The optional inline "auto-fill disabled reason" hint is out of scope; the
  existing toast feedback is retained for this release.
- The set of marker modes and the projection zones (121 / 119) offered today are
  unchanged; only their sizing and styling change.
- "Glove-friendly minimum touch size" follows the same ≥48dp (and enlarged
  marker-cell) convention already used by the redesigned sibling pages.
- The feature targets the same ATAK / device baseline already supported by the
  shipped plugin; no new platform or permission requirements are introduced.
