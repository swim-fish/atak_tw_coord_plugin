# Feature Specification: Offline Address Lookup — Import, Display, and Settings Toggle

**Feature Branch**: `004-offline-address`

**Created**: 2026-05-24

**Status**: Draft

**Input**: User description: "配合 vns-offline-routing-study 專案產出的離線地址資料、新增一個可以匯入離線地址資料的功能、tools 按鈕新增一個 Offline Address、可以選擇匯入的檔案、借鏡 GoTAK Address Plugin 使用離線的地址資料、在目前的座標顯示多一個 row 可以同時顯示地址的功能、Settings 裡面可以選擇啟用顯示地址"

## Context

Features 001 (`specs/001-tw-coord-display/`) and 002 (`specs/002-tw-coord-goto/`) shipped the on-map coordinate readout (`TwCoordWidget`) and the GoTo input page. Both work fully offline because the maths is purely local. What they do **not** show is any human-readable place text — operators see a Taipower grid or a TWD97 northing/easting, never "新北市板橋區文化路一段 100 號".

Two upstream studies recorded in `docs/research/` (the GoTAK Address Plugin study and the VNS Offline Routing Generator study) sketch the architecture for adding offline address text without giving up the offline / zero-telemetry posture this plugin has held since v1.0.0:

- The **VNS model** keeps the data pipeline outside the plugin: a companion data-generator project produces a versioned bundle (region + manifest + timestamp), the operator side-loads it, and the plugin only reads a fixed folder layout. No in-plugin downloader, no in-plugin network access.
- The **GoTAK Address Plugin** demonstrates the runtime side: an SQLite database with full-text + spatial indices supports fast reverse-lookup ("given a WGS-84 point, return the nearest meaningful address text") without contacting the network.

This feature combines both: the operator side-loads an offline-address bundle through a new **Tools → Offline Address** entry, opts in via a Settings toggle, and from then on the existing coordinate readout shows one additional row carrying the address text resolved from the user's current location. When no bundle is installed, or the toggle is off, the plugin behaves exactly as it does today (no address row, no overhead, no errors).

This feature explicitly does **not** ship the data generator itself — only the plugin-side import and display flow. The companion generator is tracked separately (see Assumptions §1) and is expected to follow the VNS-study supply-chain discipline (pinned base image, SHA-256 manifest, version-stamped output).

## Clarifications

### Session 2026-05-24

- Q: What file shape does the operator import — a single SQLite file, a zipped bundle with a manifest, or a pre-extracted directory copied via ADB?
  → A (initial, 2026-05-24 morning): **Zipped bundle.** Picked from a `.zip` archive carrying the database plus an external `manifest.txt` and `timestamp`.
  → A (revised, 2026-05-24 afternoon, after reconnaissance of the companion generator project at `<ATAK_TW_ADDRESS_GENERATOR>`): **Bare `places-<county>.sqlite` file.** The generator produces one SQLite file per Taiwan county (e.g. `places-taichung.sqlite`, `places-changhua.sqlite`) with provenance recorded inside the database itself in a `metadata` table (`schema_version`, `county`, `data_date`, `csv_sha256`, `source`, `crs`, `inserted`, etc. — see [data-model.md](./data-model.md#1-metadata-table-from-generator)). Operators import a `.sqlite` file directly; the plugin reads metadata from the in-DB table, computes the file's SHA-256 at import for plugin-side display (no external manifest exists to gate import against), and atomically activates the new dataset. Future generator versions may ship a consolidated multi-county `.zip` bundle (the generator's `all` subcommand is a documented TODO); the plugin's importer is designed so that supporting `.zip` later is a small extension, not a refactor.
- Q: When the address-display preference is on, which of the existing coordinate rows (ME, TGT, MAP) gets the additional address row underneath?
  → A: **All three are eligible, but each is controlled by its own per-row toggle in Settings.** The plugin exposes three independent booleans — *Show address for self-location (ME)*, *Show address for target (TGT)*, *Show address for map-centre (MAP)* — each defaulting to **off** on a fresh install. Operators upgrading see no visual change until they explicitly enable at least one row. If all three toggles are off, the address subsystem stays dormant (no lookups, no DB open). The previously-described "single master toggle" is replaced by these three; there is no master switch in addition.

### Session 2026-05-24 (evening)

- Q: The companion generator has shipped `.zip` bundles (per-county + `base.zip` containing `townships`/`roads`/`places-osm` + `tw-central-full.zip` consolidating all five sqlite files), plus `.manifest.txt` sidecars. Does v1 of the plugin need to handle these now?
  → A: **No — v1 still imports bare `.sqlite` only.** The generator continues to ship the bare files alongside the `.zip` bundles, so operators can import directly from `output/places-<county>.sqlite` without unzipping. To make the failure mode crystal clear when an operator picks a `.zip` by mistake, the importer adds a ZIP-magic-bytes pre-check that returns a distinct `IS_A_ZIP` failure reason with a one-line "extract the .sqlite first" hint surfaced inline on the Offline Address page. Full `.zip` unpacking, sidecar-`*.manifest.txt` parsing, multi-source reverse lookup (TGOS + OSM `places-osm.sqlite`), and the polygon-in / nearest-road extensions backed by `townships.sqlite` / `roads.sqlite` are all deferred to a follow-up feature spec (tentative `005-offline-address-multi-source`). Spec 004 ships only the single-county bare-`.sqlite` happy path.

### Session 2026-05-24 (late evening)

- Q: The generator now publishes `docs/data-contract.md` v2 that bumps `metadata.schema_version` from `'1'` to `'2'` and pre-builds `places_rtree` directly in the `.sqlite` (so plugins no longer need to build it at import). Does v1 of this spec need to react?
  → A: **Yes, narrowly — accept both v1 and v2.** The importer's `pinnedSchemaVersion` constructor param is renamed to `maxSupportedSchemaVersion` and the validation now accepts any `schema_version` in `[1, maxSupportedSchemaVersion]` (production passes `2`). The R*Tree build is already gated on `CREATE … IF NOT EXISTS` + `WHERE NOT EXISTS`, so v2 imports skip the 30–45 s build entirely and `imported.manifest.rtreeBuilt == false`. SC-003 effectively tightens to single-digit seconds when the generator ships v2 files — the placeholder 180 s budget stays as a worst-case for v1 files (R*Tree-less Changhua) until T057 measurement confirms the actual reduction. Other v2 additions (new optional `place_type` column on `places`, nullable `district_code` / `county` / `township` for OSM landmarks, new `region` / `bbox` metadata keys for OSM) are tolerated by the plugin's presence-based validation and require no further code changes; OSM-source consumption itself stays deferred to feature 005.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Side-load an offline-address bundle through Tools → Offline Address (Priority: P1)

A field operator has obtained an offline-address bundle for Taiwan (built externally by the companion data-generator project and copied onto the device). They open ATAK's Tools menu, tap the new **Offline Address** entry, are shown a file picker, select the bundle, and see the plugin acknowledge a successful import. The bundle is now the active dataset on this device.

**Why this priority**: This is the only way the address-display feature can become usable. Without an import path, every downstream story is blocked. Shipping just this story already delivers a coherent piece of value (operators can install / replace / inspect the dataset) even if they choose to never enable the on-map display.

**Independent Test**: With the plugin freshly installed and no dataset present, place a sample bundle file on the device's storage. Open ATAK → Tools → **Offline Address**. Tap **Import**. Pick the file. The plugin MUST report a successful import (name of the dataset, region label, and data-date visible on the Offline Address page) within a few seconds. Re-opening the Offline Address page later MUST still show the same dataset listed as active.

**Acceptance Scenarios**:

1. **Given** the Tools menu is open and the plugin is installed, **When** the operator looks at the menu, **Then** an **Offline Address** entry MUST be present alongside the plugin's existing entries.
2. **Given** the Offline Address page is open and no dataset has yet been imported, **When** the operator views the page, **Then** the page MUST show an empty-state ("No address dataset installed") and an **Import** affordance.
3. **Given** the operator taps **Import**, **When** the system file picker appears, **Then** the operator MUST be able to navigate to and select an offline-address bundle file.
4. **Given** the operator selects a valid bundle file, **When** the import completes, **Then** the page MUST show: the dataset's county label, the dataset's data-date, the source identifier and row count from the in-DB `metadata` table, the plugin-computed file SHA-256 and import timestamp, and a **Replace…** / **Remove** affordance.
5. **Given** an active dataset is already present, **When** the operator imports a second bundle, **Then** the new bundle MUST replace the previous one (after a confirmation prompt), and the previous dataset's files MUST be removed from the plugin's storage.

---

### User Story 2 — Address row appears under the existing coordinate readout (Priority: P1)

The operator has imported a Taiwan dataset and enabled the Settings toggle. They open the map and look at the existing coordinate readout. Where before they saw only the Taipower / TWD97 / TWD67 lines, they now see one additional row showing the address text (e.g. "新北市板橋區文化路一段 100 號") for the same point the existing rows describe. As they pan, drive, or move the map, the address text refreshes alongside the coordinate text.

**Why this priority**: This is the headline value the user asked for — the whole purpose of importing the dataset. P1 with US1 because the value chain is only complete when both ship.

**Independent Test**: With a valid dataset imported (US1 completed) and the Settings toggle enabled (US3), open ATAK and look at the coordinate readout. The widget MUST show one additional row under the existing coordinate rows containing address text for the same point. Moving the map (or moving the device, depending on which coordinate row the address tracks — see Clarifications) MUST cause the address row to refresh within a small bounded delay (target: under 1 second after movement settles).

**Acceptance Scenarios**:

1. **Given** an address dataset is imported and the Settings toggle is enabled, **When** the operator looks at the coordinate readout, **Then** an additional row labelled with the address text MUST appear underneath the existing coordinate rows.
2. **Given** the address row is showing text, **When** the underlying coordinate moves, **Then** the address text MUST recompute and update within 1 second after the coordinate has stabilised.
3. **Given** the underlying coordinate is over a location for which the dataset has no nearby address record (e.g. open ocean, sparsely-mapped terrain), **When** the address row would otherwise render, **Then** the address row MUST show a polite empty-state ("No address nearby" or equivalent) rather than appearing blank or showing a stale value.
4. **Given** the address row is showing text and the operator changes the active coordinate-format preference (e.g. via the Tools-menu toggle that cycles TPC → TWD97 → TWD67), **When** the readout re-renders in the new format, **Then** the address row MUST remain stable (it depends on the underlying lat/lon, not on which projection the operator is currently viewing).

---

### User Story 3 — Per-row Settings toggles enable / disable the address row independently for ME, TGT, MAP (Priority: P2)

The operator opens the plugin's Settings page. There is a new group of three toggles — **Show address for self-location (ME)**, **Show address for target (TGT)**, and **Show address for map-centre (MAP)** — each defaulting to **off**. Turning any single toggle on (with a dataset installed) makes the address row appear underneath that one coordinate row only. Turning multiple toggles on shows the address row under each enabled coordinate row independently. With all three off, the coordinate readout looks exactly like it does today and the address subsystem stays dormant.

**Why this priority**: The address row is opt-in by default — operators upgrading from earlier versions see no visual change until they explicitly enable at least one toggle. Per-row control matters because the three coordinate rows answer different questions (where am I / where is the target / where is the cursor) and operators commonly want only one or two of them annotated. P2 because the toggles are logically required for US2 to land, but the toggles themselves deliver no value without the row being implemented; bundling them with US2's release is the simplest path.

**Independent Test**: Install a dataset (US1). Open Settings, find the three new toggles. Turn ME on only → the address row appears only underneath the ME coordinate row, not under TGT or MAP. Turn MAP on additionally → both ME and MAP gain an address row; TGT remains without one. Turn all three off → no address row appears anywhere. With all toggles off, no address-lookup work runs in the background (verified indirectly by observing no extra battery / CPU footprint compared to today's behaviour).

**Acceptance Scenarios**:

1. **Given** the plugin's Settings page is open, **When** the operator looks at it, **Then** three toggles — **Show address for self-location (ME)**, **Show address for target (TGT)**, **Show address for map-centre (MAP)** (or equivalent translated text) — MUST be present, each defaulting to **off** on a fresh install.
2. **Given** all three toggles are off, **When** the operator looks at the coordinate readout, **Then** no address row MUST appear under any of ME / TGT / MAP regardless of whether a dataset is imported.
3. **Given** exactly one toggle is on AND a dataset is imported, **When** the operator looks at the coordinate readout, **Then** the address row MUST appear only under that one coordinate row — the other two rows MUST remain unchanged.
4. **Given** two or three toggles are on AND a dataset is imported, **When** the operator looks at the coordinate readout, **Then** an address row MUST appear under each enabled coordinate row independently (each row computed for its own underlying lat/lon).
5. **Given** any toggle is on AND no dataset is imported, **When** the operator looks at the Settings toggles, **Then** a single-line hint MUST point them to **Tools → Offline Address** to install one. The coordinate readout MUST NOT show a broken address row in this state — the rows that would otherwise carry an address simply omit it.

---

### User Story 4 — Graceful behaviour on missing, corrupt, or out-of-region data (Priority: P3)

The operator's dataset has gaps: they are in Kinmen but installed a bundle that only covers Taiwan main island, or their bundle file has been partially corrupted, or the on-disk dataset directory has been manually deleted. The plugin notices the absent / unhealthy data, surfaces a hint exactly once where the operator will see it (Tools → Offline Address page and/or Settings toggle row), and downgrades to the pre-feature behaviour. The plugin MUST NOT crash, MUST NOT block the existing coordinate readout, and MUST NOT spam toasts.

**Why this priority**: A correctness/robustness story. The happy paths in US1–US3 deliver the everyday value; this story is about not losing the existing widget when something downstream goes wrong.

**Independent Test**: With a dataset imported, delete the dataset's on-disk files via ADB (or any other out-of-band method). Re-open the map. The coordinate readout MUST continue to show its existing rows. Opening **Tools → Offline Address** MUST show the empty-state again ("No address dataset installed"), not a phantom "active dataset" entry. Re-importing the same bundle MUST recover the address row without a plugin restart.

**Acceptance Scenarios**:

1. **Given** an imported dataset's on-disk files have disappeared between sessions, **When** the plugin opens, **Then** Tools → Offline Address MUST show the empty-state and the address row MUST be omitted from the coordinate readout without any crash, toast spam, or modal dialog.
2. **Given** the underlying coordinate is outside the dataset's covered region, **When** the address row would otherwise render, **Then** it MUST show the same "No address nearby" empty-state as for a within-region but unmapped point.
3. **Given** the operator attempts to import a file that is not a valid generator output (not openable as SQLite, missing `metadata` / `places` table, unrecognised `schema_version`), **When** the import runs, **Then** the page MUST surface a clear error message identifying which check failed (e.g. "Database not readable", "Unsupported schema version: expected 1, got 2") and MUST NOT modify the active dataset.
4. **Given** an import is in progress, **When** the operator backgrounds the app or rotates the device, **Then** the import MUST either complete in the background or fail cleanly with no half-written dataset on disk (atomic activation).

---

### Edge Cases

- **Very large file**: The actual generator output for Taichung is ~500–600 MB bare; the plugin's R*Tree build adds another ~150–250 MB on top. Files up to **~1 GB** MUST import without OOM and without blocking the UI thread (target reference device same as features 001–003). The earlier ~30–80 MB size estimate (carried over from the OSM-based Address Plugin study) does not apply — TGOS data is house-number-granular, an order of magnitude denser.
- **First reverse-lookup latency**: Cold-start cost (database open, schema check, R*Tree warmup) is paid lazily — the very first address row may take longer than the steady-state 1-second target; subsequent lookups MUST hit the 1-second target.
- **Address is ambiguous** (e.g. an isolated lat/lon equidistant from two streets): The widget MUST pick one deterministically (nearest by haversine, ties broken by the shorter address text or lower record id — implementation may choose, but the choice MUST be stable across redraws to avoid flicker).
- **Operator imports while the on-map readout is visible**: The address row MUST start populating within one refresh cycle after import completes; the operator does not need to close and re-open the map.
- **Operator removes the active dataset via Tools → Offline Address while the readout is showing an address row**: The address row MUST disappear on the next refresh; no crash, no leftover stale text.
- **Operator changes any of the three Settings toggles while the readout is visible**: The corresponding row's address MUST appear / disappear within one refresh cycle; the other rows' state MUST remain unchanged.
- **Operator imports a `.sqlite` file with a `schema_version` value the plugin does not recognise**: The import MUST fail with a clear "Unsupported schema version: expected <X>, got <Y>" error and MUST NOT activate the file. (The plugin pins one known `schema_version` per release — `1` for v1 — so a future generator with `schema_version=2` is cleanly rejected by an older plugin build.)
- **Two writers race**: If the operator taps **Remove** at the same instant an address lookup is in flight, the lookup MUST either complete on the pre-removal dataset or be cancelled cleanly — never read partially-deleted files. The end state MUST converge to "no active dataset" within one refresh cycle.
- **Translated UI strings**: All visible strings introduced by this feature MUST be available in every locale the plugin already ships (English, Traditional Chinese, Japanese) and MUST honour the plugin's existing UI-language override.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: ATAK's Tools menu MUST display a new **Offline Address** entry alongside the plugin's existing entries. The label MUST be localised in every language the plugin already ships.
- **FR-002**: Tapping the **Offline Address** entry MUST open a plugin-owned page (drop-down or full screen, matching existing plugin pages' affordance) that shows: the active dataset's metadata if one is installed (region label, data-date, source identifier / hash), or an empty-state ("No address dataset installed") if not.
- **FR-003**: The Offline Address page MUST provide an **Import** affordance that opens the host platform's system file picker, scoped to SQLite files (MIME `application/octet-stream` or `application/x-sqlite3`; filename pattern `places-*.sqlite` is informational, not enforced). The operator picks the `.sqlite` file produced by the companion generator (`atak-tw-address-generator`); the plugin reads provenance from the file's in-DB `metadata` table (no external manifest is expected).
- **FR-004**: When the operator picks a candidate `.sqlite` file, the import flow MUST validate the file before activating it. Validation MUST at minimum confirm: (a) the file is openable as SQLite in read-only mode, (b) the `metadata` table exists and exposes the required keys `schema_version`, `county`, and `data_date` (with `schema_version` falling in the plugin-supported range — currently `[1, 2]` per the generator's [data-contract.md](https://path-to-generator-repo/docs/data-contract.md) v2 published 2026-05-24 evening), (c) the `places` table exists with the expected column set (see [data-model.md](./data-model.md)). The plugin MUST additionally compute the SHA-256 of the imported file at import time (recorded for plugin-side provenance display, **not** used to gate the import — there is no external manifest to compare against). Validation failures MUST leave the previously-active dataset (if any) untouched, and the page MUST surface which specific check failed.
- **FR-005**: A successful import MUST atomically activate the new dataset — at no point is the plugin reading from a partially-written file or a half-deleted previous dataset. If a previous dataset existed, its on-disk files MUST be removed after the new dataset is activated.
- **FR-006**: The Offline Address page MUST provide a **Remove** affordance that, on confirmation, deletes the active dataset's on-disk files and returns the page to the empty-state. The address row on the coordinate readout MUST disappear within one refresh cycle.
- **FR-007**: The plugin MUST store imported datasets under a stable, ATAK-managed directory (using ATAK's directory helpers, not a hard-coded `/sdcard/...` path), so the data survives plugin reinstalls in the same way other ATAK plugin data does.
- **FR-008**: The plugin Settings page MUST expose three independent boolean preferences — **Show address for self-location (ME)**, **Show address for target (TGT)**, **Show address for map-centre (MAP)** (or equivalent translated text) — each defaulting to **off** on a fresh install. Toggling any of the three MUST take effect on the existing coordinate readout within one refresh cycle. There MUST NOT be an additional "master" toggle layered on top of these three.
- **FR-009**: For each of the three preferences in FR-008 that is **on** AND a dataset is active, the coordinate readout MUST include one additional row carrying the address text resolved for that preference's underlying coordinate (the self-location lat/lon for ME, the target marker's lat/lon for TGT, the map-centre crosshair lat/lon for MAP). Each enabled row's address MUST be computed independently — they do not share a cached lookup. Rows whose corresponding preference is **off** MUST NOT display an address row.
- **FR-010**: When at least one of the three preferences in FR-008 is **on** AND no dataset is active, the Settings preferences group MUST surface a single-line hint pointing the operator to **Tools → Offline Address**. The coordinate readout MUST NOT show a broken address row in this state — the rows that would otherwise carry an address simply omit it.
- **FR-011**: When all three preferences in FR-008 are **off**, no address-lookup work MUST run in the background — the address subsystem stays dormant (no database open, no scheduled work). (Operators who don't use the feature pay zero runtime cost.)
- **FR-012**: Address lookups MUST be fully offline. The plugin MUST NOT contact the network for any address-related operation. (Inherits the zero-telemetry posture of features 001–003.)
- **FR-013**: When the underlying coordinate has no nearby address record in the active dataset (either out-of-region or in an unmapped area), the address row MUST show a polite empty-state ("No address nearby" or equivalent translated text) — not a blank row, not a stale value, not the last successful lookup.
- **FR-014**: Address text MUST update within 1 second of the underlying coordinate stabilising after movement. Per-lookup CPU work MUST be bounded so that scrolling / panning the map does not stutter even when the operator is moving the cursor continuously.
- **FR-015**: All address-lookup operations MUST run off the UI thread; failures (database read error, decoding error, out-of-bounds query) MUST be caught, logged, and surfaced as the empty-state row, never as a toast / dialog / crash.
- **FR-016**: The plugin MUST tolerate the active dataset's on-disk files disappearing or becoming unreadable between sessions. On the next coordinate refresh after detecting the loss, the address row MUST be omitted and Tools → Offline Address MUST recover to the empty-state. No plugin restart MUST be required.
- **FR-017**: Importing a new dataset while one is already active MUST require an explicit confirmation step before the previous dataset is replaced (avoid accidental data loss when the operator misclicks).
- **FR-018**: All visible strings introduced by this feature MUST be available in English, Traditional Chinese, and Japanese, and MUST honour the plugin's existing UI-language override pathway used by features 001–003.
- **FR-019**: The address row MUST be visually consistent with the existing coordinate rows (same font scale, padding, container chrome) so it reads as part of the same widget rather than an overlay.
- **FR-020**: The address row MUST NOT alter, displace, or otherwise interfere with the existing coordinate rows when the preference is on — it MUST occupy space beneath them, not in place of them.

### Key Entities *(include if feature involves data)*

- **Address dataset**: The active offline data slice. Identified by a region label, a data-date (e.g. the source OSM extract's date), and a manifest summarising provenance (source URL, build commit, SHA-256). At any time the plugin has zero or one active dataset.
- **Bundle file**: The operator-supplied file selected at import time. After successful validation, its contents become the new active dataset; the bundle file itself does not need to be retained.
- **Address record**: A single (lat, lon, address-text) triple in the dataset. The dataset contains many; the plugin's runtime query is "given a WGS-84 point and a small search radius, return the most relevant address record or nothing".
- **Address-display preferences**: Three independent boolean Settings toggles — one per coordinate row (ME, TGT, MAP). Persisted across sessions. Each defaults to **off** on a fresh install. The address subsystem is dormant when all three are off.
- **Address row state**: A derived UI state for the address row — one of {hidden, populated, empty-state}. Not persisted; recomputed on every readout refresh.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can complete the full **import a bundle → enable at least one row toggle → see the address row populate** flow in no more than **five taps** beyond opening the relevant pages (Tools → Offline Address → Import → pick file; Settings → tap one toggle; return to map). The interaction count for steady-state use (after first install) is zero — the address rows are always present once the preferences and dataset are in place.
- **SC-002**: Median time from "coordinate stabilises" to "address row updates" is under **1 second** on the project's reference device, measured over **100 consecutive map movements** while in Taiwan with a Taiwan-scale dataset installed. 95th percentile under **2 seconds**. When multiple per-row preferences are enabled, the slowest of the active rows MUST still meet this target.
- **SC-003**: Importing a single-county `.sqlite` file (Taichung-scale, ~1.3 M rows; generator output is ~500–600 MB bare, plugin's R*Tree build adds ~150–250 MB) completes in under **180 seconds** on the reference device, measured end-to-end (file picked → "Active" state shown on the Offline Address page), with progress visible to the operator throughout (no blank screen for more than 500 ms). Smaller counties (e.g. Changhua, ~0.5 M rows, ~170 MB bare) MUST complete proportionally faster. The 180 s budget is a placeholder pending T057 measurement; tighten the SC if the measured value comes in comfortably under.
- **SC-004**: With all three per-row preferences **off**, the plugin's measured background CPU and memory footprint MUST be indistinguishable from the same plugin build with this feature absent (no address-subsystem cost when the operator hasn't opted in).
- **SC-005**: After removing the active dataset's on-disk files out-of-band (simulating corruption or manual deletion), the next coordinate refresh MUST recover to the empty-state row within **2 seconds** and MUST NOT crash, toast-spam, or leave a stale address row visible.
- **SC-006**: Over **1000 consecutive address lookups** at varying coordinates inside the dataset's covered region, the plugin MUST resolve a non-empty address row in at least **95 %** of cases (the remaining 5 % being legitimately unmapped points such as small reservoirs / industrial zones). Zero crashes, zero "stale value" defects across the same run. Measurement procedure: see [quickstart.md § 6.5](./quickstart.md#65-sc-006--non-empty-resolve-rate-across-1000-scripted-lookups).
- **SC-007**: A fresh install with no bundle imported MUST present a coherent first-run experience — the operator opens Tools → Offline Address, sees a clear empty-state with the path to obtain a bundle, and the rest of the plugin (features 001–003) continues to behave exactly as before.

## Assumptions

1. **The companion data-generator is `atak-tw-address-generator`** (local sibling repo at `<ATAK_TW_ADDRESS_GENERATOR>`). It is a Docker-based pipeline that ingests TGOS (Taiwan government) address CSVs per county and emits one `places-<county>.sqlite` per run. Provenance is recorded inside the database in a `metadata` table (`schema_version`, `county`, `data_date`, `csv_sha256`, `crs`, `source`, `inserted`, etc.). As of 2026-05-24 the generator additionally ships: (a) `places-<county>.zip` per-county bundles, (b) `base.zip` carrying OSM-derived `townships.sqlite` + `roads.sqlite` + `places-osm.sqlite`, (c) consolidated `tw-central-full.zip` containing all five sqlite files; each `.zip` has a sidecar `*.manifest.txt` with ZIP SHA-256, per-file SHA-256, region bbox, and TGOS / OSM build metadata. **v1 of this spec still imports only the bare `.sqlite` files** — operators either pick `output/places-<county>.sqlite` directly or extract the `.zip` on the host first. Zip-bundle import, sidecar-manifest parsing, multi-source reverse lookup (`places-osm`), and `townships`/`roads`-backed extensions are deferred to a follow-up feature spec per Session 2026-05-24 (evening) Clarifications. This spec covers only the plugin-side import and display flow; operators acquire `.sqlite` files out-of-band (download + copy to device).
2. **The plugin imports the generator's `.sqlite` files unchanged**. The schema (`places`, `places_fts`, `metadata`) is the generator's `SCHEMA_SQL` ([data-model.md §1](./data-model.md)). The current generator schema does **not** include an R*Tree spatial index — the plugin builds one once at import time, in the same file, before atomic activation ([plan.md → research.md R3](./research.md#r3--reverse-lookup-spatial-index-rtree-built-at-plugin-import)). A future generator version that ships R*Tree directly is desirable and tracked as a generator-side enhancement; the plugin's import path detects this and skips the build step in that case.
3. **The dataset covers Taiwan, one county per file in v1**. Multi-region support (e.g. Japan, Korea) is out of scope. A consolidated multi-county Taiwan bundle is on the generator's `all` subcommand TODO list; when shipped, the plugin imports it the same way (just a larger file).
4. **A single active dataset at a time**. A library of datasets with an "active" switcher is out of scope; importing a new file replaces the previous one (with confirmation). Operators wanting Taichung + Changhua coverage simultaneously have to wait for the generator's consolidated bundle.
5. **The reverse-lookup is best-effort, not authoritative**. The address text shown is whatever the dataset records for the nearest point; the spec does not promise any specific Taiwan address standard (e.g. 鄉鎮市區 + 路段 + 號 format) — the bundle's content determines what the operator sees. Format normalisation is a generator concern, not a plugin concern.
6. **Reverse-lookup is location → text only**. Forward search (operator types an address, plugin pans the map) is explicitly out of scope. The GoTo page (feature 002) already covers coordinate input; address-based GoTo is a separate feature for a future iteration.
7. **The address text is informational, not actionable**. Tapping the address row does not initiate any further action (no "navigate to this address", no "share this address"). Operator interactions remain on the existing coordinate rows.
8. **The plugin inherits features 001–003's offline / zero-telemetry posture**. No network access is introduced. The plugin never downloads, never auto-updates, never phones home about the active dataset.
9. **Plugin storage uses ATAK's directory helpers**. The exact filesystem path (e.g. somewhere under ATAK's plugin-data root) is an implementation detail to be pinned at plan time; the spec only requires that the path survive plugin reinstall the way other ATAK plugin data does and that no hard-coded `/sdcard/...` literal appears in plugin code.
10. **Reference device** for SC-002 / SC-003 / SC-004 is whatever device the project's existing performance baselines target (consistent with features 001–003).
