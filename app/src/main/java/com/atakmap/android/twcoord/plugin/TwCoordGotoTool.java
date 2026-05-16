package com.atakmap.android.twcoord.plugin;

import android.content.Context;
import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.android.twcoord.gotopage.TwCoordGotoIntents;
import gov.tak.api.util.Disposable;

/**
 * Second Tools-menu icon (feature 002 — TW Coord GoTo input page). Sibling of {@link TwCoordTool}.
 *
 * <p>Tools icons are registered programmatically via {@code AbstractPluginTool} subclasses; both
 * tools are passed into {@link com.atak.plugins.impl.AbstractPlugin}'s {@code (IServiceController,
 * IToolbarItem[], MapComponent)} constructor by {@link TwCoordLifecycle}.
 */
public class TwCoordGotoTool extends AbstractPluginTool implements Disposable {

  public TwCoordGotoTool(Context context) {
    super(
        context,
        context.getString(R.string.app_name_goto),
        context.getString(R.string.app_desc_goto),
        context.getResources().getDrawable(R.drawable.ic_tw_coord_goto),
        TwCoordGotoIntents.ACTION_SHOW_GOTO);
  }

  @Override
  public void dispose() {}
}
