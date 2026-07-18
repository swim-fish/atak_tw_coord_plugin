# ATAK Data Package Import — How a Colliding (Already-Existing) UID Is Handled

> **Question answered:** When importing an ATAK Data Package, how is a colliding (already-existing) UID handled — overwrite, or keep the newer by update time?
>
> **Evidence sources.** *Authoritative (disassembled) bytecode:* ATAK-CIV **5.7.0.5** SDK `main.jar` (`<ATAK_SDK_5_7_0_5>/main.jar`, 33,121,240 bytes) plus the shipping runtime APK `<ATAK_SDK_5_7_0_5>/atak.apk` (SHA-256 `21ea6b363ee94f659539fac195fedc1a140dec06d0ebc23d01dc528601597508`). *Readable cross-reference:* the local upstream clone `TAK-Product-Center/atak-civ` at tag **5.5.1.10** (commit `9f6893dd657feacc35ec5de03dad721c2e44170e`).
>
> **Date:** 2026-06-17.

> ⚠️ **VERSION DRIFT — read this first.** The **authoritative** source for every behavioral claim below is the **5.7.0.5** disassembled bytecode. The **readable** cross-reference (and every GitHub permalink in this document) points at **5.5.1.10** — a *different release line*. For all of the code paths examined here the two agree (public method signatures are identical, control flow matches), and no behavioral drift was observed. **Where they ever disagree, the 5.7.0.5 bytecode is authoritative and wins.** GitHub permalinks therefore confirm *shape and contract*, but their **line numbers are valid for 5.5.1.10 only** (upstream has not published 5.7.0.x source).

---

## TL;DR — Answer

There are **two independent layers**, and they answer the question differently. Be precise about which one you mean.

### Layer A — CoT *items / markers* **inside** the package (matched by **item UID**)

- **Overwrite-in-place, NOT duplicate.** When a CoT event's UID already exists on the map, ATAK resolves the **same existing `MapItem` instance** by UID (`findItem` → `deepFindUID`) and **mutates it in place** (`setPoint`, `setType`, `processDetails`, `refresh`). A new object is allocated **only** when the UID was not found (`existing == null`). Re-adding the same object to its group is idempotent (early-return; the group store is keyed by per-object `serialId`, not UID). **No second item for the same UID is ever created.** *(confirmed)*
- **Default = last-received-wins (NO update-time comparison).** On a stock build there is **no** "keep the newer by timestamp" rule for map items. The latest-arriving event **always overwrites** the existing item regardless of its `<event time>`. *(confirmed)*
- **Newer-wins-by-timestamp EXISTS but is dormant.** A strict newer-wins gate (drops events whose timestamp is `<=` the stored one — i.e. older **and** equal-timestamp duplicates) runs **only when** the field `ignoreLateCoTEvents` is `true`. That field is set **once**, in the `MapItemImporter` constructor, from the developer/debug option `mapitemimporter.ignore-late-cot` (**default `0` → false**). No importer subclass enables it. So **on a stock build the timestamp gate is OFF for all map-item CoT imports.** *(confirmed)*
- Mission/Data Package (`FROM_MISSIONPACKAGE`) and StateSaver (`FROM_STATESAVER`) imports are **not** special-cased for timestamp dropping — those flags only gate notifications and persistence, not which event wins. *(confirmed)*

### Layer B — the Data Package *container* (matched by **package / manifest UID**)

- **The persistent dedup decision is by CONTENT HASH, not by package UID.** For a package received over the network, duplicate detection keys on **(transfer name / user label + SHA-256 content hash)** against the SAVED file-info table — the **package UID is explicitly not used**. *(confirmed)*
- **Same label + same hash, file still on disk → SKIP** (no user prompt; an "already exists" notification + a RECV transfer-log entry). If the DB row exists but the file is gone, it **re-downloads**. *(confirmed)*
- **Different content (different hash) → treated as new and proceeds.** During extraction it **overwrites on disk**, because the extraction directory **is** keyed by package UID (`<missionPackageFilesPath>/<manifest UID>/<contentUid>`) and files are unzipped with `renameIfExists = false` (logs "File already exists, over-writing", truncates in place). The UID directory is only deleted when **empty**, so stale residue from a previous larger same-UID extraction can remain. *(confirmed)*
- **There is no UID-keyed "replace prior copy" record and no duplicate-conflict AlertDialog on this receive path.** (`FileInfoPersistanceHelper` exposes lookups by label+hash and by filename, but **no `getFileInfoFromUid`**.) *(confirmed)*

### Layer C — file-payload content (KMZ / KML / GeoJSON overlays, e.g. `CCTV.zip`) — see §4

- A package whose payload is an **overlay file** (not CoT) takes a **third path**: after the container is extracted, the file is imported by the `ImportResolver` framework and the overlay is **keyed by its destination filename** (`…/overlays/<name>`), **not** by the package UID and **not** by a timestamp. Same filename → the overlay is **overwritten in place**; different filename → a new, parallel overlay. To push an update cleanly: **change the content, keep the same package UID and the same content filename** (§4.3). *(confirmed)*

**Bottom line.** *Items* with a colliding UID are **overwritten in place** (default last-received-wins; optional strict newer-wins only behind a debug flag). The *package container* is deduped **by content hash**, not by UID — identical content is skipped, changed content proceeds and **overwrites the UID-named extraction directory file-by-file**. Neither layer keeps "the newer by update time" as its default rule.

---

## 1. Scope & terminology

- **Data Package = Mission Package.** The two terms are interchangeable in the ATAK codebase; the classes live under `com.atakmap.android.missionpackage.*`. A Data Package is a ZIP carrying a **manifest** plus content (CoT `.cot` files, attachments, etc.).
- **Two distinct UID kinds:**
  - **Item UID** — the `uid` attribute of a CoT `<event>` (a marker/map item). Resolved against the live map graph. → **Layer A**.
  - **Manifest / package UID** — the container's own UID, stored as a manifest *Configuration parameter* named `"uid"` (`MissionPackageConfiguration.PARAMETER_UID = "uid"`), **not** a top-level field. Read via `MissionPackageManifest.getUID()`. → **Layer B**.
- **Import-origin flags.** `MapItemImporter` tags imports with a `from` string. `FROM_STATESAVER = "StateSaver"` and `FROM_MISSIONPACKAGE = "MissionPackage"` drive **notification suppression** (`isLocalImport`) and **persistence skipping** (`isStateSaverImport` → `persist`). Neither feeds a timestamp comparison.
- **Where each layer runs.** A Data Package's CoT content is dispatched as ordinary CoT events into the **item import** pipeline (Layer A). The package *file itself* — receive, dedup, extract to disk — is the **container** pipeline (Layer B).

---

## 2. Layer A — item UID conflict

### 2.1 Locate the existing item by UID (`findItem` → `deepFindUID`)

`MapItemImporter.importData(CotEvent, Bundle)` calls `findItem(event)` **before** `importMapItem` and passes the result in as the *existing* argument. `findItem(CotEvent)` extracts the UID and delegates to `findItem(String)`, which resolves it against the live map graph via `MapGroup.deepFindUID(uid)`. An already-present UID therefore yields the **existing object reference**, which the subtype then mutates. `importData` itself constructs **no** `MapItem` on this path. *(verdict: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
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
> `importMapItem(MapItem, CotEvent, Bundle)` is declared `protected abstract` — the create-vs-update decision is delegated to the subtype (§2.2), not made by `importData`.
>
> **Readable cross-ref (5.5.1.10, line numbers for that release only):**
> `MapItemImporter.java:98` `MapItem existing = findItem(event);`; `:112` `importMapItem(existing, event, extras)`; `:190` `findItem(String)` → `deepFindUID`; `:201` `findItem(CotEvent)`.
> - [MapItemImporter.importData L94](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java#L94)
> - [MapItemImporter.findItem(String) L190](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java#L190)
> - [MapItemImporter.findItem(CotEvent) L201](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MapItemImporter.java#L201)

**Open nuance on the lookup mechanism (uncertain — see §7).** `DefaultMapGroup.deepFindUID` is a linear depth-first `getUID().equals(uid)` scan. However, the *live-graph* entry point goes through `RootMapGroup`, which **overrides** `deepFindUID` with an O(1) `FastUIDLookup` hash index, falling back to a metadata-map lookup on a miss — **not** the linear scan. Either way the key is the **UID** and the return is the **existing live reference**; only the *matching strategy* (hash index vs. linear scan) is what the adversarial review flagged as not-as-originally-described.

### 2.2 Update-in-place vs. duplicate

`MarkerImporter.importMapItem` (the concrete implementation) casts the incoming *existing* item to `Marker` and branches on it: if it is **non-null**, the branch **jumps over** the create block and the **same instance** is updated; a fresh `Marker` is constructed **only** when `existing == null` (and even then a `doNotRecreate` bundle flag can force `FAILURE` instead). `createMarker` — `new Marker(event.getUID())` — is the **sole** `Marker` allocation site in the entire class, so an existing-UID path can never allocate a second marker. The re-add to the group is idempotent: `MapGroup.addItem` early-returns when the item is already in the group, and `DefaultMapGroup.addItemImpl` stores in a `Map` keyed by per-object `getSerialId()`, **not** by UID. *(verdict: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
>
> `com.atakmap.android.cot.importer.MarkerImporter#importMapItem`
> ```
> 46: aload_1; checkcast #41 Marker; astore 10     // existing -> local 10
> 52: aload 10; ifnonnull 84                        // existing != null -> SKIP create block
> 57..69: doNotRecreate guard -> ImportResult.FAILURE
> 70: invokevirtual #73 createMarker(...) ; astore 10 ; needsRefresh=1   // ONLY when existing==null
> ...  // update path mutates the SAME local 10:
> 249: aload 10 ; aload 11 ; invokevirtual #148 Marker.setPoint   // unconditional on update path
> 361: invokevirtual #202 Marker.setType
> 570: invokestatic  #284 CotDetailManager.processDetails(marker,event)
> 649: invokevirtual #302 addToGroup
> 1090: invokevirtual #403 Marker.refresh
> ```
> `com.atakmap.android.cot.importer.MarkerImporter#createMarker` — the **only** `new Marker`:
> ```
> 0: new #41 Marker ; 3: dup ; 4: aload_1
> 5: invokevirtual #425 CotEvent.getUID
> 8: invokespecial #426 Marker.<init>:(Ljava/lang/String;)V
> ```
> `com.atakmap.android.maps.MapGroup#addItem` (idempotent re-add):
> ```
> 0: aload_1 ; 1: ifnull 12
> 4: aload_1 ; 5: invokevirtual #69 MapItem.getGroup ; 8: aload_0
> 9: if_acmpne 13 ; 12: return          // already in this group -> no-op
> ```
> `com.atakmap.android.maps.DefaultMapGroup#addItemImpl` (store keyed by serialId, not UID):
> ```
> 1: getfield _items:Ljava/util/Map;
> 5: invokevirtual #181 MapItem.getSerialId:()J ; invokestatic Long.valueOf
> 12: invokeinterface #166 Map.put
> ```
> *(Trivial offset drift between the original notes and the verified disassembly — e.g. `setPoint` at 249 vs 245, `setType` at 361 vs 359 — does not change instruction identity or control flow.)*
>
> **Readable cross-ref (5.5.1.10):** `MarkerImporter.java:86-94` `Marker marker = (Marker) existing; if (marker == null) { if (doNotRecreate) return FAILURE; marker = createMarker(event, extras); needsRefresh = true; } else pointBefore = marker.getPoint();`. `MapItemImporter.java:210-214` `group.addItem(item);` ("The group transfer is taken care of within the addItem method").
> - [MarkerImporter.importMapItem L74](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/cot/importer/MarkerImporter.java#L74)

### 2.3 Timestamp / update-time handling (`ignoreLateCoTEvents`, late-event drop)

**Default path (gate OFF — stock build): no timestamp comparison; overwrite is unconditional.** `MarkerImporter.importMapItem` contains no compare of `CotEvent.getTime/getStart/getStale` against the existing item's stored time to accept-or-reject. The method's only three `lcmp` operations are (1) a `lastUpdateTime >= 0` guard that gates a **dead-reckoning** estimation block (`est.speed`/`est.course`/`est.dist`, computed from *current wall-clock*, not the event time), (2) a `__detailsCRC` compare that sets the `needsRefresh` flag, and (3) an `autoStaleDuration >= 0` clamp. None gates the overwrite — `setPoint` runs regardless. An **older-timestamped** same-UID event still moves/overwrites the marker. *(verdict: confirmed)*

**Optional gate (ON only via debug flag): strict newer-wins.** The late-event drop lives **outside** `importMapItem`, in `MapItemImporter.importData`: if `ignoreLateCoTEvents` is true it calls `TimeTrackingProcessService.begin(uid, event.getTime())`; a **null** token → `ImportResult.IGNORE` (event dropped before any overwrite). `begin` → `getReplacementPendingToken` compares the incoming timestamp against the pending/committed stored timestamp with `lcmp; ifle` — i.e. an event whose time is **`<=`** the stored time is dropped; **only strictly-greater** supersedes (equal timestamps are rejected as "not newer"). `ignoreLateCoTEvents` is `protected final`, set **once** in the constructor from `DeveloperOptions.getIntOption("mapitemimporter.ignore-late-cot", 0) != 0` (**default false**); no subclass re-assigns it. So this is a **debug-only** feature flag, not default behavior. *(verdicts: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
>
> `com.atakmap.android.cot.importer.MapItemImporter#<init>` (the *only* write of the field):
> ```
> 59: ldc #57            // "mapitemimporter.ignore-late-cot"
> 61: iconst_0           // default 0
> 62: invokestatic #59   // DeveloperOptions.getIntOption:(String,I)I
> 65: ifeq 72 ; 68: iconst_1 ; 69: goto 73 ; 72: iconst_0
> 73: putfield #65       // ignoreLateCoTEvents:Z   (== getIntOption(...,0) != 0)
> ```
> Field is `protected final boolean ignoreLateCoTEvents;`. `grep -c 'putfield #65'` over the whole class = **1** (only this ctor write).
>
> `com.atakmap.android.cot.importer.MapItemImporter#importData` (the gate):
> ```
> 25/26: getfield #65 ignoreLateCoTEvents:Z
> 29: ifeq 58                                   // gate OFF -> jump past begin(), no time check
> 32..44: invokevirtual TimeTrackingProcessService.begin(getUID, getTime)
> 49: ifnonnull 58
> 54: getstatic ImportResult.IGNORE ; 57: areturn   // older/equal event DROPPED (gate ON only)
> 61/65: invokevirtual #129 importMapItem            // unconditional otherwise
> ```
> `com.atakmap.android.util.TimeTrackingProcessService#getReplacementPendingToken` (strict newer-wins):
> ```
> 4: lload_2                              // incoming ts
> 5: invokestatic PendingToken.access$400 // existing.newTimestamp
> 9: lcmp ; 10: ifle 99                   // incoming <= pending -> return existing (=> null token)
> ...
> 73: invokestatic TimeRecord.access$000  // committed timeRecord.timestamp
> 76: lcmp ; 77: ifle 99                  // incoming <= committed -> drop
> 99: aload_1 ; 100: areturn
> ```
> `com.atakmap.android.cot.importer.MarkerImporter#importMapItem` (no time gate on default path): `lcmp@160` `lastUpdateTime>=0` (guards est.* only), `setPoint@249` unconditional, `lcmp@860` `__detailsCRC`, `getStale@948`/`getStart@952` → `autoStaleDuration`, `lcmp@968` clamp. **No `CotEvent.getTime`, no `CoordinatedTime.before/after/compareTo` anywhere in the method.**
>
> **Version cross-check:** 5.7.0.5 and 5.7.0.3 bytecode are **byte-identical** for this path (ctor `putfield`, `importData` gate, `getReplacementPendingToken` `lcmp/ifle 99`).
>
> **Readable cross-ref (5.5.1.10):** `MapItemImporter.java:79-80` field init `= (DeveloperOptions.getIntOption("mapitemimporter.ignore-late-cot", 0) != 0)`; `:101-108` `if (ignoreLateCoTEvents) { processToken = begin(...); if (processToken == null) return IGNORE; }`. `TimeTrackingProcessService.java:181` `if (timestamp > existing.newTimestamp)`, `:190` `if (timestamp > timeRecord.timestamp)`, `:194` `return existing;`.

---

## 3. Layer B — Data Package container UID

### 3.1 The package UID is a manifest *parameter*, not a top-level field

`MissionPackageManifest.getUID()` reads the configuration parameter named `"uid"` (`MissionPackageConfiguration.PARAMETER_UID`) — `_configuration.getParameter("uid").getValue()`, returning `null` if absent. `getParameter` is a linear scan over the `_parameters` `List<NameValuePair>`. There is no public `uid` field. *(verdict: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
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
> `MissionPackageConfiguration` exposes `public static final String PARAMETER_UID` (= `"uid"`). 5.7.0.5 vs 5.7.0.3 `getUID` is byte-identical.
>
> **Readable cross-ref (5.5.1.10):** `MissionPackageConfiguration.java:30` `PARAMETER_UID = "uid"`; manifest UID is assigned in the ctor (`setUID`, default ctor generates `UUID.randomUUID()`).
> - [MissionPackageManifest.getUID L217](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/file/MissionPackageManifest.java#L217)
> - [MissionPackageManifest ctor L103](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/file/MissionPackageManifest.java#L103)

### 3.2 Receive-time dedup is by **content hash**, not by package UID

For a package **received over the network** (CoT FileTransfer / commo), `MissionPackageReceiver.preprocessMPReceive` keys duplicate detection on **(transfer name / user label, SHA-256 hash)** against the **SAVED** file-info table — `FileInfoPersistanceHelper.getFileInfoFromUserLabelHash(transferName, sha256, TABLETYPE.SAVED)`. The package UID is **never consulted** (the source carries the explicit comment "we currently match on user label and SHA256 (not using package UID)"). If a row matches **and** the backing file still exists → it notifies "already exists with checksum", logs a `RECV` transfer entry, and returns `false`; both `handleCoTFileTransfer` and `initiateReceive` treat `false` as "we already have it" and skip the download. If the row exists but the file is gone → logs a warning and returns `true` to re-download. There is **no `getFileInfoFromUid`** in the helper's API surface. *(verdict: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
>
> `com.atakmap.android.missionpackage.MissionPackageReceiver#preprocessMPReceive`
> ```
> 57: invokestatic FileInfoPersistanceHelper.instance
> 62: getstatic TABLETYPE.SAVED
> 65: invokevirtual #931 getFileInfoFromUserLabelHash:(String;String;TABLETYPE)LAndroidFileInfo;
> 72: ifnull 237                         // no row -> proceed (return true)
> 77: invokevirtual AndroidFileInfo.file
> 80: invokestatic FileSystemUtils.isFile
> 83: ifeq 201                           // row exists but file gone -> warn, return true (re-download)
> ...148: getstatic FileTransferLog$TYPE.RECV ; 195: insertLog
> 199: iconst_0 ; 200: ireturn           // already-have-it -> SKIP
> ```
> `handleCoTFileTransfer`: `... invokespecial preprocessMPReceive ; ifne 25 ; return`.
> `initiateReceive`: `... invokespecial preprocessMPReceive ; ifne 14 ; aconst_null ; areturn`.
> `com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper` public methods: `getFileInfoFromUserLabelHash`, `getFileInfoFromUserLabel`, `getFileInfoFromFilename` — **NO `getFileInfoFromUid`**.
>
> *Honesty caveat:* the dedup **read** side is fully proven; the **write** side that populates the SAVED row (label + sha256 + filename at save time) was **not** fully disassembled — it is inferred from the matching read key and the absence of any UID-keyed lookup.
>
> **Readable cross-ref (5.5.1.10):** `MissionPackageReceiver.java:1043-1044` decisive comment ("match on user label and SHA256 (not using package UID)"); skip-return at `:1089`; callers `:1100` / `:1112`.
> - [MissionPackageReceiver.preprocessMPReceive L1034](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/MissionPackageReceiver.java#L1034)
> - [MissionPackageReceiver.handleCoTFileTransfer L1100](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/MissionPackageReceiver.java#L1100)

### 3.3 On disk: extraction is keyed by package UID and **overwrites in place**

Extracted contents are written into `<missionPackageFilesPath>/<manifest UID>/<contentUid>`, so the **package UID is the on-disk parent-directory key**. `MissionPackageEventHandler2.extract` builds the destination from `getMissionPackageFilesPath(...) + separator + manifest.getUID()` and calls `MissionPackageExtractor.UnzipFile(in, target, renameIfExists = false, buffer)`. When the target exists and `renameIfExists == false`, `UnzipFile` logs **"File already exists, over-writing:"** and writes to the **same path** (truncate / overwrite). So re-importing a package with the **same UID** collides on the UID-named directory and **overwrites each content file by name — no rename, no prompt**. *(verdict: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
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
> 64: iload_2 (renameIfExists) ; 65: ifeq 105       // false -> over-writing branch (path unchanged)
> 105..: ldc "File already exists, over-writing: "
> 159: getOutputStream(new File(filepath)) ; 173: FileSystemUtils.copyStream   // truncate/overwrite
> ```
> **Readable cross-ref (5.5.1.10):** `MissionPackageEventHandler2.java:96-105`; `MissionPackageExtractor.java:206-213` (`else { Log.d(... "File already exists, over-writing: " + filepath); }`).

### 3.4 Post-extraction cleanup deletes the UID directory **only when empty**

`MissionPackageExtractor.extract` recomputes `unzipDir = <filesPath>/<manifest UID>` and `deleteDirectory(unzipDir, false)` **only if** `listFiles() == null || length < 1` (empty). A **populated** same-UID directory is left intact. Combined with the file-by-file overwrite of §3.3, prior content is replaced **per file**, not cleared as a set — so **stale files** from a previous (larger) same-UID package can survive a smaller re-import. *(verdict: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
>
> `com.atakmap.android.missionpackage.file.MissionPackageExtractor#extract`
> ```
> 816: invokestatic getMissionPackageFilesPath
> 821: invokevirtual MissionPackageManifest.getUID
> 824: new java/io/File
> 839: IOProviderFactory.isDirectory
> 847: IOProviderFactory.listFiles ; arraylength ; if_icmpge ...   // length < 1 guard
> 867: invokestatic FileSystemUtils.deleteDirectory:(File;Z)V       // empty-only
> ```
> **Readable cross-ref (5.5.1.10):** `MissionPackageExtractor.java:149-159` `if (files == null || files.length < 1) FileSystemUtils.deleteDirectory(unzipDir, false);`.

### 3.5 In-session guard (`isAlreadyDownloaded`) — composite key, not UID-alone, not persistent

`MissionPackageDownloader` adds a second, **session-scoped** in-memory guard. `isAlreadyDownloaded(FileTransfer)` keys an in-memory `HashMap<String,FileTransfer> _downloaded` on `getDownloadKey(ftr)`, a **composite** of `name + size + uid + localPath + senderUID + sha256` (six comma-joined parts — UID is only one). It prevents re-processing the same **in-flight** transfer within a session, but is **not** a persistent UID-keyed replace/skip; the persistent gate remains the label+sha256 check of §3.2. (The incoming temp download file *is* named by `ftr.getUID()` under the incoming-download path.) *(verdict: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
>
> `com.atakmap.android.missionpackage.http.MissionPackageDownloader#isAlreadyDownloaded`
> ```
> 8: invokespecial getDownloadKey
> 13: getfield #21 _downloaded:Map
> 17: invokeinterface Map.containsKey
> 22: ifeq 52 ; 50: iconst_1 ; 51: ireturn
> ```
> `getDownloadKey` → `getName() + "," + getSize() + "," + getUID() + "," + getLocalPath() + "," + getSenderUID() + "," + getSHA256(false)`; `_downloaded = new HashMap<>()` in `<init>`; temp file `new File(getMissionPackageIncomingDownloadPath(...), ftr.getUID())`.

### 3.6 Net container behavior

| Receive case (same package UID) | Decided by | Result |
|---|---|---|
| Same label + same SHA-256, file on disk | content hash (§3.2) | **SKIP** — no prompt; "already exists" notification + RECV log |
| Same label + same SHA-256, file missing | content hash (§3.2) | **Re-download** (warning logged) |
| Different SHA-256 (content changed) | not matched → proceeds; extraction keyed by UID (§3.3) | **Overwrite-on-disk** file-by-file at `<filesPath>/<uid>/…`, `renameIfExists=false`; stale residue possible (§3.4) |

There is **no** UID-based "replace prior copy" record and **no** duplicate-conflict AlertDialog on this receive path.

---

## 4. Third path — KMZ / KML overlay Data Packages (worked example: `CCTV.zip`)

Sections 2–3 cover packages whose payload is **CoT** (markers) plus the container. Many real Data Packages instead carry a **file-payload overlay** — a KMZ/KML, GeoJSON, shapefile, image overlay, etc. These are **not** CoT events, so **Layer A does not apply**: there is no item UID, no `ignoreLateCoTEvents`, no last-received-wins marker contest. Instead, once the container is extracted (Layer B) the content file is handed to the **`ImportResolver` framework**, which registers the overlay **keyed by its destination filename**. `CCTV.zip` is exactly this shape.

### 4.1 What `CCTV.zip` actually is

```
MANIFEST/manifest.xml
c64e6cecb94cb5acf402db2e6030b7f0/CCTV.kmz      (a KMZ: doc.kml + styles + icons)
```

Manifest (verbatim):

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

- Whole-zip SHA-256 `50e6c5ae…` (the receive-dedup key, §3.2); inner KMZ SHA-256 `5f6d74fa…`.
- The inner directory `c64e6cec…` is an **opaque content id**, *not* a hash of the content (the inner KMZ's MD5 is `7670c49e…`; the filename's MD5 is `288e3c58…` — neither matches). It need not change when content changes.
- `onReceiveImport=true` → after extraction ATAK auto-imports `CCTV.kmz`; `onReceiveDelete=true` → removing the package removes the imported overlay.

### 4.2 After extraction, the content is imported by **destination filename**

`MissionPackageEventHandler2.importFile(...)` (invoked from `extract`, once per `<Content>`) finds a matching `ImportResolver` for the file and calls `sorter.beginImport(file, flags)`. For `.kmz` the resolver is `ImportKMZPackageResolver`, constructed with destination folder **`overlays`**; for `.kml` it is `ImportKMLSort`, also `overlays`. `ImportResolver.getDestinationPath(file)` builds `new File(destinationDir, file.getName())` — i.e. `…/overlays/CCTV.kmz` — and `beginImport` copies the extracted file there (`FileSystemUtils.copyFile(file, dest)` under `IMPORT_COPY`). A plain file copy onto an existing path **overwrites in place**. So the overlay's identity is its **destination filename `CCTV.kmz`** — independent of the package UID and of the content hash. Same filename → the same `overlays/CCTV.kmz` is overwritten and the displayed layer is replaced; a different filename → a new, separate overlay (the old one is not removed). *(verdict: confirmed)*

> **Evidence (5.7.0.5 bytecode — authoritative)**
>
> `…importfiles…ImportResolver#getDestinationPath` → `new File(destinationDir, file.getName()[+ext])`:
> ```
> 1: invokevirtual File.getName
> 73: new java/io/File ; 78: getfield #30 destinationDir:Ljava/io/File; ; 82: File.<init>(File,String)
> ```
> `…ImportResolver#beginImport(File, EnumSet<SortFlags>)`:
> ```
> 13:  invokevirtual #119 getDestinationPath
> 180: getstatic     #171 SortFlags.IMPORT_COPY
> 239: invokestatic  #193 FileSystemUtils.copyFile:(File;File)V   // overwrite-by-name
> 246: invokevirtual #197 onFileSorted
> ```
> `com.atakmap.android.importfiles.sort.ImportKMZPackageResolver#<init>` → `super(".kmz", FileSystemUtils.getItem("overlays"), …)`; `ImportKMLSort#<init>` → `super(".kml", "overlays", …)`.
> `com.atakmap.android.missionpackage.event.MissionPackageEventHandler2#importFile` → `sorter.getDestinationPath(file)` then `sorter.beginImport(file, flags)`.
>
> **Version-drift note (specific to this path).** In **5.7.0.5** the resolver base class is `gov.tak.api.importfiles.ImportResolver`; in the **5.5.1.10** clone it is still `com.atakmap.android.importfiles.sort.ImportResolver` — the class was **moved/renamed** between the two release lines. `getDestinationPath` / `beginImport` semantics are identical; only the package differs. (Per the code-anchoring rule, the 5.7.0.5 bytecode is authoritative.)
>
> **Readable cross-ref (5.5.1.10):** `MissionPackageEventHandler2.java:146` `importFile(...)`, `:209` `sorter.beginImport(file, flags)`; `ImportResolver.java:202` `beginImport`, `:246` `FileSystemUtils.copyFile(file, dest)`; `ImportKMZPackageResolver.java:21` `super(".kmz", FileSystemUtils.getItem(OVERLAYS_DIRECTORY), …)`; `ImportKMLSort.java:39` `super(".kml", OVERLAYS_DIRECTORY, …)`.
> - [MissionPackageEventHandler2.importFile L146](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/missionpackage/event/MissionPackageEventHandler2.java#L146)
> - [ImportResolver.beginImport L202](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L202)
> - [ImportKMZPackageResolver L21](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportKMZPackageResolver.java#L21)
> - [ImportKMLSort L39](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportKMLSort.java#L39)
>
> *The interactive **overwrite-vs-discard** prompt (`importmgr_overwrite_existing_import` / `…discard_the_new_resource`, §5) belongs to the **manual** import-manager / RemoteResource flow. The `onReceiveImport` data-package path shown here copies **silently** — no prompt.*

### 4.3 Net behavior, and **what to change** for new vs. old

Three **independent** identity keys decide the outcome of a re-import:

| Layer | Key (for `CCTV.zip`) | Effect |
|---|---|---|
| Receive dedup (§3.2) | (transfer label, whole-zip SHA-256 `50e6c5ae…`) | byte-identical zip → **skipped** silently |
| Extraction dir (§3.3) | package UID `de8e3081-…` | same UID → overwrites `…/<uid>/…` file-by-file |
| Overlay registration (§4.2) | content filename `CCTV.kmz` → `overlays/CCTV.kmz` | same filename → replaces the same overlay |

ATAK does **not** pick "the newer by date" for a package — the manifest carries no version/timestamp field that the receive path compares. **Replacement** is driven by **UID + filename**; **skip** is driven by **content hash**. You therefore control the outcome explicitly:

**Goal A — ship an update that replaces the existing CCTV layer in place (most common):**
- **Change** the KMZ content (edit the inner `doc.kml`). This changes the whole-zip SHA-256, so the package is **not** skipped at receive. *(Mandatory: a byte-identical zip is dropped as "already exists with checksum".)*
- **Keep** `uid="de8e3081-…"` → overwrites the same extraction dir; with `onReceiveDelete=true` the package stays a single managed unit (no duplicate package entry).
- **Keep** the content filename `CCTV.kmz` (and `name`) → lands on the same `overlays/CCTV.kmz` → the displayed overlay is replaced in place.
- The inner `c64e6cec…/` directory may stay unchanged (opaque id).

**Goal B — ship it as a separate, parallel layer (keep old + new):**
- **Change** `uid` to a fresh UUID **and** the content filename (e.g. `CCTV_v2.kmz`, plus `name`). Old and new coexist.

**Pitfalls — mismatched keys:**
- Change UID only, keep filename → a new package entry, but the overlay still overwrites `overlays/CCTV.kmz` → container and overlay disagree.
- Change filename only, keep UID → the old file lingers in `…/<uid>/…` (the UID dir is deleted only when empty, §3.4) **and** the old `overlays/CCTV.kmz` is **not** auto-removed → a stale duplicate overlay.
- Expecting "newer date wins" → no such mechanism for packages; use **same-UID + same-filename + changed-content** instead.

---

## 5. APK corroboration

The disassembled classes genuinely ship in the runtime, and the APK matches the SDK jar version.

- **APK identity.** Path `<ATAK_SDK_5_7_0_5>/atak.apk`; size **389,700,563** bytes; SHA-256 **`21ea6b363ee94f659539fac195fedc1a140dec06d0ebc23d01dc528601597508`**.
- **Version.** `versionName = "5.7.0.5 (3198049e)"`, package `com.atakmap.app@5.7.0.CIV` — recovered from the binary `AndroidManifest.xml` via **UTF-16** string extraction (`strings -e l`; a plain-ASCII grep returns nothing because the versionName lives in the resource string pool as UTF-16). Corroborated by the SDK folder name `ATAK-CIV-5.7.0.5-SDK`. This is the **exact** version whose `main.jar` was disassembled.

**Disassembled classes confirmed present in the shipping dex** (`classes.dex` 13.5 MB, `classes2.dex` 11.8 MB, `classes3.dex` 2.5 MB):

| Class | Defining dex |
|---|---|
| `com.atakmap.android.cot.importer.MapItemImporter` | `classes.dex` |
| `com.atakmap.android.cot.importer.MarkerImporter` | `classes.dex` (+ cross-ref in `classes2.dex`) |
| `com.atakmap.android.missionpackage.file.MissionPackageManifest` | `classes.dex` (+ cross-ref in `classes2.dex`) |
| `com.atakmap.android.missionpackage.MissionPackageReceiver` | `classes.dex` |

*(Multi-dex counts reflect a descriptor appearing both as a defined type and as a cross-dex reference — normal for multidex; presence is unambiguous.)*

**Conflict-string search (verbatim dex hits).** The APK **does** contain overwrite/dedup machinery consistent with the bytecode findings:

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

Interpretation: the **generic import manager** can present an **overwrite-vs-discard** choice (`importmgr_overwrite_existing_import` / `importmgr_discard_the_new_resource`), but specific paths also **overwrite without prompting** ("Overwriting existing file without prompting user:") and **identical-hash** files are treated as already-present ("already exists with checksum:" / "already exists. SHA256:"). The `File already exists, over-writing:` string is exactly the §3.3 `UnzipFile` overwrite branch. These corroborate Layer B: **hash-dedup + on-disk overwrite, no per-UID newer-by-time contest** on the Data Package receive path.

---

## 6. Methodology & sources

**Code-anchoring rule (project convention).** Every behavioral claim is anchored to **both** the `javap` class#method on the authoritative **5.7.0.5** `main.jar` **and** an upstream **5.5.1.10** permalink. **If the two disagree, the 5.7.0.5 SDK bytecode wins** and the doc says so. The permalinks confirm shape/contract; their **line numbers are 5.5.1.10-specific**.

**Disassembly commands (representative):**
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
javap -classpath "$JAR" -p -c com.atakmap.coremap.cot.event.CotEvent
```

**APK verification (representative):**
```sh
APK=<ATAK_SDK_5_7_0_5>/atak.apk
sha256sum "$APK"
unzip -p "$APK" AndroidManifest.xml | strings -e l | grep -E '5\.7'   # UTF-16, not ASCII
unzip -Z1 "$APK" | grep -E '^classes[0-9]*\.dex$'
# dex string scan for conflict/overwrite/dedup markers
```

**Upstream reference & version alignment.** Readable clone: `TAK-Product-Center/atak-civ`, **tag 5.5.1.10**, commit `9f6893dd657feacc35ec5de03dad721c2e44170e` (`HEAD -> main`). Authoritative bytecode: **5.7.0.5** SDK `main.jar` (dated 2025-05-23). These are **different release lines** — permalinks resolve against 5.5.1.10 and are not a line-for-line guarantee for 5.7.0.5. `javap -p` confirmed **identical public signatures** for all audited symbols (`MapItemImporter.importData / findItem(String) / findItem(CotEvent) / abstract importMapItem`; `MarkerImporter.importMapItem`; `MissionPackageManifest.getUID / setUID / (String,String,String) ctor`; `MissionPackageReceiver.preprocessMPReceive / handleCoTFileTransfer`).

**Audit discrepancies to keep in mind:**
- **Primary drift:** line numbers in permalinks are valid for 5.5.1.10 only; upstream has not published 5.7.0.x source.
- There is **no** single method literally named `import`/`dedup` in `MissionPackageReceiver` — the dedup logic lives in `preprocessMPReceive` (5.5.1.10 lines 1034-1098). Any claim that the container dedups **by package UID** is **contradicted** by the source (it matches label + SHA-256).
- The manifest UID is a **configuration parameter**, not a public `uid` field — say "configuration parameter UID", not "field uid".
- Minor package-name corrections surfaced during verification: `TimeTrackingProcessService` is in `com.atakmap.android.util`; `MissionPackageReceiver` and `MissionPackageEventHandler2` are in `com.atakmap.android.missionpackage` / `…missionpackage.event` (not the `.file` subpackage some raw notes implied).

---

## 7. Confidence & open questions

**High-confidence, confirmed (all of the load-bearing answers):**
- Layer A: same-UID items are **overwritten in place**, never duplicated (§2.1–§2.2).
- Layer A: default is **last-received-wins** with **no** timestamp gate; strict newer-wins exists **only** behind the dormant `mapitemimporter.ignore-late-cot` debug option (default off) (§2.3).
- Layer B: receive-time dedup is **by content hash**, not package UID; same content → skip; changed content → proceed and **overwrite the UID-named extraction directory** file-by-file (§3.2–§3.4).

**Uncertain / open:**
1. **`deepFindUID` matching strategy (mechanism only, not the answer).** The *original* description said the live-graph UID lookup is a linear `getUID().equals()` scan. The adversarial review marked that **uncertain**: while `DefaultMapGroup.deepFindUID` *is* a linear DFS scan, the live entry point is `RootMapGroup.deepFindUID`, which **overrides** it with an O(1) `FastUIDLookup` hash index (falling back to a metadata-map lookup on a miss). The **conclusion is unchanged** — UID is the key and the existing live reference is returned — but the *how* is hash-index, not linear scan, on the root group. *Resolution:* disassemble `com.atakmap.android.maps.RootMapGroup#deepFindUID` and `com.atakmap.android.maps.MapView#getMapItem` to confirm which entry point the importer actually reaches.
2. **SAVED file-info WRITE side (Layer B).** The dedup **read** key (label + SHA-256) is fully proven; the code that **writes** that SAVED row (`MissionPackageFileIO.save()` / `MissionPackageManifestAdapter`) was not fully disassembled. The absence of any `getFileInfoFromUid` makes a UID-keyed persistent record implausible, but a definitive statement needs the write-path disassembly.
3. **Plain (manifest-less) ZIP imports.** This document covers packages carrying a **manifest UID**. `PlainZipExtractor` (chosen when a ZIP has no manifest) was not deep-disassembled; with no manifest UID, the UID-keyed directory/dedup of Layer B does not apply, but its own overwrite behavior was out of scope here.

*Nothing in the uncertain list changes the bottom-line answer for either layer; the open items concern internal mechanism or write-path detail.*
