# Address ATAK Plugin — Architecture Study

Source: <https://github.com/GoTAK-LLC/Address-ATAK-Plugin>
Plugin version studied: `1.3.0` (ATAK 5.2.0)
Author: GoTAK LLC (closed-source binary distribution; full source on GitHub)
Companion note: [`vns-offline-routing-study.md`](./vns-offline-routing-study.md)
Date: 2026-05-24

## 1. Why this plugin is worth studying

For `atak_tw_power_plugin` the goal is **pure offline coordinate / location
information on the map**. Address Plugin solves a closely related problem:

- Forward geocoding (text → coordinate) — **fully offline-capable** via a
  downloaded SQLite database per region.
- Reverse geocoding (coordinate → address) — **online-only** (ATAK built-in
  geocoder + optional Photon/Nominatim fallback). This is the single most
  important caveat: the marketing word "offline" applies to **search**, not
  to the on-map address overlays.
- Three overlay widgets (self-location, map-centre, marker tap) — directly
  parallel features we already ship (`TwCoordWidget`) or could ship.

So the study splits into (a) **patterns we can reuse** for offline coordinate
display, and (b) **the offline data pipeline** if we ever want offline place
*names* (cities, roads, POIs) for Taiwan.

## 2. Online vs offline resources (inventory)

| Capability                       | Path in plugin                                                | Online dependency                                   | Notes |
| -------------------------------- | ------------------------------------------------------------- | --------------------------------------------------- | ----- |
| Forward search (places, POIs)    | `OfflineAddressDatabase.java` + per-region `.db` files        | None once `.db` downloaded                          | FTS5 full-text + R*Tree spatial index |
| Forward search fallback          | `NominatimApiClient.java`                                     | `nominatim.openstreetmap.org`                       | Used when offline DB empty / not downloaded |
| Nearby POI radius search         | `OfflineAddressDatabase.searchPOIs()`                         | None                                                | R*Tree bounding box + Haversine refine |
| Nearby POI fallback              | `OverpassApiClient.java`                                      | `overpass-api.de`                                   | When ≥10 offline POIs not found |
| Reverse geocoding (self/centre/marker) | `ReverseGeocoder.java`                                  | `photon.komoot.io` + `nominatim.openstreetmap.org`  | **Always online**; ATAK built-in geocoder used first |
| Database download/install        | `OfflineDataManager.java`                                     | GitHub Releases asset `manifest.json` + `*.db`      | One-time per region |
| OSM data build pipeline          | `tools/build_state_db.py`                                     | Geofabrik PBF / Overpass / custom file              | Run by maintainer, output is shipped |
| Cross-app deep link              | `addressview://navigate?lat=…&lon=…&zoom=…&tilt=…&rotation=…` | None                                                | URI scheme handler `ViewNavigationActivity` |

Take-away: the plugin is **strong on offline forward search**, but advertises
"shows my current address" without disclosing that this still needs the
internet. If we ever add a "show current 鄉鎮市區 / 道路名" overlay, we either
need an offline database or accept a network call.

## 3. Offline data pipeline (`tools/build_state_db.py`)

A single 35 KB Python script does the whole pipeline. Steps:

1. **Download** OSM PBF
   - US: `https://download.geofabrik.de/north-america/us/<state>-latest.osm.pbf`
   - World: Geofabrik region key (e.g. `asia/taiwan`)
   - City: Overpass API XML query bounded by `--bbox west,south,east,north`
   - Custom: `--file my.osm.pbf --name "My Region"`
2. **Parse** with `osmium.SimpleHandler` — emits two streams:
   - *Places* — nodes/ways with `name=*` or `addr:street=*`, classified by
     `_get_place_type()` (place / amenity / shop / tourism / aeroway / …).
   - *POIs* — nodes/ways whose tags match a hard-coded `POI_CATEGORIES`
     dictionary (~50 categories: HOSPITAL, GAS_STATION, AIRPORT, …).
   - Ways get a naive centroid (mean of node lat/lon). Good enough for an
     icon drop; not for polygon-aware display.
3. **Build SQLite** schema v2:
   ```sql
   CREATE TABLE places (id, osm_id, osm_type, lat, lon, name,
                        display_name, type, street, housenumber,
                        city, postcode, state, country);
   CREATE VIRTUAL TABLE places_fts USING fts5(
        name, display_name, street, city, postcode,
        content='places', content_rowid='id');
   CREATE TABLE pois (id, osm_id, osm_type, lat, lon, name, category,
                      address, phone, website, opening_hours);
   CREATE VIRTUAL TABLE pois_rtree USING rtree(
        id, min_lat, max_lat, min_lon, max_lon);
   CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT);
   ```
   Followed by `VACUUM` + `ANALYZE` and a `manifest.json` per output dir.
4. **Distribution** — outputs go to GitHub Releases under a fixed tag
   (`releases/tag/databases`), so the runtime always pulls
   `https://github.com/…/releases/download/databases/<region>.db`. There is
   **no signature / hash check** in the downloader code — trust is by HTTPS
   only.

File sizes per README: Virginia ~30–50 MB, California ~100–150 MB, Germany
~400–600 MB, Japan ~200–350 MB. A Taiwan database via Geofabrik would
likely sit in the 30–80 MB range.

## 4. Runtime architecture

### 4.1 Database access layer (`OfflineAddressDatabase.java`, 47 KB)

Worth reading end-to-end. Highlights:

- **Storage path is hard-coded** to `/sdcard/atak/tools/address/<state>.db`.
  Easy to side-load by file copy. (We use the same convention — `tools/`
  under ATAK's root — but per-feature directories.)
- **LRU connection cache** (`LinkedHashMap` access-order, capacity 5) keeps
  recently opened read-only databases open; `removeEldestEntry` closes the
  evicted one. Avoids the SQLite open/close penalty on multi-state queries.
- **Parallel multi-state search** via `Executors.newFixedThreadPool(4)` with
  per-state futures; early termination when ≥`DEFAULT_LIMIT` results found
  or `hasGoodMatch()` (all query words contained in one result) is true. A
  3 s `Future.get` timeout caps the worst case.
- **FTS5 query sanitiser** (`sanitizeFtsQuery`) — the single cleverest
  micro-optimisation:
  - Strip FTS5 punctuation (`" ' * ( ) - :`).
  - Numeric terms (`780`) → exact match, **never** prefix. Stops
    `780*` from matching 780, 7800, 78001, … which destroys performance.
  - Last word only → `word*` prefix (user might still be typing).
  - Everything else → exact match.
- **Graceful fallback** from FTS5 → `LIKE %query%`. The LIKE path is also
  used if the DB is missing the `places_fts` virtual table (older schema).
- **R*Tree bounding-box query then refine** — for radius search, convert
  km → lat/lon delta with the `cos(lat)` correction, query the rtree for
  bounding box overlap, then Haversine-filter to drop the corners outside
  the true circle.

### 4.2 Overlay widgets

Three peers, all extending `AbstractWidgetMapComponent`:

| Widget                     | Anchor                          | Trigger                                                                                                          | Refresh                                  |
| -------------------------- | ------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| `SelfLocationWidget`       | `RootLayoutWidget.BOTTOM_RIGHT` | always; inserted just above ATAK's `SelfLocTray` (callsign), pushing it down                                     | `ScheduledExecutorService` every `address_refresh_period` s; geocode skipped if moved <15.24 m |
| `MapCenterWidget`          | `RootLayoutWidget.BOTTOM_LEFT`  | only when both `address_show_map_center` and ATAK's built-in `map_center_designator` are on (listens on prefs)   | Same scheduler; 5 s debounce after pan/zoom; skipped if map scale > 500 m/px |
| `MarkerSelectionWidget`    | top-right corner                | tap any marker; auto-hide after 10 s                                                                             | reactive, not polled                     |

Patterns we can pick up:

- **Mode auto-detection**: `MapCenterWidget` only displays when ATAK is
  already showing the cross-hair. It listens on
  `SharedPreferences.OnSharedPreferenceChangeListener` for both its own key
  and ATAK's `map_center_designator`. Cleanly avoids fighting with ATAK's
  own UI.
- **Tap/double-tap on the widget itself**:
  - single tap → force refresh
  - double tap (300 ms) → open settings via
    `SettingsActivity.start("addressPreferences", "addressPreferences")`.
- **Cross-restart cache** in `SelfLocationWidget`: last address + last
  lat/lon stored in `SharedPreferences` so first-frame after restart shows
  *something* while geocoding runs.
- **Insert above callsign**: walks `RootLayoutWidget.BOTTOM_RIGHT` children,
  finds the one whose name contains `SelfLocTray`, and inserts at that
  index so the callsign is pushed down. Survives ATAK reordering.

### 4.3 Cross-app URI scheme

`AndroidManifest.xml` registers an exported `ViewNavigationActivity` on
`addressview://` so any app, browser tab, or QR code can pan ATAK with
`addressview://navigate?lat=40.7128&lon=-74.006&zoom=15&tilt=45&rotation=90`.
Same pattern could give us `twcoord://goto?taipower=…` or
`twcoord://goto?twd97=…` and would compose well with the existing GoTo
input page.

## 5. What is and isn't usable for `atak_tw_power_plugin`

### 5.1 Directly reusable patterns (pure offline)

1. **Three-widget set** (self, map-centre, marker-tap). We already have the
   self-location readout via `TwCoordWidget`; the map-centre mode is
   exactly the layout `MapCenterWidget` uses, and would also address the
   open bug filed in `swim-fish/atak_tw_coord_plugin#1` (map-centre readout
   staleness).
2. **Marker-tap readout** — show Taipower grid / TWD97 / TWD67 for any
   selected marker. Symmetric to `MarkerSelectionWidget` and entirely
   offline since coordinate conversion is local math.
3. **`SharedPreferences` mode-gating** — depend on ATAK's
   `map_center_designator` so the readout only appears when the user is
   already in cross-hair mode. Removes a setting the user otherwise has to
   toggle manually.
4. **Single-tap / double-tap on the widget** — single tap to copy/refresh,
   double tap to open settings. A natural complement to the existing
   readout widget.
5. **Cross-app URI scheme** — `twcoord://goto?…` would let external apps,
   shortcuts, and QR codes drive the GoTo flow.

### 5.2 Optional future direction — offline place name lookup

If we ever want "your current 鄉鎮市區 / 路名" without an internet call:

- Reuse `tools/build_state_db.py` with `--region asia/taiwan` and accept
  the place / POI subset. The resulting `taiwan.db` would be in the tens
  of MB, shipped as a GitHub Release asset.
- Mirror the LRU + FTS5 + R*Tree DB layer (the file is self-contained and
  apparently MIT-spirit; verify the licence — repo says "All rights
  reserved" so we'd need to write our own implementation, not copy code).
- Important schema gap: schema v2 has no reverse-geocoding index. To map
  *coordinate → nearest road/town* offline we either (a) brute-force
  nearest-neighbour over `places` using the R*Tree, or (b) build a
  separate kd-tree of roads. The Address plugin chose not to ship this
  and falls back to the network.

### 5.3 What NOT to copy

- `ReverseGeocoder.java` is online-only — useless for the stated offline
  goal.
- `OfflineDataManager` downloads `.db` files **without integrity checks**
  (no SHA, no signature). For a plugin we'd ship through TPC we'd want at
  minimum SHA-256 verification, ideally Ed25519 signing (the plugin
  already pulls in `bcprov-jdk15to18` for JWT verification, so the crypto
  is in-tree).
- Hard-coded `/sdcard/atak/tools/address` — works pre-Android 11 scoped
  storage, fragile on newer devices. We already use ATAK-provided
  directory helpers; keep doing that.
- Geofabrik POI extraction is whole-world / whole-country; for Taiwan it
  would over-include POIs we don't care about. Pre-filter the OSM tag set
  to whatever a Taiwan power-grid workflow actually needs.

## 6. Concrete next steps if we want to act on this

These are options, not commitments — pick whichever fits the next sprint:

- **Bug fix only**: address `swim-fish/atak_tw_coord_plugin#1` by adopting
  the Address plugin's pattern of subscribing to map-centre changes (5 s
  debounce + 500 m/px scale gate + force-refresh on the
  `map_center_designator` pref edge).
- **New feature — marker-tap coord readout**: a small overlay echoing the
  selected marker's Taipower / TWD97 / TWD67, parallel to
  `MarkerSelectionWidget`. Entirely offline; only needs the existing
  `CoordinateConverter`.
- **New feature — `twcoord://` deep link**: lightweight, no DB, drops into
  the existing GoTo `TwCoordGotoReceiver`.
- **Speculative — offline Taiwan place DB**: only worth doing if a real
  user need surfaces. Would add 30–80 MB of data and a maintenance burden
  (refresh per Geofabrik update).

## 7. Source pointers

| Concern                          | Address Plugin file                                                    | LoC  |
| -------------------------------- | ---------------------------------------------------------------------- | ---- |
| Offline DB access + LRU + FTS5   | `app/src/main/java/com/gotak/address/search/OfflineAddressDatabase.java` | ~1200 |
| Online reverse geocode (caveat)  | `app/src/main/java/com/gotak/address/selfgeo/ReverseGeocoder.java`     | ~330 |
| Self-location overlay            | `app/src/main/java/com/gotak/address/selfgeo/SelfLocationWidget.java`  | ~750 |
| Map-centre overlay (mode-gated)  | `app/src/main/java/com/gotak/address/selfgeo/MapCenterWidget.java`     | ~400 |
| Marker-tap overlay               | `app/src/main/java/com/gotak/address/selfgeo/MarkerSelectionWidget.java` | ~700 |
| DB downloader (GitHub Releases)  | `app/src/main/java/com/gotak/address/search/OfflineDataManager.java`   | ~500 |
| OSM → SQLite pipeline            | `tools/build_state_db.py`                                              | ~900 |
| OSM tag catalogue                | `app/src/main/assets/osm_tags.json`                                    | 328 KB |
| URI scheme handler               | `app/src/main/java/com/gotak/address/ViewNavigationActivity.java`      | small |
