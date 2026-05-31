# Changelog

All notable changes to `atak_tw_coord_plugin` are documented here. The format is
loosely based on [Keep a Changelog](https://keepachangelog.com/); the project
follows Semantic Versioning. Per-feature design records live under
[`docs/adr/`](docs/adr/); per-feature specs under [`specs/`](specs/).

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
