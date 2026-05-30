# Code Review — `master...HEAD`

**Date**: 2026-05-28  
**Branch**: `005-multi-county-zip-import`  
**HEAD**: `0499b8d`  
**Scope**: `master...HEAD`, 100 files, 16,613 insertions / 69 deletions  
**Focus**: memory leak、避免 plugin 當機、效能、離線地址 ZIP / multi-county import runtime risk  
**Method**: static review of changed Java/Android implementation paths. Tests were not run for this review.

## Executive Summary

整體架構有明確 lifecycle owner，主要 callback 都有 `try/catch (Throwable)` 保護，import / DB cursor / stream 多數也正確使用 close path。沒有看到會立刻造成 ATAK host 必然 crash 的 critical blocker。

需要優先處理的風險有三個：

1. ZIP entry CRC failure can still be activated as a county dataset.
2. Multi-county lookup fan-out is unbounded and can miss the stated p95 lookup budget at 22 counties.
3. Per-county Replace UI does not enforce the selected county even though comments/report enum say it should.

Memory leak 方面沒有看到明顯永久 leak，但 `OfflineAddressReceiver` 和 `BatchImportCoordinator` listener lifecycle 需要補 idempotent detach，否則 plugin teardown / future rewiring 時容易殘留 receiver/view/context reference until worker completion。

## Findings

### High — ZIP CRC failure is recorded but the extracted county can still be activated

**Files**:

- `app/src/main/java/com/atakmap/android/twcoord/address/ZipExtractor.java:219`
- `app/src/main/java/com/atakmap/android/twcoord/address/ZipExtractor.java:237`
- `app/src/main/java/com/atakmap/android/twcoord/address/ZipExtractor.java:240`
- `app/src/main/java/com/atakmap/android/twcoord/address/BatchImportCoordinator.java:190`

`ZipExtractor.extract()` adds an `ExtractedCounty` to `counties` immediately after `extractCountyEntry()` returns. If `ZipInputStream.closeEntry()` later throws, the code logs/adds a `CRC_MISMATCH` failure, but it does not remove the county from `counties` and does not delete that staging dir. `BatchImportCoordinator.processZip()` then iterates `result.counties()` and calls `activateExtractedCounty()`.

Impact: a corrupted ZIP entry can produce both a failed report row and an activation attempt for the same entry. If SQLite validation rejects it, this degrades to confusing UX and leftover staging; if the file remains SQLite-openable despite CRC mismatch, the plugin can activate data that the ZIP container says is corrupt.

Recommended fix: only append an extracted county after the entry has passed `closeEntry()`, or keep a pending extracted county and remove/delete it on `closeEntry()` failure. Add a unit test with a ZIP whose `places-*.sqlite` entry fails CRC on close.

### Medium — Multi-county lookup does one SQL query per active county

**Files**:

- `app/src/main/java/com/atakmap/android/twcoord/address/AddressSubsystem.java:266`
- `app/src/main/java/com/atakmap/android/twcoord/address/AddressSubsystem.java:271`
- `app/src/main/java/com/atakmap/android/twcoord/address/AddressSubsystem.java:278`

`lookupAcrossAllCounties()` iterates every active county and calls `facade.nearestWithin(...)` on each open database. The running radius shrinks after a hit, but the worst case still queries all active counties per debounce tick.

Impact: full Taiwan active set means up to 22 SQLite nearest-neighbour queries every 250 ms debounce cycle, across MAP / ME / TGT rows. This is the highest performance risk in the branch and can make the 15 ms p95 lookup goal unrealistic on-device.

Recommended fix: cache per-county geographic bounds at registry init/import time, then skip counties whose bbox cannot overlap the lookup radius. This should reduce normal lookups from 22 DB queries to 1-2.

### Medium — Per-county Replace does not enforce the selected county

**Files**:

- `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java:263`
- `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java:605`
- `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java:613`
- `app/src/main/java/com/atakmap/android/twcoord/address/BatchImportReport.java:24`
- `app/src/main/java/com/atakmap/android/twcoord/address/BatchImportCoordinator.java:325`

The per-county Replace row passes `county` into `confirmReplaceCounty()`, but the positive button just calls `launchPicker()`. The picked file then goes through the generic batch path, where `processBareSqlite()` peeks `metadata.county` and imports that county. There is no path carrying `countyExpected`, and `SKIPPED_COUNTY_MISMATCH` appears unused.

Impact: tapping Replace on 台中市 and picking a 高雄市 DB will add/replace 高雄市 instead of rejecting the mismatch. This is not a crash risk, but it is an operator-safety/data-correctness issue.

Recommended fix: add a coordinator entry point that accepts `expectedCounty`, reject mismatches with `SKIPPED_COUNTY_MISMATCH`, and wire `confirmReplaceCounty(countyExpected)` through it.

### Medium — Batch listener is attached but never detached

**Files**:

- `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java:305`
- `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java:308`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java:606`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java:613`

`OfflineAddressReceiver.setBatchCoordinator()` always calls `coordinator.addListener(batchListener)` and has no matching remove path. Today it is normally called once, but the method is not idempotent and `disposeImpl()` is a no-op.

Impact: if this receiver is rewired in tests, hot-reload-like plugin lifecycle, or future code calls `setBatchCoordinator()` twice, the old coordinator holds a listener that captures the receiver/view/plugin context. During teardown, an in-flight import can also continue posting UI work through that listener after `addressReceiver.dispose()`.

Recommended fix: make `setBatchCoordinator()` detach from the previous coordinator before attaching to the next one, and add an explicit receiver cleanup path called from `disposeImpl()` or `TwCoordMapComponent.onDestroyImpl()`. Also remove pending delayed progress callbacks if possible.

### Medium — Open SQLite facade per active county raises steady-state RSS / FD pressure

**Files**:

- `app/src/main/java/com/atakmap/android/twcoord/address/ActiveDatasetRegistry.java:58`
- `app/src/main/java/com/atakmap/android/twcoord/address/CountyActiveDataset.java:10`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java:650`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java:657`

The registry intentionally keeps one `AddressDatabaseFacade` open per active county. Shutdown closes survivors, and add/replace/remove close old facades, so this is not a classic leak. The risk is steady-state native memory and file descriptors when all counties are active.

Impact: 22 active counties can mean 22 SQLite handles plus WAL/SHM descriptors and native page cache. This is likely acceptable on modern tablets, but it needs device measurement before calling the memory budget proven.

Recommended fix: measure full-country RSS and FD count after init, after repeated replace/remove cycles, and after plugin unload/reload. If too high, consider lazy opening with an LRU facade cache after bbox pre-filtering.

### Low — Settings refresh rebuilds all active dataset rows on every preference change

**Files**:

- `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java:123`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java:188`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java:324`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java:329`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java:333`

Every `SharedPreferences` change calls `refreshAllSummaries()`, which reaches `refreshActiveDatasetsCategory()`, calls `removeAll()`, and recreates one `Preference` per county.

Impact: with 22 active counties, toggling a row switch or changing confidence preset rebuilds unrelated dataset rows. This should not crash the plugin, but it can make Settings feel sluggish.

Recommended fix: track a simple snapshot signature, such as `(county, dataDate, insertedRows)` joined/hash, and skip `removeAll()` when unchanged.

## Positive Notes

- Callback crash isolation is broadly present: map callbacks, preference refresh, receiver handlers, listener fan-out, lookup worker, and import worker paths catch `Throwable`.
- Stream handling is mostly bounded: ZIP extraction uses an 8 KiB buffer and importer copy uses a 64 KiB buffer; no whole-ZIP or whole-DB load was found.
- SQLite cursors in production facades use try-with-resources.
- `AddressSubsystem.close()` shuts down the lookup executor and clears listeners.
- `TwCoordMapComponent.onDestroyImpl()` unregisters ATAK receivers, removes map listeners, removes self-marker listener, disposes prefs, detaches widget, and clears static holders.

## Recommended Priority

1. Fix ZIP CRC mismatch activation path and add a corrupt-ZIP unit test.
2. Add county bbox pre-filtering before SQL nearest lookup.
3. Wire per-county Replace through an expected-county path and emit `SKIPPED_COUNTY_MISMATCH`.
4. Make `OfflineAddressReceiver` batch-listener attachment idempotent and detachable.
5. Measure 22-county RSS / FD / lookup p95 on the reference ATAK device.
6. Optimize Settings active-datasets refresh if device UX shows visible lag.

## Review Limitations

This is a static review only. I did not run Gradle tests, instrumentation tests, memory profiling, or device lookup benchmarks. The performance and memory items should be verified on the target ATAK device because SQLite native cache and ATAK host lifecycle behavior are device/runtime dependent.
