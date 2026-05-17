# Quickstart: Custom Marker Icon on the GoTo Page

**Feature**: `003-custom-marker-icon`
**Audience**: developers running the plugin on a device for the first time after this feature merges; reviewers doing acceptance testing; future-you debugging the picker.

This document walks the four acceptance flows from [spec.md](./spec.md) on a real device. Reference device: Galaxy Tab S10+ running ATAK-CIV 5.7.0.3 (the same device feature 002 was validated on).

## 0. Prerequisites

Same as feature 002's quickstart, plus:

- ATAK-CIV has its default iconsets loaded. This is the out-of-box state on any first install — no extra setup needed. To verify: open ATAK → main menu → `Settings → Display Preferences → Icon Sets`. You should see at least: `Falconview`, `Incident Management`, `Public Safety - Air`, `Responder`, `Wildfire`, and `Military` (seed).

## 1. Build and install

From the repo root:

```text
./gradlew :app:assembleCivDebug
adb install -r app/build/outputs/apk/civ/debug/ATAK-Plugin-atak_tw_coord_plugin-*-civ-debug.apk
adb shell am force-stop com.atakmap.app.civ
adb shell am start -n com.atakmap.app.civ/.MainActivity
```

When ATAK comes up, accept the plugin load prompt (`Settings → Manage Plugins → TW Coordinates`).

## 2. Acceptance Flow A — US1: pick + drop (P1 happy path)

1. Open the **Tools** menu → tap **TW Coord GoTo**.
2. The GoTo page opens at the **Taipower** tab.
3. Type a known coordinate, e.g. `H7509 DB4016` (Hualien Station, the canonical test point).
4. Wait for inline validation (≤ 100 ms) to clear; **Submit** lights up.
5. In the marker-mode area, tap **Custom Icon** (the 9th option, rightmost on the second row).
   - **Expected**: Submit goes back to disabled; a picker preview row appears below the radios showing "Pick an icon" with a tappable affordance.
6. Tap the "Pick an icon" preview.
   - **Expected**: dialog opens at step 1 within ≤ 300 ms (SC-002); shows the iconset list alphabetically.
7. Tap an iconset, e.g. **Responder**.
   - **Expected**: dialog transitions to step 2 (icon grid for Responder) within ≤ 500 ms (SC-003); thumbnails render progressively.
8. Tap an icon, e.g. `fire_truck.png`.
   - **Expected**: dialog closes; preview now shows a ~32 dp fire-truck thumbnail with "Responder" underneath; Submit re-enables within one UI frame (SC-004).
9. Tap **Submit**.
   - **Expected**: page closes; map pans to Hualien Station (X/Y only — zoom unchanged); a marker showing the picked fire-truck icon appears at the resolved coordinate; confirmation toast shows the unit+lat/lon.
10. Long-press the dropped marker.
    - **Expected**: ATAK's standard marker radial menu opens (edit / delete / route / details / …). Marker behaves identically to one placed via ATAK's own marker tools.

## 3. Acceptance Flow B — US2: validation gate (P1 correctness)

1. Open the GoTo page; type a valid coordinate; ensure marker mode is **Move only** → Submit is enabled.
2. Switch to **Custom Icon** (with no previously-picked icon).
   - **Expected**: Submit becomes disabled; preview shows "Pick an icon".
3. Tap the preview, then dismiss the picker via system back (do not pick anything).
   - **Expected**: preview still shows "Pick an icon"; Submit stays disabled.
4. Tap preview → pick an iconset → pick an icon.
   - **Expected**: Submit enables.
5. Edit the coordinate to invalidate it (e.g. delete the last digit).
   - **Expected**: Submit disables (coordinate validity dominates per US2.AC3).
6. Restore the coordinate.
   - **Expected**: Submit re-enables — the previously-picked icon is preserved across the brief invalidation.

## 4. Acceptance Flow C — US3: cross-restart persistence (P2)

1. Complete Flow A through step 8 (icon picked, no Submit yet).
2. Tap the page's outer close (drop-down chevron) without submitting.
3. Re-open the GoTo page.
   - **Expected**: marker mode is still **Custom Icon**; preview shows the same fire-truck thumbnail + "Responder".
4. Force-stop ATAK: `adb shell am force-stop com.atakmap.app.civ`.
5. Re-launch ATAK and open the GoTo page.
   - **Expected**: marker mode is still **Custom Icon**; preview still shows the fire-truck + "Responder" (SC-005 — 0 additional taps).
6. Tap the preview to open the picker.
   - **Expected**: dialog opens at **step 2** of the **Responder** iconset, with the fire-truck visually highlighted (FR-003 clarification Q1).
7. Tap the dialog's back button.
   - **Expected**: transitions to step 1 (iconset list); the previously-selected iconset is no longer special-cased on this step.

## 5. Acceptance Flow D — US4: graceful fallback (P3)

1. Complete Flow A through step 8 (Custom Icon selected, Responder/fire_truck picked).
2. Tap the page's outer close (no submit needed for this flow).
3. In ATAK's settings → `Display Preferences → Icon Sets`, remove the **Responder** iconset.
   - **Expected**: `ICONSET_REMOVED` broadcast fires.
4. Re-open the GoTo page.
   - **Expected**: marker mode is **Move only** (not Custom Icon); persistence cleared (`pref_goto_marker_mode = MOVE_ONLY`, `pref_goto_last_iconset_path` removed); no toast, no modal.
5. Tap **Custom Icon**.
   - **Expected**: preview shows the one-shot "Selected icon no longer installed. Pick again." hint.
6. Switch to **Friendly**, then back to **Custom Icon**.
   - **Expected**: preview now shows "Pick an icon" (no more hint — it was one-shot per FR-009/US4.AC3).
7. Pick a new iconset/icon; tap Submit.
   - **Expected**: works exactly like Flow A.

## 6. Performance smoke tests

Run from an `adb shell` while the GoTo page is up and Custom Icon is selected:

| What to measure | How to measure | Pass threshold |
|---|---|---|
| Picker open time (step 1) | `adb logcat -s TwCoordGotoView:* CustomIconPickerDialog:*` — look for `show step1` and `bind step1` timestamps | ≤ 300 ms median (SC-002) |
| Step-2 icon-list bind for Responder (~30 icons) | Same logcat tags — `bind step2` timestamps | ≤ 500 ms median (SC-003) |
| Step-2 icon-list bind for a large iconset (load `iconset_responder.zip`'s big-pack variant or any 500+ icon iconset) | Same | ≤ 500 ms median (SC-003) |
| Submit-enabled latency after pick | Logcat — `picked` → `submit enabled` | ≤ 16 ms (SC-004) |
| 60 fps under scroll in step 2 | Android GPU profiling: `adb shell setprop debug.hwui.profile true` | No green bars above the 16 ms line |

If any threshold fails, capture a `systrace` and add the failure to `docs/adr/0011-...-pivots.md` (the post-implement ADR for this feature).

## 7. Off-device test runs

```text
./gradlew :app:testCivDebugUnitTest          # JVM tests including all new contracts
./gradlew :app:connectedCivDebugAndroidTest  # Espresso instrumented tests (device required)
./gradlew :app:spotlessApply                 # Mandatory per Constitution I before commit
./gradlew :app:lint                          # Android lint
```

Expected JVM-test additions in this feature (rough count):

- `IconResolverTest` — 6 tests (per `contracts/icon-resolver.md` § Test contract)
- `CustomIconPickerDialogTest` — 11 tests (per `contracts/custom-icon-picker.md` § Test contract; some require Robolectric or are deferred to Espresso)
- `MarkerModeV2Test` — 6 tests (per `contracts/marker-mode-v2.md` § Test contract)
- `TwCoordGotoViewCustomIconTest` — ~8 tests covering the bind/restore/fallback paths

Espresso additions: 2–3 end-to-end tests covering Flows A and D.

## 8. Constitution VI sanity check before commit

After implementation, grep the new code for unguarded entry points:

```text
rg --type java -n '@Override\s+public\s+(void\s+onReceive|void\s+onClick|boolean\s+onItemClick|View\s+getView|void\s+onCancel|void\s+run)' app/src/main/java/com/atakmap/android/twcoord/gotopage/ --type-add 'java:*.java'
```

Every match MUST have a `try { ... } catch (Throwable t) { Log.w(TAG, ..., t); }` body. `/speckit-analyze` will flag any unguarded entry point as a CRITICAL finding.

## 9. After-merge follow-up

- Add an entry to `docs/ui/input-page.md` describing the new picker preview row and the dialog (per Constitution III).
- Author `docs/adr/0011-custom-marker-icon-implementation.md` per Constitution V (post-`/speckit-implement` ADR cadence).
