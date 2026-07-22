# Tasks: Native Taiwan Address Entry

**Input**: Design documents from `specs/013-native-address-entry/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/`, and `quickstart.md`

**Tests**: Every behavioural task below is preceded by a failing JVM,
Robolectric, contract, or instrumentation test. Device-only ATAK window,
lifecycle, compatibility, performance, and offline checks are explicit
`[RELEASE-GATE]` tasks and must remain unchecked until executed.

**Organization**: Setup and foundational phases establish the shared parser,
dataset ownership, and lookup boundaries. Story phases then deliver each user
journey independently. Paths are repository-relative and intentionally name
the production or evidence file affected by each task.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel because it changes disjoint files and has no
  dependency on another incomplete task in the same phase.
- **[Story]**: Maps the task to one user story from `spec.md`.
- **[RELEASE-GATE]**: Requires physical-device, compatibility, performance,
  signer, documentation, or provenance evidence before public release.

---

## Phase 1: Setup and Decision Record

**Purpose**: Freeze the accepted architecture, test corpus, and compatibility
evidence before behavioural implementation begins.

- [X] T001 Create ADR-0026 covering the shared lookup owner, one-Tools-entry information architecture, inert legacy preferences, stale-action handling, no-data native fallback, and supersession of ADR-0009/0020/0021/0023 in `docs/adr/0026-native-address-entry-and-tools-consolidation.md`
- [X] T002 Add ADR-0026 with its supersedes relationships and status to `docs/adr/README.md`
- [X] T003 [P] Add a provenance-recorded corpus of at least 100 normalized, overlapping-locality, proper-numeral, subnumber, floor, ambiguous, and unsupported-tail cases to `app/src/test/resources/fixtures/native_address_entry_corpus.csv`
- [X] T004 [P] Reproduce the pinned 5.7.0.9 `javap -public` signatures, SDK SHA-256, and stable 5.5.1.1–5.5.1.10 source comparison and record sanitized results in `specs/013-native-address-entry/quickstart.md`
- [X] T005 Record the pre-change focused JVM baseline and the Red-Green-Refactor evidence convention for this feature in `specs/013-native-address-entry/quickstart.md`

---

## Phase 2: Foundational Shared Infrastructure

**Purpose**: Extract shared coordinate parsing and make dataset/query ownership
safe before any Address UI or legacy-page removal.

**⚠️ CRITICAL**: All user-story work depends on this phase.

### Tests first

- [ ] T006 [P] Move coordinate parser regression tests to the neutral package and update package expectations without changing assertions in `app/src/test/java/com/atakmap/android/twcoord/coord/input/CoordinateParserRoundTripTest.java`, `app/src/test/java/com/atakmap/android/twcoord/coord/input/TaipowerParserTest.java`, and `app/src/test/java/com/atakmap/android/twcoord/coord/input/TwdTm2ParserTest.java`
- [ ] T007 [P] Add failing read-lease, revision, listener-isolation, replace/remove/close race, and monotonic-close tests in `app/src/test/java/com/atakmap/android/twcoord/address/ActiveDatasetRegistryTest.java`
- [ ] T008 [P] Add failing late-import completion, duplicate-close, and post-close registration rejection tests in `app/src/test/java/com/atakmap/android/twcoord/address/BatchImportCoordinatorTest.java`
- [ ] T009 [P] Add failing lookup-handle cancellation, no-callback-after-close, bounded-queue, availability, and no-data service contract tests in `app/src/test/java/com/atakmap/android/twcoord/address/lookup/AddressLookupServiceContractTest.java`

### Implementation

- [ ] T010 Move `CoordinateInput`, `CoordinateParser`, `ParseResult`, and `TaipowerParser` without behavioural changes to `app/src/main/java/com/atakmap/android/twcoord/coord/input/`
- [ ] T011 Update native entry and remaining callers to use the neutral coordinate parser package in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java` and `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoView.java`
- [ ] T012 Implement immutable leased snapshots, dataset revisions, listener isolation after lock release, and monotonic close in `app/src/main/java/com/atakmap/android/twcoord/address/ActiveDatasetRegistry.java`
- [ ] T013 Implement coordinator close fencing so a late imported facade is closed instead of registered in `app/src/main/java/com/atakmap/android/twcoord/address/BatchImportCoordinator.java`
- [ ] T014 [P] Add immutable availability, dataset identity, lookup identity, request, result, candidate, match-kind, resolution, and handle models under `app/src/main/java/com/atakmap/android/twcoord/address/lookup/`
- [ ] T015 Define the UI-independent asynchronous lookup and completion-dispatch contracts in `app/src/main/java/com/atakmap/android/twcoord/address/lookup/AddressLookupService.java`
- [ ] T016 Implement the closed/no-dataset service and a single bounded worker owner with per-consumer coalescing and native-interactive priority in `app/src/main/java/com/atakmap/android/twcoord/address/lookup/DefaultAddressLookupService.java`
- [ ] T017 Run the relocated coordinate tests and new registry/coordinator/service contract tests through `app/build.gradle`, then record the foundational Red-Green-Refactor result in `specs/013-native-address-entry/quickstart.md`

**Checkpoint**: Shared parsers no longer depend on the legacy Go To package;
facades cannot close under an active read; late imports and callbacks cannot
reactivate closed state.

---

## Phase 3: User Story 1 — Go to a Full Taiwan Address (Priority: P1) 🎯 MVP

**Goal**: Enter one full offline address in native Go To, resolve a unique
exact candidate or explicitly choose an ambiguous candidate, and let ATAK's
host confirmation perform the Go To action.

**Independent Test**: With one county fixture installed, enter a pinned full
address in Taiwan → Address, resolve/select it, verify no map movement before
host confirmation, then confirm and compare the candidate WGS84.

### Tests first

- [ ] T018 [P] [US1] Add failing corpus tests for Unicode width, punctuation, whitespace, `台`/`臺`, unit-adjacent Chinese numerals, proper names, longest locality prefixes, numeric subnumbers, and preserved unclassified text in `app/src/test/java/com/atakmap/android/twcoord/address/lookup/TaiwanAddressParserTest.java`
- [ ] T019 [P] [US1] Add failing forward-service tests for unique exact, duplicate exact, partial/fuzzy, no-match, no-dataset, deterministic deduplication, bounded candidates, provenance, and no nearest-partial auto-resolution in `app/src/test/java/com/atakmap/android/twcoord/address/lookup/DefaultAddressLookupServiceForwardTest.java`
- [ ] T020 [P] [US1] Add failing parameterized database-query and stable ranking tests against address fixtures in `app/src/test/java/com/atakmap/android/twcoord/address/AddressDatabaseFacadeStreetQueryTest.java`
- [ ] T021 [P] [US1] Add failing full-mode, 250 ms debounce, edit-invalidates-resolution, unique-resolution, ambiguous-state, failure-isolation, and listener timing tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/AddressEntryControllerTest.java`
- [ ] T022 [P] [US1] Add failing fourth-tab, synchronous unresolved/resolved getter, metadata-only formatter, and host-confirmation ownership tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`
- [ ] T023 [P] [US1] Add failing candidate-dialog tests for bounded rows, distinguishing labels, revision fencing, Activity window context, and plugin-context resource pre-resolution in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/AddressCandidateDialogTest.java`

### Implementation

- [ ] T024 [P] [US1] Implement `AddressDraft`, components, validation, normalization, raw/unclassified preservation, and revision semantics in `app/src/main/java/com/atakmap/android/twcoord/address/lookup/AddressDraft.java`
- [ ] T025 [US1] Implement dictionary-assisted full-address normalization and longest-prefix/tail parsing in `app/src/main/java/com/atakmap/android/twcoord/address/lookup/TaiwanAddressParser.java`
- [ ] T026 [US1] Move reusable `AddressCandidate`, `CompassDirection`, `ResultOrdering`, `StreetCandidateRanker`, and normalization behavior to neutral lookup ownership under `app/src/main/java/com/atakmap/android/twcoord/address/lookup/`
- [ ] T027 [US1] Add bounded parameterized candidate lookup and explicit exactness classification without nearest-house promotion in `app/src/main/java/com/atakmap/android/twcoord/address/AddressDatabaseFacade.java`
- [ ] T028 [US1] Implement forward dispatch, leased dataset reads, deterministic deduplication/ranking, cancellation, provenance, and completion dispatch in `app/src/main/java/com/atakmap/android/twcoord/address/lookup/DefaultAddressLookupService.java`
- [ ] T029 [US1] Implement full-address session state, debounce, candidate acceptance, resolution invalidation, and human-change notification in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/AddressEntryController.java`
- [ ] T030 [US1] Add `NativeEntryTab` and route Address separately from the three-value coordinate model in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/NativeEntryTab.java` and `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`
- [ ] T031 [US1] Add the fourth selector, compact full-address input, loading/error state, mode affordance placeholder, and bounded candidate action using one outer scroll owner in `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`
- [ ] T032 [US1] Integrate Address selection, synchronous getter/formatter behavior, host-owned confirmation, and namespaced resolution metadata in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [ ] T033 [US1] Implement the revision-fenced candidate chooser with ATAK Activity window ownership and plugin-resolved resources in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/AddressCandidateDialog.java`
- [ ] T034 [P] [US1] Add aligned full-address, normalized, loading, no-match, ambiguous, choose-result, and checked-error strings with matching format arguments in `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml`
- [ ] T035 [US1] Run the US1 parser, database, lookup, controller, pane, and dialog suites via `app/build.gradle` and record Red-Green-Refactor results in `specs/013-native-address-entry/quickstart.md`
- [ ] T036 [RELEASE-GATE] [US1] Execute the full-address and ambiguous-candidate native Go To journeys on ATAK 5.5 and 5.7.0.9, including first-tap dialog resource ownership and no pre-confirm map action, and update the sanitized matrix in `specs/013-native-address-entry/quickstart.md`

**Checkpoint**: US1 is a usable full-field native Address MVP; structured
editing, supplied-point reverse lookup, and Tools consolidation are not yet
required for this checkpoint.

---

## Phase 4: User Story 2 — Switch Full and Structured Address Entry (Priority: P1)

**Goal**: Project one canonical draft between one full field and four compact
structured fields without losing, duplicating, or inventing text.

**Independent Test**: Run at least 100 full → structured → full cases, edit a
structured tail, and prove every input character remains represented and the
same candidate set is produced.

### Tests first

- [ ] T037 [P] [US2] Add failing 100-case lossless projection, edit/recombine, unclassified-text, no-duplicate, deterministic-order, and no-relookup-on-mode-switch tests in `app/src/test/java/com/atakmap/android/twcoord/address/lookup/AddressDraftProjectionTest.java`
- [ ] T038 [P] [US2] Add failing rendering-guard, initial-full-mode, pure mode switch, read-only projection, repeated-switch-during-lookup, and accessibility-state tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/AddressEntryControllerTest.java`
- [ ] T039 [P] [US2] Add failing Robolectric layout tests for four DD-sized rows, one scroll owner, 48 dp mode control, visibility switching, font scale, and host-control clearance in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanAddressLayoutTest.java`

### Implementation

- [ ] T040 [US2] Implement full/structured projection and deterministic recombination without changing draft revision on mode-only changes in `app/src/main/java/com/atakmap/android/twcoord/address/lookup/AddressDraft.java`
- [ ] T041 [US2] Implement rendering guards, four-field edit routing, mode-state rendering, and read-only pure projection in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/AddressEntryController.java`
- [ ] T042 [US2] Add county/city, district/township, road/locality, and tail rows plus a 48 dp accessible split/join control to `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml` and `app/src/main/res/values/dimens.xml`
- [ ] T043 [P] [US2] Add aligned structured-field hints and mode-switch accessibility labels in `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml`
- [ ] T044 [US2] Run the US2 projection, controller, layout, and full US1 regression suites via `app/build.gradle` and record the result in `specs/013-native-address-entry/quickstart.md`
- [ ] T045 [RELEASE-GATE] [US2] Validate full/structured round trips, repeated switching, portrait/landscape reachability, DD-equivalent sizing, default and largest supported font scale on ATAK 5.5 and 5.7.0.9, then update `specs/013-native-address-entry/quickstart.md`

**Checkpoint**: US1 and US2 together provide the complete forward-entry
workflow while still allowing the supplied-point and Tools stories to be
verified separately.

---

## Phase 5: User Story 3 — Inspect a Supplied Point in All Taiwan Representations (Priority: P2)

**Goal**: Preserve immediate Taipower/TWD97/TWD67 preparation while Address
resolves asynchronously, never snaps a supplied WGS84 point, and never accepts
stale work.

**Independent Test**: Alternate two pinned host points 100 times, inspect all
four tabs, and verify no previous address/candidate/availability state appears;
the reverse Address result must retain the exact host point.

### Tests first

- [ ] T046 [P] [US3] Add failing reverse lookup tests for exact query WGS84 retention, separate record WGS84, bounded radius, provenance, no-data/no-match/failure, and cancellation in `app/src/test/java/com/atakmap/android/twcoord/address/lookup/DefaultAddressLookupServiceReverseTest.java`
- [ ] T047 [P] [US3] Add failing session/draft/request/dataset revision fencing and 100 alternating delayed-completion cases in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/AddressEntryControllerConcurrencyTest.java`
- [ ] T048 [P] [US3] Add failing non-null all-tab activation, null active-tab Clear, Address Auto Fill, reverse no-snap, read-only, Copy/format, and no-human-notification tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java`
- [ ] T049 [P] [US3] Add failing dispose, late-dialog callback, locale replacement, exact-instance unregister, start/stop cycle, no-data fallback, and inert retained-View tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrarTest.java` and `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneSafetyTest.java`
- [ ] T050 [P] [US3] Add failing shared-worker parity tests proving native work cannot be starved by stale map-readout requests in `app/src/test/java/com/atakmap/android/twcoord/address/AddressSubsystemTest.java`

### Implementation

- [ ] T051 [US3] Implement reverse lookup through a leased dataset session while retaining both query and record WGS84 in `app/src/main/java/com/atakmap/android/twcoord/address/lookup/DefaultAddressLookupService.java`
- [ ] T052 [US3] Implement activation generations, reverse result state, no-snap resolution, Auto Fill, active-only Clear, read-only, and terminal disposal in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/AddressEntryController.java`
- [ ] T053 [US3] Route non-null/null activation, Auto Fill, getter, formatting, listener, and disposal between coordinate and Address controllers in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`
- [ ] T054 [US3] Adapt map readout reverse consumers to the shared lookup service without changing visible row semantics or budgets in `app/src/main/java/com/atakmap/android/twcoord/address/AddressSubsystem.java`
- [ ] T055 [US3] Reorder address initialization before registrar construction and implement failure-contained reverse ownership plus teardown ordering in `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java`
- [ ] T056 [US3] Pass the live lookup service and manager navigator into new and locale-replacement panes while preserving UI-thread registration and exact-instance disposal in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrar.java`
- [ ] T057 [P] [US3] Add aligned Address unavailable, read-only, disposed, reverse-loading, and management-guidance strings in `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml`
- [ ] T058 [US3] Run reverse, stale-result, pane, registrar, widget parity, coordinate golden-vector, all-tab, Clear, Auto Fill, read-only, and lifecycle suites via `app/build.gradle` and record results in `specs/013-native-address-entry/quickstart.md`
- [ ] T059 [RELEASE-GATE] [US3] Execute Convert Coordinate, Address Auto Fill, Copy/format, missing-data isolation, read-only, 100 alternating points, active lookup/dialog unload, and re-enable journeys on ATAK 5.5 and 5.7.0.9, then update `specs/013-native-address-entry/quickstart.md`

**Checkpoint**: Every supplied-point flow has four coherent representations;
Address failures cannot regress coordinate tabs or move host geometry.

---

## Phase 6: User Story 4 — Manage Offline Data Through TW Coordinates (Priority: P2)

**Goal**: Expose exactly one public plugin Tools item and keep Import, Replace,
Remove, status, and provenance reachable through `TW Coordinates` regardless
of map-readout toggle state.

**Independent Test**: From a clean installation, open `TW Coordinates`, import
one county through its internal manager, return to native Address, and resolve
an address without another plugin Tools entry.

### Tests first

- [ ] T060 [P] [US4] Add failing preference tests for an always-selectable dataset status/management row with all address readout toggles off in `app/src/test/java/com/atakmap/android/twcoord/TwCoordPreferenceFragmentAddressTest.java`
- [ ] T061 [P] [US4] Add failing toolbar composition tests requiring only `TwCoordTool` and no public offline-address item in `app/src/test/java/com/atakmap/android/twcoord/plugin/TwCoordLifecycleTest.java`
- [ ] T062 [P] [US4] Extend manager instrumentation coverage for navigation from settings plus Import, Replace, Remove, progress, error, and same-session availability refresh in `app/src/androidTest/java/com/atakmap/android/twcoord/address/OfflineAddressFlowBCEspressoTest.java`

### Implementation

- [ ] T063 [US4] Keep `pref_address_dataset_status` enabled and route it to the internal manager independently of readout toggles in `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java`
- [ ] T064 [US4] Reduce the public toolbar array to the single existing `TwCoordTool` in `app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordLifecycle.java`
- [ ] T065 [US4] Remove only the public `OfflineAddressTool` while retaining the internal receiver/action contract in `app/src/main/java/com/atakmap/android/twcoord/plugin/OfflineAddressTool.java` and `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressIntents.java`
- [ ] T066 [US4] Inject the retained internal manager navigator into native Address and settings paths without exposing a second Tools item in `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java` and `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java`
- [ ] T067 [P] [US4] Replace active `TW Offline Addr` navigation wording with `TW Coordinates` dataset-management wording in `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml`
- [ ] T068 [US4] Run preference, lifecycle, registry, manager, and same-session availability tests through `app/build.gradle` and record results in `specs/013-native-address-entry/quickstart.md`
- [ ] T069 [RELEASE-GATE] [US4] Reload the plugin and verify exactly one Tools item plus empty/import/replace/remove/status/provenance and all-toggles-off manager access on ATAK 5.5 and 5.7.0.9, then update `specs/013-native-address-entry/quickstart.md`

**Checkpoint**: Offline management remains complete and discoverable through
the sole public plugin entry.

---

## Phase 7: User Story 5 — Upgrade Without Duplicate Workflows or Lost Datasets (Priority: P3)

**Goal**: Retire custom Go To and forward-search pages safely while preserving
valid datasets, applicable settings, coordinate parsing, and native behavior.

**Independent Test**: Upgrade a fixture with two counties and non-default
settings; prove native lookup uses the existing files, only `TW Coordinates`
appears, and stale retired actions open no UI or map mutation.

### Tests first

- [ ] T070 [P] [US5] Add failing upgrade tests for byte-compatible datasets, manifests, ordering/confidence/readout settings, native last-tab state, and inert legacy Go To preferences in `app/src/test/java/com/atakmap/android/twcoord/address/NativeAddressUpgradeTest.java`
- [ ] T071 [P] [US5] Add failing static/contract tests requiring stale Go To and forward-search actions to be unregistered no-ops and all shared parser/ranking references to live outside retired UI packages in `app/src/test/java/com/atakmap/android/twcoord/LegacyWorkflowRemovalTest.java`
- [ ] T072 [P] [US5] Add an upgrade instrumentation scenario using existing imported county files and seeded retired preferences in `app/src/androidTest/java/com/atakmap/android/twcoord/address/NativeAddressUpgradeEspressoTest.java`

### Implementation

- [ ] T073 [US5] Stop constructing/registering custom Go To and forward-search receivers and remove their externally reachable providers during component startup/teardown in `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java`
- [ ] T074 [US5] Remove custom Go To page, receiver, intents, and UI-only state after neutral parser extraction from `app/src/main/java/com/atakmap/android/twcoord/gotopage/`
- [ ] T075 [US5] Remove forward-search receiver, intents, controller/query, and UI-only county funnel while retaining neutral ranking types from `app/src/main/java/com/atakmap/android/twcoord/address/ForwardSearchReceiver.java`, `app/src/main/java/com/atakmap/android/twcoord/address/ForwardSearchIntents.java`, and `app/src/main/java/com/atakmap/android/twcoord/address/forward/`
- [ ] T076 [US5] Remove `TwCoordGotoTool` and `ForwardSearchTool` after toolbar and receiver migration from `app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordGotoTool.java` and `app/src/main/java/com/atakmap/android/twcoord/plugin/ForwardSearchTool.java`
- [ ] T077 [US5] Remove retired layouts, icons, drawables, preference shortcut, and UI-only strings while retaining the offline manager resources in `app/src/main/res/layout/tw_coord_goto.xml`, `app/src/main/res/layout/forward_search_page.xml`, `app/src/main/res/drawable/ic_tw_coord_goto.xml`, `app/src/main/res/drawable/ic_forward_search.xml`, and `app/src/main/res/xml/preferences.xml`
- [ ] T078 [US5] Delete or relocate only tests tied to retired UI while preserving coordinate parser and reusable address regressions under `app/src/test/java/com/atakmap/android/twcoord/`
- [ ] T079 [US5] Run the upgrade, stale-action, parser, ranking, registry, native entry, resource-reference, and full JVM suites via `app/build.gradle` and record results in `specs/013-native-address-entry/quickstart.md`
- [ ] T080 [RELEASE-GATE] [US5] Execute an older-version-to-feature-013 upgrade with two imported counties and retired preference state on ATAK 5.5 and 5.7.0.9, verify no re-import or duplicate workflow, and update `specs/013-native-address-entry/quickstart.md`

**Checkpoint**: All five stories are independently testable and the old
public workflows no longer participate in runtime behavior.

---

## Phase 8: Polish and Cross-Cutting Quality

**Purpose**: Align documentation, localization, resources, packaging, and all
repository quality gates after story behavior converges.

- [ ] T081 [P] Update the native four-tab architecture, dual-mode Address UI, candidate dialog, reverse no-snap rule, read-only/Clear semantics, and sizing guidance in `docs/ui/native-taiwan-coordinate-entry.md`
- [ ] T082 [P] Update the one-entry Tools navigation and always-selectable internal dataset manager design in `docs/ui/settings-fragment.md` and `docs/ui/offline-address-page.md`
- [ ] T083 [P] Rewrite canonical and Traditional Chinese operator journeys for native Address, candidate selection, Convert Coordinate, and dataset management in `docs/user-guide.md` and `docs/user-guide_zh.md`
- [ ] T084 [P] Replace the standalone forward-search guide with the native Address workflow and redirect offline instructions through `TW Coordinates` in `docs/tw-addr-search.md`, `docs/tw-addr-search_zh.md`, `docs/tw-offline-addr.md`, and `docs/tw-offline-addr_zh.md`
- [ ] T085 Update feature summary, compatibility wording, one-Tools-entry navigation, and accepted legacy removals in `README.md` and `CHANGELOG.md`
- [ ] T086 [RELEASE-GATE] Replace and renumber active Tools/native Address screenshots, scrub EXIF/XMP, verify Git LFS, and update references in `docs/images/README.md`
- [ ] T087 Add or update resource parity and accessibility assertions for English, zh-TW, and Japanese strings in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanAddressResourcesTest.java`
- [ ] T088 Audit for retired class/action/resource references, Activity-context plugin resource IDs, unexpected `INTERNET` permission, dead code, and unowned TODOs across `app/src/main/` and record the commands/results in `specs/013-native-address-entry/quickstart.md`
- [ ] T089 Run `:app:spotlessApply`, `:app:spotlessCheck`, `:app:lint`, `:app:testCivDebugUnitTest`, and `:app:assembleCivDebug` against `app/build.gradle`, then record exact results in `specs/013-native-address-entry/quickstart.md`
- [ ] T090 Run documentation link/image checks, sensitive-path scans, `git diff --check`, and a reviewed-scope status audit for `docs/` and `specs/013-native-address-entry/`, then record results in `specs/013-native-address-entry/quickstart.md`
- [ ] T091 Run `/speckit-converge` against `specs/013-native-address-entry/` and append only concrete remaining implementation gaps to `specs/013-native-address-entry/tasks.md`

---

## Phase 9: Release Readiness

**Purpose**: Keep implementation/build completion distinct from physical
acceptance, TPP output, signing, and public publication.

- [ ] T092 [RELEASE-GATE] Select and commit the release `PLUGIN_VERSION` with matching user-visible version and changelog text in `app/build.gradle` and `CHANGELOG.md` before any TPP source archive is generated
- [ ] T093 [RELEASE-GATE] Record the exact candidate commit, APK SHA-256, dataset provenance, and completed/PENDING scenario matrix without device serials or workstation paths in `specs/013-native-address-entry/quickstart.md`
- [ ] T094 [RELEASE-GATE] Complete or explicitly disposition every ATAK 5.5 and 5.7.0.9 compatibility row, including small portrait pane, large font, reload, dialog, lifecycle, and upgrade, in `specs/013-native-address-entry/quickstart.md`
- [ ] T095 [RELEASE-GATE] Measure at least 100 normalization/mode projections and 100 forward/reverse lookups on the named reference device, prove ≤100 ms local work and ≤1,000 ms median/≤2,000 ms p95 lookup budgets, and record sanitized summaries in `specs/013-native-address-entry/quickstart.md`
- [ ] T096 [RELEASE-GATE] Run the five-minute boundary-plus-two-counties scenario and prove ATAK process RSS ≤200 MiB without unbounded requests, candidates, dialogs, leases, or facades in `specs/013-native-address-entry/quickstart.md`
- [ ] T097 [RELEASE-GATE] Complete full-address, candidate, reverse, and dataset-manager journeys in airplane mode with zero plugin-triggered outbound attempts and record the result in `specs/013-native-address-entry/quickstart.md`
- [ ] T098 [RELEASE-GATE] Confirm the candidate's English/Traditional Chinese documentation, screenshot numbering/metadata/LFS, signer expectation, dataset provenance, and artifact provenance in `docs/contributing/release-readiness.md`
- [ ] T099 [RELEASE-GATE] Run the project release-readiness check before any user-authorized TPP upload and record the exact commit/version/pending gates in `docs/contributing/release-readiness.md`
- [ ] T100 [RELEASE-GATE] After a user-authorized TPP build, verify signer, version, source commit, source archive SHA-256, APK SHA-256, and durable non-Gradle staging before publication using `docs/release/tpp-runbook.md`
- [ ] T101 [RELEASE-GATE] Re-run release-readiness before any user-authorized signed tag or GitHub release and record any narrowed compatibility claim in `docs/contributing/release-readiness.md`

---

## Dependencies and Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: Starts immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 and blocks all user stories.
- **US1 (Phase 3)**: Depends only on Phase 2 and is the suggested MVP.
- **US2 (Phase 4)**: Depends on the canonical `AddressDraft` and controller
  introduced by US1; its projection tests remain independently runnable.
- **US3 (Phase 5)**: Depends on Phase 2 and the Address tab shell from US1;
  reverse/no-snap behavior remains independently testable.
- **US4 (Phase 6)**: Depends on Phase 2; it may run beside US2/US3 after the
  internal manager navigator contract is fixed.
- **US5 (Phase 7)**: Depends on US1 and US4 parity because shared code must be
  extracted and replacement navigation proven before legacy deletion.
- **Phase 8 (Polish)**: Depends on all selected story phases.
- **Phase 9 (Release Readiness)**: Depends on implementation convergence and
  a clean committed candidate. Its gates are never inferred from compilation
  or TPP success.

### Within each story

1. Add the listed failing tests and observe the intended failure.
2. Implement models and pure logic.
3. Implement services and ownership boundaries.
4. Integrate Android/ATAK UI seams and localized resources.
5. Run focused and regression suites.
6. Leave physical-device tasks unchecked until their exact ATAK line runs.

### User-story requirement map

| Story | Primary requirements | Independent evidence |
|-------|----------------------|----------------------|
| US1 | FR-001–004, FR-009–014, FR-029–031 | Full address, unique/ambiguous candidates, host confirmation |
| US2 | FR-003–008, FR-013, FR-021, FR-030–031 | 100 lossless mode round trips and compact layout |
| US3 | FR-015–022, FR-032–035 | All-tab activation, reverse no-snap, stale fencing, read-only/lifecycle |
| US4 | FR-022–026, FR-029, FR-035 | One Tools entry and complete internal manager flow |
| US5 | FR-024, FR-026–028, FR-035–036 | Upgrade retention, stale-action no-op, legacy removal |

---

## Parallel Opportunities

- T003 and T004 can proceed independently after ADR drafting begins.
- T006–T009 target separate foundational test files and can be authored in
  parallel; implementation tasks T010–T016 then follow their matching tests.
- US1 test tasks T018–T023 are disjoint; T024 and T034 can also proceed while
  service/query work is underway.
- US2 projection, controller, and layout tests T037–T039 are parallelizable.
- US3 reverse, concurrency, pane, lifecycle, and widget tests T046–T050 are
  parallelizable before their integration tasks.
- US4 preference, lifecycle, and instrumentation tests T060–T062 are disjoint.
- US5 upgrade, static-removal, and instrumentation tests T070–T072 are
  disjoint; deletion tasks remain sequential after parity is green.
- Documentation tasks T081–T084 can run in parallel after UI/navigation names
  stabilize, followed by the shared README/changelog and screenshot audits.

## Parallel Examples

### User Story 1

```text
Task T018: parser corpus in address/lookup/TaiwanAddressParserTest.java
Task T019: forward service outcomes in address/lookup/DefaultAddressLookupServiceForwardTest.java
Task T022: native pane host contract in nativeentry/TaiwanCoordinateEntryPaneContractTest.java
Task T023: cross-context dialog contract in nativeentry/AddressCandidateDialogTest.java
```

### User Story 2

```text
Task T037: lossless projection corpus in address/lookup/AddressDraftProjectionTest.java
Task T039: compact layout contract in nativeentry/TaiwanAddressLayoutTest.java
Task T043: aligned strings in values/, values-zh-rTW/, and values-ja/
```

### User Story 3

```text
Task T046: reverse no-snap service test
Task T047: stale revision-fence controller test
Task T049: registrar/disposal lifecycle test
Task T050: widget/native worker-priority parity test
```

### User Story 4

```text
Task T060: always-selectable preference test
Task T061: one-toolbar-item composition test
Task T062: retained manager instrumentation flow
```

### User Story 5

```text
Task T070: dataset/preference upgrade test
Task T071: stale-action and reference-removal contract
Task T072: physical-layout upgrade instrumentation fixture
```

---

## Implementation Strategy

### MVP first

1. Complete Phases 1 and 2.
2. Complete US1 through T035.
3. Stop and validate the full-address native journey independently.
4. Keep T036 unchecked until both physical ATAK lines are available.

### Incremental delivery

1. **Foundation**: neutral parsers, leased datasets, bounded async service.
2. **US1 MVP**: full address, exact/ambiguous candidate preparation.
3. **US2**: lossless structured editing and field-usable layout.
4. **US3**: supplied-point reverse lookup and full host lifecycle.
5. **US4**: single public Tools entry with retained internal manager.
6. **US5**: remove duplicate workflows only after replacement parity passes.
7. **Polish/Converge**: docs, localization, resource audits, full Gradle gates.
8. **Release readiness**: version freeze and explicit physical/provenance gates.

## Notes

- A task is not complete until its applicable test is green and its
  Red-Green-Refactor evidence is recorded.
- `[P]` never authorizes simultaneous edits to the same file.
- Historical ADRs/specs remain immutable; ADR-0026 supersedes rather than
  rewrites them.
- Do not delete legacy packages before neutral parser/ranking extraction and
  replacement parity.
- Do not commit raw traces, device serials, workstation paths, TPP response
  bundle names, or unrelated image metadata.
- A successful build or TPP result does not close any `[RELEASE-GATE]`.
- Tagging, TPP upload, and publication remain separate user-authorized actions.
