# ADR-0020: Search & storage page UI redesign (feature 008)

**Status**: Accepted
**Date**: 2026-06-05
**Origin**: feature `008-search-settings-ui` (`/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`). Version bump `1.2.1 → 1.3.0`.

A presentation-only redesign of the two operator-facing Tools-menu pages — the
forward address search page (`ForwardSearchReceiver`) and the offline address
storage page (`OfflineAddressReceiver`). No change to search, ranking, import,
registry, or geocoding behaviour. The concrete design was worked out up front in
`docs/design/search_settings/` (receiver change notes, replacement layouts,
county-row layout, string additions, five drawables); this ADR records the
decisions and the SDK anchoring.

## Context

Two independent page areas, both behind existing seams. The forward page's
always-visible township `GridLayout` and numeric keypad dominated vertical
space and forced a township decision on the common path; the storage page's flat
per-county list gave no sense of disk usage and its per-row Replace/Remove
buttons cluttered the row. The user's explicit constraint was to follow the ATAK
SDK samples' dialog pattern ("must follow the SDK samples or you debug for a long
time"). Phase-0 research (R1–R6) anchored each decision against `javap` of
`ATAK-CIV-5.7.0.3-SDK/main.jar` and the shipped code plus the `helloworld` /
`meshtastic_atak` samples.

## Decisions

### D1 — Forward page: segmented scope control replaces the inline township grid (US1)

The inline `fs_district_list` `GridLayout` is removed. Stage 2 is now a two-state
`RadioGroup` (`fs_scope_all` 全部 / `fs_scope_specific` 指定鄉鎮) plus one district
button (`fs_btn_district`). After a county is chosen the scope defaults to
whole-county (`controller.chooseAllDistricts()`), the button is disabled showing
"整個縣市（免選鄉鎮）", and the operator drops straight to the street stage — zero
township taps on the common path. Programmatic `check()` brackets a
detach/re-attach of the `OnCheckedChangeListener` to avoid re-entrancy.

### D2 — Forward page: on-demand district & house-number `AlertDialog`s (US2)

`showDistrictDialog()` rebuilds the glove grid (reusing `gridCell(...)`,
`fs_grid_cell_bg`) inside a scrollable dialog with a 全部 cell and the suggested
district marked "▶". `showHouseDialog()` builds the numeric keypad
(1–9, 0, 巷/弄/號/之, ⌫) per open with a live display, routing keys through the
unchanged `onKeypad(...)`; Clear resets to the whole street, Done dismisses, and
the field reflects the value via `reflectHouseField()`. The house field is hidden
until a street search produces results.

### D3 — Storage page: usage summary (total + stacked bar + legend) (US3)

`renderUsageBar(snap)` draws a total figure (`offline_address_usage_total`,
including the boundary folder), a single stacked bar of weighted segments
(`weight = max(bytes, 1)`) — one per county plus a grey boundary segment — and a
matching legend. A shared `OA_PALETTE`, indexed by `snapshot()` iteration order,
guarantees bar = legend = per-row swatch colour for each county. Reuses the
O(counties) `FileSystem.sizeOfDirectory` reads feature 007 already performs; no
new DB opens, no charting dependency.

### D4 — Storage page: per-row overflow `PopupMenu` (US4)

The inline Replace / Remove buttons collapse into a ⋮ overflow
(`offline_address_county_overflow`); `showCountyMenu(anchor, county)` opens a
`PopupMenu` with 取代… and a destructively-styled (red, `SpannableString` +
`ForegroundColorSpan`) 移除, delegating to the unchanged `confirmReplaceCounty` /
`confirmRemoveCounty` confirm-then-act flows. Every management action available
before the redesign remains reachable.

### D5 — Storage page: import progress card + failure banner (US5)

`showProgress` / `hideProgress` toggle an `offline_address_progress_card`;
`postProgress` sets a `ProgressBar` determinate (percent) only for the `COPYING`
and `BUILDING_RTREE` stages (the ones carrying a fraction) and indeterminate
otherwise. `showError` / `clearError` toggle a dismissible
`offline_address_error_card` banner with **重新選擇檔案** (re-opens the picker via
`launchPicker()`) and **關閉**; importer failure leaves installed data untouched.
The batch path routes through `showProgress` so the card shows for multi-file
imports too.

### D6 — Cross-context dialog/menu rule (FR-017, the user's constraint)

Every new `AlertDialog` / `PopupMenu` is built with the host ATAK Activity context
(`getMapView().getContext()`), while views are inflated and strings/drawables
resolved against the plugin context (ADR-0003). This is the exact pattern proven
in the shipped `OfflineAddressReceiver` confirm dialogs and the SDK samples
(`helloworld` `new AlertDialog.Builder(mapView.getContext())`; `meshtastic_atak`
`MapView.getMapView().getContext()`), and it is what prevents the silent
`BadTokenException` / `Resources.NotFoundException` failures the constraint exists
to avoid.

## Alternatives considered

- **Spinner / collapsible inline grid for scope** — rejected: small targets / still
  consumes vertical space and forces a township decision (D1).
- **System soft-keyboard for house numbers** — rejected: no 巷/弄/號/之 keys and it
  covers the candidate list under gloves (D2).
- **Third-party chart / per-county bars for usage** — rejected: no-new-deps
  constraint; a single stacked bar conveys share-of-total directly (D3).
- **Inline row action buttons / bottom sheet** — rejected: clutters the compact row;
  `PopupMenu` is the lighter, SDK-consistent anchor menu (D4).
- **Building dialogs with the plugin context** — rejected: silent
  `BadTokenException` at `show()` (D6).

## Consequences

- Operator who knows only a street searches after choosing a county with zero
  township taps (SC-001); township scoping is two taps (SC-002); storage total
  equals Σ county + boundary with consistent colours (SC-003 / SC-004); every
  prior management action remains reachable (SC-005).
- No functional regression: the controller / importer / registry / ranker APIs are
  untouched and their unit suites pass unmodified (`:app:testCivDebugUnitTest`
  green — FR-016 / FR-019).
- Crash isolation (Constitution VI): every new dialog/menu listener, the scope
  `RadioGroup` listener, the overflow `setOnMenuItemClickListener`, and the
  progress/error button listeners run through `safeRun(...)`; `renderUsageBar` /
  `postProgress` keep their `try/catch(Throwable)→Log.w`; resource lookups are
  null-checked; builders use the Activity context; colours/drawables are concrete
  (no `android.R.attr.*`).
- Localisation (Constitution V): all new strings shipped in en / zh-rTW / ja.

## Follow-ups (not done in this branch)

- **Espresso UI tests** (tasks T008/T015/T016/T023/T028/T032) and the **new JVM
  unit tests** (T022/T031) are authored as tasks but not implemented in this run:
  the Espresso suites need a device/emulator, and the pure-logic unit tests want a
  small helper-class extraction because the receivers extend `DropDownReceiver`
  (an ATAK class not loadable under plain JUnit). Tracked for a follow-up; the
  existing controller/importer/ranker suites already guard against functional
  regression.
- **On-device acceptance** (quickstart, all three locales) pending hardware.

## Links

- Spec / plan / tasks: `specs/008-search-settings-ui/{spec,plan,tasks}.md`
- Design input: `docs/design/search_settings/`
- UI docs: `docs/ui/forward-search-page.md`, `docs/ui/offline-address-page.md`
- Related: ADR-0003 (plugin-context resources), ADR-0016 (prefer SDK samples), ADR-0018 (feature 007 storage sizing this builds on).
