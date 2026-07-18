# Tasks: Prefill All Native Taiwan Tabs

**Input**: Design documents from `specs/012-prefill-native-tabs/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/activation-prefill-contract.md`, `quickstart.md`

**Tests**: Every behavioural change is test-first. ATAK-owned dialog visuals
and physical-runtime compatibility remain explicit device checks.

**Organization**: Tasks are grouped by user story and executed sequentially
where they touch the same controller or pane files.

## Phase 1: Setup

**Purpose**: Confirm the existing implementation seams and clean baseline.

- [X] T001 Verify the active feature, clean post-planning baseline, Java/test/document paths, and existing `.gitignore` coverage from `specs/012-prefill-native-tabs/plan.md`
- [X] T002 Confirm current activation, selection, Clear, Auto Fill, validation, and render behaviour in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java` and `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`

---

## Phase 2: Foundational

**Purpose**: Establish test seams and the RED baseline before production edits.

- [X] T003 Run the existing focused controller and pane contract suites to record the pre-change GREEN baseline from `app/src/test/java/com/atakmap/android/twcoord/nativeentry/`
- [X] T004 Add a package-private converter injection seam for later failure testing without changing public APIs in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`

**Checkpoint**: Existing behaviour is green and production conversion can be
failed deterministically in a JVM test.

---

## Phase 3: User Story 1 — Inspect One Point in Every System (Priority: P1) MVP

**Goal**: One non-null host activation atomically prepares Taipower, TWD97,
and TWD67, with independent unavailable state and no stale values.

**Independent Test**: Activate once for a main-island point and switch through
all systems without Auto Fill; then repeat for a zone-119 point and 100
alternating points.

### Tests for User Story 1

- [X] T005 [US1] Add failing main-island all-system, zone-119 unavailable-Taipower, and 100-alternation stale-state tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java`
- [X] T006 [US1] Add a failing activate-once/switch-all-systems contract test in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`
- [X] T007 [US1] Run the new focused tests and record the expected RED result before production implementation in `specs/012-prefill-native-tabs/tasks.md`

### Implementation for User Story 1

- [X] T008 [US1] Implement per-system draft state and atomic three-system activation snapshots in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`
- [X] T009 [US1] Render each prepared draft and its independent unavailable state on system selection in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [X] T010 [US1] Run the focused controller and pane contract suites under `app/src/test/java/com/atakmap/android/twcoord/nativeentry/` until all User Story 1 tests pass

**Checkpoint**: User Story 1 is independently functional without invoking
native Auto Fill.

---

## Phase 4: User Story 2 — Edit or Confirm One Prepared System (Priority: P2)

**Goal**: Preparation produces no human notification or host action, and only
the active draft supplies validation and a returned WGS84 point.

**Independent Test**: Activate all systems, switch/edit one draft, and verify
the listener count and returned point belong only to that active draft.

### Tests for User Story 2

- [X] T011 [US2] Add failing zero-programmatic-notification and active-only validation/result tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java`
- [X] T012 [US2] Add active-only host result and format regression coverage in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`

### Implementation for User Story 2

- [X] T013 [US2] Complete active-draft projection and preserve exactly one permitted human selection notification in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`
- [X] T014 [US2] Run the focused controller and pane contract suites under `app/src/test/java/com/atakmap/android/twcoord/nativeentry/` until all User Story 2 tests pass

**Checkpoint**: Prepared background systems have no host side effects.

---

## Phase 5: User Story 3 — Preserve Shared and Read-Only Behaviour (Priority: P3)

**Goal**: Read-only activation prepares the same snapshot while Clear, Auto
Fill, unexpected failure, and dispose retain their shipped safety semantics.

**Independent Test**: Exercise editable/read-only activation, active-only
Clear/Auto Fill, injected preparation failure, and late callbacks.

### Tests for User Story 3

- [X] T015 [US3] Add failing read-only all-system and active-only Clear/Auto Fill tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java`
- [X] T016 [US3] Add failing unexpected-preparation-failure/no-stale-result and late-callback tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneSafetyTest.java`

### Implementation for User Story 3

- [X] T017 [US3] Invalidate every prior draft atomically on unexpected activation failure while preserving active-only Clear/Auto Fill in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`
- [X] T018 [US3] Preserve contained host-boundary rendering and disposal behaviour in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [X] T019 [US3] Run all native-entry JVM suites under `app/src/test/java/com/atakmap/android/twcoord/nativeentry/` until all User Story 3 tests pass

**Checkpoint**: All three user stories work independently and stale host data
cannot escape after failure or disposal.

---

## Phase 6: Polish and Cross-Cutting Validation

- [X] T020 [P] Set `PLUGIN_VERSION` to 1.4.2 in `app/build.gradle` and update behaviour/version guidance in `docs/ui/native-taiwan-coordinate-entry.md`, `docs/user-guide.md`, `docs/user-guide_zh.md`, and `CHANGELOG.md`
- [X] T021 Run `./gradlew :app:spotlessApply` followed by `./gradlew :app:spotlessCheck :app:lint :app:testCivDebugUnitTest :app:assembleCivDebug`
- [X] T022 Verify existing coordinate golden-vector, zone-119, and round-trip suites under `app/src/test/java/com/atakmap/android/twcoord/coord/` and `app/src/test/java/com/atakmap/android/twcoord/gotopage/` pass without changed constants or tolerances
- [ ] T023 Check connected devices and execute the ATAK 5.7.0.9 quickstart journey when available; keep exact ATAK 5.5 physical evidence explicitly pending unless actually run, per `specs/012-prefill-native-tabs/quickstart.md`
- [ ] T024 Record activation timing evidence when a reference device is available; otherwise retain the `<100 ms` device measurement as pending in `specs/012-prefill-native-tabs/plan.md`
- [X] T025 Scan the reviewed diff for sensitive workstation paths/identifiers and run `git diff --check`

---

## Dependencies and Execution Order

### Phase Dependencies

- Setup precedes Foundational.
- Foundational establishes the converter failure seam and baseline before RED
  tests.
- US1 introduces the shared per-system state and therefore precedes US2/US3.
- US2 and US3 extend the US1 state model sequentially because they touch the
  same controller and pane files.
- Polish starts only after all JVM story checkpoints are green.

### Parallel Opportunities

- T020 documentation can run independently after behaviour stabilizes.
- Device availability checks may begin while full Gradle gates run, but saved
  evidence must reflect the final built APK.
- No controller/pane implementation task is marked parallel because those
  files share state invariants.

## Implementation Strategy

### MVP First

1. Complete Setup and Foundational.
2. Observe the US1 tests fail.
3. Implement the atomic all-system snapshot.
4. Stop and validate US1 independently before active-only and safety work.

### Incremental Delivery

1. US1 removes empty/stale system switching.
2. US2 proves host consumption and notifications remain active-only.
3. US3 preserves Clear, Auto Fill, read-only, failure, and disposal safety.
4. Full quality gates, documentation, and honest device matrix complete the
   implementation run.

## Notes

- Mark a task `[X]` only after its stated evidence exists.
- RED test evidence may be recorded inline in the task description or a short
  note below the applicable phase; do not commit generated Gradle logs.
- Do not claim ATAK 5.5 device PASS from source inspection, compilation, or a
  5.7.0.9 device run.
- This feature ships as v1.4.2.

## Test-First Evidence

- T007 RED (2026-07-18): focused native-entry run completed 38 tests with 8
  expected failures covering inactive main-island drafts, zone-119 drafts,
  repeated activation, read-only preparation, and injected background failure.
- T021/T022 GREEN (2026-07-18): final gates completed 383 tests with zero
  failures/errors and two existing skips; Spotless, lint, and Civ debug APK
  assembly passed.
- T023 partial (2026-07-18): final v1.4.2 APK installed successfully on
  `SM-X826B` running ATAK-CIV 5.7.0.9, and launch/logcat smoke found no fatal
  or version-skew error. The device was locked, so the interactive three-tab
  quickstart remains pending; exact ATAK 5.5 physical evidence also remains
  pending.
- T024 pending: no interactive activation timing trace was collected from the
  locked reference device; the plan's `<100 ms` device measurement is not
  claimed as complete.
