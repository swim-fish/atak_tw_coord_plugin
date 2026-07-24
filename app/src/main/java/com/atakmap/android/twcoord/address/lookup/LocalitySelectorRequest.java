package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

/** Immutable asynchronous request to prepare one county or district selector snapshot. */
public final class LocalitySelectorRequest {
  private final LookupIdentity identity;
  private final String consumerKey;
  private final LookupPriority priority;
  private final LocalitySelectorSnapshot.Kind kind;
  private final String selectedCounty;
  private final Wgs84 mapAnchor;

  private LocalitySelectorRequest(
      LookupIdentity identity,
      String consumerKey,
      LookupPriority priority,
      LocalitySelectorSnapshot.Kind kind,
      String selectedCounty,
      Wgs84 mapAnchor) {
    this.identity = Objects.requireNonNull(identity, "identity");
    this.consumerKey = Objects.requireNonNull(consumerKey, "consumerKey");
    this.priority = Objects.requireNonNull(priority, "priority");
    this.kind = Objects.requireNonNull(kind, "kind");
    if (kind == LocalitySelectorSnapshot.Kind.DISTRICT
        && (selectedCounty == null || selectedCounty.trim().isEmpty())) {
      throw new IllegalArgumentException("selectedCounty is required for district choices");
    }
    this.selectedCounty = selectedCounty;
    this.mapAnchor = mapAnchor;
  }

  public static LocalitySelectorRequest create(
      LookupIdentity identity,
      String consumerKey,
      LookupPriority priority,
      LocalitySelectorSnapshot.Kind kind,
      String selectedCounty,
      Wgs84 mapAnchor) {
    return new LocalitySelectorRequest(
        identity, consumerKey, priority, kind, selectedCounty, mapAnchor);
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

  public LocalitySelectorSnapshot.Kind kind() {
    return kind;
  }

  public String selectedCounty() {
    return selectedCounty;
  }

  public Wgs84 mapAnchor() {
    return mapAnchor;
  }
}
