package com.atakmap.android.twcoord.address;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable view of the in-DB {@code metadata} key/value table written by the companion generator
 * {@code atak-tw-address-generator/scripts/ingest_tgos_csv.py}. The plugin reads this on import to
 * surface dataset provenance on the Offline Address page and to validate the schema version.
 *
 * <p>Mandatory keys ({@code schema_version}, {@code source}, {@code county}, {@code data_date}) are
 * promoted to typed fields. Optional keys ({@code csv_sha256}, {@code csv_path}, {@code crs},
 * {@code inserted}, {@code skipped_no_number}, {@code skipped_unknown_code}) are best-effort —
 * present as nullable typed fields when parseable, and ALL key/value pairs (including unknown ones
 * from a future generator version) are preserved verbatim in {@link #raw}.
 *
 * <p>See {@code specs/004-offline-address/data-model.md §1.3} for the canonical key list.
 */
public final class GeneratorMetadata {

  private final int schemaVersion;
  private final String source;
  private final String county;
  private final String dataDate;
  private final String csvSha256; // nullable
  private final String csvPath; // nullable
  private final String crs; // nullable
  private final long insertedRows; // -1 if absent / unparseable
  private final Map<String, String> raw;

  public GeneratorMetadata(
      int schemaVersion,
      String source,
      String county,
      String dataDate,
      String csvSha256,
      String csvPath,
      String crs,
      long insertedRows,
      Map<String, String> raw) {
    this.schemaVersion = schemaVersion;
    this.source = Objects.requireNonNull(source, "source");
    this.county = Objects.requireNonNull(county, "county");
    this.dataDate = Objects.requireNonNull(dataDate, "dataDate");
    this.csvSha256 = csvSha256;
    this.csvPath = csvPath;
    this.crs = crs;
    this.insertedRows = insertedRows;
    this.raw = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(raw, "raw")));
  }

  public int schemaVersion() {
    return schemaVersion;
  }

  public String source() {
    return source;
  }

  public String county() {
    return county;
  }

  public String dataDate() {
    return dataDate;
  }

  public String csvSha256() {
    return csvSha256;
  }

  public String csvPath() {
    return csvPath;
  }

  public String crs() {
    return crs;
  }

  public long insertedRows() {
    return insertedRows;
  }

  public Map<String, String> raw() {
    return raw;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GeneratorMetadata)) return false;
    GeneratorMetadata that = (GeneratorMetadata) o;
    return schemaVersion == that.schemaVersion
        && insertedRows == that.insertedRows
        && source.equals(that.source)
        && county.equals(that.county)
        && dataDate.equals(that.dataDate)
        && Objects.equals(csvSha256, that.csvSha256)
        && Objects.equals(csvPath, that.csvPath)
        && Objects.equals(crs, that.crs)
        && raw.equals(that.raw);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        schemaVersion, source, county, dataDate, csvSha256, csvPath, crs, insertedRows, raw);
  }
}
