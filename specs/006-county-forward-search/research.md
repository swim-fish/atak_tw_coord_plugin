# Research: County-Scoped Forward Address Search

**Branch**: `006-county-forward-search` | **Date**: 2026-05-30

## Anchoring discipline

Same as features 004/005: every SDK / ATAK-CIV class referenced below is
cross-checked against **both** `javap -public` on `../ATAK-CIV-5.7.0.3-SDK/main.jar`
(canonical local source of truth) and the upstream Java source at
`github.com/TAK-Product-Center/atak-civ` (`main`). When the two disagree, the
bundled jar wins (`feedback-plan-phase-code-anchoring` memory).

Data claims below are anchored to `docs/research/county-scoped-forward-search.md`
and its executed verification scripts (`scripts/verify_polygon_in.py`,
`scripts/verify_research_claims.py`, `scripts/measure_tw_central.py`) run against
the `tw-central-full.zip` 10:50 build, SHA-256 `28a10e7d…83a9821`.

---

## R1 — WKB MultiPolygon parser: hand-rolled vs JTS

**Decision**: Ship a **hand-rolled minimal WGS84 WKB-MultiPolygon parser +
ray-cast point-in-polygon**, in `address/geo/`. Hold `org.locationtech.jts` in
reserve as a documented fallback only if a future nationwide boundary build
introduces a geometry shape the minimal parser doesn't cover.

**Rationale**:

- **Already proven.** `scripts/verify_polygon_in.py` implements exactly this
  algorithm (little-endian, OGC type 6 MultiPolygon / type 3 Polygon, exterior +
  interior rings, ray-cast PIP) and resolves **8/8 reference points** correctly
  against the real `townships.sqlite` (台中車站→台中市西區, 鹿港→彰化縣鹿港鎮,
  斗六→雲林縣斗六市, 南投市→南投縣南投市, offshore→no county). This de-risks the
  one genuinely new capability before any Java is written.
- **Zero APK cost.** The generator emits geometry via
  `shapely.geometry.MultiPolygon(...).wkb` — a fixed, known shape. The parser is
  ~80 lines; no dependency, no per-ABI native lib. This matches the project's
  lean-APK discipline (005 R5 rejected SQLCipher on size; the fallback SQLite is
  opt-in/lazy).
- **Worst-case is fine.** The largest geometry (宜蘭縣, ≈534 KB / 102 polygons /
  34,109 vertices) parses without trouble; the R*Tree prunes to 1–3 candidate
  polygons per lookup, so only those are parsed per query.
- **Defensive by construction (Constitution VI).** The blob is treated as
  untrusted input — malformed bytes (truncated, wrong type code, big-endian,
  Z/M flags) recover to "no locality" rather than throwing. JVM tests cover these.

**Alternatives considered**:

- **`org.locationtech.jts` `WKBReader` + `Geometry.covers`**: robust and
  battle-tested, handles every WKB variant, but ~1 MB APK for capability we don't
  need against a fixed generator output. Kept as a reserve: if R1's minimal parser
  ever fails a real geometry, swap `BoundaryGeometry`'s internals for JTS behind
  the same interface (the contract is parser-agnostic). Rejected as the default on
  size + the 8/8 proof.
- **`android.database` spatial functions / SpatiaLite**: not available in the
  ATAK native SQLite build; would require a heavyweight extension. Rejected.

**Implementation note**: factor the parser as `WkbMultiPolygonParser` (bytes →
`BoundaryGeometry`) + `PointInPolygon` (point + rings → boolean) so each is unit-
testable in isolation and the JTS swap, if ever needed, is contained to
`BoundaryGeometry`.

---

## R2 — Boundary facade query shape (Tier-1 polygon-in)

**Decision**: `TownshipBoundaryFacade.localityAt(lat, lon)` runs the generator's
Tier-1 reference query (data-contract §5.1): R*Tree bbox prefilter on
`townships_rtree`, then WKB `covers` on the candidate polygons, **level 8 first,
then level 7**, returning `(county_zh, name_zh, approx=false)` from the first
covering hit. `county_zh` comes back inline (no level-4 query on the happy path).
A level-4 polygon-in is the fallback only when `county_zh` is null (legacy OSM
schema — defensive; the MOI data always has it, verified 136/136 non-null).

**Rationale**:

- Mirrors `reverse_geocode.py::lookup_township`, which `verify_polygon_in.py`
  re-implemented and proved 8/8. Using the same shape keeps the plugin aligned
  with the generator's validated contract.
- Level-8-then-level-7 ordering matches the data: 縣轄鄉鎮市 (8) and 直轄市區 (7)
  partition the map; trying 8 first then 7 covers both without double-counting.
- R*Tree prune → 1–3 candidate polygons (measured) keeps the per-call WKB parse
  cost negligible, satisfying SC-002.

**Coastline tolerance**: `localityAt` takes an optional snap tolerance (default
off for forward search's county/district pick; ~1 km for the reverse readout). If
no polygon strictly covers the point, snap to the nearest level-7/8 polygon within
tolerance and set `approx=true`, mirroring the generator's `--snap-m`. County is
still resolved from the nearest polygon's `county_zh`. This handles the 台中港
環港路 / 南堤路 reclaimed-land case (research note §2, `boundary_exceptions.yaml`).

**Alternatives considered**:

- **Pure bbox, no `covers`**: would mis-assign points near district borders where
  bboxes overlap. Rejected — the WKB `covers` test is what makes the answer
  authoritative.
- **Precompute a point→district grid**: faster lookups but a large precomputed
  artifact and a generator change. Rejected — the R*Tree+covers path already meets
  the budget with zero extra data.

---

## R3 — Boundary DB mount location & lifecycle

**Decision**: Mount **one** boundary DB for the whole plugin (not per-county) at a
sibling of the active root: `active/_boundary/townships.sqlite` (leading
underscore so it can never collide with a county directory — county names never
start with `_`, and `ActiveDatasetRegistry.initFromDisk` already skips dot-dirs;
extend that skip to `_boundary`). Open it once at `TwCoordMapComponent.onCreate`
via a `TownshipBoundaryFactory` (primary `Databases.openDatabase`, falling back to
the 005 `FallbackSqliteFactory` path on R*Tree failure), keep it open for the
plugin lifetime, close at `onDestroyImpl`.

**Rationale**:

- The boundary layer is global, not per-county — one mount serves forward search
  and the reverse path for every county. Keeping it under the existing
  offline-address root means 005's staging/sweep/`AtakFileSystem` discipline and
  the R*Tree primary/fallback selection all apply unchanged.
- Open-once / close-on-exit matches the `ActiveDatasetRegistry` facade lifecycle
  (005 contract invariant 2): a ~10 MB DB held open costs little and avoids
  re-paying open cost on every pan.
- Import side: `townships.sqlite` arrives inside `base.zip` / `tw-central-full.zip`.
  The 005 `ZipExtractor` already streams every entry; R4 reclassifies the
  `townships.sqlite` entry so it lands in `_boundary/` instead of being skipped.

**Alternatives considered**:

- **Mount at the generator's `data/` root** (`tools/twcoord/data/townships.sqlite`):
  that's the eventual layout, but the active-root migration is explicitly
  out-of-scope for this feature (paired-but-independent). Staying under the 005
  root keeps blast radius small; the migration can move `_boundary/` later.
- **Per-county boundary copies**: wasteful and wrong — boundaries are national,
  not per-county. Rejected.

---

## R4 — `ZipEntryClassifier` reclassification

**Decision**: Add a `BOUNDARY` classification. `townships.sqlite` moves from
`SKIPPED_SUPPLEMENTARY` to `BOUNDARY`; the import path streams it into
`_boundary/townships.sqlite` (atomic-replace via the existing `AtakFileSystem`
move). `roads.sqlite` and `places-osm.sqlite` **stay** `SKIPPED_SUPPLEMENTARY`
(still out of scope). `timestamp.*` and `*.manifest.txt` stay skipped.

**Rationale**:

- Minimal, additive change to a well-tested classifier (005). The zip-slip /
  absolute-path defences already in `classify` are unchanged and still apply.
- Keeps the BatchImportReport honest: importing `tw-central-full.zip` now reports
  `townships.sqlite` as consumed (boundary) rather than "supplementary skipped",
  which is the user-visible signal that forward search is now enabled.

**Alternatives considered**:

- **Detect `townships.sqlite` ad hoc in the extractor** without a classifier
  bucket: scatters the rule and breaks the report's single source of truth.
  Rejected — extend the enum.

**Test impact**: `ZipEntryClassifierTest` gains a `townships.sqlite → BOUNDARY`
case; the existing `roads`/`places-osm` → skipped cases stay green.

---

## R5 — District-scoped street query + matching (stage ③)

**Decision**: Extend `AddressDatabaseFacade` with a district-scoped street query:
`List<AddressCandidate> streetCandidates(districtKey, normalisedFragment, anchorLat,
anchorLon, limit)`. The implementation filters `places` by the district (via
`district_code` or `township` — R5a) where `street LIKE fragment || '%'` (or a
`LIKE '%fragment%'` substring incl. the `段` suffix), then app-side ranks the rows
by haversine distance to the anchor and returns the top `limit`. Folding
(臺↔台, fullwidth→halfwidth, 之→-) happens in `StreetTextNormaliser` applied to
**both** the query fragment and, where needed, the candidate `street`/`name`
before comparison.

**R5a — district key**: prefer **`township`** (the human district name, what the
funnel selected) for the filter, cross-checked against the boundary layer's
`name_zh`. `district_code` (MOI 7/8-digit) is carried for exactness but the
funnel speaks in names, and `places.township` is indexed-adjacent in the existing
schema. Confirm at impl time that `township` values match the boundary
`name_zh` (both are 臺→台 normalised per the data-contract; the generator's
`extract_townships.py` and the TGOS ingest share the normalisation table).

**Rationale**:

- The candidate set after district scoping is small (research note §3.3: even a
  whole-county 1 km bbox is ≈3k rows; a district is comparable-or-less), so a
  `LIKE` + app-side haversine rank is well inside budget — no FTS, no new index.
- The `段`-spanning substring rule is mandatory and data-proven: `street='中山路'`
  matches 10,412 rows but `LIKE '中山路%'` matches 23,065; `向上路` `=`→0 of 1,645
  (verified). The query MUST use prefix/substring, never `=` (FR-009).
- Glyph/width folding is mandatory and data-proven: 4,873 Taichung street rows are
  spelled `臺…`; without folding `台灣大道` misses the gazetted `臺灣大道` (FR-010).
- Reuses the existing per-county facade (`AtakDatabasesAddressDatabase`) and the
  005 fallback — no new SQLite path.

**Alternatives considered**:

- **`places_fts` MATCH**: unicode61 gives no width/variant folding and a global
  posting-list scan; pointless once scoped to one district. Rejected (research
  note §3.2). FTS stays reserved for a future no-anchor "search anywhere" mode.
- **Pre-normalise a folded column in the generator**: a generator change, and
  unnecessary — folding a few-thousand short strings app-side is sub-millisecond.
  Rejected (no generator change this feature).

---

## R6 — Reverse-path county scoping + on-device measurement harness

**Decision**: `AddressSubsystem` gains a bound `TownshipBoundaryFacade`. When
present, `runLookup` first resolves the county via `localityAt` (with the ~1 km
snap tolerance for coastal robustness), then queries **only** that county's
facade from the registry (falling back to the existing
`lookupAcrossAllCounties` fan-out only when the boundary facade is absent or
returns no county — e.g. boundary data not installed). For SC-003/004/005, reuse
the 005 R9 Espresso harness (`AddressLookupPerformanceTest`, `BatchImportRssTest`)
extended with: a forward-search flow test, a `LocalityDetectionTest` asserting the
reference-point correctness (the 8/8 set) and that no place DB file handle opens
during a locality-only pan, and a reverse-scoping equality test (boundary-first
result == old globally-nearest result for in-county points).

**Rationale**:

- For an in-county point the globally-nearest record **is** in that county
  (county boundaries are exact and addresses lie inside them), so querying only
  that county returns the identical result while doing strictly less work — this
  is why FR-014 can promise "no operator-visible behaviour change". The fan-out
  remains as the safety net for the no-boundary-data case (FR-015/FR-017).
- The feature-006 roadmap memory mandates **real-device** p50/p95 + memory as a
  release gate, not emulator-only. The 005 harness already brackets `lookup(...)`
  with `System.nanoTime` over 100 pans and samples RSS via `Debug.MemoryInfo`;
  extending it is cheaper than a new harness.
- Locality-detection's "opens no place DB" claim (SC-002/SC-007) is testable by
  asserting the registry's facades report zero queries during a locality-only pan
  (instrument the facade or assert via a spy in the JVM test; on-device, assert no
  new DB file descriptor under the place-DB paths).

**Alternatives considered**:

- **Keep the fan-out, add forward search only**: leaves the "don't touch all
  counties" win on the table for the most-run path and forgoes sharing the
  boundary facade. The operator (Session 2026-05-30) chose to do both together.
  Rejected.
- **Emulator-only timing**: violates the roadmap memory's explicit gate.
  Rejected.

---

## Phase 1 design gate (Constitution Check re-run)

After Phase 1 outputs (data-model.md, contracts/, quickstart.md), the gate is
re-run. The new pure-logic surfaces — `WkbMultiPolygonParser`, `PointInPolygon`,
`BoundaryGeometry`, `TownshipBoundaryFacade`, `ForwardSearchController`,
`StreetTextNormaliser`, the district-scoped street query — are JVM-testable
without device (geometry + funnel are pure; the facade tests run against a fixture
`townships.sqlite` via xerial). The new host-callable surfaces
(`ForwardSearchReceiver`, `ForwardSearchTool`, button/keypad listeners, the
boundary query reachable from worker threads) are all covered by the Constitution
VI audit task in the Polish phase. No new Constitution violations surface in
design.

**Post-design Constitution Check: ✅ Pass (no Complexity Tracking entries needed).**

The WKB parser introduces **no new dependency** (hand-rolled, R1), so the only
APK delta is plugin code. If a future build forces the JTS fallback, ADR-0018
records the size trade-off then.
