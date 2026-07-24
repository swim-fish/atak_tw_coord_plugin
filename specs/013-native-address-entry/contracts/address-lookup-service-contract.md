# Contract: Shared Offline Address Lookup Service

## Responsibility

The service is the only owner of runtime forward/reverse address query work.
It is independent of Android Views, ATAK pane classes, the retired forward
page, and map readout row presentation. It reuses imported datasets, boundary
data, ranking preferences, and existing database facades.

Conceptual interface:

```java
interface AddressLookupService extends AutoCloseable {
  LookupHandle forward(ForwardAddressRequest request,
                       Callback<ForwardAddressResult> callback);
  LookupHandle reverse(ReverseAddressRequest request,
                       Callback<ReverseAddressResult> callback);
  LookupHandle localities(LocalitySelectorRequest request,
                          Callback<LocalitySelectorResult> callback);
  AddressAvailability availability();
  void addAvailabilityListener(AvailabilityListener listener);
  void removeAvailabilityListener(AvailabilityListener listener);
}
```

The exact Java signature may vary during implementation, but every invariant
below is mandatory.

## Threading and cancellation

- Boundary and database work runs on one bounded plugin worker, never the ATAK
  main thread.
- Completion is dispatched through an injected UI/completion dispatcher.
- Every request receives a unique request ID and captures dataset revision.
- `LookupHandle.cancel()` is idempotent and guarantees no later callback
  delivery for that handle.
- Newer work may coalesce or cancel queued older work for the same consumer.
- Native interactive work is prioritized or coalesced ahead of stale widget
  refresh work so the shared single worker cannot starve active entry.
- `close()` is idempotent and monotonic: reject new requests, cancel queued
  work, suppress callbacks, detach availability listeners, and release worker
  ownership.
- Ordinary query failures become failure results. Fatal VM errors are not
  swallowed; only documented version-skew linkage failures are contained at
  host boundaries.

## Dataset ownership

- Every query opens a `DatasetReadSession` and closes it in all outcomes.
- Read sessions pin referenced facades until query completion.
- Import, replace, remove, tamper invalidation, and registry close use the
  conflicting write transition and increment dataset revision.
- Availability listeners run after write ownership is released and are
  isolated from each other.
- A batch import completion arriving after coordinator/registry close cannot
  register data; any newly opened facade is closed immediately.
- The service does not change dataset directories, manifests, schema versions,
  provenance fields, or import atomicity.
- Active county choices come from the registry snapshot. District choices come
  from distinct non-empty `township` values read through the selected county's
  leased facade.
- Locality discovery results are cached by county plus dataset
  identity/revision. Any registry write transition invalidates affected cached
  snapshots before availability notification.

## Forward lookup

### Input

- Immutable parsed AddressDraft snapshot.
- Optional WGS84 anchor used only for deterministic secondary ordering.
- Existing result-ordering preference.
- Positive bounded result limit.
- Request, draft, session, and dataset revision identity.

### Processing rules

1. Normalize query and candidate values by the same deterministic rules.
2. Resolve county/district by longest known name; never infer solely from the
   first `市`/`區` character.
3. Query only the selected/resolved active county dataset when county is
   deterministic.
4. Match road/locality and tail through parameterized bounded category
   queries. `EXACT`, `TEXT_PREFIX`, `NUMERIC_NEAREST`, `DISTANCE`, and
   `FALLBACK` are each hard-capped at 20 SQL rows with explicit deterministic
   ordering. `DISTANCE` uses the current map centre when it is valid and is
   omitted otherwise.
5. Classify exactness independently from distance. Canonical full-address
   equality is exact. If the query omits only a TGOS village/neighbourhood
   prefix, exactness may also use matching county, district, street/section,
   address tail, and unclassified suffix.
6. If any exact candidates remain after classification and deduplication,
   return only those candidates. Otherwise allocate the visible list as six
   text-prefix, eight numeric-nearest, four map-distance, and two fallback
   rows.
7. Deduplicate across categories by stable candidate identity, then backfill
   in text-prefix, numeric-nearest, distance, and fallback order until the
   visible 20-row cap is reached. When distance is unavailable, its allocation
   is backfilled from the other categories.
8. Preserve each category's deterministic semantic ordering and use
   normalized address and candidate identity as stable final tie-breakers.

### Outcomes

- `NO_DATASET`: no applicable valid imported county data.
- `NO_MATCH`: valid query shape but no candidate.
- `CANDIDATES`: bounded candidates with explicit match kind and provenance.
- `FAILURE`: contained boundary/database/validation failure.

Only one deduplicated `EXACT` candidate is eligible for automatic resolution.
A missing house-number match never falls back to a nearest partial candidate
as an exact result. Multiple candidates that differ by an omitted
village/neighbourhood prefix remain explicit candidates. Candidate retrieval
never performs an unbounded county or road-family scan.

## Reverse lookup

### Input

- Exact host/query WGS84.
- Existing bounded radius.
- Request, session, and dataset revision identity.

### Outcomes

- `FOUND` carries both exact query WGS84 and nearest record candidate.
- `NO_DATASET`, `NO_MATCH`, and `FAILURE` retain the exact query but no
  resolution.

Reverse selection is deterministic: shortest haversine distance wins; an
equal-distance row with the shorter stored `number` wins next; equal lengths
use the lowest stable dataset `id`. All SQLite backends use the same ordered
bounding-box query so identical coordinates cannot fall back to engine return
order.

The caller displays the record address but returns the query WGS84 to ATAK.
Distance/confidence remains presentation metadata and never changes geometry.

## Full-address normalization and parsing

- Apply Unicode NFKC-compatible width normalization.
- Remove or standardize supported separators without changing proper names.
- Fold `台`/`臺` for matching while preserving a readable canonical display.
- Convert Chinese numbers only when syntactically adjacent to supported
  address units.
- Normalize numeric subnumber separators such as `-`, `~`, and `之` without
  altering nonnumeric punctuation arbitrarily.
- Resolve complete county/city and district/township names by longest prefix.
- Resolve road/locality against active data; parse the remaining unit tail.
- Always preserve raw input and unclassified text.

## Locality selector snapshots

### Input

- Selector kind: county or district.
- Selected canonical county for a district request.
- Optional validated WGS84 map-centre anchor.
- Pane/session generation and current dataset revision.

### Processing rules

1. Load and validate the bundled postal catalog off the main thread. A valid
   catalog carries authority, version/effective/retrieval dates, source URLs,
   source hashes, unique county/district names, three-digit prefixes, and
   bounded coordinates.
2. Build county availability only from active registry entries.
3. Build district availability only from distinct imported `township` values
   for the selected active county. Postal or boundary-only rows never create
   selectable values.
4. Normalize names for joining without changing canonical display spelling.
5. Order counties by Chunghwa Post selector position and districts by numeric
   three-digit prefix plus official source order. Append unmatched active
   values in normalized-name order and report their count.
6. Resolve the optional anchor through cached/off-thread township polygon
   containment. Promote at most one active county and, for a district request,
   at most one active district belonging to the selected county.
7. Do not substitute a nearest stored address when polygon locality is absent.
8. Publish an immutable ordered snapshot. Completion after cancellation,
   service close, pane/session replacement, or dataset revision change is
   stale and cannot be applied.

### Outcomes

- `READY`: immutable choices, ordering provenance/fallback state, optional
  promoted locality, and captured identities.
- `NO_DATASET`: no active county or no active selected county.
- `LOADING`: optional controller presentation while an uncached district
  snapshot is prepared; never a synchronous wait.
- `FAILURE`: contained catalog/database/boundary failure. Active imported
  choices use deterministic normalized-name fallback when safely obtainable;
  full-address lookup remains available.

An already-open selector holds one accepted snapshot and never receives
in-place row reordering. Reopening may use a newer map anchor or completed
dataset revision.

## Result provenance

Every candidate/resolution carries county, data date, schema version, source,
and imported-file SHA-256 when available. The service never reports stronger
accuracy or provenance than the imported data provides.

## Performance

- Local normalization/mode projection: 100 ms p95 and worst-case budget.
- Forward/reverse result visibility: 1,000 ms median and 2,000 ms p95 over at
  least 100 real-device lookups.
- Process RSS remains at or below 200 MiB during the established five-minute
  boundary-plus-two-counties scenario.
- Postal catalog load/index and the largest active county district refresh
  complete within 1,000 ms p95 on the bounded worker.
- A prepared county/district snapshot opens within 100 ms p95 and all retained
  catalog/snapshot objects add no more than 1 MiB in the two-county scenario.
- Candidate lists and queues are bounded; no new county facade is opened solely
  to render the Address tab before a query requires it.
