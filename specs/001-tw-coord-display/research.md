# Phase 0 Research — Taiwan Coordinate Display Plugin

**Date**: 2026-05-16 | **Feature**: 001-tw-coord-display | **Plan**: [plan.md](./plan.md)

This document consolidates the findings from two parallel research agents
(ATAK SDK scaffolding; pwa_map coordinate math) plus the implementation
decisions derived from them. Format per the spec-kit convention:
*Decision* / *Rationale* / *Alternatives considered*.

---

## R1. Language and toolchain

**Decision**: Java 17, `compileSdk 36`, `minSdk 26`, `targetSdk 34`,
matching the `meshtastic_atak` SDK sample exactly.

**Rationale**: The reference sample is the most direct precedent for
ATAK-CIV 5.7.0.3 plugin construction. Java 17 also avoids the Kotlin
metadata footprint (which is rarely worth its size for a small plugin)
and matches what `atak-gradle-takdev` is tested with.

**Alternatives considered**:
- *Kotlin*: ergonomic, but adds `kotlin-stdlib` and a ProGuard surface;
  no other ATAK sample we inspected uses it; sole upside (data classes)
  is outweighed by the dependency cost on a plugin this small.
- *Java 11*: works, but Java 17 is the SDK sample's target — staying
  on the well-trodden version is the lowest-risk choice.

---

## R2. Plugin scaffolding

**Decision**: Mirror `meshtastic_atak` layout — `AbstractPlugin`
lifecycle, `plugin.xml` registering `gov.tak.api.plugin.IPlugin`,
`atak-gradle-takdev` Gradle plugin, standard `MapComponent` registered
inside the lifecycle. Output APK named
`ATAK-Plugin-atak_tw_coord_plugin-{version}-{variant}-{atakVersion}.apk`
per the Gradle plugin's defaults.

**Rationale**: This is the documented and most-tested path. Deviating
costs us SDK-update churn.

**Alternatives considered**: None viable — `IPlugin` is the plugin
contract.

**Citations** (from subagent A):
- `meshtastic_atak/app/src/main/assets/plugin.xml:6-8`
- `meshtastic_atak/app/src/main/java/.../MeshtasticLifecycle.java:10-14`
- `meshtastic_atak/app/build.gradle:102-103, 224-226`

---

## R3. Map-centre coordinate stream

**Decision**: `MapView.getMapView().getMapEventDispatcher().
addMapEventListener(MapEvent.MAP_BOUNDS_CHANGED, this)`; pull the
centre from `mapView.getCenterPoint()` (or equivalent latitude/longitude
accessor) inside `onMapEvent`. Re-render the widget on each event with
no debounce, since math is microseconds and the event fires only on
actual change.

**Rationale**: This is the canonical ATAK pattern used in the
`helloworld` sample (`HelloWorldMapComponent.java:654-655`,
`RecyclerViewDropDown.java:59-61, 113`). Spec SC-002 requires 95 % of
updates within 100 ms — well within budget for direct dispatch.

**Alternatives considered**:
- *Polling at ~10 Hz*: wastes CPU and battery; rejected.
- *Listening to `MAP_VIEWPOINT_CHANGED` instead*: candidate event,
  but `MAP_BOUNDS_CHANGED` is the more conservative choice (fires for
  pan, zoom, and rotate). Will validate during implementation.

---

## R4. Own-position coordinate stream

**Decision**: `MapView.getMapView().getSelfMarker()` for the marker
reference; subscribe with `MapEvent.ITEM_CHANGED` filtered on the
self-marker UID. Apply a **1 Hz debounce** before reformatting — the
self-marker can update faster than the human eye can read, and SC of
FR-008 only requires ≥ 1 Hz. Track the last-update timestamp; if no
update for the configured stale threshold (default 10 s), emit the
"no fix" state (FR-010).

**Rationale**: Matches the `selfmarkerdata` sample pattern
(`SelfMarkerDataMapComponent.java:81, 95-132`). The 1 Hz throttle
satisfies the spec while avoiding overdraw.

**Alternatives considered**:
- *Reading `MapData.getDouble("selfLocation...")` directly*: brittle —
  the metadata keys are not part of the stable plugin API.
- *Subscribing to Android `LocationManager` directly*: would require
  duplicate location permission and runs a parallel GPS pipeline ATAK
  already maintains; rejected (also violates spec assumption that the
  plugin reuses ATAK's self-marker stream).

---

## R5. On-map readout overlay

**Decision**: Custom `MapWidget` subclass (`TwCoordWidget`) added to
`RootLayoutWidget.getLayout(RootLayoutWidget.TOP_RIGHT)`. The widget
renders a two-line text block (line 1 = "MAP: <coord>", line 2 = "ME:
<coord>") with the active unit label. Tappable: on tap, copy the
displayed value to clipboard via Android `ClipboardManager` and show a
brief Toast (FR-015).

**Rationale**: Pattern is directly modelled on
`meshtastic_atak/MeshtasticWidget.java:24-31` and
`helloworld/HelloWorldWidget.java:26-79`. `TOP_RIGHT` chosen so the
readout does not overlap the ATAK target-of-interest bezel at the
bottom; final anchor will be confirmed via `docs/ui/` once we view it
on device.

**Alternatives considered**:
- *DropDown panel*: too heavyweight; the user wants always-on, not
  on-demand.
- *Inflated XML layout via `LayoutInflater`*: works, but the SDK's
  `MapWidget` family is the documented overlay primitive and gives us
  free clipping / DPI handling.

---

## R6. Preference fragment & live re-render

**Decision**: Use ATAK's `ToolsPreferenceFragment.register(...)` to
expose a single preference screen with two `PanListPreference`
entries: coordinate unit and UI language override. Implement
`SharedPreferences.OnSharedPreferenceChangeListener` on the
`MapComponent`; on change of either key, call
`widget.invalidate()` (for unit) or
`widget.refreshLocale(LocaleOverride.contextFor(newLocale))` (for
language).

**Rationale**: The exact pattern used in
`meshtastic_atak/MeshtasticMapComponent.java:272, 307-315, 939-960`,
which itself follows ATAK SDK guidance. The change-listener path makes
FR-018 (immediate repaint, no restart) trivial.

**Alternatives considered**:
- *Custom `Activity` for settings*: violates Principle III (UX
  consistency) — would not match other ATAK plugins' look-and-feel.

---

## R7. Localisation strategy

**Decision**: Standard Android resource folders:
- `res/values/strings.xml` → English (default fallback)
- `res/values-zh-rTW/strings.xml` → Traditional Chinese (Taiwan)
- `res/values-ja/strings.xml` → Japanese

For the **fallback chain** (any `zh-*` → zh-TW; any `ja-*` → ja; else
→ en) from clarification Q2, rely on Android's automatic resource
resolution, supplemented by an explicit `LocaleOverride` helper that
maps `Locale.getLanguage()` to one of the three supported tags. The
plugin `Context` is wrapped via `context.createConfigurationContext
(configWithLocale)` whenever the user picks a non-"system" override;
this surfaces the chosen locale to `Resources.getString(...)` for both
the widget and the preference fragment without needing
`Activity.recreate()` — satisfying FR-018.

**Rationale**: Standard, well-understood, no ATAK-specific overlay
needed (confirmed by subagent A — no custom localisation layer in any
sample). `createConfigurationContext` is the modern Android idiom for
in-process locale switching (since API 17, fully stable since 24).

**Alternatives considered**:
- *Programmatic string tables*: bypasses Android resource resolution,
  loses translator tooling; rejected.
- *Recreate the entire plugin lifecycle on language change*: works,
  but visibly flashes the overlay and is overkill for a string swap.

---

## R8. Coordinate math source-of-truth

**Decision**: Port pwa_map's algorithms verbatim, preserving
constants and intermediate steps so the same golden test vectors pass
to within the published tolerances:
- **TWD97**: use `org.locationtech.proj4j` 1.3.x with the proj-string
  ```
  +proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0
  +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs
  ```
  (matches pwa_map `src/coord/twd97.ts:6-8`; EPSG:3826 forward and
  inverse).
- **TWD67**: 4-parameter datum shift from TWD97 (NOT WGS84 → TWD67
  direct) with constants:
  - Δx = 807.8 m, Δy = 248.6 m, a = 1.549 × 10⁻⁵, b = 6.521 × 10⁻⁶
  - X67 = X97 − Δx − a·X97 − b·Y97
  - Y67 = Y97 + Δy − a·Y97 − b·X97
  (pwa_map `src/coord/twd67.ts:4-14`)
- **Taipower grid**: built on TWD67 TM2 z121 with anchors
  ANCHOR_E_WEST = 170 000, ANCHOR_N_SOUTH = 2 400 000,
  REGION_WIDTH = 80 000, REGION_HEIGHT = 50 000, 8 rows × 3 columns of
  letter regions. Sub-region 800 m × 500 m; 100 m letter (A–J × A–J);
  10 m digits; optional 1 m digits at precision 11.
  Letters Y/Z → out-of-coverage (outer islands deferred, ADR 0012 in
  pwa_map).

**Rationale**: pwa_map is the user-named source of truth (spec
Assumptions). Reusing the exact algorithm gives us a free property:
the same test vectors transferred verbatim become our acceptance
tests, and any future bug in either implementation is comparable.

**Critical pitfall noted in pwa_map ADR 0004**: off-the-shelf
EPSG:3828 definitions in proj4 omit the 4-parameter shift, producing
a *silent* ~400 m error. We MUST hand-roll the TWD67 shift; we MUST
NOT delegate to proj4 for TWD67.

**Alternatives considered**:
- *Hand-roll TM2 trigonometry for TWD97*: ~100 lines, doable, but
  introduces an unverified math implementation between us and a
  well-tested library. proj4j is the right trade-off.
- *Use proj4 EPSG:3828 for TWD67*: explicit pwa_map warning against
  this; rejected.

**Reference test vectors** (lifted from pwa_map
`tests/unit/fixtures/test-vectors.json`):

| Location | WGS84 (lat, lon) | TWD97 (E, N) | TWD67 (E, N) | Taipower 9-char |
|---|---|---|---|---|
| Taipei 101 | 25.033611, 121.564472 | 306962.887, 2769619.124 | 306132.271, 2769822.821 | B7039 BD32 |
| Kaohsiung 85 | 22.61225, 120.2867 | 176669.456, 2501522.988 | 175842.607, 2501731.687 | P0703 CC43 |
| Taichung CH | 24.1416, 120.6437 | 213789.087, 2670751.115 | 212960.559, 2670956.951 | G5341 FE65 |
| Hualien Stn | 23.9932, 121.6012 | 311171.020, 2654400.548 | 310341.091, 2654606.002 | H7509 DB40 (11-char: H7509 DB4016) |

Tolerances: TWD97 ±0.1 m, TWD67 ±3 m, Taipower 9-char ±10 m, 11-char
±1 m. Same numbers used for golden tests.

---

## R9. TDAL (Tactical/Tool Data Access Layer)

**Decision**: **Do not integrate with TDAL.** Render all three units
through the in-plugin `TwCoordWidget` overlay.

**Rationale**:
1. Subagent A confirmed the TDAL plugin and its
   `coordinate_systems.xml` are **not bundled** with the ATAK-CIV
   5.7.0.3 SDK we have; integrating with it would mean depending on a
   separately distributed component the user may not have installed.
2. Taipower grid is **not an EPSG CRS** and cannot be expressed in a
   TDAL XML; so a TDAL-only design would only cover TWD97 and TWD67,
   forcing two code paths for the same readout.
3. Doing all three through one widget gives us a single coherent UX
   (FR-001, FR-006), no deployment step beyond installing the plugin,
   and a single place to apply localisation and clipboard behaviour
   (FR-015).

**Alternatives considered**:
- *Hybrid (TWD97/TWD67 via TDAL, Taipower via widget)*: two readouts
  on screen (TDAL's at map crosshair, ours top-right) confuses the
  user; rejected.
- *Ship our own `coordinate_systems.xml` alongside the plugin*: TDAL
  is itself a separate plugin we'd need to require — unacceptable
  hard dependency.

---

## R10. Build configuration & gotchas

**Decision**:
- Apply `atak-takdev-plugin` in `app/build.gradle`.
- Exclude transitive `androidx.core`, `androidx.fragment`,
  `androidx.lifecycle` to match ATAK-bundled versions
  (precedent: `meshtastic_atak/app/build.gradle:358-366`).
- Add Spotless with `googleJavaFormat()` so Principle I's formatter
  rule is enforced by Gradle, not goodwill: `./gradlew spotlessCheck`
  blocks CI on unformatted code.
- ProGuard config (`proguard-gradle.txt`) keeps the
  `IPlugin` impl class + `MapComponent` + `PreferenceFragment` and
  avoids lambdas in any code path that survives R8 — per SDK
  README.md:44-48.
- Keep `android:extractNativeLibs="true"` in `AndroidManifest.xml`
  (required for ATAK plugin loading;
  `meshtastic_atak/AndroidManifest.xml:14`).
- Java 17 source/target compatibility (Gradle
  `compileOptions.sourceCompatibility = JavaVersion.VERSION_17`).
- **No `<uses-permission android:name="android.permission.INTERNET"/>`**
  in the manifest — enforces FR-019 by construction.

**Rationale**: Each item above is either an SDK requirement, a
documented gotcha, or a constitution-driven safeguard.

**Alternatives considered**: None — these are platform constraints.

---

## R11. Testing strategy

**Decision**: Three layers, TDD per Constitution II:

1. **JVM unit tests** (`app/src/test/java/...`):
   - `coord/` — TWD97, TWD67 4-param, Taipower grid against the four
     pwa_map golden vectors plus out-of-range / domain-edge cases.
   - `Formatter` — every (unit × locale × value) combination produces
     the expected display string; clipboard equality string-match.
   - `LocaleOverride` — system locale → mapped resource locale,
     including `zh-Hans-CN`, `zh-Hant-HK`, `ja-JP`, `ko-KR`, `fr-FR`.

2. **Instrumented tests** (`app/src/androidTest/...`):
   - Preference fragment renders both lists with the right labels
     in each of the three UI languages.
   - Toggling unit triggers widget invalidate within one frame.
   - Tapping widget copies to clipboard and shows confirmation.

3. **Manual acceptance** (per `quickstart.md`):
   - Install on device with ATAK 5.7.0.3.
   - Walk through the three User Stories' acceptance scenarios.
   - Verify "out of range" state by panning to e.g. Hong Kong.

**Rationale**: The pure layer carries the entire correctness budget
of the plugin — having it 100 % JVM-testable is the single biggest
lever for fast TDD cycles. Android-coupled paths are kept thin.

**Alternatives considered**:
- *Espresso for everything*: slow, requires an emulator, fights with
  Android test runner; rejected for unit-level work on `coord/`.

---

## Outstanding NEEDS CLARIFICATION

None. All technical unknowns from the plan template are now resolved.
