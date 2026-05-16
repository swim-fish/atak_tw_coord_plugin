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

The user nominated `C:\Users\hhhnr\source\repos\pwa_map` as the
reference (`spec.md` Assumptions). The Phase-0 research agent
extracted the full algorithm verbatim (`research.md` R8).

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
  output. Anchors: `ANCHOR_E_WEST = 170 000`,
  `ANCHOR_N_SOUTH = 2 400 000`, `REGION_WIDTH = 80 000`,
  `REGION_HEIGHT = 50 000`, 8 rows × 3 columns of letter regions
  excluding Y/Z. Sub-region 800 m × 500 m; 100 m letter A–J × A–J;
  10 m digits 0-9; optional 1 m digits at precision 11
  (identical to `pwa_map src/coord/taipower.ts:82-150`).

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
- The Taipower grid is unsupported outside the 8 × 3 main-island grid
  (letters Y/Z rejected); Penghu / Lanyu users see "out of range".

## Links

- Spec: FR-003, FR-011, SC-005
- Plan: `research.md` R8
- Contracts: `contracts/coordinate-converter.md`
- Upstream provenance: `pwa_map/tests/unit/fixtures/test-vectors.json`
  v2.0.0; Taiwan Coordinate Systems Reference v2.0.0 (MIT) §6 and §8
