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
4. Match road/locality and tail through parameterized bounded queries.
5. Classify exactness independently from distance. Canonical full-address
   equality is exact. If the query omits only a TGOS village/neighbourhood
   prefix, exactness may also use matching county, district, street/section,
   address tail, and unclassified suffix.
6. Deduplicate by stable candidate identity.
7. Sort deterministically by requested ordering, then normalized address and
   candidate identity for ties.

### Outcomes

- `NO_DATASET`: no applicable valid imported county data.
- `NO_MATCH`: valid query shape but no candidate.
- `CANDIDATES`: bounded candidates with explicit match kind and provenance.
- `FAILURE`: contained boundary/database/validation failure.

Only one deduplicated `EXACT` candidate is eligible for automatic resolution.
A missing house-number match never falls back to a nearest partial candidate
as an exact result. Multiple candidates that differ by an omitted
village/neighbourhood prefix remain explicit candidates.

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
- Candidate lists and queues are bounded; no new county facade is opened solely
  to render the Address tab before a query requires it.
