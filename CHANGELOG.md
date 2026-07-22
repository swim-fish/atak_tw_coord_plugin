# Changelog

All notable changes to `atak_tw_coord_plugin` are documented here. The format is
loosely based on [Keep a Changelog](https://keepachangelog.com/); the project
follows Semantic Versioning. Per-feature design records live under
[`docs/adr/`](docs/adr/); per-feature specs under [`specs/`](specs/).

## [Unreleased]

### Added

- **ATAK's native Taiwan pane now includes Address as its fourth tab.** The
  operator can switch between one full-address field and four structured
  fields, resolve a unique local result, or choose from a bounded ambiguous
  candidate list without moving the map before ATAK confirms the action.
- Convert Coordinate and Auto Fill resolve an offline address asynchronously
  while preserving the exact host WGS84 point; reverse lookup never snaps to
  the nearest address record.

### Changed

- **TW Coordinates is now the plugin's only public Tools item.** Offline dataset
  management remains available internally from that page, Settings, and native
  Address guidance even when every map-address readout toggle is off.
- The standalone **TW Coord GoTo**, **TW Addr Search**, and **TW Offline Addr**
  pages/actions were retired after native workflow and manager parity. Existing
  coordinate/address data and settings are retained during upgrade; obsolete
  custom GoTo Recent/marker/icon preferences are ignored rather than
  destructively migrated.
- Source/API/build compatibility remains compile ATAK-CIV 5.7.0.9 and declared
  minimum runtime 5.5.0. Physical acceptance on both lines remains a release
  gate and is not inferred from JVM or current-SDK build success.

## [1.4.2] — 2026-07-18 — Native Taiwan Go To fixes

### Fixed

- **The Taiwan pane no longer overlaps ATAK's elevation and action controls.**
  Its fields now follow ATAK's compact DD layout: horizontal label/input/unit
  rows, native underline inputs, bounded 48 dp selectors, ATAK-equivalent
  normal/large text dimensions, and no empty status-area height.
- **ATAK Convert Coordinate now prepares every Taiwan representation from the
  selected map point.** Switching between Taipower, TWD97, and TWD67 no longer
  reveals an empty or stale draft. Outer-island points prepare both zone-119
  TWD systems while Taipower reports that the point is unavailable.
- Native Clear and Auto Fill remain active-tab-only operations, and
  programmatic preparation does not trigger a host action or human-change
  notification.

## [1.4.0] — 2026-07-18 — Native Taiwan coordinates in ATAK Go To

### Added

- **ATAK's shared coordinate-entry dialog now includes one Taiwan pane.** It
  supports Taipower, TWD97, and TWD67; explicit TM2 zones 121/119; ATAK-owned
  Auto Fill, Clear, and Copy controls; horizontal-only results; read-only host
  dialogs; and English, Taiwan Traditional Chinese, and Japanese strings.
- **Native entry is additive.** The advanced **TW Coord GoTo** page remains
  available for marker modes, ATAK icon-palette delegation, and ten Recent
  entries. Its `pref_goto_*` state is independent from the native pane.

### Changed

- **Minimum ATAK-CIV runtime is now 5.5.0.** The build declares
  `com.atakmap.app@5.5.0.CIV`; ATAK 5.4 is no longer supported. The compile SDK
  is ATAK-CIV 5.7.0.9. The exact ATAK 5.5 device matrix remains pending in the
  checked-in feature evidence and is not implied by a successful current-SDK
  or TPP build. See ADR-0022, ADR-0023, and ADR-0024.


## [1.3.3] — 2026-06-06 — Hotfix: TW Offline Addr Import button could be pushed off-screen

### Fixed
- **TW Offline Addr — Import button (and the boundary row) could be clipped off
  the bottom on a long county list.** The page root was a non-scrolling
  `LinearLayout` with the Import button placed below an unweighted
  `wrap_content` inner `ScrollView`; a tall county list let the inner scroller
  consume all remaining height and push the fixed Import button past the bottom
  edge with no way to reach it. The whole page is now wrapped in a single outer
  `ScrollView` and the per-county list is a plain `LinearLayout` (no nested
  vertical scroller), so every control stays reachable on short panes.

### Governance
- **Constitution → 1.2.0**: Principle III gains a "Scrollable by default" rule —
  new/modified tool pages MUST use an outer `ScrollView` unless the content is
  provably short and fixed, and fixed actions must never sit below an unbounded
  inner scroller. This hotfix is its first application.

## [1.3.2] — 2026-06-06 — GoTo input page UI redesign (feature 010)

### Changed
- **The TW Coord GoTo input page adopts the feature-008 "compact stacked"
  design**, bringing it into visual parity with the TW Addr Search and TW
  Offline Addr pages and resolving six pain points: single-column stack with
  segmented coordinate-system tabs and carded fields; a clear primary
  **Submit & go** vs ghost **Use ATAK icon palette…** hierarchy; an enlarged
  ≥72 dp glove-friendly marker grid; a single header **Use map centre** Auto
  Fill button (replacing the three per-pane buttons) that dispatches on the
  active tab; and a labelled 121/119 projection-zone segmented control with the
  119 precision advisory. Tab / zone / marker selection is now driven by
  state-list drawables, and `styleMarkerModeRadio` no longer calls
  `setBackgroundColor` (Constitution VI).
- **Relabelled** `goto_btn_submit` (送出並前往 / Submit & go),
  `goto_btn_autofill` (帶入地圖中心 / Use map centre), `goto_btn_atak_picker`
  (改用 ATAK 圖示盤… / Use ATAK icon palette…), and the zh-TW
  `goto_marker_mode_header` (落點模式 → 標點模式); added a `goto_taipower_help`
  hint. New strings localised to en / zh-rTW / ja.

### Unchanged
- No coordinate behaviour changes: parsing, datum/projection conversion,
  Submit-and-pan (X/Y only), the ATAK icon-palette hand-off, input validation,
  and the Recent list are all untouched. The existing GoTo unit suite passes
  unmodified.

## [1.3.1] — 2026-06-06 — TW Addr Search county list popup + geographic order

### Changed
- **The TW Addr Search county list (清單…) is now an on-demand pop-up.** Tapping
  **清單…** opens a scrollable `AlertDialog` grid (same pattern as the township
  chooser) instead of expanding an inline grid on the page. Counties with no
  imported dataset stay marked ⚠ and dimmed; the currently-chosen county is
  highlighted. The inline `fs_county_list` grid (and the now-unused
  `markSelected` helper) were removed.
- **County list order is now geographic, not alphabetical.** Counties are ordered
  starting at 宜蘭, north, down the west coast, around the south, up the east
  coast, then outlying islands last (澎湖 / 金門 / 連江). Only the counties present
  in the imported `townships.sqlite` appear — e.g. the central `tw-central-full`
  pack's boundary contains 12 counties, so 12 show; a national boundary shows all
  22. The order folds 臺↔台 so either name form matches.

## [1.3.0] — 2026-06-05 — Search & storage page UI redesign (feature 008)

### Changed
- **TW Addr Search — township & house-number redesign.** The always-visible
  township `GridLayout` is replaced by an **All / District** segmented scope
  control plus an on-demand district chooser dialog; the always-visible numeric
  keypad is replaced by an on-demand house-number keypad dialog. After a county is
  chosen the scope defaults to whole-county, so an operator who knows only a street
  can search immediately. Same search results, ranking, and GoTo as before.
- **TW Offline Addr — storage dashboard redesign.** State B now shows a total
  on-disk figure, a single stacked usage bar (one weighted segment per county plus
  a grey boundary segment) and a colour legend, compact per-county rows whose
  Replace / Remove actions collapse into a per-row ⋮ overflow menu, an
  import-in-progress card with a progress bar (determinate during copy /
  index-build), and a dismissible failure banner with retry / dismiss. Import /
  registry / sizing behaviour is unchanged.
- **On-map address row gains a direction arrow.** The reverse-resolved address row
  (MAP / ME / TGT) prefixes an 8-point compass arrow (↑↗→↘↓↙←↖) pointing from the
  query point to the nearest record, omitted within 3 m, before the existing
  `~` / `~~` confidence marker.

### Fixed
- **Scope segmented control showed no selected state** — the 全部 / 指定鄉鎮 radios
  use a drawable that reacts to `state_selected`, not `state_checked`; the active
  scope now highlights (and the district dialog marks the current pick).
- **地圖中心 / 所在地 didn't surface the resolved 鄉鎮市區** — they now auto-select the
  resolved district (falling back to whole-county) so the district button + scope
  update visibly.
- **County list gave no missing-data hint** — counties without an installed dataset
  are marked with ⚠ and dimmed in the 清單… grid.
- **County chip showed county + district** — it is now county-only.
- **TW Offline Addr ignored the in-app UI-language override** — the storage page now
  takes a localised-context supplier and re-inflates on a language change, so its
  strings (import / replace / remove, total-usage figure, `_boundary` row, overflow
  menu, legend) follow the selected language like the forward-search page.

See [ADR-0020](docs/adr/0020-search-settings-ui-redesign.md) (decisions D1–D6 plus
device fixes F1–F6) and `specs/008-search-settings-ui/`.

## [1.2.1] — 2026-05-31 — TW Addr Search bug fixes

### Fixed
- **TW Addr Search source buttons (所在地 / 地圖中心 / 清單) showed English labels.**
  The forward-search page inflated its layout against the raw plugin context, whose
  resources resolve to the default (English) bundle, instead of the locale-overridden
  context (ADR-0003). The page now inflates against the live localised context and
  re-inflates on the next open after an in-app UI-language change (FR-018).
- **「最相似」ordering had no visible effect after a house number was typed.** Once a
  number narrows the list, every candidate shares the same street (e.g. `五權西路一段/二段`),
  so the street-only similarity bands tied and `MOST_SIMILAR` collapsed to distance
  order. The rank now adds a secondary key — numeric proximity of the candidate's leading
  house number to the typed one — so `五權西路 + 2` floats `…一段2C號` ahead of `12號 / 20號`,
  ties broken by distance.

## [1.2.0] — 2026-05-31 — Settings page & search/storage UX tweaks (feature 007)

### Changed
- **TW Coordinates tool button now opens the settings page** instead of cycling
  the on-map coordinate unit `Off → Taipower → TWD97 → TWD67`. The cycle (and its
  toast) is removed; the coordinate format is chosen in Settings
  (`pref_coord_unit`). The button broadcasts `com.atakmap.app.ADVANCED_SETTINGS`
  with a `toolkey` extra — opening the page never mutates the active format.
  (US2, ADR-0018 D1)

### Added
- **Show on-map readout** toggle in Settings (`pref_readout_visible`, default on)
  — replaces the show/hide that the old tool-button cycle's `Off` state provided.
  (US2)
- **Address search result order** — TW Addr Search results can be ordered by
  *distance* (default) or *most similar* (text match to the query). Toggling
  re-ranks the current list in place (no re-query); the choice persists and is
  bound to the same preference on the search page and in Settings. (US1,
  ADR-0018 D2)
- **Per-dataset storage sizes** in TW Offline Addr — each county row shows its
  on-disk size and a distinct `_boundary` (townships.sqlite) row shows the
  boundary folder size, or `未安裝` when absent. (US3, ADR-0018 D3)

### Fixed
- **Offline Address Replace/Remove confirm dialogs did nothing on-device.** Plugin
  `R.string` ids were passed to an `AlertDialog.Builder` created with the ATAK
  Activity context, throwing `Resources.NotFoundException` (swallowed) so no dialog
  appeared. Titles now resolve via `pluginContext.getString(...)`. Per-county
  Remove also now deletes atomically via `ActiveDatasetRegistry.remove()`
  (close-then-delete) so a removed county can't resurrect after restart. (ADR-0018
  D4)
- **Bottom-left MAP readout stayed stale after a programmatic pan** (TW Coord GoTo
  submit, TW Addr Search tap-to-pan). `CameraController.Programmatic.panTo`
  bypasses the `MapEventDispatcher`; a renderer-level
  `MapRenderer2.OnCameraChangedListener2` now refreshes the MAP coordinate +
  address on every camera change. Closes
  [#1](https://github.com/swim-fish/atak_tw_coord_plugin/issues/1). (ADR-0018 D5)

## [1.1.0] — 2026-05-30 — County-first forward search (feature 006)
- Offline **forward** address search (text/pick → coordinate) as a county-first
  funnel; consumes the MOI `townships.sqlite` boundary layer for 縣市 + 鄉鎮市區
  detection. Reverse-path readout now scoped to the detected county (replaces the
  005 query-all-counties fan-out). See [ADR-0017](docs/adr/0017-multi-county-zip-import.md)
  context and `specs/006-county-forward-search/`.

## [1.0.4] — 2026-05-23
- Taipower letter-table correction + 5 cell-centroid regression vectors. Reverse
  offline address (feature 004) and multi-county ZIP import (feature 005) landed
  in the lead-up to 1.1.0. See [ADR-0014](docs/adr/0014-offline-address-reconnaissance.md),
  [ADR-0015](docs/adr/0015-offline-address-implementation.md).

## [1.0.3] — 2026-05-17
- Custom marker-icon picker (feature 003). See
  [ADR-0010](docs/adr/0010-custom-marker-icon-picker.md) /
  [ADR-0011](docs/adr/0011-custom-marker-icon-implementation.md).

## [1.0.1] — 2026-05-17
- GoTo input page (feature 002) follow-ups. See
  [ADR-0009](docs/adr/0009-tw-coord-goto-input-page.md).

## [1.0.0] — 2026-05-17
- Initial release: on-map readout (feature 001) + GoTo input page (feature 002)
  for Taipower / TWD97 / TWD67.
