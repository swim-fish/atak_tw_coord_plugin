package com.atakmap.android.twcoord;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.DisplayLine;
import com.atakmap.android.twcoord.coord.Formatter;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.i18n.LocaleOverride;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.android.twcoord.prefs.PreferenceStore;
import com.atakmap.android.twcoord.prefs.UserPreference;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.maps.coords.GeoPoint;
import java.util.Locale;

/**
 * Hub of all listener wiring for US1/US2/US3. Owns the widget, the preference store, and the
 * self-marker debouncer; rebuilds the localised Context whenever the user toggles UI language;
 * re-renders both rows on every inbound event or preference change.
 */
public class TwCoordMapComponent extends AbstractMapComponent {

  private static final String PREF_KEY = "tw_coord_settings";

  /** Action fired by the Tools-menu icon (see TwCoordTool constructor). */
  static final String ACTION_SHOW_PLUGIN = "com.atakmap.android.twcoord.SHOW_PLUGIN";

  private static final long SELF_TICK_MS = 1_000L;

  private Context pluginContext;
  private Context localisedPluginContext;
  private MapView mapView;
  private TwCoordWidget widget;
  private PreferenceStore prefs;
  private TwCoordPreferenceFragment prefFragment;
  private SelfMarkerSubscriber selfSub;
  private Handler ui;

  private final CoordinateConverter converter = new CoordinateConverter();
  private final Formatter formatter = new Formatter();
  private Formatter.Strings strings;

  private UserPreference activePrefs = UserPreference.defaults();
  private DisplayLine lastMapLine;
  private DisplayLine lastMeLine;
  private DisplayLine lastTargetLine;
  private MapItem lastClickedTarget;
  private boolean meStale = false;
  private boolean mePermissionDenied = false;

  private final MapEventDispatcher.MapEventDispatchListener mapCentreListener =
      event -> renderMapCentre();

  /**
   * CoT target click — render the tapped item's coordinate in the top-right row. ATAK fires
   * ITEM_CLICK every time the user taps any MapItem with a position; we keep the most-recent one as
   * the "current target" until either another item is tapped or the map itself is tapped (cleared
   * via mapClickListener).
   */
  private final MapEventDispatcher.MapEventDispatchListener targetClickListener =
      event -> {
        MapItem item = event.getItem();
        if (item instanceof PointMapItem) {
          lastClickedTarget = item;
          renderTargetFrom((PointMapItem) item);
        }
      };

  /** Tapping the map (not an item) clears the target row. */
  private final MapEventDispatcher.MapEventDispatchListener mapClickListener =
      event -> {
        lastClickedTarget = null;
        lastTargetLine = null;
        if (widget != null) widget.render(null, null, null);
      };

  /**
   * Drives the ME row from ANY ATAK location provider (GPS, network, fused, external CoT, Bluetooth
   * GPS, etc.) by hooking the unified self-marker point. ATAK funnels every active location source
   * into this single marker; observing it covers them all.
   */
  private final PointMapItem.OnPointChangedListener selfPointListener =
      item -> {
        if (item == null) return;
        GeoPoint p = item.getPoint();
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
          widget.render(lastMapLine, lastMeLine, lastTargetLine);
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
          widget.render(lastMapLine, lastMeLine, lastTargetLine);
        }
      };

  /**
   * Tools-menu tap handler. ATAK fires {@link #ACTION_SHOW_PLUGIN} when the user taps the "TW
   * Coordinates" icon under Tools; we cycle through four states:
   *
   * <pre>
   *   Off → Taipower → TWD97 → TWD67 → Off → Taipower → ...
   * </pre>
   *
   * <p>The unit change also writes to {@link PreferenceStore} so the settings page reflects the
   * cycle position.
   */
  private final BroadcastReceiver toggleReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
          if (widget == null || prefs == null || strings == null) return;

          boolean wasVisible = widget.isVisible();
          CoordinateUnit current = activePrefs.coordUnit();

          CoordinateUnit nextUnit;
          boolean nextVisible;
          if (!wasVisible) {
            nextUnit = CoordinateUnit.TAIPOWER;
            nextVisible = true;
          } else {
            switch (current) {
              case TAIPOWER:
                nextUnit = CoordinateUnit.TWD97;
                nextVisible = true;
                break;
              case TWD97:
                nextUnit = CoordinateUnit.TWD67;
                nextVisible = true;
                break;
              case TWD67:
              default:
                nextUnit = current;
                nextVisible = false;
                break;
            }
          }

          widget.setVisible(nextVisible);
          if (nextVisible && nextUnit != current) {
            // Triggers prefListener → renderMapCentre + renderMeFromLastKnown with new unit.
            prefs.setCoordinateUnit(nextUnit);
          }

          String msg;
          if (nextVisible) {
            msg = pluginContext.getString(R.string.toast_widget_cycle, unitTagFor(nextUnit));
          } else {
            msg = pluginContext.getString(R.string.toast_widget_hidden);
          }
          Toast.makeText(pluginContext, msg, Toast.LENGTH_SHORT).show();
        }
      };

  private final Runnable selfTick =
      new Runnable() {
        @Override
        public void run() {
          // Poll the self-marker every tick. ATAK only fires OnPointChangedListener when the
          // position actually changes, but for a stationary tablet the position is held
          // constant — yet still valid. Polling keeps the ME row live in both cases.
          if (mapView != null && selfSub != null) {
            Marker self = mapView.getSelfMarker();
            if (self != null) {
              GeoPoint p = self.getPoint();
              if (p != null
                  && Math.abs(p.getLatitude()) > 1e-6
                  && Math.abs(p.getLongitude()) > 1e-6) {
                selfSub.onEvent(
                    new Wgs84(
                        p.getLatitude(),
                        p.getLongitude(),
                        System.currentTimeMillis(),
                        Wgs84.Source.DEVICE_LOCATION));
              }
            }
          }
          if (selfSub != null) selfSub.tickStaleCheck();
          if (ui != null) ui.postDelayed(this, SELF_TICK_MS);
        }
      };

  @Override
  public void onCreate(Context context, Intent intent, MapView view) {
    this.pluginContext = context;
    this.mapView = view;
    this.ui = new Handler(Looper.getMainLooper());

    // SharedPreferences MUST live in the ATAK process's data dir; the plugin's own data dir
    // does not exist (the plugin runs hosted in ATAK's process, see SharedPreferencesImpl
    // "Couldn't create directory" warning when using plugin context).
    this.prefs = new PreferenceStore(view.getContext());
    this.activePrefs = prefs.snapshot();
    rebuildLocalisedContext();

    this.widget = new TwCoordWidget(view);
    widget.attach();

    this.selfSub =
        new SelfMarkerSubscriber(
            System::currentTimeMillis, 1_000L, activePrefs.staleFixThresholdMs(), subListener);

    // ATAK-CIV 5.7.0.3 fires MAP_SCROLL continuously during a user drag, MAP_SETTLED once when
    // the drag finishes, MAP_SCALE on zoom, and MAP_MOVED on programmatic recentre. Subscribe
    // to all four so the readout follows every kind of viewport change.
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_MOVED, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SCROLL, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SETTLED, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SCALE, mapCentreListener);

    view.getMapEventDispatcher().addMapEventListener(MapEvent.ITEM_CLICK, targetClickListener);
    view.getMapEventDispatcher()
        .addMapEventListener(MapEvent.ITEM_CONFIRMED_CLICK, targetClickListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_CLICK, mapClickListener);

    Marker selfNow = view.getSelfMarker();
    if (selfNow != null) {
      selfNow.addOnPointChangedListener(selfPointListener);
    }

    prefs.registerOnChange(prefListener);
    ui.postDelayed(selfTick, SELF_TICK_MS);

    AtakBroadcast.DocumentedIntentFilter toggleFilter = new AtakBroadcast.DocumentedIntentFilter();
    toggleFilter.addAction(ACTION_SHOW_PLUGIN);
    AtakBroadcast.getInstance().registerReceiver(toggleReceiver, toggleFilter);

    // Initial paint so the widget is not blank.
    renderMapCentre();
    // Seed the me-row from the current self-marker position so the user sees something even
    // before the first ITEM_REFRESH event fires. If the marker has no useful location yet,
    // fall through to a `no fix` row.
    seedSelfRow();

    // Settings entry under Tool Preferences (FR-004 / US3).
    prefFragment = new TwCoordPreferenceFragment(pluginContext);
    ToolsPreferenceFragment.register(
        new ToolsPreferenceFragment.ToolPreference(
            pluginContext.getString(R.string.pref_screen_title),
            pluginContext.getString(R.string.app_desc),
            PREF_KEY,
            pluginContext.getResources().getDrawable(R.drawable.ic_tw_coord),
            prefFragment));
  }

  @Override
  protected void onDestroyImpl(Context context, MapView view) {
    try {
      AtakBroadcast.getInstance().unregisterReceiver(toggleReceiver);
    } catch (IllegalArgumentException ignored) {
      // Receiver was never registered (onCreate aborted) — nothing to do.
    }
    ToolsPreferenceFragment.unregister(PREF_KEY);
    if (ui != null) ui.removeCallbacks(selfTick);
    if (view != null) {
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_MOVED, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_SCROLL, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_SETTLED, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_SCALE, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.ITEM_CLICK, targetClickListener);
      view.getMapEventDispatcher()
          .removeMapEventListener(MapEvent.ITEM_CONFIRMED_CLICK, targetClickListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_CLICK, mapClickListener);
      Marker selfNow = view.getSelfMarker();
      if (selfNow != null) {
        selfNow.removeOnPointChangedListener(selfPointListener);
      }
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

  private void seedSelfRow() {
    if (mapView == null || widget == null || strings == null) return;
    Marker self = mapView.getSelfMarker();
    if (self != null) {
      GeoPoint p = self.getPoint();
      if (p != null && Math.abs(p.getLatitude()) > 1e-6 && Math.abs(p.getLongitude()) > 1e-6) {
        Wgs84 fix =
            new Wgs84(
                p.getLatitude(),
                p.getLongitude(),
                System.currentTimeMillis(),
                Wgs84.Source.DEVICE_LOCATION);
        if (selfSub != null) {
          selfSub.onEvent(fix);
          return;
        }
      }
    }
    // No usable self-marker position yet — render an explicit "no fix" so the row is not
    // a placeholder dash forever.
    mePermissionDenied = !hasLocationPermission();
    DisplayLine.State state =
        mePermissionDenied ? DisplayLine.State.NO_PERMISSION : DisplayLine.State.NO_FIX;
    String value = mePermissionDenied ? strings.stateNoPermission() : strings.stateNoFix();
    lastMeLine =
        new DisplayLine(strings.labelMe(), unitTagFor(activePrefs.coordUnit()), value, "", state);
    widget.render(lastMapLine, lastMeLine, lastTargetLine);
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
    widget.render(lastMapLine, lastMeLine, lastTargetLine);
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
    widget.render(lastMapLine, lastMeLine, lastTargetLine);
  }

  private void renderTargetFrom(PointMapItem item) {
    if (item == null || widget == null || strings == null) return;
    GeoPoint p = item.getPoint();
    if (p == null) return;
    Wgs84 fix =
        new Wgs84(
            p.getLatitude(), p.getLongitude(), System.currentTimeMillis(), Wgs84.Source.COT_TARGET);
    ConversionResult result = converter.convert(fix, activePrefs.coordUnit());
    lastTargetLine =
        formatter.format(Wgs84.Source.COT_TARGET, result, activePrefs.coordUnit(), strings);
    widget.render(null, null, lastTargetLine);
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
