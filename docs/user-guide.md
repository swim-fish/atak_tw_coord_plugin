# TW Coordinates Plugin — User Guide

> **Traditional Chinese version:** [user-guide_zh.md](user-guide_zh.md)

**Version:** v1.0.1 | **ATAK-CIV compatibility:** 5.4.0 — 5.7.x
**Plugin package:** `com.atakmap.android.twcoord.plugin`
**Signed by:** TAK Product Center ATAK Untrusted Plugin Release
**Latest release:** <https://github.com/swim-fish/atak_tw_coord_plugin/releases/latest>

This guide walks you through the full plugin workflow — install, configure, read coordinates on the map, and jump to a coordinate via the input page — illustrated with on-device screenshots. Every section corresponds to one screen capture; all images live under `docs/images/`.

---

## Table of contents

1. [Install & trust](#1-install--trust)
   1. [Confirm install in TAK Package Mgmt](#11-confirm-install-in-tak-package-mgmt)
   2. [Inspect the plugin certificate](#12-inspect-the-plugin-certificate)
2. [Configuration](#2-configuration)
   1. [Open the TW Coordinates preferences](#21-open-the-tw-coordinates-preferences)
   2. [Settings overview](#22-settings-overview)
   3. [Switch the display coordinate system](#23-switch-the-display-coordinate-system)
   4. [Switch the UI language](#24-switch-the-ui-language)
3. [On-map readout widget](#3-on-map-readout-widget)
4. [Tools menu — two entries](#4-tools-menu--two-entries)
5. [Coordinate input page](#5-coordinate-input-page)
   1. [Taipower tab (台電座標)](#51-taipower-tab-台電座標)
   2. [TWD97 tab](#52-twd97-tab)
   3. [TWD67 tab](#53-twd67-tab)
   4. [Auto Fill — populate from the map centre](#54-auto-fill--populate-from-the-map-centre)
   5. [Marker mode — eight drop types](#55-marker-mode--eight-drop-types)
   6. [SUBMIT vs OPEN ATAK ICON MENU](#56-submit-vs-open-atak-icon-menu)
   7. [Recent — recently submitted entries](#57-recent--recently-submitted-entries)
6. [Accuracy & coverage notes](#6-accuracy--coverage-notes)
7. [FAQ](#7-faq)

---

## 1. Install & trust

### 1.1 Confirm install in TAK Package Mgmt

Download `ATAK-Plugin-TWCoord-v1.0.1-ATAK-5.4+.apk` from GitHub Releases and sideload it with `adb install -r <apk>` or via an on-device file manager. ATAK will normally pop the **TAK Package Mgmt** confirmation dialog automatically.

![TAK Package Mgmt confirmation dialog for TW Coordinates](images/01-package-mgmt-dialog.jpg)

The dialog shows:

| Field | Value |
| --- | --- |
| Title | `TW Coordinates` |
| Description | `Display map-centre and own-position coordinates in Taipower / TWD97 / TWD67 units.` |
| Status line | `TW Coordinates v1.0.1 () - [5.4.0] (1) is loaded and current` |

Tap **More Details** to drill in (next section), or dismiss to use the plugin immediately. **Uninstall** removes the plugin.

> The "third-party plugin" trust prompt appears on first install. **Subsequent upgrades** under the same signing cert pass through silently — you can `adb install -r` in place.

---

### 1.2 Inspect the plugin certificate

In the previous dialog tap **More Details** to expand the metadata view.

![TW Coordinates detailed metadata view](images/02-package-mgmt-details.jpg)

Key fields:

| Field | Value |
| --- | --- |
| Product Type | `ATAK Plugin` |
| Package | `com.atakmap.android.twcoord.plugin` |
| Install / Update Date | UTC timestamps |
| Local Device — Version | `1.0.1 () - [5.4.0] (1)` |
| TAK Requirement | `com.atakmap.app@5.4.0.CIV` (targets 5.4+) |
| Update Availability | `Sideloaded plugins` / `Current` |
| OS Suggested Version | Android 8.0 (Oreo) or newer |
| Validation row | `The signature for the plugin is VALID` ✓<br>`ATAK Core: Release Plugin: Release` ✓ |

Tap **Certificate** to view the full X.509 certificate. You should see:

```
Issuer / Subject: CN=TAK Product Center ATAK Untrusted Plugin Release,
                  OU=Product Center, O=TAK, L=Fort Belvoir,
                  ST=Virginia, C=US
Signature Algorithm: sha384WithRSAEncryption
Public Key Algorithm: rsaEncryption, 4096-bit RSA
SHA-256 fingerprint: f24a3805 7275fcec f67be975 ab803d12
                     f75dc235 81bef69c ba9eb03a 15bb8c17
```

This certificate is issued by the TAK Third Party Pipeline and is shared by every "non-first-party TAK Product Center build" plugin. **If the fingerprint does not match, do NOT install** — the APK has been tampered with somewhere along the supply chain.

---

## 2. Configuration

### 2.1 Open the TW Coordinates preferences

ATAK → **Settings** (gear icon) → **Tool Preferences** → **Specific Tool Preferences**. Find **TW Coordinates** in the list (hex plate with the white "TW" stencil and corner brackets).

![Specific Tool Preferences list with TW Coordinates highlighted](images/03-settings-tool-preferences-list.jpg)

Tap it to open the plugin's preferences page.

---

### 2.2 Settings overview

![TW Coordinates preferences page](images/04-tw-coordinates-settings.jpg)

The page has three sections:

**TW COORDINATES** (editable)

| Item | Default | Description |
| --- | --- | --- |
| **Display unit** | `Taipower grid (台電座標)` | Which coordinate system the on-map readout widget renders in |
| **UI language** | `Use system locale` | Language override for plugin-owned strings (system / English / Traditional Chinese / Japanese) |

**ACCURACY NOTICE** (read-only)

- **TWD97**: error < 1 m across all coverage areas.
- **TWD67**: ±3–5 m on the main island; ±10–20 m on outer islands (Penghu / Kinmen / Matsu).
- **Taipower grid** covers the main island only; outer-island fixes show *out of range* with a WGS84 fallback line.

**Open Coordinate Input** (shortcut)

Opens the same coordinate input page as the Tools menu's *TW Coord GoTo* entry (see [§5](#5-coordinate-input-page)).

---

### 2.3 Switch the display coordinate system

Tap **Display unit**. A selection dialog appears:

![Display unit selection dialog](images/05-display-unit-dialog.jpg)

Pick one:

| Option | Readout example (rendered by this plugin's formatter) |
| --- | --- |
| **Taipower grid (台電座標)** | `G5342 HE7592` (1 letter + 4 digits block + space + 2 letters + 2 or 4 digits cell; 10 m precision = 10 chars, 1 m precision = 12 chars) |
| **TWD97 / TM2 z121** | `214,000m 2,671,243m` (easting + northing in metres, locale-aware thousands separator) |
| **TWD67 / TM2 z121** | `213,915m 2,670,418m` (same format as TWD97; values **differ from TWD97** for the same location due to the datum shift — ±3–5 m on the main island) |

> **z119 corresponds to the outer-island central meridian** (119°). The plugin auto-selects z121 / z119 based on the map-centre longitude — no manual switching needed; on z119 the example gains a trailing `" z119"` suffix: `214,000m 2,671,243m z119`.

The dialog closes immediately on selection and the readout widget refreshes.

---

### 2.4 Switch the UI language

Tap **UI language**. A selection dialog appears:

![UI language selection dialog](images/06-ui-language-dialog.jpg)

Pick one:

| Option | Behaviour |
| --- | --- |
| **Use system locale** | Tracks the Android system language |
| **English** | Force English |
| **中文（正體）** | Force Traditional Chinese |
| **日本語** | Force Japanese |

This setting affects *only* this plugin's strings (readout widget, preferences page, coordinate input page). ATAK's main UI and other plugins are unaffected.

---

## 3. On-map readout widget

Back on the main map. The readout widget appears on the right edge against a translucent dark background:

![Map view with coordinate readout widget and ATAK radial menu](images/07-map-readout-widget.jpg)

The widget shows two values, and **only those two are owned by this plugin**: own-position and map-centre coordinates in the configured Taiwan grid (Taipower / TWD97 / TWD67 per §2.3). Everything else in the screenshot comes from somewhere else:

- Callsigns like `BX5ACK`, the UTM line `51R TG …`, altitudes `… m HAE`, ground speed `… km/h`, magnetic bearing `… °M EST`, Eye Alt — **ATAK's built-in HUD**.
- The `TWD97 E 214000 N 2671243` line visible in the screenshot — emitted by a **separate TDAL plugin** (its icon, a globe with a cross, is visible in the left toolbar), not by this plugin.

| Line | Example (from the screenshot) | Meaning |
| --- | --- | --- |
| Own position (callsign = `魷魚 BX5ACK`, the own marker) | **`ME TPC: G5342 HE7419`** | Taipower code at the operator's current location |
| Map centre | **`MAP TPC: G5342 HE7592`** | Taipower code at the current map-centre position |

> Both lines follow the **Display unit** setting (§2.3) — switching to TWD97 / TWD67 changes them to easting/northing format. When the GPS fix becomes stale past the threshold (default 10 s), readout text dims from white to pale yellow.

The **ATAK standard radial menu** (long-press on the map; the black wheel of 8 actions in the screenshot) is unrelated to the plugin, but it interacts with markers dropped via Marker mode (§5.5) — you can edit / delete / route through them with the radial.

---

## 4. Tools menu — two entries

Open ATAK's **Tools** menu (bottom-right toolbar button or edge-swipe).

![Tools menu showing both TW entries](images/08-tools-menu.jpg)

The plugin registers two Tools-menu entries (the icons are vector XML drawables, tinted white by ATAK's silhouette mask at runtime — the standalone previews below are rendered straight from this repo's vector geometry by `scripts/render-doc-icons.py`):

| Icon | Name | Function |
| --- | --- | --- |
| ![TW Coord GoTo](images/08b-tools-icon-tw-coord-goto.png) | **TW Coord GoTo** | Open the coordinate input page (§5) |
| ![TW Coordinates](images/08a-tools-icon-tw-coord.png) | **TW Coordinates** | Cycle the readout widget's display unit (Taipower → TWD97 → TWD67 → Off → Taipower …) |

> Both icons render as white silhouettes in the Tools menu — ATAK applies a tint mask, so the source artwork must be pure line-art on a transparent background to render correctly. The same brand mark appears in colour (OD hexagonal plate) in the Plugin Manager and Settings entries.

---

## 5. Coordinate input page

Open it via Tools → **TW Coord GoTo**, or via the **Open Coordinate Input** shortcut in the preferences page. A side panel (DropDown) slides in from the right. The content scrolls vertically so every control is reachable even on small screens (fixed in v1.0.1).

### 5.1 Taipower tab (台電座標)

![Coordinate Input — Taipower tab](images/09-coordinate-input-taipower.jpg)

| Element | Description |
| --- | --- |
| **Tab strip** | `Taipower` / `TWD97` / `TWD67` (dark = active) |
| **Auto Fill button** | Populate the field from the current map-centre coordinate (§5.4) |
| **Input field** | `H7509 DB4016` format — 1 letter + 4 digits (5×5 km block), space, 2 letters + 2 digits, with an optional 2 more digits for 10 m / 1 m resolution |
| **Marker mode** | Eight drop types (§5.5) |
| **SUBMIT** | Primary action — pan the map to the coordinate, optionally drop a marker per Marker mode (§5.6) |
| **OPEN ATAK ICON MENU** | Delegate to ATAK's native Enter Location pane; pick a pallet / icon and drop there |
| **Recent** | The 10 most recently submitted entries (§5.7) |

The parser normalises the input (strip whitespace + parens, uppercase) and then requires the result to be **exactly 9 chars (10 m precision, e.g. `H7509DB40`) or 11 chars (1 m precision, e.g. `H7509DB4016`)**. In practice a space is usually typed mid-string for readability (`H7509 DB40` or `H7509 DB4016`); both spaced and unspaced forms pass. Any other length surfaces a red inline error under the input field the moment you type.

---

### 5.2 TWD97 tab

![Coordinate Input — TWD97 tab](images/10-coordinate-input-twd97.jpg)

Tab-specific elements:

| Element | Description |
| --- | --- |
| **Easting (m)** + **Northing (m)** | Two integer fields, laid out side-by-side on the same row |
| **Zone** | `121 (main island)` / `119 (outer island)` — pick one |

Picking the wrong zone (e.g. main-island coordinates on z119) shows an amber advisory under the input.

---

### 5.3 TWD67 tab

![Coordinate Input — TWD67 tab](images/11-coordinate-input-twd67.jpg)

Layout is identical to TWD97. The difference is that the backend applies a 4-parameter Bursa-Wolf datum shift to convert TWD67 to WGS84. For accuracy see §2.2 / [§6](#6-accuracy--coverage-notes)'s **ACCURACY NOTICE**.

---

### 5.4 Auto Fill — populate from the map centre

The Auto Fill button on each tab:

1. Reads the current map-centre WGS84 latitude/longitude
2. Reverse-converts it into the tab's format (Taipower string, or TWD97 easting/northing, or TWD67 easting/northing)
3. Fills the input field(s), and sets the zone radio where applicable

**The button is greyed out (disabled)** when the map centre cannot be expressed in that coordinate system:

| Condition | Disabled buttons |
| --- | --- |
| Map centre outside Taiwan's coverage box | All three Auto Fill buttons disabled |
| Map centre on an outer island (Penghu / Kinmen / Matsu) | Only Taipower's Auto Fill disabled; TWD97 / TWD67 still work |

Tapping a disabled button surfaces a localised toast explaining why.

---

### 5.5 Marker mode — eight drop types

The Marker mode block is a 2-row × 4-column grid:

| Row 1 | Row 2 |
| --- | --- |
| **Move only** (white → arrow) — pan only, no marker | **Friendly** (blue rectangle) |
| **Waypoint** (white +) — generic waypoint | **Hostile** (red diamond) |
| **GoTo Pin** (orange pin) — destination pin indistinguishable from one dropped by ATAK's native GoToMapTool | **Neutral** (green square) |
| **Point of Interest** (orange bull's-eye) — SPI/POI marker | **Unknown** (yellow blob) |

Colour and shape follow MIL-STD-2525 conventions. **Move only** is the default (highlighted with a darker background).

> Picking any non-`Move only` mode causes SUBMIT to drop the marker via ATAK's standard `PlacePointTool`. **Callsign is ATAK's default auto-generated form** (for example `S.NN.HHmmss` for GoTo Pin) — the dropped marker is behaviourally identical to one placed via long-press → radial menu (movable, editable, deletable).

---

### 5.6 SUBMIT vs OPEN ATAK ICON MENU

Both buttons share the same coordinate parsing + last-input persistence + Recent push prelude. The difference is what happens at drop time:

| Button | Drop mechanism | When to use |
| --- | --- | --- |
| **SUBMIT** | Pan to the coordinate + drop a marker via `PlacePointTool` per the selected Marker mode | When one of the 8 MIL-STD marker types is what you need |
| **OPEN ATAK ICON MENU** | Pan to the coordinate + close our pane + broadcast `EnterLocationDropDownReceiver.START` to bring up ATAK's native Enter Location pane | When you want ATAK's own iconsets / pallets (custom icons, fine-grained SIDC, etc.) |

> **Tip:** to drop a custom icon, open ATAK's Enter Location pane first and pick the pallet + concrete icon you want, then come back and tap OPEN ATAK ICON MENU here. The map pans to your typed coordinate, ATAK's pane reopens, and a single tap drops your chosen icon there.

Both paths emit a `com.atakmap.android.twcoord.GOTO_NAV_COMPLETED` intent for downstream observers (no subscribers in v1).

---

### 5.7 Recent — recently submitted entries

Every successful SUBMIT or OPEN ATAK ICON MENU pushes the `(unit, raw value)` tuple onto the Recent list:

- Capacity 10, **FIFO eviction**
- Duplicate `(unit, raw value)` is deduped (promoted to the top)
- Each row has two widgets:
  - **Tappable label** — tap to (a) switch to that unit's tab, (b) refill the input with the row's values, and (c) restore the zone radio if applicable. You can then tweak a digit or two and re-submit.
  - **Remove button** — deletes only that row.
- The list persists in `pref_goto_recent_json`, **surviving app restarts**.

When empty, the placeholder `No recent entries.` is shown instead.

---

## 6. Accuracy & coverage notes

| Projection | Main-island error | Outer-island error | Zones |
| --- | --- | --- | --- |
| **TWD97 / TM2** | < 1 m | < 1 m | z121 (main island) / z119 (Penghu / Kinmen / Matsu) |
| **TWD67 / TM2** | ±3–5 m | ±10–20 m | z121 / z119 same as TWD97 |
| **Taipower grid** | — | **not supported** (main-island only) | z121 |

Technical notes:
- TWD67 → WGS84 uses a **4-parameter Bursa-Wolf** translation (dx/dy/dz + scale, no rotation); the larger outer-island error is intrinsic to that model.
- The plugin issues zero network traffic — the AndroidManifest does not declare `INTERNET` permission, and all conversions are pure local computation.

---

## 7. FAQ

### Q1: After install the plugin doesn't appear in the Tools menu?

Walk through **Settings → Plugins** (or **TAK Package Mgmt**) and confirm Status: *Loaded*. If you see *not loaded* or it's missing entirely:

1. If a **Load** button is offered in the dialog, tap it.
2. Otherwise force-stop ATAK and restart:
   `adb shell am force-stop com.atakmap.app.civ`
3. Still missing — Uninstall and reinstall.

### Q2: Do I need to uninstall before upgrading (v1.0.0 → v1.0.1)?

No. The **same TPP signing cert** is used across versions, so `adb install -r` works in place; user settings (language, unit, Recent) are preserved. **Only when switching to a different signing source** (e.g. from a v1.0.0 community demo cert to the TPP cert) does Android require an uninstall first.

### Q3: The readout widget sometimes doesn't show the coordinate?

With **Display unit** set to *Taipower grid*, an outer-island map centre will show `out of range`. Switch to TWD97 / TWD67 instead.

### Q4: How do I delete a marker I dropped via SUBMIT?

Long-press the marker → ATAK's standard radial menu → trash-can icon (Delete). This is ATAK's native behaviour; the plugin doesn't customise it.

### Q5: Where can I see the build / signing / security-scan evidence?

Every GitHub Release attaches:
- `mapping-vX.Y.Z.txt` — R8 obfuscation map (for crash-log de-mangling)
- `security-scan-vX.Y.Z.pdf` — Fortify SAST report
- `dependency-check-vX.Y.Z.html` — OWASP dependency CVE report
- `source-archive-vX.Y.Z.zip` — the exact source archive submitted to TAK TPP (reproducibility)

---

**Report issues:** <https://github.com/swim-fish/atak_tw_coord_plugin/issues>
**Release list:** <https://github.com/swim-fish/atak_tw_coord_plugin/releases>
