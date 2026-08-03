# Quickstart: Compact Structured Address Layout

## 1. Baseline

Record before implementation:

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
Select-String -Path app/build.gradle -Pattern 'PLUGIN_VERSION|ATAK_VERSION'
java -version
.\gradlew.bat --version
```

Expected branch: `codex/015-compact-address-layout`.

Do not stage or commit `.codex-remote-attachments/`; it contains local user
inputs, not project source or release evidence.

### 2026-08-01 baseline record

| Evidence | Result |
|----------|--------|
| Branch | `codex/015-compact-address-layout` |
| Starting commit | `51dbfb34f920b996f2b6455f702858d47c6a776b` |
| Starting plugin version | `1.5.0` |
| Dirty scope before production work | `.gitignore`, `.specify/feature.json`, and new `specs/015-compact-address-layout/` planning artifacts; local attachments ignored and excluded |
| Shell Java | Eclipse Temurin 25.0.3 |
| Gradle launcher/daemon Java | Eclipse Temurin 17.0.17 |
| Gradle | 8.14.3 |
| ATAK compile SDK | ATAK-CIV 5.7.0.9 supplied by portable command-line overrides |

The pre-change focused baseline ran `:app:spotlessCheck` and
`TaiwanAddressLayoutTest` against the existing four-row XML and passed
(`BUILD SUCCESSFUL`, 55 s; 33 tasks, 32 executed and 1 up to date). The
pre-change full baseline then ran `:app:testCivDebugUnitTest :app:lint
:app:assembleCivDebug` and passed (`BUILD SUCCESSFUL`, 1 min 13 s; 58 tasks,
29 executed and 29 up to date). Gradle reported only the existing flatDir,
configuration-time resolution, deprecation, and missing connected-test-file
warnings; no baseline task failed.

### Compatibility inheritance

Feature 015 adds no ATAK class, method, registration, callback, or lifecycle
seam. It retains:

- Android compile/minimum SDK 36/26;
- ATAK compile/minimum runtime 5.7.0.9/5.5.0;
- the ATAK-CIV 5.7.0.9 `main.jar` SHA-256
  `8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`;
- the established public `CoordinateEntryPane` and
  `CoordinateEntryCapability` `javap -public` evidence from Feature 011;
- the ATAK 5.5.1.1 public source anchor and exact ATAK 5.5.x physical-device
  release gate from ADR-0022 through ADR-0024.

No new `javap` capture is required for an XML-only hierarchy change. Build
success remains distinct from minimum-runtime device evidence.

## 2. Red: compact Address contract

First update `TaiwanAddressLayoutTest` to require:

- exactly two structured row containers;
- county/city plus district/township in row 1;
- road/locality plus house-number/floor in row 2;
- 1:1 weights for both field groups in each row;
- retained label/input group geometry, Address 8:2 split, 48 dp action, and one
  outer scroll owner;
- compact measurement below the 216 dp cap at 900 dp width;
- unchanged selector enabled states and editable field actions.

Run only the focused test before changing the layout:

```powershell
.\gradlew.bat :app:testCivDebugUnitTest `
  --tests com.atakmap.android.twcoord.nativeentry.TaiwanAddressLayoutTest
```

Expected Red: the current four-row hierarchy does not contain the two required
row containers and still reaches the old 216 dp cap.

### 2026-08-01 Red record

The first attempted Red run failed during test compilation because the
repository's AssertJ version does not provide integer `isWithin`; this was a
test-authoring error and was not accepted as product evidence. The assertion
was corrected to compare the absolute integer width difference without
changing production resources.

The next focused run compiled and executed four tests, then failed exactly the
three intended Feature 015 expectations (`BUILD FAILED`, 10 s; 30 tasks, 3
executed and 27 up to date):

- both hierarchy checks could not find the not-yet-created locality/street row
  resource IDs;
- the structured pane still measured at the historical 216 dp cap rather than
  shrink-wrapping below it.

The unchanged text-color contract continued to pass. This is the accepted Red
boundary for T003-T004.

## 3. Green and refactor

Change only the structured Address hierarchy in
`app/src/main/res/layout/taiwan_coordinate_entry_pane.xml`:

1. Add the two horizontal row containers.
2. Place the existing county and district groups in the first row with equal
   width.
3. Place the existing road and tail groups in the second row with equal width.
4. Preserve all existing field IDs, labels, inputs, hints, content
   descriptions, editor actions, 3:7 internal proportions, the 8:2 outer split,
   and the right action column.

Rerun the focused test and then the adjacent native-entry regressions:

```powershell
.\gradlew.bat :app:testCivDebugUnitTest `
  --tests com.atakmap.android.twcoord.nativeentry.TaiwanAddressLayoutTest `
  --tests com.atakmap.android.twcoord.nativeentry.TaiwanCoordinateEntryPaneContractTest `
  --tests com.atakmap.android.twcoord.nativeentry.TaiwanInlineImeContractTest `
  --tests com.atakmap.android.twcoord.nativeentry.NativeEntryFeature014RegressionTest
```

Record the Red, Green, and refactor results here during implementation.

### 2026-08-01 Green and refactor record

Production XML now adds exactly two horizontal structured containers. The
first owns the unchanged county and district field groups; the second owns the
unchanged road and tail groups. Each group uses zero width plus weight 1 while
retaining its internal 3:7 label/input weights, existing IDs, content
descriptions, hints, enabled/focus state, and editor actions. The outer Address
8:2 content/action split and single scroll owner are unchanged.

The focused four-test contract passed after the XML change (`BUILD SUCCESSFUL`,
13 s; 30 tasks, 15 executed and 15 up to date). The adjacent
`TaiwanAddressLayoutTest`, `TaiwanCoordinateEntryPaneContractTest`,
`TaiwanInlineImeContractTest`, and `NativeEntryFeature014RegressionTest` group
then passed together (`BUILD SUCCESSFUL`, 14 s; 30 tasks, 2 executed and 28 up
to date). XML parsing also passed. No Java production file changed.

### 2026-08-01 expanded state and regression record

`TaiwanAddressLayoutTest`, `TaiwanAddressResourcesTest`,
`AddressEntryControllerTest`, `TaiwanCoordinateEntryPaneSafetyTest`, and
`TaiwanCoordinateEntryPaneContractTest` passed together (`BUILD SUCCESSFUL`,
18 s; 30 tasks, 2 executed and 28 up to date). This run covers normal and 2.0
font-scale layout assertions plus Address resource parity, editable/read-only
state, mode switching, lookup, Auto Fill/Clear, and lifecycle fixtures.

## 4. Version and documentation

Synchronize `1.5.1` in:

- `app/build.gradle`;
- `CHANGELOG.md`;
- `docs/user-guide.md`;
- `docs/user-guide_zh.md`.

Update `docs/ui/native-taiwan-coordinate-entry.md` and the two user guides to
describe the two-row 1:1 structured layout. Do not treat the generated preview
as device evidence. Replacing the structured Address screenshot remains a
physical-device `[RELEASE-GATE]` after the UI is frozen.

## 5. Full automated quality gate

```powershell
.\gradlew.bat :app:spotlessApply
.\gradlew.bat :app:spotlessCheck
.\gradlew.bat :app:testCivDebugUnitTest
.\gradlew.bat :app:lint
.\gradlew.bat :app:assembleCivDebug
python scripts/check-doc-images.py
git diff --check
```

### 2026-08-01 automated quality-gate record

- `:app:spotlessApply` passed (`BUILD SUCCESSFUL`, 5 s; 3 tasks, 2 executed and
  1 up to date).
- `:app:spotlessCheck :app:testCivDebugUnitTest` passed (`BUILD SUCCESSFUL`,
  37 s; 33 tasks, 21 executed and 12 up to date).
- `:app:lint :app:assembleCivDebug` passed (`BUILD SUCCESSFUL`, 14 s; 50 tasks,
  14 executed and 36 up to date).
- `python scripts/check-doc-images.py` passed all 32 documentation images for
  names, local links, Git LFS state, and sensitive metadata.
- Version synchronization passed for `app/build.gradle`, `CHANGELOG.md`, and
  both user guides at `1.5.1`.
- `git diff --check` passed.

### 2026-08-01 reviewed-diff audit

The Feature 015 18-file layout scope contains no production Java, manifest,
image/binary, permission, dependency, network, telemetry, storage, ATAK SDK
seam, parser,
conversion, lookup, ranking, dataset, WGS84, or host-confirmation change. The
only `app/build.gradle` edit is `PLUGIN_VERSION`. Automated layout contracts
confirm one outer scroll owner and the unchanged Address action geometry. The
local attachment directory is ignored, no file is staged, and the reviewed text
contains no workstation path, username, email-derived name, or file URI. The
generated preview is not part of the repository or release evidence. A
sanitized physical-device replacement is now recorded as partial T009 evidence
in `docs/images/27-native-address-structured.png`.

### 2026-08-01 convergence record

`speckit-converge` found no unfinished buildable gap. All 12 functional
requirements and 8 success criteria have implementation, automated test,
documentation, or explicit release-gate coverage. No task was appended. T009
and T015 remain open because they require the remaining physical-device,
accessibility, performance, signer, and provenance evidence.

Review the diff for:

- no Java production, manifest, permission, dependency, network, telemetry,
  storage, parser, conversion, lookup, ranking, dataset, or WGS84 change;
- no new scroll owner or fixed-height content row;
- no missing locale key;
- no real workstation path, username, device identifier, raw attachment, or
  unrelated binary metadata.

## 6. Current-device acceptance `[RELEASE-GATE]`

On ATAK-CIV 5.7.0.9, test Address single and structured modes in portrait and
landscape, EN/zh-TW/JA, font scales 1.0 and 2.0, editable and read-only
contexts, TalkBack and Switch Access:

1. Confirm two rows and 1:1 groups in structured mode.
2. Confirm no label/value/column overlap and no horizontal scrolling.
3. Confirm county/district selector order and road-to-tail editor order.
4. Confirm mode/candidate actions and ATAK elevation, marker, Auto Fill, Clear,
   Copy, Cancel, and OK remain reachable.
5. Repeat 20 mode changes and record nearest-rank p95 from click to visible
   layout; pass at no more than 100 ms.
6. Repeat missing-data, failed lookup, locale replacement, and 20
   open/dispose/reload cycles with zero crash, stale result, or draft loss.
7. Capture a sanitized replacement structured Address screenshot only after
   the implementation is frozen.

### 2026-08-03 partial current-device evidence

- ATAK-CIV 5.7.0.9 loaded plugin `1.5.1` on the current Android 16 reference
  device and rendered the landscape, English, editable structured Address
  layout with two equal-width rows and reachable ATAK-owned controls.
- `docs/images/27-native-address-structured.png` is the reviewed device
  capture. It is cropped to the Go To dialog, has all four address values
  redacted, and contains no retained EXIF/XMP/PNG text metadata.
- T009 remains open for portrait, zh-TW/JA, font scale 2.0, read-only,
  missing-data, TalkBack/Switch Access, 20-switch p95, and lifecycle evidence.

## 7. Minimum-runtime acceptance `[RELEASE-GATE]`

Repeat the same matrix on an exact ATAK-CIV 5.5.x runtime. Until completed,
retain this gate as pending and do not infer minimum-runtime device behavior
from build or ATAK 5.7.0.9 evidence.

## 8. Release boundary

Implementation completion does not authorize a tag, TPP upload, or GitHub
Release. Before publication, run `release-readiness`, verify the exact frozen
candidate and TPP signer/provenance, and complete or explicitly disposition all
release gates without reporting unexecuted evidence as passed.
