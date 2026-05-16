package com.atakmap.android.twpower;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twpower.coord.ConversionResult;
import com.atakmap.android.twpower.coord.CoordinateConverter;
import com.atakmap.android.twpower.coord.CoordinateUnit;
import com.atakmap.android.twpower.coord.DisplayLine;
import com.atakmap.android.twpower.coord.Formatter;
import com.atakmap.android.twpower.coord.Wgs84;
import com.atakmap.android.twpower.i18n.LocaleOverride;
import com.atakmap.android.twpower.plugin.R;
import com.atakmap.android.twpower.prefs.PreferenceStore;
import com.atakmap.android.twpower.prefs.UserPreference;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.maps.coords.GeoPoint;
import java.util.Locale;

/**
 * Hub of all listener wiring for US1/US2/US3. Owns the widget, the preference store, and the
 * self-marker debouncer; rebuilds the localised Context whenever the user toggles UI language;
 * re-renders both rows on every inbound event or preference change.
 */
public class TwPowerMapComponent extends AbstractMapComponent {

  private static final String PREF_KEY = "tw_power_settings";
  private static final long SELF_TICK_MS = 1_000L;

  private Context pluginContext;
  private Context localisedPluginContext;
  private MapView mapView;
  private TwPowerWidget widget;
  private PreferenceStore prefs;
  private TwPowerPreferenceFragment prefFragment;
  private SelfMarkerSubscriber selfSub;
  private Handler ui;

  private final CoordinateConverter converter = new CoordinateConverter();
  private final Formatter formatter = new Formatter();
  private Formatter.Strings strings;

  private UserPreference activePrefs = UserPreference.defaults();
  private DisplayLine lastMapLine;
  private DisplayLine lastMeLine;
  private boolean meStale = false;
  private boolean mePermissionDenied = false;

  private final MapEventDispatcher.MapEventDispatchListener mapCentreListener =
      event -> renderMapCentre();

  private final MapEventDispatcher.MapEventDispatchListener selfItemListener =
      event -> {
        MapItem item = event.getItem();
        MapItem self = mapView != null ? mapView.getSelfMarker() : null;
        if (item == null || self == null) return;
        if (!self.getUID().equals(item.getUID())) return;
        if (!(item instanceof com.atakmap.android.maps.Marker)) return;
        GeoPoint p = ((com.atakmap.android.maps.Marker) item).getPoint();
        if (p == null) return;
        Wgs84 fix =
            new Wgs84(
                p.getLatitude(),
                p.getLongitude(),
                System.currentTimeMillis(),
                Wgs84.Source.DEVICE_LOCATION);
        if (selfSub != null) selfSub.onEvent(fix);
      };

  private final PreferenceStore.Listener prefListener =
      snap -> {
        boolean languageChanged = snap.uiLanguage() != activePrefs.uiLanguage();
        activePrefs = snap;
        if (languageChanged) rebuildLocalisedContext();
        // Repaint both rows with the new unit / language.
        renderMapCentre();
        renderMeFromLastKnown();
      };

  private final SelfMarkerSubscriber.Listener subListener =
      new SelfMarkerSubscriber.Listener() {
        @Override
        public void onFreshFix(Wgs84 fix) {
          meStale = false;
          ConversionResult result = converter.convert(fix, activePrefs.coordUnit());
          DisplayLine line =
              formatter.format(
                  Wgs84.Source.DEVICE_LOCATION, result, activePrefs.coordUnit(), strings);
          lastMeLine = line;
          widget.render(lastMapLine, lastMeLine);
        }

        @Override
        public void onStale() {
          meStale = true;
          mePermissionDenied = !hasLocationPermission();
          DisplayLine.State state =
              mePermissionDenied ? DisplayLine.State.NO_PERMISSION : DisplayLine.State.NO_FIX;
          String value = mePermissionDenied ? strings.stateNoPermission() : strings.stateNoFix();
          lastMeLine =
              new DisplayLine(
                  strings.labelMe(), unitTagFor(activePrefs.coordUnit()), value, "", state);
          widget.render(lastMapLine, lastMeLine);
        }
      };

  private final Runnable selfTick =
      new Runnable() {
        @Override
        public void run() {
          if (selfSub != null) selfSub.tickStaleCheck();
          if (ui != null) ui.postDelayed(this, SELF_TICK_MS);
        }
      };

  @Override
  public void onCreate(Context context, Intent intent, MapView view) {
    this.pluginContext = context;
    this.mapView = view;
    this.ui = new Handler(Looper.getMainLooper());

    this.prefs = new PreferenceStore(context);
    this.activePrefs = prefs.snapshot();
    rebuildLocalisedContext();

    this.widget = new TwPowerWidget(view);
    widget.attach();

    this.selfSub =
        new SelfMarkerSubscriber(
            System::currentTimeMillis, 1_000L, activePrefs.staleFixThresholdMs(), subListener);

    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_MOVED, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SCALE, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_REFRESH, selfItemListener);

    prefs.registerOnChange(prefListener);
    ui.postDelayed(selfTick, SELF_TICK_MS);

    // Initial paint so the widget is not blank.
    renderMapCentre();

    // Settings entry under Tool Preferences (FR-004 / US3).
    prefFragment = new TwPowerPreferenceFragment(pluginContext);
    ToolsPreferenceFragment.register(
        new ToolsPreferenceFragment.ToolPreference(
            pluginContext.getString(R.string.pref_screen_title),
            pluginContext.getString(R.string.app_desc),
            PREF_KEY,
            pluginContext.getResources().getDrawable(R.drawable.ic_tw_power),
            prefFragment));
  }

  @Override
  protected void onDestroyImpl(Context context, MapView view) {
    ToolsPreferenceFragment.unregister(PREF_KEY);
    if (ui != null) ui.removeCallbacks(selfTick);
    if (view != null) {
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_MOVED, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_SCALE, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.ITEM_REFRESH, selfItemListener);
    }
    if (prefs != null) {
      prefs.unregisterOnChange(prefListener);
      prefs.dispose();
    }
    if (widget != null) {
      widget.detach();
      widget = null;
    }
    this.selfSub = null;
    this.ui = null;
    this.mapView = null;
    this.pluginContext = null;
    this.localisedPluginContext = null;
  }

  private void rebuildLocalisedContext() {
    localisedPluginContext =
        LocaleOverride.contextFor(pluginContext, activePrefs.uiLanguage(), Locale.getDefault());
    strings = new ResourceStrings(localisedPluginContext);
  }

  private void renderMapCentre() {
    if (mapView == null || widget == null || strings == null) return;
    GeoPoint centre = mapView.getPoint().get();
    if (centre == null) return;
    Wgs84 fix =
        new Wgs84(
            centre.getLatitude(),
            centre.getLongitude(),
            System.currentTimeMillis(),
            Wgs84.Source.MAP_CENTRE);
    ConversionResult result = converter.convert(fix, activePrefs.coordUnit());
    DisplayLine line =
        formatter.format(Wgs84.Source.MAP_CENTRE, result, activePrefs.coordUnit(), strings);
    lastMapLine = line;
    widget.render(lastMapLine, lastMeLine);
  }

  /**
   * Re-render the me row after a preference change, reusing the last fix if any. If we have no me
   * state yet (no GPS event ever arrived), leave the row blank.
   */
  private void renderMeFromLastKnown() {
    if (widget == null || strings == null) return;
    if (lastMeLine == null) return;
    // Easiest: re-issue the cached me-line through the formatter using the new unit/language.
    // We don't carry the original Wgs84, so for OK rows we keep the previous value; for NO_FIX /
    // NO_PERMISSION rows we just relabel.
    if (lastMeLine.state() == DisplayLine.State.NO_FIX
        || lastMeLine.state() == DisplayLine.State.NO_PERMISSION) {
      DisplayLine.State state =
          mePermissionDenied ? DisplayLine.State.NO_PERMISSION : DisplayLine.State.NO_FIX;
      String value = mePermissionDenied ? strings.stateNoPermission() : strings.stateNoFix();
      lastMeLine =
          new DisplayLine(strings.labelMe(), unitTagFor(activePrefs.coordUnit()), value, "", state);
    } else {
      // OK row: rebuild label/unitTag wording in case language changed; keep numeric value.
      lastMeLine =
          new DisplayLine(
              strings.labelMe(),
              unitTagFor(activePrefs.coordUnit()),
              lastMeLine.value(),
              lastMeLine.fallback(),
              lastMeLine.state());
    }
    widget.render(lastMapLine, lastMeLine);
  }

  private String unitTagFor(CoordinateUnit u) {
    switch (u) {
      case TAIPOWER:
        return strings.unitTagTaipower();
      case TWD97:
        return strings.unitTagTwd97();
      case TWD67:
        return strings.unitTagTwd67();
      default:
        throw new IllegalStateException();
    }
  }

  private boolean hasLocationPermission() {
    if (pluginContext == null) return false;
    int granted = pluginContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    return granted == PackageManager.PERMISSION_GRANTED;
  }

  /** Strings backed by the localised plugin context. */
  private static final class ResourceStrings implements Formatter.Strings {
    private final Context ctx;

    ResourceStrings(Context ctx) {
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
