# Phase 0 Research: Search & Storage Page UI Redesign

All decisions are code-anchored per the project's plan-phase discipline: each
ATAK-specific claim is cross-checked against `javap -public` of
`ATAK-CIV-5.7.0.3-SDK/main.jar` **and** an ATAK SDK sample / shipped-code
reference. Android-framework UI classes (`AlertDialog`, `PopupMenu`,
`RadioGroup`, `ProgressBar`, `GradientDrawable`, `SpannableString`) are platform
APIs, not ATAK surface, so they are anchored against the Android framework and
the shipped plugin's existing usage rather than `main.jar`.

The line-level redesign already exists under `docs/design/search_settings/`:
- `ForwardSearchReceiver_changes.md` — forward-search receiver edits
- `OfflineAddressReceiver_changes.md` — storage receiver edits
- `forward_search_page.xml`, `offline_address_page.xml`,
  `offline_address_county_row.xml` — full replacement layouts
- `strings_additions.xml`, `strings_additions_offline_states.xml` — new strings
- `drawable/oa_*.xml` — five new drawables

This research validates those edits against the shipped seams and the SDK
sample dialog idiom; it does not re-derive the design.

---

## R1 — Cross-context dialog & menu construction (FR-017, the user's key constraint)

**Decision**: Build every new `AlertDialog`/`PopupMenu` with the **host ATAK
Activity context** obtained from `getMapView().getContext()`; inflate views and
resolve all `R.string`/`R.drawable` against **`pluginContext`**. Never build a
dialog with `pluginContext` (it has no window token → `BadTokenException`), and
never resolve plugin resources against the ATAK context (`NotFoundException`).

**Rationale**: This is the exact rule the user invoked ("must follow the SDK
samples or you debug for a long time"), and it is already proven three ways:

- **Shipped plugin** — `OfflineAddressReceiver.java` builds its confirm dialogs
  as `new AlertDialog.Builder(getMapView().getContext())` with
  `pluginContext.getString(...)` titles/messages (L722, L737, L796, L815); the
  import dialog comments the reason explicitly at L421–423 ("the plugin context
  lacks the Activity token. Using getMapView().getContext()").
- **SDK sample `helloworld`** —
  `HelloWorldDropDownReceiver.java:2187` `new AlertDialog.Builder(mapView.getContext())`.
- **SDK sample `meshtastic_atak`** — uses the `MapView.getMapView().getContext()`
  idiom throughout for Activity-scoped surfaces (toasts, notifications,
  PendingIntent, system services), e.g. `MeshtasticReceiver.java:150,159,211`.

**Anchor**: `javap -public main.jar com.atakmap.android.maps.MapView` →
`public static com.atakmap.android.maps.MapView getMapView();`. `getContext()`
is inherited from `android.view.View` (MapView extends `ViewGroup`), consistent
with all the call sites above. `DropDownReceiver` exposes an instance
`getMapView()` already used across this plugin.

**Alternatives considered**: caching a single Activity reference at `onCreate`
(rejected — `getMapView().getContext()` is the SDK-sanctioned, always-current
accessor and avoids a stale reference if ATAK recreates its Activity);
`pluginContext` for dialogs (rejected — silent `BadTokenException`, the precise
failure FR-017 exists to prevent).

---

## R2 — Forward-search scope control (FR-001, FR-002, FR-003, FR-008)

**Decision**: Replace the inline township `GridLayout` (`fs_district_list`) with
a two-button `RadioGroup` (`fs_scope_all` / `fs_scope_specific`) plus one
township `Button` (`fs_btn_district`). After a county is chosen,
`onCountyChosen()` pre-checks **whole-county**, disables the township button
(label = `fs_district_whole_county`), and calls the existing
`controller.chooseAllDistricts()` → `revealStreetStage()`. Selecting
"specific township" with no township chosen auto-opens the chooser (R3).

**Re-entrancy**: programmatic `scopeGroup.check(...)` fires
`OnCheckedChangeListener`. To avoid recursion, helpers detach the listener
(`setOnCheckedChangeListener(null)`), set the checked state, then re-attach via
`wireScopeListener()` — the pattern spelled out in the design notes
(`applyAll`/`applySpecific`). The listener body runs through the existing
`safeRun(...)` wrapper (Constitution VI).

**Map-follow**: `autoSelectDistrict(district)` becomes
`if (district resolvable) applySpecific(district) else applyAll()`, driving the
same scope control — no residual grid. `controller.districts()` /
`suggestedDistrict()` / `chooseDistrict()` / `chooseAllDistricts()` are verified
present in `ForwardSearchReceiver`/`ForwardSearchController` and unchanged.

**Alternatives considered**: a `Spinner` (rejected — small tap target, poor with
gloves vs. the established ≥48dp grid in a dialog); keeping the inline grid but
collapsible (rejected — still consumes vertical space and forces a township
decision the common path doesn't need).

---

## R3 — Township chooser dialog (FR-004, FR-005)

**Decision**: `showDistrictDialog()` builds a 3-column `GridLayout` of
glove-friendly cells (reusing the existing `gridCell(text, null)` helper and
`fs_grid_cell_bg`) inside a height-bounded `ScrollView`, wrapped in an
`AlertDialog` (Activity context, plugin strings per R1). First cell = "whole
county" (reverts to `applyAll()`); the suggested/auto-resolved district from
`controller.suggestedDistrict()` is prefixed with a "▶ " marker. Tapping a cell
calls `applySpecific(name)` then `dlg.dismiss()`.

**Rationale**: keeps the proven large-target grid for the case where the
operator *does* know the township, but off the main page. Height-bounding the
`ScrollView` (~`420*density`) handles counties with many townships (edge case).

**Alternatives considered**: `ListView`/`RecyclerView` single-column (rejected —
the 3-column grid packs more glove targets per screen and matches the prior
inline grid the operator already knows); an in-dialog filter `EditText` (kept
**optional** — design notes show how, but not required for MVP).

---

## R4 — House-number keypad dialog (FR-006, FR-007)

**Decision**: `showHouseDialog()` builds a `GridLayout` keypad
(`1..9`, 巷, `0`, 弄, 號, 之, ⌫) with a live `display` `TextView`, opened from
the `fs_house_field` button. Each key routes through the existing
`onKeypad(key)` (which updates `houseNumber` and re-queries via
`controller.withHouseNumber(...)` + `renderCandidates(...)`), then refreshes the
display and the field. Dialog offers **Clear** (neutral → empties and re-renders
whole-street) and **Done** (positive → dismiss). The house field stays hidden
until a street search produces results (`runSearch()` reveals it;
`reflectHouseField()` toggles hint vs. value).

**Rationale**: preserves the exact candidate-refinement behaviour (FR-016) while
removing the always-on keypad. `onKeypad` and `houseNumber` are verified present.

**Alternatives considered**: Android soft keyboard with `inputType=number`
(rejected — no 巷/弄/號/之 keys, and the soft keyboard covers the candidate list
under gloves); inline keypad retained (rejected — dominates the page height).

---

## R5 — Storage usage bar + legend (FR-009, FR-010, FR-011)

**Decision**: `renderUsageBar(snap)` builds a horizontal `LinearLayout` of
weighted segments — one per county (`LayoutParams` width 0,
`weight = max(bytes,1)`) using a shared `OA_PALETTE` indexed by `snap.values()`
iteration order, plus a grey boundary segment when `_boundary` exists — and a
parallel `addLegend(...)` row of colour-dot + label. The same `countyColor(i)`
feeds the per-row swatch in `offline_address_county_row.xml`, guaranteeing
bar = legend = row colour (FR-010). Total =
Σ `fileSystem.sizeOfDirectory(activeCountyDir(county))` + boundary folder
(FR-009), formatted by the existing `ByteCountFormatter`.

**Rationale**: a single stacked bar communicates relative share at a glance
without a charting dependency; reuses the O(counties) size reads 007 already
performs (no new DB opens, Constitution IV). `sizeOfDirectory` / `activeCountyDir`
/ `boundaryDir` / `boundaryDbFile` / `ByteCountFormatter` all verified present.

**Alternatives considered**: a third-party chart library (rejected — no-new-deps
constraint, overkill for 2–4 segments); per-county individual bars (rejected —
the stacked bar conveys share-of-total more directly). Many-county legend
overflow handled by wrap/`HorizontalScrollView` (edge case, not the 2–4 primary
target).

---

## R6 — Overflow menu + progress/error cards (FR-012, FR-013, FR-014, FR-015)

**Decision**:
- **Overflow** — each compact row gets an `offline_address_county_overflow`
  control; `showCountyMenu(anchor, county)` opens a `PopupMenu` (anchor =
  host-context view) with **Replace** and a destructively-styled **Remove**
  (`SpannableString` + `ForegroundColorSpan(0xFFFF6B6B)`), delegating to the
  existing `confirmReplaceCounty` / `confirmRemoveCounty` confirm-then-act flows
  unchanged (FR-012).
- **Progress** — `showProgress`/`hideProgress` toggle an
  `offline_address_progress_card`; `postProgress(stage, completed, total)` sets a
  `ProgressBar` **determinate** only for `Stage.COPYING` / `Stage.BUILDING_INDEX`
  (which carry a percent) and **indeterminate** otherwise (FR-013). The stage
  enum and `renderProgress` text are unchanged. No cancel on a single import
  (none exists); batch cancel keeps its existing flow.
- **Error** — `showError`/`hideError` toggle an `offline_address_error_card`
  banner with **retry** (= `launchPicker()` re-open) and **dismiss**; importer
  failure does not replace installed data, so the county list/sizes below the
  banner are unchanged (FR-014).
- **Boundary** — `renderBoundaryRow()` is retained; logic/strings unchanged, it
  shows boundary detail when present and "not installed" when absent (FR-015);
  the boundary bytes are also folded into the top bar's grey segment.

**Rationale**: moving row actions into an overflow keeps the compact row
readable while preserving every management action (SC-005); the progress/error
cards are pure view-state toggles over existing data, no importer change
(FR-016). `confirmReplaceCounty`/`confirmRemoveCounty`/`showProgress`/
`postProgress`/`showError` verified present.

**Alternatives considered**: inline replace/remove buttons kept on the row
(rejected — clutters the compact row and competes with the size/swatch); a
bottom-sheet for actions (rejected — `PopupMenu` is the lighter, SDK-consistent
anchor-menu pattern and needs no new layout).

---

## Version & localisation

**Decision**: bump `app/build.gradle` `PLUGIN_VERSION` `1.2.1` → `1.3.0` (MINOR:
operator-visible UX redesign, no API/schema change). All new strings added to
`values-zh-rTW` (primary, Taiwan terms), `values` (en base), and `values-ja`,
per `strings_additions*.xml` and Constitution V.

**Open items**: none. No `NEEDS CLARIFICATION` remain; the design is concrete and
every assumed seam is verified in the shipped code.
