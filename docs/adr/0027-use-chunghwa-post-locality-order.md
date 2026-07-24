# ADR-0027: Use Chunghwa Post Locality Order

**Status**: Accepted
**Date**: 2026-07-24
**Origin**: `/speckit-implement` on feature `013-native-address-entry`
**Related decision**: ADR-0026 owns native Address entry and the shared
offline lookup service

## Context

Feature 013 adds county/city and district/township selectors to the structured
Address mode in ATAK's native Taiwan coordinate-entry pane. The choices must
be useful offline, stable between openings, and limited to imported data that
can actually resolve an address (FR-037–FR-046).

The imported address schema identifies administrative areas but does not carry
postal codes. Treating its `district_code` as a postal code would be
incorrect. Boundary data can identify the locality containing the current map
centre, but boundary-only rows do not prove that a corresponding address
dataset is searchable. Fetching locality data at runtime would also violate
the plugin's offline and no-network contract.

## Decision

Bundle a provenance-recorded Chunghwa Post locality catalog as an ordering and
metadata reference only:

- county/city baseline order follows the official postal-search selector;
- district/township baseline order follows ascending three-digit postal
  prefixes, then official source order and normalized name;
- active imported registry entries are the sole authority for county
  availability;
- distinct non-empty imported `township` values are the sole authority for
  district availability within a selected county;
- unmatched active values remain selectable after matched values in a
  deterministic normalized-name order;
- polygon containment may promote one active map-centre county or district to
  the first row, but nearest-address lookup never establishes locality;
- each opened selector receives one immutable map-anchor and dataset-revision
  snapshot and never reorders in place.

The catalog is generated from an explicitly downloaded official XML source by
`scripts/generate_chunghwa_post_postal_localities.ps1`. The checked-in asset
records authority, source titles and URLs, source/effective/retrieval dates,
and available SHA-256 values. A refresh requires reviewing the complete
semantic diff and rerunning catalog, selector-order, and active-intersection
tests.

Catalog absence or validation failure does not disable structured or
full-address entry. Active imported values remain available in deterministic
normalized-name order, with no runtime download attempt.

## Alternatives considered

### Sort imported `district_code`

Rejected because it is an administrative identifier rather than a postal
prefix and would misrepresent the requested ordering.

### Use every postal or boundary locality as a choice

Rejected because a locality without active imported address rows would appear
searchable but could never resolve.

### Fetch current postal data at runtime

Rejected because it adds network permission, availability failures, and an
operational dependency to an offline feature.

### Distance-sort the complete list

Rejected because positions would change as the map moves and conflict with the
learnable postal baseline. Only one polygon-confirmed active locality may be
promoted.

### Infer locality from the nearest imported address

Rejected because sparse data and boundary proximity can promote the wrong
county or district.

## Consequences

### Positive

- Operators see only address localities that current imported data can search.
- Baseline ordering is traceable, deterministic, and available offline.
- Map context improves reachability without making the remaining list move.
- Existing address bundle, manifest, and database schemas remain unchanged.
- Unmatched newer imported locality names stay usable while catalog drift is
  visible to diagnostics and future refresh work.

### Negative

- The repository now maintains a second locality data source whose authority
  is limited to ordering and metadata.
- Catalog updates require a reviewed source download, checksum verification,
  regeneration, and semantic-diff audit.
- Postal prefixes are not proof of current delivery service and must not be
  presented as delivery validation.
- Exact ATAK 5.5/current-device layout, latency, memory, and offline evidence
  remains a release gate.

## Links

- `specs/013-native-address-entry/spec.md` — FR-037–FR-046
- `specs/013-native-address-entry/plan.md` — Phase D
- `specs/013-native-address-entry/research.md` — R16–R20
- `specs/013-native-address-entry/data-model.md` — PostalLocalityCatalog and
  LocalitySelectorSnapshot
- `app/src/main/assets/address/chunghwa_post_postal_localities.json`
- `scripts/generate_chunghwa_post_postal_localities.ps1`
- `docs/adr/0026-native-address-entry-and-tools-consolidation.md`
- [Chunghwa Post postal-code search](https://www.post.gov.tw/post/internet/SearchZone/index.jsp?ID=208)
- [Chunghwa Post postal-code downloads](https://subservices.post.gov.tw/post/internet/Download/index.jsp?ID=220306)
