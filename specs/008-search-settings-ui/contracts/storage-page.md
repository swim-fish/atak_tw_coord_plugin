# Contract: Offline Address (Storage) Page Redesign

Behavioural contract for the redesigned `OfflineAddressReceiver` +
`offline_address_page.xml` + `offline_address_county_row.xml`. Observable and
test-backed (Espresso UI + unchanged importer/registry unit suite as regression
guard).

## C-ST-1 Total usage (FR-009)
- **Given** N counties plus a boundary layer installed, **then** the header
  total equals Σ `sizeOfDirectory(activeCountyDir(county))` + boundary folder
  size, formatted by `ByteCountFormatter`. The displayed total equals the sum in
  100% of renders (SC-003).

## C-ST-2 Stacked bar + legend (FR-010)
- **Then** the page shows one weighted bar segment per county
  (`weight = max(bytes,1)`) plus one grey boundary segment, and a legend with a
  colour dot + label + size per segment.
- **Invariant**: for each county, the bar segment, legend entry, and per-row
  swatch use the identical colour (`OA_PALETTE` indexed by `snap.values()`
  order) — 100% colour consistency (SC-004).
- Many-county legends wrap or scroll rather than clip; a zero-byte county/
  boundary folder does not break the bar or total (edge cases).

## C-ST-3 Compact county row (FR-011)
- **Then** each county row shows name, `dataDate · insertedRows` summary,
  on-disk size, and a colour swatch matching its bar segment.

## C-ST-4 Overflow menu preserves actions (FR-012, SC-005)
- **When** a row's overflow (⋮) is tapped, **then** a `PopupMenu` opens with
  "replace" and a destructively-styled "remove" (red).
- **When** "replace"/"remove" is chosen, **then** the existing
  `confirmReplaceCounty` / `confirmRemoveCounty` confirmation dialog and
  downstream action run exactly as before the redesign. Every management action
  available before remains reachable after.

## C-ST-5 Import-in-progress card (FR-013)
- **Given** an import is running, **then** the progress card is visible with the
  existing progress text and a `ProgressBar`.
- The bar is **determinate** (percent) during `Stage.COPYING` and
  `Stage.BUILDING_RTREE`, and **indeterminate** during all other stages.
- A single import shows no cancel control; batch cancellation continues via the
  existing batch flow.

## C-ST-6 Failure banner (FR-014)
- **When** an import fails, **then** a dismissible banner shows the reason with
  "choose file again" (re-opens the picker via `launchPicker()`) and "dismiss".
- **And** the previously installed county list and sizes are unchanged (importer
  failure does not replace installed data).

## C-ST-7 Boundary detail row (FR-015)
- **Then** the boundary detail row remains: showing boundary details when
  `exists(boundaryDbFile())`, and a "not installed" indication when absent.
- Boundary bytes are also folded into the header total and the grey bar segment.

## C-ST-8 No behavioural change (FR-016)
- Import, registry, and sizing outcomes are unchanged; the
  importer/registry unit suites pass unmodified. Only view rendering changes.

## C-ST-9 Crash isolation (Constitution VI)
- `renderUsageBar`, `bindStateBMultiCounty`, and `postProgress` retain
  `try/catch(Throwable)→Log.w`; overflow/menu/banner listeners run through
  `safeRun(...)`; the `PopupMenu` anchors on a host-context view; resource and
  `findViewById` lookups are null-checked; `setBackgroundResource`/colour calls
  use concrete drawables/colours (no `android.R.attr.*`). No unguarded
  host→plugin entry point is introduced.
