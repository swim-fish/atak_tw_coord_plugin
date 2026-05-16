---

description: "Task list for feature 001-tw-coord-display — Taiwan Coordinate Display Plugin for ATAK"

---

# Tasks: Taiwan Coordinate Display Plugin for ATAK

**Input**: Design documents from `specs/001-tw-coord-display/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: TDD is mandatory per Constitution Principle II — test tasks are included for every behaviour-bearing change. The contracts in `contracts/` are the test specifications; do not weaken them.

**Organization**: Tasks are grouped by user story so each story can be implemented, tested, and demoed independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3) — only present in Phase 3+ user-story phases

## Path Conventions

- Plugin module: `app/`
- Production sources: `app/src/main/java/com/atakmap/android/twcoord/...`
- Resources: `app/src/main/res/...` and `app/src/main/assets/...`
- JVM unit tests: `app/src/test/java/com/atakmap/android/twcoord/...`
- Instrumented tests: `app/src/androidTest/java/com/atakmap/android/twcoord/...`
- Cross-cutting docs: `docs/adr/` and `docs/ui/` at repo root

All paths in tasks below are repo-relative.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialise the Gradle project skeleton, Android manifest, plugin descriptor, formatting tooling, and constitution-mandated docs directories.

- [ ] T001 Initialise root Gradle project with wrapper, `settings.gradle`, root `build.gradle`, and `gradle.properties` declaring `ATAK_VERSION=5.7.0.3`, `PLUGIN_VERSION=1.0.0`, and `atak.sdk.path` pointing at `C:/Users/hhhnr/source/tak/ATAK-CIV-5.7.0.3-SDK`.
- [ ] T002 Author `app/build.gradle` applying `atak-takdev-plugin`, Java 17 source/target compatibility, `compileSdk 36 / minSdk 26 / targetSdk 34`, signing config (debug + civ), `proguard-gradle.txt` reference, and `proj4j 1.3.x` dependency. Exclude `androidx.core`, `androidx.fragment`, `androidx.lifecycle` transitives per `meshtastic_atak` precedent.
- [ ] T003 [P] Add Spotless plugin to `app/build.gradle` with `googleJavaFormat()` over `src/main/java/**` and `src/test/java/**`; wire `spotlessCheck` into `check` task. Add `.git/hooks/pre-commit` (or `gradle/git-hooks/pre-commit`) that runs `./gradlew spotlessApply` and fails on unformatted code.
- [ ] T004 [P] Author `app/src/main/AndroidManifest.xml`: declare ATAK plugin component activity, `android:extractNativeLibs="true"`, and **explicitly omit** `android.permission.INTERNET` (FR-019 enforced by construction).
- [ ] T005 [P] Author `app/src/main/assets/plugin.xml` registering `gov.tak.api.plugin.IPlugin` impl `com.atakmap.android.twcoord.TwCoordLifecycle` with `singleton="true"`.
- [ ] T006 [P] Author `app/proguard-gradle.txt` keeping `TwCoordLifecycle`, `TwCoordMapComponent`, `TwCoordPreferenceFragment`, all `coord/` classes, and `org.locationtech.proj4j.**`; suppress lambda-eaten warnings per SDK README §44-48.
- [x] T007 [P] Seed `docs/adr/README.md` with the ADR template — **DONE 2026-05-16** during `/speckit-analyze` remediation (file exists; verify it still matches the template referenced by ADR-0001..0003 before marking the phase complete).
- [ ] T008 [P] Seed `docs/ui/README.md` describing the docs-as-design-record practice per Constitution Principle III, with sections for the readout widget and the settings fragment to be filled in per the per-story tasks T036a and T048a.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Plugin lifecycle, map component, pure-Java value classes, and the three localised string bundles — everything every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T009 Implement `app/src/main/java/com/atakmap/android/twcoord/TwCoordLifecycle.java` extending `AbstractPlugin`, constructor taking `IServiceController`, instantiating and registering `TwCoordMapComponent` in `onStart` / unregistering in `onStop`.
- [ ] T010 Implement minimal `app/src/main/java/com/atakmap/android/twcoord/TwCoordMapComponent.java` with `onCreate(Context, Intent, MapView)` and `onDestroyImpl(Context, MapView)`; acquire `MapView` reference; no listener wiring yet (listeners come in their user-story phases).
- [ ] T011 [P] Create `app/src/main/java/com/atakmap/android/twcoord/coord/CoordinateUnit.java` enum with values `TAIPOWER`, `TWD97`, `TWD67` and `Strings.unitTagKey()` accessor per data-model.md §1.
- [ ] T012 [P] Create `app/src/main/java/com/atakmap/android/twcoord/i18n/LanguageOverride.java` enum with values `SYSTEM`, `EN`, `ZH_TW`, `JA` per data-model.md §2.
- [ ] T013 [P] Create immutable `app/src/main/java/com/atakmap/android/twcoord/coord/Wgs84.java` value class (lat/lon validation, timestamp, `Source` inner enum `MAP_CENTRE | DEVICE_LOCATION`) per data-model.md §3.
- [ ] T014 [P] Create immutable `app/src/main/java/com/atakmap/android/twcoord/coord/Twd97Tm2.java` value class (easting, northing, zone) per data-model.md §4.
- [ ] T015 [P] Create immutable `app/src/main/java/com/atakmap/android/twcoord/coord/Twd67Tm2.java` value class (easting, northing, zone=121) per data-model.md §4.
- [ ] T016 [P] Create immutable `app/src/main/java/com/atakmap/android/twcoord/coord/TaipowerCode.java` value class with all fields and the both-or-neither constraint on `oneMeterE` / `oneMeterN` per data-model.md §4.
- [ ] T017 [P] Create `app/src/main/java/com/atakmap/android/twcoord/coord/ConversionResult.java` sealed ADT (`Ok`, `OutOfRange`, `NoFix`) per data-model.md §5; pattern-match-friendly accessors.
- [ ] T018 [P] Create `app/src/main/java/com/atakmap/android/twcoord/coord/DisplayLine.java` value class with `State` inner enum (`OK`, `OUT_OF_RANGE`, `NO_FIX`, `NO_PERMISSION`) per data-model.md §6.
- [ ] T019 [P] Create `app/src/main/java/com/atakmap/android/twcoord/prefs/UserPreference.java` value class (coordUnit, uiLanguage, staleFixThresholdMs) per data-model.md §7.
- [ ] T020 [P] Author `app/src/main/res/values/strings.xml` (English default) with all keys required by `Formatter.Strings` contract: `label_map`, `label_me`, `unit_tag_taipower`, `unit_tag_twd97`, `unit_tag_twd67`, `state_out_of_range`, `state_no_fix`, `state_no_permission`, plus preference titles.
- [ ] T021 [P] Author `app/src/main/res/values-zh-rTW/strings.xml` (Traditional Chinese — Taiwan) translating every key from T020.
- [ ] T022 [P] Author `app/src/main/res/values-ja/strings.xml` (Japanese) translating every key from T020.

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Map-centre readout in selected Taiwan unit (Priority: P1) 🎯 MVP

**Goal**: A persistent on-map readout shows the map centre's coordinate in the active unit, updating live as the user pans. Out-of-range gracefully falls back to a WGS84 line.

**Independent Test**: Install plugin, open ATAK, observe readout, pan to known landmark (Taipei 101 → `B7039 BD32` in Taipower mode), pan to Hong Kong → "out of range" with WGS84 fallback.

### Tests for User Story 1 (TDD — author and run RED before implementation) ⚠️

- [ ] T023 [P] [US1] Write `app/src/test/java/com/atakmap/android/twcoord/coord/ProjectionsTest.java` asserting WGS84 → TWD97 for all four golden vectors (Taipei 101, Kaohsiung 85, Taichung, Hualien) within ±0.1 m tolerance per `contracts/coordinate-converter.md`. MUST fail before T028 lands.
- [ ] T024 [P] [US1] Write `app/src/test/java/com/atakmap/android/twcoord/coord/DatumShiftTwd67Test.java` asserting TWD97 → TWD67 forward and inverse for all four golden vectors within ±3 m, plus warning that proj4 EPSG:3828 alone would silently break this. MUST fail before T029.
- [ ] T025 [P] [US1] Write `app/src/test/java/com/atakmap/android/twcoord/coord/TaipowerGridTest.java` asserting WGS84 (and TWD67) → Taipower 9-char codes for all four golden vectors within ±10 m, plus Y/Z letter rejection cases (Penghu, Lanyu). MUST fail before T030.
- [ ] T026 [P] [US1] Write `app/src/test/java/com/atakmap/android/twcoord/coord/CoordinateConverterTest.java` covering the converter facade contract: in-range Ok for each unit, OutOfRange for the three negative cases listed in `contracts/coordinate-converter.md`, and NullPointerException for null inputs. MUST fail before T031.
- [ ] T027 [P] [US1] Write `app/src/test/java/com/atakmap/android/twcoord/coord/FormatterTest.java` covering every (CoordinateUnit × DisplayLine state × locale) combination plus the `forClipboard(line).equals(displayedString)` invariant per `contracts/coordinate-formatter.md`. MUST fail before T033.

### Implementation for User Story 1

- [ ] T028 [P] [US1] Implement `app/src/main/java/com/atakmap/android/twcoord/coord/Projections.java` wrapping `proj4j` with the exact EPSG:3826 proj-string from research.md R8: `+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs`. Make T023 pass.
- [ ] T029 [P] [US1] Implement `app/src/main/java/com/atakmap/android/twcoord/coord/DatumShiftTwd67.java` with the verbatim 4-parameter formulas (Δx=807.8, Δy=248.6, a=1.549e-5, b=6.521e-6) from research.md R8 — both forward and inverse. Make T024 pass.
- [ ] T030 [P] [US1] Implement `app/src/main/java/com/atakmap/android/twcoord/coord/TaipowerGrid.java` with anchor constants (`ANCHOR_E_WEST=170000`, `ANCHOR_N_SOUTH=2400000`, `REGION_WIDTH=80000`, `REGION_HEIGHT=50000`) and the 8×3 region letter table excluding Y/Z, plus sub-region (800m/500m), 100m letters, 10m digits per research.md R8. Make T025 pass.
- [ ] T031 [US1] Implement `app/src/main/java/com/atakmap/android/twcoord/coord/CoordinateConverter.java` facade combining Projections → DatumShiftTwd67 → TaipowerGrid; apply the in-range guards from `contracts/coordinate-converter.md` and return `OutOfRange(fix)` outside the published window. Make T026 pass. Depends on T028 + T029 + T030.
- [ ] T032 [US1] Add a JMH micro-bench `app/src/test/java/com/atakmap/android/twcoord/coord/CoordinateConverterBench.java` asserting median `convert()` latency ≤ 50 μs (contract requirement; Constitution Principle IV evidence).
- [ ] T033 [US1] Implement `app/src/main/java/com/atakmap/android/twcoord/coord/Formatter.java` per `contracts/coordinate-formatter.md` — locale-aware `NumberFormat` cache, three unit branches, four state branches, `forClipboard` parity. Make T027 pass.
- [ ] T034 [US1] Implement `app/src/main/java/com/atakmap/android/twcoord/TwCoordWidget.java` skeleton extending `MapWidget`: two-line text layout, monospace font, 60% opaque dark-grey background, colour-by-state (white/amber/grey), `attach()` adds to `RootLayoutWidget.TOP_RIGHT`, `detach()` removes. No tap behaviour yet.
- [ ] T035 [US1] Wire `MapEvent.MAP_BOUNDS_CHANGED` listener inside `TwCoordMapComponent.onCreate`: on each event, build `Wgs84` from `MapView.getMapView()` centre, run `CoordinateConverter.convert(...)` with default unit `TWD97`, format via `Formatter`, call `widget.render(mapLine, currentSelfLine)`.
- [ ] T036 [US1] Implement out-of-range fallback rendering in `TwCoordWidget.render(...)`: when `DisplayLine.state == OUT_OF_RANGE`, draw a second line with the WGS84 lat/lon to 6 decimal places, in amber.
- [ ] T036a [US1] Author `docs/ui/readout-widget.md` accompanying the widget code from T034-T036: document anchor (`RootLayoutWidget.TOP_RIGHT`), two-row layout, monospace 14 dp, colour-by-state palette (white / amber / grey), and the OK / OUT_OF_RANGE visual variants. Include at least one screenshot or wireframe. Per Constitution Principle III, UI changes MUST be *accompanied* by docs/ui updates — do not defer to Polish.

**Checkpoint**: At this point, User Story 1 is fully functional — manual acceptance scenarios 1-3 of US1 in quickstart.md §7 should pass. Demoable as MVP.

---

## Phase 4: User Story 2 - Own-position readout in selected Taiwan unit (Priority: P1)

**Goal**: A second readout shows the device's GPS-derived position in the same unit, refreshing ≥ 1 Hz, with explicit "no fix" / "no permission" states.

**Independent Test**: With GPS on, observe `ME` row updates live. Toggle airplane mode → `ME` shows `no fix` within ~10 s. Revoke location permission → `ME` shows `no permission` with tap-to-settings.

### Tests for User Story 2 (TDD — author and run RED before implementation) ⚠️

- [ ] T037 [P] [US2] Write `app/src/test/java/com/atakmap/android/twcoord/SelfMarkerSubscriberTest.java` covering: (a) inbound events at 5 Hz produce exactly one downstream update per second (1 Hz debounce); (b) no event for > 10 s flips state to `NoFix`; (c) fresh event after stale recovers to `Ok`. MUST fail before T039.

### Implementation for User Story 2

- [ ] T038 [US2] Implement `app/src/main/java/com/atakmap/android/twcoord/SelfMarkerSubscriber.java` — pure-Java debouncer + stale-detector with injectable clock; takes a `Consumer<ConversionResult>` callback. Make T037 pass.
- [ ] T039 [US2] Wire `MapEvent.ITEM_CHANGED` filtered on `MapView.getSelfMarker().getUID()` inside `TwCoordMapComponent` to feed `SelfMarkerSubscriber`; on each debounced update, build `Wgs84(source=DEVICE_LOCATION)`, convert + format, call `widget.render(currentMapLine, selfLine)`.
- [ ] T040 [US2] Implement `NO_PERMISSION` rendering path in `TwCoordWidget`: check `ContextCompat.checkSelfPermission(ACCESS_FINE_LOCATION)` from `TwCoordMapComponent` on receiving no-fix state for > 30 s; if denied, render the `NO_PERMISSION` state with a tap handler that opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.

**Checkpoint**: User Story 2 demoable — acceptance scenarios 1-3 of US2 in quickstart.md §7 should pass.

---

## Phase 5: User Story 3 - Settings page (Priority: P2)

**Goal**: ATAK preference fragment with two single-select preferences (coord unit, UI language override). Live re-render of both readouts on any change. Selections persist across restarts.

**Independent Test**: Open settings → change unit → readouts immediately reformat. Change language → labels and unit tags repaint in the new language on the very next frame, no ATAK restart. Force-stop ATAK and relaunch → previous selections restored.

### Tests for User Story 3 (TDD — author and run RED before implementation) ⚠️

- [ ] T041 [P] [US3] Write `app/src/androidTest/java/com/atakmap/android/twcoord/prefs/PreferenceStoreTest.java` covering snapshot consistency, synchronous setX dispatch on UI thread, corrupt-value fallback to default, and listener registration order per `contracts/preference-store.md`. MUST fail before T043.
- [ ] T042 [P] [US3] Write `app/src/test/java/com/atakmap/android/twcoord/i18n/LocaleOverrideTest.java` covering the fallback chain: `zh`, `zh-CN`, `zh-Hant-HK`, `zh-Hans-SG`, `zh-TW` → `zh-rTW`; `ja`, `ja-JP` → `ja`; `en`, `en-US`, `ko-KR`, `fr-FR` → default `en`. MUST fail before T044.

### Implementation for User Story 3

- [ ] T043 [US3] Implement `app/src/main/java/com/atakmap/android/twcoord/prefs/PreferenceStore.java` per `contracts/preference-store.md` — typed `snapshot()`, `setCoordinateUnit`, `setLanguageOverride`, `setStaleFixThresholdMs`, synchronous UI-thread `registerOnChange`. Make T041 pass.
- [ ] T044 [US3] Implement `app/src/main/java/com/atakmap/android/twcoord/i18n/LocaleOverride.java` — pure-Java `mapSystemLocaleToBundle(Locale)` returning one of `en` / `zh-TW` / `ja`, plus `contextFor(Context, LanguageOverride, Locale systemLocale)` returning `context.createConfigurationContext(cfg)` with the resolved locale. Make T042 pass.
- [ ] T045 [P] [US3] Author `app/src/main/res/xml/preferences.xml` with two `PanListPreference` entries (`pref_coord_unit` with values `TAIPOWER`/`TWD97`/`TWD67`; `pref_ui_language` with values `SYSTEM`/`EN`/`ZH_TW`/`JA`), all titles / summaries / entry labels pulled from `strings.xml`.
- [ ] T046 [US3] Implement `app/src/main/java/com/atakmap/android/twcoord/TwCoordPreferenceFragment.java` extending `PluginPreferenceFragment(context, R.xml.preferences)` per `meshtastic_atak` precedent (PluginPreferencesFragment.java:13-26).
- [ ] T047 [US3] Register fragment in `TwCoordMapComponent.onCreate` via `ToolsPreferenceFragment.register(new ToolPreference(title, summary, key, drawable, new TwCoordPreferenceFragment(context)))`; unregister by key in `onDestroyImpl`.
- [ ] T048 [US3] Wire `PreferenceStore.Listener` in `TwCoordMapComponent`: on unit change → recompute both `DisplayLine`s and call `widget.render(...)`; on language change → first rebuild the localised `Context` via `LocaleOverride.contextFor(...)`, then re-resolve `Formatter.Strings` from the new context, then re-render. Locale-listener fires BEFORE widget-listener (registration order guarantee).
- [ ] T048a [US3] Author `docs/ui/settings-fragment.md` accompanying T045-T048: document both `PanListPreference` entries (coord unit, UI language), the live-repaint contract (FR-018), the locale option list ("Use system" / English / 中文（正體） / 日本語), and the listener registration order constraint. Include screenshots in each of the three UI languages. Per Constitution Principle III, do not defer to Polish.

**Checkpoint**: User Story 3 demoable — acceptance scenarios 1-3 of US3 in quickstart.md §7 pass. All three stories now work; manual walk in quickstart.md §7 should be 100% green.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Cross-cutting requirements that span multiple stories — clipboard copy (FR-015), instrumented UI verification, ADR records (Constitution V), `docs/ui` updates (Constitution III), and final acceptance.

- [ ] T049 [P] Write `app/src/androidTest/java/com/atakmap/android/twcoord/ClipboardCopyTest.java` per `contracts/widget-overlay.md`: tap each row in each of the three units × three UI languages (9 combos); assert `clipboardManager.getPrimaryClip().getItemAt(0).getText().toString().equals(displayedString)`; assert toast appears within 200 ms. MUST fail before T051.
- [ ] T050 [P] Write `app/src/androidTest/java/com/atakmap/android/twcoord/WidgetRenderTest.java` covering the four widget visual states (OK / OUT_OF_RANGE / NO_FIX / NO_PERMISSION), the no-double-write-per-frame contract on rapid taps, AND an SC-003 next-frame assertion: "trigger preference change → invalidate widget → on the very next `Choreographer.FrameCallback` the rendered text equals the post-change value" (using `CountDownLatch` + frame callback hook).
- [ ] T051 Implement tap-to-copy in `TwCoordWidget`: two independent tap targets (one per row); on tap, call `Formatter.forClipboard(line)`, write to `ClipboardManager` under label `"tw-coord"`, fire `ToastCallback.showCopiedToast(...)` with the localised resource. Make T049 pass.
- [ ] T052 [P] Write `app/src/androidTest/java/com/atakmap/android/twcoord/PreferenceFragmentTest.java` confirming the fragment renders both `PanListPreference` entries with localised labels in each of the three UI languages and that selection actually mutates `SharedPreferences`.
- [x] T053 ~~Author `docs/ui/readout-widget.md`...~~ **MOVED to T036a (end of US1)** per `/speckit-analyze` finding F2 (Constitution III: docs/ui MUST accompany UI changes).
- [x] T054 ~~Author `docs/ui/settings-fragment.md`...~~ **MOVED to T048a (end of US3)** per `/speckit-analyze` finding F2.
- [x] T055 ~~Author `docs/adr/0001-coordinate-math-source.md`...~~ **DONE 2026-05-16** during `/speckit-analyze` remediation (file exists; see ADR-0001).
- [x] T056 ~~Author `docs/adr/0002-no-tdal-integration.md`...~~ **DONE 2026-05-16** during `/speckit-analyze` remediation (file exists; see ADR-0002).
- [x] T057 ~~Author `docs/adr/0003-locale-override-mechanism.md`...~~ **DONE 2026-05-16** during `/speckit-analyze` remediation (file exists; see ADR-0003).
- [ ] T058 Run `./gradlew spotlessApply lint testCivDebugUnitTest connectedCivDebugAndroidTest` — all four MUST be green. Fix any failure before progressing.
- [ ] T059 Execute manual acceptance walk per `quickstart.md` §7 on the reference device against ATAK-CIV 5.7.0.3; record pass/fail per acceptance scenario in `specs/001-tw-coord-display/acceptance-log.md`; flag any scenario that did not pass for follow-up. **MUST include an SC-001 time-box sub-step**: stopwatch the duration from cold-launch tap → first valid `ME` readout and record the exact seconds; assert ≤ 5 s on the reference device.
- [ ] T060 Verify `CLAUDE.md` SPECKIT block still points at `specs/001-tw-coord-display/plan.md`; update only if the plan path moved. Also refresh `MEMORY.md` index in the user-memory directory if implementation revealed durable user preferences worth capturing. Per Constitution Principle V (English only).
- [ ] T061 Author `app/src/androidTest/java/com/atakmap/android/twcoord/FpsImpactTest.java` (or a scripted `adb shell dumpsys gfxinfo` benchmark in `tools/bench/fps_impact.ps1`) that compares ATAK frame rate over a 60 s map-pan workload with the plugin loaded vs. baseline ATAK, asserts ≤ 1 fps median drop per SC-007, and records the run in `specs/001-tw-coord-display/acceptance-log.md` alongside T059's results. Addresses analyze finding F4.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup completion; BLOCKS all user stories.
- **User Story 1 (Phase 3)**: depends on Phase 2; the MVP. Pure-JVM tests (T023-T027) can be authored as soon as Phase 2 value classes exist (T011-T019).
- **User Story 2 (Phase 4)**: depends on Phase 2; logically independent of US1 but shares `CoordinateConverter`/`Formatter`/`TwCoordWidget` (built in US1). In a single-developer flow, do after US1; with multiple developers, US2 can branch off Phase 2 alongside US1.
- **User Story 3 (Phase 5)**: depends on Phase 2 + the existence of `TwCoordWidget` from US1 (because the settings change-listener calls `widget.render(...)`). With one developer, do after US1.
- **Polish (Phase 6)**: depends on US1 + US2 + US3 being implementation-complete.

### Critical Within-Story Sequencing

- Tests for a story MUST be authored and observed failing before the implementation tasks of the same story (Constitution Principle II).
- Inside US1: T028, T029, T030 are parallelisable (different files, different math); T031 (`CoordinateConverter` facade) depends on all three.
- Inside US3: T043 (`PreferenceStore`) and T044 (`LocaleOverride`) are parallelisable; T046 / T047 / T048 depend on T043 + T044 + T045.
- Inside Polish: T051 (tap-to-copy impl) depends on T049 (test) AND on US1 being widget-complete.

---

## Parallel Execution Examples

### Phase 1 Setup (after T001 + T002 land)

```text
Run in parallel:
  T003  Spotless + pre-commit hook
  T004  AndroidManifest.xml
  T005  plugin.xml
  T006  proguard-gradle.txt
  T007  docs/adr/README.md
  T008  docs/ui/README.md
```

### Phase 2 Foundational (after T009 + T010 land)

```text
Run in parallel — all value classes and resource bundles:
  T011  CoordinateUnit enum
  T012  LanguageOverride enum
  T013  Wgs84 value class
  T014  Twd97Tm2 value class
  T015  Twd67Tm2 value class
  T016  TaipowerCode value class
  T017  ConversionResult ADT
  T018  DisplayLine value class
  T019  UserPreference value class
  T020  strings.xml (en)
  T021  strings.xml (zh-rTW)
  T022  strings.xml (ja)
```

### Phase 3 US1 — tests first (TDD), all parallel

```text
Author all RED tests in parallel:
  T023  ProjectionsTest      (TWD97 golden vectors)
  T024  DatumShiftTwd67Test  (TWD97 ↔ TWD67 golden vectors)
  T025  TaipowerGridTest     (Taipower 9-char golden vectors + Y/Z reject)
  T026  CoordinateConverterTest (facade + out-of-range)
  T027  FormatterTest        (clipboard equality across 9 combos)
```

### Phase 3 US1 — implement pure-JVM math in parallel

```text
Implementations of independent math files in parallel:
  T028  Projections (proj4j wrapper)
  T029  DatumShiftTwd67 (4-param)
  T030  TaipowerGrid (anchor table + steps)
```

### Phase 6 Polish — instrumented tests + fps benchmark in parallel

```text
Run in parallel:
  T049  ClipboardCopyTest (androidTest)
  T050  WidgetRenderTest  (androidTest, includes SC-003 next-frame)
  T052  PreferenceFragmentTest (androidTest)
  T061  FpsImpactTest / fps bench (SC-007)
```

(T053-T057 are listed as DONE/MOVED above — see strikethrough notes:
docs/ui authoring moved into US1/US3; design ADRs were authored on
2026-05-16 during `/speckit-analyze` remediation.)

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: Setup (T001–T008).
2. Phase 2: Foundational (T009–T022). Foundation is now ready.
3. Phase 3: User Story 1 (T023–T036). Write tests RED, implement to green, wire widget.
4. **STOP and VALIDATE** — install onto a device with ATAK 5.7.0.3 and run the US1 portion of `quickstart.md` §7.
5. Demo to stakeholders if ready.

### Incremental Delivery

1. Complete Setup + Foundational → foundation ready.
2. Add US1 → test independently → MVP ship.
3. Add US2 → test independently → ship.
4. Add US3 → test independently → ship.
5. Add Polish phase last — ADRs, docs/ui, instrumented UI tests, final acceptance.

### Parallel Team Strategy

With two or three developers:

1. All complete Setup + Foundational together (touching different files via the [P] markers above).
2. Once Foundational is done:
   - Dev A: US1 (Phase 3) — the read-the-map + readout overlay path. Owns `coord/` + `TwCoordWidget`.
   - Dev B: US2 (Phase 4) — the self-marker debouncer + permission flow. Reuses `coord/` once US1 lands the converter; can stub it until then.
   - Dev C: US3 (Phase 5) — `PreferenceStore` + `LocaleOverride` + fragment + listener wiring. Independent of US1/US2 until the wiring task (T048).
3. All converge for Polish (Phase 6).

---

## Format Validation Check

After `/speckit-analyze` remediation (2026-05-16):

- Active tasks: **59** (T001-T061 with T053/T054/T055/T056/T057
  closed; new T036a, T048a, T061).
- ✅ Every active task starts with `- [ ]` (or `- [x]` for the closed
  ones).
- ✅ Every task has a `T0NN[a]` ID; IDs are append-only — never
  recycled.
- ✅ `[P]` marker is present only where the task is parallelisable.
- ✅ `[US1]` / `[US2]` / `[US3]` story labels are present on every
  Phase 3-5 task and absent from Setup / Foundational / Polish tasks.
- ✅ Every task description contains a concrete repo-relative file
  path (or paths for cross-file tasks).
- ✅ Phase ordering reflects the constitution: docs/ui tasks live
  inside US1 / US3 (T036a, T048a); design ADRs were authored at
  decision time, not deferred.

## Changelog

| Date | Change | Origin |
|---|---|---|
| 2026-05-16 | Initial 60-task generation | `/speckit-tasks` |
| 2026-05-16 | T053 → T036a (docs/ui readout-widget into US1); T054 → T048a (docs/ui settings-fragment into US3); T055-T057 closed (ADRs authored as files); T050 augmented with SC-003 next-frame assertion; T059 augmented with SC-001 stopwatch sub-step; T060 reworded to explicit verification; added T061 (fps impact bench for SC-007) | `/speckit-analyze` findings F1-F6 remediation |
| 2026-05-16 | First `/speckit-implement` pass — MVP completed: Phase 1 (T001-T008), Phase 2 (T009-T022), and Phase 3 US1 minus T032 (T023-T036a). All 22 JVM unit tests GREEN, APK assembles successfully (ATAK-Plugin-atak_tw_coord_plugin-1.0.0-b9cfd2bb-5.7.0.3-civ-debug.apk). T032 JMH bench deferred per analyze finding F9 (JMH source-set placement is an implementation concern). T037-T061 are NOT yet started — they belong to the next `/speckit-implement` continuation. See ADR-0005 for the implement-cycle record. | `/speckit-implement` |
| 2026-05-16 | Second `/speckit-implement` pass — US2 + US3 implementation complete: T037 (SelfMarkerSubscriberTest with RED→GREEN bug catch on Long.MIN_VALUE sentinel overflow), T038 (SelfMarkerSubscriber pure-Java debouncer + stale detector), T039 (self-marker ITEM_REFRESH wiring via UID filter), T040 (NO_PERMISSION rendering; tap-to-settings deferred to T051), T042 (LocaleOverrideTest covering 12 fallback paths), T043 (PreferenceStore typed wrapper with commit() and CopyOnWriteArrayList listeners), T044 (LocaleOverride via createConfigurationContext), T045 (preferences.xml + arrays.xml), T046 (TwCoordPreferenceFragment), T047 (ToolsPreferenceFragment.register from the **com.atakmap.app.preferences** package), T048 (combined unit+language listener with locale-rebuild before render), T048a (docs/ui/settings-fragment.md). T041 (instrumented PreferenceStoreTest) deferred to Polish phase. All 31 JVM unit tests GREEN; APK 172 KB. See ADR-0006. | `/speckit-implement` |

## Implementation status (updated 2026-05-16, second `/speckit-implement` pass)

| Phase | Task range | Status |
|---|---|---|
| Phase 1 Setup | T001-T008 | ✅ all complete (T007 closed during analyze remediation) |
| Phase 2 Foundational | T009-T022 | ✅ all complete |
| Phase 3 US1 | T023-T036a | ✅ all complete EXCEPT T032 (JMH bench deferred per F9) |
| Phase 4 US2 | T037-T040 | ✅ all complete (T040 tap-to-settings deferred to T051) |
| Phase 5 US3 | T041-T048a | ✅ implementation complete; T041 (instrumented test) deferred to next pass |
| Phase 6 Polish | T049-T061 | ⏸ not started (T053-T057 closed during analyze remediation) |

**Build status**: `./gradlew :app:testCivDebugUnitTest :app:assembleCivDebug` → BUILD SUCCESSFUL (31/31 tests green, signed civ-debug APK produced, 172 KB).

**Next entry-point**: `/speckit-implement` (continuation) should pick up the Polish phase — instrumented tests (T041 / T049 / T050 / T052), tap handlers (T051 clipboard + T040 settings), JMH (T032), fps bench (T061), and the manual acceptance walk (T059). Two research-doc drifts noted in ADR-0006 (ToolsPreferenceFragment package, MAP_BOUNDS_CHANGED vs MAP_MOVED/SCALE + ITEM_REFRESH) should be folded back via a `/speckit-analyze` cycle before further code work.
