# Contract: AutoMigrator

**Module**: `app/src/main/java/com/atakmap/android/twcoord/address/AutoMigrator.java` (NEW)

One-shot, atomic v1.0.5 → v1.0.6 layout migration. Called once at plugin `onCreate` before `ActiveDatasetRegistry.initFromDisk()`. The algorithm is in research R6 + data-model §3.3; this file pins the public contract.

## Interface

```java
public final class AutoMigrator {

  public AutoMigrator(AtakFileSystem fs, AddressDatabaseFacade.Factory probeFactory);

  /**
   * Returns the migration result. Side effect: if a legacy layout is detected and validation
   * succeeds, atomically moves the legacy files into {@code active/<county>/}. On any failure
   * (validation, atomic-move not supported across mounts, partial-copy verification fails),
   * leaves the legacy files untouched and returns the corresponding result variant.
   */
  public Result tryMigrate();

  public sealed interface Result {
    record NoLegacyDetected() implements Result {}
    record Migrated(String county) implements Result {}
    record LegacyPreservedDueToValidation(String reason) implements Result {}
    record LegacyPreservedDueToAtomicMoveFailure(String reason) implements Result {}
  }
}
```

## Invariants

1. **Idempotent**: a second call after a successful migration is a NoLegacyDetected (the legacy paths no longer exist).
2. **No data loss on failure**: any abort path leaves the legacy `active/places.sqlite` + `active/imported.manifest.txt` untouched. Verified by tests via SHA comparison before/after a forced-failure scenario.
3. **Atomic move where filesystem permits**: uses `Files.move(..., ATOMIC_MOVE)`. On `AtomicMoveNotSupportedException` (cross-mount) falls back to copy+verify+delete with prerequisite free-space check.
4. **Validation gates county string**: rejects null/empty/contains-`..`/contains-`/`/contains-`\\`. Constitution VI defensive validation.
5. **Closes the probe facade** before attempting the move (avoids EBUSY on Windows-style filesystems; on Linux/Android it's a precaution against SQLite-WAL `.shm` write contention).
6. **Logs once per outcome**: `Log.i("AutoMigrator", "migrate county=<county> ok")` on success; `Log.w` on each preserve path.
7. **No outbound network**: assert-only invariant; the class has no `java.net` imports.

## Test plan (`AutoMigratorTest`, JVM/Robolectric)

| # | Scenario | Expected |
|---|---|---|
| 1 | No `active/places.sqlite` present | `NoLegacyDetected`; nothing on disk changes |
| 2 | Legacy `active/places.sqlite` (台中市) + `imported.manifest.txt` present | `Migrated("台中市")`; legacy files moved to `active/台中市/`; old paths gone |
| 3 | Legacy present + `places.sqlite-shm` + `places.sqlite-wal` (WAL mode) | all 4 files moved; staging is empty; legacy paths gone |
| 4 | Legacy present but `metadata.county` is empty | `LegacyPreservedDueToValidation("county empty")`; legacy untouched |
| 5 | Legacy present but `metadata.county` contains `..` | `LegacyPreservedDueToValidation("county contains path traversal")`; legacy untouched |
| 6 | Legacy present, simulated `AtomicMoveNotSupportedException` | falls through to copy+delete; new layout populated; legacy deleted |
| 7 | Legacy present, simulated disk-full mid-copy | `LegacyPreservedDueToAtomicMoveFailure("disk full ...")`; partial copy rolled back; legacy intact |
| 8 | Legacy present, target `active/<county>/` already exists (operator manually pre-populated) | `LegacyPreservedDueToAtomicMoveFailure("target exists")`; legacy intact (don't risk overwriting operator's pre-stage) |
| 9 | Legacy SHA before/after preservation paths | for tests #4–#8: SHA-256 of `active/places.sqlite` is bit-identical before and after `tryMigrate()` |
| 10 | Re-run after success | `NoLegacyDetected` |
