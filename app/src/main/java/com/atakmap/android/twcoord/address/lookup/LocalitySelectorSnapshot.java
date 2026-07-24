package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.address.boundary.LocalityResult;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable locality choice list captured for one selector opening. */
public final class LocalitySelectorSnapshot {
  public enum Kind {
    COUNTY,
    DISTRICT
  }

  public static final class Choice {
    private final String name;
    private final String postalKey;
    private final boolean promoted;

    Choice(String name, String postalKey, boolean promoted) {
      this.name = Objects.requireNonNull(name, "name");
      this.postalKey = postalKey;
      this.promoted = promoted;
    }

    public String name() {
      return name;
    }

    public String postalKey() {
      return postalKey;
    }

    public boolean promoted() {
      return promoted;
    }
  }

  private final Kind kind;
  private final long datasetRevision;
  private final long createdGeneration;
  private final String selectedCounty;
  private final Wgs84 mapAnchor;
  private final LocalityResult mapLocality;
  private final List<Choice> choices;
  private final boolean postalCatalogAvailable;
  private final int unmatchedPostalCount;

  LocalitySelectorSnapshot(
      Kind kind,
      long datasetRevision,
      long createdGeneration,
      String selectedCounty,
      Wgs84 mapAnchor,
      LocalityResult mapLocality,
      List<Choice> choices,
      boolean postalCatalogAvailable,
      int unmatchedPostalCount) {
    this.kind = Objects.requireNonNull(kind, "kind");
    this.datasetRevision = datasetRevision;
    this.createdGeneration = createdGeneration;
    this.selectedCounty = selectedCounty;
    this.mapAnchor = mapAnchor;
    this.mapLocality = mapLocality;
    this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
    this.postalCatalogAvailable = postalCatalogAvailable;
    this.unmatchedPostalCount = unmatchedPostalCount;
  }

  public Kind kind() {
    return kind;
  }

  public long datasetRevision() {
    return datasetRevision;
  }

  public long createdGeneration() {
    return createdGeneration;
  }

  public String selectedCounty() {
    return selectedCounty;
  }

  public Wgs84 mapAnchor() {
    return mapAnchor;
  }

  public LocalityResult mapLocality() {
    return mapLocality;
  }

  public List<Choice> choices() {
    return choices;
  }

  public boolean postalCatalogAvailable() {
    return postalCatalogAvailable;
  }

  public int unmatchedPostalCount() {
    return unmatchedPostalCount;
  }
}
