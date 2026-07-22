# ADR-0026: Integrate Native Address Entry and Consolidate Tools

**Status**: Accepted
**Date**: 2026-07-22
**Origin**: `/speckit-implement` on feature `codex/013-native-address-entry`

## Context

ADR-0023 added the Taiwan `CoordinateEntryPane` while retaining the custom
`TW Coord GoTo` workflow as a fallback. ADR-0009, ADR-0020, and ADR-0021
recorded the standalone Go To, forward-search, and search-settings workflows.
That coexistence leaves four public plugin entries in ATAK Tools and teaches
operators two ways to perform the same location-entry task.

Feature 013 requires Taiwan address entry to join Taipower, TWD97, and TWD67
inside the existing native Taiwan pane (FR-001–FR-022). It also requires
`TW Coordinates` to become the sole public Tools entry while preserving the
existing offline dataset manager and imported files (FR-023–FR-028).

Address lookup is asynchronous and database-backed, but ATAK's
`CoordinateEntryPane` getter and formatter are synchronous host callbacks.
The native pane therefore cannot directly reuse the stateful standalone
forward-search controller or perform lookup from a host callback. Dataset
replacement/removal must also not close a facade while lookup is using it.

## Decision

1. Keep one public ATAK coordinate-entry pane with stable UID
   `com.atakmap.android.twcoord.coordinateentry.taiwan`. Add Address as a
   fourth internal UI tab, not as a coordinate-system enum value or second
   top-level ATAK pane.
2. Use one canonical address draft with full and structured projections.
   Human edits start cancellable asynchronous forward lookup. Only a unique
   exact candidate resolves automatically; other candidates require explicit
   operator selection.
3. Introduce one UI-independent address lookup service shared by native entry
   and map readouts. Synchronous ATAK getters and formatters consume only
   completed cached resolution state or namespaced point metadata.
4. Lease immutable dataset snapshots during lookup. Import, replace, remove,
   invalidation, and close use conflicting write transitions and monotonic
   revision/closed-state gates.
5. Reverse lookup supplies an address label and provenance but preserves the
   exact host-provided WGS84 point. A nearby dataset record never silently
   moves geometry in Convert Coordinate or Auto Fill.
6. Expose only `TW Coordinates` in ATAK Tools. Retain the offline manager's
   action, receiver, importer, progress UI, and Import/Replace/Remove behavior
   as internal navigation from settings and native Address guidance.
7. Remove custom Go To and standalone forward-search tool/receiver/page wiring
   after their coordinate parsers, normalization, ranking, and query logic
   have moved to neutral packages and replacement parity is green.
8. Leave legacy custom Go To drafts, Recent entries, marker/icon choices, and
   other UI-only preferences inert. Do not delete them or let them affect the
   native workflow. Existing dataset paths, manifests, schemas, provenance,
   and applicable address settings remain byte-compatible.
9. Do not register redirects for stale custom Go To or forward-search actions.
   Unregistered actions are safe no-ops because ATAK 5.5 exposes no verified
   public action that opens Taiwan Address directly.
10. If address infrastructure cannot initialize, inject a closed/no-data
    lookup service so the three coordinate tabs and `TW Coordinates` remain
    available. Stop/dispose native entry before closing lookup, boundary,
    registry, facades, and executors.

This decision supersedes the coexistence/fallback part of ADR-0023 and the
active public-page decisions in ADR-0009, ADR-0020, and ADR-0021. Their
historical rationale remains unchanged. ADR-0015 and ADR-0017 continue to
govern offline storage, validation, and multi-county import behavior.

## Alternatives considered

- **Keep all legacy Tools entries as fallback.** Rejected because it preserves
  duplicate navigation and prevents the native workflow from becoming the
  single taught operator path.
- **Add Address to the coordinate-system enum.** Rejected because address
  lookup has loading, candidate, dataset, and failure states rather than a
  deterministic projection.
- **Reuse `ForwardSearchController` in the native pane.** Rejected because it
  owns a synchronous county/district funnel and nearest-candidate fallback
  that are unsafe for host confirmation.
- **Perform reverse lookup from `format()`.** Rejected because formatting is a
  synchronous host callback and must remain pure and cache-only.
- **Redirect stale actions to native Go To.** Rejected because no ATAK 5.5
  public contract selects the Taiwan Address tab, making a redirect
  version-sensitive and unverifiable.
- **Delete legacy preferences during upgrade.** Rejected because inert values
  are harmless and destructive cleanup adds migration risk without operator
  value.

## Consequences

- Operators learn one native location-entry path and one plugin settings/data
  path.
- The plugin gains an explicit asynchronous service, request revisions, and
  dataset read leases, but removes two UI-bound query owners and closes known
  stale-result/use-after-close races.
- Address unavailability cannot remove or invalidate coordinate conversion.
- Reverse address display cannot silently alter host geometry.
- Upgrade retains large offline datasets without re-import; legacy UI state is
  intentionally not migrated.
- ATAK 5.5 and 5.7.0.9 physical journeys, cross-context dialogs, performance,
  memory, and offline capture remain release gates rather than build claims.

## Links

- `specs/013-native-address-entry/spec.md` (FR-001–FR-036)
- `specs/013-native-address-entry/plan.md`
- `specs/013-native-address-entry/contracts/`
- ADR-0009, ADR-0020, ADR-0021, and ADR-0023 (partially superseded)
- ADR-0015 and ADR-0017 (offline data decisions retained)
- ADR-0022 and ADR-0024 (ATAK runtime/compile compatibility retained)
