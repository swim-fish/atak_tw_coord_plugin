package com.atakmap.android.twcoord;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.TwCoordPreferenceFragment.DatasetStatusPresentation;
import com.atakmap.android.twcoord.TwCoordPreferenceFragment.StatusStrings;
import com.atakmap.android.twcoord.address.AddressDataset;
import com.atakmap.android.twcoord.address.GeneratorMetadata;
import com.atakmap.android.twcoord.address.ImportedManifest;
import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Feature 004 / US3 — covers the four cases in {@code
 * specs/004-offline-address/contracts/address-preferences.md § Test plan}:
 *
 * <ul>
 *   <li>(a) status summary = hint when at least one toggle is on but no dataset is active
 *   <li>(b) status summary = "Active: county · data_date" when a dataset is active
 *   <li>(c) clicking the status row sends {@code ACTION_SHOW_OFFLINE_ADDRESS} — exercised by
 *       Espresso {@code OfflineAddressFlowBCEspressoTest} (T041) because {@code AtakBroadcast} +
 *       the host preference framework can't be reasonably Robolectric-shimmed without a live ATAK
 *       process; the production wiring is a 4-line {@code OnPreferenceClickListener} in {@link
 *       TwCoordPreferenceFragment#onResume()} that mirrors the existing {@code pref_open_goto}
 *       click-broadcast wiring (which similarly has no JVM test).
 *   <li>(d) toggling any one of the three SwitchPreferences writes to {@code PreferenceStore} —
 *       covered by the existing {@code AddressPreferencesTest} (which exercises the {@code
 *       KEY_ADDRESS_ROW_*} accessors directly).
 * </ul>
 *
 * <p>The non-trivial logic is the dataset-status presentation rule (the three-state table in the
 * contract). That's extracted as the static helper {@link
 * TwCoordPreferenceFragment#resolveDatasetStatus} and tested here on plain JVM — no Robolectric or
 * ATAK SDK shim required.
 */
public final class TwCoordPreferenceFragmentAddressTest {

  // ---------------------------------------------------------------------
  // Case (a) — at least one toggle on, no dataset → hint summary, clickable
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_anyToggleOnNoDataset_returnsHintClickable() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, /* anyToggleOn= */ true, null);

    assertThat(result.summary()).isEqualTo("hint");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  // ---------------------------------------------------------------------
  // Case (b) — at least one toggle on, dataset active → Active summary, clickable
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_anyToggleOnWithDataset_returnsActiveSummary() {
    StatusStrings strings = new StubStatusStrings();
    AddressDataset active = newDataset("Taichung", "2025-12-01");

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, /* anyToggleOn= */ true, active);

    // The stub formats Active: <county> · <data_date> so we can assert the substitution exactly.
    assertThat(result.summary()).isEqualTo("Active: Taichung · 2025-12-01");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  // ---------------------------------------------------------------------
  // Case — all toggles off → hidden (disabled + non-selectable), summary "none"
  // (Not explicitly in T040 (a)/(b)/(c)/(d) but the third row of the contract's
  // truth table — exercised to lock the rule in place.)
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_allTogglesOff_returnsHiddenStatus() {
    StatusStrings strings = new StubStatusStrings();
    AddressDataset active = newDataset("Taipei", "2025-11-15");

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, /* anyToggleOn= */ false, active);

    assertThat(result.summary()).isEqualTo("none");
    assertThat(result.enabled()).isFalse();
    assertThat(result.selectable()).isFalse();
  }

  // ---------------------------------------------------------------------
  // Case — all toggles off + no dataset → still hidden, summary "none"
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_allTogglesOffNoDataset_returnsHiddenStatus() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, /* anyToggleOn= */ false, null);

    assertThat(result.summary()).isEqualTo("none");
    assertThat(result.enabled()).isFalse();
    assertThat(result.selectable()).isFalse();
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private static AddressDataset newDataset(String county, String dataDate) {
    Map<String, String> raw = new LinkedHashMap<>();
    raw.put("schema_version", "2");
    raw.put("source", "TGOS");
    raw.put("county", county);
    raw.put("data_date", dataDate);
    GeneratorMetadata metadata =
        new GeneratorMetadata(
            /* schemaVersion= */ 2,
            /* source= */ "TGOS",
            county,
            dataDate,
            /* csvSha256= */ null,
            /* csvPath= */ null,
            /* crs= */ null,
            /* insertedRows= */ -1L,
            raw);
    // 64-char lowercase-hex placeholder — ImportedManifest validates the length.
    String sha = "deadbeef".repeat(8);
    ImportedManifest imported =
        new ImportedManifest(
            /* importedAt= */ Instant.parse("2026-05-24T12:00:00Z"),
            /* fileSha256= */ sha,
            /* rtreeBuilt= */ true,
            /* pluginSchemaVersion= */ 1);
    File rootDir = new File("/tmp/places");
    File dbFile = new File("/tmp/places/places.sqlite");
    return new AddressDataset(rootDir, dbFile, metadata, imported);
  }

  /** Stub strings whose values are easy to assert against. */
  private static final class StubStatusStrings implements StatusStrings {
    @Override
    public String datasetStatusNone() {
      return "none";
    }

    @Override
    public String datasetStatusHint() {
      return "hint";
    }

    @Override
    public String datasetStatusActive(String county, String dataDate) {
      return "Active: " + county + " · " + dataDate;
    }
  }
}
