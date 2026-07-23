package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

/** Immutable reverse-address lookup request that retains the exact host point. */
public final class ReverseAddressRequest {
  private final LookupIdentity identity;
  private final String consumerKey;
  private final LookupPriority priority;
  private final Wgs84 queryPoint;
  private final double radiusMeters;

  public ReverseAddressRequest(
      LookupIdentity identity,
      String consumerKey,
      LookupPriority priority,
      Wgs84 queryPoint,
      double radiusMeters) {
    if (!(radiusMeters > 0.0)) {
      throw new IllegalArgumentException("radiusMeters must be positive");
    }
    this.identity = Objects.requireNonNull(identity, "identity");
    this.consumerKey = Objects.requireNonNull(consumerKey, "consumerKey");
    this.priority = Objects.requireNonNull(priority, "priority");
    this.queryPoint = Objects.requireNonNull(queryPoint, "queryPoint");
    this.radiusMeters = radiusMeters;
  }

  public LookupIdentity identity() {
    return identity;
  }

  public String consumerKey() {
    return consumerKey;
  }

  public LookupPriority priority() {
    return priority;
  }

  public Wgs84 queryPoint() {
    return queryPoint;
  }

  public double radiusMeters() {
    return radiusMeters;
  }
}
