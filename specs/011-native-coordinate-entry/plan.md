# Implementation Plan: Native Taiwan Coordinate Entry

**Branch**: `011-native-coordinate-entry` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/011-native-coordinate-entry/spec.md`

## Summary

Register one plugin-owned **Taiwan** pane with ATAK's native
`CoordinateEntryCapability`. The pane exposes an internal Taipower / TWD97 /
TWD67 selector, delegates all forward and inverse coordinate work to the
existing conversion engine, and implements ATAK's native activation, Auto
Fill, Clear, Copy, editability, and result callbacks. A small lifecycle
registrar owns idempotent UI-thread registration and unload cleanup; a
host-independent controller owns draft and validation state so behaviour can be
tested without an ATAK device. The existing custom **TW Coord GoTo** page,
marker workflow, Recent list, and stored values remain unchanged.

## Technical Context

**Language/Version**: Java 17-compatible Android sources and Android resource
XML.

**Primary Dependencies**: ATAK-CIV SDK 5.7.0.9; Android framework; existing
Proj4J 1.3.0, `CoordinateParser`, `CoordinateConverter`, and coordinate value
objects. A new native-entry-only formatter adapter supplies the stricter Copy
shape without changing the existing widget/custom-page `Formatter`. No new
third-party dependency.

**Storage**: One ATAK-process `SharedPreferences` string containing the native
pane's last-selected Taiwan system. It is owned through `PreferenceStore`,
defaults to `TAIPOWER`, tolerates corrupt values, and is separate from all
`pref_goto_*` custom-page keys. Draft fields are session-only.

**Testing**: Test-first JVM controller, formatting, preference, and lifecycle
contract tests; existing coordinate golden-vector and round-trip suites;
Spotless, Android lint, unit test, and package gates; ATAK 5.5 and 5.7.0.9
on-device acceptance for host-owned dialog behaviour.

**Target Platform**: ATAK-CIV Android plugin on phone and tablet native
coordinate-entry dialogs; reference device Galaxy Tab S10+ (SM-X826B) running
ATAK-CIV 5.7.0.9, plus an available ATAK 5.5 device or emulator for the
minimum-runtime release matrix.

**UI Size Baseline**: Field geometry follows ATAK's native DD pane in
`coordinate_pane_dd.xml`: compact 30/65/5 label/input/unit rows, native
underline fields at `wrap_content` height, 13 sp normal / 17 sp large title
text, a 2 dp top inset, and no card-style field padding. Plugin-owned system
and zone selectors are bounded to 48 dp and empty status text is `GONE`. Device
acceptance uses a paired comparison at the same device, orientation, and font
scale; the native pane must be no smaller and no less reachable than the custom
GoTo controls.

**Android Compile SDK**: 36.

**Android Minimum SDK**: 26.

**ATAK Compile SDK**: ATAK-CIV 5.7.0.9, configured outside Git through
`ATAK_SDK_5_7_0_9`; its `main.jar` SHA-256 is
`8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.

**Minimum ATAK Runtime**: ATAK-CIV 5.5.0, as accepted by
[ADR-0022](../../docs/adr/0022-set-minimum-atak-runtime-to-5-5.md). ADR-0024
updates the compile SDK to 5.7.0.9 without changing the manifest compatibility
token; ATAK 5.4 and earlier remain unsupported.

**ATAK API Evidence**: `javap -public` against the pinned 5.7.0.9 `main.jar`
shows every `CoordinateEntryPane` callback and
`CoordinateEntryCapability.getInstance`, `registerPane`, and `unregisterPane`
as public. The matching physical runtime on `SM-X826B` reports
`versionName=5.7.0.9 (7a0f6f29)` and `versionCode=1782294331`. The earliest
available 5.5 source tag, 5.5.1.1, exposes the same public seam. ADR-0024
accepts that source as the implementation anchor while retaining exact 5.5
physical validation as a release gate.

**Project Type**: Single-module Android ATAK plugin (`app/`).

**Performance Goals**: Pane activation/rendering, system switching, validation,
Auto Fill, Clear, and formatting complete within 100 ms on the reference device
for every supported system and applicable zone, excluding host animation. Each
applicable operation/system/zone combination receives at least 20 measured
iterations, with worst-case and p95 within budget. Registration performs no file
or network I/O; coordinate conversion remains small in-memory math on the UI
thread.

**Constraints**: Fully offline; no new permission or telemetry; exactly one
pane-owned vertical scroll owner because ATAK's active-pane frame does not
scroll; custom-GoTo-parity field sizing and reachable controls; ASCII base-10
integer metres for TWD input with decimal/grouped/signed/non-ASCII forms
rejected; English, zh-rTW, and Japanese strings; WGS84 horizontal point is the
only value returned to ATAK; no coordinate-algorithm or accuracy change;
host-process failures are contained; registration and view mutation occur on
the UI thread.

**Scale/Scope**: One ATAK registration seam, one top-level host pane, three
internal coordinate systems, two TM2 zones, one new preference key, one layout,
three locale files, four focused production classes plus tests.

### Compatibility Matrix

| ATAK Line | Evidence Required | Planned Validation | Status |
|-----------|-------------------|--------------------|--------|
| 5.5 minimum runtime | Manifest declares `com.atakmap.app@5.5.0.CIV`; earliest available 5.5.1.1 source exposes the seam | Run install/lifecycle/user scenarios on an available ATAK 5.5 runtime; do not infer device PASS from source | SOURCE/API PASS; DEVICE PENDING |
| 5.7.0.9 current/compile line | `javap -public` against the pinned and hashed 5.7.0.9 `main.jar` confirms matching signatures; the connected Galaxy Tab S10+ reports the same exact runtime | Run the same lifecycle and user scenarios on the Galaxy Tab S10+ with ATAK 5.7.0.9 | API/RUNTIME VERSION PASS; FEATURE DEVICE JOURNEYS PENDING |

Device status remains pending until the implementation exists and the scenarios
are actually executed; a successful compile is not runtime evidence.

## Constitution Check

*GATE: Passed before Phase 0 and re-checked after Phase 1 design.*

| Principle | Required Plan Evidence | Status |
|-----------|------------------------|--------|
| I. Code Quality & Build Discipline | Implementation gate is `spotlessApply`, `spotlessCheck`, `lint`, `testCivDebugUnitTest`, and `assembleCivDebug`; new code stays in a cohesive `nativeentry` package and introduces no dependency | PASS |
| II. Test-First Development & Verification | Controller, formatter, preference fallback, registration idempotency, and disposed-pane behaviour receive failing JVM tests before production code; ATAK-owned visuals/lifecycle remain explicit device checks | PASS |
| III. UX, Accessibility & Localisation | One internal selector, visible zone, inline states, exactly one non-nested pane `ScrollView`, custom-GoTo-parity field dimensions, paired reachability at the same device/orientation/font scale, content descriptions/labels, and complete `values`, `values-zh-rTW`, `values-ja` resources | PASS |
| IV. Performance & Offline Operation | No I/O/network in pane callbacks; pane activation/rendering and every applicable operation/system/zone combination have a measured <100 ms worst/p95 budget over at least 20 iterations; manifest remains without `INTERNET` | PASS |
| V. Documentation & Decision Traceability | This artifact set, user guide/CHANGELOG tasks, ADR-0022 plus compile-SDK ADR-0024, and a required native-entry architecture ADR before merge preserve the decision trail | PASS |
| VI. Host-Process Isolation | Registrar is idempotent/UI-thread confined with rollback; every host callback contains failures; plugin resources use plugin/localised context; disposed panes stay safe if retained by an already-open host dialog | PASS |
| VII. ATAK SDK Compatibility | Compile/minimum version axes are distinct; 5.7.0.9 SDK `javap` and 5.5.1.1 source anchors are recorded; exact 5.5 device validation remains pending; no reflection or non-public API is used | PASS |
| VIII. Geospatial Correctness & Provenance | Existing parser/converter/constants are reused unchanged; WGS84 is the host interchange; datum, zone, units, national vectors, Taipower quantisation, and 0.5/5/20 m acceptance budgets remain tested | PASS |

**Result**: No non-negotiable violation and no complexity exception. The two
device rows are planned acceptance work, not claims of completed validation.

## Requirement Traceability

Stable requirement IDs remain the source of truth for `/speckit-tasks`, tests,
device evidence, and ADR-0023.

| Requirement IDs | Design evidence | Planned proof |
|-----------------|-----------------|---------------|
| FR-001–FR-003 | R2, `TaiwanPanePreference`, pane/UI contracts | One-tab discovery, first-use Taipower, preference default/restore tests |
| FR-004–FR-010 | R3, R11, draft/validation model | Existing parser/vector suites plus controller invalid/out-of-range tests and native Go To device cases |
| FR-011–FR-016 | R5–R7, pane/UI contracts | Controller/pane contract tests and native Auto Fill/Clear/Copy/editable/read-only device cases |
| FR-017–FR-018; FS-001–FS-004 | R4, R9, registration state model | 100-cycle fake-registry harness, partial rollback/late-callback tests, device unload/re-enable cases |
| FR-019–FR-020; SC-009 | R10 and coexistence UI contract | Upgrade fixture with at least 10 Recent entries and non-default marker mode; byte-preservation assertion/device check |
| FR-021–FR-023; QR-003–QR-004; SC-001, SC-003, SC-006, SC-008 | R7–R9 and UI contract | Three-locale resource parity, custom-GoTo-parity dimensions/reachability, timed pane activation plus per-operation/system/zone traces, offline traffic capture |
| FR-024; FS-005; QR-001; SC-005 | R1, compatibility matrix, ADR-0022/ADR-0024 | 5.5 source/API evidence, exact 5.7.0.9 SDK evidence, and completed minimum/current device matrix |
| FR-025–FR-027; QR-005; SC-002, SC-007 | R6, R11, resolved-coordinate model | Unchanged national/golden suites, native adapter equivalence, built-in → Taiwan → built-in round trip, host-owned action checks |
| QR-002; QR-006 | R4, R9, R10 and both contracts | Boundary failure tests, fallback availability, no migration, ADR/user documentation |

## Project Structure

### Documentation (this feature)

```text
specs/011-native-coordinate-entry/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── coordinate-entry-pane-contract.md
│   └── taiwan-entry-ui-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                         # generated by /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/atakmap/android/twcoord/
│   ├── TwCoordMapComponent.java     # create/start/stop registrar; locale refresh hook
│   ├── nativeentry/
│   │   ├── NativeCoordinateEntryRegistrar.java
│   │   ├── TaiwanCoordinateEntryPane.java
│   │   ├── TaiwanEntryController.java
│   │   └── TaiwanEntryFormatter.java
│   └── prefs/
│       └── PreferenceStore.java     # independent native last-system key
└── res/
    ├── layout/taiwan_coordinate_entry_pane.xml
    ├── drawable/native_entry_*.xml
    └── values{,-zh-rTW,-ja}/strings.xml

app/src/test/java/com/atakmap/android/twcoord/
├── nativeentry/
│   ├── NativeCoordinateEntryRegistrarTest.java
│   ├── TaiwanEntryControllerTest.java
│   └── TaiwanEntryFormatterTest.java
├── gotopage/                        # existing inverse/round-trip suites reused
├── coord/                           # existing vectors/converter suites reused
└── prefs/PreferenceStoreNativeEntryTest.java

docs/
├── adr/0023-native-taiwan-coordinate-entry.md
└── user-guide.md                    # native versus advanced GoTo guidance
```

**Structure Decision**: Keep ATAK-specific registration and callback adaptation
inside `nativeentry`, with a controller that depends only on existing plugin
coordinate types. `TwCoordMapComponent` owns exactly one registrar because it
already owns the plugin process lifecycle and `PreferenceStore`. The existing
`gotopage` UI is not reused as a view: it has its own `ScrollView`, marker
workflow, Recent list, and submit side effects, all of which conflict with a
small host-owned pane. Only its parser/value seams are reused.

## Complexity Tracking

No Constitution violations; this section is intentionally empty.
