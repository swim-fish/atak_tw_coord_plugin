# Implementation Plan: Compact Structured Address Layout

**Branch**: `codex/015-compact-address-layout` | **Date**: 2026-08-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/015-compact-address-layout/spec.md`

## Summary

Prepare plugin version `1.5.1` and compact the native Taiwan structured Address
form from four full-width vertical field rows into two horizontal rows. The
first row contains equal-width county/city and district/township field groups;
the second contains equal-width road/locality and house-number/floor groups.
The existing Address 8:2 content/action split, top-right mode action, single
scroll owner, field IDs, controller bindings, lookup behavior, accessibility,
and ATAK host ownership remain unchanged. A follow-up selected-target fix also
mirrors ATAK's public `HIDE_DETAILS` dismissal signal, clears only the plugin's
TGT readout, and invalidates queued TGT address results before they reach the
UI.

## Technical Context

**Language/Version**: Java 17-compatible Android sources and Android resource XML

**Primary Dependencies**: Existing ATAK-CIV SDK, Android framework, Robolectric,
JUnit 4, AssertJ, and repository Gradle plugins; no dependency additions

**Storage**: No storage change. Existing Address drafts, mode preference,
offline datasets, candidates, and WGS84 host point retain current ownership.

**Testing**: Test-first Robolectric layout contract in
`TaiwanAddressLayoutTest`; focused selected-target dismissal and queued-address
generation tests; existing native-entry/address JVM regressions; Spotless,
Android lint, Civ Debug assembly, documentation/image checks, and separate
on-device ATAK acceptance

**Target Platform**: Single-pane ATAK-CIV Android plugin; reference Galaxy Tab
S10+ plus supported portrait/landscape Go To and Convert Coordinate pane sizes

**Android Compile SDK**: 36 (unchanged)

**Android Minimum SDK**: 26 (unchanged)

**ATAK Compile SDK**: ATAK-CIV 5.7.0.9, retaining the accepted `main.jar`
SHA-256 and `javap -public` evidence from Features 011 and 014

**Minimum ATAK Runtime**: ATAK-CIV 5.5.0 (unchanged)

**ATAK API Evidence**: The layout portion adds no SDK seam. The follow-up
selected-target fix adds one public local-broadcast seam:
`AtakBroadcast.registerReceiver` / `unregisterReceiver` for
`com.atakmap.android.maps.HIDE_DETAILS`. `javap -public` against the pinned
5.7.0.9 `main.jar` confirms both lifecycle methods and
`AtakBroadcast.DocumentedIntentFilter()`. Immutable ATAK-CIV 5.5.1.1
source anchors show `MenuLayoutWidget` sending the action on background map
press/click/long-press, `CoordOverlayMapComponent` registering it for the
native overlay, and `AtakBroadcast` using the local-broadcast register/dispose
path. Exact ATAK 5.5 device acceptance remains a release gate.

**Project Type**: Single-module Android ATAK plugin

**Performance Goals**: Compact-form presentation and Address mode changes show
visible feedback within 100 ms p95 over 20 device repetitions; no new I/O or
allocation-sensitive state is introduced.

**Constraints**: One outer vertical scroll owner; no horizontal or nested
scrolling; existing 48 dp interaction targets; 8:2 Address content/action
geometry; two equal field groups per structured row; EN/zh-TW/JA parity;
offline-only; no permission, network, dependency, coordinate, parsing, or
lookup-result change; selected-target dismissal affects only TGT while MAP and
ME stay visible; user-provided attachments remain untracked and uncommitted.

**Scale/Scope**: One XML layout, two Java production classes, three focused
test classes, existing native-entry/address regression suites, version and
documentation files, UI references, and one changelog entry. No persisted
model change is introduced; address generations are transient per-row runtime
state.

### Compatibility Matrix

| ATAK Line | Evidence Required | Planned Validation | Status |
|-----------|-------------------|--------------------|--------|
| 5.5 minimum runtime | Immutable ATAK-CIV 5.5.1.1 source anchors for `HIDE_DETAILS` send, native-overlay delivery, public registration, and disposal; exact device layout/lifecycle journey | Install `1.5.1`; verify native and plugin TGT dismissal plus both Address modes, orientations, locales, font scales 1.0/2.0, read-only, accessibility, and host controls | Source anchor complete; `[RELEASE-GATE]` pending exact device |
| 5.7.0.9 current runtime | Pinned `main.jar` SHA-256 and `javap -public` evidence for `AtakBroadcast`; current-device layout/dismissal/lifecycle journey | Build, install, select a marker, tap empty map, confirm native and plugin TGT clear while MAP/ME remain, then exercise the compact-layout matrix | API/build and partial device dismissal/layout evidence complete; remaining T009 matrix pending |

## Constitution Check

*GATE: Passed before Phase 0 and re-checked after Phase 1.*

| Principle | Required Plan Evidence | Status |
|-----------|------------------------|--------|
| I. Code Quality & Build Discipline | Test-first focused suite followed by Spotless, full JVM tests, lint, Civ Debug assembly, docs checks, and `git diff --check` | PASS |
| II. Test-First Development & Verification | Update the existing four-row contract to fail on the current XML before changing production resources; retain device-only acceptance as release gates | PASS |
| III. UX, Accessibility & Localisation | One scroll owner, equal columns, 48 dp controls, logical row-major order, read-only state, EN/zh-TW/JA, TalkBack/Switch Access, and font-scale matrix | PASS |
| IV. Performance & Offline Operation | 100 ms p95 layout budget; dismissal adds no I/O, network, telemetry, permission, or persisted state; one atomic generation check guards each UI emission | PASS |
| V. Documentation & Decision Traceability | Update UI references, both user guides, changelog, version, and feature evidence. No ADR: the reversible layout refinement stays inside ADR-0023 and the follow-up uses the host's existing public selected-item dismissal contract without changing compatibility strategy. | PASS |
| VI. Host-Process Isolation | Existing field IDs and controller bindings remain intact; ordinary cleanup failures are contained, while `VirtualMachineError` and `ThreadDeath` are not swallowed; queued callbacks cannot restore dismissed TGT state | PASS |
| VII. ATAK SDK Compatibility | All four compatibility axes stated; new `AtakBroadcast` seam has pinned 5.7 `javap`, stable 5.5.1.1 source anchors, lifecycle coverage, and an exact 5.5 device release gate | PASS |
| VIII. Geospatial Correctness & Provenance | No parser, conversion, dataset, WGS84, ranking, zone, precision, or accuracy change; regression suites lock results | PASS |
| IX. Release Integrity & Provenance | Version synchronized to `1.5.1`; device/docs/signer/provenance work is marked `[RELEASE-GATE]`; no tag or publication is part of implementation | PASS |

### Post-design re-check

Phase 1 introduced only a view projection contract. Review remediation later
added a public `AtakBroadcast` seam and transient per-row generation state.
Research R6-R7, the compatibility matrix, pinned `javap`, immutable upstream
source anchors, focused Red/Green tests, and registration/disposal coverage now
document that expansion. No persistence, dependency, permission, network path,
or compatibility-strategy change is introduced. Every constitution gate
remains PASS; public release readiness remains separate from implementation
convergence.

## Project Structure

### Documentation (this feature)

```text
specs/015-compact-address-layout/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── compact-address-layout-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
app/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/com/atakmap/android/twcoord/TwCoordMapComponent.java
    │   ├── java/com/atakmap/android/twcoord/address/AddressSubsystem.java
    │   └── res/layout/taiwan_coordinate_entry_pane.xml
    └── test/java/com/atakmap/android/twcoord/
        ├── TwCoordMapComponentTargetDismissTest.java
        ├── address/AddressSubsystemTest.java
        └── nativeentry/
            ├── TaiwanAddressLayoutTest.java
            ├── TaiwanCoordinateEntryPaneContractTest.java
            ├── TaiwanInlineImeContractTest.java
            └── NativeEntryFeature014RegressionTest.java

docs/
├── ui/readout-widget.md
├── ui/native-taiwan-coordinate-entry.md
├── user-guide.md
└── user-guide_zh.md

CHANGELOG.md
```

**Structure Decision**: Preserve the current view IDs and controller seam.
Add two row containers around the existing four field-group containers in the
single layout resource. For selected-target dismissal, subscribe through the
public ATAK broadcast wrapper already used by the native overlay and invalidate
address work at the subsystem boundary. This keeps the layout bindings stable,
avoids reflection/private APIs, and isolates the host callback from lookup/UI
delivery.

## Complexity Tracking

No constitution exception is required. The additional local-broadcast seam and
per-row generation are documented and tested as bounded compatibility and
lifecycle complexity.
