# Taiwan Coordinate Display + Input Plugin for ATAK (`atak_tw_coord_plugin`)

An [ATAK-CIV](https://tak.gov/products/atak-civ) plugin that does two
things in Taiwan-flavoured coordinate units:

1. **Display** — shows the map-centre, the device's own position, and
   any tapped CoT target's coordinate as an on-map readout.
2. **Input ("GoTo")** — lets the operator type a Taiwan coordinate
   (Taipower / TWD97 / TWD67) and pans the ATAK map there. The page
   also has Auto Fill (read current map centre → fill the field) and
   a Recent list (up to 10 prior submissions).

Both features support the same three Taiwan coordinate systems:

- **Taipower grid** (台電座標) — 11-character codes over TWD67 TM2
- **TWD97 / TM2** — central meridian 121° (main island) or 119° (outer islands)
- **TWD67 / TM2** — same TM2 grid as TWD97, via a 4-parameter datum shift

Functionally similar to the BNG (British National Grid) ATAK plugin,
but Taiwan-flavoured: same readout pattern, different coordinate
systems.

## Screenshots

The three readouts live alongside ATAK's native widgets (no overlap, no
TDAL dependency):

- **Bottom-left** — map-centre coordinate, sits next to ATAK's `Eye Alt`
  and the MGRS scale bar.
- **Bottom-right** — own-position coordinate, sits alongside the
  self-callsign card.
- **Top-right** — CoT target coordinate, appears when you tap any map
  item and disappears when you tap empty map.

Screenshots from a Galaxy Tab S10+ running ATAK-CIV 5.7.0.3 are
captured under `docs/ui/` (`readout-widget.md`, `settings-fragment.md`).

## Features

| Feature | Notes |
|---|---|
| Three coordinate systems | Taipower grid (台電), TWD97, TWD67 — selectable from settings or the Tools-icon cycle |
| Three readouts | Map centre, own position (any ATAK `LocationProvider` — GPS / network / fused / external CoT / Bluetooth GPS), CoT target |
| Multi-language UI | English / Traditional Chinese (Taiwan) / Japanese; follows Android system locale by default with in-app override; switches live without ATAK restart |
| Tools-icon cycle | One-tap cycle: `Off → Taipower → TWD97 → TWD67 → Off …` with localised toast feedback |
| Clipboard copy | Tap a readout to copy the exact displayed string to the Android clipboard (FR-015) |
| Outer-island support | Penghu / Kinmen / Matsu (TM2 zone 119, EPSG:3825) — auto-selected by longitude. `z119` suffix appears on the readout when zone is non-default |
| Offline, no telemetry | Zero outbound network. Manifest deliberately omits `INTERNET` permission. No analytics or crash-reporting SDKs |
| Settings advisory | Built-in accuracy notice explaining TWD67 main-island ±3-5 m vs outer-island ±10-20 m |
| **Coordinate input page** ("GoTo") | Second Tools-menu icon opens a DropDown with three tabs (Taipower / TWD97 / TWD67), submit pans the camera to the resolved location (X/Y only — operator's zoom is preserved) |
| **Auto Fill** | One-tap fill of the active tab from the current map centre, with zone toggle (TWD97/TWD67) auto-set from longitude; disabled in real time when the centre is unrepresentable in the active tab |
| **Recent list** | Up to 10 prior successful submissions, deduped on (unit, value), persisted across ATAK restarts; tap any row to re-fill, per-row delete |
| **In-page marker-mode picker** | 9 radios under Submit (Move only + 7 affiliation/spot-map types + **Custom Icon**). Selecting non-Move-only drops a marker of that type at the resolved coord; selection persists across plugin restarts |
| **Custom Icon picker** | Two-step modal (iconset list → icon grid) reading exclusively from ATAK's existing iconset library (5 bundled iconsets out of the box + any operator-loaded). Picked icon is applied via `MarkerCreator.setIconPath`; marker behaves identically to host-placed ones. Graceful one-shot fallback when the picked iconset is removed |

## Coverage and accuracy

The plugin's accuracy budget is published in the settings advisory and
in the spec — the relevant numbers, all measured against the user-
supplied pyproj 3.6.1 + 內政部 7-parameter Bursa-Wolf CSV:

| Coordinate system | Coverage | Typical error |
|---|---|---|
| TWD97 | Whole Taiwan area (main island + outer islands) | < 1 m |
| TWD67 (main island) | 19 main-island counties / cities | ±3-5 m |
| TWD67 (outer islands) | Penghu / Kinmen / Matsu | ±10-20 m (4-param shift vs. official 7-param) |
| Taipower grid | Main island only | 11-char = 1 m sub-cell; outer islands return `OUT_OF_RANGE` |

Test coverage:

- **22 of 22** county/city seats (19 main-island + Penghu + Kinmen + Matsu) are pinned vectors in `TaiwanCitiesAuthoritativeTest`
- **9 golden vectors** in `GoldenVectors`: 4 pwa_map landmarks (Taipei 101 / Kaohsiung 85 / Taichung CH / Hualien Stn) + 5 cell-centroid regression vectors covering the L / E / D / O / T regions (added in v1.0.4 alongside the Taipower letter-table correction — see ADR-0001 follow-up note)
- **Hualien Stn 11-char** (`H7509 DB4016`) pinned as the canonical 1-m precision regression
- **3 real-world out-of-range points** (Naha Airport, Hong Kong IFC, Tokyo Tower)
- **88 JVM unit tests** total; all green

## Installation

This plugin is distributed as a regular ATAK plugin APK and is sideloaded
just like any other ATAK extension.

### Prerequisites

- ATAK-CIV **5.4.0 or later** installed on the target Android device
- USB debugging or another sideload mechanism

### Install

1. Build the APK locally (see [Build from source](#build-from-source))
   or pull a pre-built APK from the project's release artifacts.
2. Install the APK:
   ```
   adb install -r ATAK-Plugin-atak_tw_coord_plugin-<version>-civ-debug.apk
   ```
3. Launch ATAK. Either:
   - Open `Settings → Manage Plugins` and enable **TW Coordinates** if
     prompted to load a newly-installed plugin, OR
   - Force-stop and re-launch ATAK; the plugin auto-loads.

## Usage

### The on-map readouts

Once enabled, three readout boxes appear on the map:

```
              ┌─────────────────────────┐
              │ MAP TPC: B7039 BD3223   │   ← bottom-left
              │ Eye Alt: 62,479 ft MSL  │
              │ 51R TG 60593 72223 …    │
              └─────────────────────────┘
                                ⋮
                  ┌──────────────────────┐
                  │ ME TPC: B7039 BD3223 │  ← bottom-right
                  │ Callsign: HOBBY      │
                  │ 51R TG 60593 …       │
                  └──────────────────────┘
              ┌─────────────────────────┐
              │ TGT TPC: B7039 BD3223   │  ← top-right (only when
              └─────────────────────────┘    a CoT target is selected)
```

| State | Display | Colour |
|---|---|---|
| OK (in coverage) | `<label> <unit>: <value>` | White |
| Out of range | `<label> <unit>: out of range` + WGS84 fallback line | Amber |
| No GPS fix | `<label>: no fix` | Grey |
| No permission | `<label>: no permission` | Grey |

### Switching the active unit

Two ways:

1. **Tools icon (fast)** — Tap **TW Coordinates** under the Tools menu.
   Each tap advances the cycle:
   ```
   Off → Taipower → TWD97 → TWD67 → Off → …
   ```
   A short toast confirms the new state.

2. **Settings (deliberate)** —
   `Settings → Tool Preferences → Specific Tool Preferences → TW Coordinates`.
   The `Display unit` row shows a live preview using Taipei 101 as the
   sample point, e.g. `TWD97 / TM2 z121 — TWD97: 306,963m 2,769,619m`.

Both paths write to the same `SharedPreferences` so the icon cycle and
the settings page always agree.

### Switching the UI language

Same settings page, `UI language` row. Options: `Use system`,
`English`, `中文（正體）`, `日本語`. Live preview shows the three row
labels (MAP / ME / TGT) translated. Selection persists across app
restarts. Change is reflected immediately — no ATAK restart required.

### Copying a coordinate to the clipboard

Tap the readout box (TPC / TWD97 / TWD67). The exact displayed string
(including the unit tag and any `z119` suffix) is copied to the
Android clipboard. Paste anywhere — Signal / LINE / a paper-form OCR
app / a logbook.

### Outer islands

If the map centre or your own position has longitude < 120°E (Penghu,
Kinmen, Matsu), the plugin automatically switches projection to TM2
zone 119 and appends a `z119` suffix to the value, e.g.
`MAP TWD97: 309,129m 2,607,653m z119`. This is the only visual signal
distinguishing zone 119 from the main-island default — without it the
numbers would look like they were on the main-island grid.

Taipower grid is **main-island only**; outer-island fixes in Taipower
mode show `OUT OF RANGE` with the WGS84 fallback line.

## Build from source

### Prerequisites

- **JDK 17** (Temurin recommended)
- **Android Studio** (Hedgehog or later) with:
  - Android SDK platforms `android-34` and `android-36`
  - Build-tools `34.0.0` or newer
- **ATAK-CIV 5.7.0.3 SDK** unpacked locally
- **Git**

### Configure

In `local.properties` (not committed), set both:
```properties
sdk.dir=<path to your Android SDK>
sdk.path=<path to ATAK-CIV-5.7.0.3-SDK>
takdev.plugin=<path to ATAK-CIV-5.7.0.3-SDK>/atak-gradle-takdev.jar
```

The plugin's compile target is the 5.7.0.3 SDK; runtime compatibility
is declared as `com.atakmap.app@5.4.0.CIV` (works on every ATAK-CIV
version we have tested, 5.4 through 5.7.0.3).

### Common commands

```
./gradlew :app:testCivDebugUnitTest      # 88 JVM unit tests
./gradlew :app:assembleCivDebug          # signed civ-debug APK
./gradlew :app:spotlessApply             # google-java-format the codebase
./gradlew :app:lint                      # Android lint
```

The build outputs an APK at
`app/build/outputs/apk/civ/debug/ATAK-Plugin-atak_tw_coord_plugin-<version>-<gitHash>-5.4.0-civ-debug.apk`.

## Project layout

```
.
├── app/                                          # Android plugin module
│   ├── build.gradle                              # atak-takdev-plugin + Java 17 + proj4j
│   ├── proguard-gradle.txt                       # keeps plugin entry points + coord/
│   └── src/main/
│       ├── AndroidManifest.xml                   # no INTERNET permission (FR-019)
│       ├── assets/plugin.xml                     # IPlugin → TwCoordLifecycle
│       ├── java/com/atakmap/android/twcoord/     # Java source
│       │   ├── TwCoordMapComponent.java          # listener wiring + Tools-cycle
│       │   ├── TwCoordWidget.java                # 3-corner readout overlay
│       │   ├── TwCoordPreferenceFragment.java    # settings page
│       │   ├── SelfMarkerSubscriber.java         # 1 Hz debounce + 10s stale detector
│       │   ├── coord/                            # pure-Java math (JVM-testable)
│       │   ├── i18n/                             # locale fallback + Context wrapper
│       │   ├── prefs/                            # typed SharedPreferences wrapper
│       │   └── plugin/                           # AbstractPlugin / Tool entry points
│       └── res/
│           ├── values/strings.xml                # English (default)
│           ├── values-zh-rTW/strings.xml         # Traditional Chinese (Taiwan)
│           ├── values-ja/strings.xml             # Japanese
│           ├── layout/pref_item.xml              # custom preference row layouts
│           └── xml/preferences.xml               # PanListPreference declarations
├── docs/
│   ├── adr/                                      # 13 Architecture Decision Records
│   └── ui/                                       # readout + settings layout docs
├── specs/001-tw-coord-display/                   # spec / plan / tasks / contracts (display)
├── specs/002-tw-coord-goto/                      # spec / plan / tasks / contracts (GoTo input page)
├── specs/003-custom-marker-icon/                 # spec (in-flight) — Custom Icon marker mode
├── test-data/taiwan_cities_coords.csv            # 22-city authoritative coords
└── .specify/memory/constitution.md               # project constitution
```

## Spec-Kit workflow

This project is developed with the
[GitHub Spec Kit](https://github.com/github/spec-kit). Per-feature
spec / plan / tasks / contracts live under `specs/NNN-<short-name>/`:

- [`specs/001-tw-coord-display/`](specs/001-tw-coord-display/) — on-map readout widget
- [`specs/002-tw-coord-goto/`](specs/002-tw-coord-goto/) — GoTo input page
- [`specs/003-custom-marker-icon/`](specs/003-custom-marker-icon/) — Custom Icon marker mode *(in flight)*

Thirteen ADRs under [`docs/adr/`](docs/adr/) cover every architecturally
significant decision (ADR-0001 is the entry point and carries the
2026-05-23 Taipower letter-table correction follow-up; ADR-0010
captures the SDK reconnaissance for the Custom Icon feature, ADR-0011
the post-implementation pivots, ADR-0012 the icon asset pipeline, and
ADR-0013 the TPP-to-GitHub release pipeline).

## References

- [pwa_map](#) — coordinate math source-of-truth (Taipower grid +
  TWD67 4-parameter shift constants); see ADR-0001.
- [NCKU 歷史所 GIS 座標系統轉換工具](http://gis.thl.ncku.edu.tw/coordtrans/coordtrans.aspx) —
  canonical online converter for spot-checking new test points.
- [proj4j](https://github.com/locationtech/proj4j) — the Java port of
  proj4 that powers TWD97 (EPSG:3826) and TWD97 zone 119 (EPSG:3825).
- [ATAK-CIV upstream source](https://github.com/TAK-Product-Center/atak-civ) —
  the active upstream Java source for ATAK-CIV (default branch `main`).
  Use this when cross-checking SDK signatures the plugin compiles against
  (`ATAK-CIV-5.7.0.3-SDK/main.jar`) — the SDK jar holds the pinned
  contract, the upstream repo holds the implementation bodies for
  reading. The older `deptofdefense/AndroidTacticalAssaultKit-CIV`
  mirror is stale.
- [ATAK Plugin Development Guide](https://github.com/TAK-Product-Center/atak-civ/blob/main/ATAK_Plugin_Development_Guide.pdf) —
  shipped alongside the SDK; the sample plugins (`meshtastic_atak`,
  `helloworld`) this project mirrors are under
  [`atak/ATAK/app/src/main/java/`](https://github.com/TAK-Product-Center/atak-civ/tree/main/atak/ATAK/app/src/main/java)
  and adjacent sample modules.

## License

TBD. Until a `LICENSE` file lands, do not assume any specific terms.
