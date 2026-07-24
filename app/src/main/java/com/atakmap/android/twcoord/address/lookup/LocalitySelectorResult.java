package com.atakmap.android.twcoord.address.lookup;

import java.util.Objects;

/** Terminal locality selector preparation outcome. */
public final class LocalitySelectorResult {
  public enum Status {
    READY,
    NO_DATASET,
    FAILURE
  }

  private final LookupIdentity identity;
  private final Status status;
  private final LocalitySelectorSnapshot snapshot;
  private final Throwable failure;

  private LocalitySelectorResult(
      LookupIdentity identity,
      Status status,
      LocalitySelectorSnapshot snapshot,
      Throwable failure) {
    this.identity = Objects.requireNonNull(identity, "identity");
    this.status = Objects.requireNonNull(status, "status");
    this.snapshot = snapshot;
    this.failure = failure;
  }

  public static LocalitySelectorResult ready(
      LookupIdentity identity, LocalitySelectorSnapshot snapshot) {
    return new LocalitySelectorResult(
        identity, Status.READY, Objects.requireNonNull(snapshot, "snapshot"), null);
  }

  public static LocalitySelectorResult noDataset(LookupIdentity identity) {
    return new LocalitySelectorResult(identity, Status.NO_DATASET, null, null);
  }

  public static LocalitySelectorResult failure(LookupIdentity identity, Throwable failure) {
    return new LocalitySelectorResult(
        identity, Status.FAILURE, null, Objects.requireNonNull(failure, "failure"));
  }

  public LookupIdentity identity() {
    return identity;
  }

  public Status status() {
    return status;
  }

  public LocalitySelectorSnapshot snapshot() {
    return snapshot;
  }

  public Throwable failure() {
    return failure;
  }
}
