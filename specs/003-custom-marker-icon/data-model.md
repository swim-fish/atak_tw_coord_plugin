# Phase 1 Data Model: Custom Marker Icon on the GoTo Page

**Feature**: `003-custom-marker-icon`
**Date**: 2026-05-17
**Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

This document captures the entities, persisted state, and state machines introduced by this feature. The plugin owns *no* iconset storage (FR-004); all icon data is read from the host's `UserIconDatabase`. The plugin's own state is two `SharedPreferences` keys plus per-view runtime state.

## 1. Entities

### 1.1 `MarkerMode` (extended enum)

The existing 8-value enum [`com.atakmap.android.twcoord.gotopage.MarkerMode`](../../app/src/main/java/com/atakmap/android/twcoord/gotopage/MarkerMode.java) gains one ninth value.

| Constant | CoT type | Drops marker? | Custom icon? |
|---|---|---|---|
| `MOVE_ONLY` | (none) | No | No |
| `WAYPOINT` | `b-m-p-s-m` | Yes | No |
| `FRIENDLY` | `a-f-G` | Yes | No |
| `HOSTILE` | `a-h-G` | Yes | No |
| `NEUTRAL` | `a-n-G` | Yes | No |
| `UNKNOWN` | `a-u-G` | Yes | No |
| `SPI` | `b-m-p-s-p-i` | Yes | No |
| `MISSION_POINT` | `b-m-p-w-GOTO` | Yes | No |
| **`CUSTOM_ICON`** (new) | `b-m-p-s-m` | Yes | **Yes** |

`CUSTOM_ICON` uses the same CoT type as `WAYPOINT` (`b-m-p-s-m`, generic Spot Map user-placed pin) per [research R7](./research.md#r7--cot-type-for-the-custom-icon-marker-mode-fr-012) and FR-012. The on-map identity is fully expressed by the icon path on the marker, not by the CoT affiliation.

Two new helper methods on `MarkerMode`:

- `boolean requiresIconPath()` — returns true only for `CUSTOM_ICON`.
- `boolean isCustomIcon()` — returns true only for `CUSTOM_ICON` (convenience alias for switch arms).

### 1.2 `IconSelection` (new value class)

A new immutable value class `com.atakmap.android.twcoord.gotopage.IconSelection`:

| Field | Type | Description |
|---|---|---|
| `iconsetPath` | `String` (non-null) | Canonical `<iconsetUid>/<group>/<filename>` per [R4](./research.md#r4--iconset-path-format). |
| `iconsetUid` | `String` (non-null, derived) | `iconsetPath.split("/")[0]`. |
| `iconsetName` | `String` (non-null) | Display name pulled from `UserIconSet.getName()` at construction time. Cached to avoid re-querying for label rendering. |
| `iconFileName` | `String` (non-null, derived) | Last token of `iconsetPath.split("/")`. |
| `iconId` | `int` (non-negative) | `UserIcon.getId()` — primary key for `getIconBitmap(int)` fast-path. |

Construction is always via `IconSelection.from(UserIcon, UserIconSet)` — the constructor pulls fields from the SDK objects to guarantee consistency. The class is immutable and `equals`/`hashCode` use `iconsetPath` alone.

### 1.3 `PickerPreviewState` (new sealed/closed enum + payload)

A union representing what the picker preview area renders at any moment. Implemented as a sealed-ish hierarchy (Java `record`-style flat class with discriminator) to match the existing `ParseResult` pattern in feature 002:

```text
PickerPreviewState
├── Empty                           // FR-002 state (a) — "Pick an icon"
├── FallbackHint(IconSelection?)    // FR-002 state (b) — "Selected icon no longer installed."
└── Populated(IconSelection)        // FR-002 state (c) — thumbnail + label
```

`FallbackHint` carries an optional `previous` field with the just-cleared selection (for logging only; never rendered). The hint is one-shot per FR-009: rendering it transitions the page's in-memory state to `Empty` so a subsequent switch away-and-back to CUSTOM_ICON shows `Empty`, not the hint.

### 1.4 `PickerDialogState` (transient, not persisted)

Drives which page the picker dialog shows. Lives only while the dialog is open.

| State | Renders | Entered when |
|---|---|---|
| `IconsetList` | step-1 `ListView` of all `UserIconSet`s | Dialog opens with no current selection, or with a current selection whose iconset has been removed, or via the dialog's back button at step 2 |
| `IconList(UserIconSet)` | step-2 `GridView` of icons in the selected iconset | Operator picks an iconset at step 1, or dialog opens with a current selection whose iconset still exists (FR-003 re-open rule) |

Transitions: `IconsetList → IconList` via item click; `IconList → IconsetList` via back button; either → closed via icon pick (commits selection), system back, or outside tap (both treated as cancel).

## 2. Persisted state (SharedPreferences)

All keys live in the existing `tw_coord_settings` file alongside the seven `pref_goto_*` keys from feature 002. Two new keys:

| Key | Type | Default | Lifecycle |
|---|---|---|---|
| `pref_goto_marker_mode` | `String` (enum name) | `"MOVE_ONLY"` | Written on every successful `MarkerMode` change in CUSTOM_ICON-aware contexts; restored on every `bind(InputPageState)` |
| `pref_goto_last_iconset_path` | `String` (canonical 3-token path) | `null` | Written on icon pick in the picker dialog; restored on `bind`; **cleared atomically** when FR-009 fallback fires |

The `pref_goto_marker_mode` key replaces the in-session-only `markerMode` field in `TwCoordGotoView` (feature 002 behaviour — see ADR-0009 D1). The other 8 modes also now persist, by virtue of being written through the same path. The "Move only is the safe default" property is preserved because `MOVE_ONLY` is the install-time default ([R9](./research.md#r9--persistence-extending-preferencestore)).

**Atomicity**: when FR-009 fires (persisted icon path no longer resolves), `pref_goto_marker_mode` is set to `"MOVE_ONLY"` and `pref_goto_last_iconset_path` is removed in a single `SharedPreferences.Editor.commit()` call — no intermediate state where mode = CUSTOM_ICON but path = null is ever observable.

**No schema versioning**: SharedPreferences is schemaless; new keys are absent on upgrade from a v1.0.0 install, in which case the default (`MOVE_ONLY` / `null`) applies and the page behaves identically to feature 002 until the operator first picks `CUSTOM_ICON`.

## 3. Runtime (per-view) state in `TwCoordGotoView`

Three new fields, none persisted:

| Field | Type | Owner | Cleared when |
|---|---|---|---|
| `currentSelection` | `IconSelection?` | `TwCoordGotoView` | Set by picker pick; nulled by FR-009 fallback |
| `pendingFallbackHint` | `boolean` | `TwCoordGotoView` | Set true when FR-009 fires; consumed (set false) the first time the operator switches *to* CUSTOM_ICON after firing |
| `pickerDialogState` | `PickerDialogState?` | `TwCoordGotoView` | Non-null only while the dialog is open |

The picker preview's render is purely a function of `(markerMode, currentSelection, pendingFallbackHint)` per [R8](./research.md#r8--ui-integration-into-the-existing-goto-page):

- `markerMode != CUSTOM_ICON` → preview area hidden
- `markerMode == CUSTOM_ICON && pendingFallbackHint` → `FallbackHint` (consume `pendingFallbackHint = false` after rendering)
- `markerMode == CUSTOM_ICON && currentSelection != null` → `Populated(currentSelection)`
- `markerMode == CUSTOM_ICON && currentSelection == null` → `Empty`

## 4. Page-open / bind flow

Pseudo-code for the additions to `TwCoordGotoView.bind(...)` — implemented additively to feature 002's existing bind:

```text
bind(state):
    [existing feature-002 restore: activeTab, coordinate drafts, recent list, locale refresh]

    persistedMode = prefs.getGotoMarkerMode()      // default MOVE_ONLY
    persistedPath = prefs.getGotoLastIconsetPath() // default null

    if persistedMode == CUSTOM_ICON and persistedPath != null:
        if UserIcon.IsValidIconsetPath(persistedPath, requireDatabaseMatch=true, pluginContext):
            icon  = UserIcon.GetIconFromIconsetPath(persistedPath, bBitmap=false, pluginContext)
            set   = UserIconDatabase.instance(pluginContext).getIconSet(icon.getIconsetUid(), false, false)
            currentSelection = IconSelection.from(icon, set)
            markerMode = CUSTOM_ICON
        else:
            // FR-009 fallback path — atomic clear
            prefs.editor()
                .putString(pref_goto_marker_mode, MOVE_ONLY)
                .remove(pref_goto_last_iconset_path)
                .commit()
            markerMode = MOVE_ONLY
            currentSelection = null
            pendingFallbackHint = true
            log.w(TAG, "Persisted iconsetPath no longer resolves; cleared: " + persistedPath)
    else if persistedMode != CUSTOM_ICON:
        markerMode = persistedMode
        currentSelection = null
    else:
        // persistedMode == CUSTOM_ICON but path is null — defensive fallback
        markerMode = MOVE_ONLY
        currentSelection = null

    applyMarkerModeUI()    // updates 9 radios + preview area
```

The validity check uses `IsValidIconsetPath` per [R5](./research.md#r5--persisted-icon-validity-detection-fr-009-fallback). The lookup runs on the main thread because it's a single indexed SQL query — sub-ms on the reference device. If on-device measurement shows it bursting the page-open frame budget, we move it to the executor introduced in [R10](./research.md#r10--off-main-thread-discipline).

## 5. Submit flow (additions to `submitOk`)

Per FR-007 and [R3](./research.md#r3--marker-placement-with-a-custom-icon), the existing `MarkerCreator` chain gains one new builder call:

```text
if markerMode.dropsMarker():
    iconPathToUse = (markerMode.isCustomIcon() && currentSelection != null)
                        ? currentSelection.iconsetPath
                        : null
    try:
        builder = new PlacePointTool.MarkerCreator(dest)
                      .setUid(UUID.randomUUID().toString())
                      .setType(markerMode.cotType())
                      .setCallsign(callsign)
        if iconPathToUse != null:
            builder.setIconPath(iconPathToUse)
        builder.placePoint()
    catch (Throwable t):
        log.w(TAG, "marker placement failed (" + markerMode + ")", t)
        // Constitution VI — recover silently; pan / persist / toast / close still run
```

`PlacePointTool.placePoint()` auto-applies the `IconsetPath` marker metadata and the User-Icons MapGroup routing when the path passes `IsValidIconsetPath(path, false, ctx)` per [R3 upstream evidence](./research.md#r3--marker-placement-with-a-custom-icon).

## 6. Cross-feature contracts preserved

- **Recent entries list (US4 of feature 002)** — entries continue to track only `(unit, rawValue, easting, northing, zone)`. `IconSelection` is **not** stored on `RecentEntry`. Tapping a recent row re-fills the coordinate but **does not** restore the marker mode — that comes from `pref_goto_marker_mode`. FR-014 of this feature locks this contract.
- **CoT-target broadcast** — the existing `TwCoordGotoIntents.ACTION_GOTO_NAV_COMPLETED` payload (`unit`, `lat`, `lon`, `raw_value`) is unchanged. Custom icon does not introduce a new outbound contract.
- **Auto Fill** — unchanged; Auto Fill writes coordinate values, not marker-mode or icon-path state.

## 7. Failure modes and recovery

| Failure | Detection | Recovery |
|---|---|---|
| Persisted iconset removed (FR-009) | `IsValidIconsetPath(persistedPath, true, ctx) == false` on bind | Atomic clear of both prefs; FR-009 hint queued via `pendingFallbackHint` |
| Picker step-1 query throws (DB lock, corrupt iconset) | `try/catch (Throwable)` around `getIconSets(...)` in worker task | Show step-1 empty-state row; log at WARN |
| Picker step-2 bitmap decode fails for some rows (FR-010a) | `getIconBitmap(int) == null` in adapter's worker task | Skip silently; row not added to grid; log at WARN per row |
| `placePoint()` throws | `try/catch (Throwable)` around the builder chain in `submitOk` | Pan / persist / toast / close all complete; only marker placement is lost; log at WARN |
| `ICONSET_REMOVED` broadcast for the currently-selected iconset arrives while the page is open | `BroadcastReceiver.onReceive` compares broadcast `uid` to `currentSelection.iconsetUid` | Fire FR-009 fallback immediately (clear both prefs, set `pendingFallbackHint`, repaint preview); no toast |
| `ICONSET_ADDED` broadcast | `BroadcastReceiver.onReceive` invalidates picker dialog's iconset-list cache if dialog is open at step 1 | Re-query and re-render iconset list; no operator-visible toast |

All recoveries comply with Constitution Principle VI (no failure escapes plugin entry points; host process is never put at risk).
