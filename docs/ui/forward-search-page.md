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
│ 1. 縣市                                            │
│   ┌───────────────────────────────────────────┐  │
│   │ 台中市 西區          (confirm chip)         │  │  ← pre-filled from map centre
│   └───────────────────────────────────────────┘  │
│   [所在地] [地圖中心] [清單…]   (56dp buttons)     │
│   (清單 → vertical list of counties from data)    │
│                                                   │
│ 2. 鄉鎮市區                                        │
│   ▶ 西區   (▶ = operator's own district)          │  ← tap-only chips, ≥52dp rows
│   大甲區                                           │
│   …                                               │
│                                                   │
│ 3. 街道                                            │
│   [ 中山路              ] [搜尋]                    │  ← only stage that types
│                                                   │
│ [門牌號 (optional)]                                │
│   ┌─ numeric keypad ─┐                            │
│   │ 1  2  3 │                                      │  ← 56dp keys, no system IME
│   │ 4  5  6 │                                      │
│   │ 7  8  9 │                                      │
│   │ 之 0  ⌫ │                                      │
│   └─────────┘                                      │
│                                                   │
│   中山路一段1號 · 12 m      (candidate rows)        │  ← distance-ranked, tap to select
│   中山路一段5號 · 48 m                              │
│   …                                               │
│   ┌───────────────────────────────────────────┐  │
│   │              前往 (GoTo)                     │  │  ← 60dp, enabled after a pick
│   └───────────────────────────────────────────┘  │
└───────────────────────────────────────────────────┘
```

## Glove / sunlight rules applied

- **Single column**, fits the narrow DropDownReceiver panel (HALF_WIDTH).
- **Touch targets**: county-source + GoTo buttons 56–60dp; district / candidate
  rows ≥52dp; keypad keys 56dp. All ≥ the 48dp minimum.
- **Tap-only stages ① ②**: county and district need no keyboard.
- **Numeric keypad** for the house number (digits + 之 + ⌫), never the system IME.
- **Confirm-before-GoTo**: a candidate must be tapped (enabling 前往) before any
  pan; the map never moves on a fuzzy match (FR-013). GoTo uses the same
  `CameraController.Programmatic.panTo` call as `TwCoordGotoView`.

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
