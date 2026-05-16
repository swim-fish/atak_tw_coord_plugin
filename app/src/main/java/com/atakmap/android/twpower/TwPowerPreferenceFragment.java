package com.atakmap.android.twpower;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.twpower.coord.ConversionResult;
import com.atakmap.android.twpower.coord.CoordinateConverter;
import com.atakmap.android.twpower.coord.CoordinateUnit;
import com.atakmap.android.twpower.coord.DisplayLine;
import com.atakmap.android.twpower.coord.Formatter;
import com.atakmap.android.twpower.coord.Wgs84;
import com.atakmap.android.twpower.i18n.LanguageOverride;
import com.atakmap.android.twpower.i18n.LocaleOverride;
import com.atakmap.android.twpower.plugin.R;
import java.util.Locale;

/**
 * Plugin settings (T046). Hosts the two ListPreference entries declared in {@code
 * res/xml/preferences.xml} and keeps each row's summary live with a concrete preview of what the
 * current selection produces — see {@link #refreshAllSummaries()}.
 */
public class TwPowerPreferenceFragment extends PluginPreferenceFragment
    implements SharedPreferences.OnSharedPreferenceChangeListener {

  @SuppressLint("StaticFieldLeak")
  private static Context pluginContext;

  // Taipei 101 — the canonical reference point. Cheap to convert, easy to spot-check.
  private static final double SAMPLE_LAT = 25.033611;
  private static final double SAMPLE_LON = 121.564472;

  private final CoordinateConverter converter = new CoordinateConverter();
  private final Formatter formatter = new Formatter();

  public TwPowerPreferenceFragment() {
    super(pluginContext, R.xml.preferences);
  }

  @SuppressLint("ValidFragment")
  public TwPowerPreferenceFragment(final Context pluginContext) {
    super(pluginContext, R.xml.preferences);
    TwPowerPreferenceFragment.pluginContext = pluginContext;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onResume() {
    super.onResume();
    SharedPreferences sp = getPreferenceManager().getSharedPreferences();
    if (sp != null) sp.registerOnSharedPreferenceChangeListener(this);
    refreshAllSummaries();
  }

  @Override
  public void onPause() {
    super.onPause();
    SharedPreferences sp = getPreferenceManager().getSharedPreferences();
    if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(this);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
    refreshAllSummaries();
  }

  @Override
  public String getSubTitle() {
    return getSubTitle("Tool Preferences", pluginContext.getString(R.string.pref_screen_title));
  }

  /**
   * Re-resolves every visible string against the currently-selected UI language and writes it
   * back onto the live Preference objects. Without this, switching language updates the on-map
   * readouts but leaves the entire settings page frozen in the locale it was opened with — the
   * Preference framework binds {@code @string/...} references at inflate time and does not
   * react to a later config change.
   *
   * <p>Each visible row (category header, title, summary) is refreshed:
   *
   * <ul>
   *   <li>The two {@link android.preference.ListPreference} summaries get the live preview
   *       (entry label + sample formatted coordinate / sample translations).
   *   <li>The two PreferenceCategory headers and the Accuracy-notice row get their title /
   *       summary re-read from a {@link Context} wrapped via {@link LocaleOverride}.
   * </ul>
   */
  private void refreshAllSummaries() {
    LanguageOverride lang = currentLanguageOverride();
    Context wrapped = LocaleOverride.contextFor(pluginContext, lang, Locale.getDefault());

    setPreferenceTitle("pref_screen_header", wrapped.getString(R.string.pref_screen_title));
    setPreferenceTitle("pref_coord_unit", wrapped.getString(R.string.pref_coord_unit_title));
    setPreferenceTitle("pref_ui_language", wrapped.getString(R.string.pref_ui_language_title));
    setPreferenceTitle(
        "pref_accuracy_header_key", wrapped.getString(R.string.pref_accuracy_header));

    android.preference.Preference notice = findPreference("pref_accuracy_notice");
    if (notice != null) {
      notice.setTitle(wrapped.getString(R.string.pref_accuracy_title));
      notice.setSummary(wrapped.getString(R.string.pref_accuracy_summary));
    }

    refreshCoordUnitSummary(wrapped);
    refreshLanguageSummary(wrapped);
  }

  private void setPreferenceTitle(String key, CharSequence title) {
    android.preference.Preference p = findPreference(key);
    if (p != null) p.setTitle(title);
  }

  private LanguageOverride currentLanguageOverride() {
    android.preference.ListPreference pref =
        (android.preference.ListPreference) findPreference("pref_ui_language");
    if (pref == null) return LanguageOverride.SYSTEM;
    try {
      return LanguageOverride.valueOf(pref.getValue());
    } catch (IllegalArgumentException | NullPointerException e) {
      return LanguageOverride.SYSTEM;
    }
  }

  private void refreshCoordUnitSummary(Context wrapped) {
    ListPreference pref = (ListPreference) findPreference("pref_coord_unit");
    if (pref == null) return;
    // Re-set entries from the wrapped context so the dialog list rows also translate.
    pref.setEntries(wrapped.getResources().getStringArray(R.array.coord_unit_entries));
    String value = pref.getValue();
    if (value == null) value = CoordinateUnit.TWD97.name();
    CoordinateUnit unit;
    try {
      unit = CoordinateUnit.valueOf(value);
    } catch (IllegalArgumentException e) {
      unit = CoordinateUnit.TWD97;
    }
    CharSequence entry = pref.getEntry();
    if (entry == null) entry = value;
    pref.setSummary(entry + " — " + sampleCoordPreview(unit, wrapped));
  }

  private void refreshLanguageSummary(Context wrapped) {
    ListPreference pref = (ListPreference) findPreference("pref_ui_language");
    if (pref == null) return;
    pref.setEntries(wrapped.getResources().getStringArray(R.array.ui_language_entries));
    String value = pref.getValue();
    if (value == null) value = LanguageOverride.SYSTEM.name();
    LanguageOverride lang;
    try {
      lang = LanguageOverride.valueOf(value);
    } catch (IllegalArgumentException e) {
      lang = LanguageOverride.SYSTEM;
    }
    CharSequence entry = pref.getEntry();
    if (entry == null) entry = value;
    pref.setSummary(entry + " — " + sampleLanguagePreview(lang));
  }

  /** Returns "TWD97: 306,963m 2,769,619m" style preview for Taipei 101 in the given unit. */
  private String sampleCoordPreview(CoordinateUnit unit, Context wrapped) {
    Wgs84 fix =
        new Wgs84(SAMPLE_LAT, SAMPLE_LON, System.currentTimeMillis(), Wgs84.Source.MAP_CENTRE);
    ConversionResult result = converter.convert(fix, unit);
    StaticStrings strings = new StaticStrings(wrapped);
    DisplayLine line = formatter.format(Wgs84.Source.MAP_CENTRE, result, unit, strings);
    if (line.state() != DisplayLine.State.OK) return strings.stateOutOfRange();
    return line.unitTag() + ": " + line.value();
  }

  /** Returns the three row labels translated into the language the override would yield. */
  private String sampleLanguagePreview(LanguageOverride lang) {
    Context previewCtx = LocaleOverride.contextFor(pluginContext, lang, Locale.getDefault());
    return previewCtx.getString(R.string.label_map)
        + " / "
        + previewCtx.getString(R.string.label_me)
        + " / "
        + previewCtx.getString(R.string.label_target);
  }

  /**
   * Resource-backed Strings using the plugin's current configuration (NOT the language override).
   */
  private static final class StaticStrings implements Formatter.Strings {
    private final Context ctx;

    StaticStrings(Context ctx) {
      this.ctx = ctx;
    }

    @Override
    public String labelMap() {
      return ctx.getString(R.string.label_map);
    }

    @Override
    public String labelMe() {
      return ctx.getString(R.string.label_me);
    }

    @Override
    public String labelTarget() {
      return ctx.getString(R.string.label_target);
    }

    @Override
    public String unitTagTaipower() {
      return ctx.getString(R.string.unit_tag_taipower);
    }

    @Override
    public String unitTagTwd97() {
      return ctx.getString(R.string.unit_tag_twd97);
    }

    @Override
    public String unitTagTwd67() {
      return ctx.getString(R.string.unit_tag_twd67);
    }

    @Override
    public String stateOutOfRange() {
      return ctx.getString(R.string.state_out_of_range);
    }

    @Override
    public String stateNoFix() {
      return ctx.getString(R.string.state_no_fix);
    }

    @Override
    public String stateNoPermission() {
      return ctx.getString(R.string.state_no_permission);
    }
  }
}
