package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.atakmap.android.twcoord.address.lookup.ResultOrdering;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.prefs.PreferenceStore;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class NativeAddressUpgradeTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Context context;
  private SharedPreferences sharedPreferences;

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    sharedPreferences.edit().clear().commit();
  }

  @Test
  public void retainedPreferencesSurviveWhileLegacyGotoValuesRemainInert() {
    Map<String, Object> legacy = new LinkedHashMap<>();
    legacy.put(PreferenceStore.KEY_GOTO_LAST_UNIT, "TWD67");
    legacy.put(PreferenceStore.KEY_GOTO_LAST_TAIPOWER, "A1234AB5678");
    legacy.put(PreferenceStore.KEY_GOTO_RECENT_JSON, "[\"legacy\"]");
    legacy.put(PreferenceStore.KEY_GOTO_MARKER_MODE, "HOSTILE");
    SharedPreferences.Editor seed = sharedPreferences.edit();
    for (Map.Entry<String, Object> entry : legacy.entrySet()) {
      seed.putString(entry.getKey(), entry.getValue().toString());
    }
    seed.commit();

    PreferenceStore before = new PreferenceStore(context);
    before.setResultOrdering(ResultOrdering.MOST_SIMILAR);
    before.setConfidenceThresholds(ConfidenceThresholds.LOOSE);
    before.setAddressRowMe(true);
    before.setAddressRowTarget(false);
    before.setAddressRowMap(true);
    before.setReadoutVisible(false);
    before.setNativeEntryLastUnit(CoordinateUnit.TWD67);
    before.dispose();

    PreferenceStore after = new PreferenceStore(context);
    assertThat(after.getResultOrdering()).isEqualTo(ResultOrdering.MOST_SIMILAR);
    assertThat(after.getConfidenceThresholds()).isEqualTo(ConfidenceThresholds.LOOSE);
    assertThat(after.getAddressRowMe()).isTrue();
    assertThat(after.getAddressRowTarget()).isFalse();
    assertThat(after.getAddressRowMap()).isTrue();
    assertThat(after.isReadoutVisible()).isFalse();
    assertThat(after.getNativeEntryLastUnit()).isEqualTo(CoordinateUnit.TWD67);
    for (Map.Entry<String, Object> entry : legacy.entrySet()) {
      assertThat(sharedPreferences.getString(entry.getKey(), null))
          .isEqualTo(entry.getValue().toString());
    }
    after.dispose();
  }

  @Test
  public void registryStartupDoesNotRewriteExistingDatasetOrManifest() throws Exception {
    Path fixture = Paths.get("src/test/resources/fixtures/places-taichung-fixture.sqlite");
    byte[] fixtureBytes = Files.readAllBytes(fixture);
    AddressBundleImporterTest.TempFileSystem fileSystem =
        new AddressBundleImporterTest.TempFileSystem(temporaryFolder.getRoot().toPath());
    AddressBundleImporter importer =
        new AddressBundleImporter(fileSystem, new MessageDigestShaCalculator(), 3);
    AddressBundleImporter.ImportResult imported =
        importer.importFromInto(new ByteArrayInputStream(fixtureBytes), "臺中市", null);
    assertThat(imported).isInstanceOf(AddressBundleImporter.ImportResult.Success.class);
    AddressDataset dataset = ((AddressBundleImporter.ImportResult.Success) imported).dataset();
    Path manifest = dataset.rootDir().toPath().resolve(AddressBundleImporter.MANIFEST_FILE_NAME);
    byte[] databaseBefore = Files.readAllBytes(dataset.dbFile().toPath());
    byte[] manifestBefore = Files.readAllBytes(manifest);

    ActiveDatasetRegistry registry =
        new ActiveDatasetRegistry(
            importer, ignored -> new NoOpFacade(), () -> ignored -> new NoOpFacade(), fileSystem);
    registry.initFromDisk();

    assertThat(registry.snapshot()).containsKey("臺中市");
    assertThat(Files.readAllBytes(dataset.dbFile().toPath())).isEqualTo(databaseBefore);
    assertThat(Files.readAllBytes(manifest)).isEqualTo(manifestBefore);
    registry.close();
  }

  private static final class NoOpFacade implements AddressDatabaseFacade {
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
}
