# Contract: Native Taiwan Activation Prefill

## Scope

This contract refines the shipped Taiwan pane's handling of
`CoordinateEntryPane.onActivate`. Registration, UID, view ownership, Clear,
Auto Fill, formatting, disposal, and ATAK result ownership otherwise retain
the feature 011 contracts.

## Non-null activation

Given a non-null `GeoPointMetaData`, the pane must:

1. Convert its horizontal coordinate to one canonical WGS84 point.
2. Ask the controller to prepare Taipower, TWD97, and TWD67 from that point.
3. Attempt each system independently using the existing converter.
4. Represent ordinary per-system out-of-range as cleared
   `UNREPRESENTABLE`, without discarding valid sibling drafts.
5. Atomically replace all previous fields, zones, validation states, and
   resolved points before rendering.
6. Preserve the selected internal system and render its prepared state.
7. Suppress human-change notification and preference writes.

For a main-island point, all three systems are expected to be valid. For a
zone-119 point, both TWD systems are expected to select zone 119; Taipower is
expected to be unavailable unless existing converter rules say otherwise.

## System switching

After non-null activation, selecting an internal system:

- reveals that system's already prepared draft;
- projects only that draft's validation and resolved point;
- performs no host-point forward conversion;
- does not alter any sibling draft;
- retains the existing one preference write and one human-change callback for
  a permitted human selection.

## Active-only operations

| Operation | Required source/scope |
|-----------|-----------------------|
| `getGeoPointMetaData()` | Active draft only |
| `format(point)` / native Copy | Active system only; pure |
| Human field/zone edit | Target/visible draft only |
| `onActivate(null, editable)` / native Clear | Clear active draft only |
| `autofill(point)` | Replace active draft only |

Background drafts must never directly pan the map, create/update a marker,
confirm the dialog, request elevation, or provide a result to ATAK.

## Atomicity and failure

- No render or accessor may observe a mix of snapshot generations.
- An ordinary unavailable result affects only its system.
- An unexpected preparation failure must invalidate all old drafts before it
  is logged/contained by the pane boundary; retaining a previous valid result
  is forbidden.
- Rendering failure must not fabricate or return a coordinate.
- Disposal makes later activation/switch/result callbacks inert or checked
  failures under the existing pane contract.

## Read-only activation

Read-only activation performs the same programmatic three-system preparation.
Every human field, zone, and internal-system mutation remains blocked. The
selected prepared draft may still be read/formatted according to the host's
existing contract.

## Compatibility

- No method outside the public ATAK 5.5-family `CoordinateEntryPane` contract
  is introduced.
- Android compile/minimum remain API 36/26.
- ATAK compile/minimum remain 5.7.0.9/5.5.0.
- No layout, resource key, permission, storage schema, or third-party
  dependency change is required.
