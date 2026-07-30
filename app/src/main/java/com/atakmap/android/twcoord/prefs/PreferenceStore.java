package com.atakmap.android.twcoord.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.atakmap.android.twcoord.address.ConfidenceThresholds;
import com.atakmap.android.twcoord.address.lookup.ResultOrdering;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.i18n.LanguageOverride;
import com.atakmap.android.twcoord.nativeentry.TaipowerInputMode;
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

  // Retired custom Go To keys remain named solely for non-destructive upgrade tests. Production
  // code intentionally exposes no accessors and never reads, writes, or clears these values.
  public static final String KEY_GOTO_LAST_UNIT = "pref_goto_last_unit";
  public static final String KEY_GOTO_LAST_TAIPOWER = "pref_goto_last_taipower";
  public static final String KEY_GOTO_LAST_TWD97_E = "pref_goto_last_twd97_e";
  public static final String KEY_GOTO_LAST_TWD97_N = "pref_goto_last_twd97_n";
  public static final String KEY_GOTO_LAST_TWD97_ZONE = "pref_goto_last_twd97_zone";
  public static final String KEY_GOTO_LAST_TWD67_E = "pref_goto_last_twd67_e";
  public static final String KEY_GOTO_LAST_TWD67_N = "pref_goto_last_twd67_n";
  public static final String KEY_GOTO_LAST_TWD67_ZONE = "pref_goto_last_twd67_zone";
  public static final String KEY_GOTO_RECENT_JSON = "pref_goto_recent_json";

  // ATAK native coordinate-entry selection remains separate from retired Go To state.
  public static final String KEY_NATIVE_ENTRY_LAST_UNIT = "pref_native_entry_last_unit";
  public static final String KEY_NATIVE_ENTRY_TAIPOWER_MODE =
      "pref_native_entry_taipower_input_mode";

  public static final String KEY_GOTO_MARKER_MODE = "pref_goto_marker_mode";

  // Feature 004: three independent per-row address-display toggles (ME / TGT / MAP). All default
  // false on a fresh install, so upgrades from v1.0.4 see zero visual change until the operator
  // explicitly enables at least one. fireAll() is called when any of the three flips so the
  // widget refreshes within one refresh cycle.
  public static final String KEY_ADDRESS_ROW_ME = "pref_address_row_me";
  public static final String KEY_ADDRESS_ROW_TARGET = "pref_address_row_target";
  public static final String KEY_ADDRESS_ROW_MAP = "pref_address_row_map";

  // Feature 005 (Phase 7 polish): operator-selectable preset for the tilde confidence indicator
  // applied to address text. Stored as the enum name; fallback to TIGHT on missing/corrupt
  // values keeps the 2026-05-27 device-verified default for upgrading installs.
  public static final String KEY_ADDRESS_CONFIDENCE_PRESET = "pref_address_confidence_preset";

  // Native Address candidate ordering. Stored as the ResultOrdering enum name; missing/corrupt
  // values fall back to DISTANCE. The map widget does not depend on it.
  public static final String KEY_SEARCH_RESULT_ORDERING = "pref_search_result_ordering";

  // Feature 007 US2: on-map readout visibility. Replaces the show/hide the old tool-button cycle
  // provided (the cycle ended hidden). Defaults to true (shown). DOES fire fireAll() so the widget
  // reacts within one refresh cycle.
  public static final String KEY_READOUT_VISIBLE = "pref_readout_visible";

  private static final String TAG = "TwCoordPrefs";

  private final SharedPreferences sp;
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final SharedPreferences.OnSharedPreferenceChangeListener spListener;

  public PreferenceStore(Context context) {
    this(PreferenceManager.getDefaultSharedPreferences(Objects.requireNonNull(context, "context")));
  }

  /**
   * Package-private test seam. Accepts a {@link SharedPreferences} directly so JVM unit tests can
   * supply a Mockito mock without bringing in Robolectric. Production code MUST use the {@link
   * #PreferenceStore(Context)} constructor.
   */
  PreferenceStore(SharedPreferences sp) {
    this.sp = Objects.requireNonNull(sp, "sp");
    this.spListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
          @Override
          public void onSharedPreferenceChanged(SharedPreferences shared, String key) {
            if (KEY_COORD_UNIT.equals(key)
                || KEY_UI_LANGUAGE.equals(key)
                || KEY_STALE_THRESHOLD.equals(key)
                || KEY_ADDRESS_ROW_ME.equals(key)
                || KEY_ADDRESS_ROW_TARGET.equals(key)
                || KEY_ADDRESS_ROW_MAP.equals(key)
                || KEY_ADDRESS_CONFIDENCE_PRESET.equals(key)
                || KEY_READOUT_VISIBLE.equals(key)) {
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
    return new UserPreference(
        readUnit(),
        readLanguage(),
        readStale(),
        getAddressRowMe(),
        getAddressRowTarget(),
        getAddressRowMap(),
        getConfidenceThresholds());
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
      // Constitution VI: a listener throwing in onPreferenceChanged would escape into ATAK's
      // SharedPreferences listener-dispatch frame and crash the host process. Per-listener wrap
      // contains the damage and keeps the remaining listeners running.
      try {
        l.onPreferenceChanged(snap);
      } catch (Throwable t) {
        Log.w(TAG, "preference listener " + l.getClass().getName() + " threw", t);
      }
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

  public CoordinateUnit getNativeEntryLastUnit() {
    String value = sp.getString(KEY_NATIVE_ENTRY_LAST_UNIT, CoordinateUnit.TAIPOWER.name());
    try {
      return CoordinateUnit.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException e) {
      Log.w(TAG, "Unknown native-entry unit '" + value + "', falling back to TAIPOWER");
      return CoordinateUnit.TAIPOWER;
    }
  }

  public void setNativeEntryLastUnit(CoordinateUnit unit) {
    Objects.requireNonNull(unit, "unit");
    sp.edit().putString(KEY_NATIVE_ENTRY_LAST_UNIT, unit.name()).apply();
  }

  public TaipowerInputMode getNativeEntryTaipowerMode() {
    return TaipowerInputMode.fromStoredValue(
        sp.getString(KEY_NATIVE_ENTRY_TAIPOWER_MODE, TaipowerInputMode.SINGLE_FIELD.name()));
  }

  public void setNativeEntryTaipowerMode(TaipowerInputMode mode) {
    Objects.requireNonNull(mode, "mode");
    sp.edit().putString(KEY_NATIVE_ENTRY_TAIPOWER_MODE, mode.name()).apply();
  }

  // ============================================================
  // Feature 004 (Offline Address) — per-row display toggles.
  //
  // These DO fire fireAll() (via spListener) so the widget + AddressSubsystem react within one
  // refresh cycle. All three default to false on a fresh install (no key present), so
  // upgrades from v1.0.4 see zero visual change until the operator opts in.
  // ============================================================

  public boolean getAddressRowMe() {
    return sp.getBoolean(KEY_ADDRESS_ROW_ME, false);
  }

  public boolean getAddressRowTarget() {
    return sp.getBoolean(KEY_ADDRESS_ROW_TARGET, false);
  }

  public boolean getAddressRowMap() {
    return sp.getBoolean(KEY_ADDRESS_ROW_MAP, false);
  }

  public void setAddressRowMe(boolean enabled) {
    sp.edit().putBoolean(KEY_ADDRESS_ROW_ME, enabled).apply();
  }

  public void setAddressRowTarget(boolean enabled) {
    sp.edit().putBoolean(KEY_ADDRESS_ROW_TARGET, enabled).apply();
  }

  public void setAddressRowMap(boolean enabled) {
    sp.edit().putBoolean(KEY_ADDRESS_ROW_MAP, enabled).apply();
  }

  // ============================================================
  // Feature 005 (Phase 7 polish) — confidence-indicator preset.
  //
  // Stored as the enum name (e.g. "TIGHT"). Missing or corrupt values fall back to TIGHT, which
  // matches the 2026-05-27 device-verified 20 m / 100 m behaviour so upgrades see zero change.
  // ============================================================

  public ConfidenceThresholds getConfidenceThresholds() {
    String s = sp.getString(KEY_ADDRESS_CONFIDENCE_PRESET, ConfidenceThresholds.TIGHT.name());
    return ConfidenceThresholds.fromPrefValue(s);
  }

  public void setConfidenceThresholds(ConfidenceThresholds preset) {
    Objects.requireNonNull(preset, "preset");
    sp.edit().putString(KEY_ADDRESS_CONFIDENCE_PRESET, preset.name()).apply();
  }

  // ============================================================
  // Feature 007 US1 — forward-search result ordering.
  // ============================================================

  /** Persisted result ordering; defaults to {@link ResultOrdering#DISTANCE} (shipped behaviour). */
  public ResultOrdering getResultOrdering() {
    return ResultOrdering.fromName(sp.getString(KEY_SEARCH_RESULT_ORDERING, null));
  }

  public void setResultOrdering(ResultOrdering ordering) {
    Objects.requireNonNull(ordering, "ordering");
    sp.edit().putString(KEY_SEARCH_RESULT_ORDERING, ordering.name()).apply();
  }

  // ============================================================
  // Feature 007 US2 — on-map readout visibility (replaces the tool-button cycle's hide/show).
  // ============================================================

  /** Whether the on-map coordinate readout is shown. Defaults to true. */
  public boolean isReadoutVisible() {
    return sp.getBoolean(KEY_READOUT_VISIBLE, true);
  }

  public void setReadoutVisible(boolean visible) {
    sp.edit().putBoolean(KEY_READOUT_VISIBLE, visible).apply();
  }
}
