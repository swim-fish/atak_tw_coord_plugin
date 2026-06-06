# Phase 1 Data Model: GoTo Coordinate-Input Page UI Redesign

**This feature introduces no new data entities.** It is a presentation-layer
restyle. The model below documents the existing, unchanged entities the page
binds to, so the contract and tasks can reference them precisely. None of their
fields, validation rules, or state transitions change.

## Reused entities (unchanged)

### CoordinateUnit / `activeTab`
- The selected coordinate system: `TAIPOWER` | `TWD97` | `TWD67`.
- Drives which input pane is visible and which Auto Fill / submit logic runs.
- State transition: tab selection → `setActiveTab(unit)` → shows that pane,
  refreshes Auto Fill enabled-state and submit enabled-state.
- **Redesign impact**: rendered as a segmented control instead of plain tabs;
  the underlying enum and transitions are identical.

### MarkerMode
- The marker dropped on submit: `MOVE_ONLY` plus the existing affiliation/spot
  types and `CUSTOM_ICON` (full set preserved).
- Mutually exclusive selection; persisted via `PreferenceStore`.
- **Redesign impact**: rendered as an enlarged glove-friendly grid; selection
  appearance moves to a state-list drawable; enum, persistence, and the
  submit-time marker creation are unchanged.

### ParseResult (`lastTaipowerParse` / `lastTwd97Parse` / `lastTwd67Parse`)
- The outcome of parsing the current input for each system (validity + parsed
  coordinate + error message).
- Drives inline error text and submit enabled-state.
- **Redesign impact**: error text is restyled (`goto_advisory_bg` / carded
  error); parsing and validity rules are unchanged.

### MapCenterFix (`latestFix`)
- Snapshot of the current map centre and per-system representability
  (`taipowerOk()` / `twd97Ok()` / `twd67Ok()`).
- Drives the single Auto Fill button's enabled-state.
- **Redesign impact**: one button reads the active system's `*Ok()` instead of
  three buttons each reading their own; the fix object is unchanged.

### Projection zone (121 / 119)
- Per-TWD-pane zone selection within a `RadioGroup`.
- Selecting 119 surfaces the existing precision advisory.
- **Redesign impact**: rendered as a labelled segmented control; advisory
  restyled; zone-to-conversion behaviour unchanged.

### RecentEntry / RecentEntryStore
- Recently submitted coordinates list.
- **Redesign impact**: list section restyled to match; store, persistence, and
  listener fan-out (already Constitution-VI guarded) unchanged.

## Validation rules (unchanged)

- Submit is enabled only when the active system's `ParseResult` is valid.
- Auto Fill is enabled only when the active system can represent the map centre.
- All parsing, datum/projection conversion, and rounding stay byte-identical
  (verified by SC-005).
