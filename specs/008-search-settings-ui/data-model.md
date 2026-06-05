# Phase 1 Data Model: Search & Storage Page UI Redesign

This feature is **presentation-only**: no persisted schema, table, or wire
format changes. The "entities" below are transient **view-model** concepts held
inside the two receivers while their pages are open. Each maps to existing
controller/registry state — nothing here is serialised.

---

## ForwardSearchReceiver view-model

### ScopeSelection

The operator's current search breadth.

| Field | Type | Values / Default | Notes |
|---|---|---|---|
| `mode` | enum (implicit) | `WHOLE_COUNTY` (default after county chosen) \| `SPECIFIC_TOWNSHIP` | Surfaced by `scopeGroup` (`fs_scope_all` / `fs_scope_specific`). |
| `chosenDistrict` | `String?` | `null` = whole-county | The new receiver field; `null` ⇔ `WHOLE_COUNTY`. |

**State transitions**:
- county chosen → `mode = WHOLE_COUNTY`, `chosenDistrict = null`,
  `controller.chooseAllDistricts()`, township button disabled.
- select "specific" with `chosenDistrict == null` → open district dialog (R3).
- pick township `d` → `applySpecific(d)`: `mode = SPECIFIC_TOWNSHIP`,
  `chosenDistrict = d`, `controller.chooseDistrict(d)`, button shows `d`.
- pick "whole county" in dialog → `applyAll()`: back to `WHOLE_COUNTY`.
- map-follow resolves district `d` → `applySpecific(d)`; unresolved →
  `applyAll()`.

**Invariants**: `mode == SPECIFIC_TOWNSHIP ⇒ chosenDistrict != null` and
`chosenDistrict ∈ controller.districts()`. Programmatic `check()` always brackets
with listener detach/re-attach (no recursion).

### TownshipChoice (dialog-scoped)

The list presented in `showDistrictDialog()`.

| Field | Source | Notes |
|---|---|---|
| districts | `controller.districts()` | county's townships, unchanged data. |
| suggested | `controller.suggestedDistrict()` | marked "▶ " in the grid. |
| whole-county cell | constant | first cell → `applyAll()`. |

### HouseNumberEntry (dialog-scoped)

| Field | Type | Notes |
|---|---|---|
| `houseNumber` | `StringBuilder` (existing) | digits + 巷/弄/號/之; mutated by `onKeypad`. |
| folded form | derived | via existing fold; drives `controller.withHouseNumber(...)`. |
| field text | derived | empty → `fs_house_hint`; else the number (`reflectHouseField()`). |

**Visibility**: house field hidden until `runSearch()` yields results
(FR-007). Clear → empty + whole-street re-render; Done → dismiss, value kept.

---

## OfflineAddressReceiver view-model

### CountyStorageEntry (per row)

| Field | Source | Notes |
|---|---|---|
| name | `generator().county()` | row title. |
| summary | `dataDate()` · `insertedRows()` | `pref_address_active_dataset_row_format` ("115-01 · 731005 筆"). |
| size | `fileSystem.sizeOfDirectory(activeCountyDir(county))` | `ByteCountFormatter`. |
| colour | `OA_PALETTE[index % len]` | index = `snap.values()` iteration order; **must equal** bar segment + legend colour. |
| overflow | ⋮ control | opens `showCountyMenu(anchor, county)`. |

### StorageSummary (page header)

| Field | Derivation | Notes |
|---|---|---|
| total | Σ per-county sizes + boundary folder size | `offline_address_total_disk_usage_format`; equals sum in 100% of renders (SC-003). |
| segments | one weighted view per county + grey boundary | `weight = max(bytes, 1)`; bar clipped to rounded track. |
| legend | colour-dot + label + size per segment | wraps/scrolls if many counties (edge case). |

**Invariant**: for county `c`, colour(bar segment) = colour(legend) =
colour(row swatch) (FR-010 / SC-004).

### ImportStatus (transient)

| State | Trigger | UI |
|---|---|---|
| idle | no import | progress card hidden, error banner hidden. |
| in-progress | importer `ProgressListener` | progress card visible; `ProgressBar` **determinate** for `COPYING`/`BUILDING_INDEX`, else **indeterminate**; progress text shown (FR-013). |
| failed | import error | error banner visible (reason + retry + dismiss); installed county list/sizes unchanged (FR-014). |

**Boundary detail row**: retained; shows boundary detail when
`fileSystem.exists(boundaryDbFile())`, else "not installed" (FR-015). Boundary
bytes also counted in `StorageSummary.total` and shown as the grey bar segment.

---

## Out of scope (unchanged)

`ForwardSearchController`, `AddressBundleImporter`, `ActiveDatasetRegistry`,
`FileSystem`, `StreetCandidateRanker` ordering, `ResultOrdering`, the reverse
geocode facade, and all SharedPreferences keys — none change. Search results,
ranking, and import outcomes are byte-for-byte identical for the same inputs
(FR-016 / SC-006).
