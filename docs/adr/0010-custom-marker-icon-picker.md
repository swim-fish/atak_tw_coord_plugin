# ADR-0010: Custom marker icon picker on the GoTo page — reuse ATAK's `UserIconDatabase`, no plugin-owned iconset

**Status**: Proposed (SDK reconnaissance complete; implementation pending operator approval)
**Date**: 2026-05-17
**Origin**: Operator request after `c5d9d40` — *"can the marker dropped via GoTo be a custom icon (e.g. Google-style pins)?"* — combined with the constraint *"使用系統舊有的機制 不要自己開發多餘的東西 優先使用舊的功能"* (reuse the system's existing mechanism; don't build superfluous things; prefer existing functionality).

## Context

`c5d9d40` shipped an 8-radio marker-mode picker on the GoTo input page (`MarkerMode.java:15`, `TwCoordGotoView.java:758-769`). Each radio is bound to a CoT type code (`b-m-p-s-m`, `a-f-G`, `a-h-G`, …) and the Submit path places the marker through `PlacePointTool.MarkerCreator.setType(cotType).placePoint()`. ATAK derives the on-map icon from the CoT type, which means today's picker covers only the standard MIL-STD-2525 affiliations plus a handful of Spot Map / GoTo Pin variants.

The operator wants the option to drop a **custom** icon at the resolved coordinate — e.g. a Google-style pin, a wildfire incident glyph, a responder marker — without giving up the CoT-type semantics. Two questions had to be answered before designing the UI:

1. **Does ATAK already expose a public mechanism for custom icons?** If yes, the plugin should consume it; if no, this ADR would have to justify building a parallel system.
2. **Which existing iconsets are guaranteed to be on the operator's device?** Determines whether we need to bundle anything at all.

A pre-implementation SDK reconnaissance pass against `ATAK-CIV-5.7.0.3-SDK/main.jar` answered both. This ADR records what was found, so that the next ADR (which will cover the implementation pivots) does not have to re-litigate why we chose not to build our own icon framework.

## SDK reconnaissance — what ATAK already provides

All findings below were verified via `javap -public` against `main.jar` and `grep`-confirmed against the `helloworld` sample at `ATAK-CIV-5.7.0.3-SDK/samples/helloworld/.../HelloWorldDropDownReceiver.java`. Each cited class is additionally linked under [Links](#links) to its `.java` source in the active upstream mirror — `github.com/TAK-Product-Center/atak-civ` — so a future reader can cross-check method bodies (which `javap -p` only stubs) against the as-shipped implementation. The SDK jar remains the build-time contract; the upstream repo is documentation/cross-check only.

### R1 — Iconset database

`com.atakmap.android.icons.UserIconDatabase` is a singleton initialised at ATAK startup by `IconsMapAdapter.initializeUserIconDB(ctx)`. It exposes:

- `UserIconDatabase.instance(Context)` — singleton accessor.
- `getIconSets(boolean withIcons, boolean withBitmaps)` — every loaded iconset.
- `getIconSet(String uid, boolean, boolean)` / `getIconSetByName(String, boolean, boolean)` — direct lookup.
- `getIcon(String iconsetUid, String iconName, boolean)` / `getIcon(int id, boolean)` — direct icon lookup.
- `getIconBitmap(int id)` / `getIconBytes(int id)` — render-ready Bitmap / byte[].
- `addIconSet`, `removeIconSet`, `addIcon` — write path (we do **not** use these; the operator manages iconsets through ATAK's own iconset manager).
- `getMostUsedIcon(String iconsetUid)` — useful for a "Recent icon" affordance if we ever want one.

### R2 — Iconset / Icon data model

`com.atakmap.android.icons.UserIconSet`:

- `getUid()`, `getName()`, `getVersion()`.
- `getGroups()` → `List<String>` of in-set group folders.
- `getIcons(String group)` → `List<UserIcon>` for one group; `getIcons()` for all.
- `getIcon(String name)` / `getIconBestMatch(String)` — name lookup.
- `getDefaultFriendly()` / `Hostile()` / `Neutral()` / `Unknown()` — affiliation fallbacks (the iconset author can pre-bind defaults).

`com.atakmap.android.icons.UserIcon`:

- `getIconsetUid()`, `getFileName()`, `getGroup()`, `get2525cType()`, `getBitMap()`.
- **`public static final String IconsetPath = "iconsetpath"`** — the meta-data key used everywhere in ATAK to bind a marker to an icon. This is the canonical contract.
- `static String GetIconsetPath(String iconsetUid, String group, String name)` — builds the canonical `<uid>/<group>/<name>` path string.
- `static UserIcon GetIconFromIconsetPath(String, boolean, Context)` — resolves a path back to a `UserIcon`.
- `static Bitmap GetIconBitmap(String iconsetPath, Context)` — resolves a path to a Bitmap (we use this in the picker preview thumbnail).

### R3 — Placement APIs

Two public, equivalent paths exist:

- **At placement time:** `PlacePointTool.MarkerCreator.setIconPath(String)` on the same builder we already call `setType` / `setUid` / `setCallsign` / `placePoint` on (`MarkerCreator` is `com.atakmap.android.user.PlacePointTool$MarkerCreator`).
- **After placement:** `marker.setMetaString(UserIcon.IconsetPath, "<uid>/<group>/<name>.png")` followed by `marker.refresh(mapView.getMapEventDispatcher(), null, ClassRef)`. This is what `helloworld`'s `createAircraftWithRotation()` does at line 2958 to apply `34ae1613-9645-4222-a9d2-e5f243dea2865/Military/A10.png` to an `a-f-A` marker.

The `setIconPath(...)` builder method is preferred because it composes cleanly with the existing `MarkerCreator` chain we already use in `TwCoordGotoView.submitOk()` and does not require a second `refresh()` call.

### R4 — Bundled iconsets (zero ship-side dependency)

`atak.apk` ships the following iconsets, automatically inflated into `iconsets.sqlite` on first launch:

| Asset path inside `atak.apk` | Content |
|---|---|
| `assets/iconsets/iconset_falconview.zip` | FalconView military symbology |
| `assets/iconsets/iconset_incident_management.zip` | Fire / police / EMS icons |
| `assets/iconsets/iconset_ps_air.zip` | Public-safety air assets |
| `assets/iconsets/iconset_responder.zip` | First-responder pins (the closest to "Google-style") |
| `assets/iconsets/iconset_wildfire.zip` | NWCG wildfire-incident symbols |
| `assets/dbs/iconsets.sqlite` (seed DB) | The "Military" iconset referenced by `helloworld` (UID `34ae1613-9645-4222-a9d2-e5f243dea2865`) |

That is at least 5 ready-to-use iconsets on every install, plus the seed Military set. Operators can also self-install additional `.zip` iconsets via ATAK's settings → iconset manager; those become visible through `UserIconDatabase.getIconSets(...)` as well, with no extra work on our side.

### R5 — Existing UI building blocks (considered but not used)

- `com.atakmap.android.user.icon.UserIconPalletFragment` — `Fragment` rendering the icon grid for one `UserIconSet`. `newInstance(UserIconSet)` + `getPointPlacedIntent(GeoPointMetaData, String)` returning `Marker`. Reusable inside a `FragmentActivity`.
- `com.atakmap.android.user.icon.IconsetAdapterBase` — abstract `BaseAdapter` over `List<UserIcon>` with `getOnItemClickListener()`. Useful as a base class if we render the grid ourselves.
- `com.atakmap.android.user.EnterLocationDropDownReceiver` — `getInstance(MapView).processPoint(GeoPointMetaData)` drops a marker using whichever pallet the operator last had open. Singleton with `START` action and `setPallet(String uid)` / `addPallet(IconPallet, int)`.

## Decisions

### D1 — Source icons exclusively from `UserIconDatabase`; bundle nothing

The plugin ships **zero** image assets for the icon picker. Every icon offered to the operator is read at runtime from `UserIconDatabase.instance(pluginContext).getIconSets(true, true)`.

Rationale:

- ATAK already ships five iconsets out of the box (R4), which covers the operator's stated "Google-style pin" use case via `iconset_responder`.
- Operators can already load custom `.zip` iconsets through ATAK's iconset manager; those automatically appear in our picker with no plugin code change.
- Bundling our own iconset would duplicate state (two SQLite rows / two refresh cycles when the operator removes ours through the iconset manager) and forces us to own a registration/de-registration lifecycle that ATAK already manages.
- Constitution Principle VI (host-process isolation): the fewer write paths into ATAK's databases the plugin owns, the smaller the blast radius if our code misbehaves.
- The user's directive — *"使用系統舊有的機制 不要自己開發多餘的東西"* — was explicit and load-bearing.

### D2 — Apply the icon via `MarkerCreator.setIconPath(...)`, not via post-placement metadata

The Submit path will call:

```
new PlacePointTool.MarkerCreator(dest)
    .setUid(UUID.randomUUID().toString())
    .setType(cotType)            // e.g. "b-m-p-s-m"
    .setCallsign(callsign)
    .setIconPath(iconsetPath)    // NEW — canonical "<uid>/<group>/<name>" string
    .placePoint();
```

Rationale:

- One builder chain; no second `refresh()` step (R3).
- `setIconPath` is the documented `MarkerCreator` API. The `setMetaString(UserIcon.IconsetPath, ...)` route used by `helloworld` works but is appropriate for post-placement mutation (rotation, color change), not initial placement.
- Keeps the existing minimalist-builder pattern that the helloworld sample audit (recorded in `c5d9d40`'s commit message) validated as the "user-placed marker" idiom.

### D3 — Render a thin in-page picker rather than embed `UserIconPalletFragment`

The picker is built in `tw_coord_goto.xml` as an `AlertDialog` with a `Spinner` (iconset chooser) plus a `GridView` (icons in the selected iconset). The adapter either extends `IconsetAdapterBase` (R5) or, if `IconsetAdapterBase`'s view-holder is too opinionated about styling, a 30-line plain `BaseAdapter` reading `UserIconDatabase.getIconBitmap(...)`.

Rationale:

- `TwCoordGotoView` is a raw `View` controller, not a `FragmentActivity` host. Embedding `UserIconPalletFragment` requires inflating a `FragmentContainerView`, acquiring the host `FragmentManager`, and managing fragment lifecycle inside a `DropDownReceiver` — each of those is "新增的多餘東西" the operator explicitly asked us to avoid.
- `UserIconPalletFragment.newInstance(UserIconSet)` consumes exactly one iconset. Supporting all installed iconsets through that fragment would require either multiple fragment instances or a separate iconset chooser anyway, defeating the reuse argument.
- `UserIconPalletFragment.getPointPlacedIntent(...)` places the marker itself, which would collide with our existing `MarkerCreator` placement path and the persist/toast/close sequence in `submitOk()`. Reusing the data APIs without the fragment lets us keep the Submit path single-source-of-truth.
- The data-layer APIs (`UserIconDatabase`, `UserIconSet`, `UserIcon`, `MarkerCreator.setIconPath`) are the actual "system mechanism" — using them is reuse; rendering a thin Spinner+Grid that consumes them is not.

### D4 — `MarkerMode.CUSTOM_ICON` is additive; the existing 8 modes are untouched

A 9th enum constant `CUSTOM_ICON` is added to `MarkerMode.java`. Its `cotType()` is `"b-m-p-s-m"` (Spot Map — the same generic user-placed type as `WAYPOINT`), and it carries an additional `iconsetPath()` getter populated from the picker selection. The other 8 modes keep working unchanged: they continue to set only `type`, letting ATAK pick the MIL-STD-2525 frame icon.

Rationale:

- The existing 8 modes are the fast path for ATAK-native operators who already think in CoT affiliations. Removing or hiding them would be a regression.
- CoT type and icon are orthogonal in ATAK's data model: a marker can have type `a-f-G` and an iconset-overridden icon at the same time. We deliberately bind `CUSTOM_ICON` to `b-m-p-s-m` so the operator's selection is clearly "this is a user-placed pin with a custom face", not "this is a friendly ground unit with a non-standard symbol" (which would be misleading to anyone reviewing the CoT downstream).

### D5 — Persist `(markerMode, iconsetPath)` across sessions

Two new `PreferenceStore` keys:

- `pref_goto_marker_mode` — enum name; default `MOVE_ONLY` (matches the in-session reset behaviour today).
- `pref_goto_last_iconset_path` — last picked `<uid>/<group>/<name>` string; null until the operator picks one.

Why we **change** the session-reset behaviour for marker mode: `c5d9d40`'s decision to reset to `MOVE_ONLY` every session was defensive (avoid surprise marker drops). Now that the operator may have curated a specific iconset+icon, throwing away their choice every restart is friction. Persisting also lets the picker pre-select the operator's last icon when re-opened — the standard "remember my choice" UX. The defensive concern is addressed by keeping `MOVE_ONLY` as the install-time default; operators who never touch the radio still get no surprise markers.

### D6 — Graceful degradation when the picked iconset disappears

If `pref_goto_last_iconset_path` resolves to a `UserIcon` that no longer exists (operator deleted the iconset between sessions), the page silently falls back to `MOVE_ONLY` and clears the stale preference. No error toast; the picker dialog explains the situation on next open with an empty-state row ("Selected icon no longer installed. Pick again.").

Rationale: Constitution Principle VI — we cannot crash on ATAK state we don't own. Iconsets are operator-managed; the plugin is a guest.

## Alternatives considered

- **A. Bundle our own iconset (`assets/iconsets/twcoord.zip` + `UserIconDatabase.addIconSet(...)` on plugin start).** Rejected — duplicates state the operator already manages, forces us to own version/upgrade logic, and explicitly contradicts the operator's *"不要自己開發多餘的東西"* directive. The 5 bundled iconsets plus operator self-loads already cover the use case.
- **B. Embed `UserIconPalletFragment` as a sub-fragment of the GoTo page.** Rejected for the lifecycle/host reasons under D3. We use its underlying data APIs instead.
- **C. Place the marker with the standard `MarkerCreator` chain, then post-mutate it via `marker.setMetaString(UserIcon.IconsetPath, ...).refresh(...)`** (the `helloworld:2958` pattern). Rejected in favour of `setIconPath(...)` at placement time (D2) — one builder, no second refresh.
- **D. Delegate the whole drop to `EnterLocationDropDownReceiver.getInstance(mapView).processPoint(geoPointMeta)` after pan.** Rejected because it re-opens ATAK's standard enter-location pane immediately after our Submit, which (i) splits the GoTo flow across two visible drop-downs and (ii) loses our toast / persist / close / CoT-broadcast sequencing. The data-layer reuse (D1) gives us the same icon coverage without taking over the operator's screen.
- **E. Skip the picker — just hard-code a single "responder" pin.** Rejected because the operator asked for choice ("可以使用 Google 圖標") and the cost of a small Spinner+Grid is low.
- **F. Keep the picker but bundle a small set of Material-Design icons as a fallback for devices that somehow have no iconsets installed.** Rejected — `iconsets.sqlite` is part of `atak.apk` itself; if it's missing, ATAK is broken and our plugin has bigger problems than a missing icon list.

## Consequences

**Positive:**

- Zero plugin-owned image assets; zero plugin-owned iconset lifecycle. The Submit path gains one `setIconPath(...)` call and the page gains one Spinner+Grid dialog.
- Operators inherit any custom iconsets they have already loaded through ATAK's iconset manager. The plugin's icon coverage grows automatically as the operator's environment grows.
- Maintenance is bounded by the public `UserIconDatabase` / `UserIcon` / `MarkerCreator.setIconPath` API surface — these are stable contracts ATAK's own enter-location flow uses every day.
- The existing 8 marker modes keep working unchanged; the new `CUSTOM_ICON` mode is purely additive (D4).

**Negative:**

- The picker's behaviour depends on what the operator has installed. We cannot guarantee "Google-style pin X is present on every device" — but R4 shows the bundled `iconset_responder` covers that use case on every install.
- We change the session-reset policy for `markerMode` (D5). Operators who relied on the implicit "every restart goes back to Move" safety net lose that, in exchange for "remember my pick". The fast-cancel path (the `MOVE_ONLY` radio is the leftmost option) keeps the recovery cost to one tap.
- Adding `pref_goto_marker_mode` + `pref_goto_last_iconset_path` increases the SharedPreferences surface by two keys. Acceptable; consistent with the seven `pref_goto_*` keys already in use.

## Links

- **SDK classes audited** (all signatures via `javap -public` on `ATAK-CIV-5.7.0.3-SDK/main.jar`; upstream-source permalinks point at `TAK-Product-Center/atak-civ` `main` for cross-checking implementation bodies — see the disclaimer in [SDK reconnaissance](#sdk-reconnaissance--what-atak-already-provides) about SDK-jar vs upstream-source authority):
  - `com.atakmap.android.icons.UserIconDatabase` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIconDatabase.java)
  - `com.atakmap.android.icons.UserIconSet` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIconSet.java)
  - `com.atakmap.android.icons.UserIcon` (notably `IconsetPath`, `GetIconsetPath`, `GetIconBitmap`) — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/UserIcon.java)
  - `com.atakmap.android.icons.IconsMapAdapter` (`ADD_ICONSET` / `REMOVE_ICONSET` broadcast contract) — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/icons/IconsMapAdapter.java)
  - `com.atakmap.android.user.PlacePointTool$MarkerCreator` (`setIconPath`, `setType`, `setUid`, `setCallsign`, `placePoint`) — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/user/PlacePointTool.java)
  - `com.atakmap.android.user.icon.UserIconPallet` / `UserIconPalletFragment` / `IconPallet` / `IconsetAdapterBase` — [upstream package listing](https://github.com/TAK-Product-Center/atak-civ/tree/main/atak/ATAK/app/src/main/java/com/atakmap/android/user/icon)
  - `com.atakmap.android.user.EnterLocationDropDownReceiver` — [upstream source](https://github.com/TAK-Product-Center/atak-civ/blob/main/atak/ATAK/app/src/main/java/com/atakmap/android/user/EnterLocationDropDownReceiver.java)
- **Reference implementation in the SDK**: `samples/helloworld/.../HelloWorldDropDownReceiver.java:2958` — the canonical `UserIcon.IconsetPath` usage pattern that informed D2.
- **Bundled iconset zip list**: `atak.apk` `assets/iconsets/*.zip` (R4).
- **Prior ADRs**: ADR-0009 (the GoTo page itself; this ADR extends the marker-mode picker added in `c5d9d40`), ADR-0007 (same "javap the SDK before deciding" discipline applied here).
- **Constitution principles invoked**: I (formatter — no impact, pure additive), III (UX consistency — reuse ATAK's iconset model so operator's existing iconset knowledge transfers), VI (host-process isolation — D6 fallback policy; D1 no-write-path posture), V (this ADR documents the pre-implementation reconnaissance per the SDK-investigation requirement).
- **Operator directive verbatim**: *"走 3 但是 iconset 使用系統舊有的機制 不要自己開發多餘的東西 優先使用舊的功能 先看 SDK 如何使用 iconset 再決定"* (2026-05-17 chat).
- **Upstream URL update**: as of 2026-05-17 the active ATAK-CIV public source mirror is `github.com/TAK-Product-Center/atak-civ` (default branch `main`). The previously-referenced `deptofdefense/AndroidTacticalAssaultKit-CIV` mirror is stale. This ADR's permalinks pin to `main` rather than to a tag because the SDK jar (5.7.0.3) is the authoritative build-time contract; the upstream link is for human cross-checking only.
