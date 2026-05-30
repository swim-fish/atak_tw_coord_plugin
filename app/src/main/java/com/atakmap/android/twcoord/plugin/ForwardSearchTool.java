package com.atakmap.android.twcoord.plugin;

import android.content.Context;
import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.android.twcoord.address.ForwardSearchIntents;
import com.atakmap.coremap.log.Log;
import gov.tak.api.util.Disposable;

/**
 * Fourth Tools-menu entry (feature 006) — the county-scoped forward-search page. Mirrors {@link
 * OfflineAddressTool} / {@link TwCoordGotoTool}: a thin {@link AbstractPluginTool} that fires
 * {@link ForwardSearchIntents#ACTION_SHOW_FORWARD_SEARCH} when tapped. The receiver (registered in
 * {@code TwCoordMapComponent}) opens the drop-down.
 */
public class ForwardSearchTool extends AbstractPluginTool implements Disposable {

  private static final String TAG = "ForwardSearchTool";

  public ForwardSearchTool(Context context) {
    super(
        context,
        context.getString(R.string.tool_forward_search_label),
        context.getString(R.string.tool_forward_search_desc),
        context.getResources().getDrawable(R.drawable.ic_forward_search),
        ForwardSearchIntents.ACTION_SHOW_FORWARD_SEARCH);
  }

  @Override
  public void dispose() {
    try {
      // no owned state
    } catch (Throwable t) {
      Log.w(TAG, "dispose threw", t);
    }
  }
}
