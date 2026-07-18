---
title: "ADR-0023: Integrate Taiwan Coordinates with ATAK Native Entry"
status: "Accepted"
date: "2026-07-17"
authors: "Project maintainers"
tags: ["architecture", "atak-sdk", "coordinate-entry", "compatibility"]
supersedes: ""
superseded_by: ""
---

# ADR-0023: Integrate Taiwan Coordinates with ATAK Native Entry

## Status

Accepted

## Context

The plugin already provides an advanced `TwCoordGotoReceiver` with marker modes,
Recent entries, and ATAK icon-palette delegation. Operators must nevertheless
learn a plugin-specific page for the common task of entering a Taiwan
coordinate. ATAK 5.5 exposes the public `CoordinateEntryPane` and
`CoordinateEntryCapability` extension seam used by its shared Go To and other
coordinate dialogs.

The integration must add Taiwan entry without replacing ATAK panes, changing
ATAK's global `CoordinateFormat`, mutating the advanced GoTo history, or making
the plugin unload unsafe. It must also remain loadable from ATAK 5.5 while the
current compile and device SDK is 5.7.0.9.

## Decision

Register one plugin-owned `TaiwanCoordinateEntryPane` with the stable UID
`com.atakmap.android.twcoord.coordinateentry.taiwan`. The pane contains a
segmented Taipower/TWD97/TWD67 selector. TWD systems expose explicit TM2 zones
121 and 119; TWD67 zone 119 shows the published lower-accuracy advisory.

Keep parsing, session state, and display formatting separate:

- `TaiwanEntryController` owns active-system drafts, validation, read-only state,
  host Auto Fill/Clear behaviour, and horizontal results.
- `TaiwanEntryFormatter` produces canonical, state-independent Copy text.
- `TaiwanCoordinateEntryPane` adapts the public ATAK callback contract and owns
  only plugin resources and Views.

`NativeCoordinateEntryRegistrar` is the sole lifecycle owner. It posts register,
unregister, and locale replacement work to the map UI thread; uses a generation
token to reject stale work; removes the exact pane instance it registered; and
disposes late callbacks idempotently. A locale change replaces a detached pane
immediately but defers replacement while ATAK has its View attached, avoiding
mutation of an open host dialog. The layout has one outer vertical scroll owner.

The advanced custom GoTo remains an independent, supported workflow. The custom
receiver is registered before native registration is attempted and follows its
existing teardown order. Native code owns only
`pref_native_entry_last_unit`; it does not read, migrate, or write any
`pref_goto_*` key.

## Host-boundary evidence

- Minimum metadata remains `ext.ATAK_VERSION = "5.5.0"` in
  `app/build.gradle:15`.
- The advanced receiver is established before native registration in
  `TwCoordMapComponent.java:448` and `TwCoordMapComponent.java:459`.
- UI-thread lifecycle and refresh entry points are in
  `NativeCoordinateEntryRegistrar.java:95`, `:102`, and `:114`; detached
  replacement is implemented at `:183`.
- Pane host callbacks are adapted at `TaiwanCoordinateEntryPane.java:254`,
  `:275`, and `:289`; listener fan-out is isolated at `:411`, and disposal
  proceeds step-by-step through the narrow boundary at `:440`.
- Disposal catches ordinary failures and only the documented
  `NoClassDefFoundError`/`NoSuchMethodError` version-skew cases at
  `NativeCoordinateEntryRegistrar.java:260`. Fatal JVM errors are not swallowed.
- Component teardown asks the registrar to unregister before removing the
  advanced receiver at `TwCoordMapComponent.java:719`.

## Compatibility decision

ADR-0022 sets ATAK 5.5.0 as the minimum declared runtime. ADR-0024 selects the
exact ATAK-CIV 5.7.0.9 SDK for compilation and current-device validation. Public
ATAK 5.5.1.1 source anchors the implementation API, but it is not a substitute
for an exact ATAK 5.5 runtime test. The 5.5 physical compatibility matrix remains
a release gate and no successful 5.7.0.9 build may be reported as 5.5 device
proof.

## Consequences

### Positive

- Common Taiwan coordinate entry uses ATAK's familiar Go To controls.
- The same pane can render in other ATAK coordinate dialogs and honours their
  editable/read-only state.
- Advanced marker, Recent, and icon-palette workflows remain available.
- Registration or version-skew failure degrades to the existing custom page.
- No reflection or private ATAK API bridge is introduced.

### Negative

- ATAK controls the surrounding dialog and clipboard/action behaviour, so those
  paths require device tests rather than JVM-only proof.
- Locale replacement must wait until an attached pane is detached.
- Exact ATAK 5.5 install, lifecycle, and journey evidence is still required
  before release.

## Alternatives considered

### Add three top-level panes

Rejected because it crowds ATAK's native tab strip and separates three formats
that operators understand as one Taiwan-coordinate family.

### Replace the advanced custom GoTo page

Rejected because ATAK's shared dialog does not provide the plugin's marker-mode,
Recent, and icon-palette workflow.

### Extend ATAK `CoordinateFormat`

Rejected because the requirement is coordinate entry, not a core-wide format
preference. It would increase coupling and affect unrelated host surfaces.

## References

- `specs/011-native-coordinate-entry/spec.md` — FR-001, FR-017, FR-019,
  FR-024, FR-027
- `docs/adr/0022-set-minimum-atak-runtime-to-5-5.md`
- `docs/adr/0024-use-atak-5-7-0-9-compile-sdk.md`
- `docs/ui/native-taiwan-coordinate-entry.md`
