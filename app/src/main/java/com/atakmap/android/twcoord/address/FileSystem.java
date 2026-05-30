package com.atakmap.android.twcoord.address;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * JVM-mockable seam over the subset of filesystem operations {@link AddressBundleImporter} uses.
 * Production implementation ({@code AtakFileSystem}) wraps {@code
 * com.atakmap.coremap.filesystem.FileSystemUtils.getItem("tools/twcoord/offline-address/...")} plus
 * {@link java.nio.file.Files}. JVM unit tests inject an in-memory implementation so the importer
 * can be tested without Android or ATAK.
 *
 * <p>The {@code .staging-&lt;UUID&gt;/} convention (per {@code research.md §R8}) is owned by the
 * implementation: {@link #createStagingDir()} returns a freshly-created per-import directory; on
 * construction the production implementation also sweeps orphan {@code .staging-*} directories left
 * over from interrupted prior imports.
 */
public interface FileSystem {

  /**
   * Return the canonical "active dataset" directory path. The directory may or may not exist; the
   * caller checks {@link #exists(Path)}. {@link AddressBundleImporter#atomicMove(Path, Path)}
   * replaces this directory atomically during activation.
   */
  Path getActiveDir();

  /**
   * Create a fresh, empty staging directory with a unique suffix ({@code .staging-&lt;UUID&gt;/})
   * under the same parent as {@link #getActiveDir()}, and return its path.
   */
  Path createStagingDir() throws IOException;

  /**
   * Open an {@link OutputStream} that writes to {@code path}. The implementation is responsible for
   * {@code fsync} on close (production) or any equivalent durability guarantee (tests).
   */
  OutputStream openWrite(Path path) throws IOException;

  /**
   * Atomic-replace {@code dst} with {@code src} via {@link java.nio.file.Files#move} with {@code
   * ATOMIC_MOVE} and {@code REPLACE_EXISTING}. If {@code dst} previously existed, the caller is
   * responsible for having renamed it out of the way first (per {@code research.md §R8} the
   * pre-existing active dir is renamed to {@code active-old-&lt;timestamp&gt;/} before this call).
   */
  void atomicMove(Path src, Path dst) throws IOException;

  /**
   * Best-effort recursive delete. Failures are logged (production) or ignored (tests); the method
   * never throws. Used to wipe staging on failure and to clean up the old-active dir after
   * successful activation.
   */
  void deleteRecursively(Path path);

  /** True if {@code path} exists (file or directory). */
  boolean exists(Path path);

  // ----------------------------------------------------------------------
  // Feature 005: per-county helpers. Default impls derive from getActiveDir() so existing
  // implementations (e.g. TempFileSystem in AddressBundleImporterTest) don't need to be
  // touched. AtakFileSystem may override createCountyStagingDir to embed the county in the
  // directory name (`.staging-<county>-<uuid>/`) for readable log/debug output.
  // ----------------------------------------------------------------------

  /** {@code active/<county>/} — the per-county sub-directory under the active root. */
  default Path activeCountyDir(String county) {
    Objects.requireNonNull(county, "county");
    return getActiveDir().resolve(county);
  }

  /**
   * Feature 006: {@code active/_boundary/} — the single boundary-layer mount (sibling of the
   * per-county dirs; the leading underscore guarantees it cannot collide with a county name and is
   * skipped by {@code ActiveDatasetRegistry.initFromDisk}).
   */
  default Path boundaryDir() {
    return getActiveDir().resolve("_boundary");
  }

  /** {@code active/_boundary/townships.sqlite} — the mounted boundary database file. */
  default Path boundaryDbFile() {
    return boundaryDir().resolve("townships.sqlite");
  }

  /**
   * Feature 007 US3: total on-disk bytes of a single regular file, or {@code 0} when the path is
   * null/absent/a directory. Best-effort — never throws.
   */
  default long sizeOf(Path path) {
    if (path == null) return 0L;
    try {
      if (!Files.exists(path) || Files.isDirectory(path)) return 0L;
      return Files.size(path);
    } catch (RuntimeException | IOException e) {
      // Best-effort, never throws (Constitution VI). Catch RuntimeException too (e.g.
      // SecurityException on a restrictive filesystem) so nothing escapes this seam.
      return 0L;
    }
  }

  /**
   * Feature 007 US3: total on-disk bytes of every regular file under {@code dir} (recursively),
   * including SQLite sidecars (-wal/-shm/-journal/R*Tree). Returns {@code 0} when {@code dir} is
   * null/absent. Best-effort — never throws.
   */
  default long sizeOfDirectory(Path dir) {
    if (dir == null) return 0L;
    try {
      if (!Files.exists(dir)) return 0L;
      try (Stream<Path> walk = Files.walk(dir)) {
        return walk.filter(Files::isRegularFile)
            .mapToLong(
                p -> {
                  try {
                    return Files.size(p);
                  } catch (RuntimeException | IOException e) {
                    return 0L;
                  }
                })
            .sum();
      }
    } catch (RuntimeException | IOException e) {
      // Best-effort, never throws (Constitution VI). RuntimeException (e.g. SecurityException from
      // Files.walk / Files.exists) is treated as 0 rather than escaping this seam.
      return 0L;
    }
  }

  /**
   * Create a fresh, empty per-county staging directory. The implementation embeds the county name
   * (sanitised) and a random UUID in the directory name so a partially-completed import is
   * identifiable on disk during debugging and gets swept by {@code sweepOrphanStagingDirs} on next
   * plugin start.
   */
  default Path createCountyStagingDir(String county) throws IOException {
    Objects.requireNonNull(county, "county");
    Path parent = getActiveDir().getParent();
    if (parent == null) parent = getActiveDir();
    String safe = county.replaceAll("[^\\p{L}\\p{N}_-]", "_");
    Path p = parent.resolve(".staging-" + safe + "-" + UUID.randomUUID());
    Files.createDirectories(p);
    return p;
  }
}
