# Quickstart: Search & Storage Page UI Redesign — Manual Verification

On-device walkthrough validating all five user stories. Run on the reference
device (Galaxy Tab S10+ class) under ATAK-CIV 5.7.0.3 with at least two counties
imported plus the `_boundary` layer. Repeat the dialog checks once per locale
(zh-rTW / en / ja).

## Build & install
```powershell
# from repo root
./gradlew :app:assembleCivDebug    # or the configured plugin flavour
# install the produced APK to the device, load the plugin in ATAK
```
Confirm `app/build.gradle` `PLUGIN_VERSION` reads `1.3.0`.

## US1 — Search without knowing the township (P1)
1. Open the forward search tool; choose a county (清單 / 地圖中心 / 所在地).
2. **Expect**: scope control defaults to **全部** (whole-county); the township
   button reads "整個縣市（免選鄉鎮）" and is disabled; no inline township grid.
3. Type a street substring → candidates appear **without** picking a township.
4. **Expect**: same candidates as before the redesign (compare against a known
   pre-redesign result if available).

## US2 — Narrow to a township / enter a house number (P1)
1. Tap the township button → district dialog opens (3-column glove grid, a
   "全部" cell, suggested township marked "▶").
2. Tap a township → dialog dismisses; scope shows **指定鄉鎮**; button shows the
   name; results scope to it.
3. Tap the house-number field → numeric keypad dialog opens (digits + 巷/弄/號/
   之/⌫). Type a number → candidate list refines live. Tap **清除** → returns to
   whole-street; tap **完成** → dialog closes, field shows the number.
4. Enable map-follow; pan to a new county → **Expect**: county + township
   auto-applied through the same scope control; over an unresolvable point it
   falls back to **全部**; no leftover grid.

## US3 — Storage usage at a glance (P2)
1. Open TW Offline Addr with ≥2 counties + boundary installed.
2. **Expect**: a total usage figure; a single stacked bar with one coloured
   segment per county + one grey boundary segment; a legend matching those
   colours; each county row shows name, 日期·筆數, size, and a swatch.
3. **Verify**: total = sum of the per-county sizes + boundary size; each county's
   bar/legend/row swatch share one colour.

## US4 — Manage a county from its row (P2)
1. Tap a row's ⋮ overflow → menu shows **取代** and a red **移除**.
2. Choose **移除** → the existing confirmation dialog appears; confirm → existing
   removal behaviour runs (county disappears, sizes/total update).
3. Choose **取代** → existing replace confirm + picker flow runs.

## US5 — Import progress & failure feedback (P3)
1. Start a county import → **Expect**: an import-in-progress card with progress
   text and a moving bar (determinate % during copy/index-build, indeterminate
   otherwise).
2. Trigger a failure (e.g. cancel/corrupt the source) → **Expect**: a red banner
   with the reason, **重新選擇檔案** (re-opens the picker) and **關閉**; the
   previously installed county list and sizes remain unchanged below the banner.

## Cross-cutting checks
- **Dialog reliability (FR-017/SC-007)**: every dialog/menu above appears on
  first tap and dismisses without crashing ATAK, in all three locales.
- **Localisation (SC-008)**: switch device locale to en and ja; re-open each
  dialog/menu and confirm all new strings render (no `fs_*`/`offline_address_*`
  raw keys, no English fallback in ja).
- **Crash isolation (Constitution VI)**: with logcat open, exercise rapid
  double-taps on the township button, overflow, and keys — no `Throwable`
  escapes to ATAK; any caught fault logs via `Log.w` and the UI degrades
  gracefully.
- **No regression (FR-016/SC-006)**: a fixed query returns identical candidates
  and a fixed import produces identical installed data versus pre-redesign.
```
```
