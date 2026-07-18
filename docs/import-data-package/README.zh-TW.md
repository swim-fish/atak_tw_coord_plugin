# ATAK 資料包（Data Package）匯入：相同（已存在）UID 如何處理

> **本文回答的問題：** 匯入 ATAK 資料包時，遇到 UID 衝突（已存在）會如何處理——是覆寫，還是依更新時間（update time）保留較新者？
>
> **證據來源。** *權威（反組譯）bytecode：* ATAK-CIV **5.7.0.5** SDK `main.jar`（`<ATAK_SDK_5_7_0_5>/main.jar`，33,121,240 bytes），以及實際出貨的執行期 APK `<ATAK_SDK_5_7_0_5>/atak.apk`（SHA-256 `21ea6b363ee94f659539fac195fedc1a140dec06d0ebc23d01dc528601597508`）。*可讀交叉參照：* 本機上游 clone `TAK-Product-Center/atak-civ`，tag **5.5.1.10**（commit `9f6893dd657feacc35ec5de03dad721c2e44170e`）。
>
> **日期：** 2026-06-17。

> ⚠️ **版本漂移（version drift）——請先讀這段。** 下文所有行為結論的**權威**來源是 **5.7.0.5** 反組譯 bytecode。**可讀**交叉參照（以及本文所有 GitHub permalink）指向 **5.5.1.10**——不同的發行線。本文檢視的所有程式路徑，兩者一致（public method 簽章相同、控制流相符），未觀察到行為差異。**兩者若有任何不一致，以 5.7.0.5 bytecode 為準。** 因此 GitHub permalink 用來佐證*形狀與契約*，但其**行號僅對 5.5.1.10 有效**（上游未發布 5.7.0.x 原始碼）。

---

## TL;DR — 結論

共有**兩個獨立層級**，對問題的答案不同。請先釐清你指的是哪一個。

### Layer A — 套件「內部」的 CoT 項目／標記（以 **item UID** 比對）

- **就地覆寫，不會產生重複。** 當 CoT event 的 UID 已存在於地圖上時，ATAK 透過 `findItem` → `deepFindUID` 取回**同一個既有 `MapItem` 物件**並就地修改（`setPoint`、`setType`、`processDetails`、`refresh`）。只有在 UID 找不到時（`existing == null`）才會配置新物件。把同一個物件重新加入群組是冪等的（提前 return；群組儲存以每物件的 `serialId` 為鍵，而非 UID）。**同一個 UID 永遠不會產生第二個項目。** *(confirmed)*
- **預設為 last-received-wins（不比更新時間）。** stock build 上，map item **沒有**「依時間戳保留較新者」的規則。最後抵達的 event **一律覆寫**既有項目，不論其 `<event time>` 為何。 *(confirmed)*
- **「依時間戳保留較新者」確實存在，但預設休眠。** 嚴格的 newer-wins 機制（丟棄時間戳 `<=` 既有者——即較舊**與**時間戳相等的重複者）只有在欄位 `ignoreLateCoTEvents` 為 `true` 時才生效。該欄位在 `MapItemImporter` 建構子中由開發者／debug 選項 `mapitemimporter.ignore-late-cot`（**預設 `0` → false**）設定**一次**，且無任何 importer 子類別開啟它。所以 **stock build 上，所有 map-item CoT 匯入的時間 gate 都是關閉的。** *(confirmed)*
- Mission/Data Package（`FROM_MISSIONPACKAGE`）與 StateSaver（`FROM_STATESAVER`）匯入**不會**被特別處理為依時間丟棄——這些旗標只控制通知與持久化，不決定哪個 event 勝出。 *(confirmed)*

### Layer B — 資料包「容器」本身（以 **package／manifest UID** 比對）

- **持久性去重決策依「內容雜湊」，非 package UID。** 對於透過網路接收的套件，重複偵測以 **(傳輸名稱／使用者 label + SHA-256 內容雜湊)** 比對 SAVED file-info 表——**package UID 明確不被使用**。 *(confirmed)*
- **同 label + 同雜湊、檔案仍在磁碟上 → 跳過**（無使用者對話方塊；只有一則 "already exists" 通知 + 一筆 RECV 傳輸 log）。若 DB 有紀錄但檔案已不在 → **重新下載**。 *(confirmed)*
- **內容不同（雜湊不同）→ 視為新檔並繼續處理。** 解壓縮時會**就地覆寫磁碟**，因為解壓縮目錄**就是**以 package UID 命名（`<missionPackageFilesPath>/<manifest UID>/<contentUid>`），且檔案以 `renameIfExists = false` 解壓縮（log「File already exists, over-writing」，就地截斷覆寫）。UID 目錄只有在**空的時候**才會刪除，故前一個較大的同 UID 解壓縮殘留可能留存。 *(confirmed)*
- **此接收路徑上沒有以 UID 為鍵的「替換前一份」紀錄，也沒有重複衝突的 AlertDialog。**（`FileInfoPersistanceHelper` 提供以 label+hash 與以檔名查詢，但**沒有 `getFileInfoFromUid`**。） *(confirmed)*

### Layer C — 檔案內容型 payload（KMZ／KML／GeoJSON overlay，例如 `CCTV.zip`）— 見 §4

- payload 為**overlay 檔案**（非 CoT）的套件走**第三條路徑**：容器解壓縮後，檔案由 `ImportResolver` 框架匯入，overlay 以**目的檔名為鍵**（`…/overlays/<name>`），**不**以 package UID、**不**以時間戳。檔名相同 → overlay **就地覆寫**；檔名不同 → 新增一個並存的 overlay。要乾淨地推送更新：**改內容、保留相同 package UID 與相同內容檔名**（§4.3）。 *(confirmed)*

**結論。** 相同 UID 的*項目*一律**就地覆寫**（預設 last-received-wins；嚴格 newer-wins 僅在 debug 旗標下）。*套件容器*依**內容雜湊**去重，而非 UID——相同內容跳過，內容變更則繼續並**逐檔覆寫以 UID 命名的解壓縮目錄**。兩個層級的預設都不是「依更新時間保留較新者」。

---

## 1. 範圍與術語

- **Data Package = Mission Package。** 兩個詞在 ATAK 程式碼中可互換；類別位於 `com.atakmap.android.missionpackage.*`。資料包是一個 ZIP，內含一份 **manifest** 加上內容（CoT `.cot` 檔、附件等）。
- **兩種不同的 UID：**
  - **Item UID** — CoT `<event>`（標記／map item）的 `uid` 屬性。對活動中的地圖 graph 解析。→ **Layer A**。
  - **Manifest／package UID** — 容器自身的 UID，存為名為 `"uid"` 的 manifest *Configuration parameter*（`MissionPackageConfiguration.PARAMETER_UID = "uid"`），**並非**頂層欄位。透過 `MissionPackageManifest.getUID()` 讀取。→ **Layer B**。
- **匯入來源旗標。** `MapItemImporter` 以一個 `from` 字串標記匯入。`FROM_STATESAVER = "StateSaver"` 與 `FROM_MISSIONPACKAGE = "MissionPackage"` 控制**通知抑制**（`isLocalImport`）與**持久化跳過**（`isStateSaverImport` → `persist`），都不會餵入時間比較。
- **各層級在哪裡執行。** 資料包的 CoT 內容被當作一般 CoT event 派送進**項目匯入**管線（Layer A）。套件*檔案本身*——接收、去重、解壓縮到磁碟——是**容器**管線（Layer B）。檔案型 payload（KMZ/KML 等）則走**內容匯入**管線（Layer C，§4）。

---

## 2. Layer A — item UID 衝突

### 2.1 以 UID 取回既有項目（`findItem` → `deepFindUID`）

`MapItemImporter.importData(CotEvent, Bundle)` 在呼叫 `importMapItem` **之前**先呼叫 `findItem(event)`，並把結果當作 *existing* 引數傳入。`findItem(CotEvent)` 取出 UID 後委派給 `findItem(String)`，後者透過 `MapGroup.deepFindUID(uid)` 對活動地圖 graph 解析。因此已存在的 UID 會得到**既有物件參照**，由子型別接著修改。`importData` 在此路徑上**不會**建立任何 `MapItem`。 *(verdict: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `com.atakmap.android.cot.importer.MapItemImporter#importData`
> ```
> 16: aload_0
> 17: aload_1                       // event
> 18: invokevirtual #111            // findItem:(LCotEvent;)LMapItem;
> 21: astore_3                      // existing -> local 3
> ...
> 61: aload_0
> 62: aload_3                       // existing
> 63: aload_1                       // event
> 64: aload_2                       // extras
> 65: invokevirtual #129            // importMapItem:(LMapItem;LCotEvent;LBundle;)L...ImportResult;
> ```
> `com.atakmap.android.cot.importer.MapItemImporter#findItem(CotEvent)`
> ```
> 2: invokevirtual #115            // CotEvent.getUID:()Ljava/lang/String;
> 5: invokevirtual #251            // findItem:(Ljava/lang/String;)LMapItem;
> ```
> `com.atakmap.android.cot.importer.MapItemImporter#findItem(String)`
> ```
> 1: getfield #29 _mapView
> 4: invokevirtual #241            // MapView.getRootGroup:()LRootMapGroup;
> 14: invokevirtual #245           // MapGroup.deepFindUID:(Ljava/lang/String;)LMapItem;
> 17: areturn
> ```
> `importMapItem(MapItem, CotEvent, Bundle)` 宣告為 `protected abstract`——create-vs-update 決策委派給子型別（§2.2），不在 `importData` 中決定。
>
> **可讀交叉參照（5.5.1.10，行號僅對該發行版有效）：**
> `MapItemImporter.java:98` `MapItem existing = findItem(event);`；`:112` `importMapItem(existing, event, extras)`；`:190` `findItem(String)` → `deepFindUID`；`:201` `findItem(CotEvent)`。
> - [MapItemImporter.importData L94](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java#L94)
> - [MapItemImporter.findItem(String) L190](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java#L190)
> - [MapItemImporter.findItem(CotEvent) L201](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java#L201)

**查詢機制的待確認細節（uncertain — 見 §7）。** `DefaultMapGroup.deepFindUID` 是線性深度優先的 `getUID().equals(uid)` 掃描。然而*活動 graph* 的進入點走的是 `RootMapGroup`，它**覆寫**了 `deepFindUID`，改用 O(1) 的 `FastUIDLookup` 雜湊索引，miss 時退回 metadata-map 查詢——**而非**線性掃描。無論如何，鍵都是 **UID**、回傳的是**既有的活動參照**；對抗式審查標記為「與原描述不符」的只是*比對策略*（雜湊索引 vs 線性掃描）。

### 2.2 就地更新 vs 重複

`MarkerImporter.importMapItem`（具體實作）把傳入的 *existing* 項目轉型為 `Marker` 並據此分支：若**非 null**，分支會**跳過**建立區塊、就地更新**同一個實例**；只有在 `existing == null` 時才建立新的 `Marker`（即使如此，一個 `doNotRecreate` bundle 旗標仍可改為回傳 `FAILURE`）。`createMarker`——`new Marker(event.getUID())`——是整個類別中**唯一**的 `Marker` 配置點，因此既有 UID 路徑永遠不可能配置出第二個 marker。重新加入群組是冪等的：`MapGroup.addItem` 在項目已在群組中時提前 return，而 `DefaultMapGroup.addItemImpl` 以每物件的 `getSerialId()`（**非** UID）為鍵存入 `Map`。 *(verdict: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `com.atakmap.android.cot.importer.MarkerImporter#importMapItem`
> ```
> 46: aload_1; checkcast #41 Marker; astore 10     // existing -> local 10
> 52: aload 10; ifnonnull 84                        // existing != null -> 跳過建立區塊
> 57..69: doNotRecreate guard -> ImportResult.FAILURE
> 70: invokevirtual #73 createMarker(...) ; astore 10 ; needsRefresh=1   // 僅在 existing==null
> ...  // 更新路徑就地修改同一個 local 10：
> 249: aload 10 ; aload 11 ; invokevirtual #148 Marker.setPoint   // 更新路徑上無條件執行
> 361: invokevirtual #202 Marker.setType
> 570: invokestatic  #284 CotDetailManager.processDetails(marker,event)
> 649: invokevirtual #302 addToGroup
> 1090: invokevirtual #403 Marker.refresh
> ```
> `com.atakmap.android.cot.importer.MarkerImporter#createMarker` — **唯一**的 `new Marker`：
> ```
> 0: new #41 Marker ; 3: dup ; 4: aload_1
> 5: invokevirtual #425 CotEvent.getUID
> 8: invokespecial #426 Marker.<init>:(Ljava/lang/String;)V
> ```
> `com.atakmap.android.maps.MapGroup#addItem`（冪等重新加入）：
> ```
> 0: aload_1 ; 1: ifnull 12
> 4: aload_1 ; 5: invokevirtual #69 MapItem.getGroup ; 8: aload_0
> 9: if_acmpne 13 ; 12: return          // 已在此群組 -> no-op
> ```
> `com.atakmap.android.maps.DefaultMapGroup#addItemImpl`（以 serialId 為鍵儲存，非 UID）：
> ```
> 1: getfield _items:Ljava/util/Map;
> 5: invokevirtual #181 MapItem.getSerialId:()J ; invokestatic Long.valueOf
> 12: invokeinterface #166 Map.put
> ```
> *（原始筆記與驗證後反組譯間有些微 offset 漂移——例如 `setPoint` 在 249 vs 245、`setType` 在 361 vs 359——不改變指令本體或控制流。）*
>
> **可讀交叉參照（5.5.1.10）：** `MarkerImporter.java:86-94` `Marker marker = (Marker) existing; if (marker == null) { if (doNotRecreate) return FAILURE; marker = createMarker(event, extras); needsRefresh = true; } else pointBefore = marker.getPoint();`。`MapItemImporter.java:210-214` `group.addItem(item);`（「群組轉移在 addItem 方法內處理」）。
> - [MarkerImporter.importMapItem L74](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MarkerImporter.java#L74)

### 2.3 時間戳／更新時間處理（`ignoreLateCoTEvents`、late-event 丟棄）

**預設路徑（gate 關閉——stock build）：無時間比較；覆寫無條件執行。** `MarkerImporter.importMapItem` 不含任何把 `CotEvent.getTime/getStart/getStale` 與既有項目儲存時間做比較以決定接受／拒絕的程式碼。該方法僅有的三個 `lcmp` 運算是：(1) 一個 `lastUpdateTime >= 0` 守衛，用來 gate **dead-reckoning** 推估區塊（`est.speed`/`est.course`/`est.dist`，由*目前牆鐘時間*計算，而非 event 時間）；(2) 一個 `__detailsCRC` 比較，用來設定 `needsRefresh` 旗標；(3) 一個 `autoStaleDuration >= 0` clamp。沒有任何一個 gate 住覆寫——`setPoint` 一律執行。**時間戳較舊**的同 UID event 仍會移動／覆寫該 marker。 *(verdict: confirmed)*

**選用 gate（僅在 debug 旗標下開啟）：嚴格 newer-wins。** late-event 丟棄位於 `importMapItem` **之外**，在 `MapItemImporter.importData` 內：若 `ignoreLateCoTEvents` 為 true，它呼叫 `TimeTrackingProcessService.begin(uid, event.getTime())`；回傳 **null** token → `ImportResult.IGNORE`（在任何覆寫之前丟棄 event）。`begin` → `getReplacementPendingToken` 以 `lcmp; ifle` 把傳入時間戳與 pending／committed 儲存時間戳比較——亦即時間 **`<=`** 儲存值的 event 會被丟棄；**只有嚴格大於**才取代（時間戳相等者被當作「不夠新」而拒絕）。`ignoreLateCoTEvents` 為 `protected final`，在建構子中由 `DeveloperOptions.getIntOption("mapitemimporter.ignore-late-cot", 0) != 0` 設定**一次**（**預設 false**）；無子類別重新指派。因此這是**debug 專用的功能旗標**，非預設行為。 *(verdicts: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `com.atakmap.android.cot.importer.MapItemImporter#<init>`（該欄位*唯一*的寫入處）：
> ```
> 59: ldc #57            // "mapitemimporter.ignore-late-cot"
> 61: iconst_0           // 預設 0
> 62: invokestatic #59   // DeveloperOptions.getIntOption:(String,I)I
> 65: ifeq 72 ; 68: iconst_1 ; 69: goto 73 ; 72: iconst_0
> 73: putfield #65       // ignoreLateCoTEvents:Z   (== getIntOption(...,0) != 0)
> ```
> 欄位為 `protected final boolean ignoreLateCoTEvents;`。對整個類別 `grep -c 'putfield #65'` = **1**（只有此建構子寫入）。
>
> `com.atakmap.android.cot.importer.MapItemImporter#importData`（gate）：
> ```
> 25/26: getfield #65 ignoreLateCoTEvents:Z
> 29: ifeq 58                                   // gate 關 -> 跳過 begin()，不做時間檢查
> 32..44: invokevirtual TimeTrackingProcessService.begin(getUID, getTime)
> 49: ifnonnull 58
> 54: getstatic ImportResult.IGNORE ; 57: areturn   // 較舊/相等 event 被丟棄（僅 gate 開啟時）
> 61/65: invokevirtual #129 importMapItem            // 否則無條件執行
> ```
> `com.atakmap.android.util.TimeTrackingProcessService#getReplacementPendingToken`（嚴格 newer-wins）：
> ```
> 4: lload_2                              // 傳入 ts
> 5: invokestatic PendingToken.access$400 // existing.newTimestamp
> 9: lcmp ; 10: ifle 99                   // 傳入 <= pending -> 回傳 existing（=> null token）
> ...
> 73: invokestatic TimeRecord.access$000  // committed timeRecord.timestamp
> 76: lcmp ; 77: ifle 99                  // 傳入 <= committed -> 丟棄
> 99: aload_1 ; 100: areturn
> ```
> `com.atakmap.android.cot.importer.MarkerImporter#importMapItem`（預設路徑無時間 gate）：`lcmp@160` `lastUpdateTime>=0`（僅守衛 est.*）、`setPoint@249` 無條件、`lcmp@860` `__detailsCRC`、`getStale@948`/`getStart@952` → `autoStaleDuration`、`lcmp@968` clamp。**方法內沒有任何 `CotEvent.getTime`、沒有 `CoordinatedTime.before/after/compareTo`。**
>
> **版本交叉確認：** 5.7.0.5 與 5.7.0.3 bytecode 在此路徑上**逐位元組相同**（建構子 `putfield`、`importData` gate、`getReplacementPendingToken` `lcmp/ifle 99`）。
>
> **可讀交叉參照（5.5.1.10）：** `MapItemImporter.java:79-80` 欄位初始化 `= (DeveloperOptions.getIntOption("mapitemimporter.ignore-late-cot", 0) != 0)`；`:101-108` `if (ignoreLateCoTEvents) { processToken = begin(...); if (processToken == null) return IGNORE; }`。`TimeTrackingProcessService.java:181` `if (timestamp > existing.newTimestamp)`、`:190` `if (timestamp > timeRecord.timestamp)`、`:194` `return existing;`。

---

## 3. Layer B — 資料包容器 UID

### 3.1 package UID 是 manifest *parameter*，而非頂層欄位

`MissionPackageManifest.getUID()` 讀取名為 `"uid"` 的 configuration parameter（`MissionPackageConfiguration.PARAMETER_UID`）——`_configuration.getParameter("uid").getValue()`，不存在時回傳 `null`。`getParameter` 是對 `_parameters` `List<NameValuePair>` 的線性掃描。沒有 public `uid` 欄位。 *(verdict: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `com.atakmap.android.missionpackage.file.MissionPackageManifest#getUID`
> ```
> 1: getfield #47 _configuration
> 4: ldc #146 "uid"
> 6: invokevirtual #148 MissionPackageConfiguration.getParameter:(String)LNameValuePair;
> 11: ifnonnull 18 ; 14: aconst_null ; 15: goto 22
> 19: invokevirtual #152 NameValuePair.getValue:()Ljava/lang/String;
> 22: areturn
> ```
> `MissionPackageConfiguration` 暴露 `public static final String PARAMETER_UID`（= `"uid"`）。5.7.0.5 vs 5.7.0.3 `getUID` 逐位元組相同。
>
> **可讀交叉參照（5.5.1.10）：** `MissionPackageConfiguration.java:30` `PARAMETER_UID = "uid"`；manifest UID 在建構子中指派（`setUID`，預設建構子產生 `UUID.randomUUID()`）。
> - [MissionPackageManifest.getUID L217](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/file/MissionPackageManifest.java#L217)
> - [MissionPackageManifest ctor L103](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/file/MissionPackageManifest.java#L103)

### 3.2 接收時去重以**內容雜湊**進行，而非 package UID

對於**透過網路接收**的套件（CoT FileTransfer / commo），`MissionPackageReceiver.preprocessMPReceive` 以 **(傳輸名稱／使用者 label, SHA-256 雜湊)** 比對 **SAVED** file-info 表來偵測重複——`FileInfoPersistanceHelper.getFileInfoFromUserLabelHash(transferName, sha256, TABLETYPE.SAVED)`。package UID **完全不被查詢**（原始碼帶有明確註解「we currently match on user label and SHA256 (not using package UID)」）。若有一列相符**且**後端檔案仍存在 → 通知「already exists with checksum」、寫一筆 `RECV` 傳輸 log、回傳 `false`；`handleCoTFileTransfer` 與 `initiateReceive` 都把 `false` 當作「我們已經有了」而跳過下載。若該列存在但檔案已遺失 → 記一筆警告並回傳 `true` 以重新下載。helper 的 API 介面中**沒有 `getFileInfoFromUid`**。 *(verdict: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `com.atakmap.android.missionpackage.MissionPackageReceiver#preprocessMPReceive`
> ```
> 57: invokestatic FileInfoPersistanceHelper.instance
> 62: getstatic TABLETYPE.SAVED
> 65: invokevirtual #931 getFileInfoFromUserLabelHash:(String;String;TABLETYPE)LAndroidFileInfo;
> 72: ifnull 237                         // 無此列 -> 繼續（回傳 true）
> 77: invokevirtual AndroidFileInfo.file
> 80: invokestatic FileSystemUtils.isFile
> 83: ifeq 201                           // 列存在但檔案遺失 -> 警告、回傳 true（重新下載）
> ...148: getstatic FileTransferLog$TYPE.RECV ; 195: insertLog
> 199: iconst_0 ; 200: ireturn           // 已擁有 -> 跳過
> ```
> `handleCoTFileTransfer`：`... invokespecial preprocessMPReceive ; ifne 25 ; return`。
> `initiateReceive`：`... invokespecial preprocessMPReceive ; ifne 14 ; aconst_null ; areturn`。
> `com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper` 的 public 方法：`getFileInfoFromUserLabelHash`、`getFileInfoFromUserLabel`、`getFileInfoFromFilename` — **沒有 `getFileInfoFromUid`**。
>
> *誠實標註：* 去重的**讀取**側已完全證實；填入 SAVED 列（儲存時的 label + sha256 + filename）的**寫入**側未完全反組譯——由相符的讀取鍵與缺乏任何 UID 鍵查詢推論而得。
>
> **可讀交叉參照（5.5.1.10）：** `MissionPackageReceiver.java:1043-1044` 決定性註解（「match on user label and SHA256 (not using package UID)」）；跳過 return 於 `:1089`；呼叫端 `:1100` / `:1112`。
> - [MissionPackageReceiver.preprocessMPReceive L1034](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/MissionPackageReceiver.java#L1034)
> - [MissionPackageReceiver.handleCoTFileTransfer L1100](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/MissionPackageReceiver.java#L1100)

### 3.3 磁碟上：解壓縮以 package UID 為鍵並**就地覆寫**

解壓縮內容寫入 `<missionPackageFilesPath>/<manifest UID>/<contentUid>`，因此 **package UID 是磁碟上的父目錄鍵**。`MissionPackageEventHandler2.extract` 由 `getMissionPackageFilesPath(...) + separator + manifest.getUID()` 組出目的地，並呼叫 `MissionPackageExtractor.UnzipFile(in, target, renameIfExists = false, buffer)`。當 target 已存在且 `renameIfExists == false`，`UnzipFile` 記錄 **「File already exists, over-writing:」** 並寫入**同一路徑**（截斷／覆寫）。因此以**相同 UID** 重新匯入套件會撞上以 UID 命名的目錄，並**逐檔依名覆寫——不改名、不提示**。 *(verdict: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `com.atakmap.android.missionpackage.event.MissionPackageEventHandler2#extract`
> ```
> 86: invokestatic MissionPackageFileIO.getMissionPackageFilesPath
> 92: getstatic File.separatorChar
> 99: invokevirtual MissionPackageManifest.getUID
> 108: invokestatic FileSystemUtils.sanitizeWithSpacesAndSlashes
> ...113-129: new File(uidDir, sanitize(content.getManifestUid()))
> 139: iconst_0                         // renameIfExists = false
> 142: invokestatic MissionPackageExtractor.UnzipFile:(LInputStream;LFile;Z[B)V
> ```
> `com.atakmap.android.missionpackage.file.MissionPackageExtractor#UnzipFile(InputStream,File,boolean,byte[])`
> ```
> 57: invokestatic IOProviderFactory.exists(file) ; 61: ifeq 132
> 64: iload_2 (renameIfExists) ; 65: ifeq 105       // false -> 覆寫分支（路徑不變）
> 105..: ldc "File already exists, over-writing: "
> 159: getOutputStream(new File(filepath)) ; 173: FileSystemUtils.copyStream   // 截斷/覆寫
> ```
> **可讀交叉參照（5.5.1.10）：** `MissionPackageEventHandler2.java:96-105`；`MissionPackageExtractor.java:206-213`（`else { Log.d(... "File already exists, over-writing: " + filepath); }`）。

### 3.4 解壓縮後清理只在目錄**為空時**刪除

`MissionPackageExtractor.extract` 重算 `unzipDir = <filesPath>/<manifest UID>`，並**僅在** `listFiles() == null || length < 1`（空）時 `deleteDirectory(unzipDir, false)`。**非空**的同 UID 目錄會原封保留。結合 §3.3 的逐檔覆寫，先前內容是**逐檔**取代、而非整組清空——因此前一個（較大的）同 UID 套件的**殘留檔案**可能在較小的重新匯入後留存。 *(verdict: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `com.atakmap.android.missionpackage.file.MissionPackageExtractor#extract`
> ```
> 816: invokestatic getMissionPackageFilesPath
> 821: invokevirtual MissionPackageManifest.getUID
> 824: new java/io/File
> 839: IOProviderFactory.isDirectory
> 847: IOProviderFactory.listFiles ; arraylength ; if_icmpge ...   // length < 1 守衛
> 867: invokestatic FileSystemUtils.deleteDirectory:(File;Z)V       // 僅限空目錄
> ```
> **可讀交叉參照（5.5.1.10）：** `MissionPackageExtractor.java:149-159` `if (files == null || files.length < 1) FileSystemUtils.deleteDirectory(unzipDir, false);`。

### 3.5 session 內守衛（`isAlreadyDownloaded`）——複合鍵、非單以 UID、非持久

`MissionPackageDownloader` 加上第二道、**session 範圍**的記憶體內守衛。`isAlreadyDownloaded(FileTransfer)` 以 `getDownloadKey(ftr)` 為鍵存取記憶體內 `HashMap<String,FileTransfer> _downloaded`，該鍵是 `name + size + uid + localPath + senderUID + sha256` 的**複合**（六段以逗號相接——UID 只是其一）。它防止在一個 session 內重複處理同一筆**進行中**傳輸，但**不是**持久的、以 UID 為鍵的替換／跳過；持久 gate 仍是 §3.2 的 label+sha256 檢查。（進來的暫存下載檔*確實*以 `ftr.getUID()` 命名於 incoming-download 路徑下。） *(verdict: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `com.atakmap.android.missionpackage.http.MissionPackageDownloader#isAlreadyDownloaded`
> ```
> 8: invokespecial getDownloadKey
> 13: getfield #21 _downloaded:Map
> 17: invokeinterface Map.containsKey
> 22: ifeq 52 ; 50: iconst_1 ; 51: ireturn
> ```
> `getDownloadKey` → `getName() + "," + getSize() + "," + getUID() + "," + getLocalPath() + "," + getSenderUID() + "," + getSHA256(false)`；`_downloaded = new HashMap<>()` 於 `<init>`；暫存檔 `new File(getMissionPackageIncomingDownloadPath(...), ftr.getUID())`。

### 3.6 容器層淨行為

| 接收情境（相同 package UID） | 由什麼決定 | 結果 |
|---|---|---|
| 同 label + 同 SHA-256，檔案在磁碟上 | 內容雜湊（§3.2） | **跳過** — 無提示；"already exists" 通知 + RECV log |
| 同 label + 同 SHA-256，檔案遺失 | 內容雜湊（§3.2） | **重新下載**（記警告 log） |
| SHA-256 不同（內容變更） | 未相符 → 繼續；解壓縮以 UID 為鍵（§3.3） | **就地覆寫**，逐檔於 `<filesPath>/<uid>/…`，`renameIfExists=false`；可能殘留舊檔（§3.4） |

此接收路徑上**沒有**以 UID 為基礎的「替換前一份」紀錄，**也沒有**重複衝突的 AlertDialog。

---

## 4. 第三條路徑 — KMZ／KML overlay 資料包（實例：`CCTV.zip`）

§2–§3 涵蓋 payload 為 **CoT**（標記）加容器的套件。許多實際的資料包改為攜帶**檔案型 overlay** payload——KMZ/KML、GeoJSON、shapefile、影像 overlay 等。這些**不是** CoT event，故 **Layer A 不適用**：沒有 item UID、沒有 `ignoreLateCoTEvents`、沒有 last-received-wins 的 marker 競爭。取而代之，容器解壓縮後（Layer B），內容檔交給 **`ImportResolver` 框架**，後者以**目的檔名為鍵**註冊 overlay。`CCTV.zip` 正是這種形狀。

### 4.1 `CCTV.zip` 實際是什麼

```
MANIFEST/manifest.xml
c64e6cecb94cb5acf402db2e6030b7f0/CCTV.kmz      (一個 KMZ：doc.kml + 樣式 + 圖示)
```

manifest（原文）：

```xml
<MissionPackageManifest version="2">
  <Configuration>
    <Parameter name="uid"  value="de8e3081-5bfa-4741-98b0-96fad43f14fd"/>
    <Parameter name="name" value="CCTV.kmz"/>
    <Parameter name="onReceiveImport" value="true"/>
    <Parameter name="onReceiveDelete" value="true"/>
  </Configuration>
  <Contents>
    <Content ignore="false" zipEntry="c64e6cecb94cb5acf402db2e6030b7f0/CCTV.kmz">
      <Parameter name="name" value="CCTV.kmz"/>
      <Parameter name="contentType" value="KML"/>
      <Parameter name="visible" value="true"/>
    </Content>
  </Contents>
</MissionPackageManifest>
```

- 整包 SHA-256 `50e6c5ae…`（接收去重鍵，§3.2）；內層 KMZ SHA-256 `5f6d74fa…`。
- 內層目錄 `c64e6cec…` 是**不透明的 content id**，*並非*內容的雜湊（內層 KMZ 的 MD5 是 `7670c49e…`；檔名的 MD5 是 `288e3c58…`——皆不相符）。內容變更時它不需要改變。
- `onReceiveImport=true` → 解壓縮後 ATAK 自動匯入 `CCTV.kmz`；`onReceiveDelete=true` → 移除套件即移除已匯入的 overlay。

### 4.2 解壓縮後，內容以**目的檔名**匯入

`MissionPackageEventHandler2.importFile(...)`（由 `extract` 對每個 `<Content>` 呼叫一次）為檔案尋找相符的 `ImportResolver` 並呼叫 `sorter.beginImport(file, flags)`。`.kmz` 的 resolver 是 `ImportKMZPackageResolver`，以目的資料夾 **`overlays`** 建構；`.kml` 則是 `ImportKMLSort`，同樣是 `overlays`。`ImportResolver.getDestinationPath(file)` 組出 `new File(destinationDir, file.getName())`——即 `…/overlays/CCTV.kmz`——而 `beginImport` 把解壓縮出的檔案複製到該處（`IMPORT_COPY` 下的 `FileSystemUtils.copyFile(file, dest)`）。一般的檔案複製到既有路徑會**就地覆寫**。所以 overlay 的識別是它的**目的檔名 `CCTV.kmz`**——與 package UID 無關、與內容雜湊無關。檔名相同 → 同一個 `overlays/CCTV.kmz` 被覆寫、顯示的圖層被取代；檔名不同 → 新增一個獨立的 overlay（舊的不會被移除）。 *(verdict: confirmed)*

> **證據（5.7.0.5 bytecode — 權威）**
>
> `…importfiles…ImportResolver#getDestinationPath` → `new File(destinationDir, file.getName()[+ext])`：
> ```
> 1: invokevirtual File.getName
> 73: new java/io/File ; 78: getfield #30 destinationDir:Ljava/io/File; ; 82: File.<init>(File,String)
> ```
> `…ImportResolver#beginImport(File, EnumSet<SortFlags>)`：
> ```
> 13:  invokevirtual #119 getDestinationPath
> 180: getstatic     #171 SortFlags.IMPORT_COPY
> 239: invokestatic  #193 FileSystemUtils.copyFile:(File;File)V   // 依名覆寫
> 246: invokevirtual #197 onFileSorted
> ```
> `com.atakmap.android.importfiles.sort.ImportKMZPackageResolver#<init>` → `super(".kmz", FileSystemUtils.getItem("overlays"), …)`；`ImportKMLSort#<init>` → `super(".kml", "overlays", …)`。
> `com.atakmap.android.missionpackage.event.MissionPackageEventHandler2#importFile` → `sorter.getDestinationPath(file)` 後 `sorter.beginImport(file, flags)`。
>
> **版本漂移註記（此路徑特有）。** 在 **5.7.0.5** 中，resolver 基底類別是 `gov.tak.api.importfiles.ImportResolver`；在 **5.5.1.10** clone 中仍是 `com.atakmap.android.importfiles.sort.ImportResolver`——該類別在兩條發行線之間被**移動／更名**。`getDestinationPath` / `beginImport` 語意相同，僅 package 不同。（依 code-anchoring 規則，以 5.7.0.5 bytecode 為準。）
>
> **可讀交叉參照（5.5.1.10）：** `MissionPackageEventHandler2.java:146` `importFile(...)`、`:209` `sorter.beginImport(file, flags)`；`ImportResolver.java:202` `beginImport`、`:246` `FileSystemUtils.copyFile(file, dest)`；`ImportKMZPackageResolver.java:21` `super(".kmz", FileSystemUtils.getItem(OVERLAYS_DIRECTORY), …)`；`ImportKMLSort.java:39` `super(".kml", OVERLAYS_DIRECTORY, …)`。
> - [MissionPackageEventHandler2.importFile L146](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/event/MissionPackageEventHandler2.java#L146)
> - [ImportResolver.beginImport L202](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L202)
> - [ImportKMZPackageResolver L21](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportKMZPackageResolver.java#L21)
> - [ImportKMLSort L39](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportKMLSort.java#L39)
>
> *互動式的**覆寫 vs 捨棄**提示（`importmgr_overwrite_existing_import` / `…discard_the_new_resource`，§5）屬於**手動** import-manager / RemoteResource 流程。此處 `onReceiveImport` 資料包路徑是**靜默**複製——無提示。*

### 4.3 淨行為，以及新舊版本**要改哪邊**

三個**獨立**的識別鍵決定重新匯入的結果：

| 層級 | 鍵（以 `CCTV.zip` 為例） | 效果 |
|---|---|---|
| 接收去重（§3.2） | (傳輸 label, 整包 SHA-256 `50e6c5ae…`) | 位元組完全相同的 zip → **靜默跳過** |
| 解壓縮目錄（§3.3） | package UID `de8e3081-…` | 同 UID → 逐檔覆寫 `…/<uid>/…` |
| overlay 註冊（§4.2） | 內容檔名 `CCTV.kmz` → `overlays/CCTV.kmz` | 同檔名 → 取代同一個 overlay |

ATAK **不會**為套件挑選「日期較新者」——manifest 不帶任何接收路徑會比較的版本／時間欄位。**取代**由 **UID + 檔名**驅動；**跳過**由**內容雜湊**驅動。因此結果由你明確控制：

**目的 A — 推送更新，就地取代既有的 CCTV 圖層（最常見）：**
- **改**：KMZ 內容（編輯內層 `doc.kml`）。這會改變整包 SHA-256，故套件在接收時**不會被跳過**。*（必要：位元組完全相同的 zip 會被當作「already exists with checksum」丟棄。）*
- **保留**：`uid="de8e3081-…"` → 覆寫同一個解壓縮目錄；配合 `onReceiveDelete=true`，套件維持為單一受管單位（不產生重複的套件項目）。
- **保留**：內容檔名 `CCTV.kmz`（及 `name`）→ 落在同一個 `overlays/CCTV.kmz` → 顯示的 overlay 就地被取代。
- 內層 `c64e6cec…/` 目錄可維持不變（不透明 id）。

**目的 B — 當作獨立、並存的圖層（新舊並存）：**
- **改**：`uid` 換成新的 UUID，**且**內容檔名（例如 `CCTV_v2.kmz`，連同 `name`）。新舊並存。

**陷阱——鍵不一致：**
- 只改 UID、保留檔名 → 新增一個套件項目，但 overlay 仍覆寫 `overlays/CCTV.kmz` → 容器與 overlay 不一致。
- 只改檔名、保留 UID → 舊檔殘留在 `…/<uid>/…`（UID 目錄只在為空時刪除，§3.4），**且**舊的 `overlays/CCTV.kmz` **不會**被自動移除 → 殘留一個重複的 overlay。
- 期待「日期較新者勝」→ 套件層沒有這種機制；請改用**同 UID + 同檔名 + 改內容**。

---

## 5. APK 佐證

被反組譯的類別確實出貨於執行期，且 APK 與 SDK jar 版本相符。

- **APK 識別。** 路徑 `<ATAK_SDK_5_7_0_5>/atak.apk`；大小 **389,700,563** bytes；SHA-256 **`21ea6b363ee94f659539fac195fedc1a140dec06d0ebc23d01dc528601597508`**。
- **版本。** `versionName = "5.7.0.5 (3198049e)"`，package `com.atakmap.app@5.7.0.CIV`——由二進位 `AndroidManifest.xml` 透過 **UTF-16** 字串擷取（`strings -e l`）取得；純 ASCII grep 取不到，因為 versionName 以 UTF-16 存於 resource string pool。並由 SDK 資料夾名 `ATAK-CIV-5.7.0.5-SDK` 佐證。這正是被反組譯的 `main.jar` 對應的**確切**版本。

**被反組譯的類別確認存在於出貨 dex 中**（`classes.dex` 13.5 MB、`classes2.dex` 11.8 MB、`classes3.dex` 2.5 MB）：

| 類別 | 定義所在 dex |
|---|---|
| `com.atakmap.android.cot.importer.MapItemImporter` | `classes.dex` |
| `com.atakmap.android.cot.importer.MarkerImporter` | `classes.dex`（`classes2.dex` 內有交叉參照） |
| `com.atakmap.android.missionpackage.file.MissionPackageManifest` | `classes.dex`（`classes2.dex` 內有交叉參照） |
| `com.atakmap.android.missionpackage.MissionPackageReceiver` | `classes.dex` |

*（multidex 計數中，一個 descriptor 同時作為定義型別與跨 dex 參照而出現於多個 dex，是 multidex 的正常現象；存在性無歧義。）*

**衝突字串搜尋（dex 內逐字命中）。** APK **確實**含有與 bytecode 結論一致的覆寫／去重機制：

```
importmgr_overwrite_existing_import          importmgr_discard_the_new_resource
User selected to overwrite existing file:    Prompting user to overwrite on FTP server:
Overwriting existing file without prompting user:   Overwriting existing file with updates:
File already exists, checking if current:    File already exists, over-writing:
Cancelled import, not overwriting:           The destination file already exists.
File already exists, renaming to:            already exists. SHA256:
already exists with checksum:                Skipping already existing iconset:
Path Manipulation: Zip Entry Overwrite       Skipping duplicate wizard event
```

解讀：**通用 import 管理器**可以呈現**覆寫 vs 捨棄**選擇（`importmgr_overwrite_existing_import` / `importmgr_discard_the_new_resource`），但特定路徑也會**不提示直接覆寫**（「Overwriting existing file without prompting user:」），而**相同雜湊**的檔案會被視為已存在（「already exists with checksum:」/「already exists. SHA256:」）。`File already exists, over-writing:` 字串正是 §3.3 `UnzipFile` 覆寫分支。這些都佐證 Layer B：**雜湊去重 + 磁碟覆寫，資料包接收路徑上無逐 UID 的依時間競爭**。

---

## 6. 方法論與來源

**code-anchoring 規則（專案慣例）。** 每一條行為結論都同時錨定到權威 **5.7.0.5** `main.jar` 上的 `javap` class#method **以及**一個上游 **5.5.1.10** permalink。**兩者若不一致，以 5.7.0.5 SDK bytecode 為準**，文件亦會明說。permalink 佐證形狀／契約；其**行號僅對 5.5.1.10 有效**。

**反組譯指令（代表性）：**
```sh
JAR=<ATAK_SDK_5_7_0_5>/main.jar
javap -classpath "$JAR" -p   com.atakmap.android.cot.importer.MapItemImporter
javap -classpath "$JAR" -p -c com.atakmap.android.cot.importer.MapItemImporter
javap -classpath "$JAR" -p -c com.atakmap.android.cot.importer.MarkerImporter
javap -classpath "$JAR" -p -c com.atakmap.android.util.TimeTrackingProcessService
javap -classpath "$JAR" -p -c com.atakmap.android.missionpackage.file.MissionPackageManifest
javap -classpath "$JAR" -p -c com.atakmap.android.missionpackage.MissionPackageReceiver
javap -classpath "$JAR" -p -c com.atakmap.android.missionpackage.event.MissionPackageEventHandler2
javap -classpath "$JAR" -p -c com.atakmap.android.missionpackage.file.MissionPackageExtractor
javap -classpath "$JAR" -p -c com.atakmap.android.missionpackage.http.MissionPackageDownloader
javap -classpath "$JAR" -p -c com.atakmap.android.importfiles.sort.ImportKMLSort
javap -classpath "$JAR" -p -c com.atakmap.android.importfiles.sort.ImportKMZPackageResolver
javap -classpath "$JAR" -p -c gov.tak.api.importfiles.ImportResolver
```

**APK 驗證（代表性）：**
```sh
APK=<ATAK_SDK_5_7_0_5>/atak.apk
sha256sum "$APK"
unzip -p "$APK" AndroidManifest.xml | strings -e l | grep -E '5\.7'   # UTF-16，非 ASCII
unzip -Z1 "$APK" | grep -E '^classes[0-9]*\.dex$'
# 對 dex 字串掃描衝突/覆寫/去重標記
```

**上游參照與版本對齊。** 可讀 clone：`TAK-Product-Center/atak-civ`，**tag 5.5.1.10**，commit `9f6893dd657feacc35ec5de03dad721c2e44170e`（`HEAD -> main`）。權威 bytecode：**5.7.0.5** SDK `main.jar`（日期 2025-05-23）。兩者是**不同發行線**——permalink 對 5.5.1.10 解析，並非對 5.7.0.5 逐行保證。`javap -p` 已確認所有受查符號的 **public 簽章相同**（`MapItemImporter.importData / findItem(String) / findItem(CotEvent) / abstract importMapItem`；`MarkerImporter.importMapItem`；`MissionPackageManifest.getUID / setUID / (String,String,String) ctor`；`MissionPackageReceiver.preprocessMPReceive / handleCoTFileTransfer`；`ImportResolver.getDestinationPath / beginImport`）。

**需留意的稽核差異：**
- **主要漂移：** permalink 中的行號僅對 5.5.1.10 有效；上游未發布 5.7.0.x 原始碼。
- `MissionPackageReceiver` 中**沒有**字面叫 `import`/`dedup` 的單一方法——去重邏輯位於 `preprocessMPReceive`（5.5.1.10 行 1034-1098）。任何「容器以 package UID 去重」的說法都被原始碼**反駁**（它以 label + SHA-256 比對）。
- manifest UID 是一個 **configuration parameter**，不是 public `uid` 欄位——應說「configuration parameter UID」，而非「field uid」。
- `ImportResolver` 在 **5.7.0.5** 是 `gov.tak.api.importfiles.ImportResolver`，在 **5.5.1.10** 是 `com.atakmap.android.importfiles.sort.ImportResolver`（類別跨發行線被移動／更名；語意相同）。
- 驗證中浮現的次要 package 名修正：`TimeTrackingProcessService` 在 `com.atakmap.android.util`；`MissionPackageReceiver` 與 `MissionPackageEventHandler2` 在 `com.atakmap.android.missionpackage` / `…missionpackage.event`（並非某些原始筆記暗示的 `.file` 子 package）。

---

## 7. 信心與待決問題

**高信心、已確認（所有承載結論的答案）：**
- Layer A：相同 UID 的項目會**就地覆寫**、絕不重複（§2.1–§2.2）。
- Layer A：預設為 **last-received-wins**、**無**時間 gate；嚴格 newer-wins **只**存在於休眠的 `mapitemimporter.ignore-late-cot` debug 選項（預設關閉）後（§2.3）。
- Layer B：接收時去重**以內容雜湊**進行、而非 package UID；相同內容 → 跳過；內容變更 → 繼續並**逐檔覆寫以 UID 命名的解壓縮目錄**（§3.2–§3.4）。
- Layer C：KMZ/KML overlay 套件以**目的檔名**註冊 overlay；同檔名 → 就地覆寫；要乾淨更新就保留同 UID + 同檔名、只改內容（§4）。

**待確認／未決：**
1. **`deepFindUID` 比對策略（僅機制，不影響答案）。** *原始*描述說活動 graph 的 UID 查詢是線性 `getUID().equals()` 掃描。對抗式審查標記為**uncertain**：`DefaultMapGroup.deepFindUID` *確實*是線性 DFS 掃描，但活動進入點是 `RootMapGroup.deepFindUID`，它以 O(1) 的 `FastUIDLookup` 雜湊索引**覆寫**之（miss 時退回 metadata-map 查詢）。**結論不變**——鍵是 UID、回傳既有活動參照——只是*方式*在 root group 上是雜湊索引而非線性掃描。*解法：* 反組譯 `com.atakmap.android.maps.RootMapGroup#deepFindUID` 與 `com.atakmap.android.maps.MapView#getMapItem` 以確認 importer 實際走哪個進入點。
2. **SAVED file-info 的寫入側（Layer B）。** 去重的**讀取**鍵（label + SHA-256）已完全證實；**寫入**該 SAVED 列的程式（`MissionPackageFileIO.save()` / `MissionPackageManifestAdapter`）未完全反組譯。缺乏任何 `getFileInfoFromUid` 使得「以 UID 為鍵的持久紀錄」不太可能，但要下定論需要寫入路徑的反組譯。
3. **純（無 manifest）ZIP 匯入。** 本文涵蓋帶 **manifest UID** 的套件。`PlainZipExtractor`（ZIP 無 manifest 時選用）未深入反組譯；無 manifest UID 時，Layer B 的 UID 鍵目錄／去重不適用，其自身的覆寫行為超出本文範圍。

*待確認清單中的任何項目都不改變任一層級的最終答案；未決事項僅涉及內部機制或寫入路徑細節。*
