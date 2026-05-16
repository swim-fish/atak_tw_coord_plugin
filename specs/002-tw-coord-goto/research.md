# Phase 0 Research — Taiwan Coordinate Input ("GoTo") Page

**Date**: 2026-05-16 | **Feature**: 002-tw-coord-goto | **Plan**: [plan.md](./plan.md)

The Technical Context in `plan.md` has **no NEEDS CLARIFICATION
markers** — the spec's Clarifications session (2026-05-16) and the
existing feature 001 already pin almost every variable. This document
records the remaining design decisions that shape Phase 1's
data-model + contracts and that future contributors will need to
trace back when they ask "why this and not that".

Decisions are presented in the canonical
`Decision / Rationale / Alternatives` form.

---

## R1 — UI shell: `DropDownReceiver` vs. floating `Activity`

**Decision**: Implement the input page as an ATAK
`com.atakmap.android.dropdown.DropDownReceiver` (the same idiom
used by `EnterLocationDropDownReceiver`, `RoutePlannerView`, and the
native ATAK detail panes). Layout is a single root XML inflated into
the DropDown side-pane.

**Rationale**:
- Constitution Principle III (UX Consistency) explicitly forbids
  ad-hoc styling and ad-hoc interaction patterns. ATAK's native
  input affordances are all DropDowns; floating an Activity would
  break that mental model.
- DropDown auto-handles the back-press, the slide animation, and the
  multi-pane resize behaviour operators already know.
- DropDowns coexist with the live map (the map stays visible while
  the page is open), which is *required* for Auto Fill — the
  operator needs to see the map centre move under their finger and
  the Auto Fill button enable/disable in real time.

**Alternatives considered**:
- **Floating `android.app.Dialog` / `AlertDialog`**. Hides the map
  underneath; Auto Fill UX collapses. Rejected.
- **Full-screen `Activity`**. Worst case: leaves ATAK's map
  context behind entirely, returning to it on submit. Defeats the
  Auto Fill workflow. Rejected.
- **Embedded radial-menu submenu**. Too cramped for a tabbed input
  with three fields; ATAK reserves radial menus for marker-scoped
  actions, not free-form input. Rejected.

---

## R2 — Inverse converters: reuse `proj4j` `CoordinateTransform` bidirectionally

**Decision**: Add a `CoordinateParser` facade in `goto/` that calls
the same proj4j `CoordinateTransform` instances feature 001 already
constructs in `Projections.java`, but in the inverse direction
(`CoordinateTransform.target()` → `.source()` is symmetric).
TWD67 inverse goes through `DatumShiftTwd67.twd67ToTwd97`, then
`Projections.twd97ToWgs84`. Taipower inverse goes through
`TaipowerGrid.fromCode(...)` → `Twd67Tm2` → `Twd97Tm2` → `Wgs84`.

**Rationale**:
- `proj4j` transformations are mathematical inverses by construction;
  rolling our own inverse would risk numerical drift versus the
  forward path.
- The forward-path golden vectors (`taiwan_cities_coords.csv`) become
  round-trip vectors: feed `Wgs84 → string` then `string → Wgs84`
  and assert the second hop is within the same tolerance band the
  forward direction uses. No new fixture data needed.
- Zero new dependencies (Constitution II discipline: tests first,
  no library churn).

**Alternatives considered**:
- **Hand-roll a TWD67 inverse using the 4-param Bursa-Wolf shift
  inverted analytically.** The shift is a linear approximation, so
  its inverse is itself a 4-param shift with negated parameters and
  a different small-correction term. Algebraically OK but introduces
  a second-place implementation of the same math, which is exactly
  the duplication ADR-0001 warned against. Rejected.
- **Use Android's `Location.distanceBetween` as a quick check
  oracle.** Useless: it computes haversine on WGS84, not a TWD
  inverse. Rejected.

---

## R3 — Parser autodetect vs. explicit tab selector

**Decision**: Explicit tab selector. The page has three tabs
(Taipower / TWD97 / TWD67); the parser **never** autodetects which
system the user typed. The Taipower tab presents one input field;
TWD97 and TWD67 each present separate easting / northing fields plus
a zone-121/119 toggle.

**Rationale**:
- Spec Clarifications session (2026-05-16, Q3 chain) already settled
  this implicitly: the Auto Fill answer "set toggle + values for the
  *active tab*" presupposes the tab is the source of truth.
- Autodetect is ambiguous in practice: a 7-digit string `2770905`
  is both a valid TWD97 northing and a substring of a Taipower
  hundred-metre code, but they mean different things. Forcing the
  operator to pick a tab eliminates that ambiguity at the cheapest
  UX cost (one tap).
- Mirrors the existing widget's `CoordinateUnit` enum and reuses the
  three `CoordinateUnit` values directly — no new enum, no new
  branch in `CoordinateConverter`.

**Alternatives considered**:
- **Single field with autodetect heuristic.** Heuristic edge cases
  (a digit-only string with no letters could be many things) lead to
  silent miscategorisation. Rejected.
- **Two tabs (Taipower, "Numeric (TWD97/TWD67)") with a per-row
  unit toggle.** Saves one tab but moves the disambiguation onto a
  second toggle, increasing cognitive load. Rejected.

---

## R4 — Tools-menu icon registration: second icon vs. long-press

**Decision**: Register a **second Tools-menu icon**
(`com.atakmap.android.twcoord.SHOW_GOTO`) alongside the existing
unit-cycle icon (`SHOW_PLUGIN`). The two icons sit next to each
other and have visually distinct drawables (target/pin for GoTo
versus the existing grid icon for cycle).

**Rationale**:
- Discoverability: long-press is invisible until taught; a second
  icon is immediately recognisable.
- Cleanly separates the two affordances — the cycle icon is a
  *state toggle* (mutates which unit the widget shows); the GoTo
  icon is a *navigation action* (opens a page). Same mental model
  ATAK itself follows for its tool catalogue.
- ATAK's `plugin.xml` extension model supports any number of `tool`
  child elements. Adding a second one is one XML stanza plus one
  `BroadcastReceiver`.

**Alternatives considered**:
- **Long-press the existing cycle icon to open GoTo.** Hidden,
  rejected.
- **Replace the cycle icon with GoTo and move the cycle into the
  GoTo page header.** Breaks an installed-user expectation
  (the cycle is the post-MVP D3 decision in ADR-0008). Rejected.

---

## R5 — `DestinationMarker`: move-not-create

**Decision**: A single `DestinationMarker` instance is owned by
`DestinationMarkerStore`. On every successful submit, the store
looks up its current marker (if any) and either creates a new
`com.atakmap.android.maps.Marker` via `mapView.getMapItem(...)` or
moves the existing one with `marker.setPoint(...)`. Long-press on
the marker uses ATAK's standard delete affordance (the
`Marker.setRemovable(true)` flag); on removal, the store clears its
reference so the next submit creates fresh.

**Rationale**:
- Spec FR-009 / US3 acceptance scenario 2 explicitly forbid
  duplicate markers across re-submissions.
- A single owned marker is simpler than reconciling N markers; the
  recent-entries list (US4) already covers the "I want history"
  case.
- Reusing ATAK's standard Marker class means the operator can
  long-press to delete using the muscle memory they already have.

**Alternatives considered**:
- **One marker per submission, never deleted.** Map clutter after
  five submissions; violates FR-009 invariant. Rejected.
- **Custom `MapItem` subclass with bespoke delete affordance.**
  Over-engineering; uses none of ATAK's deletion plumbing. Rejected.

---

## R6 — Persistence: extend the existing `PreferenceStore`

**Decision**: All new persistence lives in the existing
`SharedPreferences` file (`tw_coord_settings`) under new
`pref_goto_*` keys. The recent-entries list is a single
JSON-encoded string under `pref_goto_recent_json`. The
last-submitted (unit, value) tuple is stored as a small set of
typed scalar keys (see plan.md Storage section).

**Rationale**:
- One file → one backup, one wipe, one migration if we ever need
  one. ATAK plugins do not get their own SQLite namespace cleanly;
  introducing a second SharedPreferences file is pure overhead.
- JSON-on-SharedPreferences is the idiomatic Android micro-list
  storage (no Room dependency needed for ~10 rows).

**Alternatives considered**:
- **Room database.** Pulls in androidx.room, kotlin-stdlib, kapt.
  Hugely disproportionate for 10 rows. Rejected.
- **Per-entry SharedPreferences keys (`pref_goto_recent_0_unit`,
  `..._value`, etc.).** Verbose, harder to compact on eviction.
  Rejected.

---

## R7 — Auto Fill event source: piggyback on existing widget plumbing

**Decision**: `MapCenterAutoFillStream` subscribes to the **same**
`MapEvent` family already wired into `TwCoordWidget`
(`MAP_SCROLL`, `MAP_SETTLED`, `MAP_SCALE`, `MAP_MOVED`), debounced
through a copy of `SelfMarkerSubscriber`'s `haveEmitted` flag
pattern. On every emission, it computes:

1. The current `Wgs84` of the map centre.
2. The valid `ParseResult` for that `Wgs84` under each of the three
   units.

It exposes three boolean signals (`taipowerOk` / `twd97Ok` /
`twd67Ok`) and one cached `(Wgs84, zone)` tuple. The view binds
the enabled-state of the Auto Fill button to whichever boolean
matches the active tab.

**Rationale**:
- Reuses an already-tested event source; doesn't risk a second
  subscriber drifting out of phase with the widget's own readout.
- The compute happens once per event, not once per button-state
  query — keeps frame budget intact.

**Alternatives considered**:
- **Poll `mapView.getCenter()` on a 16 ms timer.** Wasteful on a
  stationary map, and competes with ATAK's own render loop.
  Rejected.
- **Compute inside the click handler.** Then the button would always
  *look* enabled and would only fail at submit-time — violates
  FR-022's "real-time disabled state" requirement. Rejected.

---

## R8 — Localised strings: extend, don't fork

**Decision**: All new visible strings (~30 keys) are added to the
existing `values/strings.xml`, `values-zh-rTW/strings.xml`, and
`values-ja/strings.xml`. The zhtw-mcp lint MUST pass at 0 errors / 0
warnings on the new Traditional Chinese entries before the feature
ships, matching the discipline established in ADR-0008 D5.

**Rationale**:
- Consolidates the translation catalogue; a future translator sees
  every plugin string in one place.
- Constitution Principle V mandates English-primary documentation
  and Principle III mandates externalised strings — both are
  already met by the existing translation file structure.

**Alternatives considered**:
- **Separate `goto_strings.xml` file per locale.** Would require
  changing the manifest/resources to merge them; net negative.
  Rejected.

---

## R9 — Performance: where the 300 ms / 100 ms / one-cycle budgets land

**Decision**:
- **Submit → marker rendered (SC-002, ≤ 300 ms median)**: the
  bottleneck is `mapView.getRenderer3().getCamera().panTo(...)` plus
  one `Marker.setPoint` and ATAK's frame after that. Measure with a
  Trace event around the call site; if the path exceeds 200 ms in
  practice, drop the zoom-out animation and pan instantly (the spec
  doesn't require a smooth zoom).
- **Validation latency (SC-004, ≤ 100 ms after last keystroke)**:
  the parser is pure JVM math (linear-time string parsing + one
  proj4j transform); on modern Snapdragons it runs in microseconds.
  The dominant cost is `EditText.afterTextChanged` reflow — keep the
  parser off the UI thread by debouncing to a `HandlerThread`-bound
  validator that posts back via `runOnUiThread`.
- **Auto Fill disabled-state (SC-009, ≤ one map-event cycle)**: the
  `MapCenterAutoFillStream` (R7) propagates the `Wgs84` into a
  `LiveData<Boolean>` per unit; the view observes it; standard
  Android lifecycle propagation runs in single-digit ms.

**Rationale**:
- All three budgets are achievable without exotic optimisation
  techniques; calling it out here means future tasks can write
  the assertion immediately rather than discover it on device.

**Alternatives considered**:
- **Reactive streams (RxJava).** Overkill for one observable per
  unit. Rejected.
- **Coroutines.** Would pull in Kotlin; the plugin is Java-only.
  Rejected.

---

## R10 — Recent-entries cardinality and eviction policy

**Decision**: Capacity 10, FIFO by `timestampEpochMs`. New entries
push to the head; once size > 10, the tail is dropped. Duplicates
(same unit + same rawValue) collapse — newer timestamp wins, older
copy removed before the FIFO trim. Persistence cadence: write on
every successful submit (≤ ~10 KB JSON, negligible cost).

**Rationale**:
- 10 is the small-list cliff: enough to cover a half-shift of
  fieldwork without burying the most recent entry; small enough to
  scan visually.
- Duplicate-collapse prevents the list from devolving into "the
  same five Taipower codes I keep retyping when I make a typo".

**Alternatives considered**:
- **Capacity 5.** Too small if the operator hops between four sites
  during a shift. Rejected.
- **Capacity 50.** Scrolling burden for marginal value. Rejected.

---

## R11 — Accuracy advisory placement on the input page

**Decision**: The same advisory copy the settings page already shows
(±10–20 m on outer islands for TWD67 — see ADR-0008 D5) appears as
a *single-line note* directly above the Submit button on the
TWD97/TWD67 tabs **only when zone toggle = 119**. On the Taipower
tab the advisory is suppressed (Taipower mode is main-island only,
so the operator is never in an outer-island regime there).

**Rationale**:
- Spec FR-017 requires the advisory before submit; placing it above
  the button is the closest possible adjacency.
- Putting it inline (vs. as a tooltip on Submit) avoids hiding it
  behind a tap that would already mean the operator missed it.

**Alternatives considered**:
- **Always-on inline advisory.** Visual noise on the common-case
  zone-121 path. Rejected.
- **Toast after submit.** Too late; the operator already moved the
  camera. Rejected.

---

## Open follow-ups (deferred — not blocking implementation)

- **Custom drawable for the destination marker.** Spec assumes
  ATAK's default user-defined-point icon. Custom iconography is a
  polish-phase candidate (post-`/speckit-implement`) and will be
  decided in the post-MVP iteration ADR if user feedback asks.
- **Self-marker source for Auto Fill.** Excluded from v1 by
  Clarifications Q1; revisit only if field feedback explicitly asks.
- **CoT propagation policy.** The marker propagates with whatever
  default ATAK applies to user-placed markers; if the field wants
  "private only", it becomes a future toggle. Not in v1 scope.

These items are explicitly **out of scope for Phase 0 → Phase 2**;
they exist here as a forward record, not as live work.
