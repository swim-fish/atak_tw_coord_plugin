# Contract: `PreferenceStore`

**Package**: `com.atakmap.android.twpower.prefs`
**Module**: Android (depends on `SharedPreferences`)
**Tested at**: `app/src/androidTest/java/.../prefs/PreferenceStoreTest.java`

Typed wrapper around the plugin's `SharedPreferences`. Hides string
keys and `String`-to-enum coercion from the rest of the code; emits
strongly-typed `UserPreference` snapshots on every change.

---

## Storage keys

| Key | Type | Default | Allowed values |
|---|---|---|---|
| `pref_coord_unit` | string | `"TWD97"` | `TAIPOWER`, `TWD97`, `TWD67` |
| `pref_ui_language` | string | `"SYSTEM"` | `SYSTEM`, `EN`, `ZH_TW`, `JA` |
| `pref_stale_fix_threshold_ms` | long | `10000` | any positive long |

Any other key is ignored. Unknown values for the enum keys MUST log a
warning (logcat only, no telemetry) and fall back to the default.

---

## API

```java
public final class PreferenceStore {

    public PreferenceStore(Context context);

    /** Read the current preference snapshot. */
    public UserPreference snapshot();

    /** Persist a unit choice. Triggers onChange(). */
    public void setCoordinateUnit(CoordinateUnit unit);

    /** Persist a language override. Triggers onChange(). */
    public void setLanguageOverride(LanguageOverride lang);

    /** Persist the stale-fix threshold (ms). */
    public void setStaleFixThresholdMs(long ms);

    /**
     * Register a typed change listener.
     * The listener receives a fresh UserPreference snapshot
     * synchronously on the UI thread.
     */
    public void registerOnChange(Listener listener);
    public void unregisterOnChange(Listener listener);

    public interface Listener {
        void onPreferenceChanged(UserPreference snapshot);
    }
}
```

---

## Behaviour requirements

- **Synchronous dispatch**: `setX(...)` MUST complete the write and
  fire `onPreferenceChanged` synchronously on the UI thread before
  returning. This is what makes FR-006 / FR-018 (live repaint) easy
  for callers.
- **Atomic snapshot**: every `UserPreference` returned MUST reflect a
  consistent point-in-time view of all three keys (no torn reads).
- **Listener registration order**: listeners are notified in
  registration order. This matters because the widget listens for unit
  changes and the locale wrapper listens for language changes; the
  locale wrapper MUST be notified first so the widget reads the
  re-wrapped Context.
- **No network or disk I/O on the UI thread besides `SharedPreferences`
  commit**; per Android best practice, the underlying `commit()` is
  acceptable here because the payloads are tiny enums.

---

## Negative cases the tests MUST cover

- Corrupt value in `pref_coord_unit` (e.g. `"XYZ"`) → snapshot returns
  `TWD97` default; the corrupt value is overwritten on next `setX`.
- Listener registered after a change has already happened → no
  retroactive callback; caller must read `snapshot()` once at
  registration.
- `null` listener → `NullPointerException`.

---

## Threading

- All methods MUST be called on the UI thread.
- Listener callbacks fire on the UI thread.
- The plugin never reads / writes the same `SharedPreferences` from
  background threads.
