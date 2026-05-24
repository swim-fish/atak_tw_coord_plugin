package com.atakmap.android.twcoord.address;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Feature 004 / US4 — graceful-fallback robustness Espresso suite per quickstart.md §6.4 + Flow C4
 * (Wrong-schema negative case).
 *
 * <p>Currently {@link Ignore}d for the same reasons as the sibling import / Flow B+C tests:
 *
 * <ul>
 *   <li>ATAK-CIV must be installed and running on the target device (the plugin runs hosted in the
 *       ATAK process; an instrumented test must spin up the host).
 *   <li>A pre-pushed good fixture (e.g. {@code /sdcard/Download/places-taichung.sqlite}) plus the
 *       ability to push / synthesise a zero-byte ".sqlite" via {@code adb shell}.
 *   <li>{@code adb shell rm -rf} access to the plugin's active-dataset directory so the test can
 *       simulate the "operator deleted the files" scenario.
 * </ul>
 *
 * <p>Operators run this manually via {@code ./gradlew :app:connectedCivDebugAndroidTest} per
 * quickstart.md §6.4 + Flow C4 once the prerequisites are met (this is T048). The skeleton below is
 * the intended structure; remove {@link Ignore} when the fixtures + ADB hooks land on the CI device
 * farm. Performance assertion: the "missing files → State A" recovery MUST complete within SC-005's
 * 2000 ms budget; T048 captures the wall-clock in the test log.
 */
@RunWith(AndroidJUnit4.class)
@Ignore(
    "Requires ATAK-CIV + pre-pushed fixtures + adb shell rm-rf access — see class javadoc; T048 runs manually")
public final class OfflineAddressMissingDataEspressoTest {

  // ---------------------------------------------------------------------
  // SC-005 — recovery when the active dataset's files disappear underneath the plugin
  // (quickstart.md §6.4 + Flow C4 setup)
  // ---------------------------------------------------------------------

  /**
   * Phase 1 of the test: import a known-good fixture, assert State B (dataset metadata visible),
   * then delete the active directory via {@code adb shell rm -rf}. Re-open the Offline Address
   * page; State A (empty + Import button) MUST appear within 2 s.
   */
  @Test
  public void deleteActiveDir_pageRecoversToStateAWithin2s() {
    // Step 1: import the good fixture (Flow A) — assume FlowA passes.
    // Step 2: assert State B is visible on the Offline Address page.
    // Step 3: close the page; via Runtime.exec("rm -rf /storage/emulated/0/atak/tools/twcoord/
    //         offline-address/active") or by exposing FileSystem.deleteRecursively as a test seam,
    //         delete the active dir.
    // Step 4: send ACTION_SHOW_OFFLINE_ADDRESS broadcast.
    // Step 5: assert State A is visible within 2000 ms (capture wall-clock for SC-005).
    // Step 6: assert no crash dialog or stack-trace toast surfaced.
  }

  // ---------------------------------------------------------------------
  // Flow C4 — wrong-schema negative case (quickstart.md §5 C4)
  // ---------------------------------------------------------------------

  /**
   * Phase 2 of the test: with a previously-active dataset still installed, import a known-bad
   * fixture (zero-byte file or a non-SQLite file). The page MUST show an inline error that contains
   * the localised {@code offline_address_error_not_openable} string. The previously- active dataset
   * MUST remain unchanged (atomic activation per ADR-0014).
   */
  @Test
  public void importZeroByteFile_showsNotOpenableErrorAndKeepsPriorDataset() {
    // Step 1: import the good fixture (Flow A). Capture metadata for the comparison later.
    // Step 2: push a zero-byte file: adb shell 'echo -n "" > /sdcard/Download/wrong.sqlite'.
    // Step 3: send ACTION_SHOW_OFFLINE_ADDRESS broadcast → open the page.
    // Step 4: tap Import → pick wrong.sqlite via UiAutomator (system file picker).
    // Step 5: assert the inline error text contains the localised
    //         offline_address_error_not_openable string.
    // Step 6: assert the previously-active dataset's metadata (county / data_date / file_sha)
    //         is unchanged by re-reading State B fields.
  }

  // ---------------------------------------------------------------------
  // Flow C4 variant — non-SQLite file (e.g. a renamed .txt) — should produce
  // the same NOT_OPENABLE failure, not silently activate.
  // ---------------------------------------------------------------------

  @Test
  public void importNonSqliteFile_showsNotOpenableError() {
    // Step 1: push a small text file renamed to .sqlite:
    //         adb shell 'echo "hello" > /sdcard/Download/wrong.sqlite'
    // Step 2: Import → pick wrong.sqlite.
    // Step 3: assert the inline error contains offline_address_error_not_openable.
    // Step 4: assert the magic-bytes pre-check ran (Log line `validateStagedDb: not SQLite`)
    //         rather than the slower openDatabase path.
  }
}
