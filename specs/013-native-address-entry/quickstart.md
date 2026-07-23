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

### Recorded implementation evidence (2026-07-22)

- Pinned ATAK-CIV 5.7.0.9 `main.jar` SHA-256 matched
  `8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.
- `javap -public` exposed the complete `CoordinateEntryPane` method set plus
  public synchronized `registerPane`/`unregisterPane` and public static
  capability lookup.
- ATAK-CIV 5.5.1.1 and 5.5.1.10 exposed the same public registration methods;
  `CoordinateEntryPane.java` had no diff. Capability implementation changes
  were limited to an elevation focus helper, portrait dialog height, and
  import ordering, not the plugin registration seam.
- Source/API status is PASS. Exact 5.5 binary callback and dialog behavior
  remains PENDING as a physical `[RELEASE-GATE]`.

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

### Pre-change baseline (2026-07-22)

The focused `gotopage`, `nativeentry`, and `address` JVM suites completed with
`BUILD SUCCESSFUL` before feature implementation. Gradle used the existing
8.14.3 wrapper cache. For every behavioral task, record the intended failing
assertion before production changes and the focused green command afterward;
separate red/green commits are not required.

### Foundational Red-Green-Refactor (2026-07-22)

- RED: the relocated parser plus new registry, coordinator, and lookup-service
  suites failed compilation because the neutral parser package, leased registry
  session/revision/close API, coordinator close fence, and lookup contracts did
  not exist yet. This was the intended foundational failure.
- GREEN: the focused command below completed with `BUILD SUCCESSFUL` after the
  neutral parser move, leased snapshots, monotonic close fences, immutable
  lookup contracts, no-data service, and bounded shared worker were added.
- REFACTOR: added direct late-facade fencing, in-flight close suppression, and
  bounded-queue eviction assertions; the same focused command remained green.

```powershell
.\gradlew.bat :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.coord.input.*" `
  --tests "com.atakmap.android.twcoord.address.ActiveDatasetRegistryTest" `
  --tests "com.atakmap.android.twcoord.address.BatchImportCoordinatorTest" `
  --tests "com.atakmap.android.twcoord.address.lookup.AddressLookupServiceContractTest"
```

### US1 Red-Green-Refactor (2026-07-22)

- RED: the parser corpus, full-address database/service contracts, debounced
  controller, fourth-tab pane, and candidate dialog tests initially failed
  because canonical drafts, exactness/provenance, Address UI state, and the
  ATAK-window/plugin-resource dialog boundary were absent.
- GREEN: the focused command below completed with `BUILD SUCCESSFUL` after the
  full-field Address workflow, deterministic bounded candidates, synchronous
  resolved getter, metadata-only formatter, and revision-fenced chooser were
  implemented.
- REFACTOR: reusable forward-search candidate/ranking/normalization types were
  moved under neutral `address.lookup` ownership; optional provenance metadata
  is omitted when unavailable, and the complete focused suite plus Spotless
  remained green.

```powershell
.\gradlew.bat :app:spotlessApply :app:spotlessCheck `
  :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.address.lookup.TaiwanAddressParserTest" `
  --tests "com.atakmap.android.twcoord.address.lookup.DefaultAddressLookupServiceForwardTest" `
  --tests "com.atakmap.android.twcoord.address.AddressDatabaseFacadeStreetQueryTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.AddressEntryControllerTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.TaiwanCoordinateEntryPaneContractTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.TaiwanCoordinateEntryPaneSafetyTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.AddressCandidateDialogTest" `
  --tests "com.atakmap.android.twcoord.address.lookup.CompassDirectionTest" `
  --tests "com.atakmap.android.twcoord.address.lookup.StreetCandidateReorderTest" `
  --tests "com.atakmap.android.twcoord.address.lookup.StreetTextNormaliserTest" `
  --tests "com.atakmap.android.twcoord.address.forward.ForwardSearchControllerTest"
```

The physical ATAK 5.5 and 5.7.0.9 full-address/dialog journey remains PENDING
under T036 and is not implied by the JVM/Robolectric result.

### US2 Red-Green-Refactor (2026-07-22)

- RED: the 100-row projection, mode-state controller, and layout suites failed
  before the structured projection API, four compact rows, and active mode
  control existed.
- GREEN: all 100 corpus rows retained normalized and unclassified text exactly
  once across full → structured → full; structured edits recombined in stable
  field order, and the focused US2 plus US1 regression command passed.
- REFACTOR: the mode switch is now a pure projection that keeps draft revision,
  pending lookup, and host notification unchanged. Read-only text remains inert
  while the display projection can still switch.

```powershell
.\gradlew.bat :app:spotlessApply :app:spotlessCheck `
  :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.address.lookup.AddressDraftProjectionTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.AddressEntryControllerTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.TaiwanAddressLayoutTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.TaiwanCoordinateEntryPaneContractTest" `
  --tests "com.atakmap.android.twcoord.address.lookup.TaiwanAddressParserTest" `
  --tests "com.atakmap.android.twcoord.address.lookup.DefaultAddressLookupServiceForwardTest"
```

The portrait/landscape, DD-equivalent geometry, and largest supported font
checks on ATAK 5.5 and 5.7.0.9 remain PENDING under T045.

### US3 Red-Green-Refactor (2026-07-22)

- RED: reverse query, alternating activation, no-snap pane, synchronous
  teardown, and shared-widget tests failed before the production query engine,
  reverse session state, live service injection, and exact teardown ordering
  existed.
- GREEN: the complete `address`, `nativeentry`, `coord`, and plugin lifecycle
  suites passed after reverse lookup retained exact supplied WGS84 separately
  from record WGS84, 100 stale activations were fenced, and map readouts used
  background priority on the same bounded worker.
- REFACTOR: registry initialization now precedes native registrar construction;
  locale replacements receive the live service and manager navigator, while
  UI-thread unregister/dispose completes before service and leased-registry
  close. Address failure leaves the three coordinate tabs intact.

```powershell
.\gradlew.bat :app:spotlessApply :app:spotlessCheck `
  :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.address.*" `
  --tests "com.atakmap.android.twcoord.nativeentry.*" `
  --tests "com.atakmap.android.twcoord.coord.*" `
  --tests "com.atakmap.android.twcoord.plugin.TwCoordLifecycleTest"
```

Convert Coordinate, first-tap host dialogs, read-only host integration,
unload/re-enable, and both physical ATAK lines remain PENDING under T059.

### US4 Red-Green-Refactor (2026-07-23)

- RED: preference presentation tests still required all readout toggles to be
  enabled before dataset management was selectable, and lifecycle inspection
  exposed four public toolbar items.
- GREEN: focused preference, lifecycle, registry, shared address-subsystem, and
  native registrar tests passed after the management row became independent of
  readout visibility and the toolbar array was reduced to `TwCoordTool`.
- REFACTOR: removed only `OfflineAddressTool`; retained
  `ACTION_SHOW_OFFLINE_ADDRESS`, `OfflineAddressReceiver`, the settings
  navigator, and the native Address navigator. Android instrumentation sources
  for Import, Replace, Remove, progress, error, and same-session refresh also
  compiled successfully.

```powershell
.\gradlew.bat :app:spotlessApply `
  :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.TwCoordPreferenceFragmentAddressTest" `
  --tests "com.atakmap.android.twcoord.plugin.TwCoordLifecycleTest" `
  --tests "com.atakmap.android.twcoord.address.ActiveDatasetRegistryTest" `
  --tests "com.atakmap.android.twcoord.address.AddressSubsystemMultiCountyTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.NativeCoordinateEntryRegistrarTest" `
  :app:compileCivDebugAndroidTestJavaWithJavac
```

The command completed with `BUILD SUCCESSFUL`. The instrumentation journeys
remain source/compile coverage only; exact one-item host rendering and manager
operation on ATAK 5.5 and 5.7.0.9 remain PENDING under T069.

### US5 Red-Green-Refactor (2026-07-23)

- RED: upgrade and static-removal tests initially found the custom Go To and
  forward-search receivers, actions, pages, tools, preference accessors, and
  resources still present.
- GREEN: dataset/manifest byte-compatibility, retained settings, native
  last-coordinate-tab state, inert legacy preferences, neutral parser/ranking,
  and stale-action bytecode contracts passed after the duplicate workflows
  were removed.
- REFACTOR: retained legacy `pref_goto_*` key names only as non-destructive
  upgrade fixtures; production exposes no accessors. The offline manager,
  boundary data, registry, address database queries, `coord.input` parser, and
  `address.lookup.ResultOrdering` remain active.

```powershell
.\gradlew.bat :app:spotlessApply :app:spotlessCheck `
  :app:testCivDebugUnitTest `
  :app:compileCivDebugAndroidTestJavaWithJavac
```

The full JVM suite, Android resource merge, and instrumentation source compile
completed with `BUILD SUCCESSFUL`. The older-version physical upgrade on ATAK
5.5 and 5.7.0.9 remains PENDING under T080.

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
- an imported registry key using `台中市` with operator input normalized to
  `臺中市`, which must select the existing dataset rather than report it
  missing;
- a proper numbered road such as `工業區三十八路`, which may use a broader
  `工業區` retrieval fragment but must become exact only after the complete
  normalized street and address tail match;
- one reverse/Auto Fill display address with a village/neighbourhood prefix
  that resolves after copy and paste;
- three reverse rows at one identical coordinate: a longer `number`, two
  equal-length shorter numbers, and distinct IDs; the shortest number with the
  lowest ID must win on every supported SQLite backend;
- the same street/section and house number without that prefix, with one
  unique semantic match;
- two records in different villages sharing the same street/section and house
  number, which must remain ambiguous;
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
4. Open `TW Coordinates`; the offline-data manager must appear directly even
   when all three map address-row toggles are off.
5. Use the manager's top `TW Coordinates settings` button and verify Settings
   becomes visible. Select Dataset status and verify Settings closes before
   the manager becomes visible again.
6. In the manager, Import, Replace, and Remove fixtures.
7. Verify remaining datasets and current native lookup update without ATAK
   restart.
8. Verify custom Recent/marker/icon values do not influence native behavior;
   no destructive preference cleanup is required.
9. Send the retired custom Go To and forward-search action strings through a
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

## 15. Phase 8 static audit evidence

Recorded on 2026-07-22 against the feature branch before the full Gradle gate:

- `TaiwanAddressResourcesTest` passed under Robolectric API 34. It resolves the
  Address string contract in English, Taiwan Traditional Chinese, and Japanese,
  then verifies accessible names for the system selector, five Address inputs,
  mode action, and candidate action. The layout XML separately retains
  `android:labelFor` for all five labelled inputs; Robolectric 4.14 does not
  expose that XML attribute through `TextView.getLabelFor()`.
- A production-source search for `TwCoordGotoTool`, `ForwardSearchTool`, retired
  action names, retired page layouts/icons, and the three retired public tool
  labels returned no matches under `app/src/main/`.
- Dialog inspection found only `AddressCandidateDialog` and the internal offline
  manager. The candidate dialog resolves text through the localized plugin
  context and creates its window with the ATAK Activity context. Manager dialogs
  likewise resolve plugin strings first and use `getMapView().getContext()` for
  the window token. No Activity-context `R.string` lookup was found.
- `app/src/main/AndroidManifest.xml` contains no `INTERNET` permission, and a
  production-source search found no URL connection, OkHttp, or socket use.
- Searches for retired resource references and `TODO`/`FIXME`/`HACK`/`XXX`
  markers under `app/src/main/` returned no matches.
- `OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS` remains intentionally
  internal: it is registered by the component and sent only from Settings and
  native Address management guidance. It no longer has a Tools registration.

This is source/test evidence only. It does not close the physical dialog,
compatibility, screenshot, performance, memory, offline-capture, or upgrade
release gates.

### Bounded candidate category ranking (2026-07-23)

- RED: focused compilation failed after tests introduced the not-yet-existing
  candidate-pool, shortlist, bounded database-pool, and current-map-anchor
  seams.
- GREEN: focused SQL, shortlist, facade, lookup-service, semantic-ranking,
  controller, and registrar tests passed after implementing five
  deterministically ordered SQL pools, exact short-circuiting, `6 / 8 / 4 / 2`
  visible allocation, stable deduplication/backfill, and UI-thread map-centre
  capture.
- The SQLite fixture proves every category remains at or below 20 rows even
  when the caller asks for 200. Taiwan Boulevard fixtures cover text-prefix
  and numeric-nearest ordering, direct-road preference when the query omits
  `巷`/`弄`, and operation without a valid distance anchor.
- The complete gate
  `:app:spotlessApply :app:spotlessCheck :app:lint
  :app:testCivDebugUnitTest :app:assembleCivDebug` completed successfully with
  441 tests, zero failures/errors, two existing skips, and a civ-debug APK.
  This is source/build evidence only and does not close any physical-device or
  ATAK 5.5 compatibility release gate.

### Candidate-policy documentation synchronization (2026-07-24)

- ADR-0026 received a dated implementation clarification for the five bounded
  SQL pools, exact-only behavior, `6 / 8 / 4 / 2` allocation, deduplication,
  backfill, optional map-centre distance category, and direct-road preference.
  No new ADR was required because this specifies the accepted shared bounded
  lookup architecture without changing its external contract or storage.
- The ADR/UI indexes, README, changelog, and canonical English/Traditional
  Chinese user and Address guides now describe the same 20-row candidate
  behavior. The README no longer presents retired custom Go To Recent,
  marker-mode, or Custom Icon workflows as current features.
- `python scripts/check-doc-images.py` checked 27 documentation images and
  passed names, local image links, Git LFS, and sensitive metadata.
- A local Markdown target scan, reviewed-diff sensitive-path scan, and
  `git diff --check` passed.

## 16. Phase 8 repository quality gates

Executed on 2026-07-22 from the repository root with the existing Gradle 8.14.3
cache:

```text
.\gradlew.bat :app:spotlessApply :app:spotlessCheck :app:lint \
  :app:testCivDebugUnitTest :app:assembleCivDebug
```

Result: `BUILD SUCCESSFUL in 1m 23s`; 62 actionable tasks (47 executed, 15
up-to-date). `spotlessCheck`, Android lint, the complete civ-debug JVM suite,
and civ-debug APK assembly all completed without errors. Gradle reported the
existing flat-directory/configuration-time/deprecation notices and the JVM
class-sharing notice; no new source lint failure was emitted.

Documentation/reviewed-scope checks then produced:

- `python scripts/check-doc-images.py`: 26 images checked; names, local image
  links, Git LFS attributes, and sensitive metadata passed;
- local Markdown target scan: 77 relative links under `docs/` and this feature
  directory resolved; absolute website routes and external URLs were excluded;
- sensitive scan for workstation-home paths, local-file URI prefixes, and the
  known local username under `docs/` and this feature directory: no matches;
- `git diff --check`: pass;
- reviewed-scope status contained only the intentional ADR broken-link repair
  before the final evidence commit.

The link scan found and repaired one historical ADR link to the feature-013
retired `ic_tw_coord_goto.xml`; the ADR now records it as a removed historical
input instead of claiming the file still exists. These gates establish source,
test, documentation, and debug-build readiness only.
