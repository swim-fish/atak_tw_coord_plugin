# Feature Specification: Taiwan Coordinate Display Plugin for ATAK

**Feature Branch**: `001-tw-coord-display`

**Created**: 2026-05-16

**Status**: Draft

**Input**: User description: "ATAK Android plugin that displays the map-centre coordinate and the user's current position coordinate. A settings page lets the user choose the display unit between Taipower grid, TWD97 and TWD67. Functionally analogous to the BNG (British National Grid) ATAK plugin. References: pwa_map repo for Taipower / TWD97 / TWD67 conversion formulas; ATAK-CIV 5.7.0.3 SDK and the `meshtastic_atak` sample for plugin scaffolding."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View map-centre coordinate in the chosen Taiwan unit (Priority: P1)

A field operator working in Taiwan opens ATAK with this plugin installed. As the
operator pans, pinches, and rotates the map, a persistent on-screen readout
shows the map centre's coordinate in the currently selected Taiwan unit
(Taipower grid, TWD97, or TWD67). The value updates without perceptible delay
so the operator can correlate the map with paper maps, utility records, or
field instructions that use a Taiwan-specific grid.

**Why this priority**: This is the headline capability. Without a continuously
updating map-centre readout, the plugin offers no value beyond what ATAK
already provides for lat/lon. It is also the simplest user journey to ship
independently and validates the entire coordinate pipeline (projection,
formatting, on-map overlay).

**Independent Test**: Install the plugin on a clean ATAK build, open the map
anywhere in Taiwan, observe the readout. Pan the map; verify the readout
follows the centre crosshair within 100 ms. Toggle each of the three units
from settings (covered by US3) and confirm the value reformats correctly.

**Acceptance Scenarios**:

1. **Given** the plugin is installed and the map is centred on a location
   inside Taiwan, **When** the user opens the map view, **Then** a readout
   overlay displays the map-centre coordinate in the currently selected unit
   with appropriate precision for that unit.
2. **Given** the map-centre readout is visible, **When** the user pans the
   map, **Then** the readout updates continuously to reflect the new map
   centre, with no perceptible lag (≤ 100 ms median update latency).
3. **Given** the map is centred outside Taiwan or outside the valid domain of
   the selected coordinate system, **When** the user views the readout,
   **Then** the readout shows an explicit "out of range" indicator (not stale
   or wrong values) and includes the underlying WGS84 lat/lon as a fallback.

---

### User Story 2 - View own position coordinate in the chosen Taiwan unit (Priority: P1)

The same operator wants to read out their own GPS-derived position in the
selected Taiwan unit so they can radio it to a partner, write it on a paper
form, or cross-reference an inventory list keyed by Taipower grid.

**Why this priority**: Equally important to US1 for field workflows; some
users will primarily use one and not the other, but together they form the
minimum viable feature pair. Independent from US1 in code path (different
source of latitude/longitude) but shares the same conversion / formatting
pipeline.

**Independent Test**: With GPS available, observe the "my position" readout
shows the device's WGS84 location converted to the active unit. Disable
location services; verify the readout shows an explicit "no fix" state
instead of stale values.

**Acceptance Scenarios**:

1. **Given** the device has a valid location fix inside Taiwan, **When** the
   user views the plugin, **Then** the user's position is shown in the
   currently selected unit, refreshed at least once per second while the
   fix is valid.
2. **Given** the device has no location fix or the fix is stale beyond a
   configurable threshold, **When** the user views the plugin, **Then** the
   readout shows a "no fix" state and does not display a numeric value.
3. **Given** the user's position is outside Taiwan, **When** the user views
   the plugin, **Then** the readout shows the same "out of range" treatment
   as US1 with WGS84 fallback.

---

### User Story 3 - Switch coordinate unit from a settings page (Priority: P2)

The operator opens the plugin's settings page and selects which of the three
units (Taipower grid, TWD97, TWD67) to display. The choice applies live to
both readouts (map centre and own position) and persists across app
restarts.

**Why this priority**: The plugin still has user value with a single hard-
coded default unit, but the unit selector is what turns it from a "TWD97
plugin" into a "Taiwan coordinate" plugin and is the differentiator the user
explicitly asked for. It is a small slice once US1 and US2 are in place.

**Independent Test**: Open settings, change the unit, return to the map; both
readouts reformat without restart. Force-stop the app, relaunch; verify the
previously selected unit is restored.

**Acceptance Scenarios**:

1. **Given** the plugin is installed, **When** the user opens the settings
   page, **Then** a single-select control lists exactly three options:
   Taipower grid, TWD97, TWD67, with the current selection highlighted.
2. **Given** the user changes the selected unit, **When** they return to the
   map, **Then** both the map-centre readout and the own-position readout
   immediately display the new unit; no app restart is required.
3. **Given** the user has previously selected a unit, **When** they relaunch
   ATAK, **Then** the previously selected unit is the active unit on first
   read of the readouts.

---

### Edge Cases

- **Outside Taiwan**: All three Taiwan-specific coordinate systems are valid
  only within or near Taiwan's territorial extent. The readout MUST display
  an explicit "out of range" state and fall back to WGS84, never silently
  show numerically valid but geographically nonsensical results.
- **No GPS fix / stale fix**: The own-position readout MUST distinguish
  between "no fix" and a valid fix. Stale fixes older than a configurable
  threshold (default: 10 s) MUST be treated as no fix.
- **Map heavily zoomed in/out**: Map-centre readout MUST remain meaningful
  at all zoom levels supported by ATAK; precision MUST NOT be artificially
  inflated by additional digits that are not justified by the underlying
  GPS / map accuracy.
- **Unit switching during pan**: Changing the unit MUST not lose or freeze
  the readout; the next frame should already render the new unit.
- **Long values overlap UI**: Each unit has a maximum string length;
  the overlay must accommodate the widest expected value without clipping
  or overlapping other ATAK widgets.
- **Plugin enabled but no permission for location**: Map-centre readout MUST
  remain functional; the own-position readout shows "no permission" with a
  one-tap shortcut to the OS permission screen.
- **Coordinate copy to clipboard** (if exposed): The clipboard value MUST
  match exactly what is displayed (same unit, same precision, no extra
  whitespace).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The plugin MUST display the current map-centre coordinate as a
  persistent on-map readout while the ATAK map is visible.
- **FR-002**: The plugin MUST display the device's current location coordinate
  as a persistent readout while location services are enabled and the plugin
  is active.
- **FR-003**: The plugin MUST support three display units: Taipower grid
  coordinates, TWD97 (TM2 / 121° central meridian), and TWD67 (TM2 / 121°
  central meridian), selectable independently.
- **FR-004**: The plugin MUST provide a settings page accessible from ATAK's
  plugin menu where the user can switch among the three units.
- **FR-005**: The user's selected unit MUST persist across app and device
  restarts.
- **FR-006**: Unit changes MUST take effect immediately on all visible
  readouts without requiring an app restart.
- **FR-007**: The map-centre readout MUST update with median latency ≤ 100 ms
  after the map centre changes (pan, pinch, rotate, programmatic recentre).
- **FR-008**: The own-position readout MUST update at least once per second
  while a valid location fix is available.
- **FR-009**: When a coordinate falls outside the valid domain of the
  selected unit, the readout MUST show an explicit "out of range" state and
  expose the underlying WGS84 lat/lon as a fallback line. The system MUST
  NOT silently emit numerically valid but geographically meaningless values.
- **FR-010**: When location services are unavailable or the latest fix is
  stale beyond the configured threshold (default 10 s), the own-position
  readout MUST show a distinct "no fix" state and MUST NOT display a numeric
  value.
- **FR-011**: Each unit MUST be displayed with a precision appropriate to
  the underlying data source: Taipower grid to the nearest 1 m; TWD97/TWD67
  to the nearest 1 m for easting/northing.
- **FR-012**: The readout MUST visually identify which unit is currently
  displayed (label or short prefix) so the user is never ambiguous about
  what numbers they are seeing.
- **FR-013**: The plugin MUST be packaged and loadable as a standard ATAK
  plugin on ATAK-CIV 5.7.0.3 and compatible patch releases.
- **FR-014**: The plugin MUST function entirely offline; coordinate
  conversion MUST NOT require network access.
- **FR-015**: Tapping the readout SHOULD copy the displayed value to the
  Android clipboard, with a brief visual confirmation toast.

### Key Entities *(include if feature involves data)*

- **Coordinate Unit**: An enumerated choice the user selects among Taipower
  grid, TWD97, and TWD67. Drives formatting and conversion behaviour.
- **Coordinate Snapshot**: A timestamped tuple of (WGS84 latitude,
  WGS84 longitude, validity flag, source). Source is either "map centre" or
  "device location". The snapshot is the immutable input to the formatter.
- **Display Formatter Output**: A short, human-readable string (with optional
  unit label) plus a status flag ("ok", "out of range", "no fix",
  "no permission") that the on-map overlay renders verbatim.
- **User Preference**: A persisted record holding the user's selected
  Coordinate Unit (and any future display preferences such as decimal
  precision, label visibility, refresh rate).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A field user can read their own position in the selected
  Taiwan unit within 5 seconds of opening ATAK with the plugin installed,
  starting from a cold launch and an available GPS fix.
- **SC-002**: 95 % of map-centre readout updates after a pan gesture arrive
  on screen within 100 ms; no update takes longer than 250 ms under normal
  device load.
- **SC-003**: A user switching the display unit from settings sees both
  readouts in the new unit on the very next visible frame after returning
  to the map (no perceptible flicker, no missing/stale values).
- **SC-004**: When the map or device location falls outside Taiwan, 100 %
  of readouts visibly indicate "out of range" — zero instances of silently
  displayed wrong-but-numeric values in acceptance testing.
- **SC-005**: Coordinate conversions for any test point inside Taiwan agree
  with the pwa_map reference implementation to within 1 m for each of the
  three units.
- **SC-006**: A user can complete the round-trip "open settings → change
  unit → confirm change visible on map" in under 10 seconds.
- **SC-007**: The plugin runs without measurable impact on ATAK frame rate
  (≤ 1 fps median drop versus a baseline ATAK install) on the reference
  device.

## Assumptions

- The plugin's target ATAK runtime is ATAK-CIV 5.7.0.3 and compatible patch
  releases; broader-version support is out of scope for v1.
- The default coordinate unit on first launch is TWD97, the modern Taiwan
  national standard.
- "Taipower grid" refers to the grid system used by Taiwan Power Company on
  their utility-asset and field-survey maps; the canonical conversion
  formulas live in the `pwa_map` reference repository and are the source of
  truth for v1.
- The "out of range" fallback line uses WGS84 lat/lon (degrees, 6 decimal
  places) because lat/lon is always defined globally and is the ATAK native
  reference.
- The own-position readout uses ATAK's existing self-marker location stream;
  this plugin does not request its own location permission or duplicate the
  GPS pipeline.
- The plugin's settings UI follows ATAK's plugin settings conventions
  (preference fragment) rather than a bespoke screen, in line with
  constitution Principle III (UX consistency).
- A reference plugin similar in scope is the public BNG plugin
  (`com.atakmap.android.bng.plugin`); its UX (HUD readout + settings entry)
  is the implicit visual / interaction baseline.
- The pwa_map repository (`C:\Users\hhhnr\source\repos\pwa_map`) and the
  ATAK SDK / `meshtastic_atak` sample are accessible at planning time for
  reference but are not run-time dependencies of the shipped plugin.
