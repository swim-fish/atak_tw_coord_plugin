---
description: "Implementation task list for feature 011: native Taiwan coordinate entry"
---

# Tasks: Native Taiwan Coordinate Entry

**Input**: Design documents from `specs/011-native-coordinate-entry/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/coordinate-entry-pane-contract.md`,
`contracts/taiwan-entry-ui-contract.md`, `quickstart.md`

**Tests**: Behaviour changes use Red → Green → Refactor. Write each JVM or
Robolectric test first, run it, and retain evidence that it failed for the
intended missing behaviour before implementing the paired production task.
Resource-only and ATAK-host-only behaviour is called out explicitly and is
validated by lint, layout inflation, Perfetto/network evidence, or the named
on-device matrix rather than a fabricated JVM substitute.

**Organization**: Tasks are grouped by US1–US5 in priority order. Shared
controller, formatter, pane, and registrar seams live in US1 because every
later story extends them. ADR-0024 resolves the implementation gate by using
the available ATAK-CIV 5.7.0.9 compile/current-device SDK and accepting the
earliest public 5.5.1.1 source as the 5.5 family API anchor. ADR-0022's 5.5.0
minimum runtime remains unchanged, and physical 5.5 validation remains a
release gate.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after its stated prerequisites because it edits
  a different file from the other parallel tasks.
- **[Story]**: Maps directly to the user story in `spec.md`.
- All file paths are repository-root-relative.

## Path Conventions

- Production Java: `app/src/main/java/com/atakmap/android/twcoord/`
- Android resources: `app/src/main/res/`
- JVM/Robolectric tests: `app/src/test/java/com/atakmap/android/twcoord/`
- Feature artifacts: `specs/011-native-coordinate-entry/`
- Architecture/UI documentation: `docs/adr/`, `docs/ui/`, `docs/user-guide.md`

## Story → Priority Map

| Story | Priority | Delivery theme | Release role |
|-------|----------|----------------|--------------|
| US1 | P1 | One native Taiwan choice and valid Taipower Go To | Technical MVP |
| US2 | P1 | TWD97/TWD67, explicit zones, validation and advisory | Completes P1 field-entry slice |
| US3 | P2 | Native Auto Fill, Clear and Copy | Native-control parity |
| US4 | P2 | Preserve custom GoTo and failure fallback | Upgrade/rollback safety |
| US5 | P3 | Other host dialogs, read-only state and locale refresh | Shared-dialog coverage |

---

## Phase 1: Setup — Compatibility Gate

**Purpose**: Close the only unresolved minimum-runtime evidence gap before
depending on the native ATAK seam.

- [X] T001 Resolve the unavailable exact ATAK-CIV 5.5.0 artifact through accepted compatibility ADR `docs/adr/0024-use-atak-5-7-0-9-compile-sdk.md`; retain `ext.ATAK_VERSION = "5.5.0"`, accept public 5.5.1.1 source as the implementation-time 5.5 family API anchor, pin the compile/current-device SDK to ATAK-CIV 5.7.0.9, record its `main.jar` SHA-256/public signatures and matching `SM-X826B` runtime build in `research.md`/`quickstart.md`, and retain physical ATAK 5.5 validation as T045 release evidence (FR-024, FS-005, QR-001, SC-005).

**Checkpoint**: The exact minimum runtime is evidenced, or implementation is
explicitly blocked pending a superseding compatibility decision.

---

## Phase 2: Foundational — Shared UI Resources

**Purpose**: Add the resource surface used by every story. These are
resource-only tasks; automated behaviour starts in US1, while `lint` and
Robolectric layout inflation validate resource integrity later.

- [X] T002 [P] Add aligned native-entry strings to `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, and `app/src/main/res/values-ja/strings.xml` using the exact ID families `native_entry_taiwan`, `native_entry_system_{taipower,twd97,twd67}`, `native_entry_{taipower_hint,taipower_help,easting,northing,metres,zone,zone_121,zone_119,outer_island_advisory}`, `native_entry_error_{empty,incomplete,malformed,bad_zone,out_of_coverage,unrepresentable}`, `native_entry_read_only`, and `native_entry_a11y_*`; keep IDs and format arguments identical across locales (FR-003, FR-007, FR-010, FR-021, SC-006). Validation is T042/T044 because this task changes resources only.
- [X] T003 [P] Add concrete state-list/background resources `app/src/main/res/drawable/native_entry_segment_track.xml`, `app/src/main/res/drawable/native_entry_segment_option.xml`, `app/src/main/res/drawable/native_entry_input_bg.xml`, and `app/src/main/res/drawable/native_entry_advisory_bg.xml`; use concrete colours/resources, visible checked/disabled/error states, and no `android.R.attr.*` where a drawable ID is required (FR-022, QR-002/QR-003). Validation is Robolectric inflation in T007 plus resource/accessibility and Gradle checks in T042/T044.

**Checkpoint**: All shared strings and drawables resolve in every supported
locale and are ready for the pane layout.

---

## Phase 3: User Story 1 — Native Taiwan Taipower Go To (Priority: P1) 🎯 Technical MVP

**Goal**: ATAK shows exactly one native Taiwan choice; first use selects
Taipower; a valid Taipower input returns WGS84 to the host's normal Go To flow.

**Independent Test**: On a clean preference store and ATAK 5.7.0.9, open native
Go To, select Taiwan, enter a pinned Taipower vector, confirm, and verify the
host reaches the same WGS84 point as custom GoTo in under 30 seconds without a
plugin-owned marker, pan, affiliation, or elevation side effect.

### Tests for User Story 1 — write and observe RED first

- [X] T004 [P] [US1] Add failing default/corrupt/read-write tests for `pref_native_entry_last_unit`, first-use `TAIPOWER`, and independence from `KEY_GOTO_LAST_UNIT` in `app/src/test/java/com/atakmap/android/twcoord/prefs/PreferenceStoreNativeEntryTest.java`; record the intended Red result before T009 (FR-003, FR-020).
- [X] T005 [P] [US1] Add failing tests for Taipower draft creation, existing 9/11-character parsing, invalid/no-result behaviour, host-point forward rendering, and canonical WGS84 resolution in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java`; reuse existing golden vectors without changing tolerances and record Red before T011 (FR-004, FR-008–FR-010, FR-014, FR-025, SC-002).
- [X] T006 [P] [US1] Add failing canonical-format tests in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryFormatterTest.java`: Taipower forward output is normalised 11-character/1 m, TWD formats always contain system, E/N, metres, and explicit zone 121/119, null/unrepresentable returns null, and formatting does not mutate caller/controller state; record Red before T010 (FR-013, FR-025).
- [X] T007 [P] [US1] Add a failing Robolectric contract suite in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java` covering stable UID/name/view identity, one root `ScrollView`, Taipower `onActivate/getGeoPointMetaData`, invalid checked error, human-only listener notification, and idempotent disposed late callbacks; record Red before T012 (FR-001, FR-009, FR-016, FR-018, FS-003/FS-004).
- [X] T008 [P] [US1] Add a failing fake-registry lifecycle suite in `app/src/test/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrarTest.java` covering UI-dispatch start, exactly one registration, matching-instance unregister, stale queued-generation suppression, idempotent stop, and 100 start/stop cycles with zero duplicates; record Red before T013 (FR-017, SC-004).

### Implementation for User Story 1

- [X] T009 [P] [US1] Implement `KEY_NATIVE_ENTRY_LAST_UNIT`, `getNativeEntryLastUnit()`, and `setNativeEntryLastUnit(CoordinateUnit)` in `app/src/main/java/com/atakmap/android/twcoord/prefs/PreferenceStore.java`; default/corrupt fallback is `TAIPOWER`, writes use only the new key, and no on-map `fireAll()` or `pref_goto_*` mutation occurs until T004 is Green (FR-003, FR-020).
- [X] T010 [P] [US1] Implement the pure native-only formatter in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryFormatter.java` using existing conversion result/value objects; never change `coord/Formatter.java`, always show TWD axis/unit/zone, preserve Taipower 11-character/1 m forward output, and make T006 Green (FR-013, FR-025/FR-026).
- [X] T011 [P] [US1] Implement `TaiwanEntryController` and its in-memory system/draft/validation/session model in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`; reuse `CoordinateParser`/`CoordinateConverter`, support first-use Taipower activation and resolution, discard cached valid points on edits, separate programmatic from human changes, and make T005 Green without copying coordinate constants (FR-003/FR-004, FR-008–FR-010, FR-014, FR-025).
- [X] T012 [US1] Create `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml` with exactly one `native_entry_root` `ScrollView` and one `native_entry_content` vertical child; add the mutually exclusive `native_entry_system_{taipower,twd97,twd67}` controls, `native_entry_pane_taipower`, a labelled compact underline `native_entry_input_taipower`, and a status area that is `GONE` while empty; follow ATAK DD row/font geometry and add no plugin-owned Confirm/Auto Fill/Clear/Copy/elevation/marker controls; then implement `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java` against the public ATAK interface with UID `com.atakmap.android.twcoord.coordinateentry.taiwan`, plugin/localised resources, horizontal `GeoPointMetaData`, checked invalid errors, listener isolation, fatal-condition rethrow, and safe idempotent disposal until T007 is Green (FR-001/FR-002, FR-009, FR-016, FR-018, FR-021/FR-022, FR-027).
- [X] T013 [P] [US1] Implement `app/src/main/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrar.java` with a package-private fakeable registry gateway, `STOPPED/START_PENDING/REGISTERED/STOP_PENDING/FAILED` state, UI-thread dispatch, stable pane instance, generation token, same-instance unregister-before-dispose, and no broad `Throwable` swallowing until T008 is Green (FR-017/FR-018, FS-001/FS-002, SC-004).
- [X] T014 [US1] Own exactly one registrar in `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java`; start it after the existing custom GoTo receiver is available, stop it before `PreferenceStore`/component teardown, resolve host capability with `view.getContext()`, and contain ordinary registration failures without aborting unrelated plugin startup (FR-001, FR-017–FR-019, FS-001/FS-002).
- [ ] T015 [US1] Run `:app:testCivDebugUnitTest` for `PreferenceStoreNativeEntryTest`, `TaiwanEntryFormatterTest`, `TaiwanEntryControllerTest`, `TaiwanCoordinateEntryPaneContractTest`, and `NativeCoordinateEntryRegistrarTest`, record Red→Green evidence in `specs/011-native-coordinate-entry/quickstart.md`, then execute the US1 5.7.0.9 device journey and record the one-tab, WGS84-equivalence, host-owned-action, and under-30-second results there (SC-001/SC-002/SC-004). The device portion has no faithful JVM substitute because ATAK owns the dialog and Go To callback.

**Checkpoint**: US1 works independently as a technical MVP on the current ATAK
line. It is not a release candidate until US2/US3 and the final compatibility
matrix are complete.

---

## Phase 4: User Story 2 — All Taiwan Systems and Safe Zone Handling (Priority: P1)

**Goal**: Operators can enter Taipower, TWD97, or TWD67 with explicit TM2 zone
121/119, actionable validation, and the existing TWD67 zone-119 advisory.

**Independent Test**: Enter Taipei 101 and one authoritative zone-119 vector
for every applicable system; compare against the existing tolerances, then
verify malformed/incomplete/out-of-coverage input keeps ATAK's dialog open and
does not replace the host point.

### Tests for User Story 2 — write and observe RED first

- [X] T016 [P] [US2] Extend `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java` with failing TWD97/TWD67 zone-121/119, explicit-zone, inactive-draft isolation, numeric/partial/bad-zone/out-of-coverage, decimal/signed/grouped/non-ASCII rejection, 22-city, outer-island, and system-switch cases; require 0.5 m TWD97, 5 m TWD67 main-island, and 20 m TWD67 outer-island budgets before T018 (FR-002, FR-005/FR-006, FR-008–FR-010, SC-002/SC-007).
- [X] T017 [P] [US2] Extend `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java` with failing system/zone selector, TWD field binding, visible zone, TWD67-119 advisory, invalid Copy/confirm checked-error, and exactly-once human-change notification cases before T020 (FR-005–FR-007, FR-009/FR-010, FR-016).

### Implementation for User Story 2

- [X] T018 [P] [US2] Extend `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java` to retain separate per-system drafts, parse ASCII base-10 integer TWD metres only through existing `CoordinateParser`, reject decimal/signed/grouped/non-ASCII forms without rounding, forward-render via `CoordinateConverter`, select explicit zone 121/119, expose specific validation states, and make T016 Green without altering datum/coverage constants (FR-002, FR-004–FR-010, FR-025).
- [X] T019 [P] [US2] Extend `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml` with hidden-by-default `native_entry_pane_{twd97,twd67}`, compact DD-style 30/65/5 label/input/unit rows for `native_entry_{twd97,twd67}_{easting,northing}`, 48 dp bounded `native_entry_{twd97,twd67}_zone_{121,119}` controls, and `native_entry_twd67_advisory`; keep only the active group visible and retain the single root scroll owner (FR-002, FR-005–FR-007, FR-021/FR-022).
- [X] T020 [US2] Bind all system, field, zone, advisory, and validation states in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`; suppress callbacks during programmatic rendering, fire once for human edits, never return a stale point after invalid input, and make T017 Green (FR-006–FR-010, FR-016, QR-002/QR-003).
- [ ] T021 [US2] Run the focused native-entry JVM/Robolectric suites and unchanged `app/src/test/java/com/atakmap/android/twcoord/coord/` plus `app/src/test/java/com/atakmap/android/twcoord/gotopage/CoordinateParserRoundTripTest.java` tests, record Green/tolerance evidence in `specs/011-native-coordinate-entry/quickstart.md`, then execute the US2 all-system/zone/invalid-input device cases on ATAK 5.7.0.9 (SC-002/SC-007). Device validation is required for ATAK's keep-dialog-open behaviour.

**Checkpoint**: US1+US2 form the complete P1 field-entry slice.

---

## Phase 5: User Story 3 — Native Auto Fill, Clear and Copy (Priority: P2)

**Goal**: ATAK's own Auto Fill, Clear, and Copy controls operate on the active
Taiwan system without moving the map, confirming the dialog, or mutating the
draft during formatting.

**Independent Test**: At pinned main-island and zone-119 map centres, exercise
all three host controls for every system and verify populated/cleared fields,
zone, clipboard shape, error state, and unchanged map/draft state.

### Tests for User Story 3 — write and observe RED first

- [X] T022 [P] [US3] Extend `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java` with failing representable Auto Fill, Taipower outer-island unrepresentable-and-cleared, repeated replacement, null Clear, all-system canonical format, and no-programmatic-change-notification cases before T024 (FR-011–FR-014, SC-003).
- [X] T023 [P] [US3] Extend `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java` with failing `autofill`, `onActivate(null, editable)` Clear, pure `format(point)`, explicit TWD zone, no-altitude, and disposed late-control callback cases before T025 (FR-011–FR-016, FS-004).

### Implementation for User Story 3

- [X] T024 [P] [US3] Implement full Auto Fill replacement, unrepresentable clearing, Clear state, and pure format delegation in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java`; never combine old/new drafts or notify human-change listeners for host actions, and make T022 Green (FR-011/FR-012/FR-013/FR-014).
- [X] T025 [US3] Implement ATAK callback delegation for `autofill`, Clear-through-`onActivate(null, editable)`, and state-independent `format` in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`; use `TaiwanEntryFormatter`, keep host altitude/action ownership, contain checked failures, and make T023 Green (FR-011–FR-016, FR-027).
- [ ] T026 [US3] Run the focused suites and execute the US3 main-island/zone-119 Auto Fill, Clear, Copy, unrepresentable Taipower, and no-map-movement cases on ATAK 5.7.0.9; record clipboard strings and device evidence in `specs/011-native-coordinate-entry/quickstart.md` (SC-003). Host button dispatch/clipboard behaviour is device-only.

**Checkpoint**: Native Taiwan entry has parity with ATAK's standard coordinate
controls.

---

## Phase 6: User Story 4 — Preserve Advanced Custom GoTo and Fallback (Priority: P2)

**Goal**: Native entry is additive; custom GoTo state and entry points remain
unchanged, and a failed native registration never prevents the fallback page
from loading.

**Independent Test**: Seed at least 10 Recent entries and a non-default marker
mode, simulate native registration failure, then verify ATAK/custom GoTo remain
usable and every seeded value is byte-for-byte unchanged.

### Tests for User Story 4 — write and observe RED first

- [X] T027 [P] [US4] Extend `app/src/test/java/com/atakmap/android/twcoord/prefs/PreferenceStoreNativeEntryTest.java` with a failing upgrade fixture containing 10 `KEY_GOTO_RECENT_JSON` entries, all saved coordinate fields, and a non-default marker mode; use native selection and assert every `pref_goto_*` value is unchanged before T030 (FR-019/FR-020, SC-009).
- [X] T028 [P] [US4] Extend `app/src/test/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrarTest.java` with failing capability-lookup/register/partial-register/unregister exceptions, best-effort rollback, disposed late callback, specific `NoClassDefFoundError`/`NoSuchMethodError` version-skew handling, and fatal `VirtualMachineError`/`ThreadDeath` rethrow cases before T029 (FR-018/FR-019, FS-001/FS-002, QR-002).

### Implementation for User Story 4

- [X] T029 [US4] Harden `app/src/main/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrar.java` so ordinary exceptions and only documented linkage errors produce rollback/logged `FAILED` state, unregister failures still dispose safely, and fatal JVM conditions are rethrown until T028 is Green; never fabricate registration success (FR-018, FS-001/FS-002, QR-002).
- [X] T030 [US4] Refine `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java` so custom `TwCoordGotoReceiver` registration/entry points are established independently of native registrar success and remain available until their normal teardown; do not read/migrate/write custom history from native code, then make T027 Green and retain existing custom GoTo tests unchanged (FR-019/FR-020, QR-006).
- [ ] T031 [US4] Run `PreferenceStoreNativeEntryTest`, `PreferenceStoreCustomIconTest`, existing custom GoTo/parser tests, and the registrar failure suite; then execute the seeded-upgrade, forced-registration-failure, custom Tools/settings entry, marker mode, icon palette, Recent, and unload/re-enable device scenarios on ATAK 5.7.0.9 and record evidence in `specs/011-native-coordinate-entry/quickstart.md` (FS-001/FS-002, SC-004/SC-009). The real host fallback path is device-only.

**Checkpoint**: Native failure or use cannot remove or mutate the advanced
custom workflow.

---

## Phase 7: User Story 5 — Other Native Dialogs, Read-Only and Locale Refresh (Priority: P3)

**Goal**: The global pane safely renders supplied points in other ATAK
coordinate dialogs, respects editable/read-only state, round-trips through
built-in panes, and refreshes localisation when reopened.

**Independent Test**: Open an editable point-details flow and one read-only or
additional location flow; render/edit only where allowed, switch built-in →
Taiwan → built-in without human edits, and reopen after each supported locale
change.

### Tests for User Story 5 — write and observe RED first

- [X] T032 [P] [US5] Extend `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryControllerTest.java` with failing supplied-point rendering, no-human-edit round trip, read-only edit rejection, invalid-altitude horizontal acceptance, and active-system precision-budget cases before T035 (FR-014/FR-015, FR-025/FR-027, SC-007).
- [X] T033 [P] [US5] Extend `app/src/test/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPaneContractTest.java` with failing disabled field/system/zone controls, still-readable formatted point, no listener/result mutation from attempted read-only edits, and refreshed internal strings on activation before T035 (FR-014/FR-015, FR-021/FR-022).
- [X] T034 [P] [US5] Extend `app/src/test/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrarTest.java` with failing detached immediate locale re-register, attached deferred refresh-until-detach, stale refresh generation, and exactly-one-tab cases before T036 (FR-017/FR-021, FS-004, SC-004/SC-006).

### Implementation for User Story 5

- [X] T035 [US5] Enforce host `editable` state, supplied-point rendering, horizontal-only metadata, programmatic event suppression, and activation-time localised label/error refresh in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java` and `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java` until T032/T033 are Green (FR-014–FR-016, FR-021/FR-022, FR-027).
- [X] T036 [US5] Implement `refreshLocale()` and `REFRESH_PENDING` in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrar.java`: re-register only while the pane view is detached, defer attached refresh until detach, preserve UID/state, and make T034 Green without mutating an active host dialog (FR-017/FR-021, FS-004).
- [X] T037 [US5] Route the existing plugin language-change signal to `NativeCoordinateEntryRegistrar.refreshLocale()` in `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java`; do not change ATAK's global coordinate-format preference or custom GoTo state (FR-021/FR-026).
- [ ] T038 [US5] Run the focused suites, then execute editable point-details, one additional/read-only native location flow, MGRS/DD/DM/DMS/UTM→Taiwan→same-built-in precision at pinned main-island and zone-119 points, invalid-altitude, closed-dialog English/zh-rTW/Japanese refresh, and an active-dialog Activity/configuration recreation with retained log evidence on ATAK 5.7.0.9; verify recreation yields a valid empty or last-confirmed state without a crash, host action, or half-converted draft, and record exact host flow/result in `specs/011-native-coordinate-entry/quickstart.md` (FS-004, SC-005–SC-007). These behaviours require ATAK's real shared dialog and configuration lifecycle.

**Checkpoint**: All five user stories are independently verified on the current
ATAK line; final release gates remain below.

---

## Phase 8: Polish and Cross-Cutting Release Gates

**Purpose**: Close architecture, documentation, safety, performance, offline,
quality, and minimum/current-runtime evidence. Device-only tasks remain
unchecked until actually run.

- [X] T039 [P] Author `docs/adr/0023-native-taiwan-coordinate-entry.md` linking FR-001/FR-017/FR-019/FR-024/FR-027, ADR-0022, and compile-SDK ADR-0024; record the one-pane public API choice, stable UID, controller/formatter separation, UI-thread registrar lifecycle, open-dialog inert disposal, single scroll owner, 5.5 source/current-5.7.0.9 evidence status, and custom-page coexistence (QR-001/QR-002/QR-006).
- [ ] T040 [P] Create `docs/ui/native-taiwan-coordinate-entry.md` and update `docs/user-guide.md`, `README.md`, and `CHANGELOG.md` with the native-versus-advanced workflow, three systems/zones, 119 accuracy advisory, read-only/native controls, custom fallback, ATAK minimum claim supported by T001, and current screenshots in all applicable canonical English docs (FR-007, FR-019–FR-024, QR-003/QR-006).
- [X] T041 Audit and fix host boundaries in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/NativeCoordinateEntryRegistrar.java`, `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`, and `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java`; ensure no broad `Throwable` catch swallows `VirtualMachineError`/`ThreadDeath`, only documented linkage errors are caught, listener fan-out is isolated, optional SDK/views are null-checked, plugin resources use plugin context, and cite final file:line evidence in `docs/adr/0023-native-taiwan-coordinate-entry.md` (QR-002, FS-001–FS-004).
- [ ] T042 Validate/fix the single-scroll-owner; ATAK DD parity of compact horizontal rows, native underline `wrap_content` inputs, 13/17 sp title text, 48 dp selector heights, a 2 dp top inset, and `GONE` empty status; paired reachability/no-overlap at the same device/orientation/font scale; labels/content descriptions; disabled/read-only contrast; polite status announcements; three-locale key/format-argument parity; no `INTERNET` permission; and no global `CoordinateFormat`/`pref_goto_*` mutation across `app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`, `app/src/main/res/values*/`, and `app/src/main/AndroidManifest.xml`; record results in `specs/011-native-coordinate-entry/quickstart.md` (FR-020–FR-023, FR-026, SC-006/SC-008).
- [ ] T043 Capture named `android.os.Trace` sections for pane activation/rendering, switch, validate, Auto Fill, Clear, and format on the Galaxy Tab S10+ with ATAK 5.7.0.9; execute at least 20 iterations of every applicable operation/system/zone combination, export Perfetto samples, and record both worst-case and p95 below 100 ms; disable ATAK sync, capture the ATAK process with Network Inspector or packet capture, and retain zero-plugin-network-attempt evidence for airplane-mode Go To/Auto Fill in `specs/011-native-coordinate-entry/quickstart.md` (QR-004, SC-003/SC-008). Keep unchecked if no capture facility; JVM timing or airplane mode alone is not proof.
- [X] T044 Run `./gradlew.bat :app:spotlessApply`, `:app:spotlessCheck`, `:app:lint`, `:app:testCivDebugUnitTest`, and `:app:assembleCivDebug`; fix every applicable error/new warning in `app/src/main/`, `app/src/test/`, or feature docs and record commands/results in `specs/011-native-coordinate-entry/quickstart.md` (Constitution Principles I and II, QR-002/QR-005).
- [ ] T045 Execute the full `specs/011-native-coordinate-entry/quickstart.md` compatibility matrix on an ATAK 5.5 runtime and exact ATAK 5.7.0.9; record ATAK build, device/screen/orientation/font scale, plugin APK SHA-256, date/operator, every US1–US5 scenario, paired native-versus-custom GoTo size/reachability at default and largest accepted font scale, active-dialog Activity/configuration recreation with log evidence, and unload/re-enable without converting unavailable runs to PASS (FR-022, FR-024, FS-004/FS-005, SC-004/SC-005). This device task has no JVM substitute and blocks the release claim.
- [X] T046 Reconcile every FR-001–FR-027, FS-001–FS-005, QR-001–QR-006, and SC-001–SC-009 against code/tests/device evidence in `specs/011-native-coordinate-entry/tasks.md` and `specs/011-native-coordinate-entry/quickstart.md`; update only evidence/status, keep missing device proof explicit, and ensure ADR/docs cite stable IDs (Constitution V).
- [X] T047 Run `/speckit-converge` against `specs/011-native-coordinate-entry/spec.md`, `specs/011-native-coordinate-entry/plan.md`, and `specs/011-native-coordinate-entry/tasks.md`; append only actionable unbuilt work to `specs/011-native-coordinate-entry/tasks.md`, then repeat implementation/validation until no actionable gap remains.
- [X] T048 Run `git diff --check` and a final documentation-link/working-tree scope audit for `specs/011-native-coordinate-entry/`, `docs/adr/0023-native-taiwan-coordinate-entry.md`, `docs/ui/native-taiwan-coordinate-entry.md`, `docs/user-guide.md`, `README.md`, and `CHANGELOG.md`; preserve unrelated user changes and prepare the reviewed feature scope for an explicit commit request.

---

## Dependencies and Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 has no predecessor and blocks source implementation
  until the exact minimum-runtime seam is proven or superseded by an accepted
  compatibility decision.
- **Foundational (Phase 2)**: T002 and T003 start after T001 and run in parallel;
  both block the pane layout/contract work.
- **US1 (Phase 3)**: Depends on Foundation. Test tasks T004–T008 run first;
  T009–T013 make their paired tests Green; T014 integrates only after pane and
  registrar exist; T015 validates the story.
- **US2 (Phase 4)**: Depends on US1's controller/pane/layout. T016/T017 precede
  T018–T020; T021 validates.
- **US3 (Phase 5)**: Depends on US2 because native controls must work for every
  system. T022/T023 precede T024/T025; T026 validates.
- **US4 (Phase 6)**: Depends only on US1's preference/registrar/component seams
  and may run alongside US2/US3 if shared-file edits are coordinated. T027/T028
  precede T029/T030; T031 validates.
- **US5 (Phase 7)**: Depends on US2/US3's complete pane and US1's registrar.
  T032–T034 precede T035–T037; T038 validates.
- **Polish (Phase 8)**: Depends on all desired stories. T039/T040 may begin in
  parallel, then T041/T042 audit implementation, T043–T045 gather objective
  gates, T046 reconciles evidence, T047 converges, and T048 closes scope.

### User Story Dependency Graph

```text
Setup T001
  └─ Foundation T002–T003
       └─ US1 (native Taipower MVP)
            ├─ US2 (all systems/zones)
            │    └─ US3 (native controls for all systems)
            │         └─ US5 (shared/read-only dialogs)
            └─ US4 (custom workflow/fallback)

US3 + US4 + US5
  └─ Polish/release gates
```

### Within Each User Story

- Write and run all story test tasks first; retain the intended Red evidence.
- Implement models/controller/formatter before the Android pane that consumes
  them.
- Implement pane and registrar before `TwCoordMapComponent` integration.
- Run focused JVM/Robolectric tests before the story's device checkpoint.
- Do not mark device-only work complete from a successful build or mock.

## Parallel Opportunities

- Foundation: T002 (strings) and T003 (drawables).
- US1 Red phase: T004, T005, T006, T007, T008 touch five different test files.
- US1 Green phase after paired Red tasks: T009 (preferences), T010 (formatter),
  T011 (controller), and T013 (registrar) touch different production files;
  T012 waits for controller/formatter/resources, then T014 integrates.
- US2 Red phase: T016 (controller test) and T017 (pane contract test); Green
  T018 (controller) and T019 (layout) can run together before T020 binds them.
- US3 Red phase: T022 (controller test) and T023 (pane contract test); T024 then
  precedes pane integration T025.
- US4 Red phase: T027 (preferences) and T028 (registrar); US4 can otherwise run
  in parallel with US2/US3 until `TwCoordMapComponent.java` edits require
  coordination.
- US5 Red phase: T032, T033, and T034 touch distinct test files.
- Polish: T039 (ADR) and T040 (UI/user/release docs) start in parallel; device
  measurement T043 and documentation-only preparation may overlap after code
  stabilises.

## Parallel Execution Examples

### User Story 1

```text
T004 PreferenceStoreNativeEntryTest.java
T005 TaiwanEntryControllerTest.java
T006 TaiwanEntryFormatterTest.java
T007 TaiwanCoordinateEntryPaneContractTest.java
T008 NativeCoordinateEntryRegistrarTest.java
```

### User Story 2

```text
T016 TaiwanEntryControllerTest.java — all systems/zones/accuracy
T017 TaiwanCoordinateEntryPaneContractTest.java — fields/advisory/errors
```

### User Story 3

```text
T022 TaiwanEntryControllerTest.java — Auto Fill/Clear/format state
T023 TaiwanCoordinateEntryPaneContractTest.java — ATAK control callbacks
```

### User Story 4

```text
T027 PreferenceStoreNativeEntryTest.java — upgrade-state preservation
T028 NativeCoordinateEntryRegistrarTest.java — rollback/fatal boundaries
```

### User Story 5

```text
T032 TaiwanEntryControllerTest.java — supplied-point/read-only round trip
T033 TaiwanCoordinateEntryPaneContractTest.java — disabled/readable UI
T034 NativeCoordinateEntryRegistrarTest.java — deferred locale refresh
```

## Implementation Strategy

### Technical MVP First — US1

1. Complete T001 compatibility gate.
2. Complete Foundation T002–T003.
3. Write Red tests T004–T008.
4. Implement T009–T014 and make tests Green.
5. Run T015 and stop for independent US1 validation.

US1 alone is a technical/demo MVP. Because the product requirement includes
all three field systems, US1+US2 is the minimum P1 implementation milestone,
not a releasable feature slice; all mandatory requirements and T045 remain
release gates. Native control parity additionally requires US3.

### Incremental Delivery

1. Setup + Foundation → evidenced SDK boundary and compilable resources.
2. US1 → one native Taiwan/Taipower path → current-device demo.
3. US2 → TWD97/TWD67 and safe zones → complete P1 field slice.
4. US3 → native Auto Fill/Clear/Copy parity.
5. US4 → upgrade/fallback proof.
6. US5 → other shared dialogs/read-only/locales.
7. Polish → ADR/docs, safety audit, performance/offline proof, full Gradle and
   exact-minimum/current device matrices, convergence.

## Notes

- `[P]` means different files and no incomplete dependency, not merely that two
  tasks look conceptually independent.
- Every story test task must be observed Red before its paired implementation;
  record Red→Green evidence in `quickstart.md` or review notes.
- Do not change coordinate constants, datum models, coverage bounds, or
  published tolerances in this feature.
- Do not edit accepted ADR-0022. Any compatibility reversal needs a superseding
  ADR and matching metadata/docs changes.
- Keep T043 and T045 unchecked until the named device/capture work actually
  runs; builds and source inspection are not substitutes.
- After task generation, run `/speckit-analyze` before implementation according
  to the project workflow.

## Phase 9: Convergence

- [X] T049 Add a test-first host-callback safety boundary for `getName`, `onActivate`, `getGeoPointMetaData`, `autofill`, `format`, listener notification, and disposal in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java`; ordinary resource/conversion/listener failures must log and return a safe null/checked/no-op result, only documented `NoClassDefFoundError`/`NoSuchMethodError` version-skew cases may be contained, and `VirtualMachineError`/`ThreadDeath` must be rethrown per QR-002 and Constitution VI (partial).
- [X] T050 Add balanced, exception-safe `android.os.Trace` sections around native pane activation/rendering, system switch, validation, Auto Fill, Clear, and format paths in `app/src/main/java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java` and/or `TaiwanEntryController.java`, with a JVM-testable trace adapter where necessary, so T043 can capture operation-specific Perfetto evidence per QR-004 and SC-003 (missing).

## Phase 10: Native Layout Fix (Delivered with v1.4.2)

- [X] T051 Add `layoutUsesAtakDdCompactRowsAndBoundedControls` to `TaiwanCoordinateEntryPaneContractTest`; retain the intended Red compile result for the missing DD row/dimensions before the resource change (FR-022).
- [X] T052 Replace card-style native inputs with DD-style weighted underline rows, bound system/zone selectors to 48 dp, add ATAK-equivalent normal/large font dimensions, and hide empty status height in the layout/pane (FR-022, QR-003).
- [ ] T053 Run the focused native-entry suite and full Gradle gates, install the combined v1.4.2 build on ATAK 5.7.0.9, and record landscape Taipower/TWD97/TWD67 screenshots proving no elevation/footer overlap; keep ATAK 5.5 device evidence pending if unavailable (FR-022, FR-024, SC-005).
