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
   * Updates each preference's summary in the form {@code "<entry label> — <live preview>"} so the
   * user can see the effect of the current selection without opening the dialog.
   */
  private void refreshAllSummaries() {
    refreshCoordUnitSummary();
    refreshLanguageSummary();
  }

  private void refreshCoordUnitSummary() {
    ListPreference pref = (ListPreference) findPreference("pref_coord_unit");
    if (pref == null) return;
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
    pref.setSummary(entry + " — " + sampleCoordPreview(unit));
  }

  private void refreshLanguageSummary() {
    ListPreference pref = (ListPreference) findPreference("pref_ui_language");
    if (pref == null) return;
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
  private String sampleCoordPreview(CoordinateUnit unit) {
    Wgs84 fix =
        new Wgs84(SAMPLE_LAT, SAMPLE_LON, System.currentTimeMillis(), Wgs84.Source.MAP_CENTRE);
    ConversionResult result = converter.convert(fix, unit);
    StaticStrings strings = new StaticStrings(pluginContext);
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
