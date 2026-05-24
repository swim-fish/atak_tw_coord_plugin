package com.atakmap.android.twcoord.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.gotopage.MarkerMode;
import com.atakmap.android.twcoord.i18n.LanguageOverride;
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

  // Feature 002 (input-page GoTo) — last-submitted (unit, value) tuple plus the recent-entries
  // JSON. None of these fire `fireAll()` because the on-map readout widget does not depend on
  // them. The input page reads them directly via the getters below at open time.
  public static final String KEY_GOTO_LAST_UNIT = "pref_goto_last_unit";
  public static final String KEY_GOTO_LAST_TAIPOWER = "pref_goto_last_taipower";
  public static final String KEY_GOTO_LAST_TWD97_E = "pref_goto_last_twd97_e";
  public static final String KEY_GOTO_LAST_TWD97_N = "pref_goto_last_twd97_n";
  public static final String KEY_GOTO_LAST_TWD97_ZONE = "pref_goto_last_twd97_zone";
  public static final String KEY_GOTO_LAST_TWD67_E = "pref_goto_last_twd67_e";
  public static final String KEY_GOTO_LAST_TWD67_N = "pref_goto_last_twd67_n";
  public static final String KEY_GOTO_LAST_TWD67_ZONE = "pref_goto_last_twd67_zone";
  public static final String KEY_GOTO_RECENT_JSON = "pref_goto_recent_json";

  // Feature 003: marker-mode is durable across plugin restarts (changes feature 002's prior
  // in-session-only behaviour — ADR-0010 D5). MOVE_ONLY is the install-time default so a fresh
  // install never auto-drops markers. The Option B refactor (ADR-0011 D8) removed the
  // KEY_GOTO_LAST_ICONSET_PATH key and its atomic-clear helper since the custom picker was
  // scrapped in favour of EnterLocationDropDownReceiver delegation.
  public static final String KEY_GOTO_MARKER_MODE = "pref_goto_marker_mode";

  // Feature 004: three independent per-row address-display toggles (ME / TGT / MAP). All default
  // false on a fresh install, so upgrades from v1.0.4 see zero visual change until the operator
  // explicitly enables at least one. fireAll() is called when any of the three flips so the
  // widget refreshes within one refresh cycle.
  public static final String KEY_ADDRESS_ROW_ME = "pref_address_row_me";
  public static final String KEY_ADDRESS_ROW_TARGET = "pref_address_row_target";
  public static final String KEY_ADDRESS_ROW_MAP = "pref_address_row_map";

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
                || KEY_ADDRESS_ROW_MAP.equals(key)) {
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
        getAddressRowMap());
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

  // ============================================================
  // Feature 002 (input-page GoTo) typed accessors.
  //
  // These do NOT fire fireAll() — the readout widget is unaffected by GoTo persistence. The
  // input page reads via these getters at DropDown open time and writes via the setters on
  // successful submit (FR-014).
  // ============================================================

  public CoordinateUnit getGotoLastUnit() {
    String s = sp.getString(KEY_GOTO_LAST_UNIT, CoordinateUnit.TAIPOWER.name());
    try {
      return CoordinateUnit.valueOf(s);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, "Unknown goto-last-unit pref value '" + s + "', falling back to TAIPOWER");
      return CoordinateUnit.TAIPOWER;
    }
  }

  public void setGotoLastUnit(CoordinateUnit unit) {
    Objects.requireNonNull(unit, "unit");
    sp.edit().putString(KEY_GOTO_LAST_UNIT, unit.name()).apply();
  }

  public String getGotoLastTaipower() {
    return sp.getString(KEY_GOTO_LAST_TAIPOWER, "");
  }

  public void setGotoLastTaipower(String rawValue) {
    sp.edit().putString(KEY_GOTO_LAST_TAIPOWER, rawValue == null ? "" : rawValue).apply();
  }

  public int getGotoLastTwd97Easting() {
    return sp.getInt(KEY_GOTO_LAST_TWD97_E, 0);
  }

  public int getGotoLastTwd97Northing() {
    return sp.getInt(KEY_GOTO_LAST_TWD97_N, 0);
  }

  public int getGotoLastTwd97Zone() {
    int z = sp.getInt(KEY_GOTO_LAST_TWD97_ZONE, 121);
    return (z == 121 || z == 119) ? z : 121;
  }

  public void setGotoLastTwd97(int easting, int northing, int zone) {
    if (zone != 121 && zone != 119) {
      throw new IllegalArgumentException("zone must be 121 or 119: " + zone);
    }
    sp.edit()
        .putInt(KEY_GOTO_LAST_TWD97_E, easting)
        .putInt(KEY_GOTO_LAST_TWD97_N, northing)
        .putInt(KEY_GOTO_LAST_TWD97_ZONE, zone)
        .apply();
  }

  public int getGotoLastTwd67Easting() {
    return sp.getInt(KEY_GOTO_LAST_TWD67_E, 0);
  }

  public int getGotoLastTwd67Northing() {
    return sp.getInt(KEY_GOTO_LAST_TWD67_N, 0);
  }

  public int getGotoLastTwd67Zone() {
    int z = sp.getInt(KEY_GOTO_LAST_TWD67_ZONE, 121);
    return (z == 121 || z == 119) ? z : 121;
  }

  public void setGotoLastTwd67(int easting, int northing, int zone) {
    if (zone != 121 && zone != 119) {
      throw new IllegalArgumentException("zone must be 121 or 119: " + zone);
    }
    sp.edit()
        .putInt(KEY_GOTO_LAST_TWD67_E, easting)
        .putInt(KEY_GOTO_LAST_TWD67_N, northing)
        .putInt(KEY_GOTO_LAST_TWD67_ZONE, zone)
        .apply();
  }

  public String getGotoRecentJson() {
    return sp.getString(KEY_GOTO_RECENT_JSON, "[]");
  }

  public void setGotoRecentJson(String json) {
    sp.edit().putString(KEY_GOTO_RECENT_JSON, json == null ? "[]" : json).apply();
  }

  // ============================================================
  // Feature 003 (Custom Icon marker mode) — durable across plugin restarts.
  // The eight feature-002 marker modes ALSO persist through getGotoMarkerMode/setGotoMarkerMode
  // — this changes feature 002's in-session-only behaviour, per ADR-0010 D5. MOVE_ONLY is the
  // install-time default, so the "no surprise marker drops on fresh install" property is kept.
  // ============================================================

  /**
   * Last persisted marker-mode selection. Defaults to {@link MarkerMode#MOVE_ONLY}; falls back to
   * MOVE_ONLY on a corrupt value (e.g. an enum name from a future version we don't know).
   */
  public MarkerMode getGotoMarkerMode() {
    String s = sp.getString(KEY_GOTO_MARKER_MODE, MarkerMode.MOVE_ONLY.name());
    try {
      return MarkerMode.valueOf(s);
    } catch (IllegalArgumentException e) {
      Log.w(TAG, "Unknown goto-marker-mode pref value '" + s + "', falling back to MOVE_ONLY");
      return MarkerMode.MOVE_ONLY;
    }
  }

  public void setGotoMarkerMode(MarkerMode mode) {
    Objects.requireNonNull(mode, "mode");
    sp.edit().putString(KEY_GOTO_MARKER_MODE, mode.name()).apply();
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
}
