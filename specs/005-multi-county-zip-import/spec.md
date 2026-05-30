# Feature Specification: Multi-County + ZIP Bundle Import

**Feature Branch**: `005-multi-county-zip-import`

**Created**: 2026-05-26

**Status**: Draft

**Input**: User description: "離線地址 支援 多的檔案 zip 格式; 可以看 C:\\Users\\hhhnr\\source\\tak\\atak_vns_offline_routing\\atak-tw-address-generator\\output 這個專案產的檔案; 優先使用 有 R*Tree 格式的檔案 地區可以個別更新; 可以參考那個專案如何產出離線地址檔案的"

## Context

Feature 004 (`specs/004-offline-address/`, shipped on branch `004-offline-address` through commit `7a75618`) added offline-address lookup with a **single active dataset**: the operator picks one `places-<county>.sqlite` at a time, the plugin atomically activates it at `tools/twcoord/offline-address/active/places.sqlite`, and reverse-lookup runs against that single SQLite. Spec 004 Assumption §4 explicitly committed to single-county scope and deferred multi-county to a follow-up. Spec 004 Assumption §1 (evening clarification) deferred ZIP import to a follow-up.

This feature delivers both deferred items at once. The companion data-generator (`atak-tw-address-generator`) **already ships a multi-county-friendly layout** — see its [data-contract.md §2](file:///c/Users/hhhnr/source/tak/atak_vns_offline_routing/atak-tw-address-generator/docs/data-contract.md) — which the plugin can now align to:

- Per-county ZIPs: `places-taichung.zip` (~81 MiB → 572 MiB sqlite), `places-changhua.zip` (~34 MiB → 196 MiB sqlite), each carrying one `places-<county>.sqlite` + a `timestamp.<county>` sidecar.
- Consolidated ZIP: `tw-central-full.zip` (~165 MiB) containing all 5 sqlite (`places-taichung` + `places-changhua` + `townships` + `roads` + `places-osm`) plus 3 timestamp sidecars.
- Generator's expected deployment dir: `/sdcard/atak/tools/twcoord/data/` (flat, no extra subdirectories; discovery is a `listFiles` glob on `places-.*\.sqlite`).

Feature 005 therefore:

1. Accepts ZIP files at the picker (replacing 004's friendly `IS_A_ZIP` rejection with actual extraction).
2. Accepts multiple bare `.sqlite` files in a single picker session (multi-select).
3. Maintains **independent active datasets per county** — each county replaces / removes independently, and a failed import of one county does not corrupt others.
4. Reverse-lookup queries every active county dataset and returns the globally-nearest address (no per-county toggles; the existing per-row ME/TGT/MAP toggles from 004 still apply).
5. Auto-migrates the v1.0.5 single-active-dataset layout (`active/places.sqlite`) to the v1.0.6 per-county layout (`active/<county>/places.sqlite`, or aligned with the generator's `data/places-*.sqlite` convention — to be settled in plan-phase) on first launch.

Feature 005 explicitly does **not** ship:

- Tier-1 township polygon-in lookup (`townships.sqlite`).
- Tier-2 nearest-road lookup (`roads.sqlite`).
- Multi-source TGOS + OSM merge (`places-osm.sqlite`).
- Forward search / FTS-based text-to-coordinate queries.

These remain deferred to feature 006+ (per the generator's tiered contract §5.1–§5.6).

## Clarifications

### Session 2026-05-26

- Q: How does the operator select multiple files / a ZIP at the picker — single-pick + ZIP-only, SAF multi-select, or a wrapped picker that loops? → A: **Wrapped, chained-picker UX.** Within one batch the operator can pick multiple files (multi-select where the SDK allows it; otherwise sequential picks accumulated into the same batch). After the batch finishes importing, the Offline Address page stays in a "continue adding" state so the operator can keep adding more files / ZIPs without leaving the page; tapping a separate "完成 / Done" button (or navigating away) ends the session.
- Q: How does the Settings dataset-status area render multiple active counties? → A: **One status row per active county.** The Settings fragment exposes the existing three toggles (ME / TGT / MAP) at the top followed by an "Active datasets" section containing N rows, one per active county, each showing `<county> / <data_date> / <inserted rows>`. The fragment MUST remain vertically scrollable so that as county count grows the list scrolls naturally (no fixed-height list, no truncation of the toggles above) — operators with many active counties pan through the list with a normal vertical swipe. Tapping a county row opens the Offline Address page focused on that county.
- Q: While a batch import is in flight, what happens if the operator taps "繼續加入 / Add more" again? → A: **Queue.** The "Add more" affordance stays enabled throughout. Picking new files appends them to the pending queue (which the single-thread import executor drains in order). The page header shows the count of pending items ("待處理 N 個" / "N queued"), and each newly-queued item's per-county progress row appears in a "queued — waiting" state until the executor reaches it. Operators are never blocked by an in-flight import.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Import a ZIP bundle that contains multiple county datasets in one go (Priority: P1) 🎯 MVP

A field operator has obtained the generator's `tw-central-full.zip` (or equivalent multi-county bundle) and copied it to the device's Download folder. They open ATAK → Tools → Offline Address → Import → pick the `.zip`. The plugin extracts every `places-<county>.sqlite` inside, validates each independently against the v1/v2 schema contract, activates the ones that pass (one county at a time, atomically), and surfaces a per-county success/skip report. After the operation completes, the Offline Address page lists each successfully-activated county with its metadata, and reverse-lookup now resolves to whichever active county a given coordinate falls inside.

**Why this priority**: This is the only flow that delivers the user's primary goal — "matching the generator's bundle layout". A single-ZIP-many-counties flow also subsumes the multi-bare-file case (the operator can ZIP arbitrary `.sqlite` files locally and import once). Shipping just this story already delivers a useful and coherent piece of value.

**Independent Test**: With `tw-central-full.zip` on `/sdcard/Download/`, picking it through the Tools-menu Import button MUST result in both `places-taichung` and `places-changhua` becoming active (verified by their county names appearing in the page's dataset list and by reverse-lookup returning Taichung-area address text for a Taichung coordinate and Changhua-area text for a Changhua coordinate).

**Acceptance Scenarios**:

1. **Given** no active datasets, **When** the operator imports `tw-central-full.zip` (containing `places-taichung.sqlite` + `places-changhua.sqlite` + `places-osm.sqlite` + `townships.sqlite` + `roads.sqlite`), **Then** the Offline Address page shows two active county rows (台中市 / 彰化縣), each with its `data_date` + row count + file SHA-256; the OSM / townships / roads files are skipped with a one-line informational note ("3 supplementary files not consumed by v1.0.6 — see Feature 006").
2. **Given** active datasets exist for {台中市, 彰化縣}, **When** the operator imports a ZIP containing a new `places-yunlin.sqlite`, **Then** 雲林縣 is added to the list and the existing two remain untouched (no churn on disk, no reverse-lookup interruption for Taichung / Changhua queries during the import).
3. **Given** active dataset for 台中市 (data_date 115-01), **When** the operator imports a ZIP whose `places-taichung.sqlite` has data_date 115-06, **Then** the plugin prompts "替換現有台中市 (115-01) 為 115-06?", and on confirm the new file atomically replaces the old.
4. **Given** an import is in flight, **When** the operator opens the Offline Address page, **Then** they see a per-county progress indicator (extracting / verifying / activating) rather than a single global progress bar.

---

### User Story 2 — Remove or replace a single county independently (Priority: P1)

A field operator has multiple county datasets active. They notice the Changhua data is out of date, so they tap Replace on the Changhua row, pick a fresher `places-changhua.sqlite` (or `places-changhua.zip`), and Changhua updates in place. Taichung's lookup behaviour does not change at any point during the operation.

**Why this priority**: Per-county lifecycle is the second non-negotiable user expectation — "地區可以個別更新" in the user's words. Without it, even the multi-county MVP is brittle (every update touches every county).

**Independent Test**: With {台中市, 彰化縣} active, tapping Replace on the Changhua row → picking a different `places-changhua.sqlite` → the Changhua row updates while a Taichung-coordinate reverse-lookup continues to return Taichung address text uninterrupted.

**Acceptance Scenarios**:

1. **Given** active datasets {台中市, 彰化縣}, **When** the operator taps **Remove** on the 彰化縣 row and confirms, **Then** 彰化縣 disappears from the list, 彰化-coordinate lookups return empty-state ("the address row collapses"), Taichung-coordinate lookups still work, and the disk frees ~196 MB.
2. **Given** active datasets {台中市, 彰化縣}, **When** the operator taps **Replace** on the 台中市 row and picks a different `places-taichung.sqlite` (different data_date / SHA), **Then** the Taichung row swaps to the new SHA + data_date and the Changhua row is untouched.
3. **Given** active datasets {台中市, 彰化縣}, **When** the operator taps Replace on 台中市 and picks `places-changhua.sqlite` (county mismatch), **Then** the plugin shows an inline error "選擇的檔案是彰化縣，無法替換台中市" and the existing 台中市 dataset stays unchanged.

---

### User Story 3 — Address lookup spans every active county (Priority: P2)

A field operator has Taichung and Changhua datasets active. As they pan the map across the county border, the address row under the map-centre coord readout updates from a Taichung address (south Taichung) to a Changhua address (north Changhua) without any visible glitch. The same applies to self-marker (ME) and target (TGT) rows when the per-row toggles in Settings are on.

**Why this priority**: This is what makes multi-county user-visible. Without it, the operator sees the dataset list grow but the on-map experience doesn't change. The current 004 row-gating (per-row ME/TGT/MAP toggle) and the 250 ms debounce stay identical; only the resolver internals change.

**Independent Test**: With {台中市, 彰化縣} active and MAP toggle on, panning the map from a Taichung point (24.137°N / 120.685°E) to a Changhua point (24.08°N / 120.54°E) MUST result in the address row updating from Taichung text to Changhua text in under 1 s median (same SC-002 budget as 004).

**Acceptance Scenarios**:

1. **Given** {台中市, 彰化縣} active and MAP toggle on, **When** the map centre is at 24.137°N / 120.685°E (Taichung 火車站), **Then** the address row shows a Taichung 北區 / 東區 / 中區 address text.
2. **Given** same state, **When** the operator pans the map to 24.08°N / 120.54°E (彰化市), **Then** the address row updates to a Changhua 彰化市 address text within ~250 ms (same per-row debounce as 004).
3. **Given** only 台中市 active, **When** the map centre is at a Changhua coordinate, **Then** the address row shows the empty-state copy ("No address nearby" / "查無資料").
4. **Given** {台中市, 彰化縣} active and a point that's equally close to a Taichung record and a Changhua record (theoretical border edge), **When** the resolver runs, **Then** it returns whichever record is strictly geodetically nearest by haversine distance (deterministic; no source-county bias).

---

### User Story 4 — First-launch auto-migration from v1.0.5 (Priority: P3)

A field operator was running v1.0.5 with a single active dataset (e.g. 台中市) under the legacy path `tools/twcoord/offline-address/active/places.sqlite`. They install v1.0.6 (this feature) and open the Offline Address page. The plugin transparently migrates the legacy file into the new per-county layout (without prompting the operator), and the page renders the same active county the operator had before — same metadata, same SHA, same reverse-lookup behaviour. No re-import needed.

**Why this priority**: This is a one-time on-upgrade silent path that prevents a regression for existing operators. Critical for upgrade-ability, but invisible to a fresh-install user.

**Independent Test**: On a device that holds a v1.0.5 active dataset, installing the v1.0.6 plugin APK + opening the Offline Address page MUST result in the page showing the same county with the same metadata as before the upgrade, with no Import step.

**Acceptance Scenarios**:

1. **Given** v1.0.5 with active `tools/twcoord/offline-address/active/places.sqlite` (台中市), **When** the operator upgrades to v1.0.6 and opens Offline Address, **Then** the page shows 台中市 in the active list with identical metadata and the legacy directory is moved (not copied) to the new layout so no disk is double-spent.
2. **Given** v1.0.5 with no active dataset, **When** upgrading, **Then** the page shows the empty State A with the new "支援 ZIP 與多縣市" hint copy.
3. **Given** migration fails mid-way (e.g. disk full, permission denied), **When** the operator opens Offline Address, **Then** the page shows the legacy v1.0.5 state preserved exactly as it was (no destructive cleanup of the legacy dir until migration verifies success).

---

### Edge Cases

- **ZIP contains zero matching `places-*.sqlite`**: e.g. operator picks `base.zip` (only `townships` + `roads` + `places-osm`). Show inline error "此 ZIP 不含任何 places-<county>.sqlite — feature 006 才支援 townships/roads/OSM" and leave existing datasets untouched.
- **ZIP contains a corrupt `places-<county>.sqlite`**: per-county validation fails on that one entry; others continue. The report lists the corrupt entry with the same NOT_OPENABLE / UNSUPPORTED_SCHEMA_VERSION reason codes from 004.
- **Multi-county import partial failure**: 3 counties in ZIP, county A passes / county B disk-full / county C passes → A and C activated, B fails with DISK_FULL, prior state of B (if any) stays intact.
- **Duplicate county in same ZIP**: e.g. two `places-taichung.sqlite` entries somehow. Use the first (lowest-offset in the ZIP central directory); skip the rest with a warning.
- **Disk pressure during extraction**: streaming-extract per entry (no temp full-zip unpack); if any entry's bytes exceed remaining disk space, surface DISK_FULL on that entry only, others continue.
- **External tampering** (`adb shell rm` against an active county directory while plugin is running): the affected county auto-detects missing files and falls back to absent (per-county equivalent of 004's SC-005); other counties unaffected. The next time the resolver runs, the missing county is silently de-registered.
- **County metadata mismatch**: e.g. picked file's `metadata.county` is `臺中市` (traditional 臺) while the contract requires the normalised `台中市`. Validation surfaces it as UNEXPECTED_COUNTY_TEXT with the expected/actual values inline.
- **Single-bare-file backwards path**: operator picks a single `places-changhua.sqlite` (not a zip) — same flow as 004 but writes into the per-county layout rather than the single-active path.
- **Schema version mix in same ZIP**: one v1 entry + one v2 entry — both accepted, v1 entry takes the longer R*Tree-build path, v2 entry skips it (same logic as 004's `MAX_SUPPORTED_SCHEMA_VERSION=2` gate).
- **ZIP without sidecar manifest**: the generator's `*.manifest.txt` sidecars live outside the ZIP. The plugin does NOT require them; everything it needs (county / data_date / sha / row count) lives inside each `places-<county>.sqlite`'s `metadata` table.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST accept `.zip` files at the file picker for the Offline Address Import / Replace actions. 004's `IS_A_ZIP` error reason is removed; the picker no longer rejects ZIPs.
- **FR-002**: System MUST allow the operator to assemble a batch of multiple files (mix of `.zip` and bare `.sqlite`) for a single import session. Where the file picker SDK supports multi-selection, the operator picks multiple files in one dialog interaction; where it does not, the picker loops (a fresh picker re-appears after each pick) until the operator signals end-of-batch. After a batch completes, the Offline Address page stays in a "continue adding" state (a visible "繼續加入 / Add more" affordance plus a "完成 / Done" exit) so the operator can chain additional batches without leaving the page.
- **FR-003**: System MUST stream-extract each ZIP entry one at a time (no full-archive unpack to a temporary file before validation). Each extracted `places-<county>.sqlite` goes into a per-county staging directory (`.staging-<county>-<uuid>/`) and is atomically renamed into the active layout on validation pass.
- **FR-004**: System MUST validate every extracted (or directly-picked) `places-<county>.sqlite` independently against the v1+v2 schema contract before activation, using the same validation pipeline 004 ships (`AddressBundleImporter`). Per-entry failures MUST NOT abort other entries in the same batch.
- **FR-005**: System MUST maintain one active dataset per county. The on-disk layout MUST be one directory per county containing `places.sqlite` + `imported.manifest.txt`. (Exact root path — `tools/twcoord/offline-address/active/<county>/` vs the generator's `tools/twcoord/data/<county>/` — is a plan-phase decision; the spec only requires per-county isolation.)
- **FR-006**: System MUST allow the operator to Replace or Remove any single active county independently. Per-county Replace and Remove MUST NOT touch any other county's files or in-memory facade.
- **FR-007**: System MUST reject a Replace where the picked file's `metadata.county` does not match the row's county (inline error, no destructive action). Replace is a same-county refresh; cross-county "swap" is done by Remove + Import.
- **FR-008**: System MUST present a per-county progress / status section on the Offline Address page during a batch import (one progress row per entry being processed, plus a summary at the end with skip / success / failure counts).
- **FR-009**: System MUST run reverse-lookup across every active county dataset and return the globally-nearest record by haversine distance. The 250 ms debounce + per-row toggle gating from 004 stays identical.
- **FR-010**: System MUST detect supplementary ZIP entries (`townships.sqlite`, `roads.sqlite`, `places-osm.sqlite`, `timestamp.*`) and skip them silently with an informational summary entry ("3 supplementary files not consumed by v1.0.6 — see Feature 006"). These MUST NOT count as failures.
- **FR-011**: System MUST treat a ZIP containing zero matching `places-<county>.sqlite` entries as an inline error (`ZIP_NO_VALID_DATASETS`) rather than a successful empty import.
- **FR-012**: On first launch of v1.0.6 after a v1.0.5 upgrade, system MUST detect a legacy `tools/twcoord/offline-address/active/places.sqlite`, read the county from its embedded `metadata.county`, and migrate the file into the new per-county directory layout. Migration MUST be atomic (rename, not copy) and MUST preserve the existing `imported.manifest.txt`. If migration fails for any reason, the legacy file MUST be left in place untouched.
- **FR-013**: System MUST surface dataset-removal as an idempotent operation — removing the last active county returns the on-disk layout to "no active datasets" cleanly (no orphan directories, no stale `imported.manifest.txt` files anywhere).
- **FR-014**: System MUST handle external tampering (e.g. `adb shell rm -rf` against a county directory while the plugin is running) by silently de-registering that county on the next reverse-lookup attempt, with the resolver auto-falling back to the remaining active counties.
- **FR-015**: System MUST emit one `ACTION_DATASET_CHANGED` broadcast per atomic activation / removal (i.e. per county within a batch, not one for the whole batch). Existing subsystems (`AddressSubsystem`, the Settings status row) consume this without modification.
- **FR-016**: System MUST cap concurrent county imports inside a batch (single-thread executor reused from 004) so the import worker stays responsive on devices with constrained CPU / IO.
- **FR-017**: System MUST guarantee R*Tree spatial-index capability for reverse-lookup regardless of the host's OS SQLite build. The runtime primary path uses the ATAK-host-provided SQLite (which on the reference device + ATAK 5.7.0.3 has R*Tree compiled in, per feature 004 ADR-0015 D2); a fallback path MUST be available for hosts whose primary SQLite either lacks R*Tree or fails to open the dataset for any other reason. The fallback is opt-in by the runtime initialiser, not always-on, so APK size is not paid unless needed. (Concrete library + selection algorithm is a plan-phase decision; the spec only requires R*Tree continuity.)
- **FR-018**: Settings → 離線地址 MUST render one status row per active county under the existing per-row toggle group. Each county row MUST show `<county>` + `<data_date>` + `<inserted rows>` (or a comparable per-county metadata summary), display them in a deterministic order (e.g. by county code or by import order — plan-phase decides), and remain vertically scrollable when the count exceeds the visible area. The three per-row toggles (ME / TGT / MAP) MUST stay above the county list and MUST NOT be visually displaced by the list growing. Tapping a county row MUST open the Offline Address page focused on that county.
- **FR-019**: The "繼續加入 / Add more" affordance on the Offline Address page MUST stay enabled while a batch import is in flight. New picks MUST be enqueued onto the same single-thread executor as the current batch (no parallel imports — the FR-016 cap stands). The page MUST surface a pending-count badge ("待處理 N 個" / "N queued") and a "queued — waiting" status for each enqueued item until the executor reaches it. Cancelling the page or navigating away MUST NOT cancel the in-flight import or drop the queued items; they continue to drain in the background.

### Key Entities *(include if feature involves data)*

- **CountyActiveDataset**: One directory per active county. Holds the validated `places.sqlite` + the plugin-side `imported.manifest.txt`. Each has its own SHA-256, data_date, row count, and open SQLite facade.
- **ZipBundle**: A `.zip` archive the operator picks at the file picker. Contains 1..N `places-<county>.sqlite` entries plus any number of supplementary (townships / roads / osm / timestamp / manifest) entries that the plugin currently ignores.
- **BatchImportReport**: Per-batch summary at the end of any ZIP import or multi-select bare-file import: lists each entry with status (activated / replaced / skipped-supplementary / skipped-duplicate / failed-with-reason).
- **ActiveDatasetRegistry**: In-memory map of `county → CountyActiveDataset` maintained by the address subsystem; observed by the resolver (read at lookup time) and the Offline Address page (read at bind time). Atomically updated per county; never partially mutated.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Operators can go from no active datasets to {台中市, 彰化縣} active by picking `tw-central-full.zip` once, in **≤ 90 s** wall-clock on the reference device. (Generator's R*Tree-pre-built v2 sqlite means most of the time is SHA-256 streaming + atomic rename.)
- **SC-002**: With ≥ 2 counties active, the median reverse-lookup latency across 100 random pans inside the union of all county bboxes stays **≤ 1000 ms** (p95 ≤ 2000 ms). Same budget as 004 SC-002 — multi-county MUST NOT regress single-county performance.
- **SC-003**: Replacing any single county MUST NOT interrupt reverse-lookup for the other active counties. Measured: a Taichung-coordinate lookup fired during a Changhua Replace returns the correct Taichung address with no exception in logcat.
- **SC-004**: Removing the last active county returns the on-disk footprint to **0 bytes net** under `tools/twcoord/.../active/` (or whichever root the plan-phase chooses). No orphan `.staging-*` dirs, no stale `imported.manifest.txt`.
- **SC-005**: Importing `tw-central-full.zip` (~165 MiB compressed → ~994 MiB extracted across 5 sqlite + 3 timestamp) MUST NOT exceed device memory pressure. Plugin process RSS during extract stays **≤ 200 MiB** even with multi-county active state held in memory.
- **SC-006**: A v1.0.5 → v1.0.6 upgrade with an existing active dataset preserves it without any user-visible action. Measured: opening the Offline Address page within 10 s of first launch shows the same county + same SHA-256 + same data_date the operator had before upgrade.
- **SC-007**: Per-county Remove returns to State A for that county (status row updates, reverse-lookup returns empty for that county's area) within **≤ 2000 ms** of the user confirming Remove. Same budget as 004 SC-005.

## Assumptions

1. The companion generator's data-contract v2 (per [data-contract.md](file:///c/Users/hhhnr/source/tak/atak_vns_offline_routing/atak-tw-address-generator/docs/data-contract.md)) is the source of truth for ZIP layout and per-county `places-<county>.sqlite` shape. Feature 005 does not require any generator-side change.
2. The supplementary files (`townships.sqlite`, `roads.sqlite`, `places-osm.sqlite`) inside ZIPs are silently skipped by v1.0.6. Their consumption (Tier-1 township polygon-in lookup, Tier-2 nearest-road lookup, Tier-3 OSM landmarks) is feature 006+ scope.
3. The `*.manifest.txt` sidecars and `timestamp.<region>` files inside the ZIPs are informational and not consumed at runtime. Every piece of provenance the plugin needs (county / data_date / inserted / csv_sha256 / source) lives inside each `places-<county>.sqlite`'s `metadata` table.
4. The Settings toggles (ME / TGT / MAP) from feature 004 keep their semantics. There is no per-county toggle in v1.0.6 — every active county participates in lookup whenever any of the three row toggles is on.
5. The address subsystem opens one SQLite facade per active county and keeps them all open for the lifetime of the active set. Closing/reopening on every lookup is rejected — the per-county lookup cost stays close to single-county thanks to ATAK's native SQLite + per-county R*Tree.
6. The maximum number of concurrent active counties is bounded by available disk and ATAK's process memory limits. The plugin does not enforce a hard cap (it would arbitrarily exclude legitimate use), but the UI does surface total disk usage in the page footer so operators can self-monitor.
7. The migration path from v1.0.5 (single active dataset) is one-way: once a device has migrated to v1.0.6, downgrading to v1.0.5 leaves the v1.0.6 per-county directories untouched on disk but invisible to the older plugin (which only looks at the legacy single path). The operator can recover by re-importing on v1.0.5.
8. ZIP format support is limited to the standard PKZIP `.zip` (the format the generator produces). Other archive formats (`.7z`, `.tar.gz`, `.rar`) are out of scope and surface the existing `NOT_OPENABLE` error.
9. ZIP files larger than the generator's documented `tw-central-full.zip` (~165 MiB compressed) are accepted as long as their streaming extraction stays within the SC-005 memory budget. There is no hard size cap on the ZIP itself.
10. Espresso scripted measurements for SC-001 / SC-002 / SC-005 build on the harness work explicitly deferred from feature 004 (T022 / T044 / T048 / T057 follow-up sprint). Feature 005 plan-phase plans to land the harness or document why scripted measurement is still deferred.
11. The R*Tree continuity guarantee (FR-017) is primary-plus-fallback by design choice (recorded here so plan-phase doesn't relitigate): the ATAK-native SQLite path is preferred because it adds zero APK size, but a bundled portable SQLite runtime is acceptable as a fallback when the host fails to open the dataset (e.g. an OEM SQLite build with neither R*Tree compiled in *and* no working ATAK fallback). The fallback library should not be initialised on hosts where the primary path works — APK size paid for fallback should remain ≤ 2 MiB per ABI to keep the plugin lean. Concrete library selection (e.g. Requery `sqlite-android` vs SQLCipher vs other) is a plan-phase decision.
