package com.atakmap.android.twpower.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.twpower.TwPowerMapComponent;
import gov.tak.api.plugin.IServiceController;

public class TwPowerLifecycle extends AbstractPlugin {
  public TwPowerLifecycle(IServiceController serviceController) {
    super(
        serviceController,
        new TwPowerTool(
            serviceController.getService(PluginContextProvider.class).getPluginContext()),
        new TwPowerMapComponent());
  }
}
