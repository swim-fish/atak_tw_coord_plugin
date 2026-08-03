# Compact Structured Address Layout Contract

## Purpose

Define the observable UI contract for the `v1.5.1` structured Address
presentation inside ATAK's native Taiwan coordinate-entry pane.

## Geometry

1. The Address body retains one left content area and one top-aligned right
   action area at the established 8:2 proportion.
2. Structured content contains exactly two horizontal row containers.
3. Row 1 contains, in order, county/city and district/township field groups.
4. Row 2 contains, in order, road/locality and house-number/floor field groups.
5. Each field group has zero fixed width plus equal row weight, producing a 1:1
   split within its row.
6. Each field group retains its visible label and input/selector, with the
   established internal label/input proportion and single-line value display.
7. The alternate-mode action remains in the far-right action area with its
   existing 48 dp height. Candidate selection remains below it when visible.
8. The pane retains exactly one outer vertical scroll owner and introduces no
   horizontal or nested vertical scroll owner.
9. At the 900 dp reference width with no visible status/candidate content, the
   compact structured form shrink-wraps below the existing 216 dp pane cap.

## Interaction and accessibility

1. View and accessibility order is county/city, district/township,
   road/locality, then house-number/floor.
2. County/city and district/township retain selector clickability, focusability,
   enabled/disabled state, and read-only behavior.
3. Road/locality retains Next targeting house-number/floor.
4. House-number/floor retains its final Search/Done behavior.
5. Existing labels, hints, and content descriptions remain sourced from the
   aligned English, Traditional Chinese (Taiwan), and Japanese resources.
6. No interactive target required by the existing contract is reduced below
   48 by 48 dp.

## Behavior invariants

- No production Java binding or controller behavior needs to change.
- Mode switching does not alter represented draft content or exact host WGS84.
- Address normalization, lookup, candidates, locality ordering, Auto Fill,
  Clear, Copy, reverse no-snap, read-only, locale replacement, and lifecycle
  behavior remain unchanged.
- Taipower, TWD97, TWD67, ATAK host controls, permissions, dependencies,
  network behavior, and coordinate results remain unchanged.

## Verification boundary

- Robolectric verifies hierarchy, equal weights, ordering, one scroll owner,
  compact measurement, action geometry, and existing selector/editor states.
- Existing JVM suites verify behavior invariants.
- Physical-device release gates verify real ATAK pane reachability, clipping,
  touch targets, TalkBack/Switch Access, font scales, orientations, and the
  100 ms p95 feedback budget on ATAK 5.7.0.9 and exact ATAK 5.5.x.
