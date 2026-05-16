package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import java.util.UUID;

/**
 * Owns a single ATAK {@link Marker} that represents the most recent successful GoTo destination.
 * Per FR-009, subsequent submissions MOVE this marker rather than spawning a new one.
 *
 * <p>Marker metadata follows the user-placed-CoT conventions documented in the helloworld SDK
 * sample: type {@code b-m-p-w-GOTO}, {@code entry=user}, {@code how=h-g-i-g-o}, {@code
 * removable/editable/movable=true} so ATAK's long-press affordance deletes the marker like any
 * other user pin.
 *
 * <p>Process-scoped — one instance lives on {@link com.atakmap.android.twcoord.TwCoordMapComponent}
 * and is shared by every DropDown open within the plugin's lifetime.
 */
public final class DestinationMarkerStore {

  private static final String TAG = "TwCoordGotoMarker";

  /**
   * Marker type — matches ATAK's user-placed-waypoint convention (cf. {@code SpeechBloodHound}).
   */
  private static final String MARKER_TYPE = "b-m-p-w-GOTO";

  private final MapView mapView;
  private final String uid;
  private Marker delegate;

  public DestinationMarkerStore(MapView mapView) {
    this.mapView = mapView;
    this.uid = UUID.randomUUID().toString();
  }

  /** Visible for tests / equality checks; stable across the plugin's lifetime. */
  public String uid() {
    return uid;
  }

  /**
   * Move the existing marker or create a new one at {@code target}. Idempotent for the in-flight
   * delegate: only creates one marker per plugin process.
   */
  public synchronized void moveOrCreate(Wgs84 target, CoordinateInput input) {
    GeoPoint pt = GeoPoint.createMutable();
    pt.set(target.latitudeDeg(), target.longitudeDeg());

    // If we previously created a marker but ATAK has since removed it from the map (the user
    // long-pressed → delete), the delegate's group will be null. Treat that as "no marker exists"
    // and create fresh.
    if (delegate != null && delegate.getGroup() == null) {
      delegate = null;
    }

    if (delegate == null) {
      Marker m = new Marker(pt, uid);
      m.setType(MARKER_TYPE);
      m.setMetaBoolean("removable", true);
      m.setMetaBoolean("editable", true);
      m.setMetaBoolean("movable", true);
      m.setMetaString("entry", "user");
      m.setMetaString("how", "h-g-i-g-o");
      m.setMetaString("callsign", callsign(input));
      m.setMetaString("twcoord_goto_unit", input.unit().name());
      m.setMetaString("twcoord_goto_raw", input.displayString());
      m.setTitle(callsign(input));
      MapGroup root = mapView.getRootGroup();
      root.addItem(m);
      delegate = m;
      Log.d(TAG, "Created destination marker " + uid + " at " + summarise(target));
    } else {
      delegate.setPoint(pt);
      delegate.setMetaString("callsign", callsign(input));
      delegate.setMetaString("twcoord_goto_unit", input.unit().name());
      delegate.setMetaString("twcoord_goto_raw", input.displayString());
      delegate.setTitle(callsign(input));
      Log.d(TAG, "Moved destination marker " + uid + " to " + summarise(target));
    }
  }

  /** Used during plugin teardown — best-effort removal from the map. */
  public synchronized void removeIfPresent() {
    if (delegate != null) {
      MapGroup g = delegate.getGroup();
      if (g != null) {
        g.removeItem(delegate);
      }
      delegate = null;
    }
  }

  public synchronized boolean hasMarker() {
    return delegate != null && delegate.getGroup() != null;
  }

  private static String callsign(CoordinateInput input) {
    return input.unit().name() + " " + input.displayString();
  }

  private static String summarise(Wgs84 fix) {
    return String.format(
        java.util.Locale.ROOT, "%.6f, %.6f", fix.latitudeDeg(), fix.longitudeDeg());
  }
}
