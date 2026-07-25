# Development Guide

This guide covers local configuration, build and test commands, repository
structure, and the project's Spec Kit workflow. Release publication has
additional gates; see [Release Readiness](release-readiness.md).

## Prerequisites

- JDK 17.
- Android Studio with Android SDK platforms 34 and 36 and build-tools 34.0.0
  or newer.
- ATAK-CIV 5.7.0.9 SDK unpacked locally.
- Git and Git LFS.

The build currently uses Android compile SDK 36, target SDK 34, and minimum
SDK 26. ATAK APIs compile against ATAK-CIV 5.7.0.9 while the manifest declares
ATAK-CIV 5.5.0 as the minimum runtime.

## Local configuration

Create or update the uncommitted `local.properties`:

```properties
sdk.dir=<ANDROID_SDK>
sdk.path=<ATAK_CIV_5_7_0_9_SDK>
takdev.plugin=<ATAK_CIV_5_7_0_9_SDK>/atak-gradle-takdev.jar
```

Do not commit local SDK paths, usernames, device identifiers, credentials, or
signing material.

## Common commands

From PowerShell:

```powershell
.\gradlew.bat :app:spotlessApply
.\gradlew.bat :app:spotlessCheck
.\gradlew.bat :app:lint
.\gradlew.bat :app:testCivDebugUnitTest
.\gradlew.bat :app:assembleCivDebug
```

The debug APK is written under:

```text
app/build/outputs/apk/civ/debug/
```

Its filename includes the plugin version, source hash, minimum ATAK runtime,
flavor, and build type.

Documentation-only changes use proportionate validation, including applicable
link/image checks and `git diff --check`.

## Install a debug build

With exactly one authorized Android device:

```powershell
adb devices -l
adb install -r <DEBUG_APK>
```

Reinstalling does not guarantee ATAK reloads an already-active plugin. Disable
and re-enable TW Coordinates or restart ATAK before collecting acceptance
evidence. Never commit device serials, callsigns, locations, raw logcat, or
workstation paths.

## Repository layout

```text
.
├── app/                         # Android plugin module
│   └── src/
│       ├── main/
│       │   ├── java/com/atakmap/android/twcoord/
│       │   │   ├── address/    # imported data, lookup, boundaries, and manager
│       │   │   ├── coord/      # coordinate math, parsing, and formatting
│       │   │   ├── nativeentry/# ATAK native Taiwan pane and dialogs
│       │   │   ├── plugin/     # lifecycle and the one Tools item
│       │   │   └── prefs/      # typed preferences
│       │   ├── assets/         # plugin metadata and offline reference assets
│       │   └── res/            # layouts, drawables, strings, and preferences
│       └── test/                # JVM and Robolectric coverage
├── docs/                        # user, UI, reference, ADR, research, and release docs
├── scripts/                     # documentation, release, fixture, and audit tools
├── specs/                       # Spec Kit feature artifacts
├── test-data/                   # provenance-recorded coordinate fixtures
├── CHANGELOG.md
└── .specify/memory/constitution.md
```

Start documentation navigation at [`docs/README.md`](../README.md). Resolve
the active feature from `.specify/feature.json`; do not infer it from the
largest feature number.

## Feature history

| Directory | Responsibility |
|---|---|
| `specs/001-tw-coord-display/` | On-map coordinate readouts |
| `specs/002-tw-coord-goto/` | Historical standalone GoTo page |
| `specs/003-custom-marker-icon/` | Historical custom marker workflow |
| `specs/004-offline-address/` | Reverse offline address and data foundation |
| `specs/005-multi-county-zip-import/` | Multi-county registry and ZIP import |
| `specs/006-county-forward-search/` | Forward lookup and county scoping foundations |
| `specs/007-settings-ux-tweaks/` | Settings and result-ordering updates |
| `specs/008-search-settings-ui/` | Historical standalone search/storage redesign |
| `specs/010-goto-ui-redesign/` | Historical standalone GoTo redesign |
| `specs/011-native-coordinate-entry/` | ATAK native Taiwan coordinate entry |
| `specs/012-prefill-native-tabs/` | Native tab prefill and safety |
| `specs/013-native-address-entry/` | Native Address, locality selectors, and Tools consolidation |

Historical specs remain evidence. Current operator behavior is documented in
the user guides and current UI contracts.

## Spec Kit workflow

```text
specify
  → clarify
  → plan
  → checklist (optional)
  → tasks
  → analyze
  → implement
  → converge
  → release-readiness
```

- `analyze` is read-only.
- Behavior changes use test-first tasks and record Red → Green → Refactor
  evidence.
- ATAK SDK seams require public API evidence and minimum/current runtime
  scenarios.
- `converge` may append remaining work; repeat implementation and convergence
  until no actionable gaps remain.
- A converged implementation or successful TPP build is not automatically
  public-release ready.
- Unresolved device, compatibility, performance, signer, documentation, and
  provenance work remains an explicit `[RELEASE-GATE]`.

The [project constitution](../../.specify/memory/constitution.md) is the
authoritative source for quality, compatibility, geospatial, privacy, and
release requirements.

## Architecture and external references

- [ADR index and supersession map](../adr/README.md).
- [ATAK-CIV upstream source](https://github.com/TAK-Product-Center/atak-civ).
- [ATAK Plugin Development Guide](https://github.com/TAK-Product-Center/atak-civ/blob/main/ATAK_Plugin_Development_Guide.pdf).
- [Proj4J](https://github.com/locationtech/proj4j).
- [Documentation image workflow](../images/README.md).
- [TPP release runbook](../release/tpp-runbook.md).
