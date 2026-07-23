package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

/** Terminal reverse lookup outcome retaining the exact query point. */
public final class ReverseAddressResult {
  public enum Status {
    NO_DATASET,
    NO_MATCH,
    FOUND,
    FAILURE
  }

  private final LookupIdentity identity;
  private final Status status;
  private final Wgs84 queryPoint;
  private final AddressCandidate candidate;
  private final Throwable failure;

  private ReverseAddressResult(
      LookupIdentity identity,
      Status status,
      Wgs84 queryPoint,
      AddressCandidate candidate,
      Throwable failure) {
    this.identity = Objects.requireNonNull(identity, "identity");
    this.status = Objects.requireNonNull(status, "status");
    this.queryPoint = Objects.requireNonNull(queryPoint, "queryPoint");
    this.candidate = candidate;
    this.failure = failure;
  }

  public static ReverseAddressResult noDataset(LookupIdentity identity, Wgs84 queryPoint) {
    return new ReverseAddressResult(identity, Status.NO_DATASET, queryPoint, null, null);
  }

  public static ReverseAddressResult noMatch(LookupIdentity identity, Wgs84 queryPoint) {
    return new ReverseAddressResult(identity, Status.NO_MATCH, queryPoint, null, null);
  }

  public static ReverseAddressResult found(
      LookupIdentity identity, Wgs84 queryPoint, AddressCandidate candidate) {
    return new ReverseAddressResult(
        identity, Status.FOUND, queryPoint, Objects.requireNonNull(candidate, "candidate"), null);
  }

  public static ReverseAddressResult failure(
      LookupIdentity identity, Wgs84 queryPoint, Throwable failure) {
    return new ReverseAddressResult(
        identity, Status.FAILURE, queryPoint, null, Objects.requireNonNull(failure, "failure"));
  }

  public LookupIdentity identity() {
    return identity;
  }

  public Status status() {
    return status;
  }

  public Wgs84 queryPoint() {
    return queryPoint;
  }

  public AddressCandidate candidate() {
    return candidate;
  }

  public Throwable failure() {
    return failure;
  }
}
