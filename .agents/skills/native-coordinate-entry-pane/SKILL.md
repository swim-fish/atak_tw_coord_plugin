---
name: native-coordinate-entry-pane
description: Plan, implement, or debug the Taiwan CoordinateEntryPane integrated into ATAK native Go To/Convert Coordinates. Use for CoordinateEntryCapability, all-tab prefill, DD-sized fields, active-only Clear/Auto Fill, read-only/dispose lifecycle, or ATAK 5.5 compatibility.
---

# Native Taiwan Coordinate Entry Pane

Read ADR-0022 through ADR-0024, `docs/ui/native-taiwan-coordinate-entry.md`, and
the active feature artifacts before editing.

- Keep the public `CoordinateEntryPane` / `CoordinateEntryCapability` seam and
  register/unregister on the ATAK UI thread.
- Treat DD as the host layout reference: compact field height, no overlap with
  host elevation/marker controls, and at least 48 dp selector hit targets.
- On activation with a host point, prepare Taipower, TWD97, and TWD67 drafts
  atomically. Out-of-range systems show their safe invalid state; switching tabs
  must not expose stale values.
- Host Auto Fill and Clear affect only the active tab and emit only the active
  draft. Read-only mode, failed conversion, disposal, and late callbacks must
  remain safe and deterministic.
- Add JVM regression tests before behavior changes. Verify public SDK signatures
  against 5.7.0.9 and retain exact ATAK 5.5 physical-device evidence as a
  separate `[RELEASE-GATE]`.
