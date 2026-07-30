# ADR-0029: Fill All Native Taiwan Pages from One Host Point

**Status**: Accepted
**Date**: 2026-07-30
**Origin**: User-requested refinement on feature `014-native-entry-input-ux`

## Context

ATAK owns one **Auto Fill** action for a registered `CoordinateEntryPane`.
The Taiwan pane contains four related pages: Taipower, TWD97, TWD67, and
Address. Activation already prepares all four pages from one host point, but
the original Auto Fill contract updated only the active page. An operator who
wanted to inspect or use another Taiwan representation therefore had to repeat
Auto Fill after every page switch.

The four results must refer to one exact WGS84 source. Taipower can be
unrepresentable for an outer-island point, TWD conversion is synchronous, and
offline Address reverse lookup is asynchronous. Address labeling must retain
the existing no-snap rule.

## Decision

A non-null host Auto Fill point refreshes all four Taiwan pages in one
programmatic operation:

- `TaiwanEntryController` atomically stages Taipower, TWD97, and TWD67 from
  the same WGS84 point without changing the selected page.
- `TaiwanCoordinateEntryPane` then starts Address reverse lookup from that
  exact WGS84 point.
- An unrepresentable Taipower result becomes unavailable independently while
  representable TWD97/TWD67 drafts and the Address lookup remain usable.
- Programmatic staging and reverse completion emit no human-coordinate-change
  callback and do not invoke ATAK confirmation.
- A null point retains the existing **Clear** contract: clear only the active
  Taiwan page and cancel Address lookup/candidates only when Address is active.
- Address resolution continues to return the exact host point rather than the
  nearest address-record point.

This decision partially supersedes ADR-0023 only for the scope of host Auto
Fill. ADR-0023's registration, lifecycle, formatter, horizontal-result, and
active-only Clear decisions remain accepted. ADR-0026's Address no-snap and
asynchronous lookup decisions remain unchanged.

## Alternatives considered

- **Keep Auto Fill active-only.** Rejected because every page would no longer
  be guaranteed to describe the same latest host point.
- **Switch pages and invoke Auto Fill four times.** Rejected because it adds
  repetitive operator work and can mix points if the host location changes.
- **Make Clear affect every page.** Rejected because clearing is an editing
  action on the visible draft; broad deletion would be surprising and is not
  required for consistent host-point preparation.
- **Snap Address to the nearest record.** Rejected because it silently changes
  geometry and violates ADR-0026.

## Consequences

- One Auto Fill action makes every Taiwan page ready for immediate switching.
- The selected page and Taipower presentation mode remain stable.
- Coordinate staging stays synchronous and atomic; Address may show its normal
  pending state until offline reverse lookup completes.
- Tests must distinguish all-page non-null Auto Fill from active-only null
  Clear and prove zero human-change callbacks.

## Links

- Feature 014 spec: FR-015, FR-024, FR-025
- Feature plan: `specs/014-native-entry-input-ux/plan.md`
- Feature contract:
  `specs/014-native-entry-input-ux/contracts/taipower-entry-contract.md`
- Related ADRs: [ADR-0023](0023-native-taiwan-coordinate-entry.md),
  [ADR-0026](0026-native-address-entry-and-tools-consolidation.md)
