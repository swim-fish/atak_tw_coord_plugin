# Implementation Plan: Settings Page & Search/Storage UX Tweaks

**Branch**: `007-settings-ux-tweaks` | **Date**: 2026-05-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-settings-ux-tweaks/spec.md`

## Summary

A minor-version (`1.1.0` → `1.2.0`) maintenance release bundling three small,
independent UX tweaks on top of the shipped plugin:

1. **Result ordering** — forward-address-search candidates can be ordered by
   **most similar** (textual match to the query) or **distance** (nearest the
   anchor). The choice is a persisted preference; toggling re-ranks the current
   list in place. Realised by adding a `ResultOrdering` enum, a similarity score
   computed from the existing `StreetTextNormaliser` fold, and an ordering
   parameter threaded through `StreetCandidateRanker`. No change to which
   candidates are returned, nor to tap-to-pan/GoTo.
2. **Settings page from the tool button** — the **TW Coordinates** tool button
   (`TwCoordTool` → `ACTION_SHOW_PLUGIN`) is currently handled by
   `TwCoordMapComponent.toggleReceiver`, which **cycles the on-map coordinate
   readout** `Off → Taipower → TWD97 → TWD67` (calling `setCoordinateUnit`). This
   feature **re-points that handler to open the existing
   `TwCoordPreferenceFragment`** and **removes the unit cycling** ("取消直接切換
   座標"); the format is chosen in settings instead. A new
   `pref_readout_visible` toggle in settings preserves the show/hide the cycle
   used to provide. The exact programmatic settings-launch API is an open SDK
   item (research R1) with an in-repo `DropDownReceiver` fallback.
3. **Storage sizes in TW Offline Addr** —
   `OfflineAddressReceiver.renderActiveCountyList(ViewGroup)` gains a
   human-readable on-disk size per county
   (`FileSystem.sizeOfDirectory(fs.activeCountyDir(county))`) and a distinct
   `_boundary` row summing the boundary folder
   (`fs.sizeOfDirectory(fs.boundaryDbFile().getParent())`). `FileSystem` already
   exposes `sizeOfDirectory`/`boundaryDbFile`; no new sizing primitive needed.

All three are additive, behind the existing seams, and ship together under one
version bump. No data-schema or generator change.

## Technical Context

**Language/Version**: Java 11 (Android, ATAK plugin)
**Primary Dependencies**: ATAK-CIV 5.7.0.3 SDK (`MapView`, `DropDownReceiver`,
`AbstractPlugin`, `PluginPreferenceFragment` / ATAK preferences API), Android
`SharedPreferences`; **no new third-party libraries**
**Storage**: existing `twcoord_prefs` SharedPreferences (new ordering + widget-
visibility keys); read-only `File.length()` over existing per-county
`places.sqlite` and the `_boundary/townships.sqlite` folder
**Testing**: JUnit4 + Robolectric (unit), Espresso (instrumented UI),
existing `OfflineAddress*EspressoTest` suite
**Target Platform**: Android (ATAK-CIV 5.7.0.3 host), API 21+
**Project Type**: single (Android plugin module under `app/`)
**Performance Goals**: re-rank of a bounded candidate list in **< 1 s** (≤ a
few hundred rows, already capped by `MAX_RESULTS`); storage-size enumeration
non-blocking (a handful of `File.length()` calls); opening settings ≤ 1 frame
**Constraints**: fully offline; glove-operable single-column UI; reuse existing
GoTo/confidence/preference conventions; zh-TW strings (Constitution V); no new
deps
**Scale/Scope**: ~1 enum + 1 formatter util + 1 storage-summary helper + ranker
ordering param + preference keys + 1 new `ListPreference`/`CheckBoxPreference` +
1 ordering toggle in the forward-search layout + tool-button re-point. No new
Tools page.

## Constitution Check

*GATE: must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Assessment | Status |
|---|---|---|
| **I. Code Quality & Readability** | New surfaces are small, single-responsibility units (`ResultOrdering`, `ByteCountFormatter`, `DatasetStorageSummary`); ranker gains one ordering param; no dead/commented code; reuses `StreetTextNormaliser`. | ✅ PASS |
| **II. Test-Driven Development** | Each new pure unit (formatter, similarity scorer, ordering comparator, storage summary, preference round-trip) is written test-first; UI wiring covered by Espresso (settings opens from button; sizes render; toggle re-ranks). ≥ 80 % on the new business logic. | ✅ PASS |
| **III. UX Consistency** | Settings reuses `PluginPreferenceFragment` native styling; ordering toggle follows the page's existing tap-target/single-column conventions; empty/missing-data states defined (no datasets, missing `_boundary`, no anchor). | ✅ PASS |
| **IV. Performance** | Re-rank is in-memory over a `MAX_RESULTS`-bounded list (< 1 s); size reads are O(counties) `File.length()` calls, run off the UI thread if needed; no new DB opens. | ✅ PASS |
| **V. Traditional Chinese Localization** | All new strings (ordering labels 最相似/距離, size labels, `_boundary` row, settings entries) added to `res/values/strings.xml` in zh-TW with Taiwan terms; committed code/comments stay English. | ✅ PASS |
| **VI. Host-Process Isolation (NON-NEGOTIABLE)** | Every changed/new host→plugin entry point is wrapped in `try/catch(Throwable) → Log.w`: `toggleReceiver.onReceive` (now opens settings), the ordering-toggle + re-rank `OnClickListener`s in `ForwardSearchReceiver`, the size-rendering path in `OfflineAddressReceiver.renderActiveCountyList`, and the `pref_readout_visible` application in `prefListener`. `DatasetStorageSummary` treats absent/partial files as 0 rather than throwing. Matches the existing per-listener wrap discipline already in `PreferenceStore.fireAll`, `ActiveDatasetRegistry.fireChange`, and `TwCoordWidget.renderAddresses`. A Polish-phase crash-isolation audit task confirms it. | ✅ PASS |

**Result**: PASS — no violations; Complexity Tracking not required.

## Project Structure

### Documentation (this feature)

```
specs/007-settings-ux-tweaks/
├── plan.md              # This file
├── research.md          # Phase 0 — decisions (settings-launch API, ordering model, storage sizing)
├── data-model.md        # Phase 1 — entities (ResultOrdering, settings keys, storage summaries)
├── quickstart.md        # Phase 1 — manual verification walkthrough
├── contracts/
│   ├── result-ordering.md     # ranker ordering + persistence contract
│   ├── settings-page.md       # tool-button → settings + cancel-cycling contract
│   └── storage-display.md     # per-county + _boundary size display contract
└── checklists/
    └── requirements.md  # spec quality checklist (from /speckit-specify)
```

### Source Code (repository root)

```
app/src/main/java/com/atakmap/android/twcoord/
├── prefs/
│   └── PreferenceStore.java            # + KEY_SEARCH_RESULT_ORDERING + KEY_READOUT_VISIBLE accessors
├── coord/
│   └── ByteCountFormatter.java         # NEW — bytes → "12.3 MB" (pure, unit-tested)
├── address/
│   ├── DatasetStorageSummary.java      # NEW — per-county + boundary sizes via FileSystem.sizeOfDirectory
│   └── OfflineAddressReceiver.java     # renderActiveCountyList() shows sizes + _boundary row (inject FileSystem)
├── address/forward/
│   ├── ResultOrdering.java             # NEW — enum { MOST_SIMILAR, DISTANCE }
│   ├── StreetCandidateRanker.java      # + rank(List, ResultOrdering, String) overload (keep rank(List))
│   └── ForwardSearchReceiver.java      # ordering toggle wiring; cache raw list; in-place re-rank
├── TwCoordMapComponent.java            # toggleReceiver → open settings (was cycle); apply pref_readout_visible; inject FileSystem into OfflineAddressReceiver
└── TwCoordPreferenceFragment.java      # + ordering PanListPreference + readout CheckBoxPreference (+ refreshAllSummaries)

app/src/main/res/
├── layout/forward_search_page.xml      # + ordering toggle (最相似 / 距離) above results
├── layout/offline_address_county_row.xml # + per-county size TextView (or extend meta)
├── xml/preferences.xml                 # + pref_search_result_ordering + pref_readout_visible
└── values/{strings,arrays}.xml         # + zh-TW labels & ordering entry/value arrays

gradle.properties                       # PLUGIN_VERSION_NAME 1.1.0→1.2.0, PLUGIN_VERSION_CODE 11→12

app/src/test/java/.../                  # NEW unit tests (formatter, ranker ordering, summary, prefs)
app/src/androidTest/java/.../           # Espresso: settings-from-button, sizes, ordering toggle
```

**Structure decision**: single Android plugin module (unchanged from 004–006).
All changes slot into existing packages behind existing seams
(`PreferenceStore`, `StreetCandidateRanker`, `ActiveDatasetRegistry`/`FileSystem`,
`TwCoordPreferenceFragment`); the only new files are small pure helpers and one
enum, each independently unit-testable per Constitution II.

## Phase 0 — Research

See [research.md](research.md). Open decisions resolved there:

- **R1** — How to open `TwCoordPreferenceFragment` directly from the tool
  button via the ATAK SDK preferences API, and how to reconcile the
  spec's "tool button switches coordinates" with the actual code
  (button = visibility toggle; widget tap = format cycle). Code-anchored per
  the project's plan-phase discipline (javap `main.jar` + SDK samples +
  upstream permalinks).
- **R2** — "Most similar" scoring model: deterministic textual-match score over
  the `StreetTextNormaliser` fold, tie-broken by distance (FR-004), reusing the
  existing normaliser; no edit-distance (stays out of scope per 006).
- **R3** — Storage sizing: per-county `File.length()` over `places.sqlite` and a
  `_boundary` total summing `townships.sqlite` + WAL/SHM/journal sidecars;
  human-readable formatting; missing/partial handling (FR-015).
- **R4** — Version bump location & SemVer (MINOR) and where the ordering toggle
  lives (search page + settings, both bound to one preference).

## Phase 1 — Design & Contracts

- [data-model.md](data-model.md) — `ResultOrdering`, the two new preference keys,
  `DatasetStorageSummary` / `BoundaryStorageSummary`, and the similarity-score
  derivation; validation/defaults (DISTANCE default preserves current behaviour;
  readout-visible default = true).
- [contracts/result-ordering.md](contracts/result-ordering.md),
  [contracts/settings-page.md](contracts/settings-page.md),
  [contracts/storage-display.md](contracts/storage-display.md) — behavioural
  contracts mapping each FR to an observable, test-backed behaviour.
- [quickstart.md](quickstart.md) — on-device manual verification for all three
  stories.

## Complexity Tracking

No Constitution violations — table intentionally omitted.
