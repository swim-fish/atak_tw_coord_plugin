# TW Coordinates Plugin — User Guide

> **Traditional Chinese version:** [user-guide_zh.md](user-guide_zh.md)

**Version:** v1.0.4 | **ATAK-CIV compatibility:** 5.4.0 — 5.7.x
**Latest release:** <https://github.com/swim-fish/atak_tw_coord_plugin/releases/latest>

This is the short version. If you just want to install the plugin and use it, read this. For deeper background — accuracy notes, datum shift internals, MIL-STD-2525 marker reference — see the source files in `docs/`.

---

## Table of contents

1. [Download & install](#1-download--install)
2. [Confirm it loaded](#2-confirm-it-loaded)
3. [Use it from the Tools menu](#3-use-it-from-the-tools-menu)
4. [Settings](#4-settings)
5. [FAQ](#5-faq)

---

## 1. Download & install

<table>
<tr>
<td width="280" valign="top"><img src="images/01-package-mgmt-dialog.jpg" alt="TAK Package Mgmt confirmation dialog" width="280"></td>
<td valign="top">

1. Grab the latest APK from the [Releases page](https://github.com/swim-fish/atak_tw_coord_plugin/releases/latest). The file is named `ATAK-Plugin-TWCoord-vX.Y.Z-ATAK-5.4+.apk`.
2. Sideload it onto the device. Either:
   - `adb install -r <apk>` from a workstation, or
   - copy the APK to the device and tap it in a file manager.
3. ATAK pops the **TAK Package Mgmt** dialog. Accept it.

That's it — no extra steps, no separate "enable" toggle. The plugin is now active inside ATAK.

> Upgrading from a previous version? Just `adb install -r` over the top. The signing certificate is identical across releases, so Android keeps your settings and your Recent list intact.

</td>
</tr>
</table>

---

## 2. Confirm it loaded

If you want to double-check before relying on it:

- **Settings → Plugins** (or **TAK Package Mgmt**) → look for **TW Coordinates** with status **Loaded**.
- Or just open the Tools menu (next section). If the two TW entries are there, the plugin is live.

If the entries are missing, force-stop ATAK and reopen it:

```
adb shell am force-stop com.atakmap.app.civ
```

---

## 3. Use it from the Tools menu

<table>
<tr>
<td width="280" valign="top"><img src="images/08-tools-menu.jpg" alt="Tools menu showing both TW entries" width="280"></td>
<td valign="top">

Open ATAK's **Tools** menu (the toolbar button in the bottom-right, or edge-swipe). The plugin adds **two** entries:

- <img src="images/08b-tools-icon-tw-coord-goto.png" alt="TW Coord GoTo icon" width="24"> **TW Coord GoTo** — opens a side panel where you type a Taiwan coordinate and the map jumps there (§3.1).
- <img src="images/08a-tools-icon-tw-coord.png" alt="TW Coordinates icon" width="24"> **TW Coordinates** — cycles the on-map readout widget through Taipower → TWD97 → TWD67 → Off → Taipower … (§3.2).

</td>
</tr>
</table>

### 3.1 TW Coord GoTo — jump to a coordinate

<table>
<tr>
<td width="280" valign="top"><img src="images/09-coordinate-input-taipower.jpg" alt="Coordinate Input — Taipower tab" width="280"></td>
<td valign="top">

Tap **TW Coord GoTo** and a panel slides in from the right with three tabs: **Taipower**, **TWD97**, **TWD67**.

Workflow on every tab is the same:

1. **Type the coordinate** in that tab's format, or tap **Auto Fill** to copy the current map-centre coordinate into the field.
2. **(Optional) pick a Marker mode** — eight choices laid out as a 2×4 grid:
   - *Move only* (default — just pan, no marker)
   - *Waypoint*, *GoTo Pin*, *Point of Interest*
   - *Friendly*, *Hostile*, *Neutral*, *Unknown* (MIL-STD-2525 colours)
3. **Tap SUBMIT.** The map pans to your coordinate. If a Marker mode other than *Move only* is picked, a marker is dropped there using ATAK's native marker tool — long-press it later to edit, move, or delete it from the standard radial menu.

Alternative drop button: **OPEN ATAK ICON MENU** pans to the coordinate and then hands off to ATAK's native Enter Location pane, letting you pick any iconset / pallet ATAK has installed.

Every successful submit is saved in **Recent** (10 entries, oldest dropped first). Tap a Recent row to refill the input; tap its **×** to delete just that row.

</td>
</tr>
</table>

### 3.2 TW Coordinates — on-map readout

<table>
<tr>
<td width="280" valign="top"><img src="images/07-map-readout-widget.jpg" alt="Map readout widget" width="280"></td>
<td valign="top">

Once the readout is on, two lines appear on the right edge of the map:

- **ME TPC: …** — your own position
- **MAP TPC: …** — the current map-centre position

Both lines render in whichever coordinate system you cycled to. To turn the readout off entirely, keep tapping **TW Coordinates** in the Tools menu until it reaches the *Off* state.

</td>
</tr>
</table>

---

## 4. Settings

<table>
<tr>
<td width="280" valign="top"><img src="images/04-tw-coordinates-settings.jpg" alt="TW Coordinates preferences page" width="280"></td>
<td valign="top">

Open: ATAK → **Settings** (gear icon) → **Tool Preferences** → **Specific Tool Preferences** → **TW Coordinates**.

There are exactly **two** things you can change, plus one shortcut button:

- **Display unit** — which coordinate system the on-map readout widget uses: Taipower / TWD97 / TWD67. Same effect as cycling via the Tools menu (§3.2).
- **UI language** — forces the plugin's strings to *Use system locale* / *English* / *中文（正體）* / *日本語*. Only affects this plugin — the rest of ATAK is untouched.
- **Open Coordinate Input** *(button)* — shortcut equivalent of Tools → TW Coord GoTo.

There is also a read-only **Accuracy notice** block summarising error bounds (TWD97 < 1 m, TWD67 ±3–5 m main island / ±10–20 m outer islands, Taipower main-island only). Reference info — nothing to tap.

</td>
</tr>
</table>

---

## 5. FAQ

**Q: The plugin doesn't show up in the Tools menu.**
Check **Settings → Plugins** → status should be *Loaded*. If not, force-stop ATAK (`adb shell am force-stop com.atakmap.app.civ`) and reopen. If it's still missing, uninstall and reinstall the APK.

**Q: Do I need to uninstall before upgrading?**
No. Use `adb install -r`. The signing certificate is the same across releases; settings and Recent entries are preserved.

**Q: The readout says `out of range`.**
You're on *Taipower grid* and the map centre is on an outer island (Penghu / Kinmen / Matsu) where Taipower doesn't apply. Switch to TWD97 or TWD67.

**Q: How do I delete a marker I dropped via SUBMIT?**
Long-press the marker → ATAK's standard radial menu → trash-can icon. This is ATAK's native behaviour; the plugin doesn't customise it.

**Q: Where can I see the build / signing / security-scan evidence?**
Every GitHub Release attaches the R8 mapping file, the Fortify SAST PDF, the OWASP dependency-check HTML, and the exact source archive submitted to TAK TPP.

---

**Report issues:** <https://github.com/swim-fish/atak_tw_coord_plugin/issues>
**Release list:** <https://github.com/swim-fish/atak_tw_coord_plugin/releases>
