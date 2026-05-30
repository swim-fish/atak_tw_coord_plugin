package com.atakmap.android.twcoord.address;

/**
 * Broadcast action constants for feature 006's forward-search page. Sibling of {@link
 * OfflineAddressIntents} and {@code TwCoordGotoIntents}.
 */
public final class ForwardSearchIntents {

  /** Sent by {@code ForwardSearchTool} (Tools-menu tap); opens the forward-search DropDown. */
  public static final String ACTION_SHOW_FORWARD_SEARCH =
      "com.atakmap.android.twcoord.SHOW_FORWARD_SEARCH";

  private ForwardSearchIntents() {}
}
