package com.atakmap.android.twcoord.address.lookup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Terminal forward lookup outcome. */
public final class ForwardAddressResult {
  public enum Status {
    NO_DATASET,
    NO_MATCH,
    CANDIDATES,
    FAILURE
  }

  private final LookupIdentity identity;
  private final Status status;
  private final List<AddressCandidate> candidates;
  private final Throwable failure;

  private ForwardAddressResult(
      LookupIdentity identity,
      Status status,
      List<AddressCandidate> candidates,
      Throwable failure) {
    this.identity = Objects.requireNonNull(identity, "identity");
    this.status = Objects.requireNonNull(status, "status");
    this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
    this.failure = failure;
  }

  public static ForwardAddressResult noDataset(LookupIdentity identity) {
    return new ForwardAddressResult(identity, Status.NO_DATASET, Collections.emptyList(), null);
  }

  public static ForwardAddressResult noMatch(LookupIdentity identity) {
    return new ForwardAddressResult(identity, Status.NO_MATCH, Collections.emptyList(), null);
  }

  public static ForwardAddressResult candidates(
      LookupIdentity identity, List<AddressCandidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("candidates must not be empty");
    }
    return new ForwardAddressResult(identity, Status.CANDIDATES, candidates, null);
  }

  public static ForwardAddressResult failure(LookupIdentity identity, Throwable failure) {
    return new ForwardAddressResult(
        identity,
        Status.FAILURE,
        Collections.emptyList(),
        Objects.requireNonNull(failure, "failure"));
  }

  public LookupIdentity identity() {
    return identity;
  }

  public Status status() {
    return status;
  }

  public List<AddressCandidate> candidates() {
    return candidates;
  }

  public Throwable failure() {
    return failure;
  }
}
