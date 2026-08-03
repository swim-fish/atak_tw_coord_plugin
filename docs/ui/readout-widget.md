# UI: on-map readout widget

**Surface owner**: `com.atakmap.android.twcoord.TwCoordWidget`
**Anchors**: `RootLayoutWidget.BOTTOM_LEFT` (MAP), `BOTTOM_RIGHT` (ME), `TOP_RIGHT` (TGT)
**Phases**:
- US1 — initial widget shipped 2026-05-16 (T034 / T035 / T036)
- US2 (feature 004) — per-anchor address row added under each coordinate row (T036–T037)

## Anatomy

```
┌──────────────────────────────────┐        ┌──────────────────────────────────┐
│  TGT TWD97: 306,963m 2,769,619m  │ ← TOP  │                                  │
│  台中市西區美村路一段 600 號      │  RIGHT │                                  │
└──────────────────────────────────┘        │                                  │
                                            │                                  │
┌──────────────────────────────────┐        ┌──────────────────────────────────┐
│  MAP TWD97: 306,963m 2,769,619m  │ ← BTM  │  ME  TWD97: 306,124m 2,769,820m  │  ← BTM
│  台中市西區忠勤街 100 號          │   LEFT │  台中市西區美村路二段 50 號       │    RIGHT
└──────────────────────────────────┘        └──────────────────────────────────┘
```

Implementation: one `LinearLayoutWidget` per anchor. Each anchor's
column contains the coordinate row's `TextWidget` followed
(immediately, same anchor) by a sibling `TextWidget` for the address
row. Coordinate-row text + colour come from a `DisplayLine` produced
by `Formatter.format(...)`. Address-row text + visibility come from an
`AddressRowState` produced by the `AddressSubsystem` per-row resolver.

## Address row — added by feature 004 (US2)

A second sibling `TextWidget` per anchor renders the reverse-resolved
address for that anchor's coordinate. The row is **gated by two
independent conditions**: per-row Settings toggle ON, and a dataset is
active. All toggles default to off; the upgrade is visually zero-change
until the operator opts in.

| State        | Visible text                          | Colour                        |
|--------------|---------------------------------------|-------------------------------|
| `Hidden`     | (row not drawn — `setVisible(false)`) | n/a                           |
| `Loading`    | localised `widget_address_loading`    | `@color/address_row_text`     |
| `Text`       | `display_name` (fullwidth Taiwan addr)| `@color/address_row_text`     |
| `EmptyState` | localised `widget_address_empty_state`| `@color/address_row_text`     |

`@color/address_row_text` is `#FFBBBBBB` — a muted neutral that has
lower visual weight than the coordinate row's white. The colour is the
same regardless of the underlying coordinate-row state so the operator
reads the address as a single contextual line, not as an alert.

### Direction arrow prefix (ADR-0020 F6, since v1.3.0)

A `Text` row now prefixes an **8-point compass arrow** (↑ N, ↗ NE, → E,
↘ SE, ↓ S, ↙ SW, ← W, ↖ NW) pointing from the anchor's query point to the
resolved nearest record, so the operator can tell which way the actual
address point lies (e.g. `↗ 台中市西區…`). The bearing
(`CompassDirection.bearingDegrees`) is quantised to the nearest of 8 fixed
glyphs (`CompassDirection.arrowGlyph`) because a plain ATAK `TextWidget`
can't rotate a glyph the way the forward-search list does. The arrow is
**omitted when the record is within 3 m** of the query point (no
meaningful direction) and **precedes** the existing `~` / `~~` confidence
marker. Applies to all three rows (MAP / ME / TGT); each uses its own
anchor as the query point.

Per-row gating rules (`contracts/address-resolver.md § State derivation`):

| Toggle | Dataset active | Coord state | Address state |
|--------|---------------|-------------|---------------|
| off    | (any)         | (any)       | `Hidden`      |
| on     | no            | (any)       | `Hidden`      |
| on     | yes           | `NO_FIX` / `NO_PERMISSION` | `Hidden` (no point looking up — there is no coord) |
| on     | yes           | `OUT_OF_RANGE` / `OK` | `Loading` until lookup completes, then `Text` or `EmptyState` |

`AddressSubsystem` debounces inbound coordinate events at 250 ms per
row so a pan/zoom burst does not fan out into one query per
`MapEvent.MAP_SCROLL`. The lookup itself runs on a single-thread
scheduled executor (`twcoord-address-lookup`); results post back to the
UI thread via `MapView.post(...)`.

## State variants (coordinate row)

| State           | Visible text                                           | Colour                |
|-----------------|--------------------------------------------------------|-----------------------|
| `OK`            | `<label> <unitTag>: <value>`                           | white (`0xFFFFFFFF`)  |
| `OUT_OF_RANGE`  | `<label> <unitTag>: <localised-out-of-range>` plus a   | amber (`0xFFFFA000`)  |
|                 | second line `(<lat>, <lon>)` with WGS84 to 6 decimals  |                       |
| `NO_FIX`        | `<label>: <localised-no-fix>`                          | grey  (`0xFFB0B0B0`)  |
| `NO_PERMISSION` | `<label>: <localised-no-permission>`                   | grey  (`0xFFB0B0B0`)  |

`labelPrefix`, `unitTag`, and the state words are pulled from
`strings.xml` (currently English / Traditional Chinese / Japanese);
the widget itself contains no English literals.

## Render protocol

- `TwCoordWidget.render(mapCentreLine, selfLine, targetLine)` MUST be
  called on the ATAK UI thread (3-arg coordinate render, per-anchor).
- `TwCoordWidget.renderAddresses(mapAddr, meAddr, targetAddr)` is the
  parallel call that updates the three address rows; same UI-thread
  requirement. Wrapped internally in `try/catch (Throwable)` per
  Constitution VI.
- The widget keeps the previously-rendered `DisplayLine` for each row
  and short-circuits when both arguments equal-by-fields the previous
  values — this prevents redundant invalidate calls when the map
  redispatches `MAP_MOVED` events that did not actually change the
  centre coordinate at our display precision.
- In `TwCoordWidget.render(mapCentreLine, selfLine, targetLine)`, a `null` MAP
  or ME argument leaves that coordinate row unchanged; a `null` TGT argument
  clears only the selected-target coordinate row.
- In `TwCoordWidget.renderAddresses(mapAddr, meAddr, targetAddr)`, a `null`
  address argument is rendered as `AddressRowState.hidden()`, so that row is
  cleared/hidden rather than preserved.
- `TwCoordWidget.clearTarget()` atomically hides both TGT coordinate and
  address rows while preserving MAP and ME.
- ATAK replaces the active `MAP_CLICK` listener stack while a marker radial menu
  is open. The component therefore handles both direct background `MAP_CLICK`
  events and ATAK's stable `com.atakmap.android.maps.HIDE_DETAILS` selected-item
  dismissal broadcast. Pending TGT address work is cancelled before the widget
  is hidden. Each row has an atomic generation checked inside every UI-posted
  legacy/shared address emission, so even a runnable queued before dismissal
  cannot restore the marker. Ordinary cleanup `RuntimeException` is contained
  and logged; fatal JVM conditions are not swallowed by this boundary.

Compatibility evidence: `javap -public` against the pinned ATAK-CIV 5.7.0.9
SDK confirms the public `AtakBroadcast` register/unregister contract. ATAK-CIV
5.5.1.1 source shows
[`MenuLayoutWidget` sending `HIDE_DETAILS` on background map interaction](https://github.com/TAK-Product-Center/atak-civ/blob/6cefd4c83371789937a6a30aa4d7e81d84b82374/atak/ATAK/app/src/main/java/com/atakmap/android/menu/MenuLayoutWidget.java#L110-L120)
and
[`CoordOverlayMapComponent` registering/discarding the same action for the native overlay](https://github.com/TAK-Product-Center/atak-civ/blob/6cefd4c83371789937a6a30aa4d7e81d84b82374/atak/ATAK/app/src/main/java/com/atakmap/android/coordoverlay/CoordOverlayMapComponent.java#L24-L44).

## Background

Currently `TextWidget.TRANSLUCENT_BLACK` — the SDK-provided dark
translucent background that gives ≥ 60 % contrast against the map.

## Anchor rationale

Each row sits at a distinct anchor — MAP at BOTTOM_LEFT, ME at
BOTTOM_RIGHT, TGT at TOP_RIGHT — so all three can render
simultaneously without the rows competing for the same screen real
estate. The original feature-001 single-corner layout was widened to
per-anchor in feature 002 / 003 as more rows came online; feature
004's address rows ride the same anchors as siblings beneath each
coordinate row.

ATAK shows a bezel of self-marker stats in the bottom-right of its
default build; our BOTTOM_RIGHT ME row sits above that bezel without
overlap thanks to ATAK's per-position layout-widget stacking.

## Out of scope for v1

- Drag to reposition the widget.
- User-configurable text size, colour, font.
- Per-row anchor independence (the address row always sits at the same
  anchor as its parent coordinate row).
- Distinguishing "out-of-region" from "in-region-but-unmapped" — both
  surface as the same `widget_address_empty_state` line per ADR-0014 D14.

These belong in future ADRs if requested.

## Screenshots

_TODO — capture during US2/US3 device acceptance walk (T044 / T057) and embed:_

- `readout-widget-ok.png` — OK state, all three coordinate rows.
- `readout-widget-out-of-range.png` — OUT_OF_RANGE state with WGS84 fallback line.
- `readout-widget-no-fix.png` — ME row in no-fix state.
- `readout-widget-zh-tw.png` — Traditional Chinese labels.
- `readout-widget-ja.png` — Japanese labels.
- `readout-widget-addr-loading.png` — feature 004 address row in `Loading…` state.
- `readout-widget-addr-text.png` — feature 004 address row resolved (台中市西區美村路一段 600 號).
- `readout-widget-addr-empty.png` — feature 004 address row "No address nearby" empty state (out-of-region pan or in-region unmapped).
- `readout-widget-addr-per-row-gating.png` — only ME row enabled, MAP / TGT rows have no address line beneath them.

## Related artefacts

- Spec: `spec.md` FR-001, FR-007, FR-009, FR-012, FR-019, SC-002 (feature 001 + 004).
- Contracts: `contracts/widget-overlay.md` (feature 001), `specs/004-offline-address/contracts/widget-address-rows.md`, `contracts/address-resolver.md`.
- ADR-0002 (TDAL not used — single in-plugin render path).
- ADR-0014 (feature 004 reconnaissance; R7 widget integration + R15 coverage-gap honesty rule).
- ADR-0020 F6 (v1.3.0 — the 8-point compass arrow prefix on the resolved address row).
