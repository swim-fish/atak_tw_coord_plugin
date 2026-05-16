package com.atakmap.android.twpower;

import android.content.Context;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twpower.coord.DisplayLine;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.RootLayoutWidget;

/**
 * On-map readout overlay (TwPowerWidget). T034 skeleton: two-row layout, top-right anchor.
 * render() and tap-to-copy come in T035 / T036 / T051.
 */
public final class TwPowerWidget extends MapWidget {

  private final Context pluginContext;
  private final MapView mapView;
  private LinearLayoutWidget anchor;

  public TwPowerWidget(Context pluginContext, MapView mapView) {
    this.pluginContext = pluginContext;
    this.mapView = mapView;
  }

  /** Attach this widget to the top-right corner of ATAK's root layout. */
  public void attach() {
    RootLayoutWidget root = (RootLayoutWidget) mapView.getComponentExtra("rootLayoutWidget");
    anchor = root.getLayout(RootLayoutWidget.TOP_RIGHT);
    anchor.addWidget(this);
  }

  /** Remove this widget from its anchor (called from MapComponent.onDestroyImpl). */
  public void detach() {
    if (anchor != null) {
      anchor.removeWidget(this);
      anchor = null;
    }
  }

  /**
   * Update the two visible rows. Both arguments may be the previous values; the widget MUST
   * invalidate only when at least one differs field-by-field. T035/T036 will flesh out actual
   * rendering via TextWidget children.
   */
  public void render(DisplayLine mapCentreLine, DisplayLine selfLine) {
    // TODO(T035): paint two-row text via TextWidget children; trigger invalidate() only on change.
  }
}
