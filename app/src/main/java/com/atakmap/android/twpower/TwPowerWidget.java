package com.atakmap.android.twpower;

import android.graphics.Color;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twpower.coord.DisplayLine;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.android.widgets.TextWidget;

/**
 * On-map readout overlay (T034 + T035 + T036). Anchors a small vertical stack in the top-right
 * corner of ATAK's root layout: two text rows (map-centre, own-position), each row optionally
 * carrying a second-line WGS84 fallback when state == OUT_OF_RANGE.
 */
public final class TwPowerWidget {

  private static final int COLOR_OK = Color.WHITE;
  private static final int COLOR_OUT_OF_RANGE = 0xFFFFA000;
  private static final int COLOR_NO_FIX = 0xFFB0B0B0;
  private static final int COLOR_NO_PERMISSION = 0xFFB0B0B0;

  private final MapView mapView;
  private LinearLayoutWidget container;
  private LinearLayoutWidget anchor;
  private TextWidget mapRow;
  private TextWidget meRow;

  private DisplayLine lastMap;
  private DisplayLine lastMe;

  public TwPowerWidget(MapView mapView) {
    this.mapView = mapView;
  }

  /** Attach to the standard ATAK root layout (top-right anchor). */
  public void attach() {
    RootLayoutWidget root = (RootLayoutWidget) mapView.getComponentExtra("rootLayoutWidget");
    anchor = root.getLayout(RootLayoutWidget.TOP_RIGHT);

    container = new LinearLayoutWidget();
    container.setOrientation(LinearLayoutWidget.VERTICAL);

    mapRow = new TextWidget("", TextWidget.TRANSLUCENT_BLACK);
    meRow = new TextWidget("", TextWidget.TRANSLUCENT_BLACK);
    container.addChildWidget(mapRow);
    container.addChildWidget(meRow);

    anchor.addChildWidget(container);
  }

  /** Detach from the anchor and release children (called from MapComponent.onDestroyImpl). */
  public void detach() {
    if (anchor != null && container != null) {
      anchor.removeChildWidget(container);
    }
    container = null;
    mapRow = null;
    meRow = null;
    anchor = null;
    lastMap = null;
    lastMe = null;
  }

  /**
   * Update both rows. No-op if both arguments equal the previous render (cuts redundant invalidate
   * calls in line with contracts/widget-overlay.md). Must be called on the UI thread.
   */
  public void render(DisplayLine mapCentreLine, DisplayLine selfLine) {
    if (mapRow == null || meRow == null) {
      return;
    }
    if (!equalsNullable(mapCentreLine, lastMap)) {
      paint(mapRow, mapCentreLine);
      lastMap = mapCentreLine;
    }
    if (!equalsNullable(selfLine, lastMe)) {
      paint(meRow, selfLine);
      lastMe = selfLine;
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
