package com.atakmap.android.twcoord.nativeentry;

import android.content.Context;
import android.view.View;
import com.atakmap.android.gui.coordinateentry.CoordinateEntryCapability;
import com.atakmap.android.gui.coordinateentry.CoordinateEntryPane;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twcoord.address.lookup.AddressLookupService;
import com.atakmap.android.twcoord.address.lookup.NoDataAddressLookupService;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.prefs.PreferenceStore;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import java.util.Objects;

/** UI-thread-confined, idempotent owner of one native coordinate-entry registration. */
public final class NativeCoordinateEntryRegistrar {

  public enum State {
    STOPPED,
    START_PENDING,
    REGISTERED,
    REFRESH_PENDING,
    STOP_PENDING,
    FAILED
  }

  interface UiDispatcher {
    void post(Runnable runnable);
  }

  interface RegistryGateway {
    void register(CoordinateEntryPane pane);

    void unregister(CoordinateEntryPane pane);
  }

  interface PaneFactory {
    CoordinateEntryPane create();
  }

  public interface ContextProvider {
    Context get();
  }

  private static final String TAG = "NativeEntryRegistrar";

  private final UiDispatcher dispatcher;
  private final RegistryGateway gateway;
  private final PaneFactory paneFactory;

  private State state = State.STOPPED;
  private CoordinateEntryPane pane;
  private View refreshListenerView;
  private View.OnAttachStateChangeListener refreshDetachListener;
  private long generation;

  NativeCoordinateEntryRegistrar(
      UiDispatcher dispatcher, RegistryGateway gateway, PaneFactory paneFactory) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    this.gateway = Objects.requireNonNull(gateway, "gateway");
    this.paneFactory = Objects.requireNonNull(paneFactory, "paneFactory");
  }

  public static NativeCoordinateEntryRegistrar create(
      MapView mapView, Context pluginContext, PreferenceStore preferences) {
    Objects.requireNonNull(pluginContext, "pluginContext");
    return create(mapView, () -> pluginContext, preferences);
  }

  public static NativeCoordinateEntryRegistrar create(
      MapView mapView, ContextProvider contextProvider, PreferenceStore preferences) {
    return create(
        mapView,
        contextProvider,
        preferences,
        new NoDataAddressLookupService(runnable -> mapView.post(runnable)));
  }

  public static NativeCoordinateEntryRegistrar create(
      MapView mapView,
      ContextProvider contextProvider,
      PreferenceStore preferences,
      AddressLookupService lookupService) {
    return create(mapView, contextProvider, preferences, lookupService, () -> {});
  }

  public static NativeCoordinateEntryRegistrar create(
      MapView mapView,
      ContextProvider contextProvider,
      PreferenceStore preferences,
      AddressLookupService lookupService,
      Runnable managerNavigator) {
    Objects.requireNonNull(mapView, "mapView");
    Objects.requireNonNull(contextProvider, "contextProvider");
    Objects.requireNonNull(preferences, "preferences");
    Objects.requireNonNull(lookupService, "lookupService");
    Objects.requireNonNull(managerNavigator, "managerNavigator");
    return new NativeCoordinateEntryRegistrar(
        runnable -> mapView.post(runnable),
        new RegistryGateway() {
          @Override
          public void register(CoordinateEntryPane pane) {
            CoordinateEntryCapability.getInstance(mapView.getContext()).registerPane(pane);
          }

          @Override
          public void unregister(CoordinateEntryPane pane) {
            CoordinateEntryCapability.getInstance(mapView.getContext()).unregisterPane(pane);
          }
        },
        () ->
            new TaiwanCoordinateEntryPane(
                Objects.requireNonNull(contextProvider.get(), "contextProvider returned null"),
                mapView.getContext(),
                preferences,
                lookupService,
                managerNavigator,
                () -> currentMapAnchor(mapView)));
  }

  private static Wgs84 currentMapAnchor(MapView mapView) {
    GeoPoint point = mapView.getPoint().get();
    if (point == null
        || !Double.isFinite(point.getLatitude())
        || !Double.isFinite(point.getLongitude())
        || point.getLatitude() < -90.0
        || point.getLatitude() > 90.0
        || point.getLongitude() < -180.0
        || point.getLongitude() > 180.0) {
      return null;
    }
    return new Wgs84(
        point.getLatitude(),
        point.getLongitude(),
        System.currentTimeMillis(),
        Wgs84.Source.MAP_CENTRE);
  }

  public synchronized State state() {
    return state;
  }

  public synchronized void start() {
    if (state != State.STOPPED && state != State.FAILED) return;
    state = State.START_PENDING;
    long token = ++generation;
    dispatcher.post(() -> completeStart(token));
  }

  public synchronized void stop() {
    if (state == State.STOPPED || state == State.STOP_PENDING) return;
    long token = ++generation;
    if (state == State.START_PENDING && pane == null) {
      state = State.STOPPED;
      return;
    }
    state = State.STOP_PENDING;
    removeRefreshDetachListener();
    dispatcher.post(() -> completeStop(token));
  }

  /**
   * Completes exact-instance unregister/dispose synchronously when the component is already on the
   * ATAK UI thread during host teardown.
   */
  public synchronized void stopNowOnUiThread() {
    if (state == State.STOPPED) return;
    long token = ++generation;
    if (state == State.START_PENDING && pane == null) {
      state = State.STOPPED;
      return;
    }
    state = State.STOP_PENDING;
    removeRefreshDetachListener();
    completeStop(token);
  }

  public synchronized void refreshLocale() {
    if (state == State.REFRESH_PENDING) return;
    if (state != State.REGISTERED || pane == null) return;
    long token = ++generation;
    state = State.REFRESH_PENDING;
    View view;
    try {
      view = pane.getView();
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "Native coordinate-entry locale refresh view lookup failed", e);
      state = State.FAILED;
      return;
    }
    if (view != null && view.isAttachedToWindow()) {
      installRefreshDetachListener(view, token);
    } else {
      dispatcher.post(() -> completeRefresh(token));
    }
  }

  private void completeStart(long token) {
    synchronized (this) {
      if (token != generation || state != State.START_PENDING) return;
      CoordinateEntryPane candidate = null;
      try {
        candidate = Objects.requireNonNull(paneFactory.create(), "paneFactory returned null");
        gateway.register(candidate);
        pane = candidate;
        state = State.REGISTERED;
      } catch (NoClassDefFoundError | NoSuchMethodError e) {
        failStart(candidate, e);
      } catch (RuntimeException e) {
        failStart(candidate, e);
      }
    }
  }

  private void failStart(CoordinateEntryPane candidate, Throwable failure) {
    Log.w(TAG, "Native coordinate-entry registration failed", failure);
    if (candidate != null) {
      try {
        gateway.unregister(candidate);
      } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError rollbackFailure) {
        Log.w(TAG, "Native coordinate-entry rollback failed", rollbackFailure);
      }
      safeDispose(candidate);
    }
    pane = null;
    state = State.FAILED;
  }

  private void completeStop(long token) {
    synchronized (this) {
      if (token != generation || state != State.STOP_PENDING) return;
      CoordinateEntryPane current = pane;
      pane = null;
      if (current != null) {
        try {
          gateway.unregister(current);
        } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
          Log.w(TAG, "Native coordinate-entry unregister failed", e);
        } finally {
          safeDispose(current);
        }
      }
      state = State.STOPPED;
    }
  }

  private void completeRefresh(long token) {
    synchronized (this) {
      if (token != generation || state != State.REFRESH_PENDING || pane == null) return;
      CoordinateEntryPane current = pane;
      View view;
      try {
        view = current.getView();
      } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
        Log.w(TAG, "Native coordinate-entry locale refresh view lookup failed", e);
        state = State.FAILED;
        return;
      }
      if (view != null && view.isAttachedToWindow()) {
        installRefreshDetachListener(view, token);
        return;
      }
      removeRefreshDetachListener();
      pane = null;
      try {
        gateway.unregister(current);
      } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
        Log.w(TAG, "Native coordinate-entry locale refresh unregister failed", e);
        safeDispose(current);
        state = State.FAILED;
        return;
      }
      safeDispose(current);

      CoordinateEntryPane replacement = null;
      try {
        replacement = Objects.requireNonNull(paneFactory.create(), "paneFactory returned null");
        gateway.register(replacement);
        pane = replacement;
        state = State.REGISTERED;
      } catch (NoClassDefFoundError | NoSuchMethodError e) {
        failStart(replacement, e);
      } catch (RuntimeException e) {
        failStart(replacement, e);
      }
    }
  }

  private void installRefreshDetachListener(View view, long token) {
    if (refreshListenerView == view && refreshDetachListener != null) return;
    removeRefreshDetachListener();
    View.OnAttachStateChangeListener listener =
        new View.OnAttachStateChangeListener() {
          @Override
          public void onViewAttachedToWindow(View attachedView) {}

          @Override
          public void onViewDetachedFromWindow(View detachedView) {
            synchronized (NativeCoordinateEntryRegistrar.this) {
              if (token != generation || state != State.REFRESH_PENDING) return;
              removeRefreshDetachListener();
              dispatcher.post(() -> completeRefresh(token));
            }
          }
        };
    refreshListenerView = view;
    refreshDetachListener = listener;
    view.addOnAttachStateChangeListener(listener);
  }

  private void removeRefreshDetachListener() {
    View view = refreshListenerView;
    View.OnAttachStateChangeListener listener = refreshDetachListener;
    refreshListenerView = null;
    refreshDetachListener = null;
    if (view == null || listener == null) return;
    try {
      view.removeOnAttachStateChangeListener(listener);
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "Native coordinate-entry detach-listener removal failed", e);
    }
  }

  private static void safeDispose(CoordinateEntryPane target) {
    try {
      target.dispose();
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "Native coordinate-entry pane disposal failed", e);
    }
  }
}
