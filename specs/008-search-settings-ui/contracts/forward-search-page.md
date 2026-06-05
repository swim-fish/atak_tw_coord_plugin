# Contract: Forward Search Page Redesign

Behavioural contract for the redesigned `ForwardSearchReceiver` +
`forward_search_page.xml`. Each clause is observable and test-backed
(Espresso UI + unchanged controller unit suite as a regression guard).

## C-FS-1 Scope control replaces the inline grid (FR-001)
- **Given** the township stage is shown, **then** the page presents a
  `RadioGroup` with exactly two options (`fs_scope_all` whole-county,
  `fs_scope_specific` specific-township), a single township `Button`
  (`fs_btn_district`), and a house field (`fs_house_field`) — and **no**
  inline township grid (`fs_district_list` removed) and **no** inline keypad
  (`fs_keypad` / `fs_house_value` removed).

## C-FS-2 Whole-county default (FR-002)
- **Given** a county is chosen, **then** `fs_scope_all` is checked, the township
  button is disabled and reads `fs_district_whole_county`, `chosenDistrict` is
  `null`, `controller.chooseAllDistricts()` has been called, and the street
  stage is revealed — with no township selection required.
- **And** entering a street in this state returns the same candidates as the
  pre-redesign whole-county path (FR-016 regression guard).

## C-FS-3 Specific-scope auto-opens the chooser (FR-003)
- **Given** `chosenDistrict == null`, **when** `fs_scope_specific` is selected,
  **then** the township dialog opens automatically.
- **Given** `chosenDistrict != null`, **when** `fs_scope_specific` is selected,
  **then** `applySpecific(chosenDistrict)` re-applies without opening a dialog.

## C-FS-4 Township dialog (FR-004, FR-005)
- **When** `fs_btn_district` is tapped, **then** an `AlertDialog` opens listing
  `controller.districts()` in a 3-column glove grid plus a whole-county cell,
  with `controller.suggestedDistrict()` marked; the content scrolls and is
  height-bounded for many-township counties.
- **When** a township cell is tapped, **then** the dialog dismisses,
  `fs_scope_specific` becomes checked, the township button shows the name,
  `controller.chooseDistrict(name)` is called, and street results are scoped to
  it.
- **When** the whole-county cell is tapped, **then** scope reverts via
  `applyAll()`.

## C-FS-5 House-number dialog (FR-006, FR-007)
- **Given** street results are shown, **then** `fs_house_field` is visible
  (hidden before any street search).
- **When** the field is tapped, **then** a numeric keypad `AlertDialog` opens
  with keys `1..9 0` plus 巷 / 弄 / 號 / 之 / ⌫; each key routes through the
  existing `onKeypad(...)` and the candidate list updates live.
- **When** "clear" is chosen, **then** the entry empties and the whole-street
  candidate list re-renders; **when** "done" is chosen, **then** the dialog
  dismisses and the field reflects the entered value.

## C-FS-6 Map-follow auto-selection (FR-008)
- **When** map-follow re-resolves a county and a township from a new map centre,
  **then** the page applies the resolved township via `applySpecific(...)` (or
  `applyAll()` when the coordinate cannot be resolved), driving the same scope
  control — with no residual inline grid.

## C-FS-7 No behavioural change (FR-016)
- For identical inputs, the set and order of returned candidates, tap-to-pan,
  and GoTo behaviour are unchanged from before the redesign. The
  `ForwardSearchControllerTest` suite passes unmodified.

## C-FS-8 Crash isolation (Constitution VI)
- Every dialog/menu `OnClickListener`, the `RadioGroup` change listener, and the
  field listeners run inside `safeRun(...)`; dialogs are built with
  `getMapView().getContext()`; resource lookups are null-checked. No unguarded
  host→plugin entry point is introduced.
