package com.atakmap.android.twpower.i18n;

import android.content.Context;
import android.content.res.Configuration;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves the effective UI locale given a user override and the system locale, and produces a
 * {@link Context} wrapper whose resources serve strings in the resolved locale (FR-017, FR-018).
 *
 * <p>Fallback chain (clarification Q2): any {@code zh-*} system locale resolves to Traditional
 * Chinese (Taiwan); any {@code ja-*} resolves to Japanese; everything else falls back to English.
 */
public final class LocaleOverride {

  private LocaleOverride() {}

  /**
   * Pick the locale whose {@code strings.xml} the formatter should read, given the user's override
   * choice and the device's system locale.
   */
  public static Locale resolveForBundle(LanguageOverride override, Locale system) {
    Objects.requireNonNull(override, "override");
    Objects.requireNonNull(system, "system");
    if (override != LanguageOverride.SYSTEM) {
      return override.forcedLocale();
    }
    return mapSystemLocaleToBundle(system);
  }

  /** Apply the fallback chain to a system locale; returns one of en / zh-TW / ja. */
  public static Locale mapSystemLocaleToBundle(Locale system) {
    String lang = system.getLanguage();
    if ("zh".equals(lang)) return Locale.forLanguageTag("zh-TW");
    if ("ja".equals(lang)) return Locale.JAPANESE;
    return Locale.ENGLISH;
  }

  /**
   * Build a {@code Context} whose {@code getResources().getString(...)} returns strings for the
   * resolved bundle locale, without recreating the host activity. The plugin's {@code R.string.*}
   * lookups against this context will fetch from the right values-* folder; the host ATAK
   * activity's locale is untouched.
   */
  public static Context contextFor(Context base, LanguageOverride override, Locale system) {
    Objects.requireNonNull(base, "base");
    Locale target = resolveForBundle(override, system);
    Configuration cfg = new Configuration(base.getResources().getConfiguration());
    cfg.setLocale(target);
    return base.createConfigurationContext(cfg);
  }
}
