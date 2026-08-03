# Tasks: Compact Structured Address Layout

**Input**: Design documents from `/specs/015-compact-address-layout/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/compact-address-layout-contract.md`, and `quickstart.md`

**Tests**: The layout contract must demonstrate Red before production XML is
changed. Physical ATAK geometry, accessibility, and performance remain
explicit `[RELEASE-GATE]` tasks.

**Organization**: Shared Red tests precede the two user-story phases because
both stories depend on the same XML hierarchy. Each task names the exact file
or validation target.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: May run in parallel because it touches different files and has no
  dependency on unfinished work.
- **[Story]**: Maps to a user story from `spec.md`.
- **[RELEASE-GATE]**: Requires physical-device or publication evidence that
  automation cannot infer.

## Phase 1: Setup

**Purpose**: Capture the exact starting state and compatibility boundary.

- [X] T001 Record branch, commit, dirty-worktree scope, plugin version, Java version, Gradle version, and the pre-change focused/full quality baseline in `specs/015-compact-address-layout/quickstart.md`
- [X] T002 Confirm the initial compact-layout scope adds no ATAK API seam and record the inherited ADR-0022 through ADR-0024 compile/minimum-runtime evidence in `specs/015-compact-address-layout/quickstart.md`

---

## Phase 2: Foundational — Shared Red Contract

**Purpose**: Lock the approved two-row geometry, accessibility order, and
unchanged native Address behavior before changing production resources.

**Critical**: Complete this phase before implementing either story.

- [X] T003 Add failing two-row hierarchy, 1:1 field-group weight, row-major order, compact-height, one-scroll-owner, 8:2 action-column, 48 dp action, font-scale, selector-state, and road-to-tail editor contract assertions in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanAddressLayoutTest.java`
- [X] T004 Run the focused `TaiwanAddressLayoutTest`, confirm failure against the current four-row hierarchy and 216 dp measurement, and record the exact Red evidence in `specs/015-compact-address-layout/quickstart.md`

**Checkpoint**: The existing layout fails only the intended Feature 015
contract; production resources may now change.

---

## Phase 3: User Story 1 — Enter a Structured Address in Two Compact Rows (Priority: P1) — MVP

**Goal**: Render county/district and road/tail as two equal-column rows while
preserving the right action column and ATAK host reachability.

**Independent Test**: Switch native Taiwan Address to structured mode and
verify exactly two rows, two equal groups per row, unchanged field IDs/states,
and shrink-wrapped height below the existing cap at the reference width.

- [X] T005 [US1] Add locality and street row containers, move the existing county/district and road/tail field groups into 1:1 weighted pairs, and preserve field IDs, internal 3:7 geometry, 8:2 outer geometry, actions, labels, hints, and editor attributes in `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`
- [X] T006 [US1] Run `TaiwanAddressLayoutTest` plus `TaiwanCoordinateEntryPaneContractTest`, `TaiwanInlineImeContractTest`, and `NativeEntryFeature014RegressionTest`; refactor with tests green and record exact Green/refactor evidence in `specs/015-compact-address-layout/quickstart.md`

**Checkpoint**: The approved two-row structured Address presentation is an
independently usable MVP with unchanged Address behavior.

---

## Phase 4: User Story 2 — Preserve Legibility and Interaction States (Priority: P2)

**Goal**: Keep row-major interaction, labels, values, read-only state, locale
resources, and host controls usable across supported presentation states.

**Independent Test**: Exercise the shared contract at normal/large font scales
and existing editable, read-only, missing-data, locale, and lifecycle fixtures.

- [X] T007 [US2] Run the shared compact-layout contract under its normal and large-font configurations together with Address resource parity, read-only, mode-switch, lookup, Auto Fill/Clear, and lifecycle regression suites, then record the result in `specs/015-compact-address-layout/quickstart.md`
- [X] T008 [US2] Update the two-row geometry, 1:1 field grouping, logical order, one-scroll-owner behavior, and unchanged action ownership in `docs/ui/native-taiwan-coordinate-entry.md`
- [ ] T009 [RELEASE-GATE] [US2] On ATAK-CIV 5.7.0.9, execute the portrait/landscape, EN/zh-TW/JA, font-scale 1.0/2.0, editable/read-only, missing-data, TalkBack/Switch Access, 20 mode-change p95, host-reachability, and lifecycle matrix; capture a sanitized replacement structured Address screenshot and record evidence in `specs/015-compact-address-layout/quickstart.md`

**Checkpoint**: Automated presentation and behavior contracts are complete;
real ATAK geometry and accessibility evidence remain explicitly separated.

---

## Phase 5: Version, Documentation, and Quality Gates

**Purpose**: Synchronize `v1.5.1`, document the operator-facing refinement,
run the full repository gates, and preserve release boundaries.

- [X] T010 [P] Set `PLUGIN_VERSION` to `1.5.1` in `app/build.gradle`
- [X] T011 [P] Add the `1.5.1` compact Address entry and compatibility note to `CHANGELOG.md`, update version and two-row structured workflow text in `docs/user-guide.md` and `docs/user-guide_zh.md`, and keep the generated mockup out of release evidence
- [X] T012 Run `:app:spotlessApply`, `:app:spotlessCheck`, `:app:testCivDebugUnitTest`, `:app:lint`, `:app:assembleCivDebug`, `python scripts/check-doc-images.py`, version synchronization checks, and `git diff --check`, then record exact results in `specs/015-compact-address-layout/quickstart.md`
- [X] T013 Audit the initial layout diff for unrelated user attachments, workstation identifiers, image metadata, new permissions/dependencies/network/telemetry, Java production changes, ATAK SDK seams, nested scroll owners, and coordinate/address behavior changes; record the disposition in `specs/015-compact-address-layout/quickstart.md`
- [X] T014 Run `/speckit-converge` against Feature 015 and append only evidence-backed unfinished buildable work to `specs/015-compact-address-layout/tasks.md`
- [ ] T015 [RELEASE-GATE] Repeat the T009 compact Address matrix on exact ATAK-CIV 5.5.x, then complete or explicitly disposition all Feature 015 device, screenshot, signer, and provenance evidence before any TPP upload, tag, or public `v1.5.1` release in `specs/015-compact-address-layout/quickstart.md`

---

## Phase 6: Review Remediation — Selected-Target Lifecycle

**Purpose**: Address review findings without weakening ATAK host-process or
minimum-runtime guarantees.

- [X] T016 Add focused Red tests in `TwCoordMapComponentTargetDismissTest` proving ordinary cleanup failures are contained while `VirtualMachineError` and `ThreadDeath` propagate, and in `AddressSubsystemTest` proving legacy/shared results already queued for UI delivery cannot restore a cleared row
- [X] T017 Narrow selected-target cleanup containment in `TwCoordMapComponent` to ordinary `RuntimeException` so fatal JVM conditions keep their process-level semantics
- [X] T018 Add per-row atomic delivery generations in `AddressSubsystem` and verify the captured generation inside every UI-posted legacy/shared emission
- [X] T019 Capture the pinned ATAK-CIV 5.7.0.9 `AtakBroadcast` `javap -public` evidence, immutable official 5.5.1.1 sender/receiver/lifecycle source anchors, compatibility matrix, and device-smoke boundary in `plan.md`, `research.md`, and `quickstart.md`
- [X] T020 Run Spotless, the full Civ Debug JVM suite, lint, Civ Debug assembly, documentation/image checks, sensitive-path scan, and `git diff --check`; record the final results in `quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup** starts immediately and records the branch without staging local
  attachments.
- **Foundational** depends on Setup and blocks the XML change.
- **US1** depends on the confirmed Red contract.
- **US2** depends on the US1 hierarchy because it validates states on the new
  presentation; its documentation update can follow the frozen geometry.
- **Version and documentation** follow the green implementation. T010 and T011
  may run in parallel; T012-T014 are sequential validation. T009 and T015 stay
  open until their exact physical-device evidence exists.
- **Review remediation** follows the PR review. T016 establishes Red; T017 and
  T018 implement independent host-boundary and queued-delivery fixes; T019
  documents the seam; T020 is the final automated gate. T009 and T015 remain
  release gates after T020.

### User Story Dependency Graph

```text
Setup -> Shared Red Contract -> US1 compact rows -> US2 states/accessibility
                                                -> Version/docs/full gates
                                                -> Review remediation
```

### Within Each User Story

1. Establish the shared failing behavior contract.
2. Implement only the approved hierarchy change.
3. Run focused Green and adjacent regressions.
4. Refactor without changing expectations.
5. Keep physical-device evidence open until actually executed.

## Parallel Opportunities

After T006 freezes the UI behavior:

```text
T010: app/build.gradle version update
T011: changelog and English/Traditional Chinese user documentation
```

Device preparation for T009 may proceed while documentation is updated, but
its checkbox remains open until the exact matrix and sanitized screenshot are
recorded.

## Implementation Strategy

### MVP First

1. Complete T001-T004.
2. Complete T005-T006.
3. Demonstrate the two-row 1:1 layout with the focused tests.

### Incremental Delivery

1. **US1**: Compact hierarchy and preserved actions.
2. **US2**: Legibility, states, accessibility, and UI documentation.
3. **Polish**: `1.5.1` synchronization and complete automated gates.
4. **Review remediation**: Selected-target lifecycle tests, implementation,
   compatibility evidence, and repeated full gates.
5. **Release**: Current/minimum ATAK device evidence, screenshot, signer, and
   provenance under separate authorization.

## Notes

- Preserve `.codex-remote-attachments/` as unrelated untracked user input.
- Do not commit the generated mockup as a physical-device screenshot.
- The initial layout phase changes no production Java. Review remediation
  intentionally changes two Java production classes, adds one public
  `AtakBroadcast` seam, and adds transient per-row generations; it does not add
  a persisted model, permission, dependency, network path, coordinate change,
  or address lookup-result change.
- A clean convergence result is implementation evidence, not public-release
  readiness.
