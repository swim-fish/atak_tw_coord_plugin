# ADR-0028: Correct Taipower Subgrid Letter Ranges

**Status**: Accepted
**Date**: 2026-07-30
**Origin**: `/speckit-plan` on feature `014-native-entry-input-ux`

## Context

Feature 014 requires the native Taiwan coordinate pane to offer raw and guided
Taipower entry without changing coordinate meaning. During the input-contract
review, the parser and `TaipowerCode` constructor were found to accept A-J for
both 100 m letters even though the encoded cell is 800 m east-west by 500 m
north-south.

Dividing those dimensions into 100 m cells produces eight east-west indices
(`0..7`, A-H) and five north-south indices (`0..4`, A-E). The encoder already
produced only those ranges. Inputs using I/J east-west or F-J north-south are
not alternate spellings of the same cell: they overflow into a neighboring
subregion and are therefore noncanonical aliases.

The range evidence agrees across the Taipower description, the OSGeo grid
reference, Jidanni's reference implementation, and Sunriver's 800 m by 500 m
subdivision description. ADR-0001 remains authoritative for projection,
main-island region layout, and upstream provenance, but its inherited A-J
subgrid assumption is over-permissive.

## Decision

The authoritative Taipower domain accepts A-H for the east-west 100 m letter
and A-E for the north-south 100 m letter.

- `TaipowerParser` rejects I/J east-west and F-J north-south with the existing
  `BAD_LETTER` reason.
- `TaipowerCode` enforces the same constructor invariants.
- `TaipowerGrid` treats encoder indices outside `0..7` or `0..4` as invariant
  failures instead of clamping them.
- Raw and guided editors may retain A-Z attempts so the operator can see and
  correct the position-specific error, but the UI is not authoritative.
- Existing 9-character and 11-character canonical codes, precision, datum,
  region layout, and round-trip budgets do not change.

This decision partially supersedes only the A-J subgrid range inherited by
ADR-0001. All other ADR-0001 decisions remain accepted.

## Alternatives considered

- **Preserve A-J/A-J for compatibility.** Rejected because it accepts
  impossible cells and neighboring-subregion aliases.
- **Carry overflow into the next subregion.** Rejected because it silently
  changes operator input and becomes ambiguous at region boundaries.
- **Enforce the ranges only in the UI.** Rejected because paste, controller,
  parser, and future non-UI callers would remain over-permissive.
- **Silently discard invalid letters in guided fields.** Rejected because the
  invalid attempt would disappear instead of remaining available for
  correction.

## Consequences

- Every entry path shares one provenance-backed range rule.
- Previously accepted noncanonical aliases now fail closed.
- The parser reason vocabulary remains stable; presentation detail
  distinguishes east-west A-H from north-south A-E.
- Golden vectors remain unchanged, while new boundary and encoder-wrap vectors
  make the invariant explicit.

## Links

- Feature 014 spec: FR-021 through FR-025, SC-003, SC-004
- Feature plan: `specs/014-native-entry-input-ux/plan.md`
- Feature research: `specs/014-native-entry-input-ux/research.md` R6
- Related ADR: [ADR-0001](0001-coordinate-math-source.md)
- [Taipower Journal: pole coordinate structure](https://service.taipower.com.tw/tpcjournal/article/7441)
- [Taipower Heritage: power-coordinate introduction](https://service.taipower.com.tw/Collection/2009/2025/7769/blogPost)
- [OSGeo: Taiwan Power Company grid](https://wiki.osgeo.org/wiki/Taiwan_Power_Company_grid)
- [Jidanni `taipowergrid` reference](https://www.jidanni.org/geo/taipower/programs/taipowergrid)
- [Sunriver: Taipower grid subdivision](https://www.sunriver.com.tw/grid_taipower.htm)
