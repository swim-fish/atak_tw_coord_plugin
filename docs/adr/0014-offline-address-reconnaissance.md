# ADR-0014: Offline Address Lookup — Phase 0 reconnaissance against ATAK-CIV 5.7.0.3 SDK + companion generator schema

**Status**: Accepted (reconnaissance complete; implementation captured in ADR-0015)
**Date**: 2026-05-24
**Origin**: Operator request *"Add offline reverse-address lookup so the readout widget can show '台中市西區美村路一段 600 號' under the coordinate row"*, combined with the constraint *"使用系統舊有的機制 不要自己開發多餘的東西 優先使用舊的功能 先看 SDK 如何使用"* (reuse the system's existing mechanism; don't build superfluous things; prefer existing functionality; investigate the SDK first) — same discipline as ADR-0010.

## Context

Feature `004-offline-address` adds a new Tools-menu entry **Offline Address** that lets operators side-load a `places-<county>.sqlite` file produced by the companion generator `atak-tw-address-generator` (sibling repo at `C:\Users\hhhnr\source\tak\atak_vns_offline_routing\atak-tw-address-generator`). After import, three independent Settings toggles gate whether an address line appears under each coordinate row (ME / TGT / MAP) inside the existing `TwCoordWidget`.

Three constraints shaped Phase 0:

1. **The companion generator's schema is fixed**. The plugin must consume what the generator emits today (data-contract v1: `places`, `places_fts`, `metadata`; v2 evening of 2026-05-24 adds pre-built `places_rtree`). Plugin-side conventions cannot dictate a re-packaging step.
2. **ATAK 5.7's SDK + Android 11+ scoped storage define what plugin code can touch**. The plugin runs hosted inside the ATAK process and can only use the public API surface in `ATAK-CIV-5.7.0.3-SDK/main.jar` plus the platform Android APIs. Hard-coded `/sdcard/atak/...` paths are fragile under scoped storage; ATAK's `FileSystemUtils` exists exactly to hide that.
3. **Constitution VI** mandates that every host-callable entry point be wrapped in `try/catch (Throwable)` so a plugin bug never crashes ATAK. The new feature adds ≥ 11 entry points; each needs to be enumerated before implementation.

A pre-implementation reconnaissance pass against `ATAK-CIV-5.7.0.3-SDK/main.jar` answered the SDK questions; a parallel pass against the generator's `scripts/ingest_tgos_csv.py` answered the schema questions. This ADR records what was found so that ADR-0015 (implementation pivots) does not have to re-litigate why each path was chosen.

## SDK reconnaissance — what ATAK + Android + the companion generator already provide

All findings below were verified via `javap -public` against `ATAK-CIV-5.7.0.3-SDK/main.jar` for ATAK classes, `javap -public` against `android.jar` for Android-platform classes, and direct reads of the generator's Python source for schema details. Each cited class is additionally linked under [Links](#links) to its `.java` source in the active upstream mirror — `github.com/TAK-Product-Center/atak-civ` — so a future reader can cross-check method bodies (which `javap -p` only stubs) against the as-shipped implementation. The SDK jar remains the build-time contract; the upstream repo is documentation/cross-check only. When `javap` and upstream disagree, the SDK jar wins because the plugin compiles against it. (Same anchoring discipline as feature 003 / ADR-0010.)

### R1 — Tools-menu entry registration (`AbstractPluginTool`)

`com.atak.plugins.impl.AbstractPluginTool` is the public base class for Tools-menu entries. Constructor signature:

```
public AbstractPluginTool(Context pluginContext, String label, String description, Drawable icon, String action)
```

The plugin registers an instance from `TwCoordLifecycle.onStart` alongside the existing `TwCoordTool` (feature 001) and `TwCoordGotoTool` (feature 002); the icon is `R.drawable.ic_offline_address`. Tapping the entry broadcasts the action string `com.atakmap.android.twcoord.SHOW_OFFLINE_ADDRESS`, consumed by `OfflineAddressReceiver` (R6).

### R2 — Plugin data root (`FileSystemUtils.getItem`)

`com.atakmap.coremap.filesystem.FileSystemUtils` exposes `static File getItem(String relativePath)` and `static File getRoot()`. The plugin uses the relative path `tools/twcoord/offline-address/` so the canonical on-device location is `/sdcard/atak/tools/twcoord/offline-address/` — but **the plugin never writes that absolute string as a literal**. `FileSystemUtils` owns the absolute root and is the only call site that knows about `/sdcard/atak`. This is exactly the rule that kept the legacy Address Plugin from breaking under scoped storage (per `docs/research/address-atak-plugin-study.md`).

### R3 — Platform SQLite + R*Tree extension

Android API ≥ 21's `android.database.sqlite.SQLiteDatabase` ships **both** the R*Tree extension and FTS5. The project's `minSdk = 26` floor satisfies this with zero new dependencies. Plugin opens read-only at runtime (`OPEN_READONLY | NO_LOCALIZED_COLLATORS`) and read-write only during the staging-phase R*Tree build (when the imported file is v1).

### R4 — Companion generator schema (data-contract v1 + v2)

The generator's `atak-tw-address-generator/scripts/ingest_tgos_csv.py` `SCHEMA_SQL` block defines three tables in every emitted `.sqlite`:

- `places` — `(id INTEGER PRIMARY KEY, lat REAL, lon REAL, display_name TEXT, display_name_halfwidth TEXT, district_code TEXT, …)` with B-tree indexes on administrative codes.
- `places_fts` — FTS5 mirror over `display_name` / `display_name_halfwidth` for future forward-search; **not** used by feature 004.
- `metadata` — key/value table; mandatory keys `schema_version`, `source`, `county`, `data_date`; optional `csv_sha256`, `csv_path`, `crs`, `inserted`, etc.

**v1** (morning of 2026-05-24) ships the three tables above; **v2** (evening of 2026-05-24) adds a pre-built `places_rtree` virtual table. The plugin accepts `schema_version ∈ [1, 2]`. When v1 is imported the plugin builds the R*Tree itself in the staging directory (one-shot, ~30–45 s for Taichung); when v2 is imported the `CREATE VIRTUAL TABLE IF NOT EXISTS` + `WHERE NOT EXISTS` guards are no-ops.

### R5 — Reverse-lookup algorithm (bbox → haversine refine)

Two-stage query:

1. Cos-latitude-corrected bounding box query against `places_rtree` for a default 500 m radius: `min_lat ≤ lat+Δlat AND max_lat ≥ lat-Δlat AND min_lon ≤ lon+Δlon AND max_lon ≥ lon-Δlon`. JOIN to `places` on `id`.
2. Haversine refine on the candidate set (typically 10²–10³ rows in dense urban Taiwan) — return the closest record under the radius. Zero candidates → `AddressLookupResult.empty()`.

The R*Tree makes stage 1 `O(log n + k)`; stage 2 on k≈10³ is microseconds. The widget renders `display_name` (fullwidth form, e.g. `台中市西區美村路一段 600 號`); `display_name_halfwidth` is reserved for future forward-search.

### R6 — DropDownReceiver page pattern (`DropDownReceiver`)

`com.atakmap.android.dropdown.DropDownReceiver` is the same base class feature 002's GoTo input page (`TwCoordGotoReceiver`) extends. Confirmed via `javap` to expose `setRetain(boolean)`, `setOnStateListener(...)`, `showDropDown(View, ...)`, `closeDropDown()`. The `OfflineAddressReceiver` page renders two states: **State A** (no dataset — empty-state text + Import button) and **State B** (dataset active — metadata fields + Replace / Remove buttons). State transitions are driven by `AddressBundleImporter.activeOrNull()` (R10).

### R7 — Widget integration (`TextWidget` sibling rows)

The existing readout (`TwCoordWidget`) is built from `com.atakmap.android.widgets.TextWidget` per anchor (BOTTOM_LEFT for MAP, BOTTOM_RIGHT for ME, TOP_RIGHT for TGT). Feature 004 adds three sibling `TextWidget` instances — `mapAddrRow`, `meAddrRow`, `targetAddrRow` — at the same anchors, inserted via `addWidget(...)` immediately after each existing row so the address text reads as a second line under the coordinate. Per-row visibility is bound to `setVisible(prefEnabled && datasetActive)`. The widget colour is `@color/address_row_text` (muted neutral `#FFBBBBBB`) so the address row has lower visual weight than the coordinate row.

`TwCoordWidget.renderAddresses(...)` is wrapped internally per Constitution VI; the existing 3-row `render(...)` path is left exactly as it was — this addition is purely additive (no behavioural change for operators who never enable a toggle).

### R8 — SAF (Storage Access Framework) file picker

`androidx.activity.result.contract.ActivityResultContracts.OpenDocument` plus `androidx.activity.result.ActivityResultLauncher` is the modern, scoped-storage-compliant file picker. The plugin's `OfflineAddressFilePickerActivity` (a thin trampoline Activity, not a DropDownReceiver — SAF requires an `Activity` context for the launcher) opens the picker with `mimeType = "*/*"`. The returned `Uri` is opened via `ContentResolver.openInputStream(uri)` — no `File` path is ever materialised on disk before staging.

### R9 — Threading model (single-thread debouncing executor)

A single-thread `ScheduledExecutorService` per concern:

- **Import**: `Executors.newSingleThreadExecutor` owned by `TwCoordMapComponent`, named `twcoord-address-import`. One-shot task per import.
- **Reverse-lookup**: `Executors.newSingleThreadScheduledExecutor` owned by `AddressSubsystem`, named `twcoord-address-lookup`. Per-row debounce 250 ms; cancel-and-reschedule on new coordinates.

`SelfMarkerSubscriber` (feature 001) uses the same pattern; the new executors follow that precedent. UI updates post back to the main thread via `MapView.post(...)` / `Handler(Looper.getMainLooper())`.

### R10 — Atomic dataset activation (`Files.move` ATOMIC_MOVE)

Three-phase activation:

1. Stream-copy operator-picked `.sqlite` from `InputStream` to `tools/twcoord/offline-address/.staging-<UUID>/places.sqlite`, hashing with `MessageDigest.getInstance("SHA-256")` during the copy. Validate `metadata` + `places` schemas (R4) read-only.
2. If `places_rtree` is absent (v1 file), open the staged file read-write and build the R*Tree (`CREATE VIRTUAL TABLE IF NOT EXISTS places_rtree USING rtree(id, min_lat, max_lat, min_lon, max_lon); INSERT INTO places_rtree(...) SELECT id, lat, lat, lon, lon FROM places; ANALYZE places_rtree;`). Close. Write `imported.manifest.txt` (R11) into the staging dir.
3. Rename any pre-existing `active/` aside as `active-old-<ts>/`, then `Files.move(stagingDir, activeDir, ATOMIC_MOVE, REPLACE_EXISTING)`. The post-success deletion of `active-old-*` is best-effort.

`activeOrNull()` reads the active dir on every page open + every settings refresh. NEVER throws (Constitution VI entry point) — returns `null` cleanly on any of: dir missing, `places.sqlite` missing, `imported.manifest.txt` missing or unparseable, `openDatabase` failure, metadata table empty. Each branch logs at `Log.w` with the specific reason (R12 audit row 9).

### R11 — Plugin-side manifest (`imported.manifest.txt` key=value)

The generator's in-DB `metadata` table is dataset provenance (when the CSV was harvested, which county, schema version). The plugin owns a separate companion file `active/imported.manifest.txt` (UTF-8, key=value, one per line) carrying the plugin's view of the dataset:

```text
imported_at=2026-05-24T15:30:00Z
file_sha256=<hex 64>          # SHA-256 of the .sqlite as imported (display-only; R13)
rtree_built=true              # whether the plugin built the R*Tree at import
plugin_schema_version=1       # plugin version that performed the import
```

Plain text key=value (no JSON dependency); six lines. The file is plugin-owned and not part of the generator's contract.

### R12 — Constitution VI entry-point audit

The feature adds ≥ 11 host-callable entry points; each is enumerated up front so the implementation pass can add the outer `try/catch (Throwable) { Log.w(...) }` without ad-hoc discovery. (The table is reproduced in `research.md §R10`; final compliance audit is T056.)

### R13 — SHA-256 (display-only; not gating)

The importer pipes the `InputStream → staging file` bytes through `MessageDigest.getInstance("SHA-256")`. The digest is recorded in `imported.manifest.txt` (R11). The import is **not** gated on a hash compare because the generator publishes no authoritative `.sha256` sidecar to compare against — the in-DB `metadata.csv_sha256` is the hash of the *source CSV*, not the `.sqlite`. The file hash gives the operator a reproducibility check ("two imports of the same file produced the same hash") without inventing a security theatre.

### R14 — Settings UI (`SwitchPreference` + status row)

`android.preference.SwitchPreference` is the platform-native control for booleans; `android.preference.Preference` carries the click-target status row. Per Clarifications Session 2026-05-24 Q2, the three per-row toggles are **flat siblings, not nested under a master switch**. The status row's summary is one of three states (none / hint / `Active: <county> · <data_date>`); tapping it broadcasts `ACTION_SHOW_OFFLINE_ADDRESS` to open the page. Constitution VI rule applies — the click lambda body is wrapped.

### R15 — Coverage-gap honesty rule

A successful lookup with zero candidates within the 500 m radius produces `AddressRowState.EmptyState` ("No address nearby"). The same state covers both **out-of-region** points (e.g. Taipei when only Taichung is imported) and **in-region-but-unmapped** points (small reservoir, industrial yard, ocean). Distinguishing the two would require shipping county-polygon metadata in the bundle, which the generator does not produce. SC-006 ≥ 95 % non-empty resolves already accepts the conflation.

## Decisions (D1–D14)

The decisions mirror R1–R15 one-to-one — research.md is the working document, this ADR is the durable artefact. Each decision below cites the corresponding research-doc section so anyone diffing the two can see they agree.

**D1 — Register the Tools-menu entry via `AbstractPluginTool`** (R1). Matches feature 001 / 002. Action string `com.atakmap.android.twcoord.SHOW_OFFLINE_ADDRESS`.

**D2 — Resolve filesystem paths through `FileSystemUtils.getItem("tools/twcoord/offline-address/...")`** (R2). The plugin never embeds `/sdcard/atak` as a literal. Same root as feature 001/002 (`tools/twcoord/`) so all plugin-owned files live under one inspectable subtree.

**D3 — Platform SQLite + R*Tree, built at plugin import for v1 / pre-shipped by generator for v2** (R3 + R4). Accept `schema_version ∈ [1, 2]`. The plugin builds the R*Tree only when absent so re-imports of the same file are idempotent (and v2 imports skip the build).

**D4 — Two-stage bbox + haversine reverse lookup with a 500 m default radius** (R5). The radius is small enough to bound the candidate set in dense urban Taiwan and large enough to catch the nearest house-number in suburban areas.

**D5 — Single-county active dataset** (Spec Assumption §4). The on-disk layout uses `tools/twcoord/offline-address/active/places.sqlite` (singular). Multi-county / multi-source consumption is feature 005 territory.

**D6 — Offline Address page as a DropDownReceiver** (R6). Same pattern as `TwCoordGotoReceiver` (feature 002). Two visual states A / B driven by `activeOrNull()`.

**D7 — Address row = sibling `TextWidget` per anchor, muted-neutral colour, per-row visibility gate** (R7). Purely additive; the existing 3-row render path is unchanged.

**D8 — SAF `ActivityResultContracts.OpenDocument` for the operator's file picker** (R8). Trampoline through `OfflineAddressFilePickerActivity` because SAF requires an `Activity` context.

**D9 — Two single-thread executors (import + lookup), 250 ms per-row debounce** (R9). Cancel-and-reschedule on the lookup side; one-shot on the import side.

**D10 — Three-phase atomic activation: staging → validate → R*Tree → rename + ATOMIC_MOVE** (R10). A crash during phase 1 leaves the previous `active/` untouched; the rename window is microseconds on ext4.

**D11 — Plugin-side `imported.manifest.txt` (key=value, UTF-8)** (R11). Five lines; carries plugin-view provenance distinct from the generator's in-DB `metadata` table.

**D12 — Constitution VI entry-point audit** (R12). 11 entry points enumerated up front; final pass in T056.

**D13 — SHA-256 display-only (computed during the import copy, not gating)** (R13).

**D14 — Three flat `SwitchPreference` toggles + status `Preference` row + click-broadcast** (R14). Per Clarifications Session 2026-05-24 Q2. Status summary surfaces dataset presence at-a-glance.

## Alternatives considered

- **Bundle a custom SQLite JNI (`requery/sqlite-android` etc.)** — rejected. ~3 MB APK growth + CVE surface; platform SQLite is feature-complete (R3 alternatives).
- **Hard-code `/sdcard/atak/tools/twcoord/...`** — rejected. The legacy Address Plugin's brittleness on Android 11+ is the documented anti-pattern (R2 alternatives).
- **Build the R*Tree at every plugin restart** — rejected. ~30–45 s cold-start cost; the one-shot build at import time is strictly more conservative (R3 alternatives).
- **Refuse to import files without `places_rtree` pre-built** — rejected. Would block the entire feature on a generator-side change. The plugin builds it as a fallback (R5 alternatives).
- **Replicate `metadata` in an external `.manifest.txt` shipped alongside the `.sqlite`** — rejected. Two copies of the same truth invite drift (R5 alternatives).
- **Concatenate the address into the existing coordinate row's text** — rejected. The existing row is colour-tagged by state; mixing in an address would force the operator to read past the prefix. Separate row keeps the existing row visually identical (R7 alternatives).
- **Single shared address row at the bottom of the screen** — rejected. Spec FR-019 requires per-row address text under each enabled coordinate row.
- **Master SwitchPreference + nested 3-row sub-screen** — rejected. Operator explicitly clarified (Session 2026-05-24 Q2) that flat siblings are the right shape; a master switch is one extra step for zero information gain.
- **Gate the import on SHA-256 match against `metadata.csv_sha256`** — rejected. That hash is of the *source CSV*, not the `.sqlite` — comparing them would be a category error (R13 alternatives).
- **`Files.copy` + `delete`** for activation — rejected. Not atomic; a crash mid-copy corrupts the active dir (R10 alternatives).
- **Multi-source consumption (TGOS + OSM + townships polygon-in + roads nearest-road)** — explicitly deferred to feature 005. The generator already publishes the relevant inputs (`places-osm.sqlite`, `tw-central-full.zip`, etc.); spec 004 commits to single-county bare-sqlite (Spec Assumption §1 + Clarifications Session 2026-05-24 evening).

## Consequences

**Positive:**

- Zero new third-party dependencies. Platform SQLite + AndroidX SAF + java.nio.file.Files + java.security.MessageDigest cover every code path. The APK gains nothing beyond the new Java + XML + ~43 string keys.
- Single-county bare-sqlite consumption matches what the generator emits today, so the plugin ships before the generator's `.zip` bundle / sidecar manifest / multi-source layout are stable. The deferred items (feature 005 backlog) can ship without re-litigating Phase 0 here.
- Address row integration is purely additive: every default-off toggle means an upgrade to v1.1.0 is visually zero-change for operators who never opt in. The legacy 3-row readout's render path is unchanged.
- Three flat toggles + one status row mean every Settings interaction is one-tap. No nested dialogs, no master switch to disable accidentally.
- ATOMIC_MOVE activation means a crash during import never corrupts a previously-active dataset.
- The plugin builds the R*Tree only when v1 is imported, so v2's pre-shipped index means single-digit-second imports going forward without any code change to the plugin.

**Negative:**

- Reverse lookup is housenumber-nearest (TGOS data is registry-of-house-numbers). The widget shows the nearest house number, not the road; if the nearest house is 30 m away across a road, that house is what the operator sees. This is honest about the data, but is a different mental model than Google-style reverse geocoding which favours road names. The empty-state row text ("No address nearby") is the safety valve when no record is within 500 m.
- Active-dataset size is large (~650–850 MB for Taichung after R*Tree build for v1; v2 ships pre-built and may be slightly smaller because of generator-side VACUUM). Operators with limited internal storage may need to remove the dataset to free space; the Offline Address page's Remove button covers that.
- The plugin's import wall-clock for v1 is 30–45 s (the R*Tree build dominates). v2 reduces this to single-digit seconds. The progress UI surfaces stage-by-stage so the operator does not interpret the build as a hang.
- Single-county active dataset means an operator who needs Taichung + Changhua coverage today imports one at a time. Multi-county is feature 005.

## Links

- **SDK classes audited** (signatures via `javap -public` on `ATAK-CIV-5.7.0.3-SDK/main.jar`; upstream-source permalinks point at `TAK-Product-Center/atak-civ` `main` for cross-checking implementation bodies — see the disclaimer in [SDK reconnaissance](#sdk-reconnaissance--what-atak--android--the-companion-generator-already-provide) about SDK-jar vs upstream-source authority):
  - `com.atak.plugins.impl.AbstractPluginTool` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/plugins/AbstractPluginTool.java) (template repo; the constructor signature is documented in `ATAK_Plugin_Development_Guide.pdf` §6).
  - `com.atakmap.coremap.filesystem.FileSystemUtils` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/takEngine/src/main/java/com/atakmap/coremap/filesystem/FileSystemUtils.java)
  - `com.atakmap.android.dropdown.DropDownReceiver` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/dropdown/DropDownReceiver.java)
  - `com.atakmap.android.widgets.TextWidget` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/widgets/TextWidget.java)
  - `com.atakmap.coremap.log.Log` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/takEngine/src/main/java/com/atakmap/coremap/log/Log.java)
  - `com.atakmap.android.ipc.AtakBroadcast` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/ipc/AtakBroadcast.java)
- **Android-platform classes audited** (signatures via `javap -public` on the `android.jar` shipped with `compileSdk = 34`):
  - `android.database.sqlite.SQLiteDatabase` — <https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase>
  - `android.preference.SwitchPreference` — <https://developer.android.com/reference/android/preference/SwitchPreference>
  - `androidx.activity.result.contract.ActivityResultContracts.OpenDocument` — <https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument>
- **Companion generator source**: `atak-tw-address-generator/scripts/ingest_tgos_csv.py` `SCHEMA_SQL` block (v1) + `docs/data-contract.md` v2 spec; sibling repo at `C:\Users\hhhnr\source\tak\atak_vns_offline_routing\atak-tw-address-generator`. The plugin's `MAX_SUPPORTED_SCHEMA_VERSION = 2` is the version pin (see `AddressBundleImporter.java`).
- **Reference implementation in the SDK**: `samples/helloworld/.../HelloWorldDropDownReceiver.java` is the canonical DropDownReceiver shape; both feature 002 (`TwCoordGotoReceiver`) and feature 004 (`OfflineAddressReceiver`) mirror its lifecycle.
- **SQLite R*Tree extension reference**: <https://www.sqlite.org/rtree.html>
- **Haversine formula reference**: <https://en.wikipedia.org/wiki/Haversine_formula> (no external lib needed; the formula is six lines of Java in `AddressResolver`).
- **Address Plugin study notes**: `docs/research/address-atak-plugin-study.md` § 4.1 ("R*Tree bounding-box query then refine") for the same recipe in a production plugin; § 5 for the scoped-storage anti-pattern that motivated D2.
- **Prior ADRs invoked**:
  - ADR-0010 / 0011 — same SDK-reconnaissance-first discipline applied to feature 003 (Custom Marker Icon picker).
  - ADR-0007 — "javap the SDK before deciding" rule that this ADR follows.
  - ADR-0012 — `tw-icon` asset pipeline; the new `ic_offline_address.xml` drawable is a placeholder (T001) that T058 replaces with a design-lead-approved final.
- **Constitution principles invoked**:
  - I (formatter) — purely additive Java code passes through `:app:spotlessApply` (T059).
  - III (UX consistency) — address-row visual weight, fullwidth-punctuation in zh-rTW prose, halfwidth inside identifiers / paths (T054 + T055).
  - V (this ADR) — pre-implementation reconnaissance documented per the SDK-investigation requirement.
  - VI (host-process isolation) — D12 entry-point audit; final audit in T056 lands in ADR-0015.
- **Operator clarifications verbatim** (Clarifications Session 2026-05-24, recorded in `specs/004-offline-address/spec.md` §Clarifications):
  - Q1: "Single-county active dataset is fine for v1; multi-county is a follow-up."
  - Q2: "三個 toggle 之上不要有一個 master switch" (no master switch above the three toggles).
  - Q3 (evening): "OSM 是另一個資料源；先做 TGOS 單郡，OSM / 多源放 feature 005" (OSM is a separate source; ship single-county TGOS first, OSM / multi-source for feature 005).
- **Upstream URL note**: as of 2026-05-24 the active ATAK-CIV public source mirror is `github.com/TAK-Product-Center/atak-civ` (default branch `main`). The previously-referenced `deptofdefense/AndroidTacticalAssaultKit-CIV` mirror is stale. This ADR's permalinks pin to `main` rather than to a tag because the SDK jar (5.7.0.3) is the authoritative build-time contract; the upstream link is for human cross-checking only.
