package com.atakmap.android.twcoord;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.twcoord.address.AddressRowState;
import com.atakmap.android.twcoord.coord.DisplayLine;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;
import com.atakmap.coremap.log.Log;

/**
 * Three on-map readouts sitting alongside ATAK's native widgets:
 *
 * <ul>
 *   <li>MAP coordinate → BOTTOM-LEFT (next to ATAK's Eye Alt readout)
 *   <li>ME coordinate → BOTTOM-RIGHT (next to the self-callsign card)
 *   <li>CoT target coordinate → TOP-RIGHT (next to the cursor-on-target callout). Hidden until the
 *       user taps a target.
 * </ul>
 *
 * <p>Styling mirrors the ATAK SDK's own EyeAlt widget exactly (reverse-engineered from {@code
 * com.atakmap.android.navigation.widgets.NavWidgetsMapComponent} — see ADR-0007): shared {@link
 * MapView#getTextFormat(Typeface, int)} bold font at "default size minus 2", background style
 * {@code 2}, margin 16 dp on the outside edges and 0 on the inside.
 */
public final class TwCoordWidget {

  private static final String TAG = "TwCoordWidget";

  /**
   * Mirror EyeAlt construction (NavWidgetsMapComponent decompile, ADR-0007): {@code new
   * TextWidget("", 2)} = text + size-offset 2 above default, default font, default background. By
   * passing the offset only and NOT setting our own {@link com.atakmap.android.maps.MapTextFormat}
   * or background, we automatically inherit any future SDK change to its default styling.
   */
  private static final int TEXT_SIZE_OFFSET = 2;

  // Per-state colour applied via setColor(int). EyeAlt itself doesn't call setColor and shows
  // white text; we keep white for OK and reuse ATAK's amber/red palette for the warning
  // states so the widget reads at a glance.
  private static final int COLOR_OK = 0xFFFFFFFF;
  private static final int COLOR_OUT_OF_RANGE = 0xFFFFB300;
  private static final int COLOR_NO_FIX = 0xFFFF5555;
  private static final int COLOR_NO_PERMISSION = 0xFFFF5555;

  /** Muted neutral so the address row reads as secondary to the coord row above. */
  private static final int COLOR_ADDRESS = 0xFFBBBBBB;

  private static final float EDGE = 16f;

  private final MapView mapView;

  private LinearLayoutWidget mapAnchor;
  private LinearLayoutWidget meAnchor;
  private LinearLayoutWidget targetAnchor;
  private TextWidget mapRow;
  private TextWidget meRow;
  private TextWidget targetRow;

  // Feature 004 — sibling address row per coord row.
  private TextWidget mapAddrRow;
  private TextWidget meAddrRow;
  private TextWidget targetAddrRow;
  private AddressRowState lastMapAddr = AddressRowState.hidden();
  private AddressRowState lastMeAddr = AddressRowState.hidden();
  private AddressRowState lastTargetAddr = AddressRowState.hidden();
  // Localised "Loading address…" / "No address nearby" texts; set by TwCoordMapComponent via
  // setAddressStrings(...) so language changes propagate without coupling the widget to a
  // Context.
  private String addressLoadingText = "Loading address…";
  private String addressEmptyText = "No address nearby";

  private DisplayLine lastMap;
  private DisplayLine lastMe;
  private DisplayLine lastTarget;

  public TwCoordWidget(MapView mapView) {
    this.mapView = mapView;
  }

  public void attach() {
    RootLayoutWidget root = (RootLayoutWidget) mapView.getComponentExtra("rootLayoutWidget");
    mapAnchor = root.getLayout(RootLayoutWidget.BOTTOM_LEFT);
    meAnchor = root.getLayout(RootLayoutWidget.BOTTOM_RIGHT);
    targetAnchor = root.getLayout(RootLayoutWidget.TOP_RIGHT);

    // Margins follow EyeAlt's convention: 16 dp on the outside edges, 0 on the inside
    // (where ATAK natives sit). Mirror the left/right for the bottom-right corner.
    mapRow = newStyledTextWidget("MAP —", EDGE, EDGE, 0f, EDGE);
    meRow = newStyledTextWidget("ME —", 0f, EDGE, EDGE, EDGE);
    targetRow = newStyledTextWidget("", 0f, EDGE, EDGE, EDGE);

    mapAnchor.addWidget(mapRow);
    meAnchor.addWidget(meRow);
    targetAnchor.addWidget(targetRow);

    // Feature 004 — sibling address rows, hidden until the operator opts in via Settings.
    // Same horizontal margins as the parent coord row; smaller bottom margin so the address
    // text sits close to the coord row it annotates.
    mapAddrRow = newStyledTextWidget("", EDGE, 0f, 0f, EDGE);
    meAddrRow = newStyledTextWidget("", 0f, 0f, EDGE, EDGE);
    targetAddrRow = newStyledTextWidget("", 0f, 0f, EDGE, EDGE);
    mapAddrRow.setColor(COLOR_ADDRESS);
    meAddrRow.setColor(COLOR_ADDRESS);
    targetAddrRow.setColor(COLOR_ADDRESS);
    mapAddrRow.setVisible(false);
    meAddrRow.setVisible(false);
    targetAddrRow.setVisible(false);
    mapAnchor.addWidget(mapAddrRow);
    meAnchor.addWidget(meAddrRow);
    targetAnchor.addWidget(targetAddrRow);
  }

  /**
   * Replace the "Loading address…" / "No address nearby" texts used by the address rows. Called by
   * {@link TwCoordMapComponent} at attach time and on every UI-language change.
   */
  public void setAddressStrings(String loading, String empty) {
    if (loading != null) addressLoadingText = loading;
    if (empty != null) addressEmptyText = empty;
    // If the rows are currently showing one of the fallback strings, repaint with the new
    // localised value.
    if (lastMapAddr.isLoading() && mapAddrRow != null) mapAddrRow.setText(addressLoadingText);
    if (lastMapAddr.isEmptyState() && mapAddrRow != null) mapAddrRow.setText(addressEmptyText);
    if (lastMeAddr.isLoading() && meAddrRow != null) meAddrRow.setText(addressLoadingText);
    if (lastMeAddr.isEmptyState() && meAddrRow != null) meAddrRow.setText(addressEmptyText);
    if (lastTargetAddr.isLoading() && targetAddrRow != null)
      targetAddrRow.setText(addressLoadingText);
    if (lastTargetAddr.isEmptyState() && targetAddrRow != null)
      targetAddrRow.setText(addressEmptyText);
  }

  private static TextWidget newStyledTextWidget(
      String initial, float left, float top, float right, float bottom) {
    // Constructor (String, int) — the int is size offset, font is Typeface.DEFAULT, the
    // background is TextWidget's built-in default. This is the EXACT call EyeAlt makes.
    TextWidget tw = new TextWidget(initial, TEXT_SIZE_OFFSET);
    tw.setMargins(left, top, right, bottom);
    return tw;
  }

  public void detach() {
    if (mapAnchor != null && mapRow != null) mapAnchor.removeWidget(mapRow);
    if (meAnchor != null && meRow != null) meAnchor.removeWidget(meRow);
    if (targetAnchor != null && targetRow != null) targetAnchor.removeWidget(targetRow);
    if (mapAnchor != null && mapAddrRow != null) mapAnchor.removeWidget(mapAddrRow);
    if (meAnchor != null && meAddrRow != null) meAnchor.removeWidget(meAddrRow);
    if (targetAnchor != null && targetAddrRow != null) targetAnchor.removeWidget(targetAddrRow);
    mapAnchor = null;
    meAnchor = null;
    targetAnchor = null;
    mapRow = null;
    meRow = null;
    targetRow = null;
    mapAddrRow = null;
    meAddrRow = null;
    targetAddrRow = null;
    lastMap = null;
    lastMe = null;
    lastTarget = null;
    lastMapAddr = AddressRowState.hidden();
    lastMeAddr = AddressRowState.hidden();
    lastTargetAddr = AddressRowState.hidden();
  }

  /** Toggle all three rows on/off. Returns the new visibility state (true = visible). */
  public boolean toggleVisibility() {
    boolean newVisible = mapRow == null || !mapRow.isVisible();
    setVisible(newVisible);
    return newVisible;
  }

  public boolean isVisible() {
    return mapRow != null && mapRow.isVisible();
  }

  public void setVisible(boolean visible) {
    if (mapRow != null) mapRow.setVisible(visible);
    if (meRow != null) meRow.setVisible(visible);
    if (targetRow != null) targetRow.setVisible(visible);
    if (visible) {
      // Restore each address row's visibility from its last known state (Hidden stays
      // hidden; Text / Loading / EmptyState become visible again).
      if (mapAddrRow != null) mapAddrRow.setVisible(addressVisibleFor(lastMapAddr));
      if (meAddrRow != null) meAddrRow.setVisible(addressVisibleFor(lastMeAddr));
      if (targetAddrRow != null) targetAddrRow.setVisible(addressVisibleFor(lastTargetAddr));
    } else {
      if (mapAddrRow != null) mapAddrRow.setVisible(false);
      if (meAddrRow != null) meAddrRow.setVisible(false);
      if (targetAddrRow != null) targetAddrRow.setVisible(false);
    }
  }

  public void render(DisplayLine mapCentreLine, DisplayLine selfLine, DisplayLine targetLine) {
    if (mapRow != null && mapCentreLine != null && !equalsNullable(mapCentreLine, lastMap)) {
      paint(mapRow, mapCentreLine);
      lastMap = mapCentreLine;
    }
    if (meRow != null && selfLine != null && !equalsNullable(selfLine, lastMe)) {
      paint(meRow, selfLine);
      lastMe = selfLine;
    }
    if (targetRow != null && !equalsNullable(targetLine, lastTarget)) {
      paint(targetRow, targetLine);
      lastTarget = targetLine;
    }
  }

  private static void paint(TextWidget row, DisplayLine line) {
    if (line == null) {
      row.setText("");
      return;
    }
    switch (line.state()) {
      case OK:
        row.setText(line.labelPrefix() + " " + line.unitTag() + ": " + line.value());
        row.setColor(COLOR_OK);
        break;
      case OUT_OF_RANGE:
        row.setText(
            line.labelPrefix()
                + " "
                + line.unitTag()
                + ": "
                + line.value()
                + "\n("
                + line.fallback()
                + ")");
        row.setColor(COLOR_OUT_OF_RANGE);
        break;
      case NO_FIX:
        row.setText(line.labelPrefix() + ": " + line.value());
        row.setColor(COLOR_NO_FIX);
        break;
      case NO_PERMISSION:
        row.setText(line.labelPrefix() + ": " + line.value());
        row.setColor(COLOR_NO_PERMISSION);
        break;
      default:
        row.setText("");
    }
  }

  private static boolean equalsNullable(DisplayLine a, DisplayLine b) {
    if (a == null) return b == null;
    return a.equals(b);
  }

  // ----------------------------------------------------------------------
  // Feature 004 — Address row rendering
  // ----------------------------------------------------------------------

  /**
   * Update the three per-row address states. Any {@code null} argument is treated as {@link
   * AddressRowState.Hidden}. Skips per-row updates when the input equals the last-rendered state
   * (coalesce-on-equal, matching {@link #render}).
   */
  public void renderAddresses(
      AddressRowState mapState, AddressRowState meState, AddressRowState targetState) {
    try {
      AddressRowState m = mapState != null ? mapState : AddressRowState.hidden();
      AddressRowState e = meState != null ? meState : AddressRowState.hidden();
      AddressRowState t = targetState != null ? targetState : AddressRowState.hidden();
      if (mapAddrRow != null && !m.equals(lastMapAddr)) {
        paintAddressRow(mapAddrRow, m);
        lastMapAddr = m;
      }
      if (meAddrRow != null && !e.equals(lastMeAddr)) {
        paintAddressRow(meAddrRow, e);
        lastMeAddr = e;
      }
      if (targetAddrRow != null && !t.equals(lastTargetAddr)) {
        paintAddressRow(targetAddrRow, t);
        lastTargetAddr = t;
      }
    } catch (Throwable thr) {
      // Constitution VI: widget rendering must never propagate up into the host process.
      Log.w(TAG, "renderAddresses threw", thr);
    }
  }

  private void paintAddressRow(TextWidget row, AddressRowState state) {
    String text = addressTextFor(state, addressLoadingText, addressEmptyText);
    if (!state.isHidden()) {
      row.setText(text);
    }
    row.setVisible(addressVisibleFor(state));
  }

  // ----------------------------------------------------------------------
  // Pure helpers exposed for testing
  // ----------------------------------------------------------------------

  /**
   * Return the text the row should display for {@code state}. Used internally by {@link
   * #paintAddressRow} and exposed package-private for unit-test coverage of the state→text mapping
   * (the integration with anchors is verified separately by Espresso).
   */
  static String addressTextFor(
      AddressRowState state, String loadingFallback, String emptyFallback) {
    if (state == null) return "";
    if (state instanceof AddressRowState.Text) return ((AddressRowState.Text) state).value();
    if (state.isLoading()) return loadingFallback == null ? "" : loadingFallback;
    if (state.isEmptyState()) return emptyFallback == null ? "" : emptyFallback;
    return ""; // Hidden — caller should not call setText.
  }

  /** Return whether the row should be visible for {@code state}. */
  static boolean addressVisibleFor(AddressRowState state) {
    return state != null && !state.isHidden();
  }
}
