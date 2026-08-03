package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atakmap.android.twcoord.address.lookup.AddressAvailability;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressLookupService;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.DatasetIdentity;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressResult;
import com.atakmap.android.twcoord.address.lookup.LookupHandle;
import com.atakmap.android.twcoord.address.lookup.LookupPriority;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressResult;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Feature 004 — {@link AddressSubsystem} tests per {@code contracts/address-resolver.md
 * §AddressSubsystemTest}. Uses a {@link FakeScheduledExecutor} so debounce timing is fully
 * deterministic, and a synchronous uiPoster (so emitted state assertions are immediate).
 */
public final class AddressSubsystemTest {

  private static final long DEBOUNCE = 250L;
  private static final File FAKE_DB = new File("/tmp/places.sqlite-not-used");

  private AddressBundleImporter importer;
  private AddressDatabaseFacade facade;
  private AddressDatabaseFacade.Factory factory;
  private AddressDataset dataset;
  private FakeScheduledExecutor exec;
  private List<EmittedState> emissions;

  @Before
  public void setUp() {
    importer = mock(AddressBundleImporter.class);
    facade = mock(AddressDatabaseFacade.class);
    factory = mock(AddressDatabaseFacade.Factory.class);
    dataset =
        new AddressDataset(
            new File("/tmp/active"),
            FAKE_DB,
            new GeneratorMetadata(
                1, "tgos", "X", "X", null, null, null, 0, Collections.<String, String>emptyMap()),
            new ImportedManifest(
                java.time.Instant.parse("2026-01-01T00:00:00Z"), "0".repeat(64), true, 1));
    when(importer.activeOrNull()).thenReturn(dataset);
    when(factory.open(any(File.class))).thenReturn(facade);
    exec = new FakeScheduledExecutor();
    emissions = new ArrayList<>();
  }

  @After
  public void tearDown() {
    if (exec != null) exec.shutdownNow();
  }

  private AddressSubsystem makeSubsystem() {
    return makeSubsystem(Runnable::run);
  }

  private AddressSubsystem makeSubsystem(Consumer<Runnable> uiPoster) {
    AddressSubsystem s = new AddressSubsystem(importer, factory, exec, DEBOUNCE, uiPoster);
    s.addListener((row, state) -> emissions.add(new EmittedState(row, state)));
    return s;
  }

  // ----------------------------------------------------------------------
  // Test 1 — onCoord schedules lookup after debounce
  // ----------------------------------------------------------------------

  @Test
  public void onCoord_schedulesLookupAfterDebounce() {
    when(facade.nearestWithin(24.15, 120.65, 500.0))
        .thenReturn(new AddressRecord(24.15, 120.65, "台中市", "台中市"));
    AddressSubsystem s = makeSubsystem();
    s.setRowEnabled(AddressSubsystem.Row.ME, true);

    s.onCoord(AddressSubsystem.Row.ME, 24.15, 120.65);
    // Before debounce expires, only the Loading emission has fired.
    assertThat(emissions).hasSize(1);
    assertThat(emissions.get(0).state.isLoading()).isTrue();

    exec.advanceBy(DEBOUNCE);
    // Now the lookup result is in.
    assertThat(emissions).hasSize(2);
    assertThat(emissions.get(1).state.isText()).isTrue();
    assertThat(((AddressRowState.Text) emissions.get(1).state).value()).isEqualTo("台中市");
    s.close();
  }

  // ----------------------------------------------------------------------
  // Test 2 — onCoord cancels inflight on rapid fire (only one facade call)
  // ----------------------------------------------------------------------

  @Test
  public void onCoord_cancelsInflightLookupOnRapidFire() {
    when(facade.nearestWithin(anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(new AddressRecord(24.15, 120.65, "X", "X"));
    AddressSubsystem s = makeSubsystem();
    s.setRowEnabled(AddressSubsystem.Row.ME, true);

    s.onCoord(AddressSubsystem.Row.ME, 24.15, 120.65);
    exec.advanceBy(DEBOUNCE / 2); // inflight, not fired yet
    s.onCoord(AddressSubsystem.Row.ME, 24.16, 120.66);
    exec.advanceBy(DEBOUNCE);

    verify(facade, times(1)).nearestWithin(anyDouble(), anyDouble(), anyDouble());
    verify(facade).nearestWithin(24.16, 120.66, 500.0);
    s.close();
  }

  // ----------------------------------------------------------------------
  // Test 3 — per-row coalescing is independent
  // ----------------------------------------------------------------------

  @Test
  public void perRowCoalescing_isIndependent() {
    when(facade.nearestWithin(anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(new AddressRecord(0, 0, "z", "z"));
    AddressSubsystem s = makeSubsystem();
    s.setRowEnabled(AddressSubsystem.Row.ME, true);
    s.setRowEnabled(AddressSubsystem.Row.TGT, true);

    s.onCoord(AddressSubsystem.Row.ME, 1, 1);
    s.onCoord(AddressSubsystem.Row.TGT, 2, 2);
    // ME burst — re-fires ME but MUST NOT cancel TGT.
    s.onCoord(AddressSubsystem.Row.ME, 3, 3);
    exec.advanceBy(DEBOUNCE);

    verify(facade).nearestWithin(2, 2, 500.0); // TGT survived
    verify(facade).nearestWithin(3, 3, 500.0); // ME's latest
    verify(facade, times(2)).nearestWithin(anyDouble(), anyDouble(), anyDouble());
    s.close();
  }

  // ----------------------------------------------------------------------
  // Test 4 — setRowEnabled(false) clears the row to Hidden
  // ----------------------------------------------------------------------

  @Test
  public void setRowEnabledFalse_clearsRow() {
    when(facade.nearestWithin(anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(new AddressRecord(0, 0, "y", "y"));
    AddressSubsystem s = makeSubsystem();
    s.setRowEnabled(AddressSubsystem.Row.MAP, true);
    s.onCoord(AddressSubsystem.Row.MAP, 1, 1);
    exec.advanceBy(DEBOUNCE);
    assertThat(lastEmissionFor(AddressSubsystem.Row.MAP).isText()).isTrue();

    s.setRowEnabled(AddressSubsystem.Row.MAP, false);
    assertThat(lastEmissionFor(AddressSubsystem.Row.MAP).isHidden()).isTrue();
    s.close();
  }

  @Test
  public void clearRow_cancelsSelectedTargetLookupWithoutDisablingFutureTargets() {
    AddressSubsystem s = makeSubsystem();
    FakeLookupService shared = new FakeLookupService();
    s.setLookupService(shared);
    s.setRowEnabled(AddressSubsystem.Row.TGT, true);

    s.onCoord(AddressSubsystem.Row.TGT, 24.1, 120.6);
    exec.advanceBy(DEBOUNCE);
    LookupHandle dismissed = shared.lastHandle;

    s.clearRow(AddressSubsystem.Row.TGT);

    assertThat(dismissed.isCancelled()).isTrue();
    assertThat(lastEmissionFor(AddressSubsystem.Row.TGT).isHidden()).isTrue();
    shared.complete("stale target");
    assertThat(lastEmissionFor(AddressSubsystem.Row.TGT).isHidden()).isTrue();

    s.onCoord(AddressSubsystem.Row.TGT, 24.2, 120.7);
    exec.advanceBy(DEBOUNCE);
    assertThat(shared.lastHandle).isNotSameAs(dismissed);
    shared.complete("new target");
    assertThat(lastEmissionFor(AddressSubsystem.Row.TGT).isText()).isTrue();
    s.close();
  }

  @Test
  public void clearRow_suppressesLegacyResultAlreadyQueuedForUi() {
    when(facade.nearestWithin(24.1, 120.6, 500.0))
        .thenReturn(new AddressRecord(24.1, 120.6, "stale target", "stale target"));
    List<Runnable> queuedUi = new ArrayList<>();
    AddressSubsystem s = makeSubsystem(queuedUi::add);
    s.setRowEnabled(AddressSubsystem.Row.TGT, true);

    s.onCoord(AddressSubsystem.Row.TGT, 24.1, 120.6);
    exec.advanceBy(DEBOUNCE);
    assertThat(queuedUi).hasSize(1);

    s.clearRow(AddressSubsystem.Row.TGT);
    queuedUi.remove(0).run();

    assertThat(lastEmissionFor(AddressSubsystem.Row.TGT).isHidden()).isTrue();
    s.close();
  }

  @Test
  public void clearRow_suppressesSharedResultAlreadyQueuedForUiAndAllowsNextTarget() {
    List<Runnable> queuedUi = new ArrayList<>();
    AddressSubsystem s = makeSubsystem(queuedUi::add);
    FakeLookupService shared = new FakeLookupService();
    s.setLookupService(shared);
    s.setRowEnabled(AddressSubsystem.Row.TGT, true);

    s.onCoord(AddressSubsystem.Row.TGT, 24.1, 120.6);
    exec.advanceBy(DEBOUNCE);
    shared.complete("stale target");
    assertThat(queuedUi).hasSize(1);

    s.clearRow(AddressSubsystem.Row.TGT);
    queuedUi.remove(0).run();
    assertThat(lastEmissionFor(AddressSubsystem.Row.TGT).isHidden()).isTrue();

    s.onCoord(AddressSubsystem.Row.TGT, 24.2, 120.7);
    exec.advanceBy(DEBOUNCE);
    shared.complete("new target");
    assertThat(queuedUi).hasSize(1);
    queuedUi.remove(0).run();
    assertThat(lastEmissionFor(AddressSubsystem.Row.TGT).isText()).isTrue();
    s.close();
  }

  // ----------------------------------------------------------------------
  // Test 5 — no dataset → emits Hidden, not Loading; no scheduling
  // ----------------------------------------------------------------------

  @Test
  public void noDataset_emitsHiddenNotLoading() {
    // Override the importer to return no active dataset.
    when(importer.activeOrNull()).thenReturn(null);
    AddressSubsystem s = makeSubsystem();
    s.setRowEnabled(AddressSubsystem.Row.ME, true);
    s.onCoord(AddressSubsystem.Row.ME, 1, 1);

    // No Loading emission (would be flicker) and no scheduled task (no background work
    // when there's nothing to look up). The row stays Hidden — but emit() de-duplicates
    // identical states so the listener is not woken with a redundant Hidden→Hidden
    // transition.
    for (EmittedState e : emissions) {
      assertThat(e.state.isLoading())
          .as("no Loading emission allowed when there is no dataset")
          .isFalse();
    }
    assertThat(exec.pendingCount()).isZero();
    assertThat(lastEmissionFor(AddressSubsystem.Row.ME).isHidden()).isTrue();
    s.close();
  }

  // ----------------------------------------------------------------------
  // Test 6a — US4 / SC-005: onActiveDatasetChanged with no dataset emits Hidden
  //                          (no Loading flash) for every enabled row
  // ----------------------------------------------------------------------

  /**
   * After a Remove (or after files vanish underneath the plugin), {@link
   * AddressSubsystem#onActiveDatasetChanged()} runs. The cached facade is dropped; the next coord
   * refresh will see {@code facade == null} and emit Hidden. But we must NOT flash "Loading…" in
   * the meantime — that would be a visible glitch with no resolution because no background lookup
   * will ever fire. Verify every enabled row transitions directly to Hidden on the change broadcast
   * itself, regardless of the row's previous state.
   */
  @Test
  public void onActiveDatasetChanged_noDataset_emitsHiddenDirectlyNotLoading() {
    when(facade.nearestWithin(anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(new AddressRecord(0, 0, "before-removal", "before-removal"));
    AddressSubsystem s = makeSubsystem();
    s.setRowEnabled(AddressSubsystem.Row.ME, true);
    s.setRowEnabled(AddressSubsystem.Row.MAP, true);
    s.onCoord(AddressSubsystem.Row.ME, 1, 1);
    s.onCoord(AddressSubsystem.Row.MAP, 2, 2);
    exec.advanceBy(DEBOUNCE);
    // Both rows show a Text address.
    assertThat(lastEmissionFor(AddressSubsystem.Row.ME).isText()).isTrue();
    assertThat(lastEmissionFor(AddressSubsystem.Row.MAP).isText()).isTrue();

    // Operator removes the dataset (or files vanish). Importer now returns null.
    when(importer.activeOrNull()).thenReturn(null);
    int emissionsBefore = emissions.size();
    s.onActiveDatasetChanged();

    // Every enabled row goes straight to Hidden — no Loading in between.
    for (int i = emissionsBefore; i < emissions.size(); i++) {
      assertThat(emissions.get(i).state.isLoading())
          .as("emission #" + i + " should NOT be Loading when dataset is null")
          .isFalse();
    }
    assertThat(lastEmissionFor(AddressSubsystem.Row.ME).isHidden()).isTrue();
    assertThat(lastEmissionFor(AddressSubsystem.Row.MAP).isHidden()).isTrue();
    s.close();
  }

  // ----------------------------------------------------------------------
  // Test 6 — close cancels all + closes facade
  // ----------------------------------------------------------------------

  @Test
  public void close_cancelsAllAndClosesFacade() {
    AddressSubsystem s = makeSubsystem();
    s.setRowEnabled(AddressSubsystem.Row.ME, true);
    s.setRowEnabled(AddressSubsystem.Row.MAP, true);
    s.onCoord(AddressSubsystem.Row.ME, 1, 1);
    s.onCoord(AddressSubsystem.Row.MAP, 2, 2);
    assertThat(exec.pendingCount()).isEqualTo(2);

    s.close();

    assertThat(exec.pendingCount()).isZero();
    verify(facade).close();
    assertThat(exec.isShutdown()).isTrue();
  }

  @Test
  public void sharedWorkerReadoutUsesBackgroundPriorityAndLatestRowRequestWins() {
    AddressSubsystem s = makeSubsystem();
    FakeLookupService shared = new FakeLookupService();
    s.setLookupService(shared);
    s.setRowEnabled(AddressSubsystem.Row.MAP, true);

    s.onCoord(AddressSubsystem.Row.MAP, 24.1, 120.6);
    exec.advanceBy(DEBOUNCE);
    LookupHandle first = shared.lastHandle;
    s.onCoord(AddressSubsystem.Row.MAP, 24.2, 120.7);
    exec.advanceBy(DEBOUNCE);

    assertThat(first.isCancelled()).isTrue();
    assertThat(shared.lastRequest.priority()).isEqualTo(LookupPriority.WIDGET_BACKGROUND);
    assertThat(shared.lastRequest.consumerKey()).isEqualTo("widget-MAP");
    shared.complete("臺中市南屯區黎明路2段130號");
    assertThat(lastEmissionFor(AddressSubsystem.Row.MAP).isText()).isTrue();
    s.close();
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private AddressRowState lastEmissionFor(AddressSubsystem.Row row) {
    AddressRowState last = AddressRowState.hidden();
    for (EmittedState e : emissions) if (e.row == row) last = e.state;
    return last;
  }

  private static final class EmittedState {
    final AddressSubsystem.Row row;
    final AddressRowState state;

    EmittedState(AddressSubsystem.Row row, AddressRowState state) {
      this.row = row;
      this.state = state;
    }
  }

  private static final class FakeLookupService implements AddressLookupService {
    ReverseAddressRequest lastRequest;
    Consumer<ReverseAddressResult> callback;
    Handle lastHandle;

    @Override
    public LookupHandle forward(
        ForwardAddressRequest request, Consumer<ForwardAddressResult> callback) {
      return new Handle();
    }

    @Override
    public LookupHandle reverse(
        ReverseAddressRequest request, Consumer<ReverseAddressResult> callback) {
      this.lastRequest = request;
      this.callback = callback;
      this.lastHandle = new Handle();
      return lastHandle;
    }

    void complete(String display) {
      DatasetIdentity dataset = new DatasetIdentity("臺中市", "115-07", 3, "sha", "fixture");
      AddressCandidate candidate =
          new AddressCandidate(
              "candidate",
              display,
              display,
              new Wgs84(24.2001, 120.7001, 1L, Wgs84.Source.COT_TARGET),
              AddressMatchKind.PARTIAL,
              15d,
              "臺中市",
              dataset);
      callback.accept(
          ReverseAddressResult.found(lastRequest.identity(), lastRequest.queryPoint(), candidate));
    }

    @Override
    public AddressAvailability availability() {
      return new AddressAvailability(Collections.singleton("臺中市"), true, 1L, false);
    }

    @Override
    public void addAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void removeAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void close() {}
  }

  private static final class Handle implements LookupHandle {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    @Override
    public void cancel() {
      cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
      return cancelled.get();
    }
  }

  /**
   * Minimal {@link ScheduledExecutorService} that holds scheduled tasks in a priority queue and
   * fires them on {@link #advanceBy(long)}. Only the methods {@link AddressSubsystem} actually
   * calls are implemented; the rest throw {@link UnsupportedOperationException}.
   */
  private static final class FakeScheduledExecutor extends AbstractExecutorService
      implements ScheduledExecutorService {

    private final PriorityQueue<Task> queue = new PriorityQueue<>();
    private long nowMs = 0L;
    private boolean shutdown;

    void advanceBy(long ms) {
      nowMs += ms;
      while (!queue.isEmpty() && queue.peek().fireAt <= nowMs) {
        Task t = queue.poll();
        if (!t.cancelled) t.r.run();
      }
    }

    int pendingCount() {
      int n = 0;
      for (Task t : queue) if (!t.cancelled) n++;
      return n;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      Task t = new Task(nowMs + unit.toMillis(delay), command);
      queue.add(t);
      return t;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      List<Runnable> remaining = new ArrayList<>();
      for (Task t : queue) {
        if (!t.cancelled) remaining.add(t.r);
        t.cancelled = true;
      }
      queue.clear();
      return remaining;
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown && queue.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return true; // synchronous fake
    }

    @Override
    public void execute(Runnable command) {
      if (!shutdown) command.run();
    }

    private static final class Task implements ScheduledFuture<Object> {
      final long fireAt;
      final Runnable r;
      volatile boolean cancelled;

      Task(long fireAt, Runnable r) {
        this.fireAt = fireAt;
        this.r = r;
      }

      @Override
      public long getDelay(TimeUnit unit) {
        return unit.convert(fireAt, TimeUnit.MILLISECONDS);
      }

      @Override
      public int compareTo(Delayed o) {
        if (o instanceof Task) return Long.compare(fireAt, ((Task) o).fireAt);
        return 0;
      }

      @Override
      public boolean cancel(boolean mayInterruptIfRunning) {
        cancelled = true;
        return true;
      }

      @Override
      public boolean isCancelled() {
        return cancelled;
      }

      @Override
      public boolean isDone() {
        return cancelled;
      }

      @Override
      public Object get() throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException();
      }

      @Override
      public Object get(long timeout, TimeUnit unit)
          throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
      }
    }

    /** Drain helper for advance-then-collect patterns (currently unused; kept for symmetry). */
    Collection<Task> snapshot() {
      return new ArrayList<>(queue);
    }
  }
}
