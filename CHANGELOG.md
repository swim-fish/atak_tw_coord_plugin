# Changelog

All notable changes to `atak_tw_coord_plugin` are documented here. The format is
loosely based on [Keep a Changelog](https://keepachangelog.com/); the project
follows Semantic Versioning. Per-feature design records live under
[`docs/adr/`](docs/adr/); per-feature specs under [`specs/`](specs/).

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
