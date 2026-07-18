# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its
definition describes the execution workflow.

## Summary

[Extract the primary user requirement and the chosen technical approach from
the feature specification and research.]

## Technical Context

<!--
  ACTION REQUIRED: Replace every placeholder with concrete project evidence.
  Mark unresolved items as NEEDS CLARIFICATION and resolve them in Phase 0.
-->

**Language/Version**: Java 17-compatible Android sources and Android resource
XML, or NEEDS CLARIFICATION

**Primary Dependencies**: ATAK-CIV SDK, Android framework, and existing
repository libraries; list additions or NEEDS CLARIFICATION

**Storage**: SharedPreferences, ATAK DatabaseIface/SQLite, imported files, or
N/A; identify ownership and lifecycle

**Testing**: JVM unit/contract tests, Android lint and package build, plus
on-device ATAK acceptance where host behaviour is involved

**Target Platform**: ATAK-CIV Android plugin; name reference devices and pane
sizes relevant to the feature

**Android Compile SDK**: Android API level, currently 36 unless changed by the feature

**Android Minimum SDK**: Android API level, currently 26 unless changed by the feature

**ATAK Compile SDK**: Pinned ATAK-CIV SDK version and `main.jar` evidence

**Minimum ATAK Runtime**: ATAK-CIV 5.5.0 unless superseded by an accepted ADR

**ATAK API Evidence**: `javap -public` anchors from the ATAK compile SDK plus
minimum-runtime source/API evidence for every new ATAK seam

**Project Type**: Single-module Android ATAK plugin unless the feature plan
explicitly justifies another structure

**Performance Goals**: Plugin-owned, measurable latency/frame/memory goals for
the affected user journeys, or N/A with rationale

**Constraints**: Offline operation, host-process isolation, localisation,
geospatial correctness, storage limits, and other feature constraints

**Scale/Scope**: Number of pages, SDK seams, coordinate systems, datasets,
counties, records, or other concrete scope measures

### Compatibility Matrix

<!--
  Include each ATAK line affected by a new SDK integration. Device-only checks
  remain incomplete tasks until executed; compiling is not runtime evidence.
-->

| ATAK Line | Evidence Required | Planned Validation | Status |
|-----------|-------------------|--------------------|--------|
| 5.5 minimum runtime | Public API/source and manifest compatibility | Build/install/lifecycle scenario | NEEDS CLARIFICATION |
| Current ATAK runtime matching the ATAK compile SDK | javap-public signature and source anchor | Build/install/lifecycle scenario | NEEDS CLARIFICATION |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluate every principle in `.specify/memory/constitution.md`. At minimum,
record evidence for:

| Principle | Required Plan Evidence | Status |
|-----------|------------------------|--------|
| I. Code Quality & Build Discipline | Applicable Spotless, lint, unit-test, and assemble gates | [PASS/FAIL with evidence] |
| II. Test-First Development & Verification | Test-first tasks plus JVM/contract/device coverage split | [PASS/FAIL with evidence] |
| III. UX, Accessibility & Localisation | Scroll owner, 48 dp targets, states, accessibility, and locale resources when UI changes | [PASS/N/A/FAIL] |
| IV. Performance & Offline Operation | Plugin-owned budgets, main-thread I/O policy, memory/network impact | [PASS/N/A/FAIL] |
| V. Documentation & Decision Traceability | Required docs and whether an architectural ADR decision exists | [PASS/N/A/FAIL] |
| VI. Host-Process Isolation | Host entry boundaries, context/resource ownership, validation, and re-entrancy | [PASS/N/A/FAIL] |
| VII. ATAK SDK Compatibility | Android compile/minimum SDKs, ATAK compile/minimum versions, public API anchors, lifecycle and device matrix | [PASS/N/A/FAIL] |
| VIII. Geospatial Correctness & Provenance | Datum/zone/units, vectors, error budget, or justified N/A | [PASS/N/A/FAIL] |

Non-negotiable failures MUST be resolved before implementation. Other
complexity exceptions MUST be recorded below.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── spec.md              # /speckit-specify output
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 decisions and evidence
├── data-model.md        # Phase 1 state/entities
├── quickstart.md        # Phase 1 runnable validation guide
├── contracts/           # Phase 1 interfaces, UI, formats, or lifecycle
├── checklists/          # Requirements-quality checklists
└── tasks.md             # /speckit-tasks output
```

### Source Code (repository root)

<!--
  ACTION REQUIRED: Keep only real paths touched by this feature and expand the
  relevant packages. Do not leave placeholder option labels.
-->

```text
app/
├── build.gradle
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── java/com/atakmap/android/twcoord/
    │   └── res/
    └── test/java/com/atakmap/android/twcoord/

docs/
├── adr/
└── ui/
```

**Structure Decision**: [Document the selected real directories, ownership
boundaries, and why no simpler existing seam is sufficient.]

## Complexity Tracking

> **Fill ONLY if a non-NON-NEGOTIABLE Constitution Check item requires a
> justified exception. Non-negotiable violations cannot be waived here.**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [specific exception] | [current need] | [why the simpler compliant option is insufficient] |
