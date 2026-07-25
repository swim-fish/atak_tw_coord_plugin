# Project Overview

TW Coordinates is an offline
[ATAK-CIV](https://tak.gov/products/atak-civ) plugin for Taiwan coordinate
display, native coordinate and address entry, and county-scoped offline
address data.

The current information architecture has one native Taiwan entry pane and one
public Tools item:

```text
ATAK Go To / Convert Coordinate
  → Taiwan
      → Taipower | TWD97 | TWD67 | Address

ATAK Tools
  → TW Coordinates
      → Offline address data
      → TW Coordinates settings
```

## Capabilities

| Capability | Current behavior |
|---|---|
| Taiwan coordinate systems | Taipower grid, TWD97 / TM2, and TWD67 / TM2 |
| Native coordinate entry | Adds one Taiwan pane to ATAK's shared Go To and Convert Coordinate dialogs |
| Native offline Address | Supports full and structured address input, explicit candidate selection, and reverse display without snapping the host point |
| On-map readouts | Shows MAP, ME, and TGT coordinates; optional address rows add direction and confidence indicators |
| Offline data management | Imports, replaces, and removes county SQLite or ZIP datasets through **TW Coordinates** |
| Localisation | English, Taiwan Traditional Chinese, and Japanese, with a persistent in-app override |
| Clipboard | Tapping a coordinate readout copies the exact displayed value |
| Outer islands | TWD97/TWD67 zone 119 supports Penghu, Kinmen, and Matsu; Taipower remains main-island only |
| Privacy | No `INTERNET` permission, telemetry, analytics, or crash-reporting SDK |

ATAK continues to own Go To, Convert Coordinate, Auto Fill, Clear, Copy,
read-only state, and confirmation. The plugin contributes the Taiwan pane and
its offline data. Selecting an address candidate prepares a result; the normal
ATAK confirmation performs the final host action.

## Current screens

<p align="center">
<img src="images/23a-native-address-full.png" alt="ATAK Go To Taiwan Address pane in full-address mode" width="900"><br>
<sub>The native Taiwan pane provides coordinate and offline Address entry
inside ATAK's existing dialog.</sub>
</p>

<p align="center">
<img src="images/24-offline-address-data.png" alt="TW Coordinates offline address data manager" width="700"><br>
<sub>The plugin's only public Tools item opens the offline address manager;
the top action continues to plugin settings.</sub>
</p>

## Offline address model

Address lookup uses datasets already imported on the Android device. County
and district selectors only offer searchable imported data. A bundled
Chunghwa Post catalog supplies stable display order but does not claim that
unimported data exists.

Forward lookup can resolve one unique exact match automatically. Credible
ambiguous results require operator selection. Reverse lookup labels the exact
ATAK-supplied WGS84 point and never replaces it with the nearby address
record's geometry.

See:

- [Native Taiwan Address guide](tw-addr-search.md).
- [Offline address data guide](tw-offline-addr.md).
- [Native pane UI contract](ui/native-taiwan-coordinate-entry.md).
- [Offline manager UI contract](ui/offline-address-page.md).

## Coordinate behavior and limits

- WGS84 is the interchange representation at ATAK boundaries.
- TWD97 and TWD67 support TM2 zones 121 and 119.
- Taipower codes cover the main island only.
- TWD67 uses the project's accepted 4-parameter transformation and has lower
  expected accuracy on outer islands.

See [Coordinate systems, coverage, and accuracy](reference/coordinate-systems.md)
for formats, accuracy budgets, regression evidence, and provenance.

## Compatibility

| Axis | Current value |
|---|---|
| Android compile / minimum SDK | 36 / 26 |
| ATAK compile SDK | ATAK-CIV 5.7.0.9 |
| Minimum declared ATAK runtime | ATAK-CIV 5.5.0 |

A successful 5.7.0.9 build or device run does not prove the pending physical
ATAK 5.5 compatibility matrix. See
[ADR-0022](adr/0022-set-minimum-atak-runtime-to-5-5.md),
[ADR-0024](adr/0024-use-atak-5-7-0-9-compile-sdk.md), and
[release readiness](contributing/release-readiness.md).
