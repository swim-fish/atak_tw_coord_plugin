package com.atakmap.android.twcoord.address;

/**
 * Broadcast action constants used by the Offline Address subsystem. Parallel to {@code
 * com.atakmap.android.twcoord.gotopage.TwCoordGotoIntents}.
 *
 * <ul>
 *   <li>{@link #ACTION_SHOW_OFFLINE_ADDRESS} — sent by {@code OfflineAddressTool} (Tools-menu tap)
 *       and by the Settings dataset-status row tap; consumed by {@code OfflineAddressReceiver} to
 *       open the page.
 *   <li>{@link #ACTION_PICK_FILE_RESULT} — sent by {@code OfflineAddressFilePickerActivity} after
 *       the SAF picker returns a URI; consumed by {@code OfflineAddressReceiver} to start the
 *       import. Carries {@link #EXTRA_PICKED_URI} (a {@code content://} {@link android.net.Uri} as
 *       a string).
 *   <li>{@link #ACTION_DATASET_CHANGED} — plugin-internal notification fired after any successful
 *       import / remove; consumed by {@code AddressSubsystem} and the Settings fragment to refresh.
 * </ul>
 */
public final class OfflineAddressIntents {

  public static final String ACTION_SHOW_OFFLINE_ADDRESS =
      "com.atakmap.android.twcoord.SHOW_OFFLINE_ADDRESS";

  public static final String ACTION_PICK_FILE_RESULT =
      "com.atakmap.android.twcoord.OFFLINE_ADDRESS_PICK_FILE_RESULT";

  public static final String EXTRA_PICKED_URI =
      "com.atakmap.android.twcoord.extra.OFFLINE_ADDRESS_PICKED_URI";

  public static final String ACTION_DATASET_CHANGED =
      "com.atakmap.android.twcoord.OFFLINE_ADDRESS_DATASET_CHANGED";

  private OfflineAddressIntents() {}
}
