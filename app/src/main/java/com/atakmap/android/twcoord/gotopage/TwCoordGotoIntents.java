package com.atakmap.android.twcoord.gotopage;

/**
 * Intent action constants for feature 002's GoTo input page. Sibling of feature 001's {@code
 * com.atakmap.android.twcoord.SHOW_PLUGIN} action which lives in {@link
 * com.atakmap.android.twcoord.TwCoordMapComponent#ACTION_SHOW_PLUGIN}.
 */
public final class TwCoordGotoIntents {

  private TwCoordGotoIntents() {}

  /**
   * Inbound — tap on the second Tools-menu icon, or the "Open Coordinate Input" button on the
   * settings page. Opens the input page DropDown. Optional extras: {@code unit} string in {@code
   * TAIPOWER / TWD97 / TWD67} to force the active tab.
   */
  public static final String ACTION_SHOW_GOTO = "com.atakmap.android.twcoord.SHOW_GOTO";

  /**
   * Outbound — fired after a successful submit so downstream observers can react. Extras: {@code
   * lat} (double), {@code lon} (double), {@code unit} (string), {@code rawValue} (string).
   */
  public static final String ACTION_GOTO_NAV_COMPLETED =
      "com.atakmap.android.twcoord.GOTO_NAV_COMPLETED";

  public static final String EXTRA_UNIT = "unit";
  public static final String EXTRA_LAT = "lat";
  public static final String EXTRA_LON = "lon";
  public static final String EXTRA_RAW_VALUE = "rawValue";
}
