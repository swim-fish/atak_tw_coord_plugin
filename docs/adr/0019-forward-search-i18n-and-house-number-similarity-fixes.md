# ADR-0019: Forward-search localised inflation + house-number-aware "most similar" (1.2.1 bug fixes)

**Status**: Accepted
**Date**: 2026-05-31
**Origin**: Two operator-reported bugs against shipped 1.2.0, fixed on branch and device-accepted on Samsung Galaxy SM-X826B (`R52X908JF0W`) / ATAK-CIV 5.7.0.3. Version bump `1.2.0 → 1.2.1`.

Two independent defects on the **TW Addr Search** (forward-search) page,
both regressions of behaviour ADR-0003 (in-app locale override) and
[ADR-0018](./0018-settings-ux-tweaks.md) D2 (result ordering) were meant to
deliver. Patch release; no spec, schema, or generator change.

## Context

- **B1 — source buttons showed English.** The 所在地 / 地圖中心 / 清單 buttons
  (and every other `@string/fs_*` on the page) rendered in English even with a
  zh-TW system locale or an explicit in-app override, while the on-map readout
  widget localised correctly.
- **B2 — 「最相似」had no visible effect once a house number was typed.** Picking
  the ordering toggle after entering a number did not visibly reorder the list.

## Decisions

### D1 — Inflate the forward-search page against the live localised context (B1)

**Root cause**: `ForwardSearchReceiver` inflated `forward_search_page.xml` and
resolved all `getString(...)` against the **raw** `pluginContext`. Per ADR-0003,
plugin resources only resolve to the chosen bundle when read through the
`createConfigurationContext`-wrapped `localisedPluginContext`; the raw context
falls back to the default (English) `values/` table. The widget worked because
`TwCoordMapComponent` re-resolves its strings from `localisedPluginContext`; the
forward-search page — built once with the raw context — never did.

**Fix**: the receiver now takes a `Supplier<Context>` and `TwCoordMapComponent`
passes `() -> localisedPluginContext`. Layout inflation + `findViewById` +
button wiring move into a private `inflate()` called from the constructor.
`onReceive` compares the supplier's current context against the one last
inflated — `createConfigurationContext` returns a fresh instance per locale, so
an identity change is the "language changed" signal — and re-inflates before
showing, so a mid-session language switch repaints the page on its next open
(FR-018). No host activity recreate; consistent with ADR-0003.

### D2 — "Most similar" gains a house-number numeric-proximity secondary key (B2)

**Root cause**: `StreetCandidateRanker.reorder(...)` scored similarity on the
candidate **street** vs the folded **street fragment** only. Once a house number
narrows the list, every surviving candidate shares the same street (e.g. all
`五權西路一段 / 二段`), so the bands tie and `MOST_SIMILAR` collapses to the
distance order — indistinguishable from `DISTANCE` (the "no effect" report).

**Fix**: a house-number-aware `reorder(..., foldedHouseNumber)` overload adds a
**secondary** comparator key — the absolute difference between the candidate's
leading house number and the typed one (`houseNumberProximity`, smaller first) —
between the street-similarity band (primary) and the existing match-index /
leftover-length / distance tiebreaks. A blank/undigited number is neutral
(returns `0`), so the street-only behaviour and every existing reorder test are
preserved; a candidate with no comparable number sorts last once a number is
typed. The receiver feeds the folded keypad value through all three reorder call
sites (`runSearch` passes blank, `onKeypad` and the ordering toggle pass the
current number). Result: `五權西路 + 2` floats `…一段2C號` ahead of `12號 / 20號`,
ties by distance. Still no edit-distance (out of scope per 006/0018 D2).

**Known interaction (not changed here)**: `ForwardSearchController.withHouseNumber`
filters by substring, so typing the full `2號` excludes `2C號` (it has no `2號`
substring) — the operator types the digit `2` to keep附號 in the set, then the
proximity key ranks them. Loosening that filter is deferred; it is a matching
(not ordering) concern and risks over-matching.

## Consequences

- All `@string/fs_*` on the forward-search page now follow the in-app language,
  closing the last plugin surface that ignored ADR-0003. The same
  `Supplier<Context>` + re-inflate pattern is available should the GoTo / Offline
  Address pages need the live-switch fix later (they currently inflate once with
  the raw context too).
- `MOST_SIMILAR` is meaningfully different from `DISTANCE` at the house-number
  stage; the 3-arg `reorder` overload is retained for the street-only tests and
  delegates to the 4-arg form with a blank number.

## Verification

- `:app:testCivDebugUnitTest` — `StreetCandidateReorderTest` extended with three
  cases (numeric-closest-first, equal-number tie-by-distance, blank-number ==
  street-only); existing forward + empty-street units green, no regressions.
- `:app:assembleCivDebug` builds `1.2.1`; installed via `adb install -r` and
  operator-accepted on SM-X826B: source buttons render zh-TW, 「最相似」reorders.

## Related

- Builds on [ADR-0003](./0003-locale-override-mechanism.md) (locale override) and
  [ADR-0018](./0018-settings-ux-tweaks.md) D2 (result ordering).
- Code: `ForwardSearchReceiver`, `StreetCandidateRanker`, `TwCoordMapComponent`.
