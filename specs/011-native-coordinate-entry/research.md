# Phase 0 Research: Native Taiwan Coordinate Entry

All Phase 0 research questions are resolved. Product choices were fixed by the
specification; this document records the API evidence and technical decisions
needed to implement them.

## R1 — Use ATAK's public coordinate-entry extension seam directly

**Decision**: Implement `CoordinateEntryPane` and register the instance through
`CoordinateEntryCapability.getInstance(mapView.getContext()).registerPane(...)`.
Unregister the same instance on component teardown. Do not use reflection or
modify ATAK core.

**Evidence**:

- The earliest available 5.5 source tag (`5.5.1.1`, commit
  `0d22ae5da3918271a16ff7d7a85846b62dc04bb0`) exposes the complete public
  [pane interface](https://github.com/TAK-Product-Center/atak-civ/blob/0d22ae5da3918271a16ff7d7a85846b62dc04bb0/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryPane.java)
  and public [register/unregister methods](https://github.com/TAK-Product-Center/atak-civ/blob/0d22ae5da3918271a16ff7d7a85846b62dc04bb0/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryCapability.java#L173-L250).
- The local ATAK 5.5.1.10 source at commit
  `9f6893dd657feacc35ec5de03dad721c2e44170e` registers all built-in panes
  through the same method, attaches `pane.getView()`, calls `onActivate`, and
  delegates native Auto Fill, Clear, and Copy to the active pane.
- `javap -public` against the pinned ATAK-CIV 5.7.0.9 `main.jar` confirms the
  same eight pane methods plus public `getInstance`, `registerPane`, and
  `unregisterPane`. The inspected jar SHA-256 is
  `8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.

The repository has no 5.5.0 source tag or SDK. The `5.5.0` value accepted in
ADR-0022 is the plugin compatibility token, while the earliest inspected app
source is 5.5.1.1. The plan therefore treats exact 5.5.0 binary/API validation
as pending: it must be obtained and tested, or the accepted minimum must be
revised before release.

### T001 initial execution status — BLOCKED (2026-07-17)

The implementation run repeated the minimum-runtime audit before making any
source or Android resource change:

```powershell
$takWorkspace = $env:TAK_WORKSPACE
$atakSource = Join-Path $takWorkspace 'atak-civ'
rg --files $takWorkspace |
    Select-String '5[._-]5[._-]0|main\.jar$|\.apk$'
git -C $atakSource tag -l '5.5*'
git ls-remote --tags https://github.com/TAK-Product-Center/atak-civ.git 'refs/tags/5.5*'
adb devices -l
```

Results:

- Local ATAK SDKs are 5.7.0.3, 5.7.0.5, and 5.7.0.9. Local downloaded ATAK
  APKs are 5.6.0.18, 5.6.0.20, and 5.7.0.9. No exact 5.5.0 SDK, `main.jar`,
  APK, or archive was found in the inspected source/download locations.
- The local clone and the official remote tag listing both begin the 5.5 line
  at `5.5.1.1`; neither exposes an exact 5.5.0 tag.
- `adb devices -l` reported no connected runtime, so physical 5.5.0 evidence
  could not be collected.
- Exact-artifact SHA-256 and method signatures are unavailable because no exact
  artifact was found. The separately recorded 5.7.0.3 hash/signatures and
  5.5.1.1 source remain valid but do not satisfy T001.

### T001 implementation-gate resolution — PASS with split version axes

The user selected a locally configured ATAK-CIV 5.7.0.9 SDK as the replacement
baseline. Its path is supplied outside Git through `ATAK_SDK_5_7_0_9`. The
connected reference device supplies the matching runtime evidence:

```powershell
$atakSdk = $env:ATAK_SDK_5_7_0_9
$jar = Join-Path $atakSdk 'main.jar'
Get-FileHash $jar -Algorithm SHA256
javap -classpath $jar -public com.atakmap.android.gui.coordinateentry.CoordinateEntryPane
javap -classpath $jar -public com.atakmap.android.gui.coordinateentry.CoordinateEntryCapability
adb -s <DEVICE_SERIAL> shell dumpsys package com.atakmap.app.civ
```

- SDK `main.jar` SHA-256:
  `8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.
- Runtime: ATAK-CIV `5.7.0.9 (7a0f6f29)`, `versionCode=1782294331`, on
  `SM-X826B` (`<DEVICE_SERIAL>`).
- `javap -public` exposes all eight `CoordinateEntryPane` callbacks plus public
  `CoordinateEntryCapability.getInstance`, `registerPane`, and
  `unregisterPane`.

ADR-0024 adopts ATAK-CIV 5.7.0.9 as the compile/current-device SDK while
retaining ADR-0022's `5.5.0` runtime compatibility token. The earliest public
5.5.1.1 source is accepted as the implementation-time 5.5 family API anchor.
This unblocks T002, but exact 5.5 physical-runtime journeys remain an explicit
release gate and must not be inferred from 5.7.0.9 evidence.

**Rationale**: This is the supported SDK hook, anchored to public 5.5.1.1
source and exact 5.7.0.9 compile/runtime evidence without a reflection bridge.

**Alternatives considered**: Fork ATAK core (rejected: deployment and upgrade
coupling); reflection (rejected: unnecessary and unverified); continue only the
custom page (rejected: does not reduce native Go To learning cost).

## R2 — Register one Taiwan pane with three internal systems

**Decision**: Use stable UID
`com.atakmap.android.twcoord.coordinateentry.taiwan` and one localised host tab
name. Inside it, expose mutually exclusive Taipower, TWD97, and TWD67 modes.

**Rationale**: ATAK already shows MGRS, DD, DM, DMS, UTM, and Address panes.
Three more top-level entries would crowd both the portrait horizontal tab strip
and landscape vertical strip. One Taiwan concept keeps discovery simple while
the always-visible internal selector prevents datum ambiguity.

**Alternatives considered**: Three top-level panes (rejected: tab crowding and
three lifecycle objects); Taipower-only native entry (rejected: field users
still need TWD97/TWD67); global `CoordinateFormat` extension (rejected: outside
this public pane seam and out of scope).

## R3 — Reuse the coordinate engine through a host-independent controller

**Decision**: `TaiwanEntryController` owns the active system, field drafts,
zone, validation state, and pure formatting. It delegates inverse conversion to
the existing public `CoordinateParser` and forward conversion to
`CoordinateConverter`. It does not copy projection constants, coverage boxes,
Taipower tables, or datum shifts.

Use a dedicated `TaiwanEntryFormatter` adapter for native Copy/format. It always
emits the selected system, E/N axes, metre units, and explicit zone 119/121 for
TWD values. It consumes existing conversion results but does not reuse or alter
the on-map `Formatter`, whose intentional output omits zone 121 and axis labels.

**Rationale**: The current parser already accepts all shipped Taipower forms and
explicit TWD zones; the converter already selects zone 119/121 and exposes
out-of-range results. A controller makes host callback semantics testable on
the JVM while preserving the authoritative algorithms.

**Alternatives considered**: Reuse `TwCoordGotoView` wholesale (rejected: it
owns a nested scroll view, submit/pan, marker modes, icon picker, and Recent
state); duplicate conversion logic in the pane (rejected: accuracy drift).

## R4 — Make registration idempotent, UI-thread confined, and reversible

**Decision**: `NativeCoordinateEntryRegistrar` is a small state machine with
`start()`, `refreshLocale()`, and `stop()`. It creates at most one pane, performs
capability mutations on the map/UI thread, rolls back a partial registration on
failure, and unregisters before disposing. A package-private registry gateway
allows a fake registry to drive 100-cycle JVM lifecycle tests.

**Rationale**: ATAK's implementation mutates views during registration and
posts removal work during unregister. A stable instance plus explicit state
prevents duplicate UID registrations and races between queued start and stop.

**Open-dialog teardown rule**: ATAK 5.5 unregisters the pane from its map and
tab strip but may still hold the formerly active object until an already-open
dialog closes. Therefore `dispose()` must be idempotent and make the pane inert
without invalidating its returned `View`: subsequent callbacks return no point
or a contained `CoordinateException`, never an unchecked failure. This is safer
than trying to close a host-owned dialog through a non-public API.

**Alternatives considered**: Register directly from several component paths
(rejected: duplicate risk); create a fresh pane on every dialog (rejected: the
capability is global, not per-dialog); force-close ATAK dialogs (rejected: no
public ownership seam).

## R5 — Implement ATAK callback semantics exactly

**Decision**:

| Callback | Required behaviour |
|----------|--------------------|
| `getUID()` | Return the stable namespaced UID for the lifetime of every instance |
| `getName()` | Return the current localised Taiwan label used when ATAK creates its tab |
| `getView()` | Return the same plugin-owned root view; never create a new view per call |
| `onActivate(point, editable)` | Suppress human-change notifications; apply editability; clear on null, otherwise forward-convert the supplied horizontal point into the selected system |
| `getGeoPointMetaData()` | Parse the active draft; return a new horizontal WGS84 `GeoPointMetaData` only when valid; otherwise throw a localised `CoordinateException` |
| `autofill(point)` | Replace the active draft completely; clear on null/unrepresentable point; show inline corrective state; do not notify as a human edit |
| `format(point)` | Purely format the supplied point in the active system without mutating fields; return null when absent/unrepresentable; always include a TM2 zone for TWD values |
| `setOnChangedListener(listener)` | Retain at most one listener; invoke it only after human edits, with re-entrancy and exception containment |
| `dispose()` | Detach listeners, mark inert, and remain safe under late host callbacks |

**Rationale**: These behaviours follow the 5.5 interface Javadoc and observed
host implementation. ATAK uses `getGeoPointMetaData()` while switching panes
and confirming, calls `autofill()` for its native button, calls
`onActivate(null, editable)` for Clear, and implements Copy as
`format(getGeoPointMetaData())`.

## R6 — Keep the host in charge of actions and altitude

**Decision**: Return only WGS84 latitude/longitude wrapped in
`GeoPointMetaData`. Do not pan, create a marker, assign affiliation, alter
elevation, or broadcast a plugin action from the native pane.

**Rationale**: ATAK's positive-button path creates the returned host point,
copies metadata, applies its elevation controls, marks the result user-entered,
and invokes the opening flow's callback. Repeating any of that would cause
double actions and inconsistent behaviour among Go To, point details, route,
sensor, drawing, and range-and-bearing consumers.

## R7 — Use one compact pane-owned vertical scroll view

**Decision**: Build one root `ScrollView` with one vertical child containing the
system selector, one active field group, zone selector where applicable, and
one inline advisory/error area. This is the pane's only vertical scroll owner;
no nested scroll container is allowed. Field geometry mirrors ATAK's DD pane:
compact horizontal label/input/unit rows, native underline inputs at
`wrap_content` height, 13 sp normal / 17 sp large title text, and a 2 dp top
inset. System and zone selectors are bounded to 48 dp, card-style input
backgrounds and fixed vertical field padding are omitted, and an empty status
area is `GONE`. Controls use numeric keyboards for TM2 fields, descriptive
labels/content descriptions, and visible read-only state.

**Rationale**: ATAK 5.5's `coordinate_panel.xml` places `currentCoordPane` in a
plain `FrameLayout`; only the tab strips scroll. The pane must therefore own one
vertical scroll container to keep every control reachable on small dialogs and
with larger text. Hiding inactive field groups minimises normal scrolling, and
the absence of a nested container avoids gesture conflicts. The oldest
inspected 5.5.1.1 portrait dialog uses only 55% of screen height (5.5.1.10 uses
65%), so the device matrix must include that smaller host viewport. At each
recorded device, orientation, and font scale, acceptance compares the native
pane directly with ATAK's DD pane; Taiwan controls must remain above ATAK's
elevation and action controls without overlap.

**Alternatives considered**: Embed the custom GoTo layout (rejected: it carries
advanced controls and side effects irrelevant to a native pane); omit scrolling
(rejected: ATAK's pane frame cannot recover clipped content); three
simultaneous expanded sections (rejected: height and zone ambiguity); new
plugin Auto Fill/Clear/Copy buttons (rejected: duplicates native controls).

## R8 — Localise resources without mutating an active host dialog

**Decision**: Add matching string IDs in English, zh-rTW, and Japanese. The
pane refreshes internal labels from the existing localised plugin context on
activation. Because ATAK caches `getName()` when registering the tab, a locale
refresh re-registers only while the pane view is detached; if attached, the
registrar records a pending refresh and completes it after detach. System
configuration recreation naturally follows the normal stop/start path.

**Rationale**: This updates a closed dialog on its next opening while avoiding
host tab mutation underneath an active operator session.

**Alternatives considered**: Always re-register immediately (rejected: can
invalidate an active tab); never refresh until process restart (rejected: does
not satisfy the closed-dialog locale edge case).

## R9 — Contain every host boundary and preserve a fallback

**Decision**: Catch and log failures at registration, activation, human edit,
conversion, formatting, listener dispatch, and unload boundaries. Registration
failure triggers best-effort unregister and disposal, but does not abort
`TwCoordMapComponent.onCreate`; the custom GoTo receiver continues to register.
No broadly caught failure is converted into a plausible coordinate.

**Rationale**: The plugin shares ATAK's process. A missing native tab is a
recoverable feature loss; a fabricated or stale coordinate is not.

**Alternatives considered**: Fail plugin startup when native registration fails
(rejected: removes the documented fallback); silently return the prior point
(rejected: can move/edit the wrong location).

## R10 — Keep native and custom state independent

**Decision**: Add `pref_native_entry_last_unit` only. Do not read, migrate,
write, or clear `KEY_GOTO_LAST_UNIT`, coordinate field keys,
`KEY_GOTO_RECENT_JSON`, or marker mode. First native use is Taipower; later
native sessions restore only the native system choice, not draft values.

**Rationale**: The custom page remains the advanced and rollback path. Sharing
history would make use of one workflow unexpectedly alter the other and would
complicate rollback.

## R11 — Preserve existing provenance and error budgets

**Decision**: Run all current golden-vector, national 22-city, parser, and
round-trip tests unchanged, then add adapter tests proving native callbacks use
the same results. TWD97 remains within 0.5 m over the national vectors (0.1 m
for the stricter legacy golden set); TWD67 remains within 5 m main-island and
20 m outer-island. Taipower input continues to accept the existing 9- and
11-character forms; forward Auto Fill/format remains the current 11-character,
1 m output. Existing 9-character cell-quantisation and inverse round-trip tests
remain unchanged rather than becoming the native output default.

**Rationale**: Feature 011 is an integration feature, not a coordinate-model
change. Any required tolerance or constant change is a failure requiring a
separate specification and provenance review.

## ADR requirement

ADR-0022 already records the 5.5 minimum. Before merge, add ADR-0023 to record
the accepted one-pane architecture, public ATAK seam, lifecycle ownership, and
coexistence with the custom page.
