package com.atakmap.android.twcoord.plugin;

import android.content.Context;
import com.atak.plugins.impl.AbstractPluginTool;
import com.atakmap.android.twcoord.address.OfflineAddressIntents;
import com.atakmap.coremap.log.Log;
import gov.tak.api.util.Disposable;

/**
 * Third Tools-menu entry — the Offline Address page (feature 004). Mirrors {@link TwCoordTool} and
 * {@link TwCoordGotoTool} exactly: a thin {@link AbstractPluginTool} subclass whose only job is to
 * fire {@link OfflineAddressIntents#ACTION_SHOW_OFFLINE_ADDRESS} when the operator taps the icon.
 * The receiver side (registered in {@code TwCoordMapComponent}) opens the actual drop-down.
 */
public class OfflineAddressTool extends AbstractPluginTool implements Disposable {

  private static final String TAG = "OfflineAddressTool";

  public OfflineAddressTool(Context context) {
    super(
        context,
        context.getString(R.string.tool_offline_address_label),
        context.getString(R.string.tool_offline_address_desc),
        context.getResources().getDrawable(R.drawable.ic_offline_address),
        OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS);
  }

  @Override
  public void dispose() {
    // Constitution VI: dispose is a host-callable entry point. The body is empty here (no
    // owned state to release), but we guard defensively so any future addition cannot escape
    // an uncaught Throwable into the host process.
    try {
      // intentionally empty
    } catch (Throwable t) {
      Log.w(TAG, "dispose threw", t);
    }
  }
}
