# Quickstart — Taiwan Coordinate Display Plugin (developer setup & manual acceptance)

**Date**: 2026-05-16 | **Feature**: 001-tw-coord-display
**Audience**: a contributor (human or agent) picking up this feature for
the first time.

---

## 1. Prerequisites

- **JDK 17** on `PATH` (`java --version` reports 17).
- **Android Studio** Hedgehog (2023.1) or later, with Android SDK
  components: `platforms;android-34`, `platforms;android-36`,
  `build-tools;34.0.0` (or newer).
- **ATAK-CIV 5.7.0.3 SDK** at `C:\Users\hhhnr\source\tak\ATAK-CIV-5.7.0.3-SDK`
  (path used by `gradle.properties`).
- **ATAK-CIV 5.7.0.3 installed** on a physical Android device or
  emulator (USB debugging enabled).
- **Git** with a configured author identity (for ADR / commit metadata).

## 2. First build (no tests yet)

```powershell
cd C:\Users\hhhnr\source\tak\atak_tw_coord_plugin
.\gradlew :app:assembleCivDebug
```

Expected output: `app/build/outputs/apk/civ/debug/ATAK-Plugin-atak_tw_
power_plugin-<version>-civ-debug-5.7.0.3.apk`.

If `atak-gradle-takdev` cannot find `main.jar`, set
`atak.sdk.path=C\:/Users/hhhnr/source/tak/ATAK-CIV-5.7.0.3-SDK` in
`gradle.properties` (escape backslashes per the Gradle properties
format).

## 3. Run the unit tests (pure JVM — TDD inner loop)

```powershell
.\gradlew :app:testCivDebugUnitTest
```

This runs `coord/`, `Formatter`, and `LocaleOverride` tests on the JVM
in seconds. **All tests MUST pass** before any commit (Constitution
Principle II).

Golden-vector tests are the bedrock — if they fail you have either
broken the math or unsynced from pwa_map; do NOT relax tolerances
without a corresponding ADR explaining why.

## 4. Run the instrumented tests (Android, device required)

```powershell
.\gradlew :app:connectedCivDebugAndroidTest
```

Covers the preference fragment, widget rendering, and the clipboard
copy contract.

## 5. Format & static analysis (Principle I gate)

```powershell
.\gradlew spotlessApply lint
```

`spotlessApply` runs `google-java-format` over the entire codebase.
This MUST be run before staging any change; pre-commit hook will
reject otherwise.

## 6. Install onto a device

```powershell
.\gradlew :app:installCivDebug
# then on the device, ensure ATAK is closed, open ATAK, accept the
# new plugin's load prompt.
```

## 7. Manual acceptance walk (run after every UI-affecting change)

These map 1:1 to the spec's User Stories and acceptance scenarios.

### US1 — map-centre readout (P1)
1. Open ATAK with the plugin loaded.
2. Verify the top-right overlay shows two rows: `MAP TWD97: …m …m`
   and `ME TWD97: …m …m` (using the default TWD97 unit).
3. Pan the map — the `MAP` row updates with no perceptible lag.
4. Pan the map until centre is over Hong Kong — `MAP TWD97: out of
   range` plus the WGS84 fallback `(lat, lon)` underneath.

### US2 — own-position readout (P1)
1. With GPS enabled, verify the `ME` row shows your live position.
2. Toggle airplane mode (or revoke location permission) — `ME` row
   transitions to `no fix` (or `no permission`) within ~10 s.
3. Re-enable; verify the row recovers to a live value.

### US3 — settings page (P2)
1. ATAK menu → Tool Preferences → Specific Tool Preferences →
   `Taiwan Coordinate Display`.
2. Toggle unit to `TWD67`: both rows reformat **immediately** on
   returning to the map (no app restart).
3. Toggle unit to `Taipower`: both rows reformat; verify a known
   landmark (Taipei 101) reads `B7039 BD32` (within tolerance).
4. Toggle UI language to `中文（正體）`: row labels and unit tags
   switch language **on the very next frame** (FR-018).
5. Force-stop ATAK and relaunch: previously-selected unit + language
   restored.

### FR-015 / SC-008 — clipboard copy
1. Tap the `MAP` row: clipboard contents now equal the on-screen
   string exactly; brief toast confirmation appears within 200 ms.
2. Paste into a chat app — string is identical.
3. Repeat in each of the three UI languages.

### Edge cases (Edge Cases section of spec.md)
- Map heavily zoomed in / out — precision does not gain spurious
  digits.
- Long Taipower 11-char readout (precision flag, if enabled) does not
  overflow the widget.

## 8. Generating the ADR after `/speckit-implement`

Per Constitution Principle V, every `/speckit-implement` run finishes
with a new ADR under `docs/adr/`. Use the next available number:

```text
docs/adr/0001-coordinate-math-source.md      # provenance: pwa_map + reference doc
docs/adr/0002-no-tdal-integration.md         # why we render everything ourselves
docs/adr/0003-locale-override-mechanism.md   # createConfigurationContext approach
```

A template lives at `docs/adr/README.md`; copy-and-fill.

## 9. Updating `docs/ui/` after UI changes

Per Constitution Principle III, any change to the widget layout or the
preference fragment requires a corresponding update in `docs/ui/`:

- `docs/ui/readout-widget.md` — describes the overlay layout, anchor,
  colour palette, and the OK / out-of-range / no-fix visual variants.
- `docs/ui/settings-fragment.md` — describes the preference fragment
  fields and their interaction.

Include a screenshot or wireframe whenever the visual changes.

## 10. Troubleshooting cheatsheet

| Symptom | Likely cause | Fix |
|---|---|---|
| `ClassNotFoundException` on plugin load | AndroidX version drift | Re-check exclude rules in `app/build.gradle` against `meshtastic_atak` precedent |
| Plugin loads but no widget appears | wrong `RootLayoutWidget` anchor or `attach()` not called | check `MapComponent.onCreate` order |
| Coordinates wrong by ~400 m | accidentally calling proj4 EPSG:3828 directly | use the hand-rolled `DatumShiftTwd67` (see `research.md` R8 warning) |
| Settings change doesn't repaint | listener registered on wrong `SharedPreferences` instance | confirm `PreferenceStore.registerOnChange` fires |
| Release build crashes, debug fine | ProGuard ate a lambda or stripped `IPlugin` impl | replace lambdas with SAM classes; check `proguard-gradle.txt` keeps the entry points |
