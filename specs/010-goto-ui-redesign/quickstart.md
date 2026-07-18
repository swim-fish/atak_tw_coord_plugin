# Quickstart: GoTo Coordinate-Input Page UI Redesign

## Prerequisites

- Local `local.properties` with `sdk.dir`, `sdk.path` (ATAK-CIV 5.7.0.3 SDK),
  `takdev.plugin` (already configured in this repo).
- A connected device/emulator running ATAK 5.5+ (`adb devices` shows one).

## What changes (developer view)

| Area | File(s) | Nature |
|---|---|---|
| Layout | `app/src/main/res/layout/tw_coord_goto.xml` | Rewrite to compact stacked design |
| Drawables | `app/src/main/res/drawable/goto_*.xml` (9 new) | Copy/adapt from `docs/design/search_settings/drawable/` |
| Strings | `app/src/main/res/values{,-zh-rTW,-ja}/strings.xml` | 3–4 label edits + 1 new `goto_taipower_help` |
| View binding | `app/src/main/java/.../gotopage/TwCoordGotoView.java` | 4 localized edits (see `TwCoordGotoView_changes.md`) |
| Docs | `CHANGELOG.md`, GoTo guide under `docs/` | Per Constitution III/V |

Reference design source: `docs/design/search_settings/` (`tw_coord_goto.xml`,
`TwCoordGotoView_changes.md`, `strings_additions_goto.xml`, `goto_*` drawables).

## Implementation order (matches plan)

1. Add the 9 `goto_*` drawables.
2. Add/replace the 4 string ids in all three locales.
3. Replace `tw_coord_goto.xml` with the compact stacked layout (keep all
   preserved ids; rename the three Auto Fill buttons to single `goto_autofill`).
4. Apply the 4 Java edits in `TwCoordGotoView.java`:
   - Auto Fill 3 fields → 1 (`autoFill`), bind `R.id.goto_autofill`, one listener
     via `safeClick`, `refreshAutoFillEnabled()` reads active tab's `*Ok()`,
     `refreshLocalisedStrings()` sets one label.
   - `styleTab()` → pill selected background.
   - `styleMarkerModeRadio()` → `setChecked` only (drop `setBackgroundColor`).
   - (optional) submit text colour on enabled/disabled.
5. Update CHANGELOG + GoTo guide.

## Verify (the gate)

```powershell
./gradlew spotlessCheck lint testCivDebugUnitTest assembleCivDebug
```

- `spotlessCheck` / `lint`: zero new warnings (Constitution I).
- `testCivDebugUnitTest`: existing GoTo unit tests stay green **unmodified**
  (Constitution II refactor exemption) — `CoordinateParserRoundTripTest`,
  `TaipowerParserTest`, `TwdTm2ParserTest`, `MapCenterFixTest`, `MarkerModeTest`.
- `assembleCivDebug`: layout/drawable/string resources compile.

Install and eyeball on device:

```powershell
./gradlew installCivDebug
```

## On-device acceptance (maps to spec SCs)

1. Open **GoTo**; confirm segmented system selector + carded fields, single
   column, visibly shorter (SC-001/002, US1).
2. Enter a valid coordinate; confirm one emphasised **Submit & go** + ghost
   **Use ATAK icon palette…**; submit pans the map (SC-004, US2).
3. Confirm marker grid cells are glove-sized; selecting then submitting drops the
   right marker — Move-only or any of the seven marker types; custom icons via the
   separate ATAK icon-palette button (SC-003, US3).
4. Press header **Use map centre** on each tab; confirm it fills the active
   system and disables when not representable (toast) (US4).
5. On TWD97/67, confirm 121/119 segmented + 119 precision advisory (US5).
6. Toggle in-app UI language; confirm zh-TW / en / ja labels render (SC-006).
7. **Behaviour-preservation (SC-005)**: enter the same fixed inputs as before the
   redesign across all systems/zones; resolved coordinates must match exactly.

## Rollback

Revert the layout/drawable/string/Java changes; no data, preference, or schema
migration is involved, so rollback is a pure source revert.
