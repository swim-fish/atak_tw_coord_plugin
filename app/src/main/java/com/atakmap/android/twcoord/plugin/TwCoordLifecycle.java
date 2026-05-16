package com.atakmap.android.twcoord.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.twcoord.TwCoordMapComponent;
import gov.tak.api.plugin.IServiceController;

public class TwCoordLifecycle extends AbstractPlugin {
  public TwCoordLifecycle(IServiceController serviceController) {
    super(
        serviceController,
        new TwCoordTool(
            serviceController.getService(PluginContextProvider.class).getPluginContext()),
        new TwCoordMapComponent());
  }
}
