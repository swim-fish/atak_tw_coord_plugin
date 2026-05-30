# Contract: Storage Display in TW Offline Addr (US3)

Behavioural contract for per-county and `_boundary` storage sizes. Covers
FR-012…FR-015, SC-005.

**Current code anchor**: `OfflineAddressReceiver.renderActiveCountyList(ViewGroup)`
iterates `registry.snapshot().values()`, inflates `R.layout.offline_address_county_row`
per county, and sets `R.id.offline_addr_county_name` + `R.id.offline_addr_county_meta`
(`data_date · N rows`). Sizes come from the existing `FileSystem` API
(`sizeOfDirectory`, `activeCountyDir`, `boundaryDbFile`, `exists`).

## C1 — Per-county size shown
- **Given** ≥ 1 county dataset installed,
- **When** TW Offline Addr opens,
- **Then** each county row shows its on-disk size (`ByteCountFormatter.format(
  fs.sizeOfDirectory(fs.activeCountyDir(county)))`) alongside the existing meta
  line (FR-012, FR-014).
- *Test*: Espresso — each county row displays a size; unit —
  `DatasetStorageSummary.perCounty()` returns the county-dir size.

## C2 — Boundary folder size shown as a distinct row
- **Given** the `_boundary` folder exists (`fs.exists(fs.boundaryDbFile())`),
- **Then** a distinct **`_boundary` (townships.sqlite)** row shows the folder
  total `fs.sizeOfDirectory(boundaryDir)` (DB + sidecars) (FR-013).
- *Test*: unit — `boundary()` returns `(present=true, bytes>0)`; Espresso — the
  row renders.

## C3 — Human-readable units
- **Given** sizes spanning B…GB (e.g. 324 MB place DB, ~10 MB boundary),
- **Then** values render with binary units and one decimal at KB+ (e.g.
  `324.0 MB`, `9.8 MB`) (FR-014).
- *Test*: unit — `ByteCountFormatter` boundary/rounding cases.

## C4 — Missing / empty / partial handled
- **Given** no datasets installed, or a missing/partial county dir, or an absent
  `_boundary`,
- **Then** the screen still loads: absent paths → `sizeOfDirectory` returns 0;
  `present=false` → "未安裝"; an in-progress import does not crash the listing
  (FR-015, spec edge cases).
- *Test*: unit — in-memory fake `FileSystem` with missing/zero paths; Espresso —
  empty state loads.

## C5 — Sizes do not block the screen
- **Given** the listing renders,
- **Then** size computation (O(counties) `sizeOfDirectory` walks + one boundary
  walk) does not block the rest of the page from appearing.
- *Test*: render-path assertion; computed on the existing render path (off the UI
  thread if the page already loads asynchronously).

## C6 — Display-only
- **Given** the storage rows,
- **Then** they add no delete/management action — display only (out-of-scope
  guard).
- *Test*: review — no new mutating control introduced.
