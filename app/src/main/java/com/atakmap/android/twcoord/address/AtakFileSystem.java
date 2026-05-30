package com.atakmap.android.twcoord.address;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Production {@link FileSystem} implementation, backed by ATAK's {@link
 * FileSystemUtils#getItem(String)} pathway. The plugin's offline-address root lives at {@code
 * tools/twcoord/offline-address/} under the ATAK file system root (resolved by {@code
 * FileSystemUtils} — never hard-coded as {@code /sdcard/...} per Constitution VI's "no hard-coded
 * /sdcard literals" rule).
 *
 * <p>On construction, sweeps any {@code .staging-*} sibling directories that may have been left
 * over from a previously-interrupted import (per {@code research.md §R8}). The sweep is best-effort
 * — failures are logged at {@code Log.w} but do not block construction.
 */
public final class AtakFileSystem implements FileSystem {

  private static final String TAG = "AtakFileSystem";
  private static final String ROOT_REL = "tools/twcoord/offline-address";
  private static final String ACTIVE_DIR_NAME = "active";
  private static final String STAGING_PREFIX = ".staging-";

  /** Feature 006: boundary layer mount, sibling of the per-county dirs under active/. */
  static final String BOUNDARY_DIR_NAME = "_boundary";

  static final String BOUNDARY_DB_NAME = "townships.sqlite";

  private final Path root;

  public AtakFileSystem() {
    File rootFile = FileSystemUtils.getItem(ROOT_REL);
    this.root = rootFile.toPath();
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      Log.w(TAG, "cannot create root " + this.root, e);
    }
    sweepOrphanStagingDirs();
  }

  /** Visible-for-test ctor that lets a test inject a tmp root without touching FileSystemUtils. */
  AtakFileSystem(Path root) {
    this.root = root;
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      Log.w(TAG, "cannot create test root " + this.root, e);
    }
    sweepOrphanStagingDirs();
  }

  @Override
  public Path getActiveDir() {
    return root.resolve(ACTIVE_DIR_NAME);
  }

  @Override
  public Path boundaryDir() {
    return getActiveDir().resolve(BOUNDARY_DIR_NAME);
  }

  @Override
  public Path createStagingDir() throws IOException {
    Path p = root.resolve(STAGING_PREFIX + UUID.randomUUID());
    Files.createDirectories(p);
    return p;
  }

  @Override
  public OutputStream openWrite(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    return Files.newOutputStream(path);
  }

  @Override
  public void atomicMove(Path src, Path dst) throws IOException {
    Files.createDirectories(dst.getParent());
    try {
      Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (java.nio.file.AtomicMoveNotSupportedException e) {
      // Some Android filesystems / cross-mount situations don't support ATOMIC_MOVE; fall
      // back to plain move. Loss of atomicity here is best-effort; the staging-dir cleanup
      // pass at construction picks up any leftover state.
      Log.w(TAG, "ATOMIC_MOVE unsupported on " + src + " -> " + dst + "; falling back");
      Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  @Override
  public void deleteRecursively(Path path) {
    if (path == null || !Files.exists(path)) return;
    try (Stream<Path> walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                  Log.w(TAG, "delete " + p + " failed", e);
                }
              });
    } catch (IOException e) {
      Log.w(TAG, "walk " + path + " for delete failed", e);
    }
  }

  @Override
  public boolean exists(Path path) {
    return path != null && Files.exists(path);
  }

  private void sweepOrphanStagingDirs() {
    if (!Files.isDirectory(root)) return;
    try (Stream<Path> stream = Files.list(root)) {
      stream
          .filter(p -> p.getFileName().toString().startsWith(STAGING_PREFIX))
          .forEach(
              p -> {
                Log.w(TAG, "sweeping orphan staging dir " + p);
                deleteRecursively(p);
              });
    } catch (IOException e) {
      Log.w(TAG, "sweep orphan staging failed", e);
    }
  }
}
