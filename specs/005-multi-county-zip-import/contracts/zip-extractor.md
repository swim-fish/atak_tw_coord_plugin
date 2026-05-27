# Contract: ZipExtractor + ZipEntryClassifier

**Modules**:
- `app/src/main/java/com/atakmap/android/twcoord/address/ZipExtractor.java` (NEW)
- `app/src/main/java/com/atakmap/android/twcoord/address/ZipEntryClassifier.java` (NEW)

Streams a `.zip` into per-county staging directories. The classifier decides what each entry is (`places-<county>`, supplementary, or unrecognised) so the extractor knows what to do with the bytes.

## Interfaces

```java
public final class ZipEntryClassifier {

  public Classification classify(String entryName);

  public enum Classification {
    PLACES_COUNTY,             // matches "places-<county>.sqlite" pattern; extract + validate
    SKIPPED_SUPPLEMENTARY,     // townships / roads / places-osm / timestamp.* / *.manifest.txt
    UNRECOGNIZED               // anything else (logged at Log.w; not counted as failure)
  }

  /** Extract the county portion of "places-<county>.sqlite" if classification is PLACES_COUNTY. */
  public Optional<String> countyFromEntry(String entryName);
}

public final class ZipExtractor {

  public ZipExtractor(AtakFileSystem fs, ShaCalculator shaCalculator);

  /**
   * Stream the ZIP, dispatching each entry through the classifier. For PLACES_COUNTY entries,
   * write bytes to {@code .staging-<county>-<uuid>/places.sqlite} while computing SHA-256;
   * for everything else, increment the supplementary / unrecognised counts in the report.
   */
  public ExtractResult extract(InputStream zipStream, ProgressListener progress);

  public static final class ExtractResult {
    public final List<ExtractedCounty> counties;
    public final int supplementaryCount;
    public final int unrecognisedCount;
  }

  public static final class ExtractedCounty {
    public final String county;
    public final File stagingDir;       // .staging-<county>-<uuid>/
    public final File placesFile;       // staging/places.sqlite
    public final String shaHex;
    public final long fileSizeBytes;
  }
}
```

## Invariants (Extractor)

1. **Streaming**: max in-flight byte buffer ≤ 64 KiB (single read buffer reused per entry).
2. **Per-entry isolation**: an extract failure on entry N (e.g. DISK_FULL mid-write, CRC mismatch) does not abort entries N+1..M.
3. **Atomic staging**: each PLACES_COUNTY entry writes to `.staging-<county>-<uuid>/places.sqlite` (per-county UUID, never collides with another county's staging or a previous import's leftover).
4. **SHA-256 inline**: hash computed via `ShaCalculator.tap(sink)` during the write; the digest is final when the entry stream is exhausted.
5. **Defensive zip-slip**: entry names containing `..`, leading `/`, or absolute paths are classified UNRECOGNIZED and not extracted.
6. **Progress emission**: one `ProgressListener` call per entry transition (entered / completed); finer-grained progress within an entry is not required (each entry is one file write).

## Invariants (Classifier)

1. **Case-sensitive** matching against `places-<county>.sqlite` (generator emits lowercase only, per data-contract §2).
2. **`<county>` extraction**: regex `^places-(.+)\.sqlite$`; the captured group is the county string AS WRITTEN IN THE FILENAME. (Validation against `metadata.county` happens downstream in `AddressBundleImporter`.)
3. **Supplementary list is literal**: `townships.sqlite`, `roads.sqlite`, `places-osm.sqlite`, any name starting with `timestamp.`, any name ending with `.manifest.txt`. (UPDATE this list when feature 006 adopts townships/roads/osm.)

## Test plan (`ZipExtractorTest` + `ZipEntryClassifierTest`, JVM/Robolectric)

### Classifier

| # | Input | Expected |
|---|---|---|
| 1 | `places-taichung.sqlite` | PLACES_COUNTY, county = "taichung" |
| 2 | `places-彰化縣.sqlite` (CJK) | PLACES_COUNTY, county = "彰化縣" |
| 3 | `places-OSM.sqlite` (case-mixed) | UNRECOGNIZED (lowercase-only contract) |
| 4 | `townships.sqlite` | SKIPPED_SUPPLEMENTARY |
| 5 | `places-osm.sqlite` | SKIPPED_SUPPLEMENTARY (specific name, not a county) |
| 6 | `timestamp.taichung` | SKIPPED_SUPPLEMENTARY |
| 7 | `places-taichung.manifest.txt` | SKIPPED_SUPPLEMENTARY (manifest suffix wins over places- prefix) |
| 8 | `../etc/passwd` | UNRECOGNIZED (zip-slip defence) |
| 9 | `/absolute/path/places-taipei.sqlite` | UNRECOGNIZED |
| 10 | `places-.sqlite` (empty county) | UNRECOGNIZED (regex requires at least 1 char) |

### Extractor

| # | Scenario | Expected |
|---|---|---|
| 1 | ZIP with single `places-taichung.sqlite` | 1 ExtractedCounty; staging dir created; SHA matches independent compute |
| 2 | `tw-central-full.zip` fixture (2 places + 3 supplementary) | 2 ExtractedCounty + 3 supplementary + 0 unrecognised |
| 3 | ZIP with corrupt entry (CRC fail) | that entry's staging dir rolled back; other entries' staging dirs intact; ExtractResult reflects the failure |
| 4 | ZIP with zip-slip `../places-evil.sqlite` | classified UNRECOGNIZED; not extracted; no staging dir created |
| 5 | ZIP that's actually a TAR | early `ZipException` from `ZipInputStream.getNextEntry`; ExtractResult is empty + all-supplementary=0 (caller treats as `ZIP_NO_VALID_DATASETS`) |
| 6 | ZIP with 0 entries (empty ZIP) | ExtractResult is empty (caller treats as `ZIP_NO_VALID_DATASETS`) |
| 7 | ZIP entry larger than free disk | DISK_FULL surfaced per entry; staging dir cleaned up |
| 8 | Two ZIP entries naming the same county | first extracted to staging; second classified PLACES_COUNTY but the BatchImportCoordinator detects the duplicate (this is coordinator-level, not extractor-level — extractor returns both ExtractedCounty entries) |
| 9 | Streaming RSS budget | extract a synthetic 1 GiB ZIP fixture; assert max heap delta during extract ≤ 50 MiB (well under SC-005's 200 MiB whole-process budget) |
