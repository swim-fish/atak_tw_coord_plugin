package com.atakmap.android.twcoord;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.twcoord.address.ActiveDatasetRegistry;
import com.atakmap.android.twcoord.address.AddressBundleImporter;
import com.atakmap.android.twcoord.address.AddressDataset;
import com.atakmap.android.twcoord.address.ConfidenceThresholds;
import com.atakmap.android.twcoord.address.CountyActiveDataset;
import com.atakmap.android.twcoord.address.OfflineAddressIntents;
import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.DisplayLine;
import com.atakmap.android.twcoord.coord.Formatter;
import com.atakmap.android.twcoord.coord.Wgs84;
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

  /** Settable in tests so Robolectric can inject a stub importer without spinning up the map. */
  private AddressImporterProvider addressImporterProvider = TwCoordMapComponent::getAddressImporter;

  /** Settable in tests so the per-county list can be JVM-tested without the map component. */
  private RegistryProvider addressRegistryProvider = TwCoordMapComponent::getAddressRegistry;

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
    // The dataset manager remains an internal page. This settings row is its public navigation
    // path after the standalone Tools item is retired.
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
    refreshFeature007(wrapped);

    // Feature 004 / US3 — re-title the Offline Address category + 3 SwitchPreferences and refresh
    // the dataset-presence status row's summary + clickability per the three states in
    // contracts/address-preferences.md.
    refreshAddressSection(wrapped);
  }

  /**
   * Feature 007 — keep the readout-visibility checkbox + result-ordering list aligned with the UI
   * language. The ordering summary echoes the selected entry label so the operator sees the active
   * choice at a glance.
   */
  private void refreshFeature007(Context wrapped) {
    setPreferenceTitle(
        "pref_readout_visible", wrapped.getString(R.string.pref_readout_visible_title));
    Preference readout = findPreference("pref_readout_visible");
    if (readout != null) {
      readout.setSummary(wrapped.getString(R.string.pref_readout_visible_summary));
    }
    setPreferenceTitle(
        "pref_search_result_ordering",
        wrapped.getString(R.string.pref_search_result_ordering_title));
    ListPreference ordering = (ListPreference) findPreference("pref_search_result_ordering");
    if (ordering == null) return;
    ordering.setEntries(
        wrapped.getResources().getStringArray(R.array.search_result_ordering_entries));
    CharSequence entry = ordering.getEntry();
    ordering.setSummary(
        entry != null ? entry : wrapped.getString(R.string.pref_search_result_ordering_summary));
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

    refreshConfidencePresetSummary(wrapped);
    refreshAddressDatasetStatus(wrapped);
    refreshActiveDatasetsCategory(wrapped);
  }

  /**
   * Feature 005 polish — keep the confidence-preset row's title + entries + summary aligned with
   * the current UI-language override. The summary echoes the selected entry label (which itself
   * encodes the threshold pair like "嚴格（~ 20 公尺 / ~~ 100 公尺）"), so the operator sees the active
   * thresholds at a glance without opening the dialog.
   */
  private void refreshConfidencePresetSummary(Context wrapped) {
    setPreferenceTitle(
        "pref_address_confidence_preset",
        wrapped.getString(R.string.pref_address_confidence_preset_title));
    ListPreference pref = (ListPreference) findPreference("pref_address_confidence_preset");
    if (pref == null) return;
    // Re-set entries from the wrapped context so the dialog list rows also translate.
    pref.setEntries(wrapped.getResources().getStringArray(R.array.confidence_preset_entries));
    String value = pref.getValue();
    if (value == null) value = ConfidenceThresholds.TIGHT.name();
    ConfidenceThresholds preset = ConfidenceThresholds.fromPrefValue(value);
    CharSequence entry = pref.getEntry();
    if (entry == null) {
      // No entries bound yet (first paint) — derive a sane fallback from the preset.
      int fallbackId;
      switch (preset) {
        case OFF:
          fallbackId = R.string.opt_confidence_off;
          break;
        case STANDARD:
          fallbackId = R.string.opt_confidence_standard;
          break;
        case LOOSE:
          fallbackId = R.string.opt_confidence_loose;
          break;
        case TIGHT:
        default:
          fallbackId = R.string.opt_confidence_tight;
          break;
      }
      entry = wrapped.getString(fallbackId);
    }
    pref.setSummary(entry);
  }

  /**
   * Reads the {@link ActiveDatasetRegistry} snapshot (multi-county; Feature 005) with a {@link
   * AddressBundleImporter#activeOrNull()} fallback for the v1.0.5 intermediate state and updates
   * the status row's summary + clickability:
   *
   * <ul>
   *   <li>registry has N ≥ 1 counties → "N counties active — tap to open"
   *   <li>registry empty, legacy active dataset → "Active: &lt;county&gt; · &lt;data_date&gt;"
   *       (auto-migrate intermediate state)
   *   <li>nothing active anywhere → "No dataset — tap to open"
   * </ul>
   *
   * <p>The row is always enabled and selectable because dataset management is independent of the
   * three map readout visibility toggles.
   *
   * <p>Wrapped per Constitution VI: any exception (e.g. importer/registry not yet built because the
   * map component hasn't run {@code onCreate}) is logged at {@code Log.w} and treated as "no
   * dataset" (the most conservative summary).
   */
  private void refreshAddressDatasetStatus(Context wrapped) {
    Preference status = findPreference("pref_address_dataset_status");
    if (status == null) return;
    try {
      status.setTitle(wrapped.getString(R.string.pref_address_dataset_status_title));

      int activeCountyCount = 0;
      try {
        ActiveDatasetRegistry registry = addressRegistryProvider.get();
        if (registry != null) activeCountyCount = registry.snapshot().size();
      } catch (Throwable t) {
        Log.w(TAG, "registry snapshot threw", t);
      }
      AddressDataset legacyActive = null;
      if (activeCountyCount == 0) {
        AddressBundleImporter importer = addressImporterProvider.get();
        legacyActive = importer != null ? importer.activeOrNull() : null;
      }
      DatasetStatusPresentation p =
          resolveDatasetStatus(new ResourceStatusStrings(wrapped), activeCountyCount, legacyActive);
      status.setEnabled(p.enabled());
      status.setSelectable(p.selectable());
      status.setSummary(p.summary());
    } catch (Throwable t) {
      Log.w(TAG, "refreshAddressDatasetStatus threw", t);
      try {
        status.setEnabled(true);
        status.setSelectable(true);
        status.setSummary(wrapped.getString(R.string.pref_address_dataset_status_summary_none));
      } catch (Throwable ignored) {
        // best-effort
      }
    }
  }

  /**
   * Pure-logic helper extracted for JVM testability. See {@link #refreshAddressDatasetStatus} for
   * the four-state truth table.
   *
   * <p>The multi-county path wins over the legacy single-active path: once {@code activeCountyCount
   * > 0}, {@code legacyActive} is ignored. This matches the AddressSubsystem's preference for the
   * registry once it has been bound.
   */
  static DatasetStatusPresentation resolveDatasetStatus(
      StatusStrings strings, int activeCountyCount, AddressDataset legacyActive) {
    if (activeCountyCount > 0) {
      return new DatasetStatusPresentation(
          strings.datasetStatusActiveMulti(activeCountyCount), true, true);
    }
    if (legacyActive != null) {
      return new DatasetStatusPresentation(
          strings.datasetStatusActive(
              legacyActive.generator().county(), legacyActive.generator().dataDate()),
          true,
          true);
    }
    return new DatasetStatusPresentation(strings.datasetStatusHint(), true, true);
  }

  /**
   * Phase 7 T042 — populate the per-county list under the {@code pref_address_active_datasets}
   * category from {@link ActiveDatasetRegistry#snapshot()}. Each row is non-selectable
   * (informational); the operator manages datasets via the internal manager reachable from the
   * status row above. Wrapped per Constitution VI: registry failures degrade to an empty category
   * rather than crashing the preferences fragment.
   */
  private void refreshActiveDatasetsCategory(Context wrapped) {
    Preference catRef = findPreference("pref_address_active_datasets");
    if (!(catRef instanceof PreferenceCategory)) return;
    PreferenceCategory category = (PreferenceCategory) catRef;
    // Title is cheap and language-dependent — always refresh it.
    category.setTitle(wrapped.getString(R.string.pref_address_active_datasets_header));
    try {
      ActiveDatasetRegistry registry = addressRegistryProvider.get();
      java.util.Map<String, CountyActiveDataset> snapshot =
          registry == null ? java.util.Collections.emptyMap() : registry.snapshot();
      // onSharedPreferenceChanged fires for ANY preference (coord unit, a single row toggle,
      // confidence preset, …). Rebuilding N county rows + layout passes on every such change is
      // wasteful at 22 counties. Skip the rebuild unless the rendered content would actually
      // differ. The language is part of the signature because the row summary format string is
      // language-dependent.
      String signature =
          activeDatasetsSignature(String.valueOf(currentLanguageOverride()), snapshot);
      if (signature.equals(lastActiveDatasetsSignature)) {
        return;
      }
      lastActiveDatasetsSignature = signature;
      category.removeAll();
      for (CountyActiveDataset entry : snapshot.values()) {
        Preference row = new Preference(pluginContext);
        row.setLayoutResource(R.layout.pref_item);
        row.setTitle(entry.county());
        row.setSummary(
            wrapped.getString(
                R.string.pref_address_active_dataset_row_format,
                entry.dataset().generator().dataDate(),
                entry.dataset().generator().insertedRows()));
        row.setSelectable(false);
        category.addPreference(row);
      }
    } catch (Throwable t) {
      Log.w(TAG, "refreshActiveDatasetsCategory threw", t);
      // Invalidate so the next refresh attempts a clean rebuild rather than trusting a partial one.
      lastActiveDatasetsSignature = null;
    }
  }

  /** Last-rendered active-datasets signature; see {@link #activeDatasetsSignature}. */
  private String lastActiveDatasetsSignature;

  /**
   * Content signature for the active-datasets category: the UI language tag (the row summary format
   * is language-dependent) plus each county's {@code (county, dataDate, insertedRows)} tuple. Equal
   * signatures mean the rendered rows would be identical, so the rebuild can be skipped.
   *
   * <p>Package-private + static for unit testing (mirrors {@link #resolveDatasetStatus}); the
   * language tag is passed in so the method needs no live fragment / preference state.
   */
  static String activeDatasetsSignature(
      String languageTag, java.util.Map<String, CountyActiveDataset> snapshot) {
    StringBuilder sb = new StringBuilder();
    sb.append(languageTag).append('|');
    for (CountyActiveDataset entry : snapshot.values()) {
      sb.append(entry.county())
          .append(':')
          .append(entry.dataset().generator().dataDate())
          .append(':')
          .append(entry.dataset().generator().insertedRows())
          .append(';');
    }
    return sb.toString();
  }

  /** Immutable presentation result for the dataset-status row. */
  record DatasetStatusPresentation(String summary, boolean enabled, boolean selectable) {}

  /** Resource-backed strings used by {@link #resolveDatasetStatus} (test seam). */
  interface StatusStrings {
    String datasetStatusNone();

    String datasetStatusHint();

    String datasetStatusActive(String county, String dataDate);

    /** Phase 7 T042 — used when the multi-county registry has one or more active counties. */
    String datasetStatusActiveMulti(int countyCount);
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

    @Override
    public String datasetStatusActiveMulti(int countyCount) {
      return ctx.getString(R.string.pref_address_dataset_status_summary_multi_format, countyCount);
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

  /** Phase 7 T042 test seam — mirrors {@link AddressImporterProvider} for the registry. */
  interface RegistryProvider {
    ActiveDatasetRegistry get();
  }

  void setAddressRegistryProvider(RegistryProvider provider) {
    this.addressRegistryProvider = provider != null ? provider : () -> null;
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
