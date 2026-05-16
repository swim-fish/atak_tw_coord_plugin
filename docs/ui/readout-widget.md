# UI: on-map readout widget

**Surface owner**: `com.atakmap.android.twpower.TwPowerWidget`
**Anchor**: `RootLayoutWidget.TOP_RIGHT` (top-right corner of the map)
**Phase**: US1 — initial widget shipped 2026-05-16 (T034 / T035 / T036)

## Anatomy

```
┌────────────────────────────────────────┐
│  MAP TWD97: 306,963m 2,769,619m        │  ← row 1 (map centre)
│  ME  TWD97: 306,124m 2,769,820m        │  ← row 2 (own position)
└────────────────────────────────────────┘
```

Implementation: `LinearLayoutWidget` (vertical) containing two
`TextWidget` children, anchored in the standard ATAK top-right
layout. Each row's text and colour come from a `DisplayLine`
produced by `Formatter.format(...)`.

## State variants

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

- `TwPowerWidget.render(mapCentreLine, selfLine)` MUST be called on
  the ATAK UI thread.
- The widget keeps the previously-rendered `DisplayLine` for each row
  and short-circuits when both arguments equal-by-fields the previous
  values — this prevents redundant invalidate calls when the map
  redispatches `MAP_MOVED` events that did not actually change the
  centre coordinate at our display precision.
- Either argument may be `null`, meaning "leave the previous row
  visible" (NOT "clear the row"). Clearing is achieved by passing a
  `DisplayLine` with empty value.

## Background

Currently `TextWidget.TRANSLUCENT_BLACK` — the SDK-provided dark
translucent background that gives ≥ 60 % contrast against the map.

## Anchor rationale

Top-right was chosen over bottom-right (the meshtastic_atak default)
because:

1. ATAK shows a bezel of self-marker stats in the bottom-right that
   the readout would compete with.
2. The top-right is mostly empty on default ATAK builds; if a future
   plugin puts something there, the per-position layout widget will
   stack ours below.

## Out of scope for v1

- Drag to reposition the widget.
- User-configurable text size, colour, font.
- Per-row anchor independence (both rows always travel together).

These belong in future ADRs if requested.

## Screenshots

_TODO — capture during US1 acceptance walk (T059) and embed:_

- `readout-widget-ok.png` — OK state, all three units.
- `readout-widget-out-of-range.png` — OUT_OF_RANGE state with WGS84 fallback line.
- `readout-widget-no-fix.png` — ME row in no-fix state.
- `readout-widget-zh-tw.png` — Traditional Chinese labels.
- `readout-widget-ja.png` — Japanese labels.

## Related artefacts

- Spec: `spec.md` FR-001, FR-007, FR-009, FR-012, SC-002.
- Contract: `contracts/widget-overlay.md`.
- ADR-0002 (TDAL not used — single in-plugin render path).
