package com.atakmap.android.twcoord;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.twcoord.address.ActiveDatasetRegistry;
import com.atakmap.android.twcoord.address.AddressBundleImporter;
import com.atakmap.android.twcoord.address.AddressDatabaseFacade;
import com.atakmap.android.twcoord.address.AddressRowState;
import com.atakmap.android.twcoord.address.AddressSubsystem;
import com.atakmap.android.twcoord.address.AtakDatabasesAddressDatabase;
import com.atakmap.android.twcoord.address.AtakFileSystem;
import com.atakmap.android.twcoord.address.BatchImportCoordinator;
import com.atakmap.android.twcoord.address.FallbackSqliteFactory;
import com.atakmap.android.twcoord.address.MessageDigestShaCalculator;
import com.atakmap.android.twcoord.address.OfflineAddressIntents;
import com.atakmap.android.twcoord.address.OfflineAddressReceiver;
import com.atakmap.android.twcoord.address.ZipEntryClassifier;
import com.atakmap.android.twcoord.address.ZipExtractor;
import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.DisplayLine;
import com.atakmap.android.twcoord.coord.Formatter;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.i18n.LocaleOverride;
import com.atakmap.android.twcoord.nativeentry.NativeCoordinateEntryRegistrar;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.android.twcoord.prefs.PreferenceStore;
import com.atakmap.android.twcoord.prefs.UserPreference;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapRenderer3;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

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
  private NativeCoordinateEntryRegistrar nativeEntryRegistrar;

  // Feature 004 — Offline Address subsystem owns the importer (one per process), a
  // single-thread import executor, the page receiver, plus the runtime per-row resolver
  // (AddressSubsystem) and a separate single-thread scheduled executor for lookup work.
  private AddressBundleImporter addressImporter;

  /**
   * Static holder so {@link TwCoordPreferenceFragment} (which is constructed by the host preference
   * framework, not by this component) can reach the live importer to render the dataset-presence
   * status row. Same pattern the project already uses for {@code pluginContext} in {@link
   * TwCoordPreferenceFragment}. Set in {@link #onCreate} after the importer is built and cleared in
   * {@link #onDestroyImpl}.
   */
  @android.annotation.SuppressLint("StaticFieldLeak")
  private static AddressBundleImporter staticAddressImporter;

  /**
   * @return the live address-bundle importer if the component is running, otherwise {@code null}.
   *     The fragment treats null as "no dataset" (the more conservative state).
   */
  public static AddressBundleImporter getAddressImporter() {
    return staticAddressImporter;
  }

  private ExecutorService addressImportExecutor;
  private OfflineAddressReceiver addressReceiver;
  private AddressSubsystem addressSubsystem;
  private ScheduledExecutorService addressLookupExecutor;
  private BroadcastReceiver addressDatasetChangedReceiver;

  // Feature 005 — multi-county active datasets + batch import.
  private com.atakmap.android.twcoord.address.AtakFileSystem addressFileSystem;
  private com.atakmap.android.twcoord.address.ActiveDatasetRegistry addressRegistry;
  private com.atakmap.android.twcoord.address.BatchImportCoordinator addressCoordinator;
  private com.atakmap.android.twcoord.address.lookup.AddressLookupService addressLookupService;

  // Township boundary layer retained for county-scoped widget reverse lookup.
  private com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade addressBoundaryFacade;

  @android.annotation.SuppressLint("StaticFieldLeak")
  private static com.atakmap.android.twcoord.address.ActiveDatasetRegistry staticAddressRegistry;

  @android.annotation.SuppressLint("StaticFieldLeak")
  private static com.atakmap.android.twcoord.address.BatchImportCoordinator
      staticAddressCoordinator;

  /**
   * @return live registry while plugin is running, else {@code null}.
   */
  public static com.atakmap.android.twcoord.address.ActiveDatasetRegistry getAddressRegistry() {
    return staticAddressRegistry;
  }

  /**
   * @return live batch coordinator while plugin is running, else {@code null}.
   */
  public static com.atakmap.android.twcoord.address.BatchImportCoordinator getAddressCoordinator() {
    return staticAddressCoordinator;
  }

  // Per-row last-emitted state; aggregated here so the widget can be repainted with all three
  // values on every single-row update.
  private final Map<AddressSubsystem.Row, AddressRowState> addressRowStates =
      new EnumMap<>(AddressSubsystem.Row.class);

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
   * Coalescing guard so a burst of {@link #cameraChangedListener} fires (one per render frame
   * during an animation/drag) collapses into at most one queued UI-thread refresh.
   */
  private final AtomicBoolean mapRefreshPending = new AtomicBoolean(false);

  /**
   * Wakes the bottom-left MAP readout on every camera change, including programmatic pans. ATAK
   * camera actions can drive the renderer directly without dispatching {@link MapEvent#MAP_MOVED}
   * through the {@link MapEventDispatcher}, so {@link #mapCentreListener} never sees those moves
   * and the MAP coordinate + address line went stale (issue swim-fish/atak_tw_coord_plugin#1). This
   * renderer-level listener catches them. It fires on the GL/render thread, so it hops to the UI
   * thread via {@code mapView.post} and coalesces through {@link #mapRefreshPending}; {@code
   * renderMapCentre} then reads the now-current {@code mapView.getPoint()} (also avoiding the
   * one-frame lag of the non-animated pan). Wrapped per Constitution VI.
   */
  private final MapRenderer2.OnCameraChangedListener2 cameraChangedListener =
      new MapRenderer2.OnCameraChangedListener2() {
        @Override
        public void onCameraChanged(MapRenderer2 renderer) {
          if (mapView == null) return;
          if (mapRefreshPending.compareAndSet(false, true)) {
            mapView.post(
                () -> {
                  mapRefreshPending.set(false);
                  try {
                    renderMapCentre();
                  } catch (Throwable t) {
                    android.util.Log.w(
                        "TwCoordMapComponent", "camera-changed renderMapCentre threw", t);
                  }
                });
          }
        }

        @Override
        public void onCameraChangeRequested(MapRenderer2 renderer) {
          /* no-op — refresh on the applied change, not on the request */
        }
      };

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
        boolean meToggleChanged = snap.addressRowMe() != activePrefs.addressRowMe();
        boolean tgtToggleChanged = snap.addressRowTarget() != activePrefs.addressRowTarget();
        boolean mapToggleChanged = snap.addressRowMap() != activePrefs.addressRowMap();
        boolean confidenceChanged =
            snap.confidenceThresholds() != activePrefs.confidenceThresholds();
        activePrefs = snap;
        // Feature 007 US2 — apply on-map readout visibility (its key fires fireAll()). Idempotent.
        if (widget != null && prefs != null) widget.setVisible(prefs.isReadoutVisible());
        if (languageChanged) {
          rebuildLocalisedContext();
          if (nativeEntryRegistrar != null) {
            try {
              nativeEntryRegistrar.refreshLocale();
            } catch (NoClassDefFoundError | NoSuchMethodError e) {
              android.util.Log.w(
                  "TwCoordMapComponent", "native entry locale refresh unavailable", e);
            } catch (RuntimeException e) {
              android.util.Log.w("TwCoordMapComponent", "native entry locale refresh failed", e);
            }
          }
          if (widget != null && localisedPluginContext != null) {
            widget.setAddressStrings(
                localisedPluginContext.getString(R.string.widget_address_loading),
                localisedPluginContext.getString(R.string.widget_address_empty_state));
          }
        }
        // Repaint both rows with the new unit / language.
        renderMapCentre();
        renderMeFromLastKnown();
        // Propagate per-row toggle changes to the address subsystem.
        if (addressSubsystem != null) {
          if (meToggleChanged) {
            addressSubsystem.setRowEnabled(AddressSubsystem.Row.ME, snap.addressRowMe());
          }
          if (tgtToggleChanged) {
            addressSubsystem.setRowEnabled(AddressSubsystem.Row.TGT, snap.addressRowTarget());
          }
          if (mapToggleChanged) {
            addressSubsystem.setRowEnabled(AddressSubsystem.Row.MAP, snap.addressRowMap());
          }
          if (confidenceChanged) {
            addressSubsystem.setConfidenceThresholds(snap.confidenceThresholds());
          }
        }
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
          // Feature 004 — feed the address subsystem so the ME address row updates.
          if (addressSubsystem != null) {
            try {
              addressSubsystem.onCoord(
                  AddressSubsystem.Row.ME, fix.latitudeDeg(), fix.longitudeDeg());
            } catch (Throwable t) {
              android.util.Log.w("TwCoordMapComponent", "onCoord(ME) threw", t);
            }
          }
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
   * Tools-menu tap handler. The plugin's single public Tools entry now opens offline address data
   * first; that page owns the explicit route into the full TW Coordinates settings screen.
   */
  private final BroadcastReceiver toggleReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
          try {
            TwCoordNavigation.openToolDestination();
          } catch (Throwable t) {
            android.util.Log.w(
                "TwCoordMapComponent", "open offline address from tool button threw", t);
          }
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
    // Feature 007 US2 — apply the persisted on-map readout visibility (replaces the show/hide the
    // old tool-button cycle provided). Defaults to shown.
    widget.setVisible(prefs.isReadoutVisible());

    this.selfSub =
        new SelfMarkerSubscriber(
            System::currentTimeMillis, 1_000L, activePrefs.staleFixThresholdMs(), subListener);

    // ATAK-CIV 5.7.0.3 fires MAP_SCROLL continuously during a user drag, MAP_SETTLED once when
    // the drag finishes, and MAP_SCALE on zoom. These cover gesture-driven viewport changes.
    // NOTE: programmatic recentres via CameraController.Programmatic.panTo do NOT come through the
    // dispatcher (they drive the renderer camera directly) — the renderer camera listener below
    // covers those; keep both so every kind of viewport change refreshes the readout.
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_MOVED, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SCROLL, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SETTLED, mapCentreListener);
    view.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SCALE, mapCentreListener);

    // Catch programmatic pans (GoTo submit, forward-search tap-to-pan) that bypass the dispatcher.
    try {
      MapRenderer3 renderer = view.getRenderer3();
      if (renderer != null) renderer.addOnCameraChangedListener(cameraChangedListener);
    } catch (Throwable t) {
      android.util.Log.w("TwCoordMapComponent", "addOnCameraChangedListener threw", t);
    }

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

    // Retained offline-data manager. The single TW Coordinates Tools item, Settings, and the native
    // Address pane fire SHOW_OFFLINE_ADDRESS; there is no separate TW Offline Addr Tools item. The
    // importer + executor are owned here so they outlive any single drop-down open/close cycle and
    // so the AddressSubsystem can reuse the same importer without re-opening files.
    // Pass `2` as the max supported schema version — per the generator's
    // docs/data-contract.md (v2, 2026-05-24 evening) v2 adds `places_rtree`. The importer
    // accepts both v1 (plugin builds R*Tree at import) and v2 (generator already shipped it,
    // plugin skips the build).
    com.atakmap.coremap.log.Log.i(
        "TwCoordMapComponent",
        "Feature 004 init: building AddressBundleImporter + OfflineAddressReceiver in pid="
            + android.os.Process.myPid()
            + " uid="
            + android.os.Process.myUid());
    // Feature 005: share one AtakFileSystem instance across importer + registry + coordinator
    // so the sweep-orphan-staging pass runs once + per-county helper methods are consistent.
    addressFileSystem = new AtakFileSystem();
    // Highest data-contract schema_version the plugin accepts (inclusive). v3 is additive &
    // non-breaking vs v2 — the generator only added `area` to places_fts; the base `places` table
    // and every column the plugin reads are unchanged (see the generator's data-contract §7 v3
    // CHANGELOG + address-search-guide §5). Bumping 2→3 lets v3 datasets import instead of failing
    // UNSUPPORTED_SCHEMA_VERSION. MUST track the generator's SCHEMA_VERSION.
    addressImporter =
        new AddressBundleImporter(addressFileSystem, new MessageDigestShaCalculator(), 3);
    staticAddressImporter = addressImporter;
    addressImportExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "twcoord-address-import");
              t.setDaemon(true);
              return t;
            });
    addressReceiver =
        new OfflineAddressReceiver(
            view,
            // Live localised context (ADR-0003) so the storage page localises and repaints on a
            // language change; fall back to the raw plugin context only before it is built.
            () -> localisedPluginContext != null ? localisedPluginContext : pluginContext,
            addressImporter,
            addressImportExecutor,
            () -> TwCoordNavigation.openSettings(PREF_KEY));
    AtakBroadcast.DocumentedIntentFilter addressFilter = new AtakBroadcast.DocumentedIntentFilter();
    addressFilter.addAction(OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS);
    AtakBroadcast.getInstance().registerReceiver(addressReceiver, addressFilter);

    // Feature 004 (US2) — runtime per-row address resolver. Dedicated single-thread
    // ScheduledExecutorService for debounce + lookup; the importer hands the active
    // dataset's File to SqliteFactory which opens the read-only DB.
    addressLookupExecutor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "twcoord-address-lookup");
              t.setDaemon(true);
              return t;
            });
    // 250 ms debounce — keep 004's reaction speed even though it leaves a small
    // residual widget bg/text race on very fast pans (2026-05-27 UX call: speed
    // matters more than the rare partial-black-box flicker, which already gets
    // mitigated in TwCoordWidget.paintAddressRow via setVisible toggle +
    // onSizeChanged + setBackground re-fire + MapView.postOnActive).
    addressSubsystem =
        new AddressSubsystem(
            addressImporter,
            new AtakDatabasesAddressDatabase.Factory(),
            addressLookupExecutor,
            250L);
    for (AddressSubsystem.Row r : AddressSubsystem.Row.values()) {
      addressRowStates.put(r, AddressRowState.hidden());
    }

    // Feature 005 — multi-county registry. Owns one open SQLite facade per active county.
    // Bound to the subsystem AFTER its ctor so the legacy single-active path stays intact
    // for the (rare) "no counties" zero-state; with the registry bound, fan-out lookup is
    // the new code path (see AddressSubsystem.lookupAcrossAllCounties).
    AddressDatabaseFacade.Factory primaryFactory = new AtakDatabasesAddressDatabase.Factory();
    java.util.function.Supplier<AddressDatabaseFacade.Factory> fallbackSupplier =
        () -> new FallbackSqliteFactory();
    // Feature 005 US4: one-shot v1.0.5 → v1.0.6 auto-migrate (legacy
    // active/places.sqlite → active/<county>/places.sqlite). Runs before
    // Registry.initFromDisk so the migrated county is picked up on the same boot.
    try {
      com.atakmap.android.twcoord.address.AutoMigrator migrator =
          new com.atakmap.android.twcoord.address.AutoMigrator(addressFileSystem, primaryFactory);
      com.atakmap.android.twcoord.address.AutoMigrator.Result migrateResult = migrator.tryMigrate();
      com.atakmap.coremap.log.Log.i(
          "TwCoordMapComponent", "AutoMigrator → " + migrateResult.getClass().getSimpleName());
    } catch (Throwable t) {
      // Constitution VI: a corrupt v1.0.5 layout MUST NOT crash the host.
      com.atakmap.coremap.log.Log.w("TwCoordMapComponent", "AutoMigrator threw", t);
    }

    addressRegistry =
        new ActiveDatasetRegistry(
            addressImporter, primaryFactory, fallbackSupplier, addressFileSystem);
    addressRegistry.initFromDisk();
    addressSubsystem.setRegistry(addressRegistry);

    // Mount the township boundary layer and bind it to county-scoped widget reverse lookup. A
    // missing boundary remains non-fatal; a later import is picked up without an ATAK restart.
    boundaryFacadeOrRemount();

    // Feature 013 — one leased, bounded lookup owner serves native Address and map readouts.
    // Build it only after registry initialization, then construct the native pane so the first
    // activation observes the current dataset revision. The registrar keeps exact-instance UI
    // ownership; this component closes the service after registrar teardown.
    try {
      addressLookupService =
          new com.atakmap.android.twcoord.address.lookup.DefaultAddressLookupService(
              addressRegistry,
              runnable -> view.post(runnable),
              new com.atakmap.android.twcoord.address.lookup.DefaultAddressLookupService
                  .RegistryQueryEngine(
                  new com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser()),
              32);
      addressSubsystem.setLookupService(addressLookupService);
    } catch (RuntimeException e) {
      android.util.Log.w("TwCoordMapComponent", "shared address lookup setup failed", e);
      addressLookupService =
          new com.atakmap.android.twcoord.address.lookup.NoDataAddressLookupService(
              runnable -> view.post(runnable));
    }
    try {
      nativeEntryRegistrar =
          NativeCoordinateEntryRegistrar.create(
              view,
              () -> localisedPluginContext != null ? localisedPluginContext : pluginContext,
              prefs,
              addressLookupService,
              () ->
                  AtakBroadcast.getInstance()
                      .sendBroadcast(
                          new android.content.Intent(
                              OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS)));
      nativeEntryRegistrar.start();
    } catch (NoClassDefFoundError | NoSuchMethodError e) {
      android.util.Log.w("TwCoordMapComponent", "native entry unavailable on this ATAK", e);
      nativeEntryRegistrar = null;
    } catch (RuntimeException e) {
      android.util.Log.w("TwCoordMapComponent", "native entry setup failed", e);
      nativeEntryRegistrar = null;
    }
    // Re-render the widget when any county lifecycle event fires (add / replace / remove).
    addressRegistry.addListener(
        (county, change) -> {
          try {
            addressSubsystem.onActiveDatasetChanged();
          } catch (Throwable t) {
            android.util.Log.w("TwCoordMapComponent", "registry listener threw", t);
          }
        });

    addressCoordinator =
        new BatchImportCoordinator(
            addressImporter,
            new ZipExtractor(
                addressFileSystem, new MessageDigestShaCalculator(), new ZipEntryClassifier()),
            new ZipEntryClassifier(),
            addressRegistry,
            primaryFactory,
            addressImportExecutor,
            addressFileSystem);
    staticAddressRegistry = addressRegistry;
    staticAddressCoordinator = addressCoordinator;
    // Wire the coordinator into the receiver so the Import button routes through the batch
    // path (handles .zip + multi-county; legacy single-file path stays as a fallback).
    if (addressReceiver != null) {
      addressReceiver.setBatchCoordinator(addressCoordinator);
      addressReceiver.setRegistry(addressRegistry);
      // Feature 007 US3 — supply the FileSystem so the page can show per-county + _boundary sizes.
      addressReceiver.setFileSystem(addressFileSystem);
    }

    addressSubsystem.addListener(
        (row, state) -> {
          try {
            addressRowStates.put(row, state);
            if (widget != null) {
              widget.renderAddresses(
                  addressRowStates.get(AddressSubsystem.Row.MAP),
                  addressRowStates.get(AddressSubsystem.Row.ME),
                  addressRowStates.get(AddressSubsystem.Row.TGT));
            }
          } catch (Throwable t) {
            // Constitution VI: listener body cannot escape into the host.
            android.util.Log.w("TwCoordMapComponent", "address listener threw", t);
          }
        });
    // Apply initial toggle state from the preference snapshot.
    addressSubsystem.setRowEnabled(AddressSubsystem.Row.ME, activePrefs.addressRowMe());
    addressSubsystem.setRowEnabled(AddressSubsystem.Row.TGT, activePrefs.addressRowTarget());
    addressSubsystem.setRowEnabled(AddressSubsystem.Row.MAP, activePrefs.addressRowMap());
    // Feature 005 polish — confidence-indicator preset (TIGHT for unwritten prefs preserves
    // the 2026-05-27 device-verified 20/100 m behaviour).
    addressSubsystem.setConfidenceThresholds(activePrefs.confidenceThresholds());

    // Re-open the facade when the operator imports or removes a dataset in the internal manager.
    addressDatasetChangedReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context ctx, Intent intent) {
            try {
              if (addressSubsystem != null) addressSubsystem.onActiveDatasetChanged();
            } catch (Throwable t) {
              android.util.Log.w("TwCoordMapComponent", "ACTION_DATASET_CHANGED threw", t);
            }
          }
        };
    AtakBroadcast.DocumentedIntentFilter dsChangedFilter =
        new AtakBroadcast.DocumentedIntentFilter();
    dsChangedFilter.addAction(OfflineAddressIntents.ACTION_DATASET_CHANGED);
    AtakBroadcast.getInstance().registerReceiver(addressDatasetChangedReceiver, dsChangedFilter);

    // Seed the widget's address row strings from the localised context.
    if (localisedPluginContext != null) {
      widget.setAddressStrings(
          localisedPluginContext.getString(R.string.widget_address_loading),
          localisedPluginContext.getString(R.string.widget_address_empty_state));
    }

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
            pluginContext.getResources().getDrawable(R.drawable.ic_tw_coord_plugin),
            prefFragment));
  }

  /**
   * Return the mounted township-boundary facade, lazily reopening {@code
   * active/_boundary/townships.sqlite} when necessary. Called at setup and after dataset changes so
   * a later boundary import is available in the same session. Synchronized because setup, lookup,
   * and the import executor can race.
   */
  private synchronized com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade
      boundaryFacadeOrRemount() {
    if (addressBoundaryFacade != null) return addressBoundaryFacade;
    try {
      java.io.File f = addressFileSystem.boundaryDbFile().toFile();
      if (f.isFile()) {
        addressBoundaryFacade =
            new com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFactory().open(f);
        if (addressBoundaryFacade != null && addressSubsystem != null) {
          addressSubsystem.setBoundaryFacade(addressBoundaryFacade);
        }
      }
    } catch (Throwable t) {
      android.util.Log.w("TwCoordMapComponent", "boundaryFacadeOrRemount threw", t);
      addressBoundaryFacade = null;
    }
    return addressBoundaryFacade;
  }

  @Override
  protected void onDestroyImpl(Context context, MapView view) {
    if (nativeEntryRegistrar != null) {
      try {
        nativeEntryRegistrar.stopNowOnUiThread();
      } catch (RuntimeException e) {
        android.util.Log.w("TwCoordMapComponent", "native entry stop failed", e);
      }
      nativeEntryRegistrar = null;
    }
    try {
      AtakBroadcast.getInstance().unregisterReceiver(toggleReceiver);
    } catch (IllegalArgumentException ignored) {
      // Receiver was never registered (onCreate aborted) — nothing to do.
    }
    if (addressReceiver != null) {
      try {
        AtakBroadcast.getInstance().unregisterReceiver(addressReceiver);
      } catch (IllegalArgumentException ignored) {
        // never registered
      }
      try {
        addressReceiver.dispose();
      } catch (Exception ignored) {
        // best-effort
      }
      addressReceiver = null;
    }
    if (addressBoundaryFacade != null) {
      try {
        addressBoundaryFacade.close();
      } catch (Throwable t) {
        android.util.Log.w("TwCoordMapComponent", "close boundary facade threw", t);
      }
      addressBoundaryFacade = null;
    }
    if (addressCoordinator != null) {
      try {
        addressCoordinator.close();
      } catch (RuntimeException e) {
        android.util.Log.w("TwCoordMapComponent", "address coordinator close failed", e);
      }
    }
    if (addressImportExecutor != null) {
      try {
        addressImportExecutor.shutdownNow();
      } catch (Exception ignored) {
        // best-effort
      }
      addressImportExecutor = null;
    }
    if (addressDatasetChangedReceiver != null) {
      try {
        AtakBroadcast.getInstance().unregisterReceiver(addressDatasetChangedReceiver);
      } catch (IllegalArgumentException ignored) {
        // never registered
      }
      addressDatasetChangedReceiver = null;
    }
    if (addressSubsystem != null) {
      try {
        addressSubsystem.close();
      } catch (Exception ignored) {
        // best-effort
      }
      addressSubsystem = null;
    }
    // addressLookupExecutor is shut down by addressSubsystem.close(); set to null so we don't
    // double-shut.
    addressLookupExecutor = null;
    addressImporter = null;
    staticAddressImporter = null;
    if (addressLookupService != null) {
      try {
        addressLookupService.close();
      } catch (RuntimeException e) {
        android.util.Log.w("TwCoordMapComponent", "address lookup service close failed", e);
      }
      addressLookupService = null;
    }
    // Registry close waits for leased reads and closes each surviving facade exactly once.
    if (addressRegistry != null) {
      try {
        addressRegistry.close();
      } catch (RuntimeException e) {
        android.util.Log.w("TwCoordMapComponent", "address registry close failed", e);
      }
      addressRegistry = null;
    }
    addressCoordinator = null;
    addressFileSystem = null;
    staticAddressRegistry = null;
    staticAddressCoordinator = null;
    ToolsPreferenceFragment.unregister(PREF_KEY);
    if (ui != null) ui.removeCallbacks(selfTick);
    if (view != null) {
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_MOVED, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_SCROLL, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_SETTLED, mapCentreListener);
      view.getMapEventDispatcher().removeMapEventListener(MapEvent.MAP_SCALE, mapCentreListener);
      try {
        MapRenderer3 renderer = view.getRenderer3();
        if (renderer != null) renderer.removeOnCameraChangedListener(cameraChangedListener);
      } catch (Throwable t) {
        android.util.Log.w("TwCoordMapComponent", "removeOnCameraChangedListener threw", t);
      }
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
    // Feature 004 — feed the address subsystem so the MAP address row updates.
    if (addressSubsystem != null) {
      try {
        addressSubsystem.onCoord(
            AddressSubsystem.Row.MAP, centre.getLatitude(), centre.getLongitude());
      } catch (Throwable t) {
        android.util.Log.w("TwCoordMapComponent", "onCoord(MAP) threw", t);
      }
    }
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
    // Feature 004 — feed the address subsystem so the TGT address row updates.
    if (addressSubsystem != null) {
      try {
        addressSubsystem.onCoord(AddressSubsystem.Row.TGT, p.getLatitude(), p.getLongitude());
      } catch (Throwable t) {
        android.util.Log.w("TwCoordMapComponent", "onCoord(TGT) threw", t);
      }
    }
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
