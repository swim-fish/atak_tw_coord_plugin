# Taiwan Coordinate Display + Input Plugin for ATAK (`atak_tw_coord_plugin`)

An [ATAK-CIV](https://tak.gov/products/atak-civ) plugin that provides Taiwan
coordinate display, native coordinate/address entry, and offline address data:

1. **Display** — shows the map-centre, the device's own position, and
   any tapped CoT target's coordinate as an on-map readout.
2. **Native input** — adds one **Taiwan** pane to ATAK's shared coordinate-entry
   dialog with Taipower, TWD97, TWD67, and offline Address tabs. Ordinary Go
   To, Convert Coordinate, Auto Fill, Clear, Copy, and confirmation remain in
   the host workflow operators already know.
3. **Offline address data** — imports county-scoped datasets for forward search,
   reverse readouts, and native Convert Coordinate address display.

Coordinate conversion supports the same three Taiwan coordinate systems:

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

Each UI guide records the ATAK runtime used for its screenshots. Current
native-entry screenshots were captured on ATAK-CIV 5.7.0.9; older guides retain
their historical runtime evidence.

<p align="center">
<img src="docs/images/23a-native-address-full.png" alt="ATAK native Taiwan Address tab in single-field mode" width="900"><br>
<sub>The native Taiwan pane with the Address tab in single-field mode.</sub>
</p>

<p align="center">
<img src="docs/images/24-offline-address-data.png" alt="TW Coordinates Offline address data manager" width="700"><br>
<sub>TW Coordinates now opens offline address data first; the top button opens plugin settings.</sub>
</p>

## Features

| Feature | Notes |
|---|---|
| Three coordinate systems | Taipower grid (台電), TWD97, TWD67 — selectable from settings |
| Three readouts | Map centre, own position (any ATAK `LocationProvider` — GPS / network / fused / external CoT / Bluetooth GPS), CoT target |
| Multi-language UI | English / Traditional Chinese (Taiwan) / Japanese; follows Android system locale by default with in-app override; switches live without ATAK restart |
| Tools-icon opens offline data | Tapping **TW Coordinates** opens the offline address data manager. Its top **TW Coordinates settings** button opens display, language, search-order, and readout settings |
| Clipboard copy | Tap a readout to copy the exact displayed string to the Android clipboard (FR-015) |
| Outer-island support | Penghu / Kinmen / Matsu (TM2 zone 119, EPSG:3825) — auto-selected by longitude. `z119` suffix appears on the readout when zone is non-default |
| Offline, no telemetry | Zero outbound network. Manifest deliberately omits `INTERNET` permission. No analytics or crash-reporting SDKs |
| Settings advisory | Built-in accuracy notice explaining TWD67 main-island ±3-5 m vs outer-island ±10-20 m |
| **ATAK native Taiwan entry** | ATAK's shared coordinate-entry dialog gains one **Taiwan** pane with Taipower, TWD97, TWD67, and Address; explicit zones 121/119; full/structured lossless address modes; a maximum 20-row category-balanced candidate list; host Auto Fill/Clear/Copy; and read-only support. Exact matches are exclusive; ambiguous results combine text-prefix, numeric-nearest, current-map-distance, and fallback candidates without allowing one dense road family to consume the dialog. It uses DD-style compact fields, prepares all representable tabs when ATAK supplies a point, and preserves the exact host WGS84 during reverse lookup. See [`docs/ui/native-taiwan-coordinate-entry.md`](docs/ui/native-taiwan-coordinate-entry.md) |
| **Auto Fill** | One-tap fill of the active tab from the current map centre, with zone toggle (TWD97/TWD67) auto-set from longitude; disabled in real time when the centre is unrepresentable in the active tab |
| **Offline address lookup and management** | The native Address tab performs full or structured forward search and bounded candidate selection. Convert Coordinate and Auto Fill perform reverse lookup without snapping the host point. County SQLite/ZIP datasets produced by the [sibling generator](https://github.com/swim-fish/atak-tw-address-generator) are imported, replaced, or removed from the internal manager reached through **TW Coordinates**. Address readouts retain nearest-record direction and confidence indicators. |
| **Confidence indicator** | Per-row tilde marker (`~` / `~~`) prefix on the address text reflecting haversine distance to the nearest record. 4 presets (Off / Tight 20-100 m / Standard 50-200 m / Loose 100-500 m) selectable in Settings |

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
- The complete JVM suite is run for every release candidate; the exact count is
  reported by Gradle instead of duplicated here because it changes with each feature.

## Installation

This plugin is distributed as a regular ATAK plugin APK and is sideloaded
just like any other ATAK extension.

### Prerequisites

- ATAK-CIV **5.5.0 or later** installed on the target Android device
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

`Settings → Tool Preferences → Specific Tool Preferences → TW Coordinates`,
the `Display unit` row. The row shows a live preview using Taipei 101 as the
sample point, e.g. `TWD97 / TM2 z121 — TWD97: 306,963m 2,769,619m`.

Tapping the **TW Coordinates** Tools icon opens the offline address data
manager. Use its top **TW Coordinates settings** button to reach this settings
page. Earlier versions opened Settings directly or cycled the unit
`Off → Taipower → TWD97 → TWD67`; the format now changes only through
`Display unit`, with a separate **Show on-map readout** toggle.

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
- **ATAK-CIV 5.7.0.9 SDK** unpacked locally
- **Git**

### Configure

In `local.properties` (not committed), set both:
```properties
sdk.dir=<path to your Android SDK>
sdk.path=<path to ATAK-CIV-5.7.0.9-SDK>
takdev.plugin=<path to ATAK-CIV-5.7.0.9-SDK>/atak-gradle-takdev.jar
```

The Android build uses compile SDK 36 and minimum SDK 26. ATAK APIs compile
against the ATAK-CIV 5.7.0.9 SDK, while minimum ATAK runtime compatibility is
declared as `com.atakmap.app@5.5.0.CIV`. ATAK 5.4 is no longer supported;
see ADR-0022 and ADR-0024. The checked-in exact ATAK 5.5 device matrix remains
pending and is not implied by a successful 5.7.0.9 or TPP build.

### Common commands

```
./gradlew :app:testCivDebugUnitTest      # complete JVM unit-test suite
./gradlew :app:assembleCivDebug          # signed civ-debug APK
./gradlew :app:spotlessApply             # google-java-format the codebase
./gradlew :app:lint                      # Android lint
```

The build outputs an APK at
`app/build/outputs/apk/civ/debug/ATAK-Plugin-atak_tw_coord_plugin-<version>-<gitHash>-5.5.0-civ-debug.apk`.

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
│       │   ├── TwCoordMapComponent.java          # listener wiring + Tools → offline-data manager
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
│   ├── adr/                                      # 26 Architecture Decision Records
│   └── ui/                                       # readout + settings layout docs
├── CHANGELOG.md                                  # per-version change log
├── specs/001-tw-coord-display/                   # spec / plan / tasks / contracts (display)
├── specs/002-tw-coord-goto/                      # spec / plan / tasks / contracts (GoTo input page)
├── specs/003-custom-marker-icon/                 # Custom Icon marker mode
├── specs/004-offline-address/                    # reverse offline address
├── specs/005-multi-county-zip-import/            # multi-county + ZIP import
├── specs/006-county-forward-search/              # county-first forward search
├── specs/007-settings-ux-tweaks/                 # settings page + search/storage UX tweaks
├── specs/008-search-settings-ui/                 # search/storage UI redesign
├── specs/010-goto-ui-redesign/                   # historical custom Go To redesign
├── specs/011-native-coordinate-entry/            # native Taiwan coordinate entry
├── specs/012-prefill-native-tabs/                 # Convert Coordinate all-tab prefill
├── specs/013-native-address-entry/                # native Address + one Tools entry
├── test-data/taiwan_cities_coords.csv            # 22-city authoritative coords
└── .specify/memory/constitution.md               # project constitution
```

## Spec-Kit workflow

This project is developed with the
[GitHub Spec Kit](https://github.com/github/spec-kit). Per-feature
spec / plan / tasks / contracts live under `specs/NNN-<short-name>/`:

- [`specs/001-tw-coord-display/`](specs/001-tw-coord-display/) — on-map readout widget
- [`specs/002-tw-coord-goto/`](specs/002-tw-coord-goto/) — GoTo input page
- [`specs/003-custom-marker-icon/`](specs/003-custom-marker-icon/) — Custom Icon marker mode
- [`specs/004-offline-address/`](specs/004-offline-address/) — reverse offline address
- [`specs/005-multi-county-zip-import/`](specs/005-multi-county-zip-import/) — multi-county + ZIP import
- [`specs/006-county-forward-search/`](specs/006-county-forward-search/) — county-first forward search
- [`specs/007-settings-ux-tweaks/`](specs/007-settings-ux-tweaks/) — settings page + search/storage UX tweaks
- [`specs/008-search-settings-ui/`](specs/008-search-settings-ui/) — search/storage page UI redesign (v1.3.0)
- [`specs/010-goto-ui-redesign/`](specs/010-goto-ui-redesign/) — GoTo input page UI redesign (v1.3.2)
- [`specs/011-native-coordinate-entry/`](specs/011-native-coordinate-entry/) — ATAK native Taiwan coordinate entry
- [`specs/012-prefill-native-tabs/`](specs/012-prefill-native-tabs/) — native Convert Coordinates all-tab prefill and safety polish
- [`specs/013-native-address-entry/`](specs/013-native-address-entry/) — native offline Address entry, bounded candidates, and Tools consolidation

Per-version changes are tracked in [`CHANGELOG.md`](CHANGELOG.md). Twenty-six ADRs
under [`docs/adr/`](docs/adr/) cover every architecturally significant decision
(ADR-0001 is the entry point and carries the 2026-05-23 Taipower letter-table
correction follow-up; ADR-0014/0015 the offline-address reconnaissance +
implementation, ADR-0017 multi-county + ZIP import, and
[ADR-0018](docs/adr/0018-settings-ux-tweaks.md) the feature-007 settings/search/storage
tweaks plus the two device-found fixes — the dialog-resource trap and the
programmatic-pan readout refresh;
[ADR-0019](docs/adr/0019-forward-search-i18n-and-house-number-similarity-fixes.md) the
1.2.1 forward-search fixes — localised page inflation and house-number-aware "most
similar"; and
[ADR-0020](docs/adr/0020-search-settings-ui-redesign.md) the v1.3.0 search/storage
page redesign plus six device-found fixes — scope-button highlight,
地圖中心 district auto-select, county-list missing-data ⚠, county-only chip,
storage-page localisation, and the on-map address direction arrow; and
[ADR-0021](docs/adr/0021-goto-ui-redesign.md) the v1.3.2 GoTo input-page
compact-stacked redesign — segmented tabs, single header Auto Fill, primary /
ghost submit hierarchy, glove-friendly marker grid, and drawable-driven
selection; and
[ADR-0022](docs/adr/0022-set-minimum-atak-runtime-to-5-5.md) the ATAK 5.5
minimum-runtime decision;
[ADR-0023](docs/adr/0023-native-taiwan-coordinate-entry.md) the one-pane native
integration, lifecycle, and advanced-page coexistence decision; and
[ADR-0024](docs/adr/0024-use-atak-5-7-0-9-compile-sdk.md) the split compile SDK
5.7.0.9 / minimum runtime 5.5 validation decision; and
[ADR-0025](docs/adr/0025-separate-release-readiness-from-tpp-staging.md) the
separation of TPP staging, public-release gates, and immutable signed tags; and
[ADR-0026](docs/adr/0026-native-address-entry-and-tools-consolidation.md) the
native Address service, one-public-Tools-entry migration, and bounded
category-balanced candidate retrieval).

The active feature is resolved from `.specify/feature.json`; agent guidance
must not infer it from the newest directory. The required lifecycle is:

```text
specify -> clarify -> plan -> checklist (optional) -> tasks -> analyze -> implement -> converge -> release-readiness
```

`checklist` is an optional post-plan requirements-quality review. `analyze` is
read-only. If `converge` appends remaining tasks, run `implement` and
`converge` again until no actionable gaps remain. Behaviour changes use
test-first tasks; ATAK SDK seams additionally require public-API evidence and
minimum/current-line device scenarios. A converged implementation is not
automatically public-release ready; unchecked `[RELEASE-GATE]` evidence is
reported separately. See
[the constitution](.specify/memory/constitution.md) for the full gates.

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
  (`ATAK-CIV-5.7.0.9-SDK/main.jar`) — the SDK jar holds the pinned
  contract, the upstream repo holds the implementation bodies for
  reading. The older `deptofdefense/AndroidTacticalAssaultKit-CIV`
  mirror is stale.
- [ATAK Plugin Development Guide](https://github.com/TAK-Product-Center/atak-civ/blob/main/ATAK_Plugin_Development_Guide.pdf) —
  shipped alongside the SDK; the sample plugins (`meshtastic_atak`,
  `helloworld`) this project mirrors are under
  [`atak/ATAK/app/src/main/java/`](https://github.com/TAK-Product-Center/atak-civ/tree/main/atak/ATAK/app/src/main/java)
  and adjacent sample modules.

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 Shihyu.
