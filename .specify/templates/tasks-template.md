---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Behaviour changes require test-first tasks. Documentation-only,
configuration-only, or demonstrably device-only work may omit an automated
test only when the task records the reason and an applicable validation step.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story?] [RELEASE-GATE?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- **[RELEASE-GATE]**: Evidence that must be completed or explicitly
  dispositioned before public release. TPP preparation may report it as
  pending, but must not silently close it.
- Include exact file paths in descriptions

## Path Conventions

- **Production Java**: `app/src/main/java/com/atakmap/android/twcoord/`
- **Android resources**: `app/src/main/res/`
- **JVM tests**: `app/src/test/java/com/atakmap/android/twcoord/`
- **Feature artifacts**: `specs/[###-feature-name]/`
- **Architecture/UI docs**: `docs/adr/` and `docs/ui/`
- Use the exact paths selected by `plan.md`; do not retain template paths.

<!--
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.

  The /speckit-tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/

  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment

  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Confirm the real source, test, resource, and documentation paths from plan.md
- [ ] T002 Record Android compile/minimum SDK values and ATAK compile/minimum-runtime evidence in research.md
- [ ] T003 [P] Confirm the applicable Spotless, lint, unit-test, and assemble commands

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

Examples of foundational tasks (adjust based on your project):

- [ ] T004 Add failing JVM/contract tests for shared behavioural rules in app/src/test/java/com/atakmap/android/twcoord/
- [ ] T005 [P] Pin public ATAK API signatures and minimum-runtime source anchors in specs/[###-feature-name]/research.md
- [ ] T006 Define host-entry safety, lifecycle ownership, and unregister/dispose seams in specs/[###-feature-name]/contracts/
- [ ] T007 Add shared models/adapters required by all user stories in app/src/main/java/com/atakmap/android/twcoord/
- [ ] T008 Define logging, validation, safe fallback, and re-entrancy boundaries in the feature contract
- [ ] T009 Add authoritative coordinate vectors or dataset provenance fixtures when geospatial behaviour is in scope

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 1 (REQUIRED for behaviour changes) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T010 [P] [US1] Add a failing unit/contract test for [behaviour] in app/src/test/java/com/atakmap/android/twcoord/[package]/[Test].java
- [ ] T011 [P] [US1] Define the ATAK 5.5/current-line device scenario for [user journey] in specs/[###-feature-name]/quickstart.md

### Implementation for User Story 1

- [ ] T012 [P] [US1] Create [Entity1] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Entity1].java
- [ ] T013 [P] [US1] Create [Adapter] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Adapter].java
- [ ] T014 [US1] Implement [service/behaviour] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Class].java (depends on T012, T013)
- [ ] T015 [US1] Integrate [ATAK/UI seam] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Class].java
- [ ] T016 [US1] Add boundary validation, safe failure, and structured logging in app/src/main/java/com/atakmap/android/twcoord/[package]/[Class].java
- [ ] T017 [US1] Run the US1 JVM tests and the applicable quickstart scenario

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 2 (REQUIRED for behaviour changes) ⚠️

- [ ] T018 [P] [US2] Add a failing unit/contract test for [behaviour] in app/src/test/java/com/atakmap/android/twcoord/[package]/[Test].java
- [ ] T019 [P] [US2] Add the corresponding device acceptance scenario to specs/[###-feature-name]/quickstart.md

### Implementation for User Story 2

- [ ] T020 [P] [US2] Create [Entity] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Entity].java
- [ ] T021 [US2] Implement [Service] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Service].java
- [ ] T022 [US2] Implement [ATAK/UI seam] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Class].java
- [ ] T023 [US2] Integrate with User Story 1 through the documented contract

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 3 (REQUIRED for behaviour changes) ⚠️

- [ ] T024 [P] [US3] Add a failing unit/contract test for [behaviour] in app/src/test/java/com/atakmap/android/twcoord/[package]/[Test].java
- [ ] T025 [P] [US3] Add the corresponding device acceptance scenario to specs/[###-feature-name]/quickstart.md

### Implementation for User Story 3

- [ ] T026 [P] [US3] Create [Entity] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Entity].java
- [ ] T027 [US3] Implement [Service] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Service].java
- [ ] T028 [US3] Implement [ATAK/UI seam] in app/src/main/java/com/atakmap/android/twcoord/[package]/[Class].java

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] Update English and localised user/UI documentation in docs/
- [ ] TXXX Record an ADR in docs/adr/ only if the implementation made an architecturally significant decision
- [ ] TXXX Run `./gradlew :app:spotlessCheck :app:lint :app:testCivDebugUnitTest :app:assembleCivDebug`
- [ ] TXXX Run the ATAK 5.5/current-line compatibility scenarios from quickstart.md
- [ ] TXXX Verify plugin-owned latency, memory, offline, and main-thread-I/O budgets from plan.md
- [ ] TXXX Run `/speckit-converge` and append only actionable remaining work

---

## Phase N+1: Release Readiness

**Purpose**: Separate implementation convergence from TPP and public-release
evidence. Include only gates that are applicable to this feature.

- [ ] TXXX [RELEASE-GATE] Record the exact candidate commit and committed `PLUGIN_VERSION`
- [ ] TXXX [RELEASE-GATE] Complete the ATAK minimum/current-runtime device matrix or record an explicitly narrowed compatibility claim
- [ ] TXXX [RELEASE-GATE] Complete the plan's device-only performance and lifecycle evidence
- [ ] TXXX [RELEASE-GATE] Confirm English/localised docs, changelog, and screenshots match the candidate
- [ ] TXXX [RELEASE-GATE] Run the project release-readiness check before TPP upload and again before public release

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Behaviour tests MUST be written and observed failing before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch disjoint tests for User Story 1 together:
Task: "Unit test for [conversion/parser] in app/src/test/.../[Test].java"
Task: "Contract test for [ATAK adapter] in app/src/test/.../[ContractTest].java"

# Launch all models for User Story 1 together:
Task: "Create [Entity1] in app/src/main/java/.../[Entity1].java"
Task: "Create [Adapter] in app/src/main/java/.../[Adapter].java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify behaviour tests fail before implementing and pass afterward
- Keep device-only checks explicitly incomplete until executed on the named ATAK line
- Mark public-release blockers with `[RELEASE-GATE]`; never infer completion from build or TPP success
- Treat `/speckit-analyze` as read-only and use `/speckit-converge` after implementation
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
