package com.atakmap.android.twcoord.address.lookup;

import java.util.Objects;

/** Immutable correlation identity captured when address lookup work is dispatched. */
public final class LookupIdentity {
  private final String requestId;
  private final long sessionGeneration;
  private final long draftRevision;
  private final long datasetRevision;

  public LookupIdentity(
      String requestId, long sessionGeneration, long draftRevision, long datasetRevision) {
    this.requestId = Objects.requireNonNull(requestId, "requestId");
    this.sessionGeneration = sessionGeneration;
    this.draftRevision = draftRevision;
    this.datasetRevision = datasetRevision;
  }

  public String requestId() {
    return requestId;
  }

  public long sessionGeneration() {
    return sessionGeneration;
  }

  public long draftRevision() {
    return draftRevision;
  }

  public long datasetRevision() {
    return datasetRevision;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof LookupIdentity)) return false;
    LookupIdentity that = (LookupIdentity) other;
    return sessionGeneration == that.sessionGeneration
        && draftRevision == that.draftRevision
        && datasetRevision == that.datasetRevision
        && requestId.equals(that.requestId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requestId, sessionGeneration, draftRevision, datasetRevision);
  }
}
