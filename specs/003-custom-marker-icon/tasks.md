---

description: "Task list for feature 003-custom-marker-icon"
---

# Tasks: Custom Marker Icon on the GoTo Page

**Input**: Design documents from `/specs/003-custom-marker-icon/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: REQUIRED. Constitution Principle II is non-negotiable — tests come before the production code that satisfies them. Test tasks appear before the implementation tasks they cover, in every user-story phase.

**Constitution VI**: Every task that adds a new host-callable callback (`OnClickListener`, `OnItemClickListener`, `BroadcastReceiver.onReceive`, `DialogInterface.OnCancelListener`, `BaseAdapter.getView`, `Runnable.run`, etc.) MUST add the outer `try/catch (Throwable)` guard in the same change. The Polish phase has a final audit pass; do not defer the guards to that pass.

**Organization**: Tasks are grouped by user story. US1 and US2 are both Priority P1 and share implementation surface; US2 is delivered as a test-only gate on top of US1's implementation (see Phase 4 below).

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel — different files, no dependency on a still-pending task
- **[Story]**: Which user story this task belongs to (US1 / US2 / US3 / US4)
- Each task includes the absolute file path

## Path Conventions

- **Production code**: `app/src/main/java/com/atakmap/android/twcoord/gotopage/`
- **Layouts / drawables / strings**: `app/src/main/res/`
- **JVM unit tests**: `app/src/test/java/com/atakmap/android/twcoord/gotopage/`
- **Instrumented (Espresso) tests**: `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/`
- **Docs**: `docs/` (ADRs, UI docs)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: minimal — the Gradle module, formatter, and test scaffolding all exist from features 001 / 002. The only "setup" this feature introduces is a placeholder for the 9th marker-mode drawable so foundational tasks that reference it can compile.

- [ ] T001 [P] Add a placeholder 24 dp vector drawable for the Custom Icon radio at `app/src/main/res/drawable/ic_marker_custom_icon.xml` (simple picker-glyph shape; final styling refined in T030)
- [ ] T002 [P] Add an English placeholder for every new string key consumed by foundational + US1 tasks to `app/src/main/res/values/strings.xml`: `goto_mode_custom_icon`, `goto_custom_icon_empty`, `goto_custom_icon_hint_lost`, `goto_custom_icon_dialog_title_iconsets`, `goto_custom_icon_dialog_title_icons`, `goto_custom_icon_back`, `goto_custom_icon_empty_iconsets`, `goto_custom_icon_empty_icons`, `goto_custom_icon_iconset_count_suffix` (e.g. `" (%1$d)"`), `goto_custom_icon_preview_label_format` (e.g. `"%1$s"`). Use English so the build passes; locale parity in T003.
- [ ] T003 [P] Mirror the 10 new keys from T002 into `app/src/main/res/values-zh-rTW/strings.xml` and `app/src/main/res/values-ja/strings.xml` with empty `""` values (placeholders are replaced with proofread translations in T058 — keeping the keys present early so the locale-override pathway exercises them in tests)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: data layer + persistence + enum extension + SDK seam — everything every user story needs to compile and run. No story-specific UI work happens here; that's Phase 3+.

**⚠️ CRITICAL**: No user-story phase can begin until this phase is complete.

### Value classes

- [ ] T004 [P] Create `IconsetSummary` immutable value class (fields: `uid`, `name`, `iconCount`; `equals`/`hashCode` on `uid`) at `app/src/main/java/com/atakmap/android/twcoord/gotopage/IconsetSummary.java` per [contracts/icon-resolver.md § IconsetSummary](./contracts/icon-resolver.md#iconsetsummary-value-class)
- [ ] T005 [P] Create `IconRow` immutable value class (fields: `id`, `iconsetUid`, `group`, `fileName`, `displayName`, `iconsetPath`; `equals`/`hashCode` on `id`) at `app/src/main/java/com/atakmap/android/twcoord/gotopage/IconRow.java` per [contracts/icon-resolver.md § IconRow](./contracts/icon-resolver.md#iconrow-value-class). Include `displayName` derivation (strip `.png`/`.jpg`/`.jpeg`/`.svg` suffix, case-insensitive)
- [ ] T006 [P] Create `IconSelection` immutable value class (fields: `iconsetPath`, `iconsetUid`, `iconsetName`, `iconFileName`, `iconId`; `equals`/`hashCode` on `iconsetPath`; static factory `IconSelection.from(IconRow, IconsetSummary)` and `IconSelection.from(UserIcon, UserIconSet)`) at `app/src/main/java/com/atakmap/android/twcoord/gotopage/IconSelection.java` per [data-model.md §1.2](./data-model.md#12-iconselection-new-value-class)

### Enum extension

- [ ] T007 Author `MarkerModeV2Test` JVM unit tests at `app/src/test/java/com/atakmap/android/twcoord/gotopage/MarkerModeV2Test.java` covering all 6 cases in [contracts/marker-mode-v2.md § Test contract](./contracts/marker-mode-v2.md#test-contract). Tests MUST fail at this point (CUSTOM_ICON does not exist yet)
- [ ] T008 Extend `app/src/main/java/com/atakmap/android/twcoord/gotopage/MarkerMode.java` per [contracts/marker-mode-v2.md § v2 enum surface](./contracts/marker-mode-v2.md#v2-enum-surface): add `CUSTOM_ICON("b-m-p-s-m")`, `requiresIconPath()`, `isCustomIcon()`. Re-run T007 — all 6 tests MUST pass

### SDK seam (IconResolver)

- [ ] T009 [P] Create `IconDatabaseFacade` interface at `app/src/main/java/com/atakmap/android/twcoord/gotopage/IconDatabaseFacade.java` mirroring the 4 `UserIconDatabase` methods `IconResolver` consumes: `List<UserIconSet> getIconSets(boolean withIcons, boolean withBitmaps)`, `UserIconSet getIconSet(String uid, boolean, boolean)`, `Bitmap getIconBitmap(int id)`, `UserIcon getIcon(String iconsetUid, String fileName, boolean withBitmap)`. This is the JVM-mockable seam for testing
- [ ] T010 Author `IconResolverTest` JVM unit tests at `app/src/test/java/com/atakmap/android/twcoord/gotopage/IconResolverTest.java` covering all 6 cases in [contracts/icon-resolver.md § Test contract](./contracts/icon-resolver.md#test-contract). Mock `IconDatabaseFacade` via Mockito. Tests MUST fail
- [ ] T011 Implement `IconResolver` at `app/src/main/java/com/atakmap/android/twcoord/gotopage/IconResolver.java` per [contracts/icon-resolver.md](./contracts/icon-resolver.md): constructor accepts `Context` (production path wraps `UserIconDatabase.instance(ctx)` in a `IconDatabaseFacade` adapter) AND a constructor overload accepts `IconDatabaseFacade` directly (test path). Implement `listIconsets`, `listIcons`, `loadBitmap`, `resolveSelection`, `isValidIconsetPath`, `invalidateCaches`. Every public method body wrapped in `try/catch (Throwable)` per Constitution VI; alphabetic ordering per [R13](./research.md#r13--iconseticon-ordering-inside-the-picker); cache `listIconsets()` results until `invalidateCaches()` is called. Re-run T010 — all 6 tests MUST pass

### Persistence extension

- [ ] T012 Add new test `PreferenceStoreCustomIconTest` at `app/src/test/java/com/atakmap/android/twcoord/prefs/PreferenceStoreCustomIconTest.java` covering: round-trip of `pref_goto_marker_mode` enum-name, round-trip of `pref_goto_last_iconset_path`, default `MOVE_ONLY` when key absent, default `null` when path key absent, atomic clear (write `MOVE_ONLY` + remove path in single `commit()`). Tests MUST fail
- [ ] T013 Extend `app/src/main/java/com/atakmap/android/twcoord/prefs/PreferenceStore.java` with: `MarkerMode getGotoMarkerMode()` / `void setGotoMarkerMode(MarkerMode)`, `String getGotoLastIconsetPath()` / `void setGotoLastIconsetPath(String)` / `void clearGotoLastIconsetPath()`, and `void clearCustomIconSelectionAtomic()` (single-`commit()` write of `MOVE_ONLY` + remove of path key, per [data-model.md §2 Atomicity](./data-model.md#2-persisted-state-sharedpreferences)). Re-run T012 — all 5 tests MUST pass

### Layout placeholders

- [ ] T014 Modify `app/src/main/res/layout/tw_coord_goto.xml` to add (a) a 9th `RadioButton` row with id `goto_mode_custom_icon` matching the visual weight of the existing 8 radios from feature 002, (b) a `LinearLayout` row with id `goto_custom_icon_preview` containing an `ImageView` (`goto_custom_icon_thumb`, 32 dp square), a `TextView` (`goto_custom_icon_label`), and a second `TextView` (`goto_custom_icon_hint`, `visibility=gone` by default). Preview row's parent visibility is gone by default; toggled in T024. Use plain Android widgets per ADR-0009 D6
- [ ] T015 Wire the 9 new view ids declared in T014 into a new private `bindCustomIconViews()` helper in `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoView.java` (just `findViewById` plumbing — no behaviour yet). Existing `refreshLocalisedStrings()` gains a block that re-applies all 4 new strings to the 9th radio + preview label + hint + (later) dialog title; pull from `localisedContext` per FR-013

**Checkpoint**: Foundation ready — every user-story phase can now begin.

---

## Phase 3: User Story 1 — Pick a custom icon and drop it at the resolved coordinate (Priority: P1) 🎯 MVP

**Goal**: Operator selects **Custom Icon**, opens picker, picks iconset → picks icon, submits. Map pans to the resolved coordinate and a marker bearing the picked icon is dropped, behaving identically to host-placed markers.

**Independent Test**: per [quickstart.md § 2 Acceptance Flow A](./quickstart.md#2-acceptance-flow-a--us1-pick--drop-p1-happy-path) — 10-step end-to-end walk with `H7509 DB4016` + the Responder iconset's `fire_truck.png`.

### Tests for User Story 1

> **NOTE**: Write these tests FIRST, ensure they FAIL before implementation.

- [ ] T016 [P] [US1] Author `CustomIconPickerDialogTest` Robolectric unit tests at `app/src/test/java/com/atakmap/android/twcoord/gotopage/CustomIconPickerDialogTest.java` covering items 1–8, 11, **12** of [contracts/custom-icon-picker.md § Test contract](./contracts/custom-icon-picker.md#test-contract) — items 9 / 10 depend on `ICONSET_*` wiring and are deferred to US3+US4. Item 12 covers the FR-010a adapter-layer corrupt-bitmap silent-skip path (assert grid `getCount()` after filter; assert no `getView()` for skipped rows). At this point items 1–8, 11, 12 MUST fail
- [ ] T017 [P] [US1] Author `TwCoordGotoViewCustomIconHappyPathTest` JVM/Robolectric unit test at `app/src/test/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoViewCustomIconHappyPathTest.java` covering: switching to CUSTOM_ICON with no selection shows empty-state and disables Submit; picking an icon via the dialog enables Submit; submit dispatches a `MarkerCreator` chain that includes `setIconPath(currentSelection.iconsetPath)`. Tests MUST fail
- [ ] T018 [P] [US1] Author `CustomIconPickerEspressoTest` instrumented test at `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/CustomIconPickerEspressoTest.java` covering Acceptance Flow A end-to-end. Test MUST fail

### Picker preview state + dialog

- [ ] T019 [P] [US1] Create `PickerPreviewState` sealed-ish hierarchy (`Empty`, `FallbackHint`, `Populated(IconSelection)`) at `app/src/main/java/com/atakmap/android/twcoord/gotopage/PickerPreviewState.java` per [data-model.md §1.3](./data-model.md#13-pickerpreviewstate-new-sealedclosed-enum--payload). Pattern matches the existing `ParseResult` from feature 002
- [ ] T020 [P] [US1] Create 4 new dialog layouts: `app/src/main/res/layout/custom_icon_picker_dialog.xml` (outer frame: title + back button + content FrameLayout), `custom_icon_picker_iconset_row.xml` (single iconset row), `custom_icon_picker_icon_cell.xml` (one grid cell), `custom_icon_picker_empty_row.xml` (shared empty-state). All plain-Android widgets per ADR-0009 D6
- [ ] T021 [US1] Implement `CustomIconPickerDialog` at `app/src/main/java/com/atakmap/android/twcoord/gotopage/CustomIconPickerDialog.java` per [contracts/custom-icon-picker.md](./contracts/custom-icon-picker.md): constructor, `show(IconSelection current)` re-open rule, `dismissIfShowing()`, step-1 / step-2 swap, two private `BaseAdapter` inner classes (one for iconsets, one for icons), `Listener` callback. Worker-thread dispatch via injected `ExecutorService`; main-thread bind via injected `Handler`. **Every** click/cancel/getView callback wrapped in `try/catch (Throwable)` per Constitution VI. `onIconsetsChanged()` stub for US3/US4 to wire later. Re-run T016 — items 1–8 and 11 MUST pass

### View-layer wiring

- [ ] T022 [US1] Add `currentSelection` (`IconSelection`, nullable) and `pickerDialog` (`CustomIconPickerDialog`, nullable, lazy) fields to `TwCoordGotoView`. Add a shared `ExecutorService` field (`Executors.newFixedThreadPool(2)`) and a `Handler(Looper.getMainLooper())` field — both lifecycle-managed (started lazily on first `bind`, shut down in receiver's `onDropDownClose`)
- [ ] T023 [US1] Wire the 9th radio's `OnClickListener` in `TwCoordGotoView.<init>`: calls `setMarkerMode(MarkerMode.CUSTOM_ICON)`. Wrapped in `try/catch (Throwable)` per Constitution VI. Update `setMarkerMode` + `applyMarkerModeUI` to handle the 9th option's selected-state styling identically to the existing 8
- [ ] T024 [US1] Implement preview-area rendering in `TwCoordGotoView`: a new `renderCustomIconPreview()` method computes the `PickerPreviewState` from `(markerMode, currentSelection, pendingFallbackHint)` per [data-model.md §3](./data-model.md#3-runtime-per-view-state-in-twcoordgotoview), then toggles visibility / thumbnail / label / hint accordingly. Invoked from `applyMarkerModeUI` and from `bind`. Preview-row visibility is gone whenever `markerMode != CUSTOM_ICON`
- [ ] T025 [US1] Wire the preview row's `OnClickListener` in `TwCoordGotoView` to lazily construct `pickerDialog` and call `pickerDialog.show(currentSelection)`. Wrapped in `try/catch (Throwable)` per Constitution VI. The dialog's `Listener.onIconPicked` updates `currentSelection`, persists it via `prefs.setGotoLastIconsetPath(...)`, calls `renderCustomIconPreview()` + `refreshSubmitEnabled()`. `Listener.onCancelled` is a no-op (preview state unchanged per spec edge case)

### Submit-path integration (FR-005 + FR-007)

- [ ] T026 [US1] Extend `refreshSubmitEnabled` in `TwCoordGotoView` to apply the [contracts/marker-mode-v2.md § Submit-enabled rule](./contracts/marker-mode-v2.md#submit-enabled-rule-fr-006-contract-on-the-view-layer): `enabled = coordValid AND validMarkerSelection()`, where `validMarkerSelection()` returns true for the 8 non-custom modes and `currentSelection != null` for CUSTOM_ICON. Called from every relevant trigger (parse change, radio click, picker pick, picker cancel)
- [ ] T027 [US1] Extend the existing `submitOk` method in `TwCoordGotoView` (per [contracts/marker-mode-v2.md § Submit-path branching](./contracts/marker-mode-v2.md#submit-path-branching-contract)): when `markerMode.requiresIconPath() && currentSelection != null`, append `.setIconPath(currentSelection.iconsetPath())` to the existing `PlacePointTool.MarkerCreator` chain immediately before `.placePoint()`. The existing outer `try/catch (Throwable)` already satisfies Constitution VI; no new guard needed. Re-run T017 — all 3 cases MUST pass

### Marker-mode persistence (so US1's submit also benefits from durability)

- [ ] T028 [US1] In `TwCoordGotoView`, replace the in-session-only `markerMode` initialisation with a read from `prefs.getGotoMarkerMode()` on every `bind(...)` call. Add a `setMarkerMode(MarkerMode)` wrapper that writes through to `prefs.setGotoMarkerMode(...)`. Note: this changes feature 002's session-reset behaviour to durable, per [ADR-0010 D5](../../docs/adr/0010-custom-marker-icon-picker.md) and [research R9](./research.md#r9--persistence-extending-preferencestore)

### Drawable + Espresso

- [ ] T029 [P] [US1] Finalise the 9th-radio drawable: replace the T001 placeholder with the final picker-glyph vector (16-square coloured pin grid / picker-cursor motif) at `app/src/main/res/drawable/ic_marker_custom_icon.xml`
- [ ] T030 [US1] Run T018 again — the end-to-end Espresso Flow A MUST pass on the reference device or emulator. Capture median timings for SC-002 (picker open ≤ 300 ms), SC-003 (icon list ≤ 500 ms), SC-004 (post-pick enable ≤ 16 ms) in the test's log output
- [ ] T030a [US1] Author `CustomIconSubmitStressTest` JVM/Robolectric test at `app/src/test/java/com/atakmap/android/twcoord/gotopage/CustomIconSubmitStressTest.java` covering SC-006 (100 % icon-correctness across 100 consecutive Submits): loop 100 iterations, each iteration sets a different `currentSelection` (via mocked `IconResolver`), invokes `TwCoordGotoView.submitOk` with a fake `MarkerCreator` capture, and asserts the captured `setIconPath(...)` argument equals the iteration's `iconsetPath`. Zero iterations may show a default-icon fallback. Test MUST fail until T027 lands; final run alongside T054

**Checkpoint**: User Story 1 is fully functional. An operator can pick a custom icon and drop a custom-icon marker. This is the MVP — STOP and validate against [quickstart.md § 2 Flow A](./quickstart.md#2-acceptance-flow-a--us1-pick--drop-p1-happy-path) before proceeding.

---

## Phase 4: User Story 2 — Picker preview blocks Submit until a valid icon is selected (Priority: P1)

**Goal**: dedicated validation gate verifying the Submit-disabled-until-icon-picked rule from FR-006. Implementation is delivered by US1 (`refreshSubmitEnabled` extension in T026); this phase adds the dedicated test gate.

**Independent Test**: per [quickstart.md § 3 Acceptance Flow B](./quickstart.md#3-acceptance-flow-b--us2-validation-gate-p1-correctness) — 6-step walk asserting Submit's enabled state across mode switches and coordinate edits.

### Tests for User Story 2

- [ ] T031 [P] [US2] Author `CustomIconSubmitGateTest` JVM/Robolectric unit test at `app/src/test/java/com/atakmap/android/twcoord/gotopage/CustomIconSubmitGateTest.java` covering all 3 acceptance scenarios from US2: (AC1) switch to CUSTOM_ICON with no persisted selection ⇒ Submit disabled + preview empty-state; (AC2) dismiss picker without picking ⇒ Submit stays disabled; (AC3) invalidate coord ⇒ Submit disabled regardless of icon-pick status. Tests should pass immediately given T026's implementation — if any fail, fix T026 before continuing
- [ ] T032 [P] [US2] Audit `TwCoordGotoView` to confirm the empty-state hint string `goto_custom_icon_empty` is used (not silently shown blank); add a Robolectric assertion in T031 that the `goto_custom_icon_hint` `TextView` text equals `getString(R.string.goto_custom_icon_empty)` when the preview is in `Empty` state
- [ ] T033 [US2] Run T031 + T032 — all 3 acceptance scenarios MUST pass

**Checkpoint**: User Story 2's contract is locked in by tests. Submit is provably gated by the icon-picked status. Independently demonstrable from US1.

---

## Phase 5: User Story 3 — Selection persists across plugin restarts (Priority: P2)

**Goal**: Operator re-opens the page (or restarts ATAK) and finds **Custom Icon** + the previously-picked icon already selected, 0 additional taps needed.

**Independent Test**: per [quickstart.md § 4 Acceptance Flow C](./quickstart.md#4-acceptance-flow-c--us3-cross-restart-persistence-p2) — 7-step walk including `adb shell am force-stop` and re-launch.

### Tests for User Story 3

- [ ] T034 [P] [US3] Author `TwCoordGotoViewRestorePathTest` JVM/Robolectric unit test at `app/src/test/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoViewRestorePathTest.java` covering: bind with `prefs.getGotoMarkerMode() == CUSTOM_ICON && prefs.getGotoLastIconsetPath() == validPath` ⇒ marker-mode restored to CUSTOM_ICON, `currentSelection` populated via `IconResolver.resolveSelection(...)`, preview shows thumbnail+label. Mock `IconResolver`. Test MUST fail at this point
- [ ] T035 [P] [US3] Author `CustomIconPickerReopenRuleTest` Robolectric test at `app/src/test/java/com/atakmap/android/twcoord/gotopage/CustomIconPickerReopenRuleTest.java` covering [contracts/custom-icon-picker.md § Re-open rule](./contracts/custom-icon-picker.md#re-open-rule-fr-003--clarification-q1): `show(null)` opens at step 1; `show(currentValid)` opens at step 2 with icon highlighted; `show(currentInvalid)` opens at step 1. Test MUST fail

### Implementation for User Story 3

- [ ] T036 [US3] Implement the bind-path restore logic in `TwCoordGotoView.bind(...)` per [data-model.md §4](./data-model.md#4-page-open--bind-flow): on every bind, read `prefs.getGotoMarkerMode()` + `prefs.getGotoLastIconsetPath()`; if mode==CUSTOM_ICON and path is non-null and `IconResolver.isValidIconsetPath(path)`, populate `currentSelection` via `IconResolver.resolveSelection(path)` and set markerMode. If validity check fails, defer to T040 (US4 fallback path) — leave a TODO bookmark for US4. Wrapped in `try/catch (Throwable)`. Re-run T034 — MUST pass
- [ ] T037 [US3] Refine `CustomIconPickerDialog.show(IconSelection current)` to implement the re-open rule precisely: when `current != null && iconResolver.isValidIconsetPath(current.iconsetPath())`, fetch the matching `IconsetSummary` via `iconResolver.listIconsets()` filter, transition to step 2 with `current.iconId` highlighted in the grid adapter. When `current.iconsetUid` no longer resolves, fall back to step 1 silently (no listener notification — US4's job to fire fallback). Re-run T035 — MUST pass

### Espresso coverage

- [ ] T038 [P] [US3] Extend `CustomIconPickerEspressoTest` (T018) with a `testPersistedSelectionRestoresAcrossPageReopen` test case that opens the page, picks Custom Icon + an icon, closes the drop-down, re-opens — asserts the preview shows the same thumbnail and Submit is enabled. Add it as a method on the existing test class to share setUp

**Checkpoint**: User Story 3 fully functional. Re-opening the page restores the operator's last Custom-Icon selection without intervention.

---

## Phase 6: User Story 4 — Graceful fallback when a persisted icon's iconset is removed (Priority: P3)

**Goal**: Persisted icon's iconset has been removed between sessions. Page silently reverts to **Move only**, clears the stale path, surfaces a one-shot empty-state hint the next time the operator opens the picker.

**Independent Test**: per [quickstart.md § 5 Acceptance Flow D](./quickstart.md#5-acceptance-flow-d--us4-graceful-fallback-p3) — remove the Responder iconset via ATAK settings, re-open page, observe Move only + cleared prefs + one-shot hint behaviour.

### Tests for User Story 4

- [ ] T039 [P] [US4] Author `CustomIconFallbackTest` JVM/Robolectric unit test at `app/src/test/java/com/atakmap/android/twcoord/gotopage/CustomIconFallbackTest.java` covering all 3 acceptance scenarios from US4: (AC1) bind with persisted selection whose iconset no longer resolves ⇒ markerMode = MOVE_ONLY, both prefs cleared atomically, `pendingFallbackHint = true`; (AC2) after fallback, first switch *to* CUSTOM_ICON ⇒ preview shows lost-icon hint exactly once; (AC3) switch to different mode and back ⇒ hint no longer shown. Tests MUST fail
- [ ] T040 [P] [US4] Author `CustomIconLiveIconsetRemovalTest` Robolectric test at `app/src/test/java/com/atakmap/android/twcoord/gotopage/CustomIconLiveIconsetRemovalTest.java` covering [data-model.md §7 row 5](./data-model.md#7-failure-modes-and-recovery): operator picks CUSTOM_ICON + an icon, then `ICONSET_REMOVED` broadcast fires for the matching `uid` while the page is open — assert FR-009 fallback fires immediately, preview repaints to empty/hint, picker dialog (if open) transitions back to step 1. Test MUST fail
- [ ] T041 [P] [US4] Author `CustomIconFallbackEspressoTest` instrumented test at `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/CustomIconFallbackEspressoTest.java` covering Acceptance Flow D. Test requires programmatic iconset removal via ATAK's `REMOVE_ICONSET` broadcast — implement using a test helper that fires the broadcast for a test-only iconset added in `setUp`. Test MUST fail

### Implementation for User Story 4

- [ ] T042 [US4] Add the bind-path fallback branch to `TwCoordGotoView.bind(...)`: when `prefs.getGotoMarkerMode() == CUSTOM_ICON && persistedPath != null && !iconResolver.isValidIconsetPath(persistedPath)`, fire `prefs.clearCustomIconSelectionAtomic()`, set `markerMode = MOVE_ONLY`, set `currentSelection = null`, set `pendingFallbackHint = true`, log at WARN with the cleared path. This replaces the TODO bookmark left in T036. Re-run T039 AC1 — MUST pass
- [ ] T043 [US4] Add the one-shot hint render in `renderCustomIconPreview()`: when `markerMode == CUSTOM_ICON && pendingFallbackHint`, render the `FallbackHint` state (show `goto_custom_icon_hint` TextView with `R.string.goto_custom_icon_hint_lost`), and on return set `pendingFallbackHint = false`. Subsequent renders show the normal empty-state. Re-run T039 AC2+AC3 — MUST pass
- [ ] T044 [US4] Wire the `ICONSET_ADDED` / `ICONSET_REMOVED` broadcast receiver in `TwCoordGotoReceiver`: register on `onDropDownVisible(true)`, unregister on `onDropDownClose()`. `onReceive` body wrapped in `try/catch (Throwable)` per Constitution VI. On `ICONSET_REMOVED` whose `uid` matches `currentSelection.iconsetUid`, dispatch to view layer to fire the fallback path (extract a `view.onIconsetRemoved(String uid)` method that runs the same atomic-clear logic as T042 plus repaint). On any `ICONSET_*`, call `iconResolver.invalidateCaches()` and `pickerDialog.onIconsetsChanged()` if the dialog is open. Re-run T040 — MUST pass
- [ ] T045 [US4] Run T041 — Espresso Flow D MUST pass on device

**Checkpoint**: All four user stories independently functional. Feature is implementation-complete.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Constitution-mandated audits, documentation, locale parity, formatter pass. None of these add behaviour; all of them gate Definition of Done.

### Constitution VI audit (NON-NEGOTIABLE)

- [ ] T046 Run the grep from [quickstart.md § 8](./quickstart.md#8-constitution-vi-sanity-check-before-commit) against the new code: every match for `onReceive` / `onClick` / `onItemClick` / `getView` / `onCancel` / `run` in `app/src/main/java/com/atakmap/android/twcoord/gotopage/` MUST have a `try { ... } catch (Throwable t) { Log.w(TAG, ..., t); }` body. Any unguarded entry point is a CRITICAL finding — fix before continuing
- [ ] T047 Re-read `IconResolver`, `CustomIconPickerDialog`, `TwCoordGotoView` and confirm every SDK call (`UserIconDatabase.*`, `PlacePointTool.*`, `AtakBroadcast.*`) is inside a `try/catch (Throwable)`. The `placePoint()` call inherits feature 002's existing wrap; everything else added by this feature MUST have its own

### Locale parity

- [ ] T048 [P] Replace the placeholder zh-rTW values added in T003 with proofread Traditional-Chinese translations in `app/src/main/res/values-zh-rTW/strings.xml`. Run through the `zhtw-mcp` MCP for tone consistency with feature 001/002 strings (e.g. `自訂圖示`, `挑選圖示`)
- [ ] T049 [P] Replace the placeholder ja values added in T003 with Japanese translations in `app/src/main/res/values-ja/strings.xml` (e.g. `カスタムアイコン`, `アイコンを選択`)
- [ ] T050 [P] Run `./gradlew :app:lint` and verify zero `MissingTranslation` warnings on the new keys

### Accessibility audit (Constitution III)

- [ ] T050a Audit every new view id in `app/src/main/res/layout/tw_coord_goto.xml` (9th radio, `goto_custom_icon_preview`, `goto_custom_icon_thumb`, `goto_custom_icon_label`, `goto_custom_icon_hint`) and the 4 dialog XMLs (T020) for accessibility attributes per Constitution Principle III "Accessibility minimums": (a) every `ImageView` MUST have `android:contentDescription` — picker thumbnail = iconset name + icon name; grid cells = same; back-button = `R.string.goto_custom_icon_back`; (b) every interactive `View` MUST be focusable + clickable with `android:importantForAccessibility="yes"`; (c) text size on labels MUST use `sp` (not `dp`) for OS scale-text support. Verify via `./gradlew :app:lint` (zero new `ContentDescription` / `RelativeOverlap` warnings) and one manual TalkBack walkthrough of Acceptance Flow A on the Galaxy Tab S10+

### Documentation

- [ ] T051 [P] Update `docs/ui/input-page.md` with a new "Custom Icon picker" section covering (a) the 9th radio's appearance, (b) the preview row's three render states, (c) the two-step dialog's layout, (d) screenshots from the Galaxy Tab S10+ run of Acceptance Flow A. Per Constitution III mandate that every UI change updates `docs/ui/`
- [ ] T052 Author `docs/adr/0011-custom-marker-icon-implementation.md` per Constitution V's post-`/speckit-implement` ADR cadence. Capture: any spec deviations encountered, on-device pivots, final structural decisions that drifted from plan.md, performance measurements vs SC-002/003/004, links to commits between branch creation and merge

### Build & test gate

- [ ] T053 Run `./gradlew :app:spotlessApply` per Constitution I (formatter is enforced by the build; this MUST be the last code-touching step before commit)
- [ ] T054 Run `./gradlew :app:testCivDebugUnitTest` — every JVM test MUST pass (new tests + the entire feature-001/002 baseline must remain green)
- [ ] T055 Run `./gradlew :app:connectedCivDebugAndroidTest` on the reference device — every Espresso test MUST pass including T018 (Flow A), T038 (Flow C extension), T041 (Flow D)
- [ ] T056 Run `./gradlew :app:lint` — zero errors, zero new warnings

### On-device validation

- [ ] T057 Manually walk through [quickstart.md § 2–5 Flows A–D](./quickstart.md#2-acceptance-flow-a--us1-pick--drop-p1-happy-path) on the Galaxy Tab S10+. Capture timings for SC-002 / SC-003 / SC-004; if any threshold misses, document in T052 ADR and either fix or justify
- [ ] T057a [P] Procure or generate a ≥ 500-icon test iconset for SC-003 measurement. Two acceptable sources: (a) clone an existing bundled iconset zip (e.g. `responder`) and rename the icon files to produce 500 unique entries, then load via ATAK's iconset manager; (b) script-generate a synthetic `.zip` from public-domain SVGs and load the same way. Record the source + the iconset's UID in `docs/ui/input-page.md` (T051) under a "Performance test setup" sub-heading so CI / future maintainers can reproduce. **Blocks T058.**
- [ ] T058 Manually walk through [quickstart.md § 6 Performance smoke tests](./quickstart.md#6-performance-smoke-tests) using the iconset procured in T057a. Confirm 60 fps under scroll in step 2 and SC-003 ≤ 500 ms median over ≥ 10 picker-open / step-2-bind cycles

### Memory + README

- [ ] T059 [P] Update top-level `README.md` Features table with a row for the new "Custom Icon marker mode" capability; bump `Project layout` ADR count if needed (10 → 11 once T052 lands)

**Final checkpoint**: All Constitution principles satisfied (I: formatted, II: TDD-first, III: docs/ui updated, IV: performance measured, V: ADR-0011 authored, VI: every new entry point wrapped). Feature is ready for `/speckit-analyze` then merge.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup (T001–T003)**: no dependencies. T001–T003 are all [P] — can run in parallel.
- **Phase 2 Foundational (T004–T015)**: depends on Phase 1. T004–T006 [P]; T007 must complete before T008; T009 [P] (interface only); T010 must complete before T011; T012 must complete before T013; T014 must complete before T015. All foundational work BLOCKS Phase 3+.
- **Phase 3 US1 (T016–T030)**: depends on Phase 2 complete. T016/T017/T018 [P] — all 3 tests authored together. T019/T020 [P]. T021 depends on T016, T019, T020. T022 must run before T023/T024/T025. T026 depends on T022. T027 depends on T026 + T008 (MarkerMode v2). T028 depends on T013. T030 depends on every prior US1 task.
- **Phase 4 US2 (T031–T033)**: depends on Phase 3 complete (validation gate is delivered by US1's T026).
- **Phase 5 US3 (T034–T038)**: depends on Phase 2 complete + Phase 3's T013, T026 done. Phases 3 and 5 can run somewhat in parallel if T013/T026 are done first.
- **Phase 6 US4 (T039–T045)**: depends on Phase 5's T036 (US4 replaces the TODO bookmark from T036 with the real fallback branch in T042).
- **Phase 7 Polish (T046–T059)**: depends on all user-story phases.

### User Story Dependencies

- **US1 (P1)**: depends on Foundational. No dependency on other user stories — this is the MVP.
- **US2 (P1)**: depends on US1 — US2 is a test-only gate verifying behaviour US1 implements (specifically T026).
- **US3 (P2)**: depends on Foundational (specifically T013 for persistence API) and on US1's T026 for the picker dialog instance — but US3's bind-path restore work can be developed concurrently with US1's submit-path work if T013 is finished first.
- **US4 (P3)**: depends on US3's T036 — the bind-path restore is where the fallback branch attaches. US4 also depends on US1's picker dialog existing.

### Within Each User Story

- Tests authored FIRST and confirmed-failing before implementation (Constitution II)
- Value classes / data structures before consumers
- View-layer wiring after data layer + dialog ready
- Espresso tests last (depend on every Java change being in place)

### Parallel Opportunities

- T001 / T002 / T003 — all Setup tasks
- T004 / T005 / T006 — all value classes (different files)
- T009 — IconDatabaseFacade interface (no impl deps)
- T016 / T017 / T018 — three test files (different files, all independent)
- T019 / T020 — picker preview state + 4 dialog layouts (different files)
- T029 — drawable finalisation (independent of Java)
- T031 / T032 — US2 test pair
- T034 / T035 — US3 test pair
- T039 / T040 / T041 — US4 test triple
- T048 / T049 / T050 — locale parity tasks
- T051 / T059 — docs (different files)

---

## Parallel Example: Phase 2 Foundational

```text
# Launch the value-class trio together (different files, no dependencies):
Task T004: IconsetSummary.java
Task T005: IconRow.java
Task T006: IconSelection.java

# In parallel with the trio above, author the SDK seam interface:
Task T009: IconDatabaseFacade.java (interface only, no impl needed yet)

# Then the enum extension test + implementation pair (sequential within itself):
Task T007 → T008  # MarkerMode v2 (RED → GREEN)

# Then the SDK seam test + implementation pair (sequential within itself):
Task T010 → T011  # IconResolver (RED → GREEN)

# Then the persistence pair (sequential within itself):
Task T012 → T013  # PreferenceStore (RED → GREEN)

# Layout work runs in parallel with the test pairs above (different file):
Task T014: tw_coord_goto.xml additions
Task T015: TwCoordGotoView.bindCustomIconViews() helper
```

## Parallel Example: Phase 3 User Story 1 tests

```text
# Author all three US1 test files at once — they live in different files:
Task T016 [P]: CustomIconPickerDialogTest.java
Task T017 [P]: TwCoordGotoViewCustomIconHappyPathTest.java
Task T018 [P]: CustomIconPickerEspressoTest.java

# Confirm all three fail. Then write the implementation in dependency order.
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T003) — placeholders only
2. Complete Phase 2: Foundational (T004–T015) — every story needs this
3. Complete Phase 3: User Story 1 (T016–T030)
4. **STOP and VALIDATE**: Acceptance Flow A from quickstart on the Galaxy Tab S10+
5. Demo / commit / consider shipping as a tagged increment

### Incremental Delivery After MVP

6. Add US2 test gate (T031–T033) — fast (tests only; impl already lives in US1)
7. Add US3 persistence (T034–T038) — restores last selection across restarts
8. Add US4 fallback (T039–T045) — graceful when iconsets vanish
9. Polish (T046–T059) — Constitution-mandated audits + docs + formatter
10. `/speckit-analyze` → merge

### Parallel Team Strategy (if multiple developers)

1. **Developer A**: T004–T011 (value classes + IconResolver)
2. **Developer B**: T012–T013 (PreferenceStore) → T028 (markerMode persistence wire-up)
3. **Developer C**: T014–T015 (layout + view scaffolding) → T019–T021 (picker dialog)
4. Once Phase 2 is done, US1 implementation can fan out across A/B/C with T022–T030 (view-layer wiring + Espresso) being the convergence point.
5. US2 / US3 / US4 are owned by whichever developer finished their Phase 3 slice first.

---

## Notes

- **[P]** tasks touch different files and have no dependency on a still-pending task.
- **[Story]** label maps each task to a single user story for traceability — Setup / Foundational / Polish tasks have no `[Story]` label.
- Every user story is independently testable per its Independent Test paragraph in spec.md.
- Tests MUST be confirmed-failing before the matching implementation task runs (Constitution II Red → Green discipline).
- Commit after each task or logical group (e.g. per RED → GREEN pair).
- Every new host-callable callback gets the outer `try/catch (Throwable)` wrap in the SAME change that introduces it; T046 / T047 are the final audit, not the primary defence.
- Stop at any checkpoint to validate independently — quickstart.md has the per-story walk.
- Avoid: cross-story implementation dependencies that break independent testability (US2 is intentionally test-only on top of US1's impl; that is the one explicit exception, called out in the phase description).
