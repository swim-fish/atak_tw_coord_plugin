# V1 改版 — `ForwardSearchReceiver.java` 改動點

目標：把「鄉鎮市區整片 GridLayout」與「門牌數字鍵盤」改成 `AlertDialog` 跳出，
頁面只留 segmented（全部/指定鄉鎮）+ 一顆鄉鎮按鈕 + 一顆門牌欄位。

> **Dialog context 規則（重要，否則 `BadTokenException`）**
> `AlertDialog.Builder` 一律用 `getMapView().getContext()`（ATAK Activity，有 window token）；
> 但 **view 與字串資源用 `pluginContext`**（沿用 ADR-0003 的語系 context）。
> 與 `OfflineAddressReceiver`（L722/737/796/815）同一寫法。

---

## 1) import 新增

```java
import android.app.AlertDialog;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
// 已有：Gravity / Button / GridLayout / LinearLayout / TextView / View / ViewGroup
```

## 2) 欄位（fields）

**移除**：`districtList`、`districtAllCell`、`districtCells`、`houseValue`、`keypad`。

**新增**：

```java
private View scopeRow;
private RadioGroup scopeGroup;
private RadioButton scopeAll;
private RadioButton scopeSpecific;
private Button btnDistrict;
private Button houseField;

/** 目前選定的鄉鎮市區；null = 全部（whole-county）。 */
private String chosenDistrict;
```

`districtLabel` 保留（仍是階段標題）。

## 3) `inflate()` — 綁定 id

把原本的 districtList / houseValue / keypad 三行換成：

```java
districtLabel = view.findViewById(R.id.fs_stage_district_label);
scopeRow      = view.findViewById(R.id.fs_scope_row);
scopeGroup    = view.findViewById(R.id.fs_scope_group);
scopeAll      = view.findViewById(R.id.fs_scope_all);
scopeSpecific = view.findViewById(R.id.fs_scope_specific);
btnDistrict   = view.findViewById(R.id.fs_btn_district);
houseField    = view.findViewById(R.id.fs_house_field);
```

並 **移除 `inflate()` 結尾的 `buildKeypad();`**（鍵盤改成每次開 dialog 時建立）。
`buildKeypad()` 整個方法可刪除（其邏輯移到 `showHouseDialog()`）。

## 4) `wireStaticButtons()` — 加上三個監聽

```java
wireScopeListener();
btnDistrict.setOnClickListener(v -> safeRun(this::showDistrictDialog));
houseField.setOnClickListener(v -> safeRun(this::showHouseDialog));
```

並新增 helper（集中綁/解，避免程式設定 checked 時的 re-entrancy）：

```java
private void wireScopeListener() {
  scopeGroup.setOnCheckedChangeListener((g, id) -> safeRun(() -> onScopeChanged(id)));
}

private void onScopeChanged(int id) {
  if (controller == null) return;
  if (id == R.id.fs_scope_specific) {
    if (chosenDistrict == null) showDistrictDialog();   // 直接幫操作者開選單
    else applySpecific(chosenDistrict);
  } else {
    applyAll();
  }
}

/** 套用「全部」：免選鄉鎮、查整縣市。 */
private void applyAll() {
  chosenDistrict = null;
  btnDistrict.setEnabled(false);
  btnDistrict.setText(wholeCountyLabel());
  scopeGroup.setOnCheckedChangeListener(null);
  scopeGroup.check(R.id.fs_scope_all);
  wireScopeListener();
  onAllDistrictsChosen();        // controller.chooseAllDistricts() + revealStreetStage()
}

/** 套用指定鄉鎮。 */
private void applySpecific(String name) {
  chosenDistrict = name;
  btnDistrict.setEnabled(true);
  btnDistrict.setText(name);
  scopeGroup.setOnCheckedChangeListener(null);
  scopeGroup.check(R.id.fs_scope_specific);
  wireScopeListener();
  controller.chooseDistrict(name);
  revealStreetStage();
}

/** 「全部」按鈕的文案；可選擇帶出縣市名。 */
private String wholeCountyLabel() {
  String county = controller != null && controller.state() != null
      ? controller.state().county() : null;
  // 想顯示「整個台中市」可改成 county==null ? 預設字串 : "整個" + county;
  return pluginContext.getString(R.string.fs_district_whole_county);
}

private String safeCounty() {
  return controller != null && controller.state() != null && controller.state().county() != null
      ? controller.state().county() : "";
}
```

## 5) `onCountyChosen()` — 不再內嵌整片 grid

```java
private void onCountyChosen() {
  countyList.setVisibility(View.GONE);
  districtLabel.setVisibility(View.VISIBLE);
  scopeRow.setVisibility(View.VISIBLE);
  // 預設「全部」：選好縣市即可直接搜街道（操作者常不知道鄉鎮市區）。
  chosenDistrict = null;
  scopeGroup.setOnCheckedChangeListener(null);
  scopeGroup.check(R.id.fs_scope_all);
  wireScopeListener();
  btnDistrict.setEnabled(false);
  btnDistrict.setText(wholeCountyLabel());
  controller.chooseAllDistricts();
  revealStreetStage();
}
```

> `revealStreetStage()` 末段請把 `houseField.setVisibility(View.GONE)` 加進去
> （街道未搜尋前不顯示門牌欄位）。

## 6) `showDistrictDialog()` — 鄉鎮 AlertDialog（手套大格子）

```java
private void showDistrictDialog() {
  if (controller == null) return;
  java.util.List<String> districts = controller.districts();
  if (districts.isEmpty()) return;

  Context ui = pluginContext;                 // 資源/字串
  Context atak = getMapView().getContext();   // dialog window token
  float d = ui.getResources().getDisplayMetrics().density;

  GridLayout grid = new GridLayout(ui);
  grid.setColumnCount(3);
  int pad = (int) (8 * d);
  grid.setPadding(pad, pad, pad, pad);

  String suggested = controller.suggestedDistrict();
  TextView all = gridCell(ui.getString(R.string.fs_district_all), null);  // 既有 gridCell()
  grid.addView(all);
  for (String dd : districts) {
    TextView cell = gridCell((dd.equals(suggested) ? "▶ " : "") + dd, null);
    grid.addView(cell);
  }

  ScrollView sv = new ScrollView(ui);
  sv.addView(grid);

  final AlertDialog dlg = new AlertDialog.Builder(atak)
      .setTitle(ui.getString(R.string.fs_district_choose_title) + "（" + safeCounty() + "）")
      .setView(sv)
      .setNegativeButton(ui.getString(R.string.fs_cancel), null)
      .create();

  all.setOnClickListener(v -> safeRun(() -> { applyAll(); dlg.dismiss(); }));
  for (int i = 1; i < grid.getChildCount(); i++) {
    final String name = districts.get(i - 1);
    grid.getChildAt(i).setOnClickListener(v -> safeRun(() -> {
      applySpecific(name);
      dlg.dismiss();
    }));
  }
  dlg.show();
}
```

> `gridCell(text, null)` 會把 onClick 設成 no-op；上面再 `setOnClickListener` 覆蓋即可。
> 若鄉鎮很多想限制高度，給 `sv` 一個 `setLayoutParams`/`maxHeight`（約 `(int)(420*d)`）。
> 想要 dialog 內過濾框，可在 grid 上方加一個 `EditText`，`addTextChangedListener` 時
> `grid.removeAllViews()` 後重建符合的 cell（字串：`fs_filter_hint`）。

## 7) `showHouseDialog()` — 門牌數字鍵盤 AlertDialog

```java
private void showHouseDialog() {
  if (controller == null) return;
  Context ui = pluginContext;
  Context atak = getMapView().getContext();
  float d = ui.getResources().getDisplayMetrics().density;

  LinearLayout root = new LinearLayout(ui);
  root.setOrientation(LinearLayout.VERTICAL);
  int p = (int) (12 * d);
  root.setPadding(p, p, p, p);

  final TextView display = new TextView(ui);
  display.setTextSize(26f);
  display.setTextColor(0xFFFFFFFF);
  display.setMinHeight((int) (50 * d));
  display.setGravity(Gravity.CENTER_VERTICAL);
  display.setText(houseNumber.toString());
  root.addView(display);

  GridLayout grid = new GridLayout(ui);
  grid.setColumnCount(3);
  String[] keys = {"1","2","3","4","5","6","7","8","9","巷","0","弄","號","之","⌫"};
  for (String k : keys) {
    final String key = k;
    Button b = new Button(ui);
    b.setText(key);
    b.setTextSize(20f);
    b.setTextColor(0xFFFFFFFF);
    b.setBackgroundResource(R.drawable.fs_grid_cell_bg);
    b.setStateListAnimator(null);
    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
    lp.width = 0;
    lp.height = (int) (56 * d);
    lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
    int m = (int) (2 * d);
    lp.setMargins(m, m, m, m);
    b.setLayoutParams(lp);
    b.setOnClickListener(v -> safeRun(() -> {
      onKeypad(key);                          // 既有：更新 houseNumber + 重查結果
      display.setText(houseNumber.toString());
      reflectHouseField();
    }));
    grid.addView(b);
  }
  root.addView(grid);

  new AlertDialog.Builder(atak)
      .setTitle(ui.getString(R.string.fs_house_dialog_title))
      .setMessage(ui.getString(R.string.fs_house_dialog_subtitle))
      .setView(root)
      .setNeutralButton(ui.getString(R.string.fs_clear), (di, w) -> safeRun(() -> {
        houseNumber.setLength(0);
        reflectHouseField();
        renderCandidates(StreetCandidateRanker.reorder(
            new java.util.ArrayList<>(controller.withHouseNumber("", CANDIDATE_LIMIT)),
            currentOrdering(), lastFoldedFragment, ""));
      }))
      .setPositiveButton(ui.getString(R.string.fs_done), null)   // 完成只關閉
      .show();
}

/** 門牌欄位文案：空 → hint；有值 → 數字。 */
private void reflectHouseField() {
  houseField.setText(houseNumber.length() == 0
      ? pluginContext.getString(R.string.fs_house_hint) : houseNumber.toString());
}
```

## 8) `onKeypad(String k)` — 移除 houseValue 參照

刪掉這一行：`houseValue.setText(houseNumber.toString());`
其餘（`withHouseNumber` + `renderCandidates`）不變。顯示改由 dialog 的 `display` 與 `reflectHouseField()` 負責。

## 9) `runSearch()` — 改顯示門牌「欄位」而非鍵盤

把結尾兩行：

```java
houseValue.setVisibility(View.VISIBLE);
keypad.setVisibility(View.VISIBLE);
```

換成：

```java
houseNumber.setLength(0);
reflectHouseField();
houseField.setVisibility(View.VISIBLE);
```

（開頭原本的 `houseValue.setText("")` 一併刪除/改為 `reflectHouseField()`。）

## 10) `hideFromStage()` / `revealStreetStage()` — 換掉舊 id

所有 `districtList` / `houseValue` / `keypad` 的 visibility 設定，改成：

```java
scopeRow.setVisibility(View.GONE);
houseField.setVisibility(View.GONE);
// districtLabel 仍照舊
```

## 11) map-follow 自動選區（`chooseCountyFromCoord` / `autoSelectDistrict` / `selectAllDistrictsCell`）

舊版用 `districtCells` / `markSelected(districtList,…)`，改成：

```java
private void autoSelectDistrict(String district) {
  if (district != null && controller.districts().contains(district)) applySpecific(district);
  else applyAll();   // 座標落點無法解析鄉鎮 → 維持全部
}

private void selectAllDistrictsCell() {  // 若仍被呼叫
  applyAll();
}
```

`chooseCountyFromCoord(...)` 內 `onCountyChosen()` 之後的分支保留：
`wasAll ? applyAll() : autoSelectDistrict(loc.district())`。

---

## 受影響的測試
`ForwardSearchControllerTest` 測的是 controller（`chooseAllDistricts` / `chooseDistrict` /
`countyWideCalled`），這次只動 view/receiver，controller API 未變，**測試應全綠**。
若有 Espresso 直接點 `fs_district_list` 的 UI 測試，需改成點 `fs_btn_district` → dialog 內 cell。
