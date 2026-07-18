# UI — Native Taiwan coordinate entry

**Feature**: 011-native-coordinate-entry

**Source**: `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml` and
`app/src/main/java/com/atakmap/android/twcoord/nativeentry/`

This pane adds Taiwan coordinate systems to ATAK's shared coordinate-entry
dialog. It is intended for the common "enter a coordinate and let ATAK perform
the host action" workflow. It does not replace the plugin's advanced **TW Coord
GoTo** page.

## Choose the appropriate workflow

| Goal | Use |
|---|---|
| Enter, Auto Fill, Clear, or Copy a Taiwan coordinate through ATAK's standard dialog | ATAK **Go To** → **Taiwan** |
| Choose marker affiliation, use ATAK's icon palette, or reuse one of ten Recent entries | Tools → **TW Coord GoTo** |

The two workflows persist their selections independently. The native pane never
changes advanced GoTo drafts, Recent entries, or marker mode.

## Anatomy

```text
┌────────────────────────────────────────┐
│ [ Taipower ] [ TWD97 ] [ TWD67 ]       │
│                                        │
│ ┌────────────────────────────────────┐ │
│ │ H7509 DB4016                       │ │
│ └────────────────────────────────────┘ │
│ 11 characters · main island only       │
│                                        │
│ — when TWD97 or TWD67 is selected —    │
│ Easting (m)                             │
│ ┌────────────────────────────────────┐ │
│ │ 306963                             │ │
│ └────────────────────────────────────┘ │
│ Northing (m)                            │
│ ┌────────────────────────────────────┐ │
│ │ 2769619                            │ │
│ └────────────────────────────────────┘ │
│ TM2 zone  [ 121 ] [ 119 ]              │
│ [119 accuracy advisory when applicable]│
│ [validation status]                    │
└────────────────────────────────────────┘
ATAK-owned controls: Auto Fill · Clear · Copy · action/confirm
```

The pane owns one outer `ScrollView`; no nested vertical scroller competes with
ATAK's dialog. Inputs use the same 20 sp text, field padding, selector height,
and TWD field gap as the advanced GoTo page.

## Coordinate systems

### Taipower

- Enter a 9-character (10 m) or 11-character (1 m) code. Auto Fill and Copy use
  the canonical 11-character form, for example `H7509 DB4016`.
- Coverage is the Taiwan main island. An outer-island Auto Fill clears the old
  draft and reports that the selected system cannot represent the supplied
  point.

### TWD97 and TWD67

- Enter integer easting and northing values in metres.
- Choose zone **121** for the main island or **119** for outer islands.
- Auto Fill determines the zone from the supplied point and replaces both
  fields atomically.
- TWD67 zone 119 shows an accuracy advisory because the available datum shift
  has a wider error budget there.

## ATAK-owned controls

The surrounding dialog owns its buttons and resulting action:

- **Auto Fill** calls the pane with ATAK's current point and replaces the active
  draft.
- **Clear** supplies no point and clears only the active Taiwan draft.
- **Copy** requests a canonical string without mutating the draft.
- The dialog's action consumes horizontal WGS84 metadata. The plugin does not
  invent altitude and does not move the map during parsing or formatting.

## Read-only and additional dialogs

ATAK may reuse the global pane in details or other location dialogs. When the
host supplies `editable=false`, fields, coordinate-system selectors, and zone
selectors remain visible but disabled. The supplied point can still be read and
formatted; attempted edits do not change the controller result or notify ATAK.

## Localisation and lifecycle

Strings are available in English, Taiwan Traditional Chinese, and Japanese.
When the plugin language changes while no native dialog is open, the registrar
replaces the pane immediately. If ATAK currently has the pane attached, refresh
waits for detach so an active host dialog is never mutated in place.

Registration failure, supported version skew, plugin unload, and stale queued
callbacks are contained by the registrar. ATAK's built-in panes and the
advanced **TW Coord GoTo** page remain usable.

## Compatibility

The plugin declares ATAK 5.5.0 as its minimum runtime. It compiles and is
currently validated with the ATAK-CIV 5.7.0.9 SDK. The checked-in exact ATAK
5.5 device matrix remains pending and is not implied by the successful SDK or
TPP build.
