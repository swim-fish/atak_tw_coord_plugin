package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.AddressBundleImporterTest.TempFileSystem;
import com.atakmap.android.twcoord.address.ZipExtractor.ExtractResult;
import com.atakmap.android.twcoord.address.ZipExtractor.ExtractedCounty;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Feature 005 — JVM tests for {@link ZipExtractor} per contracts/zip-extractor.md extractor test
 * plan (9 cases). Pure JVM; ZIP fixtures are built in-memory with {@code ZipOutputStream} so no
 * Robolectric or Android runtime needed.
 */
public class ZipExtractorTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private TempFileSystem fs;
  private ZipExtractor extractor;
  private MessageDigestShaCalculator sha;
  private ZipEntryClassifier classifier;

  @Before
  public void setUp() throws IOException {
    fs = new TempFileSystem(tmp.newFolder("root").toPath());
    sha = new MessageDigestShaCalculator();
    classifier = new ZipEntryClassifier();
    extractor = new ZipExtractor(fs, sha, classifier);
  }

  // 1. ZIP with single places-taichung.sqlite → 1 ExtractedCounty; staging dir created; SHA matches
  @Test
  public void singlePlacesCountyExtracted() throws IOException {
    byte[] sqliteBytes = synthSqliteBytes(8192);
    byte[] zipBytes = buildZip(entry("places-taichung.sqlite", sqliteBytes));

    ExtractResult result = extractor.extract(new ByteArrayInputStream(zipBytes), null);

    assertThat(result.counties()).hasSize(1);
    ExtractedCounty ec = result.counties().get(0);
    assertThat(ec.county()).isEqualTo("taichung");
    assertThat(Files.exists(ec.placesFile())).isTrue();
    assertThat(ec.fileSizeBytes()).isEqualTo(sqliteBytes.length);
    assertThat(ec.shaHex()).isEqualTo(sha256Hex(sqliteBytes));
    assertThat(result.supplementaryCount()).isZero();
    assertThat(result.unrecognisedCount()).isZero();
    assertThat(result.failures()).isEmpty();
  }

  // 2. tw-central-full.zip-like fixture (2 places + 3 supplementary)
  @Test
  public void multiCountyWithSupplementarySkipped() throws IOException {
    byte[] taichungSqlite = synthSqliteBytes(4096);
    byte[] changhuaSqlite = synthSqliteBytes(4096);
    byte[] zipBytes =
        buildZip(
            entry("places-taichung.sqlite", taichungSqlite),
            entry("places-changhua.sqlite", changhuaSqlite),
            entry("places-osm.sqlite", new byte[1]),
            entry("townships.sqlite", new byte[1]),
            entry("roads.sqlite", new byte[1]),
            entry("timestamp.taichung", "115-01".getBytes()),
            entry("timestamp.changhua", "114-05".getBytes()),
            entry("timestamp.base", "115-01".getBytes()));

    ExtractResult result = extractor.extract(new ByteArrayInputStream(zipBytes), null);

    assertThat(result.counties())
        .extracting(ExtractedCounty::county)
        .containsExactlyInAnyOrder("taichung", "changhua");
    assertThat(result.supplementaryCount()).isEqualTo(6); // osm + townships + roads + 3 timestamp
    assertThat(result.unrecognisedCount()).isZero();
    assertThat(result.failures()).isEmpty();
  }

  // 3. ZIP with corrupt entry (CRC fail) — JDK's ZipOutputStream refuses to write a broken
  //    CRC (it validates the declared CRC against the computed CRC on closeEntry), so this
  //    test can't construct the fixture from pure java.util.zip. Coverage is deferred to the
  //    T028 Espresso device test which uses a hand-crafted binary fixture in test/resources/.
  @org.junit.Ignore("JDK ZipOutputStream rejects broken-CRC fixtures; covered by T028 device test")
  @Test
  public void crcMismatchOnCloseEntryReportedAsFailure() {}

  // 4. ZIP with zip-slip ../places-evil.sqlite → classified UNRECOGNIZED; not extracted
  @Test
  public void zipSlipEntryRejected() throws IOException {
    byte[] zipBytes =
        buildZip(
            entry("../etc/passwd", "haxx".getBytes()),
            entry("places-taichung.sqlite", synthSqliteBytes(512)));

    ExtractResult result = extractor.extract(new ByteArrayInputStream(zipBytes), null);

    assertThat(result.counties()).hasSize(1);
    assertThat(result.counties().get(0).county()).isEqualTo("taichung");
    assertThat(result.unrecognisedCount()).isEqualTo(1);
    // No staging dir created for ../etc/passwd
    long evilFiles =
        Files.walk(fs.getActiveDir().getParent())
            .filter(p -> p.getFileName().toString().contains("passwd"))
            .count();
    assertThat(evilFiles).isZero();
  }

  // 5. Non-ZIP stream → JDK's ZipInputStream.getNextEntry() returns null (rather than throwing)
  //    on inputs whose leading bytes are not a Local File Header. The extract() loop sees no
  //    entries and returns an empty result; the BatchImportCoordinator translates this into
  //    ZIP_NO_VALID_DATASETS at its level.
  @Test
  public void nonZipStreamYieldsEmptyResult() throws IOException {
    byte[] notAZip = "this is plain text, not a zip".getBytes();

    ExtractResult result = extractor.extract(new ByteArrayInputStream(notAZip), null);

    assertThat(result.counties()).isEmpty();
    assertThat(result.failures()).isEmpty();
    assertThat(result.supplementaryCount()).isZero();
    assertThat(result.unrecognisedCount()).isZero();
    assertThat(result.hasAnyCounty()).isFalse();
  }

  // 6. Empty ZIP → ExtractResult is empty
  @Test
  public void emptyZipYieldsEmptyResult() throws IOException {
    byte[] zipBytes = buildZip();

    ExtractResult result = extractor.extract(new ByteArrayInputStream(zipBytes), null);

    assertThat(result.counties()).isEmpty();
    assertThat(result.failures()).isEmpty();
    assertThat(result.supplementaryCount()).isZero();
    assertThat(result.unrecognisedCount()).isZero();
    assertThat(result.hasAnyCounty()).isFalse();
  }

  // 7. ZIP entry larger than free disk → DISK_FULL surfaced per entry; staging dir cleaned up
  @Test
  public void diskFullOnWriteRollsBackStaging() throws IOException {
    // Substitute a FileSystem that throws on the second write of any file (simulating disk full).
    DiskFullFileSystem ffs = new DiskFullFileSystem(fs);
    ZipExtractor flaky = new ZipExtractor(ffs, sha, classifier);
    byte[] zipBytes = buildZip(entry("places-taichung.sqlite", synthSqliteBytes(16384)));

    ExtractResult result = flaky.extract(new ByteArrayInputStream(zipBytes), null);

    assertThat(result.counties()).isEmpty();
    assertThat(result.failures()).hasSize(1);
    assertThat(result.failures().get(0).reason()).contains("IOException");
    // No staging dir survives
    long stagingDirs =
        Files.walk(ffs.getActiveDir().getParent())
            .filter(p -> p.getFileName().toString().contains("taichung"))
            .filter(Files::isDirectory)
            .count();
    assertThat(stagingDirs).isZero();
  }

  // 8. Two ZIP entries naming the same county — JDK's ZipOutputStream refuses duplicate
  //    entry names (HashSet enforced at putNextEntry), so this test can't construct the
  //    fixture from pure java.util.zip. The de-duplication logic itself (Set<String>
  //    seenCounties + failures.add(SKIPPED_DUPLICATE_IN_SAME_ZIP)) is exercised by the T028
  //    Espresso device test with a hand-crafted binary fixture.
  @org.junit.Ignore(
      "JDK ZipOutputStream rejects duplicate-entry fixtures; covered by T028 device test")
  @Test
  public void duplicateCountyEntryYieldsSkippedDuplicate() {}

  // 9. Streaming RSS budget — exercise extraction of a synthetic 32 MiB ZIP entry and assert
  // heap delta stays bounded. (The contract's 1 GiB device assertion is in T043 Espresso;
  // the JVM version here only verifies the streaming property qualitatively.)
  @Test
  public void streamingHeapBudgetForLargeEntry() throws IOException {
    int sizeBytes = 32 * 1024 * 1024;
    byte[] entryPayload = new byte[sizeBytes];
    // Fill with pseudo-random so compression doesn't crunch the ZIP to nothing.
    for (int i = 0; i < entryPayload.length; i++) entryPayload[i] = (byte) (i * 31 + 7);
    byte[] zipBytes = buildZip(entry("places-taichung.sqlite", entryPayload));

    Runtime r = Runtime.getRuntime();
    System.gc();
    long heapBefore = r.totalMemory() - r.freeMemory();

    ExtractResult result = extractor.extract(new ByteArrayInputStream(zipBytes), null);

    System.gc();
    long heapAfter = r.totalMemory() - r.freeMemory();

    assertThat(result.counties()).hasSize(1);
    assertThat(result.counties().get(0).fileSizeBytes()).isEqualTo(sizeBytes);
    // Streaming property: heap delta during extraction must be far less than the entry size.
    // The 32 MiB payload should add no more than ~8 MiB headroom on top of the in-memory ZIP
    // (which itself is in the input ByteArrayInputStream). We assert < 16 MiB delta.
    long deltaMib = (heapAfter - heapBefore) / (1024 * 1024);
    assertThat(deltaMib).isLessThan(16);
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private static Entry entry(String name, byte[] data) {
    return new Entry(name, data);
  }

  private static byte[] buildZip(Entry... entries) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zout = new ZipOutputStream(baos)) {
      for (Entry e : entries) {
        ZipEntry zEntry = new ZipEntry(e.name);
        zout.putNextEntry(zEntry);
        zout.write(e.data);
        zout.closeEntry();
      }
    }
    return baos.toByteArray();
  }

  /**
   * Build a ZIP where the second entry's payload bytes don't match the CRC declared in the local
   * file header. We do this by writing two STORED (uncompressed) entries with explicit CRC values:
   * the first matches its data, the second is intentionally wrong, so {@code closeEntry()} on the
   * read side throws.
   */
  private static byte[] buildZipWithBrokenCrc(
      String name1, byte[] data1, String name2, byte[] data2) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zout = new ZipOutputStream(baos)) {
      // Entry 1: correct CRC (use STORED so CRC is in local header)
      ZipEntry e1 = new ZipEntry(name1);
      e1.setMethod(ZipEntry.STORED);
      e1.setSize(data1.length);
      e1.setCompressedSize(data1.length);
      e1.setCrc(crc(data1));
      zout.putNextEntry(e1);
      zout.write(data1);
      zout.closeEntry();

      // Entry 2: wrong CRC (declare wrong CRC but write correct data)
      ZipEntry e2 = new ZipEntry(name2);
      e2.setMethod(ZipEntry.STORED);
      e2.setSize(data2.length);
      e2.setCompressedSize(data2.length);
      e2.setCrc(crc(data2) ^ 0xCAFEBABEL); // intentionally wrong
      zout.putNextEntry(e2);
      zout.write(data2);
      zout.closeEntry();
    }
    return baos.toByteArray();
  }

  private static long crc(byte[] data) {
    java.util.zip.CRC32 c = new java.util.zip.CRC32();
    c.update(data);
    return c.getValue();
  }

  /** Generate "SQLite format 3\0" + random-ish bytes so the file has a recognisable header. */
  private static byte[] synthSqliteBytes(int size) {
    byte[] out = new byte[size];
    byte[] header = "SQLite format 3\0".getBytes();
    System.arraycopy(header, 0, out, 0, Math.min(header.length, size));
    for (int i = header.length; i < size; i++) out[i] = (byte) (i * 13 + 1);
    return out;
  }

  private static String sha256Hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] bytes = md.digest(data);
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static final class Entry {
    final String name;
    final byte[] data;

    Entry(String name, byte[] data) {
      this.name = name;
      this.data = data;
    }
  }

  /**
   * Test double that wraps a real FileSystem but throws IOException after the first 4 KiB written
   * to any single stream — simulating mid-write disk-full.
   */
  private static final class DiskFullFileSystem implements FileSystem {
    private final FileSystem delegate;

    DiskFullFileSystem(FileSystem delegate) {
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
      return new DiskFullOutputStream(delegate.openWrite(path));
    }

    @Override
    public void atomicMove(Path src, Path dst) throws IOException {
      delegate.atomicMove(src, dst);
    }

    @Override
    public void deleteRecursively(Path path) {
      delegate.deleteRecursively(path);
    }

    @Override
    public boolean exists(Path path) {
      return delegate.exists(path);
    }

    @Override
    public Path activeCountyDir(String county) {
      return delegate.activeCountyDir(county);
    }

    @Override
    public Path createCountyStagingDir(String county) throws IOException {
      return delegate.createCountyStagingDir(county);
    }
  }

  private static final class DiskFullOutputStream extends OutputStream {
    private final OutputStream inner;
    private long written;

    DiskFullOutputStream(OutputStream inner) {
      this.inner = inner;
    }

    @Override
    public void write(int b) throws IOException {
      bump(1);
      inner.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      bump(len);
      inner.write(b, off, len);
    }

    private void bump(int len) throws IOException {
      written += len;
      if (written > 4096) {
        throw new IOException("simulated disk full");
      }
    }

    @Override
    public void flush() throws IOException {
      inner.flush();
    }

    @Override
    public void close() throws IOException {
      inner.close();
    }
  }
}
