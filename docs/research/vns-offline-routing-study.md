# ATAK VNS Offline Routing Generator — Architecture Study

Source: <https://github.com/joshuafuller/atak-vns-offline-routing-generator>
Local mirror & operator's manual: `<TAK_WORKSPACE>/atak_vns_offline_routing/`
Pinned upstream commit: `23ae3ca` (post-`v1.3.2` on `main`, 2026-04-23)
Companion note: [`address-atak-plugin-study.md`](./address-atak-plugin-study.md)
Date: 2026-05-24

## 1. Why study this alongside the Address plugin

Both projects ship offline OSM-derived data into ATAK, but they pick opposite
sides of the **data-vs-code** boundary:

- **Address plugin** — code *and* data live together: the plugin owns the
  downloader, the SQLite schema, the FTS5 query layer, and shows search
  results in its own UI. Data quality is invisible to the operator.
- **VNS generator** — *only* the data pipeline is open-source. The actual
  ATAK plugin (`com.partech.vns`, closed-source, distributed by TAK.gov)
  reads a fixed directory layout. Anyone can rebuild the data slice; nobody
  needs to touch the plugin binary.

For `atak_tw_power_plugin` the second model is the more interesting one
because it scales without coupling Taiwan-specific data refresh cycles to
the plugin's release cycle. Power-grid data, Taipower substations, even
custom basemap tiles could all sit in a sibling directory and be refreshed
independently.

## 2. The "ship a folder, read a folder" contract

VNS expects exactly this on the device:

```
/storage/emulated/0/atak/tools/VNS/GH/
└── taiwan/
    ├── taiwan.kml            ← boundary (display)
    ├── taiwan.poly           ← boundary (definition)
    ├── taiwan.timestamp      ← region-specific data date
    ├── timestamp             ← generic data date (VNS compat)
    ├── edges                 ← GraphHopper graph
    ├── geometry              ← GraphHopper geometry
    ├── location_index        ← GraphHopper spatial index
    ├── nodes                 ← GraphHopper node table
    ├── nodes_ch_car          ← CH-preprocessed nodes (car profile)
    ├── properties            ← GraphHopper metadata
    ├── shortcuts_car         ← CH shortcuts (car profile)
    ├── string_index_keys
    └── string_index_vals
```

Three operator-visible rules:

1. **Folder name = region key** (lowercase, dashes for spaces). VNS scans
   subdirectories of `GH/` at startup; new region = drop a new folder.
2. **Both timestamps must exist** — `timestamp` *and* `<region>.timestamp`.
   This is the "is this dataset present and current" probe.
3. **The whole bundle ships as a `.zip`** (~69 MB for Taiwan) that the
   operator manually unpacks into the right place. No in-plugin downloader,
   no in-plugin progress bar, no app permission to worry about.

The "drop a folder under `atak/tools/<plugin-name>/`" convention is a
deliberate ATAK ecosystem pattern — it survives plugin reinstalls and lets
multiple plugins read the same data slice.

## 3. The build pipeline (Geofabrik → GraphHopper → ZIP)

`generate-data.sh` is the whole pipeline. Logical steps:

1. **Region resolution** — `wget` the live
   `https://download.geofabrik.de/index-v1-nogeom.json`, `jq` out the PBF /
   POLY / KML URLs for the requested region ID. Retries 10× before giving
   up. No hard-coded region table — works for any Geofabrik region.
2. **Smart caching** — for each of the three files, compare the remote
   `Last-Modified` header against a stored timestamp. If unchanged, skip
   the download and skip GraphHopper re-processing entirely. Auto-backup
   the previous output when the source has moved.
3. **Memory autosizing** — read host RAM from `/proc/meminfo` (or `sysctl`
   on macOS, `wmic` on native Windows), apply linear model
   `dockerMemoryMB = (osmFileSizeMB × 4.01 + 320) × 1.2`, cap at 80 % of
   detected RAM, allow `VNS_MEMORY_GB=N` override.
4. **GraphHopper import** — invoke the pre-built JAR with the computed
   `-Xmx`/`-Xms`. Failure prints a long memory-troubleshooting recipe.
5. **VNS layout assembly** — copy `.poly` + `.kml` into the graph folder,
   extract `datareader.data_date` from `properties`, write both timestamp
   files.
6. **ZIP + cleanup** — `zip -r region.zip region/`, delete intermediate
   files, keep `cache/` for next time.

The whole thing is ~720 lines of Bash with no external coordination
service. The Python-based Address pipeline is comparable in scope but
prefers a fixed dictionary of regions and re-downloads on every run.

## 4. Docker as the supply-chain firewall

The local operator's manual (`atak-vns-offline-routing-manual.md`) treats
the build environment as the threat model, not the OSM data. Key choices:

- `openjdk:11.0.16-jre-slim` pinned by **specific version**, not floating
  `:latest` or `:11-jre-slim`. (Upstream `v1.3.2` tag pins the now-removed
  `openjdk:8-jre-slim`, hence the manual pins commit `23ae3ca` instead.)
- Pre-built GraphHopper 1.0 JARs come from Maven Central rather than
  built-from-source — but the manual notes the JARs are downloaded
  **without SHA-256 verification** and lists it as the second-highest
  residual risk.
- Manual `docker build --pull --no-cache -t local/vns-routing:<short-hash>`
  — never uses the ghcr.io pre-built image; never tags `:latest`.
- Manual `docker run` adds: `--rm`, `--tmpfs /tmp:rw,noexec,nosuid,size=2g`,
  `--cap-drop=ALL`, `--security-opt=no-new-privileges`, `--memory=8g`,
  `--cpus=4`, `--network=bridge`. Plus an explicit "**do not** mount
  `/var/run/docker.sock` or `~/`" callout.
- Post-build the operator computes SHA-256 of both the input PBF and the
  output ZIP and stores them in a per-build `*.manifest.txt`:

  ```
  Region:           taiwan (Geofabrik asia/taiwan)
  OSM data date:    Mon, 22 Apr 2026 23:48:01 GMT
  PBF SHA-256:      <hex>
  ZIP SHA-256:      <hex>
  Git commit:       23ae3ca…
  Docker image:     local/vns-routing:23ae3ca
  Base image:       openjdk:11.0.16-jre-slim
  Subnetworks:      7 (main: 983,767 nodes; 6 islands)
  ```

The subnetwork count is the integrity smoke-test — a swing from 7 to 30+
between rebuilds signals OSM noise worth investigating.

## 5. Patterns directly applicable to `atak_tw_power_plugin`

These are stealable patterns, not commitments — pick what fits.

### 5.1 If we ever ship Taiwan-specific power data

The `tools/VNS/GH/<region>/` convention generalises. A Taipower data
slice would naturally sit at `tools/twcoord/data/<region>/`:

```
/sdcard/atak/tools/twcoord/data/taiwan/
├── timestamp                ← ISO8601 data date
├── source.manifest.txt      ← SHA-256 + commit + base image
├── substations.sqlite       ← schema we define
├── transmission-lines.kml   ← if useful for visual layer
└── taipower-grid.geojson    ← if useful for vector overlay
```

The runtime contract for the plugin becomes "if folder exists and
`timestamp` is parseable, load; otherwise show the maths-only readout we
ship today." Zero coupling to the in-plugin code release cycle.

### 5.2 Generator project, not in-app downloader

The VNS pattern says: **never download in the plugin**. Build the data
offline, audit it, ship it. Concretely for us this would mean a sibling
repo `atak_tw_power_data_generator` containing:

- `Dockerfile` (pinned base image)
- `build-taiwan-data.sh` (one-shot script — OSM extract for Taiwan
  bounding box, or a Taipower-published dataset if licensing allows)
- `output/taiwan.zip` (the artefact)
- `output/taiwan.manifest.txt` (hashes, commit, build host)

Distribution = put the `.zip` in a GitHub Release of the *data* repo. The
plugin never touches the network for data.

### 5.3 The discipline list to copy verbatim

Even if we never ship external data, the security/operations discipline in
`atak-vns-offline-routing-manual.md` is worth reusing as a template for
any future generator the plugin needs:

- Pin upstream by commit hash, not by tag
- 30-day cooldown before adopting new upstream releases
- Audit Dockerfile + entry scripts for `curl|sh`, `eval`, `base64 -d|sh`
  patterns before each build
- Enumerate external domains via `grep -hoE 'https?://[^"]+'` and reject
  any not on the allowlist
- Store SHA-256 of both source and output in a manifest committed with
  the artefact
- Treat the build container as ephemeral (`--rm`, `--cap-drop=ALL`)
- Never use `:latest` or floating tags

### 5.4 Live region catalogue, not hardcoded list

VNS uses Geofabrik's `index-v1-nogeom.json` as a runtime lookup, so adding
a new region requires no code change. The Address plugin hard-codes its
region dictionary in `build_state_db.py`. If we ever build a region
selector for the TW plugin, prefer VNS's "ask the source" model — single
source of truth, automatic when Geofabrik adds a region.

### 5.5 `Last-Modified`-based cache invalidation

`is_file_current()` in `generate-data.sh` uses `wget --spider
--server-response` to read the upstream `Last-Modified` header and
compares against a stored value. Simple, no auth, no API call quota. A
useful trick whenever we need to decide "is my cached file still fresh"
against a static HTTP host.

## 6. What NOT to copy

- **No SHA-256 on the GraphHopper JAR download** in the Dockerfile —
  upstream's own gap, flagged in the manual as "mid-severity". Any fork
  we'd own should add this; we'd want to set the bar higher than the
  upstream does.
- **Memory autosizing model fitted to GraphHopper CH** — the
  `4.01 × MB + 320` formula is workload-specific; don't reuse the coefficient
  for any other pipeline.
- **`openjdk:11.0.16-jre-slim`** — already an EOL tag with no security
  updates. The manual flags this as the top residual risk. A long-term
  fork should switch to `eclipse-temurin:11-jre`.
- **Hard-coded `/storage/emulated/0/atak/tools/...`** in scripts — fragile
  under Android 11+ scoped storage. Inside our plugin code we use ATAK's
  directory helpers; the *manual instructions* to operators can name the
  path but our runtime code should not.
- **Inline `Dockerfile` `RUN echo '<yaml>'` for config** — readable in a
  diff, but messy. If we ever ship a generator, keep config in a separate
  file `COPY`'d in.

## 7. Cross-reference summary

| Concern                                | Address plugin                            | VNS generator                                     | Recommendation for TW plugin                                 |
| -------------------------------------- | ----------------------------------------- | ------------------------------------------------- | ------------------------------------------------------------ |
| Code/data boundary                     | In-app downloader + in-app DB             | External pipeline → fixed folder, plugin reads it | **VNS model** — data lives in `tools/twcoord/data/...`       |
| Build environment                      | `pip install osmium` on host              | Docker (pinned base image)                        | **VNS model** — reproducible, audit-friendly                 |
| Region catalogue                       | Hardcoded Python dict                     | Live Geofabrik API JSON                           | VNS model if we ever build a multi-region selector           |
| Integrity verification                 | None (HTTPS only)                         | Operator-computed SHA-256 + manifest              | VNS model — adapt to our distribution                        |
| Cache invalidation                     | File existence                            | `Last-Modified` header compare                    | VNS model when we cache HTTP downloads                       |
| Runtime fallback to network            | Yes (Photon/Nominatim/Overpass)           | No (fail closed)                                  | **VNS model** — "no data = no feature" is honest             |
| End-user install gesture               | In-app tap "Download"                     | USB/ADB copy of `.zip` + unzip                    | Depends on user — VNS is harder but auditable                |

## 8. Concrete next steps if we want to act on this

Optional, by ascending size:

- **Adopt the manifest discipline now** for any artefact we already produce
  (releases, ICON pipeline output) — add SHA-256 + commit + base-image
  fields to whatever ships with `v1.0.5+`.
- **Define a "data folder" contract** for the plugin even before any data
  exists: e.g. document that `tools/twcoord/data/<region>/timestamp` is
  the future probe path; ship a stub reader that no-ops cleanly when
  empty. Locks in the convention without committing to content.
- **Stand up `atak_tw_power_data_generator` sibling repo** if a concrete
  data slice surfaces (Taipower substation list, transmission corridors,
  etc.). Use the VNS Dockerfile + manual structure as the template.

## 9. Source pointers

| Concern                                | File                                                                          | Notes |
| -------------------------------------- | ----------------------------------------------------------------------------- | ----- |
| Pipeline orchestrator                  | `atak-vns-offline-routing-generator/generate-data.sh`                         | ~720 lines, the only build script |
| Container definition                   | `atak-vns-offline-routing-generator/Dockerfile`                               | ~78 lines, pins base + JAR URLs |
| Region discovery                       | `atak-vns-offline-routing-generator/list-regions.sh`                          | Broken in pinned commit (calls `curl`, missing in image); manual says use the table in Appendix B instead |
| Operator manual (zh-TW, ours)          | `atak-vns-offline-routing-manual.md` + `VNS-離線路徑-使用說明.md`              | Single source of truth for our supply-chain discipline |
| Architecture reference                 | `atak-vns-offline-routing-generator/docs/architecture.md`                     | Upstream's own write-up; useful background |
| Tech stack reference                   | `atak-vns-offline-routing-generator/docs/tech-stack.md`                       | Lists every external dep |
