# TW Coordinates Plugin — User Guide

**Version:** v1.4.4

**Language:** English · [Taiwan Traditional Chinese](user-guide_zh.md)

TW Coordinates adds Taiwan coordinate and offline address support to ATAK.
Coordinate and address entry are integrated into ATAK's native **Go To →
Taiwan** pane. The plugin exposes one Tools item, **TW Coordinates**, for
offline data management and settings.

## Choose a task

| I want to… | Start here |
|---|---|
| Install the plugin and confirm it loaded | [First-time setup](#first-time-setup) |
| Go to a Taipower, TWD97, or TWD67 coordinate | [Go to a Taiwan coordinate](#go-to-a-taiwan-coordinate) |
| Find a Taiwan address without a network | [Go to an offline address](#go-to-an-offline-address) |
| Inspect a map item in Taiwan formats | [Convert a map item's coordinate](#convert-a-map-items-coordinate) |
| Import, replace, or remove address data | [Manage offline address data](#manage-offline-address-data) |
| Show, copy, or configure map readouts | [Use and configure map readouts](#use-and-configure-map-readouts) |
| Recover from an error | [Troubleshooting](#troubleshooting) |

## How the plugin fits into ATAK

There are three current entry paths:

| ATAK path | Use it for |
|---|---|
| **Go To → Taiwan** | Enter Taipower, TWD97, TWD67, or an imported address |
| Map item details → **Coordinate** → **Taiwan** | Inspect the item's coordinate and nearby offline address |
| Tools → **TW Coordinates** | Manage offline data, then open plugin settings |

ATAK owns the Go To dialog, **Auto Fill**, **Clear**, **Copy**, and final
confirmation. The plugin supplies the Taiwan pane and performs local
conversion or lookup.

## First-time setup

### Prerequisites

- An APK built for the target ATAK signing line.
- An offline address bundle only if you need address search or address
  readouts. Coordinate conversion does not require a dataset.

### Install and verify

1. Install the plugin APK.
2. Open ATAK and enable **TW Coordinates** when prompted.
3. Open **Settings → Plugins** or **TAK Package Mgmt** and confirm the plugin
   reports **Loaded**.
4. Open ATAK **Go To** and confirm **Taiwan** is available.
5. Open Tools and confirm the single plugin item is **TW Coordinates**.

Setup is complete when both the **Taiwan** pane and **TW Coordinates** Tools
item are available.

Upgrades normally retain imported county datasets and current preferences.
After an upgrade, reload or restart ATAK if a retired Tools item remains in
ATAK's cache.

## Go to a Taiwan coordinate

1. Open ATAK **Go To → Taiwan**.
2. Select **Taipower**, **TWD97**, or **TWD67**.
3. Enter the coordinate:

   | System | Required input |
   |---|---|
   | Taipower | A 9-character 10 m or 11-character 1 m main-island code |
   | TWD97 | Integer easting and northing in metres, plus TM2 zone 121 or 119 |
   | TWD67 | Integer easting and northing in metres, plus TM2 zone 121 or 119 |

4. Resolve any validation message shown in the Taiwan pane.
5. Tap ATAK's **OK** to perform the Go To action.

The task succeeds when ATAK accepts the coordinate and moves to the requested
point. Zone 121 is used for the main island; zone 119 is used for applicable
outer-island points. Taipower is main-island only, and TWD67 zone 119 displays
an accuracy advisory.

For coverage and accuracy limits, see
[Coordinate systems, coverage, and accuracy](reference/coordinate-systems.md).

### Use ATAK's host controls

- **Auto Fill** converts ATAK's current point into the active Taiwan tab.
- **Clear** clears only the active tab. On Address, it also cancels the active
  lookup and candidate set.
- **Copy** copies the active canonical representation without moving the map.

## Go to an offline address

### Prerequisite

Import the boundary data and the county dataset that contains the address. See
[Manage offline address data](#manage-offline-address-data) if the data is not
installed yet.

### Find and confirm an address

1. Open ATAK **Go To → Taiwan → Address**.
2. Use **Single field** to paste or type one complete address, or switch to
   **Structured fields**.
3. In structured mode, choose the county/city and district, then enter the
   road/locality and number/floor.
4. Wait for local search to finish.
5. If one address resolves, review it. If ATAK shows **Multiple addresses
   match**, tap **Choose result** and select the intended administrative and
   road context.
6. Tap ATAK's **OK** to perform the Go To action.

The task succeeds when ATAK accepts the resolved address and moves to it.
Selecting a candidate alone does not move the map; ATAK's final confirmation
does.

Switching between the two input modes preserves the same draft. Common `台` /
`臺`, full-width digits, spacing, punctuation, and address-unit numeral
variants are normalized locally.

<p align="center">
<img src="images/23a-native-address-full.png" alt="ATAK Go To Taiwan Address pane in single-field mode" width="900"><br>
<sub>Use Single field for pasted or complete addresses; address content is redacted.</sub>
</p>

<p align="center">
<img src="images/23b-native-address-structured.png" alt="ATAK Go To Taiwan Address pane with county, district, road, and number fields" width="900"><br>
<sub>Use Structured fields when administrative context must be selected explicitly; address values are redacted.</sub>
</p>

If no matching dataset is installed, Address displays data-management
guidance while the coordinate tabs remain usable. For candidate behavior and
detailed examples, see the
[Native Taiwan Address feature guide](tw-addr-search.md).

## Convert a map item's coordinate

1. Open the map item's details.
2. Tap its **Coordinate** value.
3. In **Convert Coordinate**, select **Taiwan**.
4. Switch among **Taipower**, **TWD97**, **TWD67**, and **Address**.

The coordinate tabs are prepared immediately. Address resolves asynchronously
from the map item's exact WGS84 point. A nearby address may be displayed, but
the plugin never replaces or snaps the ATAK point to that address record.

The task succeeds when the requested Taiwan representation appears while the
original map-item position remains unchanged.

<p align="center">
<img src="images/20-atak-point-detail-coordinate.jpg" alt="ATAK map-item details with the Coordinate value available" width="420"><br>
<sub>Tap the Coordinate value to open Convert Coordinate.</sub>
</p>

<p align="center">
<img src="images/21-atak-convert-coordinate.jpg" alt="ATAK Convert Coordinate dialog with the Taiwan pane beside built-in panes" width="900"><br>
<sub>Taiwan is available beside ATAK's built-in coordinate panes.</sub>
</p>

In a read-only host dialog, resolved values remain visible while inputs,
selectors, and candidate actions are disabled.

## Manage offline address data

Open Tools → **TW Coordinates**. This is the plugin's only public Tools item and
opens the offline-data manager directly.

<p align="center">
<img src="images/08-tools-menu.jpg" alt="ATAK Tools showing the TW Coordinates tile, the plugin's only public Tools item" width="190"><br>
<sub>Coordinate and address entry are in ATAK Go To → Taiwan; Tools retains only TW Coordinates for data management and settings.</sub>
</p>

### Import data

1. Tap **Import…**.
2. Select a supported ZIP bundle or SQLite dataset.
3. Keep ATAK open while the progress card is visible.
4. Confirm the imported county appears with its data date, row count, and
   storage size.

Address search and readouts are ready when the boundary data and applicable
county both appear as active data.

### Replace or remove a county

- To update a county, tap its **⋮** menu, choose **Replace…**, and select the
  newer file. Existing data remains active unless replacement succeeds.
- To reclaim storage, tap **⋮** and choose **Remove**, then confirm. Removal
  deletes that county's local active data; re-import the dataset to restore it.

<p align="center">
<img src="images/24-offline-address-data.png" alt="TW Coordinates offline-data manager with the settings button, usage summary, and imported county rows" width="700"><br>
<sub>The Tools item opens this manager; the top button opens TW Coordinates settings.</sub>
</p>

For supported bundles, storage planning, status fields, and import-error
recovery, see [Offline address data](tw-offline-addr.md).

## Use and configure map readouts

The plugin can display coordinate rows for:

| Label | Point represented |
|---|---|
| **MAP** | Map centre |
| **ME** | Own position |
| **TGT** | Selected target |

Each row uses the selected Taipower, TWD97, or TWD67 display unit. Tap a
coordinate readout to copy the exact displayed string. Optional address rows
show the nearest imported address, a direction arrow, and `~` / `~~`
confidence markers.

To change readouts and other plugin options, use either path:

- Tools → **TW Coordinates** → **TW Coordinates settings**
- **Settings → Tool Preferences → Specific Tool Preferences → TW Coordinates**

Settings include the display unit, MAP/ME/TGT visibility, address rows,
confidence preset, Address candidate ordering, and plugin UI language.
Changes repaint the current readouts without requiring an ATAK restart.

## Troubleshooting

| Symptom | Check and recovery | Success check |
|---|---|---|
| **TW Coordinates** is missing from Tools | Confirm the plugin is **Loaded**. Reload or restart ATAK after an upgrade. | One **TW Coordinates** tile appears. |
| **Taiwan** is missing from Go To | Confirm the plugin is **Loaded**, then reopen Go To or restart ATAK. | **Taiwan** appears beside ATAK's coordinate panes. |
| Address says no matching dataset | Open **TW Coordinates** and import the boundary data plus the applicable county. | The county is active and Address can search it. |
| Address returns several rows | Tap **Choose result** and compare county, district, road, and number. | The selected result resolves before ATAK confirmation. |
| Address returns no match | Verify the imported county, then try **Structured fields** to make the administrative context explicit. | One result resolves or a relevant candidate list appears. |
| Readout shows a coordinate but no address | Confirm the county dataset is active and enable the corresponding MAP/ME/TGT address row in settings. | An address appears when the point is inside installed coverage. |
| Taipower shows `out of range` | Taipower covers the main island only. Use TWD97/TWD67 zone 119 where applicable. | The point is represented in a supported system. |
| Import fails | Keep existing data in place, review the displayed error, then retry with enough free space and a supported bundle. | The county appears as active data. |

All coordinate conversion and address lookup run locally. The plugin does not
require network access.

## More information

- [Documentation index](README.md)
- [Native Taiwan Address feature guide](tw-addr-search.md)
- [Offline address data feature guide](tw-offline-addr.md)
- [Coordinate systems, coverage, and accuracy](reference/coordinate-systems.md)
- [Current UI contracts](ui/README.md)
- [Report an issue](https://github.com/swim-fish/atak_tw_coord_plugin/issues)
