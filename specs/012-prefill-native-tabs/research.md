# Phase 0 Research: Prefill All Native Taiwan Tabs

All technical questions required for planning are resolved.

## R1 — Treat the issue as controller state, not a new ATAK integration

**Decision**: Keep the existing `CoordinateEntryPane` registration and change
the session/controller behaviour reached by `onActivate`.

**Evidence**:

- The pinned ATAK-CIV 5.7.0.9 `main.jar` has SHA-256
  `8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.
  `javap -public` exposes
  `CoordinateEntryPane.onActivate(GeoPointMetaData, boolean)` and the existing
  pane contract.
- The earliest public 5.5 source tag, commit
  `0d22ae5da3918271a16ff7d7a85846b62dc04bb0`, shows
  [`CoordinateEntryCapability`](https://github.com/TAK-Product-Center/atak-civ/blob/0d22ae5da3918271a16ff7d7a85846b62dc04bb0/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryCapability.java#L173-L250)
  obtaining the current coordinate and passing it to the selected pane.
- The implemented pane already converts the host `GeoPointMetaData` to WGS84
  and delegates to `TaiwanEntryController.activate`.

**Rationale**: The host already supplies the correct point. Registration,
lifecycle, and caller identification are not the failure.

**Alternatives considered**: Add a second pane or intercept a Convert
Coordinate broadcast (rejected: duplicates a working public seam); inspect the
dialog title/caller (rejected: brittle and would leave shared flows
inconsistent).

## R2 — Prepare all systems eagerly on non-null activation

**Decision**: Convert the supplied WGS84 point to all three systems during
`activate(point, editable)` before rendering. `selectSystem` only changes the
active projection and reveals its prepared draft.

**Evidence**: Current `populateFromHost` clears and converts only `activeUnit`.
Current `selectSystem` validates whatever text happens to be retained for the
new unit and performs no conversion. Because the controller survives across
host dialogs, inactive drafts may be empty or belong to a previous point.

**Rationale**: Three pure in-memory conversions are small and deterministic;
eager preparation gives immediate switching and removes temporal dependence on
which internal system happened to be selected at activation.

**Alternatives considered**: Lazy conversion on first switch (rejected:
requires retaining host-point provenance and makes a human selection perform
unexpected conversion work); call `autofill` after every switch (rejected:
changes command semantics and still exposes stale state before the call).

## R3 — Stage an immutable three-draft snapshot and commit once

**Decision**: Build a `PreparationSnapshot` locally from one WGS84 source,
then replace all controller drafts in one commit. Ordinary `OutOfRange`
results create a cleared `UNREPRESENTABLE` draft for only that system. If an
unexpected runtime failure aborts preparation, replace the previous snapshot
with a fully cleared failure snapshot before the pane boundary logs/contains
the failure.

**Rationale**: Incremental field mutation can expose a mix of the previous and
current map items if a later conversion or render step fails. Atomic commit
proves that every retained field, zone, status, and resolved point shares one
activation source.

**Alternatives considered**: Clear all fields and refill sequentially
(rejected: observers can see partial state and exceptions can leave a partial
commit); retain the old snapshot on failure (rejected: a stale valid coordinate
could be returned for a new host activation).

## R4 — Give each system its own validation and resolved state

**Decision**: Model one draft per `CoordinateUnit`, including its field text,
zone where applicable, `Validation`, and optional resolved WGS84. Existing
`validation()` and `resolvedOrNull()` accessors project only the active draft.
Human edits update and validate only their target draft.

**Rationale**: A cleared inactive Taipower draft must remember
`UNREPRESENTABLE`; otherwise switching to it recomputes the empty text as
`EMPTY` and loses the correct feedback. Per-system state also prevents one
draft's resolved point from being reused after another system is selected.

**Alternatives considered**: Keep one global validation/resolved pair plus an
availability bitmap (rejected: duplicates state and complicates edit
transitions); re-run forward conversion on switch (rejected by R2).

## R5 — Preserve active-only host consumption and human notification

**Decision**: ATAK result, validation, Copy/format, and confirmation continue
to read only the active draft. Programmatic snapshot preparation does not call
the human-change listener or selection preference writer. A human system
selection still writes the preference and sends exactly one notification.

**Rationale**: Background preparation is presentation state, not operator
intent. ATAK owns the enclosing map/marker action and must not receive one
before an operator acts.

## R6 — Keep Clear and Auto Fill active-only

**Decision**:

| Command | Scope |
|---------|-------|
| `activate(non-null, editable)` | Atomically replace all three drafts |
| `activate(null, editable)` / native Clear | Clear only the active draft |
| `autofill(point)` | Replace only the active draft |
| Human system switch | Reveal prepared/retained draft; no forward conversion |

**Rationale**: ATAK uses `onActivate(null, editable)` for its native Clear
button, while Auto Fill is a separate explicit user command. Broadening either
would erase background drafts unexpectedly and contradict the shipped feature
011 behaviour.

## R7 — Reuse all geospatial rules unchanged

**Decision**: Call the existing `CoordinateConverter` once per unit. Do not
copy or change the Taiwan bounding box, TM2 central-meridian choice, TWD67
datum shift, Taipower coverage, rounding, or parser rules.

**Rationale**: The converter already returns independent `Ok`/`OutOfRange`
results and correctly rejects Taipower for zone 119. Existing national and
outer-island vectors remain the source of truth.

## R8 — No new ADR or migration

**Decision**: Record the fix in feature artifacts, native-entry UI/user docs,
and changelog. Do not create a new ADR unless implementation changes the
accepted SDK seam, external contract, persistence, or architecture.

**Rationale**: ADR-0023 already places session state in
`TaiwanEntryController`, and ADR-0024 already fixes the compile/minimum version
strategy. This feature corrects the breadth and atomicity of that session
state without superseding either decision.
