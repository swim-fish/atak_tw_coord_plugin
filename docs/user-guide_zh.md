# TW Coordinates Plugin — 使用手冊

本手冊說明目前的操作流程。台灣座標與地址輸入已整合進 ATAK 原生座標
對話框；此外掛在 Tools 選單只保留一個公開項目：**TW Coordinates**。

## 1. 安裝與確認

1. 安裝符合目標 ATAK 簽章版本的外掛 APK。
2. 開啟 ATAK，依提示啟用 **TW Coordinates**。
3. 在 **Settings → Plugins**（或 **TAK Package Mgmt**）確認狀態為
   **Loaded**。

升級通常可使用 `adb install -r`。已匯入的縣市資料集與目前設定會保留。
舊版自訂 GoTo 的 Recent、marker 與 icon 設定會被忽略，不會影響原生流程。

## 2. 原生 Taiwan 輸入

### 前往指定座標

1. 開啟 ATAK **Go To**，選擇 **Taiwan**。
2. 選擇 **Taipower（台電）**、**TWD97** 或 **TWD67**。
3. 輸入座標；TWD97/TWD67 另選 **121**（本島）或 **119**（外島）。
4. 按 ATAK 的 **OK**，由 ATAK 執行原生 Go To 動作。

台電座標接受本島 9 碼（10 m）與 11 碼（1 m）格式；TWD97/TWD67 使用
整數公尺 Easting 與 Northing。TWD67 zone 119 會顯示精度提醒；外島使用
台電座標時會顯示超出範圍。

### 前往離線地址

1. 先從 **TW Coordinates** 匯入適用縣市的資料集（第 4 節）。
2. 開啟 ATAK **Go To → Taiwan → Address**。
3. **完整地址**使用單一輸入框；切換成**結構化**後可分別輸入縣市、
   鄉鎮市區、道路／地名與其餘地址。
4. 輸入地址。常見的 `台`／`臺`、全形數字、空白、標點，以及地址單位前
   的中文數字會在裝置端正規化。
5. 唯一精確結果可直接交由 ATAK 確認；若有多筆可信候選，按**選擇結果**，
   比對縣市／行政區／道路資訊後選取正確資料，再執行確認。

完整與結構化模式共用同一份標準草稿，重複切換不會遺失尚未分類的文字。
選取候選本身不會移動地圖；只有 ATAK 原生確認才會執行動作。若尚未匯入
適用縣市，座標分頁仍可使用，Address 會顯示資料管理提示。

候選對話框最多顯示 20 筆。若有精確結果，只顯示精確結果；否則會混合
文字前綴、門牌數字接近、目前地圖中心距離與備援候選，去除重複資料後再
補滿剩餘名額。輸入未包含巷／弄時，直接位於道路上的門牌會排在巷弄資料
之前。

詳細範例請參閱[原生地址操作](tw-addr-search_zh.md)。

<p align="center">
<img src="images/23a-native-address-full.png" alt="ATAK Go To 的 Taiwan Address 單一完整地址欄位" width="900"><br>
<sub>單一欄位模式適合直接貼上完整地址，並維持 ATAK 原生 Go To 的緊湊配置。</sub>
</p>

<p align="center">
<img src="images/23b-native-address-structured.png" alt="ATAK Go To 的 Taiwan Address 結構化四欄位" width="900"><br>
<sub>結構化模式將同一份地址投影成縣市、行政區、道路與門牌四個可編輯欄位。</sub>
</p>

### 轉換地圖圖標的座標

1. 開啟圖標詳細資料，點選 **Coordinate** 座標值。
2. 在 **Convert Coordinate** 選擇 **Taiwan**。
3. 切換台電、TWD97、TWD67 與 Address。

三種座標會立即準備；Address 會以圖標的精確 WGS84 非同步查詢。即使畫面
顯示附近門牌，也不會用該門牌資料點取代或吸附 ATAK 原始位置。

<p align="center">
<img src="images/20-atak-point-detail-coordinate.jpg" alt="ATAK 圖標詳細資料中的 Coordinate 欄位" width="420"><br>
<sub>點選 Coordinate 開啟 Convert Coordinate。</sub>
</p>

<p align="center">
<img src="images/21-atak-convert-coordinate.jpg" alt="ATAK Convert Coordinate 中的 Taiwan 分頁" width="900"><br>
<sub>Taiwan 會出現在 ATAK 內建座標分頁旁。</sub>
</p>

### ATAK 控制項與唯讀流程

- **Auto Fill** 只填入目前分頁。Address 反查不會吸附或改變 ATAK 位置。
- **Clear** 只清除目前分頁；Address 啟用時也會取消進行中的查詢與候選。
- **Copy** 取得目前分頁的標準表示方式，不會移動地圖。
- 唯讀對話框仍可檢視解析結果，但輸入、模式／系統選擇與候選動作會停用。

## 3. 地圖讀值

外掛可顯示地圖中心（**MAP**）、自身位置（**ME**）與選取目標（**TGT**）
的座標列，格式可選台電、TWD97 或 TWD67。離線地址列可顯示附近地址、
方向箭頭，以及依設定門檻產生的 `~`／`~~` 信心標記。

點選座標讀值可複製完整顯示字串。台電座標在本島範圍外會顯示替代資訊；
zone 119 的 TWD 座標會明確標示，避免與 zone 121 混淆。

## 4. TW Coordinates 設定與資料集

開啟此外掛唯一的公開 Tools 項目 **TW Coordinates**，再按最上方的
**TW Coordinates 設定**，或直接前往：

**Settings → Tool Preferences → Specific Tool Preferences → TW Coordinates**

可調整：

- 地圖讀值格式與顯示／隱藏；
- MAP／ME／TGT 地址列與信心門檻；
- 原生 Address 候選排序；
- 外掛介面語言（系統、英文、正體中文、日文）；
- 資料集狀態與內部離線資料管理頁。

即使三個地址讀值開關都關閉，資料集狀態列仍可點選。點選後會先關閉
Settings，再立即開啟管理頁，不會讓管理頁被 Settings 畫面遮住。在管理頁
可匯入支援的 ZIP／SQLite、原子取代單一縣市、移除不需要的資料，以及檢視
容量、日期與筆數來源。詳見[離線地址資料](tw-offline-addr_zh.md)。

<p align="center">
<img src="images/24-offline-address-data.png" alt="含 TW Coordinates 設定按鈕與兩個縣市資料集的離線地址資料管理頁" width="700"><br>
<sub>Tools 項目會直接開啟此管理頁；使用頂端按鈕可繼續前往外掛設定。</sub>
</p>

## 5. 常見問題

**Tools 找不到外掛。** 確認外掛狀態為 Loaded。升級後請重新載入或重啟
ATAK，讓 ATAK 的快取不再顯示已移除的舊 Tools 項目。

**Address 顯示沒有相符資料集。** 開啟 **TW Coordinates**，匯入邊界資料與
適用縣市；其他座標輸入仍可正常使用。

**地址出現多筆結果。** 這是避免誤選的設計，不會默認採用模糊結果。請按
**選擇結果**並比對行政區資訊。這是依類別平衡且有上限的候選清單，不會
列出同一條大型道路的全部門牌。

**查詢需要網路嗎？** 不需要。外掛刻意不宣告 `INTERNET` 權限；座標轉換
與地址查詢都在裝置端完成。

**讀值顯示 `out of range`。** 台電網格僅支援本島；適用外島請改用
TWD97/TWD67 zone 119。

**回報問題：** <https://github.com/swim-fish/atak_tw_coord_plugin/issues>
