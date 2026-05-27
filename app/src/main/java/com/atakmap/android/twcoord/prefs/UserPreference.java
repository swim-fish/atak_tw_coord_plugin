package com.atakmap.android.twcoord.prefs;

import com.atakmap.android.twcoord.address.ConfidenceThresholds;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.i18n.LanguageOverride;
import java.util.Objects;

public final class UserPreference {
  private final CoordinateUnit coordUnit;
  private final LanguageOverride uiLanguage;
  private final long staleFixThresholdMs;
  private final boolean addressRowMe;
  private final boolean addressRowTarget;
  private final boolean addressRowMap;
  private final ConfidenceThresholds confidenceThresholds;

  public UserPreference(
      CoordinateUnit coordUnit,
      LanguageOverride uiLanguage,
      long staleFixThresholdMs,
      boolean addressRowMe,
      boolean addressRowTarget,
      boolean addressRowMap,
      ConfidenceThresholds confidenceThresholds) {
    this.coordUnit = Objects.requireNonNull(coordUnit, "coordUnit");
    this.uiLanguage = Objects.requireNonNull(uiLanguage, "uiLanguage");
    if (staleFixThresholdMs <= 0) {
      throw new IllegalArgumentException("staleFixThresholdMs must be > 0");
    }
    this.staleFixThresholdMs = staleFixThresholdMs;
    this.addressRowMe = addressRowMe;
    this.addressRowTarget = addressRowTarget;
    this.addressRowMap = addressRowMap;
    this.confidenceThresholds =
        Objects.requireNonNull(confidenceThresholds, "confidenceThresholds");
  }

  public CoordinateUnit coordUnit() {
    return coordUnit;
  }

  public LanguageOverride uiLanguage() {
    return uiLanguage;
  }

  public long staleFixThresholdMs() {
    return staleFixThresholdMs;
  }

  public boolean addressRowMe() {
    return addressRowMe;
  }

  public boolean addressRowTarget() {
    return addressRowTarget;
  }

  public boolean addressRowMap() {
    return addressRowMap;
  }

  public ConfidenceThresholds confidenceThresholds() {
    return confidenceThresholds;
  }

  public static UserPreference defaults() {
    return new UserPreference(
        CoordinateUnit.TWD97,
        LanguageOverride.SYSTEM,
        10_000L,
        /* addressRowMe= */ false,
        /* addressRowTarget= */ false,
        /* addressRowMap= */ false,
        ConfidenceThresholds.TIGHT);
  }
}
