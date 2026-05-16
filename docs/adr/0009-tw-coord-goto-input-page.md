# ADR-0009: Taiwan-coordinate input ("GoTo") page — pan-only post-submit, no plugin-owned marker

**Status**: Accepted
**Date**: 2026-05-16
**Origin**: `/speckit-implement` of feature 002 (`specs/002-tw-coord-goto/`) plus on-device feedback from the Galaxy Tab S10+ reference device that caused two design pivots away from the as-written spec.

## Context

Feature 002 ships a Taiwan-coordinate input page (Taipower / TWD97 / TWD67) as a second affordance on the existing `atak_tw_coord_plugin`. The spec was authored before any device testing, and on first install two assumptions in it turned out wrong. This ADR captures both pivots so a future reader does not try to "fix" the implementation back to the original spec.

The four user stories shipped in this feature:

- **US1** (P1) Taipower input
- **US2** (P1) TWD97 / TWD67 input with zone 121/119 toggle
- **US5** (P2) Auto Fill from current map centre
- **US4** (P3) Recent entries list (capacity 10, FIFO, dedup)

## Decisions

### D1 — Submit moves the camera's X/Y only; never auto-creates a marker

**Spec change**: FR-008 revised (was: pan + zoom + drop marker + close. Now: pan X/Y + close). FR-009 and FR-019 superseded. SC-008 superseded.

The original spec called for the plugin to drop a `b-m-p-w-GOTO` waypoint at the resolved coordinate on every Submit and to enforce a "move not create" invariant across re-submissions. On-device testing surfaced two problems with that design:

1. **The operator wants choice.** Auto-dropping a fixed marker type denied the operator the standard "what kind of pin?" radial menu that ATAK provides on long-press. Field workflow has waypoints, Mission Points, SPIs, friendlies, hostiles, neutrals — the choice depends on the situation. The spec's hard-coded marker type encoded one choice without asking.
2. **Lower learning cost.** Direct user feedback: "與系統原本 GoTo 功能類似 不要讓使用者在學習新的操作方式 沿用舊的介面 降低使用者學習成本" (mimic the original GoTo functionality, don't make users learn a new way of operating, reuse the old interface, lower learning cost).

The pivot: Submit only pans the camera's X/Y to the destination. Marker placement is delegated entirely to ATAK's standard long-press → radial menu UX at the destination. Zero new post-submit affordance is introduced by this plugin; the input page is purely a coordinate-conversion shortcut that ends at "the camera is now centred on the place you typed". The operator's next action — drop a waypoint, a Mission Point, nothing at all — uses the same gesture they would use on any other coordinate.

Tradeoff: the plugin no longer owns a destination indicator. Operators who want a visible "I told the plugin to GoTo here" marker have to drop one themselves. Acceptable because ATAK's long-press radial is fast (≤ 2 taps) and the standard option set is rich.

### D2 — Pan changes X/Y only, never the camera's zoom (Z) or rotation

**Spec change**: FR-008 reinforced.

Initial implementation called both `CameraController.Programmatic.panTo` and `.zoomTo` (forcing a 50 m/px "town scale" view). On device the user pointed out: "GoTo 不能隨意改變 Z Value 只能改變 X Y" — operators carefully choose their zoom for the task (street level for a building, regional for a survey area) and an unexpected zoom shift is disorienting.

The pivot: Submit calls `panTo` only. Zoom, rotation, and tilt are preserved exactly as the operator set them. If the operator's current zoom is too far out to see the destination, they pinch-to-zoom themselves — that's a five-finger gesture, lower friction than an unexpected zoom snap.

### D3 — Do not delegate to `com.atakmap.android.routes.GoToMapTool`

**Spec change**: FR-008 reinforced.

An attempt to delegate the post-parse navigation to ATAK's native `GoToMapTool` (via the `com.atakmap.android.routes.GOTO_NAV_BEGIN` broadcast with a `point=lat,lon` extra) failed on device. Inspection of `GoToMapTool.onReceive`'s bytecode showed it bails early with a `self_marker_required` Toast when `ATAKUtilities.findSelf(mapView)` returns null — i.e. when the device has no GPS fix. Indoor / tablet-only operators never get past that check, so the broadcast does nothing.

The pivot: do not delegate. Call the camera API directly. This is the right call for our use-case anyway because we already converted the Taiwan coord to WGS84 ourselves; the broadcast hand-off was pure pomp.

### D4 — Recent list capacity 10, FIFO, dedup on (unit, rawValue), JSON-on-SharedPreferences

**Spec change**: none (matches contracts/recent-store.md as written).

Recent entries persist via a single `pref_goto_recent_json` key in the existing `tw_coord_settings` SharedPreferences file. `org.json.JSONArray` does encode/decode; corrupted JSON recovers as an empty list. Capacity is enforced on every `append()` after deduping on `(unit, rawValue)` — so re-submitting the same Taipower code 11 times still produces only one row.

Why 10: small enough to scan visually in the DropDown side-pane, large enough to cover a half-shift of fieldwork.

### D5 — Auto Fill subscribes to the same MAP_* event family the readout widget uses

**Spec change**: none (matches research.md R7).

`MapCenterAutoFillStream` subscribes to `MAP_MOVED`, `MAP_SCROLL`, `MAP_SETTLED`, `MAP_SCALE` while the DropDown is open and detaches when it closes. Each event recomputes a `MapCenterFix` carrying per-tab "ok" flags (Taipower main-island-only, TWD97/TWD67 in either zone). The view binds each tab's Auto Fill button enabled-state to the matching flag. Zone for TWD97/TWD67 is auto-set from longitude (<120° → zone 119, else 121) — same rule the widget already uses.

### D6 — Layout uses a RadioGroup tab bar, not androidx.material TabLayout

**Spec change**: none (tasks.md T024 was permissive between options).

`androidx.material` is not bundled by ATAK's plugin runtime in the same way the rest of androidx is, and adding it pulled in dependencies. A plain `RadioGroup` with three radio buttons + per-pane `View.GONE`/`VISIBLE` toggling is sufficient and matches the existing plugin's layout idiom (no AppCompat / Material). Lighter, no new transitive deps.

### D7 — `goto` is a Java reserved word — package is `gotopage`

**Spec change**: spec/data-model/contracts/plan/tasks/quickstart all reference `com.atakmap.android.twcoord.gotopage` (corrected during early implementation).

The original plan named the package `com.atakmap.android.twcoord.goto`, which fails to compile (Java reserved word). Renamed to `gotopage` across all artefacts in one pass.

### D8 — `H7509 DB4016` is Hualien Station, not Taipei 101

**Spec change**: quickstart.md, spec.md US1 example, tasks.md, TaipowerParserTest method names all corrected.

ADR-0001 of feature 001 names the Taipower **region letter** for Taipei 101 as `B` and for Hualien Station as `H`. My initial spec wording attributed `H7509 DB4016` to Taipei 101 — wrong; that code's region letter is H, so it points to Hualien Station. The implementation was always correct (it computed from real WGS84); only my prose was wrong. Fixed in a doc sweep.

## Alternatives considered

- **Delegate to GoToMapTool via the standard broadcast** — rejected after on-device testing surfaced the self-marker dependency (D3).
- **Drop a `b-m-p-w-GOTO` marker on every submit** (the original spec) — rejected because (a) it denied the user marker-type choice and (b) the user explicitly asked for no auto-marker.
- **Drop a marker AND open the radial menu** (`PlacePointTool.MarkerCreator.setShowNewRadial(true)`) — rejected because pan-only is even simpler and matches "降低使用者學習成本" more tightly. Long-press achieves the same with one extra gesture and no new code on our side.
- **Add zoom-to-town-scale to the submit path** — rejected after user feedback ("不能隨意改變 Z Value 只能改變 X Y").
- **Skip the Recent list** — rejected; capacity 10 is cheap and the dedup-FIFO behaviour is genuinely useful for multi-stop fieldwork.

## Consequences

**Positive:**

- Operator's existing ATAK muscle memory applies after Submit: same long-press, same radial, same marker choices. Zero new affordances learned.
- No more "GoTo doesn't work without GPS" failure mode.
- Operator's zoom is never disturbed.
- Recent list lets the operator hop between two or three points without retyping.
- Auto Fill formats the visible map centre into any of the three tabs in one tap with the zone toggle pre-set correctly.

**Negative:**

- Spec's FR-008 / FR-009 / FR-019 / SC-008 are partially obsolete; the marker-related acceptance scenarios under US1 / US2 / US3 are stale until a future spec revision. This ADR documents what shipped; a follow-up `/speckit-clarify` cycle can formalise the FR rewording if needed.
- The plugin no longer leaves a "you went here" trace on the map unless the operator explicitly drops something. For workflows that wanted a transient destination indicator, the operator now has to spend one long-press to create one.

## Links

- Commits between `d7a0cb1` (Phase 2 + 3 MVP) and the commit carrying this ADR cover D1-D8.
- Spec: FR-008 revised, FR-009 / FR-019 / SC-008 superseded; FR-021 / FR-022 / FR-023 / FR-024 unchanged.
- Plan: `specs/002-tw-coord-goto/plan.md`.
- Research: R1–R11 in `specs/002-tw-coord-goto/research.md`.
- Prior ADRs: ADR-0007 (native-widget styling — same "reverse-engineer the bytecode before pivoting" discipline applied here), ADR-0008 (post-MVP iteration bundle pattern that this ADR follows).
- Constitution Principles III (UX consistency — D1/D2/D6) and V (this ADR satisfies the post-implement documentation requirement).
