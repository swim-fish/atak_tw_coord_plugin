package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Feature 004 — tests for {@link SqliteAddressDatabase} per {@code
 * contracts/address-database-facade.md §Test plan}. Fixture databases are built directly via
 * xerial-sqlite-jdbc; the facade then reads them through Robolectric's shadow of {@link
 * SQLiteDatabase} (xerial-backed) so the production code path executes verbatim.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class AddressDatabaseFacadeTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private Path dbPath;
  private SqliteAddressDatabase facade;

  @Before
  public void setUp() throws Exception {
    dbPath = tmp.newFile("fixture.sqlite").toPath();
    Files.delete(dbPath);
  }

  @After
  public void tearDown() {
    if (facade != null) {
      facade.close();
      facade = null;
    }
  }

  // ----------------------------------------------------------------------
  // Test 1 — readMetadata returns all keys verbatim
  // ----------------------------------------------------------------------

  @Test
  public void readMetadata_returnsAllKeysVerbatim() throws Exception {
    buildFixtureFor(/* metadataOnly= */ false);
    facade = openFacade(dbPath.toFile());

    GeneratorMetadata m = facade.readMetadata();
    assertThat(m.schemaVersion()).isEqualTo(1);
    assertThat(m.county()).isEqualTo("台中市");
    assertThat(m.source()).isEqualTo("tgos");
    assertThat(m.dataDate()).isEqualTo("115-01");
    assertThat(m.crs()).isEqualTo("EPSG:4326");
    assertThat(m.insertedRows()).isEqualTo(3);
    assertThat(m.raw())
        .containsEntry("schema_version", "1")
        .containsEntry("inserted", "3")
        .containsEntry("crs", "EPSG:4326");
  }

  // ----------------------------------------------------------------------
  // Test 2 — missing metadata table → empty default
  // ----------------------------------------------------------------------

  @Test
  public void readMetadata_missingTableReturnsEmpty() throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      try (Statement s = c.createStatement()) {
        // Only build the places-related tables; no metadata.
        s.executeUpdate("CREATE TABLE places (id INTEGER PRIMARY KEY)");
      }
    }
    facade = openFacade(dbPath.toFile());

    GeneratorMetadata m = facade.readMetadata();
    assertThat(m.schemaVersion()).isEqualTo(0);
    assertThat(m.county()).isEmpty();
    assertThat(m.raw()).isEmpty();
  }

  // ----------------------------------------------------------------------
  // Test 3 — nearestWithin returns the nearest record in radius
  // ----------------------------------------------------------------------

  @Test
  public void nearestWithin_returnsNearestRecordInRadius() throws Exception {
    // Origin at 24.150000, 120.650000.
    // Row 1: ~50 m east.
    // Row 2: ~200 m east.
    // Row 3: ~800 m east.
    double originLat = 24.150000;
    double originLon = 120.650000;
    buildFixtureWithRowsAt(
        originLat,
        originLon,
        new FixtureRow[] {
          new FixtureRow(0.0, metresEastToDeltaLon(50.0, originLat), "點 50m"),
          new FixtureRow(0.0, metresEastToDeltaLon(200.0, originLat), "點 200m"),
          new FixtureRow(0.0, metresEastToDeltaLon(800.0, originLat), "點 800m"),
        });
    facade = openFacade(dbPath.toFile());

    AddressRecord r = facade.nearestWithin(originLat, originLon, 500.0);
    assertThat(r).isNotNull();
    assertThat(r.displayName()).isEqualTo("點 50m");
  }

  // ----------------------------------------------------------------------
  // Test 4 — returns null when none in radius
  // ----------------------------------------------------------------------

  @Test
  public void nearestWithin_returnsNullIfNoneInRadius() throws Exception {
    double originLat = 24.150000;
    double originLon = 120.650000;
    buildFixtureWithRowsAt(
        originLat,
        originLon,
        new FixtureRow[] {
          new FixtureRow(0.0, metresEastToDeltaLon(50.0, originLat), "點 50m"),
        });
    facade = openFacade(dbPath.toFile());

    // 30 m radius — the 50 m row is outside.
    AddressRecord r = facade.nearestWithin(originLat, originLon, 30.0);
    assertThat(r).isNull();
  }

  // ----------------------------------------------------------------------
  // Test 5 — cos-latitude bbox correction is applied
  // ----------------------------------------------------------------------

  @Test
  public void nearestWithin_respectsCosLatitudeBboxCorrection() throws Exception {
    // At 25°N, cos ≈ 0.9063. For radius=500 m:
    //   dLat allowance = 500/111320      = 0.00449°
    //   dLon allowance = 500/(111320*cos)= 0.00495°
    // A record at +480 m east (dLon = 480/(111320*cos) = 0.00476°) is:
    //   - inside the cos-corrected longitude bbox (0.00476 < 0.00495) ✓
    //   - WOULD be outside an uncorrected longitude bbox (0.00476 > 0.00449) ✗
    //   - inside the 500 m haversine radius (distance ≈ 480 m) ✓
    // → finding it proves the cos correction is applied.
    double originLat = 25.000000;
    double originLon = 121.500000;
    double dLonRec = metresEastToDeltaLon(480.0, originLat);
    buildFixtureWithRowsAt(
        originLat, originLon, new FixtureRow[] {new FixtureRow(0.0, dLonRec, "x")});
    facade = openFacade(dbPath.toFile());

    AddressRecord r = facade.nearestWithin(originLat, originLon, 500.0);
    assertThat(r)
        .as("record 480m east at lat=25° must be found when bbox is cos-corrected")
        .isNotNull();
    assertThat(r.displayName()).isEqualTo("x");
  }

  // ----------------------------------------------------------------------
  // Test 6 — empty places_rtree returns null cleanly
  // ----------------------------------------------------------------------

  @Test
  public void nearestWithin_handlesEmptyRtree() throws Exception {
    // Build a fixture with metadata + places (one row) BUT empty places_rtree.
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      try (Statement s = c.createStatement()) {
        s.executeUpdate("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        s.executeUpdate("INSERT INTO metadata VALUES ('schema_version','1')");
        s.executeUpdate("INSERT INTO metadata VALUES ('county','TestCounty')");
        s.executeUpdate("INSERT INTO metadata VALUES ('data_date','2026-01')");
        s.executeUpdate(
            "CREATE TABLE places ("
                + "id INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL,"
                + " display_name TEXT NOT NULL, display_name_halfwidth TEXT NOT NULL)");
        s.executeUpdate("INSERT INTO places VALUES (1, 24.15, 120.65, 'X', 'X')");
        s.executeUpdate(
            "CREATE VIRTUAL TABLE places_rtree USING rtree("
                + "id, min_lat, max_lat, min_lon, max_lon)");
        // Note: NO INSERT INTO places_rtree — the index is empty.
      }
    }
    facade = openFacade(dbPath.toFile());

    AddressRecord r = facade.nearestWithin(24.15, 120.65, 500.0);
    assertThat(r).as("empty R*Tree → no candidates → null").isNull();
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  /** Open the Android-side {@link SQLiteDatabase} via Robolectric's shadow and wrap as facade. */
  private static SqliteAddressDatabase openFacade(File dbFile) {
    SQLiteDatabase db =
        SQLiteDatabase.openDatabase(
            dbFile.getAbsolutePath(),
            null,
            SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    return new SqliteAddressDatabase(db);
  }

  /** Build a fixture with metadata + 3 sample rows + populated rtree. */
  private void buildFixtureFor(boolean metadataOnly) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      try (Statement s = c.createStatement()) {
        s.executeUpdate("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        s.executeUpdate("INSERT INTO metadata VALUES ('schema_version','1')");
        s.executeUpdate("INSERT INTO metadata VALUES ('source','tgos')");
        s.executeUpdate("INSERT INTO metadata VALUES ('county','台中市')");
        s.executeUpdate("INSERT INTO metadata VALUES ('data_date','115-01')");
        s.executeUpdate("INSERT INTO metadata VALUES ('crs','EPSG:4326')");
        s.executeUpdate("INSERT INTO metadata VALUES ('inserted','3')");
        if (!metadataOnly) {
          s.executeUpdate(
              "CREATE TABLE places ("
                  + "id INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL,"
                  + " display_name TEXT NOT NULL, display_name_halfwidth TEXT NOT NULL)");
          s.executeUpdate("INSERT INTO places VALUES (1, 24.15, 120.65, 'A', 'A')");
          s.executeUpdate("INSERT INTO places VALUES (2, 24.16, 120.66, 'B', 'B')");
          s.executeUpdate("INSERT INTO places VALUES (3, 24.17, 120.67, 'C', 'C')");
          s.executeUpdate(
              "CREATE VIRTUAL TABLE places_rtree USING rtree("
                  + "id, min_lat, max_lat, min_lon, max_lon)");
          s.executeUpdate(
              "INSERT INTO places_rtree(id, min_lat, max_lat, min_lon, max_lon)"
                  + " SELECT id, lat, lat, lon, lon FROM places");
        }
      }
    }
  }

  /** Build a fixture with rows positioned at deltas relative to (originLat, originLon). */
  private void buildFixtureWithRowsAt(double originLat, double originLon, FixtureRow[] rows)
      throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
      try (Statement s = c.createStatement()) {
        s.executeUpdate("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        s.executeUpdate("INSERT INTO metadata VALUES ('schema_version','1')");
        s.executeUpdate("INSERT INTO metadata VALUES ('county','X')");
        s.executeUpdate("INSERT INTO metadata VALUES ('data_date','X')");
        s.executeUpdate(
            "CREATE TABLE places ("
                + "id INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL,"
                + " display_name TEXT NOT NULL, display_name_halfwidth TEXT NOT NULL)");
        for (int i = 0; i < rows.length; i++) {
          FixtureRow row = rows[i];
          s.executeUpdate(
              "INSERT INTO places VALUES ("
                  + (i + 1)
                  + ", "
                  + (originLat + row.dLat)
                  + ", "
                  + (originLon + row.dLon)
                  + ", '"
                  + row.name
                  + "', '"
                  + row.name
                  + "')");
        }
        s.executeUpdate(
            "CREATE VIRTUAL TABLE places_rtree USING rtree("
                + "id, min_lat, max_lat, min_lon, max_lon)");
        s.executeUpdate(
            "INSERT INTO places_rtree(id, min_lat, max_lat, min_lon, max_lon)"
                + " SELECT id, lat, lat, lon, lon FROM places");
      }
    }
  }

  /** Convert metres-east to a longitude delta at the given latitude. */
  private static double metresEastToDeltaLon(double metres, double atLat) {
    double cosLat = Math.cos(Math.toRadians(atLat));
    if (cosLat < 1e-12) cosLat = 1e-12;
    return metres / (111_320.0 * cosLat);
  }

  /** Lat-degrees per metre. */
  private static final double LAT_DEGREES_PER_METRE = 1.0 / 111_320.0;

  /** Compact value type for fixture rows. */
  private static final class FixtureRow {
    final double dLat;
    final double dLon;
    final String name;

    FixtureRow(double dLat, double dLon, String name) {
      this.dLat = dLat;
      this.dLon = dLon;
      this.name = name;
    }
  }
}
