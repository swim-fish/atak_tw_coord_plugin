package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.AddressBundleImporterTest.TempFileSystem;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Feature 005 — regression tests for the per-county Replace guard in {@link
 * BatchImportCoordinator}. Covers the {@code enqueue(file, expectedCounty)} overload that the
 * Offline Address page's per-county Replace button routes through: a picked dataset whose {@code
 * metadata.county} does not equal the row the operator tapped must be rejected with {@link
 * BatchImportReport.Status#SKIPPED_COUNTY_MISMATCH} instead of silently replacing the wrong county
 * (see docs/reviews/2026-05-28-master-to-005-review.md, "Per-county Replace does not enforce the
 * selected county").
 *
 * <p>The county the coordinator reads is {@code metadata.county}, peeked through the injected
 * {@link AddressDatabaseFacade.Factory}. These tests fix that factory's returned county so the
 * bytes of the picked file are irrelevant — both the bare {@code .sqlite} path ({@code
 * processBareSqlite}) and the ZIP path ({@code activateExtractedCounty}) are exercised.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class BatchImportCoordinatorTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private TempFileSystem fs;
  private AddressBundleImporter importer;
  private ZipExtractor extractor;
  private ZipEntryClassifier classifier;
  private FixedCountyFactory primary;
  private ActiveDatasetRegistry registry;
  private ExecutorService executor;
  private BatchImportCoordinator coordinator;

  @Before
  public void setUp() throws IOException {
    fs = new TempFileSystem(tmp.newFolder("root").toPath());
    MessageDigestShaCalculator sha = new MessageDigestShaCalculator();
    importer = new AddressBundleImporter(fs, sha, 2);
    classifier = new ZipEntryClassifier();
    extractor = new ZipExtractor(fs, sha, classifier);
    primary = new FixedCountyFactory("高雄市");
    Supplier<AddressDatabaseFacade.Factory> fallback = () -> primary;
    registry = new ActiveDatasetRegistry(importer, primary, fallback, fs);
    executor = Executors.newSingleThreadExecutor();
    coordinator =
        new BatchImportCoordinator(
            importer, extractor, classifier, registry, primary, executor, fs);
  }

  @After
  public void tearDown() {
    executor.shutdownNow();
  }

  // ----------------------------------------------------------------------
  // Bare .sqlite path — processBareSqlite()
  // ----------------------------------------------------------------------

  /** Replace 台中市, but the picked bare DB declares 高雄市 → rejected, nothing activated. */
  @Test
  public void bareSqliteCountyMismatchIsRejectedWithoutActivation() throws Exception {
    primary.county = "高雄市";
    File picked = writeJunkSqlite("places-pick.sqlite");

    BatchImportReport report = runBatch(picked, "台中市");

    assertThat(report.entries()).hasSize(1);
    BatchImportReport.Entry e = report.entries().get(0);
    assertThat(e.status()).isEqualTo(BatchImportReport.Status.SKIPPED_COUNTY_MISMATCH);
    assertThat(e.county()).isEqualTo("高雄市");
    assertThat(e.details()).contains("台中市").contains("高雄市");
    assertThat(e.filename()).isEqualTo("places-pick.sqlite");
    // The mismatch guard must short-circuit BEFORE the importer runs: no county is activated.
    assertThat(report.skippedCount()).isEqualTo(1);
    assertThat(report.activatedCount()).isZero();
    assertThat(report.replacedCount()).isZero();
    assertThat(report.failedCount()).isZero();
    assertThat(registry.snapshot()).isEmpty();
  }

  /**
   * Replace 高雄市 with a DB that also declares 高雄市 → the guard passes and the file is handed to the
   * importer (which then fails on the junk bytes). The point is that it is NOT short-circuited as a
   * county mismatch.
   */
  @Test
  public void bareSqliteCountyMatchPassesGuardAndReachesImporter() throws Exception {
    primary.county = "高雄市";
    File picked = writeJunkSqlite("places-pick.sqlite");

    BatchImportReport report = runBatch(picked, "高雄市");

    assertThat(report.entries())
        .as("matching county must not be rejected as a mismatch")
        .noneMatch(e -> e.status() == BatchImportReport.Status.SKIPPED_COUNTY_MISMATCH);
    // Junk bytes can't import, so the guard-passed file lands as FAILED — proving it reached the
    // importer rather than being skipped.
    assertThat(report.failedCount()).isEqualTo(1);
  }

  /**
   * Regression (Codex PR#2 review): peekCounty must use the registry's primary→fallback open. When
   * the primary factory can't open the file but the fallback can, the county must still be peeked
   * (so the file reaches the importer) rather than rejected as "metadata.county unreadable".
   */
  @Test
  public void peekUsesFallbackWhenPrimaryCannotOpen() throws Exception {
    AddressDatabaseFacade.Factory nullPrimary = dbFile -> null; // primary can't open anything
    FixedCountyFactory fallbackFac = new FixedCountyFactory("高雄市");
    ActiveDatasetRegistry reg =
        new ActiveDatasetRegistry(importer, nullPrimary, () -> fallbackFac, fs);
    BatchImportCoordinator c =
        new BatchImportCoordinator(
            importer, extractor, classifier, reg, nullPrimary, executor, fs);
    File picked = writeJunkSqlite("places-x.sqlite");

    BatchImportReport report = runBatch(c, picked, "高雄市");

    // peek resolved 高雄市 via the fallback (not rejected as unreadable / mismatch); the file then
    // reaches the importer and fails there on the junk bytes — proving peek succeeded.
    assertThat(report.entries())
        .noneMatch(e -> e.status() == BatchImportReport.Status.SKIPPED_COUNTY_MISMATCH);
    assertThat(report.entries())
        .as("peeked county via fallback")
        .anyMatch(e -> "高雄市".equals(e.county()));
  }

  /** A plain Import (expectedCounty == null) is never rejected for a county mismatch. */
  @Test
  public void bareSqlitePlainImportIsNeverRejectedAsMismatch() throws Exception {
    primary.county = "高雄市";
    File picked = writeJunkSqlite("places-pick.sqlite");

    BatchImportReport report = runBatch(picked, null);

    assertThat(report.entries())
        .noneMatch(e -> e.status() == BatchImportReport.Status.SKIPPED_COUNTY_MISMATCH);
  }

  // ----------------------------------------------------------------------
  // ZIP path — activateExtractedCounty()
  // ----------------------------------------------------------------------

  /**
   * Replace 台中市 with a ZIP whose places-* entry declares 高雄市 → rejected, and the extractor's
   * intermediate staging dir is cleaned up (the mismatch branch returns early and so must delete it
   * itself).
   */
  @Test
  public void zipBundleCountyMismatchIsRejectedAndStagingCleaned() throws Exception {
    RecordingFileSystem recordingFs = new RecordingFileSystem(fs);
    ZipExtractor recordingExtractor =
        new ZipExtractor(recordingFs, new MessageDigestShaCalculator(), classifier);
    BatchImportCoordinator c =
        new BatchImportCoordinator(
            importer, recordingExtractor, classifier, registry, primary, executor, recordingFs);
    primary.county = "高雄市";
    // Entry name carries the romanised generator-side county; metadata.county (faked above) is what
    // the guard compares against.
    File zip = writeZip("tw-central.zip", "places-taichung.sqlite", junkBytes(1024));

    BatchImportReport report = runBatch(c, zip, "台中市");

    assertThat(report.entries())
        .anyMatch(e -> e.status() == BatchImportReport.Status.SKIPPED_COUNTY_MISMATCH);
    BatchImportReport.Entry mismatch =
        report.entries().stream()
            .filter(e -> e.status() == BatchImportReport.Status.SKIPPED_COUNTY_MISMATCH)
            .findFirst()
            .orElseThrow(AssertionError::new);
    assertThat(mismatch.county()).isEqualTo("高雄市");
    assertThat(mismatch.filename()).contains("places-taichung.sqlite");
    assertThat(report.activatedCount()).isZero();
    assertThat(report.replacedCount()).isZero();
    assertThat(registry.snapshot()).isEmpty();
    // The early-return mismatch branch must delete the extractor's staging dir itself.
    assertThat(recordingFs.deleted)
        .as("staging dir of the rejected county must be cleaned up")
        .anyMatch(p -> p.getFileName().toString().startsWith(".staging-"));
    long survivingStaging =
        Files.list(recordingFs.getActiveDir().getParent())
            .filter(p -> p.getFileName().toString().startsWith(".staging-"))
            .count();
    assertThat(survivingStaging).isZero();
  }

  /**
   * Feature 006 regression — a ZIP carrying {@code townships.sqlite} must land the boundary at
   * {@code active/_boundary/townships.sqlite}. Before the fix the extractor staged it but the
   * coordinator never moved it, so forward search was stuck on "import base data" forever.
   */
  @Test
  public void zipWithTownshipsMountsBoundaryIntoActiveBoundaryDir() throws Exception {
    File zip = writeZip("tw-central.zip", "townships.sqlite", junkBytes(2048));

    BatchImportReport report = runBatch(zip, null);

    assertThat(Files.exists(fs.boundaryDbFile()))
        .as("townships.sqlite must be mounted at active/_boundary/")
        .isTrue();
    assertThat(report.entries())
        .anyMatch(
            e ->
                e.status() == BatchImportReport.Status.ACTIVATED
                    && e.filename().contains("townships.sqlite"));
    assertThat(report.failedCount()).isZero();
    // No staging dir may survive once the boundary has been moved into place.
    long survivingStaging =
        Files.list(fs.getActiveDir().getParent())
            .filter(p -> p.getFileName().toString().startsWith(".staging-"))
            .count();
    assertThat(survivingStaging).isZero();
  }

  /**
   * Feature 006 regression — a ZIP that carries BOTH a places county and the boundary mounts the
   * boundary in addition to activating the county (the real tw-central-full.zip shape).
   */
  @Test
  public void zipWithCountyAndTownshipsMountsBoth() throws Exception {
    primary.county = "高雄市";
    File zip =
        writeZipMulti(
            "tw-central.zip",
            new String[] {"places-kaohsiung.sqlite", "townships.sqlite"},
            new byte[][] {junkBytes(1024), junkBytes(2048)});

    BatchImportReport report = runBatch(zip, null);

    assertThat(Files.exists(fs.boundaryDbFile())).as("boundary mounted").isTrue();
    assertThat(report.entries())
        .anyMatch(
            e ->
                e.status() == BatchImportReport.Status.ACTIVATED
                    && e.filename().contains("townships.sqlite"));
  }

  // ----------------------------------------------------------------------
  // Listener lifecycle — backs the OfflineAddressReceiver detach fix (review finding #4): once a
  // listener is removed (e.g. on receiver dispose / re-bind), it must receive no further batch
  // callbacks, so an in-flight or future import can't post UI work into a disposed receiver.
  // ----------------------------------------------------------------------

  @Test
  public void removedListenerReceivesNoFurtherCallbacks() throws Exception {
    primary.county = "高雄市";
    CountingCallbacks tracked = new CountingCallbacks();
    coordinator.addListener(tracked);

    // Batch 1: tracked is attached → exactly one onBatchComplete. (runBatch attaches its own
    // completion listener AFTER tracked, so by the time it returns tracked has already been fired.)
    runBatch(coordinator, writeJunkSqlite("a.sqlite"), null);
    assertThat(tracked.completes.get()).isEqualTo(1);

    // Detach, then run a second batch — tracked must not be called again.
    coordinator.removeListener(tracked);
    runBatch(coordinator, writeJunkSqlite("b.sqlite"), null);

    assertThat(tracked.completes.get())
        .as("a removed listener gets no further onBatchComplete")
        .isEqualTo(1);
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private BatchImportReport runBatch(File pick, String expectedCounty) throws InterruptedException {
    return runBatch(coordinator, pick, expectedCounty);
  }

  private static BatchImportReport runBatch(
      BatchImportCoordinator c, File pick, String expectedCounty) throws InterruptedException {
    CapturingListener listener = new CapturingListener();
    c.addListener(listener);
    c.enqueue(pick, expectedCounty);
    c.finishBatch();
    assertThat(listener.done.await(5, TimeUnit.SECONDS)).as("batch completed").isTrue();
    return listener.report;
  }

  private File writeJunkSqlite(String name) throws IOException {
    File f = tmp.newFile(name);
    Files.write(f.toPath(), "not a real sqlite database".getBytes(StandardCharsets.UTF_8));
    return f;
  }

  private File writeZip(String zipName, String entryName, byte[] entryData) throws IOException {
    File f = tmp.newFile(zipName);
    try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(f.toPath()))) {
      z.putNextEntry(new ZipEntry(entryName));
      z.write(entryData);
      z.closeEntry();
    }
    return f;
  }

  private File writeZipMulti(String zipName, String[] entryNames, byte[][] entryData)
      throws IOException {
    File f = tmp.newFile(zipName);
    try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(f.toPath()))) {
      for (int i = 0; i < entryNames.length; i++) {
        z.putNextEntry(new ZipEntry(entryNames[i]));
        z.write(entryData[i]);
        z.closeEntry();
      }
    }
    return f;
  }

  private static byte[] junkBytes(int n) {
    byte[] b = new byte[n];
    for (int i = 0; i < n; i++) b[i] = (byte) (i & 0xFF);
    return b;
  }

  /** Factory returning a facade whose metadata.county is fixed to {@link #county}. */
  private static final class FixedCountyFactory implements AddressDatabaseFacade.Factory {
    volatile String county;

    FixedCountyFactory(String county) {
      this.county = county;
    }

    @Override
    public AddressDatabaseFacade open(File dbFile) {
      final String c = county;
      return new AddressDatabaseFacade() {
        @Override
        public GeneratorMetadata readMetadata() {
          return new GeneratorMetadata(
              2, "tgos", c, "115-01", null, null, null, 0L, Collections.emptyMap());
        }

        @Override
        public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
          return null;
        }

        @Override
        public void close() {}
      };
    }
  }

  /** FileSystem decorator recording every {@link #deleteRecursively} target. */
  private static final class RecordingFileSystem implements FileSystem {
    private final FileSystem delegate;
    final List<Path> deleted = new CopyOnWriteArrayList<>();

    RecordingFileSystem(FileSystem delegate) {
      this.delegate = delegate;
    }

    @Override
    public Path getActiveDir() {
      return delegate.getActiveDir();
    }

    @Override
    public Path createStagingDir() throws IOException {
      return delegate.createStagingDir();
    }

    @Override
    public OutputStream openWrite(Path path) throws IOException {
      return delegate.openWrite(path);
    }

    @Override
    public void atomicMove(Path src, Path dst) throws IOException {
      delegate.atomicMove(src, dst);
    }

    @Override
    public void deleteRecursively(Path path) {
      if (path != null) deleted.add(path);
      delegate.deleteRecursively(path);
    }

    @Override
    public boolean exists(Path path) {
      return delegate.exists(path);
    }
  }

  /**
   * Counts {@code onBatchComplete} fan-outs across batches; used to prove a removed listener stops.
   */
  private static final class CountingCallbacks implements BatchImportCoordinator.Listener {
    final java.util.concurrent.atomic.AtomicInteger completes =
        new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public void onEntryStarted(BatchImportReport.Entry entry) {}

    @Override
    public void onEntryFinished(BatchImportReport.Entry entry) {}

    @Override
    public void onBatchComplete(BatchImportReport r) {
      completes.incrementAndGet();
    }
  }

  /** Captures the final {@link BatchImportReport} and signals batch completion. */
  private static final class CapturingListener implements BatchImportCoordinator.Listener {
    final CountDownLatch done = new CountDownLatch(1);
    volatile BatchImportReport report;

    @Override
    public void onEntryStarted(BatchImportReport.Entry entry) {}

    @Override
    public void onEntryFinished(BatchImportReport.Entry entry) {}

    @Override
    public void onBatchComplete(BatchImportReport r) {
      this.report = r;
      done.countDown();
    }
  }
}
