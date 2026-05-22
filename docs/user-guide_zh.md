# TW Coordinates Plugin — 使用手冊

> **English version:** [user-guide.md](user-guide.md)

**對應版本：** v1.0.3 ｜ **對應 ATAK-CIV：** 5.4.0 — 5.7.x
**最新發行：** <https://github.com/swim-fish/atak_tw_coord_plugin/releases/latest>

這是精簡版。若你只是想把外掛裝起來用，看這份就夠了。需要更深入的背景知識（精度資訊、基準轉換內部原理、MIL-STD-2525 標記說明），請參閱 `docs/` 目錄下的其他原始文件。

---

## 目錄

1. [下載與安裝](#1-下載與安裝)
2. [確認載入成功](#2-確認載入成功)
3. [從 Tools 選單操作](#3-從-tools-選單操作)
4. [設定](#4-設定)
5. [常見問題](#5-常見問題)

---

## 1. 下載與安裝

<table>
<tr>
<td width="280" valign="top"><img src="images/01-package-mgmt-dialog.jpg" alt="TAK Package Mgmt 確認對話框" width="280"></td>
<td valign="top">

1. 到 [Releases 頁面](https://github.com/swim-fish/atak_tw_coord_plugin/releases/latest) 下載最新的 APK，檔名為 `ATAK-Plugin-TWCoord-vX.Y.Z-ATAK-5.4+.apk`。
2. 把 APK 側載到裝置上，二擇一：
   - 從工作站用 `adb install -r <apk>` 安裝，或
   - 把 APK 複製到裝置，再用檔案管理員點開。
3. ATAK 會跳出 **TAK Package Mgmt** 對話框，按下確認即可。

就這樣 — 沒有額外步驟，也不需要另外按「啟用」開關。外掛已在 ATAK 中生效。

> 從舊版升級嗎？直接 `adb install -r` 覆蓋即可。每個版本都使用相同的簽章憑證，Android 會保留你的設定與 Recent 清單。

</td>
</tr>
</table>

---

## 2. 確認載入成功

如果想在實際使用前先檢查：

- **Settings → Plugins**（或 **TAK Package Mgmt**）→ 找到 **TW Coordinates**，狀態應為 **Loaded**。
- 或直接打開 Tools 選單（下一節）。只要看到兩個 TW 開頭的項目，外掛就已運作中。

如果項目沒出現，強制停止 ATAK 後重開：

```
adb shell am force-stop com.atakmap.app.civ
```

---

## 3. 從 Tools 選單操作

<table>
<tr>
<td width="280" valign="top"><img src="images/08-tools-menu.jpg" alt="Tools 選單中的兩個 TW 項目" width="280"></td>
<td valign="top">

打開 ATAK 的 **Tools** 選單（右下角工具列按鈕，或從邊緣滑入）。外掛新增了 **兩個** 項目：

- <img src="images/08b-tools-icon-tw-coord-goto.png" alt="TW Coord GoTo 圖示" width="24"> **TW Coord GoTo** — 從右側滑出側面板，輸入台灣座標後地圖會跳過去（§3.1）。
- <img src="images/08a-tools-icon-tw-coord.png" alt="TW Coordinates 圖示" width="24"> **TW Coordinates** — 循環切換地圖讀值小工具的顯示單位：台電 → TWD97 → TWD67 → 關閉 → 台電 …（§3.2）。

</td>
</tr>
</table>

### 3.1 TW Coord GoTo — 跳到指定座標

<table>
<tr>
<td width="280" valign="top"><img src="images/09-coordinate-input-taipower.jpg" alt="座標輸入頁 — 台電分頁" width="280"></td>
<td valign="top">

按下 **TW Coord GoTo**，右側會滑出一個面板，有三個分頁：**Taipower（台電）**、**TWD97**、**TWD67**。

三個分頁的操作流程相同：

1. **輸入座標**（依各分頁的格式），或按 **Auto Fill** 把目前的地圖中心座標直接帶入欄位。
2. **（選用）挑選 Marker mode（標記模式）** — 以 2×4 網格排列的 8 種選項：
   - *Move only*（預設 — 只平移地圖，不放標記）
   - *Waypoint*、*GoTo Pin*、*Point of Interest*
   - *Friendly*、*Hostile*、*Neutral*、*Unknown*（MIL-STD-2525 標準配色）
3. **按 SUBMIT。** 地圖會平移到你輸入的座標。若 Marker mode 不是 *Move only*，會用 ATAK 原生的標記工具在該位置放一個標記 — 之後長按該標記即可從標準環形選單編輯、移動或刪除。

另一個放置按鈕：**OPEN ATAK ICON MENU** 會平移到座標後，把控制權交給 ATAK 原生的 Enter Location 面板，讓你從 ATAK 已安裝的任何 iconset / pallet 中選擇圖示。

每次成功送出都會記錄到 **Recent**（最多 10 筆，舊的先被擠掉）。點 Recent 列表的某一列可以把當時的輸入帶回欄位；點該列的 **×** 則只刪除那一筆。

</td>
</tr>
</table>

### 3.2 TW Coordinates — 地圖讀值小工具

<table>
<tr>
<td width="280" valign="top"><img src="images/07-map-readout-widget.jpg" alt="地圖讀值小工具" width="280"></td>
<td valign="top">

啟用讀值後，地圖右側邊緣會顯示兩行：

- **ME TPC: …** — 你目前所在位置
- **MAP TPC: …** — 目前的地圖中心位置

兩行都會依你切換到的座標系統來呈現。要完全關閉讀值，只要在 Tools 選單持續點擊 **TW Coordinates** 直到進入 *Off* 狀態即可。

</td>
</tr>
</table>

---

## 4. 設定

<table>
<tr>
<td width="280" valign="top"><img src="images/04-tw-coordinates-settings.jpg" alt="TW Coordinates 設定頁" width="280"></td>
<td valign="top">

開啟方式：ATAK → **Settings**（齒輪圖示）→ **Tool Preferences** → **Specific Tool Preferences** → **TW Coordinates**。

實際上你能調整的就只有 **兩項**，再加一個捷徑按鈕：

- **Display unit** — 決定地圖讀值小工具使用的座標系統：台電 / TWD97 / TWD67。效果等同於從 Tools 選單循環切換（§3.2）。
- **UI language** — 強制此外掛的介面字串使用 *系統語系* / *英文* / *中文（正體）* / *日文*。只影響本外掛，不會動到 ATAK 其他部分。
- **Open Coordinate Input** *(按鈕)* — 等同於 Tools → TW Coord GoTo 的捷徑。

頁面下方還有一塊唯讀的 **Accuracy notice（精度說明）**，整理了誤差範圍（TWD97< 1 m、TWD67 本島 ±3–5 m / 外島 ±10–20 m、台電網格僅支援本島）。純參考資訊，不需要操作。

</td>
</tr>
</table>

---

## 5. 常見問題

**Q：Tools 選單裡沒看到外掛？**
到 **Settings → Plugins** 確認狀態是 *Loaded*。若不是，先強制停止 ATAK（`adb shell am force-stop com.atakmap.app.civ`）再重開。若還是消失，把 APK 移除後重裝。

**Q：升級時需要先移除舊版嗎？**
不需要，用 `adb install -r` 直接覆蓋即可。每個版本使用相同簽章憑證，設定與 Recent 紀錄都會保留。

**Q：讀值顯示 `out of range`？**
代表你目前選的是 *台電網格*，但地圖中心落在外島（澎湖 / 金門 / 馬祖），那裡不在台電網格涵蓋範圍內。改用 TWD97 或 TWD67 即可。

**Q：怎麼刪掉透過 SUBMIT 放下的標記？**
長按該標記 → ATAK 標準環形選單 → 垃圾桶圖示。這是 ATAK 原生行為，外掛沒有額外客製。

**Q：要在哪裡看建置 / 簽章 / 資安掃描的證據？**
每個 GitHub Release 都會附上 R8 mapping 檔、Fortify SAST 報告 PDF、OWASP dependency-check HTML 報告、以及提交給 TAK TPP 的原始碼壓縮檔。

---

**回報問題：** <https://github.com/swim-fish/atak_tw_coord_plugin/issues>
**版本列表：** <https://github.com/swim-fish/atak_tw_coord_plugin/releases>
