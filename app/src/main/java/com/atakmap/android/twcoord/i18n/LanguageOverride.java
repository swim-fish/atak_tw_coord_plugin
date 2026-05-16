package com.atakmap.android.twcoord.i18n;

import java.util.Locale;

public enum LanguageOverride {
  SYSTEM(null),
  EN(Locale.ENGLISH),
  ZH_TW(Locale.forLanguageTag("zh-TW")),
  JA(Locale.JAPANESE);

  private final Locale forced;

  LanguageOverride(Locale forced) {
    this.forced = forced;
  }

  public Locale forcedLocale() {
    return forced;
  }
}
