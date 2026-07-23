package com.atakmap.android.twcoord.address.lookup;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable snapshot of currently usable offline address data. */
public final class AddressAvailability {
  private final Set<String> counties;
  private final boolean boundaryAvailable;
  private final long datasetRevision;
  private final boolean closed;

  public AddressAvailability(
      Set<String> counties, boolean boundaryAvailable, long datasetRevision, boolean closed) {
    this.counties =
        Collections.unmodifiableSet(
            new LinkedHashSet<>(counties != null ? counties : Collections.emptySet()));
    this.boundaryAvailable = boundaryAvailable;
    this.datasetRevision = datasetRevision;
    this.closed = closed;
  }

  public Set<String> counties() {
    return counties;
  }

  public boolean boundaryAvailable() {
    return boundaryAvailable;
  }

  public long datasetRevision() {
    return datasetRevision;
  }

  public boolean closed() {
    return closed;
  }
}
