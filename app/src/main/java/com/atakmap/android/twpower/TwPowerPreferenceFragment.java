package com.atakmap.android.twpower;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.twpower.plugin.R;

/**
 * Plugin settings (T046). Hosts the two ListPreference entries declared in res/xml/preferences.xml.
 */
public class TwPowerPreferenceFragment extends PluginPreferenceFragment {

  @SuppressLint("StaticFieldLeak")
  private static Context pluginContext;

  public TwPowerPreferenceFragment() {
    super(pluginContext, R.xml.preferences);
  }

  @SuppressLint("ValidFragment")
  public TwPowerPreferenceFragment(final Context pluginContext) {
    super(pluginContext, R.xml.preferences);
    TwPowerPreferenceFragment.pluginContext = pluginContext;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public String getSubTitle() {
    return getSubTitle("Tool Preferences", pluginContext.getString(R.string.pref_screen_title));
  }
}
