# Feature Specification: Prefill All Native Taiwan Tabs

**Feature Branch**: `codex/012-prefill-native-tabs`

**Created**: 2026-07-18

**Status**: Draft

**Input**: User description: "When an operator opens ATAK Convert Coordinate from a map item's coordinate and selects Taiwan, populate every Taiwan system tab from the supplied point unless that system cannot represent the location."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Inspect one point in every Taiwan system (Priority: P1)

An operator taps a map item's coordinate, opens ATAK's Convert Coordinate
dialog, and selects Taiwan. Taipower, TWD97, and TWD67 are all prepared from
the same supplied location, so switching the internal Taiwan system does not
show an empty or previously used coordinate.

**Why this priority**: Coordinate conversion is the purpose of this host flow.
Showing only the last-selected Taiwan system forces extra manual actions and
can expose stale values that belong to another map item.

**Independent Test**: Open Convert Coordinate for a main-island point, select
Taiwan once, and inspect all three internal systems without invoking Auto Fill.
Every system must represent the same location within its published precision.

**Acceptance Scenarios**:

1. **Given** a main-island map item supplies a valid location, **When** the
   operator selects Taiwan, **Then** Taipower, TWD97, and TWD67 all contain
   coordinates derived from that exact location.
2. **Given** Taiwan opens with any previously selected internal system,
   **When** the operator switches among all three systems, **Then** no system
   is empty or displays a coordinate retained from an earlier map item.
3. **Given** a zone-119 point that Taipower cannot represent, **When** Taiwan
   is activated, **Then** TWD97 and TWD67 are populated with zone 119 while
   Taipower is clearly unavailable and contains no stale value.

---

### User Story 2 - Edit or confirm one prepared system safely (Priority: P2)

After all systems are prepared, an operator selects the representation they
want, optionally edits that active draft when the host permits editing, and
lets ATAK complete its normal host-owned action.

**Why this priority**: Prefilling must not change which draft ATAK consumes or
cause a background draft to move the map, replace the point, or signal a human
edit.

**Independent Test**: Activate Taiwan from a supplied point, switch to TWD97,
edit one value, and confirm. ATAK must consume only the active TWD97 draft;
programmatic preparation of the other systems must produce no host action.

**Acceptance Scenarios**:

1. **Given** all representable drafts were prepared programmatically, **When**
   the dialog is still untouched, **Then** ATAK receives no human-change
   notification and performs no map or marker action.
2. **Given** the operator selects and edits one Taiwan system, **When** the
   host requests the coordinate, **Then** only that active draft determines
   the returned WGS84 point.
3. **Given** the host supplies a different map item on the next activation,
   **When** Taiwan opens again, **Then** every draft is replaced or cleared
   using the new point before any system is shown.

---

### User Story 3 - Preserve shared-dialog and read-only behaviour (Priority: P3)

The same preparation rule applies when another ATAK workflow supplies a point,
while existing editable/read-only restrictions and native controls retain
their established behaviour.

**Why this priority**: The Taiwan pane is global to ATAK's shared coordinate
entry capability, so a fix limited to one screen could leave inconsistent or
unsafe state in point details, contacts, routes, or other host flows.

**Independent Test**: Activate the pane through one editable and one read-only
host flow. Verify that all internal drafts correspond to the supplied point,
that read-only controls cannot alter it, and that the visible active system
still formats the supplied location.

**Acceptance Scenarios**:

1. **Given** a read-only host activation supplies a point, **When** Taiwan is
   rendered, **Then** its prepared state represents that point and no human
   mutation can change the host result.
2. **Given** native Clear is invoked with no supplied point, **When** the pane
   receives the clear action, **Then** only the active draft is cleared as in
   the existing workflow.
3. **Given** native Auto Fill is invoked, **When** ATAK supplies its current
   point, **Then** only the active draft is replaced as in the existing
   workflow; activation-time all-system preparation is not broadened to that
   separate command.

### Edge Cases

- A point may be valid for TWD97/TWD67 but outside Taipower coverage.
- A conversion failure in one system must not erase a valid result in another.
- Reopening the dialog for a second map item must not expose any first-item
  draft, zone, validation state, or resolved point.
- The last-selected internal system may itself be unavailable while another
  system is valid.
- Zone 119 and zone 121 must be selected independently for both TWD systems.
- A null point represents the existing native Clear action, not an activation
  request to populate all systems.
- Programmatic preparation may run while the host marks the pane read-only.
- Plugin disposal or locale refresh may occur after drafts were prepared;
  late callbacks must remain inert.

### Failure & Recovery Scenarios

- **FS-001**: Given one system cannot represent the host point, when Taiwan is
  activated, then that system is cleared and marked unavailable while every
  independently representable system remains usable.
- **FS-002**: Given an ordinary conversion or rendering failure occurs during
  activation, when the operator closes and reopens the dialog, then ATAK
  remains responsive and the pane attempts a fresh preparation without
  returning a stale coordinate.
- **FS-003**: Given the plugin is unloaded after activation, when a late host
  callback arrives, then no draft mutation or exception escapes into ATAK.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: A non-null host activation MUST prepare Taipower, TWD97, and
  TWD67 drafts before the Taiwan pane is presented.
- **FR-002**: Every prepared draft MUST derive from the same canonical WGS84
  point supplied by the host.
- **FR-003**: Each system MUST be converted independently; an unavailable or
  out-of-coverage system MUST be cleared without removing valid drafts for
  other systems.
- **FR-004**: Prepared TWD97 and TWD67 drafts MUST select zone 121 or 119 from
  the supplied point using the existing zone-selection rules.
- **FR-005**: Every non-null activation MUST replace or clear all three prior
  drafts, their resolved values, and their availability states so no earlier
  map-item value can be displayed.
- **FR-006**: Switching the internal system after activation MUST display the
  already prepared draft and MUST NOT require native Auto Fill.
- **FR-007**: Activation MUST preserve the operator's last-selected internal
  system as the visible system, even when another system is also prepared.
- **FR-008**: Programmatic all-system preparation MUST NOT notify ATAK of a
  human edit or trigger a host-owned map, marker, elevation, or confirmation
  action.
- **FR-009**: Validation, formatting, Copy, and confirmation MUST continue to
  consume only the active Taiwan draft.
- **FR-010**: A null host activation used by native Clear MUST continue to
  clear only the active draft.
- **FR-011**: Native Auto Fill MUST continue to replace only the active draft;
  this feature MUST NOT broaden Auto Fill into an all-system action.
- **FR-012**: Editable and read-only restrictions MUST remain unchanged after
  all-system preparation.
- **FR-013**: The feature MUST NOT change coordinate constants, parser rules,
  published precision, custom GoTo drafts, Recent entries, or marker modes.
- **FR-014**: Unavailable-system feedback MUST use the existing supported
  English, Traditional Chinese (Taiwan), and Japanese user-visible states.
- **FR-015**: The active pane MUST render after all three preparation attempts
  without exposing a partially updated mixture of old and new drafts.
- **FR-016**: The shipped plugin version MUST be 1.4.2, with matching
  English/Traditional Chinese guide and changelog labels; the unreleased
  compact-layout work MUST NOT appear as a separate 1.4.1 release.

### Project-Wide Quality Requirements

- **QR-001 Compatibility**: The minimum ATAK runtime remains ATAK-CIV 5.5.0;
  behaviour must be validated on the minimum line when available and the
  current ATAK runtime matching the pinned compile SDK.
- **QR-002 Host safety**: Conversion, lifecycle, and rendering failures must
  stay inside the existing host-callable safety boundary and must never return
  stale coordinate data.
- **QR-003 UX and localisation**: The existing compact DD-style layout,
  accessibility labels, read-only states, and English/zh-TW/Japanese resources
  remain unchanged unless a new visible unavailable state is required.
- **QR-004 Performance and offline operation**: All-system preparation must
  complete within 100 ms at p95 and worst-case on the reference device, remain
  fully offline, and add no network permission or dependency.
- **QR-005 Geospatial correctness**: Taipower, TWD97, and TWD67 results must
  retain the established coverage, zone, normalisation, and accuracy budgets,
  including zone 119 and zone 121 golden-vector coverage.
- **QR-006 Migration**: Existing native selection preference, custom GoTo
  state, and advanced workflow remain byte-for-byte compatible; no migration
  is introduced.

### Key Entities

- **Host Activation Point**: The canonical WGS84 location supplied by ATAK for
  one coordinate-entry activation, or null for the native Clear action.
- **Taiwan System Draft**: One Taipower, TWD97, or TWD67 representation with
  fields, zone where applicable, availability, validation, and resolved WGS84.
- **Preparation Snapshot**: The atomic set of three drafts derived from one
  non-null host activation point.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every main-island golden point, one Taiwan activation yields
  three populated systems representing the same location without Auto Fill.
- **SC-002**: For every zone-119 golden point, both TWD systems select zone 119
  while Taipower is either valid within coverage or explicitly unavailable.
- **SC-003**: Across 100 alternating activations for two distinct points, zero
  drafts, zones, availability states, or returned coordinates come from the
  preceding activation.
- **SC-004**: Programmatic preparation produces zero human-change callbacks
  and zero host-owned map or marker actions before operator input.
- **SC-005**: All three preparation attempts and visible rendering complete
  within 100 ms at p95 and worst-case on the reference device.
- **SC-006**: The primary Convert Coordinate, editable shared-dialog, and
  read-only journeys complete without host crash on ATAK 5.5 when available
  and the current ATAK 5.7.0.9 reference runtime.
- **SC-007**: All existing coordinate golden-vector and round-trip suites pass
  without widened tolerances or changed constants.

## Assumptions

- This feature includes the unreleased compact native-pane work originally
  labelled v1.4.1 in closed PR #9, does not change its layout decisions, and
  ships both fixes as v1.4.2.
- "All tabs" means the three internal Taiwan systems: Taipower, TWD97, and
  TWD67; ATAK's built-in MGRS/DD/DM/DMS/UTM/Address panes are host-owned.
- The rule applies to every non-null `CoordinateEntryPane` activation because
  the plugin cannot safely distinguish every host caller by screen title.
- Native Clear and Auto Fill retain their current active-draft-only semantics.
- WGS84 remains the canonical host interchange; existing converters determine
  each system's coverage, zone, formatting, and precision.
- No new storage, permission, network operation, or ATAK SDK integration is
  required.
