# Quickstart: Validate Native Taiwan Coordinate Entry

This guide is executable after implementation. Device-only rows must remain
reported as pending until run on the named ATAK line.

## 1. Confirm feature and version inputs

From the repository root:

```powershell
Get-Content .specify/feature.json
git branch --show-current
rg -n "ATAK_VERSION|compileSdk|minSdkVersion|sourceCompatibility" app/build.gradle
Get-Content local.properties | Select-String "sdk.path|takdev.plugin"
```

Expected:

- Feature directory: `specs/011-native-coordinate-entry`
- Branch: `011-native-coordinate-entry`
- ATAK minimum runtime metadata: `5.5.0`
- Android compile/minimum SDK: 36/26
- Java compatibility: 17
- ATAK compile SDK path or Gradle project property: 5.7.0.9

## 2. Reproduce ATAK public-API evidence

```powershell
$atakSdk = $env:ATAK_SDK_5_7_0_9
if ([string]::IsNullOrWhiteSpace($atakSdk)) {
    throw 'Set ATAK_SDK_5_7_0_9 to the ATAK-CIV 5.7.0.9 SDK directory.'
}
$jar = Join-Path $atakSdk 'main.jar'
$javap = (Get-Command javap -ErrorAction Stop).Source
& $javap -classpath $jar -public 'com.atakmap.android.gui.coordinateentry.CoordinateEntryPane'
& $javap -classpath $jar -public 'com.atakmap.android.gui.coordinateentry.CoordinateEntryCapability'
Get-FileHash $jar -Algorithm SHA256
```

Required methods are all pane callbacks plus `getInstance`, `registerPane`, and
`unregisterPane`. Expected jar SHA-256:
`8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.

Against the local ATAK source checkout:

```powershell
$atakSource = $env:ATAK_CIV_SOURCE
if ([string]::IsNullOrWhiteSpace($atakSource)) {
    throw 'Set ATAK_CIV_SOURCE to the local atak-civ checkout.'
}
git -C $atakSource show 5.5.1.1:atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryPane.java
git -C $atakSource grep -n "public synchronized void registerPane\|public synchronized void unregisterPane" 5.5.1.1 -- atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryCapability.java
```

The 5.5.1.1 source is the earliest public implementation anchor for the 5.5
runtime family. ADR-0024 upgrades the compile/current-device SDK to 5.7.0.9
without changing ADR-0022's `5.5.0` runtime compatibility token.

**T001 implementation status (2026-07-17): PASS via compatibility ADR.** The connected
`SM-X826B` (`<DEVICE_SERIAL>`) reports ATAK-CIV `5.7.0.9 (7a0f6f29)`,
`versionCode=1782294331`. SDK signatures and the `main.jar` hash above match
the selected compile/current baseline. Exact ATAK 5.5 and current feature
device journeys remain pending until run.

## 3. Run test-first focused tests

### US1 Red evidence (2026-07-17)

Command (with `sdk.path` and `takdev.plugin` supplied as Gradle project
properties pointing to ATAK-CIV 5.7.0.9):

```powershell
.\gradlew.bat :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.nativeentry.*" `
  --tests "com.atakmap.android.twcoord.prefs.PreferenceStoreNativeEntryTest"
```

Result: **RED as intended** at `compileCivDebugUnitTestJavaWithJavac`. After
removing an unrelated test-harness import, the rerun reported only the missing
feature 011 production surface: `KEY_NATIVE_ENTRY_LAST_UNIT`,
`TaiwanEntryFormatter`, `TaiwanEntryController`,
`TaiwanCoordinateEntryPane`, layout IDs, and
`NativeCoordinateEntryRegistrar`. This is the retained T004–T008 Red baseline.

US1 Green rerun: **PASS**, 19 focused tests, after enabling Robolectric merged
Android resources for the real pane layout. A second Green run after
`TwCoordMapComponent` lifecycle integration also passed. The T015 physical
ATAK journey remains pending.

US2 Red run: **RED as intended** at test compilation. The new TWD97/TWD67
controller methods and layout IDs for easting, northing, zones, panes, and the
zone-119 advisory were absent from the US1 implementation. This is the retained
T016/T017 Red baseline.

US2 Green run: **PASS** for the focused controller/pane suites. The combined
native-entry, unchanged `coord.*` golden-vector, and
`CoordinateParserRoundTripTest` regression run also passed. T021's ATAK-owned
dialog/device portion remains pending.

US3 Red run: **RED as intended** at test compilation because
`TaiwanEntryController.format(Wgs84, TaiwanEntryFormatter)` did not yet exist.
The new pane tests also cover host Auto Fill/Clear/Copy, horizontal-only output,
programmatic event suppression, and disposed late controls (T022/T023).

US3 Green run: **PASS** (22 focused controller/pane tests) after pure formatter
delegation and host Auto Fill/Clear callback completion. Host-owned clipboard
dispatch and map-movement evidence remain device-only under T026.

US4 baseline and Red/Green runs: the upgrade fixture preserving ten Recent
entries and every `pref_goto_*` value passed against the already-isolated
preference implementation. Registrar lookup/register/partial-register,
rollback, unregister, documented linkage-error, and fatal-error tests passed;
the added dispose-time `NoClassDefFoundError` case first failed as intended and
passed after the narrow failure-containment fix. T031 device fallback remains
pending.

US5 Red/Green runs: locale refresh first failed at compilation because
`refreshLocale()` was absent, then passed for detached immediate replacement,
attached defer-until-detach, stale generation invalidation, and exactly-one-tab
invariants. Supplied-point/read-only controller and pane suites pass, including
disabled controls, no listener/result mutation, horizontal-only metadata, and
canonical read-only format. T038 real ATAK dialog/configuration evidence remains
pending.

Cross-cutting source/UI audit: **PASS**. The native pane has one outer
`ScrollView`; DD-style compact 30/65/5 label/input/unit rows; native underline
inputs at `wrap_content` height; 13 sp normal / 17 sp large title text; 48 dp
system/zone selectors; a 2 dp top inset; and no empty status-area height. A state-aware
segment text selector provides white unselected, black selected, and muted
read-only labels. All 31 `native_entry_*` keys have exact English/zh-rTW/Japanese
parity, XML parsing passed, status uses a polite accessibility live region, the
manifest has no `INTERNET` permission, and native code has no `CoordinateFormat`
or `pref_goto_*` mutation.

Unreleased compact-layout regression (originally labelled v1.4.1,
2026-07-18): **RED as intended** at test compilation because the DD-style row
ID and ATAK-equivalent font dimensions did not yet exist. After replacing
card-style fields with weighted underline rows, the focused
native-entry/preference run passed 49 tests. The complete
`:app:spotlessCheck :app:lint :app:testCivDebugUnitTest :app:assembleCivDebug`
gate then passed 373 tests with zero failures and two existing skips, producing
an intermediate debug APK whose generated filename contains `1.4.1`. This
layout work is not released separately; it is included in v1.4.2. Device
screenshots for Taipower/TWD97/TWD67 no-overlap acceptance remain pending under
T053.

Final local quality gate against `ATAK-CIV-5.7.0.9-SDK`: **PASS** on
2026-07-17 for `:app:spotlessCheck`, `:app:lint`,
`:app:testCivDebugUnitTest`, and `:app:assembleCivDebug`. The generated APK is
`ATAK-Plugin-atak_tw_coord_plugin-1.3.3-68337b12-5.5.0-civ-debug.apk`, size
4,070,778 bytes, SHA-256
`6EE293FA8C4853979633CF765285DFE4994D65D09E60928C49A6C23999CFA293`.

Current-device attempt: `adb install -r` succeeded on SM-X826B
`<DEVICE_SERIAL>`. The device was at the Android lock screen, so ATAK could not be
opened and no host-dialog scenario was converted to PASS. Exact ATAK 5.5 device
validation also remains unavailable and pending under T045.

## Requirement evidence reconciliation

`PASS` means the named source/JVM gate is complete. `PARTIAL` keeps required
host/device evidence explicit; it is not a release pass.

| ID | Status | Evidence or remaining gate |
|---|---|---|
| FS-001 | PASS | Registrar failure/rollback/linkage tests; custom receiver registered first |
| FS-002 | PARTIAL | Idempotent stop/dispose tests pass; real unload/re-enable is T031/T045 |
| FS-003 | PASS | Checked invalid/out-of-coverage controller and pane tests |
| FS-004 | PARTIAL | Generation/disposed callback tests pass; active-dialog recreation is T038/T045 |
| FS-005 | PARTIAL | 5.5.0 metadata and 5.5.1.1 source anchor pass; exact 5.5 runtime is T045 |
| FR-001 | PARTIAL | Exactly-one registrar tests pass; host tab observation is T015/T045 |
| FR-002 | PASS | One pane exposes three mutually exclusive controller/UI systems |
| FR-003 | PASS | Active segmented selection and separate-draft tests |
| FR-004 | PASS | Existing Taipower parser reused with regression suite |
| FR-005 | PASS | Separate integer easting/northing fields and strict parser tests |
| FR-006 | PASS | Explicit 121/119 controls and visibility tests |
| FR-007 | PASS | TWD67 zone-119 advisory layout and pane test |
| FR-008 | PASS | Existing converter/golden-vector regression suite |
| FR-009 | PASS | Invalid states clear resolved output and throw checked host error |
| FR-010 | PASS | Localised validation-state messages and status tests |
| FR-011 | PARTIAL | Auto Fill replacement tests pass; ATAK button dispatch is T026 |
| FR-012 | PARTIAL | Clear state tests pass; ATAK button dispatch is T026 |
| FR-013 | PARTIAL | Pure canonical formatter tests pass; ATAK clipboard is T026 |
| FR-014 | PASS | Supplied-point activation and replacement tests |
| FR-015 | PARTIAL | Read-only controller/View tests pass; additional host flow is T038 |
| FR-016 | PASS | Exactly-once human listener and programmatic suppression tests |
| FR-017 | PASS | 100-cycle, stale generation, locale refresh, and max-live-one tests |
| FR-018 | PASS | Ordinary/version-skew containment and fatal JVM rethrow tests |
| FR-019 | PARTIAL | Independent custom receiver lifecycle retained; device fallback is T031 |
| FR-020 | PASS | Ten-entry upgrade fixture proves every `pref_goto_*` value unchanged |
| FR-021 | PARTIAL | Three-locale parity and locale registrar tests pass; visual refresh is T038 |
| FR-022 | PARTIAL | Source dimensions/contrast/accessibility pass; paired device reachability is T042/T045 |
| FR-023 | PARTIAL | No `INTERNET` permission/network code; packet-capture proof is T043 |
| FR-024 | PARTIAL | Compile/current SDK 5.7.0.9 passes; exact 5.5 runtime is T045 |
| FR-025 | PASS | Android min/compile SDK values were not changed |
| FR-026 | PASS | No global `CoordinateFormat` or host preference mutation |
| FR-027 | PASS | Horizontal metadata only; no marker/map/elevation action in pane |
| QR-001 | PASS | ADR-0022/0024 split minimum-runtime and compile/current axes |
| QR-002 | PASS | Narrow host catches, listener isolation, lint, and registrar tests |
| QR-003 | PARTIAL | One scroll owner, parity, and a11y source pass; screenshots/reachability are T040/T042 |
| QR-004 | PARTIAL | Offline architecture passes; Perfetto/network capture is T043 |
| QR-005 | PASS | Existing WGS84/golden-vector regressions pass unchanged |
| QR-006 | PASS | Additive lifecycle and independent preference fixture pass |
| SC-001 | PARTIAL | Implementation complete; native-dialog discovery time is T015/T045 |
| SC-002 | PASS | Authoritative coordinate regression suites pass |
| SC-003 | PARTIAL | Functional JVM tests pass; device timing/host controls are T026/T043 |
| SC-004 | PARTIAL | 100 registrar cycles pass; real 100 reload/unload evidence is T031/T045 |
| SC-005 | PARTIAL | Requires exact ATAK 5.5 and 5.7.0.9 device matrix T045 |
| SC-006 | PASS | 30/30 keys match across English, zh-rTW, and Japanese |
| SC-007 | PARTIAL | Horizontal conversion tests pass; built-in pane round trip is T038 |
| SC-008 | PARTIAL | No network capability; airplane-mode host journey/capture is T043/T045 |
| SC-009 | PARTIAL | Ten-entry byte-preservation fixture passes; seeded device upgrade is T031 |

Convergence Phase 9 Red/Green: the new safety/trace suite first failed because
the trace seam and callback guards were absent. It now passes with ordinary
resource/conversion/listener failures contained, documented linkage errors
contained, fatal JVM errors rethrown, disposal continuing step-by-step, and
balanced named `TWCoord.native.{activate,render,switch,validate,autofill,clear,format}`
sections. The full Gradle quality gate passed again after T049/T050.

Final source-scope audit: `git diff --check` passed, all local Markdown links
in README, changelog, user guide, ADR-0023, and the native UI document resolve,
and the working tree changes are confined to feature 011 implementation,
tests, resources, compatibility configuration, Spec Kit artifacts, and their
canonical documentation. Pre-existing staged feature changes were preserved;
no unrelated file was reverted or staged by this audit.

After each focused test has first failed for the intended missing behaviour:

```powershell
.\gradlew.bat :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.nativeentry.*"
.\gradlew.bat :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.prefs.PreferenceStoreNativeEntryTest"
```

Required coverage:

- Taipower/TWD97/TWD67 parse and forward-fill adapters
- Zone 121/119 visibility and deterministic native formatter text, including
  explicit E/N and zone 121
- Taipower 9/11-character input compatibility and 11-character / 1 m Auto
  Fill/format output
- Invalid, incomplete, out-of-coverage, and disposed results
- Programmatic versus human changed-listener semantics
- Read-only state
- Preference default/corrupt fallback and independence from `pref_goto_*`
- 100 registrar start/stop cycles with exactly zero/one registration
- Partial-registration rollback and late callback safety

Then run the existing coordinate regression suites unchanged:

```powershell
.\gradlew.bat :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.coord.*"
.\gradlew.bat :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.gotopage.CoordinateParserRoundTripTest"
.\gradlew.bat :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.gotopage.TaipowerParserTest"
.\gradlew.bat :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.gotopage.TwdTm2ParserTest"
```

## 4. Run repository gates

```powershell
.\gradlew.bat :app:spotlessApply
.\gradlew.bat :app:spotlessCheck
.\gradlew.bat :app:lint
.\gradlew.bat :app:testCivDebugUnitTest
.\gradlew.bat :app:assembleCivDebug
```

All commands must pass with no new warnings treated as acceptable by omission.

## 5. Static safety and localisation audit

```powershell
rg -n "INTERNET" app/src/main/AndroidManifest.xml
rg -n "CoordinateEntryCapability|CoordinateEntryPane" app/src/main/java/com/atakmap/android/twcoord
rg -n "pref_native_entry_last_unit|pref_goto_" app/src/main/java/com/atakmap/android/twcoord/prefs/PreferenceStore.java
git diff --check
```

Expected:

- No `INTERNET` permission.
- ATAK native entry references are isolated to the registrar/pane and component
  lifecycle wiring.
- Native preference accessors never write a custom `pref_goto_*` key.
- Every new string key exists in English, zh-rTW, and Japanese with matching
  format arguments.

## 6. On-device compatibility matrix

Install the built plugin separately on an ATAK-CIV 5.5 runtime and the exact
ATAK-CIV 5.7.0.9 reference runtime. Record exact ATAK build, plugin APK hash,
device, screen size/orientation, Android font scale, date, operator, and
pass/fail evidence. Compare the native pane with the existing custom GoTo page
at the same orientation and font scale.

| Scenario | ATAK-CIV 5.5 | ATAK-CIV 5.7.0.9 / SM-X826B |
|----------|---------------|-------------------------------|
| Plugin start shows exactly one Taiwan tab | PENDING | PENDING |
| Valid Taipower native Go To | PENDING | PENDING |
| Valid TWD97 zone 121 and zone 119 | PENDING | PENDING |
| Valid TWD67 zone 121 and zone 119 advisory | PENDING | PENDING |
| Invalid/out-of-range confirm and Copy remain in dialog | PENDING | PENDING |
| Native Auto Fill, Clear, and Copy | PENDING | PENDING |
| Native field geometry matches DD and does not overlap host controls | PENDING | PENDING |
| Editable point-details flow | PENDING | PENDING |
| Read-only/additional native location flow | PENDING | PENDING |
| Active-dialog Activity/configuration recreation recovers safely | PENDING | PENDING |
| Disable/unload removes Taiwan tab without host crash | PENDING | PENDING |
| Custom TW Coord GoTo remains unchanged | PENDING | PENDING |
| Airplane-mode native journey | PENDING | PENDING |

Do not convert PENDING to PASS from build output or source inspection.

### Paired field-size and reachability baseline

Inspect ATAK's DD pane and the Taiwan pane on the same device, orientation, and
Android font scale. The Taiwan layout must retain compact horizontal rows,
native underline inputs at `wrap_content` height, 13 sp normal / 17 sp large
title text, 48 dp system/zone selectors, a 2 dp top inset, and a `GONE` empty
status area. Run the comparison at the default font scale and the largest
configured font scale accepted as usable; record the numeric font scale. Every
Taiwan field and zone control must remain reachable through the pane's single
scroll owner and must not overlap ATAK's elevation or action controls.

## 7. Device scenario details

### Primary native Go To

1. Open ATAK's native Go To coordinate dialog.
2. Verify one **Taiwan** choice, with Taipower selected on first use.
3. Enter a pinned Taipower golden vector and confirm.
4. Verify ATAK performs its normal Go To at the same WGS84 point as the custom
   page, without plugin-created marker/elevation side effects.
5. Time the first-use journey from opening native Go To to confirmation; an
   ATAK-familiar operator must complete it in under 30 seconds without external
   conversion guidance.

### System and zone safety

1. Test Taipei 101/main-island values in all three systems.
2. Test Magong or another authoritative zone-119 value in TWD97 and TWD67.
3. Verify the selected zone is always visible and TWD67 zone 119 shows the
   advisory.
4. Paste partial, non-numeric, bad-letter, bad-zone (test seam), and
   out-of-coverage input; verify no host action or prior-point replacement.
5. Paste decimal, signed, grouped, and non-ASCII TWD digits; verify they are
   rejected rather than rounded or locale-interpreted.
6. Starting from pinned main-island and zone-119 host points, switch each of
   MGRS, DD, DM, DMS, and UTM → Taiwan → the same built-in format without a
   human edit and compare the returned point against the active system's
   established precision budget. Address is excluded because it is a lookup
   pane rather than a deterministic coordinate-format round trip.

### Native controls

1. Centre the map on a main-island point and invoke Auto Fill for each system.
2. Repeat at a zone-119 point; Taipower must clear/show unrepresentable while
   TWD values select zone 119.
3. Invoke Copy and verify system, values, and explicit TWD zone without draft
   mutation.
4. Invoke Clear and verify confirmation is rejected until a valid new draft.

### Lifecycle and fallback

1. Before upgrade, seed at least 10 custom-page Recent entries and select a
   non-default marker mode; record the stored values.
2. Open Taiwan entry, then disable/unload the plugin.
3. Confirm ATAK remains responsive and no late callback crashes the process.
4. Open the dialog after unload and verify no Taiwan tab.
5. Re-enable and verify exactly one working tab.
6. Open custom **TW Coord GoTo** and verify all seeded Recent entries, the
   non-default marker mode, custom icon route, and saved fields are unchanged.
7. With the native dialog closed, change each supported UI locale and reopen;
   verify the Taiwan tab, internal labels, hints, advisory, and errors refresh.
8. With Taiwan entry open and an unconfirmed partial draft, trigger an Android
   Activity/configuration recreation on a controlled test device (for example,
   an orientation or locale change that the tested ATAK build actually handles
   by recreating its Activity). Retain log evidence of recreation, then verify
   there is no crash or host action and that the reopened pane is in a valid
   empty or last-confirmed state, never a half-converted draft.

### Performance and offline

In a debug build, wrap pane activation/rendering, controller switch/validate,
Auto Fill, Clear, conversion, and native formatting entry points with named
`android.os.Trace` sections. Capture a Perfetto system trace with at least 20
iterations of every applicable operation/system/zone combination; export the
trace and record every sample, worst-case, and p95 plugin duration. Both
worst-case and p95 must be below 100 ms excluding ATAK animation. Do not infer
this result from JVM timing.

For SC-008, disable ATAK server/data-sync connections, capture the ATAK process
with Android Studio Network Inspector or a device packet-capture facility, and
repeat the full native Go To and Auto Fill journey in airplane mode. Retain the
capture showing zero plugin-triggered connection attempts. If the device has no
usable capture facility, keep SC-008 pending rather than treating airplane mode
alone as proof.

## 8. Documentation and decision closure

Before merge:

- Add ADR-0023 for the one-pane architecture and lifecycle decision.
- Update `docs/user-guide.md`, `README.md` if its entry-point table changes,
  and `CHANGELOG.md`.
- Preserve the custom page as the documented advanced/fallback path.
- Attach the completed 5.5/current device matrix; do not convert build or
  source inspection into device PASS evidence.
