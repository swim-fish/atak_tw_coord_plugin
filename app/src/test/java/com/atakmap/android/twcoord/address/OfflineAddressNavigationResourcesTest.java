package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.atakmap.android.twcoord.plugin.R;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class OfflineAddressNavigationResourcesTest {

  @Test
  public void settingsActionIsPresentAtTopInEverySupportedLocale() {
    Context base = RuntimeEnvironment.getApplication();

    for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.TAIWAN, Locale.JAPANESE}) {
      Context localized = localized(base, locale);
      View page = LayoutInflater.from(localized).inflate(R.layout.offline_address_page, null);
      Button settings = page.findViewById(R.id.offline_address_open_settings);
      View title = page.findViewById(R.id.offline_address_title);

      assertThat(settings).as("settings button for %s", locale).isNotNull();
      assertThat(settings.getText().toString())
          .isEqualTo(localized.getString(R.string.offline_address_button_settings))
          .isNotBlank();
      assertThat(settings.isEnabled()).isTrue();
      ViewGroup header = (ViewGroup) settings.getParent();
      assertThat(header.indexOfChild(settings)).isEqualTo(header.indexOfChild(title) + 1);
    }
  }

  private static Context localized(Context base, Locale locale) {
    Configuration configuration = new Configuration(base.getResources().getConfiguration());
    configuration.setLocales(new LocaleList(locale));
    return base.createConfigurationContext(configuration);
  }
}
