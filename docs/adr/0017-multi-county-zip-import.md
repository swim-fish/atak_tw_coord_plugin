# ADR-0017: Multi-county + ZIP import — post-implementation pivots

**Status**: Accepted
**Date**: 2026-05-27
**Origin**: `/speckit-implement` Phase 1–6 + Phase 7 polish for feature 005-multi-county-zip-import (commits `1c29c55` scaffold → `f0aca20` Phase 7 confidence preset → `0c8d073` State B Import button) plus device acceptance on Samsung Galaxy R52X908JF0W / Android 14 / ATAK-CIV 5.7.0.3.

This ADR captures the concrete pivots made during implementation after [ADR-0014](./0014-offline-address-reconnaissance.md) defined the offline-address architecture and [ADR-0015](./0015-offline-address-implementation.md) recorded the feature 004 pivots that feature 005 builds on. Where ADR-0015 records the device-only platform realities (SAF picker dead-end, platform SQLite R*Tree gap), this ADR records the design pivots specific to multi-county + ZIP — three pure-design and four device-found.

## Context

Research R1–R10 defined the architecture. Phases 1–6 landed mostly as designed; Phase 7 polish made one substantive UX addition (confidence-indicator preset) plus three device-found UX fixes after the first acceptance run on the reference device. The R5 fallback library choice was the only research point that required a concrete pick; the rest of this ADR documents post-design pivots that the research didn't predict.

## Decisions

### D1 — Requery `sqlite-android` chosen as the FR-017 fallback library (R5)

**Spec impact**: research R5 listed three candidates for the fallback when ATAK's native SQLite path fails: Requery, AndroidX SQLite, raw NDK SQLite. R5 deferred the final pick to implementation.

**Why**: Requery's `org.requery:sqlite-android` ships R*Tree, FTS, JSON1, and the SpatiaLite extensions pre-built per ABI, identical extension surface to ATAK's native runtime. The `SQLiteDatabase` API is a near-drop-in for the Android platform `android.database.sqlite.SQLiteDatabase`, so the fallback wrapper (`FallbackSqliteFactory`) and the production facade (`AtakDatabasesAddressDatabase`) share the same SQL strings byte-for-byte (compared via diff during Phase 2b). APK size impact: +1.5 MiB per ABI (arm64-v8a, armeabi-v7a) = ~3 MiB total — comfortably under Assumption §11's 2 MiB/ABI budget.

Rejected:
- **AndroidX SQLite (`androidx.sqlite:sqlite-framework`)** — wraps the platform SQLite, so the R*Tree gap that motivated the fallback in the first place would re-surface on Samsung One UI.
- **Raw NDK SQLite** — would force the plugin to ship a hand-built JNI bridge, +25 KB per ABI but +200 LoC of bridge code and +1 ADR for the lifecycle invariants.

Lazy-init pattern: `FallbackSqliteFactory.open(...)` only runs the classloader after the primary `AtakDatabasesAddressDatabase.Factory.open(...)` has thrown. On the reference device, the primary path always succeeded (ATAK native SQLite has R*Tree per ADR-0015 D2); the fallback was never exercised in production but its `UnsatisfiedLinkError`/`Throwable` chain (Phase 2b) is JVM-tested via `FallbackSqliteFactoryTest` (7 cases).

### D2 — `ZipExtractor` streams entries one at a time with per-entry isolation

**Spec impact**: data-model.md §3 + research R5 expected ZIP extraction would dominate the SC-005 ≤ 200 MiB RSS budget for `tw-central-full.zip` (165 MB). The naive `ZipInputStream` → load-all-into-memory approach would blow the budget.

**Why**: `ZipExtractor` uses `ZipInputStream` + a 64 KiB transfer buffer + per-entry `try/finally` for the output stream. Each entry is classified by `ZipEntryClassifier` (filename + metadata-table peek) **before** extraction starts, so supplementary entries (townships / roads / OSM landmarks per data-contract v2) are skipped at the `nextEntry()` level — bytes never leave the input stream.

Per-entry isolation: a corrupt entry inside an otherwise valid ZIP throws inside its own `try`, leaving the staging directory clean and the surrounding entries unaffected. The coordinator's `BatchImportReport` records the failure but the batch continues for the remaining entries. JVM tests cover 9 paths; 2 are `@Ignored` for JDK `ZipOutputStream` edge cases that can only be reproduced via a device fixture (T043) — deferred to the post-merge Espresso harness.

### D3 — `AutoMigrator` is atomic by design with copy+verify+delete fallback on cross-mount

**Spec impact**: research R8 named `ATOMIC_MOVE` as the v1.0.5 → v1.0.6 migration primitive. The implementation uncovered that on the reference device, `/sdcard/atak/tools/twcoord/offline-address/` and the same path's `active/<county>/` parent **can land on different mount points** depending on whether the operator has an SD card inserted, which breaks `ATOMIC_MOVE` with `AtomicMoveNotSupportedException`.

**Why**: the migrator now tries `ATOMIC_MOVE` first and on failure falls back to a three-step copy + verify (file size + SHA-256 match) + delete-source sequence. If the verify step fails midway, the partial copy is rolled back — the legacy source is left untouched and the next plugin start retries from `Result.NeedsMigration`. Idempotency is preserved by checking `places.sqlite` existence under the destination per-county dir before the copy starts. 9 JVM tests in `AutoMigratorTest` cover happy path, cross-mount fallback, partial-move rollback, malformed metadata, missing destination, and idempotent re-runs.

### D4 — County name comes from `metadata.county`, not the filename romanisation

**Spec impact**: data-model.md §2 named the per-county active directory as `active/<county>/`. The first implementation pass derived `<county>` from the input filename (`places-taichung.sqlite` → `taichung`), but data-contract v2 §2 specifies that `metadata.county` carries the **normalised Traditional Chinese form** (`台中市`) that the resolver displays on the widget.

**Evidence**: 2026-05-26 evening device run surfaced as "活躍 / 已啟用" rows under Settings showing `taichung` while the address widget rendered `台中市` — same county, two display strings, operator-confusing.

**Fix**: `BatchImportCoordinator.activateExtractedCounty` now `peekCounty(file)` via the primary factory before calling `importFromInto(file, county)`. The active directory becomes `active/台中市/`, the registry key is `台中市`, and the Settings row + widget all agree. The romanised filename hint is kept only inside the staging directory name for cross-platform debuggability (filesystems on the reference device tolerate UTF-8 dir names; staging uses ASCII to side-step any host that doesn't).

### D5 — Widget redraw race mitigated, residual flicker accepted

**Spec impact**: ADR-0015 D5's "address-row redraw" path was single-active. Multi-county fan-out adds N facade reads per debounce cycle; on the reference device this widened the window where the ATAK TextWidget framework leaves the row's dark background visible without re-rendered text on fast map pans.

**Why accept**: aggressive mitigation already stacked in `TwCoordWidget.paintAddressRow` (250 ms debounce, `setVisible(false)` toggle, `setBackground` re-fire, `row.onSizeChanged()` to invalidate cached width, `mapView.postOnActive` to poke the render thread). Eliminating the residual rare flicker entirely would require either raising the debounce to ≥ 750 ms (degrading subjective responsiveness on every pan) or hooking deeper into ATAK's render pipeline than the SDK exposes. 2026-05-27 UX call: keep 250 ms responsiveness, accept the rare flicker as a known limitation.

### D6 — Confidence-indicator preset is operator-selectable (Phase 7)

**Spec impact**: feature 005 spec didn't explicitly call for a confidence-indicator preset; the original design hard-coded the 20/100 m thresholds based on TGOS urban density. Operator feedback during 2026-05-27 device verification asked for a way to widen the buckets when working in suburban areas where city-block-density assumptions don't hold.

**Why preset, not free-form**: 4 presets cover the realistic span (Off / Tight 20-100 / Standard 50-200 / Loose 100-500) and avoid the "operator enters 999999 and breaks the UI" failure mode. `ConfidenceThresholds` enum owns the bucket logic + the `< 0` sentinel for the unknown-distance case (legacy single-active path that doesn't compute haversine). Default `TIGHT` preserves the 2026-05-27 device-verified behaviour for upgrades that don't touch settings. `volatile` field on `AddressSubsystem` so UI changes reach the worker on the next `runLookup` without restart.

### D7 — Settings per-county rows are programmatically populated, not declarative XML

**Spec impact**: tasks.md T042 named a `PreferenceCategory("active_datasets")` populated programmatically. Implementation kept that — the declarative XML approach would require N at-compile-time-known county rows which contradicts the multi-county-by-import design.

**Why a `RegistryProvider` seam**: mirrors the existing `AddressImporterProvider` seam (ADR-0015 indirect) so the category population logic can be JVM-tested without spinning up the map component. `TwCoordPreferenceFragmentAddressTest` expanded from 4 → 8 cases covering the new 4-state truth table (multi-county count + legacy fallback + toggle gate), with the multi-county branch winning over the legacy single-active branch — the registry is the source of truth once `setRegistry(...)` has been called.

### D8 — Constitution VI audit: all 13 entry points guarded

Per research R10, walked the 13 entry points the feature introduces or extends, citing `file:line` for the `try/catch (Throwable)` guard at each:

| # | Entry point                                                         | File:line                                                  | Status |
|---|---------------------------------------------------------------------|------------------------------------------------------------|--------|
| 1 | `ImportFileBrowserDialog.onFileSelected` (chained, multi-batch)     | `OfflineAddressReceiver.java:613,676` via `safeRun`        | ✅ guarded |
| 2 | `ImportFileBrowserDialog.onDialogClosed`                            | same sites (lambda body inside `safeRun`)                  | ✅ guarded |
| 3 | "Add more" OnClickListener                                          | `OfflineAddressReceiver.java:130,133` via `safeRun`        | ✅ guarded (same target as Import) |
| 4 | "Done" OnClickListener                                              | not surfaced as a button — auto-hide after 3 s             | n/a |
| 5 | Per-county Replace click handler                                    | `OfflineAddressReceiver.java:263` via `safeRun`            | ✅ guarded |
| 6 | Per-county Remove click handler                                     | `OfflineAddressReceiver.java:266` via `safeRun`            | ✅ guarded |
| 7 | `AutoMigrator.tryMigrate` call from `TwCoordMapComponent.onCreate`  | `TwCoordMapComponent.java:476–485`                         | ✅ guarded |
| 8 | `Preference.OnPreferenceClickListener` (status row → page)          | `TwCoordPreferenceFragment.java:onResume` lambda body      | ✅ guarded (wrapped in `try/catch (Throwable)`) |
| 9 | `ACTION_DATASET_CHANGED` re-read (now N-county fan-out)             | `AddressSubsystem.java:lookupAcrossAllCounties` per-county `try` | ✅ guarded |
| 10 | Queue-drain worker on the single-thread executor                   | `BatchImportCoordinator.java:144,201,228,303,333,396`      | ✅ outer + per-entry guards |
| 11 | `BatchImportReport` listener callbacks                              | `OfflineAddressReceiver.java:337,384,456,485,509,523`      | ✅ guarded |
| 12 | `ActiveDatasetRegistry` observer callbacks                          | `ActiveDatasetRegistry.java:199–204` `fireChange`          | ✅ per-observer `try/catch (Throwable)` (listener short-circuit rule) |
| 13 | `FallbackSqliteFactory.open` JNI native-lib init                    | `FallbackSqliteFactory.java:60–66` `UnsatisfiedLinkError` + `Throwable` | ✅ guarded |

Net: no `Throwable` escape paths into ATAK's host process across the 13 new or extended entry points.

## Open items deferred to post-merge

- **SC-001 (Replace ≤ 30 s) / SC-002 (lookup p50 ≤ 5 ms / p95 ≤ 15 ms) / SC-005 (`tw-central-full.zip` RSS ≤ 200 MiB) / SC-007 (Settings render ≤ 100 ms)** — JVM tests cover the logic; on-device numbers require T043 (RSS Espresso) + T055 (Espresso harness). Numbers will be appended to this ADR as **D9** once the device runs land. Until then, treat the SCs as designed-to-meet-not-measured.
- **T044 Espresso crash-isolation** for the 13 entry points — the JVM regressions cover the logic; the cross-process listener guarantee (Constitution VI) is only verifiable inside an ATAK process, deferred to the Espresso harness.

## Reusable lessons (carried forward for feature 006+)

- When the spec promises atomic-by-design, expect the OS primitive to fail on at least one of {cross-mount, SAF-only path, OEM-customised mount layout}. Build the copy+verify+delete fallback in from the start, not after the first device run.
- Use generator-supplied metadata as the source of truth for any user-visible label. Filename derivations look fine on the desk but break the moment the operator switches locale or imports a hand-renamed file.
- Operator-tunable UX presets beat hard-coded thresholds whenever the field reality has more variance than the test fixtures. Preset enums (not free-form numbers) bound the failure mode while still letting the operator adapt.
- Programmatic preference rows + a `Provider` test seam beat declarative XML when row count is data-driven. Same pattern as feature 002's recent-entries list — recurring enough to qualify as a project idiom.

## Cross-references

- ADR-0014 (feature 004 reconnaissance — R10 atomic activation, R12 entry-point audit pattern).
- ADR-0015 (feature 004 post-impl pivots — D2 ATAK native SQLite preference, D5 diagnostic logging discipline).
- ADR-0016 (prefer SDK samples before self-implementing — methodology authored mid-feature 005).
- `specs/005-multi-county-zip-import/research.md` R5 (fallback library candidates) + R10 (entry-point audit preview).
- `specs/005-multi-county-zip-import/data-model.md` §2 (per-county layout), §3 (ZIP extraction), §4.1 (multi-county fan-out + monotonically-shrinking radius).
- `specs/005-multi-county-zip-import/quickstart.md` §9 (pre-PR checklist that this ADR closes out).
- Companion generator data-contract v2 §2 (per-county zip), §5.6 (composite reverse-geocode pre-spec for feature 006+).
