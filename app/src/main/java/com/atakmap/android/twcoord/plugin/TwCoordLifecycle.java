package com.atakmap.android.twcoord.plugin;

import com.atak.plugins.impl.AbstractPlugin;
import com.atak.plugins.impl.IToolbarItem;
import com.atak.plugins.impl.PluginContextProvider;
import com.atakmap.android.twcoord.TwCoordMapComponent;
import gov.tak.api.plugin.IServiceController;
import java.util.Collections;
import java.util.List;

public class TwCoordLifecycle extends AbstractPlugin {

  public TwCoordLifecycle(IServiceController serviceController) {
    super(
        serviceController,
        new IToolbarItem[] {
          new TwCoordTool(
              serviceController.getService(PluginContextProvider.class).getPluginContext())
        },
        new TwCoordMapComponent());
  }

  static List<Class<? extends IToolbarItem>> publicToolbarItemTypes() {
    return Collections.singletonList(TwCoordTool.class);
  }
}
