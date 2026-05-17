# Implementation Plan: Custom Marker Icon on the GoTo Page

**Branch**: `003-custom-marker-icon` | **Date**: 2026-05-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-custom-marker-icon/spec.md`

## Summary

Add a 9th marker-mode option — **Custom Icon** — to the existing GoTo page (delivered in feature 002), backed by a two-step picker dialog (iconset list → icon grid) that reads exclusively from ATAK's existing `UserIconDatabase`. The picked icon is applied at marker placement via the SDK's `PlacePointTool.MarkerCreator.setIconPath(...)` builder method; the resulting marker is indistinguishable from one placed via ATAK's own marker tools. The marker-mode + picked icon persist across plugin restarts; if the persisted iconset is later removed, the page silently falls back to **Move only** and surfaces a one-shot empty-state hint on the next picker open. The plugin ships zero image assets — every icon offered to the operator comes from the iconsets ATAK already has installed (5 bundled + any operator-loaded).

Technical approach: introduce a single SDK seam class (`IconResolver`) that wraps `UserIconDatabase` so the rest of the plugin never imports `com.atakmap.android.icons.*`. The picker dialog is a plain `android.app.AlertDialog` (no AppCompat — same posture as feature 002 per ADR-0009 D6) hosted by `TwCoordGotoView`. Bitmap loading is lazy per-cell off the main thread (an `Executors.newFixedThreadPool(2)` shared with the iconset enumeration) with an LRU cache. Two new `SharedPreferences` keys (`pref_goto_marker_mode`, `pref_goto_last_iconset_path`) extend the existing `tw_coord_settings` file. Constitution VI is enforced uniformly: every new host-callable entry point (~9 new callbacks per [R12](./research.md#r12--constitution-vi-compliance-audit)) gets the outer `try/catch (Throwable)` wrap.

All SDK claims in this plan and its companion docs (research.md, data-model.md, contracts/*.md) are anchored to **both** `javap -public` against `ATAK-CIV-5.7.0.3-SDK/main.jar` and the upstream Java source at `github.com/TAK-Product-Center/atak-civ` (default branch `main`). See [research.md § Anchoring discipline](./research.md#anchoring-discipline) and feedback memory `feedback-plan-phase-code-anchoring`.

## Technical Context

**Language/Version**: Java 17 (host plugin module; unchanged from feature 002).

**Primary Dependencies**:

- ATAK-CIV 5.7.0.3 SDK (`atak-gradle-takdev` 3.+; runtime-compat declared at `5.4.0.CIV` per ADR-0007).
- AndroidX `core 1.17.0`, `fragment 1.8.9`, `lifecycle 2.9.4` (existing resolutionStrategy; no new pins).
- `androidx.collection` for `LruCache` (transitive of `core`; no new direct dependency).
- Spotless 6.25 + google-java-format 1.22 (formatter is a build dependency per Constitution Principle I).
- **No new dependencies introduced by this feature.** The picker reuses `org.simpleframework.xml` (transitive of ATAK SDK via `UserIcon`'s `@Attribute` annotations) only if needed — which it is not, because we consume `UserIcon` instances from `UserIconDatabase` rather than parse iconset XML ourselves.

**Storage**: Android `SharedPreferences` (file `tw_coord_settings`, already in use). Two new keys:

- `pref_goto_marker_mode` (enum name; default `"MOVE_ONLY"`).
- `pref_goto_last_iconset_path` (canonical `<iconsetUid>/<group>/<filename>`; default null).

No new tables, no new databases. The plugin remains a strict reader of ATAK's `iconsets.sqlite`.

**Testing**:

- JVM unit tests: JUnit 4.13.2 + AssertJ 3.27.3 + Mockito 5.16.1 (existing inner loop). The `IconDatabaseFacade` interface seam (introduced in `IconResolver`'s constructor) is the single mock point; tests do not require Android or ATAK.
- Instrumented tests: AndroidX Test + Espresso 3.5.1 (existing). 2–3 new end-to-end tests cover Acceptance Flows A and D from [quickstart.md](./quickstart.md).
- TDD discipline per Constitution Principle II: `IconResolverTest`, `MarkerModeV2Test`, `CustomIconPickerDialogTest`, `TwCoordGotoViewCustomIconTest` authored before the production code that satisfies them.

**Target Platform**: ATAK-CIV 5.7.0.3 (compatibility declared at `com.atakmap.app@5.4.0.CIV`); Android `minSdk 26`, `target 34`, `compileSdk 36`; ABI `arm64-v8a` for device, `armeabi-v7a, arm64-v8a, x86` for non-bundle builds. Unchanged from feature 002.

**Project Type**: Android plugin module (single `app/` Gradle module). This feature extends the existing module — no new Gradle subproject.

**Performance Goals** (from spec SC-001 through SC-007):

- Picker open → step-1 list rendered: ≤ **300 ms** median on Galaxy Tab S10+ (SC-002).
- Step-1 iconset pick → step-2 icon list rendered (up to 500 icons): ≤ **500 ms** median (SC-003).
- Icon pick → Submit enabled: ≤ **one UI frame** / **16 ms** (SC-004).
- Restart → restored picker state visible: **0 additional taps** (SC-005).
- All UI MUST hold ≥ 60 fps under typical operator scroll/tap load (Constitution IV).

**Constraints**:

- **Offline-capable**: zero outbound network (inherits feature 002's zero-telemetry posture; FR-015 inherited from feature 001).
- **Main-thread discipline**: every SQLite query (`UserIconDatabase.getIconSets`, `getIconBitmap`) MUST run on a worker thread; UI bind dispatches back via `View.post` / main-thread `Handler` per [research R10](./research.md#r10--off-main-thread-discipline).
- **No new image assets**: the plugin contributes zero icons. Every icon offered comes from `UserIconDatabase` (FR-004).
- **Constitution VI**: every new host-callable callback (9 new entry points per [R12](./research.md#r12--constitution-vi-compliance-audit)) MUST be wrapped in `try/catch (Throwable)` at its outer scope.
- **Backwards-compat**: persistence key `pref_goto_marker_mode` is new — absent on upgrade from v1.0.0; default `MOVE_ONLY` matches feature 002's in-session-only behaviour exactly until the operator first changes it.

**Scale/Scope**:

- One new `MarkerMode` enum value (`CUSTOM_ICON`).
- One new SDK seam class (`IconResolver` + `IconDatabaseFacade` interface).
- One new value class (`IconSelection`) + one new sealed-ish hierarchy (`PickerPreviewState`).
- One new dialog controller (`CustomIconPickerDialog`) + 2 adapters (iconset list, icon grid).
- ~80 LoC of new `TwCoordGotoView` (bind path + radio handling + preview rendering + dialog wiring + ICONSET_* broadcast subscription).
- ~25 new JVM unit tests across 4 test classes + 2–3 new Espresso tests.
- 4 new dialog layout XMLs + 1 modified (`tw_coord_goto.xml` gets the 9th radio and the preview row).
- ~10 new string resources × 3 locales (en / zh-rTW / ja).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|---|---|---|
| I | **Code Quality & Formatting** (NON-NEGOTIABLE) | **PASS** | Spotless + google-java-format already enforced in `app/build.gradle`; the build fails on unformatted code. No new format/lint surface introduced. `dart format` is N/A — this is a Java module; google-java-format is the language-equivalent. |
| II | **TDD** (NON-NEGOTIABLE) | **PASS** | `IconResolver` is fully testable on the JVM via the injected `IconDatabaseFacade` mock seam. `MarkerMode` is pure enum logic. `CustomIconPickerDialog` is mostly testable via Robolectric / Espresso depending on the assertion; the contract document explicitly enumerates the test list and which framework owns each. Tests authored before production code per the existing pattern. |
| III | **UX Consistency** | **PASS** | The 9th radio matches the visual weight of the existing 8 (same 24 dp icon convention, same selected-background tint). The dialog reuses plain-Android widgets per ADR-0009 D6 (no AppCompat / Material). New strings are externalised across `values/`, `values-zh-rTW/`, `values-ja/` via the same `LocaleOverride.contextFor(...)` pathway feature 002 already uses. A new entry will be authored under `docs/ui/input-page.md` alongside the implementation. |
| IV | **Performance** | **PASS with measurement obligation** | Spec SC-002 (300 ms picker open), SC-003 (500 ms 500-icon list), SC-004 (16 ms post-pick enable), SC-005 (0 taps on restart) are explicit. [Research R2](./research.md#r2--bitmap-fetch-strategy-at-picker-step-2) defends the budgets via lazy per-cell bitmap fetch; [R10](./research.md#r10--off-main-thread-discipline) defends them via a dedicated worker pool. [Quickstart §6](./quickstart.md#6-performance-smoke-tests) lays out the measurement procedure on the reference device. |
| V | **Documentation & Knowledge Preservation** | **PASS** | English-only artefacts (spec / plan / research / data-model / contracts / quickstart / future ADRs). Per Constitution V's post-implement ADR cadence, `docs/adr/0011-custom-marker-icon-implementation.md` will be authored after `/speckit-implement` completes. The pre-implementation reconnaissance ADR (`docs/adr/0010-custom-marker-icon-picker.md`) is already in. `docs/ui/input-page.md` will gain the picker entry. |
| VI | **Host-Process Isolation** (NON-NEGOTIABLE) | **PASS with mandatory audit** | [Research R12](./research.md#r12--constitution-vi-compliance-audit) enumerates the 9 new entry points; tasks.md will include an explicit "Constitution VI guard pass" step in the Polish phase. The submit-path placement call is already wrapped (feature 002); the new `setIconPath` chain change requires no extra guard. `/speckit-analyze` is configured to flag any unguarded entry point as CRITICAL. |

**Workflow gates** (Development Workflow & Quality Gates section):

- Subagent delegation: SDK reconnaissance was done directly (small enough surface; ~7 classes) rather than via a subagent. This is a permitted exception under the guideline that subagents are for "long-running searches, multi-file audits, cross-cutting consistency checks". The reconnaissance result is captured in ADR-0010 and research.md, so the artefacts persist outside the conversation.
- Formatter: `./gradlew :app:spotlessApply` will run after every code modification (Constitution Principle I requirement).
- Crash isolation (Principle VI): see the audit obligation in the Constitution Check row above.
- ADR cadence: ADR-0010 is in (reconnaissance ADR); ADR-0011 will be authored after `/speckit-implement`.
- UI docs cadence: `docs/ui/input-page.md` to be updated alongside layout XMLs.
- Definition of Done: tasks.md will include format / lint / test / docs / Constitution VI as completion criteria.

**Result**: No violations. No entries needed in **Complexity Tracking**.

## Project Structure

### Documentation (this feature)

```text
specs/003-custom-marker-icon/
├── plan.md              # This file
├── research.md          # Phase 0 — SDK reconnaissance + decision log
├── data-model.md        # Phase 1 — entities + persistence + state machines
├── quickstart.md        # Phase 1 — local validation steps
├── contracts/           # Phase 1 — typed contracts
│   ├── icon-resolver.md           # SDK seam wrapping UserIconDatabase
│   ├── custom-icon-picker.md      # Two-step dialog UI contract
│   └── marker-mode-v2.md          # Enum extension contract
├── checklists/
│   └── requirements.md  # produced by /speckit-specify
└── tasks.md             # Phase 2 output (NOT created by /speckit-plan)
```

### Source Code (repository root)

The plugin is a single Android module `app/`. This feature extends the existing
`com.atakmap.android.twcoord.gotopage` package introduced by feature 002. No new
sub-package is created; the 5 new classes live alongside the feature-002 ones.

```text
app/src/main/java/com/atakmap/android/twcoord/
├── coord/                              # existing — forward converters (untouched)
├── gotopage/                           # existing — extended by this feature
│   ├── (feature 002 classes — untouched)
│   ├── CoordinateParser.java
│   ├── TaipowerParser.java
│   ├── TwdTm2Parser.java
│   ├── ParseResult.java
│   ├── TwCoordGotoReceiver.java        # MODIFIED — registers ICONSET_ADDED/REMOVED broadcast
│   │                                   #   receiver on onDropDownVisible, unregisters on close
│   ├── TwCoordGotoView.java            # MODIFIED — bind/restore/submit gain Custom Icon paths
│   ├── MapCenterAutoFillStream.java
│   ├── RecentEntryStore.java
│   ├── RecentEntry.java
│   ├── DestinationMarkerStore.java
│   ├── MarkerMode.java                 # MODIFIED — +CUSTOM_ICON, +requiresIconPath, +isCustomIcon
│   ├── IconResolver.java               # NEW — SDK seam over UserIconDatabase
│   ├── IconDatabaseFacade.java         # NEW — JVM-mockable interface for IconResolver tests
│   ├── IconsetSummary.java             # NEW — value class
│   ├── IconRow.java                    # NEW — value class
│   ├── IconSelection.java              # NEW — value class
│   ├── PickerPreviewState.java         # NEW — sealed-ish hierarchy
│   └── CustomIconPickerDialog.java     # NEW — two-step AlertDialog controller
├── i18n/                               # existing — locale override (reused unchanged)
├── plugin/                             # existing — untouched
├── prefs/
│   ├── PreferenceStore.java            # MODIFIED — +getGotoMarkerMode/setGotoMarkerMode,
│   │                                   #              +getGotoLastIconsetPath/setGotoLastIconsetPath/clear
│   └── UserPreference.java             # existing
├── SelfMarkerSubscriber.java           # existing — untouched
├── TwCoordMapComponent.java            # existing — untouched (broadcast registration lives in the receiver)
├── TwCoordPreferenceFragment.java      # existing — untouched
└── TwCoordWidget.java                  # existing — untouched

app/src/main/res/
├── drawable/
│   ├── ic_marker_custom_icon.xml       # NEW — 24 dp vector for the 9th radio (a picker glyph)
│   └── (existing 8 ic_marker_*.xml)
├── layout/
│   ├── tw_coord_goto.xml               # MODIFIED — +9th radio row, +picker preview row
│   ├── custom_icon_picker_dialog.xml   # NEW — outer dialog frame (title + back btn + content swap)
│   ├── custom_icon_picker_iconset_row.xml  # NEW — iconset list row
│   ├── custom_icon_picker_icon_cell.xml    # NEW — icon grid cell
│   └── custom_icon_picker_empty_row.xml    # NEW — shared empty-state row
├── values/strings.xml                  # MODIFIED — +~10 new keys
├── values-zh-rTW/strings.xml           # MODIFIED — zh-TW translations
└── values-ja/strings.xml               # MODIFIED — ja translations

app/src/test/java/com/atakmap/android/twcoord/gotopage/
├── (feature 002 tests — untouched)
├── MarkerModeV2Test.java               # NEW — 6 enum tests per contracts/marker-mode-v2.md
├── IconResolverTest.java               # NEW — 6 tests per contracts/icon-resolver.md (mocks IconDatabaseFacade)
├── CustomIconPickerDialogTest.java     # NEW — Robolectric tests for state transitions
└── TwCoordGotoViewCustomIconTest.java  # NEW — bind/restore/fallback path tests

app/src/androidTest/java/com/atakmap/android/twcoord/gotopage/
├── (feature 002 tests — untouched)
├── CustomIconPickerEspressoTest.java   # NEW — end-to-end Flow A
└── CustomIconFallbackEspressoTest.java # NEW — end-to-end Flow D (FR-009 path)

docs/
├── adr/
│   ├── 0001 … 0009                     # existing
│   ├── 0010-custom-marker-icon-picker.md  # existing — pre-implementation reconnaissance
│   └── 0011-custom-marker-icon-implementation.md  # NEW — authored after /speckit-implement
└── ui/
    └── input-page.md                   # MODIFIED — +Custom Icon section + picker dialog screenshots
```

**Structure Decision**: extend the existing `gotopage` package rather than introduce a new one. Rationale: the new code (picker, resolver, value classes) is conceptually part of the GoTo input page's surface — splitting them into a sub-package would obscure the dependency relationship (everything in `gotopage` depends on `coord/`; nothing should depend on `gotopage`). The seam discipline that keeps the picker testable on the JVM lives in `IconResolver` + `IconDatabaseFacade`, not in package boundaries.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations. Table intentionally empty.
