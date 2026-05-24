package com.atakmap.android.twcoord.address;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Feature 004 — end-to-end import smoke test (Acceptance Flow A from quickstart §3).
 *
 * <p>Currently {@link Ignore}d because the test depends on:
 *
 * <ul>
 *   <li>ATAK-CIV being installed and running on the target device (the plugin runs hosted in the
 *       ATAK process; an instrumented test must spin up the host).
 *   <li>A pre-pushed fixture {@code .sqlite} at a path the SAF system-file-picker can see (e.g.
 *       {@code /sdcard/Download/places-changhua.sqlite}).
 * </ul>
 *
 * <p>Operators run this manually via {@code ./gradlew :app:connectedCivDebugAndroidTest} per {@link
 * #quickstart_link quickstart.md §3 Acceptance Flow A} once both prerequisites are met. The
 * skeleton below is the intended structure; remove {@link Ignore} when the fixture path is
 * provisioned on the CI device farm.
 */
@RunWith(AndroidJUnit4.class)
@Ignore("Requires ATAK-CIV + pre-pushed fixture .sqlite — see class javadoc; T031 runs manually")
public final class OfflineAddressImportEspressoTest {

  /** quickstart.md §3 — full Acceptance Flow A in a single happy-path test. */
  @Test
  public void importFlowA_endToEnd() {
    // Step 1: Open the Tools menu and tap "Offline Address".
    // (Trigger by broadcasting ACTION_SHOW_OFFLINE_ADDRESS directly instead of synthesising
    // the Tools-menu tap — the broadcast IS the contract the tool uses.)
    sendShowOfflineAddressBroadcast();

    // Step 2: assert State A is visible (empty-state + Import button).
    onView(withId(/* R.id.offline_address_state_a */ 0));
    // ... assertion stub ...

    // Step 3: tap Import; SAF launches OfflineAddressFilePickerActivity. Use UiAutomator to
    // pick the fixture (Espresso cannot drive system UI).
    // ... uiAutomator drives the system file picker ...

    // Step 4: wait for the import worker. Use IdlingResource hooked to the importer's
    // ExecutorService completion.
    // ... idling resource wait ...

    // Step 5: assert State B is visible with the fixture's metadata fields populated.
    // ... assertions on R.id.offline_address_value_county etc.

    // Step 6: assert R*Tree was built (file SHA-256 + R*Tree built = true).
  }

  private static void sendShowOfflineAddressBroadcast() {
    // Implementation deferred — see class-level @Ignore rationale.
  }

  // Anchor for the javadoc link.
  static void quickstart_link() {}
}
