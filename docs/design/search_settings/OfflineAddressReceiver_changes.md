# B 案 — `OfflineAddressReceiver.java` 改動點

把 State B 改成「容量總計 + 堆疊長條 + 圖例」+ 每縣市緊湊列（列動作收進 ⋮ `PopupMenu`）。
只動 view 渲染；importer / registry / batch 流程不變。

> Dialog / PopupMenu context：沿用既有規則 —— **anchor/Builder 用 `getMapView().getContext()`**，
> view 與字串用 `pluginContext`（與 L722+ 的 AlertDialog 一致）。

---

## 1) import 新增

```java
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
// 已有：View / Button / TextView / LinearLayout / LayoutInflater / Map
```

## 2) 欄位 + 綁定

```java
private TextView usageTotal;
private LinearLayout usageBar;
private LinearLayout usageLegend;
```

在 `inflate()`（findViewById 區）加：

```java
usageTotal  = view.findViewById(R.id.offline_address_usage_total);
usageBar    = view.findViewById(R.id.offline_address_usage_bar);
usageLegend = view.findViewById(R.id.offline_address_usage_legend);
```

## 3) 縣市色盤（bar 與列共用，靠 snap.values() 迭代順序對齊）

```java
private static final int[] OA_PALETTE = {
    0xFF33CCFF, 0xFF5BD6A8, 0xFFE0B341, 0xFFC98BE0, 0xFFEF8A6B, 0xFF7FA8FF
};
private int countyColor(int i) { return OA_PALETTE[i % OA_PALETTE.length]; }
private static final int OA_BOUNDARY_COLOR = 0xFF7C7C7C;
```

## 4) `bindStateBMultiCounty(snap)` — 改每列綁定 + 先畫總計

在方法開頭（顯示 stateB 後）呼叫：

```java
renderUsageBar(snap);
```

把 `for (CountyActiveDataset entry : snap.values()) { ... }` 內的綁定改成：

```java
int index = 0;
int count = snap.size();
for (CountyActiveDataset entry : snap.values()) {
  try {
    View row = inflater.inflate(R.layout.offline_address_county_row, countyList, false);
    TextView nameView = row.findViewById(R.id.offline_address_county_name);
    TextView subView  = row.findViewById(R.id.offline_address_county_summary); // 語意改為 date·rows
    TextView sizeView = row.findViewById(R.id.offline_address_county_size);
    View colorBar     = row.findViewById(R.id.offline_address_county_color);
    Button overflow   = row.findViewById(R.id.offline_address_county_overflow);
    View divider      = row.findViewById(R.id.offline_address_county_divider);

    GeneratorMetadata gm = entry.dataset().generator();
    final String county = entry.county();

    if (nameView != null) nameView.setText(nonNull(gm.county()));
    if (subView != null) {
      subView.setText(pluginContext.getString(
          R.string.pref_address_active_dataset_row_format,
          nonNull(gm.dataDate()),
          gm.insertedRows() >= 0 ? gm.insertedRows() : 0L));   // 「115-01 · 731005 筆」
    }
    long bytes = fileSystem != null
        ? fileSystem.sizeOfDirectory(fileSystem.activeCountyDir(county)) : 0L;
    if (sizeView != null) sizeView.setText(ByteCountFormatter.format(bytes));
    if (colorBar != null) colorBar.setBackgroundColor(countyColor(index));
    if (overflow != null) {
      overflow.setOnClickListener(v -> safeRun(() -> showCountyMenu(v, county)));
    }
    if (divider != null) divider.setVisibility(index == count - 1 ? View.GONE : View.VISIBLE);

    countyList.addView(row);
  } catch (Throwable t) {
    Log.w(TAG, "inflate county row " + entry.county() + " threw", t);
  }
  index++;
}
```

> 註：`pref_address_active_dataset_row_format` 原本就是「%1$s · %2$d 筆」，剛好當小字。
> 千分位想要逗號可改用 `String.format("%,d", rows)` 自行組字串。

## 5) `renderUsageBar(snap)` — 新方法（總計含 boundary）

```java
private void renderUsageBar(java.util.Map<String, CountyActiveDataset> snap) {
  if (usageBar == null || usageTotal == null) return;
  usageBar.removeAllViews();
  if (usageLegend != null) usageLegend.removeAllViews();

  long total = 0L;
  int i = 0;
  for (CountyActiveDataset e : snap.values()) {
    long bytes = fileSystem != null
        ? fileSystem.sizeOfDirectory(fileSystem.activeCountyDir(e.county())) : 0L;
    total += bytes;
    addBarSegment(bytes, countyColor(i));
    addLegend(countyColor(i), nonNull(e.dataset().generator().county()), bytes);
    i++;
  }
  // boundary 併入總計與長條（灰段）
  long boundary = 0L;
  if (fileSystem != null && fileSystem.exists(fileSystem.boundaryDbFile())) {
    boundary = fileSystem.sizeOfDirectory(fileSystem.boundaryDir());
  }
  if (boundary > 0) {
    total += boundary;
    addBarSegment(boundary, OA_BOUNDARY_COLOR);
    addLegend(OA_BOUNDARY_COLOR, "基礎資料", boundary);
  }
  usageBar.setClipToOutline(true);   // 讓 weighted 色段被圓角底軌裁切
  usageTotal.setText(pluginContext.getString(
      R.string.offline_address_total_disk_usage_format, ByteCountFormatter.format(total)));
}

private void addBarSegment(long weight, int color) {
  View seg = new View(pluginContext);
  LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
      0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(weight, 1L));
  seg.setLayoutParams(lp);
  seg.setBackgroundColor(color);
  usageBar.addView(seg);
}

private void addLegend(int color, String label, long bytes) {
  if (usageLegend == null) return;
  float d = pluginContext.getResources().getDisplayMetrics().density;

  LinearLayout item = new LinearLayout(pluginContext);
  item.setOrientation(LinearLayout.HORIZONTAL);
  item.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
  ip.setMargins(0, 0, (int) (14 * d), 0);
  item.setLayoutParams(ip);

  View dot = new View(pluginContext);
  GradientDrawable g = new GradientDrawable();
  g.setColor(color);
  g.setCornerRadius(2 * d);
  dot.setBackground(g);
  LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams((int) (9 * d), (int) (9 * d));
  dp.setMargins(0, 0, (int) (5 * d), 0);
  dot.setLayoutParams(dp);

  TextView tv = new TextView(pluginContext);
  tv.setText(label + " " + ByteCountFormatter.format(bytes));
  tv.setTextSize(12f);
  tv.setTextColor(0xFFBFBFBF);

  item.addView(dot);
  item.addView(tv);
  usageLegend.addView(item);
}
```

> 縣市很多時 legend 會超出寬度 → 把 layout 的 `offline_address_usage_legend`
> 包一層 `HorizontalScrollView`，或改用兩行。2–4 縣市維持單行即可。

## 6) `showCountyMenu(anchor, county)` — ⋮ 的 PopupMenu

```java
private void showCountyMenu(View anchor, String county) {
  PopupMenu pm = new PopupMenu(getMapView().getContext(), anchor);
  pm.getMenu().add(0, 1, 0, pluginContext.getString(R.string.offline_address_button_replace));
  // 移除 = 危險動作 → 紅字（PopupMenu 用 SpannableString 上色）
  SpannableString remove =
      new SpannableString(pluginContext.getString(R.string.offline_address_button_remove));
  remove.setSpan(new ForegroundColorSpan(0xFFFF6B6B), 0, remove.length(), 0);
  pm.getMenu().add(0, 2, 1, remove);
  pm.setOnMenuItemClickListener(it -> {
    safeRun(() -> {
      if (it.getItemId() == 1) confirmReplaceCounty(county);
      else confirmRemoveCounty(county);
    });
    return true;
  });
  pm.show();
}
```

`confirmReplaceCounty` / `confirmRemoveCounty` 已存在（含二次確認 AlertDialog），直接沿用。

## 7) `renderBoundaryRow()` — 維持，僅外觀

字串/邏輯不變（仍用 `offline_address_boundary_row_format`）。layout 已套 `oa_boundary_block_bg`
（虛線框）。若虛線在硬體加速下變實線，於 `inflate()` 後加一行：

```java
if (boundaryRowView != null) boundaryRowView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
```

> boundary 已計入頂部長條的灰段；此區塊保留作為「基礎資料」的明細與未安裝提示（FR-015）。

## 8) 匯入中卡片 + 進度條（升級 `showProgress` / `postProgress`）

**欄位 + 綁定**（`inflate()`）：

```java
private View progressCard;
private ProgressBar progressBar;
// progressView（%文字）沿用既有欄位
```
```java
progressCard = view.findViewById(R.id.offline_address_progress_card);
progressBar  = view.findViewById(R.id.offline_address_progress_bar);
// progressView = view.findViewById(R.id.offline_address_progress);  // 既有，不變
```

**`showProgress` / `hideProgress` 改切換卡片**（progressView 仍 setText）：

```java
private void showProgress(String text) {
  if (progressView != null) progressView.setText(text == null ? "" : text);
  if (progressCard != null) progressCard.setVisibility(View.VISIBLE);
}
private void hideProgress() {
  if (progressCard != null) progressCard.setVisibility(View.GONE);
}
```

**`postProgress` 設定進度條**（COPYING / BUILDING_INDEX 有 %，其餘不確定）：

```java
private void postProgress(
    AddressBundleImporter.ProgressListener.Stage stage, long completed, long total) {
  ui.post(() -> {
    try {
      showProgress(renderProgress(stage, completed, total));
      if (progressBar != null) {
        boolean determinate =
            stage == AddressBundleImporter.ProgressListener.Stage.COPYING
            || stage == AddressBundleImporter.ProgressListener.Stage.BUILDING_INDEX;
        progressBar.setIndeterminate(!determinate);
        if (determinate) {
          int pct = total > 0 ? (int) (completed * 100 / total) : 0;
          progressBar.setProgress(Math.max(0, Math.min(100, pct)));
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "postProgress threw", t);
    }
  });
}
```

> `renderProgress` / stage enum 不變。批次的 `showProgress(...)` 呼叫也會自動套用同一張卡。
> 單一匯入沒有 cancel API，故卡片不放「取消」；批次取消仍走既有 `取消本批` 流程。

## 9) 失敗紅 banner（升級 `showError` / `hideError` + 重選/關閉）

**欄位 + 綁定**：

```java
private View errorCard;
private Button errorRetry;
private Button errorDismiss;
// errorView（原因文字）沿用既有欄位
```
```java
errorCard    = view.findViewById(R.id.offline_address_error_card);
errorRetry   = view.findViewById(R.id.offline_address_error_retry);
errorDismiss = view.findViewById(R.id.offline_address_error_dismiss);
// errorView = view.findViewById(R.id.offline_address_error);  // 既有，不變
```

**切換卡片**：

```java
private void showError(String text) {
  if (errorView != null) errorView.setText(text == null ? "" : text);
  if (errorCard != null) errorCard.setVisibility(View.VISIBLE);
}
private void hideError() {
  if (errorCard != null) errorCard.setVisibility(View.GONE);
  if (errorView != null) errorView.setText("");
}
```

**按鈕接線**（放 `inflate()` 末或 wire 區）：

```java
if (errorRetry != null)
  errorRetry.setOnClickListener(v -> safeRun(() -> { hideError(); launchPicker(); }));
if (errorDismiss != null)
  errorDismiss.setOnClickListener(v -> safeRun(this::hideError));
```

> `launchPicker()` 為既有開檔流程；失敗時既有資料集保留不動（importer 失敗不替換），
> banner 下方仍照常顯示原本的縣市清單。

## 10) 其餘狀態
- **空**：`bindStateA()` 不變；boundary 顯示「未安裝」。
- **批次**：`renderInflight` / `renderEntryFinished` / `renderBatchSummary` 流程不變，
  進度文字現在會出現在新的匯入中卡片內。

## 受影響測試
view 綁定改動；controller/importer/registry API 未變。若有 Espresso 直接點
`offline_address_county_replace` / `_remove`，改成點 `offline_address_county_overflow`
再選 PopupMenu 項目（或保留舊 id 做為相容）。

## 需要的 drawable（隨附）
`oa_usage_card_bg.xml`、`oa_usage_track_bg.xml`、`oa_boundary_block_bg.xml`
→ 放 `app/src/main/res/drawable/`。
