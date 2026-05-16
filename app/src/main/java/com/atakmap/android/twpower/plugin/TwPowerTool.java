package com.atakmap.android.twpower.plugin;

import android.content.Context;
import com.atak.plugins.impl.AbstractPluginTool;
import gov.tak.api.util.Disposable;

public class TwPowerTool extends AbstractPluginTool implements Disposable {

  public TwPowerTool(Context context) {
    super(
        context,
        context.getString(R.string.app_name),
        context.getString(R.string.app_desc),
        context.getResources().getDrawable(R.drawable.ic_tw_power),
        "com.atakmap.android.twpower.SHOW_PLUGIN");
  }

  @Override
  public void dispose() {}
}
