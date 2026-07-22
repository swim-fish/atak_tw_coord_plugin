# Implementation Plan: Native Taiwan Address Entry

**Branch**: `codex/013-native-address-entry` | **Date**: 2026-07-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/013-native-address-entry/spec.md`

## Summary

Extend the existing ATAK-native Taiwan pane with an offline Address tab that
supports a full field and a four-field structured projection of one canonical
draft. Normalize and split Taiwan addresses using longest known locality
prefixes plus a unit-tail grammar, then resolve through a shared asynchronous
service over the existing imported county datasets. Unique exact matches may
resolve automatically; ambiguous matches require explicit candidate choice.
Reverse lookup labels but never moves a host-supplied WGS84 point.

Complete the navigation migration in the same feature: keep `TW Coordinates`
as the only public Tools item, retain the offline dataset manager as an
internal page reachable from settings, and retire the custom Go To and forward
search Tools/pages after moving their shared parser/query logic to neutral
packages. Stabilize registry/import/lookup ownership with cancellation,
revision fences, dataset read leases, monotonic close, and an explicit startup
and teardown order.

## Technical Context

**Language/Version**: Java 17 Android sources and Android resource XML

**Primary Dependencies**: Existing ATAK-CIV public SDK; Android framework;
Proj4J 1.3.0; existing ATAK/platform SQLite path with the already-shipped
Requery SQLite 3.45.0 fallback; no new runtime dependency

**Storage**: Existing plugin SharedPreferences; existing imported county
SQLite files, boundary SQLite, and provenance manifests under ATAK-managed
plugin storage. Dataset format/path and manifest schema are unchanged. Legacy
custom Go To preferences remain inert rather than being deleted.

**Testing**: JUnit 4, AssertJ, Mockito, Robolectric, xerial SQLite fixtures,
existing Android instrumented tests, Gradle Spotless/lint/unit/package gates,
and real ATAK device acceptance for host dialogs, resources, lifecycle,
performance, memory, offline behavior, and compatibility

**Target Platform**: Single-module ATAK-CIV Android plugin. Current reference
device is Galaxy Tab S10+ (`SM-X826B`) on ATAK-CIV 5.7.0.9; an available ATAK
5.5 device/emulator is required for minimum-runtime release evidence. Validate
portrait/landscape, ATAK's smaller 5.5 portrait dialog, default font scale, and
the largest supported field-usable font scale.

**Android Compile SDK**: 36, inherited and unchanged

**Android Minimum SDK**: 26, inherited and unchanged

**ATAK Compile SDK**: ATAK-CIV 5.7.0.9. `main.jar` SHA-256 is
`8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.

**Minimum ATAK Runtime**: ATAK-CIV 5.5.0, inherited from ADR-0022; manifest
compatibility token remains `com.atakmap.app@5.5.0.CIV`

**ATAK API Evidence**: `javap -public` against the pinned 5.7.0.9 `main.jar`
confirms `CoordinateEntryPane` and public capability lookup/register/unregister.
ATAK 5.5.1.1, the earliest public source anchor in the runtime family, exposes
the same pane and registration contract. The pane interface is unchanged
through 5.5.1.10. Exact 5.5 callback ordering and device behavior remain a
physical release gate.

**Project Type**: Existing single-module Android ATAK plugin (`app/`); no new
module, flavor, process, service, or permission

**Performance Goals**:

- normalization and full/structured mode projection ≤ 100 ms p95 and
  worst-case on the reference device;
- forward and reverse address results visible within 1,000 ms median and
  2,000 ms p95 across at least 100 representative real-device lookups;
- zero stale result across 100 alternating supplied-point activations;
- ATAK process RSS ≤ 200 MiB during the established five-minute session with
  boundary data and at least two county datasets imported;
- one operator can complete native full-address Go To within 30 seconds.

**Constraints**:

- fully offline; no INTERNET permission, network fallback, or telemetry;
- no database, boundary, file, Future wait, or blocking work in synchronous
  ATAK pane callbacks or the main thread;
- WGS84 remains the host interchange and reverse lookup never snaps geometry;
- one outer pane scroll owner, compact DD-sized fields, 48 dp mode/candidate
  controls, and English/zh-TW/Japanese parity;
- host Activity context owns dialog windows while plugin context resolves all
  plugin resources;
- ordinary failures and documented version-skew errors are contained at host
  boundaries, while fatal JVM errors are rethrown;
- retired actions are safely unregistered/hard ignored rather than redirected
  through an unverified ATAK seam.

**Scale/Scope**:

- one existing ATAK top-level Taiwan pane with four internal tabs;
- two Address input projections and one bounded candidate dialog;
- one public Tools item and one retained internal offline manager page;
- up to the existing multi-county registry scale, with per-county datasets up
  to approximately 731,000 rows / 324 MB and approximately 10 MB boundary data;
- one bounded address worker shared by native entry and adapted address
  consumers;
- three retired public Tools workflows, two retired receivers/pages, and one
  retained internal receiver/page.

### Compatibility Matrix

| ATAK Line | Evidence Required | Planned Validation | Status |
|-----------|-------------------|--------------------|--------|
| 5.5 minimum runtime | 5.5.1.1 public pane/capability/MGRS/address source anchors; manifest 5.5.0 token; no private API | Install, plugin reload, four-tab pane, full/structured address, candidate dialog, Convert Coordinate, read-only, Clear/Auto Fill/Copy, one Tools item, dataset manager, unload | SOURCE/API PASS; DEVICE PENDING `[RELEASE-GATE]` |
| 5.7.0.9 current/compile line | Pinned/hash-verified `main.jar` and `javap -public`; exact current runtime/build | Same journeys on Galaxy Tab S10+, plus trace/RSS/offline capture | SDK/API PASS; FEATURE DEVICE/PERFORMANCE PENDING `[RELEASE-GATE]` |

## Constitution Check

*Initial gate before Phase 0 research: PASS. Re-evaluated after Phase 1 below.*

| Principle | Required Plan Evidence | Status |
|-----------|------------------------|--------|
| I. Code Quality & Build Discipline | Spotless Apply/Check, lint, full JVM tests, debug APK, `git diff --check`, resource/reference audits | PASS |
| II. Test-First Development & Verification | Red/Green tasks precede parser, service, lease, pane, migration, and lifecycle implementation; JVM/Robolectric/device split is defined in quickstart | PASS |
| III. UX, Accessibility & Localisation | One scroll owner, DD geometry, 48 dp controls, explicit loading/empty/error/read-only states, dialog resource rule, EN/zh-TW/JA parity, UI/docs/screenshots | PASS |
| IV. Performance & Offline Operation | No main-thread I/O, cancellable bounded worker, explicit 100 ms/1 s/2 s/200 MiB budgets, real-device trace/RSS/capture, no network change | PASS |
| V. Documentation & Decision Traceability | ADR-0026 is mandatory and supersedes the coexistence decision; README/changelog/guides/UI docs/screenshots update with stable requirement links | PASS |
| VI. Host-Process Isolation | Synchronous cache-only host callbacks, narrow outer boundaries, dialog contexts, revision fencing, read leases, monotonic close, idempotent teardown | PASS |
| VII. ATAK SDK Compatibility | Four version axes are explicit; 5.7.0.9 javap/hash and 5.5.1.1 source anchors recorded; exact current/minimum device matrix remains release-gated | PASS |
| VIII. Geospatial Correctness & Provenance | WGS84 host interchange, reverse no-snap rule, existing coordinate regressions unchanged, candidate/dataset provenance attached to new metadata | PASS |
| IX. Release Integrity & Provenance | Version freeze deferred to release candidate; device/compatibility/performance/docs/signer/provenance tasks must be labelled `[RELEASE-GATE]`; TPP is not acceptance | PASS |

No non-negotiable violation or justified constitution exception is present.

## Project Structure

### Documentation (this feature)

```text
specs/013-native-address-entry/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── address-lookup-service-contract.md
│   ├── native-address-pane-contract.md
│   └── tools-migration-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/atakmap/android/twcoord/
│   ├── coord/
│   │   └── input/                  # moved shared coordinate parser/value core
│   ├── nativeentry/
│   │   ├── NativeEntryTab.java
│   │   ├── AddressEntryController.java
│   │   ├── AddressCandidateDialog.java
│   │   ├── TaiwanCoordinateEntryPane.java
│   │   ├── TaiwanEntryController.java
│   │   └── NativeCoordinateEntryRegistrar.java
│   ├── address/
│   │   ├── lookup/                 # shared async lookup/parser/result contracts
│   │   ├── boundary/               # retained locality source
│   │   ├── ActiveDatasetRegistry.java
│   │   ├── AddressDatabaseFacade.java
│   │   ├── AddressSubsystem.java   # adapted widget consumer
│   │   ├── BatchImportCoordinator.java
│   │   ├── OfflineAddressReceiver.java
│   │   └── OfflineAddressIntents.java
│   ├── plugin/
│   │   ├── TwCoordLifecycle.java   # exactly one TwCoordTool
│   │   └── TwCoordTool.java
│   ├── TwCoordMapComponent.java
│   └── TwCoordPreferenceFragment.java
└── res/
    ├── layout/
    │   ├── taiwan_coordinate_entry_pane.xml
    │   ├── offline_address_page.xml
    │   └── offline_address_county_row.xml
    ├── values*/                    # EN / zh-rTW / JA aligned resources
    ├── drawable*/                  # retained one Tools icon + address controls
    └── xml/preferences.xml

app/src/test/java/com/atakmap/android/twcoord/
├── coord/input/                    # moved parser regressions
├── nativeentry/                    # pane/controller/dialog contracts
└── address/
    ├── lookup/                     # parser/service/concurrency tests
    └── existing importer/registry/database regressions

docs/
├── adr/0026-native-address-entry-and-tools-consolidation.md
├── ui/
├── images/
├── user-guide.md
├── user-guide_zh.md
└── address/offline guides as currently organized
```

Retired after shared extraction and parity tests:

```text
app/src/main/java/com/atakmap/android/twcoord/gotopage/   # UI/state; shared parser moved first
app/src/main/java/com/atakmap/android/twcoord/address/ForwardSearchReceiver.java
app/src/main/java/com/atakmap/android/twcoord/address/ForwardSearchIntents.java
app/src/main/java/com/atakmap/android/twcoord/address/forward/ForwardSearchController.java
app/src/main/java/com/atakmap/android/twcoord/address/forward/ForwardSearchQuery.java
app/src/main/java/com/atakmap/android/twcoord/address/forward/{CountySeed,CountySource}.java
app/src/main/java/com/atakmap/android/twcoord/plugin/{TwCoordGotoTool,OfflineAddressTool,ForwardSearchTool}.java
app/src/main/res/layout/{tw_coord_goto,forward_search_page}.xml
```

Reusable `CompassDirection`, `ResultOrdering`, normalization, and ranking
types are retained or moved to neutral packages before the UI funnel is
removed.

**Structure Decision**: Keep the single Android module and the accepted native
pane/registrar seam. Move deterministic coordinate parsing into `coord/input`
because it serves native entry independently of the retired page. Introduce
`address/lookup` as the UI-independent owner of parsing, query, provenance,
cancellation, and worker lifecycle. Keep storage/import/boundary types under
`address` and keep ATAK View/dialog adaptation under `nativeentry`. This is the
smallest boundary that prevents synchronous legacy page code from leaking into
the host pane while preserving existing dataset formats and reverse readouts.

## Design and Delivery Strategy

### Phase A — Stabilize shared foundations

1. Add ADR-0026 before behavior implementation.
2. Move coordinate parser/value classes and tests from the legacy Go To
   package without behavior changes.
3. Add parser/normalizer corpus and lossless AddressDraft projection tests.
4. Add lookup request/result/provenance models and a bounded async service.
5. Add registry read sessions, closed gates, import completion fencing, and
   concurrency tests.
6. Adapt existing reverse readout consumers to the shared service without
   changing their displayed results or budgets.

### Phase B — Add native Address behavior

1. Add the UI-level fourth tab and Address controller without changing the
   coordinate-system model.
2. Add full/structured controls, rendering guards, mode projection, and
   localized/accessibility states.
3. Route non-null activation to synchronous coordinate preparation plus async
   reverse Address preparation; preserve null active-only Clear.
4. Route editable input to the established 250 ms debounced forward lookup;
   auto-resolve only one exact candidate and provide explicit candidate dialog
   otherwise.
5. Attach namespaced metadata, preserve reverse query WGS84, and keep getter/
   format synchronous and cache/metadata-only.
6. Integrate lookup availability and an internal manager navigator while
   retaining Address failure isolation from coordinate tabs.

### Phase C — Consolidate Tools and remove duplicate workflows

1. Make the dataset status/management preference always selectable.
2. Reduce `TwCoordLifecycle` to `TwCoordTool` only.
3. Stop registering custom Go To and forward-search receivers; retain the
   offline receiver as internal navigation.
4. Remove legacy UI classes, UI-only preferences/tests, intents, layouts,
   drawables, and tool strings only after shared code extraction and parity.
5. Leave old stored custom values inert and preserve imported data byte-for-
   byte; verify stale old actions are safe no-ops.
6. Audit localization, render scripts, resources, manifest, and dead code.

### Phase D — Documentation and release evidence

1. Rewrite active navigation and native Address documentation in English and
   Traditional Chinese; align Japanese application strings.
2. Replace and renumber active Tools/native screenshots through the screenshot
   workflow; inspect metadata and LFS state.
3. Run all JVM/Gradle gates and current-device acceptance.
4. Complete ATAK 5.5 physical matrix and real-device performance/memory/offline
   evidence as `[RELEASE-GATE]` tasks.
5. Select/freeze the release version, then run release-readiness before any TPP
   staging, tag, or publication.

## Requirement Traceability

| Requirement Group | Research / Contract | Planned Evidence |
|-------------------|---------------------|------------------|
| FR-001–005 four tabs and dual mode | R4, R10; native pane contract | Pane/controller tests; DD/MGRS paired device layout |
| FR-006–008 normalization/splitting | R5; lookup contract; data model AddressDraft | 100-address corpus and 100 lossless mode round trips |
| FR-009–014 forward candidates/host action | R6, R8, R10; lookup + pane contracts | Exact/ambiguous/no-match tests; real candidate dialog and no pre-confirm map action |
| FR-015–021 activation/reverse/Clear/read-only | R2, R3, R9, R13; pane contract | Alternating activation, reverse no-snap, Auto Fill/Clear/Copy/read-only JVM + device journeys |
| FR-022–028 availability/Tools/migration | R11–R13; tools migration contract | Missing-data isolation, one-icon reload, always-selectable manager, upgrade fixture, stale-action no-op |
| FR-029–031 localization/layout/accessibility | R10; pane contract | Resource parity, accessibility source tests, paired 5.5/current screenshots |
| FR-032–035 geospatial/offline/compat/safety | R1–R3, R6–R9, R13–R14 | Existing golden regressions, captures, javap/source, device matrix, concurrency/lifecycle tests |
| FR-036 documentation | R11, R12, R15; tools contract | Link/image/LFS/metadata audit and canonical guide parity |

## Post-Design Constitution Check

*GATE after Phase 1 design: PASS.*

- `research.md` resolves every technical unknown without a remaining
  `NEEDS CLARIFICATION` marker.
- `data-model.md` makes reverse no-snap geometry, candidate exactness,
  provenance, revisions, and leased dataset ownership explicit.
- Contracts keep synchronous ATAK callbacks free of I/O, specify dialog
  context/resource ownership, preserve active-only Clear, and define monotonic
  cancellation/teardown.
- `quickstart.md` separates JVM/source/build evidence from current/minimum ATAK
  device gates and provides real-device latency, RSS, offline, reload, upgrade,
  and cross-context dialog checks.
- The design adds no dependency, permission, dataset format, compatibility-axis
  change, or constitution exception.
- ADR-0026, documentation, screenshots, version freeze, signer, artifact
  provenance, and both device lines remain explicit future implementation or
  `[RELEASE-GATE]` work rather than being claimed complete by planning.

## Complexity Tracking

No constitution exception is required. The shared service and registry lease
are additional structure, but they remove two UI-bound query owners and close
documented use-after-close/stale-result races; a simpler direct-controller
reuse would violate main-thread and host-lifecycle constraints.
