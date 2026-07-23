package com.atakmap.android.twcoord;

import static org.assertj.core.api.Assertions.assertThat;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import com.atakmap.android.twcoord.address.OfflineAddressIntents;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class TwCoordNavigationTest {

  @Test
  public void toolsDestinationOpensOfflineAddressData() {
    Intent destination = TwCoordNavigation.toolDestinationIntent();

    assertThat(destination.getAction())
        .isEqualTo(OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS);
  }

  @Test
  public void offlinePageSettingsDestinationTargetsPluginSettings() {
    Intent destination = TwCoordNavigation.settingsIntent("tw_coord_settings");

    assertThat(destination.getAction()).isEqualTo(TwCoordNavigation.ACTION_ADVANCED_SETTINGS);
    assertThat(destination.getStringExtra(TwCoordNavigation.EXTRA_TOOL_KEY))
        .isEqualTo("tw_coord_settings");
  }

  @Test
  public void settingsActivityFinishesBeforeOfflineDestinationIsPosted() {
    Activity settings = Robolectric.buildActivity(Activity.class).setup().get();
    AtomicBoolean destinationOpened = new AtomicBoolean();
    AtomicReference<Runnable> posted = new AtomicReference<>();
    View dispatcher =
        new View(settings) {
          @Override
          public boolean post(Runnable action) {
            posted.set(action);
            return true;
          }
        };

    TwCoordNavigation.finishThenPost(
        settings, dispatcher, () -> destinationOpened.set(settings.isFinishing()));

    assertThat(settings.isFinishing()).isTrue();
    assertThat(destinationOpened).isFalse();
    assertThat(posted.get()).isNotNull();

    posted.get().run();

    assertThat(destinationOpened).isTrue();
  }
}
