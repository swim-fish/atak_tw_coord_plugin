# Coordinate Transformation Accuracy Review

**Date:** 2026-07-24  
**Scope:** WGS84 ↔ TWD97 TM2 and TWD97 ↔ TWD67 TM2 in the ATAK Taiwan coordinate plugin

## Existing conversion pipeline

The plugin performs two distinct operations:

1. **WGS84 ↔ TWD97 TM2** with Proj4J and explicit Transverse Mercator definitions:
   GRS80 ellipsoid, scale factor 0.9999, false easting 250,000 m, false northing 0 m,
   and central meridian 121° or 119°.
2. **TWD97 ↔ TWD67** with a planar four-parameter conformal transform:
   `A = 0.00001549`, `B = 0.000006521`, `ΔX = 807.8 m`, and `ΔY = 248.6 m`.

The projection parameters are appropriate. Most practical error came from datum transformation,
zone selection, test coverage, regional-model routing, and datum-realization assumptions rather than
from the TM2 projection formula itself.

## Findings

### 1. The previous TWD67 inverse was only a first-order approximation

The forward transform is an affine linear system. The former inverse repeated the coefficients with
opposite translations instead of solving that system. Over Taiwan-sized coordinates it introduced
up to about **1.2 cm** of avoidable round-trip error.

The revised implementation uses the exact inverse determinant. This removes the mathematical
round-trip error to floating-point noise. It does not remove residual error in the empirical datum
model itself.

### 2. One nationwide four-parameter transform is not uniformly accurate

Benchmarking against a stratified selection from the OSGeo Taiwan datum common-point table produced
these radial residuals:

| Region / model | Points | Mean | RMS | Maximum |
|---|---:|---:|---:|---:|
| Main island, established four-parameter model | 33 | 0.712 m | 0.819 m | 1.528 m |
| Penghu, established four-parameter model | 42 | 9.998 m | 10.065 m | 13.578 m |
| Penghu, regional similarity model | 42 | 0.125 m | 0.150 m | 0.504 m |
| Penghu, leave-one-out validation of the model family | 42 | 0.134 m | 0.166 m | 0.599 m |

The selected Penghu model is:

```text
E67 = -502.543492499 + 0.999998583003 × E97 - 0.000124634365 × N97
N67 =  161.813279315 + 0.000124634365 × E97 + 0.999998583003 × N97
```

It is a regional least-squares model derived from 42 published common points. It is not a national
official grid model and must not be represented as cadastral-grade authority.

### 3. A hard regional-model boundary is discontinuous and not globally invertible

Selecting one affine model inside a rectangle and another outside it creates two problems. First,
the output can jump by roughly the difference between the models when a coordinate crosses the
artificial edge. Second, the two output images overlap near that edge, so an inverse selected only
from the numeric coordinate can choose the wrong model. Corner-only round-trip tests do not detect
this outside-edge ambiguity; stress sampling found possible 10–17 m mismatches.

The revised mapping applies the full Penghu model throughout a calibration core containing all 42
observed controls, the compatibility model outside a wider envelope, and a cubic smoothstep blend in
between:

```text
F(E,N) = Fmain(E,N) + w(E,N) × (Fpenghu(E,N) - Fmain(E,N))
```

The inverse solves this smooth mapping by fixed-point iteration around the exact main-model inverse.
A 289-point transition grid and 100,000 random zone-119 coordinates had sub-nanometre numerical
round-trip residual in independent Java checks. The transition band is an engineering continuity
measure, not an additional geodetic-accuracy claim outside the calibrated Penghu core.

### 4. Longitude-only TM2 zone selection misclassified part of Matsu

The former rule selected zone 119 only for longitude below 120°E. Published Matsu control points
show that the archipelago, including Dongyin and Liangdao east of 120°E, uses TM2 zone 119.
Location-aware selection now assigns three Matsu island-group envelopes to zone 119.

### 5. Kinmen and Matsu do not provide a sound TWD67 accuracy target

The OSGeo test-point table labels the displayed TWD67 values for Kinmen and Matsu as
software-converted values because those areas did not historically adopt TWD67. They are useful for
projection-zone and algebraic regression tests, but they are not independent observed TWD67 control
coordinates. The plugin therefore retains the legacy fallback for compatibility and does not claim
metre-level accuracy there.

### 6. WGS84 and TWD97 realization/epoch is the next precision ceiling

For ordinary GPS display and navigation, treating WGS84 and TWD97 as coincident is normally
adequate. Survey-grade work must identify the realization and epoch, such as TWD97, TWD97[2010],
or TWD97[2020], and account for crustal motion. A static zero-parameter WGS84/TWD97 relationship
cannot guarantee centimetre-level results through time.

NLSC's current realization-conversion program uses a grid-difference model with bilinear
interpolation for TWD97, TWD97[2010], and TWD97[2020]. It is the appropriate direction for
higher-precision realization conversion, but it is not a TWD67 conversion grid and its distributed
binary grid-data licensing/update contract must be resolved before bundling it in the plugin.

## Implemented changes

- Exact inverse matrix for the established four-parameter TWD67 model.
- Regional 42-point two-dimensional similarity transform for the Penghu calibration core.
- Smooth regional-to-compatibility transition with iterative inverse, eliminating hard-boundary
  jumps and model-selection ambiguity.
- Location-aware TM2 zone selection for all of Matsu.
- Proj4J upgrade from 1.3.0 to 1.4.3. Version 1.4.2 fixed GRS80/WGS84 recognition and
  projected datum-shift handling; 1.4.3 retains those fixes.
- An 88-point CSV regression fixture:
  - 33 geographically stratified main-island observed TWD97/TWD67 common points;
  - 42 Penghu observed common points;
  - 5 Kinmen projection/reference points;
  - 8 Matsu projection/reference points.
- Forward, inverse, zone-selection, regional-residual, leave-one-out, continuity, transition-grid,
  regional-isolation, and exact round-trip tests.

## Recommended precision tiers

| Use case | Recommended path |
|---|---|
| ATAK display, navigation, search, and marker placement | Current Proj4J TM2 projection plus the revised regional datum models |
| Main-island TWD67 interoperability | Established four-parameter model; expect roughly 1–2 m against the sampled controls |
| Penghu TWD67 interoperability | Revised regional similarity model; sampled common-point residual below 0.55 m |
| Kinmen or Matsu | Prefer WGS84/TWD97 zone 119; label TWD67 as compatibility-only unless local observed control points are supplied |
| TWD97 realization conversion | Integrate the appropriate NLSC grid-difference dataset and epoch/realization metadata |
| Engineering, cadastral, or centimetre-level work | Use NLSC control points, the required TWD97 realization/epoch, and an official/local grid or surface model |

## Sources

- National Land Surveying and Mapping Center, plane control and coordinate-system introduction:
  https://www.nlsc.gov.tw/cp.aspx?n=1482
- National Land Surveying and Mapping Center, coordinate realization conversion program:
  https://www.nlsc.gov.tw/cp.aspx?n=1674
- OSGeo Taiwan datums:
  https://wiki.osgeo.org/wiki/Taiwan_datums
- OSGeo Taiwan datum test points:
  https://wiki.osgeo.org/wiki/Taiwan_datums/Test_points
- Proj4J release notes:
  https://github.com/locationtech/proj4j/releases
