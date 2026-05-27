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
 * Feature 004 / US3 + Phase 7 T042 — covers the four-state truth table in {@code
 * specs/004-offline-address/contracts/address-preferences.md § Dataset presence summary table},
 * extended for feature 005 multi-county.
 *
 * <p>The truth table:
 *
 * <ul>
 *   <li>all toggles off → hide the row (disabled + non-selectable)
 *   <li>any toggle on + registry has N ≥ 1 counties → "N counties active — tap to open"
 *   <li>any toggle on + registry empty + legacy active dataset → "Active: county · data_date" (the
 *       auto-migrate intermediate state and v1.0.5 fallback)
 *   <li>any toggle on + nothing active anywhere → "No dataset — tap to open"
 * </ul>
 */
public final class TwCoordPreferenceFragmentAddressTest {

  // ---------------------------------------------------------------------
  // All toggles off → hidden (disabled + non-selectable), regardless of dataset state
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_allTogglesOff_returnsHiddenStatus() {
    StatusStrings strings = new StubStatusStrings();
    AddressDataset active = newDataset("Taipei", "2025-11-15");

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, false, 0, active);

    assertThat(result.summary()).isEqualTo("none");
    assertThat(result.enabled()).isFalse();
    assertThat(result.selectable()).isFalse();
  }

  @Test
  public void resolveDatasetStatus_allTogglesOffNoDataset_returnsHiddenStatus() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, false, 0, null);

    assertThat(result.summary()).isEqualTo("none");
    assertThat(result.enabled()).isFalse();
    assertThat(result.selectable()).isFalse();
  }

  // Even with N > 0 counties, toggles off → still hidden. (Multi-county shouldn't override toggle.)
  @Test
  public void resolveDatasetStatus_allTogglesOffMultiCountyActive_stillHidden() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, false, 3, null);

    assertThat(result.summary()).isEqualTo("none");
    assertThat(result.enabled()).isFalse();
    assertThat(result.selectable()).isFalse();
  }

  // ---------------------------------------------------------------------
  // Any toggle on, no datasets anywhere → hint summary, clickable
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_anyToggleOnNoDataset_returnsHintClickable() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, true, 0, null);

    assertThat(result.summary()).isEqualTo("hint");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  // ---------------------------------------------------------------------
  // Any toggle on, registry empty + legacy active (auto-migrate intermediate state)
  // → "Active: county · data_date" summary
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_legacyActiveOnly_returnsActiveSummary() {
    StatusStrings strings = new StubStatusStrings();
    AddressDataset legacy = newDataset("Taichung", "2025-12-01");

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, true, 0, legacy);

    assertThat(result.summary()).isEqualTo("Active: Taichung · 2025-12-01");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  // ---------------------------------------------------------------------
  // T042 — Any toggle on, registry has 1 county → multi-county summary (singular)
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_singleCountyActive_returnsMultiSummary() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, true, 1, null);

    assertThat(result.summary()).isEqualTo("Multi: 1");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  // ---------------------------------------------------------------------
  // T042 — Any toggle on, registry has N > 1 counties → multi-county summary
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_multiCountyActive_returnsMultiSummary() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, true, 5, null);

    assertThat(result.summary()).isEqualTo("Multi: 5");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  // ---------------------------------------------------------------------
  // T042 — Multi-county wins over legacy active (registry is the source of truth post-005)
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_multiCountyBeatsLegacyActive() {
    StatusStrings strings = new StubStatusStrings();
    AddressDataset legacy = newDataset("Taipei", "2025-11-15");

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, true, 2, legacy);

    assertThat(result.summary()).isEqualTo("Multi: 2");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
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

    @Override
    public String datasetStatusActiveMulti(int countyCount) {
      return "Multi: " + countyCount;
    }
  }
}
