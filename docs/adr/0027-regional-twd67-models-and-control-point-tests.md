# ADR-0027: Use Regional TWD67 Models and Control-Point Regression Tests

**Status**: Accepted  
**Date**: 2026-07-24  
**Origin**: Coordinate transformation accuracy review on `agent/improve-coordinate-transform-tests`

## Context

ADR-0001 selected Proj4J for WGS84/TWD97 projection and a hand-implemented four-parameter
TWD97/TWD67 datum transform. The projection definitions remain appropriate, but review against a
larger control/common-point set identified three issues:

1. the reverse four-parameter implementation was a first-order approximation rather than the exact
   inverse of the forward matrix;
2. the main-island parameters produced roughly 10 m mean residual in Penghu;
3. longitude-only zone selection assigned zone 121 to Matsu islands east of 120°E even though Matsu
   uses zone 119.

The existing golden vectors were concentrated on zone 121 and were not sufficient to detect these
regional failures.

## Decision

1. Preserve the established four-parameter model for the main island and compatibility fallback,
   but calculate its reverse direction with the exact inverse matrix.
2. Apply a regional two-dimensional similarity transform inside a conservative Penghu zone-119
   coordinate envelope. The coefficients are a least-squares fit to 42 published TWD97/TWD67 common
   points.
3. Select TM2 zone 119 for three Matsu island-group envelopes, including Dongyin and Liangdao east
   of 120°E.
4. Upgrade Proj4J from 1.3.0 to 1.4.3.
5. Pin 64 external regression points in a test-resource CSV and test WGS84↔TWD97 projection,
   TWD97↔TWD67 datum transformation, zone selection, and exact round trips.
6. Treat Kinmen and Matsu TWD67 output as compatibility-only. Their fixture values are
   software-derived rather than observed legacy TWD67 control coordinates.
7. Do not claim survey-, engineering-, or cadastral-grade accuracy. Those uses require official
   control data, an identified TWD97 realization/epoch, and an official or locally fitted grid/surface
   model.

## Alternatives considered

- **Replace all regions with the published Bursa-Wolf seven-parameter transform.** Rejected because
  the published expected accuracy remains strongly regional and is materially worse than the
  42-point Penghu fit.
- **Use one nationwide planar fit.** Rejected because it hides regional deformation and degrades
  areas that the existing main-island transform already handles well.
- **Use only longitude to select the TM2 zone.** Rejected because it cannot classify all of Matsu.
- **Claim Kinmen/Matsu accuracy against software-generated TWD67 values.** Rejected because those
  values are not independent observations.
- **Bundle an official transformation grid immediately.** Deferred until a redistributable,
  versioned, authoritative grid and update policy are identified.

## Consequences

- Main-island compatibility remains stable while forward/reverse algebra becomes exact.
- Penghu sampled residual falls from about 10 m mean and 13.6 m maximum to about 0.13 m mean and
  0.51 m maximum.
- Zone-119 projection now covers all tested Matsu points.
- Test coverage expands from a small zone-121 set to 64 points across the main island, Penghu,
  Kinmen, and Matsu.
- The regional model adds an explicit geographic validity domain and requires future control-point
  updates to be reviewed as data/model changes.

## Links

- `docs/coordinate-transform-accuracy.md`
- `app/src/test/resources/coord/osgeo-taiwan-control-points.csv`
- ADR-0001
