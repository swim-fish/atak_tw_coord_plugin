# TW Coordinates Plugin — User Guide

**Version:** v1.4.4

This guide covers the current operator workflow. Taiwan coordinate and address
entry are integrated into ATAK's native coordinate dialog. The plugin exposes
one Tools item: **TW Coordinates**.

## 1. Install and confirm

1. Install the plugin APK that matches the target ATAK signing line.
2. Open ATAK and enable **TW Coordinates** when prompted.
3. Confirm **Settings → Plugins** (or **TAK Package Mgmt**) reports the plugin
   as **Loaded**.

Upgrades can normally use `adb install -r`. Imported county datasets and active
preferences are retained. Retired custom GoTo Recent/marker/icon preferences
are ignored and are not used by the native workflow.

## 2. Native Taiwan entry

### Go To a coordinate

1. Open ATAK **Go To** and select **Taiwan**.
2. Select **Taipower**, **TWD97**, or **TWD67**.
3. Enter the coordinate. TWD97/TWD67 also require zone **121** (main island) or
   **119** (outer islands).
4. Tap ATAK's **OK** to perform the host Go To action.

Taipower accepts 9-character (10 m) and 11-character (1 m) main-island codes.
TWD97/TWD67 accept integer easting and northing in metres. TWD67 zone 119 shows
an accuracy advisory; Taipower reports out of range for outer-island points.

### Go To an offline address

1. Import the applicable county dataset through **TW Coordinates** (section 4).
2. Open ATAK **Go To → Taiwan → Address**.
3. Use **Full address** for one field, or switch to **Structured**. Tap the
   county/city selector, then the district selector; road/locality and the
   remaining address stay editable.
4. Enter an address. Common `台`/`臺`, full-width digit, spacing, punctuation,
   and address-unit numeral variants are normalised locally.
5. For one exact result, confirm with ATAK. If multiple credible records remain,
   tap **Choose result**, inspect the county/district/road context, and select
   the intended record before confirming.

Switching Full/Structured modes preserves the same canonical draft, including
unclassified text. No result selection moves the map by itself; ATAK performs
the action only after its normal confirmation. A missing county dataset leaves
the coordinate tabs usable and displays data-management guidance.

County choices come only from imported datasets. District choices come only
from address rows in the selected county, so the list cannot promise coverage
that is not installed. The map-centre locality is placed first when the
installed township boundary strictly contains the centre and that locality is
searchable. All other choices follow the bundled Chunghwa Post order; imported
values not present in that reference remain available at the end. Choosing a
different county clears an incompatible district but preserves road and
remaining-address text.

The candidate dialog shows at most 20 rows. Exact matches are shown by
themselves. Otherwise the shortlist combines text-prefix, nearby house-number,
current-map-distance, and fallback records, then removes duplicates and fills
unused capacity. When the input does not contain a lane or alley, direct-road
house numbers rank ahead of lane/alley records.

See [Native Address workflow](tw-addr-search.md) for detailed examples.

<p align="center">
<img src="images/23a-native-address-full.png" alt="ATAK Go To Taiwan Address tab using one full-address field with the structured-field action at the upper right" width="900"><br>
<sub>Single-field mode keeps paste entry compact and its mode action reachable at the upper right; address content is redacted.</sub>
</p>

<p align="center">
<img src="images/23b-native-address-structured.png" alt="ATAK Go To Taiwan Address tab split into four fields with the single-field action aligned at the upper right" width="900"><br>
<sub>Structured mode keeps all four rows on the left and the mode action at the upper right; address values are redacted.</sub>
</p>

<p align="center">
<img src="images/25a-native-address-county-selector.png" alt="ATAK native Taiwan Address county selector with the active map-centre county first" width="740"><br>
<sub>The chooser contains only imported counties and promotes one strictly contained map-centre county.</sub>
</p>

<p align="center">
<img src="images/25b-native-address-district-selector.png" alt="ATAK native Taiwan Address district selector with the active map-centre district first" width="740"><br>
<sub>The selected county's imported districts follow the promoted map-centre district in postal order.</sub>
</p>

### Convert a map item's coordinate

1. Open a map item's details and tap its **Coordinate** value.
2. In **Convert Coordinate**, select **Taiwan**.
3. Switch among Taipower, TWD97, TWD67, and Address.

The coordinate tabs are prepared immediately. Address resolves asynchronously
from the exact map-item WGS84 point. It may display a nearby address record, but
it never replaces or snaps the host point to that record.

<p align="center">
<img src="images/20-atak-point-detail-coordinate.jpg" alt="ATAK point details with the Coordinate value highlighted" width="420"><br>
<sub>Tap the Coordinate value to open Convert Coordinate.</sub>
</p>

<p align="center">
<img src="images/21-atak-convert-coordinate.jpg" alt="ATAK Convert Coordinate dialog with Taiwan beside built-in panes" width="900"><br>
<sub>Taiwan is available beside ATAK's built-in coordinate panes.</sub>
</p>

### Host controls and read-only use

- **Auto Fill** converts ATAK's current point into the active tab only. Address
  performs reverse lookup without snapping the host point.
- **Clear** clears only the active tab and cancels an active Address lookup or
  candidate set when Address is selected.
- **Copy** requests the active canonical representation without moving the map.
- In read-only host dialogs, resolved values remain visible while fields,
  selectors, and candidate actions are disabled.

## 3. On-map readouts

The plugin can show map-centre (**MAP**), own-position (**ME**), and selected-
target (**TGT**) coordinate rows. Each uses the selected Taipower/TWD97/TWD67
display unit. Optional offline address rows show the nearest local address, a
direction arrow, and `~`/`~~` confidence markers according to Settings.

Tap a coordinate readout to copy the exact displayed string. Taipower shows an
out-of-range fallback outside its main-island coverage; zone-119 TWD values are
labelled so they are not confused with zone 121.

## 4. TW Coordinates settings and datasets

Open the plugin's only public Tools item, **TW Coordinates**, then select the
top **TW Coordinates settings** button, or navigate directly to:

**Settings → Tool Preferences → Specific Tool Preferences → TW Coordinates**

Available controls include:

- display unit and on-map readout visibility;
- MAP/ME/TGT address-row toggles and confidence preset;
- native Address candidate result ordering;
- plugin UI language (system, English, Traditional Chinese, or Japanese);
- dataset status and the internal offline-data manager.

The dataset-status row remains selectable even if all three address readout
toggles are off. Selecting it first closes Settings and then opens the manager,
so the destination is not hidden behind the Settings screen. In the manager,
import a supported ZIP/SQLite dataset, replace one county atomically, remove an
unneeded county, and inspect size/date/row provenance. See
[Offline address data](tw-offline-addr.md).

<p align="center">
<img src="images/24-offline-address-data.png" alt="Offline address data manager with the TW Coordinates settings button and two imported counties" width="700"><br>
<sub>The Tools entry opens this manager directly; use the top button to continue to plugin settings.</sub>
</p>

## 5. FAQ

**The plugin is missing from Tools.** Confirm the plugin is Loaded. After an
upgrade, reload or restart ATAK so cached retired Tools entries disappear.

**Address reports no matching dataset.** Open **TW Coordinates** and import the
boundary data plus the applicable county. Coordinate entry remains available.

**Address returns several rows.** This is intentional: no ambiguous record is
silently selected. Use **Choose result** and compare administrative context.
The list is a bounded, category-balanced shortlist rather than every stored
record on a dense road.

**Does lookup require a network?** No. The plugin deliberately omits the
`INTERNET` permission; coordinate conversion and address lookup are local.

**The readout says `out of range`.** Taipower grid covers the main island only.
Use TWD97/TWD67 zone 119 for applicable outer-island points.

**Report issues:** <https://github.com/swim-fish/atak_tw_coord_plugin/issues>
