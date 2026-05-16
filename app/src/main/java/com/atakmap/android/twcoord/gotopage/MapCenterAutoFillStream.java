package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Subscribes to ATAK's map-centre event stream (the same {@code MAP_SCROLL / MAP_SETTLED /
 * MAP_SCALE / MAP_MOVED} family that the readout widget consumes) and republishes a {@link
 * MapCenterFix} to a single registered {@link Listener}. The receiver attaches the stream when the
 * DropDown opens and detaches it when it closes — no listeners outside the input page's lifetime.
 */
public final class MapCenterAutoFillStream {

  public interface Listener {
    void onMapCenterFix(MapCenterFix fix);
  }

  private final MapView mapView;
  private final CoordinateConverter converter;
  private final Listener listener;
  private boolean attached;

  private final MapEventDispatcher.MapEventDispatchListener eventListener = event -> emitFix();

  public MapCenterAutoFillStream(
      MapView mapView, CoordinateConverter converter, Listener listener) {
    this.mapView = mapView;
    this.converter = converter;
    this.listener = listener;
  }

  /** Idempotent: a second call is a no-op. */
  public synchronized void attach() {
    if (attached) return;
    MapEventDispatcher d = mapView.getMapEventDispatcher();
    d.addMapEventListener(MapEvent.MAP_MOVED, eventListener);
    d.addMapEventListener(MapEvent.MAP_SCROLL, eventListener);
    d.addMapEventListener(MapEvent.MAP_SETTLED, eventListener);
    d.addMapEventListener(MapEvent.MAP_SCALE, eventListener);
    attached = true;
    // Seed the listener immediately with the current map centre so the Auto Fill button reflects
    // reality from the moment the DropDown opens, not the moment the user first pans.
    emitFix();
  }

  /** Idempotent: safe to call even when detached or before the first attach. */
  public synchronized void detach() {
    if (!attached) return;
    MapEventDispatcher d = mapView.getMapEventDispatcher();
    d.removeMapEventListener(MapEvent.MAP_MOVED, eventListener);
    d.removeMapEventListener(MapEvent.MAP_SCROLL, eventListener);
    d.removeMapEventListener(MapEvent.MAP_SETTLED, eventListener);
    d.removeMapEventListener(MapEvent.MAP_SCALE, eventListener);
    attached = false;
  }

  /** Returns the current map-centre fix on demand (used by Auto Fill click handlers). */
  public MapCenterFix currentFix() {
    return buildFix();
  }

  private void emitFix() {
    if (listener != null) listener.onMapCenterFix(buildFix());
  }

  private MapCenterFix buildFix() {
    GeoPoint p = null;
    try {
      p = mapView.getPoint().get();
    } catch (Exception ignored) {
      // MapView may be tearing down; fall through to null fix.
    }
    if (p == null) return new MapCenterFix(null, false, false, false);
    Wgs84 fix =
        new Wgs84(
            p.getLatitude(), p.getLongitude(), System.currentTimeMillis(), Wgs84.Source.MAP_CENTRE);
    return MapCenterFix.of(fix, converter);
  }
}
