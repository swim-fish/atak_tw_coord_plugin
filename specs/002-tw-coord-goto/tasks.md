---

description: "Task list for feature 002-tw-coord-goto (Taiwan Coordinate Input GoTo Page)"
---

# Tasks: Taiwan Coordinate Input ("GoTo") Page

**Input**: Design documents from `/specs/002-tw-coord-goto/`

**Prerequisites**: plan.md (required), spec.md (required), research.md,
data-model.md, contracts/coordinate-parser.md, contracts/goto-receiver.md,
contracts/recent-store.md, quickstart.md

**Tests**: REQUIRED. Constitution Principle II (TDD) is NON-NEGOTIABLE
in this project. Every implementation task is preceded by its test
task(s); tests MUST fail before implementation lands.

**Organization**: Tasks are grouped by user story to enable independent
implementation and testing. Priority order: US1 (P1) → US2 (P1) →
US3 (P2) → US5 (P2) → US4 (P3). US5 sits before US4 because both are
P2-or-higher; US4 is P3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4, US5)
- File paths are repo-relative; absolute when ambiguous

## Path Conventions

- **Single project, Android plugin module**: `app/` (per plan.md)
- Java sources under `app/src/main/java/com/atakmap/android/twcoord/`
- JVM tests under `app/src/test/java/com/atakmap/android/twcoord/`
- Instrumented tests under `app/src/androidTest/java/com/atakmap/android/twcoord/`
- Resources under `app/src/main/res/`
- Docs under `docs/adr/`, `docs/ui/`, `docs/acceptance/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: New-package scaffolding, drawable / layout / strings
placeholders. The existing app/ module is already configured for
Java 17, Spotless, proj4j, AndroidX, ATAK SDK. Nothing in this phase
should require changing build files.

- [X] T001 Create new Java package directory `app/src/main/java/com/atakmap/android/twcoord/gotopage/` (and the matching test mirrors under `app/src/test/java/com/atakmap/android/twcoord/gotopage/` + `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/`)
- [X] T002 [P] Create drawable placeholder `app/src/main/res/drawable/ic_tw_coord_goto.xml` (pin / target glyph; final art may follow in Polish)
- [X] T003 [P] Add string-key skeleton (filled with final values, NOT empty placeholders — saves the T027/T041/T065/T079 fill-in passes) to `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rTW/strings.xml`, `app/src/main/res/values-ja/strings.xml` for the ~30 GoTo-page keys
- [X] T004 [P] Create layout skeleton `app/src/main/res/layout/tw_coord_goto.xml` (root container + RadioGroup tab bar + per-pane LinearLayouts + shared Submit button + Recent section placeholder)
- [ ] T005 Verify `./gradlew spotlessApply assembleCivDebug` is green with the new empty resources and package directory (Constitution Principle I — formatter is enforced) — DEFERRED to MVP checkpoint to amortise gradle cost

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user
story can be implemented. Includes the parser facade, the receiver
shell, plugin / manifest registration, and the new PreferenceStore
keys.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 [P] Create `ParseResult` sealed-style class in `app/src/main/java/com/atakmap/android/twcoord/gotopage/ParseResult.java` (Ok / Invalid / OutOfRange variants per data-model.md §2, with `Invalid.Reason` enum: `BAD_LENGTH`, `BAD_LETTER`, `RESERVED_LETTER_YZ`, `BAD_ZONE`, `EMPTY`, `NON_DIGIT`)
- [X] T007 [P] Create `CoordinateInput` sealed-style hierarchy in `app/src/main/java/com/atakmap/android/twcoord/gotopage/CoordinateInput.java` (Taipower / Twd97 / Twd67 records per data-model.md §1; includes `displayString()` factory)
- [X] T008 Create `CoordinateParser` facade in `app/src/main/java/com/atakmap/android/twcoord/gotopage/CoordinateParser.java` with stub bodies for `parseTaipower(String)`, `parseTwd97(int,int,int)`, `parseTwd67(int,int,int)`, `parse(CoordinateInput)` (all return `Invalid(EMPTY)` until US1/US2 lands; reuses `Projections` / `DatumShiftTwd67` / `TaipowerGrid` from feature 001)
- [X] T009 [P] Extend `app/src/main/java/com/atakmap/android/twcoord/prefs/PreferenceStore.java` with all `pref_goto_*` keys (per plan.md Storage section): `pref_goto_last_unit`, `pref_goto_last_taipower`, `pref_goto_last_twd97_e/n/zone`, `pref_goto_last_twd67_e/n/zone`, `pref_goto_recent_json`; default values per data-model.md §8
- [X] T010 [P] Add the constant `Intent` action string `com.atakmap.android.twcoord.SHOW_GOTO` and outbound `GOTO_NAV_COMPLETED` to `app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordTool.java` (sibling of the existing `SHOW_PLUGIN` constant)
- [X] T011 Create `TwCoordGotoTool` in `app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordGotoTool.java` as a sibling of the existing `TwCoordTool`. Subclass `com.atak.plugins.impl.AbstractPluginTool` with `app_name_goto` / `app_desc_goto` strings, `R.drawable.ic_tw_coord_goto`, and the action constant `com.atakmap.android.twcoord.SHOW_GOTO`. Implement `Disposable.dispose()` as a no-op (mirror `TwCoordTool` line-for-line). **Tools icons in this codebase are registered programmatically via AbstractPluginTool subclasses, not via plugin.xml — see `TwCoordTool.java` and `AbstractPlugin(IServiceController, IToolbarItem[], MapComponent)` ctor.**
- [X] T012 Create `TwCoordGotoReceiver` shell in `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoReceiver.java` extending `com.atakmap.android.dropdown.DropDownReceiver`: implement `onReceive` open/close skeleton, idempotent re-open guard, `inflateLayout(R.layout.tw_coord_goto)`, back-press handling (per contracts/goto-receiver.md §2). Submit / Auto Fill wiring stays stubbed.
- [X] T013 Wire two things into the plugin's lifecycle: (a) `TwCoordGotoReceiver` registration into `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java` `onCreate` (`AtakBroadcast.getInstance().registerReceiver(receiver, new DocumentedIntentFilter("com.atakmap.android.twcoord.SHOW_GOTO"))`) and unregister cleanly in `onDestroyImpl`; (b) switch `app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordLifecycle.java` to the `AbstractPlugin(IServiceController, IToolbarItem[], MapComponent)` ctor and pass both `TwCoordTool` and `TwCoordGotoTool` so the second Tools-menu icon actually appears.
- [X] T014 Add the "Open Coordinate Input" preference entry in `app/src/main/res/xml/preferences.xml` and bind it in `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java` to send the `SHOW_GOTO` broadcast (FR-016 — second entry point)
- [X] T015 Add `Projections.twd97ToWgs84(Twd97Tm2)` to `app/src/main/java/com/atakmap/android/twcoord/coord/Projections.java` (inverse direction of the existing forward `wgs84ToTwd97`; reuses the same proj4j `CoordinateTransform` instances for zones 121 / 119). **Both US1 Taipower path AND US2 TWD97/TWD67 paths call this** — must precede US1 to satisfy the independent-shippability invariant. (Moved here from US2 during /speckit-analyze remediation.)

**Checkpoint**: At this point, tapping the Tools icon OR the Settings entry MUST open an empty DropDown shell. No tabs, no inputs, no submit — that comes in US1.

---

## Phase 3: User Story 1 - Enter a Taipower grid code and jump there (Priority: P1) 🎯 MVP

**Goal**: Operator enters a 9- or 11-character Taipower code on a single
tab, taps Submit, ATAK pans and drops a single marker at the resolved
WGS84 point.

**Independent Test**: Per spec US1 — enter `H7509 DB4016` on the Taipower
tab, marker MUST appear within 5 m of Hualien Station (golden vector from
`taiwan_cities_coords.csv`); long-press removes the marker.

### Tests for User Story 1 (TDD, write FIRST, ensure they FAIL)

- [X] T016 [P] [US1] Write `TaipowerParserTest` in `app/src/test/java/com/atakmap/android/twcoord/gotopage/TaipowerParserTest.java` covering the negative-path cases from contracts/coordinate-parser.md (BAD_LENGTH, BAD_LETTER, RESERVED_LETTER_YZ, NON_DIGIT, normalisation: lowercase / missing space / double space / surrounding parens)
- [X] T017 [P] [US1] Write the Taipower section of `CoordinateParserRoundTripTest` in `app/src/test/java/com/atakmap/android/twcoord/gotopage/CoordinateParserRoundTripTest.java` (22-city round trip; tolerance: 5 m main island, 20 m outer; outer-island entries assert `OutOfRange` for Taipower)
- [~] T018 [P] [US1] Write `DestinationMarkerStoreTest` in `app/src/test/java/com/atakmap/android/twcoord/gotopage/DestinationMarkerStoreTest.java` (invariant: `moveOrCreate` keeps the same UID across submissions; `removeIfPresent` clears the delegate; mocked `MapView` via Mockito)
- [~] T019 [P] [US1] Write the Espresso test `TwCoordGotoReceiverOpenLifecycleTest.receiver_opensDropDown_onShowGotoIntent` and `receiver_isIdempotent_onSecondShowGotoIntent` and `receiver_closesCleanly_onBackPress` in `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoReceiverTest.java`
- [~] T020 [P] [US1] Write the Espresso test `submit_pansAndDropsMarker_thenClosesDropDown` AND `submit_emitsLocalisedConfirmationToast` in the same `TwCoordGotoReceiverTest.java` (uses Hualien Station Taipower code; asserts marker on map + DropDown closed + toast string contains the unit name and `25.034°N 121.565°E`-style WGS84 to 6 decimals per FR-010)

### Implementation for User Story 1

- [X] T021 [US1] Implement `TaipowerParser` (package-private) in `app/src/main/java/com/atakmap/android/twcoord/gotopage/TaipowerParser.java`: normalisation, 9 / 11-char length check, Y/Z rejection, A–X / A–J letter validation, `NON_DIGIT` rejection. Reuses `TaipowerGrid.fromCode(...)` from feature 001 once it exists or — if absent — adds the inverse helper to `TaipowerGrid.java` (the existing class only has `fromTwd67`; add a `fromCode(String) → Twd67Tm2` method behind the same package boundary)
- [X] T022 [US1] Wire `CoordinateParser.parseTaipower(String)` in `CoordinateParser.java` to `TaipowerParser`, then through `DatumShiftTwd67.twd67ToTwd97` → `Projections.twd97ToWgs84` (now in Phase 2 via T015) → Taiwan-box check (Lat 21.5–26.5, Lon 118.0–122.5; out-of-range returns `OutOfRange(unit, attemptedWgs84)`)
- [X] T023 [US1] Implement `DestinationMarkerStore` in `app/src/main/java/com/atakmap/android/twcoord/gotopage/DestinationMarkerStore.java`: process-scoped singleton; `moveOrCreate(Wgs84, CoordinateInput)`; UID allocated once via `UUID.randomUUID()`; marker type `b-m-p-w-GOTO`; icon `R.drawable.ic_tw_coord_goto`; `setRemovable(true)`; meta strings per contracts/goto-receiver.md §3b
- [X] T024 [US1] Implement the Taipower tab section of `TwCoordGotoView` in `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoView.java` (single `EditText` + Submit button; debounced validation via a `HandlerThread`-bound validator that posts results back to the UI thread; disables Submit while `ParseResult` ≠ `Ok`; renders inline error text from `ParseResult.Invalid.reason`)
- [X] T025 [US1] Implement the Taipower tab layout in `app/src/main/res/layout/tw_coord_goto.xml` (TabLayout + ViewPager2; one tab pane with the EditText / Submit / inline-error TextView; reuses the existing dark theme colours per ADR-0007)
- [X] T026 [US1] Wire `TwCoordGotoView.onSubmit` for the Taipower path: parser → if `Ok` then `DestinationMarkerStore.moveOrCreate` → `mapView.getRenderer3().lookAt(geoPoint, /*resolution*/ 50.0, 0.0, 0.0, /*animate*/ false)` → `PreferenceStore.setGotoLastUnit(TAIPOWER)` + `setGotoLastTaipower(rawValue)` → **emit localised confirmation toast per FR-010 in the form `<unit> → <lat>°N <lon>°E` with WGS84 lat/lon to 6 decimals (zone suffix omitted on Taipower since it is main-island only)** → close DropDown → fire outbound `GOTO_NAV_COMPLETED` intent
- [X] T027 [US1] Add localised string values for the Taipower tab (tab label "Taipower" / "台電座標" / "台電グリッド", hint, error reasons, **plus the FR-010 confirmation toast format key `goto_confirmation_toast` accepting `%1$s` (unit), `%2$.6f` (lat), `%3$.6f` (lon), plus `app_name_goto` / `app_desc_goto` for the second Tools icon**) in `values/strings.xml`, `values-zh-rTW/strings.xml`, `values-ja/strings.xml`
- [X] T028 [US1] Run `mcp__zhtw-mcp__zhtw` over the new `values-zh-rTW/strings.xml` entries; 0 errors / 0 warnings required (matches ADR-0008 D5 discipline)
- [X] T029 [US1] Run `./gradlew :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.gotopage.TaipowerParserTest" --tests "com.atakmap.android.twcoord.gotopage.CoordinateParserRoundTripTest" --tests "com.atakmap.android.twcoord.gotopage.DestinationMarkerStoreTest"` — all green
- [~] T030 [US1] Run `./gradlew :app:connectedCivDebugAndroidTest --tests "com.atakmap.android.twcoord.gotopage.TwCoordGotoReceiverTest"` on Galaxy Tab S10+ — all green
- [~] T031 [US1] Manual on-device smoke-test per `quickstart.md` §"Smoke-test the three units" — Taipower row only; record screenshot for `docs/ui/`

**Checkpoint**: MVP! The plugin now installs, opens an input page from Tools, accepts `H7509 DB4016`, pans to Hualien Station, drops a removable marker, persists the entry. Deployable.

---

## Phase 4: User Story 2 - Enter a TWD97 or TWD67 easting/northing and jump there (Priority: P1)

**Goal**: Operator switches to the TWD97 or TWD67 tab, enters separate
easting / northing, picks zone 121 (main island) or 119 (outer island),
submits. Same marker / pan behaviour as US1. Outer-island accuracy
advisory shown before submit when zone = 119.

**Independent Test**: Per spec US2 — enter `302912 / 2770905 / 121` on
TWD97 tab → marker within 0.5 m of Taipei 101; switch to TWD67 with
`302130 / 2771143 / 121` → marker within 5 m; switch to TWD97 zone 119
with Penghu coordinates → marker on Penghu + toast names `zone 119`.

### Tests for User Story 2 (TDD)

- [ ] T032 [P] [US2] Write `TwdTm2ParserTest` in `app/src/test/java/com/atakmap/android/twcoord/gotopage/TwdTm2ParserTest.java` covering zone toggle (`BAD_ZONE` when not 121/119), easting / northing length validation, OOR for coordinates outside Taiwan
- [ ] T033 [P] [US2] Extend `CoordinateParserRoundTripTest` to cover TWD97 (tolerance ≤ 0.5 m) and TWD67 (≤ 5 m main, ≤ 20 m outer) for all 22 cities in `test-data/taiwan_cities_coords.csv`; reuse the CSV loader from feature 001's `TaiwanCitiesAuthoritativeTest`
- [ ] T034 [P] [US2] Write Espresso `outer_island_advisory_appears_for_zone119` AND `submit_toast_appendsZone119_whenZoneNot121` in `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoZoneTest.java` (toggles zone 119; asserts (a) the inline advisory above Submit before tap and (b) the post-submit confirmation toast text contains the substring `zone 119` per FR-010)
- [ ] T035 [P] [US2] Write Espresso `zone_toggle_persists_per_unit` in the same file (zone toggle on TWD97 tab does NOT leak into TWD67 tab and vice versa)

### Implementation for User Story 2

- [ ] T036 [US2] Implement `TwdTm2Parser` (package-private) in `app/src/main/java/com/atakmap/android/twcoord/gotopage/TwdTm2Parser.java`: validates easting / northing / zone; constructs `Twd97Tm2.of` or `Twd67Tm2.of`; defers actual transform to the facade
- [ ] T037 [US2] Wire `CoordinateParser.parseTwd97(int,int,int)` and `CoordinateParser.parseTwd67(int,int,int)` (TWD67 path goes through `DatumShiftTwd67.twd67ToTwd97` first, then the TWD97 inverse). Both paths call `Projections.twd97ToWgs84` which was added in Phase 2 T015.
- [ ] T038 [US2] Add the TWD97 and TWD67 tabs to `app/src/main/res/layout/tw_coord_goto.xml`: each pane has two `EditText` (easting / northing, `inputType="number"`), a `RadioGroup` zone toggle (121 default, 119 alt), the Submit button, the inline-advisory `TextView` (visible only when zone = 119), and the inline-error `TextView`
- [ ] T039 [US2] Extend `TwCoordGotoView` to handle the TWD97 / TWD67 tabs: per-tab `InputPageState` field group (data-model.md §7), per-tab keystroke-debounced validation, per-tab Submit handler, per-tab zone toggle change listener (re-evaluates advisory visibility)
- [ ] T040 [US2] Wire `TwCoordGotoView.onSubmit` for TWD97 / TWD67 paths: same shape as US1 (parser → marker → pan → persist → close + outbound intent), but persists to `pref_goto_last_twd97_*` or `pref_goto_last_twd67_*`, **and emits the same localised confirmation toast per FR-010 — appending ` zone 119` to the unit tag when the resolved zone is 119, omitted otherwise**
- [ ] T041 [US2] Localise TWD97 / TWD67 tab labels, hints, zone toggle labels, the outer-island accuracy advisory, the confirmation toast (`zone 119` appended only when zone ≠ 121), and the per-reason error strings in all three `strings.xml` locale files
- [ ] T042 [US2] Run `mcp__zhtw-mcp__zhtw` over the new `values-zh-rTW/strings.xml` entries; 0 errors / 0 warnings required
- [ ] T043 [US2] Run `./gradlew :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.gotopage.TwdTm2ParserTest" --tests "com.atakmap.android.twcoord.gotopage.CoordinateParserRoundTripTest"` — all 22-city round-trips green
- [ ] T044 [US2] Run `./gradlew :app:connectedCivDebugAndroidTest --tests "com.atakmap.android.twcoord.gotopage.TwCoordGotoZoneTest"` — green
- [ ] T045 [US2] Manual on-device smoke-test per `quickstart.md`: TWD97 Taipei 101 zone 121, TWD97 Penghu zone 119, TWD67 Kaohsiung zone 121

**Checkpoint**: Both P1 stories shipped. The plugin now accepts all three unit families; the marker / pan / persist flow is unit-agnostic from US1 onward.

---

## Phase 5: User Story 3 - Edit and refine the destination marker (Priority: P2)

**Goal**: Reopening the page restores the last unit + value; resubmits
move the existing marker rather than spawning a new one; long-press
removes the marker.

**Independent Test**: Per spec US3 — after one successful submission,
reopen the page → field MUST be pre-filled with the previous value and
the previously-used tab MUST be active. Edit one character, submit →
existing marker moves, no second marker appears.

### Tests for User Story 3 (TDD)

- [ ] T046 [P] [US3] Write two Espresso tests in `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/TwCoordGotoRestoreTest.java`: (a) `receiver_restoresActiveTab_fromPreference` — cross-session restore via `PreferenceStore` (FR-003); (b) `dropDown_preservesDraft_acrossCloseAndReopen_withinSameSession` — in-session preservation per FR-018: open page, type partial value WITHOUT submitting, close DropDown via back-press, reopen, assert the partial value and the active tab are exactly as left.
- [ ] T047 [P] [US3] Write Espresso `resubmit_movesExistingMarker_doesNotDuplicate` in the same file (submits two coords, asserts exactly one marker on the map with the second submission's call-sign)
- [ ] T048 [P] [US3] Extend `DestinationMarkerStoreTest` with a `longPress_clearsStoreReference` case (simulates marker removal via the standard ATAK delete path; next `moveOrCreate` MUST allocate a fresh marker)

### Implementation for User Story 3

- [ ] T049 [US3] Implement pre-fill in `TwCoordGotoReceiver`. (a) **Cross-session (FR-003)**: on every `onReceive` open, if no in-memory `InputPageState` exists yet for this plugin process, read `pref_goto_last_unit`, `pref_goto_last_taipower` / `_twd97_*` / `_twd67_*`; seed `InputPageState`; pass to view via `bindView`. Default to Taipower / empty on first-ever open. (b) **In-session (FR-018)**: cache the current `InputPageState` on the receiver instance at every `onClose`; on subsequent `onReceive` opens within the same ATAK process, restore from that cache instead of re-reading prefs, so a closed-without-submit draft survives close-and-reopen.
- [ ] T050 [US3] Implement the long-press → store-reference-clear hook in `DestinationMarkerStore`: register a `MapItem.OnRemoveListener` on the marker; on fire, null out `delegate` so the next `moveOrCreate` creates fresh
- [ ] T051 [US3] Verify `DestinationMarkerStore.moveOrCreate` under rapid resubmit by adding an `AtomicBoolean` submit guard in `TwCoordGotoView.onSubmit` (coalesces double-taps per contracts/goto-receiver.md §5)
- [ ] T052 [US3] Run `./gradlew :app:connectedCivDebugAndroidTest --tests "com.atakmap.android.twcoord.gotopage.TwCoordGotoRestoreTest" --tests "com.atakmap.android.twcoord.gotopage.DestinationMarkerStoreTest"` — green
- [ ] T053 [US3] Manual on-device: submit two coords, verify single marker on map; close + reopen page, verify field pre-fill; long-press marker, verify it disappears + the next submit creates a fresh marker

**Checkpoint**: Operator workflow is now persistent across opens within the session AND across plugin lifetime via the saved preference keys.

---

## Phase 6: User Story 5 - Auto Fill from current map centre (Priority: P2)

**Goal**: One-tap fill of the active tab from the current map centre.
Button auto-disables when the map centre is unrepresentable in the
active tab. On TWD97 / TWD67 tabs, the zone toggle is set in lockstep
with the values from the map centre's longitude.

**Independent Test**: Per spec US5 — centre map on Taipei 101, tap Auto
Fill on each tab in turn, field MUST fill with the city's pinned value;
pan to Penghu, on Taipower tab the button MUST go disabled within one
map-event cycle.

### Tests for User Story 5 (TDD)

- [ ] T054 [P] [US5] Write `MapCenterAutoFillStreamTest` in `app/src/test/java/com/atakmap/android/twcoord/gotopage/MapCenterAutoFillStreamTest.java` using a fake `MapEvent` source (no Android dependency): asserts (a) `MapCenterFix` emitted on every event after debounce, (b) `taipowerOk` flips false for zone-119 input, (c) `twd97Ok` / `twd67Ok` flip false for inputs outside the Taiwan box
- [ ] T055 [P] [US5] Write Espresso `autoFillButton_isDisabled_whenMapCentreOutsideTaiwan` in `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/AutoFillDisabledStateTest.java`
- [ ] T056 [P] [US5] Write Espresso `autoFillButton_enables_withinOneFrame_afterPanInsideTaiwan` in the same file
- [ ] T057 [P] [US5] Write Espresso `autoFillButton_isDisabled_onTaipowerTab_whenCenterIsPenghu` in the same file
- [ ] T058 [P] [US5] Write Espresso `autoFill_setsZoneToggle_fromLongitude` (centre on Penghu, switch to TWD97 tab, tap Auto Fill, assert zone toggle reads 119)

### Implementation for User Story 5

- [ ] T059 [US5] Implement `MapCenterAutoFillStream` in `app/src/main/java/com/atakmap/android/twcoord/gotopage/MapCenterAutoFillStream.java`: subscribes to `MAP_SCROLL`, `MAP_SETTLED`, `MAP_SCALE`, `MAP_MOVED` (same set the readout widget uses); debounces via the `haveEmitted` flag pattern copied from `SelfMarkerSubscriber`; emits `MapCenterFix` to a `LiveData<MapCenterFix>` per data-model.md §3
- [ ] T060 [US5] Add the Auto Fill button to each of the three tab panes in `app/src/main/res/layout/tw_coord_goto.xml` (right of the input field on Taipower; spanning above the easting / northing rows on TWD97 / TWD67); set `android:contentDescription` for accessibility
- [ ] T061 [US5] Wire `TwCoordGotoView.onAutoFillClick` per tab: read latest `MapCenterFix`; compute the `CoordinateInput` for the active tab via the same forward converter the readout widget uses; write the string into the EditText(s); on TWD97 / TWD67 tabs, also set the zone toggle from `MapCenterFix.wgs84.longitudeDeg() < 120.0 ? 119 : 121`; do NOT trigger submit (FR-021 fill-only)
- [ ] T062 [US5] Wire the per-tab Auto Fill button's `setEnabled(...)` to the `MapCenterAutoFillStream.LiveData<MapCenterFix>` observer — `taipowerOk` for Taipower tab, `twd97Ok` for TWD97 tab, `twd67Ok` for TWD67 tab; update inside the observer for sub-frame propagation
- [ ] T063 [US5] Implement the localised tooltip / long-press hint shown when the Auto Fill button is disabled: "outside Taiwan coverage" / "Taipower does not cover outer islands"; bind via `TooltipCompat.setTooltipText` so Android 14+ shows it on long-press
- [ ] T064 [US5] Hook `attachMapCenterStream()` in `TwCoordGotoReceiver.onShow` and `detachMapCenterStream()` in `onClose`; uses the same idempotency guard as the existing `SelfMarkerSubscriber` setup
- [ ] T065 [US5] Localise the Auto Fill button text, the two tooltip strings, and the "zone 119" toast suffix in all three locale `strings.xml` files
- [ ] T066 [US5] Run `mcp__zhtw-mcp__zhtw` over the new `values-zh-rTW/strings.xml` entries
- [ ] T067 [US5] Run `./gradlew :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.gotopage.MapCenterAutoFillStreamTest"` — green
- [ ] T068 [US5] Run `./gradlew :app:connectedCivDebugAndroidTest --tests "com.atakmap.android.twcoord.gotopage.AutoFillDisabledStateTest"` — green
- [ ] T069 [US5] Manual on-device per `quickstart.md` §"Smoke-test Auto Fill" — confirm enabled/disabled propagation across Taipei → Penghu → Tokyo with each tab in turn

**Checkpoint**: Operators no longer have to type a coordinate they can see on screen; Auto Fill matches a single tap on each of the three tabs.

---

## Phase 7: User Story 4 - Recent entries list (Priority: P3)

**Goal**: Up to 10 successful submissions are persisted across ATAK
restarts, surfaced in a Recent section in the input page, tappable
to refill and re-submit, individually deletable.

**Independent Test**: Per spec US4 — after 3+ successful submits,
reopen the page, scroll to Recent; 3 rows newest-first; tap one, the
corresponding tab activates and the field fills; tap the per-row
delete, the row vanishes. After 11 submissions, only the most recent
10 remain.

### Tests for User Story 4 (TDD)

- [ ] T070 [P] [US4] Write `RecentEntryStoreTest` in `app/src/test/java/com/atakmap/android/twcoord/gotopage/RecentEntryStoreTest.java` covering every method in `contracts/recent-store.md` §"Test contract" (append, dedupe, capacity-10 FIFO eviction, JSON round-trip across reconstruction, corrupted-JSON recovery, listener invocation)
- [ ] T071 [P] [US4] Write Espresso `submit_appendsRecentEntry_capacityTen` in `app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/RecentListEspressoTest.java`
- [ ] T072 [P] [US4] Write Espresso `tapRecentRow_activatesTabAndFillsField` in the same file
- [ ] T073 [P] [US4] Write Espresso `perRowDelete_removesEntry` in the same file

### Implementation for User Story 4

- [ ] T074 [US4] Implement `RecentEntry` value class in `app/src/main/java/com/atakmap/android/twcoord/gotopage/RecentEntry.java` per data-model.md §5 (immutable; JSON-serialisable via the store)
- [ ] T075 [US4] Implement `RecentEntryStore` in `app/src/main/java/com/atakmap/android/twcoord/gotopage/RecentEntryStore.java` per contracts/recent-store.md (single `pref_goto_recent_json` key; `org.json.JSONArray` encode / decode; capacity-10 FIFO; dedup-then-trim; listener API)
- [ ] T076 [US4] Add the Recent section layout to `app/src/main/res/layout/tw_coord_goto.xml`: a vertically scrolling list (RecyclerView or LinearLayout-in-ScrollView for ≤ 10 rows; the latter is cheaper); per-row layout `app/src/main/res/layout/tw_coord_goto_recent_row.xml` with unit tag + raw value + delete glyph; section header + empty-state TextView
- [ ] T077 [US4] Wire the Recent list to `RecentEntryStore`: observe `OnChange` events; rebuild the list view on every change; click handler activates the matching tab and populates the active input fields; per-row delete handler calls `store.removeAt(index)`
- [ ] T078 [US4] Persist a new `RecentEntry` on every successful submit in `TwCoordGotoView.onSubmit` (call `store.append(RecentEntry.of(input, System.currentTimeMillis()))`)
- [ ] T079 [US4] Localise the Recent section header, the empty-state message ("No recent entries"), and the per-row tooltip (date format if any) in all three locale `strings.xml` files
- [ ] T080 [US4] Run `mcp__zhtw-mcp__zhtw` over the new `values-zh-rTW/strings.xml` entries
- [ ] T081 [US4] Run `./gradlew :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.gotopage.RecentEntryStoreTest"` — green
- [ ] T082 [US4] Run `./gradlew :app:connectedCivDebugAndroidTest --tests "com.atakmap.android.twcoord.gotopage.RecentListEspressoTest"` — green
- [ ] T083 [US4] Manual on-device: submit 11 entries across mixed units; verify only 10 remain in newest-first order; cold-restart ATAK and verify persistence

**Checkpoint**: All five user stories shipped. The plugin's input-page feature is functionally complete.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Constitution-mandated deliverables (ADR, docs/ui,
performance benchmark, formatter sweep) and final acceptance checks.

- [ ] T084 [P] Run `./gradlew spotlessApply` over the entire `app/` module to enforce Constitution Principle I; verify `./gradlew spotlessCheck` is green
- [ ] T085 [P] Run the full JVM test suite `./gradlew :app:testCivDebugUnitTest` (all feature-001 tests AND all new tests from this feature) — green
- [ ] T086 Run the full instrumented test suite `./gradlew :app:connectedCivDebugAndroidTest` on the reference device (Galaxy Tab S10+) — green
- [ ] T087 Measure SC-002 (≤ 300 ms median submit → marker rendered) by wrapping `TwCoordGotoView.onSubmit` and `DestinationMarkerStore.moveOrCreate` in `Trace` events; run 10 sequential submits; record the median + p95 in `docs/acceptance/002-tw-coord-goto.md`
- [ ] T088 Measure SC-004 (≤ 100 ms inline validation latency) by tracing the keystroke → debouncer → validator → UI update path on each tab; record in the acceptance log
- [ ] T089 Measure SC-009 (Auto Fill disabled-state propagation ≤ one map-event cycle) by emitting `Trace` events on `MapEvent` receipt and on `Button.setEnabled` invocation; assert the delta is ≤ 16 ms on average
- [ ] T090 Author `docs/ui/input-page.md` per Constitution III: DropDown layout anatomy, tab structure, Auto Fill button states (enabled / disabled with tooltip), inline-error rendering, accuracy-advisory placement; embed at least one screenshot per tab + one screenshot of the disabled Auto Fill tooltip
- [ ] T091 Author `docs/adr/0009-tw-coord-goto-input-page.md` per Constitution V: context (clarify session 2026-05-16 + the spec's US1–US5), decision (DropDownReceiver + second Tools icon + inverse proj4j + Auto Fill from MAP_CENTER only), alternatives considered (cross-reference research.md R1–R11 succinctly), consequences (positive: ATAK-native UX; negative: zhtw-mcp lint becomes part of the build flow), links to spec / plan / research / data-model / contracts / tasks
- [ ] T092 Update top-level `README.md` to list the new input-page feature alongside the existing readout-widget summary (single paragraph + a Tools-icon screenshot or text mention)
- [ ] T093 Author `docs/acceptance/002-tw-coord-goto.md` capturing the manual SC-005 verification (60-second discovery time by an uninstructed operator: at least one third-party teammate opens ATAK with the plugin installed for the first time, finds the input page from the Tools menu, and successfully submits a Taipower code under a stopwatch; record the wall-clock time)
- [ ] T094 Final manual full-flow run-through on Galaxy Tab S10+: each of US1, US2, US3, US4, US5 against `quickstart.md`; sign-off comment in `docs/acceptance/002-tw-coord-goto.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately on branch `002-tw-coord-goto`.
- **Foundational (Phase 2)**: Depends on Setup completion. **BLOCKS all user stories.**
- **User Story 1 (Phase 3, P1)**: Depends on Foundational. **MVP boundary.**
- **User Story 2 (Phase 4, P1)**: Depends on Foundational. Can run in parallel with US1 if two developers are available; otherwise sequential after US1.
- **User Story 3 (Phase 5, P2)**: Depends on **at least one of US1 / US2 having shipped** because it tests pre-fill and move-not-create behaviours.
- **User Story 5 (Phase 6, P2)**: Depends on **at least one of US1 / US2 having shipped** because Auto Fill writes into per-tab fields.
- **User Story 4 (Phase 7, P3)**: Depends on **at least one of US1 / US2 having shipped** because the recent list records successful submissions.
- **Polish (Phase 8)**: Depends on all desired user stories being complete. Some Polish tasks (T084 / T085) can run after each story for early signal.

### Within Each User Story

- Tests are mandatory and MUST be written before implementation per Constitution Principle II. Each user story has its tests listed first; the impl tasks follow.
- Models / value classes before services; services before view-wiring; view-wiring before view-layout binding. Each story phase orders these accordingly.
- Localisation strings are added in the same story phase that introduces the visible widget; zhtw-mcp lint runs immediately after.

### Parallel Opportunities

- All Setup tasks marked **[P]** (T002, T003, T004) can run in parallel.
- All Foundational tasks marked **[P]** (T006, T007, T009, T010) can run in parallel. T008 / T011 / T012 / T013 / T014 are sequential because they depend on each other or on a single shared file.
- Once Foundational is complete, US1 and US2 can be developed in parallel by two developers (they touch overlapping XML / view files but in different tab panes; merge conflicts are localised to `tw_coord_goto.xml` and `TwCoordGotoView.java`).
- Within each story, all test-writing tasks marked **[P]** can run in parallel (they live in separate test files).
- US3 / US5 can run in parallel after either P1 story ships; US4 sits after both for cleanest ordering.

---

## Parallel Example: User Story 1

```bash
# After Foundational checkpoint, launch all US1 test-writing tasks together
Task: "Write TaipowerParserTest in app/src/test/.../TaipowerParserTest.java"  # T015
Task: "Write CoordinateParserRoundTripTest in app/src/test/.../CoordinateParserRoundTripTest.java"  # T016
Task: "Write DestinationMarkerStoreTest in app/src/test/.../DestinationMarkerStoreTest.java"  # T017
Task: "Write TwCoordGotoReceiverOpenLifecycleTest in app/src/androidTest/.../TwCoordGotoReceiverTest.java"  # T018
Task: "Write submit_pansAndDropsMarker_thenClosesDropDown in TwCoordGotoReceiverTest.java"  # T019
```

```bash
# Then in sequence, implement US1
Task: "Implement TaipowerParser in app/src/main/.../TaipowerParser.java"  # T020
Task: "Wire CoordinateParser.parseTaipower"  # T021
Task: "Implement DestinationMarkerStore"  # T022
Task: "Implement Taipower tab in TwCoordGotoView"  # T023
... (continue T024 → T030 sequentially)
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete **Phase 1: Setup** (T001–T005).
2. Complete **Phase 2: Foundational** (T006–T014).
3. Complete **Phase 3: US1 Taipower** (T015–T030).
4. **STOP and VALIDATE**: Manual smoke-test on Galaxy Tab S10+ (Hualien Station Taipower → marker). Cut a candidate build.
5. Optionally ship the MVP to early users for feedback before continuing.

### Incremental Delivery

1. Complete Setup + Foundational → foundation ready.
2. Add US1 → test independently → deploy/demo (**MVP**).
3. Add US2 → test independently → deploy/demo (full P1 coverage).
4. Add US3 + US5 in parallel → test independently → deploy/demo (P2 polish).
5. Add US4 → test independently → deploy/demo (P3 nice-to-have).
6. Complete Polish (T084–T094) → final release.

Each increment is independently shippable; Constitution Principles I (formatter) and II (TDD) are enforced at every increment, not deferred to the end.

### Parallel Team Strategy (if multiple developers)

1. Team completes Setup + Foundational together (~T001–T014 in one short session).
2. Once Foundational is checkpointed:
   - Developer A: US1 (T015–T030)
   - Developer B: US2 (T031–T045)
3. After US1 or US2 lands:
   - Developer A: US5 (T054–T069)
   - Developer B: US3 (T046–T053)
4. After all P1/P2 stories shipped:
   - Either developer: US4 (T070–T083)
5. Team converges on Polish (T084–T094).

Merge-conflict surface: `tw_coord_goto.xml`, `TwCoordGotoView.java`, and the three `strings.xml` files. Rebase frequently.

---

## Notes

- **TDD enforcement**: Every test task must be authored before the matching impl task; the test MUST be observed failing once on the developer's machine before the impl is written. This is required by Constitution Principle II (NON-NEGOTIABLE).
- **Formatter cadence**: `./gradlew spotlessApply` runs at every commit boundary, not only in Polish. T005 establishes the baseline; subsequent tasks rely on Spotless catching drift.
- **zhtw-mcp lint**: every user-story phase that adds Traditional Chinese strings includes a dedicated lint task (T027 / T042 / T066 / T080). 0 errors / 0 warnings is the bar set by ADR-0008 D5.
- **ADR cadence**: per Constitution V, `/speckit-implement` produces or appends ADR-0009 (or a follow-up bundle ADR) after this feature ships; that lives at T091. `/speckit-analyze` runs MAY produce additional ADRs.
- **Independence invariant**: each user-story phase ends with a "Checkpoint" line that says exactly what works at that boundary; a story is complete only when its checkpoint statement is demonstrably true on the reference device.
- Avoid: vague tasks, same-file conflicts without dependency note, cross-story dependencies that break checkpoint independence.

**Total tasks**: 94 (Setup 5 + Foundational 10 + US1 16 + US2 14 + US3 8 + US5 16 + US4 14 + Polish 11). Foundational gained T015 (Projections inverse, moved from US2) and US2 lost its old T037 during /speckit-analyze remediation; the renumbering of Phase 3 US1 (+1) and Phase 4 US2 first six tasks (+1) keeps Phase 5/6/7/8 numbering unchanged.
