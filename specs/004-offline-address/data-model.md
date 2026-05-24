# Phase 1 Data Model: Offline Address Lookup

**Feature**: `004-offline-address` | **Date**: 2026-05-24

This document records the on-disk and in-memory data structures the plugin consumes (from the
generator) and produces (its own persistent state and runtime values). The on-disk schema is
**defined by the generator** (`atak-tw-address-generator`); this document mirrors that schema
so the plugin tests can assert against a fixed contract without round-tripping to the
generator's own source.

## 1. SQLite schema (from generator)

The `places-<county>.sqlite` file imported by the plugin is the unchanged output of
`atak-tw-address-generator/scripts/ingest_tgos_csv.py` `SCHEMA_SQL`. The plugin assumes the
following on every imported file (FR-004 validation):

### 1.1 `places` table

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PRIMARY KEY | Generator-assigned rowid; opaque to the plugin. |
| `source` | TEXT NOT NULL | `'tgos'` (current generator) or `'osm'` (future). Plugin treats as opaque. |
| `osm_id` | INTEGER | NULL for TGOS rows. |
| `lat` | REAL NOT NULL | WGS-84 latitude (degrees). Generator's `coord_transform.py` reprojects from TWD97 / TM2 zone 121 when needed. |
| `lon` | REAL NOT NULL | WGS-84 longitude. |
| `name` | TEXT | Compact halfwidth form for marker labels (e.g. `大誠街39巷2-3-2號`). **Plugin uses `display_name` for the address row, not this column.** |
| `display_name` | TEXT NOT NULL | Full Taiwan-style fullwidth address (e.g. `台中市中區大誠里大誠街３９巷２之３之２號`). **This is what the address row renders.** |
| `display_name_halfwidth` | TEXT NOT NULL | Halfwidth normalised form for future FTS5 forward-search use; not surfaced in this feature. |
| `district_code` | TEXT NOT NULL | MOI (Ministry of the Interior) district code, e.g. `6600100`. |
| `county` | TEXT NOT NULL | e.g. `台中市`. |
| `township` | TEXT NOT NULL | e.g. `中區`. |
| `village` | TEXT | e.g. `大誠里`; may be NULL. |
| `neighbor` | TEXT | 鄰 (numeric in TGOS); may be NULL. Added in generator commit dated 2026-05-24. |
| `street` | TEXT | e.g. `大誠街`; may be NULL (orphan addresses fall back to `area`). |
| `area` | TEXT | 地區; may be NULL. Used by coastal townships (e.g. `大城鄉`) when `street` is empty. Added in generator commit dated 2026-05-24. |
| `lane` | TEXT | e.g. `39巷`; may be NULL. |
| `alley` | TEXT | e.g. `5弄`; may be NULL. |
| `number` | TEXT | e.g. `2-3-2號`; may be NULL only if generator's `skipped_no_number` counter is greater than zero (such rows are skipped by the generator, so the plugin should never see them in practice). |

### 1.2 `places_fts` virtual table (FTS5)

Generator builds FTS5 over (`name`, `display_name`, `display_name_halfwidth`, `street`,
`township`) for future forward-search use. **The plugin does not query `places_fts` in this
feature** — reverse-lookup uses spatial indexing only. The table is left untouched.

### 1.3 `metadata` table

| Key | Value example | Required by plugin? |
|---|---|---|
| `schema_version` | `1` | **Yes** — must equal the plugin-pinned version (1 for v1). |
| `source` | `tgos` | Yes — surfaced on Offline Address page. |
| `county` | `台中市` | Yes — surfaced as dataset label. |
| `data_date` | `115-01` (民國年-月) | Yes — surfaced verbatim. |
| `csv_sha256` | hex 64 | Optional (display only). |
| `csv_path` | path string | Optional (display only). |
| `crs` | `EPSG:4326` / `EPSG:3826` | Optional (display only). |
| `inserted` | integer string | Optional (display only). |
| `skipped_no_number` | integer string | Optional (display only). |
| `skipped_unknown_code` | integer string | Optional (display only). |

Unknown keys are read and surfaced as a "raw metadata" expander on the Offline Address page,
but do not affect import validation (forward-compat).

### 1.4 Indexes (generator-built)

- `idx_places_district` on `places(district_code)` — not used by the plugin.
- `idx_places_lookup` on `places(district_code, village, neighbor, street, area, lane, alley, number)` — not
  used by the plugin (this is a forward-search lookup index for exact addresses).

> **Plugin validation note** (per FR-004 / `contracts/address-bundle-importer.md` test #6):
> the importer checks **presence** of the columns it actually reads (`id`, `lat`, `lon`,
> `display_name`, `display_name_halfwidth`) plus the mandatory `metadata` keys. Additional
> columns the generator emits (`neighbor`, `area`, `osm_id`, etc.) are tolerated — the
> schema is forward-compatible as long as the plugin's read columns remain present. This
> means a generator-side addition of further columns (without bumping `schema_version`) does
> not break the plugin's import path.

### 1.5 R*Tree

As of the generator's `data-contract.md` **v2 (2026-05-24 evening)** the `places_rtree`
virtual table ships pre-built inside every `places-*.sqlite` (`schema_version='2'`). For
operators still holding **v1** files (no R*Tree), the plugin builds it at import time, into
the same `.sqlite` file:

```sql
CREATE VIRTUAL TABLE IF NOT EXISTS places_rtree USING rtree(
    id,                -- rowid alias; matches places.id
    min_lat, max_lat,
    min_lon, max_lon
);
INSERT INTO places_rtree(id, min_lat, max_lat, min_lon, max_lon)
SELECT id, lat, lat, lon, lon FROM places
WHERE NOT EXISTS (SELECT 1 FROM places_rtree WHERE id = places.id);
ANALYZE places_rtree;
```

The two range columns per axis collapse to `lat = lat` / `lon = lon` because every row is a
point — R*Tree degenerates to a point index. SQLite handles this efficiently.

The `CREATE … IF NOT EXISTS` + `WHERE NOT EXISTS` pair makes the build idempotent: v2 imports
no-op (`imported.manifest.rtreeBuilt = false`); v1 imports take ~30–45 s on Taichung
(`rtreeBuilt = true`). See `research.md` R3 for the rationale; the version-detection lives
implicitly in those SQL guards rather than as an explicit branch on `metadata.schema_version`.

## 2. Plugin-side `imported.manifest.txt`

A plain-text key=value file the plugin writes into the active dataset directory immediately
before atomic activation. Schema (per [research.md §R5](./research.md#r5--bundle-layout-bare-sqlite-with-in-db-metadata)):

```text
imported_at=2026-05-24T15:30:00Z
file_sha256=<hex 64>
rtree_built=true
plugin_schema_version=1
```

| Key | Type | Source |
|---|---|---|
| `imported_at` | ISO-8601 UTC | `Instant.now()` at the moment the staging dir is renamed to `active/`. |
| `file_sha256` | lowercase hex 64 | Computed during the SAF stream copy via `MessageDigest("SHA-256")`. |
| `rtree_built` | `true` / `false` | `true` if the plugin built `places_rtree` at this import; `false` if the file already had one (future generator versions). |
| `plugin_schema_version` | small int | The plugin's pinned version (`1` for v1). Used at startup to decide whether the existing active dataset is still compatible. |

## 3. Persistence (Android SharedPreferences)

File: `tw_coord_settings` (the plugin's existing default SharedPreferences file). Three new
boolean keys, all defaulting to **false**:

| Key | Default | Cleared on plugin uninstall? |
|---|---|---|
| `pref_address_row_me` | false | Yes (Android SharedPreferences lifecycle). |
| `pref_address_row_target` | false | Yes. |
| `pref_address_row_map` | false | Yes. |

No new `String` / `Int` / `Long` keys; the import / dataset state is on-disk-only (the
existence of `active/imported.manifest.txt` is the source of truth for "is a dataset active").

## 4. In-memory model (Java types)

### 4.1 Value classes

```java
package com.atakmap.android.twcoord.address;

/** Immutable view of the generator's in-DB `metadata` table. */
public final class GeneratorMetadata {
    public final int schemaVersion;
    public final String source;          // "tgos" / "osm" / ...
    public final String county;          // "台中市"
    public final String dataDate;        // "115-01" — opaque to plugin
    public final String csvSha256;       // nullable
    public final String csvPath;         // nullable
    public final String crs;             // nullable, "EPSG:4326" / ...
    public final long insertedRows;      // -1 if unparseable / absent
    public final java.util.Map<String, String> raw;   // unmodifiable, for unknown keys
    /* ctor + equals + hashCode */
}

/** Immutable view of the plugin-side `imported.manifest.txt`. */
public final class ImportedManifest {
    public final java.time.Instant importedAt;
    public final String fileSha256;       // 64-char lowercase hex
    public final boolean rtreeBuilt;
    public final int pluginSchemaVersion;
    /* ctor + equals + hashCode */
}

/** Combined view used by the Offline Address page and the address subsystem. */
public final class AddressDataset {
    public final java.io.File rootDir;            // active/
    public final java.io.File dbFile;             // active/places.sqlite
    public final GeneratorMetadata generator;
    public final ImportedManifest imported;
    /* ctor + equals + hashCode */
}

/** One reverse-lookup hit (or part of it). */
public final class AddressRecord {
    public final double lat;
    public final double lon;
    public final String displayName;              // fullwidth Taiwan address, e.g. "台中市..."
    public final String displayNameHalfwidth;     // halfwidth normalised, for future forward-search
    /* ctor + equals + hashCode */
}
```

### 4.2 Sealed-ish result types

```java
/** Outcome of a reverse-lookup; either Found(record), Empty, or NoDataset. */
public sealed interface AddressLookupResult permits Found, Empty, NoDataset {
    record Found(AddressRecord record) implements AddressLookupResult {}
    enum Empty implements AddressLookupResult { INSTANCE }
    enum NoDataset implements AddressLookupResult { INSTANCE }
}

/** What the widget paints for a given row's address line. */
public sealed interface AddressRowState permits Hidden, Loading, Text, EmptyState {
    enum Hidden implements AddressRowState { INSTANCE }
    enum Loading implements AddressRowState { INSTANCE }
    record Text(String value) implements AddressRowState {}
    enum EmptyState implements AddressRowState { INSTANCE }
}
```

(Java 17 supports sealed interfaces and records natively; this is the same idiom the project
uses elsewhere — e.g. feature 003's `PickerPreviewState`.)

## 5. State transitions

### 5.1 Active-dataset lifecycle

```text
                  ┌──────────────────┐
                  │  No active set   │  ←—— deletion, missing files, schema mismatch
                  └─────────┬────────┘
                            │ successful import (R8 atomic move)
                            ▼
                  ┌──────────────────┐
                  │  Active set      │
                  └─────────┬────────┘
                            │ Remove via Offline Address page
                            ▼
                  ┌──────────────────┐
                  │  No active set   │
                  └──────────────────┘
```

The transition is driven exclusively by the importer's `Files.move(..., ATOMIC_MOVE,
REPLACE_EXISTING)` call (forward) or the Remove button's `Files.walk(active).map(toRm)` call
(backward). No partial states are visible to the rest of the plugin.

### 5.2 Per-row address state (per of {ME, TGT, MAP})

```text
        toggle=off OR no dataset
       ┌────────────────────────────────────┐
       │             Hidden                  │
       └─────────┬──────────────────────────┘
        toggle=on AND dataset │
                              ▼
       ┌───────────────────────────────────┐
       │            Loading                 │  ←—— before first lookup completes for this coord
       └───────┬───────────────────────────┘
               │ lookup yields Found(r)
               ▼
       ┌──────────────────┐
       │  Text(r.display) │   ◀── lookup yields Empty / coord moves to no-record point
       │                  │       ─────────────────────────▶  EmptyState
       └──────────────────┘
```

The transition diagram is symmetric: once `Loading` resolves, subsequent coord changes flip
between `Text` and `EmptyState` directly (no return to `Loading`), avoiding flicker. `Loading`
re-appears only when the dataset is first activated (cold start, first lookup) or when the row
goes from `Hidden` back to active.

## 6. Concurrency model

The `AddressSubsystem` is the single owner of writes to the per-row state map. Its public API
runs on the UI thread; its internal `ScheduledExecutorService` runs lookups; the worker posts
results back to the UI thread via `mapView.post(...)` before any state mutation. The widget
reads its own `TextWidget`s on the UI thread only. This is the same single-writer-multi-reader
discipline `SelfMarkerSubscriber` follows in feature 001.

No `synchronized` blocks; no `volatile` (the executor's task queue provides the happens-before
edge). `MapView.post` is the only thread-handoff API used.
