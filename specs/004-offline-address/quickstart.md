# Quickstart: Offline Address Lookup

**Feature**: `004-offline-address` | **Date**: 2026-05-24

Local validation steps for the feature. Run after `/speckit-implement` to confirm the
acceptance flows from [spec.md](./spec.md) pass on the reference device.

## 1. Prerequisites

- Reference device: Samsung Galaxy Tab S10+ (consistent with features 001–003).
- ATAK-CIV 5.7.0.3 installed.
- Plugin APK from `app/build/outputs/apk/civ/debug/app-civ-debug.apk` (built via
  `./gradlew :app:assembleCivDebug`).
- Sample dataset: `places-taichung.sqlite` from the local generator project
  (`C:\Users\hhhnr\source\tak\atak_vns_offline_routing\atak-tw-address-generator\output\places-taichung.sqlite`).
  Alternatively, build a smaller fixture using the generator's Changhua subcommand
  (`./run.sh county changhua`) — ~80 MB, faster smoke.

## 2. Build & install

```powershell
# From repo root
./gradlew :app:assembleCivDebug
adb install -r app/build/outputs/apk/civ/debug/app-civ-debug.apk
adb shell am force-stop com.atakmap.app.civ
```

Launch ATAK and confirm:

- Tools menu has three plugin entries: **TW Coordinates**, **TW Coord GoTo**, **Offline
  Address** (this is FR-001).
- Settings → Tool Preferences → TW Coordinates → settings page has a new "Offline Address"
  category with three SwitchPreferences (all off by default) and a hidden status row.

## 3. Acceptance Flow A — US1 import a bundle

1. ADB-push the sample dataset to a directory the system file picker can see, e.g.
   `/sdcard/Download/places-taichung.sqlite`:

   ```powershell
   adb push '<path>\places-taichung.sqlite' /sdcard/Download/
   ```

2. In ATAK: Tools → **Offline Address**.
3. Expect: State A page ("No address dataset installed", **Import…** button visible).
4. Tap **Import…**. System file picker opens.
5. Navigate to Downloads, pick `places-taichung.sqlite`.
6. Watch the progress chip:
   - "Copying… N%" climbs to 100% (~5–15 s depending on storage speed).
   - "Verifying metadata…" (instant).
   - "Building index… N%" climbs to 100% (~30–45 s on the reference device for a Taichung
     1.3 M-row file).
   - "Activating…" (instant).
7. Page refreshes to State B showing:
   - County: 台中市
   - Data date: 115-01 (or whatever the imported file's `metadata.data_date` is)
   - Source: tgos
   - Rows: 1,316,674
   - CSV SHA-256: <hex>
   - Imported: <UTC timestamp now>
   - File SHA-256: <hex>
   - R*Tree built: yes
   - Buttons: **Replace…** **Remove**

Acceptance: this flow MUST complete in ≤ 60 s end-to-end (SC-003).

## 4. Acceptance Flow B — US2 + US3 address row appears

1. Settings → Tool Preferences → TW Coordinates → Offline Address → toggle **Show address for
   self-location (ME)** to on.
2. Return to the map.
3. Confirm:
   - The ME coordinate row at BOTTOM-RIGHT now has a second line beneath it carrying the
     address text (e.g. "台中市西區美村路一段 600 號"), within 1 s of stepping outside.
   - The MAP row at BOTTOM-LEFT and the TGT row at TOP-RIGHT do NOT show an address row
     (their toggles are still off).
4. Toggle **Show address for map-centre (MAP)** to on; pan the map; confirm an address row
   appears under the MAP row and updates as you pan.
5. Toggle ME off; confirm only the MAP address row remains.

Acceptance: each row's address line MUST update within 1 s median of the underlying
coordinate stabilising (SC-002).

## 5. Acceptance Flow C — Edge cases

### C1 — out-of-region coord

Move the cursor (or pan map) outside the imported county (e.g. to Taipei when only Taichung
is imported). The address row MUST switch to "No address nearby" within 1 s, with no stale
value.

### C2 — Replace dataset

In the Offline Address page, tap **Replace…**. Confirm dialog → confirm → pick a different
`.sqlite` (e.g. `places-changhua.sqlite`). The active dataset transitions; on next coord
refresh, the address row in Taichung area shows "No address nearby" (Changhua data doesn't
cover Taichung), and in Changhua area shows an address.

### C3 — Remove dataset

Tap **Remove**. Confirm. The page returns to State A; the address row disappears within one
refresh cycle (≤ 1 s). All three toggles remain on; the Settings status row now reads "No
dataset installed — tap to open Offline Address".

### C4 — Wrong schema (negative case)

Push a fake `.sqlite` (e.g. an empty file or a totally unrelated one):

```powershell
adb shell 'sqlite3 /sdcard/Download/wrong.sqlite "CREATE TABLE foo(x);"'
```

Re-open Offline Address → Import → pick `wrong.sqlite`. The page MUST show an inline error
("Required metadata.schema_version not found" or similar). The previously-active dataset
(if any) remains.

## 6. Performance smoke tests

### 6.1 SC-002 — reverse-lookup latency

With Taichung dataset active and MAP toggle on:

1. Pan the map to a Taichung urban area (e.g. 25.04°N 121.56°E equivalent for Taichung).
2. Watch the address row update on each MAP_SETTLED event.
3. Count: should feel sub-second; spot-check with a stopwatch on 5 consecutive pans.

For a more rigorous run, the plugin's `Log.d(TAG, "address resolve took Nms")` instrumentation
(added under flag) can be parsed from `adb logcat`:

```powershell
adb logcat -s TwCoordAddress | findstr 'address resolve took'
```

Median across 100 pans must be ≤ 1000 ms; p95 ≤ 2000 ms (SC-002).

### 6.2 SC-003 — import duration

`adb logcat -s TwCoordAddress | findstr 'import phase'` produces phase-by-phase timings:

```
import phase=COPYING ms=8420
import phase=VERIFYING_METADATA ms=37
import phase=BUILDING_RTREE ms=33840
import phase=ACTIVATING ms=42
import total ms=42339
```

Total must be ≤ 60 000 ms for Taichung-scale (SC-003).

### 6.3 SC-004 — zero footprint when off

1. Turn all three toggles off (and Remove the dataset, to be extra strict).
2. Use Android Profiler (or `adb shell dumpsys meminfo com.atakmap.app.civ`) to capture
   baseline plugin memory and CPU over a 60 s map-panning session.
3. Compare against a build of the previous tag (`v1.0.4` — no address feature). Footprint
   delta should be within noise (low single-MB at most).

### 6.4 SC-005 — recovery from missing files

1. With a dataset active and at least one toggle on, kill the process or close the app.
2. ADB-rm the active directory:

   ```powershell
   adb shell 'rm -rf /sdcard/atak/tools/twcoord/offline-address/active'
   ```

3. Re-open ATAK. Open the map; pan.
4. The coordinate readout MUST behave normally; the address row stays hidden (the row gates
   on dataset presence). Open Offline Address — State A.
5. Re-import the same file; confirm the address row populates again on next pan.

Recovery on (4) MUST happen within 2 s of opening the map (SC-005).

### 6.5 SC-006 — non-empty resolve rate across 1000 scripted lookups

With Taichung dataset active and at least the MAP toggle on:

1. Generate 1000 random `(lat, lon)` points inside the Taichung bounding box
   (south=24.0°N, north=24.4°N, west=120.5°E, east=121.1°E) and save to
   `test-data/sc006-points.csv` as a header-less `lat,lon` file. A pseudo-random seed
   pinned in the file's commit message keeps runs comparable across rebuilds.
2. Push the file to the device:

   ```powershell
   adb push test-data\sc006-points.csv /sdcard/Download/
   ```

3. Trigger the debug-only feeder (gated behind the same `debug.twcoord.crash_test`-style
   property used by §7) that reads the CSV line-by-line and calls
   `AddressSubsystem.onCoord(Row.MAP, lat, lon)` directly, one fire per second:

   ```powershell
   adb shell setprop debug.twcoord.sc006_feed /sdcard/Download/sc006-points.csv
   ```

   The subsystem logs each outcome at `Log.d(TAG, "sc006 outcome=found")` or
   `outcome=empty`.
4. After all 1000 firings complete (~17 minutes), parse logcat:

   ```powershell
   adb logcat -d -s TwCoordAddress | findstr 'sc006 outcome' |
     Group-Object { ($_ -split 'outcome=')[1] } |
     ForEach-Object { '{0,-10} {1}' -f $_.Name, $_.Count }
   ```

5. Compute the ratio `found / (found + empty)`. MUST be ≥ 0.95 per SC-006. Zero crashes,
   zero "stale value" defects across the run (also visible in logcat via the absence of
   stack traces under the `TwCoordAddress` tag).
6. Unset the property when done:

   ```powershell
   adb shell setprop debug.twcoord.sc006_feed ''
   ```

Note: SC-006 is necessarily a Taichung-only check until additional counties or a
consolidated multi-county bundle exist. Document the actual measured ratio in T050
(ADR-0015) regardless of pass / fail.

## 7. Crash isolation drill (Constitution VI)

For every new entry point listed in [research.md R10](./research.md#r10--constitution-vi-compliance-audit),
inject a synthetic crash (under a debug flag) and confirm ATAK survives:

```powershell
adb shell setprop debug.twcoord.crash_test on
```

The corresponding entry point throws `RuntimeException("synthetic")` on next invocation; the
plugin MUST catch + log without crashing ATAK. After verification, unset:

```powershell
adb shell setprop debug.twcoord.crash_test ''
```

## 8. Pre-PR checklist

- [ ] `./gradlew :app:spotlessApply` clean (Constitution I).
- [ ] `./gradlew :app:lintCivDebug` no new warnings (Constitution I).
- [ ] All JVM unit tests pass (`./gradlew :app:testCivDebugUnitTest`).
- [ ] All 2–3 Espresso end-to-end tests pass (`./gradlew :app:connectedCivDebugAndroidTest`).
- [ ] Acceptance Flows A–C above hand-verified on the reference device.
- [ ] Performance smokes §6.1–§6.5 measured and within budget (SC-002, SC-003, SC-004, SC-005, SC-006).
- [ ] Crash isolation drill §7 verified for every new entry point.
- [ ] `docs/ui/offline-address-page.md` (new), `docs/ui/readout-widget.md` (modified),
      `docs/ui/settings-fragment.md` (modified) updated with screenshots.
- [ ] `docs/adr/0014-offline-address-reconnaissance.md` and (after /speckit-implement)
      `docs/adr/0015-offline-address-implementation.md` committed.
