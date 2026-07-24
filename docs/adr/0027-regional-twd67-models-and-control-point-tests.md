# ADR-0027: Use Regional TWD67 Models and Control-Point Regression Tests

**Status**: Accepted  
**Date**: 2026-07-24  
**Origin**: Coordinate transformation accuracy review on `agent/improve-coordinate-transform-tests`

## Context

ADR-0001 selected Proj4J for WGS84/TWD97 projection and a hand-implemented four-parameter
TWD97/TWD67 datum transform. The projection definitions remain appropriate, but review against a
larger control/common-point set identified four issues:

1. the reverse four-parameter implementation was a first-order approximation rather than the exact
   inverse of the forward matrix;
2. the main-island parameters produced roughly 10 m mean residual in Penghu;
3. longitude-only zone selection assigned zone 121 to Matsu islands east of 120°E even though Matsu
   uses zone 119;
4. a hard regional-model rectangle creates a discontinuity and can make the piecewise forward map
   non-injective near an artificial edge, so no inverse selector can round-trip both sides reliably.

The existing golden vectors were concentrated on zone 121 and were not sufficient to detect these
regional, transition, and inverse failures.

## Decision

1. Preserve the established four-parameter model for the main island and compatibility fallback,
   but calculate its reverse direction with the exact inverse matrix.
2. Apply a regional two-dimensional similarity transform inside a Penghu zone-119 core containing
   all 42 published observed TWD97/TWD67 common points. The coefficients are a least-squares fit to
   those points.
3. Between the Penghu core and a wider outer envelope, blend the regional correction into the
   compatibility model with a cubic smoothstep window. Outside the outer envelope, use the
   compatibility model exactly. The transition is a continuity mechanism, not an accuracy claim for
   unsampled ocean areas.
4. Invert the blended zone-119 mapping with bounded fixed-point iteration starting from the exact
   compatibility-model inverse. Verify both forward→inverse and inverse→forward behavior over a
   dense transition grid, including every outer and core edge.
5. Select TM2 zone 119 for three Matsu island-group envelopes, including Dongyin and Liangdao east
   of 120°E.
6. Upgrade Proj4J from 1.3.0 to 1.4.3.
7. Pin 88 external regression points in a test-resource CSV and test WGS84↔TWD97 projection,
   TWD97↔TWD67 datum transformation, zone selection, regional isolation, transition continuity,
   leave-one-out model behavior, and exact round trips. The fixture contains 33 stratified
   main-island points, all 42 published Penghu common points, 5 Kinmen points, and 8 Matsu points.
8. Treat Kinmen and Matsu TWD67 output as compatibility-only. Their fixture values are
   software-derived rather than observed legacy TWD67 control coordinates.
9. Do not claim survey-, engineering-, or cadastral-grade accuracy. Those uses require official
   control data, an identified TWD97 realization/epoch, and an official or locally fitted grid/surface
   model.

## Alternatives considered

- **Replace all regions with the published Bursa-Wolf seven-parameter transform.** Rejected because
  the published expected accuracy remains strongly regional and is materially worse than the
  42-point Penghu fit.
- **Use one nationwide planar fit.** Rejected because it hides regional deformation and degrades
  areas that the existing main-island transform already handles well.
- **Use only longitude to select the TM2 zone.** Rejected because it cannot classify all of Matsu.
- **Hard-switch between regional and compatibility models at one rectangle.** Rejected because the
  two affine maps differ by several metres near that edge; their images can overlap or leave gaps,
  making a single-valued inverse ambiguous.
- **Use separate axis-aligned rectangles in TWD97 and TWD67.** Rejected because it can make selected
  inputs round-trip while still leaving overlap and discontinuity for nearby inputs.
- **Claim Kinmen/Matsu accuracy against software-generated TWD67 values.** Rejected because those
  values are not independent observations.
- **Bundle an official transformation grid immediately.** Deferred until a redistributable,
  versioned, authoritative grid and update policy are identified.

## Consequences

- Main-island compatibility remains stable while forward/reverse algebra becomes exact.
- The 33 sampled main-island controls have about 0.71 m mean radial residual and 1.53 m maximum.
- Penghu sampled residual falls from about 10 m mean and 13.6 m maximum to about 0.13 m mean and
  0.51 m maximum.
- All observed Penghu controls receive the unblended regional model; the surrounding transition has
  continuous value and slope at its core and outer edges.
- The iterative inverse round-trips the dense transition test grids to micrometre-scale tolerance.
- Zone-119 projection now covers all tested Matsu points.
- Test coverage expands from a small zone-121 set to 88 external points across the main island,
  Penghu, Kinmen, and Matsu, plus explicit transition, boundary, and regional-isolation cases.
- The regional model adds an explicit validity core and transition policy that must be reviewed when
  control-point data or regional coverage changes.

## Links

- `docs/coordinate-transform-accuracy.md`
- `app/src/test/resources/coord/osgeo-taiwan-control-points.csv`
- `app/src/test/resources/coord/README.md`
- ADR-0001
