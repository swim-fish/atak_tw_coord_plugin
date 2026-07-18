# Implementation Plan: Prefill All Native Taiwan Tabs

**Branch**: `codex/012-prefill-native-tabs` | **Date**: 2026-07-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/012-prefill-native-tabs/spec.md`

## Summary

When ATAK activates the plugin's Taiwan `CoordinateEntryPane` with a non-null
WGS84 point, prepare Taipower, TWD97, and TWD67 from that same point before the
pane renders. Build the three results in a temporary immutable snapshot and
commit them together so a later system switch reveals prepared data and never
an older map item's draft. Each draft retains its own validation, resolved
point, zone, and availability. Native Clear and Auto Fill remain active-only,
and ATAK continues to consume only the active draft.

## Technical Context

**Language/Version**: Java 17-compatible Android sources; no Android resource
change is expected.

**Primary Dependencies**: ATAK-CIV SDK 5.7.0.9, Android framework, and the
existing `CoordinateConverter`, `CoordinateParser`, coordinate value objects,
`TaiwanEntryController`, and `TaiwanCoordinateEntryPane`. No new dependency.

**Storage**: No new storage. The existing `pref_native_entry_last_unit`
preference still stores only the last human-selected system. Draft snapshots
remain session-only and do not touch custom GoTo preferences or Recent data.

**Testing**: Test-first JVM controller and pane contract tests; unchanged
coordinate golden-vector/round-trip suites; Spotless, Android lint, unit test,
and package gates; on-device ATAK Convert Coordinate and shared-dialog checks.

**Target Platform**: ATAK-CIV Android plugin on phone and tablet dialogs. The
current reference device is Galaxy Tab S10+ (`SM-X826B`) on ATAK-CIV 5.7.0.9;
an available ATAK 5.5 runtime remains the minimum-line release gate.

**Android Compile SDK**: 36.

**Android Minimum SDK**: 26.

**ATAK Compile SDK**: ATAK-CIV 5.7.0.9 through `ATAK_SDK_5_7_0_9`; pinned
`main.jar` SHA-256
`8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.

**Minimum ATAK Runtime**: ATAK-CIV 5.5.0 through the existing
`com.atakmap.app@5.5.0.CIV` manifest contract. ADR-0022 and ADR-0024 continue
to govern the split minimum-runtime and compile-SDK axes.

**ATAK API Evidence**: No new ATAK seam. `javap -public` against the pinned
5.7.0.9 jar confirms `CoordinateEntryPane.onActivate(GeoPointMetaData,
boolean)` and the other seven existing callbacks. The 5.5.1.1 public source
shows `CoordinateEntryCapability` passing its current point to the selected
pane's `onActivate`; ATAK source also shows map-item details and contact
location flows supplying their point through the same shared capability.

**Project Type**: Single-module Android ATAK plugin (`app/`).

**Performance Goals**: Stage three in-memory conversions and render the active
draft within 100 ms at p95 and worst-case on the reference device, with at
least 20 measured main-island and zone-119 activations. No I/O, allocation of
large collections, or network activity in the host callback.

**Constraints**: Fully offline; no permission, telemetry, layout, parser,
projection constant, precision, or locale-string change; WGS84 remains the
host interchange; ordinary per-system out-of-range results are isolated;
unexpected activation failure must not expose stale or partially updated
drafts; read-only and host-owned actions remain unchanged.

**Scale/Scope**: One existing ATAK callback, one controller, one pane adapter,
three coordinate systems, two TM2 zones, focused JVM/contract tests, and one
device journey family. No database, migration, or new SDK registration seam.

### Compatibility Matrix

| ATAK Line | Evidence Required | Planned Validation | Status |
|-----------|-------------------|--------------------|--------|
| 5.5 minimum runtime | Existing 5.5.0 manifest token plus public 5.5.1.1 `CoordinateEntryPane`/`CoordinateEntryCapability` source | Install, activate from map-item Convert Coordinate, switch all three systems, Clear/Auto Fill, unload/reload | SOURCE/API PASS; DEVICE PENDING |
| 5.7.0.9 current/compile line | Hashed `main.jar` and `javap -public` signature; matching runtime | Run the same journey on `SM-X826B`, including main-island, zone 119, read-only, and 100 alternating activations | API PASS; FEATURE DEVICE PENDING |

A successful build or 5.7.0.9 device run is not evidence of a completed 5.5
runtime check.

## Constitution Check

*GATE: Passed before Phase 0 and re-checked after Phase 1 design.*

| Principle | Required Plan Evidence | Status |
|-----------|------------------------|--------|
| I. Code Quality & Build Discipline | Run `spotlessApply`, `spotlessCheck`, `lint`, `testCivDebugUnitTest`, and `assembleCivDebug`; change remains inside the existing `nativeentry` package | PASS |
| II. Test-First Development & Verification | Add failing controller/pane tests before production changes; split JVM correctness from ATAK-owned device journeys | PASS |
| III. UX, Accessibility & Localisation | No geometry or string change; existing DD-style sizing, three locales, status region, accessibility, and read-only rules remain regression gates | PASS |
| IV. Performance & Offline Operation | Three pure in-memory conversions, no I/O/network/dependency, named activation trace, and measured <100 ms p95/worst-case budget | PASS |
| V. Documentation & Decision Traceability | This artifact set plus native-entry UI/user documentation and changelog updates; no ADR because the accepted public seam and architecture are unchanged | PASS |
| VI. Host-Process Isolation | Atomic staging, full stale-state invalidation on unexpected failure, no programmatic human callback, and existing pane boundary containment | PASS |
| VII. ATAK SDK Compatibility | Android 36/26 and ATAK 5.7.0.9/5.5.0 axes remain explicit; no new public seam; source, `javap`, lifecycle, and device matrix recorded | PASS |
| VIII. Geospatial Correctness & Provenance | Existing converter/parser/constants remain authoritative; per-system zone/coverage results and unchanged golden vectors prove behaviour | PASS |

**Post-design re-check**: The snapshot model, activation contract, and
quickstart preserve every gate. No non-negotiable violation or complexity
exception is introduced.

## Requirement Traceability

| Requirement IDs | Design evidence | Planned proof |
|-----------------|-----------------|---------------|
| FR-001–FR-007, FR-015 | R2–R4, `PreparationSnapshot` and activation contract | Main-island/zone-119 controller tests, switch-without-Auto-Fill pane test, 100-alternation stale-state test |
| FR-008–FR-009 | R5, active projection contract | Zero-listener test, active-only result/format/Copy contract tests |
| FR-010–FR-012 | R6 and command matrix | Existing plus focused Clear, Auto Fill, editable/read-only regression tests |
| FR-013–FR-014 | R1, R7 | Unchanged converter/parser tests and locale/resource parity gates |
| QR-001–QR-002 | R1, R4, compatibility matrix | 5.5 source/API evidence, 5.7 `javap`, host-boundary failure tests, device matrix |
| QR-003–QR-006 | R5–R8 | UI regression, performance trace, offline/manifest check, golden vectors, preference byte-preservation |

## Project Structure

### Documentation (this feature)

```text
specs/012-prefill-native-tabs/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── activation-prefill-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                         # generated later by /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/atakmap/android/twcoord/nativeentry/
├── TaiwanEntryController.java       # per-system drafts and atomic host snapshot
└── TaiwanCoordinateEntryPane.java  # host callback/render adapter

app/src/test/java/com/atakmap/android/twcoord/nativeentry/
├── TaiwanEntryControllerTest.java
├── TaiwanCoordinateEntryPaneContractTest.java
└── TaiwanCoordinateEntryPaneSafetyTest.java

app/src/test/java/com/atakmap/android/twcoord/coord/
└── CoordinateConverterTest.java     # unchanged coverage/zone regression suite

docs/
├── ui/native-taiwan-coordinate-entry.md
└── user-guide.md

CHANGELOG.md
```

**Structure Decision**: Extend the existing host-independent controller rather
than teach the Android view to convert background systems. The controller owns
all draft invariants and exposes only the selected draft as the current
validation/resolved result. The pane continues to adapt ATAK callbacks and
render one visible field group. No new production class is mandatory; a small
package-private immutable snapshot/draft type may be nested or top-level based
on the simplest testable implementation.

## Implementation Sequence

1. Add failing controller tests for all-system activation, zone 119,
   unavailable Taipower, active-only result projection, zero notification,
   100 alternating activations, and unexpected-failure stale-state clearing.
2. Add failing pane contract tests that activate once and switch through every
   system without Auto Fill, including read-only and unavailable status.
3. Refactor controller state into independent drafts and implement atomic
   non-null activation. Keep `clear()` and `autofill()` active-only.
4. Update pane rendering only as needed to project the newly selected draft;
   retain the existing host boundary and trace naming.
5. Run focused tests, full coordinate regressions, quality/build gates, and
   sensitive-information scan.
6. Install on the matching ATAK 5.7.0.9 device and execute quickstart evidence;
   keep ATAK 5.5 device evidence pending until actually run.
7. Update native-entry documentation and the root changelog. Defer release
   version selection until the release/PR decision; this plan does not bump a
   version.

## Complexity Tracking

No Constitution violations; this section is intentionally empty.
