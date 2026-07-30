# Tasks: Native Taiwan Input UX

**Input**: Design documents from `/specs/014-native-entry-input-ux/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md`

**Tests**: Every behavior change follows Red → Green → Refactor. Device-only
keyboard, ATAK host, accessibility, performance, and minimum-runtime claims
remain explicit `[RELEASE-GATE]` tasks.

**Organization**: Tasks are grouped by user story so each story has an
independent goal and verification checkpoint. Shared regression fixtures live
in the foundational phase.

## Format: `[ID] [P?] [Story?] [RELEASE-GATE?] Description`

- **[P]**: Can run in parallel because it uses different files and has no
  dependency on another incomplete task.
- **[Story]**: Maps the task to a user story from `spec.md`.
- **[RELEASE-GATE]**: Requires device, candidate, signer, or other evidence
  that automation cannot infer.

## Path Conventions

- **Production Java**: `app/src/main/java/com/atakmap/android/twcoord/`
- **Android resources**: `app/src/main/res/`
- **JVM tests**: `app/src/test/java/com/atakmap/android/twcoord/`
- **Feature artifacts**: `specs/014-native-entry-input-ux/`
- **Architecture/UI docs**: `docs/adr/`, `docs/ui/`, and `docs/reference/`

---

## Phase 1: Setup (Implementation Baseline)

**Purpose**: Establish a reproducible baseline without overwriting the current
uncommitted Feature 014 planning artifacts.

- [X] T001 Record the starting branch, commit, dirty-worktree scope, `PLUGIN_VERSION`, Java version, and Gradle version in `specs/014-native-entry-input-ux/quickstart.md`
- [X] T002 Reproduce the ATAK-CIV 5.7.0.9 `main.jar` SHA-256 and `javap -public` signatures for `CoordinateEntryPane` and `CoordinateEntryCapability`, then record the result and the unchanged ATAK 5.5 source anchor in `specs/014-native-entry-input-ux/quickstart.md`
- [X] T003 Run the pre-change `:app:spotlessCheck`, focused native-entry/Taipower JVM suites, `:app:lint`, and `:app:assembleCivDebug`, then record exact baseline results and pre-existing warnings in `specs/014-native-entry-input-ux/quickstart.md`

---

## Phase 2: Foundational (Shared Regression Locks)

**Purpose**: Add shared fixtures and regression guards that all four stories
can rely on without changing production behavior.

**Critical**: Complete this phase before implementing any user story.

- [X] T004 [P] Add Feature 014 raw variants, 9/11-character fixtures, provenance vectors, encoder-wrap vectors, and invalid-letter sets in `app/src/test/java/com/atakmap/android/twcoord/coord/Feature014TaipowerFixtures.java`
- [X] T005 [P] Add passing baseline regressions for the single outer scroll owner, unchanged TWD97/TWD67/Address behavior, the historical active-only Auto Fill/Clear contract later superseded for non-null Auto Fill by T074-T077, host confirmation ownership, and absence of new Activity/network/permission seams in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/NativeEntryFeature014RegressionTest.java`
- [X] T006 Run the T004-T005 foundation suites and record their pre-feature passing baseline in `specs/014-native-entry-input-ux/quickstart.md`

**Checkpoint**: Shared fixtures and existing host behavior are locked; user
story Red tests may begin.

---

## Phase 3: User Story 1 - Type Without Leaving Go To (Priority: P1) — MVP

**Goal**: Every editable Taiwan field requests inline keyboard presentation,
uses deterministic local focus actions, and never bypasses ATAK confirmation.

**Independent Test**: Focus each existing editable Taipower, TWD97, TWD67, and
Address field in portrait and landscape. The default keyboard appears without
replacing Go To; Next/Done/Search stays inside the host flow; read-only and
disposed panes start no editable session.

### Tests for User Story 1

- [X] T007 [P] [US1] Add failing XML/view contract tests for single-line editors, `NO_FULLSCREEN`, `NO_EXTRACT_UI`, coordinate-only `FORCE_ASCII`, the current-field Next/Done/Search matrix, and plugin-owned `nextFocus*` IDs in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanInlineImeContractTest.java`
- [X] T008 [P] [US1] Add failing behavior tests for deterministic Next, Done/Search dismissal without host submission, one-shot `IME_NULL`/Enter, render non-interference, read-only focus rejection, and post-dispose callback suppression in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneSafetyTest.java`
- [X] T009 [US1] Run the T007-T008 tests, confirm the expected pre-implementation failures, and record the Red evidence in `specs/014-native-entry-input-ux/quickstart.md`

### Implementation for User Story 1

- [X] T010 [US1] Apply the inline IME flags, current TWD/Address action matrix, and plugin-owned forward/down focus IDs to existing editors in `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`
- [X] T011 [US1] Implement guarded editor-action handling, visible/enabled next-focus resolution, keyboard dismissal, and physical Enter de-duplication without invoking ATAK confirmation in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [X] T012 [US1] Extend render and dispose guards so programmatic updates do not steal focus and late editor/focus callbacks are inert in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [X] T013 [US1] Run the US1 focused suites plus `NativeEntryFeature014RegressionTest`, refactor with tests green, and record Red → Green → Refactor results in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T014 [RELEASE-GATE] [US1] Execute the pre-US2 Journey A matrix on the Galaxy Tab S10+ with ATAK-CIV 5.7.0.9/default IME for every currently existing raw Taipower, TWD, and Address field, both orientations, 20 focus attempts per field, host-control reachability, read-only behavior, and the documented nearest-rank 500 ms p95 protocol; record device/IME evidence in `specs/014-native-entry-input-ux/quickstart.md`

**Checkpoint**: The pre-US2 single-field Taiwan pane remains inside ATAK Go To
for all supported default-keyboard focus journeys. Full SC-001 evidence waits
until the four split Taipower fields exist and is completed by T028.

---

## Phase 4: User Story 2 - Switch Taipower Entry Layout (Priority: P1)

**Goal**: Add exactly two persisted Taipower layouts over one lossless draft:
one raw field and four guided fields supporting 9-character 10 m and
11-character 1 m codes.

**Independent Test**: Enter `H7509 DB40` and `H7509 DB4016` in each layout,
switch both directions across complete and representable partial drafts, and
confirm identical content, precision, validation, resolved point, and host
result. Missing/corrupt preference values start in single mode.

### Tests for User Story 2

- [X] T015 [P] [US2] Add failing tests for exact raw preservation, safe prefix projection, split-gap refusal, revision semantics, 0/1/3 incomplete tails, 2-digit 10 m, 4-digit 1 m, extra-character rejection, and 100 lossless round trips in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaipowerEntryDraftTest.java`
- [X] T016 [P] [US2] Add failing controller tests for silent mode switches, one notification per accepted edit, identical single/split resolution, activation/Auto Fill canonical 11-character staging, active Clear, read-only projection, dispose safety, and the outer-island case where Taipower is unavailable while staged TWD97/TWD67/Address drafts remain unchanged and usable in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java`
- [X] T017 [P] [US2] Add failing preference tests for single-mode default, saved split-mode restore, blank/unknown fallback, reload, read-only selection persistence, and independence from ATAK MGRS/native-tab keys in `app/src/test/java/com/atakmap/android/twcoord/prefs/PreferenceStoreNativeEntryTest.java`
- [X] T018 [P] [US2] Add failing pane/resource contract tests for exactly two modes, mutually exclusive raw/split containers, 1/4/2/4 field limits, uppercase ASCII character-class filters that preserve range-invalid A-Z attempts, fixed-group auto-advance, two-to-four final-digit continuation, focused mode handoff, localized projection and position-specific A-H/A-E errors, and EN/zh-TW/JA key parity in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`
- [X] T019 [US2] Run the T015-T018 tests, confirm the expected pre-implementation failures, and record the Red evidence in `specs/014-native-entry-input-ux/quickstart.md`

### Implementation for User Story 2

- [X] T020 [P] [US2] Add the stable `SINGLE_FIELD`/`SPLIT_FIELDS` enum and safe deserialization fallback in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaipowerInputMode.java`
- [X] T021 [P] [US2] Implement the revisioned lossless raw/split draft, split parts, precision derivation, projection refusal, position-specific `TaipowerValidationDetail`, validation snapshot, and resolved-point invariants in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaipowerEntryDraft.java`
- [X] T022 [US2] Add the plugin-owned Taipower mode key and typed get/set methods without touching ATAK MGRS or retired preferences in `app/src/main/java/com/atakmap/android/twcoord/prefs/PreferenceStore.java`
- [X] T023 [US2] Replace the controller's single Taipower string with `TaipowerEntryDraft`, expose mode/part edit operations, preserve notification semantics, stage/clear both projections atomically, and keep TWD97/TWD67/Address drafts usable when a host point is outside Taipower coverage in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`
- [X] T024 [P] [US2] Add aligned mode labels, four field labels/hints, projection/incomplete feedback, separate east-west A-H and north-south A-E correction messages, and accessibility strings in `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml`
- [X] T025 [US2] Add the two-mode selector, raw container, four-part split container, 1/4/2/4 input constraints, inline IME actions, and explicit focus order using the T024 resource keys in `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`
- [X] T026 [US2] Bind persisted mode selection, lossless controller projections, uppercase ASCII character-class/max-length filters that retain range-invalid A-Z attempts for draft validation, position-specific A-H/A-E feedback, render guards, fixed-group auto-advance, focused mode handoff, read-only projection, and listener disposal in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [X] T027 [US2] Run all US2 focused suites plus US1/foundation regressions, refactor with tests green, and record Red → Green → Refactor results in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T028 [RELEASE-GATE] [US2] Repeat Journey A for all four new split Taipower fields to complete the full SC-001 matrix, then execute Journey B on ATAK-CIV 5.7.0.9 for both canonical precision fixtures, 100 mode round trips, incomplete/unprojectable drafts, persistence, activation, all-page main-/outer-island Auto Fill, active-page Clear, read-only, locale replacement, reload, and the documented nearest-rank 100 ms p95 protocol; record evidence in `specs/014-native-entry-input-ux/quickstart.md`

**Checkpoint**: Single and split Taipower entry are independently usable views
of one draft and switching layouts alone never changes ATAK state.

---

## Phase 5: User Story 3 - Prevent Invalid Taipower Subgrid Letters (Priority: P1)

**Goal**: Make A-H east-west and A-E north-south the authoritative Taipower
100 m domain rule in the parser, value object, encoder, both UI layouts, and
current documentation.

**Independent Test**: Accept every A-H/A-E boundary and reject I/J east-west
and F-J north-south in both layouts without exposing a point or moving the map;
all canonical golden vectors retain their precision and result.

### Tests for User Story 3

- [X] T029 [P] [US3] Add failing exhaustive parser tests for A-H east-west, A-E north-south, I/J and F-J `BAD_LETTER` rejection, AA/HE at 9/11 characters, supported paste normalization, and unchanged golden vectors in `app/src/test/java/com/atakmap/android/twcoord/coord/input/TaipowerParserTest.java`
- [X] T030 [P] [US3] Add failing constructor invariant tests accepting A/A and H/E while rejecting I/J east-west and F-J north-south in `app/src/test/java/com/atakmap/android/twcoord/coord/TaipowerCodeTest.java`
- [X] T031 [P] [US3] Add failing provenance and encoder-wrap tests for `G8150 HD7812`, `W9999 HE9999`, `H1010 AA0000`, `H1010 HE9999`, `H1110 AA0000`, and `H1011 AA0000` in `app/src/test/java/com/atakmap/android/twcoord/coord/TaipowerGridTest.java`
- [X] T032 [P] [US3] Add failing controller/pane regressions proving invalid complete letters remain visible and unresolved in both layouts, cannot notify a valid point, render the localized east-west A-H or north-south A-E message, and update output-shape assertions to `[A-H][A-E]` in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java`, `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`, and `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryFormatterTest.java`
- [X] T033 [US3] Run the T029-T032 tests, confirm the expected pre-implementation failures, and record the Red evidence in `specs/014-native-entry-input-ux/quickstart.md`

### Implementation for User Story 3

- [X] T034 [P] [US3] Add ADR-0028 with Taipower/OSGeo/Jidanni/Sunriver provenance, intentional noncanonical-alias rejection, alternatives, and partial supersession of ADR-0001, then update the supersession map in `docs/adr/0028-correct-taipower-subgrid-letter-ranges.md` and `docs/adr/README.md`
- [X] T035 [P] [US3] Enforce A-H/A-E at the authoritative parse boundary while preserving existing normalization and `BAD_LETTER` behavior in `app/src/main/java/com/atakmap/android/twcoord/coord/input/TaipowerParser.java`
- [X] T036 [P] [US3] Enforce A-H/A-E constructor invariants without changing canonical formatting in `app/src/main/java/com/atakmap/android/twcoord/coord/TaipowerCode.java`
- [X] T037 [P] [US3] Replace misleading A-J comments/clamps with explicit 0..7/0..4 encoder invariants and preserve existing region/precision arithmetic in `app/src/main/java/com/atakmap/android/twcoord/coord/TaipowerGrid.java`
- [X] T038 [US3] Run all US3 focused suites plus `CoordinateParserRoundTripTest` and US2 UI regressions, refactor with tests green, and record Red → Green → Refactor results in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T039 [RELEASE-GATE] [US3] Exercise every valid and invalid 100 m letter boundary through both layouts on ATAK-CIV 5.7.0.9, verify invalid attempts remain visible with the correct localized A-H/A-E message, verify no invalid point/map movement/confirmation and unchanged 9/11 accuracy, and record evidence in `specs/014-native-entry-input-ux/quickstart.md`

**Checkpoint**: The complete Taipower input stack has one provenance-backed
A-H/A-E rule and canonical coordinates do not regress.

---

## Phase 6: User Story 4 - Use Compact Reachable Selectors (Priority: P2)

**Goal**: Render 36 dp system/zone tracks inside real 48 dp native radio
targets while preserving selected/read-only state, accessibility, localization,
and host-control reachability.

**Independent Test**: At supported pane widths, orientations, locales, and font
scales, measure 36 dp track/fill geometry, tap both transparent bands, confirm
48 dp non-overlapping target bounds, and observe unclipped labels and
checked/disabled semantics.

### Tests for User Story 4

- [X] T040 [P] [US4] Add failing resource/view geometry tests for the named 48/36/6 dp relationship, zero vertical padding, 36 dp track/checked-fill bounds, 48 by 48 dp children, smallest-pane width, ordering, and no overlap in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanSelectorPresentationTest.java`
- [X] T041 [P] [US4] Add failing interaction/accessibility regressions for top/bottom transparent-band taps, exactly-once human callbacks, silent programmatic checks, disabled-and-checked distinction, EN/zh-TW/JA names, 1.0/2.0 font scales, one-line unclipped labels, one scroll owner, and unchanged Address targets in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`
- [X] T042 [US4] Run the T040-T041 tests, confirm the expected pre-implementation failures, and record the Red evidence in `specs/014-native-entry-input-ux/quickstart.md`

### Implementation for User Story 4

- [X] T043 [US4] Add named selector touch-height, visual-height, and vertical-inset dimensions while retaining existing normal/large text dimensions in `app/src/main/res/values/dimens.xml` and `app/src/main/res/values-large/dimens.xml`
- [X] T044 [P] [US4] Center the selector track at 36 dp with 6 dp top/bottom drawable insets in `app/src/main/res/drawable/native_entry_segment_track.xml`
- [X] T045 [P] [US4] Center every option state at 36 dp and add checked-plus-disabled precedence and readable text color in `app/src/main/res/drawable/native_entry_segment_option.xml` and `app/src/main/res/color/native_entry_segment_text.xml`
- [X] T046 [US4] Apply the named 48 dp bounds, zero vertical padding, localized system/zone accessibility context, and unchanged Address-control geometry in `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml`
- [X] T047 [US4] Run all US4 focused suites plus US1-US3/foundation regressions, refactor with tests green, and record Red → Green → Refactor results in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T048 [RELEASE-GATE] [US4] Execute Journey C on ATAK-CIV 5.7.0.9 in both orientations, EN/zh-TW/JA, font scales 1.0/2.0, and TalkBack/Switch Access; measure 36 dp visuals, 48 dp bounds, transparent-band taps, label clipping, read-only selection, and host reachability in `specs/014-native-entry-input-ux/quickstart.md`

**Checkpoint**: Selector visuals are slimmer without reducing actual target
size, accessibility semantics, or available host actions.

---

## Phase 7: Polish & Cross-Cutting Documentation

**Purpose**: Synchronize current documentation, screenshots, localization, and
the complete automated quality gates after all desired stories are green.

- [X] T049 [P] Correct the current A-H/A-E grammar, 40-cell provenance, precision examples, and ADR-0028 reference in `docs/reference/coordinate-systems.md`
- [X] T050 [P] Document inline IME behavior, single/split Taipower layouts, focus/read-only/lifecycle states, and the 48 dp target containing a 36 dp selector track in `docs/ui/native-taiwan-coordinate-entry.md`
- [X] T051 [P] Update the concise native Go To Taiwan operator workflow, 9/11-character examples, split-field behavior, and validation guidance in `docs/user-guide.md`
- [X] T052 [P] Apply the same operator workflow updates in Taiwan Traditional Chinese with Taiwan-localized terminology in `docs/user-guide_zh.md`
- [X] T053 [P] Update feature/document navigation in `docs/README.md`, and change root `README.md` only where its concise Go To Taiwan summary or docs index is affected
- [X] T054 [P] Record the inline-input, dual-layout, A-H/A-E correctness, selector, compatibility, and migration notes in `CHANGELOG.md`
- [X] T055 [RELEASE-GATE] Capture the frozen current-device UI for `docs/images/23a-native-address-full.png`, `docs/images/23b-native-address-structured.png`, `docs/images/26a-native-taipower-single.png`, and `docs/images/26b-native-taipower-split.png` with no personal/device-owner content, then update references in `docs/images/README.md`, `docs/user-guide.md`, and `docs/user-guide_zh.md`
- [X] T056 Scrub EXIF/XMP, validate rendering/dimensions/references, and verify Git LFS pointer/object coverage for T055 by running `scripts/scrub-doc-images.py` and `scripts/check-doc-images.py`, then record results in `docs/images/README.md`
- [X] T057 Run `:app:spotlessApply`, `:app:spotlessCheck`, `:app:testCivDebugUnitTest`, `:app:lint`, `:app:assembleCivDebug`, locale/resource parity checks, `python scripts/check-doc-images.py`, and `git diff --check`, then record exact results in `specs/014-native-entry-input-ux/quickstart.md`
- [X] T058 Audit the reviewed diff for new permissions/dependencies/network/telemetry, non-public ATAK APIs, legacy MGRS preference coupling, stale current A-J claims, broken local links, workstation identifiers, and binary metadata, then record the disposition in `specs/014-native-entry-input-ux/quickstart.md`
- [X] T059 Run `/speckit-converge` against Feature 014 and append only evidence-backed unfinished implementation or release work to `specs/014-native-entry-input-ux/tasks.md`

---

## Phase 8: Release Readiness

**Purpose**: Keep implementation completion separate from device compatibility,
candidate identity, signer/provenance, TPP, and public-release claims.

- [ ] T060 [RELEASE-GATE] Freeze and commit the intended `PLUGIN_VERSION` in `app/build.gradle`, then record the exact candidate commit, version, variant, and clean-worktree state in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T061 [RELEASE-GATE] Run `python scripts/check-release-readiness.py --phase tpp`, build the exact frozen candidate with `.\gradlew.bat :app:clean :app:assembleCivRelease`, generate the source archive with `python scripts/build-tpp-source-zip.py --verify-build`, and record the candidate/source archive/local APK SHA-256 hashes without using the root `clean` task in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T062 [RELEASE-GATE] After a separately user-authorized TPP submission has returned, run `python scripts/stage-tpp-release.py <TPP_BUNDLE> --source-zip build/atak_tw_coord_plugin-source-tpp-v<VERSION>.zip`, stage only curated durable outputs under `dist/release-v<VERSION>/` outside Gradle-owned `build/`, and record the source/APK SHA-256 hashes plus expected signer fingerprint without committing the raw response filename or path in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T063 [RELEASE-GATE] Repeat Journeys A-D on the frozen candidate with the exact ATAK-CIV 5.7.0.9 runtime/default IME, including both orientations, locales, font scales, every raw and split field, 20 reload cycles, read-only Convert Coordinate, host controls, and safe IME degradation; record evidence in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T064 [RELEASE-GATE] Repeat Journeys A-D on an exact ATAK-CIV 5.5.x runtime; if that evidence cannot be executed, leave the gate open unless the user explicitly accepts a narrowed public claim, then record the accepted scope and omitted evidence in release notes without asserting untested 5.5 behavior in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T065 [RELEASE-GATE] Use the documented Perfetto/screen-recording protocol with 20 samples and nearest-rank p95 to measure focus feedback at 500 ms and mode/validation updates at 100 ms, then use baseline/candidate `dumpsys meminfo`, heap dumps, and 20 lifecycle cycles to prove no more than 256 KiB retained Feature 014 state per live pane, zero disposed-pane retention, explain any heap/PSS regression above 10% with an ADR for accepted architectural trade-offs, and verify offline/no-main-thread-I/O behavior in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T066 [RELEASE-GATE] Confirm EN/zh-TW/JA docs, changelog, ADR supersession, screenshots, image metadata/LFS, TalkBack labels, user-visible behavior, and any explicitly accepted narrowed-claim release note all match the frozen candidate in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T067 [RELEASE-GATE] Run `python scripts/check-release-readiness.py --phase public` against the staged candidate before tag creation or public release, verifying exact source ref, expected signer fingerprint, durable artifact provenance, release-note dispositions, and every remaining release gate in `specs/014-native-entry-input-ux/quickstart.md`
- [ ] T068 [RELEASE-GATE] After T067 passes and the user explicitly authorizes the action, create and verify a signed annotated immutable release tag that identifies the exact frozen candidate, version, staged artifact hashes, and signer; keep tag push and publication as separately authorized actions and record the tag evidence in `specs/014-native-entry-input-ux/quickstart.md`

---

## Phase 9: Requested Taipower Mode-Action Refinement

**Purpose**: Match the compact Address action pattern after current-device
review without changing draft, persistence, validation, or host ownership.

- [X] T069 Add a failing pane contract for one far-right Taipower mode action, Address-aligned 8:2 geometry, alternate-layout text, guided field behavior, projection refusal, read-only projection, and dispose safety in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`
- [X] T070 Replace the full-width Taipower segmented mode row with one borderless 48 dp action in an Address-aligned right column in `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`
- [X] T071 Bind toggle, alternate-mode text, focus handoff, projection failure, read-only behavior, and disposal to the single action in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [X] T072 Synchronize FR-004, the Taipower contract, plan, UI reference, English/Taiwan Traditional Chinese guides, and changelog with the requested right-side action pattern
- [X] T073 Run focused/full JVM tests, Spotless, lint, Civ Debug assembly, `git diff --check`, install to the current ATAK-CIV 5.7.0.9 device, restart ATAK, and record the bounded smoke result in `specs/014-native-entry-input-ux/quickstart.md`

---

## Phase 10: Fill Every Taiwan Page from One Auto Fill

**Purpose**: Make one non-null ATAK Auto Fill refresh Taipower, TWD97, TWD67,
and Address from the same host point while Clear remains active-page-only.

- [X] T074 Add failing controller, regression, and pane contract tests for all-page non-null Auto Fill, active-page null Clear, selected-page retention, exact Address no-snap WGS84, and zero human-change callbacks in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java`, `app/src/test/java/com/atakmap/android/twcoord/nativeentry/NativeEntryFeature014RegressionTest.java`, and `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`
- [X] T075 Reuse atomic all-coordinate staging for non-null Auto Fill while preserving the selected page, Taipower mode, and explicit TWD zone on unrepresentable conversion in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`
- [X] T076 Start Address reverse lookup from the same exact non-null Auto Fill WGS84 while preserving active-page-only null Clear in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [X] T077 Record ADR-0029 and synchronize the spec, plan, research, data model, contract, UI reference, English/Taiwan Traditional Chinese guides, address references, ADR index, and changelog
- [X] T078 Run focused/full JVM tests, Spotless, lint, Civ Debug assembly, documentation checks, sensitive-path audit, and `git diff --check`, then record results in `specs/014-native-entry-input-ux/quickstart.md`
- [X] T079 Install the verified Civ Debug APK to the current ATAK-CIV 5.7.0.9 device, restart ATAK, and confirm clean plugin initialization without fatal, native-entry, version-skew, or plugin-load errors
- [ ] T080 [RELEASE-GATE] On the current ATAK-CIV 5.7.0.9 device, interactively verify from every starting page that one main-/outer-island Auto Fill prepares all four pages, preserves the selected page and exact Address WGS84, and that Clear affects only the active page; repeat on exact ATAK 5.5.x under T064

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Starts immediately; preserves the existing dirty
  planning scope.
- **Foundational (Phase 2)**: Depends on Setup and blocks production changes.
- **US1 (Phase 3)**: Depends on Foundational and establishes the reusable
  editor-action/focus behavior.
- **US2 (Phase 4)**: Draft/controller/preference work can start after
  Foundational; pane focus integration in T026 depends on T011-T012.
- **US3 (Phase 5)**: Parser/value/encoder work can start after Foundational;
  both-layout UI verification in T032/T038/T039 depends on US2.
- **US4 (Phase 6)**: Semantically independent after Foundational, but should be
  applied after US2 to avoid conflicting edits to the shared layout, strings,
  and pane contract test.
- **Polish (Phase 7)**: Depends on all selected stories and a frozen UI/string
  shape.
- **Release Readiness (Phase 8)**: Depends on automated gates, synchronized
  docs/screenshots, and an explicit frozen candidate. T061 depends on T060;
  T062 requires the separately authorized TPP response for the exact T061
  source archive; T063-T066 must match that frozen candidate; T067 verifies
  public readiness after staging and all evidence; T068 additionally requires
  explicit user authorization.
- **Requested refinement (Phase 9)**: T069-T072 are sequential because they
  share the pane layout, Java adapter, contract tests, and documentation.
  T073 must pass before returning to Phase 8 release work.
- **All-page Auto Fill (Phase 10)**: T074 establishes Red before T075-T076.
  T077 follows the accepted behavior and ADR decision. T078 gates T079-T080;
  the exact ATAK 5.5 runtime check remains open under Phase 8.

### User Story Dependency Graph

```text
Setup → Foundation
             ├─→ US1 inline input ─────┐
             ├─→ US2 draft/domain UI ──┼─→ Polish → Release Readiness
             ├─→ US3 domain rules ─────┤
             └─→ US4 selector tests ───┘

US2 pane focus integration requires US1 focus helpers.
US3 both-layout integration requires the US2 split layout.
US4 production resource edits are sequenced after US2 to avoid file conflicts.
```

### Within Each User Story

1. Author behavior tests and observe the intended Red result.
2. Implement value/resource/controller behavior in dependency order.
3. Run focused Green tests and all earlier regression suites.
4. Refactor without changing expectations.
5. Keep physical-device checks open until evidence is collected.

---

## Parallel Opportunities

### User Story 1

```text
T007: Inline XML/view contract in TaiwanInlineImeContractTest.java
T008: Action/lifecycle behavior in TaiwanCoordinateEntryPaneSafetyTest.java
```

### User Story 2

```text
Test wave:
T015: TaipowerEntryDraftTest.java
T016: TaiwanEntryControllerTest.java
T017: PreferenceStoreNativeEntryTest.java
T018: TaiwanCoordinateEntryPaneContractTest.java

Implementation wave after Red:
T020: TaipowerInputMode.java
T021: TaipowerEntryDraft.java
T024: Three locale string files
```

### User Story 3

```text
Test wave:
T029: TaipowerParserTest.java
T030: TaipowerCodeTest.java
T031: TaipowerGridTest.java
T032: Native-entry/formatter contract tests

Implementation wave after Red:
T034: ADR-0028 and ADR index
T035: TaipowerParser.java
T036: TaipowerCode.java
T037: TaipowerGrid.java
```

### User Story 4

```text
T040: Selector geometry resource/view tests
T041: Selector interaction/accessibility regressions

After T043 defines dimensions:
T044: Track drawable
T045: Option drawable and text colors
```

### Documentation

```text
T049: Coordinate reference
T050: UI contract
T051: English user guide
T052: Traditional Chinese user guide
T053: Documentation indexes
T054: Changelog
```

---

## Implementation Strategy

### MVP First

1. Complete Setup and Foundational phases.
2. Complete US1 only.
3. Run T013 and the current-device T014 release gate.
4. Demonstrate that all pre-US2 Taiwan editors remain inside ATAK Go To.

This is the smallest independently valuable scope because it directly fixes
the full-screen editor problem without requiring the new Taipower layout. It is
not the final SC-001 matrix; T028 adds the four split Taipower fields.

### Incremental Delivery

1. **US1**: Inline keyboard and deterministic host-safe focus.
2. **US2**: Lossless single/split Taipower draft and persistence.
3. **US3**: Provenance-backed A-H/A-E correctness boundary.
4. **US4**: Compact 36 dp visuals with 48 dp targets.
5. **Polish**: Documentation, screenshots, full quality gates, convergence.
6. **Release**: Frozen-candidate device, performance, signer, and provenance
   evidence.

### Parallel Team Strategy

After Foundation:

- One contributor may implement US1 focus behavior.
- A second may implement US2 draft/controller/preference tests and value types.
- A third may implement US3 parser/value/encoder tests and ADR-0028.
- A fourth may prepare US4 selector tests without changing the shared layout
  until US2 integration lands.

Coordinate edits to
`app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`,
`app/src/main/res/values*/strings.xml`,
`TaiwanCoordinateEntryPane.java`, and
`TaiwanCoordinateEntryPaneContractTest.java` must remain sequential.

---

## Notes

- Every task includes an exact repository path and is intended to be executable
  without additional product clarification.
- `[P]` marks only disjoint files with no incomplete dependency.
- Device, keyboard, TalkBack, performance, signer, and exact ATAK 5.5 evidence
  remain unchecked until executed.
- A successful Gradle or TPP build does not close any `[RELEASE-GATE]`.
- Preserve unrelated dirty-worktree changes and never use blanket staging.
- Commit after each reviewed task or coherent Red/Green/Refactor group.
- Use `/speckit-converge` after implementation; `/speckit-analyze` remains
  read-only.
