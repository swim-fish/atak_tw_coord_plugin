package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.address.CountyActiveDataset;
import java.util.Objects;

/** Provenance for the dataset record that produced an address candidate. */
public final class DatasetIdentity {
  private final String county;
  private final String dataDate;
  private final int schemaVersion;
  private final String fileSha256;
  private final String source;

  public DatasetIdentity(
      String county, String dataDate, int schemaVersion, String fileSha256, String source) {
    this.county = Objects.requireNonNull(county, "county");
    this.dataDate = valueOrEmpty(dataDate);
    this.schemaVersion = schemaVersion;
    this.fileSha256 = valueOrEmpty(fileSha256);
    this.source = valueOrEmpty(source);
  }

  public static DatasetIdentity from(CountyActiveDataset active) {
    return new DatasetIdentity(
        active.county(),
        active.dataset().generator().dataDate(),
        active.dataset().generator().schemaVersion(),
        active.dataset().imported().fileSha256(),
        active.dataset().generator().source());
  }

  public String county() {
    return county;
  }

  public String dataDate() {
    return dataDate;
  }

  public int schemaVersion() {
    return schemaVersion;
  }

  public String fileSha256() {
    return fileSha256;
  }

  public String source() {
    return source;
  }

  private static String valueOrEmpty(String value) {
    return value != null ? value : "";
  }
}
