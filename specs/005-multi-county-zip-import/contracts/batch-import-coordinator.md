# Contract: BatchImportCoordinator

**Module**: `app/src/main/java/com/atakmap/android/twcoord/address/BatchImportCoordinator.java` (NEW)

Drives N picked files (each either a `.zip` or a bare `.sqlite`) through 004's existing per-file pipeline (`AddressBundleImporter`), emitting a per-entry status into a `BatchImportReport`. Owns the batch session lifecycle.

## Interface

```java
public final class BatchImportCoordinator {

  public BatchImportCoordinator(
      AddressBundleImporter importer,
      ZipExtractor zipExtractor,
      ZipEntryClassifier classifier,
      ActiveDatasetRegistry registry,
      ExecutorService importExecutor);

  /**
   * Add a picked file (either a {@code .zip} or a bare {@code .sqlite}) to the active session.
   * If no session is open, creates one. Returns the index of the entry within the session.
   */
  public int enqueue(File pickedFile);

  /**
   * Signal end-of-batch from the operator's "完成 / Done" tap. The worker drains the queue and
   * fires {@code onBatchComplete(BatchImportReport)} on the UI thread when done.
   */
  public void finishBatch();

  /** Cancel: in-flight entry completes naturally, pending entries are dropped. */
  public void cancelBatch();

  /** Observer for per-entry status updates (page binding + Settings refresh). */
  public interface Listener {
    void onEntryStarted(BatchImportReport.Entry entry);
    void onEntryFinished(BatchImportReport.Entry entry);  // status field is final by then
    void onBatchComplete(BatchImportReport report);
  }

  public void addListener(Listener listener);
  public void removeListener(Listener listener);
}
```

## Invariants

1. **Single-thread import**: all extract+validate+activate work runs on the injected single-thread `importExecutor` (same instance 004 ships, reused). The coordinator never spawns new threads.
2. **Per-entry atomicity**: an `Entry` reaches either ACTIVATED, REPLACED, one of the SKIPPED_* variants, or FAILED. No partial state.
3. **Inter-entry isolation**: an entry's FAILED status MUST NOT prevent subsequent entries from being processed.
4. **Listener fan-out is wrapped**: each `Listener.onEntryStarted` / `onEntryFinished` / `onBatchComplete` invocation is inside its own `try/catch (Throwable)` (Constitution VI listener short-circuit rule).
5. **`enqueue` is reentrant**: callable from the UI thread while the worker is draining; new entries append to the session's pending queue.
6. **`finishBatch` is idempotent**: a second call on a draining session is a no-op.
7. **`ACTION_DATASET_CHANGED` per atomic registry change**: the coordinator does not fire the broadcast directly; the `ActiveDatasetRegistry` fires one per atomic mutation (research R8). The coordinator's contract is to mutate the registry once per successful entry.
8. **No outbound network**: same as 004; the coordinator does not even compose with any class that touches `java.net.*`.

## Test plan (`BatchImportCoordinatorTest`, JVM/Robolectric)

| # | Scenario | Expected |
|---|---|---|
| 1 | Single bare `.sqlite`, validation passes | one Entry with ACTIVATED status; registry has 1 entry; one ACTION_DATASET_CHANGED |
| 2 | Single bare `.sqlite`, magic-bytes fail | one Entry with FAILED status, reason=NOT_OPENABLE; registry empty |
| 3 | ZIP with {taichung, changhua, osm, townships, roads, timestamp.base, timestamp.taichung, timestamp.changhua} | 2 ACTIVATED + 5 SKIPPED_SUPPLEMENTARY + 1 broadcast per ACTIVATED |
| 4 | ZIP with 2 valid + 1 disk-full simulated | 2 ACTIVATED + 1 FAILED reason=DISK_FULL; registry has 2 entries |
| 5 | ZIP with duplicate places-taichung entries | first ACTIVATED, rest SKIPPED_DUPLICATE; registry has 1 entry |
| 6 | Two batches back-to-back (`enqueue × 3 → finishBatch → enqueue × 2 → finishBatch`) | first session reports 3 entries, second session reports 2; per-batch ACTION_DATASET_CHANGED counts match successful activations |
| 7 | `enqueue` while previous batch is mid-extract | new file appends to current session's pending; per Q3 reentrancy |
| 8 | `cancelBatch` during a ZIP extract | in-flight entry completes, pending entries get a synthetic FAILED reason=CANCELLED |
| 9 | Listener throws on `onEntryFinished` | exception caught + logged; other listeners + subsequent entries unaffected |
| 10 | Listener removed mid-batch | no more callbacks fire for that listener; batch continues normally |

## Anchors

- Reuses `AddressBundleImporter.ImportResult` + `Reason` enum from 004 unchanged.
- `BatchImportReport.Entry.Status` is a new enum (see data-model.md §2.4).
