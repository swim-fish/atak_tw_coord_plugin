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
- ATAK compile SDK path in `local.properties`: 5.7.0.3

## 2. Reproduce ATAK public-API evidence

```powershell
$jar = 'C:\Users\<user>\source\tak\ATAK-CIV-5.7.0.3-SDK\main.jar'
$javap = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\javap.exe'
& $javap -classpath $jar -public 'com.atakmap.android.gui.coordinateentry.CoordinateEntryPane'
& $javap -classpath $jar -public 'com.atakmap.android.gui.coordinateentry.CoordinateEntryCapability'
Get-FileHash $jar -Algorithm SHA256
```

Required methods are all pane callbacks plus `getInstance`, `registerPane`, and
`unregisterPane`. Expected jar SHA-256:
`C847ADF2992D623E256AFBAC76489CB203AE1D6831D56F9DCC6B5E9D9F280763`.

Against the local ATAK source checkout:

```powershell
git -C C:\Users\<user>\source\tak\atak-civ show 5.5.1.1:atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryPane.java
git -C C:\Users\<user>\source\tak\atak-civ grep -n "public synchronized void registerPane\|public synchronized void unregisterPane" 5.5.1.1 -- atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryCapability.java
```

This proves the earliest available 5.5.1.1 source, not an exact 5.5.0 binary.
Before release, obtain exact 5.5.0 SDK/binary or device evidence; if it cannot
be obtained, create a superseding compatibility ADR and update the declared
minimum rather than rewriting ADR-0022 or marking the matrix complete.

## 3. Run test-first focused tests

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

Install the built plugin separately on exact ATAK 5.5.0, the earliest sourced
5.5.1.1 line, and the current ATAK 5.7.0.3 reference device. Record exact ATAK
build, plugin APK hash, device, screen size/orientation, Android font scale,
date, operator, and pass/fail evidence. The 5.5.1.1 portrait run must exercise
its smaller 55%-of-screen dialog height. At each configuration, compare the
native pane with the existing custom GoTo page at the same orientation and font
scale.

| Scenario | Exact ATAK 5.5.0 | ATAK 5.5.1.1 / 55% portrait | ATAK 5.7.0.3 |
|----------|------------------|------------------------------|----------------|
| Plugin start shows exactly one Taiwan tab | PENDING | PENDING | PENDING |
| Valid Taipower native Go To | PENDING | PENDING | PENDING |
| Valid TWD97 zone 121 and zone 119 | PENDING | PENDING | PENDING |
| Valid TWD67 zone 121 and zone 119 advisory | PENDING | PENDING | PENDING |
| Invalid/out-of-range confirm and Copy remain in dialog | PENDING | PENDING | PENDING |
| Native Auto Fill, Clear, and Copy | PENDING | PENDING | PENDING |
| Native field sizes match custom GoTo and are no less reachable | PENDING | PENDING | PENDING |
| Editable point-details flow | PENDING | PENDING | PENDING |
| Read-only/additional native location flow | PENDING | PENDING | PENDING |
| Active-dialog Activity/configuration recreation recovers safely | PENDING | PENDING | PENDING |
| Disable/unload removes Taiwan tab without host crash | PENDING | PENDING | PENDING |
| Custom TW Coord GoTo remains unchanged | PENDING | PENDING | PENDING |
| Airplane-mode native journey | PENDING | PENDING | PENDING |

Do not convert PENDING to PASS from build output or source inspection.

### Paired field-size and reachability baseline

Inspect both `tw_coord_goto.xml` and the native pane on the same device,
orientation, and Android font scale. The native layout must retain the shipped
custom GoTo dimensions: 20 sp input text, 14 dp Taipower vertical padding,
13 dp TWD field padding, 52 dp system selectors, 50 dp zone selectors, a 10 dp
TWD field gap, and 12 dp content inset. Run the comparison at the default font
scale and the largest configured font scale at which the custom GoTo page is
accepted as usable; record the numeric font scale. Every native field and zone
control must remain at least as large and reachable through the pane's single
scroll owner.

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
- Attach the completed device matrix; do not claim ATAK 5.5 runtime validation
  without the actual run.
