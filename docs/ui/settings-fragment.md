# UI: settings (preference) fragment

**Surface owner**: `com.atakmap.android.twcoord.TwCoordPreferenceFragment`
**Hosted at**: ATAK menu → `Settings` → `Tool Preferences` → `Specific Tool Preferences` → **TW Coordinates**
**Phase**: US3 — shipped 2026-05-16 (T045 / T046 / T047 / T048)

## Anatomy

```
┌─ TW Coordinates ───────────────────────────────────┐
│                                                    │
│  Display unit                                      │
│    Choose Taipower / TWD97 / TWD67                 │
│                                                    │
│  UI language                                       │
│    Override the plugin's UI language               │
│                                                    │
└────────────────────────────────────────────────────┘
```

Two `ListPreference` entries declared in
`app/src/main/res/xml/preferences.xml`:

| Key                 | Options                                           | Default  |
|---------------------|---------------------------------------------------|----------|
| `pref_coord_unit`   | `TAIPOWER` · `TWD97` · `TWD67`                    | `TWD97`  |
| `pref_ui_language`  | `SYSTEM` · `EN` · `ZH_TW` · `JA`                  | `SYSTEM` |

Entry labels come from `strings.xml` (and `values-zh-rTW/`, `values-
ja/`), so the dialogue text is localised to the currently-resolved
plugin UI language.

## Live-repaint contract (FR-018)

When the user dismisses the ListPreference dialog with a new value:

1. Android writes the new value into `SharedPreferences`.
2. `PreferenceStore.onSharedPreferenceChanged(...)` fires synchronously
   on the UI thread.
3. `TwCoordMapComponent.prefListener` runs:
   - If the language key changed, it first rebuilds
     `localisedPluginContext` via `LocaleOverride.contextFor(...)`,
     so subsequent `R.string.*` lookups go to the new
     `values-*` folder.
   - Then it re-renders both readouts (map row + me row) by passing
     the new unit / fresh `Formatter.Strings` into the existing
     converter pipeline.

The repaint happens before the framework returns control to the user,
so the very next frame after dismissing the dialog shows the new
language and / or unit.

## Locale-listener-before-widget-listener guarantee

`PreferenceStore.registerOnChange(...)` uses a `CopyOnWriteArrayList`,
which preserves registration order. The MapComponent currently
registers only one listener that handles both unit and language; the
listener internally rebuilds the localised context *before* re-
rendering, so there is no read-stale-context window.

If a future change splits into separate listeners (e.g. one for the
widget, one for some other surface), the locale-listener MUST be
registered first.

## Anchor rationale

Re-using ATAK's `ToolsPreferenceFragment.register(...)` puts our
settings entry next to every other plugin's settings — predictable
discovery for the user, no bespoke UI.

## Offline Address section — features 004 (US3) + 005 Phase 7

Added below the existing accuracy notice category. Three flat
`SwitchPreference` toggles (no master switch — per Clarifications
Session 2026-05-24 Q2), a confidence-indicator preset dropdown
(Phase 7 polish), a dataset-status row (the entry point to the
Offline Address page), and a dynamically-populated per-county list.

```
┌─ Offline Address ───────────────────────────────────────────┐
│                                                             │
│  Show address for self-location (ME)        [○ off]         │
│  Show address for target (TGT)              [● on ]         │
│  Show address for map-centre (MAP)          [○ off]         │
│                                                             │
│  Confidence indicator                                       │
│  嚴格（~ 20 公尺 / ~~ 100 公尺）                              │
│                                                             │
│  Dataset status                                             │
│  2 counties active — tap to open Offline Address            │
│                                                             │
│  ── Active datasets ──────────────────────────              │  ← T042 category
│  台中市                                                      │
│  2026-05-15 · 1,316,674 rows                                │
│                                                             │
│  彰化縣                                                      │
│  2026-05-15 · 678,392 rows                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

| Key                                  | Type                                    | Default  | Notes                                                                              |
|--------------------------------------|-----------------------------------------|----------|------------------------------------------------------------------------------------|
| `pref_address_row_me`                | `SwitchPreference`                      | `false`  | per-row address gate, ME                                                           |
| `pref_address_row_target`            | `SwitchPreference`                      | `false`  | per-row address gate, TGT                                                          |
| `pref_address_row_map`               | `SwitchPreference`                      | `false`  | per-row address gate, MAP                                                          |
| `pref_address_confidence_preset`     | `com.atakmap.android.gui.PanListPreference` | `TIGHT`  | T042 polish — selects `ConfidenceThresholds` (OFF / TIGHT / STANDARD / LOOSE) |
| `pref_address_dataset_status`        | `Preference` (row)                      | n/a      | summary surfaces dataset presence (truth table below)                              |
| `pref_address_active_datasets`       | `PreferenceCategory` (programmatic)     | n/a      | T042 — one child `Preference` per active county (county name + `<date> · <N> rows`) |

### Confidence-indicator preset (Phase 7)

`ConfidenceThresholds` enum with 4 presets keyed by enum name:

| Value      | Medium threshold | Low threshold | Display                                |
|------------|------------------|---------------|----------------------------------------|
| `OFF`      | n/a              | n/a           | No prefix on any distance              |
| `TIGHT`    | 20 m             | 100 m         | `~` above 20 m, `~~` above 100 m       |
| `STANDARD` | 50 m             | 200 m         | `~` above 50 m, `~~` above 200 m       |
| `LOOSE`    | 100 m            | 500 m         | `~` above 100 m, `~~` above 500 m      |

Changes propagate to `AddressSubsystem` in real time:
`PreferenceStore.spListener` fires `fireAll()` on
`KEY_ADDRESS_CONFIDENCE_PRESET` change → `TwCoordMapComponent.prefListener`
invokes `addressSubsystem.setConfidenceThresholds(snap.confidenceThresholds())`
→ the worker's next `runLookup` picks up the new preset (the field is
`volatile`). No restart required.

`TIGHT` is the install-time default — it preserves the 2026-05-27
device-verified 20 m / 100 m behaviour so upgrades from v1.0.5 see zero
visual change until the operator chooses otherwise.

### Dataset-status row summary (4-state, feature 005)

Depends on three inputs — whether *any* of the three row toggles is on,
how many counties the multi-county `ActiveDatasetRegistry` holds, and
(for the v1.0.5 → v1.0.6 auto-migrate intermediate state)
`AddressBundleImporter.activeOrNull()`:

| Any toggle on | Active counties | Legacy active | Summary                                                                                              |
|---------------|-----------------|---------------|------------------------------------------------------------------------------------------------------|
| no            | (any)           | (any)         | hidden via `setEnabled(false) + setSelectable(false)`                                                |
| yes           | ≥ 1             | (any)         | localised `pref_address_dataset_status_summary_multi_format` — `N counties active — tap to open`     |
| yes           | 0               | yes           | localised `pref_address_dataset_status_summary_active_format` — `Active: <county> · <data_date>`     |
| yes           | 0               | no            | localised `pref_address_dataset_status_summary_hint` — `No dataset installed — tap to open`          |

The multi-county branch wins over the legacy single-active branch — the
registry is the source of truth once Feature 005's `setRegistry(...)`
has been called. The legacy branch only fires during the brief
auto-migrate window before `Registry.initFromDisk()` runs.

When the row is clickable (rows 2-4 in the table), tapping it broadcasts
`OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS` to open the Offline
Address page (`docs/ui/offline-address-page.md`). The click lambda body
is wrapped in `try/catch (Throwable) { Log.w(...) }` per Constitution VI.

### Active-datasets category (T042)

Populated programmatically on every `onResume` and language change.
For each entry in `ActiveDatasetRegistry.snapshot()`, the fragment adds
a non-selectable `Preference`:

- title = `CountyActiveDataset.county()` (the normalised Traditional
  Chinese form from generator metadata, e.g. `台中市`).
- summary = `<data_date> · <N> rows` via
  `pref_address_active_dataset_row_format` (`%1$s · %2$d rows` in
  English).

Order follows `LinkedHashMap` insertion order from the registry's
underlying `ConcurrentMap`. The fragment uses a `RegistryProvider`
test seam (mirror of `AddressImporterProvider`) so the category
population can be JVM-tested without a live map component.

The pure decision-table logic is extracted as the static helper
`TwCoordPreferenceFragment.resolveDatasetStatus(StatusStrings,
boolean anyToggleOn, int activeCountyCount, AddressDataset legacyActive)`
and tested on plain JVM in `TwCoordPreferenceFragmentAddressTest`
(8 cases as of Phase 7 T042 — all four rows of the truth table plus
the multi-county-beats-legacy-active priority rule).

## Out of scope for v1

- Per-row precision toggles (Taipower 11-char, TWD97 sub-metre).
- Custom stale-fix threshold UI (the value lives in SharedPreferences
  but has no preference entry yet; defaults to 10 s).
- Restoring a prior selection from an exported settings JSON.
- A master switch above the three address-row toggles (explicitly
  rejected — Clarifications Session 2026-05-24 Q2).

## Screenshots

_TODO — capture during US3 acceptance walk (T044 / T057) and embed:_

- `settings-en.png` — base coordinate / UI-language section.
- `settings-zh-tw.png` — base section in Traditional Chinese.
- `settings-ja.png` — base section in Japanese.
- `settings-coord-unit-dialog.png`
- `settings-ui-language-dialog.png`
- `settings-address-toggles-en.png` — feature 004 Offline Address section, English.
- `settings-address-toggles-zh-tw.png` — feature 004 Offline Address section, Traditional Chinese.
- `settings-address-status-hint.png` — status row in "No dataset installed — tap to open" state.
- `settings-address-status-active.png` — status row in "Active: 台中市 · 115-01" state (legacy single-active fallback).
- `settings-address-status-hidden.png` — status row when all toggles are off (row not drawn).
- `settings-address-confidence-dialog.png` — Phase 7 confidence-preset dropdown (4 options visible).
- `settings-address-status-multi.png` — feature 005 status row in `N counties active — tap to open` state.
- `settings-address-active-datasets.png` — feature 005 T042 per-county list with 2+ rows.

## Related artefacts

- Spec: `spec.md` FR-004, FR-005, FR-006, FR-017, FR-018 (feature 001/002).
- Spec (feature 004): `specs/004-offline-address/spec.md` FR-010, FR-011, FR-018; Clarifications Session 2026-05-24 Q2.
- Contracts: `contracts/preference-store.md`, `contracts/widget-overlay.md`, `specs/004-offline-address/contracts/address-preferences.md`.
- ADR-0003 (locale-override mechanism via `createConfigurationContext`).
- ADR-0014 (feature 004 reconnaissance; R14 settings-UI decision).
- Clarification Q1 + Q2 + Q3 (2026-05-16 feature 001 session); feature 004 Session 2026-05-24 Q2 (no master switch).
