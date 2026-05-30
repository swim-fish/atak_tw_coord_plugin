# Contract: Settings Page from Tool Button (US2)

Behavioural contract for re-pointing the TW Coordinates tool button and
cancelling the direct coordinate cycling. Covers FR-006…FR-011, SC-003, SC-004.

**Current code anchor**: the Tools-menu "TW Coordinates" icon (`TwCoordTool`)
fires `ACTION_SHOW_PLUGIN`; `TwCoordMapComponent.toggleReceiver` handles it by
cycling the on-map readout `Off → Taipower → TWD97 → TWD67 → Off`. The settings
screen `TwCoordPreferenceFragment` is registered under ATAK Settings → Tool
Preferences. This contract changes the handler, not the action or registration.

## C1 — Tool button opens settings
- **Given** the operator is on the map,
- **When** they tap the TW Coordinates tool button (`ACTION_SHOW_PLUGIN`),
- **Then** the plugin settings screen (`TwCoordPreferenceFragment`) is shown
  (FR-006), via the R1 verified launch mechanism (or its DropDownReceiver
  fallback).
- *Test*: Espresso — tapping the tool shows the settings screen.

## C2 — Opening settings does not change the format
- **Given** the active coordinate format is X,
- **When** the button opens settings (no selection made),
- **Then** the on-map readout format is still X (FR-007) — no `setCoordinateUnit`
  side effect on open.
- *Test*: Espresso — open + back without selecting; readout unchanged.

## C3 — Direct coordinate cycling is cancelled
- **Given** the tool button,
- **When** it is tapped repeatedly,
- **Then** the coordinate format does **not** cycle through
  Taipower/TWD97/TWD67 (the `toggleReceiver` unit-cycle + cycle-Toast is
  removed) — "取消直接切換座標".
- *Test*: Espresso — repeated taps leave `pref_coord_unit` constant; no cycle
  Toast.

## C4 — Format is chosen in settings and applies live
- **Given** the settings screen is open,
- **When** the operator selects a coordinate format (`pref_coord_unit`),
- **Then** the on-map readout updates to it (FR-008, via the existing
  `prefListener` → `renderMapCentre` path) and the choice persists across
  sessions (FR-010).
- *Test*: Espresso — select format → readout reflects it; unit — preference
  round-trip.

## C5 — Current format is indicated
- **Given** a format is active,
- **When** settings opens,
- **Then** `pref_coord_unit` shows the active format as the selected value
  (already true via `refreshCoordUnitSummary`) (FR-009).
- *Test*: Espresso — selected entry matches the active unit.

## C6 — Readout visibility preserved via settings
- **Given** the button no longer cycles to a hidden state,
- **Then** settings exposes `CheckBoxPreference` **`pref_readout_visible`**
  (default on); `TwCoordMapComponent` applies it (`widget.setVisible(...)`) on
  snapshot and on change.
- *Test*: Espresso — toggling it shows/hides the readout; unit — default +
  round-trip.

## C7 — Settings hosts the result-ordering preference
- **Given** the settings screen,
- **Then** it includes a result-ordering `PanListPreference` bound to the same
  `pref_search_result_ordering` key as the search-page toggle (FR-011).
- *Test*: Espresso — changing it in settings is reflected on the search page and
  vice-versa.

## C8 — Localisation
- **Given** any new settings entry/label,
- **Then** its title/summary/entries are zh-TW (Taiwan terms; Constitution V),
  defined in `res/values/strings.xml` + `arrays.xml`, and refreshed by
  `refreshAllSummaries` against the UI-language override like the existing rows.
- *Test*: review + Espresso text assertions.
