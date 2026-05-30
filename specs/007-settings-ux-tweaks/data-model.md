# Phase 1 Data Model: Settings Page & Search/Storage UX Tweaks

**Feature**: 007-settings-ux-tweaks · **Date**: 2026-05-30

No persistent schema changes. The "data" here is (a) two new preference keys,
(b) one new enum, and (c) two transient value objects for storage display. All
are small and pure-Java testable. Key names + APIs are anchored to the verified
current tree.

---

## 1. ResultOrdering (enum)

`com.atakmap.android.twcoord.address.forward.ResultOrdering`

| Value | Meaning | Comparator |
|---|---|---|
| `DISTANCE` | nearest the anchor first (**default**) | `distanceMeters` asc — the order `ForwardSearchController.search(...)` already returns |
| `MOST_SIMILAR` | best textual match first | similarity desc → distance asc (tie-break, FR-004) |

- Persisted as its `name()` string in `PreferenceStore`.
- Default `DISTANCE` preserves shipped behaviour (FR-005).

---

## 2. Preferences (`prefs/PreferenceStore`, `pref_*` key convention)

Existing keys use the `pref_*` convention (e.g. `KEY_COORD_UNIT =
"pref_coord_unit"`). Two keys are added in the same style.

| Key constant | String value | Type | Default | Accessors | Requirement |
|---|---|---|---|---|---|
| `KEY_SEARCH_RESULT_ORDERING` | `"pref_search_result_ordering"` | String (`ResultOrdering.name()`) | `"DISTANCE"` | `getResultOrdering()` / `setResultOrdering(ResultOrdering)` | FR-001, FR-003 |
| `KEY_READOUT_VISIBLE` | `"pref_readout_visible"` | boolean | `true` | `isReadoutVisible()` / `setReadoutVisible(boolean)` | R1 (preserve visibility control) |

**Validation**: `getResultOrdering()` does a defensive `ResultOrdering.valueOf`
with fallback to `DISTANCE` on missing/unparseable (mirrors the existing
`readUnit()` → TWD97 fallback). Existing keys unchanged.

**`fireAll()` membership**: `pref_search_result_ordering` does **not** join the
widget-refresh `fireAll()` key set (the on-map widget doesn't depend on it); the
forward-search receiver reads it directly at open + on toggle, like the GoTo
keys. `pref_readout_visible` **does** propagate (it changes the widget) — either
via the `fireAll()` set or a dedicated apply in `prefListener` calling
`widget.setVisible(...)`.

**Cross-surface consistency** (FR-011): the search-page ordering toggle and the
settings `PanListPreference` both read/write `pref_search_result_ordering` — one
source of truth.

---

## 3. Similarity score (transient, ranking-internal)

Computed inside the new static `StreetCandidateRanker.reorder(List<AddressCandidate>
results, ResultOrdering ordering, String foldedFragment)` for `MOST_SIMILAR`;
never persisted. The existing `rank(List<Raw>, …)` method is untouched.

**Inputs**: `foldedFragment` = `StreetTextNormaliser.fold` of the street
fragment (the receiver already holds it); `foldedCandidate =
StreetTextNormaliser.fold(candidate.street() != null && !candidate.street().isEmpty()
? candidate.street() : candidate.displayName())` (mirrors the empty-street→area
coalescing of feature 006 shipped addition A7).

**Bands (high → low)** — deterministic integer; ties broken by `distanceMeters`:

| Band | Condition | Note |
|---|---|---|
| 4 | `foldedCandidate.equals(foldedFragment)` | exact match |
| 3 | `foldedCandidate.startsWith(foldedFragment)` | prefix |
| 2 | `foldedCandidate.contains(foldedFragment)` | substring (sub-rank by match index, earlier = higher) |
| 1 | otherwise | weak/none |

Within a band, fewer leftover chars (`len(candidate) − len(fragment)`) ranks
higher. Empty/blank fragment → all band 1, so `MOST_SIMILAR` degrades to distance
order (deterministic). `reorder` preserves the input list's size (it re-sorts the
already distance-capped list — no further cap). Implemented via an internal
`ScoredCandidate(AddressCandidate, int band, int subRank)` used only during sort;
the return type stays `List<AddressCandidate>`.

---

## 4. DatasetStorageSummary (transient value objects)

`com.atakmap.android.twcoord.address.DatasetStorageSummary(FileSystem fs,
ActiveDatasetRegistry registry)` — computed on demand. Uses the **existing**
`FileSystem` API (`activeCountyDir`, `boundaryDbFile`, `sizeOfDirectory`,
`exists`).

```
CountyStorage   { String countyZh; long bytes; }    // bytes = fs.sizeOfDirectory(fs.activeCountyDir(county))
BoundaryStorage { boolean present; long bytes; }     // present = fs.exists(fs.boundaryDbFile());
                                                     // bytes   = fs.sizeOfDirectory(fs.boundaryDbFile().getParent())
```

| Method | Returns | Source | Requirement |
|---|---|---|---|
| `perCounty()` | `List<CountyStorage>` (registry order) | for each `registry.snapshot().values()` → `fs.sizeOfDirectory(fs.activeCountyDir(c.county()))` | FR-012 |
| `boundary()` | `BoundaryStorage` | `fs.exists(fs.boundaryDbFile())` + `fs.sizeOfDirectory(boundaryDir)` | FR-013, FR-015 |

**Edge handling**: `sizeOfDirectory` returns 0 for an absent path (no error);
`present == false` ⇒ rendered as "未安裝"; no counties ⇒ `perCounty()` empty,
boundary still queried.

---

## 5. ByteCountFormatter (pure util)

`com.atakmap.android.twcoord.coord.ByteCountFormatter`

| Input (bytes) | Output |
|---|---|
| `0` | `0 B` |
| `1023` | `1023 B` |
| `1024` | `1.0 KB` |
| `12_900_000` | `12.3 MB` |
| `324 × 1024²` | `324.0 MB` |
| `≥ 1024³` | `x.y GB` |

Binary (1024) units; one decimal at KB and above; whole number at B. Pure — no
Android dependency — directly unit-testable and reusable by county rows and the
`_boundary` row.

---

## Relationships

```
PreferenceStore ──r/w──> pref_search_result_ordering ──> ResultOrdering
        ▲ (search-page toggle + settings PanListPreference)        │
        └──────────────────────────────────────────────┐          ▼
ForwardSearchController.search(fragment, limit)         │  StreetCandidateRanker.reorder(results, ordering, foldedFragment)
        → distance-ranked List<AddressCandidate>        │          │ StreetTextNormaliser.fold (shared w/ matching)
ForwardSearchReceiver caches results ─ toggle re-sorts ─┘          ▼  → reordered List<AddressCandidate>

OfflineAddressReceiver.renderActiveCountyList(ViewGroup)
        └─> DatasetStorageSummary(FileSystem, ActiveDatasetRegistry)
                ├─ perCounty()  → fs.sizeOfDirectory(activeCountyDir(county)) ─┐
                └─ boundary()   → fs.sizeOfDirectory(boundaryDir)            ──┴─> ByteCountFormatter.format → row text
```

No new tables, columns, files, or import-format changes.
