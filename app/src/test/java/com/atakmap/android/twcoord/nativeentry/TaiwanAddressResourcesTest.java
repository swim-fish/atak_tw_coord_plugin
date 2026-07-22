package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.view.View;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.plugin.R;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class TaiwanAddressResourcesTest {

  private static final int[] ADDRESS_STRING_IDS = {
    R.string.native_entry_system_address,
    R.string.native_entry_address_mode_full,
    R.string.native_entry_address_mode_structured,
    R.string.native_entry_address_full_label,
    R.string.native_entry_address_county_label,
    R.string.native_entry_address_district_label,
    R.string.native_entry_address_road_label,
    R.string.native_entry_address_tail_label,
    R.string.native_entry_address_choose_result,
    R.string.native_entry_address_manage_data,
    R.string.native_entry_address_no_dataset,
    R.string.native_entry_a11y_address_full,
    R.string.native_entry_a11y_address_county,
    R.string.native_entry_a11y_address_district,
    R.string.native_entry_a11y_address_road,
    R.string.native_entry_a11y_address_tail,
    R.string.native_entry_a11y_address_mode,
    R.string.native_entry_a11y_address_choose
  };

  private static final int[] ACCESSIBLE_CONTROL_IDS = {
    R.id.native_entry_system_group,
    R.id.native_entry_address_full,
    R.id.native_entry_address_county,
    R.id.native_entry_address_district,
    R.id.native_entry_address_road,
    R.id.native_entry_address_tail,
    R.id.native_entry_address_mode,
    R.id.native_entry_address_choose
  };

  @Test
  public void addressStringsResolveForEnglishTraditionalChineseAndJapanese() {
    Context base = RuntimeEnvironment.getApplication();

    for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.TAIWAN, Locale.JAPANESE}) {
      Context localized = localized(base, locale);
      for (int id : ADDRESS_STRING_IDS) {
        assertThat(localized.getString(id))
            .as("resource %s for %s", localized.getResources().getResourceEntryName(id), locale)
            .isNotBlank();
      }
    }
  }

  @Test
  public void everyAddressInputAndActionHasAnAccessibleName() {
    Context context = localized(RuntimeEnvironment.getApplication(), Locale.TAIWAN);
    TaiwanCoordinateEntryPane pane =
        new TaiwanCoordinateEntryPane(
            context,
            new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {}),
            new TaiwanEntryFormatter());
    View root = pane.getView();
    root.findViewById(R.id.native_entry_system_address).performClick();

    for (int id : ACCESSIBLE_CONTROL_IDS) {
      View control = root.findViewById(id);
      assertThat(control).as("control %s", id).isNotNull();
      assertThat(control.getContentDescription())
          .as("content description for %s", context.getResources().getResourceEntryName(id))
          .isNotNull();
      assertThat(control.getContentDescription().toString()).isNotBlank();
    }
  }

  private static Context localized(Context base, Locale locale) {
    Configuration configuration = new Configuration(base.getResources().getConfiguration());
    configuration.setLocales(new LocaleList(locale));
    return base.createConfigurationContext(configuration);
  }
}
