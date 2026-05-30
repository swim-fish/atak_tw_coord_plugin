# Contract: `AddressResolver` (+ `AddressSubsystem`)

**Package**: `com.atakmap.android.twcoord.address`

**Source of truth for**: the runtime orchestrator that turns "ME / TGT / MAP coordinate
updated" into "widget row's `AddressRowState` updated". Tests assert against this contract.

## `AddressResolver` — pure algorithm

```java
public final class AddressResolver {
    public AddressResolver(AddressDatabaseFacade facade, double radiusMeters /* default 500 */);

    /** Synchronous, off-UI-thread. */
    public AddressLookupResult lookup(double lat, double lon);
}
```

The resolver is a thin wrapper around `facade.nearestWithin(...)` that maps:

- `null` → `AddressLookupResult.Empty.INSTANCE` if a facade is present.
- `null` → `AddressLookupResult.NoDataset.INSTANCE` if the facade is null (no active dataset).
- `AddressRecord` → `AddressLookupResult.Found(record)`.

No threading, no caching — the resolver is pure compute.

## `AddressSubsystem` — lifecycle owner

```java
public final class AddressSubsystem implements AutoCloseable {
    public enum Row { ME, TGT, MAP }

    public interface Listener {
        void onAddressRowStateChanged(Row row, AddressRowState state);
    }

    public AddressSubsystem(
        AddressBundleImporter importer,
        AddressDatabaseFacade.Factory facadeFactory,
        java.util.concurrent.ScheduledExecutorService executor,
        long debounceMs /* default 250 */);

    /** Replace the per-row toggle state. */
    public void setRowEnabled(Row row, boolean enabled);

    /** New coordinate arrived for a row. The subsystem decides whether to schedule a lookup. */
    public void onCoord(Row row, double lat, double lon);

    /** Subscribe to per-row state transitions. Listener called on the UI thread via mapView.post. */
    public void addListener(Listener listener);
    public void removeListener(Listener listener);

    /** Re-open the active dataset (post-import / post-remove). Idempotent. */
    public void onActiveDatasetChanged();

    /** Shut down the executor, close the facade, clear state. */
    @Override public void close();
}
```

### Concurrency

- `setRowEnabled` / `onCoord` / `addListener` / `removeListener` are UI-thread-only.
- The internal `ScheduledExecutorService` is a single-threaded executor (per
  [R7](../research.md#r7--threading-model-debounce--executor)).
- When `onCoord(row, lat, lon)` is called:
  1. If `setRowEnabled(row, false)` or there is no dataset → emit `Hidden` (resp. `NoDataset`
     handled the same way) and return.
  2. Cancel any in-flight `ScheduledFuture<?>` for this row.
  3. Schedule a new task `debounceMs` in the future: run `resolver.lookup(lat, lon)`, post the
     result back via the UI handler, fan it out to listeners.

### Per-row coalescing

Each `Row` value has its own scheduled task slot. Updates to ME do not cancel updates to TGT.

### State derivation

The widget receives `AddressRowState` values; the subsystem computes them from the lookup
result + the row's toggle state:

| Toggle | Dataset | Lookup result | Emitted state |
|---|---|---|---|
| off | any | (no lookup scheduled) | `Hidden` |
| on | absent | (no lookup scheduled) | `Hidden` (also: page shows hint) |
| on | present | `Loading` (no result yet) | `Loading` |
| on | present | `Found(r)` | `Text(r.displayName)` |
| on | present | `Empty` | `EmptyState` |

### Close

On `close()`:

- Cancel all scheduled tasks.
- Shutdown the executor (`shutdownNow`); wait briefly (50 ms) for in-flight tasks.
- Close the facade.
- Drop all listeners.

## Test plan

### `AddressResolverTest` (8 tests)

1. `lookup_returnsFoundForNearestRecord` (uses mock facade returning a record).
2. `lookup_returnsEmptyWhenFacadeReturnsNull`.
3. `lookup_returnsNoDatasetWhenFacadeIsNull`.
4. `radiusDefaultsTo500m` (verifies the parameter passed into `facade.nearestWithin`).
5. `radiusOverrideRespected` (construct with `radiusMeters = 100`, confirm propagation).
6. `lookup_handlesFacadeThrowingCleanly` (facade throws → caught, returns `Empty`).
7. `bboxCorrectionAtLatitude25` (sanity check on the cos-latitude math used in
   `SqliteAddressDatabase`; covered here via the facade boundary).
8. `lookup_isPureNoCaching` (calling twice with same args calls facade twice).

### `AddressSubsystemTest` (6 tests)

1. `onCoord_schedulesLookupAfterDebounce` (uses a `TestScheduledExecutorService`).
2. `onCoord_cancelsInflightLookupOnRapidFire` (two `onCoord` within 250 ms → only one
   facade call).
3. `perRowCoalescing_isIndependent` (ME burst doesn't cancel TGT's pending task).
4. `setRowEnabledFalse_clearsRow` (going from on → off emits `Hidden`).
5. `noDataset_emitsHiddenNotLoading` (no facade → no scheduling, immediate `Hidden`).
6. `close_cancelsAllAndClosesFacade`.
