package com.atakmap.android.twpower;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.twpower.coord.DisplayLine;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;

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
public final class TwPowerWidget {

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

  private static final float EDGE = 16f;

  private final MapView mapView;

  private LinearLayoutWidget mapAnchor;
  private LinearLayoutWidget meAnchor;
  private LinearLayoutWidget targetAnchor;
  private TextWidget mapRow;
  private TextWidget meRow;
  private TextWidget targetRow;

  private DisplayLine lastMap;
  private DisplayLine lastMe;
  private DisplayLine lastTarget;

  public TwPowerWidget(MapView mapView) {
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
    mapAnchor = null;
    meAnchor = null;
    targetAnchor = null;
    mapRow = null;
    meRow = null;
    targetRow = null;
    lastMap = null;
    lastMe = null;
    lastTarget = null;
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
}
