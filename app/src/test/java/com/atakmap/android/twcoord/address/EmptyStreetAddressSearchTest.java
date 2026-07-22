package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import android.database.sqlite.SQLiteDatabase;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Feature 006 regression — empty-street addresses (the generator's "空街道門牌" case: {@code
 * places.street} NULL, located by a named 巷/莊/新村 in {@code places.area}) must surface in the
 * district-scoped forward-search funnel under their {@code area} locality name. Before the
 * COALESCE(street→area) fix, {@code streetCandidates} matched only {@code p.street LIKE …} and
 * these rows vanished entirely; streeted rows must remain unaffected.
 *
 * <p>Built against an in-memory {@code places} table so the test is self-contained (the committed
 * Taichung fixture is selected by {@code street LIKE} and therefore holds zero empty-street rows).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class EmptyStreetAddressSearchTest {

  private static final double ANCHOR_LAT = 24.075;
  private static final double ANCHOR_LON = 120.535;

  private SQLiteDatabase db;
  private SqliteAddressDatabase facade;

  @Before
  public void setUp() {
    db = SQLiteDatabase.create(null); // in-memory
    db.execSQL(
        "CREATE TABLE places ("
            + " id INTEGER PRIMARY KEY, lat REAL, lon REAL,"
            + " display_name TEXT, display_name_halfwidth TEXT,"
            + " township TEXT, street TEXT, area TEXT, number TEXT)");
    // Empty-street row: street NULL, located by area 介壽新村.
    insert(1, 24.0751, 120.5351, "彰化縣彰化市介壽里介壽新村１號", "彰化縣彰化市介壽里介壽新村1號", "彰化市", null, "介壽新村", "1號");
    // Another empty-street row, blank ('' not NULL) street, area 十甲巷.
    insert(2, 24.0760, 120.5360, "彰化縣彰化市十甲里十甲巷30號", "彰化縣彰化市十甲里十甲巷30號", "彰化市", "", "十甲巷", "30號");
    // A normal streeted row in the same district — must stay matchable & unchanged.
    insert(
        3, 24.0700, 120.5300, "彰化縣彰化市中山路二段100號", "彰化縣彰化市中山路二段100號", "彰化市", "中山路二段", null, "100號");
    facade = new SqliteAddressDatabase(db);
  }

  private void insert(
      int id,
      double lat,
      double lon,
      String dn,
      String dnHw,
      String township,
      String street,
      String area,
      String number) {
    db.execSQL(
        "INSERT INTO places"
            + " (id, lat, lon, display_name, display_name_halfwidth, township, street, area, number)"
            + " VALUES (?,?,?,?,?,?,?,?,?)",
        new Object[] {id, lat, lon, dn, dnHw, township, street, area, number});
  }

  @After
  public void tearDown() {
    if (facade != null) facade.close();
  }

  private List<AddressCandidate> search(String fragment) {
    return facade.streetCandidates(
        "彰化市", StreetTextNormaliser.fold(fragment), ANCHOR_LAT, ANCHOR_LON, 50);
  }

  @Test
  public void emptyStreetRowFoundByAreaName() {
    List<AddressCandidate> results = search("介壽新村");
    assertThat(results).hasSize(1);
    assertThat(results.get(0).displayName()).isEqualTo("彰化縣彰化市介壽里介壽新村１號");
    // The area surfaces in the locator slot so the list + ranker treat it like a street.
    assertThat(results.get(0).street()).isEqualTo("介壽新村");
  }

  @Test
  public void blankStreetRowFoundByAreaPrefix() {
    List<AddressCandidate> results = search("十甲巷");
    assertThat(results).hasSize(1);
    assertThat(results.get(0).street()).isEqualTo("十甲巷");
  }

  @Test
  public void streetedRowStillMatchesAndAreaDoesNotLeak() {
    List<AddressCandidate> results = search("中山路");
    assertThat(results).hasSize(1);
    assertThat(results.get(0).street()).isEqualTo("中山路二段");
    // The empty-street rows' area must NOT be returned for a 中山路 query.
    assertThat(results).noneMatch(c -> c.street().contains("新村"));
  }
}
