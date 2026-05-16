# ADR-0002: Render all three units in-plugin; do not integrate with TDAL

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: `/speckit-plan` on feature `001-tw-coord-display` (filed retroactively during `/speckit-analyze` on 2026-05-16 in response to analyze finding F3)

## Context

The reference plugin named in `spec.md` Assumptions
(`com.atakmap.android.bng.plugin`) is the ATAK **TDAL** (Tactical /
Tool Data Access Layer) plugin, per the user-supplied HackMD note
<https://hackmd.io/@Shihyu/H12BTT46xl>. TDAL is ATAK's built-in
mechanism for declaring custom Coordinate Reference Systems via an
XML file at `atak/tools/coordinate_systems/coordinate_systems.xml`;
once declared, ATAK itself renders the map-centre crosshair in the
chosen CRS.

The natural first instinct is therefore to integrate with TDAL and
let ATAK do the rendering. Two findings forced a different decision
during `/speckit-plan` (`research.md` R9):

1. The TDAL plugin and its `coordinate_systems.xml` schema are **not
   bundled** with the ATAK-CIV 5.7.0.3 SDK we have. Subagent
   exploration of the SDK turned up no `TDAL` / `coordinate_systems`
   references at all. Depending on TDAL would mean depending on a
   separately distributed component the end user may not have
   installed.
2. The Taipower grid is **not an EPSG CRS** and cannot be expressed
   in a TDAL XML. A TDAL-only design would cover at most two of the
   three required units (TWD97, TWD67) and force a second code path
   for Taipower.

## Decision

Render **all three units** through a single in-plugin
`TwPowerWidget` (a `MapWidget` subclass anchored in
`RootLayoutWidget.TOP_RIGHT`). Drive the widget from
`MapEvent.MAP_BOUNDS_CHANGED` (map centre) and `MapEvent.ITEM_CHANGED`
on `MapView.getSelfMarker()` (own position). Do not ship a
`coordinate_systems.xml`; do not assume the TDAL plugin is present.

## Alternatives considered

- **Hybrid: TWD97 / TWD67 via TDAL, Taipower via in-plugin widget.**
  Two readouts on screen (TDAL's at the crosshair, ours top-right)
  confuses the user and splits maintenance across two pipelines.
  Rejected.
- **Ship our own `coordinate_systems.xml` alongside the plugin and
  require the TDAL plugin as a hard dependency.** Adds an
  install-time burden on the user; deepens dependency surface;
  fragile across ATAK upgrades. Rejected.
- **Embed our own `coordinate_systems.xml` into the plugin assets
  and load it ourselves.** Re-implements TDAL inside our plugin
  without any TDAL benefit. Rejected.

## Consequences

**Positive:**

- Single code path for all three units (FR-001, FR-006).
- No deployment step for end users beyond installing the plugin
  (FR-013).
- Single place to apply localisation (FR-016, FR-017, FR-018),
  clipboard behaviour (FR-015), and offline lockdown (FR-019).
- Easier to test end-to-end on the JVM (Constitution Principle II).

**Negative:**

- The readout sits in the top-right corner rather than at the map
  crosshair, a small departure from where a TDAL-rendered readout
  would appear (the cross-hair). Mitigated by clear unit labelling
  (FR-012) and ADR-aware UI design (`docs/ui/readout-widget.md`).
- We do not get free TDAL features (e.g., declaring CRS bounds in
  XML); we re-implement bounds checking in `CoordinateConverter`.

## Links

- Spec: FR-001, FR-003, FR-013
- Plan: `research.md` R9
- Contracts: `contracts/widget-overlay.md`
- External: <https://hackmd.io/@Shihyu/H12BTT46xl>
