# Contract: FallbackSqliteFactory

**Module**: `app/src/main/java/com/atakmap/android/twcoord/address/FallbackSqliteFactory.java` (NEW)

The B-side of the FR-017 "A primary + B fallback" design. Opt-in opens a `places.sqlite` via a bundled portable SQLite runtime (research R5 chose `org.requery:sqlite-android`). Used only when the ATAK-native primary path (`AtakDatabasesAddressDatabase.Factory`) fails to open the file or fails the rtree-probe.

## Interface

```java
public final class FallbackSqliteFactory implements AddressDatabaseFacade.Factory {

  /**
   * Lazy-initialises the underlying Requery {@code sqlite-android} native library on first call.
   * Subsequent calls reuse the loaded library.
   */
  @Override
  public AddressDatabaseFacade open(File dbFile);

  /**
   * Diagnostic: returns whether the fallback library has been loaded into the process yet.
   * Used by tests + telemetry to assert the "opt-in, not always-on" property (Assumption §11).
   */
  public boolean isFallbackInitialised();
}
```

## Invariants

1. **Lazy-init**: the Requery native `.so` is NOT loaded by the plugin at process start. It is loaded on the first `open(...)` call after a primary-path failure (i.e. when `ActiveDatasetRegistry` decides to escalate to fallback).
2. **One-time init per process**: subsequent opens reuse the loaded native library; no double-load attempts.
3. **`UnsatisfiedLinkError` wrapped**: if the native library is not present (e.g. the ABI is not in the APK), the constructor catches `UnsatisfiedLinkError`, logs at `Log.w`, and returns null facades from `open(...)`. The plugin downgrades to "this county can't be opened" rather than crashing.
4. **Same facade contract as primary**: returned `AddressDatabaseFacade` exposes the same `readMetadata` / `nearestWithin` / `close` semantics; SQL strings are bit-identical (the same R*Tree-join query that 004 D2 ships).
5. **APK size is paid only if the library is bundled**: build.gradle adds the dependency in a way that only the targeted ABIs are packaged (per-ABI APK split or `abiFilters` in app/build.gradle). Total delta ≤ 2 MiB per ABI per Assumption §11.

## Test plan (`FallbackSqliteFactoryTest`, JVM only)

JVM tests cannot exercise the native library load (Requery's `.so` is Android-only). The test plan focuses on the orchestration around it.

| # | Scenario | Expected |
|---|---|---|
| 1 | Construct without loading library | `isFallbackInitialised() == false`; native library has not been `dlopen`'d (verified by reflection — Requery's static init flag) |
| 2 | `open(non-existent-file)` | returns null; library still not initialised |
| 3 | `open(valid-file)` first time | library loads; `isFallbackInitialised() == true`; facade is non-null; subsequent `open` calls do not re-load |
| 4 | `UnsatisfiedLinkError` simulated | constructor swallows + logs; subsequent `open` returns null |
| 5 | Concurrent first-time `open` calls (race the lazy init) | only one load; both calls get valid facades; no double-load exception |
| 6 | Facade SQL string parity | the SQL emitted by the fallback facade for `nearestWithin(lat, lon, r)` equals the SQL emitted by `AtakDatabasesAddressDatabase` (golden-string assertion). Same R*Tree-join query, same haversine refine. |

Device-only checks (covered by `BatchImportRssTest` + Espresso harness from research R9):

- Loading the Requery library does not push plugin RSS past SC-005's 200 MiB budget.
- A file that the primary path fails on (e.g. injected via test fixture) does open under the fallback path and returns address records correctly via the rtree-join query.

## Anchors

- Requery SQLite-Android javadoc: `https://github.com/requery/sqlite-android` (data-contract independent; Requery exposes a strict superset of platform SQLite API).
- The detection algorithm that decides "primary failed, escalate to fallback" lives in `ActiveDatasetRegistry.openCounty(county, file)` per research R5; this factory is the dumb collaborator.
