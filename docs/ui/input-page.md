# UI — TW Coord GoTo input page

**Features**: 002-tw-coord-goto (base page) + 003-custom-marker-icon (ATAK-picker delegation button — ADR-0011 D8) + 010-goto-ui-redesign (compact-stacked visual redesign)
**Source**: `app/src/main/res/layout/tw_coord_goto.xml` + `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoView.java`

The TW Coord GoTo input page is a `DropDownReceiver` side-pane opened by the second Tools-menu icon (or the settings-page button). It is the *only* new user-facing surface this feature adds; everything downstream of Submit is pure ATAK behaviour the operator already knows.

> **Feature 010 note:** the page was restyled into the feature-008 "compact stacked" visual language (see the redesign section below). The structure, ids, and Submit/Auto Fill/marker/zone *behaviour* are unchanged; only layout, drawables, and three button labels changed. Where the older sections below describe per-tab Auto Fill buttons or flat-colour selection, the redesign section is authoritative.

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
│ [ Open ATAK icon menu ]                │  ← optional: delegate to ATAK
│                                        │
│ Recent                                 │  ← section header
│ ──────────────                          │
│ TAIPOWER  H7509 DB4016    [ Remove ]   │  ← rows, newest first
│ TWD97     302912 / 2770905 [ Remove ]   │
│ ...                                    │
└────────────────────────────────────────┘
```

## V1 compact-stacked redesign (feature 010)

Feature 010 brings the GoTo page into visual parity with the redesigned TW Addr Search and TW Offline Addr pages, resolving six pain points **with no coordinate-behaviour change** (parser, datum/projection conversion, Submit-and-pan, ATAK-picker hand-off, validation, and Recent are untouched — confirmed by the unmodified, passing GoTo unit suite).

| # | Pain point | Resolution |
|---|---|---|
| ① | Page too long | Single-column stack, segmented tabs, carded fields (`goto_input_bg`) |
| ② | Two equal submit buttons | Primary **Submit & go** enlarged/filled (`goto_submit_primary_bg`); ATAK-palette button is a ghost secondary (`goto_submit_secondary_bg`) |
| ③ | Marker cells too small | 8-cell 4×2 grid enlarged to ≥72 dp glove-friendly cells |
| ④ | Auto Fill small + hidden | Promoted to one header-level **Use map centre** button (`goto_autofill`) dispatching on the active tab |
| ⑤ | Projection zone unclear | 121/119 as a labelled segmented control (`goto_zone_cell_bg`); 119 shows the amber precision advisory (`goto_advisory_bg`) |
| ⑥ | Inconsistent system switching | Unified "tab → field" rhythm shared by Taipower / TWD |

**Selection styling is now drawable-driven.** Tab selection uses the `goto_tab_selected` pill (via `styleTab()`); marker-cell and zone selection use `state_checked` state-list drawables. `styleMarkerModeRadio()` no longer calls `setBackgroundColor` — removing an imperative view-mutate from the hot path (Constitution VI). All new `goto_*` drawables are concrete resource ids; no `android.R.attr.*` is passed to `setBackgroundResource`.

**Labels changed (ids unchanged):** `goto_btn_submit` → "Submit & go" (送出並前往 / 送信して移動); `goto_btn_autofill` → "Use map centre" (帶入地圖中心 / 地図中心を取得); `goto_btn_atak_picker` → "Use ATAK icon palette…" (改用 ATAK 圖示盤… / ATAK アイコンパレットを使う…); `goto_marker_mode_header` zh-TW "落點模式" → "標點模式". New `goto_taipower_help` hint sits under the Taipower field.

### Anatomy (redesigned)

```
┌────────────────────────────────────────┐
│ Coordinate input        [Use map centre]│  ← title + single header Auto Fill
│ ╭──────────┬──────────┬──────────╮     │
│ │ Taipower │  TWD97   │  TWD67   │     │  ← segmented tabs (pill on selected)
│ ╰──────────┴──────────┴──────────╯     │
│ ┌────────────────────────────────────┐ │
│ │ H7509 DB4016                        │ │  ← carded input(s) for the active tab
│ └────────────────────────────────────┘ │
│ 9-char (10 m) or 11-char (1 m) · …      │  ← goto_taipower_help
│ [inline error, red]   [119 advisory]    │
│ ────────────────────────────────────    │
│ Marker mode                             │
│ [ ▣ ][ ▢ ][ ▢ ][ ▢ ]   (≥72 dp cells)  │
│ [ ▢ ][ ▢ ][ ▢ ][ ▢ ]                    │
│ ┌────────────────────────────────────┐ │
│ │            Submit & go              │ │  ← primary, filled
│ └────────────────────────────────────┘ │
│ [    Use ATAK icon palette…    ]        │  ← ghost secondary
│ Recent … (unchanged)                    │
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

Below the Submit button, the page shows a 2-row × 4-column grid of marker-mode `RadioButton`s. Selecting one (other than **Move only**) means that when Submit fires, the camera pans **and** a marker of the chosen type is dropped at the resolved coordinate using `PlacePointTool.MarkerCreator`. The marker behaves identically to one placed via ATAK's own long-press radial menu (long-press → edit / delete / route / details).

| Row | Options |
|---|---|
| 1 | **Move only** (default), **Waypoint**, **GoTo Pin**, **Point of Interest** |
| 2 | **Friendly**, **Hostile**, **Neutral**, **Unknown** |

Mode selection persists across plugin restarts via `pref_goto_marker_mode` (since feature 003 — ADR-0010 D5 / ADR-0011 D8). `MOVE_ONLY` is the install-time default so a fresh install never auto-drops markers.

### "Open ATAK icon menu" delegation button (feature 003 — ADR-0011 D8)

Immediately below the Submit button the page shows a sibling button labelled **Open ATAK icon menu** (zh-TW: 「開啟 ATAK 圖示選單」; ja: 「ATAK アイコンメニューを開く」). It is enabled under the same condition as Submit — the active tab's input must parse cleanly — and the 8 marker-mode radios above do **not** gate it.

Tapping the button:

1. Persists the last-submitted `(unit, value)` tuple (same housekeeping as Submit).
2. Appends a `RecentEntry`.
3. Closes the TW Coord GoTo DropDown so ATAK's own DropDown can take the stage.
4. Calls `EnterLocationDropDownReceiver.getInstance(mapView).processPoint(GeoPointMetaData.wrap(geoPoint))`, handing the resolved coordinate to ATAK's native enter-location pane.
5. Fires the outbound `GOTO_NAV_COMPLETED` intent (same observability hook as Submit).

ATAK's enter-location pane then drops a marker at the typed coordinate using whichever pallet/icon the operator already has selected there. This is the **same picker the operator uses every other time they drop a custom-icon marker in ATAK** — there is no plugin-side UI to learn.

Originally (commits `7688624` MVP + `1861fb8` polish) feature 003 added a 9th "Custom Icon" radio + a bespoke two-step `CustomIconPickerDialog` reading directly from `UserIconDatabase`. That implementation was scrapped after on-device sideload surfaced both an `AlertDialog` 0×0 host-window bug and direct operator feedback that the dialog should "work like the Marker one — reuse the old UI design and logic". The Option B pivot (ADR-0011 D8) deletes ~1300 LOC of plugin-side picker code in favour of one button that hands the work to ATAK. See ADR-0011 D8 for the full rationale, file-deletion list, and discussion of the trade-offs (no inline preview, two-click instead of one-click) that this pivot accepts.

### Constitution VI compliance

Every entry point in `gotopage/` (`onReceive`, `onClick`) wraps its body in `try/catch (Throwable)` and logs via `Log.w(TAG, ..., t)`. The `safeClick(tag, body)` helper in `TwCoordGotoView` is the standard wrap for click listeners.
