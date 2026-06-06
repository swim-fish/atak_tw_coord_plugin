# ADR-0021: GoTo input page UI redesign (feature 010)

**Status**: Accepted
**Date**: 2026-06-06
**Origin**: feature `010-goto-ui-redesign` (`/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`). Version bump `1.3.1 → 1.3.2`.

A presentation-only redesign of the third operator-facing Tools-menu page — the
TW Coord GoTo input page (`TwCoordGotoView` + `tw_coord_goto.xml`) — bringing it
into the "compact stacked" visual language shipped for the two search pages in
feature 008 (ADR-0020). No change to coordinate parsing, datum/projection
conversion, the Submit-and-pan path, the ATAK icon-palette hand-off, input
validation, or the Recent list. The concrete design was worked out up front in
`docs/design/search_settings/` (`tw_coord_goto.xml`, `TwCoordGotoView_changes.md`,
`strings_additions_goto.xml`, nine `goto_*` drawables); this ADR records the
decisions and the anchoring.

## Context

After feature 008 redesigned TW Addr Search and TW Offline Addr, the GoTo page
was left on its older layout and looked and behaved inconsistently with its two
siblings. Six pain points were identified: ① the page was too long; ② two
equal-weight submit buttons (Submit vs Open ATAK icon menu) caused hesitation;
③ the marker grid cells were too small for gloves; ④ Auto Fill was a small
per-pane button hidden top-right (three copies); ⑤ the 121/119 projection-zone
choice was an ambiguous pair of plain radios; ⑥ the three coordinate systems
switched with an inconsistent layout rhythm.

The page already runs entirely behind existing seams (`TwCoordGotoView`'s
`safeClick`, `setActiveTab`, `onAutoFill`, `refresh*` methods), so the work is
overwhelmingly layout + drawable. The one structural code change is merging the
three per-pane Auto Fill buttons into a single header button. This feature
introduces **no new host→plugin surface**, so the plan-phase code-anchoring
discipline yields "reuse only" — standard Android view APIs (`RadioGroup`
mutual-exclusion, state-list drawables via `android:background`) and the plugin's
own shipped seams; no new `javap`/SDK reconnaissance was required.

## Decisions

### D1 — Compact single-column stack + segmented coordinate-system tabs (US1)

`tw_coord_goto.xml` is rewritten as a single-column `ScrollView`. The
Taipower/TWD97/TWD67 selector becomes a segmented control: the `goto_tabs`
`RadioGroup` gets a rounded translucent track (`goto_segment_track`), each tab is
`button="@null"` with `gravity=center`, and `styleTab()` paints the **selected**
tab with the `goto_tab_selected` light pill (dark bold text) while unselected
tabs are transparent. Input fields are carded with `goto_input_bg`. All ids are
preserved; only the active pane is visible (`applyTabVisibility()` unchanged).

### D2 — Primary/secondary submit hierarchy (US2)

`goto_btn_submit` becomes the enlarged, colour-filled primary
(`goto_submit_primary_bg` state-list for enabled/disabled), and
`goto_btn_atak_picker` becomes a ghost secondary (`goto_submit_secondary_bg`,
transparent + thin border). Enable/disable logic is unchanged (still gated by the
active tab parsing cleanly via `refreshSubmitEnabled()`); `refreshSubmitEnabled()`
additionally dims the primary label colour when disabled (appearance only).

### D3 — Glove-friendly marker grid via state-list selection (US3)

The 8-cell 4×2 marker grid keeps its `goto_mode_*` ids but the cells grow to
72 dp with enlarged `drawableTop` icons and a `goto_marker_cell_bg` state-list
background. `styleMarkerModeRadio()` is reduced to `setChecked(selected)` only —
the `setBackgroundColor` call is **removed**; selection colour is now driven by
the drawable's `state_checked`. `applyMarkerModeUI()`'s manual mutual exclusion
across the two rows is unchanged.

### D4 — Single header "Use map centre" Auto Fill (US4, the only structural change)

The three fields `autoFillTaipower/Twd97/Twd67` and their three
`R.id.goto_autofill_*` bindings/listeners are replaced by a single `autoFill`
field bound from `R.id.goto_autofill`. Its one listener calls
`onAutoFill(activeTab)` through `safeClick`, and `refreshAutoFillEnabled()`
switches on `activeTab` to read that unit's `latestFix.*Ok()`. Because
`setActiveTab()` already calls `refreshAutoFillEnabled()`, the button re-evaluates
on every tab switch. `onAutoFill(...)` / `autoFill*FromFix(...)` /
`onMapCenterFix(...)` bodies are untouched; the not-representable feedback stays
the existing Toast (the optional inline hint was deferred — see Alternatives).

### D5 — Labelled projection-zone segmented control + inline 119 advisory (US5)

121/119 render as a labelled segmented `RadioGroup` (`goto_zone_cell_bg`,
`state_checked` colouring) inside each TWD pane; the outer-island precision
advisory (`goto_advisory_twd97/67`) is restyled with `goto_advisory_bg` and still
toggled by the unchanged `validateTwd97()/validateTwd67()` on `zone == 119`.

### D6 — Drawable-driven selection, not programmatic colour (Constitution VI)

All selection state (tabs, zone cells, marker cells) and enabled/disabled fills
(Auto Fill, primary, secondary) are expressed via nine new `goto_*` state-list /
shape drawables with **concrete resource ids**. `styleTab()` uses
`setBackgroundResource(R.drawable.goto_tab_selected)`; `styleMarkerModeRadio()`
drops `setBackgroundColor` entirely. This removes imperative view-mutation from
the hot path and eliminates the `android.R.attr.*`-vs-resource-id misuse class
that caused the 2026-05-16 ATAK crash — a net reduction in Principle VI risk.

## Alternatives considered

- **Minimal touch-ups to the existing layout** — rejected: leaves all six pain
  points and the cross-page inconsistency (D1).
- **A single submit button that toggles mode** — rejected: changes the
  Submit-vs-ATAK-picker behaviour and the hand-off contract (D2).
- **Keep programmatic `setBackgroundColor` for selection, just enlarge cells** —
  rejected: keeps the imperative styling that caused the 2026-05-16 incident
  class and drifts from the sibling pages (D3/D6).
- **Inline "Auto Fill disabled reason" hint TextView** (design doc §2 option) —
  rejected for this release: the existing Toast is smaller surface and one fewer
  view to keep null-safe; deferred to a future iteration (D4).
- **Move the 119 advisory into a dialog** — rejected: adds a host-token surface
  for information that belongs inline (D5).

## Consequences

- The GoTo page matches its two siblings: segmented tabs + carded fields (SC-001),
  the six pain points are addressed (SC-002), every target is glove-sized
  (SC-003), the primary action is unmistakable (SC-004), and labels render in
  en / zh-rTW / ja under the in-app override (SC-006).
- No functional regression: no coordinate-logic code was touched, and the existing
  GoTo unit suite (`CoordinateParserRoundTripTest`, `TaipowerParserTest`,
  `TwdTm2ParserTest`, `MapCenterFixTest`, `MarkerModeTest`) passes **unmodified**
  — the behaviour-preservation guarantee (SC-005).
- Crash isolation (Constitution VI): the merged `goto_autofill` onClick runs
  through `safeClick(Throwable)`; selection styling is drawable-driven with
  concrete ids (no `android.R.attr.*`); the `submitInFlight` re-entrancy guard is
  intact.
- Verification gate green: `spotlessCheck`, `:app:testCivDebugUnitTest`,
  `assembleCivDebug`, `lintCivDebug` all pass. Installed on Galaxy Tab S10+
  (SM-X826B).
- Localisation (Constitution V): four label/string changes shipped in all three
  locales, plus the new `goto_taipower_help` hint.

## Follow-ups (not done in this branch)

- **On-device visual acceptance** (quickstart US1–US5 in all three locales) and
  the **SC-005 byte-identical on-device coordinate check** are pending operator
  sign-off; the unmodified passing unit suite already guards against functional
  regression.
- **Espresso UI tests** were not added (pure restyle; no instrumented test
  referenced the affected ids); behaviour preservation is covered by the existing
  JVM unit suite.
