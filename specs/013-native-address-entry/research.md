# Research: Native Taiwan Address Entry

## R1 — Retain the public native coordinate-entry seam

**Decision**: Extend the existing plugin-owned Taiwan pane and keep its stable
UID. Continue registering and unregistering the exact pane instance through
ATAK's public coordinate-entry capability on the ATAK UI thread. Do not add a
new top-level pane, use reflection, or modify ATAK's global coordinate-format
model.

**Rationale**: The pinned ATAK-CIV 5.7.0.9 `main.jar` exposes the complete
`CoordinateEntryPane` interface and public `getInstance`, `registerPane`, and
`unregisterPane` methods. Its SHA-256 remains
`8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.
The earliest public source anchor in the supported family, ATAK-CIV 5.5.1.1,
exposes the same pane methods and registration calls. The feature changes pane
content and asynchronous plugin behavior, not the host seam.

**Evidence**:

- [ATAK 5.5.1.1 CoordinateEntryPane](https://github.com/TAK-Product-Center/atak-civ/blob/5.5.1.1/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryPane.java)
- [ATAK 5.5.1.1 CoordinateEntryCapability](https://github.com/TAK-Product-Center/atak-civ/blob/5.5.1.1/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryCapability.java)
- `docs/adr/0022-set-minimum-atak-runtime-to-5-5.md`
- `docs/adr/0024-use-atak-5-7-0-9-compile-sdk.md`

**Alternatives considered**:

- Add a separate top-level Taiwan Address pane: rejected because the accepted
  information architecture is one Taiwan family and ATAK's tab strip is
  already crowded.
- Extend ATAK `CoordinateFormat`: rejected because Address is a lookup mode,
  not a deterministic coordinate system.
- Use reflection for older hosts: rejected because ATAK 5.5 is already the
  supported minimum and exposes the public seam.

## R2 — Preserve ATAK's null-activation semantics

**Decision**: Treat a non-null `onActivate` point as a new supplied-point
activation that replaces every Taiwan draft. Treat `onActivate(null, ... )` as
ATAK's active-tab-only Clear behavior. Do not infer a new session from View
attach/detach events.

**Rationale**: ATAK 5.5 uses the same null callback both when clearing the
active pane and when a dialog has no current point. The callback has no caller
or operation discriminator. Feature 012 already establishes active-only Clear;
an attach-state heuristic would be version-sensitive and could unexpectedly
erase inactive coordinate drafts.

**Alternatives considered**:

- Use View attachment as a session heuristic: rejected because it is not part
  of the public pane contract and would require unproven host ordering.
- Clear every tab on null: rejected because it violates the shipped native
  Clear behavior.

## R3 — Keep synchronous host methods cache-only

**Decision**: `getGeoPointMetaData()` and `format(point)` never perform file,
boundary, or database work. The getter reads only a completed address
resolution in the current pane session and returns a new point carrying
namespaced address metadata. `format(point)` reads that metadata from the
supplied point and returns null when no address representation exists; it does
not start lookup or mutate the pane.

**Rationale**: Both methods are synchronous ATAK UI-thread callbacks. The
public contract explicitly requires `format` to work without changing pane
state. ATAK's built-in address pane follows the same pattern: asynchronous
lookup stores a point/address, the getter returns it, and formatting reads
stored metadata rather than geocoding synchronously.

**Alternatives considered**:

- Block until lookup completes: rejected because it risks freezing the ATAK
  UI and cannot provide reliable cancellation.
- Start asynchronous lookup from `format`: rejected because formatting must be
  pure and may be called regardless of visible pane state.
- Return the nearest address point before lookup completes: rejected because
  it fabricates a resolved location.

## R4 — Separate UI tabs from coordinate systems

**Decision**: Add an entry-level four-value tab model while retaining the
existing three-value coordinate-system model. The current coordinate
controller continues to own Taipower, TWD97, and TWD67; a separate address
controller owns address drafts and asynchronous state.

**Rationale**: Existing coordinate code iterates all coordinate-system values
and assumes each has deterministic forward/inverse conversion. Address lookup
has candidate, loading, dataset, and failure states and cannot satisfy those
switches. A pane-level coordinator can route host callbacks without polluting
geospatial conversion logic.

**Alternatives considered**:

- Add Address to the coordinate-system enum: rejected because it would break
  exhaustive conversion switches and misrepresent a lookup as a projection.
- Merge address state into the coordinate controller: rejected because it
  couples synchronous coordinate conversion to asynchronous dataset lifecycle.

## R5 — Normalize and split with authoritative local dictionaries

**Decision**: Normalize Unicode width, whitespace/common punctuation,
`台`/`臺`, numeric subnumber separators, and Chinese numerals only when adjacent
to address units. Split county/city and district/township using longest known
prefixes from installed boundary/dataset information; derive the road/locality
against active address data; parse only the lane/alley/house/floor/room tail by
unit grammar. Preserve raw, normalized, structured, and unclassified text.

**Rationale**: A single regular expression mis-splits overlapping place names
such as `臺南市新市區`. The existing `StreetTextNormaliser` handles only a small
folding subset and is insufficient for full addresses. The plugin already has
authoritative imported address rows and township boundary data, so a new
external postcode library or bundled duplicate locality table is unnecessary.

**Alternatives considered**:

- One large regular expression: rejected because Taiwan administrative and
  road names overlap address-unit characters.
- Add `zipcodetw` as a runtime dependency: rejected because it would duplicate
  data, add provenance/update responsibility, and diverge from active TGOS
  datasets.
- Discard unknown tails: rejected because silent loss prevents safe correction
  and violates full/structured round-trip requirements.

## R6 — Introduce one UI-independent address lookup service

**Decision**: Extract a shared service under a neutral address lookup package.
It owns a single bounded worker, forward and reverse requests, cancellation,
dataset availability, and completion dispatch. Requests/results carry a
request ID and dataset revision; the native controller additionally validates
session generation and draft revision before accepting a result.

**Rationale**: The existing forward-search controller is stateful, assumes a
county/district funnel, performs synchronous database calls, and falls back to
nearest candidates. The existing address subsystem is tied to ME/TGT/MAP
widget rows, debounce, and presentation state. Neither is an appropriate
native pane session service. A shared service also lets the widget and native
pane use one database lifecycle without duplicate executors.

**Contract summary**:

- Forward request: parsed draft, optional ranking anchor, ordering, limit,
  request ID, and dataset revision.
- Reverse request: exact host WGS84, search radius, request ID, and dataset
  revision.
- Forward outcomes: no dataset, no match, candidates, failure.
- Reverse outcomes: no dataset, no match, found, failure.
- Cancel or close guarantees no later callback delivery.
- SQL and boundary work never runs on the main thread.
- Callback delivery uses an injected completion dispatcher. A forward lookup
  initiated by human editing may notify ATAK only after its accepted result is
  committed and the synchronous getter can return it; programmatic reverse
  preparation does not emit a human-change notification.

**Alternatives considered**:

- Inject `ForwardSearchController` into the pane: rejected because it retains
  the old page's funnel and synchronous query behavior.
- Expand `AddressSubsystem`: rejected because it owns widget-specific row
  semantics and would make native entry depend on map readout settings.
- Open a database per request: rejected because it increases latency and
  bypasses registry provenance and ownership.

## R7 — Add leased dataset reads and closed-state gates

**Decision**: Add a registry read-session contract. Lookup holds a read lease
while using county facades; import/replace/remove/close takes the write side.
Registry notifications occur after releasing the write lease. The registry and
batch coordinator gain monotonic closed states; a late imported facade is
closed instead of being registered after teardown.

**Rationale**: The current registry snapshot is an unmodifiable live map, not
an immutable ownership transfer. Replace/remove closes a facade immediately,
so a concurrent lookup can query a closed database. The current import cancel
allows an in-progress item to complete and later register a facade. Feature
013 makes these races more likely because native entry and widget reverse
lookup share data continuously.

**Alternatives considered**:

- Copy the map only: rejected because copying references does not keep the
  referenced facades open.
- Ignore close/query races and return failure: rejected because repeated
  replace/remove could expose stale or nondeterministic results.
- Query facades concurrently: deferred because current facade thread safety is
  not established; one worker is sufficient for the latency budget.

## R8 — Resolve only unique exact forward matches automatically

**Decision**: Candidate match kind is explicit. Only one deduplicated exact
candidate may auto-resolve. Multiple exact results or any partial/fuzzy set
requires operator selection. Distance is a stable secondary ordering signal,
never proof of exactness.

**Rationale**: The retired forward-search workflow intentionally falls back to
the nearest street candidate when a house number does not match. That is useful
for exploration but unsafe for native coordinate confirmation, where a
plausible nearest point could be mistaken for the typed address.

**Alternatives considered**:

- Auto-select the closest result: rejected because proximity cannot resolve
  duplicate road names or missing house numbers.
- Reject all ambiguous input without candidates: rejected because explicit
  candidate selection is both safe and useful.

## R9 — Reverse lookup labels but never snaps a supplied point

**Decision**: Reverse results retain both the exact host/query WGS84 and the
nearest dataset record WGS84. Convert Coordinate and Address Auto Fill display
the record's address but return the exact host/query point with namespaced
address and dataset-provenance metadata. Forward typed-address resolution
returns the explicitly resolved record point.

**Rationale**: Reverse lookup currently allows a radius up to 500 m. Returning
the record point would silently move a map item while merely converting or
labelling it. ATAK copies point metadata into its result, so a new metadata
object can carry the address without mutating the host-supplied input.

**Alternatives considered**:

- Return the nearest record point for reverse lookup: rejected because a
  display lookup must not change the source geometry.
- Mutate host metadata in place: rejected because the pane does not own the
  supplied object.

## R10 — Use a compact dual-mode UI and explicit candidate choice

**Decision**: Keep one outer vertical scroll owner. Add a four-way Taiwan tab
selector and an Address group with full-address mode by default. A plugin-owned
48 dp mode button switches to four compact logical rows. After the established
250 ms input debounce, a unique exact result resolves inline; an ambiguous result shows a
bounded `Choose result` action that opens a candidate dialog. Merely switching
full/structured mode does not change the address revision.

Build candidate dialogs with the ATAK Activity context for the window, but
pre-resolve every plugin string and drawable through the plugin context. The
dialog callback must re-check pane disposal, session, draft, and dataset
revisions before applying a selection.

**Rationale**: The interaction mirrors ATAK's MGRS split/join affordance while
the field geometry follows the native DD pane. An explicit candidate action
avoids opening dialogs while the operator is still typing. A dialog avoids a
nested scrolling candidate list inside the height-constrained native pane.

**Evidence**:

- [ATAK MGRSPane source](https://github.com/TAK-Product-Center/atak-civ/blob/5.5.1.1/atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/MGRSPane.java)
- `docs/ui/native-taiwan-coordinate-entry.md`
- `.agents/skills/plugin-dialog-resources/SKILL.md`

**Alternatives considered**:

- Always show all fields: rejected because it consumes native dialog height
  and makes paste entry slower.
- Put candidates in a nested list inside the pane: rejected because it creates
  competing vertical scroll owners and can cover host controls.
- Pass plugin resource IDs to an Activity-context dialog builder: rejected
  because the ATAK resource table cannot resolve plugin IDs on device.

## R11 — Keep one public Tools entry and one internal manager

**Decision**: `TwCoordLifecycle` exposes only `TW Coordinates`. Remove public
tool and receiver wiring for custom Go To and forward search. Remove only the
offline manager's public tool; retain its receiver, action, page, importer,
registry, and dataset-change action as internal navigation from
`TW Coordinates`. The dataset-status preference remains selectable regardless
of map address-row toggle state.

Stale custom Go To or forward-search actions are hard ignored because their
receivers are no longer registered. No unverified redirect to a particular
native Taiwan tab is introduced.

**Rationale**: The user selected native Go To as the sole location-entry path
and `TW Coordinates` as the sole plugin Tools icon. The current dataset-status
row already opens the manager, but it becomes disabled when all address readout
toggles are off; that gate must be removed once it is the primary management
path. ATAK caches Tools items at plugin load, so device verification requires
disable/enable or a full ATAK restart after installation.

**Alternatives considered**:

- Keep legacy icons as fallback: rejected because it defeats the accepted
  information architecture and preserves duplicate code.
- Embed the full importer/progress UI directly in preferences: rejected
  because the existing internal page owns file picking, progress, replacement,
  removal, and window lifecycle correctly.
- Redirect stale actions: rejected because no verified ATAK 5.5 public action
  selects Taiwan Address directly.

## R12 — Extract shared logic before deleting legacy UI

**Decision**: Move coordinate parser/value classes used by native entry out of
the legacy Go To package first. Extract address normalization, candidates,
ranking, and database queries into the shared service before deleting the old
forward receiver/controller. Then remove UI-only classes, intents, layouts,
drawables, preferences, tests, and documentation after reference audits.
Legacy SharedPreferences values remain inert; no destructive migration is
performed. Existing imported dataset directories and manifests are unchanged.

**Rationale**: The native coordinate controller currently imports parser
classes from the legacy package. Deleting the package first would break the
shipped native pane. The address database and ranking logic also serve reverse
readouts and the new Address tab even though the old forward page is retired.

**Alternatives considered**:

- Delete entire packages and rebuild: rejected because it risks coordinate
  regression and duplicated address behavior.
- Delete old stored preferences: rejected because there is no operational
  benefit and destructive cleanup creates upgrade risk.

## R13 — Reorder initialization and make teardown monotonic

**Decision**: Build filesystem/import support, initialize the dataset registry,
mount boundary data, and create the shared lookup service before constructing
the native registrar. If address infrastructure is unavailable, inject a
no-data service so the three coordinate tabs still register. Teardown stops
and disposes the native pane first, then closes manager/coordinator, lookup
service, widget adapter, boundary, registry/facades, and finally executors.

**Rationale**: The registrar currently starts before address infrastructure
exists. The new pane requires a stable service dependency, and a supplier that
becomes null during teardown would create late-callback races. Stopping the
pane first prevents new work; closing registry only after lookup cancellation
prevents use-after-close; the closed import gate prevents late reactivation.

**Alternatives considered**:

- Keep current order and inject mutable null suppliers: rejected because
  thread visibility and shutdown behavior become difficult to prove.
- Fail all native registration when address data fails: rejected because
  address availability must not remove Taipower/TWD97/TWD67.

## R14 — Preserve compatibility, offline, performance, and memory budgets

**Decision**: Keep Android compile/minimum 36/26 and ATAK
compile/minimum-runtime 5.7.0.9/5.5.0. Add no dependency or permission. Preserve
the 100 ms normalization/mode-switch budget, 1,000 ms median and 2,000 ms p95
lookup budgets, and the existing 200 MiB process RSS ceiling with boundary data
and at least two counties imported. Re-run these on a real device; previous
unchecked feature-006 performance tasks are not evidence.

**Rationale**: The feature reuses the existing databases and libraries. A
single worker, shared registry, and bounded candidates avoid multiplying open
facades or memory. Host API/source evidence cannot replace physical ATAK 5.5
and current-runtime journeys.

**Alternatives considered**:

- Treat the current SDK build as minimum-runtime proof: rejected by ADR-0024
  and Constitution VII.
- Add online fallback: rejected by the product scope and offline constitution
  requirement.

## R15 — Record a superseding architecture decision

**Decision**: Add ADR-0026 during implementation. It must supersede the
coexistence/fallback portion of ADR-0023 and the retired public-page portions
of ADR-0020, ADR-0021, and ADR-0009, while preserving historical records and
offline storage/import decisions. It records shared address-service ownership,
single-icon information architecture, stale-action handling, inert legacy
preferences, and the failure behavior when native registration is unavailable.

**Rationale**: This feature deliberately reverses the previous decision to
keep the custom Go To workflow and materially changes service ownership and
public navigation. Historical ADR text must not be rewritten.

**Alternatives considered**:

- Amend old ADRs: rejected because accepted historical decisions are
  immutable; reversals require a superseding ADR.
