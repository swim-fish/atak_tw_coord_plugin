package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.twcoord.coord.CoordinateUnit;

/**
 * In-session ephemeral state of the input page. Held by the {@link TwCoordGotoReceiver} across
 * close-and-reopen cycles within the same ATAK process (FR-018). Not persisted to SharedPreferences
 * — cross-session continuity is handled by {@code pref_goto_last_*} keys (FR-003, see {@link
 * com.atakmap.android.twcoord.prefs.PreferenceStore}).
 */
public final class InputPageState {

  private final CoordinateUnit activeTab;
  private final String taipowerDraft;
  private final String twd97EastingDraft;
  private final String twd97NorthingDraft;
  private final int twd97Zone;
  private final String twd67EastingDraft;
  private final String twd67NorthingDraft;
  private final int twd67Zone;

  public InputPageState(
      CoordinateUnit activeTab,
      String taipowerDraft,
      String twd97EastingDraft,
      String twd97NorthingDraft,
      int twd97Zone,
      String twd67EastingDraft,
      String twd67NorthingDraft,
      int twd67Zone) {
    this.activeTab = activeTab;
    this.taipowerDraft = nonNull(taipowerDraft);
    this.twd97EastingDraft = nonNull(twd97EastingDraft);
    this.twd97NorthingDraft = nonNull(twd97NorthingDraft);
    this.twd97Zone = (twd97Zone == 121 || twd97Zone == 119) ? twd97Zone : 121;
    this.twd67EastingDraft = nonNull(twd67EastingDraft);
    this.twd67NorthingDraft = nonNull(twd67NorthingDraft);
    this.twd67Zone = (twd67Zone == 121 || twd67Zone == 119) ? twd67Zone : 121;
  }

  public static InputPageState emptyTaipower() {
    return new InputPageState(CoordinateUnit.TAIPOWER, "", "", "", 121, "", "", 121);
  }

  public CoordinateUnit activeTab() {
    return activeTab;
  }

  public String taipowerDraft() {
    return taipowerDraft;
  }

  public String twd97EastingDraft() {
    return twd97EastingDraft;
  }

  public String twd97NorthingDraft() {
    return twd97NorthingDraft;
  }

  public int twd97Zone() {
    return twd97Zone;
  }

  public String twd67EastingDraft() {
    return twd67EastingDraft;
  }

  public String twd67NorthingDraft() {
    return twd67NorthingDraft;
  }

  public int twd67Zone() {
    return twd67Zone;
  }

  private static String nonNull(String s) {
    return s == null ? "" : s;
  }
}
