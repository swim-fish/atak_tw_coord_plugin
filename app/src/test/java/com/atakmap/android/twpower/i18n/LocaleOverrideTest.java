package com.atakmap.android.twpower.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.Test;

public class LocaleOverrideTest {

  @Test
  public void system_zh_resolves_to_zh_tw() {
    assertResolvesTo(Locale.forLanguageTag("zh"), "zh-TW");
    assertResolvesTo(Locale.forLanguageTag("zh-CN"), "zh-TW");
    assertResolvesTo(Locale.forLanguageTag("zh-TW"), "zh-TW");
    assertResolvesTo(Locale.forLanguageTag("zh-Hant-HK"), "zh-TW");
    assertResolvesTo(Locale.forLanguageTag("zh-Hans-SG"), "zh-TW");
  }

  @Test
  public void system_ja_resolves_to_ja() {
    assertResolvesTo(Locale.JAPANESE, "ja");
    assertResolvesTo(Locale.forLanguageTag("ja-JP"), "ja");
  }

  @Test
  public void any_other_system_locale_falls_back_to_en() {
    assertResolvesTo(Locale.ENGLISH, "en");
    assertResolvesTo(Locale.US, "en");
    assertResolvesTo(Locale.UK, "en");
    assertResolvesTo(Locale.KOREAN, "en");
    assertResolvesTo(Locale.FRANCE, "en");
    assertResolvesTo(Locale.forLanguageTag("ko-KR"), "en");
    assertResolvesTo(Locale.forLanguageTag("fr-FR"), "en");
    assertResolvesTo(Locale.forLanguageTag("de-DE"), "en");
  }

  @Test
  public void explicit_override_wins_over_system_locale() {
    assertThat(LocaleOverride.resolveForBundle(LanguageOverride.EN, Locale.forLanguageTag("zh-TW")))
        .isEqualTo(Locale.ENGLISH);
    assertThat(LocaleOverride.resolveForBundle(LanguageOverride.ZH_TW, Locale.JAPANESE))
        .isEqualTo(Locale.forLanguageTag("zh-TW"));
    assertThat(LocaleOverride.resolveForBundle(LanguageOverride.JA, Locale.ENGLISH))
        .isEqualTo(Locale.JAPANESE);
  }

  @Test
  public void override_system_defers_to_system_locale() {
    assertThat(LocaleOverride.resolveForBundle(LanguageOverride.SYSTEM, Locale.JAPANESE))
        .isEqualTo(Locale.JAPANESE);
    assertThat(LocaleOverride.resolveForBundle(LanguageOverride.SYSTEM, Locale.KOREAN))
        .isEqualTo(Locale.ENGLISH);
  }

  private static void assertResolvesTo(Locale system, String expectedTag) {
    Locale resolved = LocaleOverride.mapSystemLocaleToBundle(system);
    assertThat(resolved.toLanguageTag()).isEqualTo(expectedTag);
  }
}
