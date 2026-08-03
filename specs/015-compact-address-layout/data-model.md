# Data Model: Compact Structured Address Layout

## Scope

This feature introduces no new persisted entity and changes no Address draft,
candidate, dataset, or coordinate model. It changes only how the existing
structured Address projection is grouped on screen.

## Existing projection fields

### Structured Address Projection

| Field | Meaning | Interaction | Layout position |
|-------|---------|-------------|-----------------|
| County/city | Existing selected or preserved county/city component | Opens the existing active-data selector when enabled | Row 1, column 1 |
| District/township | Existing selected or preserved district/township component | Opens the existing filtered selector when enabled | Row 1, column 2 |
| Road/locality | Existing editable road, section, lane, alley, or locality text | Single-line editor; Next targets address tail | Row 2, column 1 |
| House-number/floor | Existing editable number, subnumber, building, floor, or remaining tail text | Single-line final editor; Search/Done dismisses the keyboard | Row 2, column 2 |

Each row contains two equal-width field groups. Each field group retains one
visible label and one existing input/selector view.

## Existing state invariants

- The Address input mode remains either single-field or structured.
- Switching layout does not itself increment draft revision, schedule an
  additional lookup, select a candidate, or change the exact WGS84 host point.
- County/city and district/township enabled states continue to depend on active
  imported data and current locality selection.
- Road/locality and house-number/floor retain existing normalization,
  validation, lookup, focus, and editor-action behavior.
- Read-only, disposed, unavailable-data, lookup-in-flight, and resolved states
  remain owned by the existing controllers and pane adapter.

## State transitions

No state transition changes. The view continues to render the existing
single-field/structured mode switch, locality selection, text edit, lookup,
candidate selection, Auto Fill, Clear, read-only, locale replacement, and
dispose transitions.
