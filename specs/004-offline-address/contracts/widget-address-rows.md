# Contract: `TwCoordWidget` — address-row extension

**Modified class**: `com.atakmap.android.twcoord.TwCoordWidget`

**Source of truth for**: how the existing on-map readout widget gains a sibling address
`TextWidget` per coordinate row (ME, TGT, MAP), gated independently per-row.

## New widget state

Added fields, parallel to the existing three rows:

```java
private TextWidget mapAddrRow;
private TextWidget meAddrRow;
private TextWidget targetAddrRow;

private AddressRowState lastMapAddr = AddressRowState.Hidden.INSTANCE;
private AddressRowState lastMeAddr = AddressRowState.Hidden.INSTANCE;
private AddressRowState lastTargetAddr = AddressRowState.Hidden.INSTANCE;
```

Each new `TextWidget` is created via `newStyledTextWidget(...)` (the existing private factory),
with the same margins as its parent row (the row immediately above), and appended to the same
anchor (`BOTTOM_LEFT` for MAP, `BOTTOM_RIGHT` for ME, `TOP_RIGHT` for TGT).

## New public API

```java
/** Update the three per-row address states. Any null treated as Hidden. */
public void renderAddresses(AddressRowState map, AddressRowState me, AddressRowState target);
```

Behaviour:

- If the input state for a row equals `lastXxxAddr` (`equals`-style; sealed types implement
  value equality), skip the row's update — same coalesce-on-equal pattern as the existing
  `render(...)` method uses for `DisplayLine`.
- Otherwise update both the row's text and its `setVisible` flag:

| State | `row.setText(...)` | `row.setVisible(...)` |
|---|---|---|
| `Hidden` | (no change) | `false` |
| `Loading` | localised "Loading address…" | `true` |
| `Text(s)` | `s` (the address) | `true` |
| `EmptyState` | localised "No address nearby" | `true` |

The row's colour MUST be a muted neutral (e.g. `0xFFBBBBBB`) to read as secondary to the
coordinate row above. The exact value is in `res/values/colors.xml` (a new entry
`@color/address_row_text`); the Constitution III "design system" rule is satisfied by reusing
the project's existing dimension / colour resources.

## Visibility propagation

The existing `setVisible(boolean visible)` (which hides / shows all three coordinate rows)
MUST also propagate to the three address rows — when the widget is hidden, no address row
shows either. When the widget is shown again, each address row's visibility is governed by
its last-known state (so `Hidden` stays hidden, `Text(s)` reappears).

## Detach

`detach()` MUST remove all six rows from their anchors (three coordinate rows + three address
rows) and null out all six fields. Same null-check discipline as the existing code.

## Constitution VI wrapping

Both `render(...)` (existing) and `renderAddresses(...)` (new) MUST have outer `try/catch
(Throwable)` blocks logging via `Log.w(TAG, ...)` and swallowing. The existing method has
this guard already (added in feature 001); the new method gets the same shape.

## Test plan (`TwCoordWidgetAddressRowTest`, JVM via Robolectric)

| # | Test name | What it asserts |
|---|---|---|
| 1 | `attach_addsAddressRowsToEachAnchor` | After `attach()`, each anchor has 2 widgets (coord row + address row). |
| 2 | `renderAddresses_emptyToText_setsTextAndVisible` | `Hidden → Text("台北市…")` → row text matches, `isVisible() == true`. |
| 3 | `renderAddresses_textToEmpty_setsEmptyStateString` | `Text(...) → EmptyState` → row text equals the localised "No address nearby". |
| 4 | `renderAddresses_repeatedSameStateIsNoOp` | Two consecutive `Text("X")` calls cause one `setText`, not two (verified via spy). |
| 5 | `setVisibleFalse_hidesAddressRowsAlongsideCoordRows` | After `setVisible(false)`, all six rows have `isVisible() == false`. |
