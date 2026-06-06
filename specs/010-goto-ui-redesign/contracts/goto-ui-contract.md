# UI Contract: GoTo Coordinate-Input Page Redesign

This page exposes no network/service API. Its "contract" is the **view-id +
string-id surface** that Java binding, tests, and the design layout must agree
on, plus the **behaviour-preservation guarantees**. Anything not listed as
"changed/removed/added" is unchanged.

## View id contract

### Removed (structural change — R2)
| Old id | Replacement |
|---|---|
| `goto_autofill_taipower` | `goto_autofill` (single, header-level) |
| `goto_autofill_twd97` | `goto_autofill` |
| `goto_autofill_twd67` | `goto_autofill` |

### Added
| New id | Element | Purpose |
|---|---|---|
| `goto_autofill` | `Button` (header) | Single "Use map centre"; dispatches on `activeTab` |

### Preserved (must keep these ids — Java + design depend on them)
`goto_root`, `goto_title`, `goto_tabs`, `goto_tab_taipower`, `goto_tab_twd97`,
`goto_tab_twd67`, `goto_pane_taipower`, `goto_pane_twd97`, `goto_pane_twd67`,
`goto_input_taipower`, `goto_input_twd97_easting`, `goto_input_twd97_northing`,
`goto_input_twd67_easting`, `goto_input_twd67_northing`, `goto_zone_twd97`,
`goto_zone_twd97_121`, `goto_zone_twd97_119`, `goto_zone_twd67`,
`goto_zone_twd67_121`, `goto_zone_twd67_119`, `goto_advisory_twd97`,
`goto_advisory_twd67`, `goto_error_taipower`, `goto_error_twd97`,
`goto_error_twd67`, `goto_marker_mode_header`, `goto_mode_move`,
`goto_mode_waypoint`, `goto_mode_mission`, `goto_mode_spi`, `goto_mode_friendly`,
`goto_mode_hostile`, `goto_mode_neutral`, `goto_mode_unknown` (+ custom-icon
mode id as currently defined), `goto_btn_submit`, `goto_btn_atak_picker`,
`goto_recent_*`.

## String id contract

### Changed values (same ids — R-Localisation)
| id | en | zh-rTW | ja |
|---|---|---|---|
| `goto_marker_mode_header` | Marker mode | 標點模式 | マーカー種別 |
| `goto_btn_submit` | Submit & go | 送出並前往 | 送信して移動 |
| `goto_btn_autofill` | Use map centre | 帶入地圖中心 | 地図中心を取得 |
| `goto_btn_atak_picker` | Use ATAK icon palette… | 改用 ATAK 圖示盤… | ATAK アイコンパレットを使う… |

### Added
| id | en | zh-rTW | ja |
|---|---|---|---|
| `goto_taipower_help` | 9-char (10 m) or 11-char (1 m) · case-insensitive · spaces OK | 9 碼（10m）或 11 碼（1m）· 不分大小寫 · 可含空格 | 9桁（10m）/ 11桁（1m）· 大文字小文字不問 · 空白可 |

All strings resolve via `localisedContext` so the in-app UI-language override
applies.

## Drawable contract (new, concrete resource ids — no attr-ids)

`goto_segment_track`, `goto_tab_selected`, `goto_input_bg`, `goto_zone_cell_bg`,
`goto_marker_cell_bg`, `goto_autofill_bg`, `goto_advisory_bg`,
`goto_submit_primary_bg` (state-list: enabled/disabled), `goto_submit_secondary_bg`.

State-list selection colour MUST be expressed via `state_checked` /
`state_selected` / `state_enabled` in the drawable — **not** via programmatic
`setBackgroundColor` with an attribute id (Constitution VI).

## Java binding contract (`TwCoordGotoView`)

- Fields `autoFillTaipower/Twd97/Twd67` → single `autoFill`; bound from
  `R.id.goto_autofill`; null-checked.
- `autoFill.setOnClickListener(v -> safeClick("autoFill", () -> onAutoFill(activeTab)))`.
- `refreshAutoFillEnabled()` sets `autoFill.setEnabled(...)` from the active
  tab's `latestFix.*Ok()`.
- `refreshLocalisedStrings()` sets the single `autoFill` label.
- `styleTab(RadioButton, boolean)` → pill background on selection (no flat
  colour fill).
- `styleMarkerModeRadio(RadioButton, boolean)` → `setChecked` only; background
  via state-list drawable.
- (Optional) `refreshSubmitEnabled()` may set submit text colour by enabled state.

## Behaviour-preservation guarantees (MUST hold — verified by SC-005)

1. Coordinate parsing, datum/projection conversion, and rounding produce
   identical output for all systems and both zones, before vs after.
2. Submit pans the map to the resolved location (X/Y only; zoom preserved).
3. The ATAK icon-palette hand-off behaves identically.
4. Marker-mode set (incl. Move-only and Custom Icon) and the marker created on
   submit are identical.
5. Recent list contents and behaviour are identical.
6. Submit/Auto Fill re-entrancy guard (`submitInFlight`) and all entry-point
   `Throwable` wrapping (Constitution VI) remain in place.
