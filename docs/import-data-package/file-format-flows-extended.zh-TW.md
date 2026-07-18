# ATAK 檔案格式匯入流程 — 擴充篇（其餘格式）

> **本文回答的問題：** 這是 [`file-format-flows.md`](./file-format-flows.md) 的 **第 2 批（BATCH 2）** 姊妹文件。該文件涵蓋 `cot`、`datapackage`、`kml`、`kmz`、`image`、`grg`、`dted`、`iconset`、`layers` 與 `mvt`。本文件涵蓋從 ATAK-CIV `main.jar` resolver 清單列舉、但尚未分析的 **其餘 resolver**：**Shapefile**、**GeoJSON**、**GML**、**GPX**（軌跡 vs 路線）、**DRW**（FalconView 繪圖）、**LPT**（FalconView 點位）、**Tileset**、**SQLite DB**（依 schema 路由）+ **GeoPackage**、**Video**、**APK**、**憑證（`.p12`）**、**偏好設定（`.pref` XML/JSON）**、**替代聯絡人（`.csv`）**、**INFZ**（product-repo 快取）、**Support-info 套件**，以及 **TXT/XML**（依簽章路由的設定）。每個家族一節。
>
> **格式清單來源。** 本專案對 ATAK-CIV **5.7.0.5** `main.jar`（`com.atakmap.android.importfiles.sort.Import*Sort` 類別）的 resolver 列舉。
>
> **證據來源。** *權威（反組譯）bytecode：* ATAK-CIV **5.7.0.5** SDK `main.jar`（`<ATAK_SDK_5_7_0_5>/main.jar`）。*可閱讀的交叉參照：* 本機 upstream clone `TAK-Product-Center/atak-civ`，tag **5.5.1.10**，commit `9f6893dd657feacc35ec5de03dad721c2e44170e`。
>
> **日期：** 2026-06-17。

> ⚠️ **VERSION DRIFT — 請先讀這段。** 以下每一項行為主張的 **權威** 來源都是 **5.7.0.5** 反組譯 bytecode。**可閱讀** 的交叉參照（以及本文件中每一個 GitHub permalink）指向 **5.5.1.10** — 一條 *不同的發行線*。對本文所檢視的程式路徑，兩者在控制流程與方法契約上一致；若有差異（常數內聯、no-op 的副檔名附加分支、deprecation 標語），會在行內標注。**只要兩者有任何不一致，以 5.7.0.5 bytecode 為權威，bytecode 勝出。** Permalink 的 **行號僅對 5.5.1.10 有效** — upstream 尚未發佈 5.7.0.x 原始碼。
>
> **本文件分析的每一個 `*Sort` 類別都是 `@Deprecated` / `@DeprecatedApi(since="5.5", removeAt="5.8")`，** 由 `gov.tak.api.importfiles.Import*Resolver` 後繼者取代。**所有已棄用的 `*Sort` 類別在 5.7.0.5 bytecode 中仍然存在且完全可運作**，正是本文件所分析的對象（依指示）。後繼者在已驗證之處沿用相同的副檔名/目的地/MIME 契約。

> **姊妹文件。**
> - [`file-format-flows.md`](./file-format-flows.md) — 第 1 批：匯入路由框架、**四種衝突原型 (a)–(d)**（本文全篇以引用方式重複使用），以及前十種格式。
> - [`README.md`](./README.md) — 跨 Layer A（CoT 項目 UID）、Layer B（Data Package 容器、content-hash 去重）、Layer C（overlay 載荷檔名鍵）的同 UID 碰撞分析。下方的 **cert 實作範例** 依賴 README §3 的 Data Package 扇出（fan-out）。

> **四種衝突原型**（定義於 `file-format-flows.md`；本文以字母重複使用）：
> **(a)** 檔名 / 路徑就地覆寫 · **(b)** 目錄（catalog）/ DB-row 鍵（路徑冪等）· **(c)** UID 鍵取代 · **(d)** CoT 項目 UID 管線。
> 擴充格式新增了無法乾淨歸入 (a)–(d) 的行為 — 一個 **state-apply（套用狀態）** 家族（cert store、SharedPreferences、聯絡人偏好設定）與一個 **OS-handoff（交給 OS）** 家族（APK 安裝、product-repo 同步）。這些會在各節分別說明。

---

## 摘要表

| 格式 | 副檔名 | MIME（`contentType` / `mimeType`） | Resolver（`*Sort`） | 目的地 | 註冊 | 衝突鍵 | 再次匯入時 |
|---|---|---|---|---|---|---|---|
| **Shapefile** | `.shp` | `Shapefile` / `application/octet-stream` | `ImportSHPSort` | `overlays/<name>.shp` | OGR feature DB（`ShapefileSpatialDb`） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **Shapefile（zipped）** | `.zip` | `Shapefile` / `application/octet-stream` | `ImportSHPZSort` | `overlays/<name>.zip` | OGR feature DB（`ShapefileSpatialDb`） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **GeoJSON** | `.geojson` | `GeoJSON` / `application/octet-stream` | `ImportGeoJsonSort` | `overlays/<name>.geojson` | OGR feature DB（`GeoJSONSpatialDb`） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **GeoJSON（zipped）** | `.zip` | `GeoJSON` / `application/octet-stream` | `ImportGeoJsonZSort` | `overlays/<name>.zip` | OGR feature DB（`GeoJSONSpatialDb`） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **GML** | `.gml` | `GML` / `application/octet-stream` | `ImportGMLSort` | `overlays/<name>.gml` | OGR feature DB（`GMLSpatialDb`） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **GML（zipped）** | `.zip` | `GML` / `application/octet-stream` | `ImportGMLZSort` | `overlays/<name>.zip` | OGR feature DB（`GMLSpatialDb`） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **GPX（軌跡）** | `.gpx` | `GPX` / `application/gpx+xml` | `ImportGPXSort` | `overlays/<name>.gpx` | OGR feature DB（`GpxFileSpatialDb`） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **GPX（路線）** | `.gpx` | （`ROUTE_IMPORT` 廣播 — 無 `IMPORT_DATA`） | `ImportGPXRouteSort` | `overlays/<name>.gpx` | Route map-item 群組（`RouteMapReceiver`） | **route UID** | 檔案被覆寫，**但地圖上的路線被重複建立**（全新隨機 UID） |
| **DRW** | `.drw` | `DRW` / `application/x-msaccess` | `ImportDRWSort` | `overlays/<name>.drw` | OGR feature DB（`FalconViewSpatialDb`，`Main` 表） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **LPT** | `.lpt` | `LPT` / `application/x-msaccess` | `ImportLPTSort` | `overlays/<name>.lpt` | OGR feature DB（`FalconViewSpatialDb`，`Points` 表） | 檔名 **(a)** | 就地覆寫、重新匯入處理 |
| **Tileset** | `.zip` | `Tileset` / `application/zip` | `ImportTilesetSort` | `layers/<name>.zip` | 透過 **layer scanner** 的 raster catalog（`TilesetLayerScanner` → `PersistentRasterDataStore`） | 檔名 **(a)** + catalog **(b)** | 檔案被覆寫；catalog row 被更新（contains→remove→add），不重複 |
| **SQLite DB** | `.sqlite` | `SQLite Database` / `application/x-sqlite3` | `ImportSQLiteSort` | `Databases/<canonical>.sqlite`（依偵測到的 TYPE） | 具名的 ATAK DB（CoT / layers2 / spatial / iconsets / SSE） | 檔名 **(a)**（正規名） | 就地覆寫該正規 DB；來源檔名無關 |
| **GeoPackage** | `.gpkg` | （依內容路由） | `GeoPackageImportResolver`（圖磚另由 `ImportLayersSort`） | 圖磚 → `imagery/`；feature → feature DB | raster catalog / feature DB | 路徑 **(a)/(b)** | 覆寫 + catalog／feature 重新匯入 |
| **Video** | `mpeg mpg ts avi mp4 264 265 wmv mov webm mkv flv` | `Video` / `application/octet-stream` | `ImportVideoSort` | `tools/videos/<name>` | 影片庫（`VideoFileWatcher` 每 5 秒輪詢） | 檔名 **(a)** | 就地覆寫；library 條目被更新 |
| **APK** | `.apk` | `Android App` / `application/vnd.android.package-archive` | `ImportAPKSort` | `tmp/<name>.apk` | **交給 OS**（`AppMgmtUtils.install` → PackageInstaller） | 檔名 **(a)** | 暫存檔被覆寫；OS 提示安裝/更新 |
| **憑證** | `.p12` | `P12 Certificate` / `application/x-pkcs12` | `ImportCertSort` | 暫存 `cert/<name>.p12` → **cert store**（暫存檔被刪除） | 加密的 cert-store DB（+ `cot_streams` 綁定） | **cert-store slot** `type[+server+port]` | 靜默以最後寫入者為準寫入 slot；磁碟上不留殘留 |
| **偏好設定（XML）** | `.pref` | `ATAK Preferences` / `application/xml` | `ImportPrefSort` | `<config>/prefs/<name>`（已淨化的副產物） | Android `SharedPreferences`（逐鍵） | **pref-key** | 逐鍵覆寫（以最後寫入者為準）；write-once 鍵受保護；受 policy 把關 |
| **偏好設定（JSON）** | `.pref` | `ATAK Preferences` / `application/json` | `ImportJSONPrefSort` | `<config>/prefs/<name>` | Android `SharedPreferences`（逐鍵） | **pref-key** | 逐鍵覆寫（以最後寫入者為準） |
| **替代聯絡人** | `.csv` | `Contact Info` / `text/csv` | `ImportAlternateContactSort` | **無**（identity `getDestinationPath`；若位於 `atakdata/` 下則來源被安全刪除） | 本機裝置的替代聯絡人偏好設定（以 callsign 把關） | **pref-key**（逐欄位） | 逐欄位覆寫自身偏好設定；不保留檔案 |
| **INFZ** | `.infz` | `Product Repo Cache` / `application/zip` | `ImportINFZSort` | `<SUPPORT>/apks/custom/product.infz`（**固定檔名**） | product repo（`FileSystemProductProvider` + `ProductProviderManager.sync`） | 檔名（常數 `product.infz`）**(a)** | 就地覆寫 + 強制重新同步 |
| **Support info** | *（無 — 完全比對檔名）* `support.inf`、`atak_splash.png` | *（無 — `getContentMIME()==null`）* | `ImportSupportInfoSort` | `support/<name>` | **僅檔案系統**（無廣播 — MIME 為 null） | 檔名 **(a)** | 就地覆寫 |
| **TXT / XML 設定** | `.txt`、`.xml` | `TXT or XML File` / `application/xml` | `ImportTXTSort` | **依簽章路由** 的資料夾（依比對到的簽章） | 依內容路由的設定派送（geocoder / WFS / favorites / WMS / 僅複製） | 檔名 **(a)** | 就地覆寫 + 重跑後續動作 |

---

## Shapefile（`.shp` / zipped）

**它是什麼。** 一個 ESRI Shapefile 向量資料集——裸的 `.shp`（連同 `.dbf`/`.shx`/`.prj` 附屬檔），或打包 `.shp` 集合的 `.zip`。這是經典的 OGR 向量 overlay；GeoJSON／GML／DRW 家族都沿用它的 `SpatialDbContentSource` 模式（並重用其 `ic_shapefile` 圖示）。

**流程。**
- **match。** `ImportSHPSort` = `.shp` 副檔名閘門（`super.match`；之後僅記錄絕對路徑，**沒有額外內容嗅探**，故任何 `.shp` 都被接受）。`ImportSHPZSort` = `.zip` 副檔名閘門 **且** `HasSHP(File)`：開啟 `com.atakmap.util.zip.ZipFile`，於第一個小寫名稱以 `.shp` 結尾的 entry 回傳 true。
- **目的地。** 兩者皆 `super(…, FileSystemUtils.OVERLAYS_DIRECTORY, …)` → `ImportResolver.getDestinationPath` = `new File(getItem("overlays"), file.getName())` → `overlays/<name>.shp`（或 `.zip`）。
- **註冊。** `onFileSorted` 廣播 `IMPORT_DATA{contentType="Shapefile", mimeType="application/octet-stream"}`；`ShapefileSpatialDb`（`SHP_CONTENT_TYPE="Shapefile"`，一個 `OgrSpatialDb` / `SpatialDbContentSource`）以 OGR 解析該 shapefile 進 `FeatureDataStore2`，呈現為可切換的 overlay。
- **再次匯入——原型 (a)。** 固定 `overlays/<name>` 路徑；`copyFile`／`renameTo` 覆寫；`ShapefileSpatialDb` 重新匯入該路徑的 feature 集。不同檔名 = 各自獨立的 overlay；resolver 層無內容雜湊／feature 去重。

**證據。** `ImportSHPSort.<init>` `ldc ".shp"` / `getstatic FileSystemUtils.OVERLAYS_DIRECTORY`；`match` → `ImportResolver.match`（外加一個 `getAbsolutePath` log，無嗅探）。`ImportSHPZSort.<init>` `ldc ".zip"` / `OVERLAYS_DIRECTORY`；`match` → `ImportResolver.match` 後 `invokestatic HasSHP`（`ZipFile.entries` / `".shp"; String.endsWith`）。兩者 `getContentMIME` → `("Shapefile","application/octet-stream")`（`ShapefileSpatialDb.SHP_CONTENT_TYPE` / `SHP_FILE_MIME_TYPE`）。*（信心：高；目的地 + 衝突 經直接反組譯已確認——此家族的分析 agent 未回傳結構化輸出，故由人工反組譯補上。）*

**Clone（5.5.1.10）：** [`ImportSHPSort.java#L40`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSHPSort.java#L40) · [`#L46`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSHPSort.java#L46) · [`ImportSHPZSort.java#L36`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSHPZSort.java#L36) · [`#L42`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSHPZSort.java#L42) · [`ShapefileSpatialDb.java#L61`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/ShapefileSpatialDb.java#L61)。

---

## GeoJSON（`.geojson` / zipped）

**它是什麼。** OGR 為後端的 GeoJSON 向量 feature（純 `.geojson`），或一個含一個以上 `*.geojson` 條目的 `.zip`。兩者都繪製為一個 `GeoJSON` overlay 群組。

**流程。**
- **match。** `ImportGeoJsonSort` = 副檔名閘門 `.geojson` **AND** 一次內容窺探：透過 `BufferedReader.read(char[])` 讀取前 **2048 個字元**，並要求出現字面子字串 `FeatureCollection`（`GEOJSONMATCH`）。這是 **子字串嗅探，不是 JSON 解析** — 一個非 `FeatureCollection` 的 GeoJSON `Feature`/`Geometry` 會被拒，而任何開頭含有該字詞的檔案都會通過。`ImportGeoJsonZSort` = 副檔名閘門 `.zip` **AND** `HasGeoJSON(File)`：開啟 `com.atakmap.util.zip.ZipFile`，迭代條目，在第一個小寫化名稱以 `.geojson` 結尾的條目上回傳 true — **僅以名稱判定；不檢查壓縮檔內的內容。**
- **目的地。** 兩者都傳入 `super(…, "overlays", …)`；`ImportResolver.getDestinationPath` = `new File(FileSystemUtils.getItem("overlays"), file.getName())`。純檔 → `overlays/<name>.geojson`；zipped → `overlays/<name>.zip`。兩者同一目錄。
- **註冊。** `onFileSorted` 廣播 `IMPORT_DATA{contentType="GeoJSON", mimeType="application/octet-stream"}`；`GeoJSONSpatialDb`（`extends OgrSpatialDb extends SpatialDbContentSource implements Importer`）以 OGR 解析進 `FeatureDataStore2`（zipped 變體經由 `ZipVirtualFile` + 一個 `geojson-zipped` 資料來源）。
- **再次匯入時 — 原型 (a)。** `beginImport` 複製/改名到固定的 `overlays/<name>` 路徑（COPY 用 `copyFile`，MOVE 用 `renameTo`→`copyFile`），**覆寫** 任何同名檔案。無提示、無加後綴、無 content-hash/UID 去重。同名但不同內容會靜默取代前者並重新匯入處理；不同名則以第二個 overlay 共存。兩個 resolver 共用 `getContentMIME()`，所以都透過同一個 importer 匯入處理（顯示名稱 `GeoJSON` vs `Zipped GeoJSON` 僅外觀不同；`ic_shapefile` 圖示重複使用）。

**證據。** `javap -p -c`（5.7.0.5 `main.jar`）：`ImportGeoJsonSort.<init>` `ldc #1 ".geojson"` / `ldc #5 "overlays"` / `ldc #7 "GeoJSON"` → `ImportResolver.<init>(String,String,String,Drawable)`。`isGeoJSON`：`sipush 2048; newarray char` … `BufferedReader.read([C)` … `ldc #117 "FeatureCollection"; String.contains`。`ImportGeoJsonZSort.<init>` `ldc #1 ".zip"`；`match` `ImportResolver.match` 再 `invokestatic #31 HasGeoJSON`；`HasGeoJSON` `new ZipFile; ZipFile.entries; ldc #107 ".geojson"; String.endsWith`。`GeoJSONSpatialDb`：`GEOJSON_CONTENT_TYPE="GeoJSON"`、`GEOJSON_FILE_MIME_TYPE="application/octet-stream"`。`FileSystemUtils.OVERLAYS_DIRECTORY="overlays"`。*（信心：高；目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportGeoJsonSort.java#L38`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGeoJsonSort.java#L38) · [`#L78`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGeoJsonSort.java#L78) · [`ImportGeoJsonZSort.java#L35`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGeoJsonZSort.java#L35) · [`#L72`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGeoJsonZSort.java#L72) · [`GeoJSONSpatialDb.java#L61`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/GeoJSONSpatialDb.java#L61) · [`#L141`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/GeoJSONSpatialDb.java#L141)。

---

## GML（`.gml` / zipped）

**它是什麼。** OGR/GDAL 為後端的 GML 向量 feature（純 `.gml`），或一個含 `*.gml` 條目的 `.zip`（`Zipped GML`）。

**流程。**
- **match。** `ImportGMLSort` = 副檔名閘門 `.gml` **AND** `isGML(InputStream)`：讀取至多 **2048 個字元**，要求字面子字串 `<gml`。`ImportGMLZSort` = 副檔名閘門 `.zip` **AND** `HasGML(File)`：開啟 `com.atakmap.util.zip.ZipFile`，在第一個小寫化名稱以 `.gml` 結尾的條目上回傳 true。
- **目的地。** 兩者都傳入 `super(…, "overlays", …)` → `overlays/<name>`（`.gml` 或 `.zip`）。
- **註冊。** `onFileSorted` 廣播 `IMPORT_DATA{contentType="GML", mimeType="application/octet-stream"}`；`GMLSpatialDb`（`extends OgrSpatialDb`）由 `WktMapComponent` 建構並註冊（`addContentSource(new GMLSpatialDb(spatialDb))` → `ImporterManager.registerImporter(contentTypeImporter)`）。Zipped 變體經由同一個 content type / `ZIPPED_GML_DATA_SOURCE` 路由。觸發一個 `ZOOM_TO_FILE_ACTION`。
- **再次匯入時 — 原型 (a)。** 與 GeoJSON 相同：固定的 `overlays/<name>` 路徑，`copyFile`/`renameTo` 覆寫，重新廣播 → `GMLSpatialDb` 重新匯入處理。鍵是目的地檔名/路徑；content hash / feature identity 不起作用。

**證據。** `ImportGMLSort.<init>` `ldc #1 ".gml"` / `ldc #5 "overlays"` / `ldc #7 "GML"`；`match` → `isGML` `ldc #117 "<gml"; String.contains`；`getContentMIME` `ldc #7 "GML"` / `ldc #135 "application/octet-stream"`。`ImportGMLZSort.<init>` `ldc #1 ".zip"` … `"Zipped GML"`；`match` → `HasGML` `ldc #107 ".gml"; String.endsWith`。`ImportResolver.getDestinationPath` `getfield _folderName; FileSystemUtils.getItem; new File(File,String)`。`onFileSorted` `ldc_w #275 "com.atakmap.android.importexport.IMPORT_DATA"; AtakBroadcast.sendBroadcast`。**外觀瑕疵：** `ImportGMLSort` 的 `IOException` 日誌寫成 `"Error checking if GPX:"`（複製貼上殘留 — 無害；GML 路徑正確）。建構子的 4 個布林引數（`validateExt`/`copyFile`/`importInPlace`）被接受但未使用。*（信心：高；目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportGMLSort.java#L38`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGMLSort.java#L38) · [`ImportGMLZSort.java#L36`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGMLZSort.java#L36) · [`GMLSpatialDb.java#L61`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/GMLSpatialDb.java#L61) · [`WktMapComponent.java#L352`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/wkt/WktMapComponent.java#L352)。

---

## GPX — 軌跡 overlay vs GPX 作為路線

**它是什麼。** 單一 `.gpx` 檔案可依使用者/importer 選擇的 resolver **以兩種方式** 匯入：作為 **空間軌跡 overlay**（`ImportGPXSort`），或作為一組 `Route` map item 的 **路線**（`ImportGPXRouteSort`）。這是本文件中行為上最有趣的格式。

**消歧 並非 依內容判定。** `ImportGPXRouteSort extends ImportGPXSort` 且 **不覆寫 `match()`** — 兩者共用相同閘門：副檔名 `.gpx` **AND** `isGpx(InputStream)` 讀取前 **1024 個字元** 並要求子字串 `<gpx`（`GPXMATCH`）。bytecode 中 **沒有任何內容判別子** 來區分軌跡 GPX 與路線 GPX。路線變體僅在其圖示（`ic_route`）、顯示名稱（`gpx_route_file`），以及 — 關鍵地 — 其 **覆寫的 `onFileSorted`** 上不同。

**流程。**
- **目的地（兩者）。** `super(".gpx", "overlays", …)` → `overlays/<name>.gpx`。路線變體鏈結到受保護的 `ImportGPXSort` 建構子（`invokespecial ImportGPXSort.<init>(ZZZLjava/lang/String;)`），因此繼承 **相同** 的 `overlays` 資料夾。軌跡與路線的檔案複製目的地完全相同。
- **軌跡路徑（`ImportGPXSort`）。** 預設 `onFileSorted` 廣播 `IMPORT_DATA{contentType="GPX", mimeType="application/gpx+xml"}`；`GpxFileSpatialDb`（`extends OgrSpatialDb`）匯入處理至空間 feature DB 作為地圖 overlay。**再次匯入時 — 原型 (a)：** overlays 檔案就地覆寫；OGR importer 更新該檔的 feature set（依檔案路徑就地更新）。
- **路線路徑（`ImportGPXRouteSort`）。** 覆寫的 `onFileSorted` **完全略過 `IMPORT_DATA`**，廣播 `com.atakmap.android.maps.ROUTE_IMPORT`，其 `filename = src.toString()` — **原始來源路徑（複製前）**（不是 `overlays/` 的副本）。`RouteMapReceiver` 的 `ROUTE_IMPORT` 分支（tableswitch branch 13）執行 `ImportRouteTask(new File(sanitize(filename)))` → `RouteGpxIO.read` → `RouteGpxIO.toRoute`，每次匯入對每條路線鑄造 **`new Route(… UUID.randomUUID() …)`**，然後 `getRouteGroup().addItem(r)` + `persist`。**加入前沒有任何以路線名稱/UID 的查詢。**
- **再次匯入時 — 路線會重複（非 原型 (a)/(c)/(d)）。** 再次匯入同一個 GPX 路線檔會 **在地圖上新增第二條路線**（同標題、全新隨機 UID）。`overlays/` 的檔案副本仍會就地覆寫，但 **地圖上的路線被重複建立**，因為 live-object 的鍵是 route UID，而每次都鑄造一個新 UID。（注意：`RouteMapReceiver` 中 *確實* 存在一個 `deepFindUID` 去重，但在 **不同的** 分支 — branch 1，以 `routeUID`/`uid` extras 為鍵 — **不是** `ROUTE_IMPORT` 路徑。）

**證據。** `ImportGPXSort.<init>` `ldc #1 ".gpx"` / `ldc #5 "overlays"`；`match` → `isGpx` `ldc #132 "<gpx"; String.contains`；`getContentMIME` `ldc #150 "GPX"` / `ldc #152 "application/gpx+xml"`。`ImportGPXRouteSort.<init>` `getstatic R$string.gpx_route_file` / `invokespecial ImportGPXSort.<init>(ZZZLjava/lang/String;)`；`getIcon` `getstatic R$drawable.ic_route`；`onFileSorted` `ldc #38 "com.atakmap.android.maps.ROUTE_IMPORT"` / `ldc #43 "filename"` / 對 `src` 的 `File.toString` / `AtakBroadcast.sendBroadcast` — **無 `super.onFileSorted`、無 `IMPORT_DATA`。** `RouteMapReceiver` `ROUTE_IMPORT` tableswitch branch 13 → `ImportRouteTask(File)`；`ImportRouteTask.doInBackground` → `RouteGpxIO.toRoute`；`onPostExecute` `getRouteGroup().addItem(route)` + `route.persist(...)`，且 `addItem` 前 **沒有 `deepFindUID`/`getItemByName`/`findItem`**。`RouteGpxIO.toRoute` `invokestatic UUID.randomUUID + UUID.toString`。*（信心：高；經獨立重新反組譯 目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportGPXSort.java#L53`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGPXSort.java#L53) · [`ImportGPXRouteSort.java#L43`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGPXRouteSort.java#L43) · [`RouteMapReceiver.java#L882`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/routes/RouteMapReceiver.java#L882) · [`RouteGpxIO.java#L203`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/routes/RouteGpxIO.java#L203) · [`GpxFileSpatialDb.java#L18`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/GpxFileSpatialDb.java#L18)。

---

## DRW — FalconView 繪圖 overlay（`.drw`）

**它是什麼。** 一個以 **MS-Access / Jet 資料庫**（非原生 SQLite）儲存的 FalconView 繪圖，繪製為向量 overlay。

**流程。**
- **match。** 副檔名閘門 `.drw` **AND** `hasDrawing(File)`：`MsAccessDatabaseFactory.createDatabase(file)` 把 `.drw` 當成 MS-Access DB 開啟；若非 null 則執行 `query("select * from Main", null)` 並回傳 `cursor.moveToNext()`。因此一個檔案只有在 (1) 以 `.drw` 結尾、(2) 可作為 MS-Access DB 開啟、(3) `Main` 表至少有 1 列時才比對成功。*（注意：clone 的 javadoc 在第 49 行提到 "Points" 表，但 clone 第 69 行與 5.7.0.5 bytecode 常數都用 `Main` — 程式碼勝出。）*
- **目的地。** `super(".drw", "overlays", …)` → `overlays/<name>.drw`。`FalconViewSpatialDb.getFileDirectoryName()` 同樣回傳 `OVERLAYS_DIRECTORY`，所以 resolver 的落地目錄與 spatial-db 的掃描目錄一致。
- **註冊。** `onFileSorted` 廣播 `IMPORT_DATA{contentType="DRW", mimeType="application/x-msaccess"}`；`FalconViewSpatialDb`（`extends SpatialDbContentSource`，provider hint `falconview`）把 MS-Access 繪圖表解析進一個 `FeatureDataStore2`。
- **再次匯入時 — 原型 (a)。** 固定的 `overlays/<name>.drw` 路徑；`copyFile`/`renameTo` 覆寫；`FalconViewSpatialDb` 重新匯入處理，更新該檔的 feature。不同名 = 另一個 overlay。無提示/合併/content-hash。

**證據。** `ImportDRWSort.<init>` `ldc #1 ".drw"` / `ldc #5 "overlays"` / `getstatic R$string.drw_file` / `getstatic R$drawable.ic_falconview_drw`。`match` → `hasDrawing` `MsAccessDatabaseFactory.createDatabase` + `ldc #92 "select * from Main"` + `CursorIface.moveToNext`。`getContentMIME` `ldc #106 "DRW"` / `ldc #108 "application/x-msaccess"`。**Version-drift 小瑕（無行為變更）：** 5.7.0.5 的 `getDestinationPath` 有一個副檔名正規化分支（若 `getExt()` 非空且 `name` 尚未 `endsWith(getExt())` 則附加之），這在簡化的 clone 形式中不存在 — 但對 `.drw` 是 **no-op**，因為名稱已以 `.drw` 結尾，所以目的地/衝突語意不變。*（信心：高；目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportDRWSort.java#L34`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportDRWSort.java#L34) · [`#L66`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportDRWSort.java#L66) · [`#L81`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportDRWSort.java#L81) · [`FalconViewSpatialDb.java#L30`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/FalconViewSpatialDb.java#L30) · [`#L50`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/FalconViewSpatialDb.java#L50)。

---

## LPT — FalconView 點位 overlay（`.lpt`）

**它是什麼。** 一個 FalconView **點位（points）** 圖層，以 **MS-Access / Jet 資料庫** 儲存——是 DRW 的姊妹（`.drw` = 繪圖、`.lpt` = 點位）。兩者由同一個 `FalconViewSpatialDb` 處理，它把 `LPT` 與 `DRW` 定義為兩種 content type。

**流程。**
- **match。** `.lpt` 副檔名閘門 **且** `HasPoints(File)`：`MsAccessDatabaseFactory.createDatabase(file)` 以 MS-Access DB 開啟該 `.lpt`；非 null 時執行 `query("select * from Points", null)` 並回傳 `cursor.moveToNext()`。故僅當檔案 (1) 以 `.lpt` 結尾、(2) 能以 MS-Access DB 開啟、(3) `Points` 表非空時才符合。（對比 DRW 檢查的是 `Main` 表。）
- **目的地。** `super(".lpt", FileSystemUtils.OVERLAYS_DIRECTORY, …)` → `overlays/<name>.lpt`。即 `FalconViewSpatialDb` 掃描的同一個 `overlays/` 落點。
- **註冊。** `onFileSorted` 廣播 `IMPORT_DATA{contentType="LPT", mimeType="application/x-msaccess"}`（`getContentMIME` 回傳 `(FalconViewSpatialDb.LPT, FalconViewSpatialDb.MIME_TYPE)`）；`FalconViewSpatialDb` 把 MS-Access 點位表解析進 `FeatureDataStore2`，繪製為向量 overlay（地圖上的群組名為 `"LPT"`）。
- **再次匯入——原型 (a)。** 固定 `overlays/<name>.lpt` 路徑；`copyFile`／`renameTo` 覆寫；`FalconViewSpatialDb` 重新匯入。不同檔名 = 各自獨立的 overlay。無對話方塊／合併／內容雜湊。

**證據。** `ImportLPTSort.<init>` `ldc ".lpt"` / `getstatic FileSystemUtils.OVERLAYS_DIRECTORY` / `getstatic R$string.lpt_file`。`match` → `ImportResolver.match` 後 `invokestatic HasPoints`（`MsAccessDatabaseFactory.createDatabase` + `ldc "select * from Points"` + `CursorIface.moveToNext`）。`getContentMIME` → `getstatic FalconViewSpatialDb.LPT ("LPT")` / `FalconViewSpatialDb.MIME_TYPE ("application/x-msaccess")`。*（信心：高；目的地 + 衝突 經直接反組譯已確認——此家族的分析 agent 未回傳結構化輸出，故由人工反組譯補上。）*

**Clone（5.5.1.10）：** [`ImportLPTSort.java#L34`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportLPTSort.java#L34) · [`#L40`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportLPTSort.java#L40) · [`#L72`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportLPTSort.java#L72) · [`FalconViewSpatialDb.java#L30`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/FalconViewSpatialDb.java#L30)。

---

## Tileset（`.zip` → `layers/`）

**它是什麼。** 一個壓縮的圖磚影像資料集（raster），可由 `TilesetInfo.parse` 讀進一個 `DatasetDescriptor`。注意這是一個落地在 `layers/`、**而非** `overlays/` 的 **`.zip`**。

**流程。**
- **match。** 兩道閘門：(1) 副檔名閘門 `.zip`；(2) 關鍵內容嗅探 — `TilesetInfo.parse(file)` 必須產生一個 **非 null** 的 `DatasetDescriptor`（`IOException`/`IllegalStateException` 會被捕捉 → false）。一個 `TilesetInfo` 無法解析的一般 `.zip` 即使副檔名相符也會被拒。
- **目的地 — 原型 (b) 輸入。** `super(".zip", "layers", …)` → `getDestinationPath` = `FileSystemUtils.getItem("layers")/<name>.zip`。後繼者 `ImportTilesetResolver` 把這寫得很白：`super(".zip", FileSystemUtils.getItem("layers"), …)`。
- **註冊 — 經由 LAYER SCANNER，不是一對一的 content-type importer。** *誠實的告誡：* **沒有任何 `Importer` 註冊 `getContentType()=="Tileset"`。** `IMPORT_DATA{contentType="Tileset"}` 廣播沒有一對一的消費者（`LayersMapComponent` 的 `ExternalLayerDataImporter` 註冊的是 `IMPORTER_CONTENT_TYPE="External Native Data"`）。該成品實際上是透過 `TilesetLayerScanner` 上線 — 一個經由 SPI 註冊、以 `super("Tileset")` 建構、掃描 `getDefaultScanDirs("layers", true)` 的 `GenericLayerScanner`。`LayersMapComponent._initializeLayers()` 觸發 `ScanLayersService.START_SCAN_LAYER_ACTION`；scanner 發現 `layers/` 下的新檔，把它解析（`TilesetInfo`）進一個 `DatasetDescriptor`，並持久化進 **`PersistentRasterDataStore`** SQLite catalog；raster 圖層接著繪製。
- **再次匯入時 — 磁碟上 原型 (a) + catalog 中 (b)。** (1) 檔案層：`beginImport` → `mkdirs` → `copyFile`/`renameTo` 進 `layers/<name>.zip`，**覆寫** 任何同名前檔。(2) catalog 層：標準的 `ExternalLayerDataImporter.importData()` 模式是 `if (database.contains(file)) deleteData(...)` → `database.add(file,…)` — **remove-then-add 更新，無重複 row。** 所以再次匯入同一個 tileset = 同一檔被覆寫 **且** catalog row 被更新（不重複）；不同檔名則產生另一個 tileset 條目。

**證據。** `ImportTilesetSort.<init>` `ldc #1 ".zip"` / `ldc #3 "layers"` / `getstatic R$string.tileset` / `getstatic R$drawable.ic_menu_maps`。`match` `ImportResolver.match` → `invokestatic TilesetInfo.parse:(File)DatasetDescriptor` → `ifnull` → `iconst_1`/`iconst_0`；exception table 把 `IOException`+`IllegalStateException` → `iconst_0`。`getContentMIME` `ldc #48 "Tileset"` / `ldc #50 "application/zip"`。`beginImport` `getDestinationPath` → `IOProviderFactory.mkdirs` → `FileSystemUtils.copyFile` / `renameTo`。`TilesetLayerScanner` `super("Tileset")`、`getDefaultScanDirs("layers", true)`。*（信心：高；目的地 + 衝突 已確認。Doc-anchor 小瑕：catalog importer 是 `com.atakmap.android.layers.ExternalLayerDataImporter`，而 store 的具體型別是 `PersistentRasterDataStore`，繼承抽象的 `LocalRasterDataStore` — 兩者都不改變目的地/衝突行為。）*

**Clone（5.5.1.10）：** `ImportTilesetSort.java` L27-29 `super(".zip","layers",…)`；`ImportTilesetResolver.java#L24` `super(".zip", FileSystemUtils.getItem("layers"),…)`；`TilesetLayerScanner.java` L48 `super("Tileset")` / L94 `getDefaultScanDirs("layers", true)`；`LayersMapComponent.java` L153 `IMPORTER_CONTENT_TYPE="External Native Data"`、L484-486 `START_SCAN_LAYER_ACTION`、L821 `PersistentRasterDataStore`；`ExternalLayerDataImporter.java` L95-105 `contains`→`deleteData`→`add`。

---

## SQLite 資料庫（`.sqlite`）+ GeoPackage（`.gpkg`）

**它是什麼。** 一個依 schema 嗅探的 **分派器**，專門處理 ATAK 自家的 SQLite 資料庫——並非「開啟任意 sqlite」的通用路徑。`ImportSQLiteSort` 依 `.sqlite` 的 **表結構簽章** 分類，並把它安裝為 `Databases/` 下對應的正規 ATAK 資料庫。GeoPackage（`.gpkg`）則由 `GeoPackageImportResolver` 另行處理。

**流程。**
- **match——schema 分類。** `.sqlite` 副檔名閘門 **且** `getType(File) != null`。`getType` 開啟該 DB，將其表結構與六種 `TYPE` 簽章比對；回傳第一個相符者，否則 `null`（無法辨識的 sqlite **不被認領**）：

  | TYPE | 必要表 | 正規名 → 目的地 |
  |---|---|---|
  | `COT` | `spatial_ref_sys`、`CotEvent` | `Databases/cot.sqlite` |
  | `LAYERS2` | `layers`、`catalog`、`metadata` | `Databases/layers2.sqlite` |
  | `SSE` | `spatial_ref_sys`、`Entity`、`ReportRelationMap`、`Photo` | `Databases/sse.sqlite` |
  | `SITEEXPLOITATION` | （同 SSE） | `Databases/siteexploitation.sqlite` |
  | `SPATIAL` | `spatial_ref_sys`、`File`、`Geometry`、`Style` | `Databases/spatial.sqlite` |
  | `USERICONSET` | （iconset schema） | `Databases/iconsets.sqlite` |

- **目的地——每型別固定的正規檔名。** super 建構子傳入 `destinationDir = null`；被覆寫的 `getDestinationPath` 路由到 `FileSystemUtils.getItem(type._folder)`（六者皆 `_folder = "Databases"`），並改名為該型別的 **正規檔名**（`type._filename`）。故匯入一個被辨識為 SPATIAL 的 sqlite 會落在 `Databases/spatial.sqlite`，**與來源檔名無關**。
- **註冊。** 該檔案本身*就是*該子系統的在用 ATAK 資料庫（CoT 匯出 DB、`layers2` raster catalog、spatialite feature DB、iconset DB、site-exploitation）。它由擁有該正規 DB 的子系統就地消費——實際上是 **替換／還原某個具名內部資料庫**。
- **再次匯入——原型 (a)（落在正規檔名上）。** 由於目的地是固定正規檔名，再次匯入任何同 TYPE 的 sqlite 都會 **就地覆寫那唯一的正規 DB**（以最後寫入者為準）——來源檔名無關緊要。兩個不同的 SPATIAL sqlite 都指向 `Databases/spatial.sqlite` 而碰撞。
- **GeoPackage（`.gpkg`）。** 專屬的 `gov.tak.api.importfiles.GeoPackageImportResolver` 認領 `.gpkg`；GeoPackage 的 **圖磚（tile）** 內容 *也* 被 `ImportLayersSort` 認領並落在 `imagery/` 作為原生 raster（見 [`file-format-flows.md` → layers](./file-format-flows.md#layers)）。向量 feature 的 GeoPackage 則路由至 feature DB。*（專屬 resolver 的確切目的資料夾在此未確認——以第 1 批已驗證的 GeoPackage→`imagery/` raster 路徑為主要依據。）*

**證據。** `ImportSQLiteSort.<init>` `ldc ".sqlite"` / `aconst_null`（無固定資料夾）/ `ldc "SQLite Database"`。`match` → `ImportResolver.match` 後 `invokestatic getType`（回傳 `ImportSQLiteSort$TYPE` 或 null）。`TYPE` enum 常數 `COT`/`LAYERS2`/`SSE`/`SITEEXPLOITATION`/`SPATIAL`/`USERICONSET`，各為 `(正規檔名, 必要表[], "Databases")`。`getDestinationPath` 被覆寫 → `FileSystemUtils.getItem(type._folder)` + `type._filename`。`getContentMIME` → `("SQLite Database","application/x-sqlite3")`。`GeoPackageImportResolver` 建構子 `ldc "gpkg"`。*（信心：中高；ImportSQLiteSort 分派 + 正規檔名覆寫 經直接反組譯已確認——此家族的分析 agent 未回傳結構化輸出。GeoPackage 目的資料夾是唯一未完全確認的細節。）*

**Clone（5.5.1.10）：** [`ImportSQLiteSort.java#L38`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSQLiteSort.java#L38)（`enum TYPE`）· [`#L81`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSQLiteSort.java#L81)（`super(".sqlite", null, …)`）· [`#L211`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSQLiteSort.java#L211)（`getDestinationPath`）。

---

## Video（`.mp4`、`.ts`、`.mkv`、…）

**它是什麼。** 一個原始影片檔。會成為 ATAK 影片庫中的一個 live 別名。

**流程。**
- **match — 純副檔名閘門，無內容嗅探。** 當此 sorter 沒有單一副檔名覆寫時（`_ext==null`），`match` 把檔案的副檔名小寫化並檢查是否屬於 `VIDEO_EXTENSIONS` 集合。該集合由一個 13-slot 的 `String[]` 建立（`mpeg, mpg, ts, avi, mp4, 264, 265, wmv, mov, webm, mov, mkv, flv`）— `mov` 出現兩次（slot 8 與 10），所以 `HashSet` 持有 **12 個相異副檔名**；重複是無害的。**沒有 magic-byte 檢查**（原始碼甚至帶著 `// TODO: Check if the file is actually a video`）。
- **目的地。** super 建構子傳入 `destinationDir = null`；目的地完全由覆寫的 `getDestinationPath` 提供 = `new File(FileSystemUtils.getItem("tools/videos"), file.getName())` → `tools/videos/<name>`。
- **註冊 — 經由每 5 秒的資料夾輪詢，不是 `IMPORT_DATA` importer。** `beginImport` 把 `IMPORT_INPLACE` 改寫為 `IMPORT_COPY`，把檔案複製到 `tools/videos/<name>`，然後 **若原始來源路徑位於 `<cacheDir>/atakdata` 下則對其執行 `SECURE_DELETE`**（`IOProviderFactory.delete(file, IOProvider.SECURE_DELETE)` — 旗標值 `1`）。底層的 `IMPORT_DATA{contentType="Video"}` 廣播仍會發出，但沒有已註冊的 importer 消費它。改由 `VideoManager` 的 `VideoFileWatcher` **每 5 秒** 輪詢 `atak/tools/videos`，看到檔案，建立 `new ConnectionEntry(file)`，並 `addEntries(...)`，讓它出現在影片庫中。（同資料夾中的 `.xml` sidecar 由 `VideoXMLHandler` 解析。）
- **再次匯入時 — 原型 (a)。** 固定的 `tools/videos/<name>` 僅以來源檔名為鍵；`copyFile`/`renameTo` 無唯一化/提示 → **靜默覆寫**。兩個同名的不同影片會碰撞；第二個勝出。`VideoFileWatcher` 接著重新掃描被覆寫的路徑並更新 `ConnectionEntry`。

**證據。** `ImportVideoSort.<init>` `super(ext, null, getString(R$string.video), getDrawable(R$drawable.ic_video_alias))`（`destinationDir` 用 `aconst_null`）。`static{}` `bipush 13 / anewarray String` → 13 個 `ldc` → `Arrays.asList` → `new HashSet`。`match` `getfield _ext / ifnonnull` 否則 `FileSystemUtils.getExtension(file,Z,Z) / toLowerCase / getstatic VIDEO_EXTENSIONS / Set.contains`。`beginImport` 移除 `IMPORT_INPLACE` / 加入 `IMPORT_COPY` / `super.beginImport`；接著 `new File(getCacheDir(),"atakdata")` / `startsWith` / `IOProviderFactory.delete(file, 1)`。`getDestinationPath` `ldc #154 "tools/videos" / FileSystemUtils.getItem / new File(dir, getName())`。`getContentMIME` `new Pair("Video","application/octet-stream")`。*（信心：高；經獨立重新反組譯 目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportVideoSort.java#L57`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportVideoSort.java#L57)（副檔名 L36-39、`match` L57-76、`beginImport`+`getDestinationPath` L78-109）。**Drift（外觀）：** clone `getContentMIME` 回傳 `ResourceFile.UNKNOWN_MIME_TYPE`（= `application/octet-stream`）；clone 建構子用 `context.getDrawable`，bytecode 用 `getResources().getDrawable` — 值/行為相同；`copyFile` 建構子參數在兩者中皆為死碼（beginImport 強制 `IMPORT_COPY`）。

---

## APK — Android 安裝交接（`.apk`）

**它是什麼。** 一個 Android 應用程式套件。ATAK **不** 安裝它 — 它暫存到 `tmp/` 並交給 OS PackageInstaller。

**流程。**
- **match。** 副檔名閘門 `.apk` **AND** `isApk(file)` = `FileSystemUtils.ZipHasFile(file, "AndroidManifest.xml")` — 檔案必須是一個持有 `AndroidManifest.xml` 條目的 ZIP 容器。
- **目的地。** `super(".apk", FileSystemUtils.getItem("tmp"), …)` → `tmp/<name>.apk`（bytecode 內聯字面 `tmp`；clone 用 `FileSystemUtils.TMP_DIRECTORY`）。
- **註冊 — 交給 OS，不是 MapComponent importer。** `beginImport` 強制 `IMPORT_COPY`（移除 `IMPORT_MOVE`/`IMPORT_INPLACE`，加入 `IMPORT_COPY`），複製到 `tmp/<name>.apk`。覆寫的 `onFileSorted` 呼叫 `super.onFileSorted`（發出惰性的 `IMPORT_DATA` 廣播 — **沒有 MapComponent 消費這個 package-archive MIME**），然後 `AppMgmtUtils.install(context, file)` 透過 `startActivity` 啟動 `Intent(ACTION_VIEW)`，帶有一個 `FileProvider` `content://` URI + `application/vnd.android.package-archive` MIME（`FileProviderHelper.setDataAndType`、`FLAG_GRANT_READ_URI_PERMISSION`）。OS PackageInstaller 接著提示安裝/更新。（`install` 回傳 `true` 只代表安裝 Activity 被 *找到*，不代表安裝成功 — 程式內註解確認。）
- **再次匯入時 — 原型 (a)。** `getDestinationPath` 純以 `file.getName()` 為鍵且無唯一化；`copyFile` 覆寫同名暫存檔。嚴格來說匯入模式是 **複製**（非就地移動），但可觀察到的目的地檔案結果是 檔名 / 就地覆寫。

**證據。** `ImportAPKSort.<init>` `ldc #1 ".apk"` / `ldc #5 "tmp"` / `FileSystemUtils.getItem` → `ImportResolver.<init>(String,String,String,Drawable)`。`match` `ImportResolver.match` → `invokestatic #54 isApk`；`isApk` `ldc #129 "AndroidManifest.xml"` / `FileSystemUtils.ZipHasFile`。`getContentMIME` `ldc #122 "Android App"` / `ldc #124 "application/vnd.android.package-archive"`。`beginImport` `Set.remove`（IMPORT_MOVE/IMPORT_INPLACE）/ `Set.add`（IMPORT_COPY）/ `super.beginImport`。`onFileSorted` `super.onFileSorted` → `invokestatic AppMgmtUtils.install`。`AppMgmtUtils.install` `new Intent` / `ldc "android.intent.action.VIEW"` / `ldc "application/vnd.android.package-archive"` / `FileProviderHelper.setDataAndType` / `Context.startActivity`。*（信心：高；目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportAPKSort.java#L31`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAPKSort.java#L31) · [`#L41`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAPKSort.java#L41) · [`#L52`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAPKSort.java#L52) · [`ImportResolver.java#L327`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L327) · [`AppMgmtUtils.java#L75`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/update/AppMgmtUtils.java#L75) · [`FileProviderHelper.java#L30`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/util/FileProviderHelper.java#L30)。

---

## 憑證（`.p12`）

**它是什麼。** 一個 PKCS#12 keystore（client 憑證或 trust-store CA）。**live 成品是加密 cert-store DB 內的憑證 bytes**，不是磁碟上的檔案 — 這是一個 **state-apply** 格式，不是檔案 overlay。

**流程。**
- **match — 僅副檔名。** `ImportCertSort.match` 呼叫 `super.match` 並原樣回傳。**無內容嗅探** — 唯一的關鍵檢查是 `.p12` 小寫後綴。（clone 帶著一段 `//TODO look for magic numbers / KeyStore validate…` 註解；從未實作。建構子強制 `validateExt=true`，正是因為 `match()` 不做任何額外驗證，「否則此 sorter 會比對到所有東西」。）
- **目的地 — 先暫存再套用。** 兩步：(1) `super(".p12", "cert", …)` + `beginImport` 加入 `IMPORT_COPY`，所以 `.p12` 先被複製到 `cert/<name>.p12`（`getDestinationPath` = `FileSystemUtils.getItem("cert")/<name>`）。(2) **但暫存檔是暫時的：** `AtakCertificateDatabaseBase.importCertificate(location, server, type, deleteOriginal=true)` 讀取 `.p12` bytes（`FileSystemUtils.read`），把它們寫進 cert store，並 **刪除暫存檔**（`deleteOriginal=true` → `FileSystemUtils.deleteFile`）。最終結果：`cert/` 僅為暫存；最終歸宿是 cert store。
- **註冊。** 加密的 cert-store DB（+ 當存在 connect string 時的 `cot_streams` 網路連線綁定）。無 `connectString` → `saveCertificate(type, bytes)`；有則 → `saveCertificateForServerAndPort(type, host, port, bytes)`（host/port 經由 `NetConnectString` 解析）。
- **再次匯入時 — 以 cert-store SLOT 為鍵（state-apply，非 原型 (a)）。** 權威身分是 **cert-store slot**，以憑證 **TYPE**（以及可選的 **server + port**）為鍵：`TYPE_TRUST_STORE_CA` / `TYPE_CLIENT_CERTIFICATE`，當存在 connect string 時逐 server。再次匯入會 **取代** 該 slot 的 bytes — **靜默以最後寫入者為準、無重複 row、無提示**（store 為 `(type)`、`(type,server)`、`(type,server,port)` 提供 `save`/`get`/`delete` 三元組，確認此元組身分）。由於 `deleteOriginal=true`，磁碟上不留殘留以致下次碰撞。*（暫時的 `cert/<name>.p12` 暫存步驟本身是以檔名為鍵的覆寫，但成功時即被刪除，所以它不是實際的衝突單位。）*

**證據。** `ImportCertSort.<init>` `ldc #1 ".p12"` / `ldc #3 "cert"` / `ldc #7 "P12 Certificate"` → `ImportResolver.<init>(String,String,String,Drawable)`。`match` `invokespecial ImportResolver.match` → `ifne` → 回傳（僅副檔名閘門）。`beginImport` `getstatic SortFlags.IMPORT_COPY` / `Set.add` / `super.beginImport`。`getContentMIME` `new Pair("P12 Certificate","application/x-pkcs12")`。`AtakCertificateDatabaseBase.importCertificate` `FileSystemUtils.read` → bytes；`iload_3 ifeq` → `FileSystemUtils.deleteFile`；`saveCertificate(type,bytes)`（無 connectString）否則 `saveCertificateForServerAndPort(type,host,port,bytes)`。`AtakCertificateDatabaseIFace`：`TYPE_TRUST_STORE_CA="TRUST_STORE_CA"`、`TYPE_CLIENT_CERTIFICATE="CLIENT_CERTIFICATE"`。*（信心：高；目的地=cert-store + 衝突=cert-store-slot 已確認。對先前一項提示的更正：逐 server 的寫入是 `saveCertificateForServerAndPort(type,host,port,bytes)`，而非 `saveCertificateForServer(type,server,bytes)` — `connectString` 分支會解析 host/port 並呼叫 …`ForServerAndPort` 變體。）*

**Clone（5.5.1.10）：** [`ImportCertSort.java#L54`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCertSort.java#L54) · [`#L62`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCertSort.java#L62) · [`#L108`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCertSort.java#L108) · [`#L348`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCertSort.java#L348)。

### 實作範例 — `TAK_Server.zip`（TAK Server 註冊登錄）

一個裸 `.p12` 是簡單情形（如上）。真實世界的註冊登錄成品是一個 **Data Package**，不是裸憑證，並會運用 README §3 的容器扇出。

**該套件（`TAK_Server.zip`）。** `unzip -l` 顯示：
- `MANIFEST/manifest.xml` — 一個 `MissionPackageManifest version="2"`、`uid="5151d767-1f18-4beb-81ab-3656079d2389"`、`name="TAK_Server.zip"`、`onReceiveDelete="true"`、**3 個 Contents**。
- `certs/config.pref` — 一個帶有下列內容的偏好設定檔：
  - 一個 `cot_streams` 連線：`connectString0="tak.shihyu.dev:8089:ssl"`；
  - `com.atakmap.app_preferences` 鍵：`caLocation="cert/truststore-TAK-ID-CA-01.p12"`、`caPassword="atakatak"`、`certificateLocation="cert/shihyu.p12"`、`clientPassword="atakatak"`。
- `certs/shihyu.p12` — client 憑證。
- `certs/truststore-TAK-ID-CA-01.p12` — trust-store CA。

**複合流程。**
1. **容器。** `TAK_Server.zip` **不** 由 `ImportCertSort` 認領。它是一個 Mission Package（manifest v2）→ `ImportMissionPackageSort`（`getContentMIME` = `("Data Package","application/zip")`）。容器的 content-hash 去重 + 以 UID 為鍵的解壓縮目錄請見 **README §3**。
2. **扇出。** 解壓縮後，被打包的成員由 `ImportResolver` 框架匯入：
   - `config.pref` → **pref importer**（`ImportPrefSort` / `PreferenceControl.loadSettings`）把 `cot_streams` 伺服器連線 + `caLocation`/`certificateLocation`/密碼合併進 `SharedPreferences`（逐鍵 — 見下方偏好設定一節）。這就是綁定伺服器 `tak.shihyu.dev:8089:ssl` 的步驟。
   - 兩個 `.p12` 檔被安裝進 **cert store**，落在 pref 宣告的那些 `cert/...` 路徑 — `ImportCertSort.finalizeImport()` 的 `importCertificateFromPreferences` 正好讀取那些 `caLocation`/`certificateLocation`/`caPassword`/`clientPassword` 鍵，閉合迴圈。

   `config.pref` 的 `caLocation`/`certificateLocation` = `cert/...` 與 **裸 `.p12` 目的地完全一致** — 憑證安裝程式碼是共用的；唯一的差別是 `.pref`（以及隨之而來的伺服器綁定 + 憑證路徑）是隨套件抵達、還是早已在偏好設定中。

**對比。**

| 路徑 | 觸發 | Resolver | 發生什麼 |
|---|---|---|---|
| **裸 `.p12`** | 一個孤立的 `client.p12` | `ImportCertSort` | 暫存到 `cert/`，`importCertificate` 把 bytes 寫進 cert-store slot `(type[+server+port])`，暫存檔被刪除。**無伺服器綁定**（無 `.pref`）。 |
| **註冊登錄 `TAK_Server.zip`** | 一個 Data Package（manifest v2） | `ImportMissionPackageSort` → 扇出 | `config.pref` → SharedPreferences（伺服器連線 + 憑證位置）**且** 兩個 `.p12` → cert store。伺服器連線上線。 |

*（我未在 bytecode 中完整追蹤內部 MissionPackage extractor → `.pref` 派送類別鏈 — 見 README Layer-B 扇出 — 但 manifest Contents + `config.pref` 的 `caLocation`/`certificateLocation="cert/..."` 與裸 `.p12` 目的地對齊，且 `importCertificateFromPreferences` 正好讀取那些鍵。）*

---

## 偏好設定（`.pref` — XML / JSON）

**它是什麼。** 一個 ATAK 偏好設定文件，可為 **XML**（`ImportPrefSort`）或 **JSON**（`ImportJSONPrefSort`）。一個 **state-apply** 格式：實際作用是逐鍵合併進 Android `SharedPreferences`；磁碟上的副本是已淨化的副產物，不是系統的真實記錄來源。

**流程。**
- **match — 相同副檔名閘門，靠內容嗅探消歧。** 兩個建構子都 `super(".pref", PreferenceControl.DIRNAME, …)`，所以父類別的副檔名閘門相同（任何 `*.pref`）。各子類別接著做內容嗅探：
  - **XML（`ImportPrefSort`）** 讀取 `char[8192]` 並要求 `content.contains("<preferences")` **AND**（`content.contains("<preference key")` **OR** `content.contains("<entry key")`）。
  - **JSON（`ImportJSONPrefSort`）** 讀取 `char[64]` 並要求 `content.startsWith("{")` **AND** `content.contains("PreferenceControl")`（`JSONPreferenceControl.PREFERENCE_CONTROL`）。
  兩種嗅探互斥（XML 以 `<` 開頭，JSON 以 `{`），所以一個 `.pref` 恰好路由到一個 resolver；一個兩者都不符的格式錯誤 `.pref` 不被任何一方認領。*（XML 的 `isPreference` 還會在內文含有 `clientPassword`/`caPassword`/`certificateLocation`/`caLocation`/`networkMeshKey` 任一者時設定一個旁標旗標 `containsEntryToDelete`，供稍後憑證淨化用 — 不影響 `match()`。）*
- **目的地。** `getDestinationPath` = `new File(FileSystemUtils.getItem(PreferenceControl.DIRNAME), name)`，其中 `DIRNAME = CONFIG_DIRECTORY + "/prefs"`。`beginImport` 強制 `IMPORT_COPY` → 以名稱覆寫的複製，落在 `prefs/` 下。
- **註冊 — 逐鍵套用進 `SharedPreferences`。** `onFileSorted` 套用該檔：
  - **XML** 諮詢 `pref_import_pref_action` policy：`ALLOW` → 立即 `loadSettings`；`PROMPT`（`enterprise.pref` 永遠如此）→ AlertDialog，按 Yes 後載入；`DENY` → 略過。套用會走過每個 `<preference>`/`<entry key>` 並透過 `editor.putString/Boolean/Int/Float/Long/StringSet(key,value)` + `apply()` 寫入，並尊重 `WriteOncePreferences` 與重新對應舊版 baseline 名稱。
  - **JSON** 直接呼叫 `JSONPreferenceControl.getInstance().load(file, false)`（`ALLOW/PROMPT/DENY` + `WriteOncePreferences` 路徑是 **XML 專屬**）。
  - `ImportPrefSort.finalizeImport` 接著在載入那些憑證鍵 **之後** 把它們從磁碟副本中淨化掉。
- **再次匯入時 — 合併 / 逐鍵覆寫（非檔案層級去重）。** 再次匯入「同一個」`.pref` **不會** 依檔名或 hash 去重或略過：檔案以名稱覆寫 `prefs/` 中的前檔，且 **每個條目都透過 `putX(key,value)` 重新套用進 `SharedPreferences`，覆寫該鍵目前持有的任何值。** 衝突單位是 **個別偏好設定鍵**，不是檔案。跨檔案的相異鍵是累加的（聯集）；同一鍵兩次 → 以最後寫入者為準。一個防護：**已存在的 `WriteOncePreferences` 鍵會被略過**（write-once）。是否會載入則受 `pref_import_pref_action`（ALLOW/PROMPT/DENY）把關；`enterprise.pref` 永遠提示。一般的鍵碰撞靜默覆寫（無提示/重複對話方塊）。

**證據。** `ImportPrefSort.<init>` `ldc #7 ".pref"` / `getstatic PreferenceControl.DIRNAME` / `getstatic R$string.preference_file`；嗅探 `ldc #160 "<preferences"` / `ldc #166 "<preference key"` / `ldc #168 "<entry key"`（緩衝 `sipush 8192`）；`getContentMIME` `"ATAK Preferences"` + `HttpUtil.MIME_XML`。`ImportJSONPrefSort.<init>` `ldc #1 ".pref"` / `getstatic PreferenceControl.DIRNAME`；嗅探 `bipush 64` / `ldc #124 "{"` + `startsWith` / `ldc #132 "PreferenceControl"` + `contains`；`getContentMIME` `"ATAK Preferences"` + `application/json`；`onFileSorted` `JSONPreferenceControl.getInstance` / `load(File,Z)`。XML 套用：`ldc "pref_import_pref_action"` / `ldc "ALLOW"` / `PreferenceControl.getInstance` / `loadSettings(String,Z)`；`enterprise.pref` 分支 `ldc #252`。`PreferenceControl.loadSettings(Node,String,List)` `getSharedPreferences().edit()` → `Editor.putString/putBoolean/putInt/putFloat/putLong/putStringSet` → `apply()`；`WriteOncePreferences.contains(key) && SharedPreferences.contains(key)` → `ifeq`（略過）。`PreferenceControl.DIRNAME = CONFIG_DIRECTORY + "/prefs"`。*（信心：高；經獨立重新反組譯 目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportPrefSort.java#L78`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportPrefSort.java#L78) · [`#L150`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportPrefSort.java#L150) · [`ImportJSONPrefSort.java#L35`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportJSONPrefSort.java#L35) · [`PreferenceControl.java#L80`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/app/preferences/PreferenceControl.java#L80) · [`#L648`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/app/preferences/PreferenceControl.java#L648) · [`#L664`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/app/preferences/PreferenceControl.java#L664) · [`JSONPreferenceControl.java#L28`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/app/preferences/json/JSONPreferenceControl.java#L28)。

---

## 替代聯絡人（`.csv`）

**它是什麼。** 一個描述各 callsign 替代（頻外）聯絡資訊的 CSV — 電話旗標、VoIP SIP、email、XMPP。**不註冊任何 peer 聯絡人：** 它把 **一列符合 callsign 的資料套用到匯入裝置自己的** 替代聯絡人偏好設定。

**流程。**
- **match。** 副檔名閘門 `.csv` **AND** `isContact`：讀取前 **1024 個字元** 並要求字面標記 `::ALTERNATE CONTACT v2`（`CONTACT_MATCH`）。空內容 → false。
- **目的地 — 無（identity）。** `getDestinationPath` 被覆寫為 **no-op identity**：`aload_1; areturn`（回傳同一個 `File`）。**無受管資料夾複製。** `beginImport` 呼叫 `onFileSorted(file, file, flags)`（src 與 dst 同檔）。若來源位於 `<cacheDir>/atakdata` 下，套用後對其執行 `SECURE_DELETE`（`IOProviderFactory.delete(file, 1)`）。磁碟上不保留任何持久成品。
- **註冊。** `importContact` 讀取裝置自己的 callsign（小寫化）與 `AtakPreferences`，然後對每個非 `::` 的行以 `,` 切成恰好 **5 個欄位**；**只** 保留 callsign 與此裝置相符的那行，並把該行的電話旗標 / SIP / email / XMPP 寫進裝置自己的 `SharedPreferences`（`saHasPhoneNumber`、`saSipAddress`（+`saSipAddressAssignment=manual_entry`）、`saEmailAddress`、`saXmppUsername`）。空 / `NA` / `N/A` 欄位被略過（`IGNORE=["NA","N/A"]`）。其他 callsign 的列被靜默略過。`onFileSorted` 仍會廣播 `IMPORT_DATA{contentType="Contact Info", mimeType="text/csv"}`，但沒有 importer 消費它。
- **再次匯入時 — pref-key 逐欄位覆寫（state-apply）。** 再次匯入同一個 CSV 會透過 `prefs.set(key, value)` 重新套用符合的列，**無條件覆寫** 那四個鍵之每一個 — 無去重、無提示、無跨列合併。決定鍵是裝置自己的 **callsign**（決定哪一列套用）加上固定的 pref 鍵名（決定哪個值被覆寫）。空/NA 欄位讓既有偏好設定維持不動（選擇性逐欄位覆寫）。來源檔不被保留，所以沒有檔案層級碰撞。

**證據。** `ImportAlternateContactSort` 常數 `CONTACT_MATCH="::ALTERNATE CONTACT v2"`、`COMMENT="::"`、`SPLIT=","`、`IGNORE=[NA,N/A]`。`<init>` `ldc #1 ".csv"` + `ImportResolver.<init>(…,Drawable)`，`displayName` 為空。`getContentMIME` `new Pair("Contact Info","text/csv")`。`getDestinationPath` `aload_1; areturn`。`beginImport` `new File(getCacheDir(),"atakdata")` + `startsWith` + `IOProviderFactory.delete(file, 1)`；`onFileSorted(aload_1, aload_1, aload_2)`。`match` super.match + `isContact`（`::ALTERNATE CONTACT v2`）。`importContact` 對 `saHasPhoneNumber`/`saSipAddress`/`saEmailAddress`/`saXmppUsername` 的 `AtakPreferences.set`；`arraylength==5`；callsign `isEquals` 閘門。*（信心：高；經獨立重新反組譯 目的地=applied/not-stored + 衝突=pref-key 已確認。Anchored-fact 更正：`Contact Info` 是 ATAK 的 `contentType` 標籤，不是 IANA MIME — MIME 是 `text/csv`；且 NO 任何聯絡人被註冊進任何聯絡人清單/DB。）*

**Clone（5.5.1.10）：** [`ImportAlternateContactSort.java#L43`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L43) · [`#L111`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L111) · [`#L168`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L168) · [`#L194`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L194) · [`#L226`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L226)。**Drift（外觀）：** clone 使用 4-arg `(String,String,boolean,boolean)` super + `getIcon()=ic_menu_contact`；5.7.0.5 bytecode 呼叫 `(String,String,String,Drawable)` super 並傳入 `ic_csv` — 圖示不同，所有承載語意相同。

---

## INFZ — product-repo 快取（`.infz`）

**它是什麼。** 一個作為自包含 ATAK product / plugin **repo 快取** 的 ZIP（`application/zip`）。其定義比對的載荷是一個 `product.inf` CSV 索引。**一個交給 product repo 的匯入交接**，不是空間/feature DB 或 Android 安裝程式。

**流程。**
- **match。** 副檔名閘門 `.infz` **AND** `isRepoCache(file)`：`FileSystemUtils.GetZipFileString(zip, "product.inf")` 必須是一個 **非空** 條目，其文字 **含有逗號**（`,` = 至少一個 CSV repo 列）。所以 `match()` 為 true 的條件是 `.infz` 壓縮檔持有一個含逗號的非空 `product.inf`。
- **目的地 — 固定檔名。** `super(".infz", FileSystemProductProvider.LOCAL_REPO_PATH, …)`，其中 `LOCAL_REPO_PATH = AppMgmtUtils.APK_DIR + "/custom/"` 且 `APK_DIR = FileSystemUtils.SUPPORT_DIRECTORY + "/apks"`。覆寫的 `getDestinationPath` **取代 basename**，所以儲存的檔案 **永遠** 是 `<SUPPORT>/apks/custom/product.infz`（常數 `REPOZ_INDEX_FILENAME`），與來源檔名無關。
- **註冊 — 自我完成，不經由下游 `IMPORT_DATA` importer。** `onFileSorted` 呼叫 `super.onFileSorted`（資訊性 `IMPORT_DATA{contentType="Product Repo Cache"}`），然後 **刪除先前解出的 `product.inf`**（`FileSystemProductProvider.LOCAL_REPO_INDEX`）並觸發重新同步：`ApkUpdateComponent.getInstance().getProviderManager()` → `MapView.post(Runnable → providerManager.sync(false,false))`。`FileSystemProductProvider` 接著把 `apks/custom/` 當成 LOCAL repo，在 App Management 中呈現被打包的 products。
- **再次匯入時 — 對常數名稱的 原型 (a) + 強制重新同步。** 因為目的地檔名硬寫為 `product.infz`，兩個 **不同** 的來源 `.infz` 檔會在 **同一** 路徑碰撞。`copyFile`/`renameTo` **就地覆寫**，`onFileSorted` 刪除先前的 `product.inf` 索引並重新同步。無版本控制/合併/提示/去重。`conflictKey = filename`（常數 `product.infz`）。

**證據。** `ImportINFZSort.<init>` `ldc #1 ".infz"` / `getstatic FileSystemProductProvider.LOCAL_REPO_PATH` / `getstatic R$string.app_mgmt_product_repo` / `getstatic R$drawable.ic_menu_plugins`。`match` `ImportResolver.match` → `invokestatic isRepoCache`。`isRepoCache` `ldc #145 "product.inf"` / `FileSystemUtils.GetZipFileString` / `isEmpty` / `ldc #155 ","` / `String.contains`。`getDestinationPath` `ImportResolver.getDestinationPath` → `new File(dest.getParentFile(), ldc #84 "product.infz")`。`onFileSorted` `super.onFileSorted` / `getstatic LOCAL_REPO_INDEX` / `FileSystemUtils.getItem` / `isFile`→`FileSystemUtils.delete` / `MapView.post(ImportINFZSort$1 → providerManager.sync(false,false))`。`getContentMIME` `ldc #138 "Product Repo Cache"` / `ldc #140 "application/zip"`。`AppMgmtUtils.REPO_INDEX_FILENAME="product.inf"`、`REPOZ_INDEX_FILENAME="product.infz"`。*（信心：高；目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportINFZSort.java#L35`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportINFZSort.java#L35) · [`FileSystemProductProvider.java#L43`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/update/FileSystemProductProvider.java#L43) · [`AppMgmtUtils.java#L49`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/update/AppMgmtUtils.java#L49) · [`ImportResolver.java#L327`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L327)。

---

## Support info 套件（`support.inf` / `atak_splash.png`）

**它是什麼。** 一個小眾的診斷 + 品牌標識格式。只接受 **兩個硬寫的檔名** — `support.inf`（support-info 文字）與 `atak_splash.png`（自訂 app 啟動畫面影像）— 兩者都停放在 `support/`。**無 MIME、無 importer、無 DB/圖層：** resolver 的全部工作就是把那兩個指定檔案放進 `support/`。

**流程。**
- **match — 完全比對檔名，無副檔名閘門。** 建構子把一個 **空字串**（`ldc #1` = 空 Utf8）同時當成 ext 與 folder 引數傳入，所以 `_ext` 解析為 `null`，`FileFilter` **不施加副檔名閘門**（接受任何檔案）。關鍵檢查是 `getType(File) != null`：`for (TYPE t : TYPE.values()) if (t._filename.equalsIgnoreCase(file.getName())) return t;` — 對兩個 enum 常數做 **完全、不分大小寫的檔名相等比對**（`SUPPORTINF="support.inf"`、`SPLASH="atak_splash.png"`，兩者資料夾皆 `"support"`）。所以 `match()` 只有對字面上名為 `support.inf` 或 `atak_splash.png` 的檔案回傳 true（任何目錄位置皆可，因為沒有副檔名 filter）。
- **目的地。** `getDestinationPath` = `new File(FileSystemUtils.getItem("support"), file.getName())` → `support/<name>`（`SUPPORT_DIRECTORY="support"`）。
- **註冊 — 僅檔案系統。** `getContentMIME()` **未被覆寫** → 父類別回傳 `null`。底層 `onFileSorted` 檢查 `getContentMIME()`；null → 它 **完全略過 `IMPORT_DATA` 廣播**。所以檔案只是留在 `support/`（稍後由 app 為啟動畫面/診斷讀取），加上一個可選通知 + `ImportListener` 回呼。無 DB/圖層/cert/安裝程式/SharedPreferences 註冊。
- **再次匯入時 — 原型 (a)。** `getDestinationPath` 是純以（固定）檔名為鍵的固定路徑；`copyFile`/`renameTo` **就地覆寫**，取代任何前檔。因為那兩個 enum 檔名是常數，相異的 support 套件永不能共存 — 各名稱最近一次的匯入勝出。無提示/重複/合併。

**證據。** 外層 `<init>` `ldc #1`（空）同時作為 ext **與** folder / `ldc #3 "Support Info File"` / `ImportResolver.<init>(…,Drawable)`。`match` `ImportResolver.match` → `ifne` → `invokestatic getType` → `ifnull`（false）否則 true。`TYPE` `clinit` `ldc "support.inf"` / `ldc "support"` 與 `ldc "atak_splash.png"` / `ldc "support"`。`getDestinationPath` `invokestatic getType` / `getfield TYPE._folder` / `FileSystemUtils.getItem` / `new File(folder, getName())`。父類別 `getContentMIME` `aconst_null; areturn`。父類別 `onFileSorted` `getContentMIME` / `ifnull 206`（略過 `IMPORT_DATA` 建構）。`FileSystemUtils.SUPPORT_DIRECTORY="support"`。*（信心：高；目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** [`ImportSupportInfoSort.java#L50`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSupportInfoSort.java#L50) · [`#L66`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSupportInfoSort.java#L66) · [`#L96`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSupportInfoSort.java#L96) · [`ImportResolver.java#L359`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L359) · [`#L445`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L445)。

---

## TXT / XML 設定（`.txt`、`.xml`）

**它是什麼。** 一個 **依簽章路由** 的設定派送器 — 不是一般文字檢視器。註冊了兩個 `ImportTXTSort` 實例（每個副檔名一個）。一個 `.txt`/`.xml` 只有在其開頭符合 **五個 ATAK 設定簽章** 之一時才被認領，然後被路由到一個 **簽章專屬資料夾** 與一個簽章專屬的後續動作。

**流程。**
- **match。** 兩階段 AND。(1) 副檔名閘門：`super.match` → `_filter.accept` = `getName().toLowerCase().endsWith(_ext)`（`.txt` 或 `.xml`）。(2) 內容嗅探：`getType(fis) != null` — 讀取前 **1024 個字元** 並回傳第一個透過 `content.contains(t.signature)` 找到簽章的 `TxtType`。五個簽章：`<remoteResources`、`<NominatimProperties`、`<devices`、`takWfsConfig`（`XMLWFSSchemaHandler.WFS_CONFIG_ROOT`）、`::ATAK FAVORITES`（`FavoriteListAdapter.FAVS`）。一個都不符的 `.txt`/`.xml` 被拒（`t==null`）。
- **目的地 — 依簽章路由，不是單一固定資料夾。** super 建構子的 `folderName` 引數本身是 **空的**（`""`）。`getDestinationPath` = `FileSystemUtils.getItem(t.folder != null ? t.folder : "")` 再 `new File(folder, name)`（若名稱缺少副檔名則強制附加 `getExt()`）。各簽章資料夾：
  - `<remoteResources` → `ImportManagerView.XML_FOLDER`
  - `<NominatimProperties` → `GeocoderPreferenceFragment.ADDRESS_DIR`
  - `<devices` → `BluetoothDevicesConfig.DIRNAME`
  - `takWfsConfig` → 字面 `"wfs"`（`ATAK/wfs`）
  - `::ATAK FAVORITES` → `FavoriteListAdapter.DIRNAME`
- **註冊 — 依內容路由的設定派送。** 覆寫的 `onFileSorted`：先 `super.onFileSorted` 廣播 `IMPORT_DATA{contentType="TXT or XML File", mimeType="application/xml"}` 並通知各 `ImportListener`；**然後** 執行比對到的 `TxtType.action.doAction(dst)`：
  - geocoder action → `GeocoderPreferenceFragment.load(dst)`（把 Nominatim geocoder 屬性載入偏好設定）；
  - WFS action → 以 `WFSImporter.CONTENT`/`MIME_XML` 重新廣播 `IMPORT_DATA`（feature DB）；
  - favorites action → 廣播 `LayersManagerBroadcastReceiver.ACTION_ADD_FAV` + view 通知；
  - WMS action（歷史上用於 remoteResources 路徑）→ `LayersMapComponent` WMS 匯入；
  - `<remoteResources` 與 `<devices` 有 **null action**（僅複製 — 稍後由它們各自的 watcher/component 消費）。
- **再次匯入時 — 原型 (a) + 後續動作重跑。** `getDestinationPath` = `new File(signatureFolder, fileName)` 純以檔名為鍵（含強制副檔名）；`beginImport` 直接 `renameTo`/`copyFile` 到 dst，**無唯一化/版本控制/提示** → 就地覆寫。再次匯入同名設定會覆寫前檔 **並重跑後續動作**（重新載入 geocoder/favorites/WFS）。

**證據。** `ImportTXTSort.<init>` `ldc #19 ""`（空 folder 引數）/ `ldc #21 "TXT or XML File"` / `ImportResolver.<init>(String,String,String,Drawable)`；`addSignature` 呼叫註冊 `<remoteResources`→`XML_FOLDER`、`<NominatimProperties`→`ADDRESS_DIR`、`<devices`→`DIRNAME`、`takWfsConfig`→`"wfs"`、`::ATAK FAVORITES`→`FavoriteListAdapter.DIRNAME`。`match` super.match → `ifne` → `getType(...)` → `ireturn (t!=null)`。`getType` `newarray char[1024]` / `BufferedReader.read` / 迭代型別 / `String.contains(t.signature)`。`getDestinationPath` `getfield TxtType.folder` / `ldc #19 ""` fallback / `FileSystemUtils.getItem` / 強制附加 `getExt` / `new File(folder, name)`。`getContentMIME` `new Pair("TXT or XML File","application/xml")`。`onFileSorted` `TxtType.action.doAction(dest)`。`ImportFilesTask` 註冊 `new ImportTXTSort(context, ".xml"/".txt", …)`。**Anchored-fact 更正（bytecode 勝出）：** 先前一項提示說建構子 folder 是 `"TXT"` — bytecode 推翻它：super 的 `folderName` 引數是 **空字串** `""`，而真正的目的地是在 `getDestinationPath` 內依簽章決定。*（信心：高；經獨立重新反組譯 目的地 + 衝突 已確認。）*

**Clone（5.5.1.10）：** `ImportTXTSort.java` L102 `super(ext,"",CONTENT_TYPE,…)`、L106-113 `addSignature`、L118-132 `match`、L134-166 `getType`、L172-198 `getDestinationPath`；`ImportFilesTask.java` L274-275；`ImportResolver.java` L120-131（`_filter endsWith _ext`）、L246/259/271（`copyFile`/`renameTo`）、L365-375（`ACTION_IMPORT_DATA`）。

---

## 方法論與來源

**反組譯指令模式。** 每一項行為主張都透過反組譯權威的 5.7.0.5 SDK jar 驗證：

```
javap -p -c -classpath <ATAK_SDK_5_7_0_5>/main.jar \
  com.atakmap.android.importfiles.sort.ImportGeoJsonSort   # …每個類別
javap -p -constants -classpath …/main.jar \
  com.atakmap.coremap.filesystem.FileSystemUtils           # 取內聯的字串常數
```

對每個 resolver，承載的方法是 `<init>`（`super(ext, folder, displayName, drawable)` 引數固定了副檔名閘門與目的地資料夾）、`match(File)`（副檔名閘門 + 內容嗅探）、`getDestinationPath(File)`（目的地）、`beginImport(File,Set)`（複製/移動 + 旗標改寫）、`onFileSorted(...)`（廣播 / 交接），以及 `getContentMIME()`（`(contentType, mimeType)` 配對）。基底 `ImportResolver.getDestinationPath` = `new File(FileSystemUtils.getItem(_folderName), file.getName())`，除非被覆寫。

**Version drift。** 5.7.0.5 bytecode 是 **權威**；5.5.1.10 clone permalink 確認 *形狀與契約*，但其 **行號僅適用於 5.5.1.10**。觀察到的 drift 局限於：
- **常數內聯** — clone 參照具名常數（`FileSystemUtils.OVERLAYS_DIRECTORY`、`GMLSpatialDb.GML_CONTENT_TYPE`、`PreferenceControl.DIRNAME`、`FileSystemUtils.TMP_DIRECTORY`/`SUPPORT_DIRECTORY`、`REPOZ_INDEX_FILENAME`），5.7.0.5 將它們內聯為字面字串 — **值相同，無行為差異**。
- **DRW `getDestinationPath`** — 5.7.0.5 新增一個副檔名正規化分支，簡化的 clone 形式中沒有；它對 `.drw` 是 **no-op**（名稱已以 `.drw` 結尾），所以目的地/衝突語意不變。
- **外觀** — `ImportVideoSort` clone 用 `context.getDrawable` vs bytecode `getResources().getDrawable`；`ImportAlternateContactSort` clone 用 4-arg super + `getIcon()` vs bytecode `(String,String,String,Drawable)` + `ic_csv`；`ImportGMLSort` 的 `IOException` 日誌誤寫成 `"Error checking if GPX:"`。皆不影響行為。

**各格式裁定。** 全部十六個家族都以 **`destinationVerdict = confirmed`** 與 **`conflictVerdict = confirmed`** 在 **高信心** 下解決（SQLite/GeoPackage 為中高——GeoPackage 目的資料夾是唯一未確認的細節）。其中十三個由第 2 批 workflow 以獨立對抗式重新反組譯產出；**三個家族——Shapefile、LPT、SQLite+GeoPackage——其分析 agent 未回傳結構化輸出，事後由人工反組譯補上**（各節都標注此事）。折入上述各節的更正（非另立「uncertain」裁定）：

| 格式 | 更正（相對於先前一項提示；裁定仍為 confirmed） |
|---|---|
| **DRW** | clone javadoc 說「Points」表；clone 程式碼與 bytecode 都用 `select * from Main` — 程式碼勝出。5.7.0.5 在 `getDestinationPath` 新增一個 no-op 的副檔名附加分支。 |
| **Tileset** | 「Importer with content type `Tileset`」這個註冊提示是錯的 — **沒有這樣的 importer 註冊**；該成品透過 `TilesetLayerScanner` → `PersistentRasterDataStore` 上線。Catalog importer 類別是 `com.atakmap.android.layers.ExternalLayerDataImporter`。 |
| **APK** | 匯入模式嚴格來說是 **複製**（非就地移動）— `beginImport` 把旗標改寫為 `IMPORT_COPY`；可觀察到的目的地檔案結果仍是 檔名 / 就地覆寫。 |
| **Cert** | 逐 server 寫入是 `saveCertificateForServerAndPort(type,host,port,bytes)`，非 `saveCertificateForServer(type,server,bytes)`。SECURE_DELETE 旗標僅以字面 `1` 可見。 |
| **Contact** | `Contact Info` 是 `contentType` 標籤，不是 IANA MIME（MIME 是 `text/csv`）；且 **無任何聯絡人被註冊** 進任何聯絡人清單/DB — 它把比對到的列套用到匯入裝置自己的偏好設定。SECURE_DELETE 旗標僅以字面 `1` 可見。 |
| **偏好設定（JSON）** | JSON 變體經由 `JSONPreferenceControl.load(file,false)` 路由，且 **不** 使用 ALLOW/PROMPT/DENY + `WriteOncePreferences` 路徑（那些是 XML 專屬）。 |
| **TXT/XML** | 建構子 folder 是 **空字串** `""`，不是 `"TXT"`；真正的目的地是在 `getDestinationPath` 內 **依簽章** 決定。 |

**沒有任何格式帶有 `uncertain` 裁定。** 唯一被承認未反組譯的細節是不影響目的地/衝突的下游 library 端機制：`VideoFileWatcher`/`ConnectionEntry` 更新（Video），以及 cert 實作範例內部 MissionPackage-extractor → `.pref` 派送類別鏈（見 README Layer-B）。
