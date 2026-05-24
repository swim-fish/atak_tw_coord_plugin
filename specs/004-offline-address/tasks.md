---

description: "Task list for feature 004-offline-address"
---

# Tasks: Offline Address Lookup — Import, Display, and Settings Toggle

**Input**: Design documents from `/specs/004-offline-address/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: REQUIRED. Constitution Principle II is non-negotiable — tests come before the production code that satisfies them. Test tasks appear before the implementation tasks they cover, in every user-story phase.

**Constitution VI**: Every task that adds a new host-callable callback (`BroadcastReceiver.onReceive`, `DropDownReceiver.onDropDownVisible` / `onDropDownClose` / `onDropDownSizeChanged`, `Preference.OnPreferenceClickListener`, `Preference.OnPreferenceChangeListener`, `Runnable.run` on the import worker, `ScheduledFuture` callback, `OnSharedPreferenceChangeListener.onSharedPreferenceChanged`, etc.) MUST add the outer `try/catch (Throwable)` guard in the same change. Polish phase T056 is a final audit pass; **do not defer the guards to that pass**.

**Organization**: Tasks are grouped by user story. US1 and US2 are both Priority P1 and gate the feature's headline value; US3 (P2) ships the toggles that turn US2's render path on; US4 (P3) is robustness. Foundational phase MUST complete before any user-story phase begins.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel — different files, no dependency on a still-pending task
- **[Story]**: Which user story this task belongs to (US1 / US2 / US3 / US4)
- Each task includes the absolute file path

## Path Conventions

- **Production code**: `app/src/main/java/com/atakmap/android/twcoord/` (new sub-package `address/`)
- **Layouts / drawables / strings**: `app/src/main/res/`
- **JVM unit tests**: `app/src/test/java/com/atakmap/android/twcoord/`
- **Instrumented (Espresso) tests**: `app/src/androidTest/java/com/atakmap/android/twcoord/`
- **Docs**: `docs/` (ADRs, UI docs)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: minimal — Gradle module, formatter, and test scaffolding all exist from features 001 / 002 / 003. The only "setup" this feature introduces is a placeholder drawable, a colour resource, a stub layout for the new DropDownReceiver page, and string-resource placeholders so foundational tasks compile.

- [ ] T001 [P] Add a placeholder 24 dp vector drawable for the **Offline Address** Tools-menu entry at `app/src/main/res/drawable/ic_offline_address.xml` (simple address/map-pin glyph; final styling refined in T058)
- [ ] T002 [P] Add a new colour resource `<color name="address_row_text">#FFBBBBBB</color>` (muted neutral) to `app/src/main/res/values/colors.xml` per [contracts/widget-address-rows.md § State→render rules](./contracts/widget-address-rows.md#new-public-api)
- [ ] T003 [P] Create a stub `app/src/main/res/layout/offline_address_page.xml` containing the two-state container (LinearLayout root, State-A child group, State-B child group, both `visibility=gone` by default) so foundational `OfflineAddressReceiver` work compiles. Final field bindings and styling land in T029
- [ ] T004 [P] Add English placeholders for every new string key consumed by foundational + US1 + US3 tasks to `app/src/main/res/values/strings.xml`: `tool_offline_address_label`, `tool_offline_address_desc`, `offline_address_page_title`, `offline_address_empty_state`, `offline_address_button_import`, `offline_address_button_replace`, `offline_address_button_remove`, `offline_address_confirm_replace`, `offline_address_confirm_remove`, `offline_address_progress_copying`, `offline_address_progress_verifying`, `offline_address_progress_building_index`, `offline_address_progress_activating`, `offline_address_field_county`, `offline_address_field_data_date`, `offline_address_field_source`, `offline_address_field_rows`, `offline_address_field_csv_sha`, `offline_address_field_imported_at`, `offline_address_field_file_sha`, `offline_address_field_rtree_built`, `offline_address_error_not_openable`, `offline_address_error_missing_metadata`, `offline_address_error_missing_required_key`, `offline_address_error_unsupported_schema`, `offline_address_error_missing_places`, `offline_address_error_unexpected_columns`, `offline_address_error_rtree_failed`, `offline_address_error_disk_full`, `offline_address_error_activation_failed`, `offline_address_error_io`, `pref_address_header`, `pref_address_row_me_title`, `pref_address_row_me_summary`, `pref_address_row_target_title`, `pref_address_row_target_summary`, `pref_address_row_map_title`, `pref_address_row_map_summary`, `pref_address_dataset_status_title`, `pref_address_dataset_status_summary_none`, `pref_address_dataset_status_summary_hint`, `pref_address_dataset_status_summary_active_format`, `widget_address_loading`, `widget_address_empty_state`. Use English so the build passes; locale parity in T059 / T060
- [ ] T005 [P] Mirror the new keys from T004 into `app/src/main/res/values-zh-rTW/strings.xml` and `app/src/main/res/values-ja/strings.xml` with empty `""` values (placeholders are replaced with proofread translations in T059 / T060 — keeping the keys present early so the locale-override pathway exercises them in tests)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: value classes + sealed types + JVM-mockable seams + preference accessors + Tools-menu tool registration — everything every user story needs to compile. No story-specific UI work happens here; that's Phase 3+.

**⚠️ CRITICAL**: No user-story phase can begin until this phase is complete.

### Value classes

- [ ] T006 [P] Create `GeneratorMetadata` immutable value class (fields per [data-model.md §4.1](./data-model.md#41-value-classes); `equals`/`hashCode` on all fields; `raw` is `Collections.unmodifiableMap(...)`) at `app/src/main/java/com/atakmap/android/twcoord/address/GeneratorMetadata.java`
- [ ] T007 [P] Create `ImportedManifest` immutable value class (fields: `importedAt`, `fileSha256`, `rtreeBuilt`, `pluginSchemaVersion`) at `app/src/main/java/com/atakmap/android/twcoord/address/ImportedManifest.java` per [data-model.md §4.1](./data-model.md#41-value-classes)
- [ ] T008 [P] Create `AddressDataset` immutable value class (fields: `rootDir`, `dbFile`, `generator`, `imported`) at `app/src/main/java/com/atakmap/android/twcoord/address/AddressDataset.java`
- [ ] T009 [P] Create `AddressRecord` immutable value class (fields: `lat`, `lon`, `displayName`, `displayNameHalfwidth`) at `app/src/main/java/com/atakmap/android/twcoord/address/AddressRecord.java`

### Sealed result / state types

- [ ] T010 [P] Create `AddressLookupResult` sealed interface with `Found(AddressRecord)`, `Empty`, `NoDataset` permits at `app/src/main/java/com/atakmap/android/twcoord/address/AddressLookupResult.java` per [data-model.md §4.2](./data-model.md#42-sealed-ish-result-types). Use Java 17 sealed interfaces + records / enums
- [ ] T011 [P] Create `AddressRowState` sealed interface with `Hidden`, `Loading`, `Text(String)`, `EmptyState` permits at `app/src/main/java/com/atakmap/android/twcoord/address/AddressRowState.java`

### JVM-mockable seams (importer)

- [ ] T012 [P] Create `FileSystem` interface at `app/src/main/java/com/atakmap/android/twcoord/address/FileSystem.java` exposing the minimum file-ops the importer uses: `Path getActiveDir()`, `Path createStagingDir()`, `OutputStream openWrite(Path)`, `void atomicMove(Path src, Path dst)`, `void deleteRecursively(Path)`, `boolean exists(Path)`. Production implementation wraps `com.atakmap.coremap.filesystem.FileSystemUtils.getItem(...)` + `java.nio.file.Files` per [research.md R2](./research.md#r2--atak-managed-plugin-data-directory)
- [ ] T013 [P] Create `ShaCalculator` interface at `app/src/main/java/com/atakmap/android/twcoord/address/ShaCalculator.java` exposing `OutputStream tapping(OutputStream sink)` (returns a wrapping stream that updates the digest as bytes pass through) and `String finalDigestHex()`. Production implementation wraps `java.security.MessageDigest.getInstance("SHA-256")`

### SDK seam (database facade)

- [ ] T014 [P] Create `AddressDatabaseFacade` interface at `app/src/main/java/com/atakmap/android/twcoord/address/AddressDatabaseFacade.java` per [contracts/address-database-facade.md § Type signature](./contracts/address-database-facade.md#type-signature). Also create nested `Factory` interface so `AddressSubsystem` can open a new facade on dataset change without importing `Context` directly

### Action constants

- [ ] T015 [P] Create `OfflineAddressIntents` final class at `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressIntents.java` exposing `ACTION_SHOW_OFFLINE_ADDRESS`, `ACTION_PICK_FILE_RESULT` (extra key `EXTRA_PICKED_URI`), `ACTION_DATASET_CHANGED` (plugin-internal). Constants only; no behaviour

### Preference extensions

- [ ] T016 Author `AddressPreferencesTest` JVM unit tests at `app/src/test/java/com/atakmap/android/twcoord/address/AddressPreferencesTest.java` covering all 4 cases in [contracts/address-preferences.md § Test plan](./contracts/address-preferences.md#test-plan-addresspreferencestest-jvm). Mock `SharedPreferences` via Mockito. Tests MUST fail (accessors don't exist yet)
- [ ] T017 Extend `app/src/main/java/com/atakmap/android/twcoord/prefs/PreferenceStore.java` with the three new key constants (`KEY_ADDRESS_ROW_ME`, `KEY_ADDRESS_ROW_TARGET`, `KEY_ADDRESS_ROW_MAP`), getters (`getAddressRowMe` / `getAddressRowTarget` / `getAddressRowMap`), setters, and extend the `spListener` condition to call `fireAll()` when any of the three keys changes per [contracts/address-preferences.md § PreferenceStore additions](./contracts/address-preferences.md#preferencestore-additions). Re-run T016 — all 4 tests MUST pass
- [ ] T018 Extend `app/src/main/java/com/atakmap/android/twcoord/prefs/UserPreference.java` record with three new boolean fields (`addressRowMe`, `addressRowTarget`, `addressRowMap`); update `defaults()` to return all `false`. Update `PreferenceStore.snapshot()` to read the three new fields. (Existing prefs subscribers in `TwCoordMapComponent` will compile but won't react until T038 — that's by design)

### Plugin tool registration

- [ ] T019 Create `OfflineAddressTool extends AbstractPluginTool implements Disposable` at `app/src/main/java/com/atakmap/android/twcoord/plugin/OfflineAddressTool.java` mirroring `TwCoordTool` / `TwCoordGotoTool` exactly — constructor takes `Context`, calls `super(context, R.string.tool_offline_address_label, R.string.tool_offline_address_desc, R.drawable.ic_offline_address, OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS)`; `dispose()` empty body wrapped in `try/catch (Throwable)` per Constitution VI
- [ ] T020 Modify `app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordLifecycle.java` `onStart()` (or its tool-registration equivalent — verify against the existing two registrations) to also register an `OfflineAddressTool` instance alongside the existing `TwCoordTool` and `TwCoordGotoTool`. Confirm the 3rd Tools-menu entry appears after rebuilding (smoke-tested in T030)

**Checkpoint**: Foundation ready — every user-story phase can now begin.

---

## Phase 3: User Story 1 — Side-load an offline-address bundle through Tools → Offline Address (Priority: P1) 🎯 MVP

**Goal**: Operator opens Tools → **Offline Address** → Import → picks a `places-<county>.sqlite` → the plugin validates, builds the R*Tree, atomically activates, and shows the dataset's metadata on the page. The address row does not yet appear on the map (that's US2/US3); shipping just US1 already lets operators install / replace / inspect the dataset, which is intrinsic value.

**Independent Test**: per [quickstart.md § 3 Acceptance Flow A](./quickstart.md#3-acceptance-flow-a--us1-import-a-bundle) — operator imports `places-taichung.sqlite`, sees the State-B page populated with `county=台中市`, `data_date=115-01`, `rows=1,316,674`, file SHA-256, R*Tree built within 60 s (SC-003).

### Tests for User Story 1

> **NOTE**: Write these tests FIRST, ensure they FAIL before implementation.

- [ ] T021 [P] [US1] Author `AddressBundleImporterTest` JVM unit tests at `app/src/test/java/com/atakmap/android/twcoord/address/AddressBundleImporterTest.java` covering all 10 cases in [contracts/address-bundle-importer.md § Test plan](./contracts/address-bundle-importer.md#test-plan-addressbundleimportertest-jvm-junit-4). Tests use mock `FileSystem` (in-memory) + mock `ShaCalculator` + small SQLite fixture bytes (assemble via a test-only helper that calls `org.xerial:sqlite-jdbc` if needed, OR a tiny prebuilt fixture in `app/src/test/resources/offline-address/`). Tests MUST fail
- [ ] T022 [P] [US1] Author `OfflineAddressReceiverTest` Robolectric unit tests at `app/src/test/java/com/atakmap/android/twcoord/address/OfflineAddressReceiverTest.java` covering all 8 cases in [contracts/offline-address-page.md § Test plan](./contracts/offline-address-page.md#test-plan-offlineaddressreceivertest-robolectric). Tests MUST fail
- [ ] T023 [P] [US1] Author `OfflineAddressImportEspressoTest` instrumented test at `app/src/androidTest/java/com/atakmap/android/twcoord/address/OfflineAddressImportEspressoTest.java` covering Acceptance Flow A end-to-end (ADB-push a fixture `.sqlite`, simulate SAF result, assert State-B fields populated). Test MUST fail

### Importer implementation

- [ ] T024 [US1] Implement `AddressBundleImporter` at `app/src/main/java/com/atakmap/android/twcoord/address/AddressBundleImporter.java` per [contracts/address-bundle-importer.md](./contracts/address-bundle-importer.md): constructor takes `(FileSystem, ShaCalculator, int pinnedSchemaVersion)`; `importFrom(InputStream, ProgressListener)` runs the 10-step staged-then-rename sequence per the contract; `removeActive()` is idempotent; `activeOrNull()` reads `active/imported.manifest.txt` if present and returns `null` cleanly on any IO error. Outer `try/catch (Throwable)` in `importFrom`'s body translates every exception to `Failure(IO_ERROR, ex.getMessage())` per Constitution VI. Re-run T021 — all 10 tests MUST pass

### R*Tree build helper

- [ ] T025 [US1] Add a private helper `buildRTreeIfAbsent(File dbFile, ProgressListener listener)` inside `AddressBundleImporter` (or as a package-private sibling class `RTreeBuilder` if extraction helps readability) implementing the SQL recipe from [data-model.md §1.5](./data-model.md#15-rtree-plugin-built-at-import--see-researchmd-r3): open DB read-write, `CREATE VIRTUAL TABLE IF NOT EXISTS places_rtree`, `INSERT … WHERE NOT EXISTS`, `ANALYZE places_rtree`, close DB. Periodic `listener.onProgress(BUILDING_RTREE, n, total)` every ~5000 rows. On any SQL failure return `Failure(RTREE_BUILD_FAILED, ...)` and let the caller wipe staging

### SAF result trampoline

- [ ] T026 [US1] Create `OfflineAddressFilePickerActivity` at `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressFilePickerActivity.java` — a transparent shim Activity registered in `AndroidManifest.xml` (no exported intent filter; launched explicitly from `OfflineAddressReceiver`). It hosts the SAF `ActivityResultLauncher` for `ACTION_OPEN_DOCUMENT`, receives the picked `content://` URI, broadcasts `OfflineAddressIntents.ACTION_PICK_FILE_RESULT` with `EXTRA_PICKED_URI` via `AtakBroadcast`, then `finish()`. Lifecycle methods wrapped in `try/catch (Throwable)` per Constitution VI

### Receiver + page

- [ ] T027 [US1] Implement `OfflineAddressReceiver extends DropDownReceiver` at `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java` per [contracts/offline-address-page.md](./contracts/offline-address-page.md): constructor takes `(MapView, Context pluginCtx, AddressBundleImporter importer, ExecutorService importExecutor)`; `onReceive` shows the drop-down; `onDropDownVisible` binds State-A or State-B from `importer.activeOrNull()`; Import button launches the `OfflineAddressFilePickerActivity`; SAF result handler (registered in `onDropDownVisible`, unregistered in `onDropDownClose`) opens the URI's `InputStream` via `ContentResolver`, submits the import job to `importExecutor`; on `Success`/`Failure` posts back to the UI thread and re-binds the page. All 5 lifecycle callbacks wrapped in `try/catch (Throwable)` per Constitution VI (entry points 1, 2, 3, 4, 5 in [research.md R10](./research.md#r10--constitution-vi-compliance-audit))
- [ ] T028 [US1] Implement Replace and Remove flows in `OfflineAddressReceiver` per [contracts/offline-address-page.md § Replace flow / Remove flow](./contracts/offline-address-page.md#replace-flow): both prompt a plain-Android `AlertDialog` confirmation; on confirm, Replace runs the same Import job after `removeActive()`; Remove calls `importer.removeActive()` directly. After every state change, broadcast `OfflineAddressIntents.ACTION_DATASET_CHANGED` so listeners (T037) refresh

### Final page layout

- [ ] T029 [US1] Replace the T003 stub `offline_address_page.xml` with the final two-state layout per [contracts/offline-address-page.md § Page layout](./contracts/offline-address-page.md#page-layout-offline_address_pagexml). Plain-Android widgets only per ADR-0009 D6; field labels reference the strings added in T004. Re-run T022 — all 8 Robolectric tests MUST pass

### Wiring

- [ ] T030 [US1] Modify `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java` `onCreate` to construct the importer (`new AddressBundleImporter(new AtakFileSystem(), new MessageDigestShaCalculator(), 1)`), a dedicated import `ExecutorService` (single-thread, named `"twcoord-address-import"`), and the `OfflineAddressReceiver`; register the receiver for `ACTION_SHOW_OFFLINE_ADDRESS`. Symmetric `onDestroyImpl` cleanup (unregister, executor shutdown, receiver dispose). Add the production `FileSystem` and `ShaCalculator` impls (`AtakFileSystem.java`, `MessageDigestShaCalculator.java`) in the `address/` package
- [ ] T031 [US1] Run T023 (Espresso Flow A) against the reference device or emulator. MUST pass within the SC-003 budget (≤ 60 s total for a Taichung-scale fixture; if no full-scale fixture is available locally, use a 10-county-subset fixture and document the scaling)

**Checkpoint**: US1 complete — operator can install / replace / remove a dataset and see its metadata. Address row on the map is still hidden (US2 + US3 pending).

---

## Phase 4: User Story 2 — Address row appears under the existing coordinate readout (Priority: P1)

**Goal**: With at least one per-row toggle on (gated by US3) and a dataset active (US1), the existing `TwCoordWidget` renders an address row immediately under the corresponding coordinate row, updating within 1 s of the underlying coordinate stabilising (SC-002).

**Note on independence**: this story's implementation can complete and be unit-tested before US3 ships the UI toggles — the gating is via `PreferenceStore.getAddressRowMe()`/`...Target()`/`...Map()` getters that already exist after Phase 2. Tests in this phase set the preferences programmatically. The user-visible verification waits for US3's Espresso Flow B (T053).

**Independent Test**: with prefs set programmatically to `addressRowMe = true` and a fixture dataset active, panning into a Taichung urban area causes the ME row's address line to populate within 1 s (verified by `AddressSubsystemTest` + `TwCoordWidgetAddressRowTest`; Espresso Flow B in T053 is the end-to-end verification).

### Tests for User Story 2

> **NOTE**: Write these tests FIRST, ensure they FAIL before implementation.

- [ ] T032 [P] [US2] Author `AddressDatabaseFacadeTest` JVM/Robolectric tests at `app/src/test/java/com/atakmap/android/twcoord/address/AddressDatabaseFacadeTest.java` covering all 6 cases in [contracts/address-database-facade.md § Test plan](./contracts/address-database-facade.md#test-plan-addressdatabasefacadetest-jvm). Uses `org.xerial:sqlite-jdbc` (test-only dep; add to `app/build.gradle testImplementation` if not present) for an in-memory SQLite fixture with 3 known rows at known distances. Tests MUST fail
- [ ] T033 [P] [US2] Author `AddressResolverTest` JVM unit tests at `app/src/test/java/com/atakmap/android/twcoord/address/AddressResolverTest.java` covering all 8 cases in [contracts/address-resolver.md § Test plan](./contracts/address-resolver.md#addressresolvertest-8-tests). Mock `AddressDatabaseFacade`. Tests MUST fail
- [ ] T034 [P] [US2] Author `AddressSubsystemTest` JVM unit tests at `app/src/test/java/com/atakmap/android/twcoord/address/AddressSubsystemTest.java` covering all 6 cases in [contracts/address-resolver.md § AddressSubsystemTest](./contracts/address-resolver.md#addresssubsystemtest-6-tests). Use a `TestScheduledExecutorService` (controlled clock) so debounce timing is deterministic. Tests MUST fail
- [ ] T035 [P] [US2] Author `TwCoordWidgetAddressRowTest` JVM/Robolectric tests at `app/src/test/java/com/atakmap/android/twcoord/TwCoordWidgetAddressRowTest.java` covering all 5 cases in [contracts/widget-address-rows.md § Test plan](./contracts/widget-address-rows.md#test-plan-twcoordwidgetaddressrowtest-jvm-via-robolectric). Tests MUST fail

### Database facade implementation

- [ ] T036 [US2] Implement `SqliteAddressDatabase implements AddressDatabaseFacade` at `app/src/main/java/com/atakmap/android/twcoord/address/SqliteAddressDatabase.java` per [contracts/address-database-facade.md § Behaviour](./contracts/address-database-facade.md#behaviour-nearestwithinlat-lon-radius): constructor takes `File dbFile`, opens with `OPEN_READONLY | NO_LOCALIZED_COLLATORS`; `readMetadata()` reads the `metadata` table verbatim (every key into `raw`, mandatory keys into typed fields); `nearestWithin(lat, lon, radiusMeters)` runs the bbox JOIN + haversine refine. Outer `try/catch (Throwable)` in every public method returns the safe-default per the contract. Provide a `Factory` impl that opens a fresh `SqliteAddressDatabase` per active dataset. Re-run T032 — all 6 tests MUST pass

### Resolver + subsystem

- [ ] T037 [US2] Implement `AddressResolver` (pure compute) and `AddressSubsystem` (lifecycle owner, executor, debounce, per-row coalescing) at `app/src/main/java/com/atakmap/android/twcoord/address/AddressResolver.java` and `app/src/main/java/com/atakmap/android/twcoord/address/AddressSubsystem.java` per [contracts/address-resolver.md](./contracts/address-resolver.md). The subsystem subscribes to `PreferenceStore` for the three new keys, listens for `ACTION_DATASET_CHANGED`, opens / closes the facade via the `Factory`, and fans out `AddressRowState` transitions to registered listeners. Every callback (scheduled task body, preference-change reaction, dataset-change reaction) wrapped in `try/catch (Throwable)` per Constitution VI. Re-run T033 + T034 — all 14 tests MUST pass

### Widget extension

- [ ] T038 [US2] Extend `app/src/main/java/com/atakmap/android/twcoord/TwCoordWidget.java` per [contracts/widget-address-rows.md](./contracts/widget-address-rows.md): add three sibling `TextWidget` fields (`mapAddrRow`, `meAddrRow`, `targetAddrRow`), each constructed via the existing `newStyledTextWidget(...)` factory and appended to the same anchor as its coord-row sibling; add the new public `renderAddresses(...)` method with the `equals`-coalesce optimisation matching the existing `render(...)`; extend `setVisible(boolean)` to cover the new rows; extend `detach()` to remove all six rows. Both `render(...)` (existing) and `renderAddresses(...)` (new) have outer `try/catch (Throwable)` per Constitution VI. Re-run T035 — all 5 tests MUST pass

### Map component wiring

- [ ] T039 [US2] Modify `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java` `onCreate` to construct an `AddressSubsystem` (depends on the `AddressBundleImporter` from T030, the `SqliteAddressDatabase.Factory`, and a dedicated `ScheduledExecutorService`), register it as a `PreferenceStore.Listener`, register a `BroadcastReceiver` for `OfflineAddressIntents.ACTION_DATASET_CHANGED` that calls `subsystem.onActiveDatasetChanged()`, and subscribe a `Listener` that calls `widget.renderAddresses(mapState, meState, tgtState)` on the UI thread. Symmetric `onDestroyImpl` cleanup. Extend the existing render paths (`renderMapCentre`, `subListener.onFreshFix`, `renderTargetFrom`) to also call `subsystem.onCoord(Row, lat, lon)` so the subsystem schedules a lookup per row. All new callbacks wrapped in `try/catch (Throwable)` per Constitution VI (entry points 8, 9 in [research.md R10](./research.md#r10--constitution-vi-compliance-audit))

**Checkpoint**: US2 implementation complete — unit tests prove the path works. End-to-end verification waits for US3 (toggle UI to flip the gate from the operator's side).

---

## Phase 5: User Story 3 — Per-row Settings toggles enable / disable the address row independently for ME, TGT, MAP (Priority: P2)

**Goal**: Three independent SwitchPreferences in Settings → Tool Preferences → TW Coordinates let the operator turn the address row on / off per coordinate row. Defaults are all off; with all off, the address subsystem stays dormant (SC-004).

**Independent Test**: per [quickstart.md § 4 Acceptance Flow B](./quickstart.md#4-acceptance-flow-b--us2--us3-address-row-appears) — toggle ME on → ME's address row appears within 1 s; toggle MAP on additionally → both ME and MAP rows show address; toggle ME off → only MAP remains.

### Tests for User Story 3

> **NOTE**: Write these tests FIRST, ensure they FAIL before implementation.

- [ ] T040 [P] [US3] Author `TwCoordPreferenceFragmentAddressTest` Robolectric unit test at `app/src/test/java/com/atakmap/android/twcoord/TwCoordPreferenceFragmentAddressTest.java` covering: (a) after `onResume` with no dataset and at least one toggle on, `pref_address_dataset_status` summary equals the "No dataset installed — tap to open Offline Address" string; (b) after `onResume` with a dataset active, summary equals the `Active: <county> · <data_date>` format; (c) clicking the status row sends a `ACTION_SHOW_OFFLINE_ADDRESS` broadcast via `AtakBroadcast.sendBroadcast(...)`; (d) toggling any one of the three SwitchPreferences updates the in-DB pref value via `PreferenceStore.set*`. Tests MUST fail
- [ ] T041 [P] [US3] Author `OfflineAddressFlowBCEspressoTest` instrumented test at `app/src/androidTest/java/com/atakmap/android/twcoord/address/OfflineAddressFlowBCEspressoTest.java` covering [quickstart.md Flow B + Flow C1 + C2 + C3](./quickstart.md#4-acceptance-flow-b--us2--us3-address-row-appears). Test MUST fail

### Preferences XML

- [ ] T042 [US3] Modify `app/src/main/res/xml/preferences.xml` per [contracts/address-preferences.md § preferences.xml additions](./contracts/address-preferences.md#preferencesxml-additions): insert a new `PreferenceCategory` "Offline Address" after the existing accuracy notice, containing three `SwitchPreference` entries (`pref_address_row_me`, `pref_address_row_target`, `pref_address_row_map`, all `defaultValue="false"`) and a `Preference` row `pref_address_dataset_status`

### Settings fragment

- [ ] T043 [US3] Extend `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java` per [contracts/address-preferences.md § TwCoordPreferenceFragment additions](./contracts/address-preferences.md#twcoordpreferencefragment-additions): extend `refreshAllSummaries()` to re-title the new category header + three SwitchPreferences from the localised context; add private `refreshAddressDatasetStatus(Context wrapped)` that reads `AddressBundleImporter.activeOrNull()` (via a static holder pattern the project already uses for `pluginContext`) and updates the status row's summary + visibility per the three states in the contract; wire the status row's `OnPreferenceClickListener` to broadcast `ACTION_SHOW_OFFLINE_ADDRESS`. Lambda body wrapped in `try/catch (Throwable)` per Constitution VI (entry point 7 in [research.md R10](./research.md#r10--constitution-vi-compliance-audit)). Re-run T040 — all 4 Robolectric tests MUST pass

### Espresso verification

- [ ] T044 [US3] Run T041 (Espresso Flow B + C1 + C2 + C3) against the reference device or emulator. MUST pass within the SC-002 budget (median address-row update ≤ 1000 ms across 100 pans; p95 ≤ 2000 ms). Capture the timings in the test's log output for posterity

**Checkpoint**: US3 complete — the headline operator-visible feature works end-to-end. Address rows appear / disappear per per-row toggle; default state on upgrade is unchanged (all toggles off).

---

## Phase 6: User Story 4 — Graceful behaviour on missing, corrupt, or out-of-region data (Priority: P3)

**Goal**: Plugin does not crash and does not lose the existing coordinate readout when the dataset's on-disk files disappear, the dataset's schema is unexpected, or the operator imports a file the importer cannot validate. Tests verify each path; the Polish phase's Constitution VI audit is the final safety net.

**Independent Test**: per [quickstart.md § 6.4 SC-005](./quickstart.md#64-sc-005--recovery-from-missing-files) — delete the active dataset's files via ADB; re-open the map; readout continues normally; Offline Address page recovers to State A; address row stays hidden; re-importing the same file restores it within one refresh cycle.

### Tests for User Story 4

> **NOTE**: Write these tests FIRST, ensure they FAIL before implementation.

- [ ] T045 [P] [US4] Author `OfflineAddressMissingDataEspressoTest` instrumented test at `app/src/androidTest/java/com/atakmap/android/twcoord/address/OfflineAddressMissingDataEspressoTest.java` covering [quickstart.md § 4 Flow C4 + § 6.4](./quickstart.md#c4--wrong-schema-negative-case): import a known-good fixture → assert State B → delete the active directory via `Runtime.exec("rm -rf …")` (or via `FileSystem.deleteRecursively` exposed for tests) → re-open Offline Address page → assert State A within 2 s (SC-005); then import a known-bad fixture (zero-byte file) → assert inline error message contains the localised `offline_address_error_not_openable` string → assert previously-active dataset is unchanged. Test MUST fail

### Implementation

- [ ] T046 [US4] Extend `AddressBundleImporter.activeOrNull()` to gracefully detect "active directory exists but `places.sqlite` is missing or unreadable, or `imported.manifest.txt` is missing or unparseable" as **no active dataset** (return null cleanly) and log at `Log.w` with the specific reason. The directory itself is left in place; the next successful import overwrites it. Re-run T045's "missing files" assertion — MUST pass. (No changes needed to the importer's `Failure` enum — the existing `IO_ERROR` and `MISSING_REQUIRED_METADATA_KEY` reasons already cover the "import a broken file" half.)
- [ ] T047 [US4] Confirm the `AddressSubsystem` already handles `Factory.open(File)` returning null (no active dataset) by emitting `Hidden` for every row regardless of toggle state. If T037's implementation hard-codes a non-null facade assumption, add the null-tolerant branch here. (Should be no-op if T037 followed the [contracts/address-resolver.md § State derivation table](./contracts/address-resolver.md#state-derivation) literally.)
- [ ] T048 [US4] Run T045 Espresso end-to-end. MUST pass. Capture wall-clock recovery time for the "missing files" half; MUST be ≤ 2000 ms per SC-005

**Checkpoint**: US4 complete — robust recovery from every documented failure path.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: documentation, locale parity, performance measurement, final Constitution VI audit, formatter / lint sweeps.

### ADRs (Constitution V)

- [ ] T049 [P] Author `docs/adr/0014-offline-address-reconnaissance.md` capturing the Phase 0 SDK + OS reconnaissance: every `javap -public` output that the research.md decisions cite, the upstream `github.com/TAK-Product-Center/atak-civ` permalinks pinned at first reference, and the cross-verification against the bundled `../ATAK-CIV-5.7.0.3-SDK/main.jar`. Template: same shape as `docs/adr/0010-custom-marker-icon-picker.md`
- [ ] T050 Author `docs/adr/0015-offline-address-implementation.md` (after T031 / T044 / T048 all pass) capturing the actual implementation decisions, anything that diverged from research.md, the measured SC numbers from T053, and the Constitution VI audit result from T056. Template: same shape as `docs/adr/0011-custom-marker-icon-implementation.md`

### UI docs (Constitution III)

- [ ] T051 [P] Author `docs/ui/offline-address-page.md` documenting both visual states (A + B), the Import / Replace / Remove flows, and the localised string keys. Include placeholder screenshots; final screenshots captured after T044
- [ ] T052 [P] Update `docs/ui/readout-widget.md` with a new "Address row" section: per-row gating, "Loading address…" / "No address nearby" empty states, visual weight (muted neutral colour `@color/address_row_text`), and per-row examples for ME / TGT / MAP
- [ ] T053 [P] Update `docs/ui/settings-fragment.md` with a new "Offline Address" section covering the three SwitchPreferences and the dataset-presence status row

### Localisation (Constitution III FR-018)

- [ ] T054 [P] Replace the empty zh-rTW placeholders from T005 with proofread Traditional Chinese (Taiwan) translations for all ~43 new keys in `app/src/main/res/values-zh-rTW/strings.xml`. Use the project's existing zh-rTW translations as a style reference (terminology, fullwidth punctuation in prose, halfwidth inside identifiers / paths)
- [ ] T055 [P] Replace the empty ja placeholders from T005 with proofread Japanese translations for all ~43 new keys in `app/src/main/res/values-ja/strings.xml`. Use the project's existing ja translations as a style reference

### Constitution VI audit

- [ ] T056 Final Constitution VI audit pass: walk through every entry point listed in [research.md R10](./research.md#r10--constitution-vi-compliance-audit) (11 entries), open the corresponding production file, confirm the outer `try/catch (Throwable)` is present and logs via `com.atakmap.coremap.log.Log.w`. Record the audit result (file path + line range per entry point) in T050's ADR. Any unguarded entry point is a CRITICAL bug — fix before continuing

### Performance & verification

- [ ] T057 Run [quickstart.md § 6 Performance smoke tests](./quickstart.md#6-performance-smoke-tests) on the reference device (Galaxy Tab S10+): SC-002 (1 s median address refresh × 100 pans), SC-003 (60 s import for Taichung-scale), SC-004 (zero footprint when all toggles off vs v1.0.4 baseline), SC-005 (2 s recovery from missing files). Record the numbers in T050's ADR; any miss is a regression — investigate and fix before declaring done

### Final polish

- [ ] T058 Replace the T001 placeholder drawable with the final 24 dp picker-glyph vector at `app/src/main/res/drawable/ic_offline_address.xml` (map-pin + magnifying-glass motif, or whatever visual the design lead approves; consistent with the existing two TW Coord tool icons)
- [ ] T059 Run `./gradlew :app:spotlessApply` (Constitution I); confirm zero diff after re-run
- [ ] T060 Run `./gradlew :app:lintCivDebug` (Constitution I); confirm zero new warnings vs the previous build
- [ ] T061 Run the full quickstart pre-PR checklist ([quickstart.md § 8](./quickstart.md#8-pre-pr-checklist)) and confirm every box is ticked before opening the PR

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)**: no dependencies — can start immediately. All five tasks parallel (different files).
- **Phase 2 (Foundational)**: depends on Phase 1. **BLOCKS** every user-story phase.
- **Phase 3 (US1)**: depends on Phase 2.
- **Phase 4 (US2)**: depends on Phase 2. **Can run in parallel with Phase 3** (different files); but its Espresso verification (T044, US3) needs US3's toggle UI to flip the gate from the operator's side.
- **Phase 5 (US3)**: depends on Phase 4 (the widget extension + subsystem) and Phase 3 (the importer, for the dataset-presence summary).
- **Phase 6 (US4)**: depends on Phases 3 + 4 + 5.
- **Phase 7 (Polish)**: depends on Phases 3 + 4 + 5 + 6. T050 + T057 specifically depend on T031 + T044 + T048 all passing.

### Within each user story

- **TDD**: tests authored first and FAIL; implementation follows; tests then PASS.
- **Value classes / sealed types**: parallel within Phase 2 (T006–T011, T012–T015 all [P]).
- **Order within Phase 3**: T021 / T022 / T023 (tests, [P]) → T024 (importer impl) → T025 (R*Tree helper) → T026 (SAF shim) → T027 (receiver) → T028 (Replace/Remove) → T029 (final layout) → T030 (wiring) → T031 (Espresso).
- **Order within Phase 4**: T032 / T033 / T034 / T035 (tests, [P]) → T036 (facade) → T037 (resolver + subsystem) → T038 (widget) → T039 (wiring).
- **Order within Phase 5**: T040 / T041 (tests, [P]) → T042 (prefs xml) → T043 (fragment) → T044 (Espresso).
- **Order within Phase 6**: T045 (test, [P]) → T046 / T047 (impl) → T048 (Espresso).
- **Order within Phase 7**: T049 / T051 / T052 / T053 / T054 / T055 / T058 (parallel docs / drawable / translations) → T056 (audit) → T057 (perf) → T050 (ADR-0015 captures it all) → T059 / T060 (formatter / lint) → T061 (pre-PR).

### Parallel opportunities

- **Phase 1**: T001 / T002 / T003 / T004 / T005 — all 5 in parallel.
- **Phase 2 value classes**: T006 / T007 / T008 / T009 / T010 / T011 — 6 in parallel (different files).
- **Phase 2 seams**: T012 / T013 / T014 / T015 — 4 in parallel (different files).
- **Phase 3 tests**: T021 / T022 / T023 — 3 in parallel.
- **Phase 4 tests**: T032 / T033 / T034 / T035 — 4 in parallel.
- **Phase 5 tests**: T040 / T041 — 2 in parallel.
- **Phase 7 docs / drawable / translations**: T049 / T051 / T052 / T053 / T054 / T055 / T058 — 7 in parallel.
- **Stories**: Phase 3 (US1) and Phase 4 (US2) can run in parallel by two developers after Phase 2 completes. Phase 5 (US3) joins after Phase 4 lands the widget extension.

---

## Parallel Example: Phase 2 Foundational

```bash
# Launch all 6 value classes + 4 seams in parallel:
Task: "Create GeneratorMetadata at app/src/main/java/com/atakmap/android/twcoord/address/GeneratorMetadata.java"
Task: "Create ImportedManifest at app/src/main/java/com/atakmap/android/twcoord/address/ImportedManifest.java"
Task: "Create AddressDataset at app/src/main/java/com/atakmap/android/twcoord/address/AddressDataset.java"
Task: "Create AddressRecord at app/src/main/java/com/atakmap/android/twcoord/address/AddressRecord.java"
Task: "Create AddressLookupResult at app/src/main/java/com/atakmap/android/twcoord/address/AddressLookupResult.java"
Task: "Create AddressRowState at app/src/main/java/com/atakmap/android/twcoord/address/AddressRowState.java"
Task: "Create FileSystem at app/src/main/java/com/atakmap/android/twcoord/address/FileSystem.java"
Task: "Create ShaCalculator at app/src/main/java/com/atakmap/android/twcoord/address/ShaCalculator.java"
Task: "Create AddressDatabaseFacade at app/src/main/java/com/atakmap/android/twcoord/address/AddressDatabaseFacade.java"
Task: "Create OfflineAddressIntents at app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressIntents.java"
```

## Parallel Example: Phase 3 User Story 1 — tests up front

```bash
# Launch all 3 US1 tests in parallel; ALL must fail before any implementation begins:
Task: "Author AddressBundleImporterTest at app/src/test/java/com/atakmap/android/twcoord/address/AddressBundleImporterTest.java"
Task: "Author OfflineAddressReceiverTest at app/src/test/java/com/atakmap/android/twcoord/address/OfflineAddressReceiverTest.java"
Task: "Author OfflineAddressImportEspressoTest at app/src/androidTest/java/com/atakmap/android/twcoord/address/OfflineAddressImportEspressoTest.java"
```

## Parallel Example: Phase 4 User Story 2 — tests up front

```bash
# Launch all 4 US2 tests in parallel; ALL must fail before any implementation begins:
Task: "Author AddressDatabaseFacadeTest at app/src/test/java/com/atakmap/android/twcoord/address/AddressDatabaseFacadeTest.java"
Task: "Author AddressResolverTest at app/src/test/java/com/atakmap/android/twcoord/address/AddressResolverTest.java"
Task: "Author AddressSubsystemTest at app/src/test/java/com/atakmap/android/twcoord/address/AddressSubsystemTest.java"
Task: "Author TwCoordWidgetAddressRowTest at app/src/test/java/com/atakmap/android/twcoord/TwCoordWidgetAddressRowTest.java"
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 Setup → Phase 2 Foundational → Phase 3 US1.
2. **STOP and VALIDATE**: operator can install / replace / remove a dataset and inspect its metadata via Tools → Offline Address. Address row on the map is not yet wired.
3. Deploy / demo if appropriate (the import flow + Offline Address page is a coherent shippable increment).

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. Add US1 → operator manages datasets but no map-side change yet → can ship as a 0.1 milestone.
3. Add US2 (subsystem + widget, prefs-gated) → still invisible without US3 but unit tests green.
4. Add US3 (toggles) → headline feature live; deploy.
5. Add US4 (robustness) → confidence to recommend to power users.
6. Polish phase → ADRs, perf numbers, lint sweep → PR-ready.

### Parallel Team Strategy

With two developers:

1. Both complete Setup + Foundational together (small phase, fast).
2. Once Foundational is done:
   - Developer A: Phase 3 US1 (import flow + page).
   - Developer B: Phase 4 US2 (DB facade + resolver + subsystem + widget).
3. Both contribute to Phase 5 US3 (one does the prefs XML + fragment, the other writes the Espresso).
4. Either picks up Phase 6 US4 (small).
5. Both polish in parallel (T049–T055 are all [P]).

---

## Notes

- [P] = different files, no dependency on a still-pending task. Listed parallel groups are safe to fan out.
- Tests fail before implementation per Constitution II. Every "implementation" task name explicitly says "Re-run TXXX — tests MUST pass" to keep the loop tight.
- Constitution VI guards are noted on every host-callable entry point. T056 is the final audit, not a substitute for inline guards.
- Commit cadence: commit after each task or after each logical group (e.g. "all Phase 2 value classes" as one commit). The `before_*` hooks will offer to commit before every subsequent slash command.
- Avoid: vague tasks, same-file conflicts (PreferenceStore.java is touched by T017 / T018 — sequential, not parallel; TwCoordMapComponent.java is touched by T030 / T039 — sequential), cross-story dependencies that break independence.
