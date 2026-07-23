# Contract: Native Taiwan Address Pane

## Identity and host ownership

- One registered Taiwan pane keeps UID
  `com.atakmap.android.twcoord.coordinateentry.taiwan`.
- ATAK owns the surrounding dialog, top-level tab strip, elevation, Auto Fill,
  Clear, Copy, and confirmation actions.
- The plugin owns the pane View, four internal tabs, address controls,
  candidate dialog content, lookup state, and localized resources.
- Registration and exact-instance unregistration occur on the ATAK UI thread;
  unregister precedes idempotent pane disposal.
- No reflection, private ATAK API, or second top-level Address pane is allowed.

## Internal tabs

```text
Taipower | TWD97 | TWD67 | Address
```

- The three coordinate tabs retain feature 011/012 behavior and draft models.
- Address is a UI tab, not a coordinate-system enum value.
- Selecting Address routes host getter, Auto Fill, formatting, read-only, and
  Clear behavior to the address controller.
- A failure or unavailable dataset in Address cannot clear or disable a valid
  coordinate tab.

## Address layout

- The root retains one outer vertical `ScrollView`; no nested vertical list is
  placed inside the pane.
- Four tab choices share a 48 dp-high selector row.
- Full-address mode is the initial mode and contains one compact underline
  input plus one plugin-owned mode switch.
- Structured mode contains compact DD-style rows for county/city,
  district/township, road/locality, and tail.
- The mode switch and `Choose result` control have meaningful accessibility
  labels and at least 48 dp touch targets.
- Status/loading/error text is `GONE` when empty and uses a polite live region
  when visible.
- All content remains above or scroll-reachable without covering ATAK-owned
  elevation and action controls at the supported pane sizes and font scales.

## Address modes

- Both modes render one canonical AddressDraft.
- Human edits update the canonical draft, invalidate the prior resolution, and
  schedule forward lookup after debounce.
- Switching modes re-renders the draft without changing its revision,
  restarting lookup, losing unclassified text, or notifying ATAK of a location
  change.
- In read-only state, text and candidate mutation are disabled. Mode switching
  may remain available only as a pure display projection.

## Candidate selection dialog

- A unique exact result resolves inline without opening a dialog.
- Ambiguous results expose a bounded `Choose result` action; a dialog never
  opens automatically while the operator is typing.
- The dialog uses the ATAK Activity context for its window token.
- Every plugin title, message, row string, and drawable is resolved through
  the current localized plugin context before being passed to the dialog.
- No plugin resource ID is passed to an Activity-context builder method.
- A row displays enough county/district/address context to distinguish results;
  distance may be shown but cannot assert exactness.
- Selection is accepted only when pane, session, draft, request, and dataset
  revisions still match. Closing or superseding the dialog has no state effect.
- Selection prepares the result; only ATAK's host confirmation performs Go To
  or another location action.

## CoordinateEntryPane method behavior

### `onActivate(point, editable)`

- Non-null point:
  1. synchronously prepare all representable coordinate drafts;
  2. cancel prior address lookup and replace all address session state;
  3. render Address loading/unavailable state;
  4. start reverse lookup without blocking coordinate rendering.
- Null point: preserve the established active-tab-only Clear contract. If
  Address is active, clear only Address fields/candidates/resolution.
- Programmatic preparation does not fire the human-change listener.
- Failure leaves a safe empty/unavailable Address state and valid coordinate
  drafts intact.

### `autofill(point)`

- Routes only to the active internal tab.
- On Address, clears the active Address result and starts reverse lookup for
  the supplied point.
- A found reverse address labels the exact supplied point; it never snaps the
  result to the nearest address record.
- Null/unrepresentable/no-data input remains unresolved with localized state.
- Does not confirm or move the map.

### `getGeoPointMetaData()`

- Fully synchronous and performs no lookup or file/database access.
- Coordinate tabs retain their existing parser/result behavior.
- Address returns a new point metadata object only from a current completed
  AddressResolution.
- Forward exact/selected results return the candidate record WGS84.
- Reverse results return the exact host/Auto Fill WGS84 and carry address
  metadata from the nearest record.
- Empty, pending, ambiguous, no-match, no-data, failure, or disposed Address
  states throw a localized checked coordinate exception.

### `format(point)`

- Pure and fully synchronous.
- Coordinate tabs retain their canonical formatter.
- Address reads `twcoord.address.display` from the supplied point metadata and
  returns it without changing visible state; absent metadata returns null.
- Never starts reverse lookup or waits for a result.

### `setOnChangedListener(listener)`

- Human coordinate edits retain existing semantics.
- Address text edits may report human change while unresolved.
- A human-initiated forward lookup completion may report one additional change
  only after the accepted resolution is committed and the synchronous getter
  can return it.
- Explicit candidate selection reports a human change after commit.
- Programmatic activation, reverse lookup, mode projection, rendering,
  localization refresh, cancellation, and disposal do not report human change.
- Listener failure is contained without suppressing controller state.

### `dispose()`

- Idempotently marks the pane/controller disposed, increments generation,
  cancels current lookup, dismisses or invalidates candidate dialogs, removes
  watchers/listeners, and disables controls.
- Late results and dialog callbacks are ignored.
- Later activation/Auto Fill are no-op; format returns null; getter returns a
  checked disposed error.
- The root View remains an inert valid object while an already-open ATAK dialog
  may retain it.

## Locale refresh

- The registrar retains detached-pane replacement and attached-pane deferred
  replacement behavior.
- Replacement panes receive the live lookup service and manager navigator.
- Every address string has English, Traditional Chinese (Taiwan), and Japanese
  resources with matching format arguments.
