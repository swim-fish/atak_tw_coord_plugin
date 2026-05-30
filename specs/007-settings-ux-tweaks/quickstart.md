# Quickstart: Settings Page & Search/Storage UX Tweaks

**Feature**: 007-settings-ux-tweaks

On-device / emulator manual verification for the three tweaks. Assumes a build
with `tw-central-full.zip` imported (≥ 1 county active + `townships.sqlite` at
`active/_boundary/`), as used for feature 006.

## Build & install

```powershell
./gradlew assembleCivDebug
# install the plugin APK onto the ATAK-CIV 5.7.0.3 device/emulator per the
# project's normal sideload flow, then load the plugin in ATAK.
```

Confirm the version bumped: plugin shows **1.2.0** (versionCode 12) in ATAK's
plugin manager.

## US2 — Settings page from the tool button

1. Open the on-map readout (TW Coordinates) so the widget is visible.
2. **Tap the on-map readout widget** → it must **NOT** cycle the coordinate
   format (cancelled). *(C3, settings-page)*
3. Activate the **TW Coordinates tool button** → the **settings page opens**
   (it does not toggle widget visibility, and the format does not change on
   open). *(C1, C2)*
4. In settings, change the **coordinate format** → the on-map readout updates to
   the chosen format; reopening settings shows it selected. *(C4, C5)*
5. Toggle **顯示地圖讀數 (show on-map readout)** → the widget hides/shows. *(C6)*

## US1 — Result ordering

1. Open **前向搜尋 (forward search)**, funnel to a street with several matches
   (e.g. 台中市 → a district → `中山路`).
2. Default ordering is **距離** — nearest first (unchanged from before). *(C2)*
3. Switch to **最相似** → the list re-orders so the closest textual match to
   `中山路` is first; switching back to **距離** restores nearest-first — both
   **without** re-running the search. *(C3, C4)*
4. Type `台灣大道` against gazetted `臺灣大道…` rows → folded match still ranks
   correctly under 最相似. *(C5)*
5. Tap a candidate → map pans via GoTo with the compass arrow/distance, the same
   under either ordering. *(C7)*
6. Close and re-open the page (or restart) → your last ordering choice is the
   default. *(C6)* It is also adjustable from the **settings page** and the two
   stay in sync. *(settings-page C7)*

## US3 — Storage sizes in TW Offline Addr

1. Open **TW Offline Addr**.
2. Each installed county row shows its on-disk size (e.g. `台中市 … 324.0 MB`)
   next to its record count. *(C1, C3)*
3. A distinct **`_boundary` (townships.sqlite)** row shows the boundary folder
   total (e.g. `~9.8 MB`). *(C2)*
4. With no `_boundary` present (a places-only install), the boundary row shows
   **未安裝** and the screen still loads. *(C4)*

## Automated checks

```powershell
./gradlew testCivDebugUnitTest          # ByteCountFormatter, ranker orderings,
                                        # similarity bands, DatasetStorageSummary,
                                        # PreferenceStore round-trips/defaults
./gradlew connectedCivDebugAndroidTest  # Espresso: settings-from-button,
                                        # no-cycle-on-tap, ordering toggle re-rank,
                                        # storage rows render
```

All new business logic ≥ 80 % covered (Constitution II). Default ordering output
must equal the legacy distance ranking (regression guard, C2).
