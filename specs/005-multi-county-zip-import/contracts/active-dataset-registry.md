# Contract: ActiveDatasetRegistry

**Module**: `app/src/main/java/com/atakmap/android/twcoord/address/ActiveDatasetRegistry.java` (NEW)

Replaces 004's single-active model (`AddressBundleImporter.activeOrNull()`) with a multi-county map. Owns each county's open SQLite facade for the lifetime of the active set. Observed by the resolver, the Settings fragment, and the Offline Address page.

## Interface

```java
public final class ActiveDatasetRegistry {

  public ActiveDatasetRegistry(
      AddressDatabaseFacade.Factory primaryFactory,
      AddressDatabaseFacade.Factory fallbackFactorySupplier,  // lazy-init via supplier; not opened unless needed
      AtakFileSystem fs);

  /** Discover active county directories under {@code active/*} and open each facade. */
  public void initFromDisk();

  /** Returns an unmodifiable snapshot suitable for resolver fan-out + UI binding. */
  public Map<String, CountyActiveDataset> snapshot();

  /** Atomic add (used by Import on a county not previously active). */
  public void add(CountyActiveDataset dataset);

  /** Atomic replace (used by Replace flow when county matches). */
  public void replace(CountyActiveDataset dataset);

  /** Atomic remove (used by per-county Remove flow). Closes the facade. */
  public void remove(String county);

  /** Mark a county as tampered (files vanished externally). Internal; closes facade silently. */
  void deregisterOnTamper(String county);

  /** Total bytes currently active on disk (UI footer). */
  public long totalBytesOnDisk();

  public interface Listener {
    void onChange(Change change);  // {ADDED, REPLACED, REMOVED, TAMPERED}
  }
  public void addListener(Listener listener);
  public void removeListener(Listener listener);
}
```

## Invariants

1. **One facade per county at any time**: `add` / `replace` open the new facade and close the old (REPLACE only); `remove` closes the facade and erases the map entry.
2. **Open-on-init, close-on-process-exit**: facades stay open for the lifetime of the active set. The resolver does NOT open/close per lookup (would re-pay the open cost ~5–20 ms per query, blowing SC-002).
3. **Atomic mutations**: each public mutator (`add`, `replace`, `remove`) is one `ConcurrentMap` mutation + one `ACTION_DATASET_CHANGED` broadcast + one listener fan-out. No partial state visible to observers.
4. **Listener fan-out is wrapped**: each listener's `onChange` is in its own `try/catch (Throwable)` (Constitution VI).
5. **Fallback factory is lazy**: the `fallbackFactorySupplier` is invoked only on first failure of the primary factory; if all primary opens succeed, the fallback library is never classloaded / native-init'd.
6. **`deregisterOnTamper` is idempotent**: a second call on an already-removed county is a no-op (defensive against the resolver's "I just noticed files are gone" being called from concurrent paths).
7. **`totalBytesOnDisk` is best-effort**: it walks `places.sqlite` sizes only; doesn't include `-shm` / `-wal`. Acceptable approximation for the Settings footer.

## Test plan (`ActiveDatasetRegistryTest`, JVM/Robolectric)

| # | Scenario | Expected |
|---|---|---|
| 1 | `initFromDisk` on empty `active/` | snapshot empty; no listener fires |
| 2 | `initFromDisk` with 2 county dirs both valid | snapshot has 2 entries; 2 ADDED events |
| 3 | `initFromDisk` with 1 valid + 1 corrupt | valid one added; corrupt one logged + skipped; snapshot has 1 entry |
| 4 | `add(台中市)` then `add(彰化縣)` | snapshot has 2 entries in iteration order; 2 ADDED events |
| 5 | `add(台中市)` then `replace(台中市)` with new SHA | snapshot has 1 entry with new SHA; old facade is closed; 1 ADDED + 1 REPLACED event |
| 6 | `remove(台中市)` after add | snapshot empty; facade closed; 1 REMOVED event |
| 7 | Primary factory fails, fallback succeeds | fallback supplier invoked once; subsequent county opens reuse the fallback factory; ADDED event |
| 8 | Primary factory fails, fallback also fails | county not added; `add` returns gracefully; no ADDED event; log noted |
| 9 | Listener throws | exception caught; other listeners still called; map mutation succeeds |
| 10 | `deregisterOnTamper` called twice concurrently | first call removes; second is a no-op; one REMOVED event total |
| 11 | `snapshot` immutability | returned map throws on `put` / `clear` (`UnsupportedOperationException`) |

## Anchors

- Uses `ConcurrentHashMap<String, CountyActiveDataset>` (insertion-order iteration on modern JDKs for the FR-009 tie-break rule).
- `ACTION_DATASET_CHANGED` broadcast emission is delegated to a small `BroadcastEmitter` collaborator so JVM tests don't need to mock `AtakBroadcast` (matching the 004 D3 seam pattern).
