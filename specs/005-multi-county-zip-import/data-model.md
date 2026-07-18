# Data Model: Multi-County + ZIP Bundle Import

**Branch**: `005-multi-county-zip-import` | **Date**: 2026-05-26

This document captures the on-disk layout, in-memory entities, and per-county lifecycle states that feature 005 introduces. The schema of `places-<county>.sqlite` itself is owned by the sibling generator's `docs/data-contract.md` v2 at `<ATAK_TW_ADDRESS_GENERATOR>` and not duplicated here — feature 005 consumes that schema unchanged.

---

## 1. On-disk layout

### 1.1 v1.0.5 (pre-005) — single active dataset

```text
/sdcard/atak/tools/twcoord/offline-address/
  active/
    places.sqlite
    places.sqlite-shm           (optional, WAL journal mode)
    places.sqlite-wal           (optional, WAL journal mode)
    imported.manifest.txt
  .staging-<uuid>/              (transient, swept on importer ctor)
```

### 1.2 v1.0.6 (feature 005) — per-county active datasets

```text
/sdcard/atak/tools/twcoord/offline-address/
  active/
    台中市/
      places.sqlite
      places.sqlite-shm
      places.sqlite-wal
      imported.manifest.txt
    彰化縣/
      places.sqlite
      places.sqlite-shm
      places.sqlite-wal
      imported.manifest.txt
    ...
  .staging-<county>-<uuid>/    (transient, one per in-flight county; swept on importer ctor)
```

Migration from 1.1 → 1.2 is performed once at plugin onCreate by `AutoMigrator` per research R6.

### 1.3 `imported.manifest.txt` shape

Same as 004 (one per county, no schema change):

```text
imported_at=2026-05-26T12:29:34.533534Z
file_sha256=bc6a8f868ddad8faae44ce23035fb3f6dfae79a766f14b488d456068572091af
rtree_built=false
plugin_schema_version=2
```

Key invariants:

- `file_sha256` is computed by streaming the file's bytes (after extraction from a ZIP, if applicable) at import time. It is not the ZIP's outer SHA — that's a different concern.
- `rtree_built` is `false` when the generator pre-shipped `places_rtree` (the v2 case, default for 2026-05-24 onwards). The plugin's v1 build path becomes a rare fallback.
- `plugin_schema_version` records the highest `metadata.schema_version` value the plugin accepted (currently `2`); future plugins may use it to decide if the file is too new.

---

## 2. In-memory entities

### 2.1 `CountyActiveDataset`

```text
CountyActiveDataset
  county: String                 # normalised county name, e.g. "台中市" (never "臺中市")
  rootDir: File                  # active/<county>/
  placesFile: File               # active/<county>/places.sqlite
  manifestFile: File             # active/<county>/imported.manifest.txt
  generatorMetadata: GeneratorMetadata
  importedManifest: ImportedManifest
  facade: AddressDatabaseFacade    # opened lazily by ActiveDatasetRegistry
```

- Immutable value object once handed off to the registry.
- The `facade` is the open SQLite cursor — either `AtakDatabasesAddressDatabase` (primary path, ATAK native, has R*Tree) or `FallbackSqliteFactoryWrapper` (Requery-backed, also has R*Tree). The facade-opening fallback decision is in research R5.
- Lifetime: from successful activation until the next per-county Replace or Remove.

### 2.2 `ActiveDatasetRegistry`

```text
ActiveDatasetRegistry
  data: ConcurrentMap<String, CountyActiveDataset>   # keyed by county
  observers: List<Listener>
  fallbackFactoryInitialised: AtomicBoolean
```

- Thread-safe (concurrent map + atomic boolean for the one-shot fallback init).
- Updated only via atomic `add` / `replace` / `remove` operations (each is one map mutation + one observer fan-out + one `ACTION_DATASET_CHANGED` broadcast — see research R8).
- Observers are notified on the broadcast thread (the Constitution VI listener short-circuit rule applies; each `Listener.onChange(...)` invocation is in its own `try/catch`).
- Snapshot accessor `Map<String, CountyActiveDataset> snapshot()` returns a stable view for resolver fan-out and Settings rebuild.

### 2.3 `BatchSession`

```text
BatchSession
  sessionId: UUID                  # local-only, for logcat correlation
  pending: Deque<File>             # picked files awaiting extraction + validation + activation
  inFlight: File?                  # the file the worker is currently processing, if any
  reports: List<BatchImportReport.Entry>   # one per processed file
  state: State                     # OPEN (operator may add more), DRAINING (no new adds), DONE
```

- Lifetime: starts when the operator taps "Import" on a State A page; ends when the operator taps "完成 / Done" or navigates away (Q3: import continues to drain).
- One session at a time per page. The page mutates this object only on the UI thread.

### 2.4 `BatchImportReport.Entry`

```text
BatchImportReport.Entry
  filename: String              # e.g. "places-taichung.sqlite" or "tw-central-full.zip/places-changhua.sqlite"
  county: String?               # null if the entry never reached county-extraction phase
  status: Status
  details: String?              # human-readable reason; e.g. "DISK_FULL during streaming write"
  durationMs: Long              # extract + validate + activate (or just extract + validate for skipped entries)

  enum Status:
    ACTIVATED               # new county added
    REPLACED                # existing county overwritten with new data_date
    SKIPPED_SUPPLEMENTARY   # townships/roads/osm/timestamp ignored
    SKIPPED_DUPLICATE       # same county appeared twice in same ZIP
    SKIPPED_COUNTY_MISMATCH # Replace target's metadata.county didn't match
    FAILED                  # validation failed; details contains the AddressBundleImporter Failure.reason
```

- Used to render the per-county progress + the end-of-batch summary on the Offline Address page.
- Serialised to logcat (`Log.i(TAG, report.toString())`) for forensic value.

### 2.5 `ZipEntryClassification`

```text
ZipEntryClassification
  ZIP entry name pattern → classification
  ──────────────────────────────────────────
  "places-<county>.sqlite"  → PLACES_COUNTY (extract + validate)
  "places-osm.sqlite"        → SKIPPED_SUPPLEMENTARY (OSM landmark, feature 006+)
  "townships.sqlite"         → SKIPPED_SUPPLEMENTARY
  "roads.sqlite"             → SKIPPED_SUPPLEMENTARY
  "timestamp.*"              → SKIPPED_SUPPLEMENTARY
  "*.manifest.txt"           → SKIPPED_SUPPLEMENTARY (informational sidecar, feature 005 doesn't consume)
  "*.sqlite" (other)         → UNRECOGNIZED (logged at Log.w; not counted as failure)
  any other                  → UNRECOGNIZED
```

- Pattern matching is case-sensitive. The generator emits lowercase filenames exclusively (data-contract §2); plugins MAY tolerate uppercase variants if a future generator change introduces them, but feature 005's classifier is the literal-match version.

---

## 3. State transitions

### 3.1 Per-county lifecycle

```text
                   ┌──────────────────────────┐
                   │      NOT INSTALLED       │  (county absent from registry)
                   └────────┬─────────────────┘
                            │ Import successful
                            ▼
                   ┌──────────────────────────┐
                   │         ACTIVE           │  (registry entry; facade open)
                   └────────┬─────────────────┘
                            │ Replace with new metadata for same county
                            ▼
                   ┌──────────────────────────┐
                   │   ACTIVE (refreshed)     │  (registry entry; same key, new value)
                   └────────┬─────────────────┘
                            │ Remove
                            ▼
                   ┌──────────────────────────┐
                   │      NOT INSTALLED       │
                   └──────────────────────────┘

                            ↑ External tamper (FR-014): registry de-registers silently
                            │ on next resolver attempt
                            │
                   ┌──────────────────────────┐
                   │  ACTIVE BUT FILES GONE   │  (transient; never observable to UI before de-register)
                   └──────────────────────────┘
```

### 3.2 `BatchSession.State` transitions

```text
                   ┌────────────────┐
                   │     CREATED    │  (page tap of "Import" or "繼續加入")
                   └────────┬───────┘
                            │ first pick
                            ▼
                   ┌────────────────┐
                   │     OPEN       │  (operator may keep adding files)
                   └────────┬───────┘
                            │ "完成 / Done"
                            ▼
                   ┌────────────────┐
                   │   DRAINING     │  (no new adds; worker drains the queue)
                   └────────┬───────┘
                            │ all entries processed
                            ▼
                   ┌────────────────┐
                   │      DONE      │  (BatchImportReport finalised, session GC'd)
                   └────────────────┘

                   "取消本批 / Cancel batch" transitions OPEN / DRAINING → CANCELLED;
                   in-flight entry completes naturally (atomic), pending entries are dropped.
```

### 3.3 Auto-migrate state machine (research R6)

```text
                   ┌──────────────────────────┐
                   │ legacy active/places.sqlite + manifest exist? │
                   └────────┬─────────────────┘
                       no   │  yes
                            ▼
                   ┌──────────────────────────┐
                   │ read metadata.county     │
                   └────────┬─────────────────┘
                            │ county string fails validation
                            │ → NO-OP, leave legacy intact, Log.w
                            ▼
                   ┌──────────────────────────┐
                   │ mkdir active/<county>/   │
                   │ atomic-move sqlite + shm + wal + manifest │
                   └────────┬─────────────────┘
                            │ any move fails
                            │ → rollback partial moves, leave legacy intact
                            ▼
                   ┌──────────────────────────┐
                   │ remove now-empty active/ files (if any orphans) │
                   │ Log.i "migrated <county>"  │
                   └──────────────────────────┘
```

---

## 4. Cross-county lookup algorithm

### 4.1 Resolver fan-out

For a given query `(lat, lon, radiusMeters)`:

```text
fun nearestAcrossAllCounties(lat, lon, radius):
    var best: AddressRecord? = null
    var bestDist: Double = radius
    for ((county, dataset) in registry.snapshot()):
        try:
            candidate = dataset.facade.nearestWithin(lat, lon, bestDist)
            # nearestWithin uses bestDist as the bbox radius, so each subsequent
            # county only needs to find a record closer than the current winner;
            # short-circuit by passing the running best as the radius.
        catch Throwable t:
            Log.w(TAG, "lookup in " + county + " threw", t)
            continue
        if candidate != null:
            d = haversine(lat, lon, candidate.lat, candidate.lon)
            if d < bestDist:
                best = candidate
                bestDist = d
    return best
```

Key invariants:

- Each county's `nearestWithin` runs on the same `AddressLookupExecutor` thread that 004 used (single-thread scheduled). The fan-out is sequential, not parallel — multi-county MUST NOT increase concurrency pressure on SQLite.
- Passing `bestDist` (monotonically shrinking) as the per-county query radius lets each subsequent county prune its R*Tree bbox to a smaller box than the previous county's winner, reducing per-county work. On a perfectly geographic disjoint partition (Taichung non-overlapping Changhua) this means county 2 onwards see a smaller bbox than county 1.
- The Throwable catch around each county keeps a corrupted county from breaking the others (Constitution VI listener short-circuit rule).

### 4.2 Deterministic tie-break for ties at machine precision

If two records (one in Taichung, one in Changhua) are equidistant by haversine to the query point — extremely rare; the haversine of two distinct lat/lon pairs is identical only for points symmetric across the great-circle midpoint — the resolver returns the record from the county whose registry iteration came first (FR-implemented as `ConcurrentMap`'s entry-set order, which on modern Java is insertion order for `ConcurrentHashMap`).

This is "deterministic" per US3 acceptance scenario 4: "no source-county bias" means the algorithm itself doesn't prefer one county; the tie-break is iteration order, not county-specific weight.

---

## 5. Reentrancy and queue invariants

Per spec FR-019 (clarifications Q3):

- **One BatchSession per active page**: the Offline Address page owns one session; the page is a `DropDownReceiver` with single instantiation per process.
- **Queue is unbounded but back-pressured by the executor**: the executor is single-thread, so newly-enqueued files wait for in-flight files. There's no hard cap; FR-016 limits parallelism, not queue depth. UI displays the depth ("待處理 N 個").
- **`ACTION_DATASET_CHANGED` fires per atomic county change** (research R8), not per file or per batch. This means a single large ZIP yielding 2 counties produces 2 broadcasts.
- **Cancellation is cooperative**: the worker checks `session.state == CANCELLED` between entries. An in-flight entry completes its current atomic step (extract / validate / activate) before the worker exits the batch loop — never mid-rename.

---

## 6. Validation rules (extending 004's contract)

Inherited from 004's `AddressBundleImporter`:

- Magic bytes: file must start with `SQLite format 3\0`.
- Reject `PK\003\004` (ZIP magic) for `places-<county>.sqlite` paths — but feature 005 detects ZIPs upstream at the picker level, so this lower-level check only ever surfaces when an operator manually corrupts a file.
- `metadata` table must exist with rows for `schema_version`, `county`, `data_date`.
- `schema_version` ∈ `[1, MAX_SUPPORTED_SCHEMA_VERSION]` (currently 2).
- `places` table must exist with the expected columns (see data-contract §3.1).

New for feature 005:

- For Replace: picked file's `metadata.county` MUST equal the row's `county` (FR-007). The check is **exact string match** after the generator's normalisation (`台中市` not `臺中市`) — see Edge case "County metadata mismatch" + research R10's defensive-validation gate.
- For each ZIP entry's pre-extract classification: filename pattern matching is case-sensitive (data-contract §2 specifies lowercase only).
- For `BatchSession.add(file)`: file must be an existing readable regular file. Symbolic links are rejected (defensive validation; rare in `/sdcard/Download/` but possible).
- For ZIP entries containing internal `..` or absolute paths: rejected (zip-slip defence). The classifier surfaces it as `UNRECOGNIZED` with a `Log.w`; the entry is not extracted.

---

## 7. Persistence — what survives across plugin re-launches

- `active/<county>/places.sqlite` + `imported.manifest.txt`: persists indefinitely; reopened by registry at plugin onCreate.
- `.staging-<county>-<uuid>/`: transient, swept on importer ctor (FR-005 inheritance from 004).
- `BatchSession`: in-memory only. A plugin restart abandons the session; in-flight entries that had reached "activated" or "replaced" persist (their `imported.manifest.txt` is on disk); pending queue is lost. Operators that crash mid-batch must re-pick the remaining files.
- `ActiveDatasetRegistry`: in-memory only; re-built at plugin onCreate by scanning `active/*/imported.manifest.txt`.
- Settings PreferenceFragment per-county rows: rebuilt on every `ACTION_DATASET_CHANGED` broadcast and on `onResume`. No state to persist outside the registry itself.

---

## 8. Telemetry — what makes it into logcat

- `BatchImportCoordinator: starting batch sessionId=<uuid> queueDepth=N`
- `BatchImportCoordinator: processing <filename> (entry M/N)`
- `BatchImportCoordinator: <filename> → ACTIVATED county=<county> sha=<sha-prefix> durationMs=…`
- `BatchImportCoordinator: <filename> → SKIPPED_SUPPLEMENTARY (townships.sqlite)`
- `BatchImportCoordinator: <filename> → FAILED reason=<Failure.reason> details=<message> durationMs=…`
- `BatchImportCoordinator: batch sessionId=<uuid> done: activated=N replaced=M skipped=K failed=L`
- `ActiveDatasetRegistry: add county=<county> sha=<sha-prefix>`
- `ActiveDatasetRegistry: replace county=<county> oldSha=<prefix> newSha=<prefix>`
- `ActiveDatasetRegistry: remove county=<county>`
- `ActiveDatasetRegistry: deregister-on-tamper county=<county>` (FR-014 path)
- `AutoMigrator: migrate county=<county> ok` / `AutoMigrator: legacy unchanged, county not valid`
- `FallbackSqliteFactory: primary failed for <county>, lazily loading fallback runtime`

All `Log.w` paths include the throwable (`Log.w(TAG, message, throwable)`) per Constitution VI.
