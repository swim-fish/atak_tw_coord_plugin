# Quickstart: Native Taiwan Input UX

This guide is the implementation and acceptance checklist for Feature 014. It
does not treat a successful build as ATAK runtime, keyboard, accessibility, or
release evidence.

## 1. Prerequisites

- Windows PowerShell from the repository root.
- Java/Gradle environment already used by the project.
- ATAK-CIV 5.7.0.9 SDK available through `ATAK_SDK_5_7_0_9` or the repository's
  accepted portable SDK setting.
- Current reference device: Galaxy Tab S10+ (`SM-X826B`) with the exact
  ATAK-CIV 5.7.0.9 runtime and default keyboard.
- `[RELEASE-GATE]` An exact ATAK-CIV 5.5.x device/runtime for the
  minimum-runtime matrix.
- No workstation-specific path, account name, or device-owner identifier in
  committed evidence.

Resolve the active feature before running feature-specific work:

```powershell
Get-Content -Raw .specify/feature.json
```

Expected directory:

```text
specs/014-native-entry-input-ux
```

### Implementation baseline record

Recorded on 2026-07-30 before Feature 014 production changes:

| Item | Baseline |
|------|----------|
| Branch | `codex/014-native-entry-input-ux` |
| Starting commit | `17d5e6d758dc5e44aca70f867351b92059a250da` |
| Dirty scope | Staged `.specify/feature.json` and new `specs/014-native-entry-input-ux/` planning artifacts only; no production source, resource, or current documentation change |
| `PLUGIN_VERSION` | `1.4.4` |
| Shell Java | Eclipse Temurin OpenJDK `25.0.3` |
| Gradle | `8.14.3` |
| Gradle launcher/daemon Java | Eclipse Temurin OpenJDK `17.0.17` |

The workstation's default local ATAK SDK pointer was older than the accepted
compile SDK. Every recorded Gradle command therefore supplied explicit
project-property overrides for the locally available
`ATAK-CIV-5.7.0.9-SDK`; no workstation path is recorded here.

## 2. Reproduce ATAK API evidence

Feature 014 adds no ATAK seam, but the current public-pane evidence must still
match the pinned SDK:

```powershell
$atakSdkPath = $env:ATAK_SDK_5_7_0_9
if ([string]::IsNullOrWhiteSpace($atakSdkPath)) {
    throw 'Set ATAK_SDK_5_7_0_9 to the ATAK-CIV 5.7.0.9 SDK directory.'
}

$atakMainJar = Join-Path $atakSdkPath 'main.jar'
Get-FileHash -Algorithm SHA256 $atakMainJar

javap -public -classpath $atakMainJar `
  com.atakmap.android.gui.coordinateentry.CoordinateEntryPane

javap -public -classpath $atakMainJar `
  com.atakmap.android.gui.coordinateentry.CoordinateEntryCapability
```

Expected SHA-256:

```text
8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70
```

Compare the public signatures with ADR-0023/ADR-0024. Do not infer ATAK 5.5
runtime behavior from this 5.7.0.9 check.

### Reproduced API evidence

The local `ATAK-CIV-5.7.0.9-SDK/main.jar` SHA-256 was reproduced as:

```text
8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70
```

JDK 17 `javap -public` confirmed:

- `CoordinateEntryPane` retains `getUID`, `getName`, `getView`, `onActivate`,
  `getGeoPointMetaData`, `autofill`, `format`, and `setOnChangedListener`;
- `CoordinateEntryCapability` retains public synchronized `registerPane`,
  `unregisterPane`, and `getInstance`, plus its recorded public dialog,
  lookup, format, click, preference, and dispose methods.

The public ATAK-CIV 5.5.1.1 source links in ADR-0023/ADR-0024 remain the
minimum-runtime source anchor. Exact ATAK 5.5 device behavior remains a
separate release gate.

## 3. Red: add focused tests first

Before changing production code, add failing tests for:

- all A-H east-west and A-E north-south Taipower letters;
- I/J east-west and F-J north-south rejection;
- direct `TaipowerCode` invariants and encoder wrap vectors;
- lossless raw/split projection and projection refusal;
- 9-character 10 m and 11-character 1 m precision;
- default/saved/corrupt input-mode preference;
- editor flags, actions, limits, focus order, and dispose guards;
- 36 dp visible / 48 dp reachable selector geometry and transparent-band taps;
- English, Traditional Chinese (Taiwan), and Japanese parity.

Run the focused baseline and new suites:

```powershell
.\gradlew.bat :app:testCivDebugUnitTest `
  --tests "com.atakmap.android.twcoord.coord.input.TaipowerParserTest" `
  --tests "com.atakmap.android.twcoord.coord.TaipowerGridTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.TaipowerEntryDraftTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.TaiwanEntryControllerTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.TaiwanCoordinateEntryPaneContractTest" `
  --tests "com.atakmap.android.twcoord.nativeentry.TaiwanCoordinateEntryPaneSafetyTest" `
  --tests "com.atakmap.android.twcoord.prefs.PreferenceStoreNativeEntryTest"
```

Record the intentional failures before implementation.

### US1 Red record

The focused T007-T008 command compiled and executed 13 tests before production
changes. Six expectations failed exactly at the missing Feature 014 boundary:

- inline no-fullscreen/no-extract and Taipower force-ASCII flags;
- the current-field action/focus matrix;
- deterministic Next;
- Done/Search keyboard dismissal;
- one logical physical-Enter action.

The remaining safety and host-boundary expectations stayed green. This is the
recorded Red state for US1.

### Pre-change automated baseline

All baseline commands used the explicit local ATAK-CIV 5.7.0.9 SDK/plugin
overrides described above.

| Gate | Result |
|------|--------|
| `:app:spotlessCheck` | PASS (`BUILD SUCCESSFUL`, 31 s) |
| Existing native-entry, `TaipowerGridTest`, and `TaipowerParserTest` suites | PASS; the cache-warming invocation exceeded the client 60 s capture window after execution, and the immediate rerun completed successfully with the test task up to date |
| `:app:lint` | PASS (`BUILD SUCCESSFUL`, combined lint/assemble run 51 s) |
| `:app:assembleCivDebug` | PASS (`BUILD SUCCESSFUL`, combined lint/assemble run 51 s) |

Pre-existing non-fatal output included the offline TakDev missing-connected-test
notice, `flatDir` metadata warning, Spotless configuration-time resolution
warning, Gradle deprecation notice, Java deprecated-API notes, and the test JVM
class-data-sharing warning. Feature 014 must not introduce an additional lint
warning or convert these baseline notices into errors.

### Foundation regression record

`Feature014TaipowerFixtures` now centralizes raw variants, 9/11-character
fixtures, valid/invalid A-H/A-E boundary sets, provenance vectors, and encoder
wrap vectors. `NativeEntryFeature014RegressionTest` locks the pre-feature
single-scroll-owner layout, existing coordinate/address surfaces, atomic
all-system activation, the historical active-only Clear/Auto Fill baseline,
host-owned confirmation, and manifest/source offline boundary. Section 12
records the later ADR-0029 change to all-page non-null Auto Fill while retaining
active-page-only Clear.

The first test compile intentionally exposed test-only Java compatibility
issues (`findViewById` type inference and unavailable `Files.readString`);
after replacing them with an explicit `View` cast and `readAllBytes`, the
focused foundation suite passed (`BUILD SUCCESSFUL`, 10 s).

## 4. Green: Taipower domain and projection

Verify the canonical forms in both modes:

| Precision | Single input | Split input |
|-----------|--------------|-------------|
| 10 m | `H7509 DB40` | `H` / `7509` / `DB` / `40` |
| 1 m | `H7509 DB4016` | `H` / `7509` / `DB` / `4016` |

Required parser/encoder fixtures:

| Fixture | Expected |
|---------|----------|
| `G8150 HD7812` | TWD67 `(235571, 2675382)` |
| `W9999 HE9999` | TWD67 `(329999, 2449999)` |
| TWD67 `(258000, 2655000)` | `H1010 AA0000` |
| TWD67 `(258799, 2655499)` | `H1010 HE9999` |
| TWD67 `(258800, 2655000)` | `H1110 AA0000` |
| TWD67 `(258000, 2655500)` | `H1011 AA0000` |

Verify:

- existing golden vectors and round-trip tolerances do not change;
- all output shapes use `[A-H][A-E]`;
- invalid complete drafts expose no WGS84 point;
- filters reject extra split characters without replacing accepted content;
- final lengths 0/1/3 are incomplete, 2 is 10 m, and 4 is 1 m;
- raw → split → raw preserves exact raw text when no coordinate edit occurred;
- unprojectable raw text remains exact and visible in single mode;
- programmatic activation/Auto Fill creates canonical 11-character values in
  both projections without a human-change callback;
- active Clear empties both projections but retains the selected mode.

Re-run the focused tests until green.

### US2 Red, Green, and refactor record

The first T015-T018 run failed at test compilation with the expected missing
Feature 014 boundaries: `TaipowerInputMode`, `TaipowerEntryDraft`, controller
mode/part operations, the plugin preference key/accessors, and the raw/split
view resource IDs. The compiler stopped after 100 reported missing-symbol
errors (`BUILD FAILED`, 30 s), establishing the US2 Red state before production
implementation.

Green adds one revisioned draft shared by exact raw and guided projections,
safe projection refusal, typed persisted mode selection, atomic controller
staging, and the 1/4/2/4 guided editor. The focused US2 suites first passed
together (`BUILD SUCCESSFUL`, 23 s).

The regression pass exposed three mock-controller fixtures that did not yet
provide the new draft/mode snapshot. After extending that test seam, all US2,
US1, safety, pane-contract, preference, and foundation suites passed together
(`BUILD SUCCESSFUL`, 26 s; 30 tasks, 3 executed and 27 up to date). Device mode
round trips, keyboard behavior, outer-island interaction, locale replacement,
and p95 timing remain T028.

### Requested right-side mode-action refinement

Current-device review requested that Taipower match the Address mode-action
pattern instead of using a full-width segmented row. The new pane contract
first failed compilation on the four intentionally absent
`native_entry_taipower_body`, `native_entry_taipower_content`,
`native_entry_taipower_actions`, and `native_entry_taipower_mode` resource
boundaries (`BUILD FAILED`, 4 s).

Green replaces that row with an 8:2 content/action layout and one borderless
48 dp right-side action. The action names the alternate layout and retains
lossless switching, focused-editor handoff, projection refusal, read-only
projection, persistence, and inert disposal. The focused pane contract first
passed in 23 s; the expanded pane/controller/draft/preference/lifecycle
regression set passed in 26 s. `:app:spotlessCheck` plus the complete JVM suite
passed in 42 s, and `:app:lint :app:assembleCivDebug` passed in 15 s.
Documentation image/LFS/metadata validation passed for 29 images, and
`git diff --check` passed.

On 2026-07-30, the resulting plugin 1.4.4 Civ Debug APK was installed on the
Galaxy Tab S10+ running Android 16 and ATAK-CIV 5.7.0.9. ATAK was fully
restarted; one plugin initialization signal was observed, with zero native
entry registration, version-skew, fatal, or plugin-load errors in the bounded
ATAK-process log window. This smoke result does not replace T028/T048 visual,
interaction, accessibility, timing, or minimum-runtime acceptance.

### US3 Red, Green, and refactor record

The first T029-T032 run executed 85 tests. Three expectations failed at the
intended old domain boundary: the parser accepted noncanonical I/J east-west
and F-J north-south aliases, and the `TaipowerCode` constructor accepted each
over-permissive range (`BUILD FAILED`, 14 s). Existing golden vectors, guided
draft feedback, and output-shape checks remained green.

ADR-0028 now records the A-H/A-E provenance and intentional alias rejection.
The parser and value object enforce the same range, while the encoder asserts
its geometric `0..7`/`0..4` invariants instead of clamping. The complete US3
domain suites, provenance/wrap vectors, `CoordinateParserRoundTripTest`, draft,
controller, pane, and formatter regressions passed together (`BUILD
SUCCESSFUL`, 14 s; 30 tasks, 5 executed and 25 up to date). On-device invalid
entry and no-map-movement evidence remains T039.

## 5. Green: inline IME and focus

Static/editor checks:

- every editable Taiwan field is single-line;
- every editable field has `NO_FULLSCREEN` and `NO_EXTRACT_UI`;
- only Taipower alphanumeric fields have `FORCE_ASCII`;
- actions follow the contract's Next/Done/Search matrix;
- split limits are 1/4/2/4;
- county and district remain selector controls and non-IME editors;
- all `nextFocus*` IDs are plugin owned;
- auto-advance occurs only for fixed split groups 1-3;
- final two digits remain focused and accept two more digits;
- Done/Search/physical Enter never invoke host confirmation;
- read-only and disposed fields start no input or late callback.

Robolectric proves the configuration and plugin callbacks. It does not prove a
real keyboard keeps ATAK Go To visible.

### US1 Green and refactor record

The current-field inline IME flags, action matrix, and plugin-owned
`nextFocusForward`/`nextFocusDown` links are now declared in the pane layout.
The pane consumes only Next, Done, Search, and Enter actions from its own
visible/enabled editors. Next transfers focus to the configured plugin editor;
terminal actions dismiss the keyboard and clear focus without invoking ATAK
confirmation. Physical Enter down/up pairs produce one action.

The refactor centralizes action routing, rejects actions during render,
read-only, or disposed states, invalidates posted renders by lifecycle
generation, and removes editor listeners during disposal. The US1 contract,
safety, and foundation regression suites passed together (`BUILD SUCCESSFUL`,
21 s; 30 tasks, 15 executed and 15 up to date). Real-device keyboard behavior,
orientation coverage, host-control reachability, and p95 timing remain T014.

## 6. Green: selector presentation

Automated geometry must prove:

```text
target height: 48 dp
visual inset:   6 dp top + 6 dp bottom
visible track:  36 dp
```

For system, TWD97 zone, and TWD67 zone selectors:

- group and child bounds are at least 48 by 48 dp;
- track and checked fill both draw at 36 dp;
- vertical padding is zero;
- child rectangles do not overlap;
- top/bottom transparent-band taps select the intended option exactly once;
- disabled-and-checked remains distinct and announced as checked/disabled;
- programmatic selection remains silent;
- labels remain one-line, centered, unellipsized, and unclipped for
  EN/zh-TW/JA at font scales 1.0 and 2.0;
- the Address controls and single outer scroll owner do not regress.

### US4 Red, Green, and refactor record

The first T040-T041 run failed at test compilation on the six references to the
not-yet-defined selector touch, visual, and inset dimensions (`BUILD FAILED`,
4 s). This established the selector resource Red boundary before drawable or
layout changes.

Green defines the 48/36/6 dp relationship in normal and large resources,
centers the track and every option state with named insets, gives
checked-disabled state precedence, and applies explicit 48 dp bounds and
localized accessibility context to the system and both zone selectors. The
focused geometry and pane-contract suites passed (`BUILD SUCCESSFUL`, 14 s).

Robolectric does not reliably dispatch coordinate touch events to an
unattached weighted `RadioButton`. The automated contract therefore proves
that each sampled top/bottom coordinate is inside the same non-overlapping
48 dp native button but outside its 36 dp visual fill, then exercises that
button's normal click path and exactly-once callback. Physical coordinate taps
remain T048. The complete JVM suite, including US1-US3 and foundation
regressions, passed after this refactor (`BUILD SUCCESSFUL`, 41 s; 30 tasks,
3 executed and 27 up to date).

## 7. Full local quality gate

After the focused suites pass:

```powershell
.\gradlew.bat :app:spotlessApply
.\gradlew.bat :app:spotlessCheck
.\gradlew.bat :app:testCivDebugUnitTest
.\gradlew.bat :app:lint
.\gradlew.bat :app:assembleCivDebug
git diff --check
```

Review the resulting diff and verify:

- no new runtime dependency, Android permission, network path, telemetry, or
  main-thread database/file I/O;
- no host-owned resource ID or non-public ATAK method;
- no write to ATAK's MGRS preference;
- no stale `[A-J]` output assertion in current production/tests/docs;
- no missing locale key among `values`, `values-zh-rTW`, and `values-ja`;
- no real workstation path, user identifier, device-owner identifier, or
  binary metadata in reviewed files.

### 2026-07-30 local quality-gate record

The first combined Gradle invocation exceeded the 60-second command wrapper
and was not counted as evidence. Each gate was then rerun in bounded batches
with the pinned ATAK-CIV 5.7.0.9 SDK overrides:

- `:app:spotlessApply`: `BUILD SUCCESSFUL` in 3 s;
- `:app:spotlessCheck :app:testCivDebugUnitTest`: `BUILD SUCCESSFUL` in 2 s
  (33 tasks, 1 executed and 32 up to date);
- `:app:lint`: `BUILD SUCCESSFUL` in 2 s (31 tasks, 3 executed and 28 up to
  date);
- `:app:assembleCivDebug`: `BUILD SUCCESSFUL` in 1 s (38 tasks, 1 executed and
  37 up to date);
- locale parity: 209 string keys aligned across `values`,
  `values-zh-rTW`, and `values-ja`;
- `python scripts/check-doc-images.py`: 29 images checked; names, local image
  links, Git LFS, and sensitive metadata passed;
- `git diff --check`: passed with no output.

The reviewed-diff audit found no build-file or manifest change, new runtime
dependency, Android permission, network path, telemetry, main-thread I/O,
reflection, non-public ATAK method, host-owned resource ID, or MGRS preference
write. The Taipower layout preference uses its own plugin key. Current
production, tests, and user-facing documentation use the canonical A-H/A-E
ranges; historical migration references remain explicitly labeled as legacy.
The reviewed text contains no workstation path or user identifier, and this
feature changes no binary asset.

The documentation refactor link checker reports 82 pre-existing repository
failures, all outside the Feature 014 reviewed files; no failure originates
from this feature's README, guide, reference, UI, ADR, or Spec Kit files.

## 8. Current-device acceptance `[RELEASE-GATE]`

Record:

| Evidence | Value |
|----------|-------|
| Git commit and clean/dirty state | |
| Plugin version/build variant/signer fingerprint | |
| Device model/Android build/density | |
| ATAK version/build | |
| Default IME package/version/subtype | |
| Orientation/locale/font scale/pane width | |
| Perfetto trace/screen-recording evidence IDs | |
| Baseline and candidate `dumpsys meminfo`/heap-dump IDs | |
| Start/end time | |

### Measurement protocol

1. Capture Android Perfetto/System Trace with input, view, window-manager, IME,
   frame, scheduler, disk, and network activity. Capture synchronized screen
   video so the first visible focus/IME, projection, or validation frame is
   unambiguous.
2. For focus timing, measure field tap/input dispatch to the first focus or
   visible-IME frame. For mode/validation timing, measure the click/edit
   dispatch to the first frame containing the requested projection or status.
3. Collect 20 samples for every required field and mode. Do not discard slow or
   failed samples. Sort each set and report nearest-rank p95 at
   `ceil(0.95 * N)`.
4. Capture `adb shell dumpsys meminfo <ATAK_PACKAGE>` before pane creation,
   after steady state, and after 20 open/dispose/reload cycles. Capture a heap
   dump after the cycles and count live/disposed Feature 014 panes, controllers,
   drafts, listeners, and views.
5. Repeat the same memory scenario against the pre-feature baseline. Report
   heap and PSS deltas; explain any increase above 10%, and record an ADR if the
   increase is an accepted architectural trade-off.
6. Inspect Perfetto disk/network/UI-thread slices and the manifest diff to
   confirm no added network I/O, synchronous UI-thread file I/O, telemetry, or
   permission.

### Journey A - Inline keyboard

For every editable Taiwan field in portrait and landscape:

1. Tap the field 20 times across reopen cycles.
2. Confirm focus or keyboard feedback appears while ATAK Go To remains visible.
3. Confirm elevation, Auto Fill, Clear, Copy, marker, and confirm controls
   remain reachable through the host flow.
4. Exercise Next, Done/Search, edit/correct, and physical Enter when available.
5. Confirm no plugin-owned replacement/full-screen editor appears.

Pass:

- 100% of supported-default-IME attempts retain Go To context.
- At least 95% show focus/keyboard feedback within 500 ms.
- Zero duplicate host actions or draft loss.

### Journey B - Taipower single/split

1. Enter both pinned 9/11-character fixtures in each mode.
2. Run at least 100 complete and representable-partial raw/split round trips.
3. Continue the final split group from two to four digits.
4. Test every valid/invalid 100 m letter boundary.
   Confirm an out-of-range attempt remains visible and the localized message
   identifies east-west A-H or north-south A-E.
5. Test lowercase, spaces, CR/LF paste, no-space form, and surrounding
   parentheses in single mode.
6. Attempt to switch an unprojectable raw draft.
7. Activate, Auto Fill, Clear, close/reopen, locale-switch, read-only, and
   reload while each mode is selected.
8. Auto Fill once with an outer-island host point. Confirm Taipower is
   unavailable while TWD97/TWD67 refresh in zone 119 and Address starts from
   the same exact WGS84 point.

Pass:

- identical resolved point and precision between modes;
- zero switch-only character/state/point changes;
- invalid/incomplete values never move or confirm;
- saved mode restores, missing/corrupt value uses single mode;
- mode switching and validation feedback meet 100 ms p95.

### Journey C - Compact selectors

For system and both zone selectors:

1. Capture sanitized screenshots at font scales 1.0 and 2.0 in EN/zh-TW/JA,
   portrait and landscape.
2. Use recorded device density to confirm the visible track is 36 dp, allowing
   at most one physical pixel for antialiasing.
3. Inspect accessibility bounds and confirm at least 48 by 48 dp.
4. Tap each option's top and bottom transparent bands 20 times.
5. Use TalkBack/Switch Access to verify names, order, checked, disabled, and
   read-only state.
6. Confirm no clipped/ellipsized label and every plugin/host control remains
   reachable.

Pass: zero wrong selections, overlapping targets, duplicate callbacks, missing
labels, or hidden current selection.

### Journey D - Lifecycle and safe degradation

1. Repeat 20 open/register/use/dispose/reload cycles, including disposal while
   an editor owns focus.
2. Switch locale with focus active.
3. If available, smoke-test a third-party/floating IME, hardware keyboard,
   DeX, and multi-window.
4. Confirm unexpected IME presentation never loses the draft, crashes ATAK, or
   duplicates a host action.
5. Inspect retained objects after the cycles: no disposed pane and no more than
   256 KiB of Feature 014 state per live pane.

## 9. Minimum-runtime acceptance `[RELEASE-GATE]`

Repeat Journeys A-D on an exact ATAK-CIV 5.5.x runtime. Record the same device,
IME, locale, orientation, font, timing, lifecycle, and accessibility evidence.

Until this is complete:

- source/API compatibility may be reported;
- current-device behavior may be reported;
- exact minimum-runtime feature compatibility must remain pending.

A narrowed minimum-runtime claim is not an automatic fallback. It requires
explicit user acceptance, and the accepted scope plus omitted device evidence
must be recorded in release notes. Unexecuted ATAK 5.5.x behavior must never be
reported as passed.

## 10. Documentation and screenshot gate

After UI/string freeze:

1. Add ADR-0028 and update the ADR supersession index.
2. Update:
   - `docs/reference/coordinate-systems.md`;
   - `docs/ui/native-taiwan-coordinate-entry.md`;
   - `docs/user-guide.md`;
   - `docs/user-guide_zh.md`;
   - `docs/README.md`;
   - root `README.md` only where its concise workflow summary is affected;
   - `CHANGELOG.md`.
3. Recapture current native-pane screenshots that display the old selector or
   do not show the new Taipower mode, including `23a`/`23b` when still
   applicable.
4. Follow `docs-screenshot-workflow`: crop/redact, strip non-rendering
   EXIF/XMP metadata, and verify LFS.
5. Run:

```powershell
python scripts/check-doc-images.py
git lfs ls-files
git diff --check
```

6. Inspect every changed binary rather than trusting its extension.

## 11. Release completion rule

Feature implementation is locally complete only when the automated gates are
green. A public compatibility/release claim additionally requires:

- exact ATAK 5.5.x and 5.7.0.9 device evidence;
- default-IME, orientation, locale, font, accessibility, performance, and
  lifecycle evidence;
- final screenshots/docs matched to the frozen UI;
- release version/source ref/signer/artifact provenance;
- a successful TPP preflight followed by a clean exact-candidate build using
  `.\gradlew.bat :app:clean :app:assembleCivRelease`;
- a source archive generated by
  `python scripts/build-tpp-source-zip.py --verify-build` from that candidate;
- durable release staging under `dist/release-v<VERSION>/`, outside
  Gradle-owned `build/`, created from a separately user-authorized TPP response
  by `scripts/stage-tpp-release.py`, with recorded SHA-256 hashes and signer
  fingerprint;
- a passing `python scripts/check-release-readiness.py --phase public` result
  for the staged candidate and all release-note dispositions;
- a signed annotated immutable tag for the exact verified candidate, created
  only after the public gate passes and the user explicitly authorizes it;
- every unresolved `[RELEASE-GATE]` completed or, after explicit user
  acceptance, documented in release notes with the narrowed claim and omitted
  evidence.

Tag creation, push, TPP upload, and publication are separate externally
mutating actions and each remains subject to explicit user authorization.

## 12. All-page Auto Fill refinement

ADR-0029 changes the non-null host Auto Fill contract without changing ATAK
button ownership:

1. Open Go To → Taiwan and select any of Taipower, TWD97, TWD67, or Address.
2. Invoke Auto Fill once from a main-island point.
3. Confirm the originally selected page remains selected.
4. Switch through all four pages. Taipower, TWD97, and TWD67 must resolve to
   the supplied point; Address must finish from the same exact WGS84 without
   snapping to its nearest record.
5. Repeat from an outer-island point. Taipower must be unavailable while both
   TWD pages select zone 119 and Address remains usable.
6. Invoke Clear on each page in turn. Only the active page may be cleared;
   Address Clear must also cancel pending lookup/candidates.
7. Confirm Auto Fill and Clear emit no human-change callback and do not invoke
   ATAK confirmation.

### Red → Green record

The first focused run after T074 compiled and executed 60 tests, then failed
exactly three new expectations:

- `TaiwanEntryControllerTest` showed inactive coordinate drafts remained stale;
- `NativeEntryFeature014RegressionTest` showed the active-only baseline;
- `TaiwanCoordinateEntryPaneContractTest` showed Address and inactive
  coordinate pages were not refreshed.

This was the expected Red state (`BUILD FAILED`, 20 s). After T075-T076, the
same 60-test focused command passed (`BUILD SUCCESSFUL`, 16 s). The Green
implementation reuses atomic coordinate staging, starts Address lookup from the
same exact WGS84, retains the selected page, preserves active-page-only Clear,
and emits no human-change callback.

### T078 automated gates

| Gate | Result |
|------|--------|
| `:app:spotlessApply :app:spotlessCheck :app:testCivDebugUnitTest` | PASS (`BUILD SUCCESSFUL`, 48 s) |
| `:app:lint :app:assembleCivDebug` | PASS (`BUILD SUCCESSFUL`, 15 s) |
| `python scripts/check-doc-images.py` | PASS (31 images; names, links, Git LFS, and sensitive metadata) |
| `git diff --check` | PASS |
| Reviewed staged/unstaged sensitive-path scan | PASS; no workstation path or username match |

No Activity, permission, dependency, network, telemetry, private ATAK API, or
compatibility-axis change was introduced.

### T079 current-device install and startup smoke

On 2026-07-30, the verified Civ Debug APK installed successfully on the
reference SM-X826B running Android 16 and ATAK-CIV 5.7.0.9
(`5.7.0.9 (7a0f6f29)`). The installed plugin reported
`1.4.4 (17d5e6d7) - [5.5.0]`. A full ATAK restart left the process running and
produced one plugin initialization signal with zero Auto Fill/native-entry
failure, fatal exception, version-skew, or plugin-load-failure matches in the
bounded startup log window.

This establishes install/startup readiness only. T080 remains open for the
interactive all-page Auto Fill and active-page Clear matrix, and exact ATAK
5.5.x evidence remains open under T064.
