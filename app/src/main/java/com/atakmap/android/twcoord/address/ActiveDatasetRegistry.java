package com.atakmap.android.twcoord.address;

import com.atakmap.coremap.log.Log;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Feature 005 — in-memory map of {@code county → CountyActiveDataset}. Replaces 004's single active
 * dataset model. Observed by {@code AddressSubsystem} (resolver fan-out) and the Offline Address
 * page + Settings fragment (UI bind).
 *
 * <p>Per contracts/active-dataset-registry.md:
 *
 * <ul>
 *   <li>One facade per county at any time. {@link #add} / {@link #replace} close the old facade
 *       (REPLACE only); {@link #remove} closes the facade and erases the entry.
 *   <li>Atomic mutations: each mutator is one ConcurrentMap mutation + one listener fan-out.
 *   <li>Listener fan-out wrapped per Constitution VI listener short-circuit rule.
 *   <li>Fallback factory lazily resolved on first failure of the primary path; if all primary opens
 *       succeed, the fallback library is never classloaded.
 * </ul>
 */
public final class ActiveDatasetRegistry implements AutoCloseable {

  private static final String TAG = "ActiveDatasetRegistry";

  /** Change events broadcast to listeners. */
  public enum Change {
    ADDED,
    REPLACED,
    REMOVED,
    TAMPERED
  }

  /** Listener interface for UI / subsystem observers. Each invocation is exception-isolated. */
  public interface Listener {
    void onChange(String county, Change change);
  }

  private final AddressBundleImporter importer;
  private final AddressDatabaseFacade.Factory primaryFactory;
  private final Supplier<AddressDatabaseFacade.Factory> fallbackFactorySupplier;
  private final FileSystem fs;

  private final ConcurrentHashMap<String, CountyActiveDataset> data = new ConcurrentHashMap<>();
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final ReentrantReadWriteLock ownershipLock = new ReentrantReadWriteLock(true);
  private volatile long revision;
  private volatile boolean closed;

  /** Lazy-cached fallback factory; created on first need (research R5). */
  private final AtomicReference<AddressDatabaseFacade.Factory> fallbackFactory =
      new AtomicReference<>();

  public ActiveDatasetRegistry(
      AddressBundleImporter importer,
      AddressDatabaseFacade.Factory primaryFactory,
      Supplier<AddressDatabaseFacade.Factory> fallbackFactorySupplier,
      FileSystem fs) {
    this.importer = Objects.requireNonNull(importer, "importer");
    this.primaryFactory = Objects.requireNonNull(primaryFactory, "primaryFactory");
    this.fallbackFactorySupplier =
        Objects.requireNonNull(fallbackFactorySupplier, "fallbackFactorySupplier");
    this.fs = Objects.requireNonNull(fs, "fs");
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  /**
   * Walk every sub-directory under {@code active/} and open the county that's present. Skips
   * corrupt counties (per-county isolation) but logs each skip at {@code Log.w}.
   */
  public void initFromDisk() {
    if (closed) return;
    Path activeRoot = fs.getActiveDir();
    if (!fs.exists(activeRoot)) {
      Log.i(TAG, "initFromDisk: no active root at " + activeRoot);
      return;
    }
    Set<String> countiesFromDisk = new HashSet<>();
    try (Stream<Path> stream = Files.list(activeRoot)) {
      stream
          .filter(Files::isDirectory)
          .forEach(
              dir -> {
                String county = dir.getFileName().toString();
                if (county.startsWith(".")) return; // skip .staging-*/ and hidden dirs
                // Feature 006: the boundary layer mounts at active/_boundary/; it is not a county.
                if (county.startsWith("_")) return;
                countiesFromDisk.add(county);
                openAndRegister(county, Change.ADDED);
              });
    } catch (IOException e) {
      Log.w(TAG, "initFromDisk: list " + activeRoot + " failed", e);
    }
    Log.i(
        TAG,
        "initFromDisk: loaded "
            + snapshot().size()
            + "/"
            + countiesFromDisk.size()
            + " active counties");
  }

  /** Atomic add: a fresh county becomes active. Caller MUST have completed import + activation. */
  public void add(CountyActiveDataset dataset) {
    Objects.requireNonNull(dataset, "dataset");
    String county = dataset.county();
    boolean changed = false;
    ownershipLock.writeLock().lock();
    try {
      if (closed) {
        closeQuietly(dataset.facade());
        return;
      }
      CountyActiveDataset prev = data.put(county, dataset);
      if (prev != null) closeQuietly(prev.facade());
      revision++;
      changed = true;
    } finally {
      ownershipLock.writeLock().unlock();
    }
    if (changed) fireChange(county, Change.ADDED);
  }

  /** Atomic replace: same county, new dataset; closes the previous facade. */
  public void replace(CountyActiveDataset dataset) {
    Objects.requireNonNull(dataset, "dataset");
    String county = dataset.county();
    boolean changed = false;
    ownershipLock.writeLock().lock();
    try {
      if (closed) {
        closeQuietly(dataset.facade());
        return;
      }
      CountyActiveDataset prev = data.put(county, dataset);
      if (prev != null) closeQuietly(prev.facade());
      revision++;
      changed = true;
    } finally {
      ownershipLock.writeLock().unlock();
    }
    if (changed) fireChange(county, Change.REPLACED);
  }

  /** Atomic remove: deletes the on-disk directory + closes the facade + erases the map entry. */
  public void remove(String county) {
    if (county == null) return;
    boolean changed = false;
    ownershipLock.writeLock().lock();
    try {
      if (closed) return;
      CountyActiveDataset prev = data.remove(county);
      if (prev != null) closeQuietly(prev.facade());
      try {
        Path dir = fs.activeCountyDir(county);
        if (fs.exists(dir)) fs.deleteRecursively(dir);
      } catch (RuntimeException e) {
        Log.w(TAG, "remove(" + county + "): on-disk delete threw", e);
      }
      revision++;
      changed = true;
    } finally {
      ownershipLock.writeLock().unlock();
    }
    if (changed) fireChange(county, Change.REMOVED);
  }

  /**
   * Mark a county as tampered (files vanished externally) and de-register it. Idempotent — calling
   * on an already-removed county is a no-op (and no second TAMPERED event fires).
   */
  public void deregisterOnTamper(String county) {
    if (county == null) return;
    boolean changed = false;
    ownershipLock.writeLock().lock();
    try {
      if (closed) return;
      CountyActiveDataset prev = data.remove(county);
      if (prev == null) return;
      closeQuietly(prev.facade());
      revision++;
      changed = true;
    } finally {
      ownershipLock.writeLock().unlock();
    }
    if (!changed) return;
    Log.w(TAG, "deregisterOnTamper: " + county + " files vanished externally");
    fireChange(county, Change.TAMPERED);
  }

  /** Unmodifiable snapshot of {@code county → CountyActiveDataset} for resolver + UI binding. */
  public Map<String, CountyActiveDataset> snapshot() {
    ownershipLock.readLock().lock();
    try {
      return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    } finally {
      ownershipLock.readLock().unlock();
    }
  }

  /** Open a leased immutable snapshot whose facades remain valid until the session closes. */
  public ReadSession openReadSession() {
    ownershipLock.readLock().lock();
    if (closed) {
      ownershipLock.readLock().unlock();
      throw new IllegalStateException("registry is closed");
    }
    return new ReadSession(
        Collections.unmodifiableMap(new LinkedHashMap<>(data)), revision, ownershipLock.readLock());
  }

  public long revision() {
    return revision;
  }

  public boolean isClosed() {
    return closed;
  }

  /** Total bytes of {@code places.sqlite} across every active county. Best-effort. */
  public long totalBytesOnDisk() {
    ownershipLock.readLock().lock();
    try {
      long total = 0;
      for (CountyActiveDataset c : data.values()) {
        File f = c.placesFile();
        if (f != null && f.isFile()) total += f.length();
      }
      return total;
    } finally {
      ownershipLock.readLock().unlock();
    }
  }

  /** Diagnostic: returns whether the fallback factory was created (i.e. ever needed). */
  public boolean isFallbackInitialised() {
    return fallbackFactory.get() != null;
  }

  // ----------------------------------------------------------------------
  // Listener fan-out (Constitution VI listener short-circuit rule)
  // ----------------------------------------------------------------------

  public void addListener(Listener listener) {
    if (listener == null) return;
    ownershipLock.readLock().lock();
    try {
      if (!closed) listeners.add(listener);
    } finally {
      ownershipLock.readLock().unlock();
    }
  }

  public void removeListener(Listener listener) {
    if (listener != null) listeners.remove(listener);
  }

  private void fireChange(String county, Change change) {
    for (Listener l : listeners) {
      try {
        l.onChange(county, change);
      } catch (Throwable t) {
        Log.w(TAG, "listener " + l + " onChange threw", t);
      }
    }
  }

  // ----------------------------------------------------------------------
  // Internals
  // ----------------------------------------------------------------------

  /** Open the county via primary; on failure, escalate to fallback (lazy). */
  private void openAndRegister(String county, Change change) {
    AddressDataset ds = importer.activeForCounty(county);
    if (ds == null) {
      Log.w(TAG, "openAndRegister: importer.activeForCounty(" + county + ") returned null");
      return;
    }
    AddressDatabaseFacade facade = openFacadeWithFallback(ds.dbFile());
    if (facade == null) {
      Log.w(TAG, "openAndRegister: neither primary nor fallback opened " + ds.dbFile());
      return;
    }
    CountyActiveDataset entry = new CountyActiveDataset(county, ds, facade);
    boolean registered = false;
    ownershipLock.writeLock().lock();
    try {
      if (closed) {
        closeQuietly(facade);
        return;
      }
      CountyActiveDataset prev = data.put(county, entry);
      if (prev != null) closeQuietly(prev.facade());
      revision++;
      registered = true;
    } finally {
      ownershipLock.writeLock().unlock();
    }
    if (registered) fireChange(county, change);
  }

  /**
   * Tries the primary factory first. On null result, lazily creates the fallback factory and
   * retries. The probe-with-rtree-SELECT step from research R5 is left to the higher-level test
   * code; for the registry-internal path, we treat any non-null facade as good.
   *
   * <p>Public so {@code BatchImportCoordinator.peekCounty} can read {@code metadata.county} through
   * the same primary→fallback path (and shared lazy fallback cache) the registry uses to activate;
   * otherwise a file the fallback could open but the primary cannot would be rejected at peek
   * before activation ever reaches the fallback. Caller owns the returned facade's {@code close()}.
   */
  public AddressDatabaseFacade openFacadeWithFallback(File dbFile) {
    if (closed) return null;
    AddressDatabaseFacade primary;
    try {
      primary = primaryFactory.open(dbFile);
    } catch (Throwable t) {
      Log.w(TAG, "primary factory.open threw for " + dbFile, t);
      primary = null;
    }
    if (primary != null) return primary;
    AddressDatabaseFacade.Factory fb = fallbackFactory.get();
    if (fb == null) {
      try {
        fb = fallbackFactorySupplier.get();
      } catch (Throwable t) {
        Log.w(TAG, "fallback factory supplier threw", t);
        return null;
      }
      if (fb == null) {
        Log.w(TAG, "fallback factory supplier returned null");
        return null;
      }
      fallbackFactory.compareAndSet(null, fb);
      fb = fallbackFactory.get(); // re-read in case of race
    }
    try {
      return fb.open(dbFile);
    } catch (Throwable t) {
      Log.w(TAG, "fallback factory.open threw for " + dbFile, t);
      return null;
    }
  }

  private static void closeQuietly(AddressDatabaseFacade f) {
    if (f == null) return;
    try {
      f.close();
    } catch (Throwable t) {
      Log.w(TAG, "facade close threw", t);
    }
  }

  @Override
  public void close() {
    ownershipLock.writeLock().lock();
    try {
      if (closed) return;
      closed = true;
      for (CountyActiveDataset dataset : data.values()) closeQuietly(dataset.facade());
      data.clear();
      revision++;
    } finally {
      ownershipLock.writeLock().unlock();
    }
    listeners.clear();
  }

  /** Read-side ownership token. Close on the same worker thread that opened it. */
  public static final class ReadSession implements AutoCloseable {
    private final Map<String, CountyActiveDataset> datasets;
    private final long revision;
    private final java.util.concurrent.locks.Lock readLock;
    private final AtomicBoolean released = new AtomicBoolean();

    private ReadSession(
        Map<String, CountyActiveDataset> datasets,
        long revision,
        java.util.concurrent.locks.Lock readLock) {
      this.datasets = datasets;
      this.revision = revision;
      this.readLock = readLock;
    }

    public Map<String, CountyActiveDataset> datasets() {
      return datasets;
    }

    public long revision() {
      return revision;
    }

    @Override
    public void close() {
      if (released.compareAndSet(false, true)) readLock.unlock();
    }
  }
}
