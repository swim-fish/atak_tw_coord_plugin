package com.atakmap.android.twcoord.address;

import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import com.atakmap.coremap.log.Log;
import java.util.EnumMap;
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
    if (facade == null || resolver == null) {
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

  /** Re-open the facade after a successful import / remove. Idempotent. */
  public void onActiveDatasetChanged() {
    openFacadeFromActive();
    boolean hasDataset = facade != null;
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
      AddressLookupResult result =
          resolver != null ? resolver.lookup(lat, lon) : AddressLookupResult.noDataset();
      state = mapResultToState(result);
    } catch (Throwable t) {
      Log.w(TAG, "lookup task threw", t);
      state = AddressRowState.emptyState();
    }
    final AddressRowState toEmit = state;
    uiPoster.accept(() -> emit(row, toEmit));
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

  private AddressRowState mapResultToState(AddressLookupResult r) {
    if (r instanceof AddressLookupResult.Found) {
      return AddressRowState.text(((AddressLookupResult.Found) r).record().displayName());
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
