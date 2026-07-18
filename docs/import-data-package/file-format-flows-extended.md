# ATAK File-Format Import Flows — Extended (Remaining Formats)

> **Question answered:** This is the **BATCH 2** companion to [`file-format-flows.md`](./file-format-flows.md). That doc covered `cot`, `datapackage`, `kml`, `kmz`, `image`, `grg`, `dted`, `iconset`, `layers`, and `mvt`. This doc covers the **remaining resolvers** enumerated from the ATAK-CIV `main.jar` resolver list but not yet analyzed: **Shapefile**, **GeoJSON**, **GML**, **GPX** (track vs route), **DRW** (FalconView drawing), **LPT** (FalconView points), **Tileset**, **SQLite DB** (schema-routed) + **GeoPackage**, **Video**, **APK**, **Certificate (`.p12`)**, **Preferences (`.pref` XML/JSON)**, **Alternate contacts (`.csv`)**, **INFZ** (product-repo cache), **Support-info package**, and **TXT/XML** (signature-routed config). One section per family.
>
> **Format list source.** The project's resolver enumeration of the ATAK-CIV **5.7.0.5** `main.jar` (`com.atakmap.android.importfiles.sort.Import*Sort` classes).
>
> **Evidence sources.** *Authoritative (disassembled) bytecode:* ATAK-CIV **5.7.0.5** SDK `main.jar` (`<ATAK_SDK_5_7_0_5>/main.jar`). *Readable cross-reference:* the local upstream clone `TAK-Product-Center/atak-civ` at tag **5.5.1.10**, commit `9f6893dd657feacc35ec5de03dad721c2e44170e`.
>
> **Date:** 2026-06-17.

> ⚠️ **VERSION DRIFT — read this first.** The **authoritative** source for every behavioral claim below is the **5.7.0.5** disassembled bytecode. The **readable** cross-reference (and every GitHub permalink in this document) points at **5.5.1.10** — a *different release line*. For the code paths examined here the two agree on control flow and method contracts; where a difference exists (constant inlining, a no-op extension-append branch, the deprecation banner) it is called out inline. **Where they ever disagree, the 5.7.0.5 bytecode is authoritative and wins.** Permalink **line numbers are valid for 5.5.1.10 only** — upstream has not published 5.7.0.x source.
>
> **Every `*Sort` class analyzed in this doc is `@Deprecated` / `@DeprecatedApi(since="5.5", removeAt="5.8")`,** superseded by a `gov.tak.api.importfiles.Import*Resolver` successor. **All of the deprecated `*Sort` classes remain present and fully functional in the 5.7.0.5 bytecode** and are what this doc analyzes (as instructed). The successors carry the same extension/destination/MIME contracts where verified.

> **Companion docs.**
> - [`file-format-flows.md`](./file-format-flows.md) — BATCH 1: the import-routing framework, the **four conflict archetypes (a)–(d)** (reused by reference throughout this doc), and the first ten formats.
> - [`README.md`](./README.md) — the same-UID collision analysis across Layer A (CoT item-UID), Layer B (Data Package container, content-hash dedup), and Layer C (overlay-payload filename keying). The **cert worked example** below depends on the README §3 Data Package fan-out.

> **The four conflict archetypes** (defined in `file-format-flows.md`; reused here by letter):
> **(a)** filename / path overwrite-in-place · **(b)** catalog / DB-row keyed (path-idempotent) · **(c)** UID-keyed replace · **(d)** CoT item-UID pipeline.
> The extended formats add behaviors that don't fit (a)–(d) cleanly — a **state-apply** family (cert store, SharedPreferences, contact prefs) and an **OS-handoff** family (APK install, product-repo sync). Those are noted per-section.

---

## Summary table

| Format | Ext(s) | MIME (`contentType` / `mimeType`) | Resolver (`*Sort`) | Destination | Registration | Conflict key | On re-import |
|---|---|---|---|---|---|---|---|
| **Shapefile** | `.shp` | `Shapefile` / `application/octet-stream` | `ImportSHPSort` | `overlays/<name>.shp` | OGR feature DB (`ShapefileSpatialDb`) | filename **(a)** | overwrite-in-place, re-ingest |
| **Shapefile (zipped)** | `.zip` | `Shapefile` / `application/octet-stream` | `ImportSHPZSort` | `overlays/<name>.zip` | OGR feature DB (`ShapefileSpatialDb`) | filename **(a)** | overwrite-in-place, re-ingest |
| **GeoJSON** | `.geojson` | `GeoJSON` / `application/octet-stream` | `ImportGeoJsonSort` | `overlays/<name>.geojson` | OGR feature DB (`GeoJSONSpatialDb`) | filename **(a)** | overwrite-in-place, re-ingest |
| **GeoJSON (zipped)** | `.zip` | `GeoJSON` / `application/octet-stream` | `ImportGeoJsonZSort` | `overlays/<name>.zip` | OGR feature DB (`GeoJSONSpatialDb`) | filename **(a)** | overwrite-in-place, re-ingest |
| **GML** | `.gml` | `GML` / `application/octet-stream` | `ImportGMLSort` | `overlays/<name>.gml` | OGR feature DB (`GMLSpatialDb`) | filename **(a)** | overwrite-in-place, re-ingest |
| **GML (zipped)** | `.zip` | `GML` / `application/octet-stream` | `ImportGMLZSort` | `overlays/<name>.zip` | OGR feature DB (`GMLSpatialDb`) | filename **(a)** | overwrite-in-place, re-ingest |
| **GPX (track)** | `.gpx` | `GPX` / `application/gpx+xml` | `ImportGPXSort` | `overlays/<name>.gpx` | OGR feature DB (`GpxFileSpatialDb`) | filename **(a)** | overwrite-in-place, re-ingest |
| **GPX (route)** | `.gpx` | (`ROUTE_IMPORT` broadcast — no `IMPORT_DATA`) | `ImportGPXRouteSort` | `overlays/<name>.gpx` | Route map-item group (`RouteMapReceiver`) | **route UID** | file overwritten **but on-map route DUPLICATED** (new random UID) |
| **DRW** | `.drw` | `DRW` / `application/x-msaccess` | `ImportDRWSort` | `overlays/<name>.drw` | OGR feature DB (`FalconViewSpatialDb`, `Main` table) | filename **(a)** | overwrite-in-place, re-ingest |
| **LPT** | `.lpt` | `LPT` / `application/x-msaccess` | `ImportLPTSort` | `overlays/<name>.lpt` | OGR feature DB (`FalconViewSpatialDb`, `Points` table) | filename **(a)** | overwrite-in-place, re-ingest |
| **Tileset** | `.zip` | `Tileset` / `application/zip` | `ImportTilesetSort` | `layers/<name>.zip` | raster catalog via **layer scanner** (`TilesetLayerScanner` → `PersistentRasterDataStore`) | filename **(a)** + catalog **(b)** | file overwritten; catalog row refreshed (contains→remove→add), not duplicated |
| **SQLite DB** | `.sqlite` | `SQLite Database` / `application/x-sqlite3` | `ImportSQLiteSort` | `Databases/<canonical>.sqlite` (per detected TYPE) | the named ATAK DB (CoT / layers2 / spatial / iconsets / SSE) | filename **(a)** (canonical name) | overwrite the canonical DB in place; source name irrelevant |
| **GeoPackage** | `.gpkg` | (routed by content) | `GeoPackageImportResolver` (+ `ImportLayersSort` for tiles) | tiles → `imagery/`; features → feature DB | raster catalog / feature DB | path **(a)/(b)** | overwrite + catalog/feature re-ingest |
| **Video** | `mpeg mpg ts avi mp4 264 265 wmv mov webm mkv flv` | `Video` / `application/octet-stream` | `ImportVideoSort` | `tools/videos/<name>` | Video library (`VideoFileWatcher` 5 s poll) | filename **(a)** | overwrite-in-place; library entry refreshed |
| **APK** | `.apk` | `Android App` / `application/vnd.android.package-archive` | `ImportAPKSort` | `tmp/<name>.apk` | **OS handoff** (`AppMgmtUtils.install` → PackageInstaller) | filename **(a)** | staged file overwritten; OS prompts install/update |
| **Certificate** | `.p12` | `P12 Certificate` / `application/x-pkcs12` | `ImportCertSort` | staged `cert/<name>.p12` → **cert store** (staged file deleted) | encrypted cert-store DB (+ `cot_streams` binding) | **cert-store slot** `type[+server+port]` | silent last-write-wins into slot; no on-disk residue |
| **Preferences (XML)** | `.pref` | `ATAK Preferences` / `application/xml` | `ImportPrefSort` | `<config>/prefs/<name>` (sanitized side artifact) | Android `SharedPreferences` (per-key) | **pref-key** | per-key overwrite (last-writer-wins); write-once keys protected; policy-gated |
| **Preferences (JSON)** | `.pref` | `ATAK Preferences` / `application/json` | `ImportJSONPrefSort` | `<config>/prefs/<name>` | Android `SharedPreferences` (per-key) | **pref-key** | per-key overwrite (last-writer-wins) |
| **Alternate contacts** | `.csv` | `Contact Info` / `text/csv` | `ImportAlternateContactSort` | **none** (identity `getDestinationPath`; source secure-deleted if under `atakdata/`) | local device's alternate-contact prefs (callsign-gated) | **pref-key** (per-field) | per-field overwrite of own prefs; no file retained |
| **INFZ** | `.infz` | `Product Repo Cache` / `application/zip` | `ImportINFZSort` | `<SUPPORT>/apks/custom/product.infz` (**fixed name**) | product repo (`FileSystemProductProvider` + `ProductProviderManager.sync`) | filename (constant `product.infz`) **(a)** | overwrite-in-place + forced re-sync |
| **Support info** | *(none — exact filename)* `support.inf`, `atak_splash.png` | *(none — `getContentMIME()==null`)* | `ImportSupportInfoSort` | `support/<name>` | **filesystem only** (no broadcast — MIME null) | filename **(a)** | overwrite-in-place |
| **TXT / XML config** | `.txt`, `.xml` | `TXT or XML File` / `application/xml` | `ImportTXTSort` | **signature-routed** folder (per matched signature) | content-routed config dispatch (geocoder / WFS / favorites / WMS / copy-only) | filename **(a)** | overwrite-in-place + re-run after-action |

---

## Shapefile (`.shp` / zipped)

**What it is.** An ESRI Shapefile vector dataset — a bare `.shp` (with its sidecar `.dbf`/`.shx`/`.prj`) or a `.zip` bundling the set. The canonical OGR vector overlay; the GeoJSON / GML / DRW families all mirror its `SpatialDbContentSource` pattern (and reuse its `ic_shapefile` icon).

**Flow.**
- **match.** `ImportSHPSort` = the `.shp` extension gate (`super.match`; the resolver only logs the absolute path afterwards — there is **no extra content sniff**, so any `.shp` is accepted). `ImportSHPZSort` = ext gate `.zip` **AND** `HasSHP(File)`: opens `com.atakmap.util.zip.ZipFile` and returns true on the first entry whose lowercased name ends `.shp`.
- **destination.** Both `super(…, FileSystemUtils.OVERLAYS_DIRECTORY, …)` → `ImportResolver.getDestinationPath` = `new File(getItem("overlays"), file.getName())` → `overlays/<name>.shp` (or `.zip`).
- **registration.** `onFileSorted` broadcasts `IMPORT_DATA{contentType="Shapefile", mimeType="application/octet-stream"}`; `ShapefileSpatialDb` (`SHP_CONTENT_TYPE="Shapefile"`, an `OgrSpatialDb` / `SpatialDbContentSource`) OGR-parses the shapefile into a `FeatureDataStore2` and surfaces it as a toggleable overlay.
- **on re-import — archetype (a).** Fixed `overlays/<name>` path; `copyFile`/`renameTo` overwrite; `ShapefileSpatialDb` re-ingests that path's feature set. A different filename = a separate overlay; no content-hash/feature dedup at the resolver layer.

**Evidence.** `ImportSHPSort.<init>` `ldc ".shp"` / `getstatic FileSystemUtils.OVERLAYS_DIRECTORY`; `match` → `ImportResolver.match` (+ a `getAbsolutePath` log, no sniff). `ImportSHPZSort.<init>` `ldc ".zip"` / `OVERLAYS_DIRECTORY`; `match` → `ImportResolver.match` then `invokestatic HasSHP` (`ZipFile.entries` / `".shp"; String.endsWith`). Both `getContentMIME` → `("Shapefile","application/octet-stream")` (`ShapefileSpatialDb.SHP_CONTENT_TYPE` / `SHP_FILE_MIME_TYPE`). *(confidence: high; destination + conflict CONFIRMED by direct disassembly — this family's analyze agent did not return structured output, so it was filled in by hand.)*

**Clone (5.5.1.10):** [`ImportSHPSort.java#L40`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSHPSort.java#L40) · [`#L46`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSHPSort.java#L46) · [`ImportSHPZSort.java#L36`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSHPZSort.java#L36) · [`#L42`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSHPZSort.java#L42) · [`ShapefileSpatialDb.java#L61`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/ShapefileSpatialDb.java#L61).

---

## GeoJSON (`.geojson` / zipped)

**What it is.** OGR-backed GeoJSON vector features (plain `.geojson`) or a `.zip` containing one or more `*.geojson` entries. Both render as a `GeoJSON` overlay group.

**Flow.**
- **match.** `ImportGeoJsonSort` = ext gate `.geojson` **AND** a content peek: reads the first **2048 chars** via `BufferedReader.read(char[])` and requires the literal substring `FeatureCollection` (`GEOJSONMATCH`). This is a **substring sniff, not a JSON parse** — a non-`FeatureCollection` GeoJSON `Feature`/`Geometry` is rejected, and any file whose head contains the word would pass. `ImportGeoJsonZSort` = ext gate `.zip` **AND** `HasGeoJSON(File)`: opens `com.atakmap.util.zip.ZipFile`, iterates entries, returns true on the first entry whose lowercased name ends `.geojson` — **name-based only; no content check inside the archive.**
- **destination.** Both pass `super(…, "overlays", …)`; `ImportResolver.getDestinationPath` = `new File(FileSystemUtils.getItem("overlays"), file.getName())`. Plain → `overlays/<name>.geojson`; zipped → `overlays/<name>.zip`. Same dir for both.
- **registration.** `onFileSorted` broadcasts `IMPORT_DATA{contentType="GeoJSON", mimeType="application/octet-stream"}`; `GeoJSONSpatialDb` (`extends OgrSpatialDb extends SpatialDbContentSource implements Importer`) OGR-parses into a `FeatureDataStore2` (zipped variant via `ZipVirtualFile` + a `geojson-zipped` data source).
- **on re-import — archetype (a).** `beginImport` copies/renames to the fixed `overlays/<name>` path (`copyFile` for COPY, `renameTo`→`copyFile` for MOVE), **overwriting** any same-named file. No prompt, no suffix, no content-hash/UID dedup. Different content under the same name silently replaces the first and is re-ingested; a different name coexists as a second overlay. The two resolvers share `getContentMIME()`, so both ingest through the same importer (display names `GeoJSON` vs `Zipped GeoJSON` differ cosmetically; `ic_shapefile` icon reused).

**Evidence.** `javap -p -c` (5.7.0.5 `main.jar`): `ImportGeoJsonSort.<init>` `ldc #1 ".geojson"` / `ldc #5 "overlays"` / `ldc #7 "GeoJSON"` → `ImportResolver.<init>(String,String,String,Drawable)`. `isGeoJSON`: `sipush 2048; newarray char` … `BufferedReader.read([C)` … `ldc #117 "FeatureCollection"; String.contains`. `ImportGeoJsonZSort.<init>` `ldc #1 ".zip"`; `match` `ImportResolver.match` then `invokestatic #31 HasGeoJSON`; `HasGeoJSON` `new ZipFile; ZipFile.entries; ldc #107 ".geojson"; String.endsWith`. `GeoJSONSpatialDb`: `GEOJSON_CONTENT_TYPE="GeoJSON"`, `GEOJSON_FILE_MIME_TYPE="application/octet-stream"`. `FileSystemUtils.OVERLAYS_DIRECTORY="overlays"`. *(confidence: high; destination + conflict CONFIRMED.)*

**Clone (5.5.1.10):** [`ImportGeoJsonSort.java#L38`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGeoJsonSort.java#L38) · [`#L78`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGeoJsonSort.java#L78) · [`ImportGeoJsonZSort.java#L35`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGeoJsonZSort.java#L35) · [`#L72`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGeoJsonZSort.java#L72) · [`GeoJSONSpatialDb.java#L61`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/GeoJSONSpatialDb.java#L61) · [`#L141`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/GeoJSONSpatialDb.java#L141).

---

## GML (`.gml` / zipped)

**What it is.** OGR/GDAL-backed GML vector features (plain `.gml`) or a `.zip` containing a `*.gml` entry (`Zipped GML`).

**Flow.**
- **match.** `ImportGMLSort` = ext gate `.gml` **AND** `isGML(InputStream)`: reads up to **2048 chars**, requires the literal substring `<gml`. `ImportGMLZSort` = ext gate `.zip` **AND** `HasGML(File)`: opens `com.atakmap.util.zip.ZipFile`, returns true on the first entry whose lowercased name ends `.gml`.
- **destination.** Both pass `super(…, "overlays", …)` → `overlays/<name>` (`.gml` or `.zip`).
- **registration.** `onFileSorted` broadcasts `IMPORT_DATA{contentType="GML", mimeType="application/octet-stream"}`; `GMLSpatialDb` (`extends OgrSpatialDb`) is constructed + registered by `WktMapComponent` (`addContentSource(new GMLSpatialDb(spatialDb))` → `ImporterManager.registerImporter(contentTypeImporter)`). Zipped variant routes through the same content type / `ZIPPED_GML_DATA_SOURCE`. A `ZOOM_TO_FILE_ACTION` fires.
- **on re-import — archetype (a).** Same as GeoJSON: fixed `overlays/<name>` path, `copyFile`/`renameTo` overwrite, re-broadcast → `GMLSpatialDb` re-ingests. Key is the destination filename/path; content hash / feature identity play no role.

**Evidence.** `ImportGMLSort.<init>` `ldc #1 ".gml"` / `ldc #5 "overlays"` / `ldc #7 "GML"`; `match` → `isGML` `ldc #117 "<gml"; String.contains`; `getContentMIME` `ldc #7 "GML"` / `ldc #135 "application/octet-stream"`. `ImportGMLZSort.<init>` `ldc #1 ".zip"` … `"Zipped GML"`; `match` → `HasGML` `ldc #107 ".gml"; String.endsWith`. `ImportResolver.getDestinationPath` `getfield _folderName; FileSystemUtils.getItem; new File(File,String)`. `onFileSorted` `ldc_w #275 "com.atakmap.android.importexport.IMPORT_DATA"; AtakBroadcast.sendBroadcast`. **Cosmetic bug:** `ImportGMLSort`'s `IOException` log reads `"Error checking if GPX:"` (copy-paste artifact — harmless; the GML path is correct). The 4 ctor booleans (`validateExt`/`copyFile`/`importInPlace`) are accepted but unused. *(confidence: high; destination + conflict CONFIRMED.)*

**Clone (5.5.1.10):** [`ImportGMLSort.java#L38`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGMLSort.java#L38) · [`ImportGMLZSort.java#L36`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGMLZSort.java#L36) · [`GMLSpatialDb.java#L61`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/GMLSpatialDb.java#L61) · [`WktMapComponent.java#L352`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/wkt/WktMapComponent.java#L352).

---

## GPX — track-overlay vs GPX-as-route

**What it is.** A single `.gpx` file can be imported **two ways** depending on which resolver the user/importer selects: as a **spatial track overlay** (`ImportGPXSort`) or as a **route** of `Route` map items (`ImportGPXRouteSort`). This is the most behaviorally interesting format in the doc.

**The disambiguation is NOT by content.** `ImportGPXRouteSort extends ImportGPXSort` and **does not override `match()`** — both share the identical gate: ext `.gpx` **AND** `isGpx(InputStream)` reads the first **1024 chars** and requires the substring `<gpx` (`GPXMATCH`). The bytecode contains **no content discriminator** between a track-GPX and a route-GPX. The route variant only differs in its icon (`ic_route`), display name (`gpx_route_file`), and — decisively — its **overridden `onFileSorted`**.

**Flow.**
- **destination (both).** `super(".gpx", "overlays", …)` → `overlays/<name>.gpx`. The route variant chains to the protected `ImportGPXSort` ctor (`invokespecial ImportGPXSort.<init>(ZZZLjava/lang/String;)`), so it inherits the **same** `overlays` folder. File-copy destination is identical for track and route.
- **track path (`ImportGPXSort`).** Default `onFileSorted` broadcasts `IMPORT_DATA{contentType="GPX", mimeType="application/gpx+xml"}`; `GpxFileSpatialDb` (`extends OgrSpatialDb`) ingests into the spatial feature DB as a map overlay. **Re-import — archetype (a):** overlays file overwritten in place; the OGR importer refreshes that file's feature set (update-in-place by file path).
- **route path (`ImportGPXRouteSort`).** The overridden `onFileSorted` **skips `IMPORT_DATA` entirely** and broadcasts `com.atakmap.android.maps.ROUTE_IMPORT` with `filename = src.toString()` — the **ORIGINAL source path, pre-copy** (not the `overlays/` copy). `RouteMapReceiver`'s `ROUTE_IMPORT` branch (tableswitch branch 13) runs `ImportRouteTask(new File(sanitize(filename)))` → `RouteGpxIO.read` → `RouteGpxIO.toRoute`, which mints **`new Route(… UUID.randomUUID() …)` per route on every import**, then `getRouteGroup().addItem(r)` + `persist`. **There is NO lookup by route name/UID before adding.**
- **on re-import — route DUPLICATES (NOT archetype (a)/(c)/(d)).** Re-importing the same GPX route file **adds a second route to the map** (same title, brand-new random UID). The `overlays/` file copy is still overwritten in place, but the **on-map route is duplicated** because the live-object key is the route UID and a fresh UID is minted every time. (Note: a `deepFindUID` dedup *does* exist in `RouteMapReceiver`, but in a **different** branch — branch 1, keyed on `routeUID`/`uid` extras — **not** the `ROUTE_IMPORT` path.)

**Evidence.** `ImportGPXSort.<init>` `ldc #1 ".gpx"` / `ldc #5 "overlays"`; `match` → `isGpx` `ldc #132 "<gpx"; String.contains`; `getContentMIME` `ldc #150 "GPX"` / `ldc #152 "application/gpx+xml"`. `ImportGPXRouteSort.<init>` `getstatic R$string.gpx_route_file` / `invokespecial ImportGPXSort.<init>(ZZZLjava/lang/String;)`; `getIcon` `getstatic R$drawable.ic_route`; `onFileSorted` `ldc #38 "com.atakmap.android.maps.ROUTE_IMPORT"` / `ldc #43 "filename"` / `File.toString` on `src` / `AtakBroadcast.sendBroadcast` — **no `super.onFileSorted`, no `IMPORT_DATA`.** `RouteMapReceiver` `ROUTE_IMPORT` tableswitch branch 13 → `ImportRouteTask(File)`; `ImportRouteTask.doInBackground` → `RouteGpxIO.toRoute`; `onPostExecute` `getRouteGroup().addItem(route)` + `route.persist(...)` with **no `deepFindUID`/`getItemByName`/`findItem` before `addItem`**. `RouteGpxIO.toRoute` `invokestatic UUID.randomUUID + UUID.toString`. *(confidence: high; destination + conflict CONFIRMED by independent re-disassembly.)*

**Clone (5.5.1.10):** [`ImportGPXSort.java#L53`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGPXSort.java#L53) · [`ImportGPXRouteSort.java#L43`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportGPXRouteSort.java#L43) · [`RouteMapReceiver.java#L882`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/routes/RouteMapReceiver.java#L882) · [`RouteGpxIO.java#L203`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/routes/RouteGpxIO.java#L203) · [`GpxFileSpatialDb.java#L18`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/GpxFileSpatialDb.java#L18).

---

## DRW — FalconView drawing overlay (`.drw`)

**What it is.** A FalconView drawing stored as an **MS-Access / Jet database** (not raw SQLite), rendered as a vector overlay.

**Flow.**
- **match.** Ext gate `.drw` **AND** `hasDrawing(File)`: `MsAccessDatabaseFactory.createDatabase(file)` opens the `.drw` as an MS-Access DB; if non-null it runs `query("select * from Main", null)` and returns `cursor.moveToNext()`. So a file matches only if it (1) ends `.drw`, (2) opens as an MS-Access DB, and (3) the `Main` table has ≥1 row. *(Note: clone javadoc at line 49 mentions a "Points" table, but both clone line 69 and the 5.7.0.5 bytecode constant use `Main` — code wins.)*
- **destination.** `super(".drw", "overlays", …)` → `overlays/<name>.drw`. `FalconViewSpatialDb.getFileDirectoryName()` also returns `OVERLAYS_DIRECTORY`, so the resolver drop dir and the spatial-db scan dir agree.
- **registration.** `onFileSorted` broadcasts `IMPORT_DATA{contentType="DRW", mimeType="application/x-msaccess"}`; `FalconViewSpatialDb` (`extends SpatialDbContentSource`, provider hint `falconview`) parses the MS-Access drawing tables into a `FeatureDataStore2`.
- **on re-import — archetype (a).** Fixed `overlays/<name>.drw` path; `copyFile`/`renameTo` overwrite; `FalconViewSpatialDb` re-ingests, refreshing that file's features. Different name = separate overlay. No prompt/merge/content-hash.

**Evidence.** `ImportDRWSort.<init>` `ldc #1 ".drw"` / `ldc #5 "overlays"` / `getstatic R$string.drw_file` / `getstatic R$drawable.ic_falconview_drw`. `match` → `hasDrawing` `MsAccessDatabaseFactory.createDatabase` + `ldc #92 "select * from Main"` + `CursorIface.moveToNext`. `getContentMIME` `ldc #106 "DRW"` / `ldc #108 "application/x-msaccess"`. **Version-drift nit (no behavior change):** 5.7.0.5 `getDestinationPath` has an extension-normalization branch (if `getExt()` non-empty and `name` doesn't already `endsWith(getExt())`, append it) absent from the simplified clone form — but it's a **no-op for `.drw`** because the name already ends `.drw`, so destination/conflict semantics are unchanged. *(confidence: high; destination + conflict CONFIRMED.)*

**Clone (5.5.1.10):** [`ImportDRWSort.java#L34`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportDRWSort.java#L34) · [`#L66`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportDRWSort.java#L66) · [`#L81`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportDRWSort.java#L81) · [`FalconViewSpatialDb.java#L30`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/FalconViewSpatialDb.java#L30) · [`#L50`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/FalconViewSpatialDb.java#L50).

---

## LPT — FalconView points overlay (`.lpt`)

**What it is.** A FalconView **points** layer stored as an **MS-Access / Jet database** — the sibling of DRW (`.drw` = drawings, `.lpt` = points). Both are handled by the same `FalconViewSpatialDb`, which defines `LPT` and `DRW` as its two content types.

**Flow.**
- **match.** Ext gate `.lpt` **AND** `HasPoints(File)`: `MsAccessDatabaseFactory.createDatabase(file)` opens the `.lpt` as an MS-Access DB; if non-null it runs `query("select * from Points", null)` and returns `cursor.moveToNext()`. So a file matches only if it (1) ends `.lpt`, (2) opens as an MS-Access DB, and (3) has a non-empty `Points` table. (Contrast DRW, which checks the `Main` table.)
- **destination.** `super(".lpt", FileSystemUtils.OVERLAYS_DIRECTORY, …)` → `overlays/<name>.lpt`. Same `overlays/` drop dir that `FalconViewSpatialDb` scans.
- **registration.** `onFileSorted` broadcasts `IMPORT_DATA{contentType="LPT", mimeType="application/x-msaccess"}` (`getContentMIME` returns `(FalconViewSpatialDb.LPT, FalconViewSpatialDb.MIME_TYPE)`); `FalconViewSpatialDb` parses the MS-Access points tables into a `FeatureDataStore2` and renders them as a vector overlay (the on-map group name is `"LPT"`).
- **on re-import — archetype (a).** Fixed `overlays/<name>.lpt` path; `copyFile`/`renameTo` overwrite; `FalconViewSpatialDb` re-ingests. Different name = separate overlay. No prompt/merge/content-hash.

**Evidence.** `ImportLPTSort.<init>` `ldc ".lpt"` / `getstatic FileSystemUtils.OVERLAYS_DIRECTORY` / `getstatic R$string.lpt_file`. `match` → `ImportResolver.match` then `invokestatic HasPoints` (`MsAccessDatabaseFactory.createDatabase` + `ldc "select * from Points"` + `CursorIface.moveToNext`). `getContentMIME` → `getstatic FalconViewSpatialDb.LPT ("LPT")` / `FalconViewSpatialDb.MIME_TYPE ("application/x-msaccess")`. *(confidence: high; destination + conflict CONFIRMED by direct disassembly — this family's analyze agent did not return structured output, so it was filled in by hand.)*

**Clone (5.5.1.10):** [`ImportLPTSort.java#L34`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportLPTSort.java#L34) · [`#L40`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportLPTSort.java#L40) · [`#L72`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportLPTSort.java#L72) · [`FalconViewSpatialDb.java#L30`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/spatial/file/FalconViewSpatialDb.java#L30).

---

## Tileset (`.zip` → `layers/`)

**What it is.** A zipped tiled-imagery dataset (raster) that `TilesetInfo.parse` can read into a `DatasetDescriptor`. Note this is a **`.zip`** that lands in `layers/`, **not** `overlays/`.

**Flow.**
- **match.** Two gates: (1) ext gate `.zip`; (2) decisive content sniff — `TilesetInfo.parse(file)` must yield a **non-null** `DatasetDescriptor` (an `IOException`/`IllegalStateException` is caught → false). A generic `.zip` that `TilesetInfo` can't parse is rejected even though the extension matches.
- **destination — archetype (b) input.** `super(".zip", "layers", …)` → `getDestinationPath` = `FileSystemUtils.getItem("layers")/<name>.zip`. The successor `ImportTilesetResolver` makes this literal: `super(".zip", FileSystemUtils.getItem("layers"), …)`.
- **registration — via the LAYER SCANNER, not a 1:1 content-type importer.** *Honest caveat:* **no `Importer` registers `getContentType()=="Tileset"`.** The `IMPORT_DATA{contentType="Tileset"}` broadcast has no 1:1 consumer (`LayersMapComponent`'s `ExternalLayerDataImporter` registers `IMPORTER_CONTENT_TYPE="External Native Data"`). The artifact actually goes live through `TilesetLayerScanner` — a `GenericLayerScanner` registered via SPI, constructed `super("Tileset")`, scanning `getDefaultScanDirs("layers", true)`. `LayersMapComponent._initializeLayers()` fires `ScanLayersService.START_SCAN_LAYER_ACTION`; the scanner discovers the new file under `layers/`, parses it (`TilesetInfo`) into a `DatasetDescriptor`, and persists it into the **`PersistentRasterDataStore`** SQLite catalog; the raster layer then renders.
- **on re-import — archetype (a) on disk + (b) in catalog.** (1) File layer: `beginImport` → `mkdirs` → `copyFile`/`renameTo` into `layers/<name>.zip`, **overwriting** a prior same-named file. (2) Catalog layer: the canonical `ExternalLayerDataImporter.importData()` pattern is `if (database.contains(file)) deleteData(...)` → `database.add(file,…)` — **remove-then-add update, no duplicate row.** So re-importing the same tileset = same file overwritten **and** catalog row refreshed (not duplicated); a different filename produces a separate tileset entry.

**Evidence.** `ImportTilesetSort.<init>` `ldc #1 ".zip"` / `ldc #3 "layers"` / `getstatic R$string.tileset` / `getstatic R$drawable.ic_menu_maps`. `match` `ImportResolver.match` → `invokestatic TilesetInfo.parse:(File)DatasetDescriptor` → `ifnull` → `iconst_1`/`iconst_0`; exception table maps `IOException`+`IllegalStateException` → `iconst_0`. `getContentMIME` `ldc #48 "Tileset"` / `ldc #50 "application/zip"`. `beginImport` `getDestinationPath` → `IOProviderFactory.mkdirs` → `FileSystemUtils.copyFile` / `renameTo`. `TilesetLayerScanner` `super("Tileset")`, `getDefaultScanDirs("layers", true)`. *(confidence: high; destination + conflict CONFIRMED. Doc-anchor nit: the catalog importer is `com.atakmap.android.layers.ExternalLayerDataImporter`, and the store's concrete type is `PersistentRasterDataStore` extending abstract `LocalRasterDataStore` — neither changes destination/conflict behavior.)*

**Clone (5.5.1.10):** `ImportTilesetSort.java` L27-29 `super(".zip","layers",…)`; `ImportTilesetResolver.java#L24` `super(".zip", FileSystemUtils.getItem("layers"),…)`; `TilesetLayerScanner.java` L48 `super("Tileset")` / L94 `getDefaultScanDirs("layers", true)`; `LayersMapComponent.java` L153 `IMPORTER_CONTENT_TYPE="External Native Data"`, L484-486 `START_SCAN_LAYER_ACTION`, L821 `PersistentRasterDataStore`; `ExternalLayerDataImporter.java` L95-105 `contains`→`deleteData`→`add`.

---

## SQLite database (`.sqlite`) + GeoPackage (`.gpkg`)

**What it is.** A schema-sniffing **dispatcher** for ATAK's own SQLite databases — not a generic "open any sqlite" path. `ImportSQLiteSort` classifies a `.sqlite` by its **table signature** and installs it as the matching canonical ATAK database under `Databases/`. GeoPackage (`.gpkg`) is handled separately by `GeoPackageImportResolver`.

**Flow.**
- **match — schema classification.** Ext gate `.sqlite` **AND** `getType(File) != null`. `getType` opens the DB and matches its tables against six `TYPE` signatures; it returns the first match, else `null` (an unrecognized sqlite is **not claimed**):

  | TYPE | required tables | canonical name → destination |
  |---|---|---|
  | `COT` | `spatial_ref_sys`, `CotEvent` | `Databases/cot.sqlite` |
  | `LAYERS2` | `layers`, `catalog`, `metadata` | `Databases/layers2.sqlite` |
  | `SSE` | `spatial_ref_sys`, `Entity`, `ReportRelationMap`, `Photo` | `Databases/sse.sqlite` |
  | `SITEEXPLOITATION` | (same as SSE) | `Databases/siteexploitation.sqlite` |
  | `SPATIAL` | `spatial_ref_sys`, `File`, `Geometry`, `Style` | `Databases/spatial.sqlite` |
  | `USERICONSET` | (iconset schema) | `Databases/iconsets.sqlite` |

- **destination — fixed canonical name per type.** The super ctor passes `destinationDir = null`; the overridden `getDestinationPath` routes to `FileSystemUtils.getItem(type._folder)` (all six use `_folder = "Databases"`) and renames to the type's **canonical filename** (`type._filename`). So importing a recognized SPATIAL sqlite lands it at `Databases/spatial.sqlite` **regardless of the source filename**.
- **registration.** The file *is* the live ATAK database for that subsystem (CoT export DB, the `layers2` raster catalog, the spatialite feature DB, the iconset DB, site-exploitation). It is consumed in place by whichever subsystem owns that canonical DB — effectively a **restore/replace of a named internal database**.
- **on re-import — archetype (a) on the canonical name.** Because the destination is the fixed canonical filename, re-importing any sqlite of the same TYPE **overwrites the one canonical DB in place** (last-write-wins) — the source filename is irrelevant. Two different SPATIAL sqlites both target `Databases/spatial.sqlite` and collide.
- **GeoPackage (`.gpkg`).** A dedicated `gov.tak.api.importfiles.GeoPackageImportResolver` claims `.gpkg`; GeoPackage **tile** content is *also* claimed by `ImportLayersSort` and lands in `imagery/` as native raster (see [`file-format-flows.md` → layers](./file-format-flows.md#layers)). Vector-feature GeoPackages route to the feature DB. *(The dedicated resolver's exact destination folder was not pinned down here — treat the GeoPackage→`imagery/` raster path, verified in batch 1, as the load-bearing route.)*

**Evidence.** `ImportSQLiteSort.<init>` `ldc ".sqlite"` / `aconst_null` (no fixed folder) / `ldc "SQLite Database"`. `match` → `ImportResolver.match` then `invokestatic getType` (returns `ImportSQLiteSort$TYPE` or null). `TYPE` enum constants `COT`/`LAYERS2`/`SSE`/`SITEEXPLOITATION`/`SPATIAL`/`USERICONSET`, each `(canonical filename, required-tables[], "Databases")`. `getDestinationPath` overridden → `FileSystemUtils.getItem(type._folder)` + `type._filename`. `getContentMIME` → `("SQLite Database","application/x-sqlite3")`. `GeoPackageImportResolver` ctor `ldc "gpkg"`. *(confidence: medium-high; ImportSQLiteSort dispatch + canonical-name overwrite CONFIRMED by direct disassembly — this family's analyze agent did not return structured output. GeoPackage destination folder is the one detail not fully pinned.)*

**Clone (5.5.1.10):** [`ImportSQLiteSort.java#L38`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSQLiteSort.java#L38) (`enum TYPE`) · [`#L81`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSQLiteSort.java#L81) (`super(".sqlite", null, …)`) · [`#L211`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSQLiteSort.java#L211) (`getDestinationPath`).

---

## Video (`.mp4`, `.ts`, `.mkv`, …)

**What it is.** A raw video file. Becomes a live alias in the ATAK video library.

**Flow.**
- **match — pure extension gate, NO content sniff.** When this sorter has no single-extension override (`_ext==null`), `match` lowercases the file's extension and checks membership in the `VIDEO_EXTENSIONS` set. The set is built from a 13-slot `String[]` (`mpeg, mpg, ts, avi, mp4, 264, 265, wmv, mov, webm, mov, mkv, flv`) — `mov` appears twice (slots 8 and 10), so the `HashSet` holds **12 distinct extensions**; the duplicate is harmless. There is **no magic-byte check** (the source even carries a `// TODO: Check if the file is actually a video`).
- **destination.** The super ctor passes `destinationDir = null`; the destination is supplied entirely by the overridden `getDestinationPath` = `new File(FileSystemUtils.getItem("tools/videos"), file.getName())` → `tools/videos/<name>`.
- **registration — via the 5-second folder poll, not an `IMPORT_DATA` importer.** `beginImport` rewrites `IMPORT_INPLACE`→`IMPORT_COPY`, copies the file to `tools/videos/<name>`, then **if the original source path is under `<cacheDir>/atakdata` it is `SECURE_DELETE`d** (`IOProviderFactory.delete(file, IOProvider.SECURE_DELETE)` — flag value `1`). The base `IMPORT_DATA{contentType="Video"}` broadcast still fires, but no registered importer consumes it. Instead `VideoManager`'s `VideoFileWatcher` polls `atak/tools/videos` **every 5 s**, sees the file, builds `new ConnectionEntry(file)`, and `addEntries(...)`, surfacing it in the Video library. (`.xml` sidecars in the same folder are parsed by `VideoXMLHandler`.)
- **on re-import — archetype (a).** Fixed `tools/videos/<name>` keyed solely on source filename; `copyFile`/`renameTo` with no uniquify/prompt → **silent overwrite**. Two different videos sharing a name collide; second wins. `VideoFileWatcher` then re-scans the overwritten path and refreshes the `ConnectionEntry`.

**Evidence.** `ImportVideoSort.<init>` `super(ext, null, getString(R$string.video), getDrawable(R$drawable.ic_video_alias))` (`aconst_null` for `destinationDir`). `static{}` `bipush 13 / anewarray String` → 13 `ldc`s → `Arrays.asList` → `new HashSet`. `match` `getfield _ext / ifnonnull` else `FileSystemUtils.getExtension(file,Z,Z) / toLowerCase / getstatic VIDEO_EXTENSIONS / Set.contains`. `beginImport` remove `IMPORT_INPLACE` / add `IMPORT_COPY` / `super.beginImport`; then `new File(getCacheDir(),"atakdata")` / `startsWith` / `IOProviderFactory.delete(file, 1)`. `getDestinationPath` `ldc #154 "tools/videos" / FileSystemUtils.getItem / new File(dir, getName())`. `getContentMIME` `new Pair("Video","application/octet-stream")`. *(confidence: high; destination + conflict CONFIRMED by independent re-disassembly.)*

**Clone (5.5.1.10):** [`ImportVideoSort.java#L57`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportVideoSort.java#L57) (extensions L36-39, `match` L57-76, `beginImport`+`getDestinationPath` L78-109). **Drift (cosmetic):** clone `getContentMIME` returns `ResourceFile.UNKNOWN_MIME_TYPE` (= `application/octet-stream`); clone ctor uses `context.getDrawable`, bytecode uses `getResources().getDrawable` — same values/behavior; the `copyFile` ctor param is dead in both (beginImport forces `IMPORT_COPY`).

---

## APK — Android install handoff (`.apk`)

**What it is.** An Android application package. ATAK does **not** install it — it stages to `tmp/` and hands off to the OS PackageInstaller.

**Flow.**
- **match.** Ext gate `.apk` **AND** `isApk(file)` = `FileSystemUtils.ZipHasFile(file, "AndroidManifest.xml")` — the file must be a ZIP container holding an `AndroidManifest.xml` entry.
- **destination.** `super(".apk", FileSystemUtils.getItem("tmp"), …)` → `tmp/<name>.apk` (bytecode inlines literal `tmp`; clone uses `FileSystemUtils.TMP_DIRECTORY`).
- **registration — OS handoff, not a MapComponent importer.** `beginImport` forces `IMPORT_COPY` (removes `IMPORT_MOVE`/`IMPORT_INPLACE`, adds `IMPORT_COPY`), copies to `tmp/<name>.apk`. The overridden `onFileSorted` calls `super.onFileSorted` (which fires the inert `IMPORT_DATA` broadcast — **no MapComponent consumes the package-archive MIME**), then `AppMgmtUtils.install(context, file)` launches `Intent(ACTION_VIEW)` with a `FileProvider` `content://` URI + `application/vnd.android.package-archive` MIME (`FileProviderHelper.setDataAndType`, `FLAG_GRANT_READ_URI_PERMISSION`) via `startActivity`. The OS PackageInstaller then prompts install/update. (`install` returning `true` means only that the install Activity was *found*, not that install succeeded — in-code comment confirms.)
- **on re-import — archetype (a).** `getDestinationPath` is keyed purely on `file.getName()` with no uniquification; `copyFile` overwrites a same-named staged file. Strictly the import mode is **copy** (not in-place move), but the observable destination-file result is filename / overwrite-in-place.

**Evidence.** `ImportAPKSort.<init>` `ldc #1 ".apk"` / `ldc #5 "tmp"` / `FileSystemUtils.getItem` → `ImportResolver.<init>(String,String,String,Drawable)`. `match` `ImportResolver.match` → `invokestatic #54 isApk`; `isApk` `ldc #129 "AndroidManifest.xml"` / `FileSystemUtils.ZipHasFile`. `getContentMIME` `ldc #122 "Android App"` / `ldc #124 "application/vnd.android.package-archive"`. `beginImport` `Set.remove`(IMPORT_MOVE/IMPORT_INPLACE) / `Set.add`(IMPORT_COPY) / `super.beginImport`. `onFileSorted` `super.onFileSorted` → `invokestatic AppMgmtUtils.install`. `AppMgmtUtils.install` `new Intent` / `ldc "android.intent.action.VIEW"` / `ldc "application/vnd.android.package-archive"` / `FileProviderHelper.setDataAndType` / `Context.startActivity`. *(confidence: high; destination + conflict CONFIRMED.)*

**Clone (5.5.1.10):** [`ImportAPKSort.java#L31`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAPKSort.java#L31) · [`#L41`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAPKSort.java#L41) · [`#L52`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAPKSort.java#L52) · [`ImportResolver.java#L327`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L327) · [`AppMgmtUtils.java#L75`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/update/AppMgmtUtils.java#L75) · [`FileProviderHelper.java#L30`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/util/FileProviderHelper.java#L30).

---

## Certificate (`.p12`)

**What it is.** A PKCS#12 keystore (client certificate or trust-store CA). The **live artifact is the cert bytes inside the encrypted cert-store DB**, not a file on disk — this is a **state-apply** format, not a file overlay.

**Flow.**
- **match — extension-only.** `ImportCertSort.match` calls `super.match` and returns it unchanged. **No content sniff** — the only decisive check is the `.p12` lowercase suffix. (The clone carries a `//TODO look for magic numbers / KeyStore validate…` comment; never implemented. The ctor forces `validateExt=true` precisely because `match()` does no extra validation, "otherwise this sorter will match everything".)
- **destination — staged then applied.** Two steps: (1) `super(".p12", "cert", …)` + `beginImport` adds `IMPORT_COPY`, so the `.p12` is first copied to `cert/<name>.p12` (`getDestinationPath` = `FileSystemUtils.getItem("cert")/<name>`). (2) **But the staged file is transient:** `AtakCertificateDatabaseBase.importCertificate(location, server, type, deleteOriginal=true)` reads the `.p12` bytes (`FileSystemUtils.read`), writes them into the cert store, and **deletes the staged file** (`deleteOriginal=true` → `FileSystemUtils.deleteFile`). Net: `cert/` is staging only; the final home is the cert store.
- **registration.** Encrypted cert-store DB (+ a `cot_streams` network connection binding when a connect string is present). No `connectString` → `saveCertificate(type, bytes)`; with one → `saveCertificateForServerAndPort(type, host, port, bytes)` (the host/port are resolved via `NetConnectString`).
- **on re-import — cert-store SLOT keyed (state-apply, NOT archetype (a)).** The authoritative identity is the **cert-store slot** keyed by certificate **TYPE** (and optionally **server + port**): `TYPE_TRUST_STORE_CA` / `TYPE_CLIENT_CERTIFICATE`, per-server when a connect string is present. Re-import **replaces** the bytes for that slot — **silent last-write-wins, no duplicate row, no prompt** (the store exposes `save`/`get`/`delete` triples for `(type)`, `(type,server)`, `(type,server,port)`, confirming the tuple identity). Because `deleteOriginal=true`, there's no lingering on-disk duplicate to collide with next time. *(The transient `cert/<name>.p12` staging step is itself filename-keyed overwrite, but it is deleted on success, so it is not the operative conflict unit.)*

**Evidence.** `ImportCertSort.<init>` `ldc #1 ".p12"` / `ldc #3 "cert"` / `ldc #7 "P12 Certificate"` → `ImportResolver.<init>(String,String,String,Drawable)`. `match` `invokespecial ImportResolver.match` → `ifne` → return (ext gate only). `beginImport` `getstatic SortFlags.IMPORT_COPY` / `Set.add` / `super.beginImport`. `getContentMIME` `new Pair("P12 Certificate","application/x-pkcs12")`. `AtakCertificateDatabaseBase.importCertificate` `FileSystemUtils.read` → bytes; `iload_3 ifeq` → `FileSystemUtils.deleteFile`; `saveCertificate(type,bytes)` (no connectString) else `saveCertificateForServerAndPort(type,host,port,bytes)`. `AtakCertificateDatabaseIFace`: `TYPE_TRUST_STORE_CA="TRUST_STORE_CA"`, `TYPE_CLIENT_CERTIFICATE="CLIENT_CERTIFICATE"`. *(confidence: high; destination=cert-store + conflict=cert-store-slot CONFIRMED. Correction vs an earlier hint: the per-server write is `saveCertificateForServerAndPort(type,host,port,bytes)`, NOT `saveCertificateForServer(type,server,bytes)` — the `connectString` branch resolves host/port and calls the …`ForServerAndPort` variant.)*

**Clone (5.5.1.10):** [`ImportCertSort.java#L54`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCertSort.java#L54) · [`#L62`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCertSort.java#L62) · [`#L108`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCertSort.java#L108) · [`#L348`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportCertSort.java#L348).

### Worked example — `TAK_Server.zip` (TAK Server enrollment)

A bare `.p12` is the simple case (above). The real-world enrollment artifact is a **Data Package**, not a bare cert, and exercises the README §3 container fan-out.

**The package (`TAK_Server.zip`).** `unzip -l` shows:
- `MANIFEST/manifest.xml` — a `MissionPackageManifest version="2"`, `uid="5151d767-1f18-4beb-81ab-3656079d2389"`, `name="TAK_Server.zip"`, `onReceiveDelete="true"`, **3 Contents**.
- `certs/config.pref` — a preferences file carrying:
  - a `cot_streams` connection: `connectString0="tak.shihyu.dev:8089:ssl"`;
  - `com.atakmap.app_preferences` keys: `caLocation="cert/truststore-TAK-ID-CA-01.p12"`, `caPassword="atakatak"`, `certificateLocation="cert/shihyu.p12"`, `clientPassword="atakatak"`.
- `certs/shihyu.p12` — the client certificate.
- `certs/truststore-TAK-ID-CA-01.p12` — the trust-store CA.

**Composite flow.**
1. **Container.** `TAK_Server.zip` is **not** claimed by `ImportCertSort`. It is a Mission Package (manifest v2) → `ImportMissionPackageSort` (`getContentMIME` = `("Data Package","application/zip")`). See **README §3** for the container's content-hash dedup + UID-keyed extraction directory.
2. **Fan-out.** After extraction the bundled members are imported by the `ImportResolver` framework:
   - `config.pref` → the **pref importer** (`ImportPrefSort` / `PreferenceControl.loadSettings`) merges the `cot_streams` server connection + the `caLocation`/`certificateLocation`/passwords into `SharedPreferences` (per-key — see the Preferences section below). This is what binds the server `tak.shihyu.dev:8089:ssl`.
   - the two `.p12` files are installed into the **cert store** at exactly the `cert/...` paths the pref declares — `ImportCertSort.finalizeImport()`'s `importCertificateFromPreferences` reads precisely those `caLocation`/`certificateLocation`/`caPassword`/`clientPassword` keys, closing the loop.

   The `config.pref` `caLocation`/`certificateLocation` = `cert/...` match the **bare-`.p12` destination exactly** — the cert-installation code is shared; the only difference is whether the `.pref` (and thus the server binding + cert paths) arrives inside the bundle or is already in prefs.

**Contrast.**

| Path | Trigger | Resolver | What happens |
|---|---|---|---|
| **Bare `.p12`** | a lone `client.p12` | `ImportCertSort` | staged to `cert/`, `importCertificate` writes the bytes into the cert-store slot `(type[+server+port])`, staged file deleted. **No server binding** (no `.pref`). |
| **Enrollment `TAK_Server.zip`** | a Data Package (manifest v2) | `ImportMissionPackageSort` → fan-out | `config.pref` → SharedPreferences (server connection + cert locations) **and** both `.p12` → cert store. Server connection goes live. |

*(I did not fully trace the internal MissionPackage extractor → `.pref` dispatch class chain in bytecode — see README Layer-B fan-out — but the manifest Contents + `config.pref` `caLocation`/`certificateLocation="cert/..."` align with the bare-`.p12` destination, and `importCertificateFromPreferences` reads exactly those keys.)*

---

## Preferences (`.pref` — XML / JSON)

**What it is.** An ATAK preferences document, either **XML** (`ImportPrefSort`) or **JSON** (`ImportJSONPrefSort`). A **state-apply** format: the operative effect is a per-key merge into Android `SharedPreferences`; the on-disk copy is a sanitized side artifact, not the system of record.

**Flow.**
- **match — same ext gate, disambiguated by content sniff.** Both ctors `super(".pref", PreferenceControl.DIRNAME, …)`, so the parent ext gate is identical (any `*.pref`). Each subclass then content-sniffs:
  - **XML (`ImportPrefSort`)** reads `char[8192]` and requires `content.contains("<preferences")` **AND** (`content.contains("<preference key")` **OR** `content.contains("<entry key")`).
  - **JSON (`ImportJSONPrefSort`)** reads `char[64]` and requires `content.startsWith("{")` **AND** `content.contains("PreferenceControl")` (`JSONPreferenceControl.PREFERENCE_CONTROL`).
  The two sniffs are mutually exclusive (XML starts `<`, JSON `{`), so a `.pref` routes to exactly one resolver; a malformed `.pref` matching neither is claimed by neither. *(XML's `isPreference` also sets a side-flag `containsEntryToDelete` if the body contains any of `clientPassword`/`caPassword`/`certificateLocation`/`caLocation`/`networkMeshKey`, for later credential scrubbing — it does not affect `match()`.)*
- **destination.** `getDestinationPath` = `new File(FileSystemUtils.getItem(PreferenceControl.DIRNAME), name)` where `DIRNAME = CONFIG_DIRECTORY + "/prefs"`. `beginImport` forces `IMPORT_COPY` → overwrite-by-name copy under `prefs/`.
- **registration — applied into `SharedPreferences`, per key.** `onFileSorted` applies the file:
  - **XML** consults the `pref_import_pref_action` policy: `ALLOW` → `loadSettings` immediately; `PROMPT` (always for `enterprise.pref`) → AlertDialog then load on Yes; `DENY` → skip. Application walks each `<preference>`/`<entry key>` and writes via `editor.putString/Boolean/Int/Float/Long/StringSet(key,value)` + `apply()`, honoring `WriteOncePreferences` and remapping legacy baseline names.
  - **JSON** calls `JSONPreferenceControl.getInstance().load(file, false)` directly (the `ALLOW/PROMPT/DENY` + `WriteOncePreferences` path is **XML-specific**).
  - `ImportPrefSort.finalizeImport` then scrubs the sensitive credential keys from the on-disk copy **after** loading them.
- **on re-import — MERGE / per-key overwrite (NOT file-level dedup).** Re-importing "the same" `.pref` does **not** dedup or skip by filename or hash: the file overwrites the prior copy in `prefs/` by name, and **every entry is re-applied into `SharedPreferences` via `putX(key,value)`, overwriting whatever that key currently holds.** The conflict unit is the **individual preference key**, not the file. Distinct keys across files are additive (union); the same key twice → last-writer-wins. One guard: **`WriteOncePreferences` keys that already exist are skipped** (write-once). Whether the load happens at all is gated by `pref_import_pref_action` (ALLOW/PROMPT/DENY); `enterprise.pref` always prompts. Ordinary key collisions silently overwrite (no prompt/duplicate dialog).

**Evidence.** `ImportPrefSort.<init>` `ldc #7 ".pref"` / `getstatic PreferenceControl.DIRNAME` / `getstatic R$string.preference_file`; sniff `ldc #160 "<preferences"` / `ldc #166 "<preference key"` / `ldc #168 "<entry key"` (buffer `sipush 8192`); `getContentMIME` `"ATAK Preferences"` + `HttpUtil.MIME_XML`. `ImportJSONPrefSort.<init>` `ldc #1 ".pref"` / `getstatic PreferenceControl.DIRNAME`; sniff `bipush 64` / `ldc #124 "{"` + `startsWith` / `ldc #132 "PreferenceControl"` + `contains`; `getContentMIME` `"ATAK Preferences"` + `application/json`; `onFileSorted` `JSONPreferenceControl.getInstance` / `load(File,Z)`. XML apply: `ldc "pref_import_pref_action"` / `ldc "ALLOW"` / `PreferenceControl.getInstance` / `loadSettings(String,Z)`; `enterprise.pref` branch `ldc #252`. `PreferenceControl.loadSettings(Node,String,List)` `getSharedPreferences().edit()` → `Editor.putString/putBoolean/putInt/putFloat/putLong/putStringSet` → `apply()`; `WriteOncePreferences.contains(key) && SharedPreferences.contains(key)` → `ifeq` (skip). `PreferenceControl.DIRNAME = CONFIG_DIRECTORY + "/prefs"`. *(confidence: high; destination + conflict CONFIRMED by independent re-disassembly.)*

**Clone (5.5.1.10):** [`ImportPrefSort.java#L78`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportPrefSort.java#L78) · [`#L150`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportPrefSort.java#L150) · [`ImportJSONPrefSort.java#L35`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportJSONPrefSort.java#L35) · [`PreferenceControl.java#L80`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/app/preferences/PreferenceControl.java#L80) · [`#L648`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/app/preferences/PreferenceControl.java#L648) · [`#L664`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/app/preferences/PreferenceControl.java#L664) · [`JSONPreferenceControl.java#L28`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/app/preferences/json/JSONPreferenceControl.java#L28).

---

## Alternate contacts (`.csv`)

**What it is.** A CSV describing alternate (out-of-band) contact info — phone flag, VoIP SIP, email, XMPP — for callsigns. **Does NOT register any peer contact:** it applies **one callsign-matched row to the IMPORTING device's own** alternate-contact prefs.

**Flow.**
- **match.** Ext gate `.csv` **AND** `isContact`: reads the first **1024 chars** and requires the literal marker `::ALTERNATE CONTACT v2` (`CONTACT_MATCH`). Empty content → false.
- **destination — NONE (identity).** `getDestinationPath` is overridden to a **no-op identity**: `aload_1; areturn` (returns the same `File`). **No managed-folder copy.** `beginImport` calls `onFileSorted(file, file, flags)` (same file as src and dst). If the source sits under `<cacheDir>/atakdata`, it is `SECURE_DELETE`d after applying (`IOProviderFactory.delete(file, 1)`). No persistent on-disk artifact retained.
- **registration.** `importContact` reads the device's own callsign (lowercased) and `AtakPreferences`, then for each non-`::` line splits on `,` into exactly **5 fields**; keeps **only** the line whose callsign matches this device, and writes that line's phone-flag / SIP / email / XMPP into the device's own `SharedPreferences` (`saHasPhoneNumber`, `saSipAddress` (+`saSipAddressAssignment=manual_entry`), `saEmailAddress`, `saXmppUsername`). Empty / `NA` / `N/A` fields are skipped (`IGNORE=["NA","N/A"]`). Rows for other callsigns are silently skipped. `onFileSorted` still broadcasts `IMPORT_DATA{contentType="Contact Info", mimeType="text/csv"}` but no importer consumes it.
- **on re-import — pref-key per-field overwrite (state-apply).** Re-importing the same CSV re-applies the matching row via `prefs.set(key, value)`, **unconditionally overwriting** each of the four keys — no dedup, no prompt, no cross-row merge. The deciding key is the device's own **callsign** (which row applies) plus the fixed pref key names (which value is overwritten). Empty/NA fields leave the existing pref untouched (selective per-field overwrite). The source file is not retained, so there is no file-level collision.

**Evidence.** `ImportAlternateContactSort` constants `CONTACT_MATCH="::ALTERNATE CONTACT v2"`, `COMMENT="::"`, `SPLIT=","`, `IGNORE=[NA,N/A]`. `<init>` `ldc #1 ".csv"` + `ImportResolver.<init>(…,Drawable)` with empty `displayName`. `getContentMIME` `new Pair("Contact Info","text/csv")`. `getDestinationPath` `aload_1; areturn`. `beginImport` `new File(getCacheDir(),"atakdata")` + `startsWith` + `IOProviderFactory.delete(file, 1)`; `onFileSorted(aload_1, aload_1, aload_2)`. `match` super.match + `isContact` (`::ALTERNATE CONTACT v2`). `importContact` `AtakPreferences.set` for `saHasPhoneNumber`/`saSipAddress`/`saEmailAddress`/`saXmppUsername`; `arraylength==5`; callsign `isEquals` gate. *(confidence: high; destination=applied/not-stored + conflict=pref-key CONFIRMED by independent re-disassembly. Anchored-fact correction: `Contact Info` is the ATAK `contentType` label, not an IANA MIME — the MIME is `text/csv`; and NO contact is registered into any contact list/DB.)*

**Clone (5.5.1.10):** [`ImportAlternateContactSort.java#L43`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L43) · [`#L111`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L111) · [`#L168`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L168) · [`#L194`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L194) · [`#L226`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportAlternateContactSort.java#L226). **Drift (cosmetic):** clone uses the 4-arg `(String,String,boolean,boolean)` super + `getIcon()=ic_menu_contact`; 5.7.0.5 bytecode calls the `(String,String,String,Drawable)` super passing `ic_csv` — icon differs, all load-bearing semantics identical.

---

## INFZ — product-repo cache (`.infz`)

**What it is.** A ZIP (`application/zip`) acting as a self-contained ATAK product / plugin **repo cache**. Its match-defining payload is a `product.inf` CSV index. **An import-handoff to the product repo**, not a spatial/feature DB or Android installer.

**Flow.**
- **match.** Ext gate `.infz` **AND** `isRepoCache(file)`: `FileSystemUtils.GetZipFileString(zip, "product.inf")` must be a **non-empty** entry whose text **contains a comma** (`,` = at least one CSV repo row). So `match()` is true iff the `.infz` archive holds a non-empty `product.inf` with a comma.
- **destination — fixed filename.** `super(".infz", FileSystemProductProvider.LOCAL_REPO_PATH, …)` where `LOCAL_REPO_PATH = AppMgmtUtils.APK_DIR + "/custom/"` and `APK_DIR = FileSystemUtils.SUPPORT_DIRECTORY + "/apks"`. The override `getDestinationPath` **replaces the basename** so the stored file is **ALWAYS** `<SUPPORT>/apks/custom/product.infz` (constant `REPOZ_INDEX_FILENAME`), independent of the source filename.
- **registration — self-completing, not via a downstream `IMPORT_DATA` importer.** `onFileSorted` calls `super.onFileSorted` (informational `IMPORT_DATA{contentType="Product Repo Cache"}`), then **deletes the prior extracted `product.inf`** (`FileSystemProductProvider.LOCAL_REPO_INDEX`) and triggers a re-sync: `ApkUpdateComponent.getInstance().getProviderManager()` → `MapView.post(Runnable → providerManager.sync(false,false))`. `FileSystemProductProvider` then treats `apks/custom/` as the LOCAL repo, exposing the bundled products in App Management.
- **on re-import — archetype (a) on the constant name + forced re-sync.** Because the destination filename is hard-coded `product.infz`, two **different** source `.infz` files collide on the **same** path. `copyFile`/`renameTo` **overwrites in place**, `onFileSorted` deletes the prior `product.inf` index and re-syncs. No versioning/merge/prompt/dedup. `conflictKey = filename` (constant `product.infz`).

**Evidence.** `ImportINFZSort.<init>` `ldc #1 ".infz"` / `getstatic FileSystemProductProvider.LOCAL_REPO_PATH` / `getstatic R$string.app_mgmt_product_repo` / `getstatic R$drawable.ic_menu_plugins`. `match` `ImportResolver.match` → `invokestatic isRepoCache`. `isRepoCache` `ldc #145 "product.inf"` / `FileSystemUtils.GetZipFileString` / `isEmpty` / `ldc #155 ","` / `String.contains`. `getDestinationPath` `ImportResolver.getDestinationPath` → `new File(dest.getParentFile(), ldc #84 "product.infz")`. `onFileSorted` `super.onFileSorted` / `getstatic LOCAL_REPO_INDEX` / `FileSystemUtils.getItem` / `isFile`→`FileSystemUtils.delete` / `MapView.post(ImportINFZSort$1 → providerManager.sync(false,false))`. `getContentMIME` `ldc #138 "Product Repo Cache"` / `ldc #140 "application/zip"`. `AppMgmtUtils.REPO_INDEX_FILENAME="product.inf"`, `REPOZ_INDEX_FILENAME="product.infz"`. *(confidence: high; destination + conflict CONFIRMED.)*

**Clone (5.5.1.10):** [`ImportINFZSort.java#L35`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportINFZSort.java#L35) · [`FileSystemProductProvider.java#L43`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/update/FileSystemProductProvider.java#L43) · [`AppMgmtUtils.java#L49`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/update/AppMgmtUtils.java#L49) · [`ImportResolver.java#L327`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L327).

---

## Support info package (`support.inf` / `atak_splash.png`)

**What it is.** A niche diagnostics + branding format. Only **two hard-coded filenames** are ever accepted — `support.inf` (support-info text) and `atak_splash.png` (custom app splash image) — both parked in `support/`. **No MIME, no importer, no DB/layer:** the resolver's whole job is to drop the two named files in `support/`.

**Flow.**
- **match — exact filename, NO extension gate.** The ctor passes an **empty string** (`ldc #1` = empty Utf8) as **both** the ext and folder args, so `_ext` resolves to `null` and the `FileFilter` applies **no extension gate** (accepts any file). The decisive check is `getType(File) != null`: `for (TYPE t : TYPE.values()) if (t._filename.equalsIgnoreCase(file.getName())) return t;` — an **exact, case-insensitive filename equality** against the two enum constants (`SUPPORTINF="support.inf"`, `SPLASH="atak_splash.png"`, both folder `"support"`). So `match()` returns true ONLY for a file literally named `support.inf` or `atak_splash.png` (any directory position, since there's no ext filter).
- **destination.** `getDestinationPath` = `new File(FileSystemUtils.getItem("support"), file.getName())` → `support/<name>` (`SUPPORT_DIRECTORY="support"`).
- **registration — filesystem only.** `getContentMIME()` is **NOT overridden** → the parent returns `null`. The base `onFileSorted` checks `getContentMIME()`; null → it **skips the `IMPORT_DATA` broadcast entirely**. So the file just lives in `support/` (read later by the app for splash/diagnostics), plus an optional notification + `ImportListener` callbacks. No DB/layer/cert/installer/SharedPreferences registration.
- **on re-import — archetype (a).** `getDestinationPath` is a fixed path keyed purely on the (fixed) filename; `copyFile`/`renameTo` **overwrite in place**, replacing any prior file. Because the two enum filenames are constants, distinct support bundles can never coexist — the latest import of each name wins. No prompt/duplicate/merge.

**Evidence.** Outer `<init>` `ldc #1` (empty) as ext **and** folder / `ldc #3 "Support Info File"` / `ImportResolver.<init>(…,Drawable)`. `match` `ImportResolver.match` → `ifne` → `invokestatic getType` → `ifnull` (false) else true. `TYPE` `clinit` `ldc "support.inf"` / `ldc "support"` and `ldc "atak_splash.png"` / `ldc "support"`. `getDestinationPath` `invokestatic getType` / `getfield TYPE._folder` / `FileSystemUtils.getItem` / `new File(folder, getName())`. Parent `getContentMIME` `aconst_null; areturn`. Parent `onFileSorted` `getContentMIME` / `ifnull 206` (skips the `IMPORT_DATA` build). `FileSystemUtils.SUPPORT_DIRECTORY="support"`. *(confidence: high; destination + conflict CONFIRMED.)*

**Clone (5.5.1.10):** [`ImportSupportInfoSort.java#L50`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSupportInfoSort.java#L50) · [`#L66`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSupportInfoSort.java#L66) · [`#L96`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportSupportInfoSort.java#L96) · [`ImportResolver.java#L359`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L359) · [`#L445`](https://github.com/TAK-Product-Center/atak-civ/blob/9f6893dd657feacc35ec5de03dad721c2e44170e/atak/ATAK/app/src/main/java/com/atakmap/android/importfiles/sort/ImportResolver.java#L445).

---

## TXT / XML config (`.txt`, `.xml`)

**What it is.** A **signature-routed** config dispatcher — NOT a generic text viewer. Two `ImportTXTSort` instances are registered (one per extension). A `.txt`/`.xml` is claimed only if its head matches one of **five ATAK config signatures**, and is then routed to a **signature-specific folder** and a signature-specific after-action.

**Flow.**
- **match.** Two-stage AND. (1) ext gate: `super.match` → `_filter.accept` = `getName().toLowerCase().endsWith(_ext)` (`.txt` or `.xml`). (2) content sniff: `getType(fis) != null` — reads the first **1024 chars** and returns the FIRST `TxtType` whose signature is found via `content.contains(t.signature)`. The five signatures: `<remoteResources`, `<NominatimProperties`, `<devices`, `takWfsConfig` (`XMLWFSSchemaHandler.WFS_CONFIG_ROOT`), `::ATAK FAVORITES` (`FavoriteListAdapter.FAVS`). A `.txt`/`.xml` matching none is rejected (`t==null`).
- **destination — signature-routed, NOT a single fixed folder.** The super ctor's `folderName` arg is itself **empty** (`""`). `getDestinationPath` = `FileSystemUtils.getItem(t.folder != null ? t.folder : "")` then `new File(folder, name)` (force-appending `getExt()` if the name lacks it). Per-signature folders:
  - `<remoteResources` → `ImportManagerView.XML_FOLDER`
  - `<NominatimProperties` → `GeocoderPreferenceFragment.ADDRESS_DIR`
  - `<devices` → `BluetoothDevicesConfig.DIRNAME`
  - `takWfsConfig` → literal `"wfs"` (`ATAK/wfs`)
  - `::ATAK FAVORITES` → `FavoriteListAdapter.DIRNAME`
- **registration — content-routed config dispatch.** The overridden `onFileSorted`: first `super.onFileSorted` broadcasts `IMPORT_DATA{contentType="TXT or XML File", mimeType="application/xml"}` and notifies `ImportListener`s; **then** the matched `TxtType.action.doAction(dst)` runs:
  - geocoder action → `GeocoderPreferenceFragment.load(dst)` (loads Nominatim geocoder props into prefs);
  - WFS action → re-broadcast `IMPORT_DATA` with `WFSImporter.CONTENT`/`MIME_XML` (feature DB);
  - favorites action → broadcast `LayersManagerBroadcastReceiver.ACTION_ADD_FAV` + view notification;
  - WMS action (historically for the remoteResources path) → `LayersMapComponent` WMS import;
  - `<remoteResources` and `<devices` have a **null action** (copy-only — consumed later by their own watchers/components).
- **on re-import — archetype (a) + after-action re-run.** `getDestinationPath` = `new File(signatureFolder, fileName)` keyed purely on filename (with forced ext); `beginImport` `renameTo`/`copyFile` directly onto dst with **no uniqueness/versioning/prompt** → overwrite-in-place. Re-importing the same-named config overwrites the prior file **and re-runs the after-action** (reload geocoder/favorites/WFS).

**Evidence.** `ImportTXTSort.<init>` `ldc #19 ""` (empty folder arg) / `ldc #21 "TXT or XML File"` / `ImportResolver.<init>(String,String,String,Drawable)`; `addSignature` calls registering `<remoteResources`→`XML_FOLDER`, `<NominatimProperties`→`ADDRESS_DIR`, `<devices`→`DIRNAME`, `takWfsConfig`→`"wfs"`, `::ATAK FAVORITES`→`FavoriteListAdapter.DIRNAME`. `match` super.match → `ifne` → `getType(...)` → `ireturn (t!=null)`. `getType` `newarray char[1024]` / `BufferedReader.read` / iterate types / `String.contains(t.signature)`. `getDestinationPath` `getfield TxtType.folder` / `ldc #19 ""` fallback / `FileSystemUtils.getItem` / force-append `getExt` / `new File(folder, name)`. `getContentMIME` `new Pair("TXT or XML File","application/xml")`. `onFileSorted` `TxtType.action.doAction(dest)`. `ImportFilesTask` registers `new ImportTXTSort(context, ".xml"/".txt", …)`. **Anchored-fact correction (bytecode wins):** an earlier hint said the ctor folder was `"TXT"` — the bytecode disproves it: the super `folderName` arg is the **empty string** `""`, and the real destination is per-signature inside `getDestinationPath`. *(confidence: high; destination + conflict CONFIRMED by independent re-disassembly.)*

**Clone (5.5.1.10):** `ImportTXTSort.java` L102 `super(ext,"",CONTENT_TYPE,…)`, L106-113 `addSignature`, L118-132 `match`, L134-166 `getType`, L172-198 `getDestinationPath`; `ImportFilesTask.java` L274-275; `ImportResolver.java` L120-131 (`_filter endsWith _ext`), L246/259/271 (`copyFile`/`renameTo`), L365-375 (`ACTION_IMPORT_DATA`).

---

## Methodology & sources

**Disassembly command pattern.** Every behavioral claim was verified by disassembling the authoritative 5.7.0.5 SDK jar:

```
javap -p -c -classpath <ATAK_SDK_5_7_0_5>/main.jar \
  com.atakmap.android.importfiles.sort.ImportGeoJsonSort   # …per class
javap -p -constants -classpath …/main.jar \
  com.atakmap.coremap.filesystem.FileSystemUtils           # for inlined string constants
```

For each resolver the load-bearing methods are `<init>` (the `super(ext, folder, displayName, drawable)` args fix the extension gate and destination folder), `match(File)` (ext gate + content sniff), `getDestinationPath(File)` (destination), `beginImport(File,Set)` (copy/move + flag rewrites), `onFileSorted(...)` (broadcast / handoff), and `getContentMIME()` (the `(contentType, mimeType)` pair). The base `ImportResolver.getDestinationPath` = `new File(FileSystemUtils.getItem(_folderName), file.getName())` unless overridden.

**Version drift.** The 5.7.0.5 bytecode is **authoritative**; the 5.5.1.10 clone permalinks confirm *shape and contract* but their **line numbers apply to 5.5.1.10 only**. Observed drift was confined to:
- **Constant inlining** — the clone references named constants (`FileSystemUtils.OVERLAYS_DIRECTORY`, `GMLSpatialDb.GML_CONTENT_TYPE`, `PreferenceControl.DIRNAME`, `FileSystemUtils.TMP_DIRECTORY`/`SUPPORT_DIRECTORY`, `REPOZ_INDEX_FILENAME`) that 5.7.0.5 inlines to the literal strings — **same values, no behavioral difference**.
- **DRW `getDestinationPath`** — 5.7.0.5 adds an extension-normalization branch absent from the simplified clone form; it is a **no-op for `.drw`** (name already ends `.drw`), so destination/conflict semantics are unchanged.
- **Cosmetic** — `ImportVideoSort` clone uses `context.getDrawable` vs bytecode `getResources().getDrawable`; `ImportAlternateContactSort` clone uses a 4-arg super + `getIcon()` vs bytecode `(String,String,String,Drawable)` + `ic_csv`; `ImportGMLSort`'s `IOException` log misreads `"Error checking if GPX:"`. None affect behavior.

**Per-format verdicts.** All sixteen families resolved with **`destinationVerdict = confirmed`** and **`conflictVerdict = confirmed`** at **high confidence** (SQLite/GeoPackage medium-high — the GeoPackage destination folder is the one un-pinned detail). Thirteen families were produced by the batch-2 workflow with independent adversarial re-disassembly; **three families — Shapefile, LPT, and SQLite+GeoPackage — had their analyze agents fail to return structured output and were filled in by hand-disassembly afterward** (their sections carry that note). The corrections folded into the sections above (not separate "uncertain" verdicts):

| Format | Correction (vs an earlier hint; verdict still confirmed) |
|---|---|
| **DRW** | clone javadoc says "Points" table; both clone code and bytecode use `select * from Main` — code wins. 5.7.0.5 adds a no-op ext-append branch in `getDestinationPath`. |
| **Tileset** | registration hint "Importer with content type `Tileset`" is wrong — **no such importer registers**; the artifact goes live via `TilesetLayerScanner` → `PersistentRasterDataStore`. Catalog importer class is `com.atakmap.android.layers.ExternalLayerDataImporter`. |
| **APK** | the import mode is strictly **copy** (not in-place move) — `beginImport` rewrites flags to `IMPORT_COPY`; observable destination-file result is still filename / overwrite-in-place. |
| **Cert** | per-server write is `saveCertificateForServerAndPort(type,host,port,bytes)`, NOT `saveCertificateForServer(type,server,bytes)`. The SECURE_DELETE flag is visible only as the literal `1`. |
| **Contact** | `Contact Info` is a `contentType` label, not an IANA MIME (the MIME is `text/csv`); and **no contact is registered** into any contact list/DB — it applies the matched row to the importing device's own prefs. SECURE_DELETE flag visible only as literal `1`. |
| **Preferences (JSON)** | the JSON variant routes through `JSONPreferenceControl.load(file,false)` and does **NOT** use the ALLOW/PROMPT/DENY + `WriteOncePreferences` path (those are XML-specific). |
| **TXT/XML** | the ctor folder is the **empty string** `""`, not `"TXT"`; the real destination is **per-signature** inside `getDestinationPath`. |

**No format carried an `uncertain` verdict.** The only acknowledged non-disassembled details are downstream library-side mechanisms that do not affect destination/conflict: the `VideoFileWatcher`/`ConnectionEntry` refresh (Video), and the internal MissionPackage-extractor → `.pref` dispatch class chain inside the cert worked example (see README Layer-B).
