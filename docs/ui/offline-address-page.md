# UI — Offline Address page

**Feature**: 004-offline-address
**Source**: `app/src/main/res/layout/offline_address_page.xml` + `app/src/main/java/com/atakmap/android/twcoord/address/OfflineAddressReceiver.java`

The Offline Address page is a `DropDownReceiver` side-pane opened by the third Tools-menu icon (or by tapping the Dataset-status row in Settings). It is the operator's single management surface for the offline reverse-address dataset — import, replace, remove, inspect provenance.

## Anatomy

The page renders one of two states determined by `AddressBundleImporter.activeOrNull()`.

### State A — no dataset installed

```
┌──────────────────────────────────────────────────────┐
│ Offline Address                                      │  ← title (R.id.offline_address_title)
│ ─────────────────────────────────────────────────    │
│                                                      │
│  No address dataset installed. Use the .sqlite       │  ← R.id.offline_address_empty_state
│  file produced by the atak-tw-address-generator      │     localised long-form copy
│  project.                                            │
│                                                      │
│  [             Import…              ]                │  ← R.id.offline_address_button_import
│                                                      │
└──────────────────────────────────────────────────────┘
```

### State B — dataset active

```
┌──────────────────────────────────────────────────────┐
│ Offline Address                                      │
│ ─────────────────────────────────────────────────    │
│                                                      │
│  County             台中市                            │  ← R.id.offline_address_value_county
│  Data date          115-01                            │  ← R.id.offline_address_value_data_date
│  Source             tgos                              │  ← R.id.offline_address_value_source
│  Rows               1,316,674                         │  ← R.id.offline_address_value_rows
│  CSV SHA-256        ab12…ef34 (12 chars + ellipsis)   │  ← R.id.offline_address_value_csv_sha
│  Imported           2026-05-24 15:30 UTC              │  ← R.id.offline_address_value_imported_at
│  File SHA-256       de45…6789 (12 chars + ellipsis)   │  ← R.id.offline_address_value_file_sha
│  R*Tree built       yes (plugin / generator)          │  ← R.id.offline_address_value_rtree_built
│                                                      │
│  [   Replace…   ]    [   Remove   ]                  │  ← R.id.offline_address_button_{replace,remove}
│                                                      │
└──────────────────────────────────────────────────────┘
```

The fields under State B come from the union of the dataset's in-DB `metadata` table (county / data_date / source / inserted / csv_sha256) and the plugin-side `imported.manifest.txt` (imported_at / file_sha256 / rtree_built). Long SHA-256 values are truncated to a leading-12-chars + ellipsis affordance to stay on a single line — the full hex is available by tapping the value (long-press copies it to the clipboard).

## Import flow (Flow A — quickstart §3)

1. Operator taps **Import…** (or the Tools-menu icon when no dataset is active).
2. The page launches `OfflineAddressFilePickerActivity` (a thin trampoline `Activity` because SAF requires an `Activity` context, not a `DropDownReceiver`).
3. SAF system file picker opens at `Intent.ACTION_OPEN_DOCUMENT` with `mimeType = "*/*"`. Operator picks a `.sqlite` file.
4. The trampoline returns the `Uri` via `ActivityResultLauncher`; `OfflineAddressReceiver` opens an `InputStream` and posts a one-shot job to the `twcoord-address-import` executor.
5. A progress chip cycles through stages: `offline_address_progress_copying` → `offline_address_progress_verifying` → `offline_address_progress_building_index` (only for v1 files) → `offline_address_progress_activating`.
6. On success: the page transitions to State B with the new dataset's fields populated; `OfflineAddressIntents.ACTION_DATASET_CHANGED` broadcasts so `AddressSubsystem.onActiveDatasetChanged()` re-opens the facade and address rows on the map start resolving from the new data.
7. On failure: the page stays at the prior state (State A if no previous dataset, or State B with the previous dataset's fields still showing). An inline error TextView surfaces the localised failure reason (`offline_address_error_*`); the previously-active dataset is untouched (atomic activation per ADR-0014 D10).

## Replace flow (Flow C2)

State B → **Replace…** → confirmation dialog with the active dataset's county name (`offline_address_confirm_replace`, `%1$s` = county) → on confirm, identical to the Import flow above, with the previously-active dir renamed aside before the `ATOMIC_MOVE` and best-effort-deleted after the new dataset activates.

## Remove flow (Flow C3)

State B → **Remove** → confirmation dialog with the active dataset's county name (`offline_address_confirm_remove`, `%1$s` = county) → on confirm, `AddressBundleImporter.removeActive()` recursively deletes the `active/` directory, then broadcasts `ACTION_DATASET_CHANGED`. The page transitions to State A. The map widget's three address rows go straight to `Hidden` (no Loading flash — `AddressSubsystem.onActiveDatasetChanged()` short-circuits to Hidden when `facade == null`, per the US4 fix in commit `9379ca7`).

## Error rendering

Inline error messages appear in `R.id.offline_address_inline_error` (TextView, red), localised via the `offline_address_error_*` string keys. The error stays visible until the operator dismisses it by retrying the Import or closing the page.

| `Failure.Reason`                | Localised string key                              | Operator action                                  |
|----------------------------------|---------------------------------------------------|--------------------------------------------------|
| `NOT_OPENABLE`                   | `offline_address_error_not_openable`              | Verify the file is a genuine `.sqlite`           |
| `IS_A_ZIP`                       | `offline_address_error_is_zip`                    | Extract the `.zip` on the host first             |
| `MISSING_METADATA_TABLE`         | `offline_address_error_missing_metadata`          | Use a generator-produced file                    |
| `MISSING_REQUIRED_METADATA_KEY`  | `offline_address_error_missing_required_key` (`%1$s` = missing key) | Re-export from a newer generator |
| `UNSUPPORTED_SCHEMA_VERSION`     | `offline_address_error_unsupported_schema` (`%1$s` = details) | Update the plugin or re-export at a supported version |
| `MISSING_PLACES_TABLE`           | `offline_address_error_missing_places`            | Use a generator-produced file                    |
| `UNEXPECTED_PLACES_COLUMNS`      | `offline_address_error_unexpected_columns` (`%1$s` = column list) | Re-export from a generator version matching this plugin's schema range |
| `RTREE_BUILD_FAILED`             | `offline_address_error_rtree_failed`              | Check device storage / power; retry              |
| `DISK_FULL`                      | `offline_address_error_disk_full`                 | Free space and retry                             |
| `ACTIVATION_FAILED`              | `offline_address_error_activation_failed`         | Previous dataset still active; retry             |
| `IO_ERROR`                       | `offline_address_error_io` (`%1$s` = exception)   | Check logs; retry                                |

The "previous dataset preserved on failure" guarantee is enforced by atomic activation (ADR-0014 D10); the page's inline error text re-states this so the operator understands they have not lost their data.

## Recovery from files vanishing (US4 / SC-005)

If the operator deletes the active dir out-of-band (e.g. via ADB), `activeOrNull()` returns null cleanly on the next page open and the page renders State A. The active dir is left in place if a partial directory remains; the next successful import overwrites it. Specific failure modes are logged at `Log.w` (per AddressBundleImporter `activeOrNull` logging in commit `9379ca7`) so a future operator can grep logcat for the reason.

## String keys

All 30+ user-facing keys live under `app/src/main/res/values/strings.xml`, with proofread Traditional Chinese and Japanese counterparts in `values-zh-rTW/` and `values-ja/` (T054 / T055). The page contains zero English literals at runtime — the locale-override pathway (ADR-0003) resolves each key against the operator's effective UI language.

Key groups (verbatim names):

- Identity — `tool_offline_address_label`, `tool_offline_address_desc`, `offline_address_page_title`, `offline_address_empty_state`.
- Buttons — `offline_address_button_import / _replace / _remove`.
- Confirmations — `offline_address_confirm_replace`, `offline_address_confirm_remove` (both take `%1$s` = county).
- Progress — `offline_address_progress_copying / _verifying / _building_index / _activating`.
- State B fields — `offline_address_field_county / _data_date / _source / _rows / _csv_sha / _imported_at / _file_sha / _rtree_built`.
- Failures — `offline_address_error_*` (see table above).

## Feature 005 additions (v1.0.6)

v1.0.6 lifts the single-active assumption: the page now holds N
independently-managed county datasets and accepts both bare `.sqlite`
files and ZIP bundles produced by
[`atak-tw-address-generator`](https://github.com/swim-fish/atak-tw-address-generator)
v2 (`places-<county>.sqlite` per-county zip, or the bundled
`tw-central-full.zip`).

### State B layout (multi-county)

Each active county is rendered as a row in a vertical `ScrollView`
+ `LinearLayout` container (`offline_address_county_row.xml`), with
its own Replace + Remove buttons:

```
┌──────────────────────────────────────────────────────────────┐
│ Offline Address                                              │
│ ──────────────────────────────────────────────────────────── │
│                                                              │
│  ── 台中市 ────────────────────────────                       │
│  County        台中市                                         │
│  Data date     2026-05-15                                    │
│  Rows          1,316,674                                     │
│  [   Replace…   ]   [   Remove   ]                           │
│                                                              │
│  ── 彰化縣 ────────────────────────────                       │
│  County        彰化縣                                         │
│  Data date     2026-05-15                                    │
│  Rows          678,392                                       │
│  [   Replace…   ]   [   Remove   ]                           │
│                                                              │
│  [   Add more…   ]   [   Done   ]   (queued: 0)              │
│  Total: 789 MB on disk                                       │
└──────────────────────────────────────────────────────────────┘
```

Per-county Replace dialogs use ADR-0015 D8's `AlertDialog(getMapView().
getContext())` pattern so the dialog inherits the ATAK Activity's
window token rather than the plugin context (which throws
`BadTokenException`).

### ZIP bundle import (`tw-central-full.zip` etc.)

The Import button now accepts both `.sqlite` and `.zip` files. ZIP
input is routed through `ZipExtractor` (streaming + per-entry
isolation) → `ZipEntryClassifier` (sorts entries into per-county
`places-<county>.sqlite` vs supplementary files) →
`BatchImportCoordinator` (enqueues each classified entry on the
single-thread executor for activation).

Supplementary content shipped in v2 bundles (townships, OSM
landmarks, roads) is **skipped, not failed** in v1.0.6 — those are
feature 006 work. The progress chip and per-entry status surfaces
report `offline_address_entry_status_skipped_supplementary` for
clarity.

### Chained-picker batch session (Clarifications Q1 + Q3)

The operator may keep adding files / ZIPs to the same batch session
mid-import. Each pick enqueues onto the coordinator's single-thread
executor (preserving FIFO ordering); the page's queue badge
(`offline_address_batch_in_flight_format` — `N queued`) updates
live and the bottom summary (`offline_address_batch_done` —
`Activated %d · Replaced %d · Skipped %d · Failed %d`) refreshes
on every batch boundary.

`onBatchComplete` schedules a 3-second auto-hide of the progress
chip when `report.failedCount() == 0`; failure-bearing batches stay
visible so the operator can read the inline error.

### v1.0.5 → v1.0.6 auto-migrate (US4)

On plugin `onCreate`, `AutoMigrator` detects a legacy
`active/places.sqlite` layout and moves it under
`active/<county>/places.sqlite` based on the metadata.county value.
`ATOMIC_MOVE` is the happy path; on a cross-mount failure the
migrator falls back to copy + verify + delete, with full rollback
on a partial move. Idempotent — `Result.AlreadyMigrated` returned
on subsequent runs. Reference: `AutoMigrator.tryMigrate()` +
ADR-0017.

### New per-county string keys

- `offline_address_button_continue_adding` — "Add more"
- `offline_address_button_done` — "Done"
- `offline_address_button_cancel_batch` — "Cancel batch"
- `offline_address_batch_in_flight_format` — queue badge
- `offline_address_batch_done` — bottom summary
- `offline_address_entry_status_{extracting,validating,activated,
  skipped_supplementary,skipped_duplicate,failed}` — per-entry
  status labels
- `offline_address_error_zip_no_valid_datasets`,
  `offline_address_error_county_mismatch_format` — new failure
  reasons specific to the ZIP / per-county Replace paths
- `offline_address_total_disk_usage_format` — page footer total

## Out of scope for v1.0.6

- Multi-source consumption beyond TGOS places (townships, OSM
  landmarks, OSM roads nearest-road) — deferred to feature 006.
- Forward search ("type a place name → show on map") — `places_fts`
  index is shipped by the generator but not surfaced by this feature.
- Drag to reposition the side-pane (`DropDownReceiver` opens at its
  host-determined location).

## Screenshots

_TODO — capture during US1 / US2 / US3 device acceptance walks (T031 / T044 / T057) and embed:_

- `offline-address-state-a-en.png` — empty State A, English.
- `offline-address-state-a-zh-tw.png` — empty State A, Traditional Chinese.
- `offline-address-state-a-ja.png` — empty State A, Japanese.
- `offline-address-state-b-taichung.png` — populated State B with Taichung fixture.
- `offline-address-progress-copying.png` — Import progress chip mid-copy.
- `offline-address-progress-building-index.png` — Import progress chip during R*Tree build (v1 import).
- `offline-address-progress-activating.png` — Import progress chip during atomic activation.
- `offline-address-error-not-openable.png` — inline error for `NOT_OPENABLE`.
- `offline-address-error-is-zip.png` — inline error for `IS_A_ZIP`.
- `offline-address-error-missing-metadata.png` — inline error for `MISSING_METADATA_TABLE`.
- `offline-address-confirm-replace.png` — Replace confirmation dialog.
- `offline-address-confirm-remove.png` — Remove confirmation dialog.

## Feature 008 redesign — usage summary, overflow menu, progress/error cards

State B's flat per-county list became a storage dashboard. Importer / registry /
sizing behaviour is unchanged — only rendering.

- **Usage summary** (`offline_address_usage_card`): a total-on-disk figure
  (`offline_address_usage_total`), a single stacked bar
  (`offline_address_usage_bar`) with one weighted segment per county plus a grey
  **基礎資料** (boundary) segment, and a colour legend
  (`offline_address_usage_legend`). The total includes the boundary folder
  (FR-009). The bar segment, legend dot, and per-row swatch for a county share
  one colour from `OA_PALETTE`, indexed by snapshot order (FR-010 / SC-004).
- **Compact rows** (`offline_address_county_row`): colour swatch
  (`_county_color`) + name + "資料日期 · 筆數" sub-line + on-disk size
  (`_county_size`) + a ⋮ overflow (`_county_overflow`) + a divider.
- **Overflow menu** (US4): the former inline Replace / Remove buttons collapse
  into a `PopupMenu` with **取代…** and a destructively-styled (red) **移除**,
  delegating to the existing confirm-then-act flows unchanged.
- **Import-in-progress card** (`offline_address_progress_card`): the old plain
  progress text becomes a card with a `ProgressBar` that is determinate (percent)
  during `COPYING` / `BUILDING_RTREE` and indeterminate otherwise (US5 / FR-013).
- **Failure banner** (`offline_address_error_card`): a dismissible red banner with
  the reason, **重新選擇檔案** (re-opens the picker) and **關閉**; installed data
  is left untouched on failure (US5 / FR-014).
- **Boundary row** (`offline_address_boundary_row`): retained, now wrapped in a
  dashed `oa_boundary_block_bg` block; shows the boundary size when installed and
  "未安裝" when absent (FR-015). Its bytes are also folded into the top bar.
- New drawables: `oa_usage_card_bg`, `oa_usage_track_bg`, `oa_boundary_block_bg`,
  `oa_progress_card_bg`, `oa_error_card_bg`. New strings:
  `offline_address_usage_boundary_label`, `offline_address_importing_label`,
  `offline_address_error_title`, `offline_address_action_retry/dismiss` (en /
  zh-rTW / ja). See ADR-0020.

### Localisation follows the in-app language override (ADR-0020 F5)

`OfflineAddressReceiver` now takes a `Supplier<Context>` localised-context supplier
(`() -> localisedPluginContext`) instead of a fixed `pluginContext`, and
re-inflates in `onReceive` when the UI language changed since the last open — the
same pattern as `ForwardSearchReceiver` (ADR-0003). Before this fix the storage
page's strings (import / replace / remove, total-usage figure, `_boundary` row,
overflow menu, legend) were frozen at construction and never switched language.
As with the forward page, a language change while the page is **open** takes
effect on the next open (close + reopen), not live.

## Related artefacts

- Spec: `specs/004-offline-address/spec.md` FR-001..FR-014, FR-019, SC-001..SC-006.
- Contracts: `specs/004-offline-address/contracts/address-bundle-importer.md`, `contracts/offline-address-page.md`, `contracts/widget-address-rows.md`, `contracts/address-preferences.md`.
- Quickstart: `specs/004-offline-address/quickstart.md` (acceptance flows A / B / C1..C4 + perf smoke tests).
- ADRs: ADR-0014 (Phase 0 reconnaissance — R6 page pattern, R8 SAF picker, R10 atomic activation, R12 entry-point audit, R15 coverage-gap honesty rule).
- Sibling docs: `docs/ui/readout-widget.md` (address rows beneath each coordinate row), `docs/ui/settings-fragment.md` (the three per-row toggles + dataset-status row).
