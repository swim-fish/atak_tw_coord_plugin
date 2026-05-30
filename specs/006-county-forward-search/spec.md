# Feature Specification: County-Scoped Forward Address Search

**Feature Branch**: `006-county-forward-search`

**Created**: 2026-05-30

**Status**: Draft

**Input**: User description: "縣市優先的離線地址前向搜尋（county-scoped forward address search）。依據 docs/research/county-scoped-forward-search.md 的研究結論與已驗證資料。可以快速區分所在地縣市（不用把 22 縣市都一次讀取進去）；地址模糊搜尋限制在單一縣市（先選縣市：所在地／地圖中心／選單），後續透過鄉鎮市區逐步篩檢；UX 要參考 ATAK tools 的限制以及戴上手套觸控的問題。"

## Context

Features 004 / 005 (shipped) gave the plugin **offline reverse geocoding**:
a coordinate (self-marker / target / map-centre) resolves to the nearest
TGOS house-number address across every active per-county dataset. What they
do **not** offer is the opposite direction — **forward search**: an operator
typing/picking a place and being taken to it on the map.

This feature adds forward search, but deliberately **not** as a free-text
"search anywhere in Taiwan" box. The research note
[`docs/research/county-scoped-forward-search.md`](../../docs/research/county-scoped-forward-search.md)
(measured + executed against the `tw-central-full.zip` 10:50 build, SHA-256
`28a10e7d…`, `townships.sqlite` MOI boundary release `1140318`) establishes
that the only convergent, glove-usable shape is an **administrative funnel**:

1. **Locality detection becomes cheap and authoritative.** The generator
   re-sourced `townships.sqlite` from the MOI authoritative boundary
   shapefiles, so every 鄉鎮市區 polygon now carries its parent 縣市 inline
   (`county_zh`). The whole boundary layer is ~10 MB. A single spatial
   lookup answers "which 縣市 + 鄉鎮市區 is this coordinate in?" **without
   opening any of the 100–324 MB per-county address databases.** This is the
   "don't read all 22 counties at once" requirement, met by data shape.
2. **Forward search funnels county → 鄉鎮市區 → street → pin.** The operator
   picks a county (from their location, the map centre, or a list), then a
   district, and only then is that one county's address database consulted;
   street matching runs on the district-scoped candidate set, and the final
   pin uses a house number or distance ranking.

The feature also **adopts townships-first scoping on the existing reverse
path**: reverse-lookup first resolves the county via the township polygons,
then queries only that county's dataset, removing the current "query every
active county and compare" fan-out. Forward and reverse share the one new
townships facade and ship together.

This is **feature 006**, building on shipped 004/005. It begins consuming
`townships.sqlite`, which feature 005 currently imports but classifies as a
skipped supplementary file.

### Out of scope (explicitly deferred)

- **Tier-2 nearest-road lookup** (`roads.sqlite`) — a later feature.
- **OSM landmarks / non-TGOS addresses** (`places-osm.sqlite`) — a later feature.
- **Global free-text "search anywhere by name" with no location anchor** (would
  need an FTS fan-out across all counties; the funnel intentionally replaces it).
- **Active-root migration** from `active/<county>/places.sqlite` to the
  generator's flat `data/places-<county>.sqlite` convention (paired with 006 in
  the roadmap but independently shippable; not required here).
- **Typo / edit-distance fuzziness** on the street match — v1 does substring +
  glyph/width folding only; edit-distance is a post-v1 addition.

## Clarifications

### Session 2026-05-30

- Q: When the self-marker (GPS) county and the map-centre county disagree, which seeds the county picker by default? → A: **地圖中心 (map centre).** It matches the map the operator is reading; the 所在地 (GPS) source stays one tap away. (Recorded in the research note §6.)
- Q: How deep is the street match in v1? → A: **Substring + glyph/width folding only.** Mandatory: `段`-spanning substring (never exact `=`), `臺`↔`台` fold, fullwidth→halfwidth digit fold. Typo/edit-distance tolerance is deferred past v1.
- Q: Does the reverse path also adopt county scoping in this feature? → A: **Yes.** Reverse-lookup resolves county via the townships polygon-in first, then queries only that county's facade, sharing the new townships facade with forward search.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Find an address near me by funnelling county → 鄉鎮市區 → street (Priority: P1) 🎯 MVP

A field operator wants to go to a street address near their current operating
area. They open Tools → 前向搜尋 (a standalone Tools-menu entry, sibling to
離線地址 and the GoTo input page). The page shows their current
county/district already detected from the map (e.g. 台中市西區). They confirm
the county, the district list for that county is shown with the operator's own
district pre-highlighted, they tap a district, type or pick a street fragment
(e.g. `中山路`), see a short list of candidate matches ranked by distance from
the map centre, tap one, review the resolved address + distance, and tap
〔前往 / GoTo〕 to pan the map there.

**Why this priority**: This is the whole point of the feature and the user's
primary ask — "縣市優先 → 鄉鎮市區逐步篩檢" forward search. It is independently
useful even without the reverse-path change (US3) or the manual county picker
edge cases. Shipping just this delivers a coherent, demonstrable forward-search
flow.

**Independent Test**: With `tw-central-full.zip` imported (台中市 + 彰化縣 active,
`townships.sqlite` consumed), opening the forward-search page at a Taichung
map-centre MUST pre-detect 台中市 + the right 區, let the operator narrow to a
district and a street fragment, and GoTo a resulting address — verified by the
map panning to a coordinate inside that district whose address text contains the
typed street fragment.

**Acceptance Scenarios**:

1. **Given** the map centre is at 台中車站 (24.1417, 120.6736) and 台中市 is
   active, **When** the operator opens forward search, **Then** the county is
   pre-filled as 台中市 and the district as 西區 (the detected locality), without
   opening any place database yet.
2. **Given** county 台中市 + district 大甲區 are selected, **When** the operator
   enters `中山路`, **Then** the page shows candidate addresses whose street is
   in the 中山路 family (including segmented forms `中山路一段` / `中山路二段`),
   scoped to 大甲區, ranked by distance from the map centre.
3. **Given** a candidate is shown with its address text + distance, **When** the
   operator taps 〔前往 / GoTo〕, **Then** the map pans to that coordinate using
   the same GoTo flow the plugin already ships, and no pan happens before the
   explicit confirm.
4. **Given** county 台中市 + district 西區 are selected, **When** the operator
   enters `台灣大道` (colloquial 台), **Then** matches include the gazetted
   `臺灣大道…` rows (glyph folded), i.e. the variant glyph does not cause a miss.
5. **Given** a street fragment matches nothing in the chosen district, **When**
   the search runs, **Then** the page shows a clear empty-state ("此鄉鎮市區查無
   符合的街道") and the operator can change district or fragment without leaving
   the page.

---

### User Story 2 — Choose the county three ways: my location, map centre, or a list (Priority: P1)

A field operator is not always standing where they want to search. The county
stage offers three sources: **所在地** (from the self-marker GPS), **地圖中心**
(from the current map centre), and **清單** (pick from the list of counties the
installed boundary data actually contains). When GPS and map centre disagree,
the page seeds from the **map centre** by default, with the 所在地 source one tap
away. The county list is read from the boundary data, never a hard-coded
22-county table.

**Why this priority**: Without a way to choose the county, the funnel only works
when the operator happens to be standing in the target county. The three-source
picker is what makes forward search usable while panning, planning, or operating
remotely — the user explicitly asked for 所在地／地圖中心／選單 selection.

**Independent Test**: With the map centred on a Changhua coordinate but the
self-marker in Taichung, opening forward search MUST seed 彰化縣 (map centre) by
default; tapping 〔所在地〕 MUST switch to 台中市; tapping 〔清單〕 MUST show a
county list whose entries match the counties present in the installed
`townships.sqlite` (not a fixed 22-county list).

**Acceptance Scenarios**:

1. **Given** the self-marker is in 台中市 and the map centre is in 彰化縣, **When**
   the operator opens forward search, **Then** the county is seeded as 彰化縣 (map
   centre default) and a one-tap 〔所在地：台中市〕 affordance is offered.
2. **Given** the county stage, **When** the operator taps 〔清單〕, **Then** the
   list shows exactly the counties present in the installed boundary data (for
   the `tw-central` bundle: 台中市, 彰化縣, 雲林縣, 苗栗縣, 南投縣, 新竹縣,
   嘉義縣, 花蓮縣, 宜蘭縣, 新北市, 新竹市, 桃園市) and no county that is absent
   from the data.
3. **Given** a county is selected by any of the three sources, **When** the
   district stage opens, **Then** it lists that county's 鄉鎮市區 and (for the
   所在地/地圖中心 sources) pre-highlights the operator's own district.
4. **Given** the operator's coordinate falls outside every county boundary in the
   installed data (e.g. offshore, or a county not in the bundle), **When** the
   county stage seeds, **Then** no county is auto-selected and the operator is
   prompted to pick from 〔清單〕.

---

### User Story 3 — Reverse lookup scoped to the detected county (Priority: P2)

The existing on-map reverse readout (the address line under the map-centre /
self-marker / target coordinate) keeps working exactly as the operator expects,
but internally it now first determines the county from the township boundaries
and queries only that county's dataset, instead of querying every active county
and comparing. The operator sees no behaviour change — only equal-or-better
responsiveness as more counties become active.

**Why this priority**: This is an internal correctness/efficiency improvement
that directly serves "don't touch all counties" on the path that runs most
often. It is P2 because it is invisible to the operator when there are few active
counties; its value grows with the active-county count. It shares the township
facade with US1/US2, so it is cheap to include in the same feature.

**Independent Test**: With {台中市, 彰化縣} active, a Taichung map-centre reverse
readout MUST resolve using only the Taichung dataset (verifiable by the result
being identical to today's globally-nearest result for in-county points) and MUST
NOT regress the reverse-lookup latency budget (SC-003, carried from 004/005).

**Acceptance Scenarios**:

1. **Given** {台中市, 彰化縣} active and the map centre at a Taichung point, **When**
   the reverse readout updates, **Then** it shows the same address text it shows
   today (the nearest Taichung house number) — no visible behaviour change.
2. **Given** the map centre at a point whose county is detected but whose dataset
   is **not** installed (e.g. 雲林縣 detected, no `places-yunlin.sqlite`), **When**
   the reverse readout updates, **Then** it shows the county/district from the
   township layer as a best-effort locality and an empty house-number state,
   rather than silently showing nothing.
3. **Given** the map centre at a coordinate on harbour-reclaimed land (台中港
   環港路, seaward of every legal township polygon), **When** the county is
   resolved, **Then** the county is still identified and the district is snapped
   to the nearest one within tolerance and flagged approximate, rather than
   returning "no locality".

---

### User Story 4 — Pin the result by house number or distance (Priority: P3)

After narrowing to a street, an operator who knows the house number can enter it
to land on the exact building; an operator who does not can rely on the
distance-ranked list to pick the nearest match of that street. House-number entry
uses a large numeric keypad rather than the system keyboard, so it works with
gloves.

**Why this priority**: Street + district still spans a multi-kilometre corridor,
so a final disambiguation step materially improves the result. It is P3 because
US1 already delivers a usable distance-ranked pin; the house-number keypad is a
precision enhancement on top.

**Independent Test**: With county/district/street selected for a long road
(e.g. 台中 向上路, which spans nine segments), entering a house number MUST land
on the matching building, while leaving it blank MUST fall back to the
nearest-by-distance candidate.

**Acceptance Scenarios**:

1. **Given** county 台中市, district 西區, street `向上路` selected, **When** the
   operator enters a house number via the numeric keypad, **Then** the candidate
   list narrows to addresses matching that number on 向上路 and GoTo lands on the
   building.
2. **Given** the same selection with no house number entered, **When** the
   operator confirms, **Then** the result is the 向上路 address nearest to the
   map-centre anchor by distance.

---

### Edge Cases

- **Boundary data not installed**: the operator imported only per-county
  `places-*.sqlite` (no `base.zip` / `townships.sqlite`). Forward search cannot
  detect locality; the page shows a clear state explaining that the boundary
  data (base bundle) is required and offers the manual 〔清單〕… which is also
  empty without boundary data, so the page degrades to "匯入 base 資料以啟用前向
  搜尋". Reverse lookup falls back to the existing 004/005 behaviour.
- **County detected but its place dataset absent**: locality (縣市 + 鄉鎮市區)
  shows from the township layer, but street search has nothing to query — the
  page shows the locality and a "此縣市地址資料未安裝" prompt.
- **Coastline / reclaimed land**: a coordinate seaward of every legal township
  polygon returns no strict district; the county is still unambiguous and the
  district is snapped to the nearest within ~1 km and flagged approximate.
- **County present in boundary data but operator is on its edge clipped by the
  bundle bbox**: peripheral counties in a regional bundle may contain only some
  districts; the district list shows only what the data contains, and detection
  may legitimately return no district for a clipped area.
- **Glyph/width variants in operator input**: `臺`/`台`, fullwidth/halfwidth
  digits, and `之`/`-` must all match; e.g. typing `台灣大道` matches gazetted
  `臺灣大道`, and `２之３` matches `2-3`.
- **Exact-equality street trap**: a bare street name like `向上路` exists only as
  segmented forms (`向上路一段`…`九段`) in the data — matching MUST be substring,
  never exact equality, or the search returns nothing for such roads.
- **Worst-case boundary geometry parse**: a large multipolygon county
  (e.g. 宜蘭縣, ~102 polygons / ~34k vertices) must parse and test without
  noticeable delay; the spatial prefilter ensures only a handful of candidate
  polygons are ever parsed per lookup.
- **Multiple districts share a street name within one county**: e.g. 中山路 in
  15+ 台中 districts — district selection (not just county) is required to
  converge, which the funnel enforces by construction.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST detect the current 縣市 + 鄉鎮市區 for a given
  coordinate using the administrative boundary data alone, WITHOUT opening any
  per-county address database. (This is the constraint; FR-003 specifies the
  mechanism that realises it.)
- **FR-002**: System MUST consume the boundary dataset (`townships.sqlite`,
  currently skipped as supplementary by feature 005) as a first-class input,
  mounting it once and reusing it across forward search and reverse lookup.
- **FR-003**: System MUST resolve a coordinate to (縣市, 鄉鎮市區) via a spatial
  bounding-box prefilter followed by an exact polygon-containment test, returning
  the parent county inline from the matched district (no separate county query on
  the success path).
- **FR-004**: When no district polygon strictly contains the coordinate (e.g.
  reclaimed coastal land), system MUST still identify the county and MAY snap to
  the nearest district within a bounded tolerance, flagging the result as
  approximate.
- **FR-005**: System MUST offer county selection from three sources — self-marker
  location (所在地), map centre (地圖中心), and a manual list (清單) — and MUST
  default to the map-centre county when the self-marker and map-centre counties
  differ.
- **FR-006**: The manual county list MUST be derived from the counties actually
  present in the installed boundary data, and MUST NOT rely on a hard-coded list
  of Taiwan counties.
- **FR-007**: After a county is chosen, system MUST present that county's
  鄉鎮市區 list for selection, and MUST pre-highlight the operator's own district
  when the county was chosen via the 所在地 or 地圖中心 source.
- **FR-008**: System MUST open and query only the single selected county's
  address database for street matching — never all active counties at once during
  forward search.
- **FR-009**: Street matching MUST be a substring/prefix match that spans the
  segment suffix (`段`), and MUST NOT use exact string equality (which misses
  segmented and multi-segment roads entirely).
- **FR-010**: Street matching MUST fold the `臺`/`台` glyph variants and
  fullwidth↔halfwidth digits (and `之`↔`-`) so colloquial operator input matches
  gazetted/stored forms.
- **FR-011**: System MUST rank street-match candidates by distance from the
  current anchor (map centre, or self-marker as applicable) and present a bounded,
  scrollable candidate list.
- **FR-012**: System MUST allow the operator to disambiguate the final pin by
  entering a house number; when none is entered, system MUST fall back to the
  nearest candidate by distance.
- **FR-013**: System MUST NOT move/pan the map until the operator explicitly
  confirms a candidate (no auto-pan on a fuzzy match); on confirm it MUST reuse
  the plugin's existing GoTo flow.
- **FR-014**: The reverse on-map readout MUST resolve the county via the boundary
  layer first and then query only that county's dataset, replacing the
  query-all-active-counties comparison, with no operator-visible behaviour change
  for in-county points.
- **FR-015**: When reverse lookup detects a county whose address dataset is not
  installed, system MUST surface the boundary-derived locality (縣市 + 鄉鎮市區)
  as a best-effort answer rather than an empty result.
- **FR-016**: The forward-search UI MUST be operable with gloved fingers and
  within the ATAK tool side-panel constraints: a single-column layout, large
  touch targets, county/district selection achievable by tapping (no typing),
  and house-number entry via a large numeric keypad rather than the system
  keyboard.
- **FR-017**: System MUST degrade gracefully when the boundary data is absent —
  forward search clearly states the boundary bundle is required, and reverse
  lookup falls back to the existing feature 004/005 behaviour.
- **FR-018**: Forward search MUST reuse the existing confidence indicator and
  result-presentation conventions from features 004/005 (showing the resolved
  address and its distance/approximation state) before any GoTo.
- **FR-019**: System MUST NOT regress feature 005's multi-county reverse-lookup
  behaviour or its import/lifecycle flows; consuming the boundary layer MUST be
  additive.

### Key Entities *(include if feature involves data)*

- **AdministrativeBoundarySet**: The installed boundary data covering 縣市
  (level-4) and 鄉鎮市區 (level-7/8) polygons, each district carrying its parent
  county inline. Mounted once; the source of both locality detection and the
  county/district pick lists. Small relative to address data.
- **LocalityResult**: The outcome of resolving a coordinate against the boundary
  set — (county, district, approximate?). May have a county with no district
  (clipped/coastal) or be empty (outside all boundaries).
- **CountySource**: The operator's chosen county plus its provenance
  (所在地 / 地圖中心 / 清單), which drives whether the district stage
  pre-highlights a district. (Implemented as the `CountySource` enum plus a
  `CountySeed` value carrying the default + alternative counties — see
  data-model §2.3.)
- **ForwardSearchQuery**: The accumulated funnel state — county → district →
  street fragment → optional house number — plus the distance anchor used for
  ranking.
- **AddressCandidate**: A street-matched address from the selected county's
  dataset, with its display text, coordinate, and distance from the anchor;
  the unit shown in the candidate list and handed to GoTo on confirm.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: From opening the forward-search page at a known map centre, an
  operator can reach a confirmed GoTo for a nearby street address in **≤ 5 taps**
  and **≤ 30 s** (county confirm → district → street fragment → candidate →
  GoTo). "Taps" counts discrete touch actions on county/district/candidate/GoTo
  controls; entering the street fragment at stage ③ is the one stage where
  keystrokes are allowed and those keystrokes are **not** counted as taps. No
  full-keyboard typing is required for the county/district stages.
- **SC-002**: Locality detection (coordinate → 縣市 + 鄉鎮市區) completes in
  **≤ 100 ms p95** on the reference device and **does not open any per-county
  address database** to do so (verified by the address databases remaining
  unopened during a pan that only updates the locality).
- **SC-003**: The reverse on-map readout, after adopting county scoping, MUST NOT
  regress the 004/005 latency budget: median reverse-lookup **≤ 1000 ms**
  (p95 **≤ 2000 ms**) across 100 random pans inside the union of active county
  bboxes, measured **on a real device** (not emulator), reported as p50/p95.
- **SC-004**: Memory does not balloon from holding the boundary layer plus the
  active county datasets: plugin process RSS during a 5-minute panning session
  with the boundary layer mounted and ≥ 2 counties active stays **≤ 200 MiB**
  (same budget as feature 005 SC-005), measured on a real device.
- **SC-005**: County detection is correct for known points: a fixed set of
  reference coordinates (urban centres, town halls, an offshore point) resolves
  to the correct 縣市 + 鄉鎮市區 (or "no county" offshore) with **100 %**
  agreement against the boundary data (the research note's executed probe is
  8/8; the device test MUST match).
- **SC-006**: Street matching finds segmented roads that exact-equality would
  miss: for a road that exists only as segments (e.g. 向上路), forward search
  returns candidates (non-zero), demonstrating the substring-incl-`段` rule.
- **SC-007**: Forward search confines its address-database access to the single
  selected county — during a forward search in one county, no other county's
  address database is queried (verifiable by instrumentation/logging).

## Assumptions

1. The companion generator's `townships.sqlite` (MOI authoritative boundary
   shapefiles, release `1140318`) is the source of truth for locality detection,
   with `county_zh` inline on every 鄉鎮市區 polygon and a spatial index present.
   No generator change is required by this feature.
2. The boundary data ships in the generator's base bundle and is imported by the
   operator the same way per-county data is; feature 006 reclassifies it from
   "skipped supplementary" to "consumed".
3. The polygon-containment test runs on WGS84 multipolygon geometry; the research
   note has executed a dependency-free parser that resolves 8/8 reference points,
   so this capability is proven feasible without a heavy geometry library. The
   concrete library-vs-hand-rolled choice is a plan-phase decision.
4. Per-county address datasets keep their feature-005 shape (`places.sqlite` with
   a street column carrying the `段` suffix, a district key, and display text);
   forward search reads them, it does not change their schema or import.
5. The GoTo destination flow, the per-row debounce, and the confidence indicator
   from features 004/005 are reused unchanged; forward search composes with them.
6. Counties present in a regional bundle (e.g. `tw-central`) are a subset of all
   Taiwan counties, and peripheral counties may be partially represented; the UI
   reflects whatever the installed data contains.
7. On-device performance/memory measurement is a release gate (per the project's
   feature-006 roadmap), not a deferred polish item; emulator-only timing is not
   sufficient.
8. The reverse-path county-scoping change preserves the exact result for points
   that fall inside an active county; it changes how the result is computed, not
   what it is, for the common case.
