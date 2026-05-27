package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.AddressBundleImporterTest.TempFileSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Feature 005 — Robolectric tests for the {@link AddressBundleImporter#importFromInto} per-county
 * overload + {@link AddressBundleImporter#removeActive(String)} sibling.
 *
 * <p>Per contracts/active-dataset-registry.md + plan T020. Validates: (a) staging dir + active dir
 * are per-county; (b) imports of different counties coexist on disk; (c) per-county removeActive
 * deletes only the targeted county's dir.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class AddressBundleImporterMultiCountyTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private TempFileSystem fs;
  private MessageDigestShaCalculator sha;
  private AddressBundleImporter importer;

  private static final int MAX_SUPPORTED_SCHEMA = 2;

  @Before
  public void setUp() throws IOException {
    fs = new TempFileSystem(tmp.getRoot().toPath());
    sha = new MessageDigestShaCalculator();
    importer = new AddressBundleImporter(fs, sha, MAX_SUPPORTED_SCHEMA);
  }

  @Test
  public void importFromInto_writesIntoActiveCountyDir() throws Exception {
    byte[] bytes = buildFixture("台中市");
    AddressBundleImporter.ImportResult r =
        importer.importFromInto(new ByteArrayInputStream(bytes), "台中市", null);

    assertThat(r.isSuccess()).as("import success").isTrue();
    AddressBundleImporter.ImportResult.Success ok = (AddressBundleImporter.ImportResult.Success) r;
    Path expectedDir = fs.activeCountyDir("台中市");
    assertThat(ok.dataset().rootDir().toPath()).isEqualTo(expectedDir);
    assertThat(Files.exists(expectedDir.resolve("places.sqlite"))).isTrue();
    assertThat(Files.exists(expectedDir.resolve("imported.manifest.txt"))).isTrue();
    // Single-active layout (active/places.sqlite) MUST stay absent — multi-county-only path.
    assertThat(Files.exists(fs.getActiveDir().resolve("places.sqlite"))).isFalse();
  }

  @Test
  public void twoCountiesCoexistInPerCountyLayout() throws Exception {
    byte[] taichungBytes = buildFixture("台中市");
    byte[] changhuaBytes = buildFixture("彰化縣");

    AddressBundleImporter.ImportResult r1 =
        importer.importFromInto(new ByteArrayInputStream(taichungBytes), "台中市", null);
    AddressBundleImporter.ImportResult r2 =
        importer.importFromInto(new ByteArrayInputStream(changhuaBytes), "彰化縣", null);

    assertThat(r1.isSuccess()).isTrue();
    assertThat(r2.isSuccess()).isTrue();
    assertThat(Files.exists(fs.activeCountyDir("台中市").resolve("places.sqlite"))).isTrue();
    assertThat(Files.exists(fs.activeCountyDir("彰化縣").resolve("places.sqlite"))).isTrue();
  }

  @Test
  public void removeActiveByCounty_leavesOtherCountyAlone() throws Exception {
    importer.importFromInto(new ByteArrayInputStream(buildFixture("台中市")), "台中市", null);
    importer.importFromInto(new ByteArrayInputStream(buildFixture("彰化縣")), "彰化縣", null);

    importer.removeActive("彰化縣");

    assertThat(Files.exists(fs.activeCountyDir("彰化縣"))).isFalse();
    assertThat(Files.exists(fs.activeCountyDir("台中市").resolve("places.sqlite"))).isTrue();
  }

  @Test
  public void removeActiveByCounty_idempotentOnMissingCounty() {
    // No imports yet. Should not throw, should not create any state.
    importer.removeActive("台中市");
    assertThat(Files.exists(fs.activeCountyDir("台中市"))).isFalse();
  }

  @Test
  public void removeActiveByCounty_nullCountyIsSwallowed() {
    importer.removeActive((String) null); // logs Log.w but does not throw
    // No state changed.
    assertThat(Files.exists(fs.getActiveDir())).isFalse();
  }

  @Test
  public void importFromInto_rejectsNullCounty() {
    try {
      importer.importFromInto(new ByteArrayInputStream(new byte[16]), null, null);
      org.junit.Assert.fail("expected NullPointerException");
    } catch (NullPointerException expected) {
      // ok
    }
  }

  // ----------------------------------------------------------------------
  // Fixture builder — mirrors AddressBundleImporterTest.buildFixture but takes a county
  // parameter so we can build distinct Taichung/Changhua test fixtures.
  // ----------------------------------------------------------------------

  private byte[] buildFixture(String county) throws Exception {
    Path fixture = tmp.newFile("fx-" + county + "-" + UUID.randomUUID() + ".sqlite").toPath();
    Files.delete(fixture);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + fixture)) {
      try (Statement s = c.createStatement()) {
        s.executeUpdate("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        s.executeUpdate("INSERT INTO metadata VALUES ('schema_version','2')");
        s.executeUpdate("INSERT INTO metadata VALUES ('source','tgos')");
        s.executeUpdate("INSERT INTO metadata VALUES ('county','" + county + "')");
        s.executeUpdate("INSERT INTO metadata VALUES ('data_date','115-01')");
        s.executeUpdate(
            "CREATE TABLE places ("
                + "id INTEGER PRIMARY KEY, source TEXT NOT NULL, osm_id INTEGER,"
                + " lat REAL NOT NULL, lon REAL NOT NULL,"
                + " name TEXT, display_name TEXT NOT NULL, display_name_halfwidth TEXT NOT NULL,"
                + " district_code TEXT NOT NULL, county TEXT NOT NULL, township TEXT NOT NULL,"
                + " village TEXT, neighbor TEXT, street TEXT, area TEXT,"
                + " lane TEXT, alley TEXT, number TEXT)");
        // v2 generator pre-builds places_rtree so the importer skips its own R*Tree build path.
        s.executeUpdate(
            "CREATE VIRTUAL TABLE places_rtree USING rtree("
                + "id, min_lat, max_lat, min_lon, max_lon)");
      }
    }
    return Files.readAllBytes(fixture);
  }
}
