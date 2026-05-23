# ADR-0001: Coordinate math sourced verbatim from `pwa_map`

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: `/speckit-plan` on feature `001-tw-coord-display` (filed retroactively during `/speckit-analyze` on 2026-05-16 in response to analyze finding F3)

## Context

Feature 001-tw-coord-display requires conversion from WGS84 to three
Taiwan coordinate systems:

- **TWD97** / TM2 zone 121 (EPSG:3826)
- **TWD67** / TM2 zone 121 (EPSG:3828-like, but with a 4-parameter
  datum shift)
- **Taipower grid** (台電座標) — a Taiwan-Power-Company-internal grid
  that is NOT a standard EPSG CRS

The spec (FR-003, FR-011) and SC-005 (1 m agreement with the reference
implementation) require a single authoritative source for the math so
that the same test vectors flow through both implementations.

The user nominated a local clone of the `pwa_map` repository as the
reference (`spec.md` Assumptions). The Phase-0 research agent extracted
the full algorithm verbatim (`research.md` R8).

## Decision

Port the algorithms from `pwa_map` into the plugin's pure-Java
`coord/` package, preserving constants and intermediate steps so the
four published golden test vectors (Taipei 101, Kaohsiung 85 Sky
Tower, Taichung City Hall, Hualien Railway Station) pass within the
documented tolerances.

Concrete shape:

- **TWD97**: use `org.locationtech.proj4j` 1.3.x with the EPSG:3826
  proj-string `+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000
  +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs`
  (identical to `pwa_map src/coord/twd97.ts:6-8`).
- **TWD67**: hand-rolled 4-parameter datum shift *from* TWD97 — NOT a
  direct WGS84 → TWD67 path. Constants: Δx = 807.8 m, Δy = 248.6 m,
  a = 1.549 × 10⁻⁵, b = 6.521 × 10⁻⁶
  (identical to `pwa_map src/coord/twd67.ts:4-14`).
- **Taipower grid**: hand-rolled grid arithmetic built on the TWD67
  output. Anchors: `ANCHOR_E_WEST = 90 000`,
  `ANCHOR_N_SOUTH = 2 400 000`, `REGION_WIDTH = 80 000`,
  `REGION_HEIGHT = 50 000`, 8 rows × 4 columns of letter regions per
  the OSGeo / Jidanni / Sunriver consensus TAIWAN_MAP layout (Y/Z
  reserved offshore; I, S, X and blank cells out of coverage in v1).
  Sub-region 800 m × 500 m; 100 m letter A–J × A–J; 10 m digits 0-9;
  optional 1 m digits at precision 11. (See the 2026-05-23 follow-up
  note below — pwa_map's original 8 × 3 / anchor-170 000 layout
  mis-labelled rows 3–7 and has been replaced.)

The four golden vectors are the source of truth for v1 tests
(`contracts/coordinate-converter.md`).

## Alternatives considered

- **Hand-roll TM2 trigonometry for TWD97 instead of proj4j.** ~100
  lines of well-known math but introduces an unverified
  implementation between us and a well-tested library. Rejected.
- **Use proj4j EPSG:3828 directly for TWD67.** Critical pwa_map
  warning (its ADR 0004) says off-the-shelf EPSG:3828 omits the
  4-parameter shift, yielding a *silent ~400 m error*. Rejected.
- **Independent re-derivation from official government documents.**
  Slower, no measurable accuracy benefit, no test cross-check with
  `pwa_map`. Rejected.

## Consequences

**Positive:**

- Same test vectors transfer verbatim → cross-validation between
  implementations.
- Failing tests pinpoint either our math or pwa_map's, both
  comparable.
- Constitution Principle II (TDD) is easy: golden vectors arrive
  pre-validated.

**Negative:**

- We inherit pwa_map's accuracy budget (TWD67 ±3 m by the linearised
  4-parameter model).
- Future changes to pwa_map's algorithm require a coordinated update
  here; we MUST track upstream changes manually.
- The Taipower grid is unsupported outside the 8 × 4 main-island grid
  (letters I underwater; S, X, Y, Z reserved for offshore anchors not
  yet implemented); Penghu / Matsu / Kinmen users see "out of range".

## Links

- Spec: FR-003, FR-011, SC-005
- Plan: `research.md` R8
- Contracts: `contracts/coordinate-converter.md`
- Upstream provenance: `pwa_map/tests/unit/fixtures/test-vectors.json`
  v2.0.0; Taiwan Coordinate Systems Reference v2.0.0 (MIT) §6 and §8

## 2026-05-23 follow-up — Taipower letter-table correction

**Reported by**: end-user bug ticket
`L0593BA86 → (23.9217149, 121.0492016)`. Our decoder placed
`L0593BA86` at TWD67 `(334 185, 2 646 565)` — in the Pacific Ocean
east of Hualien — instead of the user-supplied inland location.

**Root cause**: the letter table that we copied verbatim from
`pwa_map src/coord/taipower.ts` ships an 8-row × 3-column rectangle
anchored at easting 170 000 m TWD67. That layout dropped the
westernmost mainland column entirely. The actual Taiwan Power Company
mainland grid is 8 × 4 anchored at easting 90 000 m, with the western
column populated only for rows 3–5 (J, M, P) and otherwise blank.

Symptom by row:

| Row | pwa_map letters (anchor 170 km) | Correct letters (anchor 90 km)       |
|-----|---------------------------------|--------------------------------------|
| 0   | A, B, C                         | _, A, B, C — matches pwa_map         |
| 1   | D, E, F                         | _, D, E, F — matches pwa_map         |
| 2   | G, H, I                         | _, G, H, _ (I = underwater)          |
| 3   | J, K, L                         | J, K, L, _ — shifted east by 80 km!  |
| 4   | M, N, O                         | M, N, O, _ — shifted east by 80 km!  |
| 5   | P, Q, R                         | P, Q, R, _ — shifted east by 80 km!  |
| 6   | S, T, U                         | _, T, U, _ (S = Matsu offshore)      |
| 7   | V, W, X                         | _, V, W, _ (X = Penghu offshore)     |

The shift mis-labelled Kaohsiung 85 building as P0703 (should be
Q0703) and put L outside the main island. The user's L0593BA86 maps
correctly to the central-Hualien inland cell at TWD67
`(254 185, 2 646 565)` under the corrected layout.

**Decision**: replace `TaipowerGrid.REGION_LETTERS` (and the parser
mirror in `TaipowerParser`) with the OSGeo / Jidanni / Sunriver
consensus layout. Update the Kaohsiung 85 golden vector from
`P0703 CC43` to `Q0703 CC43` and add a fifth golden vector
`L0593 BA86 ↔ (23.9217588, 121.0492519)` so the L region is exercised
by `TaipowerGridTest`.

**References**:
- <https://wiki.osgeo.org/wiki/Taiwan_Power_Company_grid>
- <https://www.jidanni.org/geo/taipower/programs/taipowergrid>
  (Perl `TAIWAN_MAP` constant)
- <https://www.sunriver.com.tw/grid_taipower.htm>
- User-supplied verification tool
  <https://linspace.somee.com/TPCToMap/>

**Provenance note**: pwa_map remains the upstream reference for TWD97
/ TWD67 math; the Taipower grid table is now sourced from the OSGeo
consensus instead of pwa_map, since the latter is empirically wrong
for the mainland-west column. We MUST flag this divergence whenever
re-syncing with pwa_map.
