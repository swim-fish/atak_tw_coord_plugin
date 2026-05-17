package com.atakmap.android.twcoord.gotopage;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.android.twcoord.prefs.PreferenceStore;
import com.atakmap.coremap.log.Log;

/**
 * Owns the TW Coord GoTo input page DropDown. Opened by {@link TwCoordGotoIntents#ACTION_SHOW_GOTO}
 * broadcasts originating from either the second Tools-menu icon ({@link
 * com.atakmap.android.twcoord.plugin.TwCoordGotoTool}) or the "Open Coordinate Input" button on the
 * settings page.
 *
 * <p>This file handles the DropDown lifecycle and wires the view to the parser / marker store /
 * preferences. Submit / ATAK-picker delegation / Auto Fill / Recent logic lives in {@link
 * TwCoordGotoView}.
 */
public class TwCoordGotoReceiver extends DropDownReceiver implements OnStateListener {

  private static final String TAG = "TwCoordGotoReceiver";

  private final Context pluginContext;
  private final View view;
  private final CoordinateParser parser;
  private final PreferenceStore prefs;
  private final MapCenterAutoFillStream autoFillStream;
  private final RecentEntryStore recentStore;
  private final RecentEntryStore.Listener recentListener;

  /** Reference to the bound view controller; non-null while the DropDown is bound. */
  private TwCoordGotoView controller;

  /** In-memory cache of the in-progress input page state (FR-018: survives close-reopen). */
  private InputPageState inSessionState;

  public TwCoordGotoReceiver(MapView mapView, Context pluginContext, PreferenceStore prefs) {
    super(mapView);
    this.pluginContext = pluginContext;
    this.prefs = prefs;
    this.parser = new CoordinateParser();
    this.recentStore = new RecentEntryStore(prefs);
    LayoutInflater inflater = LayoutInflater.from(pluginContext);
    this.view = inflater.inflate(R.layout.tw_coord_goto, null);
    this.controller =
        new TwCoordGotoView(
            view, pluginContext, mapView, parser, prefs, recentStore, this::closeDropDown);
    this.autoFillStream =
        new MapCenterAutoFillStream(
            mapView,
            new CoordinateConverter(),
            fix -> {
              if (controller != null) controller.onMapCenterFix(fix);
            });
    this.recentListener =
        entries -> {
          if (controller != null) controller.onRecentEntriesChanged(entries);
        };
    recentStore.registerListener(recentListener);
  }

  @Override
  public void disposeImpl() {
    if (recentStore != null && recentListener != null) {
      recentStore.unregisterListener(recentListener);
    }
    controller = null;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    final String action = intent.getAction();
    if (action == null) return;
    if (!TwCoordGotoIntents.ACTION_SHOW_GOTO.equals(action)) return;

    // Idempotency: a second SHOW_GOTO while the page is open is a no-op.
    if (isVisible()) {
      Log.d(TAG, "SHOW_GOTO arrived while DropDown is already visible — no-op");
      return;
    }

    // Build the initial state. In-session draft (FR-018) wins; else FR-003 cross-session restore.
    InputPageState seed = inSessionState != null ? inSessionState : restoreFromPrefs(intent);
    if (controller != null) {
      controller.bind(seed);
    }

    // US5: attach the map-centre stream so the Auto Fill buttons' enabled state tracks reality
    // from the moment the page opens. Detached in onDropDownClose so we don't leak listeners.
    autoFillStream.attach();

    showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, this);
  }

  private InputPageState restoreFromPrefs(Intent intent) {
    // Optional intent extra forces the active tab — used by deep-link callers.
    String extraUnit = intent.getStringExtra(TwCoordGotoIntents.EXTRA_UNIT);
    CoordinateUnit activeTab;
    if (extraUnit != null) {
      try {
        activeTab = CoordinateUnit.valueOf(extraUnit);
      } catch (IllegalArgumentException e) {
        activeTab = prefs.getGotoLastUnit();
      }
    } else {
      activeTab = prefs.getGotoLastUnit();
    }
    return new InputPageState(
        activeTab,
        prefs.getGotoLastTaipower(),
        intOrEmpty(prefs.getGotoLastTwd97Easting()),
        intOrEmpty(prefs.getGotoLastTwd97Northing()),
        prefs.getGotoLastTwd97Zone(),
        intOrEmpty(prefs.getGotoLastTwd67Easting()),
        intOrEmpty(prefs.getGotoLastTwd67Northing()),
        prefs.getGotoLastTwd67Zone());
  }

  private static String intOrEmpty(int v) {
    return v == 0 ? "" : Integer.toString(v);
  }

  @Override
  public void onDropDownVisible(boolean v) {}

  @Override
  public void onDropDownSelectionRemoved() {}

  @Override
  public void onDropDownClose() {
    // Persist the in-memory state for the next open within the same ATAK process (FR-018).
    if (controller != null) {
      inSessionState = controller.snapshotState();
    }
    autoFillStream.detach();
  }

  @Override
  public void onDropDownSizeChanged(double width, double height) {}

  /** Visible for testing — clear cached in-session state. */
  public void resetInSessionState() {
    inSessionState = null;
  }
}
