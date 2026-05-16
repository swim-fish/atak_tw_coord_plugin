package com.atakmap.android.twcoord.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.IToolbarItem;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.twcoord.TwCoordMapComponent;
import gov.tak.api.plugin.IServiceController;

public class TwCoordLifecycle extends AbstractPlugin {

  public TwCoordLifecycle(IServiceController serviceController) {
    // Multi-tool constructor (AbstractPlugin(IServiceController, IToolbarItem[], MapComponent)).
    // Feature 001 ships the unit-cycle icon (TwCoordTool); feature 002 adds the GoTo input page
    // icon (TwCoordGotoTool). Both icons appear in the ATAK Tools menu.
    super(
        serviceController,
        new IToolbarItem[] {
          new TwCoordTool(
              serviceController.getService(PluginContextProvider.class).getPluginContext()),
          new TwCoordGotoTool(
              serviceController.getService(PluginContextProvider.class).getPluginContext())
        },
        new TwCoordMapComponent());
  }
}
