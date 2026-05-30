# Implementation Plan: County-Scoped Forward Address Search

**Branch**: `006-county-forward-search` | **Date**: 2026-05-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-county-forward-search/spec.md`

## Summary

Add **offline forward address search** to the plugin as a county-first funnel
(縣市 → 鄉鎮市區 → street substring → house-number/distance pin → GoTo), and
**begin consuming `townships.sqlite`** — the MOI authoritative boundary layer
that feature 005 imports but classifies as a skipped supplementary file. The
boundary layer (≈10 MB, `county_zh` inline on every district) answers "which
縣市 + 鄉鎮市區 is this coordinate in?" via an R*Tree bbox prefilter + WKB
MultiPolygon point-in-polygon test, **without opening any 100–324 MB per-county
address database**. The same boundary facade is then used to scope the existing
reverse on-map readout to a single county, replacing 005's query-every-active-
county haversine fan-out.

This is plugin-side work on already-shipped generator data; no generator change
is required. The research note
[`docs/research/county-scoped-forward-search.md`](../../docs/research/county-scoped-forward-search.md)
(measured + executed against the `tw-central-full.zip` 10:50 build, SHA-256
`28a10e7d…`) is the design input; its load-bearing claims are already proven by
`scripts/verify_polygon_in.py` (8/8 reference points resolve correctly with a
dependency-free WKB parser) and `scripts/verify_research_claims.py`.

Three clarifications resolved in spec (Session 2026-05-30): (1) county picker
defaults to **map-centre** when GPS and map-centre disagree; (2) v1 street match
is **substring + glyph/width fold only** (no edit-distance); (3) the reverse path
**adopts townships-first county scoping** in this same feature.

Technical approach hinges on four pieces, three of which extend 004/005 seams and
one of which is genuinely new:

1. **NEW — boundary facade.** A `TownshipBoundaryFacade` over `townships.sqlite`
   that runs the Tier-1 query (R*Tree bbox → WKB `covers`) and returns
   `(county, district, approx)`. Needs a **WGS84 WKB MultiPolygon parser +
   point-in-polygon** — the one new capability (today's facades only do point
   R*Tree + haversine, never geometry). Library-vs-hand-rolled settled in Phase 0
   R1; the research note already proves a ~80-line hand-rolled parser resolves
   8/8 reference points.
2. **EXTEND `ZipEntryClassifier` + import** to consume `townships.sqlite` as a
   mounted singleton (one boundary DB for the whole plugin, not per-county),
   reclassifying it from `SKIPPED_SUPPLEMENTARY` to a consumed `BOUNDARY` bucket.
3. **NEW — forward-search subsystem + page.** A `ForwardSearchController` (pure
   logic: funnel state, street matching/folding, distance ranking) plus a
   `ForwardSearchReceiver` (`DropDownReceiver`) implementing the glove UX. Street
   matching reuses each county's existing `AddressDatabaseFacade` extended with a
   district-scoped street query.
4. **EXTEND `AddressSubsystem`** so reverse lookup first resolves county via the
   boundary facade then queries only that county's facade (removing the
   `lookupAcrossAllCounties` fan-out for in-county points), reusing the same
   boundary facade as forward search.

All SDK claims in this plan and its companion docs are anchored to **both**
`javap -public` against `../ATAK-CIV-5.7.0.3-SDK/main.jar` and the upstream Java
source at `github.com/TAK-Product-Center/atak-civ` (default branch `main`), per
the `feedback-plan-phase-code-anchoring` memory + ADR-0014/0015 precedent. SDK
surfaces this feature relies on, verified via `javap -public` against the bundled
jar at plan time:
- `com.atakmap.android.maps.MapView` — `getCenterPoint()` and `getSelfMarker()`
  (confirmed `public`; `getCenterPoint()` returns
  `com.atakmap.coremap.maps.coords.GeoPointMetaData`, `getSelfMarker()` returns
  `com.atakmap.android.maps.Marker`). These supply the map-centre / self-marker
  anchors for locality detection and distance ranking.
- `com.atakmap.coremap.maps.coords.GeoPoint` — `getLatitude()` / `getLongitude()`
  (confirmed `public`).
- **GoTo is reused, not re-implemented.** The plugin already pans via
  `com.atakmap.map.CameraController$Programmatic.panTo(getRenderer3(), GeoPoint,
  boolean)` inside `TwCoordGotoView` (`TwCoordGotoView.java:783` / `:877`;
  class present in the jar as `com/atakmap/map/CameraController$Programmatic`).
  Feature 006 routes a confirmed forward-search result through that existing path
  (via `TwCoordGotoView` / the GoTo receiver), so it introduces **no new camera
  call site** of its own. (Note: the class is `com.atakmap.map.CameraController`,
  not `com.atakmap.android.maps.CameraController`.)

## Technical Context

**Language/Version**: Java 11 (sourceCompatibility / targetCompatibility 11 in
`app/build.gradle`; same as features 004/005).

**Primary Dependencies**:
- ATAK-CIV-SDK 5.7.0.3 (`compileOnly`, jar at `../ATAK-CIV-5.7.0.3-SDK/main.jar`)
- ATAK native SQLite via `com.atakmap.database.Databases` (R*Tree-enabled, 004 D2)
  — reused for both the boundary DB and the place DBs.
- AndroidX preferences + appcompat (existing)
- Existing per-county SQLite facade (`AtakDatabasesAddressDatabase`) + the 005
  `FallbackSqliteFactory` opt-in path — reused unchanged for street queries.
- **NEW potential**: a WKB geometry parser for the boundary facade. Two candidates
  evaluated in Phase 0 R1: (a) a hand-rolled minimal WKB-MultiPolygon reader +
  ray-cast PIP (~0 KB, already prototyped in `scripts/verify_polygon_in.py`),
  (b) `org.locationtech.jts` `WKBReader` + `Geometry.covers` (~1 MB). No external
  archive lib needed (boundary DB is a plain SQLite, not a ZIP at runtime).
- Existing test stack: JUnit 4, Mockito, Robolectric (test-only), xerial
  sqlite-jdbc 3.x (test-only, R*Tree-enabled).

**Storage**: One boundary DB mounted singleton at the plugin's existing
offline-address root (exact path settled in R3 — sibling to `active/`, e.g.
`active/_boundary/townships.sqlite`, kept under the same root so 005's
staging/sweep discipline applies). Per-county place DBs unchanged
(`active/<county>/places.sqlite`).

**Testing**: `./gradlew :app:testCivDebugUnitTest` for JVM (Robolectric + xerial)
covering pure logic (WKB parser, PIP, funnel state, street folding/ranking,
boundary facade against a fixture `townships.sqlite`); on-device Espresso for the
SC-003/SC-004/SC-005 measurements (builds on the 005 R9 harness).

**Target Platform**: Android API 26+ via ATAK-CIV 5.7.x (same baseline as 004/005).

**Project Type**: Android ATAK plugin (single APK module at `app/`); no new
module / flavour / sourceSet.

**Performance Goals** (from spec SC):
- SC-001 forward search to confirmed GoTo in ≤ 5 taps / ≤ 30 s
- SC-002 locality detection perceptibly instant, opens **no** place DB
- SC-003 reverse lookup median ≤ 1000 ms (p95 ≤ 2000 ms) across 100 real-device
  pans — must not regress 005
- SC-004 RSS ≤ 200 MiB with boundary layer + ≥ 2 counties, real-device
- SC-005 county detection 100 % correct on the reference point set (matches the
  research note's executed 8/8)

**Constraints**:
- Zero outbound network (inherited from 004 FR-019: no `INTERNET` permission)
- Locality detection MUST NOT open a place DB (SC-002 / SC-007)
- Forward search MUST query only the selected county's place DB (SC-007 / FR-008)
- Reverse-path county scoping MUST preserve the exact in-county result (FR-014)
- Lean APK: WKB parser choice carries a soft budget; hand-rolled is ~0 KB
- Constitution VI (Host-Process Isolation): every new entry point
  (`ForwardSearchReceiver.onReceive` / `onDropDownClose`, every button/keypad
  `OnClickListener`, the boundary-facade query path reachable from the resolver
  worker, the new Tools-menu tool) wrapped in `try / catch (Throwable)` → `Log.w`.

**Scale/Scope**:
- Boundary layer: ≈10 MB, 12 縣市 + 31 直轄市區 + 105 縣轄鄉鎮市 = 136 polygons
  for `tw-central` (nationwide build would carry all of Taiwan's divisions);
  worst-case geometry 宜蘭縣 ≈534 KB / 102 polygons / 34,109 vertices. R*Tree
  prunes to 1–3 candidate polygons per lookup (measured).
- Per-county place DB: unchanged from 005 (≤ ~731k rows / ~324 MB Taichung).
- District-scoped street candidate set: a fraction of a 1 km bbox (≈3k rows
  county-wide), trivially app-side matchable.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| **I. Code Quality & Formatting** | ✅ Pass | Spotless + lint gates apply; new resource strings follow the ADR-0015 D4 StringFormatMatches precedent. New pure-logic classes (WKB parser, PIP, funnel, street matcher) are self-contained and JVM-testable. |
| **II. Test-First Development (TDD)** | ⚠ Conditional | JVM unit tests authored first for every new pure-logic class: `WkbMultiPolygonParser`, `PointInPolygon`, `TownshipBoundaryFacade` (against a fixture `townships.sqlite`), `ForwardSearchController` (funnel + folding + ranking), the district-scoped street query. On-device Espresso for SC-003/004/005 builds on the 005 R9 harness. **Conditional pass** until R6 confirms the harness reuse. |
| **III. UX Consistency** | ✅ Pass | New page → new `docs/ui/forward-search-page.md` (Principle III mandates a `docs/ui/` entry for a new flow); glove UX rules (≥48dp, tap-first, numeric keypad, confirm-before-GoTo) recorded there. New strings: en + zh-rTW + ja parity (echoes 004/005 FR-018). Reuses the confidence-tilde + GoTo conventions for continuity. |
| **IV. Performance** | ✅ Pass | SC-002/003/004/005 are explicit and measured on-device (not emulator), per the feature-006 roadmap memory. Locality detection budget is bounded by the 1–3 candidate-polygon prune (measured). Reverse-path change is a net reduction in work for the common case (1 county queried vs N). |
| **V. Documentation & Knowledge Preservation** | ✅ Pass | ADR-0018 authored post-implement (next after 0017) capturing the WKB parser decision, boundary-facade design, reverse-path scoping, and the Constitution VI audit. UI docs + README in the same change set. |
| **VI. Host-Process Isolation (NON-NEGOTIABLE)** | ✅ Pass (with audit task) | New entry points — `ForwardSearchReceiver` lifecycle + every button/keypad/list `OnClickListener`, the new Tools-menu tool's broadcast, the boundary-facade query reachable from the resolver worker and the forward-search worker, WKB parse on possibly-malformed bytes (defensive-validation rule) — MUST be wrapped per Principle VI. Explicit Polish-phase audit (mirrors 004 T056 / 005 ADR-0017 audit). The WKB parser MUST treat the blob as untrusted input and recover to "no locality" rather than throw (defensive-validation at boundaries). |

**No violations to justify in Complexity Tracking at gate entry.**

## Project Structure

### Documentation (this feature)

```text
specs/006-county-forward-search/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── township-boundary-facade.md
│   ├── wkb-multipolygon-parser.md
│   ├── forward-search-controller.md
│   ├── street-query.md
│   └── reverse-county-scoping.md
├── checklists/
│   └── requirements.md  # /speckit-specify validation output (already created)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created here)
```

### Source Code (repository root)

```text
app/
├── src/
│   ├── main/
│   │   ├── java/com/atakmap/android/twcoord/
│   │   │   ├── TwCoordMapComponent.java            # MODIFY: mount boundary facade at onCreate; wire ForwardSearchReceiver + tool; pass boundary facade to AddressSubsystem
│   │   │   └── address/
│   │   │       ├── geo/
│   │   │       │   ├── WkbMultiPolygonParser.java   # NEW: WGS84 WKB (type 3/6) → rings; defensive on malformed bytes
│   │   │       │   ├── PointInPolygon.java          # NEW: ray-cast PIP incl. holes
│   │   │       │   └── BoundaryGeometry.java        # NEW: parsed multipolygon + bbox + covers()
│   │   │       ├── boundary/
│   │   │       │   ├── TownshipBoundaryFacade.java  # NEW: R*Tree bbox → covers; (county,district,approx); snap tolerance
│   │   │       │   ├── LocalityResult.java          # NEW: (county, district, approx)
│   │   │       │   └── TownshipBoundaryFactory.java # NEW: open townships.sqlite via Databases (primary) / fallback
│   │   │       ├── forward/
│   │   │       │   ├── ForwardSearchController.java # NEW: funnel state, street folding + substring + distance rank
│   │   │       │   ├── CountySource.java            # NEW: enum {SELF, MAP_CENTER, LIST} + provenance
│   │   │       │   ├── ForwardSearchQuery.java      # NEW: county→district→fragment→houseNumber + anchor
│   │   │       │   ├── AddressCandidate.java        # NEW: display text + coord + distance
│   │   │       │   └── StreetTextNormaliser.java    # NEW: 臺↔台, fullwidth→halfwidth, 之→-, 段-spanning
│   │   │       ├── ForwardSearchReceiver.java       # NEW: DropDownReceiver — the glove UX page
│   │   │       ├── AddressDatabaseFacade.java       # MODIFY: add district-scoped street query method
│   │   │       ├── AtakDatabasesAddressDatabase.java# MODIFY: implement street query (native SQLite)
│   │   │       ├── SqliteAddressDatabase.java       # MODIFY: implement street query (test path)
│   │   │       ├── AddressSubsystem.java            # MODIFY: boundary-first reverse scoping
│   │   │       ├── ZipEntryClassifier.java          # MODIFY: townships.sqlite → BOUNDARY (consumed), not SKIPPED
│   │   │       ├── AtakFileSystem.java              # MODIFY: boundary-dir helper (sibling to active/)
│   │   │       └── (other 004/005 classes — unchanged)
│   │   ├── plugin/
│   │   │   └── ForwardSearchTool.java               # NEW: Tools-menu entry (mirrors OfflineAddressTool / TwCoordGotoTool)
│   │   ├── res/
│   │   │   ├── layout/forward_search_page.xml       # NEW: single-column funnel + numeric keypad + candidate list
│   │   │   ├── values/strings.xml                   # MODIFY: ~20 new keys (county/district pickers, keypad, states, errors)
│   │   │   ├── values-zh-rTW/strings.xml            # MODIFY: zh-TW parity
│   │   │   └── values-ja/strings.xml                # MODIFY: ja parity
│   │   └── AndroidManifest.xml                      # NO CHANGE expected (no new permission)
│   └── test/java/com/atakmap/android/twcoord/address/
│       ├── geo/WkbMultiPolygonParserTest.java       # NEW: type 3/6, holes, endianness, malformed → safe
│       ├── geo/PointInPolygonTest.java              # NEW: inside/outside/on-edge/hole cases
│       ├── boundary/TownshipBoundaryFacadeTest.java # NEW: 8/8 reference points (fixture townships.sqlite), snap tolerance, offshore→null
│       ├── forward/ForwardSearchControllerTest.java # NEW: funnel transitions, map-centre default, 段 substring, glyph/width fold, distance rank
│       ├── forward/StreetTextNormaliserTest.java    # NEW: 臺↔台, fullwidth digits, 之→-
│       ├── AddressDatabaseFacadeStreetQueryTest.java# NEW: district-scoped street query (xerial fixture)
│       ├── AddressSubsystemReverseScopingTest.java  # NEW: boundary-first reverse == old result for in-county points
│       └── (existing 004/005 tests — adapted only where signatures change)
└── build.gradle                                     # MODIFY ONLY IF R1 picks JTS (hand-rolled needs no dep)

docs/
├── adr/
│   └── 0018-county-forward-search.md                # NEW (after /speckit-implement)
├── ui/
│   └── forward-search-page.md                       # NEW: funnel + glove UX + states
└── research/
    └── county-scoped-forward-search.md              # EXISTING design input (committed d453681)
```

**Structure Decision**: Same single-module Android plugin layout as 001–005. New
classes are grouped into three new sub-packages under `address/` — `geo/`
(geometry primitives), `boundary/` (the township facade), `forward/` (the search
funnel) — to keep the new surface area legible and the pure-logic classes
JVM-testable in isolation, mirroring how 004/005 kept `address/` cohesive. No new
sourceSets/flavours/modules. `docs/adr/` gets one new ADR (0018); `docs/ui/` gets
one new file (forward-search-page.md).

## Complexity Tracking

> No Constitution Check violations to justify at plan-phase entry. The only new
> dependency risk is the WKB parser (R1): the hand-rolled option adds 0 KB and is
> already prototype-proven (8/8), so the default path introduces no new
> dependency. If R1 instead selects JTS, this table will record the APK-size
> trade-off and why hand-rolled was rejected.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| _(none at gate)_ | — | — |

Re-check after Phase 1 design (see end of research.md for the post-design gate result).
