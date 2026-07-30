# Implementation Plan: Native Taiwan Input UX

**Branch**: `codex/014-native-entry-input-ux` | **Date**: 2026-07-30 |
**Spec**: [spec.md](./spec.md)

**Input**: Feature specification from
`/specs/014-native-entry-input-ux/spec.md`

## Summary

Keep every editable field in ATAK's native Go To Taiwan pane in the host
dialog while the software keyboard is visible, add a persisted two-mode
Taipower editor (one lossless raw field or four guided fields), correct the
authoritative Taipower 100 m letter ranges to A-H east/west and A-E
north/south, reduce the selectors' visible height without reducing their
48 dp touch targets, and make one host Auto Fill refresh all four Taiwan
pages from the same WGS84 point.

The implementation retains the existing public `CoordinateEntryPane` and
`CoordinateEntryCapability` integration. It adds plugin-owned draft and input
mode types below `nativeentry`, applies explicit inline IME options to the
existing XML fields, and represents the 36 dp selector track with 6 dp vertical
drawable insets inside the existing 48 dp controls. No new ATAK seam, screen,
permission, dependency, database, or network path is introduced.

## Technical Context

**Language/Version**: Java 17-compatible Android sources and Android resource
XML.

**Primary Dependencies**: Existing ATAK-CIV SDK and Android framework APIs,
including `CoordinateEntryPane`, `CoordinateEntryCapability`, `EditText`,
`EditorInfo`, `InputFilter`, `RadioGroup`, `SharedPreferences`, and
plugin-owned resources. No runtime dependency is added.

**Storage**: One plugin-owned `SharedPreferences` string value for
`TaipowerInputMode`. It is independent of ATAK's MGRS preference and the
existing last-native-tab preference. Coordinate drafts remain in memory and
expire with the pane.

**Testing**: JUnit 4, AssertJ, Mockito, Robolectric resource/view contract
tests, Android lint, Spotless, debug package assembly, and on-device ATAK
acceptance for host, keyboard, layout, accessibility, and lifecycle behavior.

**Target Platform**: Single-module ATAK-CIV Android plugin. The current
reference device is a Galaxy Tab S10+ (`SM-X826B`) running ATAK-CIV 5.7.0.9
with its default keyboard. An exact ATAK-CIV 5.5.x physical-device or
representative emulator run remains a release gate. Portrait, landscape,
normal font scale, and the largest field-usable supported font scale are in
scope.

**Android Compile SDK**: API 36.

**Android Minimum SDK**: API 26.

**ATAK Compile SDK**: ATAK-CIV 5.7.0.9. The pinned `main.jar` SHA-256 is
`8AE6CA6028F72A99537FC2CE9436A4E4964356CB90C7934C35ABE7A7CB065B70`.

**Minimum ATAK Runtime**: ATAK-CIV 5.5.0, unchanged.

**ATAK API Evidence**: Existing ADR-0023 and ADR-0024 evidence covers the
public `CoordinateEntryPane` callbacks and
`CoordinateEntryCapability.registerPane`/`unregisterPane` lifecycle. A
reproduced `javap -public` check against the pinned 5.7.0.9 `main.jar` matches
those signatures. ATAK-CIV 5.5.1.1 public source remains the stable
minimum-runtime anchor. This feature adds no ATAK API seam; device validation
is still required because keyboard and host layout behavior cannot be proven
by compilation.

**Project Type**: Existing single-module Android ATAK plugin.

**Performance Goals**:

- At least 95% of field-focus attempts show focus feedback or the visible
  keyboard within 500 ms on the reference current device.
- At least 95% of mode switches and local Taipower validation updates render
  within 100 ms.
- Mode switching, parsing, filters, and preference reads/writes perform no
  network or database I/O and no synchronous file I/O on the UI thread.
- Added views and draft objects retain no more than 256 KiB per live pane, and
  20 reopen/reload cycles leave no retained disposed pane.

**Measurement Method**:

- Capture Android Perfetto/System Trace with input, view, window-manager, IME,
  frame, scheduler, disk, and network activity while a synchronized screen
  recording identifies the first visible focus/IME or rendered
  mode/validation frame.
- Measure field tap/input dispatch to the first focus or visible-IME frame and
  mode/edit dispatch to the first frame containing the requested projection or
  validation state. Collect 20 samples for each required field/mode without
  discarding failures, sort the samples, and report nearest-rank p95 at
  `ceil(0.95 * N)`.
- Record `adb shell dumpsys meminfo <ATAK_PACKAGE>` before pane creation, after
  steady state, and after 20 open/dispose/reload cycles. Pair it with a heap
  dump to count retained Feature 014 objects and confirm no disposed pane.
  Compare the same scenario against the pre-feature baseline; explain any
  heap/PSS increase above 10% and record an ADR when it reflects an
  architectural trade-off.
- Use Perfetto thread, disk, and network slices plus a manifest diff to prove
  that edit, switch, filter, parser, and preference paths add no network I/O,
  synchronous UI-thread file I/O, telemetry, or permission.

**Constraints**:

- Retain one plugin-owned vertical scroll owner and ATAK ownership of
  confirmation, Auto Fill, Clear, Copy, marker, and elevation. The plugin pane
  adapts non-null Auto Fill to all four Taiwan pages while Clear remains
  active-page-only.
- All editable fields request inline IME presentation. A third-party IME may
  ignore the request; acceptance uses the named default keyboards and data
  safety must remain intact for every IME.
- Split Taipower input is exactly `[region] [four digits] [two letters]
  [two or four digits]`.
- Taipower remains TWD67 TM2 zone 121 and main-island only.
- English, Traditional Chinese (Taiwan), and Japanese resources remain
  aligned.
- The feature is fully offline and adds no permission or telemetry.
- Late callbacks after dispose or locale-driven replacement are inert.

**Scale/Scope**:

- One registered Taiwan `CoordinateEntryPane`; no additional page.
- Eight existing editable fields receive explicit inline IME contracts and
  four mutually exclusive split Taipower fields are added.
- Three selector rows retain 48 dp controls with 36 dp visible tracks.
- One new preference value, two Taipower entry modes, three locale resource
  sets, and one corrected coordinate-validation boundary.
- Existing Taipower 9-character (10 m) and 11-character (1 m) formats,
  TWD97/TWD67 entry, and offline Address entry remain supported.

### Compatibility Matrix

| ATAK Line | Evidence Required | Planned Validation | Status |
|-----------|-------------------|--------------------|--------|
| 5.5 minimum runtime | ATAK-CIV 5.5.1.1 public source for `CoordinateEntryPane`, `CoordinateEntryCapability`, and native MGRS pane; manifest minimum; no post-5.5 API | `[RELEASE-GATE]` install, register/unregister, inline keyboard, both Taipower modes, selectors, read-only, and 20 reload cycles on an exact 5.5.x runtime | SOURCE/API PASS; DEVICE PENDING |
| 5.7.0.9 current runtime | Pinned `main.jar` hash and `javap -public` signatures from ADR-0024 | Build and run the full feature matrix on `SM-X826B`, ATAK-CIV 5.7.0.9, in portrait and landscape | SDK/API PASS; FEATURE DEVICE/PERF PENDING |

## Constitution Check

*GATE: Passed before Phase 0 research. Re-checked after Phase 1 design below.*

| Principle | Required Plan Evidence | Status |
|-----------|------------------------|--------|
| I. Code Quality & Build Discipline | Test-first changes are confined to existing parser, grid, `nativeentry`, preferences, layout, drawable, dimension, string, and focused test paths. Spotless, lint, unit-test, assemble, and diff checks are explicit quickstart gates. | PASS |
| II. Test-First Development & Verification | Boundary, draft projection, persistence, IME, selector, lifecycle, and regression tests precede implementation. JVM/resource checks are separated from current/minimum ATAK device gates. | PASS |
| III. UX, Accessibility & Localisation | One scroll owner remains. Visible selector tracks become 36 dp while controls remain at least 48 dp. Focus order, read-only states, screen-reader labels, large fonts, and `values`/`values-zh-rTW`/`values-ja` parity are contractual. | PASS |
| IV. Performance & Offline Operation | The 500 ms focus, 100 ms local-update, 256 KiB retained-pane budgets are measurable. No network, database, new permission, or synchronous file I/O is introduced. | PASS |
| V. Documentation & Decision Traceability | `research.md`, `data-model.md`, contracts, quickstart, UI/reference/user docs, changelog, and ADR index updates are planned. ADR-0028 supersedes only ADR-0001's incorrect subgrid-letter range; ADR-0029 partially supersedes ADR-0023 for all-page Auto Fill. | PASS |
| VI. Host-Process Isolation | The existing public pane remains the only host boundary. UI callbacks use render/dispose guards, validation fails closed, mode switches do not emit host changes, and plugin resources remain plugin-context owned. | PASS |
| VII. ATAK SDK Compatibility | Android 36/26 and ATAK 5.7.0.9/5.5.0 axes are fixed. Existing public signatures have current-SDK `javap` and stable minimum-runtime source anchors. Device-only claims remain release gates. | PASS |
| VIII. Geospatial Correctness & Provenance | Taipower remains TWD67 zone 121. A-H/A-E follows the 800 m by 500 m sheet divided into forty 100 m cells, will be recorded in a superseding ADR, and receives exhaustive boundary and golden-vector tests. | PASS |
| IX. Release Integrity & Provenance | Build success is not release evidence. Current/minimum runtime, documentation, screenshots, signer, source ref, and artifact provenance remain explicit `[RELEASE-GATE]` items tied to the final release candidate. | PASS |

## Design and Delivery Strategy

### Phase A - Lock correctness and draft semantics

1. Add failing parser, `TaipowerCode`, and `TaipowerGrid` tests for all A-H and
   A-E boundaries, I/J east-west rejection, F-J north-south rejection, 9/11
   precision, and unchanged golden vectors.
2. Add `TaipowerInputMode`, `TaipowerEntryDraft`, and split-part tests before
   replacing the controller's single Taipower string. Keep the exact raw value
   when it cannot be safely projected.
3. Add preference tests for default, round trip, corrupt-value fallback,
   reload, and independence from ATAK MGRS and the native-tab preference.
4. Correct the parser/model/grid validation once at the authoritative domain
   boundary. Do not rely on UI filters as correctness enforcement.

### Phase B - Add the two-mode inline editor

1. Add one right-aligned mode action using the Address pane's 8:2
   content/action pattern, plus mutually exclusive raw/split containers. The
   action names the alternate layout. The split order is region, subregion
   digits, 100 m letters, and precision digits.
2. Project both layouts from one draft. A projection-only mode switch updates
   the view and preference but never invokes the human-change listener.
3. Apply uppercase ASCII character-class and exact maximum-length filters to
   Taipower fields. Preserve A-Z letter attempts, including range-invalid I/J
   or F-J, so draft validation can render the position-specific A-H/A-E
   message instead of silently deleting input. Treat final lengths 0/1/3 as
   incomplete, 2 as valid 10 m, 4 as valid 1 m, and reject input beyond four
   characters without replacing accepted text.
4. Request inline editing with `IME_FLAG_NO_FULLSCREEN` and the ATAK 5.5 MGRS
   compatibility flag `IME_FLAG_NO_EXTRACT_UI`. Use `IME_ACTION_NEXT` for
   non-final fields and the existing Done/Search semantics for final fields.
   Apply force-ASCII only to coordinate fields, never to Chinese-capable
   Address input.
5. Move focus automatically only after fixed-length split groups 1-3. Do not
   auto-complete the final group at two digits because it may extend to four.
6. Preserve read-only and dispose behavior by disabling editors, removing
   watchers/listeners, dismissing keyboard ownership safely, and ignoring late
   callbacks.

### Phase C - Compact selectors and align resources

1. Retain each system/zone `RadioGroup` and `RadioButton` at 48 dp.
2. Change the track and selected-option drawables to use 6 dp top/bottom
   insets, producing a 36 dp visible track without delegated or overlapping
   touch regions.
3. Add named dimensions for touch height and visual inset, then test normal,
   large, disabled, selected, and read-only states.
4. Add aligned English, Traditional Chinese (Taiwan), and Japanese strings for
   Taipower modes, field hints, projection errors, and accessibility labels.

### Phase D - Integrate, document, and collect evidence

1. Run focused tests, the full JVM suite, Spotless, lint, package assembly,
   locale/static audits, and `git diff --check`.
2. Update the native pane UI reference, coordinate-system reference, English
   and Traditional Chinese user guides, docs indexes, ADR index, and
   `CHANGELOG.md`. Add ADR-0028 to supersede ADR-0001 only for the Taipower
   100 m subgrid letter ranges.
3. Refresh user-guide screenshots only after the UI and strings are frozen,
   using the repository screenshot workflow and metadata/LFS checks.
4. Collect current-device keyboard, focus, layout, performance, accessibility,
   and lifecycle evidence using the documented Perfetto, nearest-rank p95, and
   memory protocol.
5. Freeze and commit `PLUGIN_VERSION`, then build the exact candidate with
   `.\gradlew.bat :app:clean :app:assembleCivRelease`. Stage durable release
   inputs with `scripts/build-tpp-source-zip.py --verify-build`, record the
   source archive and local APK SHA-256 hashes, and never use the root `clean`
   task to manage durable outputs.
6. Keep exact ATAK 5.5.x device evidence, signer/provenance checks, signed-tag
   authorization, and final release-candidate documentation evidence open as
   `[RELEASE-GATE]` until executed against the frozen version and source ref.
   A narrowed minimum-runtime claim requires explicit user acceptance and a
   release-note disposition; it cannot be inferred from missing evidence.
7. After a separately user-authorized TPP submission returns, use
   `scripts/stage-tpp-release.py` to curate the returned APK, source archive,
   reports, hashes, and signer fingerprint under `dist/release-v<VERSION>/`,
   outside Gradle-owned `build/`.
8. Run the public-readiness gate against the staged candidate and all device,
   documentation, and release-note dispositions. Only after it passes and the
   user explicitly authorizes the action, create a signed annotated immutable
   release tag for the exact verified candidate. Tag push and publication
   remain separately authorized actions.

### Phase E - Refresh every Taiwan page from one Auto Fill

1. Add controller and pane regression tests before production edits. The tests
   must prove that non-null Auto Fill refreshes Taipower, TWD97, TWD67, and
   Address from one point, retains the selected page, and emits no
   human-change callback.
2. Reuse the existing atomic coordinate staging path for Auto Fill, preserving
   prior TWD zone choice only when a supplied point is unrepresentable.
3. Start Address reverse lookup from the same exact WGS84 after coordinate
   staging. Retain the asynchronous no-snap rule.
4. Keep null Clear active-page-only and preserve Address cancellation behavior.
5. Record the contract change in ADR-0029 and synchronize the feature
   artifacts, UI/operator documentation, changelog, and device journey.

### Requirement Traceability

| Requirements | Requirement Area | Design/Contract | Primary Verification |
|--------------|------------------|-----------------|----------------------|
| FR-001-FR-003 | Inline keyboard and focus | `contracts/native-inline-input-contract.md` | Robolectric editor-option tests plus current/5.5 device focus matrix |
| FR-004-FR-016 | Taipower single/split modes and persistence | `data-model.md`, `contracts/taipower-entry-contract.md` | Draft/controller/preference tests and 100 projection round trips |
| FR-006-FR-010, QR-005-QR-006, SC-002-SC-004, SC-007 | A-H/A-E correction and precision | `research.md`, ADR-0028, Taipower contract | Exhaustive letter-boundary, golden-vector, and round-trip tests |
| FR-017-FR-019, SC-006 | 36 dp visible / 48 dp reachable selectors | `contracts/selector-presentation-contract.md` | Resource/view geometry tests and device tap/font-scale checks |
| FR-020-FR-024, QR-002-QR-004, SC-001, SC-005, SC-008-SC-009 | Host, lifecycle, offline, performance, and locale safety | Inline and Taipower contracts | Pane/controller/registrar tests, timing matrix, and 20 reload cycles |
| FR-025, SC-011 | All-page Auto Fill and active-page Clear | ADR-0029, Taipower contract, Plan Phase E | Controller/pane tests plus current/minimum-device all-tab journey |
| QR-001, QR-007, SC-010 | Compatibility, documentation, and release integrity | Plan Phase D and `quickstart.md` | Current/minimum device matrix, docs/LFS/metadata audit, and final release-gate record |

## Project Structure

### Documentation (this feature)

```text
specs/014-native-entry-input-ux/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── native-inline-input-contract.md
│   ├── selector-presentation-contract.md
│   └── taipower-entry-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
app/src/main/
├── java/com/atakmap/android/twcoord/
│   ├── coord/
│   │   ├── TaipowerCode.java
│   │   ├── TaipowerGrid.java
│   │   └── input/TaipowerParser.java
│   ├── nativeentry/
│   │   ├── TaipowerEntryDraft.java
│   │   ├── TaipowerInputMode.java
│   │   ├── TaiwanCoordinateEntryPane.java
│   │   └── TaiwanEntryController.java
│   └── prefs/PreferenceStore.java
└── res/
    ├── drawable/native_entry_segment_option.xml
    ├── drawable/native_entry_segment_track.xml
    ├── layout/taiwan_coordinate_entry_pane.xml
    ├── values/dimens.xml
    ├── values/strings.xml
    ├── values-ja/strings.xml
    └── values-zh-rTW/strings.xml

app/src/test/java/com/atakmap/android/twcoord/
├── coord/
│   ├── TaipowerGridTest.java
│   └── input/TaipowerParserTest.java
├── nativeentry/
│   ├── TaipowerEntryDraftTest.java
│   ├── TaiwanCoordinateEntryPaneContractTest.java
│   ├── TaiwanCoordinateEntryPaneSafetyTest.java
│   └── TaiwanEntryControllerTest.java
└── prefs/PreferenceStoreNativeEntryTest.java

docs/
├── adr/
│   ├── 0028-correct-taipower-subgrid-letter-ranges.md
│   └── README.md
├── reference/coordinate-systems.md
├── ui/native-taiwan-coordinate-entry.md
├── user-guide.md
├── user-guide_zh.md
└── README.md

CHANGELOG.md
README.md
```

**Structure Decision**: Reuse the existing `coord` package as the sole
Taipower validity/encoding authority, `nativeentry` as presentation and
in-memory draft ownership, and `PreferenceStore` as plugin preference
ownership. The existing public native pane is sufficient; introducing an
Activity, Fragment, custom keyboard, database, or additional ATAK integration
would add ownership and lifecycle seams without satisfying a requirement.

## Post-Design Constitution Check

Phase 1 design introduces only plugin-owned value types, XML resources, and one
preference. The contracts keep host ownership, one scroll owner, 48 dp
reachable targets, locale parity, lossless draft projection, fail-closed
geospatial validation, offline operation, and public ATAK APIs explicit.
ADR-0028, ADR-0029, and the current/minimum-runtime device matrix close the
decisions that cannot be established by implementation alone. All nine
principles remain PASS; no exception is required.

## Complexity Tracking

No constitution exception is required.
