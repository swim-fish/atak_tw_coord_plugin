package com.atakmap.android.twcoord;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.twcoord.address.AddressBundleImporter;
import com.atakmap.android.twcoord.address.AddressDataset;
import com.atakmap.android.twcoord.address.OfflineAddressIntents;
import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.DisplayLine;
import com.atakmap.android.twcoord.coord.Formatter;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.gotopage.TwCoordGotoIntents;
import com.atakmap.android.twcoord.i18n.LanguageOverride;
import com.atakmap.android.twcoord.i18n.LocaleOverride;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.log.Log;
import java.util.Locale;

/**
 * Plugin settings (T046). Hosts the two ListPreference entries declared in {@code
 * res/xml/preferences.xml} and keeps each row's summary live with a concrete preview of what the
 * current selection produces — see {@link #refreshAllSummaries()}.
 */
public class TwCoordPreferenceFragment extends PluginPreferenceFragment
    implements SharedPreferences.OnSharedPreferenceChangeListener {

  private static final String TAG = "TwCoordPreferenceFragment";

  @SuppressLint("StaticFieldLeak")
  private static Context pluginContext;

  /** Suffix that distinguishes the three new address-row keys. */
  private static final String[] ADDRESS_ROW_KEYS = {
    "pref_address_row_me", "pref_address_row_target", "pref_address_row_map"
  };

  /** Settable in tests so Robolectric can inject a stub importer without spinning up the map. */
  private AddressImporterProvider addressImporterProvider = TwCoordMapComponent::getAddressImporter;

  // Taipei 101 — the canonical reference point. Cheap to convert, easy to spot-check.
  private static final double SAMPLE_LAT = 25.033611;
  private static final double SAMPLE_LON = 121.564472;

  private final CoordinateConverter converter = new CoordinateConverter();
  private final Formatter formatter = new Formatter();

  public TwCoordPreferenceFragment() {
    super(pluginContext, R.xml.preferences);
  }

  @SuppressLint("ValidFragment")
  public TwCoordPreferenceFragment(final Context pluginContext) {
    super(pluginContext, R.xml.preferences);
    TwCoordPreferenceFragment.pluginContext = pluginContext;
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

    // FR-016 — settings-page button opens the GoTo input page (second entry point alongside
    // the Tools-menu icon). Bind here rather than in onCreate so the click handler is reattached
    // every time the user navigates back to this screen.
    Preference openGoto = findPreference("pref_open_goto");
    if (openGoto != null) {
      openGoto.setOnPreferenceClickListener(
          p -> {
            Intent i = new Intent(TwCoordGotoIntents.ACTION_SHOW_GOTO);
            AtakBroadcast.getInstance().sendBroadcast(i);
            return true;
          });
    }

    // Feature 004 / US3 — clicking the status row opens the Offline Address page so the operator
    // can import / replace / remove the dataset without leaving Settings.
    Preference status = findPreference("pref_address_dataset_status");
    if (status != null) {
      status.setOnPreferenceClickListener(
          p -> {
            try {
              Intent i = new Intent(OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS);
              AtakBroadcast.getInstance().sendBroadcast(i);
            } catch (Throwable t) {
              Log.w(TAG, "ACTION_SHOW_OFFLINE_ADDRESS broadcast threw", t);
            }
            return true;
          });
    }
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
   * Re-resolves every visible string against the currently-selected UI language and writes it back
   * onto the live Preference objects. Without this, switching language updates the on-map readouts
   * but leaves the entire settings page frozen in the locale it was opened with — the Preference
   * framework binds {@code @string/...} references at inflate time and does not react to a later
   * config change.
   *
   * <p>Each visible row (category header, title, summary) is refreshed:
   *
   * <ul>
   *   <li>The two {@link android.preference.ListPreference} summaries get the live preview (entry
   *       label + sample formatted coordinate / sample translations).
   *   <li>The two PreferenceCategory headers and the Accuracy-notice row get their title / summary
   *       re-read from a {@link Context} wrapped via {@link LocaleOverride}.
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

    // Feature 004 / US3 — re-title the Offline Address category + 3 SwitchPreferences and refresh
    // the dataset-presence status row's summary + clickability per the three states in
    // contracts/address-preferences.md.
    refreshAddressSection(wrapped);
  }

  private void refreshAddressSection(Context wrapped) {
    setPreferenceTitle("pref_address_header_key", wrapped.getString(R.string.pref_address_header));
    setPreferenceTitle(
        "pref_address_row_me", wrapped.getString(R.string.pref_address_row_me_title));
    setPreferenceTitle(
        "pref_address_row_target", wrapped.getString(R.string.pref_address_row_target_title));
    setPreferenceTitle(
        "pref_address_row_map", wrapped.getString(R.string.pref_address_row_map_title));
    Preference me = findPreference("pref_address_row_me");
    if (me != null) me.setSummary(wrapped.getString(R.string.pref_address_row_me_summary));
    Preference tgt = findPreference("pref_address_row_target");
    if (tgt != null) tgt.setSummary(wrapped.getString(R.string.pref_address_row_target_summary));
    Preference map = findPreference("pref_address_row_map");
    if (map != null) map.setSummary(wrapped.getString(R.string.pref_address_row_map_summary));

    refreshAddressDatasetStatus(wrapped);
  }

  /**
   * Reads {@link AddressBundleImporter#activeOrNull()} lazily and updates the status row's summary
   * + clickability per the three states in {@code contracts/address-preferences.md § Dataset
   * presence summary table}:
   *
   * <ul>
   *   <li>all three switches off → hide the row (disabled + non-selectable)
   *   <li>at least one switch on, no dataset → "No dataset installed — tap to open Offline Address"
   *   <li>at least one switch on, dataset active → "Active: &lt;county&gt; · &lt;data_date&gt;"
   * </ul>
   *
   * <p>Wrapped per Constitution VI: any exception (e.g. importer not yet built because the map
   * component hasn't run {@code onCreate}) is logged at {@code Log.w} and treated as "no dataset"
   * (the more conservative summary).
   */
  private void refreshAddressDatasetStatus(Context wrapped) {
    Preference status = findPreference("pref_address_dataset_status");
    if (status == null) return;
    try {
      status.setTitle(wrapped.getString(R.string.pref_address_dataset_status_title));

      boolean anyToggleOn = anyAddressToggleOn();
      AddressBundleImporter importer = addressImporterProvider.get();
      AddressDataset active = importer != null ? importer.activeOrNull() : null;
      DatasetStatusPresentation p =
          resolveDatasetStatus(new ResourceStatusStrings(wrapped), anyToggleOn, active);
      status.setEnabled(p.enabled());
      status.setSelectable(p.selectable());
      status.setSummary(p.summary());
    } catch (Throwable t) {
      Log.w(TAG, "refreshAddressDatasetStatus threw", t);
      try {
        status.setSummary(wrapped.getString(R.string.pref_address_dataset_status_summary_none));
      } catch (Throwable ignored) {
        // best-effort
      }
    }
  }

  /**
   * Pure-logic helper extracted for JVM testability. Decides the status row's summary text +
   * enabled/selectable flags from the three inputs in {@code contracts/address-preferences.md §
   * Dataset presence summary table}:
   *
   * <ul>
   *   <li>all three switches off → hide the row (disabled + non-selectable)
   *   <li>at least one switch on, no dataset → "No dataset installed — tap to open Offline Address"
   *   <li>at least one switch on, dataset active → "Active: &lt;county&gt; · &lt;data_date&gt;"
   * </ul>
   */
  static DatasetStatusPresentation resolveDatasetStatus(
      StatusStrings strings, boolean anyToggleOn, AddressDataset active) {
    if (!anyToggleOn) {
      return new DatasetStatusPresentation(strings.datasetStatusNone(), false, false);
    }
    if (active == null) {
      return new DatasetStatusPresentation(strings.datasetStatusHint(), true, true);
    }
    String summary =
        strings.datasetStatusActive(active.generator().county(), active.generator().dataDate());
    return new DatasetStatusPresentation(summary, true, true);
  }

  private boolean anyAddressToggleOn() {
    SharedPreferences sp = getPreferenceManager().getSharedPreferences();
    if (sp == null) return false;
    for (String key : ADDRESS_ROW_KEYS) {
      if (sp.getBoolean(key, false)) return true;
    }
    return false;
  }

  /** Immutable presentation result for the dataset-status row. */
  record DatasetStatusPresentation(String summary, boolean enabled, boolean selectable) {}

  /** Resource-backed strings used by {@link #resolveDatasetStatus} (test seam). */
  interface StatusStrings {
    String datasetStatusNone();

    String datasetStatusHint();

    String datasetStatusActive(String county, String dataDate);
  }

  /** Production {@link StatusStrings} impl reading from the wrapped {@link Context}'s resources. */
  private static final class ResourceStatusStrings implements StatusStrings {
    private final Context ctx;

    ResourceStatusStrings(Context ctx) {
      this.ctx = ctx;
    }

    @Override
    public String datasetStatusNone() {
      return ctx.getString(R.string.pref_address_dataset_status_summary_none);
    }

    @Override
    public String datasetStatusHint() {
      return ctx.getString(R.string.pref_address_dataset_status_summary_hint);
    }

    @Override
    public String datasetStatusActive(String county, String dataDate) {
      return ctx.getString(
          R.string.pref_address_dataset_status_summary_active_format, county, dataDate);
    }
  }

  /**
   * Test seam — production wires this to {@link TwCoordMapComponent#getAddressImporter()}.
   * Robolectric tests inject a stub returning a pre-built dataset / {@code null}.
   */
  interface AddressImporterProvider {
    AddressBundleImporter get();
  }

  void setAddressImporterProvider(AddressImporterProvider provider) {
    this.addressImporterProvider = provider != null ? provider : () -> null;
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
