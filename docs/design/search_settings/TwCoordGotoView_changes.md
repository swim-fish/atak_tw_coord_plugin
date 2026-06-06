# V1 改版 — `TwCoordGotoView.java` 改動點

目標：把 GoTo 輸入頁改成 V1「緊湊堆疊」版面，修掉六個痛點，**不動座標數學**
（`CoordinateParser` / `Projections` / `DatumShiftTwd67` / `CoordinateConverter` 全部不變），
也**不動 `onSubmit` / `submitOk` / `onAtakPicker` / Recent / 驗證流程**。

絕大多數是版面（`tw_coord_goto.xml`）與背景 drawable 的事；Java 只有四處需要動：
**§2 Auto Fill 三併一**、**§3 styleTab**、**§4 styleMarkerModeRadio**、**§5 主鈕文字色（選配）**。

> 沿用既有規則：所有 onClick 仍走 `safeClick(tag, runnable)`；資源/字串用 `localisedContext`，
> dialog/window token 用 `getMapView().getContext()`（本頁無新增 dialog）。

---

## 1) 隨附資源

**新 drawable**（放 `app/src/main/res/drawable/`）：
`goto_segment_track.xml`、`goto_tab_selected.xml`、`goto_input_bg.xml`、
`goto_zone_cell_bg.xml`、`goto_marker_cell_bg.xml`、`goto_autofill_bg.xml`、
`goto_advisory_bg.xml`、`goto_submit_primary_bg.xml`、`goto_submit_secondary_bg.xml`。

**字串**：見 `strings_additions_goto.xml`（`goto_marker_mode_header` 改「標點模式」、
`goto_btn_submit`/`goto_btn_autofill`/`goto_btn_atak_picker` 改文案、新增 `goto_taipower_help`）。

**marker 圖示**：`ic_marker_*` 沿用，不變。

---

## 2) Auto Fill 三併一（唯一結構性改動）

原本三個 per-pane 按鈕 `goto_autofill_taipower / _twd97 / _twd67` 合併為標題列單一
`goto_autofill`，行為依 `activeTab` 決定。

**欄位（fields）**：移除

```java
private Button autoFillTaipower;
private Button autoFillTwd97;
private Button autoFillTwd67;
```

新增

```java
private Button autoFill;
```

**`inflate()` 綁定**：把三行 `findViewById(R.id.goto_autofill_*)` 換成

```java
this.autoFill = root.findViewById(R.id.goto_autofill);
```

**`wireListeners()`**：三個 listener 換成一個（依 activeTab 分流）

```java
autoFill.setOnClickListener(
    v -> safeClick("autoFill", () -> onAutoFill(activeTab)));
```

**`refreshAutoFillEnabled()`**：改成只控一顆，依 activeTab 的可表示性

```java
private void refreshAutoFillEnabled() {
  boolean ok;
  switch (activeTab) {
    case TAIPOWER: ok = latestFix.taipowerOk(); break;
    case TWD97:    ok = latestFix.twd97Ok();    break;
    case TWD67:
    default:       ok = latestFix.twd67Ok();    break;
  }
  autoFill.setEnabled(ok);
}
```

> `setActiveTab()` 末端已呼叫 `refreshAutoFillEnabled()`，切分頁時按鈕的 enable 會跟著 activeTab 更新——
> 不需額外處理。`onAutoFill(...)` / `autoFill*FromFix(...)` / `onMapCenterFix(...)` 本體不變。
>
> **`refreshLocalisedStrings()`**：把三行 `autoFillTaipower/_twd97/_twd67.setText(...)`
> 換成一行 `autoFill.setText(c.getString(R.string.goto_btn_autofill));`

**（選配）行內停用原因提示**：目前停用時是「按下才跳 Toast」（`onAutoFill` 早退）。
若想改 FR-022 的行內提示，在標題列 autoFill 右側加一個 `goto_autofill_hint` TextView，
於 `refreshAutoFillEnabled()` 內依 `ok` 切 VISIBLE 並 `setText(`既有
`goto_autofill_hint_outside_taiwan` / `goto_autofill_hint_taipower_outer_island`）。
本版預設沿用既有 Toast，不新增此 TextView。

---

## 3) `styleTab()` — segmented 膠囊（取代純色塊）

分頁仍 `button="@null"` + 程式驅動選取態；只把「選取背景」從 `setBackgroundColor(0xFF333333)`
換成圓角膠囊 drawable，未選取為透明：

```java
private static void styleTab(RadioButton tab, boolean selected) {
  if (selected) {
    tab.setTextColor(0xFF1B1B1B);                 // 深字配淺膠囊
    tab.setTypeface(Typeface.DEFAULT_BOLD);
    tab.setBackgroundResource(R.drawable.goto_tab_selected);
  } else {
    tab.setTextColor(0xFFBFBFBF);
    tab.setTypeface(Typeface.DEFAULT);
    tab.setBackgroundColor(0x00000000);           // 透明，露出 segment_track 底
  }
}
```

> `applyTabVisibility()` 其餘不變（仍 setChecked + 切 pane 的 VISIBLE/GONE）。

---

## 4) `styleMarkerModeRadio()` — 改吃 state_checked 背景

標點格背景改用 `goto_marker_cell_bg`（state-list，已在 layout 的 `android:background` 指定），
選取上色交給 drawable 的 `state_checked`，Java 只要維持 `setChecked` 即可——
**移除** `setBackgroundColor`：

```java
private static void styleMarkerModeRadio(RadioButton btn, boolean selected) {
  btn.setChecked(selected);
  // 背景不再程式設定；goto_marker_cell_bg 的 state_checked 會自動上青框青底。
}
```

> `applyMarkerModeUI()` 不變（仍逐顆呼叫 `styleMarkerModeRadio` 做手動互斥）。
> 投影帶 121/119 在同一 RadioGroup，互斥自動處理，`goto_zone_cell_bg` 亦靠 state_checked 上色，
> 無需程式碼。

---

## 5) 主送出鈕停用字色（選配，純外觀）

`goto_btn_submit` 背景由 `goto_submit_primary_bg`（state-list）負責 enabled/disabled 換色，
文字色在 layout 設 `#06222E`。若希望停用時文字也轉灰，於 `refreshSubmitEnabled()` 末端補：

```java
submitButton.setTextColor(coordOk ? 0xFF06222E : 0xFF5F6B70);
```

`atakPickerButton` 維持既有 enable 規則（與 submit 同步），外觀走 `goto_submit_secondary_bg`，
文字色 `#BFBFBF`，不需 Java 改動。

---

## 受影響的測試
- 座標數學 / parser / controller 測試：**未動，全綠**。
- 若有 Espresso 直接點 `goto_autofill_taipower` / `_twd97` / `_twd67`：改點單一
  `goto_autofill`（會依當前分頁分流）。
- 標點選取、投影帶、送出 enable 的斷言若是看 `isChecked()` / `isEnabled()`：**不受影響**
  （仍由 setChecked / setEnabled 驅動，只是背景換 drawable）。
