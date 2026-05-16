# Implementation Plan: Taiwan Coordinate Display Plugin for ATAK

**Branch**: `001-tw-coord-display` | **Date**: 2026-05-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-tw-coord-display/spec.md`

## Summary

Build an ATAK-CIV 5.7.0.3 plugin (Android, Java 17) that renders two
persistent on-map readouts — the *map centre* coordinate and the *device
own-position* coordinate — in one of three Taiwan coordinate systems:
Taipower grid, TWD97 (TM2 z121), TWD67 (TM2 z121). A standard ATAK
preference fragment lets the user toggle the active unit and override the
UI language (system / English / 中文（正體） / 日本語); both readouts
repaint live on either change. Clipboard copy on tap is mandatory.

Technical approach (after research, see `research.md`):

- **Plugin scaffold** mirrors the `meshtastic_atak` SDK sample:
  `AbstractPlugin` lifecycle + `plugin.xml` registering `IPlugin`, built
  with the `atak-gradle-takdev` Gradle plugin. Standard ATAK signing.
- **Coordinate math** lives in a pure-Java `coord/` package with zero
  Android dependencies (so it can be unit-tested on the JVM, per
  Constitution Principle II): `proj4j` for TWD97 (matching pwa_map's
  EPSG:3826 proj-string verbatim), hand-rolled 4-parameter datum shift
  for TWD67 (Δx=807.8, Δy=248.6, a=1.549e-5, b=6.521e-6), hand-rolled
  grid arithmetic for the Taipower system over a TWD67 base.
- **On-map readout** is a custom `MapWidget` anchored in
  `RootLayoutWidget` (top-right by default), populated each frame from
  `MapEvent.MAP_BOUNDS_CHANGED` (centre) and `MapEvent.ITEM_CHANGED` on
  `MapView.getSelfMarker()` (self), with a 1 Hz debounce on the self
  stream to satisfy FR-008 without thrashing.
- **TDAL is intentionally not used.** Research confirmed the TDAL plugin
  and its `coordinate_systems.xml` are NOT shipped with ATAK-CIV 5.7.0.3;
  also, Taipower grid is not an EPSG CRS so TDAL could only ever cover
  two of the three units. Doing all three through one in-plugin overlay
  is simpler, gives us one code path, and removes a deployment step for
  end users.
- **Localisation** uses standard Android resource folders:
  `res/values/strings.xml` (English default), `res/values-zh-rTW/`
  (Traditional Chinese), `res/values-ja/` (Japanese). Locale-override
  preference is realised by wrapping the plugin `Context` with
  `createConfigurationContext(Configuration)` whenever the override is
  applied, so the on-map widget repaints in the new language without an
  ATAK restart (FR-018).
- **No outbound network**, no telemetry SDK, no `INTERNET` permission
  declared (FR-019, FR-020).

## Technical Context

**Language/Version**: Java 17 (matching `meshtastic_atak` reference; Kotlin
not adopted to minimise dependency surface and ProGuard friction)

**Primary Dependencies**:
- ATAK-CIV 5.7.0.3 SDK (`main.jar`) — provided
- `atak-gradle-takdev` Gradle plugin — provided in SDK
- `org.locationtech.proj4j:proj4j:1.3.x` — for EPSG:3826 (TWD97)
  projection; no Android transitive dependencies
- Android `core-ktx` / `appcompat` etc. **explicitly excluded** to match
  ATAK's bundled AndroidX versions (see `meshtastic_atak` build.gradle
  precedent)
- JUnit 4 + Truth (or AssertJ) for unit tests
- AndroidX Test + Espresso for instrumented tests (constrained to
  versions ATAK already bundles)

**Storage**: Android `SharedPreferences` only, holding two keys —
`pref_coord_unit` (string enum: `TAIPOWER` | `TWD97` | `TWD67`) and
`pref_ui_language` (string: `SYSTEM` | `en` | `zh-TW` | `ja`). No
location history, no coordinate history (FR-020).

**Testing**:
- Unit tests (pure JVM, no Android): `coord/`, `i18n/`, `Formatter` —
  golden vectors from pwa_map (Taipei 101, Kaohsiung 85, Taichung,
  Hualien); TDD per Constitution II
- Instrumented tests (Android, `androidTest`): preference fragment +
  widget rendering + live unit-switch repaint
- Manual acceptance: per `quickstart.md`, executed on the reference
  device against ATAK-CIV 5.7.0.3 install

**Target Platform**: Android, `minSdkVersion 26`, `targetSdkVersion 34`,
`compileSdk 36`. Runtime: ATAK-CIV 5.7.0.3 and compatible patch
releases.

**Project Type**: Single Android-app project (ATAK plugin variant); see
Project Structure section.

**Performance Goals** (from spec SC-002 / SC-007):
- ≥ 95 % of map-centre readout updates land ≤ 100 ms after `MAP_BOUNDS_
  CHANGED`; absolute worst-case ≤ 250 ms.
- ≤ 1 fps median frame-rate drop versus baseline ATAK install.
- Coordinate conversion latency itself is microsecond-scale (pure CPU);
  the budget is dominated by event dispatch + widget invalidate.

**Constraints**:
- Offline-only (FR-014). No `INTERNET` permission.
- No analytics / crash-reporting SDK (FR-019).
- AndroidX deps must be excluded or pinned to ATAK-bundled versions.
- No lambdas in release-built code paths that get reflected over by
  ProGuard (SDK README warning); use SAM interfaces.
- All map / widget callbacks run on the UI thread; do not block.

**Scale/Scope**:
- One Android app module, ~12 production Java classes.
- 3 coordinate systems × 3 UI languages × 2 readouts.
- Approx. 1–2 k LOC production, similar test LOC due to golden vectors.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence / Notes |
|---|---|---|---|
| I | Code Quality & Formatting Discipline | **PASS** | Use `google-java-format` (Java equivalent to `dart format`) on every change; enforced via Gradle `spotless` task and pre-commit hook. `./gradlew lint` MUST be clean. |
| II | Test-First Development (TDD) | **PASS** | Pure-JVM unit tests for `coord/` and `i18n/` will be authored first using the four pwa_map golden vectors (Taipei 101, Kaohsiung 85, Taichung, Hualien); integration tests cover preference fragment + widget repaint; instrumented Espresso tests for clipboard tap. |
| III | UX Consistency | **PASS** | Use ATAK-standard `Pan*Preference` widgets; overlay anchored in `RootLayoutWidget` like reference plugins; localisation uses standard `values-*/strings.xml`. `docs/ui/` will document the readout layout and the language toggle. |
| IV | Performance Requirements | **PASS** | Conversion math is O(1) microseconds; map event listener and widget render path are the canonical ATAK pattern that other plugins use without measurable fps impact. 1 Hz debounce on self-marker stream guards against update storms. |
| V | Documentation & Knowledge Preservation | **PASS** | All committed docs in English. `docs/adr/0001-coordinate-math-source.md` will be authored on first `/speckit-implement` capturing the pwa_map provenance and the TDAL-not-used decision. `docs/ui/` will be authored alongside the widget. |

**Pre-Phase-0 gate**: **PASS** — no violations; Complexity Tracking is empty.

**Post-Phase-1 re-check** (after `research.md`, `data-model.md`,
`contracts/*.md`, `quickstart.md`): **PASS**.

| # | Principle | Status | Post-design evidence |
|---|---|---|---|
| I | Code Quality & Formatting | **PASS** | `gradle spotlessApply` documented in `quickstart.md` §5; pre-commit hook to enforce. |
| II | TDD | **PASS** | `contracts/coordinate-converter.md` and `contracts/coordinate-formatter.md` lock in golden-vector tests and clipboard-equality tests respectively; both layers are pure JVM. |
| III | UX Consistency | **PASS** | `contracts/widget-overlay.md` and `contracts/preference-store.md` commit to standard ATAK widget primitives. `docs/ui/` placeholder seeded in Project Structure. |
| IV | Performance | **PASS** | `contracts/coordinate-converter.md` sets a micro-bench bound of ≤ 50 μs per `convert()`; the self-marker stream is debounced to 1 Hz per `research.md` R4; SC-002 ≤ 100 ms remains conservative. |
| V | Documentation & Knowledge Preservation | **PASS** | All Phase-0 / Phase-1 docs are English. `quickstart.md` §8 mandates the ADR cadence per Principle V. |

No new violations introduced by the design pass. Complexity Tracking
remains empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-tw-coord-display/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan output)
├── data-model.md        # Phase 1 output (/speckit-plan output)
├── quickstart.md        # Phase 1 output (/speckit-plan output)
├── contracts/           # Phase 1 output (/speckit-plan output)
│   ├── coordinate-converter.md
│   ├── coordinate-formatter.md
│   ├── preference-store.md
│   └── widget-overlay.md
├── checklists/
│   └── requirements.md  # From /speckit-specify + /speckit-clarify
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
atak_tw_power_plugin/
├── app/
│   ├── build.gradle
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── assets/
│   │   │   │   └── plugin.xml             # IPlugin extension registration
│   │   │   ├── java/com/atakmap/android/twpower/
│   │   │   │   ├── TwPowerLifecycle.java        # extends AbstractPlugin
│   │   │   │   ├── TwPowerMapComponent.java     # map listeners, widget mgmt
│   │   │   │   ├── TwPowerWidget.java           # MapWidget subclass (the readout)
│   │   │   │   ├── TwPowerPreferenceFragment.java
│   │   │   │   ├── prefs/PreferenceStore.java   # typed wrapper around SharedPreferences
│   │   │   │   ├── coord/
│   │   │   │   │   ├── CoordinateUnit.java       # enum
│   │   │   │   │   ├── Wgs84.java                # value class
│   │   │   │   │   ├── Twd97Tm2.java
│   │   │   │   │   ├── Twd67Tm2.java
│   │   │   │   │   ├── TaipowerCode.java
│   │   │   │   │   ├── Projections.java          # proj4j wrapper (EPSG:3826)
│   │   │   │   │   ├── DatumShiftTwd67.java      # 4-param shift
│   │   │   │   │   ├── TaipowerGrid.java         # grid arithmetic
│   │   │   │   │   ├── CoordinateConverter.java  # facade
│   │   │   │   │   └── Formatter.java            # CoordinateUnit → display string
│   │   │   │   └── i18n/
│   │   │   │       └── LocaleOverride.java       # wraps Context with chosen locale
│   │   │   └── res/
│   │   │       ├── values/strings.xml             # English (default)
│   │   │       ├── values-zh-rTW/strings.xml      # Traditional Chinese (Taiwan)
│   │   │       ├── values-ja/strings.xml          # Japanese
│   │   │       ├── xml/preferences.xml
│   │   │       └── drawable/ic_tw_power.xml
│   │   ├── test/java/com/atakmap/android/twpower/
│   │   │   └── coord/
│   │   │       ├── ProjectionsTest.java          # TWD97 golden vectors
│   │   │       ├── DatumShiftTwd67Test.java      # TWD97↔TWD67 golden vectors
│   │   │       ├── TaipowerGridTest.java         # Taipower golden vectors
│   │   │       ├── CoordinateConverterTest.java  # end-to-end facade
│   │   │       └── FormatterTest.java            # display string fidelity
│   │   └── androidTest/java/com/atakmap/android/twpower/
│   │       ├── PreferenceFragmentTest.java       # unit-switch repaint
│   │       ├── WidgetRenderTest.java             # overlay rendering
│   │       └── ClipboardCopyTest.java            # FR-015 fidelity
│   └── proguard-gradle.txt
├── build.gradle
├── settings.gradle
├── gradle.properties                              # PLUGIN_VERSION, ATAK_VERSION
├── gradle/wrapper/...
├── docs/
│   ├── adr/                                       # populated by /speckit-implement onward
│   │   └── README.md
│   └── ui/                                        # populated alongside UI changes
│       └── README.md
└── specs/
    └── 001-tw-coord-display/
        └── ...
```

**Structure Decision**: Single Android app module under `app/`, following
the ATAK plugin convention demonstrated by `meshtastic_atak`. Pure
coordinate / i18n logic lives under `app/src/main/java/.../coord/` and
`.../i18n/` so it can be unit-tested on the JVM without an Android
runtime — this is what makes TDD (Principle II) practical for the bulk
of the codebase. Android-coupled classes (Lifecycle, MapComponent,
Widget, PreferenceFragment) are intentionally thin shells delegating to
the pure layer.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No Constitution Check violations. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
