<!--
  End-user feature guide (English) for the Offline Address (reverse-geocode) feature.
  Icons in docs/images are rendered from the real Android vector drawables by
  scripts/render-doc-icons.py — re-run it if the drawables change.
-->
# TW Offline Addr — feature guide

> See the Taiwan street address of any point on the map, **fully offline**. No
> network, no Google.

**Feature:** 004 Offline Address · 008 storage-page redesign | **Versions:** v1.1.0 → **v1.3.0** | **Targets:** ATAK-CIV 5.5.0 – 5.7.x | **Language:** English · [中文](tw-offline-addr_zh.md)

---

## What does it do?

<table>
<tr>
<td width="150" valign="top" align="center">
<img src="images/08c-tools-icon-offline-address.png" alt="TW Offline Addr tool icon" width="120"><br>
<sub>Tools-menu icon<br>"TW Offline Addr"</sub>
</td>
<td valign="top">

Turns a **coordinate → address**. When you move the map, the plugin
automatically resolves the **county + township + road + house number** for that
point and shows it in the on-map readout.

- ✅ **Fully offline**: the address data is imported onto the device, so it works
  in the field with no signal.
- ✅ **Three points**: map centre, my own location, and a target point each get
  their own address line.
- ✅ **Taiwan house-number format**, e.g. 「臺中市西區臺灣大道二段 100 號」.

> Its mirror-image sibling is **[TW Addr Search](tw-addr-search.md)** (type an
> address → pan the map there).

</td>
</tr>
</table>

### Screenshot UI is in Chinese — label glossary

The screenshots in this guide show the app in Chinese. The English UI labels and
their Chinese equivalents:

| English (UI) | Chinese (screenshot) |
|---|---|
| Import / Add more | 匯入… / 匯入更多 |
| Replace… / Remove | 取代… / 移除 |
| Total … on disk | 共佔用 … |
| Base data | 基礎資料 |
| Done | 完成 |

> The page is titled **Offline Address** in the English UI (referred to here as
> **TW Offline Addr**).

---

## At a glance

```
①  Import the address data (one file per county, once only)
        │
        ▼
②  Move the map to the point you care about
        │
        ▼
③  The readout shows that point's address automatically
```

---

## Getting the offline data pack (`tw-central-full.zip`)

The address data is **not bundled** in the plugin (it is too large) — you fetch
it once and import it. Central-Taiwan data is packaged as **`tw-central-full.zip`**
(covers Taichung, Changhua, etc.), produced by the
**[atak-tw-address-generator](https://github.com/swim-fish/atak-tw-address-generator)**
project (built from MOI boundary data + TGOS house-number data). Once you have
the ZIP, import it per the next section.

### File sizes & on-device footprint

The ZIP holds several files, but **the plugin only uses the county boundary and
address (places) files**; OSM landmarks and road files are skipped automatically
and take **no device space**.

| File in the ZIP | Contents | Unzipped (approx.) | On device after import |
|---|---|---:|:---:|
| `townships.sqlite` | county boundaries (**required**) | 10 MB | ✅ ~10 MB |
| `places-taichung.sqlite` | Taichung addresses (~731k) | 324 MB | ✅ (if you import Taichung) |
| `places-changhua.sqlite` | Changhua addresses (~427k) | 187 MB | ✅ (if you import Changhua) |
| `places-osm.sqlite` | OSM landmarks | 162 MB | ❌ skipped |
| `roads.sqlite` | roads | 24 MB | ❌ skipped |
| **`tw-central-full.zip`** | full download (compressed) | **~149 MB** | deletable after import |

### How much space do I need after import?

Only the **boundary + the counties you import** count:

| What you import | On-device footprint (approx.) |
|---|---:|
| boundary + Taichung | **334 MB** |
| boundary + Changhua | **197 MB** |
| boundary + Taichung + Changhua | **521 MB** |

> 💡 **Space tip**: importing unzips first, so keep roughly "ZIP + the footprint
> above" free temporarily; **the ZIP can be deleted after import**. Remove
> counties you don't need any time to reclaim space.

> Figures are measured against the central-Taiwan build; other builds may differ
> slightly.

---

## ① First use: import the address data

Once you have `tw-central-full.zip`, import it. Data ships as a ZIP or a single
`.sqlite`, one file per county.

1. Open ATAK's **Tools menu** → tap the **TW Offline Addr** icon <img src="images/08c-tools-icon-offline-address.png" width="20" align="center">.
2. The first time you see the **empty state** with an **Import** button.
3. Tap it and pick the address file in the browser (`tw-central-full.zip` or
   `places-臺中市.sqlite`, etc.).
4. After import the page lists the active counties and their data dates.

```
┌──────────────────────────────┐
│  TW Offline Addr             │
│                              │
│  No address data imported    │
│                              │
│        [  Import  ]          │  ← first-run screen
└──────────────────────────────┘
              │ after import
              ▼
┌──────────────────────────────────────┐
│  Total 498.9 MB on disk                │
│  ▓▓▓▓▓▓▓▓▓▓▓▒▒▒▒▒▒░  ← per-county bar   │
│  ● Taichung ● Changhua ● Base  ← legend │
│  ┌────────────────────────────────┐   │
│  │▎Taichung 115-01 · 731005    310.2 MB ⋮│  ← tap ⋮ to Replace/Remove
│  │▎Changhua 114-05 · 426690    179.1 MB ⋮│
│  └────────────────────────────────┘   │
│        [  Import…  ]                    │
│  _boundary (townships.sqlite): 9.6 MB   │
└──────────────────────────────────────┘
```

Each county has a **colour swatch** on its left that matches the same-colour
segment in the **usage bar** at the top and the legend, so you can see at a
glance which county takes the most disk space; the top "Total" is the sum of all
counties **+** the `_boundary` base data.

> 💡 You can import **multiple counties**. Across counties the plugin **first
> uses the boundary file to decide which county a point falls in**, then queries
> **only that county's** address data (never all counties at once). So even with
> Taichung + Changhua imported, a point near the county line resolves to the
> correct county.

<p align="center">
  <img src="images/17-tw-offline-addr-usage.jpg" alt="TW Offline Addr: total usage + stacked bar + legend + per-county rows" width="560"><br>
  <sub>Actual screen: the "Total 498.9 MB on disk" figure and the stacked bar
  (<b>Taichung 310.2 MB</b> / <b>Changhua 179.1 MB</b> / <b>Base data 9.6 MB</b>)
  with a matching legend; below it, the compact per-county rows (colour swatch,
  data date · row count, size, <b>⋮</b>); the dashed block at the bottom is
  <code>_boundary (townships.sqlite)</code>.</sub>
</p>

<p align="center">
  <img src="images/18-tw-offline-addr-overflow.jpg" alt="Tapping a county row's ⋮ opens a Replace / Remove menu" width="560"><br>
  <sub>The <b>⋮</b> on the right of each county row opens a menu: <b>Replace…</b>
  (swap in a newer file for the same county) and a red <b>Remove</b> (delete that
  county's data, with a confirmation).</sub>
</p>

---

## ② + ③ Read addresses on the map

After import you do nothing — move the map into any imported county's area and
the **readout** shows the address automatically.

<p align="center">
  <img src="images/12-tw-offline-addr-readout.jpg" alt="Bottom-left live address readout with a direction arrow" width="320"><br>
  <sub>The bottom-left readout shows the map-centre address live:
  <code>→ ~ 台中市西區土庫里五權西路一段 2 號</code>. The leading <b>→</b> is a
  <b>direction arrow</b> (points toward the actual address point — see below);
  <code>~</code> is the confidence marker.</sub>
</p>

There are up to three readout lines, one per point; the address sits under the
coordinate:

```
MAP   24.1469, 120.6839
      臺中市西區臺灣大道二段100號
ME    24.1502, 120.6701
      臺中市西區美村路一段88號
```

- **MAP**: the point at the centre of the map.
- **ME**: your own (self-marker) position.
- **TGT**: a selected target point.

Which lines show is toggled under **Settings → Tool Preferences → TW
Coordinates**.

<p align="center">
  <img src="images/13-tools-and-readouts.png" alt="Three on-map address readouts; the Tools menu shows the plugin's four tools" width="760"><br>
  <sub>Address readouts appear at the <b>bottom-left / top-right / bottom-right</b>
  of the map; the Tools menu on the right lists the plugin's four tools:
  <b>TW Coordinates</b>, <b>TW Coord GoTo</b>, <b>TW Offline Addr</b>,
  <b>TW Addr Search</b>.</sub>
</p>

### What is the direction arrow at the front of the address? (v1.3.0)

When the resolved house number is **not right under the query point**, the
address is prefixed with an **8-point compass arrow** pointing toward where the
actual house number lies from that line's point — so you can walk/scan toward it:

| Shown | Meaning |
|---|---|
| `→ ~ 臺中市…2號` | the actual address is to the **east** (→), a little away (`~`) |
| `↑ 臺中市…100號` | the actual address is to the **north** (↑) |

Arrows are `↑ N, ↗ NE, → E, ↘ SE, ↓ S, ↙ SW, ← W, ↖ NW`, quantised to the
nearest of 8 directions; no arrow is shown when the record is essentially on the
point (within 3 m). Each of the three readouts (MAP / ME / TGT) uses its own
point as the reference.

### What is the `~` in front of the address?

When the nearest house number is **some distance** from the query point, a tilde
is added to signal "this is a nearby number, not directly underneath":

| Shown | Meaning |
|---|---|
| `臺中市…100號` | right on the house number (very accurate) |
| `~ 臺中市…100號` | a nearby number (slightly off) |
| `~~ 臺中市…100號` | a farther number (for reference) |

---

## Managing imported counties

Back on the **TW Offline Addr** page, each county is one row; tap the **⋮** on
its right to open a menu:

- **Replace…**: overwrite that county with newer data. Tap ⋮ → "Replace…" → pick
  the new file; **no need to Remove first**. The old data is kept **until the
  import succeeds, and is not lost on failure** (updates are safe). Keep
  temporary space roughly the size of that county free.
- **Remove**: delete that county's data and reclaim space (shown in red, with a
  confirmation).

> An import in progress shows a **progress card** (with a progress bar); on
> failure a red banner appears where you can **choose the file again** or dismiss
> — **existing data is untouched**.

### What do the on-screen fields mean?

A row / its detail may show these fields:

| Field | Meaning |
|---|---|
| **Data date `115-01`** | the data's version date, **ROC year-month** (year 115, January). |
| **`731005 rows`** | the number of house-number records for that county. |
| **Source `tgos`** | the data source (`tgos` = MOI house numbers; `osm` = OpenStreetMap). |
| **`R*Tree built` / `SHA-256`** | technical verification fields; **ignore** as a normal user. |

---

## Performance & resources

- **All lookups run locally on the device** — fully offline, **no mobile data**.
- The readout updates live as the map moves; a typical point lookup is
  **instant**.
- The **first import of a large county** (e.g. Taichung 324 MB / ~731k rows) may
  take **a few minutes** to unzip and activate — that is normal; lookups return
  to instant afterward.
- Importing only the counties you need saves both **device space** and import
  time.

---

## FAQ

**Q: The readout shows only a coordinate, no address.**
A: ① the county for that point isn't imported yet; ② that line is turned off in
Settings. Confirm the matching county is imported first.

**Q: Does it need a network?**
A: No. Every lookup completes on the device.

**Q: How big is the data?**
A: ~100–300 MB per county. Import only the counties you need.

**Q: After import I can't find the data / it says "import first".**
A: Make sure you **did not skip** `townships.sqlite` (the county boundary file) —
it is the key to locating the county. Re-import the full ZIP.

**Q: At sea / on outlying islands / abroad, the readout is blank.**
A: Outside Taiwan's county boundaries the address can't be determined, so a blank
readout is normal. It recovers once you're back over covered mainland Taiwan.

### Import failure messages

| On-screen message | Meaning / fix |
|---|---|
| **Not enough disk space** | Out of space — remove unneeded counties or free space, then retry. |
| **Unsupported schema version** | Data version mismatch — update the plugin, or have the data re-exported in a compatible version. |
| **Database not readable** / **Places table missing** | The file is corrupt or not an address database — re-fetch / re-export the data. |
| **This is a .zip bundle. Extract… first** | You hit the old single-file path — use the normal Import and pick `tw-central-full.zip` directly (this version supports zip). |
| **skipped N / failed N** | Some items skipped (OSM/roads are normally skipped) or failed (see the failed count); failures are usually a version mismatch or a corrupt file. |

---

> Want the reverse — "type an address → jump to the map location"? See the
> **[TW Addr Search guide](tw-addr-search.md)**.
