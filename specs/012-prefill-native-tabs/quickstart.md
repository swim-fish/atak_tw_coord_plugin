# Quickstart: Verify All Native Taiwan Tabs Are Prefilled

## Preconditions

- Configure the ATAK-CIV 5.7.0.9 SDK outside Git through
  `ATAK_SDK_5_7_0_9` (or the repository's documented portable SDK setting).
- Connect the reference ATAK 5.7.0.9 device for device steps.
- Do not record a real device serial or workstation home path in committed
  evidence; use `<DEVICE_SERIAL>` and portable environment variables.

## 1. Run test-first focused verification

Before production changes, add and run failing tests for:

- main-island activation preparing all three systems;
- zone-119 activation preparing TWD97/TWD67 and clearing Taipower;
- switching systems without Auto Fill;
- zero programmatic human-change callbacks;
- active-only result, Clear, and Auto Fill;
- 100 alternating activations with zero stale state;
- unexpected preparation failure clearing every old result.

```powershell
./gradlew :app:testCivDebugUnitTest --tests "*TaiwanEntryControllerTest" --tests "*TaiwanCoordinateEntryPaneContractTest"
```

Record the expected RED state before implementing, then the GREEN state after
the controller/pane changes.

## 2. Run full repository gates

```powershell
./gradlew :app:spotlessApply
./gradlew :app:spotlessCheck
./gradlew :app:lint
./gradlew :app:testCivDebugUnitTest
./gradlew :app:assembleCivDebug
```

Also confirm the existing coordinate converter, national-vector, zone-119,
and round-trip tests pass without changed tolerances or constants.

## 3. Install the debug APK

Use the repository's generated Civ debug APK and a placeholder serial in any
saved evidence:

```powershell
adb devices -l
adb -s <DEVICE_SERIAL> install -r <DEBUG_APK>
```

Confirm the installed package still declares ATAK compatibility 5.5.0 and the
plugin reports 1.4.2 while the device actually runs ATAK-CIV 5.7.0.9. These
are separate version axes.

## 4. Main-island Convert Coordinate journey

1. Tap a map item's displayed coordinate to open ATAK Convert Coordinate.
2. Select **Taiwan** once.
3. Without Auto Fill, inspect Taipower, TWD97, and TWD67.
4. Verify all three are populated, both TWD systems select zone 121, and each
   representation resolves to the same source location within existing error
   budgets.
5. Repeat with a different, clearly separated point and verify no value from
   the first point remains.

## 5. Zone-119 and unavailable-system journey

1. Open Convert Coordinate for a Penghu or Kinmen/Matsu golden point.
2. Select Taiwan and inspect all internal systems without Auto Fill.
3. Verify TWD97 and TWD67 are populated with zone 119.
4. Verify Taipower is empty and shows the existing unavailable state, with no
   stale main-island code.

## 6. Command and read-only regressions

- Invoke native Clear and verify only the active draft is cleared.
- Invoke native Auto Fill and verify only the active draft is replaced.
- In an editable flow, edit one system and confirm ATAK consumes only it.
- In a read-only shared flow, verify all prepared values are present but every
  human mutation remains blocked.
- Unload/reload the plugin and confirm late callbacks do not crash ATAK.

## 7. Performance and repeated activation

Use the existing `TWCoord.native.activate` trace around at least 20
main-island and 20 zone-119 activations. Record p95 and worst-case; both must
be below 100 ms on the reference device. Run 100 alternating activations for
two points and observe zero stale field, zone, status, or returned result.

## 8. Minimum-runtime evidence

Repeat the device journey on an actual ATAK 5.5 runtime when available. Until
then, report the row as **DEVICE PENDING**; do not infer it from compilation,
5.5.1.1 source, or the 5.7.0.9 device result.

## 9. Sensitive-information scan

Review the final diff and generated evidence for local paths and identifiers:

```powershell
git diff --check
git diff -- . ':!*.png' ':!*.jpg' | Select-String -Pattern '[A-Za-z]:\\Users\\|/Users/|/home/|file:/{3}|<known-local-username>'
```

Replace any match with `<USER_HOME>`, `<TAK_WORKSPACE>`, `<DEVICE_SERIAL>`, or
the documented environment variable before committing.
