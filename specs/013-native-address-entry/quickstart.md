# Quickstart: Validate Native Taiwan Address Entry

This guide is the runnable validation handoff for feature 013. Commands and
device journeys become acceptance evidence only after implementation. A build
or ATAK 5.7 run never substitutes for the ATAK 5.5 physical-device gates.

## 1. Prerequisites

- JDK and Android SDK versions already accepted by this repository.
- ATAK-CIV 5.7.0.9 SDK available through `ATAK_SDK_5_7_0_9` or the existing
  uncommitted Gradle SDK configuration.
- Local ATAK source checkout available through `ATAK_CIV_SOURCE` for minimum-
  runtime source evidence.
- A valid offline address bundle containing boundary data and at least two
  county datasets for functional, migration, memory, and concurrency tests.
- One ATAK-CIV 5.5 device/emulator and the current ATAK-CIV 5.7.0.9 reference
  device for release acceptance.
- No raw TPP bundle, device serial, home-directory path, or email-derived
  artifact name is committed with evidence.

Confirm the active feature and clean scope:

```powershell
Get-Content .specify/feature.json
git status --short --branch
```

Expected active directory:

```text
specs/013-native-address-entry
```

## 2. Reproduce ATAK API evidence

```powershell
javap -classpath "$env:ATAK_SDK_5_7_0_9\main.jar" -public `
  com.atakmap.android.gui.coordinateentry.CoordinateEntryPane

javap -classpath "$env:ATAK_SDK_5_7_0_9\main.jar" -public `
  com.atakmap.android.gui.coordinateentry.CoordinateEntryCapability

Get-FileHash "$env:ATAK_SDK_5_7_0_9\main.jar" -Algorithm SHA256

git -C "$env:ATAK_CIV_SOURCE" diff 5.5.1.1 5.5.1.10 -- `
  atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryPane.java `
  atak/ATAK/app/src/main/java/com/atakmap/android/gui/coordinateentry/CoordinateEntryCapability.java
```

Expected compile SDK hash:

```text
8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70
```

Retain command output with the plan evidence. Exact ATAK 5.5 binary/runtime
behavior remains `[RELEASE-GATE]` until run physically.

## 3. Test-first focused suites

After each new behavior has first failed for the intended reason, run focused
JVM/Robolectric suites for the eventual implementation packages:

```powershell
.\gradlew.bat :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.address.lookup.*" `
  --tests "com.atakmap.android.twcoord.nativeentry.*"

.\gradlew.bat :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.address.*" `
  --tests "com.atakmap.android.twcoord.coord.*"
```

Required focused coverage:

- at least 100 normalization/parser corpus addresses;
- at least 100 full-to-structured-to-full lossless round trips;
- unique exact, duplicate exact, ambiguous, partial, no-match, no-dataset, and
  failure outcomes;
- no automatic nearest-partial resolution;
- forward and reverse point semantics, including reverse preserving the exact
  host WGS84;
- request cancellation, close, session/draft/dataset revision fencing, and no
  late callbacks;
- registry read leases racing with replace/remove/close;
- late import completion rejected after coordinator/registry close;
- three coordinate tabs unaffected by Address failure;
- non-null all-tab activation, null active-only Clear, Address Auto Fill,
  read-only, formatting metadata, locale refresh, and disposal;
- single toolbar item and stale retired actions producing no legacy UI;
- existing datasets/preferences surviving upgrade without re-import.

## 4. Repository quality gates

```powershell
.\gradlew.bat :app:spotlessApply
.\gradlew.bat :app:spotlessCheck
.\gradlew.bat :app:lint
.\gradlew.bat :app:testCivDebugUnitTest
.\gradlew.bat :app:assembleCivDebug
git diff --check
```

No new warning, skipped failure, or unrelated deletion is accepted silently.

## 5. Static architecture and resource audit

```powershell
rg -n -A12 "IToolbarItem\[\]" `
  app/src/main/java/com/atakmap/android/twcoord/plugin/TwCoordLifecycle.java

rg -n "TwCoordGotoTool|OfflineAddressTool|ForwardSearchTool|TwCoordGotoReceiver|ForwardSearchReceiver" `
  app/src/main app/src/test

rg -n "setTitle\(R\.|setMessage\(R\.|setItems\(R\." `
  app/src/main/java/com/atakmap/android/twcoord

rg -n "INTERNET" app/src/main/AndroidManifest.xml

rg -n "pref_open_goto|TW Coord GoTo|TW Addr Search|TW Offline Addr" `
  README.md docs app/src/main/res app/src/main/java
```

Expected:

- toolbar array contains only `TwCoordTool`;
- retired Tool/receiver classes have no production references;
- no Activity-context dialog resolves plugin IDs through ATAK resources;
- no INTERNET permission;
- active documentation and UI strings do not direct operators to retired
  Tools entries;
- historical specs/ADRs may retain their original wording.

## 6. Address parser and lookup fixture

Use a provenance-recorded fixture covering at least:

- `臺中市南屯區黎明路二段130號6樓之4`;
- `台`/`臺`, full-width digits, whitespace, punctuation, and numeric
  subnumbers;
- overlapping administrative names such as `臺南市新市區`;
- proper numeral names such as `八德路`;
- segmented roads, named localities, lanes, alleys, floors, and rooms;
- duplicate street/house names across districts/counties;
- one missing house number where nearest fallback would be wrong;
- one point with no applicable imported county dataset.

For every fixture record, retain raw input, expected normalized form, expected
components/unclassified text, expected candidate IDs, and dataset provenance.

## 7. Install and reload on a device

Build/install with the repository device workflow. After installation, disable
then re-enable the plugin or fully restart ATAK. A reinstall alone may leave
the cached old Tools entries visible.

Record without exposing serials:

- ATAK exact version/build;
- device model, Android version, screen size/orientation, and font scale;
- plugin APK SHA-256 and source commit;
- dataset county/date/schema/hash;
- date, scenario, and PASS/FAIL/PENDING.

## 8. Primary native Address journeys

### Full address

1. Open ATAK native Go To and select Taiwan → Address.
2. Verify first use shows one full-address field and the mode switch.
3. Enter a unique exact fixture address.
4. Verify loading completes, normalized address is shown, and no map movement
   occurs before host confirmation.
5. Confirm through ATAK and verify the stored candidate WGS84.
6. Time the journey; it must complete within 30 seconds for an ATAK-familiar
   operator.

### Structured round trip

1. Enter the full complex fixture address.
2. Switch to four structured fields and inspect all components.
3. Edit the tail, switch back, and verify no character or unclassified text is
   lost or duplicated.
4. Switch repeatedly during pending lookup; only the current draft may win.

### Ambiguous candidates

1. Enter a fixture that returns multiple exact/credible candidates.
2. Verify no candidate is silently selected and host confirmation remains
   unresolved.
3. Tap `Choose result`; verify the dialog appears on first tap.
4. Verify rows have distinguishing context and selection alone does not move
   the map.
5. Edit the draft while/reopening the dialog and confirm stale selection is
   rejected.

### Dialog resource ownership

On the real ATAK host, verify candidate, error, and dataset-management dialogs:

- appear on first invocation;
- use localized plugin text without `Resources.NotFoundException`;
- remain attached to the ATAK Activity window;
- survive close/reopen without stale callback.

Robolectric success alone is not evidence for this cross-context behavior.

## 9. Convert Coordinate, Auto Fill, Clear, and read-only

1. Open Convert Coordinate from a main-island map item.
2. Select Taiwan and verify Taipower/TWD97/TWD67 prepare immediately while
   Address resolves asynchronously.
3. Switch to Address after resolution and Copy; verify formatting uses address
   metadata and triggers no new lookup.
4. Compare the returned point before/after Address inspection: reverse lookup
   must preserve the exact host WGS84 even when the nearest address record is
   offset.
5. Repeat with no applicable dataset; coordinate tabs remain valid and Address
   shows management guidance.
6. Invoke Address Auto Fill at a map-center point and verify the same no-snap
   rule.
7. Invoke Clear with Address active; only Address clears. Repeat with each
   coordinate tab and verify active-only behavior.
8. Open one read-only host flow; Address may display but cannot edit/select or
   change the host point.
9. Alternate two points 100 times with delayed callbacks and verify zero stale
   address/candidate/resolution state.

## 10. Tools and upgrade migration

1. Start from an older plugin version with at least two imported counties,
   address preferences, and seeded custom Go To Recent/marker/icon values.
2. Upgrade and reload the plugin.
3. Verify ATAK Tools shows exactly `TW Coordinates` for this plugin.
4. Open `TW Coordinates` with all three map address-row toggles off; dataset
   status/management must still be selectable.
5. Open the internal manager and Import, Replace, and Remove fixtures.
6. Verify remaining datasets and current native lookup update without ATAK
   restart.
7. Verify custom Recent/marker/icon values do not influence native behavior;
   no destructive preference cleanup is required.
8. Send the retired custom Go To and forward-search action strings through a
   diagnostic test; no page, map change, or uncaught failure may result.

## 11. Concurrency and lifecycle

1. Hold a lookup against county A while replacing/removing county A; verify the
   read session completes safely or is cancelled before the old facade closes.
2. Begin an import, unload the plugin during the current item, and verify late
   completion cannot register a facade after close.
3. Disable/unload with Address lookup and candidate dialog active.
4. Verify ATAK remains responsive, late work is inert, and the next dialog has
   no Taiwan pane while disabled.
5. Re-enable and verify exactly one working Taiwan pane and one Tools entry.
6. Repeat registrar start/stop in the existing 100-cycle automated harness.

## 12. Performance, memory, and offline gates

### `[RELEASE-GATE]` latency

Capture named trace sections for normalization, mode projection, forward
lookup, reverse lookup, result commit, and render on the current reference
device with representative datasets:

- normalization/mode projection: p95 and worst-case ≤ 100 ms;
- forward/reverse: median ≤ 1,000 ms and p95 ≤ 2,000 ms over at least 100
  measured lookups.

### `[RELEASE-GATE]` memory

With boundary data and at least two counties imported, run the established
five-minute panning/lookup session. ATAK process RSS must remain ≤ 200 MiB and
must not grow from unbounded candidates, requests, dialogs, or facade handles.

### `[RELEASE-GATE]` offline

In airplane mode with ATAK sync/server connections disabled, capture the ATAK
process while completing full-address, candidate, reverse, and dataset-manager
journeys. Retain evidence of zero plugin-triggered outbound attempts. If no
capture facility is available, keep the gate PENDING.

## 13. Compatibility matrix

| Scenario | ATAK 5.5 minimum | ATAK 5.7.0.9 current |
|----------|------------------|----------------------|
| Exactly one Taiwan pane / one Tools item | PENDING | PENDING |
| Full and structured Address entry | PENDING | PENDING |
| Unique exact and ambiguous candidate dialog | PENDING | PENDING |
| Convert Coordinate reverse no-snap behavior | PENDING | PENDING |
| Address Auto Fill, Clear, Copy/format | PENDING | PENDING |
| Read-only flow | PENDING | PENDING |
| Missing dataset leaves coordinates usable | PENDING | PENDING |
| Import/Replace/Remove through TW Coordinates | PENDING | PENDING |
| Upgrade retains imported datasets | PENDING | PENDING |
| Small portrait pane / large font reachability | PENDING | PENDING |
| Active lookup/dialog unload and re-enable | PENDING | PENDING |
| Airplane-mode journey | PENDING | PENDING |

Do not convert PENDING from source inspection, JVM tests, compilation, TPP
success, or a different ATAK line.

## 14. Documentation and release closure

Before merge/release:

- add ADR-0026 with the superseding decisions in `research.md` R15;
- update `docs/ui/native-taiwan-coordinate-entry.md`, settings UI docs, README,
  changelog, English/Traditional Chinese user guides, Address guide, and
  offline dataset guide;
- replace/renumber/sanitize active screenshots and verify Git LFS state;
- freeze and commit the selected plugin version before TPP source staging;
- complete or explicitly disposition every `[RELEASE-GATE]` without claiming
  unexecuted compatibility, performance, signer, or provenance evidence;
- run release-readiness before TPP upload, tagging, or publication.
