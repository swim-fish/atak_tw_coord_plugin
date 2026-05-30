package com.atakmap.android.twcoord.address;

import com.atakmap.coremap.log.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipException;

/**
 * Feature 005 — drives N picked files (each a {@code .zip} or bare {@code .sqlite}) through {@link
 * AddressBundleImporter}'s pipeline, emitting per-entry status into a {@link BatchImportReport}.
 * Owns the {@code BatchSession} state machine per data-model.md §3.2 +
 * contracts/batch-import-coordinator.md.
 *
 * <p>Reentrancy (FR-019, Clarifications Q3): {@link #enqueue} stays enabled while a batch is
 * draining. New files append to the pending queue; the single-thread executor processes them in
 * order. The chained-picker UX on the page consumes {@link Listener} callbacks to render the
 * "queued — waiting" status until the worker reaches each entry.
 */
public final class BatchImportCoordinator {

  private static final String TAG = "BatchImportCoordinator";

  /** Listener for per-entry status updates + batch completion. Wrapped per Constitution VI. */
  public interface Listener {
    void onEntryStarted(BatchImportReport.Entry entry);

    void onEntryFinished(BatchImportReport.Entry entry);

    void onBatchComplete(BatchImportReport report);
  }

  private final AddressBundleImporter importer;
  private final ZipExtractor zipExtractor;
  private final ZipEntryClassifier classifier;
  private final ActiveDatasetRegistry registry;
  private final AddressDatabaseFacade.Factory primaryFactory;
  private final ExecutorService executor;
  private final FileSystem fs;

  /** A queued pick paired with the county the operator expects it to replace, if any. */
  private static final class PendingItem {
    final File file;
    final String expectedCounty; // nullable: null for a plain Import (no county constraint)

    PendingItem(File file, String expectedCounty) {
      this.file = file;
      this.expectedCounty = expectedCounty;
    }
  }

  private final Deque<PendingItem> pending = new ArrayDeque<>();
  private final List<BatchImportReport.Entry> reports = new ArrayList<>();
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private final AtomicBoolean draining = new AtomicBoolean(false);
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicBoolean finishRequested = new AtomicBoolean(false);

  public BatchImportCoordinator(
      AddressBundleImporter importer,
      ZipExtractor zipExtractor,
      ZipEntryClassifier classifier,
      ActiveDatasetRegistry registry,
      AddressDatabaseFacade.Factory primaryFactory,
      ExecutorService executor,
      FileSystem fs) {
    this.importer = Objects.requireNonNull(importer, "importer");
    this.zipExtractor = Objects.requireNonNull(zipExtractor, "zipExtractor");
    this.classifier = Objects.requireNonNull(classifier, "classifier");
    this.registry = Objects.requireNonNull(registry, "registry");
    this.primaryFactory = Objects.requireNonNull(primaryFactory, "primaryFactory");
    this.executor = Objects.requireNonNull(executor, "executor");
    this.fs = Objects.requireNonNull(fs, "fs");
  }

  // ----------------------------------------------------------------------
  // Public API (UI-thread only)
  // ----------------------------------------------------------------------

  /** Add a picked file (.zip or .sqlite) to the active session; start worker if idle. */
  public synchronized int enqueue(File pickedFile) {
    return enqueue(pickedFile, null);
  }

  /**
   * Add a picked file constrained to {@code expectedCounty} (the per-county Replace flow). Any
   * dataset whose {@code metadata.county} does not equal {@code expectedCounty} is rejected with
   * {@link BatchImportReport.Status#SKIPPED_COUNTY_MISMATCH} instead of being activated. Pass
   * {@code null} for an unconstrained Import.
   */
  public synchronized int enqueue(File pickedFile, String expectedCounty) {
    Objects.requireNonNull(pickedFile, "pickedFile");
    pending.addLast(new PendingItem(pickedFile, expectedCounty));
    int idx = pending.size() + reports.size();
    if (draining.compareAndSet(false, true)) {
      finishRequested.set(false);
      cancelled.set(false);
      reports.clear();
      executor.execute(this::drain);
    }
    return idx;
  }

  /** Signal end-of-batch from the operator's "完成 / Done" tap. */
  public synchronized void finishBatch() {
    finishRequested.set(true);
  }

  /** Cancel: in-flight entry completes naturally; pending entries are dropped. */
  public synchronized void cancelBatch() {
    cancelled.set(true);
    finishRequested.set(true);
  }

  public void addListener(Listener listener) {
    if (listener != null) listeners.add(listener);
  }

  public void removeListener(Listener listener) {
    if (listener != null) listeners.remove(listener);
  }

  /** Diagnostic: count of items waiting to be processed (visible on the page as a badge). */
  public synchronized int queueDepth() {
    return pending.size();
  }

  // ----------------------------------------------------------------------
  // Internal — worker loop
  // ----------------------------------------------------------------------

  private void drain() {
    try {
      while (!cancelled.get()) {
        PendingItem next;
        synchronized (this) {
          next = pending.pollFirst();
          if (next == null) {
            if (finishRequested.get()) break;
            // No work + no finish signal → spin-wait. In practice the page never enqueues
            // without finishing; defensive 50 ms sleep avoids busy-loop.
            try {
              wait(50);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              cancelled.set(true);
              break;
            }
            continue;
          }
        }
        processOne(next);
      }
      BatchImportReport report = new BatchImportReport(reports);
      Log.i(TAG, "batch done: " + report);
      fireOnComplete(report);
    } catch (Throwable t) {
      Log.w(TAG, "drain threw", t);
    } finally {
      draining.set(false);
    }
  }

  private void processOne(PendingItem item) {
    File file = item.file;
    String name = file.getName();
    long startMs = System.currentTimeMillis();
    if (name.toLowerCase().endsWith(".zip")) {
      processZip(file, startMs, item.expectedCounty);
    } else {
      processBareSqlite(file, startMs, item.expectedCounty);
    }
  }

  private void processZip(File zipFile, long startMs, String expectedCounty) {
    BatchImportReport.Entry started =
        new BatchImportReport.Entry(
            zipFile.getName(), null, BatchImportReport.Status.ACTIVATED, null, 0);
    fireOnStart(started);
    try (InputStream in = new FileInputStream(zipFile)) {
      ZipExtractor.ExtractResult result = zipExtractor.extract(in, null);
      if (!result.hasAnyConsumable()) {
        // Neither a places-<county>.sqlite NOR the townships.sqlite boundary — nothing to mount.
        addAndFire(
            zipFile.getName(),
            null,
            BatchImportReport.Status.FAILED,
            "ZIP_NO_VALID_DATASETS",
            System.currentTimeMillis() - startMs);
        return;
      }
      // Account for skipped + failed entries inside the ZIP first (one Entry per skipped item).
      for (int i = 0; i < result.supplementaryCount(); i++) {
        addAndFire(
            zipFile.getName(), null, BatchImportReport.Status.SKIPPED_SUPPLEMENTARY, null, 0);
      }
      for (ZipExtractor.FailedEntry fe : result.failures()) {
        BatchImportReport.Status s =
            fe.reason() != null && fe.reason().contains("SKIPPED_DUPLICATE")
                ? BatchImportReport.Status.SKIPPED_DUPLICATE
                : BatchImportReport.Status.FAILED;
        addAndFire(fe.entryName(), fe.county(), s, fe.reason(), 0);
      }
      // Activate each extracted county.
      for (ZipExtractor.ExtractedCounty ec : result.counties()) {
        long countyStart = System.currentTimeMillis();
        activateExtractedCounty(ec, zipFile.getName(), countyStart, expectedCounty);
      }
      // Feature 006: persist the boundary layer (townships.sqlite) into active/_boundary/ so the
      // township facade can mount it. Without this the extractor staged it but it was discarded,
      // leaving forward search permanently on "import base data" (the empty-_boundary bug).
      if (result.boundary() != null) {
        activateBoundary(result.boundary(), zipFile.getName(), System.currentTimeMillis());
      }
    } catch (ZipException e) {
      addAndFire(
          zipFile.getName(),
          null,
          BatchImportReport.Status.FAILED,
          "NOT_A_ZIP: " + describe(e),
          System.currentTimeMillis() - startMs);
    } catch (Throwable t) {
      Log.w(TAG, "processZip threw", t);
      addAndFire(
          zipFile.getName(),
          null,
          BatchImportReport.Status.FAILED,
          "IO_ERROR: " + describe(t),
          System.currentTimeMillis() - startMs);
    }
  }

  /**
   * The extractor has already streamed the county's bytes into a staging dir with a final SHA; we
   * hand the staging-dir file's stream back to the importer to validate metadata + activate.
   */
  private void activateExtractedCounty(
      ZipExtractor.ExtractedCounty ec, String parentZipName, long startMs, String expectedCounty) {
    // Peek the actual county from the extracted file's metadata.county BEFORE handing the
    // bytes to the importer. The classifier extracts the county from the ZIP entry name
    // ("places-<county>.sqlite") which is the romanised generator-side filename (e.g.
    // "taichung"), but the human-readable county the UI shows and the directory layout
    // expects is the in-DB normalised string (e.g. "台中市"). Resolving via metadata.county
    // here means the registry, the active dir layout, and the UI all agree.
    String countyFromFilename = ec.county();
    String realCounty;
    try {
      realCounty = peekCounty(ec.placesFileAsFile());
    } catch (Throwable t) {
      Log.w(TAG, "peekCounty threw for extracted entry " + ec.placesFile(), t);
      addAndFire(
          parentZipName + "/places-" + countyFromFilename + ".sqlite",
          countyFromFilename,
          BatchImportReport.Status.FAILED,
          "metadata.county unreadable: " + describe(t),
          System.currentTimeMillis() - startMs);
      return;
    }
    if (realCounty == null || realCounty.isEmpty()) {
      addAndFire(
          parentZipName + "/places-" + countyFromFilename + ".sqlite",
          countyFromFilename,
          BatchImportReport.Status.FAILED,
          "metadata.county missing or empty",
          System.currentTimeMillis() - startMs);
      return;
    }
    String county = realCounty;
    if (expectedCounty != null && !expectedCounty.equals(county)) {
      // Per-county Replace target's metadata.county didn't match the row the operator tapped.
      // Reject without activating, and clean up the extractor's staging dir (the activation
      // try/finally below — which normally does this — is skipped by this early return).
      try {
        fs.deleteRecursively(ec.stagingDir());
      } catch (Throwable t) {
        Log.w(TAG, "cleanup of mismatched staging " + ec.stagingDir() + " threw", t);
      }
      addAndFire(
          parentZipName + "/places-" + countyFromFilename + ".sqlite",
          county,
          BatchImportReport.Status.SKIPPED_COUNTY_MISMATCH,
          "expected " + expectedCounty + " but file is " + county,
          System.currentTimeMillis() - startMs);
      return;
    }
    BatchImportReport.Entry started =
        new BatchImportReport.Entry(
            parentZipName + "/places-" + countyFromFilename + ".sqlite",
            county,
            BatchImportReport.Status.ACTIVATED,
            null,
            0);
    fireOnStart(started);
    try (InputStream in = new FileInputStream(ec.placesFileAsFile())) {
      // Note: importFromInto re-stages + re-SHAs the bytes. The extractor's intermediate
      // staging dir will be left behind under the .staging-* sweep policy.
      AddressBundleImporter.ImportResult result = importer.importFromInto(in, county, null);
      if (!result.isSuccess()) {
        AddressBundleImporter.ImportResult.Failure fail =
            (AddressBundleImporter.ImportResult.Failure) result;
        addAndFire(
            parentZipName + "/places-" + county + ".sqlite",
            county,
            BatchImportReport.Status.FAILED,
            fail.reason() + ": " + (fail.details() == null ? "" : fail.details()),
            System.currentTimeMillis() - startMs);
        return;
      }
      AddressBundleImporter.ImportResult.Success ok =
          (AddressBundleImporter.ImportResult.Success) result;
      // Open facade + register.
      AddressDatabaseFacade facade = primaryFactory.open(ok.dataset().dbFile());
      if (facade == null) {
        addAndFire(
            parentZipName + "/places-" + county + ".sqlite",
            county,
            BatchImportReport.Status.FAILED,
            "FACADE_OPEN_FAILED",
            System.currentTimeMillis() - startMs);
        return;
      }
      boolean isReplace = registry.snapshot().containsKey(county);
      CountyActiveDataset entry = new CountyActiveDataset(county, ok.dataset(), facade);
      if (isReplace) {
        registry.replace(entry);
        addAndFire(
            parentZipName + "/places-" + county + ".sqlite",
            county,
            BatchImportReport.Status.REPLACED,
            null,
            System.currentTimeMillis() - startMs);
      } else {
        registry.add(entry);
        addAndFire(
            parentZipName + "/places-" + county + ".sqlite",
            county,
            BatchImportReport.Status.ACTIVATED,
            null,
            System.currentTimeMillis() - startMs);
      }
    } catch (Throwable t) {
      Log.w(TAG, "activateExtractedCounty threw", t);
      addAndFire(
          parentZipName + "/places-" + county + ".sqlite",
          county,
          BatchImportReport.Status.FAILED,
          "IO_ERROR: " + describe(t),
          System.currentTimeMillis() - startMs);
    } finally {
      // Best-effort: clean up the extractor's intermediate staging dir.
      try {
        fs.deleteRecursively(ec.stagingDir());
      } catch (Throwable t) {
        Log.w(TAG, "cleanup of extractor staging " + ec.stagingDir() + " threw", t);
      }
    }
  }

  /**
   * Feature 006: atomically move the extractor-staged {@code townships.sqlite} into {@code
   * active/_boundary/townships.sqlite} so the township-boundary facade can mount it (locality
   * detection + county-scoped reverse, and the forward-search funnel gate). Reported as {@code
   * ACTIVATED} since it activates base data; a move failure is {@code FAILED}. Mirrors the
   * per-county staging-dir cleanup in {@link #activateExtractedCounty}.
   */
  private void activateBoundary(
      ZipExtractor.ExtractedCounty boundary, String parentZipName, long startMs) {
    String entryName = parentZipName + "/townships.sqlite";
    try {
      fs.atomicMove(boundary.placesFile(), fs.boundaryDbFile());
      addAndFire(
          entryName,
          null,
          BatchImportReport.Status.ACTIVATED,
          "boundary",
          System.currentTimeMillis() - startMs);
    } catch (Throwable t) {
      Log.w(TAG, "activateBoundary threw", t);
      addAndFire(
          entryName,
          null,
          BatchImportReport.Status.FAILED,
          "BOUNDARY_MOVE_FAILED: " + describe(t),
          System.currentTimeMillis() - startMs);
    } finally {
      try {
        fs.deleteRecursively(boundary.stagingDir());
      } catch (Throwable t) {
        Log.w(TAG, "cleanup of boundary staging " + boundary.stagingDir() + " threw", t);
      }
    }
  }

  /**
   * Bare {@code .sqlite} path. Peeks the file's metadata.county via a one-shot SQLite open (through
   * the registry's primary→fallback open, so a fallback-only-readable file isn't rejected here),
   * then hands the file to the importer.
   */
  private void processBareSqlite(File sqliteFile, long startMs, String expectedCounty) {
    BatchImportReport.Entry started =
        new BatchImportReport.Entry(
            sqliteFile.getName(), null, BatchImportReport.Status.ACTIVATED, null, 0);
    fireOnStart(started);
    String county;
    try {
      county = peekCounty(sqliteFile);
    } catch (Throwable t) {
      Log.w(TAG, "peekCounty threw for " + sqliteFile, t);
      addAndFire(
          sqliteFile.getName(),
          null,
          BatchImportReport.Status.FAILED,
          "metadata.county unreadable: " + describe(t),
          System.currentTimeMillis() - startMs);
      return;
    }
    if (county == null || county.isEmpty()) {
      addAndFire(
          sqliteFile.getName(),
          null,
          BatchImportReport.Status.FAILED,
          "metadata.county missing or empty",
          System.currentTimeMillis() - startMs);
      return;
    }
    if (expectedCounty != null && !expectedCounty.equals(county)) {
      // Per-county Replace target's metadata.county didn't match the row the operator tapped.
      addAndFire(
          sqliteFile.getName(),
          county,
          BatchImportReport.Status.SKIPPED_COUNTY_MISMATCH,
          "expected " + expectedCounty + " but file is " + county,
          System.currentTimeMillis() - startMs);
      return;
    }
    try (InputStream in = new FileInputStream(sqliteFile)) {
      AddressBundleImporter.ImportResult result = importer.importFromInto(in, county, null);
      if (!result.isSuccess()) {
        AddressBundleImporter.ImportResult.Failure fail =
            (AddressBundleImporter.ImportResult.Failure) result;
        addAndFire(
            sqliteFile.getName(),
            county,
            BatchImportReport.Status.FAILED,
            fail.reason() + ": " + (fail.details() == null ? "" : fail.details()),
            System.currentTimeMillis() - startMs);
        return;
      }
      AddressBundleImporter.ImportResult.Success ok =
          (AddressBundleImporter.ImportResult.Success) result;
      AddressDatabaseFacade facade = primaryFactory.open(ok.dataset().dbFile());
      if (facade == null) {
        addAndFire(
            sqliteFile.getName(),
            county,
            BatchImportReport.Status.FAILED,
            "FACADE_OPEN_FAILED",
            System.currentTimeMillis() - startMs);
        return;
      }
      boolean isReplace = registry.snapshot().containsKey(county);
      CountyActiveDataset entry = new CountyActiveDataset(county, ok.dataset(), facade);
      if (isReplace) {
        registry.replace(entry);
        addAndFire(
            sqliteFile.getName(),
            county,
            BatchImportReport.Status.REPLACED,
            null,
            System.currentTimeMillis() - startMs);
      } else {
        registry.add(entry);
        addAndFire(
            sqliteFile.getName(),
            county,
            BatchImportReport.Status.ACTIVATED,
            null,
            System.currentTimeMillis() - startMs);
      }
    } catch (Throwable t) {
      Log.w(TAG, "processBareSqlite threw", t);
      addAndFire(
          sqliteFile.getName(),
          county,
          BatchImportReport.Status.FAILED,
          "IO_ERROR: " + describe(t),
          System.currentTimeMillis() - startMs);
    }
  }

  /**
   * Read {@code metadata.county} (one-shot open then close), via the registry's primary→fallback
   * open so a file the fallback can read but the primary cannot is not wrongly rejected at peek.
   */
  private String peekCounty(File sqliteFile) {
    AddressDatabaseFacade peek = registry.openFacadeWithFallback(sqliteFile);
    if (peek == null) return null;
    try {
      GeneratorMetadata md = peek.readMetadata();
      if (md == null) return null;
      return md.county();
    } finally {
      try {
        peek.close();
      } catch (Throwable t) {
        Log.w(TAG, "peek facade close threw", t);
      }
    }
  }

  // ----------------------------------------------------------------------
  // Listener fan-out (Constitution VI listener short-circuit)
  // ----------------------------------------------------------------------

  private void fireOnStart(BatchImportReport.Entry e) {
    for (Listener l : listeners) {
      try {
        l.onEntryStarted(e);
      } catch (Throwable t) {
        Log.w(TAG, "listener onEntryStarted threw", t);
      }
    }
  }

  private void fireOnFinish(BatchImportReport.Entry e) {
    for (Listener l : listeners) {
      try {
        l.onEntryFinished(e);
      } catch (Throwable t) {
        Log.w(TAG, "listener onEntryFinished threw", t);
      }
    }
  }

  private void fireOnComplete(BatchImportReport report) {
    for (Listener l : listeners) {
      try {
        l.onBatchComplete(report);
      } catch (Throwable t) {
        Log.w(TAG, "listener onBatchComplete threw", t);
      }
    }
  }

  /** Append entry to {@link #reports} and fire onEntryFinished. */
  private void addAndFire(
      String filename, String county, BatchImportReport.Status status, String details, long ms) {
    BatchImportReport.Entry e = new BatchImportReport.Entry(filename, county, status, details, ms);
    reports.add(e);
    Log.i(TAG, e.toString());
    fireOnFinish(e);
  }

  private static String describe(Throwable t) {
    if (t == null) return "unknown";
    String msg = t.getMessage();
    return t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
  }
}
