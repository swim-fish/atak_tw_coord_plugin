# Phase 0 Research: Settings Page & Search/Storage UX Tweaks

**Feature**: 007-settings-ux-tweaks · **Date**: 2026-05-30

This feature touches only shipped seams; there are no new third-party
dependencies. The decisions below resolve the four open questions from plan.md.
All code references are anchored to the **verified current tree** (read in full,
not from memory) under `app/src/main/java/com/atakmap/android/twcoord/…`.

> **Plan-phase discipline note** (per user memory
> `feedback-plan-phase-code-anchoring` + `feedback-prefer-sdk-samples-before-implementing`):
> the one genuinely new SDK interaction — opening the plugin's preference screen
> programmatically (R1) — has **no existing precedent in this repo** and could
> not be verified against the SDK jar in this planning session. It is flagged as
> a MUST-verify item: before implementing, run `javap -public` against
> `../ATAK-CIV-5.7.0.3-SDK/main.jar` for `com.atakmap.android.preference.AtakPreferenceFragment`
> and `com.atakmap.app.preferences.ToolsPreferenceFragment`, and scan
> `ATAK-CIV-SDK/samples/` for a plugin that opens its own Tool Preferences
> screen. SDK jar wins on any disagreement with this note.

---

## R1 — Open the settings page from the TW Coordinates tool button (US2)

### Current behaviour (verified, code-anchored)

- `plugin/TwCoordTool.java` (the Tools-menu "TW Coordinates" icon) is an
  `AbstractPluginTool` that fires the action
  **`ACTION_SHOW_PLUGIN = "com.atakmap.android.twcoord.SHOW_PLUGIN"`**.
- `TwCoordMapComponent.toggleReceiver` (an inner `BroadcastReceiver` registered
  on that action, `TwCoordMapComponent.java:283–329`) handles the tap by
  **cycling the on-map coordinate readout**:
  `Off → Taipower → TWD97 → TWD67 → Off …`. It calls `widget.setVisible(...)`,
  `prefs.setCoordinateUnit(nextUnit)`, and shows a `Toast`. **This is the
  "直接切換座標" the request asks to remove.**
- There is **no widget-tap cycling**: `TwCoordWidget` has no touch handler; the
  cycle lives entirely in `toggleReceiver`.
- The settings screen `TwCoordPreferenceFragment` (a
  `com.atakmap.android.preference.PluginPreferenceFragment`, inflating
  `res/xml/preferences.xml`) is registered under **ATAK Settings → Tool
  Preferences → TW Coordinates** via
  `com.atakmap.app.preferences.ToolsPreferenceFragment.register(...)`
  (`TwCoordMapComponent.java:607–614`) and unregistered in `onDestroyImpl`
  (`:749`). `docs/ui/settings-fragment.md` confirms this screen is reachable
  **only** via ATAK's Settings list today, and explicitly documents that
  `ACTION_SHOW_PLUGIN` cycles the readout and does **not** open the fragment.

### Decision

- **Re-purpose `toggleReceiver`** (the `ACTION_SHOW_PLUGIN` handler) to **open
  the plugin settings screen** instead of cycling the unit. Keep the action
  constant + tool registration unchanged — only the handler body changes.
- **Cancel the coordinate cycling**: delete the unit-cycle / `setCoordinateUnit`
  / cycle-Toast logic from `toggleReceiver`. The coordinate format then changes
  **only** via the settings `pref_coord_unit` `PanListPreference`, whose existing
  change path already re-renders the widget (`prefListener` → `renderMapCentre`).
- **Preserve readout show/hide.** Cycling was also the only way to hide/show the
  on-map readout (it ended the cycle hidden). Replace that with a new
  `CheckBoxPreference` **`pref_readout_visible`** (default **true**) in settings;
  `TwCoordMapComponent` applies it on the preference snapshot and on change
  (calling `widget.setVisible(...)`), mirroring how the three `pref_address_row_*`
  toggles already propagate through `prefListener`.

### Settings-launch mechanism — RESOLVED (T014, javap-verified 2026-05-30)

Verified against `../ATAK-CIV-5.7.0.3-SDK/main.jar`:

- **`com.atakmap.app.ADVANCED_SETTINGS` broadcast + `toolkey` extra** is the
  ATAK-sanctioned way to open a plugin's registered Tool Preferences screen
  directly. Verified in the **meshtastic sample**
  (`MeshtasticDropDownReceiver.openPluginPreferences`):
  `new Intent("com.atakmap.app.ADVANCED_SETTINGS").putExtra("toolkey",
  <prefKey>)` then `AtakBroadcast.getInstance().sendBroadcast(...)`. The action
  is also listed in the SDK `docs/broadcastlist.txt` (line 81). Our fragment is
  already registered (in `TwCoordMapComponent.onCreate`) via
  `ToolsPreferenceFragment.register(new ToolPreference(..., PREF_KEY, ...))` with
  **`PREF_KEY = "tw_coord_settings"`** — the value passed as `toolkey`.
- **Rejected: `PreferenceControl.openSettings(...)`** — `javap` of
  `com.atakmap.app.preferences.PreferenceControl` shows **no such method** (it
  has `loadSettings`/`saveSettings`/`getInstance`, not an open-screen call). My
  first draft guessed this API; the compiler caught it. The broadcast is the
  correct, sample-backed mechanism.
- **Rejected: `AtakPreferenceFragment.showScreen(PreferenceFragment[, String])`**
  — it is `protected` (an in-fragment sub-screen push, used by the helloworld
  sample's `onPreferenceClick`); not callable from a `BroadcastReceiver`.

**Chosen mechanism**: in the `ACTION_SHOW_PLUGIN` handler, send
`Intent("com.atakmap.app.ADVANCED_SETTINGS")` with `toolkey = PREF_KEY` via
`AtakBroadcast`, wrapped in `try/catch(Throwable)→Log.w` (Constitution VI). No
`DropDownReceiver` fallback needed. Merely opening settings does not touch
`pref_coord_unit`, satisfying FR-007.

### Rationale

Matches the request literally (button → settings; stop the direct switch),
reuses the already-registered fragment (no new screen — Constitution III),
keeps the public broadcast action stable, and isolates the only SDK risk behind
a verify-or-fallback gate so the feature can ship regardless.

### Alternatives considered

- *Keep cycling, add a long-press for settings.* Rejected: the request says the
  button itself should open settings and the cycling should be removed.
- *Drop show/hide entirely (always-on readout).* Rejected: removes a relied-upon
  capability; the `CheckBoxPreference` is cheap and testable.
- *Build a brand-new settings screen.* Rejected: duplicates
  `TwCoordPreferenceFragment` (DRY, Constitution I).

---

## R2 — "Most similar" ordering model (US1)

### Current behaviour (verified, code-anchored)

- `address/forward/StreetCandidateRanker.rank(List<Raw> rows, String
  foldedFragment, double anchorLat, double anchorLon, int limit)`
  (`StreetCandidateRanker.java:57`) is a static, pure, never-throws method that
  fold-filters each `Raw` row, builds `AddressCandidate`s with a haversine
  distance, **sorts by `distanceMeters` ascending only** (no displayName
  tie-break), and truncates to `limit` (`<=0` ⇒ no cap). The fold is applied
  here via `StreetTextNormaliser.fold`, so **the ranker already has the folded
  query fragment in hand.**
- `address/forward/AddressCandidate` carries `lat`, `lon`, `displayName`,
  `displayNameHalfwidth`, `street`, `number`, `distanceMeters` (accessor methods
  of the same names). No `area`/`bearing` field.
- `ForwardSearchController.search(String streetFragment, int limit)`
  (`ForwardSearchController.java:178`) folds the fragment and delegates to
  `AddressDatabaseFacade.streetCandidates(district, folded, anchorLat, anchorLon,
  limit)` (or `streetCandidatesCountyWide(...)` in 全部 mode), which internally
  calls `StreetCandidateRanker.rank(...)`. So `search(...)` returns a
  **distance-ranked, limit-capped `List<AddressCandidate>`**.
- `address/forward/StreetTextNormaliser.fold(String)` is the shared 臺→台 +
  width fold used throughout `forward/`.

### Decision

- Add enum `address/forward/ResultOrdering { MOST_SIMILAR, DISTANCE }`.
- Add a new static **`StreetCandidateRanker.reorder(List<AddressCandidate>
  results, ResultOrdering ordering, String foldedFragment)`** that re-sorts an
  already-built candidate list (the existing `rank(List<Raw>, …)` is untouched,
  so all current callers/tests stay green — FR-005 regression guard):
  - **DISTANCE** → sort by `distanceMeters` ascending (the order `search(...)`
    already returns; effectively identity).
  - **MOST_SIMILAR** → **similarity desc, then `distanceMeters` asc** (FR-004
    tie-break).
- **Similarity score** (deterministic, pure, unit-tested) over the
  `StreetTextNormaliser.fold` of the candidate's `street()` (falling back to
  `displayName()` when `street` is empty — consistent with feature 006's
  empty-street→area coalescing, shipped addition A7) compared to
  `foldedFragment`: band 4 exact equality > band 3 prefix > band 2 substring
  (sub-ranked by match index) > band 1 none; within a band, shorter leftover
  ranks higher. No edit-distance (out of scope, carried from feature 006).
- **In-place re-rank without re-query** (FR-002 / C3): `ForwardSearchReceiver`
  caches the current `List<AddressCandidate>` (the `search(...)` output) and the
  folded fragment; flipping the ordering toggle re-invokes
  `StreetCandidateRanker.reorder(cached, ordering, foldedFragment)` and repaints
  — no new facade/DB call. The ordering reorders the **currently displayed**
  (distance-capped) list, which is exactly FR-002's "re-order the currently
  displayed candidate list"; the receiver also applies `reorder(...)` to each
  fresh `search(...)` result so the chosen ordering takes effect on new searches
  too. The ordering value is read from `PreferenceStore` (R4).

### Rationale

Adding an overload (not changing the signature) keeps the proven path intact and
makes the new ordering a pure, isolated unit ideal for TDD (Constitution II).
Reusing `StreetTextNormaliser` keeps similarity folding identical to the search
itself. DISTANCE stays default → zero behavioural change for untouched installs.

### Alternatives considered

- *Edit-distance similarity.* Rejected: out of scope (feature 006), heavier.
- *Re-query with `ORDER BY` in SQL.* Rejected: similarity needs the Java-side
  fold + the anchor distance is computed in Java; ranking already lives (and is
  tested) in `StreetCandidateRanker`.
- *Push ordering into `rank(List<Raw>, …)` / the facade so MOST_SIMILAR ranks the
  full set before the `limit` cap.* Rejected for v1: it would force a re-query on
  every toggle (breaking C3's "no new DB call"), and FR-002 explicitly scopes the
  re-order to the **currently displayed** list. A separate `reorder(...)` keeps
  the existing `rank`/facade path untouched. Caveat logged: MOST_SIMILAR reorders
  within the distance-capped top-N, so a highly-similar-but-distant row outside
  that top-N is not surfaced — acceptable for this UX tweak; revisit if operators
  report misses.

---

## R3 — Storage sizes in TW Offline Addr (US3)

### Current behaviour (verified, code-anchored)

- `address/OfflineAddressReceiver.renderActiveCountyList(ViewGroup)`
  (`OfflineAddressReceiver.java:108`) iterates `registry.snapshot().values()`
  (`:124`), inflates `R.layout.offline_address_county_row` per county (`:137`),
  and sets `R.id.offline_addr_county_name` (county) + `R.id.offline_addr_county_meta`
  (`data_date · N rows`, `:148–153`).
- `address/CountyActiveDataset.placesFile()` → `dataset.dbFile()` (a `File`).
- `address/FileSystem` **already exposes** everything needed:
  `boundaryDbFile()` (→ `active/_boundary/townships.sqlite`),
  `activeCountyDir(String)` (→ `active/<county>/`),
  `sizeOfDirectory(Path)` (recursive bytes, **0 if absent**), `sizeOf(Path)`,
  `exists(Path)`, `getActiveDir()`. `AtakFileSystem` is the production impl.
- `ActiveDatasetRegistry.totalBytesOnDisk()` already sums per-county
  `placesFile().length()` (file only) — a precedent for size reporting.

### Decision

- New pure helper `address/DatasetStorageSummary(FileSystem fs,
  ActiveDatasetRegistry registry)`:
  - `perCounty()` → list of `(countyZh, bytes)` where **`bytes =
    fs.sizeOfDirectory(fs.activeCountyDir(county))`** — the whole county folder
    (places.sqlite + R*Tree/WAL/SHM sidecars), the honest on-disk footprint of
    "各縣市檔案大小".
  - `boundary()` → `(present, bytes)` where `present = fs.exists(fs.boundaryDbFile())`
    and **`bytes = fs.sizeOfDirectory(fs.boundaryDbFile().getParent())`** — the
    whole `_boundary/` folder (townships.sqlite + sidecars). `present=false` ⇒
    render "未安裝" (FR-015).
- New pure util `coord/ByteCountFormatter.format(long bytes)` →
  binary-unit human string (`0 B`, `1.0 KB`, `12.3 MB`, `324.0 MB`, `x.y GB`).
- `OfflineAddressReceiver`: gains a `FileSystem` handle (injected from
  `TwCoordMapComponent`, which already owns `addressFileSystem`) and appends the
  formatted per-county size to the `offline_addr_county_meta` line (or a new
  `offline_addr_county_size` `TextView` in the row layout), plus appends one
  distinct **`_boundary` (townships.sqlite)** summary row after the county list.

### Rationale

`FileSystem.sizeOfDirectory` already does the recursive, absent-safe sizing — no
new sidecar-summing logic needed, and the `FileSystem` seam makes both helpers
unit-testable with the existing in-memory fake (Constitution II). Folder-level
sizing is more honest than `places.sqlite` alone. O(counties) directory walks
on the existing render path; non-blocking.

### Alternatives considered

- *`placesFile().length()` only.* Rejected: misses R*Tree/WAL sidecars, so the
  number under-reports real footprint. (Reuse `sizeOfDirectory` instead.)
- *Add `sizeBytes` to `CountyActiveDataset` at discovery.* Rejected: size is
  volatile (WAL); compute on render so it is fresh when the screen opens.
- *SI (1000) units.* Rejected: Android storage UIs use binary units; match them.

---

## R4 — Version bump, preference keys & toggle placement

### Decision

- **Version**: `gradle.properties` `PLUGIN_VERSION_NAME` `1.1.0 → 1.2.0` and
  `PLUGIN_VERSION_CODE` `11 → 12` (read by `app/build.gradle`, ADR-0013). MINOR
  per SemVer (additive UX).
- **New preference keys** in `prefs/PreferenceStore` — following the repo's
  existing `pref_*` key convention (e.g. `KEY_COORD_UNIT = "pref_coord_unit"`):
  - **`pref_search_result_ordering`** (String = `ResultOrdering.name()`, default
    `"DISTANCE"`), accessors `getResultOrdering()` / `setResultOrdering(...)`,
    defensive `valueOf` → `DISTANCE` on missing/corrupt (mirrors the existing
    `readUnit()` fallback pattern).
  - **`pref_readout_visible`** (boolean, default `true`), accessors
    `isReadoutVisible()` / `setReadoutVisible(...)` (from R1).
  - Whether changing `pref_search_result_ordering` should fire `fireAll()` (the
    widget does not depend on it, the forward-search page does) → it need **not**
    join the `fireAll()` key set; the forward-search receiver reads it directly
    at open + on toggle, matching how the GoTo keys are read directly without
    `fireAll()`.
- **Ordering toggle in two synced places** (FR-011): a compact two-option control
  (最相似 / 距離) in `res/layout/forward_search_page.xml` above the candidate list,
  **and** a `PanListPreference` in `res/xml/preferences.xml`. Both read/write the
  single `pref_search_result_ordering` key — one source of truth, no divergence.

### Rationale

Default `DISTANCE` preserves the shipped ordering for untouched installs (zero
regression). Following the established `pref_*` key naming + defensive-`valueOf`
fallback keeps `PreferenceStore` internally consistent. A single key keeps the
in-flow toggle and the settings entry in lockstep.

### Alternatives considered

- *Toggle only in settings* (rejected: re-ordering mid-search shouldn't require
  leaving the page — SC-001 speed) / *only on the search page* (rejected: FR-011
  asks settings to host the new preference).

---

## Summary of new/changed surfaces (verified targets)

| Surface | Kind | Tested by |
|---|---|---|
| `address/forward/ResultOrdering` enum | new | ranker tests |
| `StreetCandidateRanker.reorder(List<AddressCandidate>, ResultOrdering, String)` | new | unit (both orderings, tie-break, distance-identity) |
| similarity scorer (in ranker) | new | unit (exact/prefix/substring/none, length) |
| `coord/ByteCountFormatter` | new | unit (B/KB/MB/GB, rounding) |
| `address/DatasetStorageSummary` | new | unit (per-county dir, boundary dir, absent) |
| `prefs/PreferenceStore` `pref_search_result_ordering` + `pref_readout_visible` | changed | unit (round-trip, defaults) |
| `TwCoordMapComponent.toggleReceiver` → open settings (was: cycle unit) | changed | Espresso (button opens settings; no unit change) |
| `TwCoordMapComponent` apply `pref_readout_visible` via `prefListener` | changed | Espresso (toggle shows/hides readout) |
| `TwCoordPreferenceFragment` + `preferences.xml` ordering + readout entries | changed | Espresso + manual |
| `OfflineAddressReceiver.renderActiveCountyList` + `offline_address_county_row.xml` | changed | Espresso (size rows render) |
| `ForwardSearchReceiver` ordering toggle + cached-list re-rank | changed | Espresso (toggle re-ranks, no re-query) |
| `res/values/{strings,arrays}.xml` (zh-TW) | changed | Espresso + review |
| `gradle.properties` `PLUGIN_VERSION_NAME`/`PLUGIN_VERSION_CODE` | changed | build |

All NEEDS CLARIFICATION resolved (R1 settings-launch carries an explicit
verify-or-fallback gate). Ready for Phase 1 design.
