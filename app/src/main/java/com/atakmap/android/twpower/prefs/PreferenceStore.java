package com.atakmap.android.twpower.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.atakmap.android.twpower.coord.CoordinateUnit;
import com.atakmap.android.twpower.i18n.LanguageOverride;
import com.atakmap.coremap.log.Log;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Typed wrapper around the plugin's SharedPreferences (T043). Per contracts/preference-store.md:
 * synchronous on-UI-thread dispatch, atomic snapshot, FIFO listener order, corrupt-value fallback
 * to defaults.
 */
public final class PreferenceStore {

  public interface Listener {
    void onPreferenceChanged(UserPreference snapshot);
  }

  public static final String KEY_COORD_UNIT = "pref_coord_unit";
  public static final String KEY_UI_LANGUAGE = "pref_ui_language";
  public static final String KEY_STALE_THRESHOLD = "pref_stale_fix_threshold_ms";

  private static final String TAG = "TwPowerPrefs";

  private final SharedPreferences sp;
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final SharedPreferences.OnSharedPreferenceChangeListener spListener;

  public PreferenceStore(Context context) {
    Objects.requireNonNull(context, "context");
    this.sp = PreferenceManager.getDefaultSharedPreferences(context);
    this.spListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences shared, String key) {
            if (KEY_COORD_UNIT.equals(key)
                || KEY_UI_LANGUAGE.equals(key)
                || KEY_STALE_THRESHOLD.equals(key)) {
              fireAll();
            }
          }
        };
    sp.registerOnSharedPreferenceChangeListener(spListener);
  }

  public void dispose() {
    sp.unregisterOnSharedPreferenceChangeListener(spListener);
    listeners.clear();
  }

  public UserPreference snapshot() {
    return new UserPreference(readUnit(), readLanguage(), readStale());
  }

  public void setCoordinateUnit(CoordinateUnit unit) {
    Objects.requireNonNull(unit, "unit");
    sp.edit().putString(KEY_COORD_UNIT, unit.name()).commit();
    // OnSharedPreferenceChangeListener will fire fireAll() synchronously.
  }

  public void setLanguageOverride(LanguageOverride lang) {
    Objects.requireNonNull(lang, "lang");
    sp.edit().putString(KEY_UI_LANGUAGE, lang.name()).commit();
  }

  public void setStaleFixThresholdMs(long ms) {
    if (ms <= 0) throw new IllegalArgumentException("ms must be > 0");
    sp.edit().putLong(KEY_STALE_THRESHOLD, ms).commit();
  }

  public void registerOnChange(Listener listener) {
    Objects.requireNonNull(listener, "listener");
    listeners.add(listener);
  }

  public void unregisterOnChange(Listener listener) {
    listeners.remove(listener);
  }

  private void fireAll() {
    UserPreference snap = snapshot();
    for (Listener l : listeners) {
      l.onPreferenceChanged(snap);
    }
  }

  private CoordinateUnit readUnit() {
    String s = sp.getString(KEY_COORD_UNIT, CoordinateUnit.TWD97.name());
    try {
      return CoordinateUnit.valueOf(s);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, "Unknown coord-unit pref value '" + s + "', falling back to TWD97");
      return CoordinateUnit.TWD97;
    }
  }

  private LanguageOverride readLanguage() {
    String s = sp.getString(KEY_UI_LANGUAGE, LanguageOverride.SYSTEM.name());
    try {
      return LanguageOverride.valueOf(s);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, "Unknown ui-language pref value '" + s + "', falling back to SYSTEM");
      return LanguageOverride.SYSTEM;
    }
  }

  private long readStale() {
    long ms = sp.getLong(KEY_STALE_THRESHOLD, 10_000L);
    return ms > 0 ? ms : 10_000L;
  }
}
