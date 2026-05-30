# Contract: Result Ordering (US1)

Behavioural contract for forward-search result ordering. Each clause is
observable and test-backed. Covers FR-001…FR-005, SC-001, SC-002.

## C1 — Two orderings offered
- **Given** a forward search returning ≥ 2 candidates,
- **Then** the page exposes a two-option control: **最相似** (`MOST_SIMILAR`) and
  **距離** (`DISTANCE`).
- *Test*: Espresso — both options visible above the candidate list.

## C2 — DISTANCE is the default & matches today
- **Given** no ordering has ever been chosen,
- **Then** results use `DISTANCE` (`distanceMeters` ascending) — identical to the
  pre-feature ordering returned by `ForwardSearchController.search(...)`.
- *Test*: unit — `StreetCandidateRanker.reorder(list, DISTANCE, fragment)`
  preserves the distance-ascending order of its input (identity for an
  already-distance-sorted list).

## C3 — Switching re-orders in place (no new search)
- **Given** a displayed candidate list,
- **When** the operator switches the ordering control,
- **Then** the same candidate set is re-sorted and redrawn **without** issuing a
  new county/database query, within < 1 s (SC-001).
- *Test*: Espresso — toggle changes row order; instrumentation asserts no new
  facade query; unit — re-rank is a pure in-memory sort.

## C4 — MOST_SIMILAR ranks by textual match, ties by distance
- **Given** candidates whose `street`/`displayName` match the query to differing
  degrees,
- **When** ordering is `MOST_SIMILAR`,
- **Then** higher similarity-band candidates precede lower ones, and equal-band
  candidates are ordered by ascending distance (FR-004).
- *Test*: unit — exact > prefix > substring > none; equal band → nearer first.

## C5 — Folding is consistent with search
- **Given** a query `台灣大道` and a candidate gazetted `臺灣大道…`,
- **When** similarity is scored,
- **Then** the 臺/台 + width fold (`StreetTextNormaliser`) applies so the match is
  recognised (not penalised as a mismatch).
- *Test*: unit — folded equality/prefix scores in the correct band.

## C6 — Preference persists across searches & sessions
- **Given** the operator selected an ordering,
- **When** they run a later search (same or new session),
- **Then** that ordering is the default (SC-002), read from
  `pref_search_result_ordering`.
- *Test*: unit — `PreferenceStore` round-trip + default; Espresso — re-open page
  reflects the saved choice.

## C7 — Ordering never changes the result set or pan behaviour
- **Given** any ordering,
- **Then** the set of returned candidates is unchanged, and tapping a candidate
  pans via the existing GoTo flow with the same compass arrow/distance (FR-005).
- *Test*: unit — both orderings return the same elements (set equality);
  Espresso — tap-to-pan unaffected.

## C8 — Empty/absent anchor degrades gracefully
- **Given** no computable distance anchor,
- **Then** `DISTANCE` falls back deterministically (insertion/displayName order)
  and `MOST_SIMILAR` still ranks by similarity — neither crashes nor blanks rows.
- *Test*: unit — null/empty-anchor path returns a stable ordering.
