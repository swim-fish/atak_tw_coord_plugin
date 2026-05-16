# Contract — `TwCoordGotoReceiver` (DropDown lifecycle + Intent surface)

**Feature**: 002-tw-coord-goto | **Java package**: `com.atakmap.android.twcoord.goto`

`TwCoordGotoReceiver` is the ATAK glue that opens / closes the input
page, owns the page's view tree, and serves as the
`BroadcastReceiver` for the Tools-menu icon's intent. It extends
`com.atakmap.android.dropdown.DropDownReceiver` (same parent as
ATAK's native input pages).

This contract spans three concerns:
1. The **intent action** that opens the page.
2. The **DropDown lifecycle** that views and Auto Fill streams hook
   into.
3. The **map handle operations** (pan + marker move-or-create) that
   submit triggers.

---

## 1. Intent action surface

| Action constant | Direction | Carries | Effect |
|---|---|---|---|
| `com.atakmap.android.twcoord.SHOW_GOTO` | inbound | optional extras (see below) | Opens the input page DropDown. Idempotent: a second send while open is a no-op. |
| `com.atakmap.android.twcoord.SHOW_GOTO` extras: `unit` (string, optional) | inbound | `"TAIPOWER" \| "TWD97" \| "TWD67"` | Forces the page to open with that tab active. Absent → use `InputPageState.activeTab` or `PreferenceStore.getGotoLastUnit()`. |
| `com.atakmap.android.twcoord.GOTO_NAV_COMPLETED` | outbound | extras `lat`, `lon`, `unit`, `rawValue` | Fired by `TwCoordGotoView.onSubmit` after the marker is placed. Allows downstream observers (none in v1, but reserved for future). |

The Tools-menu icon registration in `plugin.xml` adds a second
`<extension type="tool">` (alongside the existing one) whose `action`
attribute is `com.atakmap.android.twcoord.SHOW_GOTO`. The receiver
itself is registered in `TwCoordMapComponent.onCreate` via
`AtakBroadcast.getInstance().registerReceiver(...)`.

The intent surface **does not** chain to ATAK's native
`com.atakmap.android.routes.GOTO_NAV_BEGIN`. Although that action
would re-use ATAK's pan+marker handling for free, it expects a
`GeoPoint.parseGeoPoint(String)` value — Taiwan unit strings are not
parseable by that path. We do the conversion ourselves and call the
camera + marker APIs directly (§3).

---

## 2. DropDown lifecycle

```text
Tools-icon tap
   │
   ▼  AtakBroadcast send: SHOW_GOTO
TwCoordGotoReceiver.onReceive
   ├── if dropDown.isVisible() → return    (idempotent reopen)
   ├── inflateLayout(R.layout.tw_coord_goto)
   ├── bindView(view, inputPageState)      (see TwCoordGotoView)
   ├── attachMapCenterStream()             (R7 in research.md)
   └── showDropDown(view, …)               (standard DropDownReceiver call)

User pans the map
   │
   ▼
MapCenterAutoFillStream.publish(MapCenterFix)
   │
   ▼
TwCoordGotoView re-evaluates Auto Fill button state for active tab

User taps Auto Fill
   │
   ▼  TwCoordGotoView.onAutoFillClick
   ├── read latest MapCenterFix
   ├── if !(activeTabOk) → no-op (button should be disabled anyway)
   ├── format Wgs84 → CoordinateInput for active tab
   └── write into EditText(s) and zone toggle

User taps Submit
   │
   ▼  TwCoordGotoView.onSubmit
   ├── parser.parse(coordinateInput) → ParseResult
   ├── if Invalid / OutOfRange → show inline error; abort
   ├── if Ok(wgs84) →
   │    ├── DestinationMarker.moveOrCreate(wgs84, input)  (§3)
   │    ├── mapView pan+zoom (§3)
   │    ├── PreferenceStore.setGotoLast{Unit,Value,…}(input)
   │    ├── RecentEntryStore.append(RecentEntry.of(input, now))
   │    └── closeDropDown()
   └── send GOTO_NAV_COMPLETED outbound intent

User presses back button while page is open
   │
   ▼
DropDown framework invokes onBackPressed
   ├── closeDropDown()
   ├── persist InputPageState into receiver's in-memory cache
   └── detachMapCenterStream()             (release MAP_* listeners)

Plugin is being disabled / process tearing down
   │
   ▼
TwCoordMapComponent.onDestroy
   ├── unregister receiver
   └── detach all listeners
```

**Idempotency / safety**:
- `attachMapCenterStream()` / `detachMapCenterStream()` MUST be
  paired and safe to call multiple times; the stream itself uses the
  same `haveEmitted` flag pattern as `SelfMarkerSubscriber` to avoid
  duplicate-listener leaks.
- Submit MUST short-circuit cleanly if the receiver is being torn
  down between the click and the parse callback (the Auto Fill edge
  case in spec).

---

## 3. Map handle operations

### 3a. Pan + zoom

On a successful submit, the page calls:

```java
mapView.getRenderer3()
    .lookAt(GeoPoint.createMutable().set(wgs84.latitudeDeg(),
                                          wgs84.longitudeDeg()),
            zoomMetresPerPixel,
            /*rotation*/ 0.0,
            /*tilt*/ 0.0,
            /*animate*/ false);
```

`zoomMetresPerPixel` is set to a town-scale view (~ 50 m/px on
Galaxy Tab S10+; final value tuned per device). The pan is
**non-animated** by default to keep the submit→render path under the
300 ms budget (SC-002); a future ADR may revisit if user feedback
asks for a smooth animation.

### 3b. Marker move-or-create (`DestinationMarker`)

```java
DestinationMarker.moveOrCreate(Wgs84 target, CoordinateInput input):

    if delegate == null:
        Marker m = new Marker(uid, GeoPoint.fromWgs84(target));
        m.setTitle(callSign(input));
        m.setType("b-m-p-w-GOTO");          // user-placed waypoint
        m.setIcon(R.drawable.ic_tw_coord_goto);
        m.setRemovable(true);                // honoured by ATAK long-press
        m.setMetaString("twcoord_goto_unit", input.unit().name());
        m.setMetaString("twcoord_goto_raw",   input.displayString());
        mapView.getRootGroup().addItem(m);
        delegate = m;
    else:
        delegate.setPoint(GeoPoint.fromWgs84(target));
        delegate.setTitle(callSign(input));
        delegate.setMetaString("twcoord_goto_unit", input.unit().name());
        delegate.setMetaString("twcoord_goto_raw",   input.displayString());

    private String callSign(CoordinateInput input):
        return input.unit().name() + " " + input.displayString();
        // e.g. "TAIPOWER H7509 DB4016", "TWD97 302912 / 2770905"
```

`uid` is allocated once on first call via
`UUID.randomUUID().toString()`; stable across moves so that downstream
CoT propagation (if ATAK is connected to a network) updates the same
marker rather than spawning duplicates.

---

## 4. Test contract (Espresso, instrumented)

Instrumented tests live under `app/src/androidTest/`. They verify the
DropDown + receiver lifecycle on a real device or emulator.

### Required scenarios

```java
@Test public void receiver_opensDropDown_onShowGotoIntent()
@Test public void receiver_isIdempotent_onSecondShowGotoIntent()
@Test public void receiver_closesCleanly_onBackPress()
@Test public void receiver_restoresActiveTab_fromPreference()
@Test public void receiver_restoresActiveTab_fromIntentExtra()
@Test public void autoFillButton_isDisabled_whenMapCentreOutsideTaiwan()
@Test public void autoFillButton_enables_withinOneFrame_afterPanInsideTaiwan()
@Test public void autoFillButton_isDisabled_onTaipowerTab_whenCenterIsPenghu()
@Test public void submit_pansAndDropsMarker_thenClosesDropDown()
@Test public void resubmit_movesExistingMarker_doesNotDuplicate()
@Test public void submit_persistsLastInput_acrossDropDownReopen()
@Test public void submit_appendsRecentEntry_capacityTen()
```

Each test seeds a deterministic map state (Taipei 101 centre,
Kaohsiung centre, Penghu centre, Tokyo centre) and asserts both the
view state (button enabled-ness, error text) and the resulting map
state (marker presence, marker position within tolerance band).

---

## 5. Failure modes & required handling

| Failure | Required behaviour |
|---|---|
| ATAK `MapView` is null during `attachMapCenterStream()` | Skip attach, log a debug message, leave the Auto Fill button disabled with a generic hint. Do not crash. |
| `Projections.twd97ToWgs84` throws (proj4j malformed input) | Surface as `ParseResult.Invalid(NON_DIGIT)` (or a more specific code if the exception carries one); never let it propagate. |
| User submits while `DestinationMarker.moveOrCreate` is mid-flight (rapid double-tap on Submit) | Coalesce: second tap is a no-op until the first completes. Implementation: a single AtomicBoolean guard on submit. |
| Map view tears down between click and pan call | Catch `IllegalStateException`, abort the pan, leave the marker placement to ATAK's marker-add to handle (or skip silently if `addItem` also throws). The disabled-state guard already prevents the common cases. |
