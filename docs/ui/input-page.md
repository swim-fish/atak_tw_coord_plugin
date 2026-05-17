# UI — TW Coord GoTo input page

**Features**: 002-tw-coord-goto (base page) + 003-custom-marker-icon (Custom Icon mode & picker)
**Source**: `app/src/main/res/layout/tw_coord_goto.xml` + `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoView.java` + `CustomIconPickerDialog.java` + `IconResolver.java` + dialog layouts under `app/src/main/res/layout/custom_icon_picker_*.xml`

The TW Coord GoTo input page is a `DropDownReceiver` side-pane opened by the second Tools-menu icon (or the settings-page button). It is the *only* new user-facing surface this feature adds; everything downstream of Submit is pure ATAK behaviour the operator already knows.

## Anatomy

```
┌────────────────────────────────────────┐
│ Coordinate input                       │  ← title (R.id.goto_title)
│ ┌──────────┬──────────┬──────────┐    │
│ │ Taipower │  TWD97   │  TWD67   │    │  ← tab bar (RadioGroup,
│ └──────────┴──────────┴──────────┘    │     one radio per unit)
│ ────────────────────────────────────   │
│                          [ Auto Fill ] │  ← per-tab Auto Fill button
│ [Input field(s) for the active tab]   │
│ [Inline error TextView, red]           │  ← visible only when input invalid
│ [Outer-island advisory, amber]         │  ← visible only when zone = 119
│                                        │
│ [        Submit        ]               │  ← disabled until input is Ok
│                                        │
│ Recent                                 │  ← section header
│ ──────────────                          │
│ TAIPOWER  H7509 DB4016    [ Remove ]   │  ← rows, newest first
│ TWD97     302912 / 2770905 [ Remove ]   │
│ ...                                    │
└────────────────────────────────────────┘
```

## Tab contents

### Taipower tab

- One `EditText` for the 9- or 11-character code (case-insensitive, whitespace-tolerant).
- Auto Fill button (disabled when map centre is outside Taipower's main-island coverage).
- Inline error in red below the field when the input is invalid; cleared on Ok.

### TWD97 tab

- Two `EditText`s: easting (m), northing (m). `inputType="number"`.
- Zone toggle (`RadioGroup`): **121 (main island)** default, **119 (outer island)** alternative.
- Auto Fill button (disabled only when map centre is outside Taiwan's coverage box entirely).
- Outer-island accuracy advisory in amber, visible only while zone = 119.

### TWD67 tab

Same shape as TWD97 (easting + northing + zone toggle + advisory + error). TWD67 inherits the 4-parameter Bursa-Wolf shift from feature 001 — ±3–5 m on the main island, ±10–20 m on outer islands.

## Auto Fill

Each tab has a small **Auto Fill** button in the top-right of the pane. When the operator taps it, the input fields are populated from the current map centre:

- Taipower: writes the canonical `H7509 DB4016` form into the input field.
- TWD97 / TWD67: writes integer easting + northing into the two fields, **and** sets the zone toggle from the map-centre's longitude (`<120°` → zone 119, else 121).

The button is **disabled in real time** whenever the map centre cannot be expressed in the active tab:

- Map centre outside Taiwan's coverage box → all three Auto Fill buttons disabled.
- Map centre on an outer island while the Taipower tab is active → only Taipower's Auto Fill is disabled (TWD97 / TWD67 stay enabled).

Tapping a disabled Auto Fill button shows a localised toast explaining why.

The map-centre stream attaches when the DropDown opens and detaches when it closes — no `MapEventDispatcher` listeners leak beyond the page's lifetime.

## Submit

**Behaviour** (revised post-MVP — see ADR-0009 D1/D2):

1. Validate the active tab's input via `CoordinateParser`. If not `Ok`, Submit stays disabled (this is a guard; the click handler also short-circuits).
2. Persist the last-submitted `(unit, value)` tuple to `SharedPreferences` (FR-014).
3. Append a `RecentEntry` to `RecentEntryStore` (capacity 10, dedup on `(unit, rawValue)`).
4. Pan the camera's **X/Y** to the resolved point. **Zoom (Z) and other camera attributes are preserved** — the operator's chosen scale is never disturbed.
5. Show a brief confirmation toast `<unit> → <lat>°N <lon>°E` (zone-119 suffix appended when the resolved zone is 119).
6. Close the DropDown.
7. Fire the outbound `com.atakmap.android.twcoord.GOTO_NAV_COMPLETED` intent for any future downstream observers (none in v1).

**No marker is created.** If the operator wants a marker at the destination, they use ATAK's standard long-press → radial menu, which gives them the full type chooser (Waypoint / Mission Point / SPI / Friendly / Hostile / etc.). This is the same gesture they use everywhere else in ATAK; the input page does not introduce a new marker-placement affordance.

## Recent entries

The Recent section at the bottom of the DropDown shows the most recent **10** successful submissions, newest first, deduped on `(unit, rawValue)`. Each row is two widgets:

- A clickable label `<UNIT>  <rawValue>` (e.g. `TAIPOWER  H7509 DB4016`). Tapping it activates the matching tab and fills the input with the row's values, including the zone toggle. The operator can then edit a digit and Submit, or just Submit as-is.
- A `[Remove]` button that deletes only that row.

The list is persisted in `pref_goto_recent_json` (single JSON-encoded array in the plugin's SharedPreferences file). The list survives ATAK restarts and reinstalls (as long as the SharedPreferences file is preserved by `adb install -r`).

When the list is empty, an "No recent entries" placeholder TextView is shown instead.

## Localisation

All visible strings come from `app/src/main/res/values{,-zh-rTW,-ja}/strings.xml`. The Traditional-Chinese strings pass the `zhtw-mcp` lint at 0 errors / 0 warnings (full-width punctuation throughout, no Mainland-style usages).

Language changes via the settings page take effect when the operator next opens the DropDown — the layout inflates against the current configuration each time.

## What this page deliberately does NOT do

- It does not change the camera's zoom (Z) — only the pan target (X/Y) (ADR-0009 D2).
- It does not delegate to ATAK's native `GoToMapTool` (broken without GPS — ADR-0009 D3).
- It does not enforce a "single owned marker, move-not-create" invariant.

(Earlier ADR-0009 D1 said "no marker is created"; that was superseded by feature 002's marker-mode picker — see below — and again extended by feature 003's Custom Icon mode.)

## Marker-mode picker (feature 002 + 003)

Below the Submit button, the page shows a 3-row × ~4-column grid of marker-mode `RadioButton`s. Selecting one (other than **Move only**) means that when Submit fires, the camera pans **and** a marker of the chosen type is dropped at the resolved coordinate using `PlacePointTool.MarkerCreator`. The marker behaves identically to one placed via ATAK's own long-press radial menu (long-press → edit / delete / route / details).

| Row | Options |
|---|---|
| 1 | **Move only** (default), **Waypoint**, **GoTo Pin**, **Point of Interest** |
| 2 | **Friendly**, **Hostile**, **Neutral**, **Unknown** |
| 3 | **Custom Icon** (feature 003) + 3 empty cells for visual balance |

Mode selection persists across plugin restarts via `pref_goto_marker_mode` (since feature 003 — ADR-0010 D5). `MOVE_ONLY` is the install-time default so a fresh install never auto-drops markers.

### Custom Icon picker (feature 003)

Selecting **Custom Icon** reveals a picker preview row immediately below the marker-mode grid. The row has three render states:

```
─────────────────────────────────────────────
[ icon thumb 32dp ]  iconset name           ← Populated state
─────────────────────────────────────────────
[ empty 32dp ]       Pick an icon           ← Empty state
─────────────────────────────────────────────
[ empty 32dp ]       Pick an icon
                     Selected icon no       ← FallbackHint (one-shot, FR-009)
                     longer installed.
─────────────────────────────────────────────
```

Tapping the row opens a modal **two-step picker dialog** (`CustomIconPickerDialog`):

- **Step 1 (iconset list)**: ATAK's `UserIconDatabase.getIconSets(...)` enumerated and sorted alphabetically. Each row reads `<iconset name> (<icon count>)`. Tap an iconset to advance.
- **Step 2 (icon grid)**: 3-column grid of 48 dp thumbnails, each labelled with the icon's filename (extension stripped). Tap an icon to commit. Title shows "Icons in `<iconset>`"; a Back button returns to step 1.

The dialog re-opens contextually per FR-003: if the operator already has a selection AND its iconset still exists, the dialog opens at step 2 of that iconset (one-tap re-pick). Otherwise it opens at step 1.

### Data sourcing

- The plugin contributes **zero** image assets. Every icon shown comes from `UserIconDatabase` — including the 5 iconsets ATAK ships out-of-box (`falconview`, `incident_management`, `ps_air`, `responder`, `wildfire`) plus the `Military` seed set and anything the operator self-loaded through ATAK's iconset manager.
- New iconsets installed mid-session arrive via `IconsMapAdapter.ICONSET_ADDED` broadcasts; the receiver invalidates `IconResolver`'s cache and notifies the open dialog (if any).
- Iconsets removed mid-session fire `IconsMapAdapter.ICONSET_REMOVED`. If the operator's currently-selected icon belonged to the removed iconset, the page atomic-clears (`pref_goto_marker_mode` ← `MOVE_ONLY` + remove `pref_goto_last_iconset_path` in a single `apply()`) and queues a one-shot fallback hint (FR-009).

### Submit-path integration

When the operator submits with `CUSTOM_ICON` selected and a valid icon picked, the existing `PlacePointTool.MarkerCreator` chain in `submitOk()` gains one builder call:

```java
new MarkerCreator(dest)
    .setUid(UUID.randomUUID().toString())
    .setType("b-m-p-s-m")         // generic Spot Map pin — no affiliation semantics
    .setCallsign(callsign)
    .setIconPath(currentSelection.iconsetPath())   // NEW
    .placePoint();
```

`PlacePointTool` automatically writes the `IconsetPath` marker metadata and routes the marker into the User Icons MapGroup, so the dropped marker is indistinguishable from one the operator placed via ATAK's own marker tools.

### Threading

`IconResolver` queries `UserIconDatabase` synchronously (SQLite + `BitmapFactory.decodeByteArray`), so the dialog uses a `Executors.newFixedThreadPool(2)` for iconset enumeration and per-cell bitmap fetch. Results post back to the main thread via a `Handler(Looper.getMainLooper())`. The pool is lifecycle-managed: lazily started on first picker open, shut down in `dismissCustomIconPicker()` on drop-down close.

### Corrupt-bitmap policy (FR-010a)

Rows whose bitmap fails to decode are silently filtered out at picker bind time via `CustomIconPickerDialog.filterRenderable(...)`. The adapter's `getCount()` reflects only renderable rows; `getView()` is never invoked for skipped ones. Each skip is logged at WARN with the iconset UID + filename; no operator-visible toast or placeholder.

### Constitution VI compliance

Every entry point in `gotopage/` (`onReceive`, `onClick`, `onItemClick`, `getView`, `onCancel`, worker `Runnable.run`) wraps its body in `try/catch (Throwable)` and logs via `Log.w(TAG, ..., t)`. The `safeClick(tag, body)` helper in `TwCoordGotoView` is the standard wrap for click listeners; the dialog's adapter `getView` overrides and `BroadcastReceiver.onReceive` use inline guards.
