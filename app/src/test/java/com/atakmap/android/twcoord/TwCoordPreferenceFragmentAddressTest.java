package com.atakmap.android.twcoord;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.TwCoordPreferenceFragment.DatasetStatusPresentation;
import com.atakmap.android.twcoord.TwCoordPreferenceFragment.StatusStrings;
import com.atakmap.android.twcoord.address.AddressDatabaseFacade;
import com.atakmap.android.twcoord.address.AddressDataset;
import com.atakmap.android.twcoord.address.AddressRecord;
import com.atakmap.android.twcoord.address.CountyActiveDataset;
import com.atakmap.android.twcoord.address.GeneratorMetadata;
import com.atakmap.android.twcoord.address.ImportedManifest;
import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

/**
 * Covers the internal dataset-manager status presentation, including feature 013's requirement that
 * management remain reachable independently of map readout visibility.
 *
 * <p>The truth table:
 *
 * <ul>
 *   <li>registry has N ≥ 1 counties → "N counties active — tap to open"
 *   <li>registry empty + legacy active dataset → "Active: county · data_date" (the auto-migrate
 *       intermediate state and v1.0.5 fallback)
 *   <li>nothing active anywhere → "No dataset — tap to open"
 * </ul>
 */
public final class TwCoordPreferenceFragmentAddressTest {

  // ---------------------------------------------------------------------
  // Management remains reachable with all readout toggles off.
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_readoutsOffLegacyDataset_remainsSelectable() {
    StatusStrings strings = new StubStatusStrings();
    AddressDataset active = newDataset("Taipei", "2025-11-15");

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, 0, active);

    assertThat(result.summary()).isEqualTo("Active: Taipei · 2025-11-15");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  @Test
  public void resolveDatasetStatus_readoutsOffNoDataset_remainsSelectable() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, 0, null);

    assertThat(result.summary()).isEqualTo("hint");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  @Test
  public void resolveDatasetStatus_readoutsOffMultiCounty_remainsSelectable() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, 3, null);

    assertThat(result.summary()).isEqualTo("Multi: 3");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  // ---------------------------------------------------------------------
  // No datasets anywhere → hint summary, clickable
  // ---------------------------------------------------------------------
  @Test
  public void resolveDatasetStatus_noDataset_returnsHintClickable() {
    StatusStrings strings = new StubStatusStrings();

    DatasetStatusPresentation result =
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, 0, null);

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
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, 0, legacy);

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
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, 1, null);

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
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, 5, null);

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
        TwCoordPreferenceFragment.resolveDatasetStatus(strings, 2, legacy);

    assertThat(result.summary()).isEqualTo("Multi: 2");
    assertThat(result.enabled()).isTrue();
    assertThat(result.selectable()).isTrue();
  }

  // ---------------------------------------------------------------------
  // Review 2026-05-28 finding #6 — active-datasets refresh signature. The Settings fragment skips
  // rebuilding the per-county rows when the signature is unchanged, so the signature MUST stay
  // stable for identical content and MUST change for any content / language difference that would
  // alter the rendered rows.
  // ---------------------------------------------------------------------

  @Test
  public void activeDatasetsSignature_identicalContent_isStable() {
    Map<String, CountyActiveDataset> a = snapshot(county("台中市", "115-01", 1000));
    Map<String, CountyActiveDataset> b = snapshot(county("台中市", "115-01", 1000));

    assertThat(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", a))
        .isEqualTo(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", b));
  }

  @Test
  public void activeDatasetsSignature_addingCounty_changes() {
    Map<String, CountyActiveDataset> one = snapshot(county("台中市", "115-01", 1000));
    Map<String, CountyActiveDataset> two =
        snapshot(county("台中市", "115-01", 1000), county("彰化縣", "114-05", 500));

    assertThat(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", one))
        .isNotEqualTo(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", two));
  }

  @Test
  public void activeDatasetsSignature_dataDateChange_changes() {
    Map<String, CountyActiveDataset> before = snapshot(county("台中市", "115-01", 1000));
    Map<String, CountyActiveDataset> after = snapshot(county("台中市", "115-02", 1000));

    assertThat(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", before))
        .isNotEqualTo(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", after));
  }

  @Test
  public void activeDatasetsSignature_insertedRowsChange_changes() {
    Map<String, CountyActiveDataset> before = snapshot(county("台中市", "115-01", 1000));
    Map<String, CountyActiveDataset> after = snapshot(county("台中市", "115-01", 2000));

    assertThat(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", before))
        .isNotEqualTo(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", after));
  }

  @Test
  public void activeDatasetsSignature_languageChange_changes() {
    Map<String, CountyActiveDataset> same = snapshot(county("台中市", "115-01", 1000));

    assertThat(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", same))
        .isNotEqualTo(TwCoordPreferenceFragment.activeDatasetsSignature("JA", same));
  }

  @Test
  public void activeDatasetsSignature_emptySnapshot_isStablePerLanguage() {
    Map<String, CountyActiveDataset> empty = snapshot();

    assertThat(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", empty))
        .isEqualTo(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", empty));
    assertThat(TwCoordPreferenceFragment.activeDatasetsSignature("ZH_TW", empty))
        .isNotEqualTo(TwCoordPreferenceFragment.activeDatasetsSignature("EN", empty));
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

  /**
   * A per-county snapshot entry with a controllable inserted-rows count (part of the signature).
   */
  private static CountyActiveDataset county(String county, String dataDate, long insertedRows) {
    Map<String, String> raw = new LinkedHashMap<>();
    raw.put("schema_version", "2");
    raw.put("source", "TGOS");
    raw.put("county", county);
    raw.put("data_date", dataDate);
    GeneratorMetadata metadata =
        new GeneratorMetadata(2, "TGOS", county, dataDate, null, null, null, insertedRows, raw);
    ImportedManifest imported =
        new ImportedManifest(Instant.parse("2026-05-24T12:00:00Z"), "deadbeef".repeat(8), true, 1);
    AddressDataset dataset =
        new AddressDataset(
            new File("/tmp/places/" + county),
            new File("/tmp/places/" + county + "/places.sqlite"),
            metadata,
            imported);
    return new CountyActiveDataset(county, dataset, new StubFacade());
  }

  private static Map<String, CountyActiveDataset> snapshot(CountyActiveDataset... entries) {
    Map<String, CountyActiveDataset> m = new LinkedHashMap<>();
    for (CountyActiveDataset e : entries) m.put(e.county(), e);
    return m;
  }

  /** Minimal facade — the signature only reads dataset metadata, never the facade. */
  private static final class StubFacade implements AddressDatabaseFacade {
    @Override
    public GeneratorMetadata readMetadata() {
      return null;
    }

    @Override
    public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
      return null;
    }

    @Override
    public void close() {}
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
