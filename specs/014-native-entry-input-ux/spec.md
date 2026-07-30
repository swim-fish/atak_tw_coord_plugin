# Feature Specification: Native Taiwan Input UX

**Feature Branch**: `codex/014-native-entry-input-ux`

**Created**: 2026-07-30

**Status**: Draft

**Input**: User description: "Keep Go To Taiwan input inside the current ATAK
dialog, add MGRS-style single-field and split-field Taipower entry, enforce the
correct A-H/A-E letter ranges, and make the system and zone selectors visually
more compact."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Type Without Leaving Go To (Priority: P1)

An operator opens ATAK Go To, selects Taiwan, and taps any editable Taiwan
coordinate or address field. The software keyboard appears while the current
Go To dialog remains visible instead of replacing it with a full-screen text
editor.

**Why this priority**: Leaving the Go To dialog removes surrounding coordinate
context and makes field entry feel like an unrelated screen. Keeping the
operator in place is the most immediate usability and safety improvement.

**Independent Test**: Open each editable Taiwan field on the reference device
in portrait and landscape. The default system keyboard appears, the field and
Go To context remain visible, and the operator can enter, revise, and submit a
value without entering a separate full-screen editor.

**Acceptance Scenarios**:

1. **Given** an editable Taipower, TWD97, TWD67, or Address field is visible,
   **When** the operator taps it, **Then** the software keyboard appears without
   replacing the ATAK Go To dialog with a full-screen text editor.
2. **Given** a coordinate format contains consecutive fields, **When** the
   operator completes a non-final field or uses the keyboard's Next action,
   **Then** focus moves to the next logical field and remains within the same
   Go To dialog.
3. **Given** the operator is editing the final field, **When** the keyboard's
   Done or Search action is used, **Then** the keyboard action is handled
   without opening another screen or bypassing ATAK's host-owned confirmation.
4. **Given** the pane is read-only, **When** the operator taps displayed
   content, **Then** no editable keyboard session starts.

---

### User Story 2 - Switch Taipower Entry Layout (Priority: P1)

An operator entering a Taipower coordinate can switch between one familiar
single field and a guided split layout modelled on ATAK's native MGRS entry.
Both layouts represent the same draft and support 9-character 10 m codes and
11-character 1 m codes.

**Why this priority**: A single field is fast for paste and experienced users,
while a guided split layout reduces character-position and letter-range errors
when a code is read from a pole or radioed by another operator.

**Independent Test**: Enter the pinned 9-character and 11-character Taipower
vectors in either layout, switch layouts in both directions, and confirm that
the visible code, precision, resolved point, and ATAK confirmation result do
not change.

**Acceptance Scenarios**:

1. **Given** single-field mode is active, **When** the operator enters
   `H7509 DB40`, **Then** the draft is accepted as a 9-character, 10 m Taipower
   code.
2. **Given** single-field mode is active, **When** the operator enters
   `H7509 DB4016`, **Then** the draft is accepted as an 11-character, 1 m
   Taipower code.
3. **Given** split-field mode is active, **When** the operator enters
   `H`, `7509`, `DB`, and `40`, **Then** the same 9-character draft and resolved
   point are produced as single-field entry.
4. **Given** split-field mode is active, **When** the operator enters
   `H`, `7509`, `DB`, and `4016`, **Then** the same 11-character draft and
   resolved point are produced as single-field entry.
5. **Given** a valid or incomplete draft that can be represented in both
   layouts, **When** the operator switches layouts repeatedly, **Then** no
   character, precision, validation state, or resolved point is lost or
   changed merely because of the switch.
6. **Given** an existing installation has no saved Taipower entry-mode choice,
   **When** Taiwan entry opens after upgrade, **Then** single-field mode is used
   to preserve the existing workflow.
7. **Given** the operator selects a Taipower entry mode, **When** the pane is
   closed and later reopened or the plugin is reloaded, **Then** the last
   selected mode is restored.
8. **Given** either Taipower layout is visible, **When** the operator views the
   pane, **Then** one mode action appears in the far-right action column,
   matches the Address mode-action pattern, and names the alternate layout
   that will be shown when activated.

---

### User Story 3 - Prevent Invalid Taipower Subgrid Letters (Priority: P1)

An operator receives immediate, precise validation for the two 100 m Taipower
subgrid letters: the east-west letter accepts A through H and the north-south
letter accepts A through E.

**Why this priority**: Accepting letters outside the physical 800 m by 500 m
subgrid can decode a plausible but incorrect location. Input guidance and the
underlying validator must agree on the valid grid.

**Independent Test**: Exercise every boundary letter and one letter beyond each
boundary through both entry layouts. A/H and A/E boundary values are accepted;
I/J in the east-west position and F-J in the north-south position are rejected
without moving the map.

**Acceptance Scenarios**:

1. **Given** a structurally complete Taipower code, **When** its first 100 m
   letter is within A-H and its second is within A-E, **Then** validation
   continues to coordinate coverage and resolution.
2. **Given** either entry layout, **When** the first 100 m letter is I or J,
   **Then** the code is rejected as malformed, the attempted letter remains
   visible for correction, localized feedback identifies that the east-west
   letter must be A-H, and no location is confirmed.
3. **Given** either entry layout, **When** the second 100 m letter is F through
   J, **Then** the code is rejected as malformed, the attempted letter remains
   visible for correction, localized feedback identifies that the north-south
   letter must be A-E, and no location is confirmed.
4. **Given** a valid code accepted before the upgrade, **When** it is entered
   after the upgrade, **Then** it retains the same precision and resolves
   within the established Taipower accuracy budget.

---

### User Story 4 - Use Compact Reachable Selectors (Priority: P2)

An operator sees slimmer Taiwan system and TWD zone selectors, leaving the
native pane visually lighter while each option remains easy to tap and readable
at supported font sizes.

**Why this priority**: The current 48 dp tracks are visually heavy in the
height-constrained Go To dialog, but reducing the actual target to 32 dp would
make field operation less reliable.

**Independent Test**: Compare the current and updated pane at the same device,
orientation, and font scale. The visible system and zone tracks are 36 dp high,
each option retains at least a 48 dp reachable target, and no label is clipped
or overlaps the coordinate fields or ATAK-owned controls.

**Acceptance Scenarios**:

1. **Given** the Taiwan system selector is visible, **When** it is rendered at
   a supported font scale, **Then** its visible track is 36 dp high while every
   system option retains at least a 48 dp reachable target.
2. **Given** a TWD97 or TWD67 zone selector is visible, **When** the operator
   taps anywhere in the reachable area for 121 or 119, **Then** the intended
   zone is selected even where the visible track is inset.
3. **Given** the largest supported field-usable font scale, **When** all four
   system labels and both zone labels are shown, **Then** the labels remain
   legible, centred, and unclipped.

---

### User Story 5 - Fill Every Taiwan Page at Once (Priority: P1)

An operator presses ATAK Auto Fill once and can then switch among Taipower,
TWD97, TWD67, and Address without repeating the action. Every page describes
the same latest host WGS84 point.

**Why this priority**: Repeating Auto Fill on each page is unnecessary field
work and can leave stale representations from different host points.

**Independent Test**: Activate the pane with one point, select any Taiwan page,
then Auto Fill with a different point. Switch through all four pages and
confirm that every coordinate/address result derives from the second point,
the originally selected page remains selected, and no human-change callback
or ATAK confirmation occurs.

**Acceptance Scenarios**:

1. **Given** any Taiwan page is active, **When** ATAK supplies a non-null Auto
   Fill point, **Then** Taipower, TWD97, TWD67, and Address are refreshed from
   that same exact WGS84 point without changing the active page.
2. **Given** an outer-island Auto Fill point, **When** Taipower cannot
   represent it, **Then** Taipower is marked unavailable while both zone-119
   TWD pages and Address remain prepared and usable.
3. **Given** any Taiwan page is active, **When** ATAK supplies no point for
   Clear, **Then** only the active page is cleared and no inactive draft is
   deleted.
4. **Given** Address reverse lookup finds a nearby record, **When** it
   completes, **Then** Address retains the exact Auto Fill point and does not
   snap geometry to the record.

### Edge Cases

- The final split Taipower digit group is empty, one digit, or three digits:
  the draft is incomplete rather than malformed and cannot be confirmed.
- The final split Taipower digit group contains two digits: the code represents
  10 m precision and remains editable so two optional 1 m digits can be added.
- The final split Taipower digit group contains more than four digits: extra
  input is rejected without altering the accepted four digits.
- A single-field paste contains lowercase letters, supported whitespace, or
  one pair of surrounding parentheses: it is normalised without changing the
  coordinate.
- A single-field draft contains characters or ordering that cannot be
  represented safely in split fields: requesting split mode keeps the
  single-field draft visible, reports a localised malformed-input state, and
  does not discard or rearrange the draft.
- A supported main-island region letter is followed by an otherwise valid code
  that resolves outside Taiwan coverage: the result remains out of coverage
  and ATAK receives no confirmed point.
- Auto Fill targets a point outside supported Taipower coverage: Taipower
  remains unavailable while TWD97, TWD67, and Address are refreshed and remain
  usable.
- A third-party keyboard ignores the supported inline-editor request: the
  plugin preserves the draft and remains safe, but release acceptance is based
  on the default keyboard in the named device matrix.
- The operator switches input layout while the pane is read-only: the visible
  projection may change, but no text can be edited and no host change is
  reported.

### Failure & Recovery Scenarios

- **FS-001**: Given malformed or incomplete Taipower input, when ATAK requests
  the current point, then the pane returns a localised checked input error and
  ATAK remains running without moving the map.
- **FS-002**: Given a mode switch cannot project the current raw draft without
  loss, when split mode is requested, then single-field mode remains active,
  the draft remains byte-for-byte available to the operator, and a localised
  validation state explains why it cannot be split.
- **FS-003**: Given plugin disposal, reload, or locale replacement occurs while
  a field owns focus, when a late keyboard, focus, or text callback arrives,
  then it is ignored safely and the replacement pane restores the selected
  Taipower mode without duplicating a host action.
- **FS-004**: Given a supported input method temporarily opens an unexpected
  editor presentation, when the operator returns to ATAK Go To, then the draft
  is preserved and can be continued or cleared without restarting ATAK.
- **FS-005**: Given an invalid legacy A-I/A-J or A-F/A-J subgrid combination is
  entered after upgrade, when validation runs, then it is rejected visibly
  rather than decoded into a neighbouring subgrid.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every editable field owned by the Taiwan pane MUST request an
  inline software-keyboard experience that keeps the current ATAK Go To dialog
  visible on the supported device and keyboard matrix.
- **FR-002**: Taiwan input MUST NOT open a separate plugin-owned or replacement
  screen merely because an editable field receives focus.
- **FR-003**: Consecutive split fields MUST expose a logical forward focus order
  and a final Done or Search action without bypassing ATAK's host-owned
  confirmation.
- **FR-004**: The Taipower tab MUST provide exactly two operator-selectable
  entry layouts: one single field and one four-part split layout. One
  right-aligned mode action matching the Address content/action pattern MUST
  switch between them and name the alternate layout; it MUST NOT consume a
  separate full-width selector row.
- **FR-005**: The split layout MUST present, in order, one region-letter field,
  one four-digit subregion field, one two-letter 100 m subgrid field, and one
  final field accepting either two or four digits.
- **FR-006**: The first 100 m subgrid letter MUST accept A-H only, and the
  second 100 m subgrid letter MUST accept A-E only, in both entry layouts and
  in the authoritative coordinate validator. The editors MUST preserve an
  attempted ASCII letter outside those ranges long enough to display
  position-specific validation feedback; they MUST NOT silently discard it.
- **FR-007**: The region letter MUST retain the existing supported main-island
  Taipower region set; this feature MUST NOT add offshore Taipower anchors or
  imply coverage outside the current domain.
- **FR-008**: A complete 9-character Taipower code MUST retain 10 m precision,
  and a complete 11-character code MUST retain 1 m precision.
- **FR-009**: Zero, one, or three digits in the final split field after any
  preceding content MUST be treated as incomplete; two or four digits MUST
  proceed to full validation; more than four digits MUST not be accepted.
- **FR-010**: Letter entry MUST be case-insensitive and displayed
  consistently in uppercase. Single-field entry MUST retain the existing
  supported whitespace, no-space, lowercase, mixed-case, and surrounding-
  parentheses paste forms.
- **FR-011**: Single and split layouts MUST be projections of one Taipower
  draft. Switching layouts MUST NOT silently delete, reorder, round, or replace
  any operator-entered coordinate content.
- **FR-012**: A layout switch alone MUST NOT report a coordinate change to
  ATAK, alter the current resolved point, move the map, or invoke host
  confirmation.
- **FR-013**: When a raw draft cannot be represented safely in split fields,
  the plugin MUST retain single-field mode and the complete draft, show a
  localised validation state, and allow correction or clearing.
- **FR-014**: The plugin MUST remember the last selected Taipower entry layout
  independently of ATAK's native MGRS preference. Existing installations with
  no saved choice MUST default to single-field mode.
- **FR-015**: Host activation and Auto Fill MUST prepare the same canonical
  11-character Taipower value for both entry layouts without emitting a human
  change. Active-tab Clear MUST clear both projections of the Taipower draft.
- **FR-016**: Read-only mode MUST prevent text and precision changes. It MAY
  allow layout switching only as a non-mutating display projection.
- **FR-017**: The Taiwan system selector and both TWD zone selectors MUST use a
  36 dp visible track while each option retains a reachable target of at least
  48 dp.
- **FR-018**: Compact selector presentation MUST NOT reduce label legibility,
  logical focus order, screen-reader naming, read-only distinction, or
  reachability at supported pane sizes and font scales.
- **FR-019**: Address mode, candidate-selection, and locality-selection
  controls are outside the selector-compaction change and MUST retain their
  existing reachable target requirements.
- **FR-020**: Invalid Taipower input MUST remain unresolved and MUST NOT move
  the map, confirm a point, mutate another coordinate tab, or terminate ATAK.
- **FR-021**: Lifecycle disposal, locale replacement, or plugin reload MUST
  ignore late field and keyboard callbacks, preserve valid stored preferences,
  and leave a safe inert or replacement pane.
- **FR-022**: The single-field and split-field Taipower journeys, inline
  keyboard behavior, and selector presentation MUST use aligned English,
  Traditional Chinese (Taiwan), and Japanese labels, position-specific
  invalid-letter feedback, and accessibility text.
- **FR-023**: The feature MUST remain fully offline and MUST add no network,
  telemetry, or Android permission requirement.
- **FR-024**: Existing TWD97, TWD67, Address, host Clear, Copy, elevation,
  marker, and confirmation ownership MUST remain unchanged except for the
  shared inline-keyboard and selector-presentation requirements stated here.
- **FR-025**: A non-null host Auto Fill point MUST refresh Taipower, TWD97,
  TWD67, and Address from the same exact WGS84 point while retaining the active
  page and emitting no human-change callback. A null Clear point MUST remain
  active-page-only. Address reverse lookup MUST retain its asynchronous
  no-snap contract.

### Project-Wide Quality Requirements

- **QR-001 Compatibility**: The feature MUST retain ATAK-CIV 5.5.0 as the
  minimum supported runtime and ATAK-CIV 5.7.0.9 as the current acceptance
  line. Inline keyboard, split Taipower entry, selector reachability, read-only
  behavior, and dispose/reload behavior MUST be checked on both lines before a
  public compatibility claim is made.
- **QR-002 Host safety**: Malformed input, unavailable coordinate coverage,
  unsupported keyboard behavior, missing resources, focus changes, and
  lifecycle interruption MUST recover without terminating ATAK or dispatching
  a duplicate host action.
- **QR-003 UX and localisation**: The pane MUST retain one vertical scroll
  owner, keep every field and host-owned action reachable, provide meaningful
  labels and focus order, and maintain English, Traditional Chinese (Taiwan),
  and Japanese parity.
- **QR-004 Performance and offline operation**: Focus feedback and the visible
  keyboard MUST appear within 500 ms for at least 95% of attempts on the
  reference device. Layout switching and local validation feedback MUST appear
  within 100 ms for at least 95% of attempts and require no network access.
  Evidence MUST use the repeatable timing, percentile, and memory protocol
  defined in `plan.md` and `quickstart.md`.
- **QR-005 Geospatial correctness**: Taipower remains a TWD67 zone-121
  main-island grid. Accepted 9- and 11-character vectors MUST retain the
  existing 10 m and 1 m precision semantics, round-trip accuracy budgets, and
  deterministic locale-independent parsing. The A-H/A-E limits MUST be backed
  by provenance-recorded source evidence and boundary tests.
- **QR-006 Migration**: Existing valid Taipower input and formatted output
  remain compatible. The prior over-permissive A-I/A-J and A-F/A-J combinations
  are intentionally rejected as a correctness fix. Existing users start in
  single-field mode until they select and save another mode.
- **QR-007 Release evidence**: Public release remains blocked until current and
  minimum ATAK device evidence covers both orientations, supported font
  scales, default keyboards, both Taipower layouts, 9/11-character accuracy,
  A-H/A-E rejection boundaries, all-page Auto Fill, active-page Clear,
  selector reachability, read-only mode, reload/disposal, localisation,
  documentation, signer, and provenance. A successful build or TPP result
  alone is insufficient. A narrowed compatibility claim is permitted only
  after the user explicitly accepts it and the accepted scope and omitted
  evidence are recorded in release notes; unexecuted evidence MUST NOT be
  described as passed.

### Key Entities

- **Taipower Entry Draft**: One operator-editable coordinate draft containing
  the region letter, four subregion digits, two 100 m subgrid letters, optional
  10 m and 1 m digits, precision state, validation state, and a resolved
  location only when complete and valid.
- **Taipower Entry Layout**: The operator's single-field or split-field view of
  the same draft, including the last selected layout preference but no separate
  coordinate value.
- **Inline Entry Session**: The current focused field, logical next field,
  editability state, keyboard presentation expectation, and pane lifecycle
  generation needed to keep input inside the host flow safely.
- **Selector Presentation**: The visible track and larger reachable area for
  one Taiwan system or TWD zone option, including its selected, disabled, and
  accessibility states.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the reference current ATAK device using its default keyboard,
  100% of editable Taiwan fields keep the ATAK Go To dialog visible when
  focused in portrait and landscape across 20 repeated focus attempts per
  field.
- **SC-002**: Operators can enter and confirm each pinned 9-character and
  11-character Taipower vector successfully in both single and split layouts,
  with identical resolved coordinates for the two layouts.
- **SC-003**: Across 100 single-to-split-to-single round trips covering complete
  and representable partial drafts, zero characters,
  precision states, validation states, or resolved points change solely
  because of layout switching.
- **SC-004**: All 8 east-west 100 m letters A-H and all 5 north-south letters
  A-E pass boundary validation, while 100% of tested I/J east-west and F-J
  north-south combinations are rejected before host confirmation and produce
  the correct localized position-specific A-H or A-E feedback without silently
  deleting the attempted letter.
- **SC-005**: At least 95% of field-focus attempts display keyboard or focus
  feedback within 500 ms, and at least 95% of layout switches and validation
  updates appear within 100 ms on the reference device under the documented
  20-sample nearest-rank p95 measurement protocol.
- **SC-006**: The 36 dp visible system and zone tracks retain at least 48 dp
  reachable targets, show zero clipped labels, and leave all pane and
  ATAK-owned controls reachable at every supported orientation and font scale
  in the acceptance matrix.
- **SC-007**: Accepted Taipower golden vectors retain the established
  round-trip budgets: 9-character codes resolve within their 10 m cell
  semantics and 11-character codes within the promised 1 m representation,
  with no regression in TWD97, TWD67, or Address results.
- **SC-008**: On ATAK 5.5 and 5.7.0.9, malformed input, outer-island Auto Fill,
  read-only use, locale replacement, and 20 repeated plugin reload/dispose
  cycles produce zero ATAK crashes, zero duplicate confirmations, and zero
  stale-draft exposure.
- **SC-009**: English, Traditional Chinese (Taiwan), and Japanese acceptance
  runs show no missing, fallback, clipped, or mismatched strings for the new
  modes, hints, validation states, and accessibility labels.
- **SC-010**: Before public release, every device, compatibility,
  documentation, signer, and provenance release gate listed in QR-007 is either
  completed or, after explicit user acceptance, documented in release notes
  with the narrowed public claim and the omitted evidence.
- **SC-011**: For every tested starting page and main-/outer-island fixture,
  one non-null Auto Fill produces three coordinate drafts plus one Address
  request from the same WGS84 point, preserves the selected page, and emits
  zero human-change callbacks; null Clear still affects only the active page.

## Assumptions

- The supported runtime range remains ATAK-CIV 5.5.0 through the current pinned
  ATAK-CIV 5.7.0.9 line; Android and ATAK compatibility axes are unchanged.
- The reference current device remains the project-recorded Galaxy Tab S10+
  using its default system keyboard. The exact minimum-runtime device remains
  a separate release-gate resource.
- The default system keyboards in the named device matrix define acceptance
  for inline editing. Third-party keyboards may override editor presentation,
  but such behavior must not cause data loss, duplicate action, or host failure.
- The established Taipower main-island region table, TWD67 zone-121 datum,
  anchors, conversion model, golden vectors, and coverage limits remain
  authoritative except for correcting the two 100 m letter ranges.
- The east-west A-H and north-south A-E limits follow an 800 m by 500 m
  subregion divided into forty 100 m by 100 m cells, consistent with the
  accepted Taipower-grid source record. The plan will refresh the relevant ADR
  evidence before the behavior ships.
- Single-field mode remains the safe migration default because it is the only
  Taipower entry layout present before this feature.
- Draft text need not survive closing the ATAK Go To dialog; this feature
  preserves draft content across layout switches within the active pane and
  preserves only the selected layout preference across reopen or reload.
- The plugin continues to rely on ATAK's host-owned Go To confirmation,
  elevation, marker, Auto Fill, Clear, and Copy actions. This feature adds no
  second confirmation or navigation path.
