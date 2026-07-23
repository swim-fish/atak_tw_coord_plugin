package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

/** Accepted address resolution, separated from transient candidates. */
public final class AddressResolution {
  public enum Source {
    UNIQUE_EXACT,
    OPERATOR_SELECTED,
    REVERSE_LABEL
  }

  private final String displayAddress;
  private final String normalizedAddress;
  private final Wgs84 resolvedPoint;
  private final Wgs84 recordPoint;
  private final Source source;
  private final DatasetIdentity datasetIdentity;
  private final LookupIdentity identity;

  public AddressResolution(
      String displayAddress,
      String normalizedAddress,
      Wgs84 resolvedPoint,
      Wgs84 recordPoint,
      Source source,
      DatasetIdentity datasetIdentity,
      LookupIdentity identity) {
    this.displayAddress = Objects.requireNonNull(displayAddress, "displayAddress");
    this.normalizedAddress = Objects.requireNonNull(normalizedAddress, "normalizedAddress");
    this.resolvedPoint = Objects.requireNonNull(resolvedPoint, "resolvedPoint");
    this.recordPoint = Objects.requireNonNull(recordPoint, "recordPoint");
    this.source = Objects.requireNonNull(source, "source");
    this.datasetIdentity = Objects.requireNonNull(datasetIdentity, "datasetIdentity");
    this.identity = Objects.requireNonNull(identity, "identity");
  }

  public String displayAddress() {
    return displayAddress;
  }

  public String normalizedAddress() {
    return normalizedAddress;
  }

  public Wgs84 resolvedPoint() {
    return resolvedPoint;
  }

  public Wgs84 recordPoint() {
    return recordPoint;
  }

  public Source source() {
    return source;
  }

  public DatasetIdentity datasetIdentity() {
    return datasetIdentity;
  }

  public LookupIdentity identity() {
    return identity;
  }
}
