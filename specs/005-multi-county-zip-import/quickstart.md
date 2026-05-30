# Quickstart: Multi-County + ZIP Bundle Import

**Branch**: `005-multi-county-zip-import` | **Date**: 2026-05-26

Operator-facing flow + on-device acceptance recipe + performance smoke tests. Mirrors 004's `quickstart.md` shape.

## 1. Prerequisites

- Reference device: Samsung Galaxy `R52X908JF0W` (Android 14) or equivalent ATAK-supported tablet.
- ATAK-CIV 5.7.0.3+ installed.
- Plugin APK at the merge commit of branch `005-multi-county-zip-import` installed (`adb install -r ATAK-Plugin-atak_tw_coord_plugin-1.0.6-<sha>-5.4.0-civ-debug.apk`).
- Optional: a v1.0.5-era operator running the plugin already, with one county active (e.g. 台中市). For the auto-migrate quickstart, do NOT manually clear `/sdcard/atak/tools/twcoord/offline-address/active/` before the upgrade.
- Fixtures pre-pushed to device:
  - `/sdcard/Download/places-taichung.sqlite` (599 MB)
  - `/sdcard/Download/places-changhua.sqlite` (196 MB)
  - `/sdcard/Download/tw-central-full.zip` (~165 MB compressed; contains 5 sqlite + 3 timestamp)
  - `/sdcard/Download/places-taichung.zip` (~81 MB; per-county zip)

## 2. Build & install

```powershell
# From repo root
./gradlew :app:assembleCivDebug
adb install -r app/build/outputs/apk/civ/debug/ATAK-Plugin-atak_tw_coord_plugin-1.0.6-*.apk
adb shell am force-stop com.atakmap.app.civ
adb logcat -c
```

## 3. Acceptance Flow A — US1 ZIP bundle import (MVP)

1. Open ATAK-CIV from the launcher.
2. ☰ → Tools → 離線地址 → 匯入. The plugin shows `ImportFileBrowserDialog`.
3. Pick `/sdcard/Download/tw-central-full.zip`.
4. Watch the per-entry progress on the Offline Address page:
   - `places-taichung.sqlite` → 解壓中 → 驗證中 → 活躍 (Activated)
   - `places-changhua.sqlite` → 解壓中 → 驗證中 → 活躍
   - `places-osm.sqlite` → 略過：補充檔 (Skipped: supplementary)
   - `townships.sqlite` → 略過：補充檔
   - `roads.sqlite` → 略過：補充檔
   - `timestamp.taichung` / `timestamp.changhua` / `timestamp.base` → 略過：補充檔
5. After all entries finish, the page lists two active county rows (台中市 / 彰化縣) and one summary line: "2 縣市活躍、5 個補充檔已略過".
6. Tap "完成 / Done" to close the batch session.

Expected logcat (cherry-picked):

```text
BatchImportCoordinator: starting batch sessionId=<uuid> queueDepth=1
BatchImportCoordinator: processing tw-central-full.zip
ZipExtractor: classify places-taichung.sqlite → PLACES_COUNTY (taichung)
ZipExtractor: extracted places-taichung.sqlite (572 MiB, sha=bc6a…)
BatchImportCoordinator: places-taichung.sqlite → ACTIVATED county=台中市 sha=bc6a8f86 durationMs=…
ZipExtractor: classify places-changhua.sqlite → PLACES_COUNTY (changhua)
ZipExtractor: extracted places-changhua.sqlite (196 MiB, sha=f6a9…)
BatchImportCoordinator: places-changhua.sqlite → ACTIVATED county=彰化縣 sha=f6a99d41 durationMs=…
ZipExtractor: classify places-osm.sqlite → SKIPPED_SUPPLEMENTARY
ZipExtractor: classify townships.sqlite → SKIPPED_SUPPLEMENTARY
ZipExtractor: classify roads.sqlite → SKIPPED_SUPPLEMENTARY
ZipExtractor: classify timestamp.taichung → SKIPPED_SUPPLEMENTARY
ZipExtractor: classify timestamp.changhua → SKIPPED_SUPPLEMENTARY
ZipExtractor: classify timestamp.base → SKIPPED_SUPPLEMENTARY
BatchImportCoordinator: batch sessionId=<uuid> done: activated=2 replaced=0 skipped=5 failed=0
```

## 4. Acceptance Flow B — US3 multi-county reverse-lookup

1. With {台中市, 彰化縣} active from Flow A:
2. Settings → 離線地址 → 開啟 **地圖中心 (MAP)** toggle.
3. Back to the map. Pan the centre to 24.137°N / 120.685°E (台中車站) → MAP row shows a Taichung 北區 / 東區 address.
4. Pan to 24.08°N / 120.54°E (彰化市) → MAP row shows a Changhua 彰化市 address within ~250 ms.
5. Pan to 25.0°N / 121.5°E (台北市) → MAP row shows the empty-state ("查無資料").

## 5. Acceptance Flow C — US2 per-county lifecycle

### C1 — Replace

1. With {台中市, 彰化縣} active:
2. Offline Address page → tap **替換** on the 彰化縣 row → SAF picker → choose `/sdcard/Download/places-changhua.sqlite` (same county, simulated newer data_date by manipulating `metadata.data_date` if needed).
3. Confirm. Watch the 彰化縣 row update; Taichung row is unchanged.
4. Reverse-lookup at 24.137°N / 120.685°E still returns Taichung text DURING the replace (FR-006 + SC-003).

### C2 — Remove

1. Offline Address page → tap **移除** on the 彰化縣 row → confirm.
2. 彰化縣 disappears from the list; only 台中市 remains.
3. Reverse-lookup at 24.08°N / 120.54°E (Changhua coord) now returns empty-state; at 24.137°N / 120.685°E still returns Taichung text.

### C3 — Cross-county Replace (negative)

1. With 台中市 active:
2. Tap **替換** on the 台中市 row → pick `places-changhua.sqlite`.
3. Inline error: "選擇的檔案是彰化縣，無法替換台中市". 台中市 row unchanged.

### C4 — Chained-picker (Q1) + queue (Q3)

1. Empty State. Tap 匯入 → pick `places-taichung.sqlite`. Watch import start.
2. **Immediately tap 繼續加入** (while taichung import is mid-extract). Pick `places-changhua.sqlite`.
3. The page shows "待處理 1 個" badge while taichung finishes; then 彰化縣 starts.
4. Tap **完成 / Done** any time after queue is empty; session closes.

## 6. Acceptance Flow D — US4 v1.0.5 auto-migrate

Setup: prepare a v1.0.5 environment manually:

```powershell
# Ensure no active dir exists in v1.0.6 layout
adb shell rm -rf /sdcard/atak/tools/twcoord/offline-address/active/*
# Place a v1.0.5-style single active dataset
adb shell mkdir -p /sdcard/atak/tools/twcoord/offline-address/active
adb push places-taichung.sqlite /sdcard/atak/tools/twcoord/offline-address/active/places.sqlite
# (manually craft a matching imported.manifest.txt with the file's SHA)
```

Then:

1. Install the v1.0.6 plugin (`adb install -r`).
2. Force-stop ATAK + relaunch.
3. Open ☰ → Tools → 離線地址 within ~10 s of ATAK reaching the map view.

Expected: the page shows 台中市 as active with the same SHA + same data_date as the pre-upgrade v1.0.5 state.

Logcat:

```text
AutoMigrator: legacy active/places.sqlite detected, validating county
AutoMigrator: county=台中市 ok, atomic-move to active/台中市/
AutoMigrator: migrate county=台中市 ok
```

If the legacy file is missing or `metadata.county` invalid:

```text
AutoMigrator: legacy not detected (or county failed validation)
```

## 7. Performance smoke tests

### 7.1 SC-001 — `tw-central-full.zip` import duration

```powershell
# Pre-condition: empty active dir
adb shell rm -rf /sdcard/atak/tools/twcoord/offline-address/active/*
adb shell am force-stop com.atakmap.app.civ
```

1. Open ATAK → Tools → 離線地址 → 匯入 → pick `tw-central-full.zip`.
2. Start a stopwatch when the file is picked.
3. Stop when "活躍" appears for both 台中市 and 彰化縣.
4. **Target: ≤ 90 s.**

### 7.2 SC-002 — multi-county lookup latency × 100 pans

Use the `AddressLookupPerformanceTest` Espresso harness (research R9):

```powershell
./gradlew :app:connectedCivDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.atakmap.android.twcoord.address.AddressLookupPerformanceTest
```

The test pans the map over 100 random points in the union of {台中市, 彰化縣} bboxes and asserts median ≤ 1000 ms, p95 ≤ 2000 ms.

### 7.3 SC-005 — `tw-central-full.zip` import RSS

```powershell
./gradlew :app:connectedCivDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.atakmap.android.twcoord.address.BatchImportRssTest
```

The test imports `tw-central-full.zip`, samples `Debug.MemoryInfo.getTotalPss()` every 250 ms for the duration of the import, and asserts max sample ≤ 200 MiB.

### 7.4 SC-007 — per-county Remove latency

1. With at least 2 counties active, prepare a stopwatch.
2. Offline Address page → tap **移除** on any county row → confirm.
3. Stop when the row disappears AND a subsequent reverse-lookup in that county's bbox returns empty-state.
4. **Target: ≤ 2000 ms.**

## 8. Crash isolation drill (Constitution VI)

For each new entry point listed in research R10:

1. Manually inject a Throwable into the entry point's logic (a test build can be made with a `if (System.getenv("CHAOS") != null) throw new RuntimeException();` chaos guard).
2. Exercise the entry point.
3. Verify: ATAK process stays alive; logcat has `Log.w` from the entry point; the plugin downgrades gracefully (e.g. county not added rather than ATAK crash).

Acceptance: 13 entry points × 1 chaos run each = 13 successful "ATAK still alive" verifications.

## 9. Pre-PR checklist

- [ ] `./gradlew :app:spotlessApply` clean (Constitution I).
- [ ] `./gradlew :app:lintCivDebug` no new warnings (Constitution I).
- [ ] All JVM unit tests pass (`./gradlew :app:testCivDebugUnitTest`); new tests from research R9 + contracts pass alongside the 004 tests.
- [ ] All Espresso end-to-end tests pass (`./gradlew :app:connectedCivDebugAndroidTest`).
- [ ] Acceptance Flows A–D above hand-verified on the reference device.
- [ ] Performance smokes §7.1–§7.4 measured and within budget.
- [ ] Crash isolation drill §8 verified for all 13 new entry points (research R10 list).
- [ ] `docs/ui/offline-address-page.md` (modified — chained picker, queue badge, per-county list) updated with screenshots.
- [ ] `docs/ui/settings-fragment.md` (modified — scrollable per-county rows) updated.
- [ ] `docs/adr/0017-multi-county-zip-import.md` (after /speckit-implement) committed capturing implementation pivots and the chosen fallback SQLite library (R5).
