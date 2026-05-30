<!--
  End-user feature guide (zh-TW) for the Offline Address (reverse-geocode) feature.
  Icons in docs/images are rendered from the real Android vector drawables by
  scripts/render-doc-icons.py — re-run it if the drawables change.
-->
# TW Offline Addr（離線地址）— 功能介紹

> 地圖上的點，**離線**就能看出它的台灣門牌地址。不需網路、不需 Google。

**對應功能：** 004 離線地址 ｜ **適用：** ATAK-CIV 5.4.0 – 5.7.x

---

## 這個功能在做什麼？

<table>
<tr>
<td width="150" valign="top" align="center">
<img src="images/08c-tools-icon-offline-address.png" alt="TW Offline Addr 工具圖示" width="120"><br>
<sub>Tools 選單圖示<br>「TW Offline Addr」</sub>
</td>
<td valign="top">

把**座標 → 地址**。當你把地圖移到某個位置，外掛會自動查出那一點的**縣市 + 鄉鎮市區 + 路 + 門牌號**，直接顯示在畫面上的讀數列。

- ✅ **完全離線**：地址資料事先匯入裝置，野外沒訊號也能用。
- ✅ **三種點位**：地圖中心、我的位置、目標點，各自顯示地址。
- ✅ **台灣門牌格式**：例如「臺中市西區臺灣大道二段 100 號」。

> 它的反向兄弟功能是 **[TW Addr Search](tw-addr-search_zh.md)**（打字找地址 → 移動地圖）。

</td>
</tr>
</table>

---

## 使用流程一覽

```
①  匯入地址資料（每個縣市一個檔案，做一次就好）
        │
        ▼
②  把地圖移到想看的位置
        │
        ▼
③  讀數列自動顯示該點的地址
```

---

## 取得離線地圖資料包（`tw-central-full.zip`）

地址資料**不內建**在外掛裡（檔案太大），需自行取得並匯入一次。中部地區的資料打包成 **`tw-central-full.zip`**（涵蓋台中、彰化等縣市）。向資料提供者取得該 ZIP 後，照下一節匯入即可離線使用。

### 各檔案大小與匯入後佔用

ZIP 內含多個檔案，但**外掛只會用到縣市邊界與地址（places）檔**；OSM 地標與道路檔會自動略過、**不佔裝置空間**。

| ZIP 內檔案 | 內容 | 解壓大小（約） | 匯入後佔用裝置空間 |
|---|---|---:|:---:|
| `townships.sqlite` | 縣市邊界（**必要**） | 10 MB | ✅ 約 10 MB |
| `places-taichung.sqlite` | 台中市地址（約 73 萬筆） | 324 MB | ✅（若匯入台中） |
| `places-changhua.sqlite` | 彰化縣地址（約 43 萬筆） | 187 MB | ✅（若匯入彰化） |
| `places-osm.sqlite` | OSM 地標 | 162 MB | ❌ 略過 |
| `roads.sqlite` | 道路 | 24 MB | ❌ 略過 |
| **`tw-central-full.zip`** | 下載整包（壓縮後） | **約 149 MB** | 匯入後可刪除 |

### 匯入後需要多少空間？

只計算**邊界 + 你選擇匯入的縣市**：

| 你匯入的範圍 | 裝置佔用空間（約） |
|---|---:|
| 邊界 + 台中市 | **334 MB** |
| 邊界 + 彰化縣 | **197 MB** |
| 邊界 + 台中 + 彰化 | **521 MB** |

> 💡 **空間建議**：匯入過程會先解壓，請預留約「ZIP + 上表佔用」的暫時空間；**匯入完成後 ZIP 可以刪除**。不需要的縣市可隨時在頁面上「移除」釋放空間。

> 數字為實測值（中部資料建置版本），不同建置可能略有出入。

---

## ① 第一次使用：匯入地址資料

取得 `tw-central-full.zip` 後就能匯入。資料以 ZIP 或單一 `.sqlite` 提供，一個縣市一份。

1. 開啟 ATAK 的 **Tools 選單** → 點 **TW Offline Addr** 圖示 <img src="images/08c-tools-icon-offline-address.png" width="20" align="center">。
2. 第一次進入會看到 **空狀態**，畫面上有一個 **Import（匯入）** 按鈕。
3. 按下後用檔案瀏覽器選擇地址檔（`tw-central-full.zip` 或 `places-臺中市.sqlite` 之類）。
4. 匯入完成後，畫面會列出已啟用的縣市與資料日期。

```
┌──────────────────────────────┐
│  TW Offline Addr             │
│                              │
│  尚未匯入任何地址資料             │
│                              │
│        [  Import 匯入  ]       │  ← 第一次看到的畫面
└──────────────────────────────┘
              │ 匯入後
              ▼
┌──────────────────────────────┐
│  已啟用的縣市                    │
│  ┌────────────────────────┐   │
│  │ 臺中市   115-01         │   │
│  │            [取代] [移除] │   │
│  └────────────────────────┘   │
│        [  Import 匯入更多  ]    │
└──────────────────────────────┘
```

> 💡 可以匯入**多個縣市**，外掛會依地圖位置自動用對應縣市的資料。

---

## ② + ③ 在地圖上看地址

匯入後不用做任何事 — 把地圖移到任何已匯入縣市的範圍內，**讀數列**就會自動顯示地址。

<p align="center">
  <img src="images/12-tw-offline-addr-readout.jpg" alt="左下角即時地址讀數，右側為 TW Offline Addr 已匯入的縣市清單" width="760"><br>
  <sub>左下角即時顯示地圖中心的地址（<code>~ 台中市西區土庫里五權西路一段 2 號</code>）；右側 TW Offline Addr 頁面顯示已匯入 <b>台中市</b>（731005 筆）與 <b>彰化縣</b>（426690 筆）。</sub>
</p>

讀數列最多有三列，各對應一個點位，座標下方就是該點的**地址**：

```
中心  24.1469, 120.6839
      臺中市西區臺灣大道二段100號
我    24.1502, 120.6701
      臺中市西區美村路一段88號
```

- **中心**：地圖正中央那一點。
- **我**：你自己（self marker）的位置。
- **目標**：點選的目標點。

哪幾列要顯示，可在 **Settings → Tool Preferences → TW Coordinates** 開關。

<p align="center">
  <img src="images/13-tools-and-readouts.png" alt="地圖三處地址讀數，右側 Tools 選單可見本 plugin 四個工具" width="760"><br>
  <sub>地圖<b>左下／右上／右下</b>三處各自顯示地址讀數；右側 Tools 選單可見本 plugin 的四個工具：<b>TW Coordinates</b>、<b>TW Coord GoTo</b>、<b>TW Offline Addr</b>、<b>TW Addr Search</b>。</sub>
</p>

### 地址前面的 `~` 是什麼？

當最接近的門牌離查詢點**有點距離**時，地址前會加上波浪號提示你「這是附近的門牌，不是正上方」：

| 顯示 | 意思 |
|---|---|
| `臺中市…100號` | 就在門牌上（很準） |
| `~ 臺中市…100號` | 附近的門牌（稍有距離） |
| `~~ 臺中市…100號` | 較遠的門牌（參考用） |

---

## 管理已匯入的縣市

回到 **TW Offline Addr** 頁面，每個縣市一列：

- **取代**：用新版資料覆蓋該縣市（例如更新的資料日期）。
- **移除**：刪除該縣市的資料，釋放空間。

---

## 常見問題

**Q：讀數列沒有地址，只有座標？**
A：① 該位置所在縣市還沒匯入；② 該列在設定裡被關掉了。先確認有匯入對應縣市。

**Q：要連網嗎？**
A：不用。所有查詢都在裝置本機完成。

**Q：資料多大？**
A：一個縣市約 100–300 MB。可只匯入需要的縣市。

**Q：匯入後找不到資料 / 顯示「請先匯入」？**
A：請確認匯入時**沒有略過** `townships.sqlite`（縣市邊界檔），它是定位縣市的關鍵。重新匯入完整 ZIP 即可。

---

> 想反過來「打地址 → 跳到地圖位置」？請看 **[TW Addr Search 功能介紹](tw-addr-search_zh.md)**。
