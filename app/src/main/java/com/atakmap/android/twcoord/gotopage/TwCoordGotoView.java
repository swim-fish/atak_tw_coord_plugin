package com.atakmap.android.twcoord.gotopage;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.DatumShiftTwd67;
import com.atakmap.android.twcoord.coord.Projections;
import com.atakmap.android.twcoord.coord.TaipowerCode;
import com.atakmap.android.twcoord.coord.Twd67Tm2;
import com.atakmap.android.twcoord.coord.Twd97Tm2;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.i18n.LocaleOverride;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.android.twcoord.prefs.PreferenceStore;
import com.atakmap.android.user.EnterLocationDropDownReceiver;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * View controller for the TW Coord GoTo input page. Owns the inflated layout, switches between
 * Taipower / TWD97 / TWD67 panes, runs keystroke validation, and on Submit drives the pan + persist
 * + toast + close path.
 *
 * <p><b>Marker policy:</b> Submit pans the camera (X/Y only — Z / zoom is preserved) and optionally
 * drops a marker using one of the 8 fixed marker-mode radios. For custom icons the page exposes a
 * separate "Drop via ATAK picker" button that pans the camera and opens ATAK's native {@link
 * EnterLocationDropDownReceiver} pane so the operator picks an icon from there (ADR-0011 D8). That
 * delegation reuses the host's existing pallet/iconset UI instead of bundling our own picker.
 */
public final class TwCoordGotoView {

  private static final String TAG = "TwCoordGotoView";

  private final View root;
  private final Context pluginContext;
  private final MapView mapView;
  private final CoordinateParser parser;
  private final PreferenceStore prefs;
  private final Runnable dropDownCloser;
  private final AtomicBoolean submitInFlight = new AtomicBoolean(false);

  // Tab radios.
  private final RadioButton tabTaipower;
  private final RadioButton tabTwd97;
  private final RadioButton tabTwd67;

  // Panes.
  private final View paneTaipower;
  private final View paneTwd97;
  private final View paneTwd67;

  // Taipower tab.
  private final EditText inputTaipower;
  private final TextView errorTaipower;

  // TWD97 tab.
  private final EditText inputTwd97Easting;
  private final EditText inputTwd97Northing;
  private final RadioGroup zoneTwd97;
  private final RadioButton zoneTwd97_121;
  private final RadioButton zoneTwd97_119;
  private final TextView advisoryTwd97;
  private final TextView errorTwd97;

  // TWD67 tab.
  private final EditText inputTwd67Easting;
  private final EditText inputTwd67Northing;
  private final RadioGroup zoneTwd67;
  private final RadioButton zoneTwd67_121;
  private final RadioButton zoneTwd67_119;
  private final TextView advisoryTwd67;
  private final TextView errorTwd67;

  // Shared submit button + ATAK picker delegation button.
  private final Button submitButton;
  private final Button atakPickerButton;

  // Auto Fill (US5).
  private final Button autoFillTaipower;
  private final Button autoFillTwd97;
  private final Button autoFillTwd67;
  private final CoordinateConverter forwardConverter = new CoordinateConverter();

  // Recent entries (US4).
  private final RecentEntryStore recentStore;
  private final LinearLayout recentList;
  private final TextView recentEmpty;

  // Marker mode (Move-only vs Drop-{type}). Feature 003 changes the prior in-session-only
  // behaviour: markerMode is now persisted across plugin restarts via
  // pref_goto_marker_mode (ADR-0010 D5). MOVE_ONLY remains the install-time default so the
  // "no accidental marker drops" property is preserved on fresh installs. Custom icons are
  // handled by the separate "Drop via ATAK picker" button — they do NOT live in this enum.
  private final RadioButton modeMove;
  private final RadioButton modeWaypoint;
  private final RadioButton modeMission;
  private final RadioButton modeSpi;
  private final RadioButton modeFriendly;
  private final RadioButton modeHostile;
  private final RadioButton modeNeutral;
  private final RadioButton modeUnknown;
  private MarkerMode markerMode = MarkerMode.MOVE_ONLY;

  // State.
  private CoordinateUnit activeTab = CoordinateUnit.TAIPOWER;
  private ParseResult lastTaipowerParse;
  private ParseResult lastTwd97Parse;
  private ParseResult lastTwd67Parse;
  private MapCenterFix latestFix = new MapCenterFix(null, false, false, false);

  /**
   * Context wrapped with the operator's UI-language override. Re-resolved at every {@link
   * #bind(InputPageState)} so that opening the page picks up the latest language choice (FR-013).
   * All visible strings MUST be fetched via this context, never via the raw {@link #pluginContext},
   * otherwise the GoTo page renders in the system locale while the rest of the plugin renders in
   * the override.
   */
  private Context localisedContext;

  public TwCoordGotoView(
      View root,
      Context pluginContext,
      MapView mapView,
      CoordinateParser parser,
      PreferenceStore prefs,
      RecentEntryStore recentStore,
      Runnable dropDownCloser) {
    this.root = root;
    this.pluginContext = pluginContext;
    this.localisedContext = pluginContext; // overwritten in bind() before any string lookup runs.
    this.mapView = mapView;
    this.parser = parser;
    this.prefs = prefs;
    this.recentStore = recentStore;
    this.dropDownCloser = dropDownCloser;

    this.tabTaipower = root.findViewById(R.id.goto_tab_taipower);
    this.tabTwd97 = root.findViewById(R.id.goto_tab_twd97);
    this.tabTwd67 = root.findViewById(R.id.goto_tab_twd67);
    this.paneTaipower = root.findViewById(R.id.goto_pane_taipower);
    this.paneTwd97 = root.findViewById(R.id.goto_pane_twd97);
    this.paneTwd67 = root.findViewById(R.id.goto_pane_twd67);

    this.inputTaipower = root.findViewById(R.id.goto_input_taipower);
    this.errorTaipower = root.findViewById(R.id.goto_error_taipower);

    this.inputTwd97Easting = root.findViewById(R.id.goto_input_twd97_easting);
    this.inputTwd97Northing = root.findViewById(R.id.goto_input_twd97_northing);
    this.zoneTwd97 = root.findViewById(R.id.goto_zone_twd97);
    this.zoneTwd97_121 = root.findViewById(R.id.goto_zone_twd97_121);
    this.zoneTwd97_119 = root.findViewById(R.id.goto_zone_twd97_119);
    this.advisoryTwd97 = root.findViewById(R.id.goto_advisory_twd97);
    this.errorTwd97 = root.findViewById(R.id.goto_error_twd97);

    this.inputTwd67Easting = root.findViewById(R.id.goto_input_twd67_easting);
    this.inputTwd67Northing = root.findViewById(R.id.goto_input_twd67_northing);
    this.zoneTwd67 = root.findViewById(R.id.goto_zone_twd67);
    this.zoneTwd67_121 = root.findViewById(R.id.goto_zone_twd67_121);
    this.zoneTwd67_119 = root.findViewById(R.id.goto_zone_twd67_119);
    this.advisoryTwd67 = root.findViewById(R.id.goto_advisory_twd67);
    this.errorTwd67 = root.findViewById(R.id.goto_error_twd67);

    this.submitButton = root.findViewById(R.id.goto_btn_submit);
    this.atakPickerButton = root.findViewById(R.id.goto_btn_atak_picker);
    this.autoFillTaipower = root.findViewById(R.id.goto_autofill_taipower);
    this.autoFillTwd97 = root.findViewById(R.id.goto_autofill_twd97);
    this.autoFillTwd67 = root.findViewById(R.id.goto_autofill_twd67);
    this.recentList = root.findViewById(R.id.goto_recent_list);
    this.recentEmpty = root.findViewById(R.id.goto_recent_empty);

    this.modeMove = root.findViewById(R.id.goto_mode_move);
    this.modeWaypoint = root.findViewById(R.id.goto_mode_waypoint);
    this.modeMission = root.findViewById(R.id.goto_mode_mission);
    this.modeSpi = root.findViewById(R.id.goto_mode_spi);
    this.modeFriendly = root.findViewById(R.id.goto_mode_friendly);
    this.modeHostile = root.findViewById(R.id.goto_mode_hostile);
    this.modeNeutral = root.findViewById(R.id.goto_mode_neutral);
    this.modeUnknown = root.findViewById(R.id.goto_mode_unknown);

    // Constitution VI: every onClick body wrapped via safeClick(...) so an inner SDK fault
    // (Toast / Resources / setBackgroundColor / etc.) cannot propagate up and crash ATAK.
    // safeClick(tag, runnable) catches Throwable and logs via Log.w(TAG, tag + " failed", t).
    modeMove.setOnClickListener(
        v -> safeClick("modeMove", () -> setMarkerMode(MarkerMode.MOVE_ONLY)));
    modeWaypoint.setOnClickListener(
        v -> safeClick("modeWaypoint", () -> setMarkerMode(MarkerMode.WAYPOINT)));
    modeMission.setOnClickListener(
        v -> safeClick("modeMission", () -> setMarkerMode(MarkerMode.MISSION_POINT)));
    modeSpi.setOnClickListener(v -> safeClick("modeSpi", () -> setMarkerMode(MarkerMode.SPI)));
    modeFriendly.setOnClickListener(
        v -> safeClick("modeFriendly", () -> setMarkerMode(MarkerMode.FRIENDLY)));
    modeHostile.setOnClickListener(
        v -> safeClick("modeHostile", () -> setMarkerMode(MarkerMode.HOSTILE)));
    modeNeutral.setOnClickListener(
        v -> safeClick("modeNeutral", () -> setMarkerMode(MarkerMode.NEUTRAL)));
    modeUnknown.setOnClickListener(
        v -> safeClick("modeUnknown", () -> setMarkerMode(MarkerMode.UNKNOWN)));

    autoFillTaipower.setOnClickListener(
        v -> safeClick("autoFillTaipower", () -> onAutoFill(CoordinateUnit.TAIPOWER)));
    autoFillTwd97.setOnClickListener(
        v -> safeClick("autoFillTwd97", () -> onAutoFill(CoordinateUnit.TWD97)));
    autoFillTwd67.setOnClickListener(
        v -> safeClick("autoFillTwd67", () -> onAutoFill(CoordinateUnit.TWD67)));

    tabTaipower.setOnClickListener(
        v -> safeClick("tabTaipower", () -> setActiveTab(CoordinateUnit.TAIPOWER)));
    tabTwd97.setOnClickListener(
        v -> safeClick("tabTwd97", () -> setActiveTab(CoordinateUnit.TWD97)));
    tabTwd67.setOnClickListener(
        v -> safeClick("tabTwd67", () -> setActiveTab(CoordinateUnit.TWD67)));

    inputTaipower.addTextChangedListener(textWatcher(this::validateTaipower));
    inputTwd97Easting.addTextChangedListener(textWatcher(this::validateTwd97));
    inputTwd97Northing.addTextChangedListener(textWatcher(this::validateTwd97));
    inputTwd67Easting.addTextChangedListener(textWatcher(this::validateTwd67));
    inputTwd67Northing.addTextChangedListener(textWatcher(this::validateTwd67));

    zoneTwd97.setOnCheckedChangeListener((g, id) -> validateTwd97());
    zoneTwd67.setOnCheckedChangeListener((g, id) -> validateTwd67());

    submitButton.setOnClickListener(v -> safeClick("submitButton", this::onSubmit));
    atakPickerButton.setOnClickListener(v -> safeClick("atakPickerButton", this::onAtakPicker));
  }

  /**
   * Constitution VI helper. Every {@code OnClickListener} the page registers SHOULD route its body
   * through this method so an inner SDK fault (Toast, Resources, view-mutator throw) is caught at
   * the entry-point's outer scope per Constitution Principle VI, logged via {@code Log.w(TAG, tag +
   * " failed", t)}, and returns without re-throwing into ATAK.
   *
   * <p>The {@code tag} is the static name of the click source ({@code "modeMove"}, {@code
   * "tabTaipower"}, etc.) so logcat narrows down which handler tripped.
   */
  private static void safeClick(String tag, Runnable body) {
    try {
      body.run();
    } catch (Throwable t) {
      Log.w(TAG, tag + " failed", t);
    }
  }

  /**
   * Re-apply every visible string from {@link #localisedContext} so layout-time @string references
   * (which resolve against the plugin's base configuration) are overridden by the operator's
   * UI-language preference. Tab labels, field hints, zone labels, button captions, advisory text,
   * Recent header, and empty state are all refreshed here. Error / toast strings are read on demand
   * via {@code localisedContext.getString(...)}; no refresh needed for those.
   */
  private void refreshLocalisedStrings() {
    Context c = localisedContext;
    try {
      // Title.
      TextView title = root.findViewById(R.id.goto_title);
      if (title != null) title.setText(c.getString(R.string.goto_title));

      // Tab radios.
      tabTaipower.setText(c.getString(R.string.goto_tab_taipower));
      tabTwd97.setText(c.getString(R.string.goto_tab_twd97));
      tabTwd67.setText(c.getString(R.string.goto_tab_twd67));

      // Taipower pane.
      inputTaipower.setHint(c.getString(R.string.goto_hint_taipower));

      // TWD97 pane.
      inputTwd97Easting.setHint(c.getString(R.string.goto_hint_easting));
      inputTwd97Northing.setHint(c.getString(R.string.goto_hint_northing));
      zoneTwd97_121.setText(c.getString(R.string.goto_zone_121));
      zoneTwd97_119.setText(c.getString(R.string.goto_zone_119));
      advisoryTwd97.setText(c.getString(R.string.goto_outer_island_advisory));

      // TWD67 pane.
      inputTwd67Easting.setHint(c.getString(R.string.goto_hint_easting));
      inputTwd67Northing.setHint(c.getString(R.string.goto_hint_northing));
      zoneTwd67_121.setText(c.getString(R.string.goto_zone_121));
      zoneTwd67_119.setText(c.getString(R.string.goto_zone_119));
      advisoryTwd67.setText(c.getString(R.string.goto_outer_island_advisory));

      // Buttons (Submit + ATAK picker + Auto Fill ×3).
      submitButton.setText(c.getString(R.string.goto_btn_submit));
      atakPickerButton.setText(c.getString(R.string.goto_btn_atak_picker));
      autoFillTaipower.setText(c.getString(R.string.goto_btn_autofill));
      autoFillTwd97.setText(c.getString(R.string.goto_btn_autofill));
      autoFillTwd67.setText(c.getString(R.string.goto_btn_autofill));

      // Recent section header + empty state.
      TextView recentHeader = root.findViewById(R.id.goto_recent_header);
      if (recentHeader != null) recentHeader.setText(c.getString(R.string.goto_recent_header));
      recentEmpty.setText(c.getString(R.string.goto_recent_empty));

      // Marker mode header + 8 radios.
      TextView markerModeHeader = root.findViewById(R.id.goto_marker_mode_header);
      if (markerModeHeader != null) {
        markerModeHeader.setText(c.getString(R.string.goto_marker_mode_header));
      }
      modeMove.setText(c.getString(R.string.goto_mode_move));
      modeWaypoint.setText(c.getString(R.string.goto_mode_waypoint));
      modeMission.setText(c.getString(R.string.goto_mode_mission));
      modeSpi.setText(c.getString(R.string.goto_mode_spi));
      modeFriendly.setText(c.getString(R.string.goto_mode_friendly));
      modeHostile.setText(c.getString(R.string.goto_mode_hostile));
      modeNeutral.setText(c.getString(R.string.goto_mode_neutral));
      modeUnknown.setText(c.getString(R.string.goto_mode_unknown));
    } catch (Throwable t) {
      Log.w(TAG, "refreshLocalisedStrings failed", t);
    }
  }

  private static TextWatcher textWatcher(Runnable r) {
    return new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {}

      @Override
      public void afterTextChanged(Editable s) {
        r.run();
      }
    };
  }

  /** Bind the controller to an initial state. Called on every DropDown open. */
  public void bind(InputPageState state) {
    // Refresh the localised context from the current UI-language override on every open. Strings
    // that were inflated against the pluginContext are then re-applied programmatically by
    // refreshLocalisedStrings() below so the page honours the override (FR-013).
    try {
      localisedContext =
          LocaleOverride.contextFor(
              pluginContext, prefs.snapshot().uiLanguage(), Locale.getDefault());
    } catch (Throwable t) {
      Log.w(TAG, "locale override resolution failed; falling back to plugin context", t);
      localisedContext = pluginContext;
    }
    refreshLocalisedStrings();

    if (state == null) {
      activeTab = prefs.getGotoLastUnit();
      inputTaipower.setText(prefs.getGotoLastTaipower());
      inputTwd97Easting.setText(intOrEmpty(prefs.getGotoLastTwd97Easting()));
      inputTwd97Northing.setText(intOrEmpty(prefs.getGotoLastTwd97Northing()));
      applyZoneRadio(zoneTwd97_121, zoneTwd97_119, prefs.getGotoLastTwd97Zone());
      inputTwd67Easting.setText(intOrEmpty(prefs.getGotoLastTwd67Easting()));
      inputTwd67Northing.setText(intOrEmpty(prefs.getGotoLastTwd67Northing()));
      applyZoneRadio(zoneTwd67_121, zoneTwd67_119, prefs.getGotoLastTwd67Zone());
    } else {
      activeTab = state.activeTab();
      inputTaipower.setText(state.taipowerDraft());
      inputTwd97Easting.setText(state.twd97EastingDraft());
      inputTwd97Northing.setText(state.twd97NorthingDraft());
      applyZoneRadio(zoneTwd97_121, zoneTwd97_119, state.twd97Zone());
      inputTwd67Easting.setText(state.twd67EastingDraft());
      inputTwd67Northing.setText(state.twd67NorthingDraft());
      applyZoneRadio(zoneTwd67_121, zoneTwd67_119, state.twd67Zone());
    }
    // Feature 003 — restore persisted markerMode. Defensive try/catch wraps the pref read.
    try {
      this.markerMode = prefs.getGotoMarkerMode();
    } catch (Throwable t) {
      Log.w(TAG, "restore markerMode failed; defaulting to MOVE_ONLY", t);
      this.markerMode = MarkerMode.MOVE_ONLY;
    }

    applyTabVisibility();
    applyMarkerModeUI();
    validateTaipower();
    validateTwd97();
    validateTwd67();
    renderRecentList();
  }

  /** Captured for {@link TwCoordGotoReceiver#onDropDownClose()} per FR-018. */
  public InputPageState snapshotState() {
    return new InputPageState(
        activeTab,
        textOf(inputTaipower),
        textOf(inputTwd97Easting),
        textOf(inputTwd97Northing),
        currentZone(zoneTwd97_121),
        textOf(inputTwd67Easting),
        textOf(inputTwd67Northing),
        currentZone(zoneTwd67_121));
  }

  private void setActiveTab(CoordinateUnit u) {
    activeTab = u;
    applyTabVisibility();
    refreshSubmitEnabled();
    refreshAutoFillEnabled();
  }

  /** Called by the receiver whenever the {@link MapCenterAutoFillStream} emits a fresh fix. */
  public void onMapCenterFix(MapCenterFix fix) {
    this.latestFix = fix != null ? fix : new MapCenterFix(null, false, false, false);
    refreshAutoFillEnabled();
  }

  private void refreshAutoFillEnabled() {
    autoFillTaipower.setEnabled(latestFix.taipowerOk());
    autoFillTwd97.setEnabled(latestFix.twd97Ok());
    autoFillTwd67.setEnabled(latestFix.twd67Ok());
  }

  // ----------- Auto Fill -----------

  private void onAutoFill(CoordinateUnit tabUnit) {
    if (!latestFix.okForUnit(tabUnit)) {
      String msg;
      if (tabUnit == CoordinateUnit.TAIPOWER) {
        msg = localisedContext.getString(R.string.goto_autofill_hint_taipower_outer_island);
      } else {
        msg = localisedContext.getString(R.string.goto_autofill_hint_outside_taiwan);
      }
      Toast.makeText(pluginContext, msg, Toast.LENGTH_SHORT).show();
      return;
    }
    Wgs84 fix = latestFix.wgs84();
    if (fix == null) return;
    switch (tabUnit) {
      case TAIPOWER:
        autoFillTaipowerFromFix(fix);
        break;
      case TWD97:
        autoFillTwd97FromFix(fix);
        break;
      case TWD67:
        autoFillTwd67FromFix(fix);
        break;
    }
  }

  private void autoFillTaipowerFromFix(Wgs84 fix) {
    ConversionResult r = forwardConverter.convert(fix, CoordinateUnit.TAIPOWER);
    if (!r.isOk()) return;
    @SuppressWarnings("rawtypes")
    ConversionResult.Ok ok = (ConversionResult.Ok) r;
    TaipowerCode code = (TaipowerCode) ok.value();
    inputTaipower.setText(renderTaipower(code));
  }

  private void autoFillTwd97FromFix(Wgs84 fix) {
    Twd97Tm2 t97 = Projections.wgs84ToTwd97(fix);
    inputTwd97Easting.setText(Long.toString(Math.round(t97.eastingMetres())));
    inputTwd97Northing.setText(Long.toString(Math.round(t97.northingMetres())));
    applyZoneRadio(zoneTwd97_121, zoneTwd97_119, t97.zone());
  }

  private void autoFillTwd67FromFix(Wgs84 fix) {
    Twd97Tm2 t97 = Projections.wgs84ToTwd97(fix);
    Twd67Tm2 t67 = DatumShiftTwd67.twd97ToTwd67(t97);
    inputTwd67Easting.setText(Long.toString(Math.round(t67.eastingMetres())));
    inputTwd67Northing.setText(Long.toString(Math.round(t67.northingMetres())));
    applyZoneRadio(zoneTwd67_121, zoneTwd67_119, t67.zone());
  }

  // ----------- Recent list (US4) -----------

  /** Called by the receiver whenever the {@link RecentEntryStore} mutates. */
  public void onRecentEntriesChanged(java.util.List<RecentEntry> entries) {
    renderRecentList();
  }

  private void renderRecentList() {
    if (recentList == null) return;
    // Defensive guard — a single bad row must NEVER bring down ATAK. The crash logged at
    // 22:31:17.122 (selectableItemBackground attr-vs-resource confusion) escaped the receiver's
    // `bind()` path and killed the process; wrapping here keeps later listener fires
    // self-contained.
    try {
      java.util.List<RecentEntry> entries =
          recentStore != null ? recentStore.getAll() : java.util.Collections.emptyList();
      recentList.removeAllViews();
      recentEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
      for (int i = 0; i < entries.size(); i++) {
        final int index = i;
        final RecentEntry entry = entries.get(i);
        recentList.addView(buildRecentRow(entry, index));
      }
    } catch (Exception e) {
      Log.w(TAG, "renderRecentList failed; showing empty Recent section", e);
      recentList.removeAllViews();
      recentEmpty.setVisibility(View.VISIBLE);
    }
  }

  private View buildRecentRow(RecentEntry entry, int index) {
    Context ctx = recentList.getContext();
    LinearLayout row = new LinearLayout(ctx);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setLayoutParams(
        new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    int padding = (int) (ctx.getResources().getDisplayMetrics().density * 6);
    row.setPadding(padding, padding, padding, padding);

    TextView label = new TextView(ctx);
    LinearLayout.LayoutParams labelLp =
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    label.setLayoutParams(labelLp);
    label.setText(unitLabel(entry.unit()) + "  " + entry.rawValue());
    label.setTextColor(0xFFFFFFFF);
    label.setTextSize(14f);
    // Make the label tappable. No ripple background — `android.R.attr.selectableItemBackground`
    // is an attribute id, not a drawable resource id; trying to use it crashes ATAK with
    // Resources.NotFoundException. The TextView is still clickable without a ripple drawable.
    label.setClickable(true);
    label.setOnClickListener(v -> safeClick("recentLabel", () -> refillFromRecent(entry)));

    Button del = new Button(ctx);
    LinearLayout.LayoutParams delLp =
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    del.setLayoutParams(delLp);
    del.setText(localisedContext.getString(R.string.goto_recent_delete));
    del.setMinHeight(0);
    del.setTextSize(12f);
    int dp4 = (int) (ctx.getResources().getDisplayMetrics().density * 4);
    int dp10 = (int) (ctx.getResources().getDisplayMetrics().density * 10);
    del.setPadding(dp10, dp4, dp10, dp4);
    del.setOnClickListener(
        v ->
            safeClick(
                "recentDelete",
                () -> {
                  if (recentStore != null) recentStore.removeAt(index);
                }));

    row.addView(label);
    row.addView(del);
    row.setGravity(Gravity.CENTER_VERTICAL);
    return row;
  }

  private void refillFromRecent(RecentEntry entry) {
    activeTab = entry.unit();
    applyTabVisibility();
    switch (entry.unit()) {
      case TAIPOWER:
        inputTaipower.setText(entry.rawValue());
        break;
      case TWD97:
        inputTwd97Easting.setText(intOrEmpty(entry.easting()));
        inputTwd97Northing.setText(intOrEmpty(entry.northing()));
        applyZoneRadio(zoneTwd97_121, zoneTwd97_119, entry.zone());
        break;
      case TWD67:
        inputTwd67Easting.setText(intOrEmpty(entry.easting()));
        inputTwd67Northing.setText(intOrEmpty(entry.northing()));
        applyZoneRadio(zoneTwd67_121, zoneTwd67_119, entry.zone());
        break;
    }
    validateTaipower();
    validateTwd97();
    validateTwd67();
  }

  /** Render a {@link TaipowerCode} in the canonical {@code H7509 DB4016} form. */
  private static String renderTaipower(TaipowerCode c) {
    if (!c.hasOneMetrePrecision()) {
      return String.format(
          Locale.ROOT,
          "%c%04d %c%c%d%d",
          c.region(),
          c.subRegion(),
          c.hundredMeterE(),
          c.hundredMeterN(),
          c.tenMeterE(),
          c.tenMeterN());
    }
    return String.format(
        Locale.ROOT,
        "%c%04d %c%c%d%d%d%d",
        c.region(),
        c.subRegion(),
        c.hundredMeterE(),
        c.hundredMeterN(),
        c.tenMeterE(),
        c.tenMeterN(),
        c.oneMeterE(),
        c.oneMeterN());
  }

  private void applyTabVisibility() {
    tabTaipower.setChecked(activeTab == CoordinateUnit.TAIPOWER);
    tabTwd97.setChecked(activeTab == CoordinateUnit.TWD97);
    tabTwd67.setChecked(activeTab == CoordinateUnit.TWD67);

    // Visual differentiation: the active tab gets bold white text on a slightly lighter
    // background, inactive tabs get muted grey on transparent. `button="@null"` removes the
    // default radio bullet (we want tab-strip feel, not a radio list), so we MUST drive the
    // visual state programmatically — otherwise the layout's static textColor wins and there is
    // no visible indication of which tab is selected.
    styleTab(tabTaipower, activeTab == CoordinateUnit.TAIPOWER);
    styleTab(tabTwd97, activeTab == CoordinateUnit.TWD97);
    styleTab(tabTwd67, activeTab == CoordinateUnit.TWD67);

    paneTaipower.setVisibility(activeTab == CoordinateUnit.TAIPOWER ? View.VISIBLE : View.GONE);
    paneTwd97.setVisibility(activeTab == CoordinateUnit.TWD97 ? View.VISIBLE : View.GONE);
    paneTwd67.setVisibility(activeTab == CoordinateUnit.TWD67 ? View.VISIBLE : View.GONE);
  }

  private void setMarkerMode(MarkerMode mode) {
    this.markerMode = mode;
    // Feature 003 — persist the choice so the next page-open / plugin-restart re-binds it.
    // This intentionally changes feature 002's session-reset behaviour (ADR-0010 D5).
    try {
      prefs.setGotoMarkerMode(mode);
    } catch (Throwable t) {
      Log.w(TAG, "setGotoMarkerMode failed", t);
    }
    applyMarkerModeUI();
    refreshSubmitEnabled();
  }

  private void applyMarkerModeUI() {
    // Manual mutual exclusion across the 8 radios — they live in 2 separate LinearLayout rows
    // rather than one RadioGroup, so Android won't uncheck the others automatically. Also,
    // `button="@null"` removes the default radio bullet so the selection is shown via background
    // tint (same pattern as the tab strip).
    styleMarkerModeRadio(modeMove, markerMode == MarkerMode.MOVE_ONLY);
    styleMarkerModeRadio(modeWaypoint, markerMode == MarkerMode.WAYPOINT);
    styleMarkerModeRadio(modeMission, markerMode == MarkerMode.MISSION_POINT);
    styleMarkerModeRadio(modeSpi, markerMode == MarkerMode.SPI);
    styleMarkerModeRadio(modeFriendly, markerMode == MarkerMode.FRIENDLY);
    styleMarkerModeRadio(modeHostile, markerMode == MarkerMode.HOSTILE);
    styleMarkerModeRadio(modeNeutral, markerMode == MarkerMode.NEUTRAL);
    styleMarkerModeRadio(modeUnknown, markerMode == MarkerMode.UNKNOWN);
  }

  private static void styleMarkerModeRadio(RadioButton btn, boolean selected) {
    btn.setChecked(selected);
    btn.setBackgroundColor(selected ? 0xFF333333 : 0x00000000);
  }

  private static void styleTab(RadioButton tab, boolean selected) {
    if (selected) {
      tab.setTextColor(0xFFFFFFFF);
      tab.setTypeface(Typeface.DEFAULT_BOLD);
      tab.setBackgroundColor(0xFF333333);
    } else {
      tab.setTextColor(0xFFBFBFBF);
      tab.setTypeface(Typeface.DEFAULT);
      tab.setBackgroundColor(0x00000000);
    }
  }

  // ----------- validation -----------

  private void validateTaipower() {
    String raw = textOf(inputTaipower);
    lastTaipowerParse = parser.parseTaipower(raw);
    renderError(lastTaipowerParse, errorTaipower);
    refreshSubmitEnabled();
  }

  private void validateTwd97() {
    int zone = currentZone(zoneTwd97_121);
    advisoryTwd97.setVisibility(zone == 119 ? View.VISIBLE : View.GONE);
    Integer e = parseInt(textOf(inputTwd97Easting));
    Integer n = parseInt(textOf(inputTwd97Northing));
    if (e == null || n == null) {
      lastTwd97Parse = ParseResult.invalid(CoordinateUnit.TWD97, ParseResult.Reason.EMPTY);
    } else {
      lastTwd97Parse = parser.parseTwd97(e, n, zone);
    }
    renderError(lastTwd97Parse, errorTwd97);
    refreshSubmitEnabled();
  }

  private void validateTwd67() {
    int zone = currentZone(zoneTwd67_121);
    advisoryTwd67.setVisibility(zone == 119 ? View.VISIBLE : View.GONE);
    Integer e = parseInt(textOf(inputTwd67Easting));
    Integer n = parseInt(textOf(inputTwd67Northing));
    if (e == null || n == null) {
      lastTwd67Parse = ParseResult.invalid(CoordinateUnit.TWD67, ParseResult.Reason.EMPTY);
    } else {
      lastTwd67Parse = parser.parseTwd67(e, n, zone);
    }
    renderError(lastTwd67Parse, errorTwd67);
    refreshSubmitEnabled();
  }

  private void renderError(ParseResult result, TextView errorView) {
    if (result == null) {
      errorView.setVisibility(View.GONE);
      errorView.setText("");
      return;
    }
    if (result.isOk()) {
      errorView.setVisibility(View.GONE);
      errorView.setText("");
      return;
    }
    if (result.isInvalid()) {
      ParseResult.Invalid inv = (ParseResult.Invalid) result;
      if (inv.reason() == ParseResult.Reason.EMPTY) {
        errorView.setVisibility(View.GONE);
        errorView.setText("");
        return;
      }
      errorView.setText(localiseReason(inv.reason()));
      errorView.setVisibility(View.VISIBLE);
      return;
    }
    // OutOfRange
    errorView.setText(localisedContext.getString(R.string.goto_err_out_of_range));
    errorView.setVisibility(View.VISIBLE);
  }

  private void refreshSubmitEnabled() {
    boolean coordOk = activeTabParse() != null && activeTabParse().isOk();
    submitButton.setEnabled(coordOk);
    // The ATAK-picker button shares Submit's enable rule: the active tab must parse cleanly.
    // markerMode does not gate it — picker delegation always uses ATAK's currently-active pallet.
    atakPickerButton.setEnabled(coordOk);
  }

  private ParseResult activeTabParse() {
    switch (activeTab) {
      case TAIPOWER:
        return lastTaipowerParse;
      case TWD97:
        return lastTwd97Parse;
      case TWD67:
      default:
        return lastTwd67Parse;
    }
  }

  // ----------- submit -----------

  private void onSubmit() {
    if (!submitInFlight.compareAndSet(false, true)) return;
    try {
      ParseResult r = activeTabParse();
      if (r == null || !r.isOk()) return;
      ParseResult.Ok ok = (ParseResult.Ok) r;
      submitOk(ok.wgs84(), ok.input());
    } finally {
      submitInFlight.set(false);
    }
  }

  private void submitOk(Wgs84 wgs84, CoordinateInput input) {
    persistLastInput(input);

    // US4: append to Recent (capacity-10, FIFO, dedup on (unit, rawValue)).
    if (recentStore != null) {
      recentStore.append(RecentEntry.fromCoordinateInput(input, System.currentTimeMillis()));
    }

    // Move the camera to the resolved destination — X/Y only. Zoom (Z) and other camera attributes
    // are preserved per the user's "GoTo 不能隨意改變 Z value 只能改變 X Y" directive. The operator
    // drops markers (waypoint / Mission Point / SPI / etc.) via ATAK's standard long-press →
    // radial menu at the destination; zero new post-submit affordance is introduced here.
    GeoPoint dest = GeoPoint.createMutable();
    dest.set(wgs84.latitudeDeg(), wgs84.longitudeDeg());
    try {
      CameraController.Programmatic.panTo(mapView.getRenderer3(), dest, /*animate*/ false);
    } catch (Throwable t) {
      Log.w(TAG, "camera pan failed", t);
    }
    Log.d(
        TAG,
        "Panned to "
            + input.displayString()
            + " ("
            + input.unit()
            + ") → "
            + wgs84.latitudeDeg()
            + ", "
            + wgs84.longitudeDeg());

    // If the operator picked a Drop-{type} marker mode, drop the marker via ATAK's standard
    // PlacePointTool.MarkerCreator using the same minimalist pattern the helloworld SDK sample
    // uses in SpeechPointDropper.pointPlotter: only UID + type + callsign, then placePoint().
    //
    // Earlier attempts that added setHow("h-g-i-g-o") and setMetaString("entry", "user") made
    // ATAK treat the marker as if it had come in via CoT from an external source, which
    // suppressed the long-press radial's Delete affordance. The minimalist call below lets
    // PlacePointTool's internal defaults populate the "user-placed" metadata correctly so the
    // resulting marker behaves like ATAK's own long-press drop-pin (movable + editable +
    // removable, standard radial menu).
    //
    // Constitution VI: SDK call wrapped — any fault must NEVER take down ATAK.
    if (markerMode.dropsMarker()) {
      try {
        String callsign = input.unit().name() + " " + input.displayString();
        new com.atakmap.android.user.PlacePointTool.MarkerCreator(dest)
            .setUid(UUID.randomUUID().toString())
            .setType(markerMode.cotType())
            .setCallsign(callsign)
            .placePoint();
        Log.d(TAG, "Dropped " + markerMode + " marker at " + callsign);
      } catch (Throwable t) {
        Log.w(TAG, "marker placement failed (" + markerMode + ")", t);
      }
    }

    showSubmitConfirmationToast(wgs84, input);
    broadcastNavCompleted(wgs84, input);
    closeDropDown();
  }

  // ----------- ATAK picker delegation (Option B — ADR-0011 D8) -----------

  /**
   * "Drop via ATAK picker" button handler. Mirrors Submit's housekeeping (persist + recent + pan +
   * close), then closes our drop-down and broadcasts {@link EnterLocationDropDownReceiver#START} so
   * ATAK's native enter-location pane takes the stage. The operator picks a pallet/icon from there
   * and drops a marker via ATAK's own UX — the map is already centred on the typed coordinate.
   *
   * <p><b>Why not {@code processPoint}?</b> The initial Option B implementation tried {@code
   * EnterLocationDropDownReceiver.getInstance(mapView).processPoint(GeoPointMetaData.wrap(p))} as a
   * one-click drop, falling back to opening the pane on null. In practice {@code processPoint}
   * almost always returns null in our calling context: the receiver's {@code _selectedIconPallet}
   * defaults to {@code Icon2525cPallet}, but the pallet only assembles a placement intent after the
   * operator has chosen a concrete SIDC code (or, for {@code UserIconPallet}, an icon cell) within
   * its own UI. Without a selected concrete icon, {@code pallet.getPointPlacedIntent(...)} returns
   * an empty intent, {@code processPoint} logs <i>"Unable to process point with empty intent"</i>
   * and pops a user-visible English toast. Removing the {@code processPoint} attempt eliminates
   * that spurious toast every time the operator taps our button.
   *
   * <p>Constitution VI: every SDK call wrapped — any fault must NEVER take down ATAK.
   */
  private void onAtakPicker() {
    if (!submitInFlight.compareAndSet(false, true)) return;
    try {
      ParseResult r = activeTabParse();
      if (r == null || !r.isOk()) return;
      ParseResult.Ok ok = (ParseResult.Ok) r;
      Wgs84 wgs84 = ok.wgs84();
      CoordinateInput input = ok.input();

      persistLastInput(input);
      if (recentStore != null) {
        recentStore.append(RecentEntry.fromCoordinateInput(input, System.currentTimeMillis()));
      }

      // Pan first — X/Y only, Z preserved — so when ATAK's pane opens the map is already centred
      // on the typed coordinate and the operator's tap-to-drop lands there.
      GeoPoint dest = GeoPoint.createMutable();
      dest.set(wgs84.latitudeDeg(), wgs84.longitudeDeg());
      try {
        CameraController.Programmatic.panTo(mapView.getRenderer3(), dest, /*animate*/ false);
      } catch (Throwable t) {
        Log.w(TAG, "camera pan failed", t);
      }

      // Close our drop-down so ATAK's drop-down can take the stage without stacking.
      closeDropDown();

      try {
        AtakBroadcast.getInstance().sendBroadcast(new Intent(EnterLocationDropDownReceiver.START));
        Log.d(
            TAG,
            "Opened ATAK enter-location pane @ "
                + wgs84.latitudeDeg()
                + ", "
                + wgs84.longitudeDeg()
                + " ("
                + input.unit()
                + ")");
      } catch (Throwable t) {
        Log.w(TAG, "open ATAK enter-location pane failed", t);
      }

      broadcastNavCompleted(wgs84, input);
    } finally {
      submitInFlight.set(false);
    }
  }

  // ----------- shared post-action helpers -----------

  /** Persist the active-tab last-submitted tuple so the next open pre-fills it (FR-014). */
  private void persistLastInput(CoordinateInput input) {
    prefs.setGotoLastUnit(input.unit());
    if (input instanceof CoordinateInput.Taipower) {
      prefs.setGotoLastTaipower(((CoordinateInput.Taipower) input).rawValue());
    } else if (input instanceof CoordinateInput.Twd97) {
      CoordinateInput.Twd97 t = (CoordinateInput.Twd97) input;
      prefs.setGotoLastTwd97(t.easting(), t.northing(), t.zone());
    } else if (input instanceof CoordinateInput.Twd67) {
      CoordinateInput.Twd67 t = (CoordinateInput.Twd67) input;
      prefs.setGotoLastTwd67(t.easting(), t.northing(), t.zone());
    }
  }

  /** Show the FR-010 confirmation toast (with zone-119 suffix when applicable). */
  private void showSubmitConfirmationToast(Wgs84 wgs84, CoordinateInput input) {
    int resolvedZone = resolvedZoneOf(input);
    String unitLabel = unitLabel(input.unit());
    String unitWithZone =
        resolvedZone == 119
            ? unitLabel
                + " "
                + String.format(
                    Locale.ROOT, localisedContext.getString(R.string.goto_zone_suffix), 119)
            : unitLabel;
    String toastMsg =
        String.format(
            Locale.ROOT,
            localisedContext.getString(R.string.goto_confirmation_toast),
            unitWithZone,
            wgs84.latitudeDeg(),
            wgs84.longitudeDeg());
    Toast.makeText(pluginContext, toastMsg, Toast.LENGTH_LONG).show();
  }

  /** Broadcast the outbound NAV_COMPLETED intent so downstream observers can react. */
  private void broadcastNavCompleted(Wgs84 wgs84, CoordinateInput input) {
    Intent done = new Intent(TwCoordGotoIntents.ACTION_GOTO_NAV_COMPLETED);
    done.putExtra(TwCoordGotoIntents.EXTRA_UNIT, input.unit().name());
    done.putExtra(TwCoordGotoIntents.EXTRA_LAT, wgs84.latitudeDeg());
    done.putExtra(TwCoordGotoIntents.EXTRA_LON, wgs84.longitudeDeg());
    done.putExtra(TwCoordGotoIntents.EXTRA_RAW_VALUE, input.displayString());
    AtakBroadcast.getInstance().sendBroadcast(done);
  }

  private void closeDropDown() {
    if (dropDownCloser != null) {
      try {
        dropDownCloser.run();
      } catch (Exception e) {
        Log.w(TAG, "dropdown close failed", e);
      }
    }
  }

  private static int resolvedZoneOf(CoordinateInput input) {
    if (input instanceof CoordinateInput.Twd97) return ((CoordinateInput.Twd97) input).zone();
    if (input instanceof CoordinateInput.Twd67) return ((CoordinateInput.Twd67) input).zone();
    return 121; // Taipower is main-island only.
  }

  // ----------- helpers -----------

  private String unitLabel(CoordinateUnit unit) {
    switch (unit) {
      case TAIPOWER:
        return localisedContext.getString(R.string.unit_tag_taipower);
      case TWD97:
        return localisedContext.getString(R.string.unit_tag_twd97);
      case TWD67:
        return localisedContext.getString(R.string.unit_tag_twd67);
      default:
        return unit.name();
    }
  }

  private String localiseReason(ParseResult.Reason reason) {
    int resId;
    switch (reason) {
      case EMPTY:
        resId = R.string.goto_err_empty;
        break;
      case BAD_LENGTH:
        resId = R.string.goto_err_bad_length;
        break;
      case BAD_LETTER:
        resId = R.string.goto_err_bad_letter;
        break;
      case RESERVED_LETTER_YZ:
        resId = R.string.goto_err_reserved_letter_yz;
        break;
      case BAD_ZONE:
        resId = R.string.goto_err_bad_zone;
        break;
      case NON_DIGIT:
      default:
        resId = R.string.goto_err_non_digit;
        break;
    }
    return localisedContext.getString(resId);
  }

  private static String textOf(EditText e) {
    CharSequence s = e.getText();
    return s == null ? "" : s.toString();
  }

  private static Integer parseInt(String s) {
    if (s == null || s.isEmpty()) return null;
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String intOrEmpty(int v) {
    return v == 0 ? "" : Integer.toString(v);
  }

  private static int currentZone(RadioButton z121Button) {
    return z121Button.isChecked() ? 121 : 119;
  }

  private static void applyZoneRadio(RadioButton z121, RadioButton z119, int zone) {
    z121.setChecked(zone != 119);
    z119.setChecked(zone == 119);
  }
}
