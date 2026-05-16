package com.atakmap.android.twpower;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Foundational skeleton (T010). Listener wiring is added in US1/US2/US3 phases. Holds the MapView
 * reference and the widget so later tasks can attach event listeners and re-render on inbound
 * events.
 */
public class TwPowerMapComponent extends AbstractMapComponent {

  private Context pluginContext;
  private MapView mapView;
  private TwPowerWidget widget;

  @Override
  public void onCreate(Context context, Intent intent, MapView view) {
    this.pluginContext = context;
    this.mapView = view;
    // Widget creation, listener registration, and preference-fragment registration happen in
    // tasks T034, T035, T047 respectively.
  }

  @Override
  protected void onDestroyImpl(Context context, MapView view) {
    if (widget != null) {
      widget.detach();
      widget = null;
    }
    this.mapView = null;
    this.pluginContext = null;
  }
}
