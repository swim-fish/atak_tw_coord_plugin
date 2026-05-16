package com.atakmap.android.twpower.prefs;

import com.atakmap.android.twpower.coord.CoordinateUnit;
import com.atakmap.android.twpower.i18n.LanguageOverride;
import java.util.Objects;

public final class UserPreference {
  private final CoordinateUnit coordUnit;
  private final LanguageOverride uiLanguage;
  private final long staleFixThresholdMs;

  public UserPreference(
      CoordinateUnit coordUnit, LanguageOverride uiLanguage, long staleFixThresholdMs) {
    this.coordUnit = Objects.requireNonNull(coordUnit, "coordUnit");
    this.uiLanguage = Objects.requireNonNull(uiLanguage, "uiLanguage");
    if (staleFixThresholdMs <= 0) {
      throw new IllegalArgumentException("staleFixThresholdMs must be > 0");
    }
    this.staleFixThresholdMs = staleFixThresholdMs;
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

  public static UserPreference defaults() {
    return new UserPreference(CoordinateUnit.TWD97, LanguageOverride.SYSTEM, 10_000L);
  }
}
