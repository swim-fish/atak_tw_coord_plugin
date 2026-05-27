# Research: Multi-County + ZIP Bundle Import

**Branch**: `005-multi-county-zip-import` | **Date**: 2026-05-26

## Anchoring discipline

Same as feature 004: every SDK / ATAK-CIV class referenced below is cross-checked against **both** `javap -public` on `../ATAK-CIV-5.7.0.3-SDK/main.jar` (canonical local source of truth) and the upstream Java source at `github.com/TAK-Product-Center/atak-civ` (`main` branch). When the two disagree, the bundled jar wins (see feedback memory `feedback-plan-phase-code-anchoring`).

---

## R1 — Active-root path: stay at `tools/twcoord/offline-address/active/<county>/` vs move to generator's `tools/twcoord/data/`

**Decision**: Stay at `tools/twcoord/offline-address/active/<county>/places.sqlite` for v1.0.6. Schedule a second-step migration to `tools/twcoord/data/places-<county>.sqlite` (the generator data-contract §2 convention) when feature 006 lands townships / roads / OSM at the same `data/` root.

**Rationale**: Two roots in play:

- Plugin v1.0.5 ships at `tools/twcoord/offline-address/active/places.sqlite`. Backwards-compat for upgrading operators requires the v1.0.6 layout to be **detectable from** the legacy path (FR-012 auto-migrate).
- Generator data-contract v2 §2 prescribes `/sdcard/atak/tools/twcoord/data/places-<county>.sqlite` (flat, no county sub-directories; per-county discovery via `listFiles` glob). This is forward-looking for the multi-source townships / roads / OSM landscape feature 006 will tackle.

Keeping the active root at `tools/twcoord/offline-address/active/<county>/` for v1.0.6 means:

- One filesystem operation per migration (rename `active/places.sqlite` → `active/<county>/places.sqlite`), not two.
- `OfflineAddressTool` + `OfflineAddressReceiver` URLs / paths / staging dirs from 004 continue to compose under the same root.
- The eventual `data/`-root migration becomes a known feature 006 task with a clear seam (`AtakFileSystem.activeCountyDir(county)`), not an emergency follow-up.

The sub-directory layer (one dir per county) keeps `imported.manifest.txt` and the future per-county WAL/SHM files isolated and lets `.staging-<county>-<uuid>/` co-exist with active dirs without collision.

**Alternatives considered**:

- **Move to `data/places-<county>.sqlite` flat layout (matching generator)**: would force feature 005 to also re-anchor 004's `AddressBundleImporter` paths + auto-migrate twice (v1.0.5 → flat `data/`) inside a single release. Bigger blast radius; tabled to feature 006.
- **Keep `active/places.sqlite` legacy path + add a sibling `active/<county>.sqlite` per county at the same level**: avoids sub-directory creation but loses isolation for per-county WAL/SHM and `imported.manifest.txt`; merge-conflict-prone if two operators ever copy datasets manually. Rejected.

---

## R2 — ZIP streaming-extract approach

**Decision**: Use `java.util.zip.ZipInputStream` with a single-buffer read loop, processing one entry at a time. No `java.util.zip.ZipFile` (which requires a `File` and seekable backing).

**Rationale**:

- The picker hands the plugin a `File` from `ImportFileBrowserDialog.onFileSelected`, but the architectural seam should be `InputStream` so the existing `AddressBundleImporter.importFrom(InputStream, ProgressListener)` contract (commit 03f910e) extends naturally.
- `ZipInputStream` reads sequentially through the central directory in declaration order, lets us short-circuit on early failure, and never holds more than one entry's bytes in flight.
- The Android `java.util.zip` implementation supports ZIP64 (no 4 GiB cap on entry size), CRC validation, and DEFLATE — which is what the generator emits per its data-contract §6.
- Per FR-003, each entry's bytes go directly into `.staging-<county>-<uuid>/places.sqlite` via a streamed `Files.copy(zipEntryStream, stagingPath, REPLACE_EXISTING)`. No full-zip unpack, no `ByteArrayOutputStream`, no `byte[]` allocation > 64 KiB. RSS for extraction is bounded by `8 KiB read buffer + one streaming SQLite write` ≈ < 16 KiB at any instant. Together with the (large) JVM heap for the FacadeRegistry the SC-005 ≤ 200 MiB RSS budget is comfortable.

**Alternatives considered**:

- **`java.util.zip.ZipFile`**: needs a seekable `File`. Would force a temp-copy of the picker URI before opening — wasteful when the picker already gives us a `File`. Rejected for the API symmetry alone.
- **Apache Commons Compress**: ~500 KB JAR for capabilities we don't need (TAR/7Z/RAR/etc.). Rejected on APK size.
- **`ZipFile` with `Channels`**: faster random access for very large zips, but we read sequentially anyway. No measurable win over `ZipInputStream`. Rejected.

---

## R3 — Multi-file picker mechanism (resolves spec Clarifications Q1)

**Decision**: Chained-picker UX driven by `ImportFileBrowserDialog` (SDK ships single-select only — confirmed via `javap -public com.atakmap.android.gui.ImportFileBrowserDialog`: only `setOnDismissListener(DialogDismissed)` with `void onFileSelected(File)`). The Offline Address page tracks a `BatchSession` and re-opens the picker after each pick until the operator taps "完成 / Done".

**Rationale**:

- The SDK has no multi-select API. The decoded `javap`:
  ```text
  public class com.atakmap.android.gui.ImportFileBrowserDialog {
    public com.atakmap.android.gui.ImportFileBrowserDialog setTitle(java.lang.String);
    public com.atakmap.android.gui.ImportFileBrowserDialog setExtensionTypes(java.lang.String...);
    public com.atakmap.android.gui.ImportFileBrowserDialog setStartDirectory(java.io.File);
    public com.atakmap.android.gui.ImportFileBrowserDialog setUseProvider(boolean);
    public com.atakmap.android.gui.ImportFileBrowserDialog setOnDismissListener(
        com.atakmap.android.gui.ImportFileBrowserDialog$DialogDismissed);
    public void show();
  }
  public interface com.atakmap.android.gui.ImportFileBrowserDialog$DialogDismissed {
    public abstract void onFileSelected(java.io.File);
    public abstract void onDialogClosed();
  }
  ```
  - One file per dialog show, returned synchronously on the UI thread.
- Reopening SAF for multi-select (`ACTION_OPEN_DOCUMENT` + `EXTRA_ALLOW_MULTIPLE`) re-introduces the cross-UID broadcast death (ADR-0015 D1) — explicitly rejected.
- Implementation shape: on the first "Import" tap the page creates a `BatchSession` and shows the picker. `onFileSelected` enqueues the file, finishes the dialog, then (still on the UI thread) shows the picker again. Each pick adds an entry to the queue + a "queued" row to the page. A "完成 / Done" button ends the session and lets the import worker drain. A "取消本批 / Cancel batch" button is also available.
- Per FR-019 (Clarifications Q3), if the worker has already started a previous batch, new picks enqueue onto the same single-thread executor. The page shows the queue depth as a badge.

**Alternatives considered**:

- **Custom multi-select dialog (RecyclerView of files)**: would need its own file-system traversal + selection logic + accessibility wiring. Reinventing what the SDK already does well, only to add multi-select. Rejected on cost.
- **`setUseProvider(true)`**: this flag toggles whether the dialog uses Android's content provider system. Per the upstream source comment (cross-checked at `atak-civ:main/atak/ATAK/app/src/main/java/com/atakmap/android/gui/ImportFileBrowserDialog.java`), it controls a single-vs-multi mode for content-provider URIs only — not local files. Stays at default (`false`).

---

## R4 — Streaming SHA-256 inside the ZIP-extract path

**Decision**: Wrap the `ZipEntry` `InputStream` in a `Tap` from `MessageDigestShaCalculator.tap(sink)` so the SHA-256 of each `places-<county>.sqlite` is computed inline during extraction — no second-pass read.

**Rationale**:

- 004's `MessageDigestShaCalculator` already provides `Tap tap(OutputStream sink)` returning a wrapper that updates the digest on every `write()` and forwards the bytes to the underlying sink (the staging-dir file).
- ZIP entry decompression already streams; the streaming SHA piggy-backs on the same byte path.
- This matches 004's `AddressBundleImporter.importFrom` contract exactly — the file SHA is part of `imported.manifest.txt` and feature 005 must produce one per active county.

**Alternatives considered**:

- **Compute SHA after extraction by re-reading the staging file**: doubles the I/O cost. Rejected on SC-001 ≤ 90 s budget grounds.
- **Use the ZIP entry's CRC-32 instead of SHA-256**: CRC-32 is collision-prone (2^32 space) and inappropriate for the file-integrity claim in `imported.manifest.txt`. Rejected on correctness.

---

## R5 — Fallback portable SQLite library for FR-017

**Decision**: `org.requery:sqlite-android` (currently 3.45.0). Initialised lazily on first failed primary-path open; APK size impact ~1.5 MiB per ABI (arm64-v8a only on the device targets) → total APK delta ~3 MiB across {armeabi-v7a, arm64-v8a}, comfortably under the Assumption §11 budget of ≤ 2 MiB **per ABI**.

**Rationale**:

- Bundles SQLite 3.45 with `SQLITE_ENABLE_RTREE`, `SQLITE_ENABLE_FTS5`, `SQLITE_ENABLE_JSON1`, `SQLITE_ENABLE_DBSTAT_VTAB` all on; matches the generator's data-contract v2 expectations.
- API shape mirrors `android.database.sqlite.SQLiteDatabase` (drop-in `io.requery.android.database.sqlite.SQLiteDatabase` namespace), making a `FallbackSqliteFactory` that returns an `AddressDatabaseFacade` straightforward — same SQL strings, same cursor-style row consumption.
- MIT licence; no GPL concerns.
- Loaded from a single `.so` per ABI via standard Android JNI; first call is lazy so the binaries are not paged in unless needed (matches the "opt-in by the runtime initialiser" requirement from Assumption §11).
- Active upstream maintenance (last release 2025-Q4), 4 K GitHub stars, used by major projects (Square, Stripe) for SQLite portability.

**Detection algorithm** (for the runtime initialiser):

When `ActiveDatasetRegistry.openCounty(county, file)` is called:

1. Try `AtakDatabasesAddressDatabase.Factory.open(file)` (primary path).
2. If primary returns non-null, **probe with** `SELECT 1 FROM places_rtree LIMIT 0` (no rows, just compiles the SQL). If it succeeds → primary is good, return primary facade.
3. If primary returns null, or the probe throws `SQLITE_ERROR` with message containing "no such module: rtree" or any compile failure: lazily initialise the fallback factory, retry the open via fallback, re-probe with the same `SELECT 1 FROM places_rtree LIMIT 0`. If fallback works → return fallback facade. If both fail → return null (county not openable; surfaces as missing-data state, eligible for SC-005 graceful recovery).

**Alternatives considered**:

- **SQLCipher for Android**: full-featured (rtree + encryption + FTS) but ~6 MiB per ABI for the encryption we don't need. Rejected on APK budget.
- **Build SQLite from source via NDK**: ~1 MiB per ABI achievable, but requires CI uplift (NDK toolchain, per-ABI builds), licence due-diligence on the bundled `.c`, and a permanent maintenance line. Rejected on engineering cost; reconsider if Requery is unmaintained.
- **SkSQLDelight / Room**: ORMs sitting on top of the same Android SQLite — they share the missing-rtree problem. Rejected as they don't solve the underlying constraint.
- **Pure-Java SQLite implementation (e.g. SQL.js port)**: no Java port that supports R*Tree exists in the ecosystem. Rejected on availability.

---

## R6 — Auto-migrate algorithm (v1.0.5 → v1.0.6)

**Decision**: At plugin `onCreate`, before initialising `ActiveDatasetRegistry`, run `AutoMigrator.tryMigrate()`. Algorithm:

1. Check for legacy `active/places.sqlite` + `active/imported.manifest.txt`.
2. If absent → no-op, return.
3. Open the legacy `places.sqlite` (read-only) via the same SQLite primary-path used by the production resolver. Read `metadata.county`.
4. If county string fails any of {non-null, length > 0, no path separators, no embedded `..`} validators → leave legacy in place, log `Log.w`, return (preserves the legacy state for v1.0.5 downgrade or manual recovery — FR-012 mandates).
5. Create `active/<county>/` (mkdir).
6. **Atomic rename**: `Files.move(active/places.sqlite, active/<county>/places.sqlite, ATOMIC_MOVE)`. Same for `imported.manifest.txt`. Same for `places.sqlite-shm` and `places.sqlite-wal` if present.
7. If any rename fails (`AtomicMoveNotSupportedException` because the staging dir and target dir straddle a mount point) → fall back to copy-then-delete, but ONLY after verifying free space ≥ source size. If verification fails or copy partial → roll back (delete partial copies) + leave legacy unchanged.
8. On success, delete the now-empty `active/` flat layout. Log success at `Log.i`.

**Rationale**:

- `Files.move` with `ATOMIC_MOVE` works inside a single filesystem mount, which `/sdcard/atak/...` always is (Samsung Knox encrypted Android storage uses a single tmpfs-backed mount at this depth).
- The "verify free space then copy then delete" fallback covers theoretical multi-mount cases (e.g. moved-to-SD-card storage).
- Validation gates (county string format) prevent a corrupt v1.0.5 dataset from creating a path-traversal artifact (e.g. county = `../../etc/passwd`) — Constitution VI defensive-validation rule applies.

**Alternatives considered**:

- **Migrate on every plugin open**: would re-scan + re-rename on every ATAK launch even after migration was done. Wasteful. Use the "no legacy path detected" guard step.
- **Prompt the operator to confirm migration**: feature 004's UX has trained operators to expect single active dataset. Surprising them at startup with a confirmation dialog is worse UX than a silent migrate-then-show-the-page (FR-012: "without prompting the operator"). Rejected.
- **Defer migration until first multi-county Import attempt**: leaves the layout inconsistent for an unknown duration; reverse-lookup would either work via the legacy path (special-case code permanently) or fail (operator confusion). Rejected.

---

## R7 — Settings PreferenceCategory dynamic per-county rows (resolves spec Clarifications Q2 wiring)

**Decision**: Build a dynamic `PreferenceCategory("active_datasets")` whose `Preference` rows are added programmatically in `TwCoordPreferenceFragment.onCreate` (after `addPreferencesFromResource(R.xml.preferences)`). Each row is a stock `Preference` with `setTitle(county)`, `setSummary("data_date / inserted rows")`, and `setOnPreferenceClickListener(...)` opening the Offline Address page focused on that county. The existing scrollable container (`PreferenceFragment`'s built-in `RecyclerView`) handles arbitrary row count.

**Rationale**:

- `androidx.preference.PreferenceCategory` supports `addPreference(Preference)` dynamically at runtime — verified by `javap -public androidx.preference.PreferenceCategory`. No need for a custom adapter.
- `PreferenceFragment`'s root view is a `RecyclerView` (per AndroidX docs); arbitrary scrolling is free.
- Rebuilds on `ACTION_DATASET_CHANGED` (FR-015): remove all rows from the category + add fresh ones per current `ActiveDatasetRegistry` snapshot. Cheap because Settings is usually not on screen during heavy import; on screen, the rebuild happens once per county change which is rare.
- The three per-row toggles stay at the top in `preferences.xml`; the dynamic category is appended below.

**Alternatives considered**:

- **One `xml/preferences.xml` row per possible county (all 22 Taiwan administrative divisions)**: bloats the layout, requires `setVisibility(visible/gone)` toggling per active state. Rejected.
- **Single multi-line `Preference` showing all active counties as a comma-joined summary**: rejected by Clarifications Q2 (`A — every county its own row, scrollable`).
- **Custom `RecyclerView` outside the PreferenceFragment**: would need its own scroll coordination + accessibility wiring. Reinvents what `PreferenceCategory.addPreference` already does. Rejected.

---

## R8 — `ACTION_DATASET_CHANGED` per-county fan-out

**Decision**: Keep the existing `OfflineAddressIntents.ACTION_DATASET_CHANGED` broadcast unchanged in shape (no new extras, no county-specific filter), but fire one broadcast per atomic per-county change (activate / replace / remove). The receivers (`AddressSubsystem` listener + Settings fragment refresh) consume the broadcast by re-reading the entire `ActiveDatasetRegistry` snapshot — they don't try to parse "which county changed" from the intent. This keeps the broadcast shape consistent with 004 and avoids cache invalidation bugs on missed events.

**Rationale**:

- Per FR-015 the broadcast is fired per atomic county change. Consumers re-read the registry; they don't depend on parsing per-county info from the intent itself.
- Re-reading the registry is cheap (lookup over a `Map<String, CountyActiveDataset>` of < 30 entries); no need for a "diff" payload.
- This is the **idempotent receiver** pattern: the broadcast tells consumers "something changed, re-read state", not "this specific thing changed". Resilient to dropped or coalesced broadcasts.

**Alternatives considered**:

- **Add `EXTRA_CHANGED_COUNTY` string extra**: would let consumers do incremental updates instead of full re-read. But the cost of full re-read (a `HashMap` walk over ~10 active counties) is microseconds, vs the maintenance cost of "do we trust the intent's extra was preserved across the broadcast?" Rejected on simplicity.
- **One broadcast per BATCH (not per county)**: would coalesce a 5-county batch into one event. But mid-batch consumers (e.g. Settings page reopened by the operator between county 2 and county 3) would not see counties 1+2's state until the batch ends. Rejected on staleness.

---

## R9 — Espresso harness for SC-001 / SC-002 / SC-005 measurements

**Decision**: Land a minimal Espresso skeleton in feature 005's `app/src/androidTest/`, covering the three measurable SCs that 004 explicitly deferred (T031/T044/T048/T057). Espresso instrumentation runs on the reference device via `./gradlew :app:connectedCivDebugAndroidTest`.

- `OfflineAddressFlowABCTest` — end-to-end of US1+US2+US3 acceptance scenarios with the `tw-central-full.zip` fixture pre-pushed to `/sdcard/Download/`.
- `AddressLookupPerformanceTest` — programmatic pan over 100 random points in the union of {台中市, 彰化縣} bboxes, measuring median + p95 from `System.nanoTime` brackets around the resolver's `lookup(...)` call.
- `BatchImportRssTest` — uses `Debug.MemoryInfo` to assert plugin process RSS stays ≤ 200 MiB during the `tw-central-full.zip` import flow.

**Rationale**:

- Espresso is the only on-device harness that can measure SC-001 wall-clock (import duration), SC-002 latency (100 pans), and SC-005 memory (RSS sampled during extract).
- Feature 004 deferred this work explicitly (ADR-0015 Performance table SC-002 / SC-005 / SC-006 marked DEFERRED). Picking it up in 005's plan-phase consolidates the harness into one feature instead of strewn across two.
- The deferred 004 tasks (T022 Robolectric receiver tests + T044 / T048 device runs) inherit the same harness; 005's PR closes the 004 follow-up loop.
- Cost: ~1 day of Espresso skeleton work, ~1 day of fixture pinning and assertion polish, ~1 day of device run / measurement / record-keeping in ADR-0016.

**Alternatives considered**:

- **Keep deferring**: would leave SC-002 / SC-005 unmeasured indefinitely. Constitution IV demands first-class performance acceptance criteria; cannot be deferred indefinitely without ADR justification each time. Rejected.
- **Manual operator runs (like 004's T031 + T044 partial)**: works for one-off verification, doesn't give regression coverage. Better as a complement to Espresso, not a replacement.

---

## R10 — Constitution VI entry-point audit checklist (preview)

Per Principle VI mandate ("a task is incomplete if it adds a new plugin entry point without the outer Throwable guard"), the new entry points introduced by feature 005 — to be wrapped in `try / catch (Throwable)` → `Log.w(TAG, "...", t)` and verified by the polish-phase audit:

| # | Entry point | File | Notes |
|---|---|---|---|
| 1 | `ImportFileBrowserDialog.DialogDismissed.onFileSelected` (chained, multi-batch) | `OfflineAddressReceiver.launchPicker` | Already wrapped in 004; reuse pattern |
| 2 | `ImportFileBrowserDialog.DialogDismissed.onDialogClosed` | same | Already wrapped in 004 |
| 3 | `OnClickListener` for "繼續加入 / Add more" button | `OfflineAddressReceiver` | NEW — wrap with `safeRun` |
| 4 | `OnClickListener` for "完成 / Done" button | same | NEW |
| 5 | Per-county Replace button click handler (one per county row in dataset list) | same | NEW — wrap with `safeRun` |
| 6 | Per-county Remove button click handler (one per county row) | same | NEW — wrap with `safeRun` |
| 7 | `AutoMigrator.tryMigrate` callable from `TwCoordMapComponent.onCreate` | `AutoMigrator` | NEW — outer `try/catch(Throwable)` at call site |
| 8 | `Preference.OnPreferenceClickListener` per Settings county row | `TwCoordPreferenceFragment` | NEW — wrap each click body |
| 9 | `ACTION_DATASET_CHANGED` receiver (re-read registry path in `AddressSubsystem`) | `AddressSubsystem` | Carried forward from 004; re-audit because the read path now iterates over N counties |
| 10 | Queue-drain worker on the single-thread executor (drains chained picker queue) | `BatchImportCoordinator` | NEW — `try/catch(Throwable)` outside the per-entry loop AND inside it |
| 11 | `BatchImportReport` listener callbacks (page binding observer) | `OfflineAddressReceiver` | NEW — wrap each observer call |
| 12 | `ActiveDatasetRegistry` observer callbacks (resolver + page binding) | `ActiveDatasetRegistry` | NEW — each observer call in its own `try/catch` (Principle VI listener short-circuit rule) |
| 13 | `FallbackSqliteFactory.open` first-time-load classloader path (JNI native lib init) | `FallbackSqliteFactory` | NEW — wrap `UnsatisfiedLinkError` separately (Error, not Exception); Constitution VI catches it via `Throwable` |

Audit closes after `/speckit-implement` by walking the list and citing file:line for each guard. Mirrors 004 T056 / ADR-0015 D5 audit.

---

## Phase 1 design gate (Constitution Check re-run)

After Phase 1 outputs (data-model.md, contracts/, quickstart.md) are generated, the gate is re-run. The four design surfaces — `BatchImportCoordinator`, `ActiveDatasetRegistry`, `ZipExtractor`, `AutoMigrator` — are intentionally pure-JVM with seam-injected dependencies (FileSystem / ShaCalculator / SqliteFactory) per the 004 D3 + seam-test discipline, so the design itself is testable on JVM without device. No new Constitution VI violations surface in design; the Polish-phase audit (R10) covers ongoing entry-point wrapping.

**Post-design Constitution Check: ✅ Pass (no Complexity Tracking entries needed).**
