# ATAK 檔案格式匯入流程——各檔案型別走哪一條管線

> **本文回答的問題：** 當你把檔案拖入／匯入 ATAK 時，哪個 resolver 會認領它、它落在磁碟的哪個位置、由哪個子系統註冊它，以及重新匯入時會發生什麼（覆寫、跳過、產生重複）？每種檔案格式各一節，另附一張統整比較表。
>
> **格式清單來源。** HackMD 筆記《TAK Mission Package Manifest — File Formats and Import Overview》（ATAK 5.5）。
>
> **證據來源。** *權威（反組譯）bytecode：* ATAK-CIV **5.7.0.5** SDK `main.jar`（`<ATAK_SDK_5_7_0_5>/main.jar`）。*可讀交叉參照：* 本機上游 clone `TAK-Product-Center/atak-civ`，tag **5.5.1.10**，commit `9f6893dd657feacc35ec5de03dad721c2e44170e`。
>
> **日期：** 2026-06-17。

> ⚠️ **版本漂移（version drift）——請先讀這段。** 下文所有行為結論的**權威**來源是 **5.7.0.5** 反組譯 bytecode。**可讀**交叉參照（以及本文所有 GitHub permalink）指向 **5.5.1.10**——*不同的發行線*。本文檢視的程式路徑上，兩者在控制流與 method 契約上一致；若有差異，會就地標註（例如 resolver 基底類別從 `com.atakmap.android.importfiles.sort.ImportResolver` 移至 `gov.tak.api.importfiles.ImportResolver`；數個 `*Sort` 類別標為 `@Deprecated`／`@DeprecatedApi(removeAt 5.8)` 並由 `gov.tak.api.importfiles.Import*Resolver` 取代，但這些已棄用類別在 5.7.0.5 中仍存在且可運作）。**兩者若有任何不一致，以 5.7.0.5 bytecode 為準。** Permalink 的**行號僅對 5.5.1.10 有效**（上游未發布 5.7.0.x 原始碼）。

> **配套文件。** [`README.md`](./README.md) 跨三個層級深入回答相同 UID 碰撞的問題——**Layer A** = CoT 項目（item UID，last-received-wins）、**Layer B** = 資料包容器（內容雜湊去重 + 以 UID 為鍵的解壓縮目錄）、**Layer C** = 以目的檔名為鍵的 KMZ／KML overlay payload。**本文不重複該分析。** 關於 `cot`、`datapackage`（`.zip`／`.dpk`）、`kml`、`kmz` 的衝突細節，下文各節保持精簡並連結至 `README.md` §2／§3／§4。

---

## 匯入路由如何運作

幾乎每一種檔案型別都搭乘同一套 **`ImportResolver`** 框架（較舊的已棄用子類別命名為 `Import*Sort`；目前的則是 `gov.tak.api.importfiles.Import*Resolver`）。共用管線為：

1. **`match(File)`**——每個已註冊的 resolver 判斷自己是否認領該檔案。這是 `super.match()`（由建構子的 `_ext` 組成的**副檔名**閘門），對多數格式而言其後還有一道**內容嗅探**（讀取前 N 個 byte／char，或開啟壓縮檔尋找特徵簽章）。單憑副檔名很少足夠。
2. **`beginImport(File, Set<SortFlags>)`**——選定的 resolver 把檔案複製／移動到它的目的地。
3. **`getDestinationPath(File)`**——將目標算為 **`destinationDir + file.getName()`**（建構子固定 `destinationDir`；檔名取自來源檔名，除了補上缺漏的副檔名外不變）。少數 resolver 會**覆寫**此方法（DTED 從標頭 byte 重新推導路徑；CoT 回傳檔案本身）。
4. **`FileSystemUtils.copyFile` / `renameTo`**——將檔案寫入目的地。把單純的複製寫到既有路徑上會**就地覆寫**。
5. **`onFileSorted(...)`**——基底 resolver 廣播 `Intent "com.atakmap.android.importexport.IMPORT_DATA"`，攜帶 `contentType`／`mimeType`／`uri`（來自 `getContentMIME()`），並通知已註冊的 `ImportListener`。（CoT 與 iconset 改用**各自的**廣播 action——`IMPORT_COT`、`ADD_ICONSET`。）
6. **MapComponent／importer 註冊**——某個 `MapComponent` 先前已向 `ImporterManager` 註冊一個以 `(contentType, mimeType)` 為鍵的 `Importer`；該 importer 消費此廣播，並把檔案匯入它的子系統（spatial DB、raster catalog、CoT dispatcher、icon DB，或 DTED cell tree）。

### 四種衝突原型

跨所有格式，「重新匯入時會發生什麼」可收斂為四種模式：

| 原型 | 識別鍵 | 重新匯入行為 | 格式 |
|---|---|---|---|
| **(a) 檔名／路徑覆寫**（檔案 overlay） | 目的路徑 = `destinationDir/<file.getName()>` | 靜默就地覆寫；不同檔名 = 第二個並存的 overlay；resolver 層無內容／UID 去重 | KML、KMZ、MVT、**影像**（地理標記 JPEG → 根目錄）、資料包（resolver 層）、iconset（磁碟上的 zip） |
| **(b) 以 catalog／DB-row 為鍵**（raster／elevation） | catalog DB 中的絕對檔案**路徑**，或地理**cell 路徑** | 路徑已建檔且未變更 → **跳過**（冪等）；已變更 → 就地重新驗證／更新；不同檔名 → 額外新增一筆 | GRG（`GRGs2.sqlite` catalog）、**Layers／原生影像**（`imagery/` + `LayersMapComponent`）、DTED（cell-tree 佈局**即是** catalog） |
| **(c) 以 UID 為鍵的替換**（容器／icon set） | 從 manifest／XML 解析出的 UID | 同 UID → 刪除舊 row／目錄再插入新者 = 就地**替換**；上一層的內容雜湊去重可能對相同內容**跳過** | iconset（`iconset.xml` UID）、資料包容器（manifest UID + 內容雜湊，見 README §3） |
| **(d) CoT item-UID 管線** | CoT `<event uid=…>` | 同 UID → 既有 `MapItem` **就地修改**（last-received-wins；嚴格的 newer-wins 僅在休眠的 `ignoreLateCoTEvents` debug 旗標後方）；不同 UID → 新標記 | CoT（`.cot`），以及資料包內的 CoT payload（見 README §2） |

---

## 統整表

| 格式 | 副檔名 | MIME | Resolver（5.7.0.5 啟用中） | 目的地 | 註冊 | 衝突鍵 | 重新匯入時 |
|---|---|---|---|---|---|---|---|
| **CoT** | `.cot` | `application/cot+xml` | `ImportCotSort` | *無*——解析／派送；若位於 `cache/atakdata/` 下，來源檔被 secure-delete | CoT importer 管線（`CotImporterManager` → `MapItemImporter`／`MarkerImporter`） | CoT event **item-UID** | 就地修改既有 `MapItem`（last-received-wins；嚴格 newer-wins 僅在 `ignoreLateCoTEvents` 時）；新 UID = 新標記 |
| **資料包** | `.zip`、`.dpk` | `application/zip` | `ImportMissionPackageSort`（`@Deprecated`；V1=`.zip`，V2=`.dpk`） | `tools/datapackage/<name>`（其後於下游解壓縮） | 資料包 overlay + 經 `IMPORT_DATA` → `MissionPackageExtractor` 的逐內容散開 | **檔名**（resolver）／manifest-UID + 內容雜湊（下游） | resolver：同檔名 → `moveToTemp`+刪除再複製 = 覆寫；下游以 manifest-UID／雜湊去重（README §3） |
| **KML** | `.kml` | `application/vnd.google-earth.kml+xml` | `ImportKMLSort`（`@Deprecated`） | `overlays/<name>` | spatial feature DB（下游 KML importer）+ `URIContentHandler` | **路徑**（`overlays/<file.getName()>`） | 以檔名靜默就地覆寫；不同檔名 = 並存的 overlay（README §4） |
| **KMZ** | `.kmz` | `application/vnd.google-earth.kmz` | `ImportKMZResolver`（向量）／`ImportKMZPackageResolver`（多 payload） | `overlays/<name>` | OGR spatial DB（`KmlFileSpatialDb`）或散開（`KMZPackageImporter`） | **檔名**（`overlays/<file.getName()>`） | 就地覆寫 + 重新建檔；不同檔名 = 第二個 overlay（README §4） |
| **影像** | `.jpg`、`.jpeg`（地理標記） | `image/jpeg` | `ImportJPEGSort`（+ `ImportJPEGResolver`） | ATAK **根目錄**（資料夾 `null`） | 影像 IPP importer（`image.ipp.ImportImageSort`，`"JPEG Image"`） | **檔名** | 依檔名就地覆寫；**未地理標記的 JPEG 不被認領**（需 EXIF GPS） |
| **GRG** | `.ovr.sqlite`、`.ovr.mbtiles`、GeoTIFF、GroundOverlay KML/KMZ、小型 NITF、GeoPDF、MBTiles、MCIA-GRG 目錄 | `application/octet-stream`（`External GRG Data`） | `ImportGRGResolver`（目前）／`ImportGRGSort`（`@Deprecated`） | `<root>/grg/<name>` | raster catalog `Databases/GRGs2.sqlite`（`PersistentRasterDataStore`）→ `DatasetRasterLayer2` overlay | catalog 中的絕對檔案**路徑**（cell 路徑） | 磁碟：以檔名覆寫；catalog：**已存在且為最新則跳過**，已變更則更新；不同檔名 = 額外新增一筆 |
| **DTED** | `.dt0`–`.dt3`、含 DTED 的 `.zip`／`.dpk` | `application/dted`、`application/zip` | `ImportDTEDSort`、`ImportDTEDZSort`（V1=`.zip`，V2=`.dpk`） | `<root>/DTED/<e\|wXXX>/<n\|sYY>.dtN`（從標頭推導） | DTED cell tree（目錄佈局**即是** catalog；無 DB row） | **地理 cell 路徑** | 就地覆寫同一個 cell 檔（last writer wins）；檔名無關緊要——由標頭決定 cell |
| **Iconset** | `.zip` | `application/zip` | `ImportUserIconSetSort` | ATAK **根目錄**（無子資料夾） | `UserIconDatabase`（`iconsets.sqlite`） | **iconset UID**（`<iconset uid=…>`） | 刪除舊 UID row 再插入新者 = **替換**；磁碟上的 zip 以檔名覆寫 |
| **Layers** | 內容嗅探（無副檔名閘門） | `application/octet-stream`（`External Native Data`） | `ImportLayersSort` | `imagery/<name>` | `LayersMapComponent` 原生影像／地圖圖層 | **檔名／路徑** | 覆寫 + 原生圖層重新掃描；不同檔名 = 新圖層 |
| **MVT** | `.mvt`、`.mbtiles` | `application/vnd.mapbox-vector-tile` | `ImportMVTSort`（`@Deprecated`）／`gov.tak.api…ImportMVTResolver` | `overlays/<name>` | `FeatureDataStore2` spatial DB + MapOverlayManager files-overlay（`MvtSpatialDb`） | **路徑** | 就地先移除再重新匯入（保留可見性）= 覆寫／刷新；不同檔名 = 新 layer |

---

## cot

**它是什麼。** 單一筆 Cursor-on-Target XML event（`<event>` + `<point>`）——一個標記／map item。

**流程。** `ImportResolver.match` 先接受 `.cot` **FileFilter**；接著 `ImportCotSort` 讀取前 **384 個 char**，並要求**同時**包含 `"<event"` 與 `"<point"`（`isCoT`）——這是內容嗅探，而非僅看副檔名（一個外形像 CoT 但副檔名非 `.cot` 的檔案，會在嗅探前就被濾掉）。`beginImport` **不**複製檔案：`getDestinationPath` 回傳檔案本身，且 super 的資料夾引數為空字串 `""`（所以**不會**建立 `cot/` 資料夾）。它將 XML 讀入一個 String，廣播 `Intent "…IMPORT_COT"` 並附加 `"xml"=<該 XML>`，若來源位於 `cache/atakdata/` 下則對它執行 **`SECURE_DELETE`**，以免下次啟動時又被重新匯入。`ImportExportMapComponent.importCotReceiver` 解析該 event 並 `dispatchFrom()` 進入 ATAK 內部的 CoT dispatcher → `CotImporterManager.importData` 依型別挑選一個 importer → `MapItemImporter.importData` 以 event UID 查詢既有 `MapItem`（`deepFindUID`）；找到則由 `MarkerImporter` 就地修改它，否則 `createMarker(new Marker(uid))`。**衝突 = 原型 (d)：** 同 UID 覆寫同一個標記（last-received-wins；嚴格 newer-wins 僅在休眠的 `ignoreLateCoTEvents` debug 旗標開啟時）；不同 UID 則新增一個。**完整的 item-UID 分析見 [README §2](./README.md#2-layer-a--item-uid-conflict)。**

**證據。** `javap ImportCotSort.<init>` → `ldc ".cot"`、`super(".cot","",…)`（空資料夾）、`sipush 384; newarray char`。`javap ImportCotSort#match` → `invokespecial ImportResolver.match` 接著 `invokestatic isCoT(InputStream,[C)`。`javap ImportCotSort#isCoT(String)` → `ldc "<event"; String.contains; ifeq 39` / `ldc "<point"; String.contains; ifeq 39; iconst_1`。`javap ImportCotSort#getDestinationPath` → `aload_1; areturn`（回傳檔案本身）。`javap ImportCotSort#beginImport` → `IMPORT_COT` intent、`putExtra("xml", …)`、`AtakBroadcast.sendBroadcast`，接著 `getCacheDir()`+`"atakdata"` 的 `startsWith` 檢查 → `IOProviderFactory.delete(file, SECURE_DELETE)`。`javap MapItemImporter#findItem` → `getUID` → `MapGroup.deepFindUID`。`javap MarkerImporter#importMapItem` → `checkcast Marker`、`ifnonnull`（經 `setPoint`／`setMetaString`／`setTitle` 就地修改），否則 `createMarker`（唯一的 `new Marker(uid)`，由 `doNotRecreate`→`FAILURE` 把關）。*兩處 clone 與 bytecode 漂移（行為不變）：* 在 5.7.0.5 中，新標記路徑改經由 helper `createMarker(CotEvent,Bundle)`（並非在 `:302/:306/:339` 直接 `new Marker`）；所引用的 clone 行號僅對 5.5.1.10 有效。

- [ImportCotSort.java#L51 — `super(".cot","",…)`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCotSort.java#L51)
- [ImportCotSort.java#L103 — 內容嗅探](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCotSort.java#L103)
- [ImportCotSort.java#L124 — `beginImport`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCotSort.java#L124)
- [ImportExportMapComponent.java#L318 — `importCotReceiver`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importexport/ImportExportMapComponent.java#L318)
- [MapItemImporter.java#L99 / #L190](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java#L99)
- [MarkerImporter.java#L74 / #L339](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MarkerImporter.java#L74)

---

## datapackage

**它是什麼。** 一個 `.zip` / `.dpk` Mission Package（資料包）——一個 ZIP，攜帶 `MANIFEST/manifest.xml` 加上內容（CoT、附件、overlay）。

**流程（兩個磁碟階段）。** **階段 A（此 resolver）：** `ImportMissionPackageSort.match` 執行 `super.match`（副檔名 `.zip`／`.dpk`）；在 **strict** 模式回傳 `MissionPackageExtractorFactory.HasManifest(file)`（一個名稱 `endsWith(MANIFEST_XML)` 的 zip entry），在 **non-strict** 模式則要求 `ZipUtils.isZip` **且**沒有其他非 MissionPackage sorter 認領該 zip（「沒人想要的純 zip」）。建構子的目的資料夾 = `TOOL_DATA_DIRECTORY + "/" + mission_package_folder` = **`tools/datapackage`**。`beginImport`：若已存在同名檔案則先 `moveToTemp` 再刪除（**檔名覆寫**），強制加入 `IMPORT_COPY`，再由 `super.beginImport` 將來源複製到 `tools/datapackage/<name>`。父類別 `onFileSorted` 讀取 `getContentMIME()=("Data Package","application/zip")` 並廣播 `IMPORT_DATA`。**階段 B（下游，非此類別）：** `ImportReceiver` → `ImporterManager.findImporter("Data Package","application/zip").importData(uri)` 交棒給 `MissionPackageExtractor` / `ExtractMissionPackageTask`，它解壓縮進 datapackage 樹（轉移暫存區 `tools/datapackage/transfer`）、讀取 manifest、把套件註冊進資料包 overlay，並把其內容散開給各自的 importer。**衝突：此 resolver 為原型 (a)**（檔名就地覆寫）；**下游為原型 (c)**（MissionPackage 子系統中的 manifest-UID + 內容雜湊去重）。*對 HackMD 筆記的更正：* `beginImport` 本身**不會**直達 extractor——解壓縮是經廣播派送，隔了一跳。**容器 UID + 內容雜湊去重見 [README §3](./README.md#3-layer-b--data-package-container-uid)。**

**證據。** `javap ImportMissionPackageSort#match` → `invokestatic MissionPackageExtractorFactory.HasManifest`；non-strict `ZipUtils.isZip` + `instanceof ImportMissionPackageSort`（跳過自身）+ `ImportResolver.match`。建構子 → `ldc "tools"` + `R$string.mission_package_folder`（`="datapackage"`）。`javap #beginImport` → `getDestinationPath`、`IOProviderFactory.exists`／`isFile`、`FileSystemUtils.moveToTemp`、`deleteFile`、`SortFlags.IMPORT_COPY`、`super.beginImport`。`javap getContentMIME` → `ldc "Data Package"`、`ldc "application/zip"`。此類別為 `@Deprecated`（自 5.5，removeAt 5.8），由 `gov.tak.api.importfiles.ImportMissionPackageResolver` 取代，但仍是任務中所指名的 resolver，且在 5.7.0.5 中存在。

- [ImportMissionPackageSort.java#L92 / #L119 / #L162](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportMissionPackageSort.java#L92)
- [ImportReceiver.java#L135 — `findImporter`+`importData`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importexport/ImportReceiver.java#L135)
- [MissionPackageReceiver.java#L1290 — datapackage 目錄 + extract task](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/MissionPackageReceiver.java#L1290)

---

## kml

**它是什麼。** 一個 `.kml` 向量 overlay（placemark、線、多邊形）。

**流程。** `ImportKMLSort.match`：先過 `super.match` 副檔名閘門（`.kml`，不符即快速失敗），再做內容嗅探——`isKml` 讀取前 **2048 個 char**，當 `content.contains("<kml")` **或** `content.matches("(?s).*<[^>]+:kml.*")`（字面的 `<kml` 或帶 namespace 的 `<ns:kml`）為真時才回傳 true。目的地 = **`overlays/<file.getName()>`**（建構子傳入 `"overlays"`；基底 `getDestinationPath` = `new File(getItem("overlays"), name)`）。`beginImport` 把檔案複製到該處，**就地覆寫任何同名檔案**，沒有 `exists()` 預檢、沒有對話方塊、不會改名為唯一名稱。`onFileSorted` 接著廣播 `IMPORT_DATA{contentType="KML", mime=application/vnd.google-earth.kml+xml}` 並通知 `ImportListener`；下游 KML importer（`KmlFileSpatialDb`，一個 `SpatialDbContentSource`）把檔案以檔案**路徑**為鍵建檔進 spatial feature DB，並註冊一個 `URIContentHandler`，使其在 Overlay Manager 中呈現為可切換的 overlay。**衝突 = 原型 (a)：** 路徑／檔名，靜默覆寫。*結論備註（已更正）：* 檔案系統覆寫由 `ImportKMLSort`／`ImportResolver` 證實；「spatial-DB 先移除再重加」是**下游** KML importer 的性質，**無法**從這些 resolver 類別證明——不要把它歸給它們。`ImportKMLSort` 為 `@Deprecated`（removeAt 5.8，→ `gov.tak.api.importfiles.ImportKMLResolver`）。**見 [README §4](./README.md#4-third-path--kmz--kml-overlay-data-packages-worked-example-cctvzip)。**

**證據。** `javap ImportKMLSort.<init>` → `ldc ".kml"`、`ldc "overlays"`、`super(…)`。`javap #match` → `ImportResolver.match; ifne; iconst_0; ireturn` 接著 `isKml`。`javap #isKml` → `sipush 2048; newarray char` … `ldc "<kml"; String.contains; ifne`；`ldc "(?s).*<[^>]+:kml.*"; String.matches`。`javap getContentMIME` → `ldc "KML"`、`ldc "application/vnd.google-earth.kml+xml"`。`FileSystemUtils.OVERLAYS_DIRECTORY="overlays"`。`ImportResolver.getDestinationPath` → `new File(folder, fileName)`；`beginImport` 的 COPY 分支只把 `src.equals(dst)` 做為守衛，接著無條件 `copyFile`（靜默覆寫）。

- [ImportKMLSort.java#L39 / #L88 / #L102](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportKMLSort.java#L39)
- [ImportResolver.java#L338 / #L365](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L338)
- [SpatialDbContentSource.java#L387 / #L397](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/SpatialDbContentSource.java#L387)
- [KmlFileSpatialDb.java#L31](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/KmlFileSpatialDb.java#L31)

---

## kmz

**它是什麼。** 一個 `.kmz`（壓縮過的 KML）——向量 overlay、raster GRG、3D 模型，或三者混合。

**流程（四個 resolver，全以 `.kmz` 為鍵；消歧義純粹在 `match()` 內——沒有 `filterFoundResolvers` 覆寫）。** **純／向量 KMZ** 由 `ImportKMZResolver`（目前）／`ImportKMZSort`（`@Deprecated`）透過三道閘門認領：(1) `super.match` 副檔名 `.kmz`；(2) `hasKML` 在 zip 中尋找一個通過 `isKml` 的 `*.kml`（strict 模式要求剛好一個頂層 `doc.kml`）；(3) 決定性的 GRG-vs-overlay 檢查——`KMZPackageImporter.getContentTypes(file)` 必須包含 `"KML"`（否則 `Log.d("Skipping GRG KMZ")` → `false`，交給 GRG resolver），接著 GDAL `ogr.Open` 的 driver 名稱必須 `contains("kml")`。`getContentTypes()` 以 SAX 解析 `doc.kml`：`<GroundOverlay>`→`"External GRG Data"`、`<Model>`→ModelImporter、`<Placemark>` 幾何→`"KML"`。**多 payload** KMZ 由 `ImportKMZPackageResolver`／`Sort` 認領，其整個方法主體即 `return getContentTypes(file).size() > 1`。目的地 = **`overlays/<file.getName()>`**（決定性，無唯一化後綴，無雜湊／UID 檢查）。`beginImport` `copyFile`（或 `renameTo`→`copyFile`）**就地覆寫**；`onFileSorted` 廣播 `IMPORT_DATA`。路由：`"KML"`→`KmlFileSpatialDb`（OGR DB）建檔／繪製該 overlay；`"KMZ"`→`KMZPackageImporter.importData` 逐一走訪內容型別並各自派送（KML→OGR DB、GRG→GRG catalog、Model→ModelImporter）。**衝突 = 原型 (a)：** 同檔名 → 覆寫 + 重新建檔；不同檔名 → 第二個獨立的 overlay（無內容雜湊／KML-id 去重）。**見 [README §4](./README.md#4-third-path--kmz--kml-overlay-data-packages-worked-example-cctvzip)。**

**證據。** `javap ImportKMZResolver.<init>` → `ldc ".kmz"`、`ldc "overlays"`、`FileSystemUtils.getItem`、`super(String,File,String,Drawable)`。`javap #match` → `super.match` → `hasKML` → `getContentTypes`.`contains("KML")`；否則 `Log.d "Skipping GRG KMZ:"`；接著 `ogr.Open` … `GetName().toLowerCase().contains("kml")`。`javap ImportKMZPackageSort#match` 整個主體 → `getContentTypes; List.size; if_icmple`。`getContentMIME`：純→`("KML","application/vnd.google-earth.kmz")`，package→`("KMZ",…)`。`getDestinationPath` → `new File(destinationDir, name)`（無唯一化後綴）；`beginImport` → `copyFile`／`renameTo`+`copyFile` 後備。常數：`OVERLAYS_DIRECTORY="overlays"`、`KmlFileSpatialDb.KML_CONTENT_TYPE="KML"`、`KMZPackageImporter.CONTENT_TYPE="KMZ"`、`GRGMapComponent.IMPORTER_CONTENT_TYPE="External GRG Data"`。

- [ImportKMZSort.java#L57 / #L66 / #L76](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportKMZSort.java#L57)
- [ImportKMZResolver.java#L53 / #L60](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportKMZResolver.java#L53)
- [ImportKMZPackageSort.java#L40](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportKMZPackageSort.java#L40)
- [KMZPackageImporter.java#L70 / #L105 / #L140](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/layers/kmz/KMZPackageImporter.java#L105)

---

## image

**它是什麼。** 一張 **GPS 地理標記**的 JPEG 照片（`.jpg` / `.jpeg`）。

**流程。** 這裡**確實**有一個專屬 resolver——`ImportJPEGSort`（以及其新 API 鏡像 `gov.tak.api…ImportJPEGResolver`），為 `.jpg` 與 `.jpeg` 註冊（`ImportFilesTask` 加入 `new ImportJPEGSort(context, ".jpg", …)` 與 `".jpeg"`）。`match` = `super.match`（副檔名閘門）**且** `isJpegExif(file)`，後者要求可讀的 EXIF **且帶有 GPS 定位**——`ExifHelper.getExifMetadata(file).getGPS() != null`。因此一張普通、**未地理標記**的 JPEG **不會**被此 resolver 認領。建構子傳入資料夾 `null`，故 `getDestinationPath` = `getItem(null)` = ATAK **根目錄**；照片以原檔名原樣複製進根目錄。`onFileSorted` 廣播 `IMPORT_DATA{contentType="JPEG Image", mime=image/jpeg}`，由**影像 IPP importer**（`com.atakmap.android.image.ipp.ImportImageSort` / `ImportImageResolver`，註冊相同的 `("JPEG Image","image/jpeg")` 配對）消費，把地理標記照片置於其 EXIF GPS 位置。**衝突 = 原型 (a)：** 根目錄同檔名 → 靜默就地覆寫；不同檔名 → 各自獨立匯入。*未地理標記的影像、以及內嵌於其他套件的影像不在此處理*——它們改走 **iconset**（icon 成員）、**grg**（地理參照 raster）或**資料包附件**路徑。

**證據。** `javap ImportJPEGSort#match` → `ImportResolver.match` 後 `invokestatic isJpegExif`；`isJpegExif` → `ExifHelper.getExifMetadata` → `TiffImageMetadata.getGPS; ifnull → false`。`javap ImportJPEGSort.<init>` → `super(ext, null, …)`（資料夾 `null` → 根目錄）。`javap getContentMIME` → `ldc "JPEG Image"`、`ldc "image/jpeg"`。`ImportFilesTask` 為 `.jpg` 與 `.jpeg` 建構它。消費端 `ImportImageResolver#getContentMIME` → 同一個 `("JPEG Image","image/jpeg")` 配對。*（內嵌影像：`ImportUserIconSetResolver.IconFilenameFilter` 接受 `.jpg/.jpeg/.png/.bmp/.webp/.gif` 作為 iconset 成員——見 **iconset** 一節；GroundOverlay/GeoTIFF/NITF raster 走 **grg**；附件搭乘資料包容器，[README §3](./README.md#3-layer-b--data-package-container-uid)。）*

- [ImportJPEGSort.java#L45 — `super(ext, null, …)`（資料夾 = ATAK 根目錄）](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportJPEGSort.java#L45)
- [ImportJPEGSort.java#L58 — `isJpegExif` 要求 `getGPS() != null`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportJPEGSort.java#L58)
- [ImportFilesTask.java#L290 — 為 `.jpg` / `.jpeg` 註冊](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/task/ImportFilesTask.java#L290)
- [ImportImageResolver.java#L127 — `("JPEG Image","image/jpeg")` 消費端（影像 IPP）](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/image/ipp/ImportImageResolver.java#L127)

---

## grg

**它是什麼。** 一張 Gridded Reference Graphic——地理參照的 raster 影像 overlay。輸入形狀眾多：`.ovr.sqlite` / `.ovr.mbtiles`、GeoTIFF（`ImageryFileType` 路徑 `"grg"`）、GroundOverlay KML/KMZ、小型（<256 MB）GDAL NITF、GeoPDF、非 terrain 的 MBTiles、MCIA-GRG 目錄。

**流程。** `match(File)` 是一道**多分支嗅探**，並非純粹的副檔名測試。對指名的案例，決定性檢查是副檔名後綴測試：`endsWith(".ovr.sqlite") || endsWith(".ovr.mbtiles")`。其他為真的分支：一個 MCIA-GRG **目錄**（`MCIAGRGLayerInfoSpi.isMCIAGRG`）；`ImageryFileType.getFileType` 路徑 == `"grg"`；小型 NITF（GDAL，<256 MB）；路徑 == `"overlays"` 且壓縮檔含 `GroundOverlay` tag 的 KML/KMZ；GeoPDF（`isGeoPDF`）；非 `"terrain"` 的 MBTiles。目的地 = **`<root>/grg/<file.getName()>`**（`getItem("grg")` = `new File(getRoot(), "grg")`——**不是** `overlays`、**不是** `imagery`；由建構子的 super 引數 `"grg"` 決定）。`beginImport` `copyFile`／`renameTo` 進入 `grg/`（同名就地覆寫）。`onFileSorted` 觸發 `IMPORT_DATA(contentType "External GRG Data", mime application/octet-stream)`，而 GRG-discovery 的 `Scanner`（`getDefaultScanDirs("grg")`）掃過 `grg/` 並呼叫 `grgDatabase.add(file)`。**註冊 = 原型 (b)：** `LocalRasterDataStore.addNoSync` 呼叫 `containsImpl(file)` → `PersistentRasterDataStore.containsImpl` 執行 `layersDb.queryCatalog(file)`（純粹**以路徑為鍵**）；若該路徑已建檔則**立即回傳而不重新匯入**（暫存工作目錄被刪除；既有 row 不動 = **跳過**）。若該路徑上的檔案**已變更**，`refresh`／`validateCatalogRowNoSync` 偵測到陳舊的現時性（file-tree 大小／時間戳）並重新匯入 = **就地更新**。**不同檔名** = 不同路徑 = 額外新增第二筆 catalog（不覆寫）。catalog 位於 `Databases/GRGs2.sqlite`，並呈現為 `DatasetRasterLayer2` GRG overlay。

> HackMD 筆記的 `conflictKey` enum 沒有 "file-path" 選項；去重鍵精確而言是**絕對檔案路徑**（`queryCatalog(File)`），由 file-tree 現時性重新驗證——**不是**內容雜湊或 UID。

**證據。** `javap ImportGRGSort.<init>` → `ldc "grg"`、`super(String,String,String,Drawable)`（ext=null，folder=`"grg"`）。`javap ImportGRGResolver.<init>` → `ldc "grg"`、`FileSystemUtils.getItem`、`super(String,File,String,Drawable)`。`javap ImportResolver#getDestinationPath` → `getfield _folderName; getItem; new File(dir,name)`。`javap #match` → `ldc ".ovr.sqlite"; endsWith; ifne; … ".ovr.mbtiles"; iconst_1`。`javap getContentMIME` → `ldc "External GRG Data"`、`ldc "application/octet-stream"`。`GRGMapComponent` → `DATABASE_FILE=getItem("Databases/GRGs2.sqlite")`、`new PersistentRasterDataStore(...)`、`refresh()`。`javap LocalRasterDataStore#addNoSync` → `invokevirtual containsImpl; ifeq; iconst_1; ireturn`（已存在則跳過）。`javap PersistentRasterDataStore#containsImpl` → `layersDb.queryCatalog(File); CatalogCursor.moveToNext; ireturn`。*漂移：* 在 5.7.0.5 中 `ImportGRGResolver` 繼承 `gov.tak.api.importfiles.ImportResolver`，並在建構子經 `getItem("grg")` 取得其 dest File；解析後的目的地與以路徑為鍵的去重，與舊版 `ImportGRGSort` 完全相同。

- [ImportGRGSort.java#L57 / #L78 / #L104](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGRGSort.java#L57)
- [GRGMapComponent.java#L67 / #L89 / #L100](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/grg/GRGMapComponent.java#L67)
- [GRGDiscovery.java#L66 / #L148](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/grg/GRGDiscovery.java#L66)

---

## dted

**它是什麼。** DTED 高程 cell（`.dt0`／`.dt1`／`.dt2`／`.dt3`，鬆散檔或壓縮在 `.zip`／`.dpk` 內）。

**流程。** **鬆散檔：** `ImportDTEDSort.match` = `isDted(file.getName())`——先轉小寫再 `endsWith(".dt3"|".dt2"|".dt1"|".dt0")`，**純以副檔名判斷**（match 時**不**讀標頭）。**Zip：** `ImportDTEDZSort.match` = `super.match`（`.zip`／`.dpk`）**且** `hasDTED(file)`（開啟壓縮檔；若有 cell 命名的 entry `n/s/w/e…` 即符合，否則經 `readDtedFile` 驗證標頭）；接著 `filterFoundResolvers` 清空清單並只加入自身，因此 DTED zip 絕不會同時被當成資料包。**目的地很特殊——`getDestinationPath` 被覆寫**，改從 DTED **標頭 byte** 重新推導 cell 路徑（而非來源檔名所在目錄）：`<root>/DTED/<e|wXXX>/<n|sYY>.dtN`。`beginImport` `renameTo(src,dest)`（複製／刪除後備）覆寫既有的 cell 檔。對 zip，`onFileSorted`→`installDTED` 解壓縮每個 cell，把每條路徑重新推導到 `DTED/` 下，並以截斷式 `getOutputStream`（覆寫）串流寫出。**註冊：** 純檔案系統——**無 DB row、無 spatial-DB／`layers.sqlite` 插入**；`DTED/` cell-tree 佈局**即是** catalog，由原生高程讀取器按需掃描。**衝突 = 原型 (b)，地理 cell 路徑：** 同經緯度 cell → 同檔案路徑 → last writer wins；來源檔名無關緊要（由標頭決定 cell），因此兩個檔名不同但屬同一 cell 的檔案會碰撞並依設計覆寫。

> *相對於 HackMD 筆記的修正：* (1) 路徑是從**標頭**重新推導，而非來源檔名，因此即使標錯名的檔案仍會落在它真正的 cell；(2) **沒有** catalog DB 寫入——目錄樹本身即是 catalog。

**證據。** `javap ImportDTEDSort#match` → `invokestatic isDted`；`isDted` → `ldc ".dt3"/".dt2"/".dt1"/".dt0"; endsWith`。`javap #getDestinationPath` → `invokestatic readDtedFile`；`readDtedFile` → `bipush 24; newarray char`（24 個標頭 byte），從 `b[19],b[13],b[14],b[11],b[4],b[5],b[6]` 組出 basedir/filename，`ldc "DTED"; getItem; new File(File,String)`。`javap #beginImport` → `File.equals`（相同則為 no-op）、`FileSystemUtils.renameTo`、`onFileSorted`。`javap ImportDTEDZSort#match` → `super.match` + `hasDTED`（`ZipFile.entries` + `containsDT` + `readDtedFile`）；`filterFoundResolvers` → `List.clear`+`List.add(this)`；`onFileSorted` → `installDTED` → 逐 entry e/w+n/s 路徑、`getOutputStream` 截斷式寫入。*漂移：* 5.5.1.10 建構子傳入 `FileSystemUtils.DTED_DIRECTORY`；5.7.0.5 傳入字面 `"DTED"`——兩者皆 = `getItem("DTED")` = `root+/DTED`。較新的 `gov.tak.api` `ImportDTEDResolver`／`DTEDZCallback` 做的是完全相同的 cell 路徑覆寫。

- [ImportDTEDSort.java#L52 / #L92 / #L166 / #L79](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportDTEDSort.java#L52)
- [ImportDTEDZSort.java#L84 / #L92](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportDTEDZSort.java#L84)
- [DTEDZCallback.java#L70 / #L112 / #L192](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/callbacks/DTEDZCallback.java#L70)

---

## iconset

**它是什麼。** 一個含 `iconset.xml`（帶 `<iconset uid=…>`）加上一張以上 icon 影像的 `.zip`。

**流程。** `ImportUserIconSetSort.match` = `super.match`（`.zip` FileFilter）**且** `HasIconset(file,true)`：該 zip 必須含一個 entry，其小寫名稱 `endsWith "iconset.xml"` 且通過 `isIconsetXml`（前 1024 個 char 含 `"<iconset"`），**且**至少一個 entry 通過 `IconFilenameFilter`（`.jpg/.jpeg/.png/.bmp/.webp/.gif`）；否則它記錄「Invalid iconset (no image)」／「(XML required)」並回傳 false。目的地 = ATAK **檔案系統根目錄**（無子資料夾）：建構子傳入 `_folderName=null`，而 `getItem(null)` 回傳 `getRoot()`——所以該 `.zip` 被原樣複製進根目錄，**而非**一個專屬的 iconset 資料夾。`onFileSorted` 廣播 `Intent "…ADD_ICONSET"{filepath=root zip}` → `IconsMapComponent` 的 receiver → `IconsMapAdapter.addIconset`：解析 `iconset.xml` → `UserIconSet(uid,…)`；`getIconSet(uid)`；**若已存在帶該 UID 的 iconset → `removeIconSet(existing)`**（`DELETE FROM iconsets`／`icons WHERE iconset_uid=?`）再 `addIconSet(new)`（`INSERT INTO iconsets` + 逐影像 `INSERT INTO icons`）。**衝突 = 原型 (c)，iconset-UID 替換：** 同 UID 重新匯入會先刪除舊 row 再插入新者 = 就地替換（不同／遞增的 `<version>` 只是順帶一起）。注意較低層的 `UserIconDatabase.addIconSet` 有**自己的**守衛（`getIconSet(uid)!=null → "Iconset already exists:" → return -1`，亦即單獨呼叫 `addIconSet` 會**跳過**）——但匯入路徑先移除舊 row 以擊敗它，所以**匯入的淨行為是替換，而非跳過**。

> *對 HackMD 筆記的修正：* 啟用中的 sort 類別是 `com.atakmap.android.importfiles.sort.ImportUserIconSetSort`（筆記中的 `ImportUserIconSetResolver` 僅以新 API 鏡像 `gov.tak.api.importfiles.ImportUserIconSetResolver` 存在，Sort 會伸進去取用其 `IconFilenameFilter`）。真正的匯入時去重關鍵字是 **"Removing old version of iconset:"**（`IconsMapAdapter`），而非 "Skipping already existing iconset:"（那個字串是 `IconsetAdapter` 中另一條清單／UI 路徑）。DB 檔 = `iconsets.sqlite`；表 `iconsets`（PK `uid`）+ `icons`（FK `iconset_uid`）。

**證據。** `javap ImportUserIconSetSort.<init>` → `ldc ".zip"`、`aconst_null`（資料夾）、`getString(R$string.user_icon_set)`、`super(String,String,String,Drawable)`。`javap #match` → `ImportResolver.match; ifne; iconst_0; ireturn` … `invokestatic HasIconset`。`HasIconset` → `ldc "iconset.xml"`、`isIconsetXml`、`IconFilenameFilter`；`isIconsetXml` → `ldc "<iconset"; String.contains`。`onFileSorted` → `ldc "com.atakmap.android.icons.ADD_ICONSET"`、`putExtra("filepath", …)`。`FileSystemUtils.getItem` → `isEmpty → getRoot(); areturn`。`javap IconsMapAdapter#addIconset` → `getIconSet(uid,false,false)`；`ifnull` 跳過移除，否則 `ldc "Removing old version of iconset:"` + `removeIconSet`；接著 `addIconSet`。`javap UserIconDatabase#removeIconSet` → `ldc "iconsets"`、`ldc "iconset_uid=?"`、`AndroidDatabaseAdapter.delete`。`addIconSet` 守衛 → `getIconSet; ifnull; ldc "Iconset already exists:"; ldc2_w -1l; lreturn`。

- [IconsMapAdapter.java#L287 / #L294 / #L306](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/icons/IconsMapAdapter.java#L287)
- [UserIconDatabase.java#L41 / #L479 / #L486 / #L513](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIconDatabase.java#L479)
- [IconsMapComponent.java#L51 — `ADD_ICONSET`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/icons/IconsMapComponent.java#L51)

---

## layers

**它是什麼。** 原生地圖／影像圖層資料——raster 地圖來源、圖磚集（tileset）或 GeoPackage。這正是 HackMD 筆記繫於 `LayersMapComponent` 的 **「External Native Data」** 類別。

**流程。** 這裡**確實**有一個專屬 resolver——`ImportLayersSort`（`ImportFilesTask` 加入 `new ImportLayersSort(context)`）。建構子傳入 **ext `null`**（無副檔名閘門——純內容比對）與資料夾 **`"imagery"`**。`match` 在以下**任一**成立時認領檔案：`ImageryFileType.getFileType(file)` 解析為非 DTED 的影像型別；`ImportGRGSort.isGeoPDF`；非 `"terrain"` 的 `MBTilesInfo`；帶圖磚內容的 `GeoPackage`；`StreamingTiles.parse`；或 `DatasetDescriptorFactory2.isSupported`——亦即原生 raster／tileset／GeoPackage／streaming 地圖來源。目的地 = **`imagery/<file.getName()>`**（`getItem("imagery")`）。`onFileSorted` 廣播 `IMPORT_DATA{contentType="External Native Data", mime=application/octet-stream}`，由 `LayersMapComponent`（`IMPORTER_CONTENT_TYPE = "External Native Data"`）消費，它掃描 `imagery/` 並把資料註冊為可選取的原生影像／地圖圖層。**衝突 = 原型 (a)/(b)：** 同檔名 → 就地覆寫 `imagery/<name>` 檔 + 以路徑為鍵的原生圖層重新掃描；不同檔名 → 額外新增一個圖層。

**與 GRG 的關係。** `ImportLayersSort` 與 `ImportGRGSort` 是**姊妹 raster importer**，目的地與 catalog 不同：*Layers* 把**原生底圖／tileset** 資料導入 `imagery/`（由 `LayersMapComponent` 註冊），而 *GRG* 把**地理參照 overlay** raster 導入 `grg/`（由 `GRGMapComponent` 建檔於 `GRGs2.sqlite`，見 **grg** 一節）。某個檔案由哪個 resolver 認領，取決於 `ImportFilesTask` sorter 清單中的 `match()` 優先序。（其他「類圖層」結果——spatial feature DB 中的向量 KML/KMZ/MVT、`DTED/` cell tree 中的 DTED——見各自章節。）

**證據。** `javap ImportLayersSort#match` → `ImageryFileType.getFileType…getID`、`ImportGRGSort.isGeoPDF`、`MBTilesInfo.get…content != "terrain"`、`GeoPackage.getPackageContents`、`StreamingTiles.parse`、`DatasetDescriptorFactory2.isSupported`。`javap ImportLayersSort.<init>` → `ldc "imagery"`、`super(null, "imagery", …)`（ext null，資料夾 `imagery`）。`javap getContentMIME` → `ldc "External Native Data"`、`ldc "application/octet-stream"`。`LayersMapComponent.IMPORTER_CONTENT_TYPE = "External Native Data"`。

- [ImportLayersSort.java#L30 — `super(null, "imagery", …)`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportLayersSort.java#L30)
- [ImportLayersSort.java#L43 — imagery 型別／GeoPackage／tileset 比對](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportLayersSort.java#L43)
- [ImportFilesTask.java#L365 — `new ImportLayersSort(context)`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/task/ImportFilesTask.java#L365)
- [LayersMapComponent.java#L153 — `External Native Data` 消費端](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/layers/LayersMapComponent.java#L153)

---

## mvt

**它是什麼。** Mapbox Vector Tiles——一個 `.mvt` 或 `.mbtiles` **tile 容器**（**不是**裸的 `.pbf`；整個 clone 中找不到任何 `.pbf` 引用——`.pbf` tile 僅為其內部 payload）。

**流程。** `ImportMVTSort.match`：先過 `super.match` 副檔名過濾（`.mvt`／`.mbtiles`），再做**決定性的內容檢查** `FeatureDataSourceContentFactory.parse(file,"MVT") != null`（必須是真正可被 MVT 解析的檔案）。目的地 = **`overlays/<file.getName()>`**（建構子傳入內嵌字面 `"overlays"`；`getDestinationPath` = `new File(getItem("overlays"), name)`，檔名不變）。`onFileSorted` 廣播 `IMPORT_DATA{contentType="MVT", mime=application/vnd.mapbox-vector-tile}` → 路由至 `MvtSpatialDb`（由 `WktMapComponent.addContentSource` 註冊：`registerImporter` + `addFilesOverlay`）。`MvtSpatialDb.importData(Uri)` → `SpatialDbContentSource`：**原型 (a)／路徑衝突**——`DataSourceDataStoreControl.contains(file)` 以目的**路徑**為鍵；由於複製把 `file.getName()` 保留進固定的 `overlays/` 目錄，重新匯入同名檔會命中同一路徑：它先擷取舊 layer 的可見性、`remove(file)`，再由 `processFile` 重新匯入（若仍 contained 則 `update`，否則 `add(file,"MVT")`）——一次**就地覆寫／刷新**並保留可見性，**無重複、無對話方塊**。不同檔名 = 一個新的分立 layer。註冊 = 原生 `FeatureDataStore2` spatial DB + 一個 `MapOverlayManager` files-overlay，在 Overlay Manager 中呈現為「Mapbox Vector Tiles」。

> *對 HackMD 筆記的修正：* 啟用中的類別是 `ImportMVTSort`（筆記中的 `ImportMVTResolver` 並**不**作為 `sort` 類別存在——`javap` 回報「class not found」；一個較新的 `gov.tak.api.importfiles.ImportMVTResolver` 確實存在，且同樣被註冊處理 `.mvt`／`.mbtiles`）。`ImportMVTSort` 為 `@Deprecated`（removeAt 5.8），但在 5.7.0.5 中存在／已註冊。副檔名是 `.mvt`／`.mbtiles`，**絕非** `.pbf`。

**證據。** `javap ImportMVTSort#match` → `ldc "MVT"; invokestatic FeatureDataSourceContentFactory.parse; ifnull → false else true`。`javap getContentMIME` → `ldc "MVT"`、`ldc "application/vnd.mapbox-vector-tile"`。`javap <init>` → `ldc "overlays"`（內嵌 String，**不是** `OVERLAYS_DIRECTORY` getstatic）、`R$string.mvt_file`、`R$drawable.ic_mvt`、`super(…)`。`javap ImportResolver#getDestinationPath` → `getfield _folderName; getItem; new File(File,name)`。`javap SpatialDbContentSource#importData(Uri)` → `contains`、`getHandler`、`isVisible`、`remove`、`processFile`。`javap #processFile` → `DataSourceDataStoreControl.contains` → `update` : `add(file,getProviderHint="MVT")`。`WktMapComponent L370` → `new MvtSpatialDb(spatialDb)` + `addContentSource`。

- [ImportMVTSort.java#L29 / #L42](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportMVTSort.java#L42)
- [MvtSpatialDb.java#L44 / #L54](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/MvtSpatialDb.java#L44)
- [SpatialDbContentSource.java#L221 / #L387](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/SpatialDbContentSource.java#L221)
- [WktMapComponent.java#L370](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/wkt/WktMapComponent.java#L370)

---

## 方法論與來源

**程式碼錨定規則（專案慣例）。** 每一項行為結論都**同時**錨定到權威 **5.7.0.5** `main.jar` 上的 `javap` `class#method` **以及**一條上游 **5.5.1.10** permalink。**兩者若不一致，以 5.7.0.5 SDK bytecode 為準**，文件會註明。permalink 用來確認形狀／契約；其**行號僅對 5.5.1.10 有效**（上游未發布 5.7.0.x 原始碼）。

**反組譯指令模式。**
```sh
JAR=<ATAK_SDK_5_7_0_5>/main.jar
javap -classpath "$JAR" -p        <fqcn>          # 簽章（存在性 / @Deprecated / 建構子形狀）
javap -classpath "$JAR" -p -c     <fqcn>          # bytecode（match/beginImport/getDestinationPath/onFileSorted）
javap -classpath "$JAR" -constants <fqcn>         # String 常數（資料夾名、MIME、content type）
```
代表性反組譯類別：`ImportCotSort`、`ImportMissionPackageSort`、`ImportKMLSort`、`ImportKMZResolver`／`ImportKMZSort`／`ImportKMZPackageSort`、`ImportGRGSort`／`ImportGRGResolver`、`ImportDTEDSort`／`ImportDTEDZSort`、`ImportUserIconSetSort`、`ImportMVTSort`；外加 receiver／importer `ImportResolver`、`ImportExportMapComponent`、`MapItemImporter`／`MarkerImporter`、`MissionPackageExtractorFactory`、`KMZPackageImporter`、`GRGMapComponent`／`GRGDiscovery`／`PersistentRasterDataStore`／`LocalRasterDataStore`、`DTEDZCallback`、`IconsMapAdapter`／`UserIconDatabase`、`MvtSpatialDb`／`SpatialDbContentSource`／`WktMapComponent`；以及常數持有者 `FileSystemUtils`。

**版本漂移摘要。** Bytecode（5.7.0.5）與 clone（5.5.1.10）在每一條稽核路徑上**控制流一致**。已知的結構性漂移（行為不變）：
- **Resolver 基底類別移動。** `com.atakmap.android.importfiles.sort.ImportResolver`（5.5.1.10）→ `gov.tak.api.importfiles.ImportResolver`（5.7.0.5）。舊版 `Import*Sort` 類別為 `@Deprecated`／`@DeprecatedApi(removeAt 5.8)`；目前的 `gov.tak.api…Import*Resolver` 替代品存在且已註冊。各格式的目的地 + 衝突語意在這對類別間完全相同。
- **CoT 新標記呼叫形狀。** 5.7.0.5 把新標記建立路由經由 `createMarker(CotEvent,Bundle)` helper（帶 `doNotRecreate`→`FAILURE` 守衛），而非在 clone 的 `:302/:306/:339` 直接 `new Marker(uid)`。
- **DTED dest 常數。** 5.5.1.10 建構子傳入 `FileSystemUtils.DTED_DIRECTORY`；5.7.0.5 傳入字面 `"DTED"`——兩者皆解析為 `getItem("DTED")`。
- **GRG dest 常數。** 5.7.0.5 `ImportGRGResolver` 在建構子呼叫 `FileSystemUtils.getItem("grg")`（傳入一個 `File`），對比舊版 `Sort` 傳入裸的 `"grg"` 資料夾字串——解析後路徑相同。

**逐格式的歧異／不確定結論（請遵守）：**
- **CoT——已更正（行為不變）：** `match` 要求前 384 char 內**同時**含 `<event` **與** `<point`（由 `.cot` FileFilter 把關），且目的地**不是** `cot/` 資料夾——資料夾引數為 `""` 且 `getDestinationPath` 回傳檔案本身；磁碟上唯一的複本是來源，若位於 `cache/atakdata/` 下則被 secure-delete。注意：在 `ignoreLateCoTEvents` 下為 last-by-event-time-wins，而非無條件的 last-received。
- **KML——衝突結論已更正：** 檔案系統的路徑／檔名覆寫已由 `ImportKMLSort`／`ImportResolver` 確認；「spatial-DB 先移除再重加」是**下游** KML importer 的性質，從 resolver bytecode **無法證明**——不要把它歸給 resolver。
- **MVT——引用已更正（不影響實質）：** 類別是 `ImportMVTSort`（非 `ImportMVTResolver`）；`"overlays"` 是內嵌 String 常數，而非 `OVERLAYS_DIRECTORY` 欄位引用。
- **GRG——衝突鍵命名：** schema enum 缺少 "file-path" 選項；真正的鍵是**絕對檔案路徑**（`queryCatalog(File)`），由 file-tree 現時性（大小／時間戳）重新驗證，而非內容雜湊或 UID。"geographic-cell-path" 是最接近的標籤。
- **影像——已更正 → 確認：** 專屬 resolver **確實存在**。`ImportJPEGSort`（+ 新 API `ImportJPEGResolver`）為 `.jpg`/`.jpeg` 註冊，並以 EXIF **GPS** 為閘門（`isJpegExif` → `getGPS() != null`）；資料夾為 `null` → ATAK **根目錄**；content `("JPEG Image","image/jpeg")` 由影像 IPP importer（`image.ipp.ImportImageSort`）消費。*（先前「沒有專屬 image importer」的解讀，是此格式的分析 agent 未回傳結構化輸出所致，已由直接反組譯更正。未地理標記／內嵌影像仍走 iconset／GRG／附件路徑。）*
- **Layers——已更正 → 確認：** 專屬 resolver **確實存在**。`ImportLayersSort`（在 `ImportFilesTask` 註冊）ext 為 `null`、資料夾為 `imagery`，經 `ImageryFileType` / `GeoPackage` / `MBTilesInfo` / `StreamingTiles` / `DatasetDescriptorFactory2` 做內容嗅探，並送出 `("External Native Data","application/octet-stream")` 由 `LayersMapComponent` 消費。*（先前「沒有單一 layers importer」的解讀同樣是分析 agent 失敗所致，已由直接反組譯更正。它是 GRG 的姊妹：`imagery/` vs `grg/`。）*

**關於 `cot`、`datapackage`、`kml`、`kmz` 的相同 UID 碰撞細節**，見配套的 [`README.md`](./README.md)（Layer A item-UID §2、Layer B 容器 §3、Layer C overlay payload §4）。
