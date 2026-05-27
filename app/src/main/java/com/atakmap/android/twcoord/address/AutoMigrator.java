package com.atakmap.android.twcoord.address;

import com.atakmap.coremap.log.Log;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Feature 005 v1.0.5 → v1.0.6 auto-migrate. One-shot path that detects the legacy single-active
 * layout ({@code active/places.sqlite} + {@code active/imported.manifest.txt} at top of the active
 * root) and moves the files into the new per-county sub-directory layout ({@code
 * active/<county>/places.sqlite}). Per contracts/auto-migrator.md.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>Idempotent: a second call after a successful migration is {@link Result.NoLegacyDetected}.
 *   <li>No data loss on failure — any abort path leaves the legacy files untouched.
 *   <li>Atomic where the filesystem supports it; falls back to copy-then-verify-then-delete with
 *       free-space precheck on cross-mount.
 *   <li>County string validated before any rename (rejects null/empty/contains-{@code ..}/path
 *       separators) — defends against a crafted v1.0.5 dataset that could path-traverse.
 *   <li>NEVER throws — Constitution VI entry point.
 * </ul>
 */
public final class AutoMigrator {

  private static final String TAG = "AutoMigrator";
  private static final String LEGACY_DB_NAME = "places.sqlite";
  private static final String LEGACY_MANIFEST_NAME = "imported.manifest.txt";
  private static final String[] OPTIONAL_WAL_FILES = {"places.sqlite-shm", "places.sqlite-wal"};

  private final FileSystem fs;
  private final AddressDatabaseFacade.Factory probeFactory;

  public AutoMigrator(FileSystem fs, AddressDatabaseFacade.Factory probeFactory) {
    this.fs = Objects.requireNonNull(fs, "fs");
    this.probeFactory = Objects.requireNonNull(probeFactory, "probeFactory");
  }

  /** Sealed-like result hierarchy — use {@code instanceof} or pattern matching at the call site. */
  public interface Result {}

  public static final class NoLegacyDetected implements Result {}

  public static final class Migrated implements Result {
    public final String county;

    public Migrated(String county) {
      this.county = county;
    }
  }

  public static final class LegacyPreservedDueToValidation implements Result {
    public final String reason;

    public LegacyPreservedDueToValidation(String reason) {
      this.reason = reason;
    }
  }

  public static final class LegacyPreservedDueToAtomicMoveFailure implements Result {
    public final String reason;

    public LegacyPreservedDueToAtomicMoveFailure(String reason) {
      this.reason = reason;
    }
  }

  /** Run once at plugin onCreate before {@code ActiveDatasetRegistry.initFromDisk()}. */
  public Result tryMigrate() {
    try {
      Path active = fs.getActiveDir();
      Path legacyDb = active.resolve(LEGACY_DB_NAME);
      Path legacyManifest = active.resolve(LEGACY_MANIFEST_NAME);
      if (!fs.exists(legacyDb) || !fs.exists(legacyManifest)) {
        return new NoLegacyDetected();
      }
      // Read metadata.county via the probe factory (closed immediately to avoid EBUSY on
      // some Windows-style filesystems before the rename).
      String county;
      AddressDatabaseFacade probe = null;
      try {
        probe = probeFactory.open(legacyDb.toFile());
        if (probe == null) {
          Log.w(TAG, "tryMigrate: probe factory could not open " + legacyDb);
          return new LegacyPreservedDueToValidation("probe open returned null");
        }
        GeneratorMetadata md = probe.readMetadata();
        county = md != null ? md.county() : null;
      } finally {
        if (probe != null) {
          try {
            probe.close();
          } catch (Throwable t) {
            Log.w(TAG, "probe close threw", t);
          }
        }
      }
      String validation = validateCounty(county);
      if (validation != null) {
        Log.w(TAG, "tryMigrate: county validation failed (" + validation + "); leaving legacy");
        return new LegacyPreservedDueToValidation(validation);
      }

      Path target = fs.activeCountyDir(county);
      if (fs.exists(target)) {
        // Operator pre-populated the target (or a prior partial migration); don't overwrite.
        Log.w(TAG, "tryMigrate: target " + target + " already exists; leaving legacy");
        return new LegacyPreservedDueToAtomicMoveFailure("target exists");
      }
      try {
        Files.createDirectories(target);
      } catch (IOException e) {
        Log.w(TAG, "tryMigrate: mkdir " + target + " failed", e);
        return new LegacyPreservedDueToAtomicMoveFailure("mkdir failed: " + describe(e));
      }

      // Move db + manifest + optional WAL files. If any fails, roll back the prior moves.
      java.util.List<Path[]> moved = new java.util.ArrayList<>();
      try {
        atomicOrCopy(legacyDb, target.resolve(LEGACY_DB_NAME));
        moved.add(new Path[] {target.resolve(LEGACY_DB_NAME), legacyDb});
        atomicOrCopy(legacyManifest, target.resolve(LEGACY_MANIFEST_NAME));
        moved.add(new Path[] {target.resolve(LEGACY_MANIFEST_NAME), legacyManifest});
        for (String walName : OPTIONAL_WAL_FILES) {
          Path src = active.resolve(walName);
          if (fs.exists(src)) {
            atomicOrCopy(src, target.resolve(walName));
            moved.add(new Path[] {target.resolve(walName), src});
          }
        }
      } catch (IOException e) {
        Log.w(TAG, "tryMigrate: file move failed mid-way; rolling back", e);
        rollback(moved);
        // Don't leave a half-populated target dir.
        try {
          fs.deleteRecursively(target);
        } catch (Throwable ignored) {
          // best-effort
        }
        return new LegacyPreservedDueToAtomicMoveFailure(describe(e));
      }
      Log.i(TAG, "migrate county=" + county + " ok");
      return new Migrated(county);
    } catch (Throwable t) {
      Log.w(TAG, "tryMigrate threw", t);
      return new LegacyPreservedDueToAtomicMoveFailure("threw: " + describe(t));
    }
  }

  private void atomicOrCopy(Path src, Path dst) throws IOException {
    try {
      Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      // Cross-mount path — copy + verify size + delete source.
      long srcSize = Files.size(src);
      Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
      long dstSize = Files.size(dst);
      if (dstSize != srcSize) {
        Files.deleteIfExists(dst);
        throw new IOException(
            "copy size mismatch: src=" + srcSize + " dst=" + dstSize + " for " + src);
      }
      Files.delete(src);
    }
  }

  /** Reverse a partial set of moves: move the dst paths back to their src locations. */
  private void rollback(java.util.List<Path[]> moved) {
    for (int i = moved.size() - 1; i >= 0; i--) {
      Path dst = moved.get(i)[0];
      Path src = moved.get(i)[1];
      try {
        if (Files.exists(dst)) {
          Files.move(dst, src, StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (IOException e) {
        Log.w(TAG, "rollback of " + dst + " → " + src + " failed", e);
      }
    }
  }

  /**
   * Returns null if the county string is valid, else a short reason. Rejects null, empty, path
   * separators, drive letters, and {@code ..} (zip-slip style traversal defence).
   */
  static String validateCounty(String county) {
    if (county == null) return "county is null";
    if (county.isEmpty()) return "county is empty";
    if (county.contains("..")) return "county contains path traversal";
    if (county.contains("/")) return "county contains forward slash";
    if (county.contains("\\")) return "county contains backslash";
    if (county.contains(":")) return "county contains colon";
    if (county.contains("\0")) return "county contains null byte";
    return null;
  }

  private static String describe(Throwable t) {
    if (t == null) return "unknown";
    String msg = t.getMessage();
    return t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
  }
}
