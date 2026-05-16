# Quickstart — Taiwan Coordinate Input ("GoTo") Page

**Feature**: 002-tw-coord-goto | **Audience**: developers extending or
verifying the input page after `/speckit-implement` lands

This document covers the **dev / test / install loop** that is
specific to this feature; the global plugin build instructions live in
the repo `README.md` and the feature-001
[`quickstart.md`](../001-tw-coord-display/quickstart.md). Anything you
need beyond what is here is reused from feature 001 unchanged.

---

## Prerequisites

You should already have the feature-001 environment running:

- JDK 17 on `JAVA_HOME` (Android Gradle 8.13 requirement).
- ATAK-CIV 5.7.0.3 SDK unpacked at
  `C:\Users\hhhnr\source\tak\ATAK-CIV-5.7.0.3-SDK`.
- `local.properties` containing both `sdk.dir=...` and
  `sdk.path=C:/Users/hhhnr/source/tak/ATAK-CIV-5.7.0.3-SDK`.
- A Galaxy Tab S10+ (or any ATAK-CIV 5.7.0.3-compatible device) on
  USB with `adb devices` listing it.
- ATAK installed on the device. The plugin shares its
  `applicationId` (`com.atakmap.android.twcoord.plugin`) with feature
  001, so installing this branch replaces the feature-001 build.

If you have **not** built feature 001 on this device yet, do so first
— this feature reuses its `PreferenceStore` keys and won't initialise
cleanly without those keys having defaults written.

---

## Build & install

From repo root:

```powershell
# Format-and-build (Constitution Principle I)
./gradlew spotlessApply
./gradlew assembleCivDebug

# Push to the connected device
adb install -r app/build/outputs/apk/civ/debug/ATAK-Plugin-atak_tw_coord_plugin-*-civ-debug.apk
```

ATAK will detect the new plugin version on its next launch. Open
**ATAK ⇒ Settings ⇒ Plugins** and confirm the version line shows
`1.0.0 (...) - [5.4.0]` with this feature's commits in the changelog.

---

## Open the input page

Two entry points (FR-016):

1. **Tools menu icon** — Tap the ATAK Tools button, scroll the icon
   list, and tap the **TW Coord GoTo** icon (pin / target glyph,
   visually distinct from the existing unit-cycle icon). The
   DropDown side-pane slides in from the right.
2. **Settings page button** — Open **Settings ⇒ Specific ⇒ TW
   Coordinates**, scroll to the bottom, tap **Open Coordinate Input**.
   Same DropDown opens.

On first open the page defaults to the **Taipower** tab with an
empty field. Subsequent opens restore the last used tab and last
submitted value (per FR-003 / FR-014).

---

## Smoke-test the three units

Use these golden values from `test-data/taiwan_cities_coords.csv`.
The map MUST pan and a single marker MUST appear at Taipei 101 in
each case.

| Tab | Field input | Expected map result |
|---|---|---|
| Taipower | `H7509 DB4016` | Marker at Hualien Station within 5 m |
| TWD97 | E `302912`, N `2770905`, zone `121` | Marker within 0.5 m of Taipei 101 |
| TWD67 | E `302130`, N `2771143`, zone `121` | Marker within 5 m |

Outer-island check (US2 acceptance 2):

| Tab | Input | Expected |
|---|---|---|
| TWD97 | E `297540`, N `2596218`, zone **119** | Marker on Penghu's main island; confirmation toast names `zone 119`. |
| Taipower | `H7509 DB4016` then re-open and try Penghu Taipower (none provided — the field accepts any 9/11-char code but resolves to OUT_OF_RANGE for outer-island longitudes) | Inline error: "Taipower does not cover outer islands". |

---

## Smoke-test Auto Fill

1. Centre the map roughly on Taipei 101 (use the readout widget to
   confirm).
2. Open the input page. Confirm the **Auto Fill** button (right of the
   input field on whichever tab is active) is **enabled**.
3. Tap Auto Fill. The input field MUST fill with the Taipei 101
   value for the active tab; the zone toggle (TWD97/TWD67 tabs) MUST
   read `121`.
4. Pan the map to Penghu (longitude < 120°). The Auto Fill button
   stays enabled on the TWD97 / TWD67 tabs — re-tap and confirm the
   zone toggle flips to `119` (FR-023).
5. Switch to the Taipower tab. Auto Fill MUST go **disabled** within
   a frame (the map centre is on Penghu; Taipower mode is main-island
   only). Long-press the disabled button — a tooltip / hint string
   MUST read "Taipower does not cover outer islands".
6. Pan back to Taipei. Auto Fill MUST re-enable within a frame.
7. Pan well outside Taiwan (e.g. Tokyo). Auto Fill MUST go disabled
   on **every** tab.

---

## Verify Recent entries

After 3+ successful submits across mixed units:

1. Re-open the input page.
2. Scroll to the **Recent** section at the bottom of the DropDown.
3. Confirm the entries are ordered **newest-first**, each labelled
   with its unit (`TAIPOWER` / `TWD97` / `TWD67`) and the original
   input string.
4. Tap any row. The corresponding unit tab activates and the input
   field populates. Submit acts on that value.
5. Tap the row's delete (`✕`) glyph. Row disappears immediately.
6. Cold-restart ATAK. Confirm the Recent list persists across the
   restart (FR-014). After 11 submits, confirm only the most recent
   10 remain (R10 / FIFO).

---

## Verify localisation (FR-013, SC-006)

1. Open **Settings ⇒ TW Coordinates ⇒ Language** and toggle through
   `Use system`, `English`, `中文（正體）`, `日本語`.
2. After each switch, open the input page and confirm every visible
   string (tab labels, field hints, error messages, accuracy
   advisory, Auto Fill tooltip, Submit button) re-renders in the
   chosen language.
3. Run the `zhtw-mcp` lint over `app/src/main/res/values-zh-rTW/strings.xml`:

    ```powershell
    # Invoked via the mcp__zhtw-mcp tool in the dev environment.
    # Goal: 0 errors / 0 warnings on this feature's new entries.
    ```

---

## Running the JVM test suite

The contract tests in
`app/src/test/java/com/atakmap/android/twcoord/gotopage/` are pure-JVM and
run without a device:

```powershell
./gradlew :app:testCivDebugUnitTest --tests "com.atakmap.android.twcoord.gotopage.*"
```

Targets:
- `TaipowerParserTest` — normalisation + length / letter rejection.
- `TwdTm2ParserTest` — easting / northing / zone validation.
- `CoordinateParserRoundTripTest` — 22-city round-trip; pinned
  tolerances (TWD97 ≤ 0.5 m, TWD67 main ≤ 5 m, outer ≤ 20 m).
- `MapCenterAutoFillStreamTest` — fake `MapEvent` source, asserts
  debounce + per-unit `*Ok` flag transitions.
- `RecentEntryStoreTest` — dedup + FIFO + JSON round-trip.

All MUST be green before any submit to a branch. Feature 001's
existing tests MUST also stay green (they are not touched by this
feature, but the build runs them on every CI invocation).

---

## Running the instrumented test suite

DropDown / receiver lifecycle and Auto Fill propagation can only be
verified on a device:

```powershell
./gradlew :app:connectedCivDebugAndroidTest --tests "com.atakmap.android.twcoord.gotopage.*"
```

The instrumented suite asserts the spec acceptance scenarios for
US1 / US2 / US3 / US5 by driving the page directly. Manual on-device
verification is still required for US4 (Recent list visual ordering)
and for SC-005 (60-second discovery time by an uninstructed
operator); both are documented in the acceptance log
(`docs/acceptance/002-tw-coord-goto.md`, authored after
`/speckit-implement`).

---

## Common pitfalls

- **The Auto Fill button never enables.** You are likely missing the
  `MapCenterAutoFillStream` registration; check that
  `TwCoordMapComponent.onCreate` calls `attachMapCenterStream()` and
  unwinds it in `onDestroy`.
- **Submit pans but does not drop a marker.** Confirm
  `mapView.getRootGroup().addItem(marker)` is called *after*
  `marker.setRemovable(true)`; ATAK's add-item path validates the
  flag and silently drops the marker if it sees state changes after
  attach. Reuse the order shown in `contracts/goto-receiver.md` §3b.
- **Recent list is empty after a fresh install.** The
  `pref_goto_recent_json` key defaults to `"[]"`; an empty array is
  the expected first-launch state.
- **ATAK shows "Plugin incompatible"**. The plugin declares
  compatibility against `com.atakmap.app@5.4.0.CIV`; the device's
  ATAK must be 5.4.0 or newer. See ADR-0007.
