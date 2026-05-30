# County-Scoped Forward Search & Locality Detection — Revised Plan

Date: 2026-05-30
Author: research note (pre-feature, re-plan)
Status: supersedes the locality/convergence stance of
[`forward-fuzzy-address-search.md`](./forward-fuzzy-address-search.md) §5.3
in light of the MOI authoritative boundary re-source.

Scope: how the plugin should (a) **cheaply identify which 縣市 / 鄉鎮市區 the
operator is in without opening any per-county address database**, and (b) run
**forward address search scoped to a single county, then progressively narrowed
by 鄉鎮市區**, with a UX that survives ATAK's `tools` panel constraints and
gloved-finger touch.

Companion docs:
- Forward/fuzzy feasibility + efficiency: [`forward-fuzzy-address-search.md`](./forward-fuzzy-address-search.md)
- Generator data contract v2: `atak-tw-address-generator/docs/data-contract.md`
- Plugin reverse-lookup internals: feature 004/005 (`AddressSubsystem`,
  `ActiveDatasetRegistry`, `SqliteAddressDatabase`)

> **Measured-data provenance.** Every figure tagged **(measured)** below was
> queried from the **`tw-central-full.zip` build compiled 2026-05-30 10:50**
> (148.97 MB; region `tw-central`, bbox `120.2,23.55,121.45,24.75`), the latest
> generator output that consumes the MOI authoritative boundary shapefiles
> (內政部 直轄市/縣市界線 + 鄉鎮市區界線, **release 1140318**). Township figures
> come from the bundled `townships.sqlite` (10.08 MB); place figures from
> `places-taichung.sqlite` (731,005 rows) / `places-changhua.sqlite` (426,690)
> / `places-osm.sqlite` (588,393). The measurement script is
> `scripts/measure_tw_central.py`; re-run it after any generator rebuild to
> refresh the appendix.
>
> **Verified 2026-05-30.** The zip measured here is unchanged since the 10:50
> build (SHA-256 `28a10e7d…83a9821`; no newer build exists). Every claim below
> was re-executed against it by `scripts/verify_research_claims.py` (counts,
> coverage, candidate sets — all PASS) **and** the load-bearing Tier-1
> polygon-in claim was *executed end-to-end* by `scripts/verify_polygon_in.py`
> (a dependency-free WKB-MultiPolygon parser + ray-cast PIP) — **8/8 known
> points resolved to the correct 縣市 + 鄉鎮市區**. See §8.

---

## 1. What changed in the data (the trigger for this re-plan)

The generator's `townships.sqlite` was **re-sourced from the MOI authoritative
boundary shapefiles** (release 1140318) — the *legal* ground truth — replacing
the earlier OSM-derived admin polygons. Two consequences drive the whole
design:

1. **`county_zh` is now inline on every 鄉鎮市區 polygon** (data-contract §3.2).
   A single R*Tree-pruned polygon-in hit on a level-7/8 row yields **both**
   halves of 「彰化縣鹿港鎮」 — no runtime admin_level=4 spatial join, no second
   query. The OSM pipeline had to *infer* county membership at lookup time;
   now it is a column read.

2. **The administrative hierarchy is authoritative and tiny.** **(measured)**
   For `tw-central` the boundary layer is **12 縣市 (level 4) + 31 直轄市區
   (level 7) + 105 縣轄鄉鎮市 (level 8) = 136 districts**, all in a **10.08 MB**
   file that ships inside `base.zip`. This is two orders of magnitude smaller
   than any single county's place DB (台中 324 MB), so it can be mounted
   unconditionally and queried on every map move.

**(measured) The 12 counties whose boundary overlaps `tw-central`:** 台中市,
彰化縣, 雲林縣, 苗栗縣, 南投縣, 新竹縣, 嘉義縣, 花蓮縣, 宜蘭縣, 新北市,
新竹市, 桃園市.

**(measured) Districts per county inside the bbox** (whole polygons kept on
*overlap*, so peripheral counties are partially represented — this is the count
the picker would show, not the county's national total):

| County | Districts in bbox | | County | Districts in bbox |
|---|---:|---|---|---:|
| 台中市 | 29 | | 新竹縣 | 9 |
| 彰化縣 | 26 | | 嘉義縣 | 8 |
| 雲林縣 | 20 | | 花蓮縣 | 8 |
| 苗栗縣 | 18 | | 宜蘭縣 | 2 |
| 南投縣 | 13 | | 新北市 / 新竹市 / 桃園市 | 1 each |

> The single-district peripheral counties (新北市=1, 桃園市=1, 新竹市=1) are an
> artefact of the bbox clip, not real data. A nationwide (`--region all`) build
> would carry every district; the plugin's picker must therefore key off
> *whatever townships.sqlite actually contains*, never a hard-coded 22-county
> table.

---

## 2. Requirement A — identify the current 縣市 / 鄉鎮市區 without loading 22 county DBs

**Problem.** "Which county am I in?" must be answered to (a) scope a forward
search and (b) decide which per-county place DB (if any) to even open. Opening
every `places-*.sqlite` to find out is exactly the cost we must avoid — the
Taichung file alone is 324 MB.

**Design.** Resolve `(county, township)` from `townships.sqlite` **alone**:

```
R*Tree bbox prefilter (townships_rtree)  →  WKB polygon-in (covers test)
   admin_level 8 first, then 7           →  county_zh comes back inline
```

This is the generator's Tier-1 reference query (data-contract §5.1,
`reverse_geocode.py::lookup_township`). Properties that make it the right
primitive here:

- **Touches only `base.zip`'s 10 MB townships layer.** No place DB is opened
  to answer the locality question. The 324/187 MB county files are opened
  *only after* a county is chosen and *only* for the address phase.
- **One hit gives both levels.** `county_zh` inline → no level-4 fallback query
  on the happy path (the level-4 polygon-in stays as a fallback only for the
  legacy OSM schema with NULL `county_zh`).
- **Authoritative.** Legal MOI boundaries, not an OSM approximation — the
  county/township shown to the operator matches the gazetted division.
- **Cheap enough to run on every map-centre move.** R*Tree prunes 136 polygons
  to a handful; the WKB `covers` test runs on 1–3 candidate multipolygons.

**Coastline caveat (carry forward).** The MOI 鄉鎮市區界線 follows the *legal*
coastline, which lags land reclamation. Points on reclaimed land (台中港
環港路 / 南堤路 in 龍井/梧棲) sit tens-to-hundreds of metres seaward of every
polygon, so the strict `covers` test returns no township. The county is still
unambiguous. Mirror the generator's `--snap-m` tolerance: if no polygon covers
the point, snap to the nearest level-7/8 polygon within ~1 km and flag the
result `approx`. (The generator ships `config/boundary_exceptions.yaml` keyed by
`boundary_release` so these known gaps are tracked, not silently masked.)

**Implementation cost — one genuinely new capability: WKB polygon-in on
Android.** Today's `SqliteAddressDatabase` only does R*Tree-bbox + haversine on
*points* (`places_rtree`); it never parses geometry. Township detection needs a
WGS84 **WKB MultiPolygon** parser + point-in-polygon test. Two options (a
plan-phase decision, parallel to feature 005's R5 SQLite-fallback choice):

| Option | APK cost | Risk |
|---|---|---|
| **`org.locationtech.jts` `WKBReader` + `Geometry.covers`** | ~1 MB | Robust, battle-tested; one more dependency. |
| **Hand-rolled minimal WKB-MultiPolygon reader + ray-cast PIP** | ~0 KB | Full control, no dep; must handle MultiPolygon + holes + the ring-winding correctly, and be fuzz-tested against the generator's shapely output. |

Given the plugin's lean-APK discipline (feature 005 R5 rejected SQLCipher on
size; the fallback SQLite is opt-in/lazy), the hand-rolled parser is attractive
**iff** we constrain the geometry to "MultiPolygon, WGS84, exterior+interior
rings" — which is exactly what `extract_townships.py` emits
(`shapely.geometry.MultiPolygon(...).wkb`). Recommend prototyping the minimal
parser first, with a JTS fallback held in reserve.

> **Proven viable (2026-05-30).** `scripts/verify_polygon_in.py` implements
> exactly this minimal path — a dependency-free WKB-MultiPolygon reader
> (little-endian, OGC 2D, type 6/3, exterior+holes) + ray-cast PIP — and runs
> the generator's Tier-1 query shape (R*Tree bbox → `covers`) against the real
> `townships.sqlite`. **8/8 known points resolved to the correct 縣市 + 鄉鎮市區**
> (台中車站→台中市西區, 鹿港→彰化縣鹿港鎮, 斗六→雲林縣斗六市, …) and an
> offshore point correctly returned "no county". Worst-case geometry is 宜蘭縣
> at 534 KB / 102 polygons / 34,109 vertices — a one-off parse per polygon that
> the R*Tree only reaches for 1–3 candidates per query, so device cost is
> negligible. **The hand-rolled parser is therefore the recommended path; JTS is
> a fallback only if a future nationwide build introduces a geometry shape this
> probe didn't cover.**

---

## 3. Requirement B — county-first forward search, narrowed by 鄉鎮市區

### 3.1 The funnel

```
①  Pick county        ②  Pick 鄉鎮市區       ③  Match street          ④  Pin
   所在地縣市      →     (list of that      →    substring incl. 段  →   distance-rank
   地圖中心縣市          county's districts)     in district-scoped       OR house number
   選單選取             (big tap targets)        candidate set            → GoTo
```

Each stage is driven by data the previous stage scopes down:

- **① County** — three sources, all resolving to one county string:
  1. **所在地縣市** — self-marker lat/lon → §2 townships polygon-in.
  2. **地圖中心縣市** — map-centre lat/lon → §2 townships polygon-in.
  3. **選單選取** — pick from the level-4 county list **read out of
     `townships.sqlite`** (not hard-coded). For `tw-central` that's the 12
     names in §1; nationwide it's all of them.
- **② 鄉鎮市區** — `SELECT name_zh FROM townships WHERE county_zh = ? AND
  admin_level IN (7,8) ORDER BY name_zh`. **(measured)** a county has ~8–29
  districts in this bbox — a single scrollable grid of large chips, no typing.
  For modes ①.1/①.2 the operator's own district is pre-highlighted (we already
  resolved it in §2), so the common case is a one-tap confirm.
- **③ Street** — only now is the **single** county's `places-<county>.sqlite`
  consulted, filtered to the chosen district via `district_code` (or the
  `township` column), then street-substring matched. Two data-shape rules from
  the companion doc are mandatory here and are *not* optional fuzziness:
  - **Substring incl. the `段` suffix** — `street='中山路'` matches **(measured)
    10,412** Taichung rows but `street LIKE '中山路%'` matches **23,065**; for
    向上路 the bare `=` matches **0** of **1,645** (every number lives under
    一段…九段). Match must be prefix/substring, never `=`.
  - **`臺`↔`台` fold** — road names keep their gazetted glyph; **(measured)**
    Taichung still stores **4,873** `臺…` street rows (臺灣大道). Fold both query
    and candidate to one glyph.
- **④ Pin** — district + street still spans a multi-km corridor, so the final
  pin needs **either** a typed house number **or** distance ranking against the
  map-centre/self anchor. Reuse the existing GoTo plumbing (`TwCoordGotoView`).

### 3.2 Why this revises `forward-fuzzy-address-search.md` §5.3

§5.3 concluded *"single county is not the convergence unit; go spatial-first."*
That remains true for the **final geometric pin** — but it was reasoning in a
world where county membership had to be *inferred* from OSM. With the MOI
re-source, the administrative funnel is now **authoritative, single-query, and
free**, which changes the trade-off:

- §5.3's objection was "a road name scatters across 15 townships within a
  county." The funnel answers that by making the operator pick the township
  *first* (one tap from a 8–29-item list), collapsing the scatter before any
  text match runs.
- The spatial component §5.3 argued for does **not** disappear — it returns as
  **stage ④** (distance-rank the district-scoped matches). The revision is
  about *ordering*: administrative funnel for coarse convergence + DB scoping +
  glove-friendly UI, then spatial ranking for the pin. The two are
  complementary, not competing.
- **The funnel also sidesteps the FTS5 risk entirely.** Once scoped to one
  district, the candidate set is small (a district is a fraction of the §1
  county counts; even a *whole-county* 1 km bbox is **(measured)** ~3,000–4,200
  rows — see appendix), so app-side normalise + substring + optional
  edit-distance is sub-millisecond. No global `places_fts` fan-out, no
  unicode61 limitations, no extra index. (FTS stays relevant only for a future
  "search anywhere by name with no location anchor" mode.)

### 3.3 Candidate-set sizes that justify app-side matching (measured)

`places-taichung.sqlite`, square window, `±deg` half-width on lat & lon:

| Centre | ±0.0023° (~0.5 km) | ±0.0045° (~1 km) | ±0.0090° (~2 km) |
|---|---:|---:|---:|
| 台中車站 24.1417,120.6736 | 664 | 3,381 | 13,679 |
| 一中商圈 24.1505,120.6840 | 598 | 3,098 | 12,819 |
| 大甲區 24.3486,120.6225 | 1,021 | 4,164 | 10,361 |

A *district*-scoped `district_code` filter is comparable-or-smaller than a 1 km
bbox, and matching a few thousand short `name`/`street` strings in Java is
trivially within the per-move latency budget the reverse path already meets
(<200 ms for the 731k-row bbox query, per data-contract §5.3).

---

## 4. UX under ATAK `tools` + gloved touch

ATAK constraints that shape the design:

- The plugin lives in a **`DropDownReceiver` side panel** — narrow (≈⅓ screen
  on a phone, often less in split-screen), portrait-biased, and competing with
  the map for width. Wide multi-column forms don't fit.
- The file picker (`ImportFileBrowserDialog`) is **single-select** (feature 005
  R3) — confirms the ecosystem bias toward *one decisive action at a time*.
- **Gloved fingers**: imprecise touch, no fine drag, no comfortable on-screen
  keyboard. Outdoor sunlight: needs high contrast + large type.

Design rules derived from this:

1. **Minimise typing; maximise tapping.** The funnel is built so stages ①–②
   are pure taps. Only stage ③ may need text — and even there, the
   district-scoped street set is short enough to offer an **incremental filter
   list** (type 1–2 chars → list shrinks) rather than free recall, or a
   chip-from-recents.
2. **Big touch targets.** ≥48 dp minimum, target ~56–64 dp for primary
   actions and district chips. One action per row; generous vertical spacing
   so a gloved tap can't hit two rows.
3. **Stage ① as three large buttons:** 〔所在地〕〔地圖中心〕〔清單〕. The
   first two auto-resolve via §2 and render the result as a **confirm chip**
   ("台中市北屯區 ✓ 變更") so a wrong GPS/centre is one tap to correct, not a
   silent assumption. 〔清單〕 opens the level-4 county list.
4. **Stage ② as a scrollable chip grid** of the county's districts (8–29 items
   — fits one or two scroll pages of big chips). Pre-select the operator's own
   district when it came from ①.1/①.2.
5. **Stage ④ house number via a large numeric keypad**, not the system IME —
   gloved numeric entry on big buttons is far more reliable than a full
   keyboard, and house numbers are digits + optional `之/-`.
6. **Always confirm before acting.** Resolve → show the candidate
   address + distance → explicit 〔前往 GoTo〕. Never auto-pan on a fuzzy match.
7. **Reuse existing patterns**: the per-row debounce, the confidence-tilde
   decorator (`ConfidenceThresholds`), and `TwCoordGotoView`'s GoTo path all
   carry over so the forward flow feels identical to the shipped reverse flow.

---

## 5. Plugin architecture deltas vs the shipped 005 code

| Area | Today (005) | Needed |
|---|---|---|
| `townships.sqlite` | **Skipped** on import (spec 005 FR-010 treats it as a supplementary file) | **Consumed**: imported from `base.zip`, mounted once, queried for §2 locality. |
| Geometry | None — only point R*Tree + haversine (`SqliteAddressDatabase`) | New **WKB MultiPolygon parser + point-in-polygon** facade (§2; JTS vs hand-rolled is a plan-phase call). |
| Reverse lookup | `AddressSubsystem.lookupAcrossAllCounties` fans out across **every** active county facade | Optional optimisation: resolve county via townships polygon-in first, then query **only** that county's facade — removes the N-county haversine fan-out (directly serves "don't touch 22 DBs" on the reverse path too). |
| Forward search | **Does not exist** | New subsystem + a `DropDownReceiver` page implementing the §3 funnel and §4 UX. |
| Import scope | `places-<county>.sqlite` only; townships/roads/osm skipped | `base.zip` (townships) becomes a first-class import; registry gains a singleton townships facade alongside the per-county place facades. |
| Dependency budget | Lazy fallback SQLite (R5), no geometry dep | +WKB parsing. Keep the lean-APK discipline: prefer the minimal hand-rolled parser, JTS in reserve. |

These are **feature 006+ scope** — they are deliberately out of the shipped 005
(reverse-geocode, multi-county import). This note is the design input for that
next feature, not a change to 005.

---

## 6. Decisions & open questions

**Decided (operator, 2026-05-30):**

- **County-selection default → 地圖中心.** When 所在地 GPS and 地圖中心
  disagree, stage ① seeds from the map centre (matches the map the operator is
  reading); 所在地 stays one tap away. *(Stage ①.2 is the default chip.)*
- **Stage ③ matching depth → substring + glyph/width fold only for v1.**
  Mandatory: `段`-spanning substring, `臺`↔`台`, fullwidth→halfwidth digit fold.
  Edit-distance / typo tolerance is **deferred** past v1 (cheap on this
  candidate size, but its ranking UX is out of v1 scope).
- **Reverse path → adopt townships-first county scoping in the same feature.**
  Resolve county via the new townships polygon-in facade, then query only that
  county's facade — removes the current N-county haversine fan-out. Shares the
  new townships facade with forward search, so both ship together.

**Still open (decide at plan-phase):**

1. **WKB parser**: minimal hand-rolled vs JTS (§2). Recommend prototyping the
   minimal parser against `extract_townships.py`'s shapely WKB and only falling
   back to JTS if MultiPolygon/holes/winding prove fiddly.
2. **House-number stage (④)**: numeric-keypad entry vs pure distance-ranking vs
   both. Distance-ranking needs no typing (best for gloves) but assumes the
   operator wants "nearest match of this street," not a specific number.

**Status: stays in research.** No `/speckit-specify` yet — this note + the
appendix are the design input for when feature 006 is opened.

---

## 7. Measured appendix (tw-central-full.zip, 2026-05-30 10:50 build)

```
townships.sqlite  (10.08 MB, source=moi-shapefile, boundary_release=1140318)
  level 4 (縣市)        : 12
  level 7 (直轄市區)    : 31
  level 8 (縣轄鄉鎮市)  : 105
  counties: 台中市 彰化縣 雲林縣 苗栗縣 南投縣 新竹縣 嘉義縣 花蓮縣 宜蘭縣 新北市 新竹市 桃園市
  districts/county (in bbox): 台中29 彰化26 雲林20 苗栗18 南投13 新竹縣9 嘉義8 花蓮8 宜蘭2 新北1 新竹市1 桃園1

places-taichung.sqlite : county=台中市 schema=2 rows=731,005
places-changhua.sqlite : county=彰化縣 schema=2 rows=426,690
places-osm.sqlite      : source=osm-clipped schema=2 rows=588,393

forward-search bbox candidate counts (places-taichung):
  台中車站 24.1417,120.6736 : 0.5km=664  1km=3,381  2km=13,679
  一中商圈 24.1505,120.6840 : 0.5km=598  1km=3,098  2km=12,819
  大甲區   24.3486,120.6225 : 0.5km=1,021 1km=4,164 2km=10,361

street family (places-taichung):
  street='中山路'        : 10,412      street LIKE '中山路%' : 23,065
  street='向上路'        : 0           street LIKE '向上路%' : 1,645
  name   LIKE '%中山%'   : 26,413      distinct street       : 3,752
  street LIKE '臺%'      : 4,873   (→ 臺↔台 fold mandatory)
```

Regenerate with `python scripts/measure_tw_central.py` after any generator rebuild.

---

## 8. Verification log (executed, not asserted)

Run against `tw-central-full.zip` SHA-256 `28a10e7d…83a9821` (the 10:50 build;
unchanged since). Three scripts, all re-runnable after a generator rebuild:

| Script | What it proves | Result |
|---|---|---|
| `scripts/measure_tw_central.py` | Headline counts + bbox candidate sets | counts match doc |
| `scripts/verify_research_claims.py` | Re-measures V2/V3/V6/V7/V8 + checks `county_zh` coverage, WKB byte shape, district-scoping narrowing | all **PASS** |
| `scripts/verify_polygon_in.py` | **Executes** Tier-1 polygon-in with a dependency-free WKB+PIP parser | **8/8 points correct** |

Key verified facts (each tagged PASS by the scripts):

- **Township layer**: 12 縣市 / 31 直轄市區 / 105 縣轄鄉鎮市 (matches metadata);
  source `moi-shapefile`, release `1140318`.
- **`county_zh` coverage**: 136 level-7/8 rows, **0 null/empty** → "one polygon-in
  hit yields county+township" holds for every district.
- **Tier-1 polygon-in actually resolves** (the previously-unproven core claim):
  台中車站→台中市西區, 一中商圈→台中市北區, 彰化市中心→彰化縣彰化市,
  鹿港→彰化縣鹿港鎮, 大甲→台中市大甲區, 斗六→雲林縣斗六市,
  南投市→南投縣南投市, offshore (24.0,119.5)→no county. R*Tree pruned to 1–3
  candidate polygons per query.
- **Hand-rolled WKB+PIP is Android-viable** (no JTS dependency required) — worst
  geometry 宜蘭縣 534 KB / 102 polys / 34,109 verts parses fine; only reached
  for the handful of R*Tree candidates per lookup.
- **`geometry_wkb` byte shape**: little-endian, OGC type 6 (MultiPolygon),
  WGS84 lon/lat range — matches what the minimal parser expects.
- **District scoping narrows the street family**: county-wide `中山路%` = 23,065
  rows spread over **18** districts; scoping to the densest (太平區) = 4,979
  (22%). `places` carries both `district_code` and `township` keys for stage ③.
- **Data-shape rules confirmed**: `street='中山路'` 10,412 vs `LIKE '中山路%'`
  23,065 (= drops >half); `向上路` `=`→0 of 1,645 (100% miss); `臺%` streets
  4,873 (臺↔台 fold mandatory).
- **Place counts**: 台中 731,005 / 彰化 426,690 / osm 588,393 (COUNT(*) ==
  metadata.inserted for the TGOS files).
