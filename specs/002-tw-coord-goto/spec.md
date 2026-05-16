# Feature Specification: Taiwan Coordinate Input ("GoTo") Page

**Feature Branch**: `002-tw-coord-goto`

**Created**: 2026-05-16

**Status**: Draft

**Input**: User description: "先看 ATAK Tools GoTo 的功能 目標又新增一個台灣座標輸入的頁面"
("First study the ATAK Tools > GoTo feature, then add a Taiwan-coordinate
input page that is functionally analogous to it.")

## Context

ATAK ships a built-in "GoTo" affordance (broadcast action
`com.atakmap.android.routes.GOTO_NAV_BEGIN`, served by
`com.atakmap.android.routes.GoToMapTool`) that accepts a single
coordinate string in WGS84 lat/lon, MGRS, or DMS, then pans the camera
to that point and drops a temporary marker. The string is parsed by
`GeoPoint.parseGeoPoint(String)` and never accepts Taiwan-specific
units (Taipower grid, TWD97 TM2, TWD67 TM2). Operators in Taiwan who
receive a coordinate over radio or paper in a Taiwan unit currently
have to:

1. Open an external app or web tool to convert the Taiwan coordinate to
   WGS84,
2. Copy the lat/lon back into ATAK's GoTo,
3. Repeat for every coordinate handed off.

This feature adds a **Taiwan coordinate input page** to the existing
`atak_tw_coord_plugin` so the operator can enter a Taipower / TWD97 /
TWD67 string directly, pick a unit explicitly, and have ATAK pan to
the point and drop a marker — same end behaviour as Tools > GoTo, but
sourced from the units Taiwanese operators actually receive.

## Clarifications

### Session 2026-05-16

- Q: Should the input page expose an "Auto Fill" affordance, and if so
  from which coordinate source?
  → A: Yes. **Map centre only.** A single Auto Fill button reads the
  current map centre's WGS84 coordinate and writes the equivalent
  string into the currently-selected unit tab. Self-marker / selected-
  CoT-marker / last-submitted-point are **not** supported sources in
  v1; the last-submitted value is already covered by FR-003.
- Q: After Auto Fill runs, does it auto-submit (pan + drop marker) or
  only fill the input field?
  → A: **Fill only.** Auto Fill writes the formatted string into the
  field (and the zone toggle — see below); the operator MUST still
  tap the submit affordance to pan and drop the marker. Rationale:
  the map centre is already where the operator is looking, so
  submitting there would just drop a pin at the unmoved camera; the
  realistic next action is to edit one or two digits (e.g. correct a
  digit heard over radio) before submitting.
- Q: How does Auto Fill behave when the map centre cannot be expressed
  in the currently selected tab (out of Taiwan coverage box, or
  Taipower tab with the centre on an outer island)?
  → A: The Auto Fill button MUST be **disabled in real-time** while
  the map centre is unrepresentable in the active tab. The button
  MUST surface a localised tooltip / long-press hint explaining the
  reason ("outside Taiwan coverage" / "Taipower does not cover outer
  islands"). The button must re-enable as soon as the operator pans
  back into the valid domain.
- Q: On the TWD97 / TWD67 tabs, should Auto Fill also set the zone
  121/119 toggle from the map centre's longitude?
  → A: **Yes — same longitude rule the readout widget already uses**
  (longitude < 120° → zone 119, otherwise zone 121). Auto Fill sets
  the toggle and the easting/northing values together. The operator
  may still override the zone toggle before submitting; FR-006's
  explicit manual toggle remains in force.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enter a Taipower grid code and jump there (Priority: P1)

A field operator in Taiwan receives a Taipower grid code over radio
(e.g. `H7509 DB4016`). They open the plugin's input page from the
ATAK Tools menu, choose the **Taipower** tab, paste or type the
11-character code, and submit. ATAK's map pans and zooms to the
corresponding location and drops a temporary marker so the operator
can see exactly where to go.

**Why this priority**: This is the single highest-leverage workflow. Of
the three supported units, the Taipower grid is the one ATAK has no
native answer for, and it is the unit most commonly used by Taiwan's
utility / emergency-response field teams. Shipping just this story
already replaces the external-converter workaround for the majority of
field handoffs.

**Independent Test**: With the plugin installed, open the Tools menu →
"TW Coord GoTo". Select the Taipower tab, enter `H7509 DB4016` (花蓮車站 / Hualien Station). Submit. Map MUST pan to within 5 m of Hualien Station (the golden coordinate used in the plugin's existing tests), and a marker
MUST appear at that location. Removing the marker MUST be a single
long-press action.

**Acceptance Scenarios**:

1. **Given** the plugin is installed and ATAK is running, **When** the
   user opens the Tools menu and taps the "TW Coord GoTo" affordance,
   **Then** an input page opens with a unit selector defaulted to the
   last-used unit (Taipower on first launch) and an empty input field
   focused for typing.
2. **Given** the input page is open on the Taipower tab, **When** the
   user enters a valid 9-char or 11-char Taipower code and submits,
   **Then** the map pans and zooms to the corresponding location, drops
   a marker at that location, and closes the input page.
3. **Given** the input page is open on the Taipower tab, **When** the
   user enters a syntactically invalid code (wrong length, illegal
   region letter Y/Z, illegal hundred-metre letter, non-digit in a
   digit slot), **Then** the submit action is disabled and an inline,
   localised error message describes what specifically is wrong; the
   map does not move.

---

### User Story 2 - Enter a TWD97 or TWD67 easting/northing and jump there (Priority: P1)

The same operator receives a TWD97 easting/northing pair from a survey
record, or a TWD67 pair from an older paper map. They open the input
page, switch to the **TWD97** or **TWD67** tab, type the easting and
northing in their respective fields, and submit. ATAK pans and drops a
marker just as in US1.

**Why this priority**: TWD97 (EPSG:3826) is the official civilian
coordinate of the R.O.C. survey system and ships in most engineering
records; TWD67 is its predecessor and still appears in legacy archives.
Both must be first-class inputs alongside Taipower or the page is
incomplete for half the realistic handoff scenarios.

**Independent Test**: Open the input page, switch to the TWD97 tab,
enter easting `302912` and northing `2770905`. Submit. Map MUST pan to
within 1 m of Taipei 101 (using the same pyproj-pinned golden vector
from the existing test fixture). Repeat on the TWD67 tab with
`302130 / 2771143`; result MUST be within 5 m on the main island and
within 20 m on outer islands.

**Acceptance Scenarios**:

1. **Given** the input page is on the TWD97 tab, **When** the user
   enters a valid 6/7-digit easting and a 7-digit northing, **Then** the
   map pans to the converted location, drops a marker, and the page
   closes.
2. **Given** the input page is on the TWD97 or TWD67 tab and the
   operator has set the zone toggle to **119** (Penghu / outer-island
   mode), **When** the user submits a valid easting/northing, **Then**
   the system MUST interpret the input against TM2 zone 119 and
   surface the resolved zone in a confirmation toast (`zone 119`) so
   the user can detect zone misuse.
3. **Given** the input page is on the TWD67 tab, **When** the user
   submits an outer-island coordinate, **Then** the same accuracy
   advisory string shown in the existing settings page (±10–20 m on
   outer islands) appears as a one-line note above the submit button
   *before* submit, so the operator knows the resulting pin may
   disagree with an official 7-parameter reference by that amount.

---

### User Story 3 - Edit and refine the destination marker (Priority: P2)

After landing at the destination, the operator wants to (a) reopen the
input page with the last-used unit and value pre-filled so they can
tweak one digit (e.g. correct a misheard digit over radio) and re-jump
without retyping the whole code, and (b) remove the marker when done.

**Why this priority**: Once US1/US2 work, "fix a typo" is the next
recurring need. It is not a blocker for the headline workflow (the
operator can just re-enter the full string from scratch) so it sits
below P1, but it is the smallest meaningful UX improvement that turns
a one-shot tool into a usable workspace.

**Independent Test**: After completing a Taipower GoTo (US1), reopen
the input page from the Tools menu. The page MUST present the
previously-entered Taipower code in the field, with the previously-used
tab active, and the cursor positioned for editing. Modify any single
character, submit, and confirm the marker moves (not duplicates) to
the new location.

**Acceptance Scenarios**:

1. **Given** the operator has previously submitted a Taipower / TWD97 /
   TWD67 coordinate, **When** they open the input page again,
   **Then** the previously selected unit tab is active and the
   previously submitted value is pre-filled in the appropriate field.
2. **Given** a destination marker exists from a previous submission,
   **When** the operator submits a new coordinate in any unit,
   **Then** the existing marker MUST be moved (not duplicated) to the
   new location.
3. **Given** a destination marker exists, **When** the operator
   long-presses the marker, **Then** ATAK's standard delete affordance
   removes it, and the next submission creates a fresh marker.

---

### User Story 4 - Recent entries list (Priority: P3)

The operator wants to quickly recall the last few coordinates they
have entered so they can hop between two or three points without
retyping or fishing through their radio notes.

**Why this priority**: A nice-to-have that meaningfully accelerates
the multi-stop fieldwork loop but is not in the critical path for any
single handoff. Distinct code surface from US1/US2 (a stored list +
list view) so deferring it to a P3 increment is safe.

**Independent Test**: After three successful submissions across mixed
units, open the input page; a "Recent" section MUST list the three
entries in reverse-chronological order, each tappable to refill the
input and unit, with a clear delete affordance per row.

**Acceptance Scenarios**:

1. **Given** N successful coordinate submissions, **When** the operator
   opens the input page, **Then** the most recent min(N, 10) entries are
   visible, newest-first, each labelled with its unit and original
   input string.
2. **Given** a non-empty recent list, **When** the operator taps a row,
   **Then** that unit tab activates and that input string fills the
   field; submit acts on that value.
3. **Given** a non-empty recent list, **When** the operator clears the
   list, **Then** the list is empty and no recent entries persist
   across ATAK restarts.

---

### User Story 5 - Auto Fill from current map centre (Priority: P2)

The operator has panned the ATAK map to roughly the right spot (using
ATAK's normal touch gestures) but wants the precise coordinate of the
current map centre formatted in the active Taiwan unit — to copy onto
paper, microphone-cue to a partner, or to lightly edit before
submitting. They tap an **Auto Fill** button next to the input field
and the page writes the map-centre coordinate, formatted in the
currently selected unit, straight into the input. The operator can
then edit a digit or two and submit.

**Why this priority**: Removes the typing burden when the operator is
already pointing at the destination on screen — a frequent workflow
when they read a coordinate visually from a satellite photo or hand-off
from a colleague's screen. Not in the critical path for the headline
"radio-call → marker" workflow (US1), so it sits below P1 but above
the recent-list nice-to-have (US4).

**Independent Test**: Centre the map on Taipei 101 (verified by the
existing readout widget). On the input page, switch to each of the
three tabs and tap Auto Fill. The input field MUST in each case fill
with the Taipei 101 value pinned in `taiwan_cities_coords.csv`,
matching the per-unit tolerance bands (TWD97 ≤ 0.5 m, TWD67 main ≤
5 m, Taipower 11-char). No submit happens.

**Acceptance Scenarios**:

1. **Given** the input page is open and the map centre is inside the
   active tab's valid domain, **When** the operator taps Auto Fill,
   **Then** the input field receives the map centre's coordinate
   formatted in the active unit, and (on TWD97/TWD67 tabs) the zone
   121/119 toggle is set automatically from the map centre's
   longitude using the same rule as the readout widget. No marker is
   dropped and the map does not move.
2. **Given** the input page is on the Taipower tab and the map centre
   is on an outer island, **Then** the Auto Fill button MUST be in a
   disabled / unavailable state and MUST surface a localised hint
   ("Taipower does not cover outer islands"). The button MUST
   re-enable within one map-event cycle after the operator pans back
   to the main island.
3. **Given** the input page is open and the map centre is outside the
   Taiwan coverage box (latitude 21.5–26.5°N, longitude 118.0–122.5°E),
   **Then** the Auto Fill button MUST be disabled on every tab with a
   localised hint ("outside Taiwan coverage").

---

### Edge Cases

- **Out-of-domain WGS84 result.** Inputs that syntactically parse but
  resolve to a WGS84 point outside Taiwan's defined coverage box
  (latitude 21.5–26.5°N, longitude 118.0–122.5°E used by the existing
  `CoordinateConverter`) MUST be rejected with an inline error,
  matching the existing widget's "out of range" semantics.
- **Outer-island ambiguity.** A bare 6-digit easting in the TWD97 tab
  could belong to zone 121 or zone 119. The page MUST require the
  operator to pick a zone (default 121, toggle for 119) rather than
  silently guessing, and MUST show the resolved zone in the
  confirmation toast.
- **Mixed-case / spacing in Taipower input.** Inputs `H7509DB4016`,
  `H7509 DB4016`, `h7509 db4016` MUST all parse as the same code.
- **Paste from clipboard.** Pasting a string with leading / trailing
  whitespace, embedded newlines, or surrounding parentheses (common
  shapes for copy-paste from web tools) MUST be tolerated; the page
  normalises the string before validation.
- **No GPS available.** Page MUST work without any GPS fix; it has no
  dependency on the device's own location.
- **Map locked to another locale (mil-grid / MGRS).** Submission MUST
  pan and drop the marker regardless of what other coordinate readout
  ATAK is currently showing — the underlying camera and marker APIs
  operate on WGS84 internally.
- **Plugin disabled mid-flow.** If the user disables the plugin in
  ATAK's plugin manager while the input page is open, the page MUST
  close gracefully without leaving a dangling marker or hung dialog.
- **Locale change mid-flow.** If the operator changes the plugin's UI
  language (via the existing settings page) while the input page is
  open, the input page repaints in the new language without losing the
  in-progress input string.
- **Auto Fill overwrites in-progress edit.** Tapping Auto Fill while
  the operator has already typed a partial value MUST overwrite the
  current input without a confirmation dialog (standard form-Auto-Fill
  semantics; the recent-entries list (US4) lets them recover the prior
  value if they realise the overwrite was a mistake).
- **Auto Fill source disappears.** If the map view is being torn down
  (e.g. ATAK is exiting, or the plugin is being disabled) at the
  moment Auto Fill is tapped, the input field MUST be left unchanged
  and no exception MUST propagate to the user; the disabled-state
  guard (Q3) already prevents the more common cases.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The plugin MUST register a new Tools-menu affordance,
  visually and linguistically separate from the existing unit-cycle
  Tools icon, that opens a "TW Coord GoTo" input page.
- **FR-002**: The input page MUST offer three explicit input modes,
  selectable as tabs (or an equivalent affordance): **Taipower**,
  **TWD97**, **TWD67**.
- **FR-003**: On open, the input page MUST default to the unit and
  value most recently submitted; on first-ever open, it MUST default to
  **Taipower** with an empty field.
- **FR-004**: The Taipower input mode MUST accept 9-character codes
  (10 m precision) and 11-character codes (1 m precision) per FR-011
  of feature 001, with the following normalisation: case-insensitive,
  whitespace-tolerant, single internal space optional.
- **FR-005**: The Taipower input mode MUST reject codes containing the
  reserved region letters `Y` or `Z`, with an inline error message
  explaining that only A–X (excluding Y/Z) are valid.
- **FR-006**: The TWD97 and TWD67 input modes MUST accept separate
  easting and northing values as decimal-metres (integer digits only;
  trailing decimals tolerated and ignored), with an explicit toggle for
  TM2 zone 121 (main island, default) versus zone 119 (Penghu and
  outer islands).
- **FR-007**: Validation MUST happen on every keystroke and gate the
  submit affordance: while the input is invalid the submit MUST be
  disabled and an inline localised error MUST describe the specific
  problem (length, illegal character, out-of-domain, etc.).
- **FR-008** (revised 2026-05-16 post-on-device — see ADR-0009): On
  valid submit the system MUST (a) pan the ATAK map's X/Y so the
  resolved point is centred, **preserving the operator's current zoom
  level (Z) and other camera attributes**, and (b) close the input
  page. The system MUST NOT auto-create any marker at the
  destination; marker placement is the operator's responsibility via
  ATAK's standard long-press → radial menu (zero new UI). The system
  MUST NOT delegate the submit path to
  `com.atakmap.android.routes.GoToMapTool` because that path bails
  with a "self_marker_required" toast when no GPS fix is available.
- **FR-009** (revised — superseded by FR-008): The plugin does not
  own any persistent marker, so "move-not-create" no longer applies.
  Operators who drop markers via long-press use ATAK's normal marker
  lifecycle (long-press the marker → Delete in the radial menu).
- **FR-010**: A successful submit MUST emit a localised toast naming
  the resolved unit, the resolved zone (only when zone ≠ 121), and the
  underlying WGS84 lat/lon to 6 decimals (e.g. `Taipower → 25.034°N
  121.565°E`), so the operator gets visible confirmation of the
  conversion without having to inspect the marker.
- **FR-011**: When the resolved WGS84 point falls outside Taiwan's
  defined coverage box, the submit MUST be blocked, the inline error
  MUST say "outside Taiwan coverage" in the active language, and the
  map MUST NOT move.
- **FR-012**: The page MUST surface a localised paste affordance (or
  honour the system paste gesture) and normalise the pasted string
  before validation (strip surrounding whitespace / parentheses /
  embedded newlines, collapse multiple internal spaces).
- **FR-013**: The page MUST be available in English, Traditional
  Chinese (Taiwan), and Japanese, consistent with the existing
  plugin's language model. All visible strings — labels, hints, error
  messages, the accuracy advisory, the confirmation toast — MUST come
  from the locale-resolved resource bundle.
- **FR-014**: The plugin MUST persist the last successfully submitted
  (unit, value) pair across ATAK restarts so US3 acceptance scenario 1
  works after a reboot, and MUST persist the up-to-10-entry "Recent"
  list (US4) across restarts.
- **FR-015**: The page MUST NOT initiate any outbound network
  communication; all parsing and conversion happens on-device. This
  inherits the plugin's existing zero-telemetry posture (feature 001
  FR-018 / FR-019).
- **FR-016**: The page MUST be reachable from at least two entry
  points: the new Tools-menu icon (FR-001) and a button on the
  existing settings page (`TwCoordPreferenceFragment`), so a user
  exploring the settings page can discover the GoTo feature.
- **FR-017**: When the entered TWD97 / TWD67 coordinate falls in zone
  119, an inline note MUST appear above the submit button repeating
  the outer-island accuracy advisory already used on the settings
  page (±10–20 m vs official 7-parameter), so the operator is reminded
  *before* they jump.
- **FR-018**: The page MUST keep the in-progress input string intact
  if the user navigates away and back within the same ATAK session
  (e.g. tabbed out to consult radio notes, then returned).
- **FR-019** (revised — superseded by FR-008): The plugin no longer
  owns a destination marker. Operators who place markers do so via
  ATAK's standard radial menu with whatever icon / call-sign / type
  ATAK provides (waypoint, Mission Point, SPI, etc.).
- **FR-020**: All visible numeric strings (easting/northing display,
  WGS84 confirmation) MUST follow the locale's numeric formatting
  conventions (e.g. decimal separator) but the *underlying* input MUST
  always accept ASCII digits regardless of locale, since the unit
  systems are defined in ASCII digits.
- **FR-021**: The input page MUST provide an **Auto Fill** affordance
  (button or equivalent control) that, when activated, reads the
  current map centre's WGS84 coordinate, converts it to a string in
  the currently selected unit, and writes that string into the input
  field. No submit, no marker, no map movement: Auto Fill writes only
  to the input state.
- **FR-022**: When the Auto Fill source is unrepresentable in the
  active tab — map centre outside the Taiwan coverage box (any tab),
  or map centre on an outer island while the Taipower tab is active —
  the Auto Fill affordance MUST be in a disabled state and MUST
  surface a localised explanatory hint (tooltip, long-press hint, or
  equivalent affordance permitted by the host UI framework). The
  disabled state MUST update in real time as the operator pans the
  map (within one map-event cycle), and MUST not lag the underlying
  readout widget's own out-of-range indicator.
- **FR-023**: On the TWD97 and TWD67 tabs, Auto Fill MUST set the zone
  121/119 toggle in lockstep with the easting/northing values using
  the same longitude rule as the readout widget (longitude < 120° →
  zone 119, otherwise zone 121). The operator's prior toggle state is
  overwritten by Auto Fill; the operator MAY still flip the toggle
  manually before submit (FR-006 remains in force).
- **FR-024**: Auto Fill MUST overwrite an in-progress operator input
  without a confirmation dialog. Operators who Auto Fill by mistake
  recover via the recent-entries list (US4) or by retyping.

### Key Entities *(include if feature involves data)*

- **CoordinateInput**: The structured input being edited on the page.
  - `unit`: one of {Taipower, TWD97, TWD67}.
  - `rawValue`: the operator's literal input string (for Taipower) or
    `{easting, northing, zone}` triple (for TWD97/TWD67).
  - `validationState`: VALID | INVALID(reason) | OUT_OF_RANGE.
  - Relationships: one CoordinateInput resolves to at most one
    `Wgs84` (see feature 001 data model) on submit; failed inputs
    resolve to no `Wgs84` at all.
- **DestinationMarker**: The marker dropped at the resolved point.
  - Identity persists across submissions (FR-009 — move, not
    duplicate).
  - Carries the unit name and the original input string for display.
  - Removable via standard ATAK long-press.
- **RecentEntry**: A historical successful submission.
  - Fields: `unit`, `rawValue`, `timestampEpochMs`.
  - Capacity: up to 10, FIFO eviction by timestamp.
  - Persisted across ATAK restarts (FR-014).
- **InputPageState** (in-session, not persisted across restarts):
  - Currently-selected tab and in-progress strings, kept while the
    page is closed-and-reopened within the same ATAK session (FR-018).
  - Cleared on plugin restart.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can convert a Taipower code received over
  radio into a centred ATAK marker in under **15 seconds** end-to-end
  (open page → type code → submit → see marker), validated on the
  reference device (Galaxy Tab S10+).
- **SC-002**: Median round-trip from submit-tap to marker-rendered
  MUST be under **300 ms** on the reference device (parsing,
  conversion, camera pan, marker creation combined).
- **SC-003**: For each of the 22 county-seat entries in the existing
  `taiwan_cities_coords.csv` fixture, entering the pinned Taipower /
  TWD97 / TWD67 value and submitting MUST drop a marker within the
  per-system tolerance bands already used in the project's
  authoritative-test suite (TWD97 ≤ 0.5 m, TWD67 main ≤ 5 m, TWD67
  outer ≤ 20 m, Taipower depends on derived TWD67 → same band).
- **SC-004**: Invalid inputs MUST be rejected within **100 ms** of the
  last keystroke (inline-error feedback latency), without ever
  triggering a network call or filesystem write.
- **SC-005**: On a clean device install with no prior usage history,
  a new operator can find the input page from the Tools menu and
  successfully submit their first Taipower coordinate in under **60
  seconds** without consulting documentation.
- **SC-006**: 100% of strings on the page (labels, errors,
  confirmations) MUST resolve to a translated value in each of
  English, Traditional Chinese (Taiwan), and Japanese; verified by
  the existing `zhtw-mcp` lint pass at zero errors / zero warnings on
  the Chinese strings.
- **SC-007**: The page MUST function with zero network connectivity;
  verified by enabling airplane mode and completing the full submit
  flow at least once for each of the three input modes.
- **SC-008** (revised — obsolete): superseded by FR-008's pan-only
  policy; the plugin no longer drops markers, so duplicate-marker
  prevention is no longer a measurable success criterion.
- **SC-009**: Tapping Auto Fill MUST update the input field within
  **100 ms** of the tap on the reference device (Galaxy Tab S10+),
  and the Auto Fill disabled-state indicator MUST track the map
  centre's representability within **one map-event cycle** of the
  pan/zoom event (no perceptible lag versus the existing readout
  widget's out-of-range indicator).

## Assumptions

- Spec inherits feature 001's coordinate-math source (4-parameter
  Bursa-Wolf TWD67, pyproj-pinned TWD97), accuracy bands, and language
  support exactly. Any change to those is out of scope here.
- The marker icon and call-sign style use ATAK's standard
  user-defined point affordance; no custom drawable is required for
  v1. A bespoke icon is deferred.
- "Drop a marker" produces a local CoT marker. Whether it propagates
  to network peers via ATAK's CoT pipeline is **out of scope** for
  this spec — the marker's network-publishability follows whatever
  default behaviour ATAK already applies to user-placed markers.
- The page is implemented as plugin-managed Android UI surfaced via
  the same `Tools-menu icon → BroadcastReceiver → UI` pattern the
  plugin already uses for its unit-cycle action, not by patching
  ATAK's native `GoToMapTool`.
- Outer-island Taipower input remains **out of scope** (the existing
  TWD67 → Taipower path is main-island only; Penghu / Kinmen / Matsu
  inputs in the Taipower tab return the same "out of range" path the
  display widget uses).
- The "Recent entries" list (US4) stores only what the operator typed;
  it does NOT store the resolved WGS84 coordinate, so a future change
  to the conversion constants would change the destination of an old
  recent entry. This is acceptable — the operator's source of truth is
  the original Taiwan-coord string, not a cached lat/lon.
- The plugin "applicationId" / package namespace stays
  `com.atakmap.android.twcoord.plugin`; this feature does not
  introduce a new plugin artefact.
- Auto Fill (US5 / FR-021..FR-024) is **map-centre only** in v1.
  Self-marker and selected-CoT-marker sources were considered and
  declined for v1 to keep the affordance unambiguous; they remain
  future candidates if field demand surfaces. The "use the
  last-submitted point" case is already covered by FR-003 and the
  recent-entries list (US4), so an Auto Fill source for it would be
  redundant.
