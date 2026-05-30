<!-- SPECKIT START -->
<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
at `specs/006-county-forward-search/plan.md` along with its companion
docs (`research.md`, `data-model.md`, `contracts/*.md`, `quickstart.md`).
<!-- SPECKIT END -->

Active feature: **006-county-forward-search** — adds offline **forward**
address search (text/pick → coordinate) as a county-first funnel, and
begins consuming `townships.sqlite` (the MOI authoritative boundary layer
that feature 005 imports but skips). Highlights:
- **Locality detection** from the ~10 MB `townships.sqlite` alone — a
  coordinate resolves to 縣市 + 鄉鎮市區 via R*Tree bbox + WKB
  MultiPolygon point-in-polygon, **without opening any 100–324 MB place
  DB** (`county_zh` is inline on every district). "Don't load all 22
  counties at once" met by data shape.
- **County-first funnel**: ① county (所在地 / 地圖中心 / 清單; map-centre
  default; list read from `townships.sqlite`, never hard-coded) → ②
  鄉鎮市區 (pre-highlighted when from SELF/MAP_CENTER) → ③ street
  substring (incl. `段`, 臺↔台 + width fold) → ④ house-number / distance
  pin → confirm → existing GoTo (no auto-pan).
- **Reverse-path county scoping**: the on-map readout resolves county via
  the boundary facade first, then queries only that county's facade —
  removing 005's query-all-active-counties fan-out (no operator-visible
  change for in-county points).
- **Glove + ATAK tool-panel UX**: single column, ≥48dp targets, tap-only
  ①②, numeric keypad for house numbers, confirm-before-GoTo.
- Out of scope: `roads.sqlite` (Tier-2), `places-osm.sqlite` landmarks,
  global FTS no-anchor search, active-root migration, edit-distance typo
  tolerance. The new WKB parser is hand-rolled (~0 KB, proven 8/8 by
  `scripts/verify_polygon_in.py`); JTS held in reserve.

Design input: `docs/research/county-scoped-forward-search.md` (measured +
executed against the `tw-central-full.zip` 10:50 build, SHA-256
`28a10e7d…`); re-verify with `scripts/{measure_tw_central,verify_research_claims,verify_polygon_in}.py`
after any generator rebuild.

Builds on the shipped:
- **005-multi-county-zip-import** (`specs/005-multi-county-zip-import/`) —
  multi-county per-county active datasets + ZIP import + the
  `ActiveDatasetRegistry` / `AddressSubsystem` / `AtakDatabasesAddressDatabase`
  / `FallbackSqliteFactory` / `ZipEntryClassifier` seams that feature 006
  extends. ADR-0017 records its decisions.
- **004-offline-address** (`specs/004-offline-address/`) — the
  single-active-dataset reverse-geocode flow. ADR-0014 (recon) + ADR-0015
  (implementation) record the SDK + on-device pivots (ImportFileBrowserDialog,
  ATAK native SQLite for R*Tree, AlertDialog Activity context).
- **001-tw-coord-display**, **002-tw-coord-goto**,
  **003-custom-marker-icon** — earlier features whose patterns
  (TwCoordWidget, DropDownReceiver, TwCoordGotoView GoTo plumbing) feature
  006 composes unchanged.

Sibling generator project: `atak-tw-address-generator` at
`C:\Users\hhhnr\source\tak\atak_vns_offline_routing\atak-tw-address-generator`.
Its [data-contract.md v2](file:///c/Users/hhhnr/source/tak/atak_vns_offline_routing/atak-tw-address-generator/docs/data-contract.md)
defines the `townships.sqlite` (§3.2, MOI release 1140318) + per-county
`places-*.sqlite` shapes feature 006 consumes — no generator changes
required by this feature.

Plan-phase Phase 0 decisions live in
`specs/006-county-forward-search/research.md` (R1–R6: hand-rolled WKB
parser, boundary-facade query shape, boundary DB mount/lifecycle,
`ZipEntryClassifier` reclassification, district-scoped street query,
reverse-path scoping + on-device measurement). The Plan-phase code
anchoring discipline (cite both `javap -public` against
`ATAK-CIV-5.7.0.3-SDK/main.jar` AND upstream permalinks on
`github.com/TAK-Product-Center/atak-civ`) is captured in the user-level
memory `feedback-plan-phase-code-anchoring.md`.
<!-- SPECKIT END -->
