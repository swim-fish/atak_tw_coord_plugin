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
and ATAK host ownership remain unchanged.

## Technical Context

**Language/Version**: Java 17-compatible Android sources and Android resource XML

**Primary Dependencies**: Existing ATAK-CIV SDK, Android framework, Robolectric,
JUnit 4, AssertJ, and repository Gradle plugins; no dependency additions

**Storage**: No storage change. Existing Address drafts, mode preference,
offline datasets, candidates, and WGS84 host point retain current ownership.

**Testing**: Test-first Robolectric layout contract in
`TaiwanAddressLayoutTest`, existing native-entry/address JVM regressions,
Spotless, Android lint, Civ Debug assembly, documentation/image checks, and
separate on-device ATAK acceptance

**Target Platform**: Single-pane ATAK-CIV Android plugin; reference Galaxy Tab
S10+ plus supported portrait/landscape Go To and Convert Coordinate pane sizes

**Android Compile SDK**: 36 (unchanged)

**Android Minimum SDK**: 26 (unchanged)

**ATAK Compile SDK**: ATAK-CIV 5.7.0.9, retaining the accepted `main.jar`
SHA-256 and `javap -public` evidence from Features 011 and 014

**Minimum ATAK Runtime**: ATAK-CIV 5.5.0 (unchanged)

**ATAK API Evidence**: No new ATAK SDK seam. The existing public
`CoordinateEntryPane` / `CoordinateEntryCapability` evidence from ADR-0023 and
ADR-0024 remains sufficient; exact ATAK 5.5 device acceptance stays a release
gate.

**Project Type**: Single-module Android ATAK plugin

**Performance Goals**: Compact-form presentation and Address mode changes show
visible feedback within 100 ms p95 over 20 device repetitions; no new I/O or
allocation-sensitive state is introduced.

**Constraints**: One outer vertical scroll owner; no horizontal or nested
scrolling; existing 48 dp interaction targets; 8:2 Address content/action
geometry; two equal field groups per structured row; EN/zh-TW/JA parity;
offline-only; no behavior, permission, network, dependency, or coordinate
change; user-provided attachments remain untracked and uncommitted.

**Scale/Scope**: One XML layout, one focused layout test class, existing
native-entry regression suites, four version/document files, one UI reference,
and one changelog entry. No Java production class or persisted model change is
planned.

### Compatibility Matrix

| ATAK Line | Evidence Required | Planned Validation | Status |
|-----------|-------------------|--------------------|--------|
| 5.5 minimum runtime | Existing public source/API and manifest compatibility plus exact device layout/lifecycle journey | Install `1.5.1`, exercise both Address modes, orientations, locales, font scales 1.0/2.0, read-only, accessibility, and host controls | `[RELEASE-GATE]` pending exact device |
| 5.7.0.9 current runtime | Existing `javap -public` evidence plus current-device layout/lifecycle journey | Build, install, and exercise the same compact-layout matrix on the reference device | Build/API baseline exists; Feature 015 device journey pending |

## Constitution Check

*GATE: Passed before Phase 0 and re-checked after Phase 1.*

| Principle | Required Plan Evidence | Status |
|-----------|------------------------|--------|
| I. Code Quality & Build Discipline | Test-first focused suite followed by Spotless, full JVM tests, lint, Civ Debug assembly, docs checks, and `git diff --check` | PASS |
| II. Test-First Development & Verification | Update the existing four-row contract to fail on the current XML before changing production resources; retain device-only acceptance as release gates | PASS |
| III. UX, Accessibility & Localisation | One scroll owner, equal columns, 48 dp controls, logical row-major order, read-only state, EN/zh-TW/JA, TalkBack/Switch Access, and font-scale matrix | PASS |
| IV. Performance & Offline Operation | 100 ms p95 device budget; XML-only production change adds no I/O, network, telemetry, permission, or persistent state | PASS |
| V. Documentation & Decision Traceability | Update UI reference, both user guides, changelog, and version. No ADR: this is a reversible presentation refinement inside ADR-0023's existing architecture. | PASS |
| VI. Host-Process Isolation | No host boundary or callback change; existing field IDs and controller bindings remain intact; regressions cover lifecycle/read-only behavior | PASS |
| VII. ATAK SDK Compatibility | All four compatibility axes stated; no new seam; exact 5.5 and current-device layout evidence remain explicit release gates | PASS |
| VIII. Geospatial Correctness & Provenance | No parser, conversion, dataset, WGS84, ranking, zone, precision, or accuracy change; regression suites lock results | PASS |
| IX. Release Integrity & Provenance | Version synchronized to `1.5.1`; device/docs/signer/provenance work is marked `[RELEASE-GATE]`; no tag or publication is part of implementation | PASS |

### Post-design re-check

Phase 1 introduces only a view projection contract and validation guide. It
adds no new model, SDK seam, persistence, dependency, permission, network path,
or architecture decision. Every constitution gate remains PASS; public release
readiness remains separate from implementation convergence.

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
    ├── main/res/layout/taiwan_coordinate_entry_pane.xml
    └── test/java/com/atakmap/android/twcoord/nativeentry/
        ├── TaiwanAddressLayoutTest.java
        ├── TaiwanCoordinateEntryPaneContractTest.java
        ├── TaiwanInlineImeContractTest.java
        └── NativeEntryFeature014RegressionTest.java

docs/
├── ui/native-taiwan-coordinate-entry.md
├── user-guide.md
└── user-guide_zh.md

CHANGELOG.md
```

**Structure Decision**: Preserve the current view IDs and controller seam.
Add two row containers around the existing four field-group containers in the
single layout resource. This is the smallest change that expresses the
approved preview, keeps Java bindings stable, and lets Robolectric verify the
new geometry without creating a custom View or new layout resource.

## Complexity Tracking

No constitution exception or additional architectural complexity is required.
