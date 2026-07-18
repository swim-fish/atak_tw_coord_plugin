# Implementation Plan: Offline Address Lookup — Import, Display, and Settings Toggle

**Branch**: `004-offline-address` | **Date**: 2026-05-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-offline-address/spec.md`

## Summary

Ship an offline reverse-address lookup feature on top of the existing on-map coordinate readout
(`TwCoordWidget`). The operator side-loads a `places-<county>.sqlite` file produced by the
companion data-generator project (`atak-tw-address-generator`, local sibling repo at
`<ATAK_TW_ADDRESS_GENERATOR>`) through a new
Tools-menu entry **Offline Address**. The file is validated against the generator's schema
(`metadata` table with `schema_version` / `county` / `data_date`; `places` table with the expected
column set); the plugin then builds an R*Tree spatial index into the same file (one-shot,
~30–45 s for a Taichung-scale 1.3 M-row dataset — the generator does not currently ship an
R*Tree, see [R3](./research.md#r3--reverse-lookup-spatial-index-rtree-built-at-plugin-import)),
writes a plugin-side `imported.manifest.txt` companion file recording the import-time SHA-256
of the file and timestamp, and atomically activates the new dataset.
Three independent Settings toggles (`pref_address_row_me`, `pref_address_row_target`,
`pref_address_row_map`, each defaulting to **off**) gate whether the address row appears under each
of `TwCoordWidget`'s three existing rows (ME / TGT / MAP). When all toggles are off, the address
subsystem stays dormant — no database open, no scheduled work, no measurable footprint.

Runtime reverse-lookup is purely local: given a WGS-84 point, compute a small bounding box
(cos-latitude corrected), query an R*Tree index for candidate records, refine by haversine distance,
return the nearest record's address text (or empty-state row if nothing within the search radius).
All DB access runs on a single background `Executor` with per-row request coalescing and a 250 ms
debounce, so panning / driving cannot stutter the UI. The plugin ships zero address data — every
address shown comes from the operator-imported bundle.

Technical approach pivots on three SDK / JVM seams to keep the importer / resolver testable on
the JVM without Android or ATAK: `AddressBundleImporter` wraps SAF (Storage Access Framework) +
streaming SHA-256 + R*Tree build + atomic directory replace; `AddressDatabaseFacade` is a
JVM-mockable interface around the SQLite reader (both `metadata` reads and `places_rtree` +
`places` JOIN queries); `AddressResolver` composes the facade with the haversine-refine algorithm. The widget
extension adds three sibling `TextWidget` instances (one under each existing row), gated per-row by
the new preferences. The Tools-menu entry is a new `OfflineAddressTool` (extends
`AbstractPluginTool`, same shape as `TwCoordTool` / `TwCoordGotoTool`) that broadcasts
`com.atakmap.android.twcoord.SHOW_OFFLINE_ADDRESS` to a new `OfflineAddressReceiver`
(`DropDownReceiver`). Constitution VI is enforced uniformly: every new host-callable entry point
(~11 new callbacks per [R10](./research.md#r10--constitution-vi-compliance-audit)) gets the outer
`try/catch (Throwable)` wrap.

All SDK claims in this plan and its companion docs (`research.md`, `data-model.md`,
`contracts/*.md`, `quickstart.md`) are anchored to **both** `javap -public` against
`../ATAK-CIV-5.7.0.3-SDK/main.jar` and the upstream Java source at
`github.com/TAK-Product-Center/atak-civ` (default branch `main`). See
[research.md § Anchoring discipline](./research.md#anchoring-discipline) and feedback memory
`feedback-plan-phase-code-anchoring`.

## Technical Context

**Language/Version**: Java 17 (host plugin module; unchanged from features 001–003).

**Primary Dependencies**:

- ATAK-CIV 5.7.0.3 SDK (`atak-gradle-takdev` 3.+; runtime-compat declared at `5.4.0.CIV` per
  ADR-0007).
- AndroidX `core 1.17.0`, `fragment 1.8.9`, `lifecycle 2.9.4` (existing resolutionStrategy; no
  new pins).
- Android platform SQLite (`android.database.sqlite.SQLiteDatabase`) — used in
  `OPEN_READONLY | NO_LOCALIZED_COLLATORS` mode. R*Tree extension is bundled in Android
  platform SQLite from API 21+ (well below this project's `minSdk 26` floor)
  ([R3](./research.md#r3--reverse-lookup-spatial-index-rtree-built-at-plugin-import)).
- `java.security.MessageDigest` (`SHA-256`) — JDK stdlib; no new dependency.
- Spotless 6.25 + google-java-format 1.22 (formatter is a build dependency per Constitution
  Principle I).
- **No new third-party dependencies introduced by this feature.** SAF + JDK stdlib copy
  the picked `.sqlite` into staging; plugin-side `imported.manifest.txt` parsing is a single
  key-per-line text file ([R5](./research.md#r5--bundle-layout-bare-sqlite-with-in-db-metadata));
  JSON / XML are not needed. (A future move to a `.zip` consolidated bundle adds only
  `java.util.zip.ZipInputStream`, also stdlib.)

**Storage**:

- Android `SharedPreferences` (file `tw_coord_settings`, already in use). Three new keys:
  - `pref_address_row_me` (boolean, default **false**).
  - `pref_address_row_target` (boolean, default **false**).
  - `pref_address_row_map` (boolean, default **false**).
- Filesystem (ATAK-managed plugin data directory) — bundle staging and active dataset:
  - Working / staging: `<atak-root>/tools/twcoord/offline-address/.staging-<UUID>/`
    (per-import suffix; concurrent imports get distinct dirs; `AtakFileSystem` sweeps
    orphan `.staging-*` dirs on construction)
  - Active dataset: `<atak-root>/tools/twcoord/offline-address/active/`
  - The exact path is resolved at runtime via the ATAK `FileSystemUtils.getItem("tools/twcoord/offline-address/...")`
    pathway ([R2](./research.md#r2--atak-managed-plugin-data-directory)); no hard-coded
    `/sdcard/...` literals per Constitution VI.

**Testing**:

- JVM unit tests: JUnit 4.13.2 + AssertJ 3.27.3 + Mockito 5.16.1 (existing inner loop). The
  `AddressDatabaseFacade` interface seam and the `AddressBundleImporter`'s injected `FileSystem`
  / `ShaCalculator` interfaces let every component be unit-tested on the JVM without Android or
  ATAK.
- Instrumented tests: AndroidX Test + Espresso 3.5.1 (existing). 2–3 new end-to-end tests cover
  Acceptance Flows A (US1 import-then-show) and D (US4 missing-files fallback) from
  [quickstart.md](./quickstart.md).
- TDD discipline per Constitution Principle II: `AddressBundleImporterTest`,
  `AddressDatabaseFacadeTest`, `AddressResolverTest`, `OfflineAddressReceiverTest`,
  `TwCoordWidgetAddressRowTest`, `AddressPreferencesTest` authored before the production code
  that satisfies them.

**Target Platform**: ATAK-CIV 5.7.0.3 (compatibility declared at `com.atakmap.app@5.4.0.CIV`);
Android `minSdk 26`, `target 34`, `compileSdk 36`; ABI `arm64-v8a` for device, `armeabi-v7a,
arm64-v8a, x86` for non-bundle builds. Unchanged from features 001–003.

**Project Type**: Android plugin module (single `app/` Gradle module). This feature extends the
existing module — no new Gradle subproject.

**Performance Goals** (from spec SC-002 / SC-003 / SC-004 / SC-005):

- "Coordinate stabilises → address row updates" median **≤ 1 s**, p95 **≤ 2 s**, measured over
  100 consecutive map movements on the reference device (Galaxy Tab S10+, consistent with
  features 001–003) — SC-002.
- Bundle import end-to-end **≤ 180 s** (placeholder) for a Taichung-scale single-county file
  (~1.3 M rows, ~500–600 MB generator output plus ~150–250 MB R*Tree build, total ~650–850 MB
  on-disk after activation), progress visible (no blank UI > 500 ms) — SC-003. Tighten the SC
  if T057 measurement comes in comfortably under the placeholder.
- Dataset-files-missing → graceful recovery **≤ 2 s** on next refresh — SC-005.
- All three toggles **off** → background CPU / memory footprint indistinguishable from this
  plugin build with the feature absent — SC-004.

**Constraints**:

- **Offline-capable**: zero outbound network for address operations (inherits features 001–003
  zero-telemetry posture).
- **Main-thread discipline**: every SQLite query MUST run on the background executor; UI
  bind dispatches back via `mapView.post` / `Handler` per
  [R7](./research.md#r7--threading-model-debounce--executor).
- **No new image assets**: the plugin contributes zero address data. Every address comes from the
  operator-imported bundle (FR-004; companion data-generator is out of scope per Assumption §1).
- **Constitution VI**: every new host-callable callback (~11 new entry points per
  [R10](./research.md#r10--constitution-vi-compliance-audit)) MUST be wrapped in
  `try/catch (Throwable)` at its outer scope. Listener fan-out wraps each delegate per
  Principle VI.
- **Backwards-compat**: three new persistence keys all default to `false`; on upgrade from
  v1.0.4 (no key present), the address subsystem is dormant. Zero visual change for operators
  who don't opt in.

**Scale/Scope**:

- Three JVM-mockable seams (`FileSystem`, `ShaCalculator`, `AddressDatabaseFacade` plus its
  nested `Factory`) consumed by the new `AddressBundleImporter` and `AddressSubsystem`.
- One new value-object family (`AddressDataset`, `GeneratorMetadata`, `ImportedManifest`,
  `AddressRecord`, `AddressLookupResult`, `AddressRowState`).
- One new resolver (`AddressResolver`), one new lifecycle owner (`AddressSubsystem` —
  owns the executor, the active dataset handle, the per-row coalescer).
- One new tool / receiver pair (`OfflineAddressTool`, `OfflineAddressReceiver` —
  `DropDownReceiver`).
- ~40 LoC of new `TwCoordWidget` (3 new `TextWidget` instances + `renderAddresses(...)`
  helper) + ~30 LoC of new `TwCoordMapComponent` wiring (subsystem lifecycle + per-row gating).
- ~50 LoC of new `TwCoordPreferenceFragment` (3 new SwitchPreferences + dataset-presence hint).
- ~40 new JVM unit tests across 6 test classes + 2–3 new Espresso tests.
- 3 new `<SwitchPreference>` entries in `preferences.xml` + 1 dialog layout XML for the
  Offline Address page.
- ~14 new string resources × 3 locales (en / zh-rTW / ja).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | **Code Quality & Formatting** (NON-NEGOTIABLE) | **PASS** | Spotless + google-java-format already enforced in `app/build.gradle`; build fails on unformatted code. No new format / lint surface introduced. |
| II | **TDD** (NON-NEGOTIABLE) | **PASS** | Three explicit JVM-mockable seams (`AddressBundleImporter` injects a `FileSystem` + `ShaCalculator`; `AddressDatabaseFacade` is the SDK seam; `AddressResolver` composes the two) ensure every component is unit-testable on the JVM without Android or ATAK. Contracts under `contracts/` enumerate the test list per class. Tests authored before production code per the existing pattern (features 001–003). |
| III | **UX Consistency** | **PASS** | The address rows match the existing `TwCoordWidget` styling exactly (same `TextWidget(initial, 2)` constructor, same margins) — see [R6](./research.md#r6--widget-address-row-integration). The Offline Address page is a `DropDownReceiver` consistent with feature 002's `TwCoordGotoReceiver`. The Settings toggles use `SwitchPreference` consistent with the prior `PanListPreference` rows. A new entry will be authored under `docs/ui/offline-address-page.md`; the existing `docs/ui/readout-widget.md` and `docs/ui/settings-fragment.md` will get an "Address row" section. |
| IV | **Performance** | **PASS with measurement obligation** | Spec SC-002 (1 s median address refresh), SC-003 (≤ 180 s placeholder for Taichung-scale ~500–600 MB file plus ~150–250 MB R*Tree build; tighten after T057 measurement), SC-004 (zero footprint when off), SC-005 (2 s recovery), SC-006 (≥ 95 % non-empty resolves over 1000 scripted lookups) are explicit. [Research R3](./research.md#r3--reverse-lookup-spatial-index-rtree-built-at-plugin-import) defends the lookup budget via R*Tree bbox-then-haversine built once at import; [R7](./research.md#r7--threading-model-debounce--executor) defends the runtime debounce + coalescing; [R8](./research.md#r8--atomic-dataset-activation) defends recovery via the staging→rename atomicity. [Quickstart §6](./quickstart.md#6-performance-smoke-tests) lays out the measurement procedure on the reference device. |
| V | **Documentation & Knowledge Preservation** | **PASS** | English-only artefacts (spec / plan / research / data-model / contracts / quickstart / future ADRs). Per Constitution V's post-implement ADR cadence, `docs/adr/0015-offline-address-implementation.md` will be authored after `/speckit-implement` completes. A pre-implementation reconnaissance ADR (`docs/adr/0014-offline-address-reconnaissance.md`) will be authored alongside Phase 0 research. `docs/ui/offline-address-page.md` (new), plus updates to `docs/ui/readout-widget.md` and `docs/ui/settings-fragment.md`, will land alongside the implementation. |
| VI | **Host-Process Isolation** (NON-NEGOTIABLE) | **PASS with mandatory audit** | [Research R10](./research.md#r10--constitution-vi-compliance-audit) enumerates the 11 new entry points: `OfflineAddressReceiver.onReceive` / `onDropDownVisible` / `onDropDownClose` / `onDropDownSizeChanged`, `OfflineAddressTool` constructor / dispose, 3 new `SwitchPreference` change listeners, `AddressSubsystem.onCoordRefresh`, `AddressBundleImporter`'s SAF-result-handler callback. `tasks.md` will include an explicit "Constitution VI guard pass" step in the Polish phase. Existing `TwCoordWidget` rendering path is untouched (the new `renderAddresses(...)` helper is wrapped internally). `/speckit-analyze` is configured to flag any unguarded entry point as CRITICAL. |

**Workflow gates** (Development Workflow & Quality Gates section):

- **Subagent delegation**: SDK reconnaissance for this feature is small (~6 classes —
  `MapView`, `FileSystemUtils`, `DropDownReceiver`, `AtakBroadcast`, `AbstractPluginTool`,
  `PreferenceManager`) and is done directly rather than via a subagent. Result is captured in
  ADR-0014-reconnaissance + research.md, so the artefacts persist outside the conversation.
- **Formatter**: `./gradlew :app:spotlessApply` will run after every code modification.
- **Crash isolation** (Principle VI): see the audit obligation in the Constitution Check row
  above.
- **ADR cadence**: ADR-0014-reconnaissance authored alongside Phase 0; ADR-0015-implementation
  authored after `/speckit-implement`.
- **UI docs cadence**: `docs/ui/offline-address-page.md` (new) + updates to
  `docs/ui/readout-widget.md` and `docs/ui/settings-fragment.md` alongside layout XMLs.
- **Definition of Done**: `tasks.md` will include format / lint / test / docs / Constitution VI
  as completion criteria.

**Result**: No violations. No entries needed in **Complexity Tracking**.

## Project Structure

### Documentation (this feature)

```text
specs/004-offline-address/
├── plan.md              # This file
├── research.md          # Phase 0 — SDK / OS reconnaissance + decision log (R1..R12)
├── data-model.md        # Phase 1 — entities + persistence + state machines + SQLite schema
├── quickstart.md        # Phase 1 — local validation steps (build, side-load sample bundle, smoke)
├── contracts/           # Phase 1 — typed contracts
│   ├── address-bundle-importer.md      # SAF + SHA-256 + atomic activate
│   ├── address-database-facade.md      # SDK seam: SQLiteDatabase reader
│   ├── address-resolver.md             # WGS-84 → AddressLookupResult algorithm
│   ├── offline-address-page.md         # DropDownReceiver UI contract
│   ├── widget-address-rows.md          # TwCoordWidget extension contract
│   └── address-preferences.md          # 3 booleans + dataset-presence hint logic
├── checklists/
│   └── requirements.md  # produced by /speckit-specify
└── tasks.md             # Phase 2 output (NOT created by /speckit-plan)
```

### Source Code (repository root)

The plugin is a single Android module `app/`. This feature adds a new sub-package
`com.atakmap.android.twcoord.address` (consistent with `gotopage/` from feature 002) and extends
the existing `TwCoordWidget`, `TwCoordMapComponent`, `TwCoordPreferenceFragment`,
`PreferenceStore` classes.

```text
app/src/main/java/com/atakmap/android/twcoord/
├── coord/                              # existing — forward converters (untouched)
├── gotopage/                           # existing — untouched
├── i18n/                               # existing — locale override (reused unchanged)
├── plugin/
│   ├── R.java                          # generated — untouched
│   ├── TwCoordGotoTool.java            # existing — untouched
│   ├── TwCoordLifecycle.java           # MODIFIED — registers OfflineAddressTool
│   ├── TwCoordTool.java                # existing — untouched
│   └── OfflineAddressTool.java         # NEW — AbstractPluginTool, third Tools-menu entry
├── prefs/
│   ├── PreferenceStore.java            # MODIFIED — +3 boolean accessors, +listener fan-out
│   │                                   #   for the 3 new keys (so the widget refreshes when
│   │                                   #   toggled)
│   └── UserPreference.java             # MODIFIED — +3 booleans
├── address/                            # NEW package
│   ├── AddressDataset.java             # NEW — value: { genMetadata, importedManifest, dbFile }
│   ├── GeneratorMetadata.java          # NEW — value mapping the in-DB `metadata` table
│   │                                   #   (schema_version, county, data_date, source, csv_sha256, ...)
│   ├── ImportedManifest.java           # NEW — value for plugin-side `imported.manifest.txt`
│   │                                   #   (imported_at, file_sha256, rtree_built, plugin_schema_version)
│   ├── AddressRecord.java              # NEW — value: { lat, lon, displayName, displayNameHalfwidth }
│   ├── AddressLookupResult.java        # NEW — sealed-ish: Found(AddressRecord) | Empty | NoDataset
│   ├── AddressRowState.java            # NEW — sealed-ish: Hidden | Loading | Text(String) | EmptyState
│   ├── AddressBundleImporter.java      # NEW — SAF read + SHA-256 + R*Tree build + atomic activate
│   ├── FileSystem.java                 # NEW — JVM-mockable interface seam for importer
│   ├── ShaCalculator.java              # NEW — JVM-mockable interface seam for importer
│   ├── AddressDatabaseFacade.java      # NEW — interface (SDK seam) — reads metadata + spatial lookups
│   ├── SqliteAddressDatabase.java      # NEW — production AddressDatabaseFacade
│   ├── AddressResolver.java            # NEW — composes facade + haversine refine
│   ├── AddressSubsystem.java           # NEW — lifecycle owner: executor, debouncer, listener fan-out
│   ├── OfflineAddressReceiver.java     # NEW — DropDownReceiver; Import / Remove / metadata page
│   ├── OfflineAddressFilePickerActivity.java  # NEW — transparent SAF shim Activity hosting
│   │                                   #   ActivityResultLauncher for ACTION_OPEN_DOCUMENT
│   ├── AtakFileSystem.java             # NEW — production FileSystem impl wrapping
│   │                                   #   FileSystemUtils.getItem(...) + java.nio.file.Files
│   │                                   #   (sweeps orphan .staging-<UUID>/ dirs on construction)
│   ├── MessageDigestShaCalculator.java # NEW — production ShaCalculator impl wrapping
│   │                                   #   MessageDigest.getInstance("SHA-256")
│   └── OfflineAddressIntents.java      # NEW — action constants (parallel to TwCoordGotoIntents)
├── SelfMarkerSubscriber.java           # existing — untouched
├── TwCoordMapComponent.java            # MODIFIED — registers OfflineAddressReceiver, owns
│                                       #   AddressSubsystem; per-row gating in the existing
│                                       #   render paths
├── TwCoordPreferenceFragment.java      # MODIFIED — 3 new SwitchPreferences; dataset-presence hint
└── TwCoordWidget.java                  # MODIFIED — +3 sibling TextWidget instances,
                                        #   +renderAddresses(...), per-row visibility gating

app/src/main/res/
├── drawable/
│   └── ic_offline_address.xml          # NEW — 24 dp vector for the 3rd Tools-menu entry
├── layout/
│   ├── offline_address_page.xml        # NEW — DropDownReceiver content (empty-state + metadata
│   │                                   #   + Import / Remove buttons)
│   └── (existing layouts unchanged)
├── xml/
│   └── preferences.xml                 # MODIFIED — +new PreferenceCategory "Offline Address"
│                                       #   with 3 SwitchPreference entries + the page-open shortcut
├── values/strings.xml                  # MODIFIED — +~14 new keys
├── values-zh-rTW/strings.xml           # MODIFIED — zh-TW translations
└── values-ja/strings.xml               # MODIFIED — ja translations

app/src/main/AndroidManifest.xml        # MODIFIED — no new permissions; OfflineAddressReceiver
                                        #   is registered programmatically via AtakBroadcast
                                        #   (same pattern as TwCoordGotoReceiver); adds one
                                        #   <activity android:name=".address.OfflineAddressFilePickerActivity"
                                        #   android:exported="false"
                                        #   android:theme="@android:style/Theme.Translucent.NoTitleBar"/>
                                        #   for the SAF picker shim

app/src/test/java/com/atakmap/android/twcoord/address/
├── AddressBundleImporterTest.java      # NEW — 10 tests per contracts/address-bundle-importer.md
├── AddressDatabaseFacadeTest.java      # NEW — 6 tests per contracts/address-database-facade.md
├── AddressResolverTest.java            # NEW — 8 tests (bbox math, haversine refine, debouncing,
│                                       #   coalescing) per contracts/address-resolver.md
├── AddressSubsystemTest.java           # NEW — 6 tests (lifecycle, per-row gating)
└── AddressPreferencesTest.java         # NEW — 4 tests per contracts/address-preferences.md

app/src/test/java/com/atakmap/android/twcoord/
├── (feature 001–003 tests — untouched)
└── TwCoordWidgetAddressRowTest.java    # NEW — 5 tests per contracts/widget-address-rows.md

app/src/androidTest/java/com/atakmap/android/twcoord/address/
├── OfflineAddressImportEspressoTest.java       # NEW — end-to-end Flow A (US1)
└── OfflineAddressMissingDataEspressoTest.java  # NEW — end-to-end Flow D (US4)

docs/
├── adr/
│   ├── 0001 … 0013                     # existing
│   ├── 0014-offline-address-reconnaissance.md  # NEW — Phase 0 reconnaissance
│   └── 0015-offline-address-implementation.md  # NEW — authored after /speckit-implement
└── ui/
    ├── offline-address-page.md         # NEW — Offline Address page (Import / Remove / metadata)
    ├── readout-widget.md               # MODIFIED — +Address-row section, per-row gating screenshots
    └── settings-fragment.md            # MODIFIED — +Address-row toggles section
```

**Structure Decision**: introduce a new `address/` sub-package rather than colocating with
`gotopage/` or top-level. Rationale: the address subsystem is conceptually independent from the
GoTo input page (no shared types, no shared lifecycle, no shared persistence besides the same
preferences file). The seam discipline that keeps the picker testable on the JVM lives in the
two `*Facade` interfaces (`AddressDatabaseFacade`, plus the importer's injected `FileSystem` /
`ShaCalculator`), not in package boundaries. The widget extension lives in the existing
`TwCoordWidget` because the address rows are conceptually part of the readout widget, not a
separate widget — keeping them collocated avoids cross-package dependencies for a tightly
coupled rendering concern.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations. Table intentionally empty.
