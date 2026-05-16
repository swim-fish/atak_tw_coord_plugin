# Implementation Plan: Taiwan Coordinate Input ("GoTo") Page

**Branch**: `002-tw-coord-goto` | **Date**: 2026-05-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-tw-coord-goto/spec.md`

## Summary

Add a second user-facing affordance to the existing
`atak_tw_coord_plugin`: a **Taiwan coordinate input page** that lets a
field operator type a Taipower / TWD97 / TWD67 string, optionally
**Auto Fill** the current map centre into the field, and then pan
ATAK to the resolved point with a single dropped marker — same end
behaviour as ATAK's native Tools > GoTo, but sourced from the units
Taiwan operators actually receive over radio or paper.

Technical approach: reuse the existing forward-conversion pipeline
(`Projections`, `DatumShiftTwd67`, `TaipowerGrid`,
`CoordinateConverter`) and add a parallel **inverse-converter**
layer (`CoordinateParser`) that consumes a normalised string +
explicit unit selector and returns a validated `Wgs84`. The page itself
is implemented as an ATAK `DropDownReceiver` (consistent with native
ATAK input pages such as `EnterLocationDropDownReceiver`) rather than a
free-floating `Activity`; this preserves the multi-pane workspace
behaviour operators already expect. A new Tools-menu icon
(`com.atakmap.android.twcoord.SHOW_GOTO`) registered alongside the
existing `SHOW_PLUGIN` action opens the page; the existing settings
page gains a button to the same `DropDownReceiver` so the page has
two entry points (FR-016). All persistence (last-submitted value,
recent entries, ephemeral input page state) uses the existing
`PreferenceStore` plus a tiny JSON-encoded list keyed off
`SharedPreferences`. Auto Fill subscribes to the same map-centre
event stream the readout widget already consumes
(`SelfMarkerSubscriber` / `MapEvent.MAP_*` family); a one-pass enable
check on every event keeps the button's disabled-state in sync with
the map centre's representability in the active tab (FR-022).

## Technical Context

**Language/Version**: Java 17 (host plugin module).

**Primary Dependencies**:
- ATAK-CIV 5.7.0.3 SDK (`atak-gradle-takdev` 3.+, compile target
  declared at runtime-compat `5.4.0.CIV` per ADR-0007 of feature 001).
- `org.locationtech.proj4j:proj4j:1.3.0` (reused; same `+lon_0=121` and
  `+lon_0=119` `CoordinateTransform` instances are used for the
  inverse direction).
- Android Gradle Plugin 8.13, AndroidX (`core 1.17.0`, `fragment
  1.8.9`, `lifecycle 2.9.4` per existing resolutionStrategy).
- Spotless 6.25 + google-java-format 1.22 (formatter is a build
  dependency per Constitution Principle I).

**Storage**: Android `SharedPreferences` (file
`tw_coord_settings`, already in use). New keys:
- `pref_goto_last_unit` (enum name).
- `pref_goto_last_taipower` / `pref_goto_last_twd97_e` /
  `pref_goto_last_twd97_n` / `pref_goto_last_twd97_zone` /
  `pref_goto_last_twd67_e` / `pref_goto_last_twd67_n` /
  `pref_goto_last_twd67_zone`.
- `pref_goto_recent_json` — JSON-encoded `RecentEntry[]`, capacity 10.

**Testing**:
- JVM unit tests: JUnit 4.13.2 + AssertJ 3.27.3 (existing inner
  loop). All parser, validation, formatter, and recent-store logic is
  pure Java with golden-vector tests against
  `test-data/taiwan_cities_coords.csv`.
- Instrumented tests: AndroidX Test + Espresso 3.5.1 (existing
  configuration) for the `DropDownReceiver` and the Auto Fill
  disabled-state propagation.
- TDD discipline per Constitution Principle II: parser tests + widget
  contract tests authored before implementation; manual acceptance on
  Galaxy Tab S10+ for ATAK-specific lifecycle paths that can't be
  black-box-tested off-device.

**Target Platform**: ATAK-CIV 5.7.0.3 (compatibility declared at
`com.atakmap.app@5.4.0.CIV` for broad device support); Android
`minSdk 26`, `target 34`, `compileSdk 36`; ABI `arm64-v8a` for
device, `armeabi-v7a, arm64-v8a, x86` for non-bundle builds.

**Project Type**: Android plugin module (single `app/` Gradle module).
This feature extends the existing module — no new Gradle subproject.

**Performance Goals** (from spec SC-002 / SC-004 / SC-009):
- Submit → marker rendered ≤ **300 ms** median on Galaxy Tab S10+.
- Inline validation ≤ **100 ms** after last keystroke.
- Auto Fill button disabled-state updates ≤ **one map-event cycle**.
- All UI on the main thread MUST hold ≥ 60 fps (Constitution IV).

**Constraints**:
- **Offline-capable**: no outbound network (FR-015, inherits feature
  001 zero-telemetry posture).
- **Thread-safe converters**: Auto Fill listener fires off
  `MapEvent` callbacks, which can be invoked from a non-UI thread in
  some ATAK versions; the validation pipeline MUST tolerate that.
- **No new dependencies**: reuse proj4j 1.3.0 from feature 001.

**Scale/Scope**:
- One new `DropDownReceiver` + one new Tools-menu icon registration.
- One new package (`com.atakmap.android.twcoord.goto`) containing
  ~6 new classes: `CoordinateParser` (+3 unit-specific sub-parsers),
  `TwCoordGotoReceiver`, `TwCoordGotoView`, `MapCenterAutoFillStream`,
  `RecentEntryStore`.
- ~80 new unit tests (parser × 22 city × 3 units + round-trip ×
  Auto Fill state machine + recent-store).
- ~6 new layout XMLs, ~30 new string-resource entries × 3 locales
  (en / zh-rTW / ja).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | **Code Quality & Formatting** (NON-NEGOTIABLE) | **PASS** | Spotless + google-java-format already enforced in `app/build.gradle`; the build fails on unformatted code. No new format/lint surface is introduced. |
| II | **TDD** (NON-NEGOTIABLE) | **PASS** | Parser, validator, recent-store, and Auto Fill state machine are pure Java with deterministic inputs; tests authored first per the existing pattern. Golden vectors reuse `taiwan_cities_coords.csv`. UI lifecycle paths covered by Espresso. |
| III | **UX Consistency** | **PASS** | The page is a `DropDownReceiver`, the standard ATAK input-page idiom (mirrors `EnterLocationDropDownReceiver`). Tools-menu icon registers alongside the existing one. Strings are externalised across `strings.xml` / `values-zh-rTW` / `values-ja`. New `docs/ui/input-page.md` entry will be authored alongside the layout XMLs (Principle III mandate). |
| IV | **Performance** | **PASS with measurement obligation** | Spec SC-002 (300 ms submit), SC-004 (100 ms validation), SC-009 (one map-event cycle for Auto Fill disable) are explicit; instrumented test will time them on the reference device. Auto Fill state updates are debounced to the same `SelfMarkerSubscriber` debounce window already in use (16 ms or ATAK's natural pan/scroll event cadence). |
| V | **Documentation & Knowledge Preservation** | **PASS** | English-only artefacts (spec / plan / research / data-model / contracts / quickstart / future ADRs). Each `/speckit-analyze` and `/speckit-implement` cycle produces an ADR per Constitution V; first ADR for this feature lands as `docs/adr/0009-tw-coord-goto-input-page.md` after `/speckit-implement`. `docs/ui/input-page.md` will document the new DropDown layout. |

**Workflow gates** (Development Workflow & Quality Gates section):
- Subagent delegation: planning and research used subagents for
  ATAK SDK reconnaissance (see Phase 0). PASS.
- Formatter: `./gradlew spotlessApply` will be run after every code
  modification (Constitution Principle I requirement).
- Definition of Done: extended in tasks.md (next phase) to include
  ADR, docs/ui, performance gate, and formatter pass.

**Result**: No violations. No entries are needed in
**Complexity Tracking**.

## Project Structure

### Documentation (this feature)

```text
specs/002-tw-coord-goto/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── coordinate-parser.md       # inverse-converter contract
│   ├── goto-receiver.md           # DropDown + Intent + map handle
│   └── recent-store.md            # persistence schema
├── checklists/
│   └── requirements.md  # produced by /speckit-specify
└── tasks.md             # Phase 2 output (NOT created by /speckit-plan)
```

### Source Code (repository root)

The plugin is a single Android module `app/`. This feature adds one
new sub-package and one new test sub-package; existing files in
`com.atakmap.android.twcoord.coord.*` and
`com.atakmap.android.twcoord.prefs.*` are reused unmodified for the
forward direction, and gain a sibling
`com.atakmap.android.twcoord.goto.*` package for the inverse +
input-page surface.

```text
app/src/main/java/com/atakmap/android/twcoord/
├── coord/                     # existing — forward converters (reused)
│   ├── CoordinateConverter.java
│   ├── Projections.java
│   ├── DatumShiftTwd67.java
│   ├── TaipowerGrid.java
│   ├── Twd97Tm2.java / Twd67Tm2.java / TaipowerCode.java
│   ├── Wgs84.java / ConversionResult.java / DisplayLine.java / Formatter.java
│   └── CoordinateUnit.java
├── goto/                      # NEW package for this feature
│   ├── CoordinateParser.java          # public facade: string → Wgs84
│   ├── TaipowerParser.java            # private — 9/11-char Taipower
│   ├── TwdTm2Parser.java              # private — TWD97 + TWD67 easting/northing
│   ├── ParseResult.java               # sealed-ish Ok | Invalid(reason) | OutOfRange
│   ├── TwCoordGotoReceiver.java       # ATAK DropDownReceiver subclass
│   ├── TwCoordGotoView.java           # Android View root + per-tab controllers
│   ├── MapCenterAutoFillStream.java   # MAP_* event → ParseResult publisher
│   ├── RecentEntryStore.java          # JSON-encoded SharedPreferences list
│   ├── RecentEntry.java               # immutable value class
│   └── DestinationMarkerStore.java    # single-marker move-not-create handle
├── i18n/                      # existing — locale override (reused)
├── plugin/
│   ├── TwCoordLifecycle.java          # existing — extend to register
│   │                                  #   TwCoordGotoReceiver
│   └── TwCoordTool.java               # existing — extend with second
│                                      #   Tools-menu icon SHOW_GOTO
├── prefs/
│   ├── PreferenceStore.java           # existing — extend with goto_* keys
│   └── UserPreference.java            # existing
├── SelfMarkerSubscriber.java          # existing — pattern reused for
│                                      #   MapCenterAutoFillStream debouncer
├── TwCoordMapComponent.java           # existing — wiring point for the new
│                                      #   receiver registration
├── TwCoordPreferenceFragment.java     # existing — add an "Open Coordinate
│                                      #   Input" button bound to SHOW_GOTO
└── TwCoordWidget.java                 # existing — untouched

app/src/main/res/
├── drawable/
│   ├── ic_tw_coord.xml                # existing — unit cycle icon
│   └── ic_tw_coord_goto.xml           # NEW — pin/target icon for second Tools icon
├── layout/
│   ├── pref_item.xml / pref_warning_item.xml / pref_category.xml  # existing
│   └── tw_coord_goto.xml              # NEW — input page root (tabs + Auto Fill + submit)
├── values/strings.xml                 # existing — add ~30 new keys
├── values-zh-rTW/strings.xml          # existing — add zh-TW translations
├── values-ja/strings.xml              # existing — add ja translations
└── xml/preferences.xml                # existing — add the open-input-page entry

app/src/main/assets/
└── plugin.xml                         # existing — declares TwCoordLifecycle

app/src/test/java/com/atakmap/android/twcoord/goto/
├── TaipowerParserTest.java            # NEW
├── TwdTm2ParserTest.java              # NEW
├── CoordinateParserRoundTripTest.java # NEW — 22-city authoritative round trip
├── MapCenterAutoFillStreamTest.java   # NEW — fake event source
└── RecentEntryStoreTest.java          # NEW — JSON round-trip + capacity

app/src/androidTest/java/com/atakmap/android/twcoord/goto/
├── TwCoordGotoReceiverTest.java       # NEW — DropDown open/close lifecycle
└── AutoFillDisabledStateTest.java     # NEW — pan → button state propagation

docs/
├── adr/
│   ├── 0001 … 0008                    # existing — feature 001 ADRs
│   └── 0009-tw-coord-goto-input-page.md  # NEW — authored after /speckit-implement
└── ui/
    └── input-page.md                  # NEW — DropDown layout, tab anatomy,
                                       #   Auto Fill button states, screenshots
```

**Structure Decision**: extend the existing single-module Android
plugin layout rather than introduce a new Gradle module. The
inverse-converter logic is co-located with the existing forward
converters but in a new `goto` sub-package so the dependency
direction stays one-way (`goto/` depends on `coord/`, never the
other way round). `DropDownReceiver` is the canonical ATAK pattern
for input pages; using it preserves UX consistency (Constitution III).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations. Table intentionally empty.
