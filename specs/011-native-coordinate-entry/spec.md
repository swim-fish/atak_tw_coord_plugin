# Feature Specification: Native Taiwan Coordinate Entry

**Feature Branch**: `011-native-coordinate-entry`

**Created**: 2026-07-17

**Status**: Draft

**Input**: User description: "Integrate Taiwan coordinate entry into ATAK's
native coordinate-entry experience so operators can use familiar ATAK Go To
controls while retaining support for Taipower, TWD97, and TWD67 coordinates.
Support ATAK-CIV 5.5 and later while compiling with the locally available
ATAK-CIV 5.7.0.9 SDK, and keep the existing custom GoTo page during the
migration."

## Scope

This feature adds one operator-visible **Taiwan** choice to ATAK's native
coordinate-entry experience. Inside that choice, the operator selects
Taipower, TWD97, or TWD67 and enters the corresponding Taiwan coordinate.
Using one Taiwan choice avoids adding three more top-level choices to an ATAK
dialog that already contains several coordinate formats.

The native experience becomes the quickest path for ordinary Go To and point
editing. The existing **TW Coord GoTo** plugin page remains available for its
advanced marker modes, custom icon palette, Recent list, and established
operator workflow. Removing that page, changing coordinate algorithms, or
adding Taiwan systems to ATAK's global coordinate-display preference is out of
scope.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Use Taiwan coordinates in native ATAK Go To (Priority: P1)

An ATAK operator receives a Taiwan coordinate over radio or on paper. They open
ATAK's standard Go To coordinate dialog, select **Taiwan**, enter the
coordinate, and complete the same native action they already use for MGRS,
latitude/longitude, or UTM. The map reaches the resolved location without the
operator first learning a separate plugin page or using an external converter.

**Why this priority**: Reducing the learning cost of the primary coordinate
handoff is the purpose of the feature. A native Go To path provides value even
before any secondary host dialog is verified.

**Independent Test**: On a clean plugin installation, open ATAK's native Go To
dialog, select Taiwan, enter a pinned Taipower coordinate, confirm, and verify
that ATAK completes its normal Go To result at the same WGS84 location as the
existing custom page.

**Acceptance Scenarios**:

1. **Given** ATAK-CIV 5.5 or later is running with the plugin enabled, **When**
   the operator opens the native coordinate-entry dialog, **Then** exactly one
   visible Taiwan choice is available alongside ATAK's built-in formats.
2. **Given** the Taiwan choice is active and contains a valid coordinate,
   **When** the operator confirms the native dialog, **Then** ATAK receives the
   resolved location and performs the host flow that opened the dialog.
3. **Given** the operator has not used the Taiwan choice before, **When** they
   first open it, **Then** Taipower is selected and the page is ready for input
   without changing any global ATAK coordinate-display preference.

---

### User Story 2 - Enter all supported Taiwan systems safely (Priority: P1)

An operator switches within the Taiwan choice between Taipower, TWD97, and
TWD67. TWD97 and TWD67 allow an explicit TM2 zone choice so a plausible number
cannot silently resolve in the wrong zone. Invalid or out-of-coverage input is
explained without closing the native dialog or moving the map.

**Why this priority**: Taiwan field handoffs use all three systems. Omitting
TWD67 or a zone selector would force some operators back to an external
converter and could produce geographically plausible but incorrect points.

**Independent Test**: Enter the existing golden vectors for Taipei 101 and one
zone-119 location in each applicable system. Verify that each resolves within
its established tolerance and that an invalid value leaves the native dialog
open with actionable feedback.

**Acceptance Scenarios**:

1. **Given** Taipower is selected, **When** the operator enters a currently
   accepted 9- or 11-character Taipower code with supported spacing and letter
   case, **Then** it resolves to the same point as the existing custom GoTo
   page.
2. **Given** TWD97 or TWD67 is selected, **When** the operator enters easting
   and northing and explicitly selects zone 121 or 119, **Then** the values are
   interpreted as ASCII base-10 integer metres in that selected datum and zone.
3. **Given** a TWD67 zone-119 coordinate is entered, **When** the page displays
   the coordinate, **Then** the operator sees the existing outer-island
   accuracy advisory before confirming.
4. **Given** an input is malformed, incomplete, or resolves outside Taiwan's
   supported coverage, **When** the operator attempts to confirm or copy it,
   **Then** the dialog remains open, the host action is not performed, and a
   localised message identifies the corrective action.

---

### User Story 3 - Reuse native Auto Fill, Clear, and Copy controls (Priority: P2)

An operator uses ATAK's standard coordinate-dialog controls instead of learning
plugin-specific equivalents. Auto Fill converts the host-provided map centre
into the active Taiwan system, Clear empties the Taiwan fields, and Copy
produces a complete human-readable Taiwan coordinate without changing the
current draft.

**Why this priority**: Matching native controls is the main learning-cost
benefit after basic entry works, but operators can still complete Go To without
these conveniences.

**Independent Test**: Centre the map on pinned main-island and zone-119
locations. For every Taiwan system, invoke native Auto Fill, Clear, and Copy
and verify the visible values, selected zone, clipboard text, and unchanged map
state before confirmation.

**Acceptance Scenarios**:

1. **Given** the map centre is representable by the selected Taiwan system,
   **When** the operator invokes native Auto Fill, **Then** the corresponding
   fields and zone are populated without confirming the dialog or moving the
   map.
2. **Given** the map centre cannot be represented by the selected Taiwan
   system, **When** Auto Fill is invoked, **Then** the active draft is cleared
   and an explanatory localised state is shown; no stale coordinate is
   presented as current.
3. **Given** a valid Taiwan draft exists, **When** the operator invokes native
   Copy, **Then** the clipboard receives a deterministic string containing the
   coordinate system, values, and zone where applicable.
4. **Given** a Taiwan draft exists, **When** the operator invokes native Clear,
   **Then** every field belonging to the active draft is cleared and subsequent
   confirmation is rejected until a new valid coordinate exists.

---

### User Story 4 - Keep the advanced custom GoTo workflow (Priority: P2)

An existing plugin user upgrades and can continue opening **TW Coord GoTo**
from the Tools menu or settings shortcut. Their marker mode, custom icon,
Recent entries, and saved coordinate values remain available while the native
Taiwan choice offers a simpler alternative.

**Why this priority**: Immediate removal would regress established workflows
and turn a learning-cost improvement into a forced migration. Coexistence makes
the native integration independently releasable and reversible.

**Independent Test**: Upgrade an installation containing saved Recent entries
and a non-default marker mode. Complete one native Taiwan Go To, then open the
custom page and verify that its entry points, preferences, Recent entries, and
marker workflow are unchanged.

**Acceptance Scenarios**:

1. **Given** an operator upgrades from a version with the custom GoTo page,
   **When** the upgrade completes, **Then** the existing Tools-menu and settings
   entry points remain available.
2. **Given** existing custom-page preferences and Recent entries, **When** the
   native Taiwan integration is first used, **Then** those stored values are
   neither deleted nor rewritten.
3. **Given** the native Taiwan choice cannot be registered, **When** the plugin
   otherwise loads successfully, **Then** ATAK remains usable and the existing
   custom GoTo page remains the documented fallback.

---

### User Story 5 - Use Taiwan entry in other native location dialogs (Priority: P3)

An operator encounters the same ATAK coordinate-entry dialog while editing a
point, route location, range-and-bearing endpoint, or another host-supported
location. The Taiwan choice behaves consistently and respects whether that
host flow is editable or read-only.

**Why this priority**: The native coordinate dialog is shared by several ATAK
workflows. Consistency provides additional value, but the primary Go To journey
can ship independently once unintended host interactions are contained.

**Independent Test**: Open one editable point-details flow and one additional
native location flow. Verify that the Taiwan choice displays the supplied
point, accepts a valid edit only when allowed, and returns the same location as
the built-in formats.

**Acceptance Scenarios**:

1. **Given** an editable native location dialog supplies an existing point,
   **When** the operator selects Taiwan, **Then** the point is rendered in the
   last-selected Taiwan system and may be edited.
2. **Given** a read-only native location dialog supplies an existing point,
   **When** the operator selects Taiwan, **Then** the point is formatted for
   viewing and the Taiwan fields cannot alter the host location.
3. **Given** the operator switches between Taiwan and a built-in format,
   **When** no human edit occurred, **Then** the represented geographic point
   remains unchanged within the active format's published precision.

### Edge Cases

- Repeated plugin enable, disable, or reload cycles must never create duplicate
  Taiwan choices or leave a non-functional choice after unload.
- A native coordinate dialog already open during plugin disable must close or
  cease using the Taiwan choice safely without terminating ATAK.
- Taipower remains main-island only; Auto Fill or manual input for unsupported
  outer-island locations must not manufacture a plausible code.
- TWD97 and TWD67 zone 119 and zone 121 can contain similar-looking numeric
  values; the selected zone must always remain visible with the draft.
- Empty, partial, pasted, mixed-case, whitespace-heavy, and out-of-range input
  must not replace the host's current valid point.
- Decimal TWD values, locale grouping separators, signs, and non-ASCII digits
  are rejected rather than rounded or interpreted differently from the
  existing custom GoTo parser.
- A locale change while the dialog is closed must be reflected the next time
  it opens; saved numeric values must remain unchanged.
- A point without valid altitude must still permit horizontal Taiwan
  coordinate entry; host-owned elevation behaviour remains independent.
- Repeated native Auto Fill calls must replace the prior draft completely and
  must not combine values from different systems or zones.

### Failure & Recovery Scenarios

- **FS-001**: Given native integration setup fails, when the plugin loads, then
  ATAK remains running, no broken Taiwan choice is shown, the failure is
  diagnosable, and the custom GoTo page remains available.
- **FS-002**: Given the plugin is disabled or unloaded, when ATAK later opens a
  coordinate dialog, then no stale Taiwan choice remains; re-enabling the
  plugin restores exactly one working choice.
- **FS-003**: Given malformed or out-of-coverage input, when the host requests
  a coordinate, then the dialog remains open with localised corrective
  feedback and the prior host point is preserved.
- **FS-004**: Given a host lifecycle interruption occurs while Taiwan entry is
  active, when the dialog is recreated or reopened, then the plugin returns to
  a valid empty or last-confirmed state and does not expose a half-converted
  coordinate.
- **FS-005**: Given a runtime older than ATAK-CIV 5.5, when installation or
  loading is attempted, then the release is treated as unsupported rather than
  silently claiming native Taiwan compatibility.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The plugin MUST add exactly one top-level **Taiwan** choice to
  ATAK's native coordinate-entry experience while the plugin is enabled.
- **FR-002**: The Taiwan choice MUST provide three mutually exclusive input
  systems: Taipower, TWD97, and TWD67.
- **FR-003**: The Taiwan choice MUST keep the selected system visible at all
  times and MUST remember the last-selected Taiwan system across dialog
  openings; first use MUST default to Taipower.
- **FR-004**: Taipower input MUST accept every format currently accepted by
  the existing custom GoTo page and MUST preserve its normalisation, coverage,
  precision, and rejection rules.
- **FR-005**: TWD97 and TWD67 input MUST expose separate easting and northing
  values as ASCII base-10 integer metres and an explicit TM2 zone selector for
  zones 121 and 119. Decimal values, grouping separators, signs, and non-ASCII
  digits MUST be rejected rather than rounded or locale-interpreted.
- **FR-006**: The active zone MUST remain visible whenever a TWD97 or TWD67
  draft is displayed, copied, auto-filled, or submitted.
- **FR-007**: TWD67 zone-119 entry MUST show the existing outer-island accuracy
  advisory before the operator confirms the coordinate.
- **FR-008**: Every valid Taiwan input MUST resolve to the same canonical WGS84
  location and within the same published tolerance as the existing coordinate
  engine and golden vectors.
- **FR-009**: Invalid, incomplete, ambiguous, or out-of-coverage input MUST NOT
  dismiss the dialog, change the map, replace the host point, or return a
  coordinate to the host flow.
- **FR-010**: Invalid input feedback MUST identify the relevant problem using
  user-facing language, including malformed Taipower code, missing numeric
  value, unsupported zone, and outside-coverage cases.
- **FR-011**: Native Auto Fill MUST populate the active Taiwan system from the
  host-provided point and MUST select the matching TM2 zone where applicable.
- **FR-012**: Native Clear MUST remove all coordinate values from the active
  Taiwan draft and leave it invalid until new input is supplied.
- **FR-013**: Native Copy and other host formatting requests MUST return a
  deterministic, human-readable Taiwan coordinate string without mutating the
  visible draft.
- **FR-014**: When a host flow supplies an existing point, the Taiwan choice
  MUST render that point in the last-selected Taiwan system without changing
  its geographic location.
- **FR-015**: When the host marks a coordinate as read-only, the Taiwan choice
  MUST prevent human edits while still displaying and formatting the point.
- **FR-016**: Operator edits MUST notify the native dialog that the coordinate
  changed so host-owned elevation or confirmation state can remain coherent.
- **FR-017**: Plugin enablement MUST result in at most one registered Taiwan
  choice, and plugin disablement or unload MUST remove that choice and its
  active resources.
- **FR-018**: A native integration failure MUST be contained without
  terminating ATAK or disabling unrelated plugin features.
- **FR-019**: The existing **TW Coord GoTo** Tools-menu entry, settings
  shortcut, marker modes, custom icon workflow, saved coordinate values, and
  Recent list MUST remain available and behaviourally unchanged.
- **FR-020**: Native Taiwan entry MUST NOT delete, migrate, or reinterpret the
  custom page's existing preferences or Recent entries.
- **FR-021**: All visible labels, hints, advisories, and validation messages
  introduced by this feature MUST be available in English, Traditional
  Chinese (Taiwan), and Japanese.
- **FR-022**: The internal system selector and input controls MUST remain
  reachable in supported ATAK dialog sizes, with meaningful accessibility
  labels and field-usable touch targets. Their field geometry MUST match the
  existing `tw_coord_goto.xml` baseline: 20 sp input text, 14 dp Taipower field
  vertical padding, 13 dp TWD field padding, 52 dp system selectors, 50 dp zone
  selectors, a 10 dp TWD field gap, and 12 dp content inset. On every
  compatibility-matrix device, orientation, and font scale, the native fields
  MUST be no smaller and no less reachable than the corresponding custom GoTo
  fields under the same configuration.
- **FR-023**: The feature MUST operate fully offline and MUST NOT add network
  permission, telemetry, or an online conversion dependency.
- **FR-024**: Native Taiwan entry MUST be supported on ATAK-CIV 5.5 and the
  current supported ATAK line; ATAK-CIV 5.4 and earlier remain unsupported.
- **FR-025**: This feature MUST NOT change the project's coordinate
  transformation constants, datum models, coverage bounds, or published
  accuracy claims.
- **FR-026**: This feature MUST NOT add Taipower, TWD97, or TWD67 to ATAK's
  global coordinate-format preferences or alter the on-map readout selection.
- **FR-027**: Marker creation, affiliation, elevation, map movement, and other
  results owned by the native host flow MUST retain ATAK's normal behaviour;
  the Taiwan choice supplies only the resolved location and display text.

### Project-Wide Quality Requirements

- **QR-001 Compatibility**: Planning and release validation MUST distinguish
  Android compile SDK 36, Android minimum SDK 26, ATAK compile SDK 5.7.0.9,
  and ATAK minimum runtime 5.5.0. Native entry must be exercised on ATAK 5.5
  and the current supported ATAK 5.7.0.9 line before release.
- **QR-002 Host safety**: Registration, activation, human edits, conversion,
  formatting, and unload failures must remain contained within the plugin and
  must not terminate or destabilise ATAK.
- **QR-003 UX and localisation**: The Taiwan choice must use one internal
  selector rather than three top-level host choices, respect native editable
  state, fit supported dialog sizes, and provide complete English, zh-TW, and
  Japanese strings.
- **QR-004 Performance and offline operation**: Pane activation/rendering,
  switching the Taiwan system, validating input, Auto Fill, Clear, formatting,
  and conversion must meet the SC-003 latency budget and require no network
  access.
- **QR-005 Geospatial correctness**: WGS84 remains the canonical host
  interchange. Taipower uses its existing TWD67 zone-121 main-island domain;
  TWD97 and TWD67 retain explicit zones 121 and 119, existing golden vectors,
  round-trip expectations, and published error budgets.
- **QR-006 Migration**: The native choice is additive. The custom GoTo page and
  its stored state remain the rollback and advanced-workflow path until a
  separately specified feature decides otherwise.

### Key Entities

- **Taiwan Coordinate Draft**: The operator's active system and editable
  values. It contains a Taipower code or an easting, northing, and TM2 zone,
  plus a validation state. It must never be returned to the host while invalid.
- **Resolved Coordinate**: The canonical WGS84 point produced from a valid
  Taiwan Coordinate Draft. It preserves the coordinate provenance needed for
  deterministic display but does not redefine host-owned altitude or marker
  behaviour.
- **Taiwan Pane Preference**: The last-selected internal Taiwan system. It is
  independent of ATAK's global display-format preference and must not alter
  the existing custom GoTo history.
- **Native Entry Session**: The current host-supplied point, editability state,
  map-centre value, and Taiwan draft visible for one native dialog session.
  Unload or lifecycle interruption must end the session safely.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a clean installation, an operator familiar with ATAK's native
  Go To can find Taiwan entry and complete a valid Taipower Go To in under
  **30 seconds** without opening the plugin-specific GoTo page or consulting
  external conversion documentation.
- **SC-002**: All existing authoritative golden vectors resolve within the
  current published tolerances: TWD97 at or below **0.5 m**, TWD67 main island
  at or below **5 m**, TWD67 outer islands at or below **20 m**, and Taipower
  within the tolerance inherited from its TWD67 source.
- **SC-003**: Pane activation/rendering, validation, system switching, Auto
  Fill, Clear, and formatting update the visible Taiwan state within **100 ms**
  on the reference device for every supported system and applicable zone,
  excluding host-owned dialog animation time. The result is measured over at
  least 20 iterations of each applicable operation/system/zone combination and
  both worst-case and p95 must remain within the budget.
- **SC-004**: Across **100** consecutive enable/disable or plugin reload
  cycles in an automated lifecycle harness, the next native dialog contains
  either exactly one working Taiwan choice while enabled or none while
  disabled, with no duplicate choice and no uncaught plugin failure.
- **SC-005**: The primary Go To, invalid-input, Auto Fill, read-only display,
  and unload scenarios complete successfully on both ATAK-CIV **5.5** and the
  current supported ATAK 5.7.0.9 line before release; device-only results
  remain explicitly incomplete until actually run.
- **SC-006**: **100%** of strings introduced by this feature resolve in
  English, Traditional Chinese (Taiwan), and Japanese, with no missing or
  mismatched format arguments.
- **SC-007**: For MGRS, DD, DM, DMS, and UTM, every built-in-to-Taiwan-to-built-
  in switch without a human edit at pinned main-island and zone-119 points
  round-trips within the precision promised by the selected Taiwan system.
  Address is excluded because it is a lookup pane rather than a deterministic
  coordinate-format round trip.
- **SC-008**: The full native Taiwan Go To and Auto Fill journeys succeed in
  airplane mode with **zero** outbound network attempts and no new network
  permission.
- **SC-009**: An upgrade preserving at least 10 custom-page Recent entries and
  a non-default marker mode retains **100%** of those values after native entry
  is used.

## Assumptions

- ATAK-CIV 5.5 is the minimum supported runtime established by ADR-0022;
  ATAK-CIV 5.4 compatibility is not part of this feature. ADR-0024 updates the
  compile SDK to 5.7.0.9 without raising this minimum.
- The feature depends on ATAK's supported ability to accept plugin-provided
  native coordinate-entry choices and to remove them during plugin unload.
  Planning must verify the public contract against the 5.5 source line and the
  pinned ATAK-CIV 5.7.0.9 SDK before implementation. Physical 5.5 and current
  runtime journeys remain release validation.
- One top-level **Taiwan** choice with an internal three-system selector is the
  chosen product shape. Three separate top-level Taipower, TWD97, and TWD67
  choices are intentionally rejected to limit host-tab crowding.
- First use defaults to Taipower to preserve the existing custom page's field
  workflow; subsequent sessions use the last-selected Taiwan system.
- The existing coordinate engine, accepted input forms, WGS84 coverage bounds,
  golden vectors, TWD67 transformation, and Taipower main-island limitation are
  authoritative and are reused without algorithm changes.
- The native host flow owns its confirmation, marker, affiliation, elevation,
  and map-action behaviour. The Taiwan choice provides a location and formatted
  representation only.
- The existing custom GoTo page remains the advanced and rollback workflow for
  this release. Any future deprecation or removal requires a separate
  specification with migration evidence.
