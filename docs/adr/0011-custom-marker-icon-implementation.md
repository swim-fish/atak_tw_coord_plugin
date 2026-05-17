# ADR-0011: Custom Icon picker — post-implementation pivots

**Status**: Accepted
**Date**: 2026-05-17
**Origin**: `/speckit-implement` MVP delivery for feature 003 (commits `7688624` MVP + this commit's polish-round-1) plus on-device sideload to Galaxy Tab S10+.

This ADR captures the concrete pivots and structural decisions made during the implementation pass after [ADR-0010](./0010-custom-marker-icon-picker.md) defined the architecture. ADR-0010 stays the reconnaissance/design record; this ADR records what actually shipped.

## Context

ADR-0010 defined six decisions for the Custom Icon picker. The plan / contracts / tasks (commits `44913b4` and `7688624`) elaborated the design without contradicting it. During implementation a handful of structural choices had to be made that weren't fully specified upfront, and one cross-cutting Constitution VI audit surfaced a pre-existing gap in feature 002.

## Decisions

### D1 — Robolectric tests deferred to a future commit

**Spec impact**: tasks.md T016 / T017 / T030a were marked "MUST fail until …" but their full execution depends on Robolectric, which is not in the project's `app/build.gradle` `testImplementation` block.

Three options were considered:

- **A.** Add Robolectric to the build alongside the MVP. Rejected — Robolectric is a non-trivial dep (transitive AndroidX libs, AGP integration quirks, ~30s extra test runtime), and bundling it with the MVP would inflate the commit's blast radius beyond "Custom Icon picker".
- **B.** Skip those tests entirely. Rejected — FR-010a (corrupt-bitmap silent skip) is genuinely worth a JVM gate.
- **C.** Extract the FR-010a filter into a static helper and JVM-test that one path. The remaining dialog-UI tests become a follow-up that pairs with adding Robolectric.

Chose **C**. `CustomIconPickerDialog.filterRenderable(List<IconRow>, IconResolver)` is package-private and static; `CustomIconPickerFilterTest` exercises it with mocked `IconResolver` (4 cases — happy path, null input, empty input, all-corrupt input). The remaining items (1–8, 11 of `contracts/custom-icon-picker.md`) stay in the deferred Robolectric phase.

### D2 — `testOptions.unitTests.returnDefaultValues = true` added to `app/build.gradle`

**Why**: Constitution Principle VI mandates every entry point wraps `Throwable` and logs via `com.atakmap.coremap.log.Log.w(...)`. The Log class is a stub in the Android SDK jar; without `returnDefaultValues`, unmocked methods throw `RuntimeException("Method ... not mocked.")` — which breaks every test that touches a Constitution VI guard path (corrupt-value handlers, swallowed-exception handlers, etc.).

The fix is a 1-line addition under `android { testOptions { ... } }`. It makes all Android stub methods return null/0/false instead of throwing, which is what JVM unit tests assume when they don't use Robolectric. This unblocked 6 of the 7 initial test failures.

### D3 — `PreferenceStore` gets a package-private `SharedPreferences`-accepting constructor as the JVM test seam

**Why**: The existing constructor calls `PreferenceManager.getDefaultSharedPreferences(context)`, a static method that can't be mocked without Robolectric or PowerMock. Adding a second constructor that accepts `SharedPreferences` directly lets `PreferenceStoreCustomIconTest` inject a Mockito-mocked `SharedPreferences` + `SharedPreferences.Editor` pair.

Production code path unchanged — the public `PreferenceStore(Context)` constructor delegates to the new one via:

```java
public PreferenceStore(Context context) {
  this(PreferenceManager.getDefaultSharedPreferences(Objects.requireNonNull(context, "context")));
}

PreferenceStore(SharedPreferences sp) { ... }   // package-private test seam
```

### D4 — Constitution VI audit retrofits `safeClick(tag, body)` helper to all 18 click handlers in `gotopage/`

**Spec impact**: T046/T047 audit found 14 unguarded `OnClickListener` lambdas inherited from feature 002 (8 marker-mode radios + 3 tab radios + 3 autofill buttons + Submit + Recent label/del). Strictly speaking these violated Principle VI's "MUST catch Throwable at the entry-point body's outer scope" mandate. They survived feature 002's ADR-0009 sweep because the inner methods (`setActiveTab`, `setMarkerMode`, `onAutoFill`, `onSubmit`, `refillFromRecent`) are themselves defensive.

The fix is a thin helper:

```java
private static void safeClick(String tag, Runnable body) {
  try { body.run(); } catch (Throwable t) { Log.w(TAG, tag + " failed", t); }
}
```

All 18 click listeners route through it. The `tag` parameter ("modeMove", "tabTaipower", "submitButton", etc.) narrows logcat reports if a click ever does trip. Total cost: one 5-line helper + 18 lambda rewrites.

This brings feature 002's pre-existing click handlers into strict Principle VI compliance at the same time as feature 003 — addressing a finding that should have caught feature 002's audit and didn't.

### D5 — Live `ICONSET_REMOVED` fallback wired through `view.onIconsetRemoved(uid)` rather than via a synthetic `bind()` call

**Why**: FR-009's bind-path fallback runs whenever the page opens. But operators can remove an iconset *while the page is already open* — at which point the bind-path doesn't run again. The receiver subscribes to `ICONSET_REMOVED`, and when the broadcast's `uid` matches the operator's `currentSelection.iconsetUid`, dispatches into a new `TwCoordGotoView.onIconsetRemoved(uid)` method that runs the same atomic-clear + `pendingFallbackHint = true` + `applyMarkerModeUI()` sequence. This way the FR-009 logic lives in exactly one place (`TwCoordGotoView`) and both entry points (bind + live broadcast) use it.

The receiver also invokes `view.onIconsetsChanged()` for `ICONSET_ADDED`, which invalidates `IconResolver`'s cache and notifies the picker dialog (if open).

### D6 — First-pass zh-rTW / ja translations shipped with the MVP commit (T002/T003 collapsed with T048/T049)

**Spec impact**: T002 and T003 in tasks.md called for English placeholders + empty translation values, with T048/T049 doing the proofread sweep in Polish. The implementation instead shipped reasonable first-pass translations for all 3 locales in the MVP commit.

**Why**: empty `""` values trigger `MissingTranslation` lint warnings, which were eliminated by writing real translations. The strings are simple enough (radio label, empty-state hint, dialog title, back button, etc.) that a first-pass translation matches the established feature-001/002 tone without needing the `zhtw-mcp` MCP pass. T048/T049 remain as a "Polish: proofread sweep" task, but the build is shippable without them.

### D7 — `Space` widget used to balance the 9th-radio row in `tw_coord_goto.xml`

**Why**: the 9th radio (Custom Icon) is the only option in its row. To match the visual weight of the 8 radios above (which use `layout_weight=1` in 4-column rows), the row contains the radio at `weight=1` plus an invisible `Space` widget at `weight=3`. This puts the radio in the leftmost quarter of the row, mimicking the column alignment of the 4×2 grid above. Cleaner than a `GridLayout` for this one-radio case.

> **Superseded by D8.** The 9th radio and its balancing `Space` widget were removed when the bespoke picker was scrapped in favour of delegating to `EnterLocationDropDownReceiver`. The marker-mode block reverted to the original 4×2 grid of 8 radios.

### D8 — Scrap the bespoke picker and delegate custom-icon drops to `EnterLocationDropDownReceiver` (Option B pivot)

**Status**: Accepted 2026-05-17. Supersedes D7 and amends D5; the `pref_goto_marker_mode` durability decision stands but the `CUSTOM_ICON` enum value and `pref_goto_last_iconset_path` key are gone.

**Context**: After the MVP (`7688624`) and polish (`1861fb8`) commits, on-device sideload surfaced two issues. First, tapping the "挑選圖示" preview row produced no visible dialog (the `AlertDialog.setView()` host rendered at 0×0 size until `getWindow().setLayout(MATCH_PARENT, MATCH_PARENT)` was called after `show()`, AND the dialog needed an `Activity` context — `mapView.getContext()` — not the plugin context, to acquire a window token). Second — and more important — the operator pushback was direct: "選單可以仿照 Marker 的方式嗎? 重用舊的 UI 設計與邏輯" ("can the menu work like the Marker one — reuse the old UI design and logic?"). The 0×0 bug was fixable; the design objection was that we had built a bespoke picker dialog at all when ATAK already ships one.

**Decision**: Remove the bespoke `CustomIconPickerDialog` flow entirely. Replace the 9th `CUSTOM_ICON` marker-mode radio with a new sibling button below Submit ("Drop via ATAK picker" / 「改用 ATAK 圖示挑選器落點」 / 「ATAK アイコン選択でドロップ」) that:

1. Parses the active tab into a `Wgs84` (same path as Submit).
2. Persists the last-input tuple + appends to Recent (same housekeeping as Submit).
3. Closes our `DropDownReceiver` so ATAK's drop-down can take the stage.
4. Calls `EnterLocationDropDownReceiver.getInstance(mapView).processPoint(GeoPointMetaData.wrap(geoPoint))`, handing the resolved coordinate to ATAK's native enter-location pane. That pane drops the marker using whichever pallet/icon the operator already has active in their own UI — exactly the "reuse the old UI" the operator asked for.
5. Broadcasts `ACTION_GOTO_NAV_COMPLETED` (same outbound signal as Submit).

The 8 quick-radio marker modes survive untouched; they remain the no-extra-clicks path for the common GoTo + drop pin scenarios. The new button is independent of the radio selection — it always delegates to ATAK regardless of which radio is checked.

**Files deleted (≈1300 LOC and 8 production classes / 4 layouts / 1 drawable / 2 test classes):**

- `gotopage/CustomIconPickerDialog.java`
- `gotopage/IconResolver.java`, `IconDatabaseFacade.java`, `IconRow.java`, `IconsetSummary.java`, `IconSelection.java`, `PickerPreviewState.java`
- `res/layout/custom_icon_picker_dialog.xml`, `custom_icon_picker_iconset_row.xml`, `custom_icon_picker_icon_cell.xml`, `custom_icon_picker_empty_row.xml`
- `res/drawable/ic_marker_custom_icon.xml`
- `test/.../IconResolverTest.java`, `CustomIconPickerFilterTest.java`
- Trimmed: `MarkerMode.CUSTOM_ICON` + `requiresIconPath()` + `isCustomIcon()` helpers
- Trimmed: `PreferenceStore.KEY_GOTO_LAST_ICONSET_PATH` + 4 accessor methods + `clearCustomIconSelectionAtomic()`
- Trimmed: `TwCoordGotoReceiver`'s `iconsetChangeReceiver` `BroadcastReceiver` + `ICONSET_ADDED/REMOVED` subscription
- Trimmed: 10 `goto_custom_icon_*` string keys + `goto_mode_custom_icon` across all 3 locales (replaced by one `goto_btn_atak_picker`)

**Forward-compatibility for already-installed beta builds:** `PreferenceStore.getGotoMarkerMode()` already falls back to `MOVE_ONLY` on any unknown enum name (the `corruptValueFallsBackToMoveOnly` path). The new `markerMode_legacyCustomIconValueFallsBackToMoveOnly` JVM test pins this behaviour so the `CUSTOM_ICON` string left behind by a pre-pivot install can never crash on read.

**Why this beats the bespoke picker:**

- **Operator UX.** Custom-icon drops now flow through the exact same picker the operator sees everywhere else in ATAK. Zero new vocabulary, zero new mental model.
- **Maintenance.** ATAK owns the picker dialog. Iconset additions/removals, language support, new icon formats — all free.
- **Constitution VI surface.** ~1300 LOC of plugin-side picker UI deleted means ~1300 LOC of `try/catch (Throwable)` audit surface deleted too.
- **0×0 dialog bug.** Gone, along with the whole class of plugin-context-vs-Activity-context window-token problems.

**What we lose** vs. the originally-scoped picker:

- The "currently picked custom icon" preview row. The operator no longer sees a thumbnail of their chosen icon inline on the GoTo page — they pick fresh in ATAK's pane each time. Acceptable trade: the bespoke preview was the source of the 0×0 bug, and operators picking from ATAK's pallet already have the visual context they need there.
- The atomic `FR-009` "iconset removed under our feet" fallback. No longer needed — there is no persisted icon selection to invalidate.
- One-click custom-icon drop. The Option B flow is two-click (button → ATAK pane → tap icon). For the typical "drop at a typed coordinate" use case this still beats the prior bespoke picker's three-click flow (radio → preview row → dialog row).

**ADR-0010 alternative D (originally rejected)** has effectively been re-adopted in a narrower form. D's rejection was "splits the GoTo UX across two visible drop-downs" + "loses our toast / persist / close / CoT-broadcast sequencing". We address both by (i) explicitly making the operator opt into the delegation via a dedicated button (so the two-drop-down sequence is a deliberate user choice, not an unexpected side-effect of Submit), and (ii) doing the persist + Recent + NAV_COMPLETED broadcast ourselves before the delegation call. ADR-0010 D's original objection that "the data-layer reuse (D1) gives us the same icon coverage" turned out to be false in practice: a bespoke UI-layer reuse on top of `UserIconDatabase` reproduces ATAK's picker badly. Reusing ATAK's picker directly is cheaper and better.

## Alternatives considered

- **Embed `UserIconPalletFragment` instead of writing `CustomIconPickerDialog`** — rejected per ADR-0010 D3 (fragment vs DropDownReceiver lifecycle mismatch). Confirmed during implementation: the fragment also fans out marker-placement itself, which would conflict with our `submitOk` path.
- **Per-cell sync bitmap fetch on the UI thread** — rejected per ADR-0010 + R10. Implementation uses a 2-thread `ExecutorService` so even a 500-icon iconset binds without jank.
- **Auto-fire Submit immediately after icon pick** — rejected per the clarify-round Q2 answer. Picking only enables Submit; explicit Submit is the only mutating action (matches feature 002's deliberate-commit posture).

## Consequences

**Positive:**

- MVP commit (`7688624`) is a single self-contained behaviour change with zero new runtime dependencies (no Robolectric, no AppCompat, no Material).
- The `safeClick` helper closed a pre-existing Constitution VI gap that ADR-0009's audit had missed — net cleanup of the entire `gotopage/` package.
- Single seam (`IconResolver`) keeps SDK dependency one-directional and JVM-testable.
- All 104 JVM unit tests pass (`./gradlew :app:testCivDebugUnitTest`); zero lint warnings (`./gradlew :app:lintCivDebug`).

**Negative:**

- Dialog-UI test coverage (T016 items 1–8, 11; T017; T030a) is deferred. The Espresso path will pick most of them up on-device, but until then the dialog state machine has no automated test gate.
- `pref_goto_marker_mode` persisting all 9 modes (not just `CUSTOM_ICON`) changes feature 002's prior session-reset behaviour. Operators who relied on the implicit "every restart goes back to Move only" safety net lose it. Documented in ADR-0010 D5; the mitigation is keeping `MOVE_ONLY` as the install-time default so fresh installs still don't auto-drop.

## Links

- Spec: `specs/003-custom-marker-icon/spec.md`
- Plan: `specs/003-custom-marker-icon/plan.md`
- Tasks: `specs/003-custom-marker-icon/tasks.md`
- Pre-implementation ADR: [ADR-0010](./0010-custom-marker-icon-picker.md) (SDK reconnaissance + D1–D6 architectural decisions)
- Prior ADRs invoked: ADR-0007 (javap-the-SDK discipline), ADR-0009 (feature 002 GoTo page; D1/D2/D6 referenced).
- Constitution principles satisfied: I (formatter), II (TDD — RED→GREEN observable across MarkerMode/IconResolver/PreferenceStore/FilterTest commits), III (UX consistency + docs/ui updated), IV (worker-pool off-main-thread; performance measurements pending T030 Espresso), V (this ADR), VI (`safeClick` retrofit + every new callback wrapped).
- Commits: `7688624` (MVP — US1 functional path), this commit (US4 live fallback + Constitution VI audit retrofit + docs).
- Upstream cross-reference: SDK behaviour anchored against `github.com/TAK-Product-Center/atak-civ` per [feedback memory `feedback-plan-phase-code-anchoring`].
