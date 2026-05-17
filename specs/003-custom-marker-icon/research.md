# Phase 0 Research: Custom Marker Icon on the GoTo Page

**Feature**: `003-custom-marker-icon`
**Date**: 2026-05-17
**Spec**: [spec.md](./spec.md) | **Prior ADR**: [ADR-0010](../../docs/adr/0010-custom-marker-icon-picker.md)

This phase resolves the technical unknowns surfaced by [Technical Context](./plan.md#technical-context) of plan.md. Every SDK claim below is anchored to **both** the bundled SDK jar (`javap -public` against `ATAK-CIV-5.7.0.3-SDK/main.jar` — the build-time contract) and the upstream `.java` source on `github.com/TAK-Product-Center/atak-civ` `main` (implementation bodies, for cross-checking behaviour). When the two disagree, the SDK jar wins (see the [Plan-phase code anchoring](#anchoring-discipline) note at the bottom).

## Research Items

### R1 — Iconset and icon enumeration

**Decision**: Read all iconsets via `UserIconDatabase.instance(pluginContext).getIconSets(withIcons=true, withBitmaps=false)`.

**Why**: `getIconSets` is the single public read entry point exposed by the SDK that walks ATAK's `iconsets.sqlite` and returns a `List<UserIconSet>`. The two booleans let the caller trade off cursor work — pulling icon rows but skipping the bitmap blob keeps the step-1 query cheap enough to run inside SC-002's 300 ms picker-open budget on the reference device (cursor walk + ~1–2 KB of name/group/filename strings per icon; bitmap blobs are typically 1–4 KB each and add up across hundreds of icons).

**Signature** (`javap -public` on `main.jar`):

```text
public java.util.List<com.atakmap.android.icons.UserIconSet> getIconSets(boolean, boolean);
```

**Upstream source**: [`UserIconDatabase.java:317`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIconDatabase.java#L317) — confirms a single `SELECT * FROM iconsets` followed by a per-iconset `SELECT * FROM icons WHERE iconset_uid=?` when `bIcons=true`. Bitmap blobs are read only when `bBitmaps=true`.

**Alternatives considered**:

- `getIconSet(String uid, ...)` / `getIconSetByName(String, ...)` — single-set lookup; useful for picker step 2 *if* we cached the UID, but starting with the full enumeration matches the spec's "list every iconset" wording (FR-003 step 1).
- `IconsMapAdapter.initializeUserIconDB(ctx)` — would re-trigger the seed-DB extraction logic. Not needed; ATAK has done this at startup before any plugin loads.

### R2 — Bitmap fetch strategy at picker step 2

**Decision**: At step 2, fetch each icon's bitmap lazily per grid cell via `UserIconDatabase.getIconBitmap(int id)`, dispatched from the grid adapter's `getView()` and capped at one decode per scroll frame.

**Why**: A 500-icon iconset with `withBitmaps=true` would load ~1–2 MB of raw PNG blobs synchronously on a single DB cursor walk — guaranteed to bust SC-003's 500 ms budget and SC-002's 300 ms picker-open. Lazy per-cell fetch lets the visible cells populate first (8–12 cells per viewport at 64 dp thumbnails); the rest stream in as the operator scrolls.

**Signature**:

```text
public android.graphics.Bitmap getIconBitmap(int);
```

**Upstream source**: [`UserIconDatabase.java:376`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIconDatabase.java#L376) → delegates to [`getIconBytes(int)`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIconDatabase.java#L384) — single indexed `SELECT bitmap FROM icons WHERE id=?` followed by `BitmapFactory.decodeByteArray`. Sub-millisecond per call for typical 32–64 px PNGs on the reference device.

**Threading**: `getIconBitmap` is sync I/O (cursor + bitmap decode). It MUST NOT run on the main thread per Constitution Principle IV. Implementation will hand each `getView()` request to a small `ThreadPoolExecutor` (2–4 worker threads) and post the result back via `View.post(...)`. A `LruCache<Integer, Bitmap>` (~16 MB) avoids re-decoding the same icon while the operator scrolls.

**Alternatives considered**:

- `UserIcon.GetIconBitmap(iconsetPath, ctx)` — same SQL underneath but also runs the iconsetpath parser; slightly more work per call. We already have the `UserIcon` objects from R1, so the int-id path is leaner.
- `UserIcon.GetIconBitmapQuery(int)` returns the raw SQL — useful if we wanted to batch-load via a custom cursor, but the per-call cost is already low enough.

### R3 — Marker placement with a custom icon

**Decision**: Extend the existing `PlacePointTool.MarkerCreator` chain in `TwCoordGotoView.submitOk()` with one new builder call: `.setIconPath(iconsetPath)` before `.placePoint()`.

**Why**: `setIconPath` is the SDK's documented hook for binding a marker to a `UserIcon`. When the path passes `UserIcon.IsValidIconsetPath(path, false, ctx)`, `PlacePointTool.placePoint()` automatically (a) writes the `IconsetPath` metadata onto the placed marker and (b) routes the marker into the User Icons `MapGroup` (`usericonGroup`) via `UserIcon.GetOrAddSubGroup(...)`. No additional `setMetaString`, no `marker.refresh()`, no manual MapGroup wiring — one builder call covers everything.

**Signatures**:

```text
// MarkerCreator builder, javap on main.jar:
public com.atakmap.android.user.PlacePointTool$MarkerCreator setIconPath(java.lang.String);
public com.atakmap.android.maps.Marker placePoint();

// UserIcon constants, javap on main.jar:
public static final java.lang.String IconsetPath;
```

**Upstream source**:

- [`PlacePointTool.java:215`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/user/PlacePointTool.java#L215) — `setIconPath` body (null/empty rejection only).
- [`PlacePointTool.java:408`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/user/PlacePointTool.java#L408) — `placePoint` writes `marker.setMetaString(UserIcon.IconsetPath, iconsetPath)` when the field is non-empty.
- [`PlacePointTool.java:582`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/user/PlacePointTool.java#L582) — `getMapGroup()` routes the marker into the User Icons group when `IsValidIconsetPath(path, false, ctx)` is true.
- [`UserIcon.java:30`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIcon.java#L30) — `IconsetPath` constant string value is `"IconsetPath"` (capital-I; this is the marker-metadata key, not a path prefix).

**Important**: the javadoc on `setIconPath` says "asset://[uid]/directory/name", but the **actual** call (line 408) just stores the string verbatim. The bundled iconsets and `UserIcon.GetIconsetPath(uid, group, fileName)` produce the format `<uid>/<group>/<filename>` with no `asset://` prefix; this is the format we use.

**Constitution VI**: the `placePoint()` call MUST be wrapped in `try`/`catch (Throwable)`. The existing `TwCoordGotoView.submitOk()` already wraps the placement call; the new `setIconPath` chain change requires no extra guard.

### R4 — Iconset path format

**Decision**: The plugin produces and consumes iconset paths in the exact form `<iconsetUid>/<group>/<filename>` — three slash-separated tokens, none of which may be empty.

**Why**: This is the format that (a) `UserIcon.GetIconsetPath(uid, group, name)` produces, (b) `UserIcon.IsValidIconsetPath(...)` accepts, (c) `UserIcon.GetIconFromIconsetPath(...)` parses, and (d) `PlacePointTool.placePoint()` stores onto the marker.

**Upstream source**: [`UserIcon.java:30`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIcon.java#L30) — javadoc on the constant explicitly: `<iconsetpath UID>/<group>/<filename>`. [`UserIcon.java:352`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIcon.java#L352) — `GetIconsetPath` rejects any token that is empty.

**Edge case**: `GetIconFromIconsetPath` at [`UserIcon.java:373`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIcon.java#L373) ignores `tokens[1]` (the group) when querying the DB — it only uses `tokens[0]` (uid) and `tokens[last]` (filename). This means two icons with the same filename in different groups of the same iconset collide on resolution. We treat this as the SDK's responsibility — the plugin persists the canonical 3-token form and lets `GetIconFromIconsetPath` do the lookup; if the SDK can't disambiguate, neither will we (group collision is rare and not on the spec's hot path).

### R5 — Persisted-icon validity detection (FR-009 fallback)

**Decision**: On every page open with `CUSTOM_ICON` mode persisted, call `UserIcon.IsValidIconsetPath(persistedPath, requireDatabaseMatch=true, pluginContext)`. False ⇒ FR-009 fallback fires: revert to `MOVE_ONLY`, clear `pref_goto_last_iconset_path`, queue the one-shot empty-state hint.

**Why**: This is the SDK's authoritative "does this iconset path still resolve?" check. It splits the path, validates the format, and (when `requireDatabaseMatch=true`) confirms `GetIconFromIconsetPath` returns non-null.

**Signature**:

```text
public static boolean IsValidIconsetPath(java.lang.String, boolean, android.content.Context);
public static boolean IsValidIconsetPath(java.lang.String, android.content.Context);  // calls the 3-arg with true
```

**Upstream source**: [`UserIcon.java:121`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIcon.java#L121).

**Why not just call `GetIconFromIconsetPath` and check for null**: equivalent on the happy path, but `IsValidIconsetPath` short-circuits on the 2525C / Spot Map fast paths (lines 128–130), which we don't want to accidentally tolerate as "valid" custom icons. The 3-arg version with `requireDatabaseMatch=true` is the cleanest API for our check.

### R6 — Reacting to iconset add/remove during a session

**Decision**: Register a `BroadcastReceiver` on `ICONSET_ADDED` and `ICONSET_REMOVED` while the GoTo page is open; on either action, invalidate the picker dialog's iconset list cache and re-evaluate the current selection's validity (FR-009 path if it now resolves to nothing).

**Why**: The host's iconset manager runs outside the plugin's view. Without listening, the picker could show a stale list (a just-removed iconset still in the iconset chooser, or a just-added one missing). Both broadcast constants are public on `IconsMapAdapter`.

**Signatures and broadcast payloads**:

```text
public static final java.lang.String ADD_ICONSET     = "com.atakmap.android.icons.ADD_ICONSET";
public static final java.lang.String REMOVE_ICONSET  = "com.atakmap.android.icons.REMOVE_ICONSET";
public static final java.lang.String ICONSET_ADDED   = "com.atakmap.android.icons.ICONSET_ADDED";
public static final java.lang.String ICONSET_REMOVED = "com.atakmap.android.icons.ICONSET_REMOVED";
```

**Upstream source**:

- [`IconsMapAdapter.java:51–56`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/IconsMapAdapter.java#L51) — constant declarations.
- [`IconsMapAdapter.java:578`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/IconsMapAdapter.java#L578) — `ICONSET_ADDED` payload: `extra "uid"` (String), `extra "show_progress"` (boolean).
- [`IconsMapAdapter.java:656`](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/IconsMapAdapter.java#L656) — `ICONSET_REMOVED` payload: `extra "uid"` (String).

**Subscription lifecycle**: register on `onDropDownVisible(true)`, unregister on `onDropDownClose()` — matches the pattern feature 002's `MapCenterAutoFillStream` already uses for its `MAP_*` listeners. Registration uses `AtakBroadcast.getInstance().registerReceiver(...)` — already a known pattern in `TwCoordGotoReceiver`.

**Constitution VI**: the `onReceive` callback MUST be wrapped in `try/catch (Throwable)`.

### R7 — CoT type for the Custom Icon marker mode (FR-012)

**Decision**: `MarkerMode.CUSTOM_ICON.cotType() = "b-m-p-s-m"` — the Spot Map generic user-placed pin, same type as the existing `WAYPOINT` mode.

**Why**: `b-m-p-s-m` is documented in the existing `MarkerMode.java:18-22` block (audited in `c5d9d40`) as "the canonical ATAK user-placed generic pin type, used by the helloworld SDK sample's drop-pin path." It carries no affiliation semantics (unlike `a-f-G`, `a-h-G`, …), which matches FR-012's "generic user-placed pin so the marker carries no spurious affiliation semantics." When `setIconPath` is also set, the type takes a back seat — `PlacePointTool` routes the marker into the User Icons MapGroup based on the icon-path validity (R3), so the operator-visible identity is purely the picked icon.

**Affiliation sub-question**: should the operator be able to pick CoT type AND custom icon orthogonally (e.g. "friendly + custom icon")? Out of scope for this feature — see [Assumptions](./spec.md#assumptions) bullet on affiliation. If a later feature needs it, the enum becomes a `(cotType, iconsetPath?)` pair instead of a flat enum value.

### R8 — UI integration into the existing GoTo page

**Decision**: Extend `app/src/main/res/layout/tw_coord_goto.xml` with (a) a 9th `RadioButton` row in the existing marker-mode area, (b) a picker preview `LinearLayout` immediately under the 9th radio that shows the empty-state / fallback-hint / thumbnail+name. The picker dialog itself is a `androidx.appcompat.app.AlertDialog`-equivalent constructed at runtime — feature 002 uses plain `android.app.AlertDialog` to avoid the AppCompat dependency (see [ADR-0009 §D6](../../docs/adr/0009-tw-coord-goto-input-page.md)); we will do the same.

**Why**: The picker preview lives inline so the operator sees the current selection at all times (matches the visual weight of the existing 8 radios per the spec's clarification). The dialog itself is transient — opens on preview tap, closes on icon pick / cancel — and does not need to remain across page closes (Assumption: "transient overlay, no per-dialog-position persistence").

**Layout**:

- Marker-mode header (existing `R.id.goto_marker_mode_header`) is unchanged.
- The existing 8 radios stay in their 2 `LinearLayout` rows.
- A 3rd row is added with the 9th radio (`R.id.goto_mode_custom_icon`) on its own.
- Under the radios, a new `LinearLayout` (`R.id.goto_custom_icon_preview`) visibility-toggles on `markerMode == CUSTOM_ICON`. It contains an `ImageView` (`R.id.goto_custom_icon_thumb`, 32 dp square) + a `TextView` (`R.id.goto_custom_icon_label`) for the iconset name. A second `TextView` (`R.id.goto_custom_icon_hint`, gone by default) carries the FR-009 fallback hint.

**Picker dialog**: a single reusable `Dialog` instance per `TwCoordGotoView`. Two `View` states inside it: an iconset list (`ListView` over `List<UserIconSet>`) and an icon grid (`GridView` over `List<UserIcon>` for the selected iconset). A `Back` button in the dialog's title bar returns to the iconset list when at step 2. System back / outside-tap dismisses the dialog as cancel (per spec edge cases).

**Strings**: ~10 new keys across `values/`, `values-zh-rTW/`, `values-ja/`:

- `goto_mode_custom_icon` — radio label
- `goto_custom_icon_empty` — "Pick an icon"
- `goto_custom_icon_hint_lost` — "Selected icon no longer installed. Pick again."
- `goto_custom_icon_dialog_title_iconsets` / `..._icons`
- `goto_custom_icon_back` — back button
- `goto_custom_icon_empty_iconsets` — "No iconsets installed."
- `goto_custom_icon_empty_icons` — "This iconset has no icons."

All read through `TwCoordGotoView.localisedContext` per FR-013 (the existing `refreshLocalisedStrings()` pathway).

### R9 — Persistence (extending PreferenceStore)

**Decision**: Two new `SharedPreferences` keys in the existing `tw_coord_settings` file:

- `pref_goto_marker_mode` — enum name (`"MOVE_ONLY"`, `"WAYPOINT"`, … `"CUSTOM_ICON"`); default `"MOVE_ONLY"`.
- `pref_goto_last_iconset_path` — String (the canonical `<uid>/<group>/<filename>`); default `null`.

**Why**: Reuse the existing `PreferenceStore` typed wrapper. No new file, no schema versioning needed. The keys join the existing `pref_goto_*` family.

**Behaviour change from feature 002**: ADR-0009 D1 documented that marker mode resets to `MOVE_ONLY` on every session restart (in-session-only persistence). This feature **changes that** — `pref_goto_marker_mode` is now durable. Rationale: see spec's clarification answer Q4 (preserving the operator's curated icon across restarts is the entire point of US3). The "safe default" property is preserved because `MOVE_ONLY` is still the install-time default. ADR-0010 D5 already records this change.

**API additions on `PreferenceStore`**:

- `MarkerMode getGotoMarkerMode()` / `void setGotoMarkerMode(MarkerMode)`
- `String getGotoLastIconsetPath()` / `void setGotoLastIconsetPath(String)` / `void clearGotoLastIconsetPath()`

### R10 — Off-main-thread discipline

**Decision**: All `UserIconDatabase` calls execute on a dedicated `ExecutorService` (Java `Executors.newFixedThreadPool(2)`). Results are dispatched back to the main thread via `View.post(...)` or `Handler(Looper.getMainLooper()).post(...)`.

**Why**: Constitution Principle IV (60 fps frame budget = 16 ms per frame). Any synchronous SQLite query + bitmap decode that exceeds 16 ms causes a jank frame. Empirically a single `getIconBitmap(int)` is sub-ms on the reference device, but on a 500-icon `getIconSets(true, false)` walk the worker thread is mandatory.

**Lifecycle**: the executor is owned by `TwCoordGotoView`, instantiated on first `bind(...)` call after CUSTOM_ICON is selected, and `shutdown()`-ed on `TwCoordGotoReceiver.onDropDownClose()`. A 2-thread pool is sufficient (one for iconset enumeration, one for bitmap fetch); we don't need a per-cell thread.

**Bitmap cache**: `androidx.collection.LruCache<Integer, Bitmap>` sized at 16 MB (or `Runtime.maxMemory() / 16`, whichever is smaller). Cache survives across picker open/close within a session but is cleared on `onDropDownClose()` to free memory.

### R11 — Locale handling for new strings

**Decision**: All visible strings flow through `TwCoordGotoView.localisedContext` (the `LocaleOverride.contextFor(...)`-wrapped context the existing code already uses). New strings are added to `R.string` and read via `localisedContext.getString(R.string.goto_mode_custom_icon)` etc.

**Why**: FR-013 inherits feature 002's locale-override pathway. The existing `refreshLocalisedStrings()` method in `TwCoordGotoView` is the centralised re-binding point; new view IDs get a line each in that method.

**Translation source**: the existing `values-zh-rTW/strings.xml` and `values-ja/strings.xml` plus the canonical `values/strings.xml`. Translations follow the same `zhtw-mcp-clean` discipline established in feature 001 (proofread by the user's Traditional-Chinese MCP).

### R12 — Constitution VI compliance audit

**Decision**: Every new host-callable entry point introduced by this feature MUST be wrapped in `try`/`catch (Throwable)` at its outer scope, logging via `com.atakmap.coremap.log.Log.w` and returning without re-throw.

**New entry points introduced by this feature** (each MUST have a wrap):

1. `RadioButton.setOnClickListener` for the 9th radio (`modeCustomIcon`) — calls `setMarkerMode(MarkerMode.CUSTOM_ICON)`.
2. The picker preview's tap listener — opens the picker dialog.
3. The picker dialog's iconset-list `ListView.OnItemClickListener` — transitions to step 2.
4. The picker dialog's icon-grid `GridView.OnItemClickListener` — commits the pick.
5. The picker dialog's back-button `OnClickListener` — returns to step 1.
6. The picker dialog's `OnCancelListener` (system-back / outside-tap) — treats as cancel.
7. The `ICONSET_ADDED` / `ICONSET_REMOVED` `BroadcastReceiver.onReceive` callback.
8. The `ExecutorService` task bodies (any uncaught exception inside `Runnable.run()` becomes a silent worker death; wrap to log).
9. The bitmap-cache `LruCache.sizeOf` / `entryRemoved` overrides (called by AndroidX on background threads).

The `submitOk` path already has Constitution VI coverage (feature 002); only the new `.setIconPath(...)` call needs to live inside that existing try/catch.

**Audit hook**: tasks.md will include an explicit "Constitution VI guard pass" step in the Polish phase, plus the `/speckit-analyze` gate will fail if any of the above is missing a wrap.

### R13 — Iconset/icon ordering inside the picker

**Decision**: Iconsets are listed alphabetically by `UserIconSet.getName()`, case-insensitive. Icons within an iconset are listed alphabetically by `UserIcon.getFileName()` after the icon's filename extension is stripped, case-insensitive. Group dimension is flattened: every icon in the iconset appears in one scrollable grid regardless of `getGroup()` (matches the spec's "no more than two levels" edge case).

**Why**: Spec did not constrain ordering, but stable alphabetical ordering matches the operator's mental model from file managers and from ATAK's own pallet UIs. `UserIconSet.getName()` is already populated and stable across sessions; `getFileName()` is the canonical icon identifier (the same string that participates in the iconset path).

**Alternative considered**: `UserIconDatabase.getMostUsedIcon(String iconsetUid)` exposes a use-count field, so a "Recently used" section could lead the icon list. Rejected for v1 — adds adapter complexity and the operator's first-time discovery story benefits more from predictable alphabetical browsing than from MRU.

### R14 — Out-of-scope / explicitly deferred

The following SDK surfaces were inspected and deliberately not used. Documented here so a future reader doesn't redo the analysis:

- **`UserIconPalletFragment.newInstance(UserIconSet)`** — single-iconset fragment with its own marker-placement path. Rejected per ADR-0010 D3 (DropDownReceiver root is not a `FragmentActivity` host; the fragment also places the marker itself, colliding with our `submitOk` path).
- **`IconsetAdapterBase`** — abstract `BaseAdapter` base for icon grids. Considered for the picker step-2 grid; rejected because our minimal `BaseAdapter` is ~30 LoC and `IconsetAdapterBase`'s view-holder has styling opinions (item background, padding, selection drawable) that would need overriding anyway.
- **`EnterLocationDropDownReceiver.processPoint(...)`** — placing markers via the host's existing enter-location flow. Rejected per ADR-0010 alternative D (re-opens ATAK's own drop-down after our Submit; splits the GoTo UX across two visible drop-downs).
- **`UserIconDatabase.addIconSet(...)` / `addIcon(...)`** — write path. The plugin is strictly read-only against the icon database (FR-004); operators add iconsets through ATAK's own iconset manager.
- **`Icon2525cPallet`, `SpotMapPallet`** — special-cased palette types. Out of scope; the picker enumerates `UserIconSet` rows only, not 2525c or spot map types.

## Anchoring discipline

Per memory `feedback-plan-phase-code-anchoring`, every SDK claim in this document cites both:

- a **`javap -public`** line against `ATAK-CIV-5.7.0.3-SDK/main.jar` — proof the API exists in the pinned 5.7.0.3 contract the plugin compiles against, **and**
- a **permalink** into [`TAK-Product-Center/atak-civ`](https://github.com/TAK-Product-Center/atak-civ) `main` — for cross-checking method bodies (private helpers, control flow, edge-case handling) that `javap` cannot show.

If the two ever disagree, the SDK jar wins — the plugin compiles against it, not against upstream `main`. The upstream link is documentation/cross-check only.

## Open Items (none)

All technical unknowns from plan.md's Technical Context are resolved. No `[NEEDS CLARIFICATION]` markers remain.
