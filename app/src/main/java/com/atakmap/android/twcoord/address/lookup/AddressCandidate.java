package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

/** Immutable address record returned by forward or reverse lookup. */
public final class AddressCandidate {
  private final String candidateId;
  private final String displayAddress;
  private final String displayAddressHalfwidth;
  private final String normalizedAddress;
  private final Wgs84 recordPoint;
  private final AddressMatchKind matchKind;
  private final double distanceMeters;
  private final String county;
  private final DatasetIdentity datasetIdentity;
  private final String street;
  private final String number;

  public AddressCandidate(
      String candidateId,
      String displayAddress,
      String normalizedAddress,
      Wgs84 recordPoint,
      AddressMatchKind matchKind,
      double distanceMeters,
      String county,
      DatasetIdentity datasetIdentity) {
    this(
        candidateId,
        displayAddress,
        "",
        normalizedAddress,
        recordPoint,
        matchKind,
        distanceMeters,
        county,
        datasetIdentity,
        "",
        "");
  }

  /** Compatibility constructor for existing forward-search facade/ranker callers. */
  public AddressCandidate(
      double lat,
      double lon,
      String displayName,
      String displayNameHalfwidth,
      String street,
      String number,
      double distanceMeters) {
    this(
        lat + ":" + lon + ":" + valueOrEmpty(displayName),
        valueOrEmpty(displayName),
        valueOrEmpty(displayNameHalfwidth),
        StreetTextNormaliser.fold(displayName),
        new Wgs84(lat, lon, 1L, Wgs84.Source.COT_TARGET),
        AddressMatchKind.PARTIAL,
        distanceMeters,
        "",
        null,
        valueOrEmpty(street),
        valueOrEmpty(number));
  }

  private AddressCandidate(
      String candidateId,
      String displayAddress,
      String displayAddressHalfwidth,
      String normalizedAddress,
      Wgs84 recordPoint,
      AddressMatchKind matchKind,
      double distanceMeters,
      String county,
      DatasetIdentity datasetIdentity,
      String street,
      String number) {
    this.candidateId = Objects.requireNonNull(candidateId, "candidateId");
    this.displayAddress = Objects.requireNonNull(displayAddress, "displayAddress");
    this.displayAddressHalfwidth = valueOrEmpty(displayAddressHalfwidth);
    this.normalizedAddress = Objects.requireNonNull(normalizedAddress, "normalizedAddress");
    this.recordPoint = Objects.requireNonNull(recordPoint, "recordPoint");
    this.matchKind = Objects.requireNonNull(matchKind, "matchKind");
    this.distanceMeters = distanceMeters;
    this.county = Objects.requireNonNull(county, "county");
    this.datasetIdentity = datasetIdentity;
    this.street = valueOrEmpty(street);
    this.number = valueOrEmpty(number);
  }

  public String candidateId() {
    return candidateId;
  }

  public String displayAddress() {
    return displayAddress;
  }

  public String displayName() {
    return displayAddress;
  }

  public String displayNameHalfwidth() {
    return displayAddressHalfwidth;
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

  public double lat() {
    return recordPoint.latitudeDeg();
  }

  public double lon() {
    return recordPoint.longitudeDeg();
  }

  public String street() {
    return street;
  }

  public String number() {
    return number;
  }

  public AddressCandidate withLookupData(
      String stableId,
      String normalized,
      AddressMatchKind kind,
      String datasetCounty,
      DatasetIdentity provenance) {
    return new AddressCandidate(
        stableId,
        displayAddress,
        displayAddressHalfwidth,
        normalized,
        recordPoint,
        kind,
        distanceMeters,
        datasetCounty,
        Objects.requireNonNull(provenance, "provenance"),
        street,
        number);
  }

  public AddressCandidate withMatch(String normalized, AddressMatchKind kind) {
    return new AddressCandidate(
        candidateId,
        displayAddress,
        displayAddressHalfwidth,
        normalized,
        recordPoint,
        kind,
        distanceMeters,
        county,
        datasetIdentity,
        street,
        number);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof AddressCandidate)) return false;
    AddressCandidate that = (AddressCandidate) other;
    return Double.compare(that.lat(), lat()) == 0
        && Double.compare(that.lon(), lon()) == 0
        && displayAddress.equals(that.displayAddress)
        && street.equals(that.street)
        && number.equals(that.number);
  }

  @Override
  public int hashCode() {
    return Objects.hash(lat(), lon(), displayAddress, street, number);
  }

  @Override
  public String toString() {
    return "AddressCandidate{"
        + displayAddress
        + " @"
        + lat()
        + ","
        + lon()
        + " "
        + distanceMeters
        + "m}";
  }

  private static String valueOrEmpty(String value) {
    return value != null ? value : "";
  }
}
