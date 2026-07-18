# Contract: ATAK Native Taiwan Coordinate Entry Pane

## Identity and ownership

- Interface: `com.atakmap.android.gui.coordinateentry.CoordinateEntryPane`
- Stable UID: `com.atakmap.android.twcoord.coordinateentry.taiwan`
- Owner: one `NativeCoordinateEntryRegistrar` per live `TwCoordMapComponent`
- Registration: one `CoordinateEntryCapability.registerPane(pane)` call on the UI thread
- Teardown: matching `unregisterPane(pane)` before idempotent `pane.dispose()`
- Host compatibility: ATAK 5.5+ only; no reflection or private API

## Method contract

### `String getUID()`

Returns the stable UID above. It is non-null, non-empty, and invariant across
locales, sessions, versions, and instances.

### `String getName()`

Returns the localised top-level label for Taiwan. ATAK reads this value during
registration; locale changes refresh a detached pane registration or defer the
refresh until detach.

### `View getView()`

Returns the same non-null plugin-owned root view for the life of the pane. The
view uses plugin resource IDs and owns exactly one non-nested vertical
`ScrollView`, because ATAK's active-pane `FrameLayout` does not scroll. It
remains a safe inert object after dispose because an already-open ATAK dialog
may temporarily retain it.

### `void onActivate(GeoPointMetaData currentPoint, boolean editable)`

1. Set the editability of every human input and selector.
2. Suppress `OnChangedListener` while applying programmatic changes.
3. If `currentPoint == null`, clear the active draft and inline state.
4. Otherwise, forward-convert its horizontal coordinate into the active
   Taiwan system, replacing all active fields and selecting zone 119/121.
5. If unrepresentable, clear the active fields and show a localised state.
6. Contain/log unchecked failures; never leave a stale draft marked valid.

### `GeoPointMetaData getGeoPointMetaData() throws CoordinateException`

1. Parse only the active system's visible draft.
2. If valid, return a newly created horizontal WGS84 `GeoPointMetaData`.
3. If empty, malformed, incomplete, bad-zone, out-of-coverage, or disposed,
   throw `CoordinateException` carrying the applicable localised corrective
   message and no fabricated fallback point.
4. Do not pan, create a marker, apply affiliation, or decide altitude.

### `void autofill(GeoPointMetaData point)`

1. Suppress human-change notification.
2. Clear the active draft first.
3. If `point` is representable, forward-convert and populate all active values
   plus zone.
4. If null or unrepresentable, remain empty/invalid and display the relevant
   localised state.
5. Do not confirm the dialog or move the map.

### `String format(GeoPointMetaData point)`

- Pure: does not mutate draft, selected system, focus, errors, preference, or
  listener state.
- Returns null for null, disposed, or unrepresentable input.
- Uses the active Taiwan system at call entry.
- Excludes altitude because ATAK appends/owns elevation.
- Canonical value shapes:
  - `Taipower: <normalised 11-character / 1 m code>`
  - `TWD97 / TM2 zone <119|121>: E <integer> m, N <integer> m`
  - `TWD67 / TM2 zone <119|121>: E <integer> m, N <integer> m`
- System labels may be localised, but values, units, and explicit TWD zone are
  deterministic for a given locale and point.
- Formatting is supplied by the native-entry-only `TaiwanEntryFormatter`; the
  existing on-map/custom-page `Formatter` and its output remain unchanged.

### `void setOnChangedListener(OnChangedListener listener)`

- Stores zero or one listener; null detaches it.
- Fires only for human field edits, zone changes, or system selection.
- Does not fire for `onActivate`, Auto Fill, native Clear, Copy/format, locale
  refresh, view binding, or dispose.
- Wraps listener invocation so client failure cannot escape into ATAK.
- Re-entrant callbacks do not produce an unbounded notification loop.

### `void dispose()`

- Idempotent and monotonic.
- Detaches text/check/attach listeners that can be detached safely.
- Clears the ATAK changed listener, marks controller state disposed, and
  prevents future valid results.
- Does not destroy or replace the root view while ATAK may retain it.
- Every later callback is safe and returns null/no-op or a contained checked
  error as appropriate.

## Registration failure contract

If capability lookup or registration fails:

1. Log one diagnosable failure with ATAK/runtime context.
2. Attempt best-effort unregister rollback if registration may be partial.
3. Dispose the pane.
4. Leave registrar state `FAILED`/not registered.
5. Continue plugin startup so the custom **TW Coord GoTo** path remains usable.

If unregister fails, log and still dispose. Never claim successful removal in
tests or release notes until device verification confirms the next dialog has
no Taiwan tab.
