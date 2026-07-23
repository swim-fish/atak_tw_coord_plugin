package com.atakmap.android.twcoord.prefs;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.atakmap.android.twcoord.address.lookup.ResultOrdering;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** Feature 007 — new PreferenceStore keys (result ordering + readout visibility). */
@RunWith(RobolectricTestRunner.class)
public class PreferenceStore007Test {

  private Context ctx;
  private PreferenceStore prefs;

  @Before
  public void setUp() {
    ctx = RuntimeEnvironment.getApplication();
    prefs = new PreferenceStore(ctx);
  }

  @Test
  public void resultOrderingDefaultsToDistance() {
    assertThat(prefs.getResultOrdering()).isEqualTo(ResultOrdering.DISTANCE);
  }

  @Test
  public void resultOrderingRoundTrips() {
    prefs.setResultOrdering(ResultOrdering.MOST_SIMILAR);
    assertThat(prefs.getResultOrdering()).isEqualTo(ResultOrdering.MOST_SIMILAR);
    prefs.setResultOrdering(ResultOrdering.DISTANCE);
    assertThat(prefs.getResultOrdering()).isEqualTo(ResultOrdering.DISTANCE);
  }

  @Test
  public void resultOrderingCorruptValueFallsBackToDistance() {
    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
    sp.edit().putString(PreferenceStore.KEY_SEARCH_RESULT_ORDERING, "GARBAGE").commit();
    assertThat(prefs.getResultOrdering()).isEqualTo(ResultOrdering.DISTANCE);
  }

  @Test
  public void readoutVisibleDefaultsToTrue() {
    assertThat(prefs.isReadoutVisible()).isTrue();
  }

  @Test
  public void readoutVisibleRoundTrips() {
    prefs.setReadoutVisible(false);
    assertThat(prefs.isReadoutVisible()).isFalse();
    prefs.setReadoutVisible(true);
    assertThat(prefs.isReadoutVisible()).isTrue();
  }
}
