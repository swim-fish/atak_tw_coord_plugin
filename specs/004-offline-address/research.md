# Phase 0 Research: Offline Address Lookup

**Feature**: `004-offline-address` | **Date**: 2026-05-24

This document records the reconnaissance and decisions made before Phase 1 design. Each item
follows the **Decision / Rationale / Alternatives considered** format and is anchored to BOTH
the bundled SDK (`../ATAK-CIV-5.7.0.3-SDK/main.jar`, verified via `javap -public`) and the
upstream Java source (`github.com/TAK-Product-Center/atak-civ`, branch `main`) per the user-level
memory `feedback-plan-phase-code-anchoring`. When `javap` and upstream disagree, the SDK jar
wins because the plugin compiles against it.

## Anchoring discipline

Every SDK class reference cited below MUST be reproducible via one of:

```text
javap -public -classpath '../ATAK-CIV-5.7.0.3-SDK/main.jar' <fully-qualified-class>
javap -public -classpath '../ATAK-CIV-5.7.0.3-SDK/main.jar' -p <class>   # if private members needed
```

The corresponding upstream cross-check uses URLs of the form
`https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/<package-path>/<class>.java`.
These permalinks are pinned at first reference and re-verified during `/speckit-implement` if the
SDK behaviour the citation supports is found to disagree with observation.

## R1 — Tools-menu entry registration pattern

**Decision**: Add `OfflineAddressTool extends AbstractPluginTool` (constructor takes the plugin
`Context` plus a fixed action string), register it from `TwCoordLifecycle.onStart` alongside the
existing `TwCoordTool` and `TwCoordGotoTool`. The action string is `com.atakmap.android.twcoord.SHOW_OFFLINE_ADDRESS`.

**Rationale**: Matches the in-tree pattern exactly. `app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordTool.java`
(read at plan time, line 7–20) shows `AbstractPluginTool` only needs a label, description, icon
drawable, and broadcast action. The existing `TwCoordLifecycle` already registers two such
tools; adding a third is a one-line addition there. The receiver side mirrors
`TwCoordGotoReceiver` — see R11 for the receiver decision.

**Alternatives considered**:

- *Reuse `TwCoordTool` with a multi-mode cycle* — rejected. `TwCoordTool` already cycles four
  visibility / unit states (`Off → Taipower → TWD97 → TWD67 → Off`); adding "open address page"
  as a fifth state would conflate two unrelated UI surfaces.
- *Add the entry under Settings only (no Tools menu icon)* — rejected. The spec (FR-001)
  explicitly requires the Tools-menu entry alongside the existing two plugin tools, and an
  operator's main entry point for managing a sideloaded dataset is the Tools menu, not a
  Settings sub-screen.

**SDK anchor**:

- `javap -public -classpath '../ATAK-CIV-5.7.0.3-SDK/main.jar' com.atak.plugins.impl.AbstractPluginTool`
  — confirms the constructor `(Context, String label, String description, Drawable icon, String action)`.
- Upstream: `atak/ATAKPluginTemplate/.../plugin/AbstractPluginTool.java` (template repo; the
  pattern is documented in `ATAK_Plugin_Development_Guide.pdf` §6 "Adding a Tool").

## R2 — ATAK-managed plugin data directory

**Decision**: Resolve the active-dataset and staging directories at runtime via
`com.atakmap.coremap.filesystem.FileSystemUtils.getItem(String)`. The plugin uses the relative
path `tools/twcoord/offline-address/` so the canonical on-device location is
`/sdcard/atak/tools/twcoord/offline-address/`. The plugin never writes that absolute path as a
string literal — `FileSystemUtils` owns the absolute root resolution and is the only call site
that knows about `/sdcard/atak`.

**Rationale**: `FileSystemUtils.getItem(...)` is the single ATAK-blessed helper for "I want a
file under the ATAK root that survives plugin reinstall and respects scoped-storage shims"
behaviour. ATAK 5.4–5.7 all route this through the same data-fs root. The relative path
`tools/twcoord/` aligns with the convention used in the VNS-study (`tools/VNS/GH/<region>/`)
and the Address Plugin (`tools/address/<state>.db`); reusing the parent `tools/twcoord/`
keeps every plugin-owned file under one inspectable subtree.

**Alternatives considered**:

- *`Context.getExternalFilesDir(null)` (Android scoped-storage compliant)* — rejected. The
  plugin runs hosted in the ATAK process; its private data dir is ATAK's own dir, which is the
  wrong scope for "data shared between sessions of this plugin". The ATAK `FileSystemUtils`
  helper exists exactly to abstract this.
- *Hard-coded `/sdcard/atak/tools/twcoord/...`* — rejected. Constitution Principle VI's bullet
  "Hard-coded `/sdcard/atak/tools/address` … fragile under Android 11+ scoped storage" applies
  directly; the `address-atak-plugin-study.md` notes this is exactly what made the Address
  Plugin fragile on newer devices.

**SDK anchor**:

- `javap -public -classpath '../ATAK-CIV-5.7.0.3-SDK/main.jar' com.atakmap.coremap.filesystem.FileSystemUtils`
  — confirms `static File getItem(String relativePath)` and `static File getRoot()`.
- Upstream: `takEngine/.../coremap/filesystem/FileSystemUtils.java` (path relative to the
  upstream coremap submodule; the upstream URL at first audit will be re-pinned to a
  permalink in ADR-0014-reconnaissance).

## R3 — Reverse-lookup spatial index (R*Tree built at plugin import)

**Decision**: Use Android's built-in SQLite (`android.database.sqlite.SQLiteDatabase.openDatabase(...)`
with `OPEN_READONLY | NO_LOCALIZED_COLLATORS`). Platform SQLite from API ≥ 21 ships both R*Tree
and FTS5 extensions, so the project's `minSdk 26` floor satisfies the requirement with no new
dependency. **However, the companion generator (`atak-tw-address-generator`) does not currently
ship an R*Tree** — its schema (verified at plan time against
`atak-tw-address-generator/scripts/ingest_tgos_csv.py` `SCHEMA_SQL`) includes `places`,
`places_fts` (FTS5), and `metadata`, with only B-tree indexes on `district_code` and
`(district_code, village, street, lane, alley, number)`. Reverse-lookup at 1.3 M rows over a
B-tree-on-administrative-code is infeasible within the 1 s SC-002 budget.

**Resolution**: The plugin builds the R*Tree at import time, into the **same** `.sqlite` file
the operator imported, as a one-shot step inside the validation / staging pipeline:

```sql
CREATE VIRTUAL TABLE IF NOT EXISTS places_rtree USING rtree(
    id,                -- INTEGER PRIMARY KEY of places (1:1)
    min_lat, max_lat,
    min_lon, max_lon
);
INSERT INTO places_rtree(id, min_lat, max_lat, min_lon, max_lon)
SELECT id, lat, lat, lon, lon FROM places
WHERE NOT EXISTS (SELECT 1 FROM places_rtree WHERE id = places.id);
ANALYZE places_rtree;
```

For 1.3 M Taichung rows on a Galaxy Tab S10+ this takes ~30–45 seconds and roughly doubles the
on-disk size (R*Tree node pages plus the rowid mapping). The staging dir gives us a clean
write-then-rename window. Subsequent imports (re-importing the same file) skip the build via the
`CREATE VIRTUAL TABLE IF NOT EXISTS` guard and the `WHERE NOT EXISTS` insert filter.

**Future generator enhancement** (recommended, not blocking): if a future generator version adds
the R*Tree directly to its `SCHEMA_SQL`, the plugin's import skips the build step (the `IF NOT
EXISTS` and the `WHERE NOT EXISTS` are no-ops). The on-disk file size grows but generator-side
build cost replaces plugin-side import cost — a more honest split that lets the plugin's import
finish in single-digit seconds. Recommendation: open a tracking issue against
`atak-tw-address-generator` to add R*Tree to `SCHEMA_SQL` immediately after `places_fts` is
built. The plugin does **not** wait for this change.

**Rationale**: Building the R*Tree once at import is preferable to building it every plugin
restart (no point burning 30–45 s of cold-start on every launch) or every query (infeasible).
The `CREATE … IF NOT EXISTS` plus `WHERE NOT EXISTS` filter makes the operation idempotent so
re-imports do not re-build. The pre-activation timing keeps the operator-visible failure mode
clean: if the R*Tree build fails (disk-full, bad data), the staging dir is wiped and the
previously-active dataset is untouched.

**Alternatives considered**:

- *Bundle SQLite JNI (e.g. `requery/sqlite-android`)* — rejected. Adds ~3 MB to the APK, brings
  CVE surface, and the platform SQLite is feature-complete for our needs.
- *Build an in-memory grid hash at first lookup, cache for lifetime* — rejected. A grid hash
  over 1.3 M `(lat, lon)` rows costs ~80 MB of heap; the JVM heap on ATAK is tight enough that
  we cannot afford that allocation lifetime-of-process. Disk-backed R*Tree pages page in on
  demand.
- *Linear scan with naive haversine* — rejected. 1.3 M rows × per-row haversine on every query
  blows the 1 s SC-002 budget by orders of magnitude.

**SDK anchor**:

- `javap -public -classpath '<android.jar>' android.database.sqlite.SQLiteDatabase`
  — confirms `openDatabase(String, CursorFactory, int)`, `OPEN_READONLY`,
  `OPEN_READWRITE`, `NO_LOCALIZED_COLLATORS`. The plugin opens read-write only during the
  staging-phase R*Tree build, then closes and re-opens read-only for runtime queries.
- Android SDK reference: <https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase>
- SQLite R*Tree docs: <https://www.sqlite.org/rtree.html>
- Generator source for current schema: `atak-tw-address-generator/scripts/ingest_tgos_csv.py`
  (`SCHEMA_SQL` block, lines 54–93 at the version verified on 2026-05-24).

## R4 — Reverse-lookup algorithm (bbox → haversine, plus display-name pick)

**Decision**: Two-stage query.

**Stage 1** — convert the desired search radius (default **500 m**) to a lat/lon delta
with the cos-latitude correction (`Δlon = Δlat / cos(latRad)`), query `places_rtree` for
records inside `(min_lat ≤ lat + Δlat AND max_lat ≥ lat - Δlat AND min_lon ≤ lon + Δlon AND max_lon ≥ lon - Δlon)`,
JOIN to `places` on `id` and project `(lat, lon, display_name, display_name_halfwidth)`.

**Stage 2** — for each candidate, compute haversine distance to the query point; return the
record with the smallest distance under the radius. Empty result → `AddressLookupResult.Empty`.
The widget renders `display_name` (the fullwidth form is the natural Taiwan address layout, e.g.
"台中市中區大誠里大誠街３９巷２之３之２號"); `display_name_halfwidth` is reserved for future
forward-search use and not surfaced in this feature.

**Rationale**: The R*Tree (built per R3) makes the bbox lookup `O(log n + k)` where k is the
number of records inside the box. For a 500 m × 500 m box in Taichung's denser urban areas k is
on the order of 10²–10³ (Taiwan address density is high); haversine refine on that count is
microseconds. The radius is large enough to catch the nearest house-number record in suburban
Taiwan, small enough to bound k in dense urban centres.

The "house number nearest to my point" semantic that this returns is honest about what TGOS
data is — it is a registry of postal addresses, not a street-name geometry layer. If the
nearest house number is 30 m away across a road, the operator sees that house number; if no
record is within 500 m, the row reads "No address nearby".

**Alternatives considered**:

- *Expand-on-miss (start with 100 m, double until hit)* — rejected. Adds 2–3 round-trips in the
  worst case; constant-radius is more predictable and the empty-state row already handles "no
  record within radius" honestly.
- *Project lat/lon to a TWD97 grid and do Euclidean distance* — rejected. The cos-latitude
  bbox + haversine combination already produces sub-metre accuracy at Taiwan latitudes for the
  500 m radius we care about; the TWD97 projection step adds complexity without measurable
  accuracy gain.
- *Use the `places_fts` FTS5 index for reverse-lookup* — rejected. FTS5 is for full-text
  forward search ("the operator types `大誠街` → show matches"); it is not a spatial index and
  cannot answer "what is closest to this lat/lon".
- *Return all records within radius (sorted by distance) and let the UI pick* — rejected.
  Increases the data crossing the thread boundary by 1–3 orders of magnitude for no UI benefit;
  the widget only shows one address per row.

**Source anchor**:

- Address Plugin study notes (`docs/research/address-atak-plugin-study.md` § 4.1, "R*Tree
  bounding-box query then refine") for the same recipe in a production plugin.
- Haversine reference: <https://en.wikipedia.org/wiki/Haversine_formula> (no external lib
  needed; the formula is six lines of Java).
- Generator's `display_name` composition: `atak-tw-address-generator/scripts/normalize_address.py`
  `compose_display_name(...)` function (lines 86–122), which assembles the Taiwan-format string
  from county / district / village / street / lane / alley / number.

## R5 — Bundle layout (bare `.sqlite` with in-DB metadata)

**Decision**: The plugin imports the file format the companion generator
(`atak-tw-address-generator`) actually produces today: a single SQLite file named
`places-<county>.sqlite` (e.g. `places-taichung.sqlite`, `places-changhua.sqlite`). All
provenance lives inside the database, in the `metadata` table written by the generator's
`ingest_tgos_csv.py`:

| Key | Value example | Plugin treatment |
|---|---|---|
| `schema_version` | `1` | **Mandatory**; must equal the plugin-pinned value (`1` for plugin v1); mismatch → import rejected (FR-004 / Edge Cases). |
| `source` | `tgos` | **Mandatory**; informational; surfaced on Offline Address page. |
| `county` | `台中市` | **Mandatory**; rendered as the dataset's display label. |
| `data_date` | `115-01` (民國年-月) | **Mandatory**; rendered on the Offline Address page; format opaque to the plugin (display verbatim). |
| `csv_sha256` | `<hex 64>` | Optional (display only); SHA-256 of the source TGOS CSV used to build this file. |
| `csv_path` | `input/115年1月GIS門牌_台中市…` | Optional (display only); the source CSV filename. |
| `crs` | `EPSG:4326` | Optional (display only); the source CRS. |
| `inserted` | `1316674` | Optional (display only); row count. |
| `skipped_no_number` / `skipped_unknown_code` | small ints | Optional (display only); generator diagnostics. |

Filename pattern `places-<county>.sqlite` is informational — the plugin's file picker accepts
any `.sqlite`-extension file and identifies the file by the `metadata` table contents, not by
the filename. (Operators may rename files; that must not break import.) The plugin computes the
**file's** SHA-256 at import time and stores it in a plugin-side companion file
(`active/imported.manifest.txt`, plain text key=value) for the Offline Address page to display
provenance. This file is plugin-owned and not part of the generator's contract.

**Rationale**: The plugin's job is to consume what the generator emits, not to dictate a
re-packaging step. The generator stores everything provenance-related in the SQLite `metadata`
table already; adding an external manifest would duplicate the data and create drift risk.
The companion `imported.manifest.txt` is a tiny one-shot file (5–6 lines) the plugin owns
locally for its own bookkeeping; it carries the plugin's view of the dataset (when imported,
what the file's SHA-256 is at import, plugin schema-version pin), distinct from the data's
provenance which stays in `metadata`.

The plugin's `imported.manifest.txt` schema (plain-text key=value):

```text
imported_at=2026-05-24T15:30:00Z
file_sha256=<hex 64>                   # SHA-256 of the .sqlite file as imported
rtree_built=true                       # whether the plugin built the R*Tree at import
plugin_schema_version=1                # the plugin version that imported this dataset
```

**Alternatives considered**:

- *Refuse to import a file without `places_rtree`* — rejected. The current generator does not
  produce it; refusing the import would block the entire feature on a generator-side change.
  Building the R*Tree at import time (R3) is a strictly more conservative integration.
- *Replicate `metadata` in an external `.manifest.txt` shipped alongside the `.sqlite`* —
  rejected. Two copies of the same truth invite drift; the generator only writes the DB.
- *Persist `imported.manifest.txt` as JSON* — rejected. Six keys, zero nesting; key=value is
  zero-parser-dependency.
- *Lock filename to `places-<county>.sqlite` exactly* — rejected. Operators routinely rename
  downloads; identifying the dataset by `metadata` content is robust.

## R6 — Widget address row integration

**Decision**: Extend `TwCoordWidget` with three sibling `TextWidget` instances —
`mapAddrRow`, `meAddrRow`, `targetAddrRow` — each added to the same anchor as its parent row
(BOTTOM_LEFT, BOTTOM_RIGHT, TOP_RIGHT respectively) *immediately after* the existing row, so
the address text reads as a second line under the coordinate. Each new row uses the identical
`TextWidget(initial, TEXT_SIZE_OFFSET)` constructor and inherits the EyeAlt styling exactly,
keeping visual weight aligned (FR-019). Per-row visibility is bound to the new preferences via
`row.setVisible(prefEnabled && datasetActive)`; when both are true and a lookup is in flight,
the row shows a `Loading…` placeholder.

**Rationale**: Same anchor + same parent layout = the new row sits naturally underneath the
coordinate row without colliding with ATAK's own widgets (callsign card, eye-alt readout). The
existing `addWidget(...)` call already returns the index where the widget was inserted; the
address row goes immediately after at the same anchor. The widget is internally re-wrapped
inside the existing `try/catch (Throwable)` guard at `TwCoordWidget.render(...)` per
Constitution VI; the new public `renderAddresses(...)` method gets the same treatment.

**Alternatives considered**:

- *Concatenate the address into the existing row's text* — rejected. The existing row's text
  is colour-tagged based on state (OK / OUT_OF_RANGE / NO_FIX / NO_PERMISSION); mixing in an
  address would force the operator to read past the coloured prefix to find the place name.
  Separate rows let each be independently coloured (e.g. address row uses muted white) and
  independently sized.
- *Use a single shared address row at the bottom of the screen* — rejected. The spec
  explicitly requires per-row address text (US3 acceptance scenario 3: "the address row MUST
  appear only under that one coordinate row").
- *Use a custom Drawable subclass for the row* — rejected. The existing readout widget achieves
  EyeAlt styling parity via the `TextWidget(String, int)` constructor; introducing a new
  Drawable would be a needless variant.

**SDK anchor**:

- `javap -public -classpath '../ATAK-CIV-5.7.0.3-SDK/main.jar' com.atakmap.android.widgets.TextWidget`
  — confirms the `(String, int)` constructor and `setVisible(boolean)`.
- Upstream: `atak/ATAK/app/src/main/java/com/atakmap/android/widgets/TextWidget.java`
- `app/src/main/java/com/atakmap/android/twcoord/TwCoordWidget.java` lines 78–85 — the in-tree
  `newStyledTextWidget(...)` factory we'll reuse for the address rows.

## R7 — Threading model (debounce + executor)

**Decision**: A single-thread `ScheduledExecutorService` owned by `AddressSubsystem`. Per-row
"latest coordinate" requests are coalesced through a `Map<Row, ScheduledFuture<?>>` — when a
new fix arrives, the in-flight scheduled task (if any) is cancelled and a new one is scheduled
**250 ms** in the future. The 250 ms debounce absorbs the burst of `MapEvent.MAP_SCROLL`
events ATAK fires during a pan / zoom. Once the task fires, it runs the bbox + haversine
lookup, posts the result back to the UI thread via `mapView.post(...)`, where
`TwCoordWidget.renderAddresses(...)` is invoked.

**Rationale**: A single thread is sufficient — the workload is one query per row per debounce
window, max 3 queries in flight worst case. Cancel-and-reschedule beats a continuous queue
because operator panning generates a long stream where only the last position matters. The
250 ms window is the same magnitude as the Address Plugin's `MapCenterWidget` 5 s debounce
divided by the spec's tighter 1 s budget — short enough that an operator who stops panning
sees the address in well under the 1 s SC-002 target, long enough to drop intermediate fixes.

**Alternatives considered**:

- *`Executors.newFixedThreadPool(2)` shared with import* — rejected. Conflates lifecycle
  (importer wants a one-shot job; resolver wants debounce). Two single-thread executors are
  simpler to reason about.
- *Coroutines / RxJava* — rejected. Adds a dependency; the workload is small enough that
  hand-rolled `ScheduledExecutorService` is clearer and matches the project's existing
  thread-discipline pattern (see `SelfMarkerSubscriber`).
- *`AsyncTask` (legacy)* — rejected. Deprecated, and lifecycle is harder to control than a
  dedicated executor.

## R8 — Atomic dataset activation

**Decision**: Three-phase activation.

Phase 1 — copy the operator-picked `.sqlite` from the SAF `InputStream` into the staging
directory `tools/twcoord/offline-address/.staging-<timestamp>/places.sqlite`, hashing during
the copy (R9); `fsync` on close. Open it read-only to verify `metadata` and `places` schemas
(FR-004).

Phase 2 — open the staged file read-write to build the R*Tree (R3), then close. Write the
plugin-side `imported.manifest.txt` (R5) into the staging directory.

Phase 3 — atomically replace `tools/twcoord/offline-address/active/` with the staging directory
via `java.nio.file.Files.move(stagingDir, activeDir, ATOMIC_MOVE, REPLACE_EXISTING)` **after**
first renaming any pre-existing `active/` to `active-old-<timestamp>/` for the post-success
deletion. The post-success deletion is best-effort (logged on failure but does not abort the
activation).

**Rationale**: `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` is the strongest guarantee the
filesystem provides on Android (ext4) — the rename is one inode-table update. A crash during
Phase 1 leaves `active/` untouched (validation has not happened); a crash during the rename is
the only window where the operation is not atomic, and on ext4 that window is microseconds. A
crash after the rename but before the old-dir cleanup just leaves a `active-old-*` stale dir,
which the plugin's `onCreate` sweeps on next start.

**Alternatives considered**:

- *Single-file activation (rename `staging.sqlite` to `active.sqlite`)* — rejected. The bundle
  must carry the `manifest.txt` and `timestamp` files alongside the DB so the page can render
  metadata after a restart; multi-file activation needs the directory swap.
- *Copy-then-delete* — rejected. Not atomic; a crash mid-copy corrupts the active dir.
- *Write a `.activation_complete` sentinel file* — rejected. Adds a file the plugin has to
  read and trust before opening the DB; an atomic directory swap is strictly safer.

## R9 — SHA-256 (display-only provenance, not gating)

**Decision**: As the importer streams the operator-picked `.sqlite` file from the SAF
`InputStream` to the staging-dir target file, it pipes the bytes through
`java.security.MessageDigest.getInstance("SHA-256")`. The computed digest (lowercase hex) is
written to the plugin-side `imported.manifest.txt` (R5) for the Offline Address page to display
as provenance. **The import is not gated on a hash compare** because the generator does not
publish an authoritative hash to compare against — the in-DB `metadata.csv_sha256` is the hash
of the *source CSV*, not the `.sqlite` file. The file's hash is recorded so that an operator
inspecting `imported.manifest.txt` can confirm two import sessions produced bit-identical files
(reproducibility check) or compare against an out-of-band hash the dataset publisher provides.

**Rationale**: Hashing during the copy adds zero wall-clock overhead because the SAF read is
the bottleneck (mostly disk I/O). `MessageDigest` is stdlib; no new dependency. Recording the
file SHA without gating on it keeps the integration honest: there is no published authoritative
hash today, so claiming a gate would be theatre.

**Future enhancement** (not in v1): if the generator project starts publishing a `.sha256`
sidecar alongside the `.sqlite` on its release channel, the plugin's importer can optionally
read an operator-supplied expected hash (paste or scan) and reject the import on mismatch. The
storage location (`imported.manifest.txt`) is already in place.

**Alternatives considered**:

- *Gate the import on SHA-256 match against `metadata.csv_sha256`* — rejected. That hash is of
  the *source CSV*, not the `.sqlite`; comparing them would be a category error.
- *Skip the hash entirely* — rejected. Even display-only, the hash is the only invariant the
  operator has if they want to confirm "this is the same file I downloaded".
- *Use a non-cryptographic hash (CRC32, xxHash)* — rejected. CRC32 is too weak for tamper
  detection if ever needed; xxHash requires a third-party dep. SHA-256 is stdlib and ARMv8
  crypto-extension accelerated on the reference device.

## R10 — Constitution VI compliance audit

**Decision**: Enumerate every new entry point the host can call into and confirm each will be
wrapped in `try/catch (Throwable)` at its outer scope (rule from Principle VI). The list of new
entry points this feature adds:

| # | Entry point | Class | Wrap location |
|---|---|---|---|
| 1 | `onReceive(Context, Intent)` | `OfflineAddressReceiver` | outer body |
| 2 | `onDropDownVisible(boolean)` | `OfflineAddressReceiver` | outer body |
| 3 | `onDropDownClose()` | `OfflineAddressReceiver` | outer body |
| 4 | `onDropDownSizeChanged(double, double)` | `OfflineAddressReceiver` | outer body |
| 5 | `OfflineAddressTool(Context)` ctor | `OfflineAddressTool` | n/a (super-call only; super is already wrapped in host) |
| 6 | `dispose()` | `OfflineAddressTool` | outer body |
| 7 | `onPreferenceClick(Preference)` for the "Open Offline Address page" shortcut | `TwCoordPreferenceFragment` | lambda body |
| 8 | `onSharedPreferenceChanged(...)` reaction for the 3 new keys (driven from `PreferenceStore`) | `PreferenceStore.spListener` | inside `fireAll` per-listener wrap |
| 9 | `AddressSubsystem.onCoordRefresh(GeoPoint, Row)` | `AddressSubsystem` | outer body of the callback registered with the existing render path |
| 10 | SAF `ActivityResultLauncher` callback receiving the picked URI | `OfflineAddressReceiver` | outer body of the registered callback |
| 11 | Import worker `Runnable.run()` body | `AddressBundleImporter` (executor task) | outer body |

**Rationale**: Same list-and-wrap discipline as feature 003 (which audited 9 entry points,
documented in plan-003 R12). The list is enumerated in `tasks.md` Polish phase so reviewers can
check each off; `/speckit-analyze` is configured to flag any unguarded entry point as CRITICAL.

The existing `TwCoordWidget.render(...)` path is **not** widened — the new `renderAddresses(...)`
helper is wrapped internally; the existing 3-row render path remains exactly as it is.

**Alternatives considered**:

- *Wrap-everything macro / aspect-oriented sugar* — rejected. The plugin doesn't use AOP; the
  hand-written wraps are visible at the call site (a reviewer can spot a missing one in 3
  seconds), which is the entire point of Principle VI.

## R11 — Settings UI (3 SwitchPreferences + dataset-presence hint)

**Decision**: Insert a new `PreferenceCategory` "Offline Address" into `res/xml/preferences.xml`
**after** the existing accuracy notice category. The category contains three `SwitchPreference`
entries (`pref_address_row_me`, `pref_address_row_target`, `pref_address_row_map`) plus a
non-clickable summary row (`pref_address_dataset_status`) that surfaces one of:

- "No dataset installed — tap to open Offline Address" (when at least one toggle is on but no
  dataset is active)
- "Active: <region> · <data_date>" (when a dataset is active)
- (hidden when all three toggles are off)

The `pref_address_dataset_status` row is also clickable; tapping it broadcasts
`OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS`, opening the page. The fragment refreshes
the status row in `onResume` and on every `onSharedPreferenceChanged` for the relevant keys.

**Rationale**: SwitchPreference is the platform-native control for booleans and matches the
project's existing settings style. Keeping the status row inline (not in a separate dialog)
gives the operator the dataset state at-a-glance and a one-tap path to the management page,
which directly addresses FR-010 (toggle on but no dataset → surface a hint).

**Alternatives considered**:

- *One "master" SwitchPreference + a nested 3-row sub-screen* — rejected. The user explicitly
  clarified that the three toggles should not have a master switch on top (Clarifications
  session 2026-05-24, Q2). Three flat toggles are the literal answer.
- *Use ATAK's `PanListPreference` for a multi-select* — rejected. PanListPreference is
  ATAK-styled but multi-select via that widget is awkward UX (one-time list dialog rather than
  in-line toggles), and the operator would lose the at-a-glance state.

**SDK anchor**:

- Android SDK: `android.preference.SwitchPreference` —
  <https://developer.android.com/reference/android/preference/SwitchPreference>
- In-tree existing pattern: `app/src/main/res/xml/preferences.xml` (read at plan time) — uses
  `com.atakmap.android.gui.PanListPreference` for list rows and stock `Preference` for
  informational rows.

## R12 — Coverage gap behaviour (in-region but unmapped)

**Decision**: A successful lookup that returns zero records (because the operator is over a
genuinely unmapped point — small reservoir, industrial yard, ocean) MUST produce
`AddressRowState.EmptyState` with the text "No address nearby" (localised). This is the same
state as for *out-of-region* points (FR-013 explicitly says both cases produce the same row).
The empty-state row remains visible for as long as the underlying coordinate stays in that
condition; once the coordinate moves to a point with an address, the row updates immediately.

**Rationale**: Distinguishing "out of region" from "in region but unmapped" requires storing a
polygon (or set of polygons) for the dataset's covered area, which is overengineering for the
spec's stated success criterion (SC-006 already accepts ≥ 95 % coverage as success). A single
empty-state text is honest about the absence and avoids implying a system fault.

**Alternatives considered**:

- *"Out of region" vs "no address nearby" distinct strings* — rejected. The dataset has no
  region-polygon metadata in the schema we're shipping (data-model.md §1); inventing this
  distinction would require extending the bundle format.
- *Last-known-good fallback* — rejected. Showing a stale value would mislead an operator
  expecting the row to reflect "here" — the empty-state honesty rule (FR-013) explicitly
  forbids stale values.
