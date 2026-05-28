# Code Review — `master..HEAD` (features 004 + 005)

**Date**: 2026-05-27
**Scope**: 16 commits, 100 files, ~16 600 insertions (since `master`)
**Reviewer**: focused audit on memory leaks, crash risks, and plugin runtime performance
**Branch tip at review**: `0499b8d` (`docs(005): ADR-0016 + ADR-0017 + UI docs + README v1.0.6 polish`)

This document is the standalone version of the review carried out on the evening of 2026-05-27. It is meant to be readable cold — no conversation context required. Apply / triage tomorrow.

---

## Executive summary

Architecture is sound. **No critical pre-merge blocker**. Constitution VI guards are in place for all 13 entry points (ADR-0017 D8). The two real concerns are:

1. **N-county SQL fan-out latency (§3.1)** — at 22 active counties (full Taiwan) the per-debounce-tick worker cost is estimated ~50–100 ms, which makes SC-002 (lookup p95 ≤ 15 ms) tight. **Highest-ROI fix in this review.**
2. **Settings fragment over-refresh (§3.2)** — any `SharedPreferences` change rebuilds the entire per-county `PreferenceCategory`, including N `Preference` constructions + layout passes. Wasteful at 22 counties.

Everything else is observation / defensive polish. Recommended action: ship the PR after the §3.1 mitigation; everything else is fine to defer or land in a follow-up.

---

## 1. Memory and lifecycle

### 1.1 ⚠️ Multiple `addListener` without matching `removeListener`

| Site                                                                              | Risk                                                                                          |
| --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `OfflineAddressReceiver.setBatchCoordinator` (line 305-310)                       | `coordinator.addListener(batchListener)` — if called twice with different coordinators, the old `batchListener` remains subscribed to the old coordinator |
| `TwCoordMapComponent.onCreate` line 493 `addressRegistry.addListener(...)`        | Pure lambda, cannot dedupe; second `onCreate` invocation would double-subscribe              |
| `TwCoordMapComponent.onCreate` line 520 `addressSubsystem.addListener(...)`       | Same as above                                                                                |

**Production reality**: ATAK plugin `MapComponent.onCreate` runs once per plugin load, so this never fires in normal use. But `setBatchCoordinator` lacks an idempotency guard — easy to add:

```java
public void setBatchCoordinator(BatchImportCoordinator coordinator) {
  if (this.batchCoordinator == coordinator) return;
  if (this.batchCoordinator != null) this.batchCoordinator.removeListener(batchListener);
  this.batchCoordinator = coordinator;
  if (coordinator != null) coordinator.addListener(batchListener);
}
```

**Severity**: Low. Defensive polish.

### 1.2 ⚠️ N native SQLite handles open simultaneously

`ActiveDatasetRegistry.data` is `ConcurrentHashMap<county, CountyActiveDataset>`. Each entry holds an open `AddressDatabaseFacade` = one SQLite connection + page cache + prepared-statement cache.

Full Taiwan = 22 counties = **22 open native SQLite connections**, plus their `-wal` / `-shm` companion files = ~50+ open file descriptors.

`TwCoordMapComponent.onDestroyImpl` (lines 650-664) correctly walks the registry and closes every facade — no leak. The concern is **runtime steady-state memory**, not a leak. Each connection allocates page cache (default ~2 MB per ATAK native SQLite open), so 22 × ~2 MB ≈ 44 MB just for page caches.

**Severity**: Low → Medium. Worth measuring on-device as part of ADR-0017 D9 (deferred perf numbers).

### 1.3 ✅ Static fields are properly nulled in destroy

`pluginContext` / `staticAddressImporter` / `staticAddressRegistry` / `staticAddressCoordinator` are all explicitly null'd in `onDestroyImpl` (lines 647 / 663 / 667 / 668). `@SuppressLint("StaticFieldLeak")` annotations are appropriate for the ATAK plugin context idiom.

---

## 2. Crash risks

### 2.1 ✅ Constitution VI coverage is complete

All 13 entry points from research R10 have `try/catch (Throwable)` outer guards. Listener short-circuit rule (per-listener try/catch) is correctly applied in:

- `ActiveDatasetRegistry.fireChange` (lines 199-204)
- `AddressSubsystem.emit` (lines 322-325)
- `BatchImportCoordinator` (lines 144, 201, 228, 303, 333, 396, 418, 432, 442, 452)
- `OfflineAddressReceiver.safeRun` (line 751)

ADR-0017 D8 already cites `file:line` for each guard.

### 2.2 ⚠️ Disk I/O on UI thread in Settings refresh

`TwCoordPreferenceFragment.refreshAddressDatasetStatus` (lines 215-220) calls `importer.activeOrNull()`, which reads `imported.manifest.txt` and probes file existence under `active/`. Wrapped in `try / catch (Throwable)` so won't crash, but it does block UI thread briefly.

**Mitigated** by the conditional: only fires when `activeCount == 0` (line 215). In the multi-county production state, this branch is dead. It only matters during the v1.0.5 → v1.0.6 auto-migrate window before `Registry.initFromDisk()` runs.

**Severity**: Very low. Acceptable as-is.

### 2.3 ✅ Cross-thread state is safe

`AddressSubsystem` holds three `EnumMap` fields (`enabled` / `inflight` / `lastState`) that are **not** thread-safe in isolation, but the code path is:

- Writes: all on UI thread (`setRowEnabled` / `onCoord` / `cancelInflight` / `emit` via `uiPoster`)
- Worker thread (`runLookup`) does **not** touch any `EnumMap`; results are funneled back to UI thread via `uiPoster.accept(() -> emit(...))`

`confidenceThresholds` is correctly declared `volatile` (worker reads, UI writes). No race in the production path.

---

## 3. Performance

### 3.1 🚨 N-county SQL fan-out is unbounded — highest-impact concern

`AddressSubsystem.lookupAcrossAllCounties` (lines 254-281) iterates `registry.snapshot()` and calls `f.nearestWithin(...)` for **every** county. With 22 active counties:

| Active counties | SQL queries per debounce tick | Estimated worker time |
| --------------- | ----------------------------- | --------------------- |
| 1               | 1                             | ~2-5 ms               |
| 2               | 2                             | ~5-10 ms              |
| 22              | 22                            | **~50-100 ms**        |

At 250 ms debounce, the worker thread spends up to **40% of every cycle** doing SQL. SC-002's p95 ≤ 15 ms is tight to nearly unmet at 22 counties.

The monotonically-shrinking radius optimization (line 263 onwards) helps when one county is geographically close, but the worst-case fan-out (operator near a county boundary) still touches multiple databases.

**Recommended fix** (~30 LoC):

Pre-compute a `Map<county, BBox>` from each dataset's `places_rtree` MIN/MAX rows at registry-init time. In `lookupAcrossAllCounties`, filter snapshot to only counties whose BBox overlaps `(lat, lon) ± LOOKUP_RADIUS_M`. Typical case drops from 22 → 1-2 queries; an order-of-magnitude latency improvement.

```java
// At registry-init time, populate per CountyActiveDataset:
//   bbox = SELECT MIN(min_lat), MIN(min_lon), MAX(max_lat), MAX(max_lon) FROM places_rtree
// In lookupAcrossAllCounties:
for (CountyActiveDataset entry : snap.values()) {
  if (!entry.bboxOverlaps(lat, lon, bestDist)) continue;  // skip non-candidates
  ...
}
```

**Severity**: High at 22 counties; Low at 1-3 counties. **The single highest-ROI item in this review.**

### 3.2 ⚠️ Settings fragment over-refreshes on every shared-pref change

`refreshAllSummaries()` → `refreshAddressSection()` → `refreshActiveDatasetsCategory()` calls `category.removeAll()` and then constructs N new `Preference` objects + N `addPreference()` invocations on every `onSharedPreferenceChanged` callback.

The listener fires for **any** preference change — coord unit, UI language, the three address-row toggles, confidence preset, etc. A single toggle of `pref_address_row_me` rebuilds 22 county rows when only one boolean changed.

**Recommended fix** (~50 LoC):

Track the last-rendered snapshot signature (hash of `(county, dataDate, insertedRows)` tuples). Skip `refreshActiveDatasetsCategory` body when the signature is unchanged. Other refresh helpers (title / summary updates) are cheap and can stay.

**Severity**: Medium. UX feels sluggish on 22-county installs when toggling settings.

### 3.3 ⚠️ R*Tree build is O(N) for v1 files

`AddressBundleImporter.buildRtreeIfAbsent` runs `INSERT INTO places_rtree` row-by-row for files without a pre-built index. On a 1.3M-row Taichung dataset (~600 MB), this is the long pole during import.

**Mitigated** by data-contract v2 — the generator now ships `places_rtree` pre-built (ADR-0015 D-Note). Only v1-era files hit this path. Recorded in ADR-0017 D2.

**Severity**: Low. Inherent to v1 files; v2 path is fast.

### 3.4 ✅ ZipExtractor uses 8 KiB streaming buffer

`BUF_SIZE = 8192` (line 41) + `ZipInputStream` wrapping the raw stream + per-entry `try/catch` isolation. No anti-pattern of loading the whole archive into memory. SC-005 ≤ 200 MiB RSS budget should hold by design.

### 3.5 ⚠️ TwCoordWidget `paintAddressRow` stacks 5 View ops per refresh

`setVisible(false)` → `setBackground(...)` re-fire → `setVisible(true)` → `row.onSizeChanged()` → `mapView.postOnActive(...)`. Triggered on every coord update (debounced 250 ms). Fast-pan steady state ≈ 4 Hz → ~20 View ops/sec.

**Accepted trade-off** per ADR-0017 D5 (2026-05-27 UX call — speed > eliminating rare flicker). Don't touch unless a device profile shows it dominates frame time.

---

## 4. Lower-priority observations

| #   | Observation                                                                                                              | Suggestion                                                              |
| --- | ------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------- |
| 4.1 | `bindStateBMultiCounty` re-inflates `R.layout.offline_address_county_row` N times on every rebind                        | OK as-is — page opens are user-driven, not high-frequency               |
| 4.2 | `ConfidenceThresholds.decorate` allocates `"~ " + name` / `"~~ " + name` strings on every successful lookup              | Negligible — JVM escape analysis should inline                          |
| 4.3 | Brief window in `onDestroyImpl` between `addressReceiver = null` (line 617) and `addressCoordinator = null` (line 665)   | Single-threaded destroy path → no NPE in practice; OK                   |
| 4.4 | 22-county install holds ~50+ open file descriptors (each SQLite + `-wal` + `-shm`)                                       | Android per-process default is 1024; well within budget                 |
| 4.5 | `LocaleOverride.contextFor` does NOT cache wrapped contexts; called on every `refreshAllSummaries`                       | Negligible; `createConfigurationContext` is cheap                       |

---

## 5. Recommended action priority

1. **🚨 §3.1 — Add county BBox pre-filter to `lookupAcrossAllCounties`** (~30 LoC). Directly determines whether SC-002 holds at 22 counties. Highest ROI in this review.
2. **⚠️ §1.1 — `setBatchCoordinator` idempotency guard** (~5 LoC). Pure defensive; trivial.
3. **⚠️ §3.2 — Diff-then-rebuild in `refreshActiveDatasetsCategory`** (~50 LoC). Improves Settings UX on multi-county installs.
4. **⚠️ §1.2 + ADR-0017 D9 — measure 22-county RSS and lookup p95 on the reference device**. Decides whether §3.1 is actually needed or a theoretical concern.

No critical blocker — the PR can proceed under the existing 3-commit history. The four items above can be addressed in a follow-up commit / PR after device verification numbers come in.

---

## 6. Out-of-scope (already deferred per ADR-0017 open items)

- T043 Espresso RSS measurement
- T044 Constitution VI crash-isolation Espresso (cross-process verification)
- T055 connectedCivDebugAndroidTest harness
- T056 ADR-0017 D9 perf numbers (depends on T055)

These need device + Espresso harness. Track in the post-merge follow-up.
