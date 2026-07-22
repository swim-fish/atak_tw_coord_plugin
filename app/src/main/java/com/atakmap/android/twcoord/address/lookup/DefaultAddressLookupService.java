package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.address.ActiveDatasetRegistry;
import com.atakmap.android.twcoord.address.AddressRecord;
import com.atakmap.android.twcoord.address.CountyActiveDataset;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.coremap.log.Log;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Single bounded worker owner for forward and reverse offline address lookup. */
public final class DefaultAddressLookupService implements AddressLookupService {
  private static final String TAG = "DefaultAddressLookup";

  /**
   * Database/boundary query seam. Feature-specific lookup rules are implemented behind this API.
   */
  public interface QueryEngine {
    ForwardAddressResult forward(
        ForwardAddressRequest request, ActiveDatasetRegistry.ReadSession session) throws Exception;

    ReverseAddressResult reverse(
        ReverseAddressRequest request, ActiveDatasetRegistry.ReadSession session) throws Exception;

    static QueryEngine noData() {
      return new QueryEngine() {
        @Override
        public ForwardAddressResult forward(
            ForwardAddressRequest request, ActiveDatasetRegistry.ReadSession session) {
          return session.datasets().isEmpty()
              ? ForwardAddressResult.noDataset(request.identity())
              : ForwardAddressResult.noMatch(request.identity());
        }

        @Override
        public ReverseAddressResult reverse(
            ReverseAddressRequest request, ActiveDatasetRegistry.ReadSession session) {
          return session.datasets().isEmpty()
              ? ReverseAddressResult.noDataset(request.identity(), request.queryPoint())
              : ReverseAddressResult.noMatch(request.identity(), request.queryPoint());
        }
      };
    }
  }

  /** Production query engine over one leased registry snapshot. */
  public static final class RegistryQueryEngine implements QueryEngine {
    private final TaiwanAddressParser parser;

    public RegistryQueryEngine(TaiwanAddressParser parser) {
      this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public ForwardAddressResult forward(
        ForwardAddressRequest request, ActiveDatasetRegistry.ReadSession session) {
      AddressDraft draft =
          parser.parse(
              request.normalizedAddress(),
              request.identity().draftRevision(),
              AddressInputMode.FULL);
      if (session.datasets().isEmpty()) return ForwardAddressResult.noDataset(request.identity());
      CountyActiveDataset active = session.datasets().get(draft.components().countyCity());
      if (active == null) return ForwardAddressResult.noDataset(request.identity());

      Wgs84 anchor = request.anchorPoint();
      List<AddressCandidate> raw =
          active.facade().fullAddressCandidates(draft, anchor, Math.max(request.limit(), 1));
      DatasetIdentity provenance = DatasetIdentity.from(active);
      Map<String, AddressCandidate> deduplicated = new LinkedHashMap<>();
      for (AddressCandidate candidate : raw) {
        String normalized = parser.normalize(candidate.displayAddress());
        AddressMatchKind kind =
            normalized.equals(draft.normalizedAddress())
                ? AddressMatchKind.EXACT
                : candidate.matchKind();
        String stableId = provenance.fileSha256() + ":" + candidate.candidateId();
        deduplicated.putIfAbsent(
            stableId,
            candidate.withLookupData(stableId, normalized, kind, active.county(), provenance));
      }
      if (deduplicated.isEmpty()) return ForwardAddressResult.noMatch(request.identity());
      List<AddressCandidate> candidates = new ArrayList<>(deduplicated.values());
      candidates.sort(
          Comparator.comparingInt(
                  (AddressCandidate candidate) ->
                      candidate.matchKind() == AddressMatchKind.EXACT ? 0 : 1)
              .thenComparingDouble(AddressCandidate::distanceMeters)
              .thenComparing(AddressCandidate::normalizedAddress)
              .thenComparing(AddressCandidate::candidateId));
      if (candidates.size() > request.limit()) {
        candidates = new ArrayList<>(candidates.subList(0, request.limit()));
      }
      return ForwardAddressResult.candidates(request.identity(), candidates);
    }

    @Override
    public ReverseAddressResult reverse(
        ReverseAddressRequest request, ActiveDatasetRegistry.ReadSession session) {
      if (session.datasets().isEmpty()) {
        return ReverseAddressResult.noDataset(request.identity(), request.queryPoint());
      }
      CountyActiveDataset bestDataset = null;
      AddressRecord bestRecord = null;
      double bestDistance = request.radiusMeters();
      Wgs84 query = request.queryPoint();
      for (CountyActiveDataset active : session.datasets().values()) {
        AddressRecord record =
            active.facade().nearestWithin(query.latitudeDeg(), query.longitudeDeg(), bestDistance);
        if (record == null) continue;
        double distance =
            haversineMeters(query.latitudeDeg(), query.longitudeDeg(), record.lat(), record.lon());
        if (distance > bestDistance) continue;
        bestDataset = active;
        bestRecord = record;
        bestDistance = distance;
      }
      if (bestRecord == null || bestDataset == null) {
        return ReverseAddressResult.noMatch(request.identity(), query);
      }
      DatasetIdentity provenance = DatasetIdentity.from(bestDataset);
      String normalized = parser.normalize(bestRecord.displayName());
      String stableId =
          provenance.fileSha256()
              + ":"
              + bestRecord.lat()
              + ":"
              + bestRecord.lon()
              + ":"
              + normalized;
      AddressCandidate candidate =
          new AddressCandidate(
              stableId,
              bestRecord.displayName(),
              normalized,
              new Wgs84(
                  bestRecord.lat(),
                  bestRecord.lon(),
                  query.timestampEpochMs(),
                  Wgs84.Source.COT_TARGET),
              AddressMatchKind.PARTIAL,
              bestDistance,
              bestDataset.county(),
              provenance);
      return ReverseAddressResult.found(request.identity(), query, candidate);
    }

    private static double haversineMeters(
        double latitude1, double longitude1, double latitude2, double longitude2) {
      double phi1 = Math.toRadians(latitude1);
      double phi2 = Math.toRadians(latitude2);
      double deltaPhi = Math.toRadians(latitude2 - latitude1);
      double deltaLambda = Math.toRadians(longitude2 - longitude1);
      double a =
          Math.sin(deltaPhi / 2.0) * Math.sin(deltaPhi / 2.0)
              + Math.cos(phi1)
                  * Math.cos(phi2)
                  * Math.sin(deltaLambda / 2.0)
                  * Math.sin(deltaLambda / 2.0);
      return 12_742_000.0 * Math.asin(Math.sqrt(a));
    }
  }

  private final ActiveDatasetRegistry registry;
  private final Executor completionDispatcher;
  private final QueryEngine queryEngine;
  private final int queueCapacity;
  private final Object queueLock = new Object();
  private final Deque<Work> nativeQueue = new ArrayDeque<>();
  private final Deque<Work> backgroundQueue = new ArrayDeque<>();
  private final Map<String, Work> latestByConsumer = new ConcurrentHashMap<>();
  private final List<AvailabilityListener> availabilityListeners = new CopyOnWriteArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean();
  private final ActiveDatasetRegistry.Listener registryListener =
      (county, change) -> refreshAvailability();
  private final Thread worker;
  private volatile AddressAvailability availability;

  public DefaultAddressLookupService(
      ActiveDatasetRegistry registry,
      Executor completionDispatcher,
      QueryEngine queryEngine,
      int queueCapacity) {
    if (queueCapacity <= 0) throw new IllegalArgumentException("queueCapacity must be positive");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.completionDispatcher =
        Objects.requireNonNull(completionDispatcher, "completionDispatcher");
    this.queryEngine = Objects.requireNonNull(queryEngine, "queryEngine");
    this.queueCapacity = queueCapacity;
    this.availability = currentAvailability(false);
    registry.addListener(registryListener);
    worker = new Thread(this::runWorker, "twcoord-address-lookup");
    worker.setDaemon(true);
    worker.start();
  }

  @Override
  public LookupHandle forward(
      ForwardAddressRequest request, Consumer<ForwardAddressResult> callback) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(callback, "callback");
    ensureOpen();
    Work work =
        new Work(request.consumerKey(), request.priority()) {
          @Override
          void runQuery() {
            ForwardAddressResult result;
            try (ActiveDatasetRegistry.ReadSession session = registry.openReadSession()) {
              result = queryEngine.forward(request, session);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            } catch (Exception e) {
              result = ForwardAddressResult.failure(request.identity(), e);
            }
            ForwardAddressResult completed = result;
            dispatch(this, () -> callback.accept(completed));
          }
        };
    enqueue(work);
    return work;
  }

  @Override
  public LookupHandle reverse(
      ReverseAddressRequest request, Consumer<ReverseAddressResult> callback) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(callback, "callback");
    ensureOpen();
    Work work =
        new Work(request.consumerKey(), request.priority()) {
          @Override
          void runQuery() {
            ReverseAddressResult result;
            try (ActiveDatasetRegistry.ReadSession session = registry.openReadSession()) {
              result = queryEngine.reverse(request, session);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            } catch (Exception e) {
              result = ReverseAddressResult.failure(request.identity(), request.queryPoint(), e);
            }
            ReverseAddressResult completed = result;
            dispatch(this, () -> callback.accept(completed));
          }
        };
    enqueue(work);
    return work;
  }

  @Override
  public AddressAvailability availability() {
    return availability;
  }

  @Override
  public void addAvailabilityListener(AvailabilityListener listener) {
    if (listener == null) return;
    synchronized (availabilityListeners) {
      if (!closed.get()) availabilityListeners.add(listener);
    }
  }

  @Override
  public void removeAvailabilityListener(AvailabilityListener listener) {
    if (listener != null) availabilityListeners.remove(listener);
  }

  int queuedWorkCount() {
    synchronized (queueLock) {
      return nativeQueue.size() + backgroundQueue.size();
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    registry.removeListener(registryListener);
    synchronized (queueLock) {
      cancelAndClear(nativeQueue);
      cancelAndClear(backgroundQueue);
      latestByConsumer.clear();
      queueLock.notifyAll();
    }
    synchronized (availabilityListeners) {
      availabilityListeners.clear();
    }
    availability = currentAvailability(true);
    worker.interrupt();
  }

  private void enqueue(Work work) {
    synchronized (queueLock) {
      ensureOpen();
      Work older = latestByConsumer.put(work.consumerKey, work);
      if (older != null) {
        older.cancel();
        nativeQueue.remove(older);
        backgroundQueue.remove(older);
      }
      while (nativeQueue.size() + backgroundQueue.size() >= queueCapacity) {
        Work evicted =
            !backgroundQueue.isEmpty() ? backgroundQueue.pollFirst() : nativeQueue.pollFirst();
        if (evicted != null) {
          evicted.cancel();
          latestByConsumer.remove(evicted.consumerKey, evicted);
        }
      }
      queueFor(work.priority).addLast(work);
      queueLock.notifyAll();
    }
  }

  private Deque<Work> queueFor(LookupPriority priority) {
    return priority == LookupPriority.NATIVE_INTERACTIVE ? nativeQueue : backgroundQueue;
  }

  private void runWorker() {
    while (!closed.get()) {
      Work work;
      synchronized (queueLock) {
        while (!closed.get() && nativeQueue.isEmpty() && backgroundQueue.isEmpty()) {
          try {
            queueLock.wait();
          } catch (InterruptedException e) {
            if (closed.get()) return;
          }
        }
        if (closed.get()) return;
        work = !nativeQueue.isEmpty() ? nativeQueue.pollFirst() : backgroundQueue.pollFirst();
      }
      if (work == null || work.isCancelled()) continue;
      try {
        work.runQuery();
      } catch (RuntimeException e) {
        Log.w(TAG, "lookup work threw", e);
      } finally {
        latestByConsumer.remove(work.consumerKey, work);
      }
    }
  }

  private void dispatch(Work work, Runnable callback) {
    if (closed.get() || work.isCancelled()) return;
    try {
      completionDispatcher.execute(
          () -> {
            if (closed.get() || work.isCancelled()) return;
            try {
              callback.run();
            } catch (RuntimeException e) {
              Log.w(TAG, "completion callback threw", e);
            }
          });
    } catch (RuntimeException e) {
      Log.w(TAG, "completion dispatch failed", e);
    }
  }

  private void refreshAvailability() {
    if (closed.get()) return;
    AddressAvailability updated = currentAvailability(false);
    availability = updated;
    for (AvailabilityListener listener : availabilityListeners) {
      try {
        listener.onAvailabilityChanged(updated);
      } catch (RuntimeException e) {
        Log.w(TAG, "availability listener threw", e);
      }
    }
  }

  private AddressAvailability currentAvailability(boolean terminal) {
    if (terminal || registry.isClosed()) {
      return new AddressAvailability(Collections.emptySet(), false, registry.revision(), true);
    }
    return new AddressAvailability(
        new LinkedHashSet<>(registry.snapshot().keySet()), false, registry.revision(), false);
  }

  private void ensureOpen() {
    if (closed.get()) throw new IllegalStateException("service is closed");
  }

  private static void cancelAndClear(Deque<Work> queue) {
    for (Work work : new ArrayList<>(queue)) work.cancel();
    queue.clear();
  }

  private abstract static class Work implements LookupHandle {
    private final String consumerKey;
    private final LookupPriority priority;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    Work(String consumerKey, LookupPriority priority) {
      this.consumerKey = Objects.requireNonNull(consumerKey, "consumerKey");
      this.priority = Objects.requireNonNull(priority, "priority");
    }

    abstract void runQuery();

    @Override
    public void cancel() {
      cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
      return cancelled.get();
    }
  }
}
