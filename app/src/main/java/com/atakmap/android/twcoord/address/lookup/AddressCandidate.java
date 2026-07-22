package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

/** Immutable address record returned by forward or reverse lookup. */
public final class AddressCandidate {
  private final String candidateId;
  private final String displayAddress;
  private final String normalizedAddress;
  private final Wgs84 recordPoint;
  private final AddressMatchKind matchKind;
  private final double distanceMeters;
  private final String county;
  private final DatasetIdentity datasetIdentity;

  public AddressCandidate(
      String candidateId,
      String displayAddress,
      String normalizedAddress,
      Wgs84 recordPoint,
      AddressMatchKind matchKind,
      double distanceMeters,
      String county,
      DatasetIdentity datasetIdentity) {
    this.candidateId = Objects.requireNonNull(candidateId, "candidateId");
    this.displayAddress = Objects.requireNonNull(displayAddress, "displayAddress");
    this.normalizedAddress = Objects.requireNonNull(normalizedAddress, "normalizedAddress");
    this.recordPoint = Objects.requireNonNull(recordPoint, "recordPoint");
    this.matchKind = Objects.requireNonNull(matchKind, "matchKind");
    this.distanceMeters = distanceMeters;
    this.county = Objects.requireNonNull(county, "county");
    this.datasetIdentity = Objects.requireNonNull(datasetIdentity, "datasetIdentity");
  }

  public String candidateId() {
    return candidateId;
  }

  public String displayAddress() {
    return displayAddress;
  }

  public String normalizedAddress() {
    return normalizedAddress;
  }

  public Wgs84 recordPoint() {
    return recordPoint;
  }

  public AddressMatchKind matchKind() {
    return matchKind;
  }

  public double distanceMeters() {
    return distanceMeters;
  }

  public String county() {
    return county;
  }

  public DatasetIdentity datasetIdentity() {
    return datasetIdentity;
  }
}
