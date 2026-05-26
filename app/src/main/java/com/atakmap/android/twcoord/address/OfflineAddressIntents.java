package com.atakmap.android.twcoord.address;

/**
 * Broadcast action constants used by the Offline Address subsystem. Parallel to {@code
 * com.atakmap.android.twcoord.gotopage.TwCoordGotoIntents}.
 *
 * <ul>
 *   <li>{@link #ACTION_SHOW_OFFLINE_ADDRESS} — sent by {@code OfflineAddressTool} (Tools-menu tap)
 *       and by the Settings dataset-status row tap; consumed by {@code OfflineAddressReceiver} to
 *       open the page.
 *   <li>{@link #ACTION_DATASET_CHANGED} — plugin-internal notification fired after any successful
 *       import / remove; consumed by {@code AddressSubsystem} and the Settings fragment to refresh.
 * </ul>
 *
 * <p>File selection is done via ATAK SDK's {@code com.atakmap.android.gui.ImportFileBrowserDialog}
 * (synchronous callback inside the ATAK process), not via a broadcast handoff. See
 * {@code OfflineAddressReceiver#launchPicker} for the call site.
 */
public final class OfflineAddressIntents {

  public static final String ACTION_SHOW_OFFLINE_ADDRESS =
      "com.atakmap.android.twcoord.SHOW_OFFLINE_ADDRESS";

  public static final String ACTION_DATASET_CHANGED =
      "com.atakmap.android.twcoord.OFFLINE_ADDRESS_DATASET_CHANGED";

  private OfflineAddressIntents() {}
}
