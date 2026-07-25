# Coordinate Systems, Coverage, and Accuracy

This reference defines the coordinate systems exposed by TW Coordinates and
the limits operators and maintainers must preserve.

## Supported systems

| System | Input and display model | Coverage |
|---|---|---|
| Taipower grid | 9-character 10 m or 11-character 1 m main-island code over TWD67 TM2 | Taiwan main island |
| TWD97 / TM2 | Integer easting and northing; zone 121 or 119 | Main island and outer islands |
| TWD67 / TM2 | Integer easting and northing; zone 121 or 119; accepted 4-parameter datum shift | Main island and outer islands |

WGS84 latitude/longitude remains the canonical interchange representation at
ATAK boundaries. Projected or grid coordinates are converted at the plugin
boundary.

## Zone behavior

- Zone 121 is the main-island default.
- Zone 119 applies to points west of 120°E, including Penghu, Kinmen, and
  Matsu.
- TWD readouts append `z119` when the non-default zone is active.
- Taipower input and formatting report out of range for outer-island points
  rather than returning a plausible but invalid code.

## Accuracy budget

The current accepted budget was compared against pyproj 3.6.1 and the
Ministry of the Interior 7-parameter Bursa-Wolf reference data supplied to the
project:

| Coordinate system | Typical error or resolution |
|---|---|
| TWD97 | Less than 1 m |
| TWD67, main island | Approximately ±3–5 m |
| TWD67, outer islands | Approximately ±10–20 m with the 4-parameter shift compared with the official 7-parameter transform |
| Taipower grid | 11-character code represents a 1 m sub-cell; coverage is main-island only |

These are transformation and representation limits, not a promise about the
Android device's GNSS accuracy or the accuracy of imported address records.

## Operator-visible behavior

- Out-of-range Taipower readouts show an explicit fallback instead of
  fabricating a grid code.
- TWD67 zone 119 displays an accuracy advisory.
- Address reverse lookup may display the nearest record, but preserves the
  exact ATAK host point.
- Coordinate formatting is locale-safe and deterministic.

## Regression evidence

The maintained JVM evidence includes:

- all 22 Taiwan county/city seats in `TaiwanCitiesAuthoritativeTest`;
- nine golden vectors in `GoldenVectors`, including four `pwa_map` landmarks
  and five Taipower cell-centroid regression vectors;
- the canonical Hualien Station 11-character vector
  `H7509 DB4016`;
- real-world out-of-range points including Naha Airport, Hong Kong IFC, and
  Tokyo Tower;
- forward/inverse and zone 119/121 regression coverage.

The exact test count is intentionally not duplicated here because it changes
as features are added. Use the complete Gradle JVM suite for current evidence.

## Sources and decisions

- [ADR-0001 — coordinate math source](../adr/0001-coordinate-math-source.md).
- [ADR-0008 — accepted post-MVP precision and outer-island behavior](../adr/0008-post-mvp-iterations.md).
- [ADR-0022 — minimum ATAK runtime](../adr/0022-set-minimum-atak-runtime-to-5-5.md).
- [ADR-0024 — ATAK-CIV 5.7.0.9 compile SDK](../adr/0024-use-atak-5-7-0-9-compile-sdk.md).
- [Proj4J](https://github.com/locationtech/proj4j).
- [NCKU GIS coordinate converter](http://gis.thl.ncku.edu.tw/coordtrans/coordtrans.aspx)
  for independent spot checks.
