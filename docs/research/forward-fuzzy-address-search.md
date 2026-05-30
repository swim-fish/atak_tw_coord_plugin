# Offline Forward / Fuzzy Address Search — Feasibility & Efficiency Study

Date: 2026-05-28 (estimates) · Updated 2026-05-30 with measured data
Author: research note (pre-feature)
Scope: how the plugin could support **forward** address search — typing a
road / street fragment (`中山路`, `大誠街39巷`) and getting on-map locations —
**without** the operator typing the full administrative hierarchy
(縣市 / 鄉鎮市區 / 村里).
Companion docs:
- Generator data contract: `atak-tw-address-generator/docs/data-contract.md` (v2)
- Plugin data model: `specs/004-offline-address/data-model.md`
- Generator normalisation: `atak-tw-address-generator/scripts/normalize_address.py`
- Reverse-geocode roadmap: feature 006 (townships / roads / places-osm tiers)
- **County-first funnel + locality detection + glove UX (revises §5.3):**
  [`county-scoped-forward-search.md`](./county-scoped-forward-search.md)

> **Measured-data provenance.** The 2026-05-30 update replaces the original
> order-of-magnitude estimates with figures queried directly from the generator
> output kit **`tw-central-full.zip`** (region `tw-central`, bbox
> `120.2,23.55,121.45,24.75`, generated `2026-05-27T14:52:03Z`,
> SHA-256 `0767edc3…2e13681`). Row counts come from the bundled
> `places-taichung.sqlite` (TGOS data-date 115-01), `places-changhua.sqlite`
> (114-05), `roads.sqlite`, `townships.sqlite`, and `places-osm.sqlite`, plus
> the kit `*.manifest.txt`. Bbox candidate counts were computed on the
> `places(lat,lon)` columns (each point has `min==max`, so the count equals
> what `places_rtree` returns). All figures below tagged **(measured)** are
> from this kit; anything tagged **(generator-internal)** is a pre-dedup count
> reported by the generator that these shipped files cannot independently
> reconstruct.

---

## 1. Problem framing

The shipped offline-address subsystem (features 004 / 005) does **reverse
geocoding only**: map coordinate → nearest address, via the `places_rtree`
spatial index + haversine refine. Forward search (text → coordinate) is the
opposite query and is **not implemented** — although the generator already
ships the data structures for it.

The specific ask is **regional fuzzy search**: match a road/street fragment
near the operator's location, tolerating that the input omits the admin prefix
(and ideally tolerating fullwidth/halfwidth digits and 台/臺 variants).

## 2. What the generator already produces (facts that drive the design)

From the generator data contract (v2) and ingest scripts:

1. **Three distinct data sources** (data-contract §2 / §3):
   - `places-*.sqlite` — **per-county** TGOS house-number points. **(measured)**
     Taichung **731,005** rows (data-date 115-01), Changhua **426,690** rows
     (114-05, 1 row skipped dirty). **The only source the plugin currently
     imports** (features 004 / 005). *(The original note's "≈ 467k" Changhua
     figure was an over-estimate; the shipped file holds 426,690.)*
   - `roads.sqlite` — from `base.zip`; OSM road centrelines as WKB
     LineStrings. Has `idx_roads_name` (B-tree on `name_zh`) + `roads_rtree`.
     **(measured) 77,457 segments, 40,336 distinct `name_zh`, 0 unnamed** —
     **not** "a few thousand" as first estimated, but still two orders of
     magnitude below the point data and fully bbox-scopable. Not yet imported
     by the plugin (feature 006).
   - `townships.sqlite` — admin polygons; `idx_townships_name`. **(measured)
     118 polygons** (12 level-4 + 29 level-7 + 77 level-8). Feature 006.
   - `places-osm.sqlite` — **(measured) 588,385 rows** (6,491 landmarks +
     581,894 OSM addresses kept; 1,662,144 excluded). Feature 006 composite tier.

2. **Point data is de-duplicated to ≤ 1 row per `(lat, lon)`** (data-contract
   §7 CHANGELOG). **(measured)** In `places-taichung.sqlite`,
   `COUNT(DISTINCT lat,lon)` = `COUNT(*)` = **731,005** — i.e. exactly one row
   per coordinate, confirming the dedup invariant holds in the shipped file.
   *(The "1.3M → 731k" raw-input reduction is **generator-internal**; the
   shipped file only preserves the post-dedup result.)* So a bbox window holds
   fewer candidate rows than the raw TGOS counts suggest.

3. **Variant Han glyphs (`臺`/`台`) survive in road names — by design — so the
   query side MUST fold them.** data-contract §4.1 claims `臺 → 台` is
   normalised "everywhere" across `name`/`street`/`display_name` and that
   "plugins MAY assume any name uses `台`." **(measured)** this is **false for
   road names**: `places-taichung.sqlite` stores `臺灣大道一段…十段`
   (**4,873** rows, glyph `臺`) while `台中路`/`台貿路`/`電台街` use `台`. This
   is **not** a generator bug — it is correct: each road keeps its *legal /
   gazetted* spelling (臺灣大道 is the official name 臺中市政府 gazetted with
   `臺`). A 0-clash check confirms self-consistency: mapping every `臺X` street
   to `台X` and intersecting with the `台X` set yields **0** roads spelled both
   ways — i.e. no road flips glyphs; each is fixed. **Consequence:** because
   `臺`/`台` are interchangeable variants in *operator input* (a user types the
   colloquial `台灣大道` for the gazetted `臺灣大道`), the query side must treat
   them as one equivalence class — fold both the query and the candidate to a
   single representative glyph (or carry a variant table) before matching.
   Other query-side transforms remain fullwidth digit → halfwidth and `之` →
   `-`. *(§4.1's wording is the thing that needs fixing — not the data; see
   §6.)*

4. **The `name` column is a compact "street + number" halfwidth string** and is
   indexed in `places_fts`. **(measured)** Real rows from
   `places-taichung.sqlite`: `name` = `大誠街11巷1號` while the paired
   `display_name` = `台中市中區大誠里大誠街１１巷１號` (fullwidth, full admin
   hierarchy). The `name` column therefore already carries a street-level string
   **without** the 縣市/鄉鎮/里 prefix **and already halfwidth** — exactly what
   the "omit admin hierarchy" requirement needs, and matching against `name`
   needs no *digit-width* folding (only `display_name`/`display_name_halfwidth`
   differ on digit width). It does **still** need `臺`/`台` variant folding (§2.3)
   — `name` carries `臺灣大道…` verbatim.

5. **`places_fts` is FTS5, `content='places'` (external content), tokenizer
   `unicode61`** (data-contract §3.1). Indexed columns:
   `name, display_name, display_name_halfwidth, street, township`.
   `MATCH` returns rowids that are joined back to `places`. Crucially, an FTS
   MATCH and an R*Tree bbox **cannot be combined in one efficient query** —
   they are two separate virtual tables, so one prunes and the other filters
   afterward.

6. **unicode61 = per-character CJK tokens.** A phrase query `MATCH '"中山路"'`
   therefore matches a *contiguous substring* of indexed text (data-contract
   §5.5). It does **not** provide typo tolerance, reordering, or width/variant
   folding.

7. **The `street` column carries the segment suffix (`段`), so exact equality
   silently drops most of a road.** **(measured)** In `places-taichung.sqlite`
   the 大甲區 中山路 splits into `street` values `中山路一段` (1,851 rows) and
   `中山路二段` (1,367 rows); the bare `street='中山路'` matches only **2** rows
   (both `中山路401巷`). County-wide, `street='中山路'` returns **10,412** rows
   but `street LIKE '中山路%'` returns **23,065** — exact equality drops more
   than half of the "中山路 family." The extreme case is 台中 **向上路**: the
   bare `street='向上路'` matches **0** rows (every house number lives under
   一段…九段), so an `=` query returns *nothing at all* — a 100 % miss, not just
   a halving. **Any street query must therefore be a prefix/substring match, not
   `=`** — and this is a *data-shape* requirement independent of the typo/width
   fuzziness discussed elsewhere.

## 3. Three candidate search paths and their efficiency

| Path | Method | Efficiency | Fuzziness | Available now? |
|---|---|---|---|---|
| **Spatial-first @ places** (recommended) | `places_rtree` bbox (start ≈ 500 m, expand on too-few hits) → app-side normalise + `contains` / edit-distance on the windowed set | **Best.** Same pattern the generator measured at **< 200 ms on 731k-row Taichung** (data-contract §5.3); typically far less. **(measured)** a 500 m box around Taichung station holds ~**660** candidate rows, a 1 km box ~**3,000–4,200**, a 2 km box ~**13k** (see §5.1) — all trivial to scan in app code. Touches only the 1–2 county DBs whose bbox overlaps. | **True fuzzy** — even the 1 km candidate set (few-thousand rows) is small enough to run any matching (normalisation, substring, Levenshtein) in app code; unicode61's limits don't apply. | ✅ Uses already-imported per-county data; no generator change. |
| **roads.sqlite name search** | `roads_rtree` bbox → `name_zh LIKE '%中山%'` then app-side fuzzy on the windowed segments | Excellent **regionally** — `roads_rtree` prunes to a handful of segments before the `LIKE`. Note a *global* `LIKE` would scan **(measured) 77,457** segments (40,336 distinct names), so always bbox-scope first. Returns the road as a line, so GoTo can target the nearest point on it. | LIKE substring; app-side fuzzy is trivial on the bbox-windowed segments. | ⚠️ Requires importing `base.zip` first (feature 006). |
| **Global FTS @ places_fts** (§5.5) | `places_fts MATCH '"中山路"'` fanned out across all active county DBs | **Worst** for common names: high-frequency tokens have long posting lists. **(measured)** in Taichung alone, `name LIKE '%中山%'` matches **26,413** rows (`display_name` 29,079); fan that out across up to 22 county DBs and then app-side rank. | Exact contiguous substring only (unicode61). No typo tolerance. | ✅ But only sensible for **search-anywhere-by-name** with no location anchor. |

## 4. Conclusion

- **Regional search = spatial-first, and it is essentially "free."** Once the
  R*Tree bbox prunes to a few hundred (500 m) up to a few thousand (1 km) rows
  **(measured §5.1)**, the text-matching cost is negligible *and* you can do
  real fuzzy matching that FTS5/unicode61 cannot. The generator's own §5.3
  reverse-geocode numbers already prove the bbox → app-refine latency budget.
  **FTS5 is not needed in this mode.**
- **For pure road/street names (no house number), `roads.sqlite` is even
  cheaper** (77k segments total but bbox-scopable to a handful, indexed name)
  and yields proper road geometry — but it depends on importing `base.zip`
  (feature 006).
- **FTS (§5.5) is only worth it for the non-regional "search anywhere by
  name" mode**, where no bbox can prune; and even then unicode61 gives no
  typo tolerance.

## 5. Recommended phasing

### 5.1 Measured bbox candidate counts (tw-central-full kit)

Counts from `places-taichung.sqlite` (731,005 rows) over a square window
centred on the given point; `±deg` is the half-width applied to both lat and
lon. These are the candidate sets the app-side fuzzy match would scan:

| Centre | ±0.0023° (~0.5 km box) | ±0.0045° (~1 km box) | ±0.0090° (~2 km box) |
|---|---|---|---|
| 台中車站 24.1417, 120.6736 | **664** | **3,373** | **13,669** |
| 一中商圈 24.1505, 120.6840 | — | **3,088** | **12,803** |
| 大甲區 24.3486, 120.6225 | — | **4,151** | — |

Takeaways:
- The original "≤ ~500 rows per 500 m" estimate was optimistic — a real 500 m
  box around a dense centre is ~660 rows, and a 1 km box is ~3,000–4,200.
- Even so, app-side normalisation + substring/Levenshtein over a few-thousand
  short strings is sub-millisecond-to-low-millisecond in Java — well inside the
  latency budget. The thesis ("spatial-first is essentially free") holds; only
  the candidate-count figure needed correcting.
- Rural windows are *not* necessarily sparser (大甲 ±0.0045° = 4,151 > the urban
  一中 3,088) because TGOS density follows the built footprint, not the city
  label — so a fixed start radius is fine, with expansion only when hits are few.

### 5.2 Phasing

- **Phase 1 — works on today's per-county data, no generator change.**
  `places_rtree` bbox around the map centre / self-marker → app-side
  normalise (fullwidth→halfwidth, `之`→`-`, `臺`→`台`) + fuzzy match on the
  candidate rows → rank by distance. Delivers house-number-level results near
  the operator. Starts with a small bbox (≈ 500 m → ~660 rows, §5.1) and
  expands when too few matches are found (each expansion is one more cheap
  R*Tree query). This reuses — and could share code with — the bbox pre-filter
  recommended in the 2026-05-28 review finding #2.
- **Phase 2 — once `base.zip` / `roads.sqlite` is consumed (feature 006).**
  Add road-name search against `roads.sqlite` for the "find this road near me"
  use case, returning the road line for GoTo.

Phase 1's app-side normalisation step must therefore also do **street-name
substring matching that spans the `段` suffix** (§2.7): treat the typed `中山路`
as a prefix of `中山路一段` / `中山路二段`, not an exact `street` value.

### 5.3 Why "single county" is not the convergence unit (measured)

> **Revised 2026-05-30 — see [`county-scoped-forward-search.md`](./county-scoped-forward-search.md).**
> This section's "single county is not the convergence unit → go spatial-first"
> conclusion was reasoned before the MOI authoritative boundary re-source. With
> `county_zh` now inline on every 鄉鎮市區 polygon, the administrative funnel
> (縣市 → 鄉鎮市區 → street) is authoritative, single-query, and free, so it is
> the right *coarse* converger + DB-scoper + glove-friendly UI; the spatial
> argument below still holds, but only as the **final pin** (stage ④), not as
> the whole strategy. Read the two together.

A natural first instinct is to scope road-name fuzzy search to one county. The
data shows that **county is too coarse, and even township is not enough** — the
only unit that actually converges is a spatial bbox (or bbox + house number).

- **Road names repeat across counties.** **(measured)** 台中 and 彰化 share
  **347** identical `street` names (out of 3,752 / 2,362 distinct each). 中山路
  alone: 台中 10,412 + 彰化 2,916 rows (`street='中山路'`, i.e. *before* the
  `段` undercount of §2.7).
- **Road names repeat *within* one county too.** 中山路 appears in **15
  distinct 台中 townships** (中區, 大甲區, 清水區, 豐原區, 霧峰區, …). So a
  county filter still leaves a road name scattered across the whole county.
- **Township + road name is far better but still not a pin.** **(measured)**
  「大甲 中山路」 converges from 23,065 (county-wide 中山路 family) down to
  **3,220** rows — a 7× cut — but 大甲 中山路一段 alone spans roughly **5–6 km**,
  so those 3,220 house numbers are strung along a corridor, not a point.
- **The extreme case — 向上路 — breaks even township scoping.** **(measured)**
  台中 向上路 has **1,645** rows across **9 segments** (一段…九段) and **6
  townships** (西區, 南屯區, 沙鹿區, 龍井區, 梧棲區, 大肚區), spanning **9.3 km
  N–S × 15.8 km E–W** (a >18 km diagonal from the city core out to the coast).
  Segments do **not** align to township borders — 向上路五段 alone straddles
  南屯/大肚/龍井 — so even "鄉鎮 + 段" can't cleanly cut it, and 「南屯 向上路」
  still returns **363** rows along several km. Only bbox + distance ranking pins
  it.
- **Conclusion — three layers, none optional:** (1) **bbox or township** for
  coarse convergence; (2) **street substring match incl. `段`** (§2.7); (3)
  **house number *or* spatial distance** for the final pin. Layer 3 is what
  pulls the design back to **spatial-first**: bbox around the map centre →
  substring-match `中山路` in the windowed candidates → rank by distance. A
  typed "鄉鎮 + 路名" is best treated as an *optional text filter on top of the
  bbox*, not as a standalone locator.

## 6. Open questions (decide before scaffolding)

1. **UX anchor:** is the requirement "roads/streets **near the map centre**"
   (→ spatial-first, efficient, Phase 1 only) or also "**anywhere in Taiwan by
   name**" (→ needs the FTS fan-out + ranking path)? This single answer decides
   the whole architecture. **§5.3 makes the case that even a "by name" mode
   needs at least township scoping — county alone leaves 中山路 across 15
   townships — and that township + name still returns thousands of rows along a
   multi-km corridor, so distance ranking (i.e. a spatial component) is required
   regardless.**
2. **Fuzziness level:** note there are **three independent** axes — the first
   two are **mandatory regardless of UX choice**, only the third is optional.
   (a) *Data-shape* matching — street substring incl. the `段` suffix (§2.7);
   exact `=` drops >half of the 中山路 family and **100 %** of 向上路.
   (b) *Variant-glyph equivalence* — fold `臺`↔`台` (§2.3); without it the typed
   `台灣大道` misses the gazetted `臺灣大道` (4,873 rows) entirely. Both (a) and
   (b) are app-side and cheap. (c) *Input typo* fuzziness — is "omit the admin
   prefix + digit width" enough, or is typo tolerance required (edit-distance on
   the bbox candidate set in Phase 1; trigram re-index → generator data-contract
   v3 only if a *global* typo-tolerant search is ever needed)?
3. **Result target:** GoTo a point (reuse the existing GoTo plumbing in
   `TwCoordGotoView`) vs drop a marker vs both.
4. **FTS5 runtime availability** is only relevant if the global FTS path is
   chosen; it would need the same native-vs-fallback probe used for R*Tree
   (research R5). The Phase 1 spatial-first path does not use FTS at all and
   so avoids this risk.

**Upstream follow-up (generator, doc-only).** §2.3 found that road names keep
their gazetted `臺`/`台` spelling (correct behaviour), which contradicts
data-contract §4.1's claim that `臺→台` is applied "everywhere" and that plugins
"MAY assume any name uses `台`." The fix is to the **§4.1 wording, not the
data**: it should state that proper nouns retain their gazetted variant glyph
and that consumers must fold `臺`↔`台` as an equivalence class at query time. No
`places-*.sqlite` regeneration is needed; this does not change the
"feature 005 needs no generator change" position.

## 7. References

- Generator data contract v2: `§2` (file set), `§3.1` (places + FTS + R*Tree
  schema), `§4.1`/`§4.2` (glyph + digit normalisation), `§5.3` (tier-3 bbox
  reverse geocode, the latency proof), `§5.5` (forward search query).
- Plugin data model: `specs/004-offline-address/data-model.md §1.1`–`§1.5`.
- Normalisation rules: `atak-tw-address-generator/scripts/normalize_address.py`.
- Related: 2026-05-28 code review finding #2 (bbox pre-filter) —
  `docs/reviews/2026-05-28-master-to-005-review.md`.
