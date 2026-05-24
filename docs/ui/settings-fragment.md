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

## Offline Address section — feature 004 (US3)

Added below the existing accuracy notice category. Three flat
`SwitchPreference` toggles (no master switch — per Clarifications
Session 2026-05-24 Q2) plus one `Preference` row that surfaces the
active dataset's presence at a glance.

```
┌─ Offline Address ───────────────────────────────────┐
│                                                     │
│  Show address for self-location (ME)        [○ off] │
│  Adds an address line under the ME coordinate row.  │
│                                                     │
│  Show address for target (TGT)              [● on ] │
│  Adds an address line under the TGT coordinate row. │
│                                                     │
│  Show address for map-centre (MAP)          [○ off] │
│  Adds an address line under the MAP coordinate row. │
│                                                     │
│  Dataset status                                     │
│  Active: 台中市 · 115-01                            │
│                                                     │
└─────────────────────────────────────────────────────┘
```

| Key                              | Type                | Default | Notes                              |
|----------------------------------|---------------------|---------|------------------------------------|
| `pref_address_row_me`            | `SwitchPreference`  | `false` | per-row address gate, ME           |
| `pref_address_row_target`        | `SwitchPreference`  | `false` | per-row address gate, TGT          |
| `pref_address_row_map`           | `SwitchPreference`  | `false` | per-row address gate, MAP          |
| `pref_address_dataset_status`    | `Preference` (row)  | n/a     | summary surfaces dataset presence  |

**Dataset-status row summary** depends on two inputs — whether *any*
of the three toggles is on, and whether `AddressBundleImporter.activeOrNull()`
returns a dataset:

| Any toggle on | Dataset active | Summary                                                                  |
|---------------|----------------|--------------------------------------------------------------------------|
| no            | (any)          | hidden via `setEnabled(false) + setSelectable(false)`                    |
| yes           | no             | localised `pref_address_dataset_status_summary_hint` — tap to open page |
| yes           | yes            | localised `pref_address_dataset_status_summary_active_format` (`Active: <county> · <data_date>`) — tap to open page |

When the row is clickable (either of the bottom two rows in the truth
table), tapping it broadcasts `OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS`
to open the Offline Address page (`docs/ui/offline-address-page.md`).
The click lambda body is wrapped in `try/catch (Throwable) { Log.w(...) }`
per Constitution VI.

The summary refreshes on every `onResume` and on every
`onSharedPreferenceChanged(...)` for the three keys, so toggling a
switch immediately updates the dataset-status row's visibility and
text without re-entering Settings.

The pure decision-table logic is extracted as the static helper
`TwCoordPreferenceFragment.resolveDatasetStatus(StatusStrings, boolean
anyToggleOn, AddressDataset active)` and tested on plain JVM in
`TwCoordPreferenceFragmentAddressTest`. The fragment retrieves the
live importer via the `TwCoordMapComponent.getAddressImporter()`
static accessor (same pattern as `TwCoordPreferenceFragment.pluginContext`).

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
- `settings-address-status-active.png` — status row in "Active: 台中市 · 115-01" state.
- `settings-address-status-hidden.png` — status row when all toggles are off (row not drawn).

## Related artefacts

- Spec: `spec.md` FR-004, FR-005, FR-006, FR-017, FR-018 (feature 001/002).
- Spec (feature 004): `specs/004-offline-address/spec.md` FR-010, FR-011, FR-018; Clarifications Session 2026-05-24 Q2.
- Contracts: `contracts/preference-store.md`, `contracts/widget-overlay.md`, `specs/004-offline-address/contracts/address-preferences.md`.
- ADR-0003 (locale-override mechanism via `createConfigurationContext`).
- ADR-0014 (feature 004 reconnaissance; R14 settings-UI decision).
- Clarification Q1 + Q2 + Q3 (2026-05-16 feature 001 session); feature 004 Session 2026-05-24 Q2 (no master switch).
