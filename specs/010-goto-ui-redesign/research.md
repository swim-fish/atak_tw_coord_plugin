# Phase 0 Research: GoTo Coordinate-Input Page UI Redesign

No open `NEEDS CLARIFICATION` items remain: the feature is a presentation-layer
restyle whose target design is already fully specified in
`docs/design/search_settings/`. This document records the decisions that shape
the implementation and the seams they reuse.

## Code-anchoring note

Per the project's plan-phase discipline (memory
`feedback_plan_phase_code_anchoring.md`): host-callable surfaces are normally
cross-checked against `javap -public` of `ATAK-CIV-5.7.0.3-SDK/main.jar` AND
upstream permalinks. **This feature introduces no new host→plugin surface.** It
reuses only:

- Standard Android view APIs (`RadioGroup` auto mutual-exclusion among direct
  child radios, state-list `Drawable` via `android:background` /
  `setBackgroundResource(int)` with concrete resource ids).
- The plugin's own already-shipped seams in `TwCoordGotoView`
  (`safeClick(tag, runnable)`, `setActiveTab`, `onAutoFill`,
  `refreshAutoFillEnabled`, `refreshSubmitEnabled`, `refreshLocalisedStrings`,
  `localisedContext`, `submitInFlight`).

No `CameraController.panTo`, `Marker`, `MapView`, or `CoordinateParser` call site
changes — so no new SDK reconnaissance is required for this plan. The behaviour
seams stay exactly where feature 002/003 left them.

## R1 — Apply the feature-008 "compact stacked" design system

**Decision**: Reuse the shipped visual language (segmented controls, carded
fields, single-column stack, primary/secondary button hierarchy, ≥48dp glove
targets) rather than invent new styling.

**Rationale**: Constitution III prohibits ad-hoc styling and requires mirroring
existing flows. The two sibling pages already ship this system; matching it gives
operators one mental model across all three Tools-menu pages. The design is
pre-authored in `docs/design/search_settings/tw_coord_goto.xml` and the
`goto_*` drawables.

**Alternatives considered**: (a) Minimal touch-ups to the current layout —
rejected: leaves the six pain points and the cross-page inconsistency. (b) A
bespoke GoTo-only look — rejected: violates the shared-design-system rule.

## R2 — Merge three per-pane Auto Fill buttons into one header button

**Decision**: Replace `goto_autofill_taipower/_twd97/_twd67` with a single
header-level `goto_autofill` whose onClick calls `onAutoFill(activeTab)` and
whose enabled-state is computed in `refreshAutoFillEnabled()` from the active
tab's representability (`latestFix.taipowerOk()/twd97Ok()/twd67Ok()`).

**Rationale**: Pain point ④ (auto-fill too small / hidden). This is the only
structural change. `setActiveTab()` already calls `refreshAutoFillEnabled()`, so
tab switches update the single button for free. `onAutoFill(...)` /
`autoFill*FromFix(...)` / `onMapCenterFix(...)` bodies are unchanged.

**Alternatives considered**: Keep three buttons but enlarge them — rejected:
still triples the control count and keeps them buried per-pane.

**Risk / mitigation**: Any test or external caller of the three old ids must move
to `goto_autofill`. Audit result: **no Espresso/instrumented test references the
old ids** (grep across `app/src/`), and the unit suite does not touch views — so
no test edits are required.

## R3 — Selected-state via state-list drawables, not programmatic colour

**Decision**: Drive tab, marker-cell, and zone-cell selection appearance through
state-list drawables (`goto_tab_selected`, `goto_marker_cell_bg`,
`goto_zone_cell_bg`) keyed on `state_checked`/`state_selected`. `styleTab()`
swaps a pill background on selection; `styleMarkerModeRadio()` **drops**
`setBackgroundColor` entirely and only keeps `setChecked`.

**Rationale**: Pain points ③/⑤/⑥ and **Constitution VI**. The 2026-05-16 ATAK
crash was a misused `android.R.attr.*` in a view path; moving selection styling
into concrete state-list drawables removes programmatic background calls from the
hot path and uses only concrete resource ids — a net reduction in VI risk.

**Alternatives considered**: Continue programmatic `setBackgroundColor` with
larger sizes — rejected: keeps the imperative styling that caused the incident
class and is harder to keep consistent with the sibling pages.

## R4 — Primary/secondary submit hierarchy

**Decision**: `goto_btn_submit` becomes the emphasised primary (filled, enlarged,
`goto_submit_primary_bg` state-list for enabled/disabled colour); the ATAK
icon-palette button becomes a ghost/secondary (`goto_submit_secondary_bg`).
Optional: dim the primary label colour when disabled in `refreshSubmitEnabled()`.

**Rationale**: Pain point ②. Enable/disable logic is unchanged (still driven by
the existing coordinate-validity check); only the visual weight changes.

**Alternatives considered**: Single button that toggles mode — rejected: changes
behaviour and the ATAK hand-off contract.

## R5 — Projection-zone segmented control + 119 advisory

**Decision**: Render 121/119 as a labelled segmented control per TWD pane;
keep the existing immediate advisory for the 119 zone (`advisoryTwd97/67`),
restyled with `goto_advisory_bg`. Mutual exclusion stays with `RadioGroup`.

**Rationale**: Pain point ⑤. The advisory text and trigger already exist; this
is styling + clarity, no logic change.

**Alternatives considered**: Move the advisory into a dialog — rejected: adds a
dialog (new host token surface) for information that belongs inline.

## R6 — Auto-fill disabled feedback: keep the toast

**Decision**: Retain the existing Toast emitted by `onAutoFill(...)` early-return
when the active system can't represent the map centre; do **not** add the
optional inline `goto_autofill_hint` TextView this release.

**Rationale**: Resolved in spec Clarifications. Smaller surface, matches design
doc §2 default, avoids a new view to keep null-safe.

**Alternatives considered**: Inline hint TextView (design doc §2 option) —
deferred to a future iteration if operators ask for it.

## Localisation

Three label strings change value (same ids): `goto_marker_mode_header`,
`goto_btn_submit`, `goto_btn_autofill`, `goto_btn_atak_picker`; one new id
`goto_taipower_help`. All in `values/`, `values-zh-rTW/`, `values-ja/`, and they
flow through `localisedContext` so the in-app language override applies (matches
sibling pages). Source values are in `strings_additions_goto.xml`.
