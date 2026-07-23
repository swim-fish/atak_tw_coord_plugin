package com.atakmap.android.twcoord;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.twcoord.address.OfflineAddressIntents;

/** Internal navigation intents shared by the Tools entry, settings, and offline-data page. */
final class TwCoordNavigation {

  static final String ACTION_ADVANCED_SETTINGS = "com.atakmap.app.ADVANCED_SETTINGS";
  static final String EXTRA_TOOL_KEY = "toolkey";

  private TwCoordNavigation() {}

  static Intent offlineAddressIntent() {
    return new Intent(OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS);
  }

  static Intent toolDestinationIntent() {
    return offlineAddressIntent();
  }

  static Intent settingsIntent(String preferenceKey) {
    return new Intent(ACTION_ADVANCED_SETTINGS).putExtra(EXTRA_TOOL_KEY, preferenceKey);
  }

  static void openOfflineAddress() {
    AtakBroadcast.getInstance().sendBroadcast(offlineAddressIntent());
  }

  static void openToolDestination() {
    AtakBroadcast.getInstance().sendBroadcast(toolDestinationIntent());
  }

  static void openSettings(String preferenceKey) {
    AtakBroadcast.getInstance().sendBroadcast(settingsIntent(preferenceKey));
  }

  /**
   * Leave ATAK's settings Activity before opening a map-owned DropDown. Posting through the map
   * view lets the finish request leave the current preference click frame first, so the DropDown is
   * not created invisibly behind Settings.
   */
  static void finishThenOpenOfflineAddress(Activity settingsActivity, View mapDispatcher) {
    finishThenPost(settingsActivity, mapDispatcher, TwCoordNavigation::openOfflineAddress);
  }

  static void finishThenPost(Activity settingsActivity, View dispatcher, Runnable destination) {
    if (settingsActivity != null) settingsActivity.finish();
    if (destination == null) return;
    if (dispatcher == null || !dispatcher.post(destination)) {
      destination.run();
    }
  }
}
