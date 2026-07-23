package com.atakmap.android.twcoord.address;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Physical upgrade journey retained as a release gate for both supported ATAK lines. */
@RunWith(AndroidJUnit4.class)
@Ignore("Requires older plugin fixture, ATAK host, two imported counties, and plugin reload")
public final class NativeAddressUpgradeEspressoTest {

  @Test
  public void upgradePreservesDataAndMakesRetiredActionsNoOps() {
    // Step 1: install the older fixture with two counties, retained address preferences, and
    //         seeded custom Go To Recent/marker/icon values.
    // Step 2: upgrade and reload the plugin; assert exactly one TW Coordinates Tools item.
    // Step 3: use native Address without re-import and verify ordering/confidence/readout settings.
    // Step 4: send SHOW_GOTO and SHOW_FORWARD_SEARCH; assert no page, map mutation, or crash.
  }
}
