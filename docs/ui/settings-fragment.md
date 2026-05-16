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

## Out of scope for v1

- Per-row precision toggles (Taipower 11-char, TWD97 sub-metre).
- Custom stale-fix threshold UI (the value lives in SharedPreferences
  but has no preference entry yet; defaults to 10 s).
- Restoring a prior selection from an exported settings JSON.

## Screenshots

_TODO — capture during US3 acceptance walk (T059) and embed:_

- `settings-en.png`
- `settings-zh-tw.png`
- `settings-ja.png`
- `settings-coord-unit-dialog.png`
- `settings-ui-language-dialog.png`

## Related artefacts

- Spec: `spec.md` FR-004, FR-005, FR-006, FR-017, FR-018.
- Contracts: `contracts/preference-store.md`, `contracts/widget-overlay.md`.
- ADR-0003 (locale-override mechanism via `createConfigurationContext`).
- Clarification Q1 + Q2 + Q3 (2026-05-16 session).
