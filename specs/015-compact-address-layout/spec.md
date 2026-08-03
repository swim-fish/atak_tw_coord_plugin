# Feature Specification: Compact Structured Address Layout

**Feature Branch**: `codex/015-compact-address-layout`

**Created**: 2026-08-01

**Status**: Draft

**Input**: User description: "Prepare v1.5.1 and compact the native Taiwan structured Address form so county/city and district/township share one row, road and address tail share a second row, and both rows use equal columns like the compact Taipower presentation."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enter a Structured Address in Two Compact Rows (Priority: P1)

An operator switches the native Taiwan Address page to structured entry and
sees the same four address components arranged as a compact two-by-two form.
County/city and district/township share the first row; road/locality and
house-number/floor details share the second row. Each field group receives
half of its row so ATAK's surrounding controls remain visible and reachable.

**Why this priority**: The current four-row presentation consumes unnecessary
vertical space and crowds ATAK-owned elevation, marker, Auto Fill, Clear,
Copy, and confirmation controls during field use.

**Independent Test**: Switch Address from single-field to structured mode in
an editable native Go To pane and verify that all four components are visible
in two equal-column rows while the host controls remain reachable.

**Acceptance Scenarios**:

1. **Given** Address is in structured mode, **When** the pane is displayed,
   **Then** county/city and district/township appear together in the first row
   as two equal-width field groups.
2. **Given** Address is in structured mode, **When** the pane is displayed,
   **Then** road/locality and house-number/floor details appear together in the
   second row as two equal-width field groups.
3. **Given** the compact structured form is visible, **When** the operator
   reviews the surrounding dialog, **Then** the Address mode action and all
   ATAK-owned controls remain visible or reachable through the pane's single
   vertical scroll path without overlap.
4. **Given** active locality data is available, **When** the operator chooses
   county/city and district/township and types the remaining components,
   **Then** selection, validation, candidate resolution, and host confirmation
   behave exactly as before the layout change.

---

### User Story 2 - Preserve Legibility and Interaction States (Priority: P2)

An operator can use the compact form across supported orientations, languages,
font scales, editable/read-only modes, and lifecycle transitions without
clipped labels, ambiguous focus, lost draft content, or reduced touch targets.

**Why this priority**: Reducing vertical space must not trade away field
legibility, accessibility, or the safe state rules of the native ATAK pane.

**Independent Test**: Exercise the two-row structured form in English,
Traditional Chinese (Taiwan), and Japanese at font scales 1.0 and 2.0, in
portrait and landscape, in both editable and read-only host contexts.

**Acceptance Scenarios**:

1. **Given** any supported language and font scale, **When** the structured
   form is displayed, **Then** all four field labels remain distinguishable,
   input values remain readable, and the two columns do not overlap.
2. **Given** the structured form is editable, **When** the operator navigates
   with touch, keyboard actions, TalkBack, or Switch Access, **Then** controls
   follow the logical order county/city, district/township, road/locality, then
   house-number/floor details.
3. **Given** the host opens the pane as read-only, **When** structured Address
   is selected, **Then** the same two-row arrangement is shown without enabling
   edits or locality selectors.
4. **Given** a partially or fully populated Address draft, **When** the
   operator switches between single-field and structured modes or the pane is
   re-rendered, **Then** no represented address content, candidate state, or
   exact host point is changed solely by the compact layout.

### Edge Cases

- A county/city or district/township name is longer than its hint or typical
  example: the value remains readable within its half-row field and does not
  overlap the adjacent field group.
- No active Address dataset is installed: locality fields retain their safe
  unavailable behavior while the two-row geometry remains stable.
- A parsed locality is not currently selectable: its explicit unavailable
  draft value remains visible and is not silently replaced or discarded.
- A validation or lookup status becomes visible below the form: it uses the
  existing single vertical scroll owner and does not cover the two rows or
  ATAK-owned controls.
- The pane is narrower or text is larger than the reference landscape view:
  labels and values remain accessible without introducing horizontal scroll
  or a second vertical scroll owner.

### Failure & Recovery Scenarios

- **FS-001**: Given locality choices are unavailable or a lookup fails, when
  the operator uses structured mode, then the pane preserves the draft,
  presents the existing recoverable status, and does not destabilize ATAK.
- **FS-002**: Given the pane is disposed or replaced during a lookup, when a
  late callback arrives, then it cannot restore focus, mutate the compact
  form, or expose a stale result.
- **FS-003**: Given a supported keyboard ignores an inline presentation hint,
  when the operator returns to the pane, then all represented draft content
  remains intact and the host confirmation flow remains authoritative.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Structured Address entry MUST present exactly two field rows.
- **FR-002**: The first structured row MUST contain county/city followed by
  district/township, with each field group receiving one half of the available
  structured-content width.
- **FR-003**: The second structured row MUST contain road/locality followed by
  house-number/floor details, with each field group receiving one half of the
  available structured-content width.
- **FR-004**: Each field group MUST retain a distinct visible label, a
  single-line value or hint, and its existing editable, selectable, disabled,
  and read-only semantics.
- **FR-005**: The Address content and action areas MUST retain the established
  proportion, with the alternate-mode action kept in the top-aligned far-right
  action area rather than moved below the form.
- **FR-006**: The compact form MUST preserve the logical interaction order of
  county/city, district/township, road/locality, then house-number/floor
  details.
- **FR-007**: Every interactive Address control MUST retain a reachable target
  of at least 48 dp in both dimensions where the existing control contract
  requires it.
- **FR-008**: The compact form MUST use the pane's existing single vertical
  scroll owner and MUST NOT introduce horizontal scrolling, a nested vertical
  scroller, or overlap with ATAK-owned controls.
- **FR-009**: Switching Address modes, selecting localities, editing text,
  resolving candidates, Auto Fill, Clear, Copy, read-only rendering, and host
  confirmation MUST preserve their existing behavior and data ownership.
- **FR-010**: The layout change MUST NOT alter Address normalization, offline
  lookup, candidate ranking, reverse no-snap behavior, dataset state, WGS84
  host interchange, or any Taipower, TWD97, or TWD67 behavior.
- **FR-011**: English, Traditional Chinese (Taiwan), and Japanese labels,
  hints, and accessibility names MUST remain aligned and usable in the compact
  two-column presentation.
- **FR-012**: The shipped plugin version for this change MUST be `1.5.1`, with
  the changelog and both user guides identifying the same version.

### Project-Wide Quality Requirements

- **QR-001 Compatibility**: Android compile/minimum 36/26 and ATAK
  compile/minimum-runtime 5.7.0.9/5.5.0 remain unchanged. Exact ATAK 5.5 and
  current-runtime device evidence remains distinct from build evidence.
- **QR-002 Host safety**: Malformed draft state, missing resources, failed
  lookups, read-only rendering, lifecycle interruption, and late callbacks
  must recover without terminating ATAK or exposing a stale result.
- **QR-003 UX and localisation**: The two-row form must remain legible and
  reachable in supported pane sizes, portrait and landscape, font scales 1.0
  and 2.0, English, Traditional Chinese (Taiwan), and Japanese, including
  TalkBack and Switch Access use.
- **QR-004 Performance and offline operation**: Layout and mode changes must
  remain immediately responsive to the operator and must add no network path,
  telemetry, permission, or main-thread file/database work.
- **QR-005 Geospatial correctness**: The feature must not change coordinate
  conversion, address point selection, WGS84 interchange, coverage, zone,
  precision, or accuracy behavior.
- **QR-006 Migration**: Existing Address drafts, imported datasets,
  preferences, mode selection, and legacy upgrade behavior require no
  migration and must remain usable after upgrading to `1.5.1`.
- **QR-007 Release evidence**: Public release requires synchronized version
  documentation, deterministic layout and regression checks, current-runtime
  device acceptance, exact ATAK 5.5 compatibility evidence or an explicitly
  narrowed claim, accessibility checks, image/documentation validation where
  screenshots change, signer verification, and artifact provenance.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 100% of structured Address presentations, the four components
  occupy exactly two rows and two equal-width field groups per row in the
  required order.
- **SC-002**: Across the supported orientation, pane-size, language, and font
  scale matrix, zero labels, values, fields, or adjacent columns overlap, and
  every Address and ATAK host action remains reachable.
- **SC-003**: Across at least 100 single-to-structured-to-single round trips,
  100% of represented address characters, selected locality state, candidate
  state, and exact host WGS84 point are preserved unless the operator edits or
  clears them.
- **SC-004**: Across editable, read-only, missing-data, lookup-failure, locale
  replacement, and 20 reload/dispose cycles, there are zero ATAK crashes, zero
  duplicate confirmations, and zero stale-result or focus restorations.
- **SC-005**: All existing Address normalization, selector ordering, candidate
  ranking, Auto Fill, Clear, reverse no-snap, offline, coordinate, and lifecycle
  regression expectations pass without widened tolerances or altered outcomes.
- **SC-006**: On the current reference device, the compact structured form and
  mode changes provide visible feedback within 100 ms p95 across at least 20
  measured repetitions and add no observable pause to ATAK-owned controls.
- **SC-007**: Before public release, the required exact ATAK 5.5 and current
  runtime device, accessibility, documentation, signer, and provenance evidence
  is completed or explicitly dispositioned without claiming unexecuted proof.
- **SC-008**: The published application version, changelog version, English
  guide version, and Traditional Chinese guide version all report `1.5.1`.

## Assumptions

- The approved preview defines the intended visual grouping: two horizontal
  rows, two equal field groups per row, with the existing right-side Address
  mode action unchanged.
- The default Address experience remains single-field entry; this feature only
  compacts the structured presentation.
- Existing imported offline Address datasets, locality ordering, normalization,
  lookup, candidate, and reverse no-snap contracts remain authoritative.
- ATAK continues to own elevation, marker, Auto Fill, Clear, Copy, map movement,
  and final confirmation outside the plugin-owned pane.
- The feature changes layout and release version only; it introduces no new
  persisted entity, permission, dependency, network path, or coordinate rule.
