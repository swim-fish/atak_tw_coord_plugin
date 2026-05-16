# UI — TW Coord GoTo input page

**Feature**: 002-tw-coord-goto | **Source**: `app/src/main/res/layout/tw_coord_goto.xml` + `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoView.java`

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

- It does not auto-create any marker (see ADR-0009 D1).
- It does not change the camera's zoom (Z) — only the pan target (X/Y) (ADR-0009 D2).
- It does not delegate to ATAK's native `GoToMapTool` (broken without GPS — ADR-0009 D3).
- It does not enforce a "single owned marker, move-not-create" invariant (no marker is created in the first place).
- It does not let the operator pick a marker type from inside the page. That choice belongs in ATAK's standard radial menu after the operator long-presses the destination.
