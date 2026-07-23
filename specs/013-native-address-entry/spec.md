# Feature Specification: Native Taiwan Address Entry

**Feature Branch**: `codex/013-native-address-entry`

**Created**: 2026-07-22

**Status**: Draft

**Input**: User description: "Add a fourth Address tab to the Taiwan pane in
ATAK's native Go To and Convert Coordinate experience. Let operators switch
between one full-address field and four structured address fields. Retire the
duplicate TW Coord GoTo and TW Addr Search Tools entries, move offline address
management under TW Coordinates, and leave TW Coordinates as the plugin's only
Tools entry."

## Scope

This feature makes offline Taiwan address search part of the native Taiwan
coordinate-entry experience that operators already use for Taipower, TWD97,
and TWD67. The Taiwan choice gains an Address tab with a full-address mode and
a structured mode. Both modes represent one shared address draft and resolve
through the operator's installed offline datasets.

The feature also completes the migration away from duplicate plugin pages.
`TW Coordinates` becomes the only public plugin entry in ATAK Tools. Offline
dataset import, status, replacement, and removal remain available from that
entry. The custom `TW Coord GoTo` and `TW Addr Search` workflows are retired,
including their plugin-specific marker, icon-palette, and recent-entry user
experiences.

Online geocoding, changes to coordinate transformation algorithms, changes to
address dataset formats, and adding a separate top-level ATAK Address format
are outside this feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Go to a full Taiwan address from native Go To (Priority: P1)

An ATAK operator receives a Taiwan street address. They open ATAK's native Go
To dialog, select Taiwan and then Address, enter the full address in one field,
review the resolved result, and confirm using ATAK's normal action controls.
They do not need to discover a separate plugin search page.

**Why this priority**: Bringing the primary address-to-location journey into
the familiar native dialog is the main operator value of this feature.

**Independent Test**: With one supported county dataset active, enter a pinned
full address in the native Address tab, select the resolved candidate when
needed, confirm, and verify that ATAK reaches the candidate's stored location.

**Acceptance Scenarios**:

1. **Given** at least one applicable offline address dataset is active,
   **When** the operator enters a complete address with one uniquely matching
   stored location, **Then** the Address tab displays the normalized address
   and prepares that location for the host action.
2. **Given** an address produces multiple credible candidates, **When** lookup
   completes, **Then** the operator must select one clearly labelled candidate
   before the address can be confirmed.
3. **Given** a candidate has been resolved, **When** the operator presses the
   host dialog's confirmation action, **Then** ATAK performs the same
   host-owned Go To behavior used by its built-in coordinate formats.
4. **Given** no candidate is selected or resolved, **When** the operator tries
   to confirm, **Then** the dialog remains open, no map action occurs, and
   corrective feedback remains visible.

---

### User Story 2 - Switch between full and structured address entry (Priority: P1)

An operator may have a complete pasted address or may prefer to enter known
parts separately. A mode-switch control changes the Address tab between one
full-address field and four logical fields for county/city, district/township,
road/locality, and the remaining lane/alley/number/floor portion. Switching
modes never discards the draft.

**Why this priority**: Taiwan address forms are variable. A full field is fast
for paste and radio transcription, while structured fields make ambiguous or
partially known addresses easier to correct.

**Independent Test**: Enter a full address containing a county, district,
road section, house number, and floor; switch to structured mode and back; then
verify that every original component remains present and resolves to the same
candidate.

**Acceptance Scenarios**:

1. **Given** a full address has recognizable county, district, road, and tail
   components, **When** the operator switches to structured mode, **Then** the
   four fields contain the corresponding components without losing any
   unrecognized text.
2. **Given** the operator edits one or more structured fields, **When** they
   return to full-address mode, **Then** the combined address reflects all
   edits in a deterministic order.
3. **Given** an address includes supported full-width characters, whitespace,
   `台`/`臺` variants, or Chinese numerals adjacent to address units, **When**
   it is normalized, **Then** equivalent forms can find the same stored
   address without changing proper names such as `八德路`.
4. **Given** part of an address cannot be safely classified, **When** modes are
   switched, **Then** that text remains visible and editable rather than being
   silently removed or assigned an invented meaning.

---

### User Story 3 - Inspect a supplied point in all Taiwan representations (Priority: P2)

An operator opens Convert Coordinate or another native location flow from a
map point and selects Taiwan. Taipower, TWD97, and TWD67 continue to represent
the supplied point immediately. When an applicable offline dataset is active,
the Address tab also shows the best available stored address for that point.

**Why this priority**: Address integration must extend the shared native pane
without regressing the already shipped all-coordinate-tab behavior or exposing
an address retained from a previous map point.

**Independent Test**: Open Convert Coordinate for two distinct pinned points
in sequence. For each point, inspect all four Taiwan tabs and verify that no
coordinate, address, candidate, or availability state comes from the previous
point.

**Acceptance Scenarios**:

1. **Given** a host flow supplies a point covered by active coordinate and
   address data, **When** Taiwan is activated, **Then** the three coordinate
   tabs represent that point and the Address tab fills with its best available
   stored address when lookup completes.
2. **Given** no applicable address dataset is active, **When** Taiwan is
   activated from a supplied point, **Then** all representable coordinate tabs
   remain usable while Address clearly reports that offline data is
   unavailable.
3. **Given** an address lookup for a prior point completes late, **When** a new
   point is already active, **Then** the late result is ignored and cannot
   replace the current address draft.
4. **Given** the host marks the flow read-only, **When** the operator opens any
   Taiwan tab, **Then** prepared values are visible but cannot change the host
   location.

---

### User Story 4 - Manage offline data through TW Coordinates (Priority: P2)

An operator opens ATAK Tools and sees only `TW Coordinates` for this plugin.
From that page they can inspect address dataset status and open the existing
offline data management experience to import, replace, or remove
county datasets.

**Why this priority**: Address entry is not useful without a discoverable way
to manage offline data, while four separate Tools entries create unnecessary
navigation and learning cost.

**Independent Test**: From a clean installation, open `TW Coordinates`, import
one county dataset, return to native Go To, and resolve an address
without using any other plugin Tools entry.

**Acceptance Scenarios**:

1. **Given** the plugin is enabled, **When** the operator opens ATAK Tools,
   **Then** exactly one plugin entry named `TW Coordinates` is visible.
2. **Given** no dataset is installed, **When** the operator opens
   `TW Coordinates`, **Then** the page reports the empty state and provides a
   clear path to offline dataset management.
3. **Given** the operator opens offline dataset management from
   `TW Coordinates`, **When** they import, replace, or remove a
   dataset, **Then** the established management outcomes remain available.
4. **Given** the operator returns to native Address entry after changing the
   active datasets, **When** a new lookup begins, **Then** it uses the current
   active dataset state without requiring an ATAK restart.

---

### User Story 5 - Upgrade without duplicate workflows or lost datasets (Priority: P3)

An existing operator upgrades from a version that exposed `TW Coord GoTo`,
`TW Addr Search`, `TW Offline Addr`, and `TW Coordinates`. Their installed
offline address datasets and relevant search settings remain usable, but the
three duplicate Tools entries and their retired pages no longer appear.

**Why this priority**: The migration should simplify navigation without
forcing large offline datasets to be re-imported or leaving broken shortcuts.

**Independent Test**: Upgrade an installation with multiple imported datasets
and non-default address settings. Verify that the datasets and settings remain
effective, only `TW Coordinates` appears in Tools, and native address lookup
works from the retained data.

**Acceptance Scenarios**:

1. **Given** an upgraded installation contains valid offline datasets,
   **When** the new plugin starts, **Then** those datasets remain installed,
   remain installed and are available to native Address lookup.
2. **Given** the former custom Go To or forward-search shortcut is invoked by
   stale external state, **When** the retired action is unavailable, **Then**
   ATAK remains stable and no partial retired page is displayed.
3. **Given** the operator had custom Go To Recent entries, marker choices, or
   icon choices, **When** they upgrade, **Then** the feature makes no promise to
   expose or migrate those retired user experiences, and their stored values
   cannot alter native Go To behavior.

### Edge Cases

- An address may omit a county, use a county alias, or match the same road name
  in several active counties.
- A county and district may be recognizable while the road or house-number
  tail is incomplete or unsupported.
- `台` and `臺`, full-width digits, punctuation, spaces, hyphenated subnumbers,
  and Chinese address-unit numerals may appear in pasted text.
- Chinese numeral characters may be part of a proper road name and must not be
  converted merely because they look numeric.
- A unique road-level result may still contain many house-number candidates;
  the nearest candidate must not be silently chosen as an exact address.
- A dataset may be removed, replaced, or fail validation while a
  native Address lookup is pending.
- Switching input mode or Taiwan tab repeatedly may occur while lookup is in
  progress.
- A second host point may replace the first before reverse lookup completes.
- Native Clear with no host point must affect only the active Taiwan tab; it
  must not delete other prepared coordinate drafts or offline data.
- A read-only or disposed pane may receive a late result and must remain
  unchanged.
- Long localized labels and address text must remain reachable in supported
  dialog sizes and font scales without covering ATAK's elevation and action
  controls.

### Failure & Recovery Scenarios

- **FS-001**: Given no applicable offline dataset exists, when the operator
  opens or searches the Address tab, then a localized empty state explains
  the requirement and offers a path to dataset management while the three
  coordinate tabs remain functional.
- **FS-002**: Given an address is malformed, incomplete, ambiguous, or absent
  from active data, when lookup completes, then no coordinate is returned and
  the operator can correct the draft or choose a candidate without reopening
  the host dialog.
- **FS-003**: Given a dataset or lookup failure occurs, when the operator
  retries after correcting dataset state, then a fresh lookup can succeed and
  no stale candidate is reused.
- **FS-004**: Given the plugin is unloaded, the pane is disposed, or the host
  flow changes during lookup, when late work completes, then it has no visible
  effect and no exception escapes into ATAK.
- **FS-005**: Given native Taiwan pane registration is unavailable on an
  unsupported host, when the plugin starts, then ATAK and `TW Coordinates`
  remain usable, no broken Taiwan choice is shown, and no removed legacy page
  is restored as an undocumented fallback.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The native Taiwan choice MUST provide exactly four mutually
  exclusive internal tabs: Taipower, TWD97, TWD67, and Address.
- **FR-002**: Address MUST be an internal Taiwan tab and MUST NOT create an
  additional top-level ATAK coordinate-format choice.
- **FR-003**: The Address tab MUST support a full-address mode containing one
  editable address field and a structured mode containing four logical fields:
  county/city, district/township, road/locality, and the remaining
  lane/alley/number/floor/room tail.
- **FR-004**: First use MUST show full-address mode. The operator MUST be able
  to switch modes with one clearly labelled, accessible control.
- **FR-005**: Both address modes MUST represent one shared draft; switching
  modes MUST preserve all recognized and unrecognized input and MUST NOT
  independently retain contradictory values.
- **FR-006**: Full-address normalization MUST treat supported full-width and
  half-width forms, whitespace and common punctuation, and `台`/`臺` variants
  as equivalent for lookup while retaining a readable normalized address.
- **FR-007**: Chinese numerals adjacent to supported address units MAY be
  normalized to digits, but numeral characters inside proper names MUST remain
  unchanged.
- **FR-008**: Address decomposition MUST prefer complete known county,
  district, and road/locality names over shorter overlapping names and MUST
  preserve any text that cannot be classified safely.
- **FR-009**: Lookup MUST use only installed, active offline address data and
  MUST NOT require or attempt an online geocoding service.
- **FR-010**: A unique exact address result MUST prepare its stored location
  for the host flow and display the normalized address to the operator. Exact
  matching MUST accept either canonical full-address equality or, when the
  operator omits a TGOS village/neighbourhood prefix, one unique candidate
  whose county/city, district/township, street/section, address tail, and
  unclassified suffix all match.
- **FR-011**: Multiple credible results MUST be presented for explicit
  operator selection with enough address context to distinguish them. The
  nearest result MUST NOT be silently treated as exact solely because it is
  nearest. Two or more candidates that differ only by an omitted
  village/neighbourhood prefix MUST remain unresolved until selection.
- **FR-012**: An address MUST remain unresolved until a unique result exists
  or the operator selects one result. Unresolved input MUST NOT be returned to
  the host, move the map, replace a point, or dismiss the dialog.
- **FR-013**: Editing any address field after resolution MUST invalidate the
  prior selection until the edited draft is resolved again.
- **FR-014**: The host dialog's existing confirmation action MUST remain the
  only action that completes native Go To or another host-owned location
  change; selecting a candidate only prepares the result.
- **FR-015**: A non-null host activation MUST continue preparing every
  representable coordinate tab from the supplied WGS84 point and MUST start an
  Address reverse lookup when applicable offline data is available.
- **FR-016**: Address reverse lookup MUST never delay, clear, or invalidate a
  successfully prepared Taipower, TWD97, or TWD67 draft.
- **FR-017**: Every non-null host activation MUST replace or clear the prior
  Address draft, candidates, resolved point, lookup state, and availability
  state before a result for the new activation is displayed. A null activation
  retains the active-tab-only Clear semantics in FR-019.
- **FR-018**: Late, cancelled, or superseded lookup results MUST NOT replace
  the current session's address or coordinate.
- **FR-019**: Native Clear MUST clear only the active internal Taiwan tab.
  Clearing Address MUST remove its fields, candidates, and resolved result but
  MUST NOT modify offline datasets.
- **FR-020**: Native Auto Fill on Address MUST resolve the host-provided point
  to the best available offline address. Auto Fill on coordinate tabs MUST
  retain its existing active-tab behavior.
- **FR-021**: Read-only host flows MUST display prepared Address results while
  preventing text edits, mode changes that mutate data, candidate changes, and
  any returned location change.
- **FR-022**: When no applicable dataset is active, Address MUST show a
  localized unavailable state and a discoverable path to offline dataset
  management without degrading the three coordinate tabs.
- **FR-023**: ATAK Tools MUST display exactly one public entry for this plugin,
  named `TW Coordinates`.
- **FR-024**: The public `TW Coord GoTo`, `TW Addr Search`, and
  `TW Offline Addr` Tools entries and their standalone user workflows MUST be
  removed.
- **FR-025**: `TW Coordinates` MUST display offline dataset status and provide
  access to dataset import, validation, replacement, and removal.
- **FR-026**: Existing valid offline datasets and applicable address-search
  preferences MUST remain usable after upgrade without re-import.
- **FR-027**: Retired custom Go To Recent entries, marker modes, and icon
  choices are not migration targets; they MUST NOT affect native coordinate or
  address behavior after upgrade.
- **FR-028**: Invocation of a stale retired action MUST be safely ignored or
  redirected to an active documented entry point without opening a partial
  legacy workflow or destabilizing ATAK.
- **FR-029**: All visible labels, hints, candidate states, normalization
  feedback, loading states, empty states, and errors introduced or changed by
  this feature MUST be available in English, Traditional Chinese (Taiwan), and
  Japanese.
- **FR-030**: Address fields and controls MUST follow the compact dimensions of
  the native DD pane and existing Taiwan coordinate rows, remain reachable in
  supported ATAK dialog sizes, and avoid covering host-owned elevation or
  action controls at supported font scales and orientations.
- **FR-031**: The address mode-switch and candidate controls SHOULD provide at
  least 48 dp touch targets and MUST have meaningful accessibility labels.
- **FR-032**: The feature MUST retain WGS84 as the host interchange location
  and MUST NOT change coordinate transformation constants, zones, coverage,
  precision, or published accuracy claims.
- **FR-033**: Address and coordinate entry MUST remain fully offline and MUST
  NOT add network permission, telemetry, or an online fallback.
- **FR-034**: The feature MUST support ATAK-CIV 5.5.0 and the current supported
  ATAK line; ATAK-CIV 5.4 and earlier remain unsupported.
- **FR-035**: Plugin enable, disable, reload, dialog activation, and lookup
  failures MUST be contained so that ATAK continues operating and no duplicate
  Taiwan choice or unusable residual Tools entry remains.
- **FR-036**: User documentation MUST describe the four-tab native workflow,
  the single `TW Coordinates` Tools entry, offline dataset management, address
  mode switching, candidate selection, and the removal of legacy entry points.

### Project-Wide Quality Requirements

- **QR-001 Compatibility**: Planning and release validation MUST distinguish
  Android compile SDK 36, Android minimum SDK 26, ATAK compile SDK 5.7.0.9,
  and ATAK minimum runtime 5.5.0. Native Address entry and the consolidated
  Tools workflow must be exercised on ATAK 5.5 and the current supported ATAK
  line before release.
- **QR-002 Host safety**: Registration, activation, lookup completion,
  candidate selection, formatting, read-only display, stale retired actions,
  and unload failures must remain contained within the plugin and must never
  terminate or destabilize ATAK.
- **QR-003 UX and localisation**: The four internal tabs, both address modes,
  candidate and dataset states, and the single Tools path must remain usable
  in field-sized dialogs and have aligned English, zh-TW, and Japanese text.
- **QR-004 Performance and offline operation**: Address normalization, mode
  switching, forward lookup, reverse lookup, and candidate display must meet
  the measurable latency outcomes while requiring no network access.
- **QR-005 Geospatial correctness**: Stored address locations are returned as
  canonical WGS84 points with their dataset provenance. Taipower, TWD97, and
  TWD67 retain their established coverage, zones, golden vectors, and error
  budgets.
- **QR-006 Migration**: Valid offline datasets and applicable address settings
  remain available without re-import. The three retired Tools workflows are
  intentionally not retained as fallback paths, and their UI-only state is not
  migrated into native entry.
- **QR-007 Release evidence**: Public release is blocked until current and
  minimum-runtime device journeys, latency evidence, offline behavior,
  localization, documentation screenshots, signer, dataset provenance, and
  release artifact provenance are complete or explicitly dispositioned
  without overstating compatibility.

### Key Entities

- **Address Draft**: The operator's full-address text, structured components,
  normalized representation, preserved unclassified text, edit mode, and
  validation state for one native entry session.
- **Address Candidate**: One offline stored address that may satisfy a draft,
  including sufficient display context, its canonical WGS84 point, match
  status, and source dataset identity.
- **Address Resolution**: The unique or operator-selected candidate currently
  prepared for the host flow. It becomes invalid after any draft edit or
  relevant dataset-state change.
- **Offline Dataset State**: The installed county datasets, their provenance,
  validity, and availability to native lookup and the management page.
- **Native Entry Session**: The host-supplied point, editability, active Taiwan
  tab, four tab drafts, current lookup generation, and prepared result for one
  native dialog activation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With an applicable dataset active, an operator familiar with
  native ATAK Go To can enter a complete Taiwan address and confirm a valid
  destination in under **30 seconds** without opening a separate plugin search
  page.
- **SC-002**: For a test corpus of at least **100** valid addresses covering
  county/city aliases, districts, road sections, lanes, house subnumbers, and
  floors, **100%** of supported equivalent input forms produce the same
  candidate set and no proper road name is changed by numeral normalization.
- **SC-003**: Across at least **100** full-to-structured-to-full round trips,
  **100%** of entered characters remain represented in the resulting draft,
  including text that cannot be classified.
- **SC-004**: Address normalization and input-mode switching update visible
  state within **100 ms** at p95 and worst-case on the reference device.
- **SC-005**: For representative active county datasets, forward and reverse
  address results become visible within **1,000 ms median** and
  **2,000 ms p95** across at least 100 measured lookups on the reference
  device, excluding time spent waiting for operator candidate selection.
- **SC-006**: Across **100** alternating activations for two distinct points,
  zero displayed addresses, candidates, resolved points, or availability
  states come from the preceding activation, including deliberately delayed
  lookup completions.
- **SC-007**: ATAK Tools shows exactly **one** plugin entry in English, zh-TW,
  and Japanese, and **100%** of offline dataset management actions previously
  available through `TW Offline Addr` remain reachable through
  `TW Coordinates`.
- **SC-008**: An upgrade fixture containing at least two installed county
  datasets retains **100%** of those datasets, and native Address lookup uses
  them without re-import.
- **SC-009**: The native full-address, structured-address, ambiguous-candidate,
  missing-dataset, Convert Coordinate, read-only, stale-result, and unload
  journeys complete without an uncaught plugin failure on ATAK-CIV **5.5.0**
  and the current supported ATAK line before public release.
- **SC-010**: The complete address and dataset-management journeys succeed in
  airplane mode with **zero** outbound network attempts and no new network
  permission.
- **SC-011**: **100%** of strings introduced or changed by this feature resolve
  in English, Traditional Chinese (Taiwan), and Japanese with no missing or
  mismatched format arguments.
- **SC-012**: All existing coordinate golden-vector, round-trip, native
  all-tab-prefill, Clear, Auto Fill, read-only, and lifecycle expectations pass
  without widened tolerances or changed coordinate behavior.

## Assumptions

- ATAK-CIV 5.5.0 remains the minimum supported runtime and ATAK-CIV 5.7.0.9
  remains the pinned compile SDK. This feature does not change the four
  compatibility axes established by project governance.
- The existing imported county address datasets, their stored WGS84 points,
  provenance records, active-dataset state, and management operations remain
  authoritative and do not require a new dataset format.
- The default Address experience is one full-address field. Structured entry
  is an editing aid, not a guarantee that every Taiwan address can be
  losslessly categorized into a universal postal schema.
- Complete addresses containing county/city and district provide the most
  deterministic first-release journey. Partial addresses may use explicit or
  unambiguous locality context; ambiguous locality always requires operator
  refinement or candidate selection.
- One top-level Taiwan choice with four internal tabs is the accepted product
  shape. Address is not added to the coordinate-system model or ATAK's global
  coordinate-display preference.
- ATAK owns dialog confirmation, elevation, marker creation, map movement, and
  other host-flow outcomes. Address selection supplies one prepared WGS84
  location and display address only.
- `TW Coordinates` remains a settings and navigation page. The established
  offline dataset manager may open as an internal subpage rather than placing
  its full import and progress experience directly inside the settings list.
- Removal of custom Go To marker affiliation, icon palette, and Recent entry
  experiences is accepted. Reintroducing them requires a separately specified
  feature.
- The planning phase will record an ADR because this feature reverses the
  earlier coexistence strategy and makes a material workflow and service-
  ownership change.
