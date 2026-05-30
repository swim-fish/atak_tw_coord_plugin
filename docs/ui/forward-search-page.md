# UI: Forward Search page (feature 006)

A standalone Tools-menu entry (前向搜尋), sibling to 離線地址 and the GoTo input
page. Implements the county-first funnel from
[`docs/research/county-scoped-forward-search.md`](../research/county-scoped-forward-search.md)
with a glove + ATAK-side-panel UX (FR-016).

## Funnel (single column, top → bottom)

```
┌─ 前向搜尋 ──────────────────────────────┐
│ [boundary-missing banner]  (only if no base data) │
│                                                   │
│ 1. 縣市                                  [重設]    │  ← Reset clears the funnel
│   ┌───────────────────────────────────────────┐  │
│   │ 台中市 西區          (county chip)          │  │  ← pre-filled from map centre
│   └───────────────────────────────────────────┘  │
│   [所在地] [地圖中心] [清單…]   (56dp buttons)     │  ← 地圖中心/所在地 also re-anchor
│   (清單 → list of counties from data)             │     + auto-select the resolved 區
│                                                   │
│ 2. 鄉鎮市區            (3-column grid, ≥50dp)      │
│   [全部] [▶西區] [大甲區]   (▶ = own district;     │  ← 全部 = whole-county search
│   [南區] [北區]  …          全部 = no 區 filter)    │
│                                                   │
│ 3. 街道                                            │
│   [ 中山路              ] [搜尋]                    │  ← only stage that types
│                                                   │
│ [門牌號 / 巷弄 (optional)]                          │
│   ┌─ numeric keypad ─┐                            │
│   │ 1  2  3 │                                      │  ← 56dp keys, no system IME
│   │ 4  5  6 │                                      │
│   │ 7  8  9 │                                      │
│   │ 巷 0  弄 │                                      │  ← 巷/弄/號 narrow the address tail
│   │ 號 之 ⌫ │                                      │
│   └─────────┘                                      │
│                                                   │
│   ↗ 中山路一段1號 · 12 m    (candidate rows)        │  ← 16-pt compass arrow + distance;
│   ↘ 中山路一段5號 · 48 m                            │     TAP A ROW to pan the map
│   …                                               │
│   ┌───────────────────────────────────────────┐  │
│   │              前往 (GoTo)                     │  │  ← 60dp, re-pans the last pick
│   └───────────────────────────────────────────┘  │
└───────────────────────────────────────────────────┘
```

## Glove / sunlight rules applied

- **Single column**, fits the narrow DropDownReceiver panel (HALF_WIDTH).
- **Touch targets**: county-source + GoTo buttons 56–60dp; district / candidate
  rows ≥52dp; keypad keys 56dp. All ≥ the 48dp minimum.
- **Tap-only stages ① ②**: county and district need no keyboard; district is a
  3-column grid with a leading **全部** (whole-county) cell.
- **Numeric keypad** for the house-number / 巷弄 tail (digits + 之 + **巷/弄/號** +
  ⌫), never the system IME.
- **Tap-a-result-to-pan**: tapping a candidate row pans the map straight to it
  (no separate confirm step); the map never moves on a fuzzy/partial match before
  a row is tapped (FR-013). The 前往/GoTo button re-pans the last selection. Pan
  uses the same `CameraController.Programmatic.panTo` call as `TwCoordGotoView`
  (pan only — zoom is preserved).
- **Result arrow**: each row leads with a 16-point compass arrow from the current
  distance anchor to that address. The anchor is the map centre by default and is
  re-pointed by 地圖中心 / 所在地 (and on a cross-county map-follow settle).
- **Reset**: a 重設 button returns the funnel to the map-centre default.

## County sources (stage ①)

| Button | Source | Behaviour |
|---|---|---|
| 地圖中心 | map centre coord | **default seed** when it disagrees with 所在地 (FR-005) |
| 所在地 | self-marker GPS | one tap to switch to the operator's location |
| 清單… | manual list | counties **read from `townships.sqlite`** (FR-006), never hard-coded |

When the county comes from 所在地 / 地圖中心, the operator's own 鄉鎮市區 is
pre-highlighted (▶) in stage ② (FR-007).

## States

- **Boundary data absent** → banner "請先匯入 base 資料（townships.sqlite）以啟用
  前向搜尋"; the funnel is hidden (FR-017).
- **County detected, dataset not installed** → street search returns the
  "此縣市地址資料未安裝" prompt.
- **No street match in district** → "此鄉鎮市區查無符合的街道" empty-state; the
  operator can change district / fragment without leaving the page.
- **Offshore / outside all boundaries** → county not auto-selected; the operator
  picks from 清單.

## Address text + confidence

Candidate rows reuse the feature-004/005 `ConfidenceThresholds` tilde decorator
(`~` / `~~` prefixes) on the address text, with the distance appended
(`…路…號 · 12 m`), so the forward result reads consistently with the reverse
on-map readout (FR-018).

## i18n

All strings are externalised with en / zh-rTW / ja parity
(`fs_*`, `tool_forward_search_*`). zh-rTW uses Taiwan vocabulary (縣市,
鄉鎮市區, 門牌號, 前往, 公尺).

## Reverse-path note (no visible change)

This feature also makes the existing on-map address readout resolve the county
via the boundary layer first and query only that county's dataset. For points
inside an active county the displayed text is identical to before; when a
detected county has no installed dataset the row now shows the 縣市 + 鄉鎮市區
locality instead of going blank (FR-015).
