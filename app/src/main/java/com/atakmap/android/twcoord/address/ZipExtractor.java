package com.atakmap.android.twcoord.address;

import com.atakmap.coremap.log.Log;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Streams a {@code .zip} into per-county staging directories. Each {@code places-<county>.sqlite}
 * entry becomes one staged file + computed SHA-256. Supplementary entries (townships / roads /
 * places-osm / timestamp.* / *.manifest.txt) are counted and skipped. Unrecognized entries are
 * counted and logged at {@code Log.w}.
 *
 * <p>Per contracts/zip-extractor.md:
 *
 * <ul>
 *   <li>Streaming: in-flight buffer ≤ 8 KiB (single buffer reused per entry).
 *   <li>Per-entry isolation: a failure on entry N (e.g. DISK_FULL mid-write, CRC mismatch on
 *       close-entry) does NOT abort entries N+1..M. The extractor catches per-entry IOException,
 *       rolls back the staging dir for that entry, appends a {@link FailedEntry} to the result, and
 *       continues.
 *   <li>SHA-256 inline via {@link ShaCalculator.Tap}.
 *   <li>Defensive zip-slip: entries with {@code ..}, leading {@code /}, etc. are already
 *       UNRECOGNIZED via {@link ZipEntryClassifier} and never reach extraction.
 * </ul>
 */
public final class ZipExtractor {

  private static final String TAG = "ZipExtractor";
  private static final String PLACES_FILE_NAME = "places.sqlite";
  private static final int BUF_SIZE = 8192;

  private final FileSystem fs;
  private final ShaCalculator sha;
  private final ZipEntryClassifier classifier;

  public ZipExtractor(FileSystem fs, ShaCalculator sha, ZipEntryClassifier classifier) {
    this.fs = Objects.requireNonNull(fs, "fs");
    this.sha = Objects.requireNonNull(sha, "sha");
    this.classifier = Objects.requireNonNull(classifier, "classifier");
  }

  /** Optional progress hook; emit once per entry transition. */
  public interface ProgressListener {
    void onEntryStart(String entryName);

    void onEntryEnd(String entryName, ZipEntryClassifier.Classification classification);
  }

  /** Result of a streaming extract pass. All lists are unmodifiable. */
  public static final class ExtractResult {
    private final List<ExtractedCounty> counties;
    private final List<FailedEntry> failures;
    private final int supplementaryCount;
    private final int unrecognisedCount;

    public ExtractResult(
        List<ExtractedCounty> counties,
        List<FailedEntry> failures,
        int supplementaryCount,
        int unrecognisedCount) {
      this.counties = Collections.unmodifiableList(new ArrayList<>(counties));
      this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
      this.supplementaryCount = supplementaryCount;
      this.unrecognisedCount = unrecognisedCount;
    }

    public List<ExtractedCounty> counties() {
      return counties;
    }

    public List<FailedEntry> failures() {
      return failures;
    }

    public int supplementaryCount() {
      return supplementaryCount;
    }

    public int unrecognisedCount() {
      return unrecognisedCount;
    }

    public boolean hasAnyCounty() {
      return !counties.isEmpty();
    }
  }

  /** One successfully-extracted {@code places-<county>.sqlite} entry. */
  public static final class ExtractedCounty {
    private final String county;
    private final Path stagingDir;
    private final Path placesFile;
    private final String shaHex;
    private final long fileSizeBytes;

    public ExtractedCounty(
        String county, Path stagingDir, Path placesFile, String shaHex, long fileSizeBytes) {
      this.county = county;
      this.stagingDir = stagingDir;
      this.placesFile = placesFile;
      this.shaHex = shaHex;
      this.fileSizeBytes = fileSizeBytes;
    }

    public String county() {
      return county;
    }

    public Path stagingDir() {
      return stagingDir;
    }

    public Path placesFile() {
      return placesFile;
    }

    public String shaHex() {
      return shaHex;
    }

    public long fileSizeBytes() {
      return fileSizeBytes;
    }

    /** Convenience for callers expecting {@code File}. */
    public File placesFileAsFile() {
      return placesFile.toFile();
    }
  }

  /** One ZIP entry whose extraction failed. */
  public static final class FailedEntry {
    private final String entryName;
    private final String county; // nullable; null if classification failed before county extraction
    private final String reason;

    public FailedEntry(String entryName, String county, String reason) {
      this.entryName = entryName;
      this.county = county;
      this.reason = reason;
    }

    public String entryName() {
      return entryName;
    }

    public String county() {
      return county;
    }

    public String reason() {
      return reason;
    }
  }

  /**
   * Stream-extract the ZIP. Caller owns {@code zipStream} (the extractor wraps but does not close
   * any underlying SAF / file streams the caller may have layered). Failures during individual
   * entries are captured in {@link ExtractResult#failures()} but do not throw.
   *
   * @throws IOException only if the underlying ZIP framing is unreadable (malformed central
   *     directory, etc.); individual entry failures are reported in the result.
   */
  public ExtractResult extract(InputStream zipStream, ProgressListener progress)
      throws IOException {
    Objects.requireNonNull(zipStream, "zipStream");

    List<ExtractedCounty> counties = new ArrayList<>();
    List<FailedEntry> failures = new ArrayList<>();
    int supplementaryCount = 0;
    int unrecognisedCount = 0;
    java.util.Set<String> seenCounties = new java.util.HashSet<>();

    ZipInputStream zin = new ZipInputStream(zipStream);
    ZipEntry entry;
    while ((entry = nextEntrySafely(zin)) != null) {
      String name = entry.getName();
      if (progress != null) {
        try {
          progress.onEntryStart(name);
        } catch (Throwable t) {
          Log.w(TAG, "progress.onEntryStart threw", t);
        }
      }
      ZipEntryClassifier.Classification cls = ZipEntryClassifier.Classification.UNRECOGNIZED;
      try {
        if (entry.isDirectory()) {
          unrecognisedCount++;
          cls = ZipEntryClassifier.Classification.UNRECOGNIZED;
        } else {
          cls = classifier.classify(name);
          switch (cls) {
            case PLACES_COUNTY:
              Optional<String> countyOpt = classifier.countyFromEntry(name);
              if (countyOpt.isEmpty()) {
                // Defensive: classifier said PLACES_COUNTY but countyFromEntry returned empty.
                unrecognisedCount++;
                cls = ZipEntryClassifier.Classification.UNRECOGNIZED;
                break;
              }
              String county = countyOpt.get();
              if (!seenCounties.add(county)) {
                // Duplicate county within the same ZIP — keep the first, skip the rest.
                Log.w(TAG, "duplicate county entry ignored: " + name);
                failures.add(new FailedEntry(name, county, "SKIPPED_DUPLICATE_IN_SAME_ZIP"));
                break;
              }
              try {
                ExtractedCounty ec = extractCountyEntry(zin, county);
                counties.add(ec);
              } catch (IOException e) {
                Log.w(TAG, "extract failed for entry " + name + " (county=" + county + ")", e);
                failures.add(new FailedEntry(name, county, describe(e)));
              }
              break;
            case SKIPPED_SUPPLEMENTARY:
              supplementaryCount++;
              break;
            case UNRECOGNIZED:
            default:
              Log.w(TAG, "unrecognised zip entry: " + name);
              unrecognisedCount++;
              break;
          }
        }
      } finally {
        try {
          zin.closeEntry();
        } catch (IOException e) {
          // CRC mismatch at closeEntry is treated as a per-entry failure if it was a county entry.
          Log.w(TAG, "closeEntry on " + name + " threw", e);
          if (cls == ZipEntryClassifier.Classification.PLACES_COUNTY) {
            failures.add(new FailedEntry(name, null, "CRC_MISMATCH: " + describe(e)));
          }
        }
        if (progress != null) {
          try {
            progress.onEntryEnd(name, cls);
          } catch (Throwable t) {
            Log.w(TAG, "progress.onEntryEnd threw", t);
          }
        }
      }
    }

    return new ExtractResult(counties, failures, supplementaryCount, unrecognisedCount);
  }

  private ZipEntry nextEntrySafely(ZipInputStream zin) throws IOException {
    try {
      return zin.getNextEntry();
    } catch (ZipException e) {
      // Malformed central directory or non-ZIP stream entirely — propagate so the caller can
      // surface ZIP_NO_VALID_DATASETS or similar.
      throw e;
    }
  }

  private ExtractedCounty extractCountyEntry(ZipInputStream zin, String county) throws IOException {
    Path stagingDir = fs.createCountyStagingDir(county);
    Path placesPath = stagingDir.resolve(PLACES_FILE_NAME);
    long totalRead = 0;
    String shaHex;
    OutputStream sink = fs.openWrite(placesPath);
    try (ShaCalculator.Tap tap = sha.tap(sink)) {
      OutputStream tapStream = tap.stream();
      byte[] buf = new byte[BUF_SIZE];
      int read;
      while ((read = zin.read(buf)) > 0) {
        tapStream.write(buf, 0, read);
        totalRead += read;
      }
      tapStream.flush();
      tap.close(); // closes sink internally; safe to call before reading hex()
      shaHex = tap.hex();
    } catch (IOException e) {
      // Roll back the staging dir for this county — leaves other counties' staging intact.
      fs.deleteRecursively(stagingDir);
      throw e;
    }
    return new ExtractedCounty(county, stagingDir, placesPath, shaHex, totalRead);
  }

  private static String describe(Throwable t) {
    if (t == null) return "unknown";
    String msg = t.getMessage();
    return t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
  }
}
