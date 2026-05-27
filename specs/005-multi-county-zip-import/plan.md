# Implementation Plan: Multi-County + ZIP Bundle Import

**Branch**: `005-multi-county-zip-import` | **Date**: 2026-05-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-multi-county-zip-import/spec.md`

## Summary

Lift feature 004's offline-address subsystem from a single active dataset (`tools/twcoord/offline-address/active/places.sqlite`) to N independently-updatable per-county active datasets (`active/<county>/places.sqlite`), and add ZIP bundle import on top of the existing `.sqlite` flow. The companion data-generator (`atak-tw-address-generator`) already ships a multi-county-friendly layout (per-county `places-<county>.zip` + a consolidated `tw-central-full.zip`), so this is plugin-side alignment work — not a generator change. Three clarifications resolved in spec (Session 2026-05-26): (1) chained-picker UX with multi-select where the SDK allows it; (2) one-row-per-county scrollable Settings status section; (3) queue-based reentrancy for new picks during an in-flight batch.

Technical approach hinges on four extensions of 004's existing seams:

1. `AddressBundleImporter` already streams a single SQLite through SHA-256 + validation + atomic activate. Wrap it in a new `BatchImportCoordinator` that drives N inputs (each a `.sqlite` extracted from a ZIP entry, or a directly-picked bare `.sqlite`) through the same pipeline, emitting a per-entry `BatchImportReport.Entry` (activated / replaced / skipped / failed).
2. The single-active `imported.manifest.txt` becomes one per active county. The `AtakFileSystem` seam (production root from `FileSystemUtils.getItem("tools/twcoord/offline-address/active/")`) gains a per-county sub-directory layer with the existing `.staging-<uuid>/` discipline applied per county.
3. `AddressSubsystem` switches from holding one `AddressDatabaseFacade` to an `ActiveDatasetRegistry` of `county → CountyActiveDataset`; the resolver fan-outs `nearestWithin(lat, lon, r)` across each facade and returns the globally-nearest result by haversine distance.
4. `AtakDatabasesAddressDatabase.Factory` from 004 stays the primary SQLite path. A new `FallbackSqliteFactory` (concrete library settled in Phase 0 research, leading candidate `org.requery:sqlite-android`) is initialised on-demand when the primary path fails to open a dataset — see FR-017 / Assumption §11 ("A primary + B fallback", user-decided 2026-05-26).

All SDK claims in this plan and its companion docs are anchored to **both** `javap -public` against `../ATAK-CIV-5.7.0.3-SDK/main.jar` and the upstream Java source at `github.com/TAK-Product-Center/atak-civ` (default branch `main`). See `feedback-plan-phase-code-anchoring` memory + ADR-0014 / ADR-0015 from feature 004 for the precedent. Anchoring discipline applies to every new ATAK SDK reference introduced by feature 005 (file picker multi-select alternatives in R3, PreferenceCategory dynamic-list manipulation in R7, etc.).

## Technical Context

**Language/Version**: Java 11 (sourceCompatibility / targetCompatibility 11 in `app/build.gradle`; same as feature 004).

**Primary Dependencies**:
- ATAK-CIV-SDK 5.7.0.3 (`compileOnly`, jar at `../ATAK-CIV-5.7.0.3-SDK/main.jar`)
- ATAK native SQLite via `com.atakmap.database.Databases` (rtree-enabled, 004 D2)
- AndroidX preferences + appcompat (existing)
- Java `java.util.zip.ZipInputStream` for streaming extract (no external archive lib needed)
- **NEW potential**: a portable SQLite library for the FR-017 fallback path — `org.requery:sqlite-android` is the leading candidate (rtree-enabled, ~1.5 MB per ABI, MIT licence). Final selection in Phase 0 R5.
- Existing test stack: JUnit 4, Mockito, Robolectric (test-only), xerial sqlite-jdbc 3.x (test-only, rtree-enabled).

**Storage**: Per-county directory layout under the plugin's existing root, each with `places.sqlite` (+ optional shm/wal) + `imported.manifest.txt`. Staging dirs `.staging-<county>-<uuid>/` swept on importer construction (FR-005 + FR-013 from 004's pattern).

**Testing**: `./gradlew :app:testCivDebugUnitTest` for JVM (Robolectric + xerial); on-device Espresso for end-to-end (T031/T044/T048 of 004 deferred to follow-up — feature 005 reopens the harness work in plan-phase R9).

**Target Platform**: Android API 26+ via ATAK-CIV 5.7.x (same baseline as 004).

**Project Type**: Android ATAK plugin (single APK module at `app/`); not a separate library, not a multi-module project.

**Performance Goals** (from spec SC):
- SC-001 `tw-central-full.zip` → {台中市, 彰化縣} active in ≤ 90 s wall-clock
- SC-002 multi-county reverse-lookup median ≤ 1000 ms (p95 ≤ 2000 ms) across 100 random pans
- SC-005 plugin RSS during extract ≤ 200 MiB
- SC-007 per-county Remove ≤ 2000 ms to State A

**Constraints**:
- Zero outbound network (inherited from 004 FR-019: no `INTERNET` permission, enforced at manifest level)
- Single-thread import executor (FR-016); cross-batch reentrancy via queue (FR-019)
- Per-county atomicity: a failed county MUST NOT corrupt or de-register others
- Auto-migrate (FR-012) MUST be one-way and lossless from v1.0.5 single-active to v1.0.6 per-county
- Constitution VI (Host-Process Isolation): every new host-callable entry point wrapped in `try / catch (Throwable)` → `Log.w`

**Scale/Scope**:
- Active county count: 1..~22 (Taiwan administrative divisions); generator currently ships taichung + changhua + (next sprint) more.
- Per-county dataset: up to ~1.3 M rows / ~600 MB SQLite (taichung) including pre-built `places_rtree` (~25 % of file).
- Multi-county active concurrent disk footprint: ~1.3 GB upper bound across 5 county-equivalents; UI surfaces a footer with the running total per Assumption §6.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| **I. Code Quality & Formatting** | ✅ Pass | Spotless + lint gates apply; ADR-0015 D4 lint StringFormatMatches fix transfers as a known-style precedent for new resource strings introduced in 005. |
| **II. Test-First Development (TDD)** | ⚠ Conditional | JVM unit tests for every new pure-logic class (BatchImportCoordinator, ActiveDatasetRegistry, per-county facade fan-out, auto-migrate). Espresso harness deferred from 004 (T022/T031/T044/T048/T057); plan-phase R9 commits to either landing the harness or documenting the device-bound manual run path. **Conditional pass** until R9 resolves. |
| **III. UX Consistency** | ✅ Pass | `docs/ui/offline-address-page.md` (existing) + `docs/ui/settings-fragment.md` (existing) MUST be updated for chained-picker UX, per-county rows, queue-state badge. New strings: zh-rTW + ja + en parity required (Constitution III FR-018 from 004 echoes here). |
| **IV. Performance** | ✅ Pass | SC-001 / SC-002 / SC-005 are explicit; profile points are inheriting 004's instrumentation (logcat `picker returned file=…` + `opened FileInputStream`). The 250 ms debounce + single-thread executor are reused — no new performance budget regression vector beyond multi-county fan-out cost. |
| **V. Documentation & Knowledge Preservation** | ✅ Pass | ADR-0016 will be authored post-implement capturing Phase 0 decisions (per Constitution V). UI docs + READMEs in same change set per Principle V cadence. |
| **VI. Host-Process Isolation (NON-NEGOTIABLE)** | ✅ Pass (with audit task) | Every new entry point added by 005 — chained-picker `DialogDismissed.onFileSelected` callbacks (one per batch entry), the queue-drain worker thread, the per-county Replace / Remove `OnClickListener`s, the migration runner that fires at plugin onCreate, the `ACTION_DATASET_CHANGED` fan-out per county, the `setOnPreferenceClickListener` per Settings county row — MUST be wrapped per Principle VI. Audit task explicit in Polish phase (mirrors 004 T056 / ADR-0015 D5 audit). |

**No violations to justify in Complexity Tracking at gate entry.**

## Project Structure

### Documentation (this feature)

```text
specs/005-multi-county-zip-import/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── batch-import-coordinator.md
│   ├── active-dataset-registry.md
│   ├── zip-extractor.md
│   ├── auto-migrator.md
│   └── fallback-sqlite-factory.md
├── checklists/
│   └── requirements.md  # /speckit-specify validation output (already created)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
app/
├── src/
│   ├── main/
│   │   ├── java/com/atakmap/android/twcoord/
│   │   │   ├── TwCoordMapComponent.java        # MODIFY: wire BatchImportCoordinator + ActiveDatasetRegistry; auto-migrate hook at onCreate
│   │   │   ├── TwCoordPreferenceFragment.java  # MODIFY: dynamic per-county PreferenceScreen rows (FR-018)
│   │   │   └── address/
│   │   │       ├── AddressBundleImporter.java          # MODIFY: per-county staging + manifest paths
│   │   │       ├── AtakFileSystem.java                 # MODIFY: per-county sub-dir helpers
│   │   │       ├── AddressDataset.java                 # MODIFY (or extend): per-county identity (county code)
│   │   │       ├── AddressSubsystem.java               # MODIFY: hold ActiveDatasetRegistry, fan-out lookup
│   │   │       ├── OfflineAddressReceiver.java         # MODIFY: dataset list view, chained picker, queue badge
│   │   │       ├── BatchImportCoordinator.java         # NEW: drives N inputs through 004's pipeline
│   │   │       ├── ActiveDatasetRegistry.java          # NEW: county → CountyActiveDataset map + observers
│   │   │       ├── CountyActiveDataset.java            # NEW: per-county wrapper (facade + manifest + paths)
│   │   │       ├── BatchImportReport.java              # NEW: per-entry result list
│   │   │       ├── ZipExtractor.java                   # NEW: streaming-extract a ZIP into staging
│   │   │       ├── ZipEntryClassifier.java             # NEW: classify entries (places-sqlite / supplementary / unknown)
│   │   │       ├── AutoMigrator.java                   # NEW: v1.0.5 single-active → v1.0.6 per-county layout
│   │   │       ├── FallbackSqliteFactory.java          # NEW: opt-in portable-SQLite path for FR-017 fallback
│   │   │       ├── AtakDatabasesAddressDatabase.java   # KEEP: primary SQLite (004 D2)
│   │   │       ├── SqliteAddressDatabase.java          # KEEP: test fixture (004 D3)
│   │   │       └── (other 004 classes — unchanged)
│   │   ├── res/
│   │   │   ├── layout/offline_address_page.xml         # MODIFY: dataset list, queue badge, "繼續加入 / 完成" affordances
│   │   │   ├── xml/preferences.xml                     # MODIFY: dynamic county-row category
│   │   │   ├── values/strings.xml                      # MODIFY: ~12 new keys (queue badge, per-county summary, "繼續加入", "完成", error messages)
│   │   │   ├── values-zh-rTW/strings.xml               # MODIFY: zh-TW parity
│   │   │   └── values-ja/strings.xml                   # MODIFY: ja parity
│   │   └── AndroidManifest.xml                         # NO CHANGE expected (004 manifest already minimal)
│   └── test/java/com/atakmap/android/twcoord/address/
│       ├── BatchImportCoordinatorTest.java             # NEW: per-entry success/skip/fail matrix
│       ├── ZipExtractorTest.java                       # NEW: streaming-extract, entry-classification, partial-failure
│       ├── ZipEntryClassifierTest.java                 # NEW: places-* / supplementary / unknown rules
│       ├── ActiveDatasetRegistryTest.java              # NEW: county add/replace/remove, observer fan-out
│       ├── AddressSubsystemMultiCountyTest.java        # NEW: cross-county lookup, "globally-nearest" determinism
│       ├── AutoMigratorTest.java                       # NEW: v1.0.5 layout detection + atomic rename + failure rollback
│       ├── FallbackSqliteFactoryTest.java              # NEW: opt-in trigger, rtree-probe, swap behaviour
│       ├── AddressBundleImporterMultiCountyTest.java   # NEW: per-county staging + manifest paths
│       └── (existing 004 tests — adapted only where signatures change)
└── build.gradle                                        # MODIFY: add fallback SQLite dep (after R5 picks one)

docs/
├── adr/
│   ├── 0017-multi-county-zip-import.md                 # NEW (after /speckit-implement): post-impl decisions, R*Tree fallback library choice, Constitution VI audit
│   │                                                    # (ADR-0016 = methodology lesson "prefer SDK samples", authored separately during 005 development)
│   └── (existing 0001-0015 unchanged)
└── ui/
    ├── offline-address-page.md                          # MODIFY: multi-county list, chained picker, queue badge
    ├── settings-fragment.md                             # MODIFY: per-county scrollable row section
    └── (others unchanged)
```

**Structure Decision**: Same single-module Android plugin layout used by features 001 / 002 / 003 / 004. No new sourceSets, no new flavours, no new modules. New classes live in the existing `address/` package alongside 004's; new layouts / strings extend the existing `res/`. Test classes follow the existing JVM-only Robolectric+xerial discipline. `docs/adr/` gets one new ADR (0016) post-implement; `docs/ui/` gets two existing files updated.

## Complexity Tracking

> No Constitution Check violations to justify at plan-phase entry. R5 (FR-017 fallback library) carries an APK-size budget of ≤ 2 MiB per ABI per Assumption §11; if the leading candidate's size proves to violate the budget in Phase 0, this table will be populated with an alternative + rejection rationale.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| _(none at gate)_ | — | — |

Re-check after Phase 1 design (see end of research.md for the post-design gate result).
