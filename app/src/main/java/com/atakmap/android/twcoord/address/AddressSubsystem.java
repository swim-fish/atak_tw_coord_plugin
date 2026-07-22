package com.atakmap.android.twcoord.address;

import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import com.atakmap.coremap.log.Log;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Lifecycle owner for the per-row address resolver. Per {@code contracts/address-resolver.md
 * §AddressSubsystem}:
 *
 * <ul>
 *   <li>Holds a single-thread {@link ScheduledExecutorService} for lookup work.
 *   <li>Coalesces per-row coordinate updates via a per-row {@link ScheduledFuture}; cancel +
 *       re-schedule with {@code debounceMs} (default 250 ms).
 *   <li>Maps {@link AddressLookupResult} → {@link AddressRowState} and fans out to registered
 *       listeners on the UI thread via the injected {@code uiPoster}.
 *   <li>Opens / closes the {@link AddressDatabaseFacade} via the {@link
 *       AddressDatabaseFacade.Factory} in response to {@link #onActiveDatasetChanged()}.
 *   <li>Enforces FR-012 by installing a {@link StrictMode.ThreadPolicy} on the worker thread that
 *       fails on any accidental network call from the address subsystem.
 * </ul>
 */
public final class AddressSubsystem implements AutoCloseable {

  private static final String TAG = "AddressSubsystem";

  public enum Row {
    ME,
    TGT,
    MAP
  }

  public interface Listener {
    void onAddressRowStateChanged(Row row, AddressRowState state);
  }

  private final AddressBundleImporter importer;
  private final AddressDatabaseFacade.Factory facadeFactory;
  private final ScheduledExecutorService executor;
  private final long debounceMs;
  private final Consumer<Runnable> uiPoster;

  private final EnumMap<Row, Boolean> enabled = new EnumMap<>(Row.class);
  private final EnumMap<Row, ScheduledFuture<?>> inflight = new EnumMap<>(Row.class);
  private final EnumMap<Row, AddressRowState> lastState = new EnumMap<>(Row.class);
  private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

  private AddressDatabaseFacade facade;
  private AddressResolver resolver;
  private boolean strictModeInstalled;

  /**
   * Feature 005: optional multi-county registry. When non-null, {@link #runLookup} fans out across
   * every active county and returns the geodetically-nearest record (data-model.md §4). When null,
   * the legacy single-active path (via {@link #facade} + {@link #resolver}) is used.
   */
  private ActiveDatasetRegistry registry;

  /**
   * Feature 006: optional township boundary facade. When non-null, {@link #runLookup} resolves the
   * county via {@link #boundary} first and queries only that county's facade (removing the
   * cross-county fan-out for in-county points, FR-014); falls back to {@link
   * #lookupAcrossAllCounties} when the boundary facade is absent or returns no county (FR-017).
   * {@code volatile} because it is bound from the UI thread and read on the worker thread.
   */
  private volatile com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade boundary;

  /** Coastline tolerance for the reverse-path county resolve (metres). */
  private static final double REVERSE_SNAP_M = 1000.0;

  /**
   * Feature 005 polish: operator-selectable preset for the tilde confidence indicator. Default
   * {@link ConfidenceThresholds#TIGHT} preserves the 2026-05-27 device-verified 20 m / 100 m
   * behaviour when no preference has been written yet. {@code volatile} because {@link #runLookup}
   * reads from the worker thread while {@link #setConfidenceThresholds} writes from the UI thread.
   */
  private volatile ConfidenceThresholds confidenceThresholds = ConfidenceThresholds.TIGHT;

  /** Production lookup radius (research.md §R4). */
  private static final double LOOKUP_RADIUS_M = 500.0;

  /**
   * Below this query-to-record distance the on-map address row shows no direction arrow — the
   * record is essentially on the query point, so a bearing would be noise.
   */
  private static final double ARROW_MIN_DISTANCE_M = 3.0;

  /** Earth mean radius for haversine (matches AtakDatabasesAddressDatabase). */
  private static final double EARTH_R_M = 6_371_000.0;

  /**
   * Production constructor — uses an inline {@link Handler} on the main looper to fan results out
   * to the UI thread.
   */
  public AddressSubsystem(
      AddressBundleImporter importer,
      AddressDatabaseFacade.Factory facadeFactory,
      ScheduledExecutorService executor,
      long debounceMs) {
    this(importer, facadeFactory, executor, debounceMs, defaultUiPoster());
  }

  /** Test seam: inject a synchronous (or any) {@code uiPoster}. */
  public AddressSubsystem(
      AddressBundleImporter importer,
      AddressDatabaseFacade.Factory facadeFactory,
      ScheduledExecutorService executor,
      long debounceMs,
      Consumer<Runnable> uiPoster) {
    this.importer = Objects.requireNonNull(importer, "importer");
    this.facadeFactory = Objects.requireNonNull(facadeFactory, "facadeFactory");
    this.executor = Objects.requireNonNull(executor, "executor");
    if (debounceMs < 0) throw new IllegalArgumentException("debounceMs must be >= 0");
    this.debounceMs = debounceMs;
    this.uiPoster = Objects.requireNonNull(uiPoster, "uiPoster");

    for (Row r : Row.values()) {
      enabled.put(r, false);
      lastState.put(r, AddressRowState.hidden());
    }
    openFacadeFromActive();
  }

  // ----------------------------------------------------------------------
  // Public API (UI-thread only)
  // ----------------------------------------------------------------------

  /**
   * Feature 005 hook: bind the multi-county registry. After this is set, the subsystem stops
   * relying on the legacy single-active {@code AddressBundleImporter.activeOrNull} path; the
   * registry's snapshot drives every lookup. Idempotent — calling with the same registry is a
   * no-op. Pass {@code null} to detach (used by {@link #close()} indirectly via the host).
   *
   * <p>Wired in {@code TwCoordMapComponent.onCreate} after {@code Registry.initFromDisk}.
   */
  public void setRegistry(ActiveDatasetRegistry registry) {
    this.registry = registry;
  }

  /**
   * Feature 006: bind the shared township boundary facade for county-scoped reverse lookup. Pass
   * {@code null} (or never call this) to keep the 005 cross-county fan-out behaviour. Idempotent;
   * safe to call from the UI thread while lookups are in flight (the field is volatile).
   */
  public void setBoundaryFacade(
      com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade boundary) {
    this.boundary = boundary;
  }

  /**
   * Feature 005 polish: change the confidence-indicator preset. Idempotent; safe to call from the
   * UI thread while lookups are in flight (the field is volatile, the worker reads it on each
   * {@link #runLookup} entry).
   */
  public void setConfidenceThresholds(ConfidenceThresholds thresholds) {
    this.confidenceThresholds = thresholds != null ? thresholds : ConfidenceThresholds.TIGHT;
  }

  public void setRowEnabled(Row row, boolean en) {
    Objects.requireNonNull(row, "row");
    enabled.put(row, en);
    if (!en) {
      cancelInflight(row);
      emit(row, AddressRowState.hidden());
    }
  }

  public void onCoord(Row row, double lat, double lon) {
    Objects.requireNonNull(row, "row");
    if (!Boolean.TRUE.equals(enabled.get(row))) {
      // Disabled rows stay Hidden; no work to do.
      return;
    }
    if (!hasAnyActiveDataset()) {
      // No dataset → Hidden (contract §State derivation).
      emit(row, AddressRowState.hidden());
      return;
    }
    cancelInflight(row);

    // First lookup for this row after activation / re-enable → show Loading until the
    // background task fires. If a Text(...) or EmptyState is already shown, skip the
    // Loading flash to avoid flicker on every coord update.
    AddressRowState prev = lastState.get(row);
    if (prev == null || prev.isHidden()) {
      emit(row, AddressRowState.loading());
    }

    ScheduledFuture<?> f =
        executor.schedule(() -> runLookup(row, lat, lon), debounceMs, TimeUnit.MILLISECONDS);
    inflight.put(row, f);
  }

  public void addListener(Listener l) {
    Objects.requireNonNull(l, "listener");
    listeners.add(l);
  }

  public void removeListener(Listener l) {
    listeners.remove(l);
  }

  /**
   * Re-open the facade after a successful import / remove. Idempotent.
   *
   * <p>Feature 005: when a {@link #registry} is bound, this method does NOT re-open the legacy
   * single facade — the registry owns per-county lifecycle. The method still resets per-row state
   * (cancel inflight, flip to Loading) so the UI doesn't show a stale resolved address.
   */
  public void onActiveDatasetChanged() {
    if (registry == null) {
      openFacadeFromActive();
    }
    boolean hasDataset = hasAnyActiveDataset();
    // Drop stale per-row state. If a dataset is active, the operator sees Loading until the next
    // coord refresh resolves a fresh address. If no dataset is active (removal, or files vanished
    // — see US4 / SC-005), every row goes straight to Hidden so the address line disappears
    // without a misleading "Loading…" flash that would never resolve.
    for (Row r : Row.values()) {
      cancelInflight(r);
      if (!Boolean.TRUE.equals(enabled.get(r)) || !hasDataset) {
        emit(r, AddressRowState.hidden());
        continue;
      }
      AddressRowState prev = lastState.get(r);
      if (prev != null && !prev.isHidden()) {
        emit(r, AddressRowState.loading());
      }
    }
  }

  @Override
  public void close() {
    for (Row r : Row.values()) cancelInflight(r);
    try {
      executor.shutdownNow();
      executor.awaitTermination(50, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Throwable t) {
      Log.w(TAG, "executor shutdown threw", t);
    }
    if (facade != null) {
      try {
        facade.close();
      } catch (Throwable t) {
        Log.w(TAG, "facade close threw", t);
      }
      facade = null;
      resolver = null;
    }
    listeners.clear();
  }

  // ----------------------------------------------------------------------
  // Internal — worker thread
  // ----------------------------------------------------------------------

  private void runLookup(Row row, double lat, double lon) {
    installStrictModeIfFirstRun();
    AddressRowState state;
    try {
      AddressLookupResult result;
      if (registry != null) {
        result = lookupScoped(lat, lon);
      } else {
        result = resolver != null ? resolver.lookup(lat, lon) : AddressLookupResult.noDataset();
      }
      state = mapResultToState(result, lat, lon);
    } catch (Throwable t) {
      Log.w(TAG, "lookup task threw", t);
      state = AddressRowState.emptyState();
    }
    final AddressRowState toEmit = state;
    uiPoster.accept(() -> emit(row, toEmit));
  }

  /**
   * Feature 005 multi-county fan-out. For each active county, query its facade for the nearest
   * record within {@link #LOOKUP_RADIUS_M}; pick the globally-nearest result by haversine distance.
   * Per-county exceptions are caught (Constitution VI listener short-circuit) so a corrupt county
   * can't break the resolver for the rest. See data-model.md §4.1.
   */
  /** Package-private for {@code AddressSubsystemMultiCountyTest}. */
  AddressLookupResult lookupAcrossAllCounties(double lat, double lon) {
    Map<String, CountyActiveDataset> snap = registry.snapshot();
    if (snap.isEmpty()) return AddressLookupResult.noDataset();
    AddressRecord best = null;
    double bestDist = LOOKUP_RADIUS_M;
    for (CountyActiveDataset entry : snap.values()) {
      try {
        AddressDatabaseFacade f = entry.facade();
        if (f == null) continue;
        // Pass the running best-distance as the bbox radius — each subsequent county only
        // looks within the area where it could beat the current winner. Monotonically
        // shrinking radius per research R5 / data-model §4.1.
        AddressRecord candidate = f.nearestWithin(lat, lon, bestDist);
        if (candidate == null) continue;
        double d = haversineMeters(lat, lon, candidate.lat(), candidate.lon());
        if (d < bestDist) {
          bestDist = d;
          best = candidate;
        }
      } catch (Throwable t) {
        Log.w(TAG, "lookup in " + entry.county() + " threw", t);
      }
    }
    if (best == null) return AddressLookupResult.empty();
    return AddressLookupResult.found(best, bestDist);
  }

  /**
   * Feature 006 reverse-path county scoping (FR-014). Resolves the county via the boundary facade,
   * then queries only that county's facade; falls back to {@link #lookupAcrossAllCounties} when the
   * boundary is absent or the point is outside all boundaries (FR-017). When the detected county
   * has no installed dataset, returns {@link AddressLookupResult#localityOnly} (FR-015).
   *
   * <p>For a point inside an active county the single-county result equals the old globally-nearest
   * result (the nearest record lies in the county that contains the point), so there is no
   * operator-visible change — only less work. Package-private for {@code
   * AddressSubsystemReverseScopingTest}.
   */
  AddressLookupResult lookupScoped(double lat, double lon) {
    com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade b = boundary;
    if (b == null) {
      return lookupAcrossAllCounties(lat, lon); // no boundary data → exact 005 behaviour
    }
    com.atakmap.android.twcoord.address.boundary.LocalityResult loc;
    try {
      loc = b.localityAt(lat, lon, REVERSE_SNAP_M);
    } catch (Throwable t) {
      Log.w(TAG, "boundary.localityAt threw; falling back to fan-out", t);
      return lookupAcrossAllCounties(lat, lon);
    }
    if (loc == null || loc.county() == null) {
      return lookupAcrossAllCounties(lat, lon); // offshore / outside data → fan-out fallback
    }
    Map<String, CountyActiveDataset> snap = registry.snapshot();
    CountyActiveDataset entry = snap.get(loc.county());
    if (entry == null) {
      // County detected but its dataset isn't installed → best-effort locality (FR-015).
      return AddressLookupResult.localityOnly(loc.county(), loc.district());
    }
    try {
      AddressDatabaseFacade f = entry.facade();
      if (f != null) {
        AddressRecord rec = f.nearestWithin(lat, lon, LOOKUP_RADIUS_M);
        if (rec != null) {
          double d = haversineMeters(lat, lon, rec.lat(), rec.lon());
          return AddressLookupResult.found(rec, d);
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "scoped lookup in " + loc.county() + " threw", t);
    }
    // County dataset present but no record within radius → still surface the locality so the row
    // isn't blank (consistent with FR-015's best-effort intent).
    return AddressLookupResult.localityOnly(loc.county(), loc.district());
  }

  /** True iff the resolver currently has at least one dataset (registry or legacy) to query. */
  private boolean hasAnyActiveDataset() {
    if (registry != null) {
      return !registry.snapshot().isEmpty();
    }
    return facade != null && resolver != null;
  }

  static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double p1 = Math.toRadians(lat1);
    double p2 = Math.toRadians(lat2);
    double dPhi = Math.toRadians(lat2 - lat1);
    double dLambda = Math.toRadians(lon2 - lon1);
    double a =
        Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
    return 2 * EARTH_R_M * Math.asin(Math.sqrt(a));
  }

  /**
   * FR-012 enforcement: any network call from the address subsystem fails the worker thread.
   * Installed once, on the first task invocation (the StrictMode policy is per-thread; the
   * subsystem owns a single-thread executor so one install covers every future task).
   */
  private void installStrictModeIfFirstRun() {
    if (strictModeInstalled) return;
    try {
      StrictMode.setThreadPolicy(
          new StrictMode.ThreadPolicy.Builder()
              .detectNetwork()
              .penaltyLog()
              .penaltyDeath()
              .build());
    } catch (Throwable t) {
      // StrictMode may NPE under unusual hosting; non-fatal.
      Log.w(TAG, "StrictMode install failed; FR-012 enforcement degraded to no-op", t);
    }
    strictModeInstalled = true;
  }

  private void emit(Row row, AddressRowState state) {
    AddressRowState prev = lastState.get(row);
    if (state.equals(prev)) return;
    lastState.put(row, state);
    for (Listener l : listeners) {
      try {
        l.onAddressRowStateChanged(row, state);
      } catch (Throwable t) {
        Log.w(TAG, "listener threw", t);
      }
    }
  }

  private AddressRowState mapResultToState(
      AddressLookupResult r, double queryLat, double queryLon) {
    if (r instanceof AddressLookupResult.Found) {
      AddressLookupResult.Found f = (AddressLookupResult.Found) r;
      String text = confidenceThresholds.decorate(f.record().displayName(), f.distanceMeters());
      // Prefix a compass arrow pointing from the query point (map centre / self / target) to the
      // resolved record, so the operator can see which way the actual address point lies. Skipped
      // when the record is essentially on the query point (no meaningful direction).
      double metres = haversineMeters(queryLat, queryLon, f.record().lat(), f.record().lon());
      if (metres >= ARROW_MIN_DISTANCE_M) {
        double bearing =
            com.atakmap.android.twcoord.address.lookup.CompassDirection.bearingDegrees(
                queryLat, queryLon, f.record().lat(), f.record().lon());
        text =
            com.atakmap.android.twcoord.address.lookup.CompassDirection.arrowGlyph(bearing)
                + " "
                + text;
      }
      return AddressRowState.text(text);
    }
    if (r instanceof AddressLookupResult.LocalityOnly) {
      // Feature 006 FR-015: county/district known but no house number — show the locality text
      // (distinct from the empty-state) so the operator still sees where they are.
      return AddressRowState.text(((AddressLookupResult.LocalityOnly) r).localityText());
    }
    // Empty or NoDataset → empty-state ("No address nearby"). NoDataset's "Hidden" handling
    // happens upstream in onCoord before scheduling.
    return AddressRowState.emptyState();
  }

  private void cancelInflight(Row row) {
    ScheduledFuture<?> f = inflight.remove(row);
    if (f != null) f.cancel(false);
  }

  private void openFacadeFromActive() {
    if (facade != null) {
      try {
        facade.close();
      } catch (Throwable t) {
        Log.w(TAG, "old facade close threw", t);
      }
      facade = null;
      resolver = null;
    }
    AddressDataset active;
    try {
      active = importer.activeOrNull();
    } catch (Throwable t) {
      Log.w(TAG, "importer.activeOrNull threw", t);
      active = null;
    }
    if (active == null) return;
    AddressDatabaseFacade fresh = facadeFactory.open(active.dbFile());
    if (fresh != null) {
      facade = fresh;
      resolver = new AddressResolver(fresh, 500.0);
    }
  }

  private static Consumer<Runnable> defaultUiPoster() {
    try {
      Handler h = new Handler(Looper.getMainLooper());
      return h::post;
    } catch (Throwable t) {
      // Looper.getMainLooper() is unavailable in raw JVM contexts. Fall back to inline run.
      return Runnable::run;
    }
  }
}
