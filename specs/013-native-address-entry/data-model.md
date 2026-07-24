# Data Model: Native Taiwan Address Entry

## 1. Native entry selection

### NativeEntryTab

UI-level selection for the one Taiwan pane:

```text
TAIPOWER | TWD97 | TWD67 | ADDRESS
```

This model is separate from the three-value coordinate-system model. Only the
first three values route to deterministic coordinate conversion.

### AddressInputMode

```text
FULL | STRUCTURED
```

- First use begins in `FULL`.
- Mode is session state, not a new persisted preference.
- Changing mode alone does not change the draft revision or notify ATAK of a
  location change.

## 2. Address draft

### AddressDraft

| Field | Type | Rule |
|-------|------|------|
| `rawAddress` | string | Exact human-entered/composed text; never null |
| `normalizedAddress` | string | Deterministic lookup form; never null |
| `components` | AddressComponents | Current structured projection |
| `unclassifiedText` | string | Preserved text that cannot be safely assigned |
| `mode` | AddressInputMode | Visible full or structured projection |
| `draftRevision` | long | Increments on semantic human edit or Clear |
| `validation` | AddressValidation | Current input classification |

### AddressComponents

| Field | Meaning |
|-------|---------|
| `countyCity` | Full recognized county/city name |
| `districtTownship` | Full recognized district/township name |
| `roadLocality` | Road, street, section, named locality, or area |
| `tail` | Lane, alley, house number, floor, room, and preserved suffix |

The full and structured controls are projections of one `AddressDraft`; they
are not independently persisted strings. Combining components follows the
field order above and appends unclassified text exactly once.

### AddressValidation

```text
EMPTY
PARTIAL
READY_TO_LOOKUP
LOOKUP_PENDING
NO_DATASET
NO_MATCH
AMBIGUOUS
RESOLVED
READ_ONLY
FAILURE
DISPOSED
```

Validation never implies a location unless state is `RESOLVED` and the
resolution revisions match the current session.

## 3. Dataset availability and read ownership

### AddressAvailability

| Field | Type | Rule |
|-------|------|------|
| `counties` | immutable set of strings | Valid imported county datasets currently usable |
| `boundaryAvailable` | boolean | Whether locality dictionaries/boundaries are mounted |
| `datasetRevision` | long | Increments after import, replace, remove, tamper removal, or close |
| `closed` | boolean | Monotonic terminal state |

There is no inactive-but-installed dataset state in this feature. Imported
valid county datasets are usable until replaced, removed, invalidated, or the
registry closes.

### DatasetReadSession

An ownership lease over an immutable county-to-dataset/facade snapshot.

- Opening fails safely after registry close.
- Lookup may use leased facades until the session closes.
- Import/replace/remove/registry close waits for conflicting readers before
  closing a facade.
- Listener notification occurs only after the write transition completes and
  its lock is released.
- The session never exposes mutation operations.

### DatasetIdentity

| Field | Purpose |
|-------|---------|
| `county` | Dataset scope |
| `dataDate` | Source release/date provenance |
| `schemaVersion` | Imported generator contract version |
| `fileSha256` | Imported file identity when available |
| `source` | Generator/source label |

The existing on-disk dataset and manifest formats remain unchanged.

### PostalLocalityCatalog

Read-only bundled ordering reference:

| Field | Type | Rule |
|-------|------|------|
| `schemaVersion` | integer | Positive supported asset schema |
| `datasetId` | string | Stable catalog identity |
| `retrievedOn` | local date | Date the official sources were retrieved |
| `sources` | provenance records | Authority, title, URL, version/effective/update dates, and source SHA-256 when available |
| `counties` | ordered list | Chunghwa Post county selector order; unique normalized names |
| `districtCount` | integer | Declared count equal to validated district rows |

Each county entry contains:

| Field | Rule |
|-------|------|
| `name` | Canonical Traditional Chinese county/city name |
| `selectorOrder` | Unique positive position in the official county selector |
| `districts` | Ordered postal-locality rows for that county |

Each postal-locality row contains:

| Field | Rule |
|-------|------|
| `name` | Canonical district/township name |
| `postalPrefix` | Exactly three decimal digits |
| `postalOrder` | Stable county-local position after numeric prefix ordering and official tie-break |
| `center` | Optional published latitude/longitude metadata; never availability proof |

The catalog controls order only. A county or district becomes selectable only
through active imported data. Catalog load failure produces a deterministic
fallback rather than disabling full-address entry.

### ActiveLocalityCatalog

Small immutable searchable-locality view derived from one leased registry
snapshot:

| Field | Rule |
|-------|------|
| `datasetRevision` | Must match the registry revision used to derive it |
| `counties` | Active valid imported county names only |
| `districtsByCounty` | Distinct non-empty imported `township` names per active county |
| `unmatchedPostalCount` | Diagnostic count; unmatched active values remain usable |
| `closed` | Terminal snapshot created after service close; contains no choices |

District discovery runs on the bounded worker and is cached by county and
dataset identity/revision. It never changes the imported database or manifest.

### LocalitySelectorSnapshot

One immutable view used for one selector opening:

| Field | Rule |
|-------|------|
| `kind` | `COUNTY` or `DISTRICT` |
| `datasetRevision` | Active dataset revision captured for the list |
| `selectedCounty` | Required for `DISTRICT`; null for county list |
| `mapAnchor` | Valid WGS84 map-centre snapshot or null |
| `mapLocality` | Cached boundary county/district matching the anchor or null |
| `choices` | Active imported values in final display order |
| `postalCatalogAvailable` | Whether official ordering was applied |
| `createdGeneration` | Pane/session identity used to reject late callbacks |

Each choice contains its canonical name, postal selector/order key when
matched, `promoted` flag, and deterministic fallback key. Exactly zero or one
choice may be promoted. The list never mutates or reorders after publication.

### LocalityValueState

Derived presentation state for the canonical AddressComponents locality
strings:

```text
EMPTY | SELECTABLE | UNAVAILABLE
```

- `SELECTABLE` means the exact normalized value exists in the current active
  selector snapshot.
- `UNAVAILABLE` preserves a parsed/pasted value that is not currently
  searchable; it remains visible but cannot yield a resolution until corrected
  or active data changes.
- This state never replaces or deletes the AddressDraft component text.

## 4. Lookup requests and results

### LookupIdentity

Every request/result carries:

| Field | Purpose |
|-------|---------|
| `requestId` | Unique request correlation |
| `sessionGeneration` | Host activation generation captured by controller |
| `draftRevision` | Address draft version captured at dispatch |
| `datasetRevision` | Dataset snapshot version captured at dispatch |

A result is accepted only when all four values still match current state.

### ForwardAddressRequest

| Field | Meaning |
|-------|---------|
| `identity` | LookupIdentity |
| `draft` | Immutable normalized AddressDraft snapshot |
| `anchorPoint` | Optional valid current map-centre WGS84 used only for the distance category and stable distance ordering |
| `ordering` | Existing candidate ordering preference |
| `limit` | Positive candidate display limit, hard-capped at 20 |

Forward retrieval uses five independently bounded categories:

```text
EXACT | TEXT_PREFIX | NUMERIC_NEAREST | DISTANCE | FALLBACK
```

Every category is capped at 20 SQL rows. Exact candidates are exclusive.
Otherwise the initial visible allocation is `6 / 8 / 4 / 2` for text prefix,
numeric nearest, distance, and fallback. Stable-identity deduplication and
semantic-order backfill produce at most 20 visible candidates. An unavailable
map-centre anchor removes the distance category rather than substituting a
fixed Taiwan coordinate.

### ReverseAddressRequest

| Field | Meaning |
|-------|---------|
| `identity` | LookupIdentity |
| `queryPoint` | Exact host-supplied WGS84 |
| `radiusMeters` | Existing bounded reverse radius |

### AddressCandidate

| Field | Type | Rule |
|-------|------|------|
| `candidateId` | stable string | Dataset identity plus stable record identity |
| `displayAddress` | string | Operator-facing stored address |
| `normalizedAddress` | string | Deterministic comparison form |
| `recordPoint` | WGS84 | Stored dataset coordinate |
| `matchKind` | AddressMatchKind | Exactness is explicit |
| `distanceMeters` | double/unknown | Ranking only; never establishes exactness |
| `county` | string | Candidate county |
| `datasetIdentity` | DatasetIdentity | Required provenance |

### AddressMatchKind

```text
EXACT | PARTIAL | FUZZY
```

`EXACT` means either canonical full-address equality or semantic equality after
omitting only a TGOS village/neighbourhood prefix while retaining an exact
county, district, street/section, address tail, and unclassified suffix. Only
one deduplicated `EXACT` candidate may resolve automatically; multiple
semantic matches remain ambiguous.

### ForwardAddressResult

```text
NO_DATASET | NO_MATCH | CANDIDATES | FAILURE
```

- `CANDIDATES` carries a deterministic list capped at 20 after category
  allocation, cross-category deduplication, and backfill.
- A unique exact list may be converted into a resolution automatically.
- Every other non-empty list remains `AMBIGUOUS` until human selection.
- A nearest partial candidate is never promoted to exact.

### ReverseAddressResult

```text
NO_DATASET | NO_MATCH | FOUND | FAILURE
```

`FOUND` carries the exact query point and the nearest record candidate. The
query point remains the resolved host location; the record supplies the
display address and provenance. Reverse ranking is stable by distance,
`number` length, then dataset `id`; rows sharing one coordinate therefore
produce the same address across supported SQLite backends.

## 5. Resolution and ATAK metadata

### AddressResolution

| Field | Meaning |
|-------|---------|
| `displayAddress` | Human-readable resolved address |
| `normalizedAddress` | Canonical normalized address |
| `resolvedPoint` | Point returned to ATAK |
| `recordPoint` | Dataset record point used for address display |
| `source` | `UNIQUE_EXACT`, `OPERATOR_SELECTED`, or `REVERSE_LABEL` |
| `datasetIdentity` | Source provenance |
| `identity` | Lookup/session/draft/dataset revisions |

Point rules:

- `UNIQUE_EXACT` and `OPERATOR_SELECTED`: `resolvedPoint = recordPoint`.
- `REVERSE_LABEL`: `resolvedPoint = exact host query point`; `recordPoint` may
  differ and must never silently replace it.

### Namespaced point metadata

The pane returns a new point metadata object and may attach these stable
plugin-owned keys:

```text
twcoord.address.display
twcoord.address.normalized
twcoord.address.resolution_source
twcoord.address.dataset.county
twcoord.address.dataset.data_date
twcoord.address.dataset.schema_version
twcoord.address.dataset.sha256
twcoord.address.record.latitude
twcoord.address.record.longitude
```

- Missing optional provenance values are omitted, not serialized as `null`.
- `format(point)` reads only this metadata and returns null when the display
  address is absent.
- Host-supplied metadata objects are never modified in place.

## 6. Native address session

### NativeAddressSession

| Field | Meaning |
|-------|---------|
| `generation` | Increments on each non-null host activation and disposal |
| `editable` | Host editability flag |
| `draft` | Current AddressDraft |
| `lookupState` | Idle/pending/result/failure state |
| `candidates` | Current accepted candidates |
| `resolution` | Current accepted AddressResolution or null |
| `lookupHandle` | Current cancellable request or null |
| `localitySnapshot` | Latest accepted immutable county/district selector snapshot or null |
| `localityHandle` | Current cancellable locality preparation request or null |
| `countyValueState` | Derived EMPTY/SELECTABLE/UNAVAILABLE state |
| `districtValueState` | Derived EMPTY/SELECTABLE/UNAVAILABLE state |
| `disposed` | Monotonic terminal flag |

### State transitions

```text
non-null host activation
  → cancel old request
  → increment generation
  → clear address state
  → reverse pending or no-dataset
  → reverse found/no-match/failure

human edit
  → increment draftRevision
  → clear candidates and resolution
  → debounce
  → forward pending
  → unique exact resolved | ambiguous | no-match | failure

candidate selection
  → verify identities
  → operator-selected resolution
  → human-change notification

mode switch
  → re-project same draft
  → derive locality value states from latest selector snapshot
  → no revision change and no lookup restart

county selector open
  → capture current map anchor + dataset revision
  → use accepted cached county snapshot or request refresh
  → publish one immutable ordered list
  → no in-place reorder

county selection
  → verify pane/session/dataset snapshot identities
  → update county component
  → clear incompatible district
  → preserve road and tail
  → increment draftRevision
  → clear candidates and resolution
  → request district snapshot and fresh forward lookup

district selector open
  → require selected active county
  → use accepted cached district snapshot or request refresh
  → publish one immutable ordered list
  → no in-place reorder

district selection
  → verify pane/session/dataset snapshot identities
  → update district component
  → preserve road and tail
  → increment draftRevision
  → clear candidates and resolution
  → fresh forward lookup

null activation / native Clear while Address active
  → cancel lookup and locality requests
  → increment draftRevision
  → empty Address state only

dataset import/replace/remove
  → increment datasetRevision
  → invalidate active-locality cache
  → cancel/ignore older lookup and selector results
  → invalidate existing resolution
  → retain canonical draft text and re-derive unavailable/selectable state
  → fresh lookup only after current input/session requests it

dispose
  → increment generation
  → cancel lookup and locality requests
  → clear listeners/resolution
  → DISPOSED terminal state
```

## 7. Existing and retired persistence

- Keep existing imported dataset directories, manifests, boundary files,
  dataset-status data, confidence settings, candidate ordering preference, and
  native last-coordinate-tab preference.
- Remove the custom Go To settings shortcut and code access to UI-only custom
  Go To preferences after parser extraction.
- Legacy custom Go To Recent, marker, icon, and field preference values remain
  inert in SharedPreferences. This feature neither reads nor deletes them.
- Address input mode and current draft are not persisted by this feature.
