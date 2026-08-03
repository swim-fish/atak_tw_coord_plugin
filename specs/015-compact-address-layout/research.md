# Research: Compact Structured Address Layout

## R1. Preserve the existing Address content/action split

**Decision**: Keep the Address body as an 8:2 horizontal split. Structured
fields remain in the left content column; the alternate-mode and candidate
actions remain in the top-aligned right column.

**Rationale**: This matches the accepted preview, the current native Address
contract, and the compact Taipower action pattern. It avoids placing an action
below content that can grow and keeps ATAK-owned controls reachable.

**Alternatives considered**:

- Use the full pane width for fields and move the mode action below them:
  rejected because it regresses the established Address/Taipower pattern and
  consumes vertical space.
- Reduce the action column below 20 percent: rejected because localized action
  text and the 48 dp target already fit the accepted 8:2 geometry.

## R2. Nest existing field groups inside two equal rows

**Decision**: Add one first-row container for county/city plus
district/township and one second-row container for road/locality plus
house-number/floor. Each existing field-group container occupies weight 1 in
its row and keeps its label/input 3:7 internal proportion.

**Rationale**: Existing view IDs, field types, labels, hints, controller
bindings, enabled states, and editor actions remain unchanged. Only parentage
and geometry change, keeping the implementation narrow and testable.

**Alternatives considered**:

- Replace the form with a grid widget: rejected because it adds a new layout
  abstraction and makes equal weights and accessibility order less explicit.
- Put labels above inputs: rejected because the approved preview uses inline
  compact labels and the existing 3:7 groups have adequate width inside each
  half-row at the supported pane sizes.
- Use four raw input fields without labels like Taipower guided entry: rejected
  because locality and address components are not self-identifying fixed
  groups and require persistent visible labels.

## R3. Keep row-major accessibility and editor order

**Decision**: Order view children as county/city, district/township,
road/locality, then house-number/floor. County and district keep their existing
selector behavior; road keeps Next targeting the tail; tail keeps Search/Done.

**Rationale**: Visual, touch, screen-reader, and keyboard order agree. No Java
callback change is necessary.

**Alternatives considered**:

- Column-major order: rejected because it conflicts with the operator's visual
  scan and the accepted row grouping.
- Add custom accessibility traversal code: rejected because ordered XML
  children and existing content descriptions already express the required
  sequence.

## R4. Shrink-wrap the compact form instead of forcing the old viewport cap

**Decision**: The existing bounded outer scroll owner remains, but the two-row
structured form should measure below the 216 dp cap at the 900 dp reference
width when no status/candidate content is visible.

**Rationale**: The old four-row test intentionally reached the cap. Keeping
that result after reducing the form to two rows would hide the benefit and
indicate unnecessary height or padding.

**Alternatives considered**:

- Retain a fixed 216 dp structured viewport: rejected because it wastes the
  vertical space this feature is meant to return to ATAK.
- Remove the bounded scroll owner: rejected because status text, large fonts,
  and future candidate content still require one safe overflow path.

## R5. Synchronize version and current documentation without an ADR

**Decision**: Set the plugin and user-facing documentation version to `1.5.1`,
add a changelog entry, and update the UI and user-guide descriptions. Keep
current screenshots until a sanitized physical-device replacement is captured
as a release gate.

**Rationale**: The user explicitly requested a patch-version increment. The
layout change refines presentation within ADR-0023's existing native pane
architecture. The later selected-target remediation adopts the same public
local broadcast already used by ATAK's native coordinate overlay; it does not
replace the accepted integration architecture or compatibility strategy.

**Alternatives considered**:

- Add an ADR: rejected because the public host contract and compatibility
  strategy already exist; R6 records the bounded registration, delivery,
  disposal, source, and binary evidence needed for this use of that contract.
- Treat the generated mockup as release evidence: rejected because it is a
  design preview, not a physical-device screenshot.

## R6. Mirror ATAK selected-marker dismissal through its public local broadcast

**Decision**: Retain the direct `MapEvent.MAP_CLICK` listener and also register
an `AtakBroadcast` receiver for
`com.atakmap.android.maps.HIDE_DETAILS`. Both paths clear only the plugin's TGT
coordinate/address readout. Register during component creation and unregister
during component destruction.

**Rationale**: While a marker radial menu is active, ATAK replaces the active
map-listener stack. A background tap therefore reaches ATAK's menu listener but
can bypass the plugin's direct `MAP_CLICK` listener. ATAK's own menu emits
`HIDE_DETAILS` for background map press/click/long-press, and its native
coordinate overlay consumes the same action. Mirroring that public contract
keeps native and plugin selected-target state synchronized without reflection,
private fields, or assumptions about listener-stack ownership.

**Pinned 5.7.0.9 binary evidence**:

- `main.jar` SHA-256:
  `8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.
- `javap -public` confirms
  `AtakBroadcast.getInstance()`,
  `registerReceiver(BroadcastReceiver, DocumentedIntentFilter)`,
  `unregisterReceiver(BroadcastReceiver)`, and
  `DocumentedIntentFilter()` are public. The implementation adds the action
  through the inherited Android `IntentFilter.addAction(String)` method.

**Minimum-runtime source anchors**: immutable official ATAK-CIV commit
`6cefd4c83371789937a6a30aa4d7e81d84b82374` from the 5.5.1.1 line shows:

- [`MenuLayoutWidget` sends `HIDE_DETAILS` for background map interaction](https://github.com/TAK-Product-Center/atak-civ/blob/6cefd4c83371789937a6a30aa4d7e81d84b82374/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MenuLayoutWidget.java#L110-L120).
- [`CoordOverlayMapComponent` registers `HIDE_DETAILS` and unregisters its receiver](https://github.com/TAK-Product-Center/atak-civ/blob/6cefd4c83371789937a6a30aa4d7e81d84b82374/atak/ATAK/app/src/main/java/com/atakmap/android/coordoverlay/CoordOverlayMapComponent.java#L24-L44).
- [`AtakBroadcast` exposes the local register/unregister lifecycle](https://github.com/TAK-Product-Center/atak-civ/blob/6cefd4c83371789937a6a30aa4d7e81d84b82374/atak/ATAK/app/src/main/java/com/atakmap/android/ipc/AtakBroadcast.java#L174-L234).

**Lifecycle matrix**:

| Stage | Plugin behavior | Compatibility evidence |
|-------|-----------------|------------------------|
| Registration | Construct one documented filter and register one receiver during `onCreate` | Public 5.7.0.9 `javap`; public 5.5.1.1 source; reviewed component path |
| Delivery | Treat `HIDE_DETAILS` and direct background `MAP_CLICK` as idempotent requests to clear only TGT | 5.5.1.1 sender/native-receiver source; focused JVM tests; 5.7.0.9 device smoke test |
| Disposal | Unregister the same receiver during `onDestroy`; contain ordinary missing-registration failures | Public 5.7.0.9 `javap`; 5.5.1.1 unregister source; reviewed component path |

Exact ATAK-CIV 5.5.0 physical-device acceptance remains a release gate; the
5.5.1.1 source anchor proves API lineage but is not a substitute for that
runtime journey.

**Alternatives considered**:

- Rely only on the plugin's `MAP_CLICK` listener: rejected because ATAK's
  marker-menu listener stack can bypass it.
- Observe private menu state or use reflection: rejected because those are
  unstable, unevidenced seams and increase host-process risk.
- Clear MAP and ME with TGT: rejected because `HIDE_DETAILS` dismisses the
  selected item, not the operator's map-centre or self-location context.

## R7. Invalidate queued address emissions and preserve fatal JVM semantics

**Decision**: Maintain an atomic generation per MAP/ME/TGT address row. Every
new coordinate or explicit clear increments that row's generation. Debounced,
legacy, and shared-resolver paths capture the generation and re-check it inside
every UI-posted emission. Cleanup boundaries catch ordinary
`RuntimeException`, but do not catch `VirtualMachineError` or `ThreadDeath`.

**Rationale**: Cancelling a future or lookup handle does not retract a runnable
already queued on the UI thread. A generation check at the point of emission
prevents an old TGT result from restoring a dismissed marker. Limiting
containment to ordinary runtime failures protects ATAK from plugin faults while
allowing fatal JVM conditions to keep their process-level semantics.

**Alternatives considered**:

- Check cancellation only before posting: rejected because dismissal can race
  after that check and before the UI runnable executes.
- Use one global generation: rejected because clearing TGT must not invalidate
  independent MAP or ME work.
- Catch `Throwable` around cleanup: rejected because it can swallow fatal JVM
  conditions inside the ATAK host process.
