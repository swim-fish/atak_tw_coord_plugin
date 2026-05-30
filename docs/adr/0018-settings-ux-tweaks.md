# ADR-0018: Settings page from tool button + search/storage UX tweaks (feature 007)

**Status**: Accepted
**Date**: 2026-05-31
**Origin**: feature `007-settings-ux-tweaks` (`/speckit-plan` → `/speckit-implement`) plus device acceptance on Samsung Galaxy SM-X826B (`R52X908JF0W`) / Android 14 / ATAK-CIV 5.7.0.3. Version bump `1.1.0 → 1.2.0`.

Feature 007 bundles three small, independent UX tweaks on top of the shipped
plugin, and the on-device acceptance run surfaced two pre-existing bugs (one in
shipped 005 code, one the long-parked GoTo readout issue) that this branch also
fixes. This ADR records the design decisions plus the two device-found pivots.

## Context

Three tweaks, all additive and behind existing seams (`PreferenceStore`,
`StreetCandidateRanker`, `ActiveDatasetRegistry`/`FileSystem`,
`TwCoordPreferenceFragment`), shipped together under one MINOR bump. No
data-schema or generator change. Phase-0 research (R1–R4) resolved the
settings-launch SDK API, the similarity-scoring model, the storage-sizing
approach, and the version-bump location.

## Decisions

### D1 — Tool button opens settings instead of cycling the coordinate unit (US2)

**Spec impact**: the **TW Coordinates** tool button (`TwCoordTool` →
`ACTION_SHOW_PLUGIN`) was handled by `TwCoordMapComponent.toggleReceiver`, which
cycled the on-map readout `Off → Taipower → TWD97 → TWD67` via `setCoordinateUnit`
plus a localised toast. The feature re-points that handler to **open the existing
`TwCoordPreferenceFragment`** and removes the cycle ("取消直接切換座標"). Format is
chosen in settings (`pref_coord_unit`); the show/hide the `Off` state used to
provide moves to a new `pref_readout_visible` `CheckBoxPreference` (default
`true`).

**Settings-launch API (R1)**: `toggleReceiver` now broadcasts
`com.atakmap.app.ADVANCED_SETTINGS` with a `toolkey` extra of `PREF_KEY` — the
ATAK-sanctioned jump-to-a-plugin's-Tool-Preferences pattern (mirrored from the
meshtastic `MeshtasticDropDownReceiver.openPluginPreferences` sample; action
listed in the SDK `docs/broadcastlist.txt`). The two callable-API alternatives
were both rejected after javap of `main.jar`: `PreferenceControl` has **no**
`openSettings(...)` method (so it cannot launch the screen), and
`AtakPreferenceFragment.showScreen(...)` is `protected` (in-fragment only, per the
helloworld sample) — neither is reachable from the tool-button receiver, leaving
the broadcast as the working public mechanism (see research.md R1). Merely opening
the page does NOT mutate the active format (FR-007). Wrapped per Constitution VI.

### D2 — Result ordering is an in-place re-rank over the existing fold (US1)

**Spec impact**: forward-search candidates can be ordered **most-similar**
(textual match to the query) or **distance** (nearest the anchor) via a persisted
`pref_search_result_ordering` preference (`DISTANCE` default — preserves current
behaviour). A `ResultOrdering` enum + `StreetCandidateRanker.reorder(List,
ResultOrdering, foldedFragment)` computes a deterministic similarity score over
the existing `StreetTextNormaliser` fold (exact > prefix > substring-by-index >
none, tie-broken by `distanceMeters`, shorter-leftover wins within a band; empty
fragment → distance order; 臺/台 + width fold honoured). Toggling re-ranks the
**cached** candidate list in place — no re-query, no change to which candidates
are returned nor to tap-to-pan/GoTo. The toggle lives both on the search page and
in Settings, bound to the same key. No edit-distance (stays out of scope per 006).

### D3 — Storage sizes via `File.length()` over existing dirs (US3)

**Spec impact**: TW Offline Addr shows each county dataset's on-disk size and a
distinct `_boundary` (townships.sqlite) folder size. `DatasetStorageSummary`
(over a `FileSystem` seam) sums `sizeOfDirectory(activeCountyDir(county))` per
county and `sizeOfDirectory(boundaryDir())` for the boundary; `ByteCountFormatter`
renders binary units (one decimal at KB+). Missing/partial files → `0`; absent
`_boundary` → `未安裝` (FR-015). No new DB opens; all reads are best-effort and
wrapped per Constitution VI.

### D4 — DEVICE-FOUND: plugin `R.string` ids must be resolved against `pluginContext`, not the dialog's ATAK Activity context

**Symptom**: on the reference device the per-county and legacy **Replace** /
**Remove** confirm dialogs in `OfflineAddressReceiver` did nothing — the button
press registered but no dialog appeared, no crash.

**Root cause**: each dialog was built with
`new AlertDialog.Builder(getMapView().getContext())` (correct — the ATAK Activity
context owns the window token) but set its title via `.setTitle(R.string.…)` with
a **plugin** resource id. `Builder.setTitle(int)` resolves the id against the
builder's context, whose `Resources` belong to the ATAK host APK — the plugin id
isn't there, so it throws `Resources.NotFoundException` during construction, which
the receiver's `safeRun` swallows. JVM/Robolectric tests run against a single
merged resource table and never see the split; the per-county confirm dialogs'
on-device walk had been deferred, so the bug shipped in 005.

**Fix**: resolve every plugin resource to a value with `pluginContext` first —
`.setTitle(pluginContext.getString(R.string.…))` — matching the already-correct
Import dialog. The decision is captured as a reusable rule in the
`plugin-dialog-resources` project skill so future button/dialog work doesn't
repeat it. While here, per-county Remove was re-pointed to rely solely on
`ActiveDatasetRegistry.remove()` (atomic close-then-delete) instead of pre-deleting
via `importer.removeActive()` while the SQLite facade is still open (which could
resurrect `places.sqlite` via WAL checkpoint-on-close).

### D5 — DEVICE-FOUND: programmatic pans need a renderer-level camera listener

**Symptom** (the long-parked issue `swim-fish/atak_tw_coord_plugin#1`, widened):
TW Coord GoTo submit AND forward-search tap-to-pan move the map, but the
bottom-left MAP coordinate + address line stays stale.

**Root cause**: both pan via
`CameraController.Programmatic.panTo(mapView.getRenderer3(), dest, false)`, which
drives the renderer camera directly and does **not** dispatch
`MapEvent.MAP_MOVED` through the `MapEventDispatcher`. The MAP readout's
`mapCentreListener` is subscribed to the dispatcher, so it never woke on
programmatic moves (gesture drags fire `MAP_SCROLL`/`MAP_SETTLED` and worked
fine).

**Fix**: `TwCoordMapComponent` registers a renderer-level
`MapRenderer2.OnCameraChangedListener2` via
`view.getRenderer3().addOnCameraChangedListener(...)` that catches **all** camera
changes, including programmatic pans. It fires on the GL/render thread, so it hops
to the UI thread via `mapView.post` and coalesces through an
`AtomicBoolean mapRefreshPending` (at most one queued refresh per UI loop), then
calls `renderMapCentre()` — which reads the now-current `mapView.getPoint()`, also
fixing the one-frame lag of the non-animated pan. The dispatcher listeners are
kept as belt-and-suspenders. No synthetic global `MAP_MOVED` is injected (it would
wake unrelated ATAK components). Closes issue #1.

## Consequences

- The Tools-icon "cycle" affordance is gone; operators switch format in Settings
  and toggle the readout's visibility there. README + user-guide (EN + zh-TW) +
  `docs/ui/settings-fragment.md` updated accordingly.
- D4's rule generalises beyond this feature — any plugin dialog that passes a
  plugin resource id to a host-context builder will silently fail; the skill
  captures it.
- D5 removes the dependency on which `MapEvent` a given ATAK version dispatches
  for a programmatic move — the renderer camera listener is the single source of
  truth for "the viewport changed".

## Verification

- `:app:testCivDebugUnitTest` (007 units: ordering reorder, formatter, storage
  summary, preference round-trip) green; no regressions.
- `:app:assembleCivDebug` + `:app:spotlessCheck` pass.
- On-device (SM-X826B): tool button opens settings (no cycle); per-county Remove
  + Import confirm dialogs appear and act; GoTo + tap-a-search-result refresh the
  bottom-left MAP coordinate and address.
- Deferred: `:app:connectedCivDebugAndroidTest` Espresso (T006/T012/T024) — needs
  the device-attached CI harness.

## Related

- Spec / plan / tasks / contracts: [`specs/007-settings-ux-tweaks/`](../../specs/007-settings-ux-tweaks/).
- Builds on [ADR-0014](./0014-offline-address-reconnaissance.md),
  [ADR-0015](./0015-offline-address-implementation.md),
  [ADR-0016](./0016-prefer-sdk-samples-before-implementing.md),
  [ADR-0017](./0017-multi-county-zip-import.md).
- Issue [`swim-fish/atak_tw_coord_plugin#1`](https://github.com/swim-fish/atak_tw_coord_plugin/issues/1) (D5).
