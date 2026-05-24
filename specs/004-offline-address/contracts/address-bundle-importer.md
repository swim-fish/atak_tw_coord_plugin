# Contract: `AddressBundleImporter`

**Package**: `com.atakmap.android.twcoord.address`

**Source of truth for**: the import flow from "operator picks `.sqlite` via SAF" through
"atomic activation". Tests assert against this contract.

## Type signature (sketch)

```java
public final class AddressBundleImporter {
    public interface ProgressListener {
        void onProgress(Stage stage, long completedBytes, long totalBytes);
        enum Stage { COPYING, VERIFYING_METADATA, BUILDING_RTREE, ACTIVATING }
    }

    public sealed interface ImportResult permits Success, Failure {
        record Success(AddressDataset dataset) implements ImportResult {}
        record Failure(Reason reason, String details) implements ImportResult {}
        enum Reason {
            NOT_OPENABLE,                  // SQLite open failed
            IS_A_ZIP,                      // operator picked a .zip bundle — v1 expects bare .sqlite
            MISSING_METADATA_TABLE,
            MISSING_REQUIRED_METADATA_KEY, // e.g. schema_version absent
            UNSUPPORTED_SCHEMA_VERSION,    // schema_version != plugin-pinned
            MISSING_PLACES_TABLE,
            UNEXPECTED_PLACES_COLUMNS,
            RTREE_BUILD_FAILED,
            DISK_FULL,
            ACTIVATION_RENAME_FAILED,
            CANCELLED,
            IO_ERROR,
        }
    }

    public AddressBundleImporter(FileSystem fs, ShaCalculator sha, int pinnedSchemaVersion);

    /** Import a candidate file. Blocks the calling thread; intended to run on a worker. */
    public ImportResult importFrom(java.io.InputStream picked, ProgressListener listener);

    /** Remove the active dataset. Idempotent. */
    public void removeActive();

    /** Return the active dataset if any; null if none. */
    public AddressDataset activeOrNull();
}
```

`FileSystem` and `ShaCalculator` are JVM-mockable seams (the production wiring binds them to
`com.atakmap.coremap.filesystem.FileSystemUtils` and `java.security.MessageDigest("SHA-256")`).

## Behavioural contract

### Successful import sequence

1. Open staging dir `tools/twcoord/offline-address/.staging-<UUID>/` (mkdir -p).
2. Stream the `InputStream` to `.staging-<UUID>/places.sqlite`, piping through `ShaCalculator`.
   `ProgressListener.onProgress(COPYING, n, total)` fired at most every 100 ms.
3. Close stream; `fsync`.
4. Open the staged DB **read-only**; verify per FR-004:
   - `metadata` table exists; required keys present; `schema_version == pinnedSchemaVersion`.
   - `places` table exists with the column set documented in
     [data-model.md §1.1](../data-model.md#11-places-table).
   - Fire `ProgressListener.onProgress(VERIFYING_METADATA, 0, 1)`.
   Close.
5. If `places_rtree` is absent: open the staged DB **read-write**; execute the R*Tree creation
   + populate script from [data-model.md §1.5](../data-model.md#15-rtree-plugin-built-at-import--see-researchmd-r3).
   Periodic `onProgress(BUILDING_RTREE, n, total)` based on row count progress. Close.
6. Write `.staging-<UUID>/imported.manifest.txt` with `imported_at`, `file_sha256`,
   `rtree_built`, `plugin_schema_version`.
7. Rename any existing `active/` to `active-old-<timestamp>/`.
8. Atomic-move `.staging-<UUID>/` → `active/` via
   `Files.move(staging, active, ATOMIC_MOVE, REPLACE_EXISTING)`.
   Fire `onProgress(ACTIVATING, 1, 1)`.
9. Best-effort delete `active-old-<timestamp>/`. Log on failure but return `Success`.
10. Construct and return `Success(new AddressDataset(active, places.sqlite, gen, imp))`.

### Failure modes

Any failure at any step:

- Wipes the staging dir (`Files.walkFileTree(..., DELETE)` ignored on errors).
- Leaves the previously-active dataset (if any) untouched.
- Returns `Failure(reason, details)` where `reason` is the most-specific match from the enum
  and `details` is a one-line message safe to surface as a toast / row text.

The importer MUST NOT throw out of its public API — all exceptions are caught and mapped to
`Failure(IO_ERROR, ex.getMessage())`. (Per Constitution VI; the importer is the boundary
between trusted plugin code and untrusted external file content.)

### Cancellation

If the SAF stream is closed early (operator cancels at the system file picker before
`importFrom` is called) the worker never starts. If `importFrom` is in progress and the caller
interrupts its thread, the importer MUST return `Failure(CANCELLED, ...)` and clean up
staging. Cancellation does not retry.

## Test plan (`AddressBundleImporterTest`, JVM, JUnit 4)

| # | Test name | What it asserts |
|---|---|---|
| 1 | `import_writesPlacesSqliteIntoStaging` | Fed a minimal valid stream + the in-memory FS mock, the staged path contains the file. |
| 2 | `import_computesSha256DuringCopy` | The `file_sha256` in `imported.manifest.txt` matches a known fixture's SHA. |
| 3 | `import_rejectsNonOpenableDb` | A stream of random bytes returns `Failure(NOT_OPENABLE, ...)` and leaves no active dataset. |
| 4 | `import_rejectsMissingSchemaVersion` | A DB whose `metadata` lacks `schema_version` returns `Failure(MISSING_REQUIRED_METADATA_KEY, ...)`. |
| 5 | `import_rejectsWrongSchemaVersion` | `metadata.schema_version = 2` against pinned `1` returns `Failure(UNSUPPORTED_SCHEMA_VERSION, "expected 1, got 2")`. |
| 6 | `import_rejectsMissingPlacesColumns` | A DB whose `places` is missing `display_name` returns `Failure(UNEXPECTED_PLACES_COLUMNS, ...)`. |
| 7 | `import_buildsRtreeIfAbsent` | A valid DB without `places_rtree` ends with `places_rtree` populated; `imported.manifest.rtreeBuilt == true`. |
| 8 | `import_skipsRtreeIfPresent` | A valid DB already containing `places_rtree` skips the build; `imported.manifest.rtreeBuilt == false`. |
| 9 | `import_atomicActivationLeavesPreviousDatasetIntactOnFailure` | RTree build set to fail mid-way; the previously-active dataset is untouched and an `Failure(RTREE_BUILD_FAILED, ...)` is returned. |
| 10 | `removeActive_isIdempotent` | Two consecutive removes both leave `activeOrNull() == null`; neither throws. |

All tests use the `FileSystem` mock (an in-memory `java.nio.file.spi.FileSystemProvider`) and a
`ShaCalculator` mock (a `MessageDigest`-by-content map).
