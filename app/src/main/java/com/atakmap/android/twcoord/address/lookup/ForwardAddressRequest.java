package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

/** Immutable full-address lookup request. */
public final class ForwardAddressRequest {
  private final LookupIdentity identity;
  private final String consumerKey;
  private final LookupPriority priority;
  private final String normalizedAddress;
  private final Wgs84 anchorPoint;
  private final int limit;

  private ForwardAddressRequest(
      LookupIdentity identity,
      String consumerKey,
      LookupPriority priority,
      String normalizedAddress,
      Wgs84 anchorPoint,
      int limit) {
    if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
    this.identity = Objects.requireNonNull(identity, "identity");
    this.consumerKey = Objects.requireNonNull(consumerKey, "consumerKey");
    this.priority = Objects.requireNonNull(priority, "priority");
    this.normalizedAddress = Objects.requireNonNull(normalizedAddress, "normalizedAddress");
    this.anchorPoint = anchorPoint;
    this.limit = limit;
  }

  public static ForwardAddressRequest create(
      LookupIdentity identity,
      String consumerKey,
      LookupPriority priority,
      String normalizedAddress,
      int limit) {
    return new ForwardAddressRequest(
        identity, consumerKey, priority, normalizedAddress, null, limit);
  }

  public static ForwardAddressRequest create(
      LookupIdentity identity,
      String consumerKey,
      LookupPriority priority,
      String normalizedAddress,
      Wgs84 anchorPoint,
      int limit) {
    return new ForwardAddressRequest(
        identity, consumerKey, priority, normalizedAddress, anchorPoint, limit);
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

  public String normalizedAddress() {
    return normalizedAddress;
  }

  public Wgs84 anchorPoint() {
    return anchorPoint;
  }

  public int limit() {
    return limit;
  }
}
