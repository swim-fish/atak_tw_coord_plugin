package com.atakmap.android.twpower;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twpower.coord.ConversionResult;
import com.atakmap.android.twpower.coord.CoordinateConverter;
import com.atakmap.android.twpower.coord.CoordinateUnit;
import com.atakmap.android.twpower.coord.DisplayLine;
import com.atakmap.android.twpower.coord.Formatter;
import com.atakmap.android.twpower.coord.Wgs84;
import com.atakmap.android.twpower.plugin.R;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * MapComponent wiring (T010 expanded by T035). Subscribes to MAP_BOUNDS_CHANGED, runs each event
 * through CoordinateConverter + Formatter, pushes the resulting DisplayLine into the widget. Self-
 * marker wiring (US2) and preference-fragment registration (US3) layer on top in later phases.
 */
public class TwPowerMapComponent extends AbstractMapComponent {

  private Context pluginContext;
  private MapView mapView;
  private TwPowerWidget widget;

  private final CoordinateConverter converter = new CoordinateConverter();
  private final Formatter formatter = new Formatter();
  private final Formatter.Strings strings = new ResourceStrings();

  // Default unit until US3 wires preference-driven selection.
  private CoordinateUnit activeUnit = CoordinateUnit.TWD97;

  private final MapEventDispatcher.MapEventDispatchListener mapCentreListener =
      new MapEventDispatcher.MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
          renderMapCentre();
        }
      };

  @Override
  public void onCreate(Context context, Intent intent, MapView view) {
    this.pluginContext = context;
    this.mapView = view;

    widget = new TwPowerWidget(view);
    widget.attach();

    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_MOVED, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SCALE, mapCentreListener);

    // Paint once on startup with the current centre so the widget is not blank for the first
    // few frames before the user pans.
    renderMapCentre();
  }

  @Override
  protected void onDestroyImpl(Context context, MapView view) {
    if (view != null) {
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_MOVED, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_SCALE, mapCentreListener);
    }
    if (widget != null) {
      widget.detach();
      widget = null;
    }
    this.mapView = null;
    this.pluginContext = null;
  }

  private void renderMapCentre() {
    if (mapView == null || widget == null) return;
    GeoPoint centre = mapView.getPoint().get();
    if (centre == null) return;

    Wgs84 fix =
        new Wgs84(
            centre.getLatitude(),
            centre.getLongitude(),
            System.currentTimeMillis(),
            Wgs84.Source.MAP_CENTRE);
    ConversionResult result = converter.convert(fix, activeUnit);
    DisplayLine line = formatter.format(Wgs84.Source.MAP_CENTRE, result, activeUnit, strings);
    widget.render(line, null);
  }

  /** Strings backed by the plugin's Android string resources. */
  private final class ResourceStrings implements Formatter.Strings {
    @Override
    public String labelMap() {
      return pluginContext.getString(R.string.label_map);
    }

    @Override
    public String labelMe() {
      return pluginContext.getString(R.string.label_me);
    }

    @Override
    public String unitTagTaipower() {
      return pluginContext.getString(R.string.unit_tag_taipower);
    }

    @Override
    public String unitTagTwd97() {
      return pluginContext.getString(R.string.unit_tag_twd97);
    }

    @Override
    public String unitTagTwd67() {
      return pluginContext.getString(R.string.unit_tag_twd67);
    }

    @Override
    public String stateOutOfRange() {
      return pluginContext.getString(R.string.state_out_of_range);
    }

    @Override
    public String stateNoFix() {
      return pluginContext.getString(R.string.state_no_fix);
    }

    @Override
    public String stateNoPermission() {
      return pluginContext.getString(R.string.state_no_permission);
    }
  }
}
