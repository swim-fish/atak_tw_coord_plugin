# Feature Specification: Settings Page & Search/Storage UX Tweaks

**Feature Branch**: `007-settings-ux-tweaks`

**Created**: 2026-05-30

**Status**: Draft

**Input**: User description: "Version update 小調整 — 調整地址搜尋結果排序（最相似結果 或 距離）；更改 TW Coordinates tool 按鈕改成打開 plugin 設定頁面，取消直接切換座標，進入設定頁面調整；TW Offline Addr 也需要顯示各縣市檔案大小以及顯示 _boundary(townships.sqlite) 資料夾大小"

## Overview

A minor-version maintenance release that bundles three small, independent UX
improvements to the shipped plugin:

1. Let the operator choose how forward-address-search results are ordered —
   by best textual match ("most similar") or by distance from the anchor.
2. Repurpose the **TW Coordinates** tool button so it opens a dedicated
   plugin **settings page** instead of immediately cycling the on-map
   coordinate format; the format is now chosen inside that settings page.
3. Surface on-disk storage usage in **TW Offline Addr** — the file size of
   each imported county dataset and the size of the `_boundary`
   (`townships.sqlite`) folder.

Each change is small and shippable on its own; together they form the next
plugin version bump.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Choose How Search Results Are Ordered (Priority: P1)

A field operator runs a forward address search and gets a list of candidate
matches. Depending on the task, they sometimes want the result whose address
text most closely matches what they typed at the top (e.g., they typed a
fairly complete address and want the exact place first), and other times want
the nearest matching place to their current anchor at the top (e.g., they
typed a partial street and want the closest one). The operator can switch
between **most-similar** ordering and **distance** ordering, and the plugin
remembers their preference for the next search.

**Why this priority**: Result ordering is the single biggest factor in how
fast an operator finds the right place in a list; getting the wrong place at
the top costs scrolling and mistaps under field conditions. This is the most
directly task-impacting of the three tweaks.

**Independent Test**: Perform one search that returns several candidates,
toggle the ordering control, and confirm the list re-orders accordingly and
that the choice persists into a subsequent search — without needing either of
the other two changes.

**Acceptance Scenarios**:

1. **Given** a search returning multiple candidates ordered by distance,
   **When** the operator selects "most similar" ordering, **Then** the
   currently displayed candidate list re-orders so that, among the displayed
   candidates, the one whose address text best matches the query appears first.
2. **Given** the operator has selected "most similar" ordering, **When** they
   select "distance" ordering, **Then** the list re-orders so the candidate
   nearest the active distance anchor appears first.
3. **Given** the operator chose an ordering on a prior search, **When** they
   open the search page and run a new search, **Then** results use the
   previously chosen ordering by default.
4. **Given** any ordering is selected, **When** the operator taps a candidate,
   **Then** the map pans to that candidate exactly as before (ordering does
   not change pan/GoTo behaviour).

---

### User Story 2 - Open a Settings Page from the TW Coordinates Tool (Priority: P2)

An operator taps the **TW Coordinates** tool button. Instead of the on-map
coordinate readout immediately cycling to the next format (the current
behaviour), a plugin **settings page** opens. From that page the operator
chooses the active on-map coordinate format (and adjusts other available
plugin preferences). Choosing a format in the settings page updates the on-map
readout; closing the page returns the operator to the map.

**Why this priority**: The current button cycles format on every tap, which is
easy to trigger by accident and gives no way to see or pick a specific format.
A settings page makes the choice explicit and creates a home for the other
preferences this release introduces. It depends on no other story but is
slightly less time-critical than result ordering.

**Independent Test**: Tap the TW Coordinates tool button and confirm a
settings page opens (rather than the format cycling), select a specific
coordinate format there, and confirm the on-map readout switches to it.

**Acceptance Scenarios**:

1. **Given** the operator is on the map, **When** they tap the TW Coordinates
   tool button, **Then** the plugin settings page opens and the on-map
   coordinate format does NOT change merely from opening it.
2. **Given** the settings page is open, **When** the operator selects a
   coordinate format, **Then** the on-map coordinate readout updates to that
   format.
3. **Given** the operator selected a format previously, **When** they reopen
   the settings page, **Then** the currently active format is shown as the
   selected option.
4. **Given** the settings page is open, **When** the operator closes it,
   **Then** they return to the map with the chosen format active.
5. **Given** the settings page is open, **When** the operator toggles the
   "show on-map readout" control off (then on), **Then** the on-map coordinate
   readout hides (then shows), and the setting persists across sessions.

---

### User Story 3 - See Storage Used by Offline Datasets (Priority: P3)

An operator opens **TW Offline Addr** to review what offline data is installed.
For each imported county dataset they can see how much disk space it occupies,
and they can also see the size of the shared `_boundary`
(`townships.sqlite`) folder. This helps them decide what to keep or remove on
storage-constrained devices.

**Why this priority**: Useful housekeeping information, but it does not change
search or coordinate behaviour, so it is the least time-critical of the three.

**Independent Test**: Import one or more county datasets, open TW Offline Addr,
and confirm a per-county size figure is shown for each dataset plus a single
size figure for the `_boundary` folder.

**Acceptance Scenarios**:

1. **Given** one or more county datasets are installed, **When** the operator
   opens TW Offline Addr, **Then** each county entry shows its on-disk size in
   human-readable units.
2. **Given** the `_boundary` (`townships.sqlite`) folder exists, **When** the
   operator opens TW Offline Addr, **Then** its total size is shown as a
   distinct entry.
3. **Given** no county datasets are installed, **When** the operator opens TW
   Offline Addr, **Then** the screen still loads and the boundary folder size
   (if present) is shown without error.

---

### Edge Cases

- **Most-similar within the displayed set**: ordering re-sorts the candidates
  already shown (a distance-bounded top-N); it does not pull in a closer textual
  match that fell outside that set. The candidate set is unchanged by ordering
  (FR-005).
- **Tie in most-similar ordering**: when two candidates match the query text
  equally well, ordering falls back to distance so the result remains
  deterministic.
- **No active distance anchor**: when distance cannot be computed (no anchor),
  distance ordering must degrade gracefully (e.g., fall back to most-similar
  or keep insertion order) rather than crash or show blank rows.
- **Settings opened with no coordinate data / before map ready**: the settings
  page must still open and let the operator pick a format.
- **Missing or empty `_boundary` folder**: TW Offline Addr shows the boundary
  entry as absent or 0 rather than failing to load.
- **Very large datasets**: size figures must remain readable (appropriate
  units such as MB/GB) for 100–324 MB place DBs and the ~10 MB boundary DB.
- **Size while an import is in progress**: a partially written dataset should
  not crash the size listing.

## Requirements *(mandatory)*

### Functional Requirements

#### Result ordering
- **FR-001**: The forward address search MUST let the operator choose between
  two result orderings: "most similar" (best textual match to the query) and
  "distance" (nearest to the active distance anchor first).
- **FR-002**: The system MUST re-order the currently displayed candidate list
  immediately when the operator changes the ordering, without requiring a new
  search to be issued.
- **FR-003**: The system MUST persist the operator's chosen ordering and apply
  it as the default for subsequent searches within and across sessions.
- **FR-004**: "Most similar" ordering MUST rank candidates by how closely each
  candidate's address text matches the operator's query, breaking ties by
  distance so ordering is deterministic.
- **FR-005**: Changing the ordering MUST NOT change which candidates are
  returned, nor alter tap-to-pan / GoTo behaviour or the per-result compass
  arrow.

#### Settings page & coordinate format
- **FR-006**: Tapping the TW Coordinates tool button MUST open the plugin
  settings page instead of cycling the on-map coordinate format.
- **FR-007**: The system MUST NOT change the active on-map coordinate format
  as a side effect of merely opening the settings page.
- **FR-008**: The settings page MUST let the operator select the active on-map
  coordinate format from the supported formats, and selecting a format MUST
  update the on-map readout.
- **FR-009**: The settings page MUST indicate which coordinate format is
  currently active when opened.
- **FR-010**: The selected coordinate format MUST persist across sessions
  (equivalent persistence to the prior behaviour).
- **FR-011**: The settings page MUST be the home for the result-ordering
  preference (FR-003) so all newly introduced preferences are adjustable in
  one place. (The ordering control MAY also be reachable from the search page;
  the two MUST stay in sync.)
- **FR-017**: Because the tool button no longer cycles the on-map readout to a
  hidden state, the settings page MUST provide a control to show/hide the
  on-map coordinate readout, defaulting to shown, and the on-map readout MUST
  honour it. (Preserves the visibility control the cycling button used to give.)

#### Storage usage display
- **FR-012**: TW Offline Addr MUST display the on-disk size of each imported
  county dataset.
- **FR-013**: TW Offline Addr MUST display the total size of the `_boundary`
  (`townships.sqlite`) folder as a distinct entry.
- **FR-014**: Sizes MUST be shown in human-readable units appropriate to the
  magnitude (e.g., MB/GB) and MUST load without blocking the rest of the
  screen.
- **FR-015**: The storage display MUST handle missing/empty/partial datasets
  and a missing `_boundary` folder without error.

#### Release
- **FR-016**: The plugin version MUST be incremented for this maintenance
  release.

### Key Entities *(include if feature involves data)*

- **Plugin Settings**: the persisted set of operator preferences, including
  the active coordinate format and the search-result ordering preference.
- **Result Ordering Preference**: an enumerated choice — "most similar" or
  "distance" — that controls candidate list order.
- **County Dataset (storage view)**: a representation of an installed county
  place DB with its display name and on-disk size.
- **Boundary Folder (storage view)**: the `_boundary` (`townships.sqlite`)
  location with its total on-disk size.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An operator can switch result ordering and see the list re-order
  in under 1 second for a typical candidate list, with no new search required.
- **SC-002**: After choosing an ordering, 100% of subsequent searches in the
  same and later sessions open with that ordering as the default.
- **SC-003**: Tapping the TW Coordinates tool button opens the settings page
  100% of the time and never silently changes the on-map format on open.
- **SC-004**: An operator can identify and select a specific coordinate format
  from the settings page in under 15 seconds, including for first-time use.
- **SC-005**: For every installed county dataset, TW Offline Addr shows a size
  figure, and the `_boundary` folder size is shown whenever the folder exists.
- **SC-006**: All three changes ship together under a single incremented
  plugin version with no regression to existing search, GoTo, or reverse-geocode
  behaviour.

## Assumptions

- "Most similar" is interpreted as ranking by textual match quality between
  the operator's query and each candidate's address text (closer/cleaner
  matches rank higher); exact algorithm is an implementation detail deferred
  to planning, but it reuses the existing query/normalisation rules (臺↔台,
  width fold, `段` handling) rather than introducing fuzzy/edit-distance
  matching (which remains out of scope per feature 006).
- The result-ordering control is presented as a simple two-way toggle/selector;
  it lives in the settings page and MAY be mirrored on the search page for
  quick access, with both reflecting the same persisted preference.
- The "plugin settings page" is the plugin's existing consolidated preferences
  screen (the same screen reachable today via ATAK Settings → plugin
  preferences); this feature reuses it as the destination of the TW Coordinates
  tool button rather than introducing a brand-new screen. It at minimum hosts
  the coordinate-format choice and the result-ordering preference. Additional
  pre-existing preferences (if any) may be co-located but no new unrelated
  preferences are introduced by this feature.
- The set of supported coordinate formats is unchanged from the current
  behaviour — only the way the operator switches between them changes.
- The tool button previously doubled as the on-map readout's show/hide control
  (the cycle ended hidden). That capability moves to a "show on-map readout"
  toggle in the settings page (FR-017), defaulting to shown.
- "各縣市檔案大小" means the on-disk size of each installed per-county place DB;
  "_boundary 資料夾大小" means the total size of the boundary folder containing
  `townships.sqlite` (and any sidecar files).
- Sizes are computed from the active datasets on device storage at the time the
  TW Offline Addr screen is opened (no continuous live monitoring required).
- "Version update 小調整" refers to a minor maintenance version bump that
  bundles these tweaks; no data-schema or dataset-generator changes are needed.

## Out of Scope

- Fuzzy / edit-distance typo tolerance in search (remains out of scope from
  feature 006).
- Any change to which candidates a search returns or to county-first funnel
  behaviour.
- Deleting/managing datasets from the storage view (display only; no new
  delete action is required by this feature).
- New coordinate formats or changes to coordinate computation.
- Tier-2 `roads.sqlite` / Tier-3 landmark search and other deferred 006 items.
