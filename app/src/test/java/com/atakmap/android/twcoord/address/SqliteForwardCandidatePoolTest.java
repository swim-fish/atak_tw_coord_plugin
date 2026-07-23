package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import android.database.sqlite.SQLiteDatabase;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressDraft;
import com.atakmap.android.twcoord.address.lookup.AddressInputMode;
import com.atakmap.android.twcoord.address.lookup.ForwardCandidatePool;
import com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class SqliteForwardCandidatePoolTest {

  private SqliteAddressDatabase facade;
  private AddressDraft draft;

  @Before
  public void setUp() {
    SQLiteDatabase db = SQLiteDatabase.create(null);
    db.execSQL(
        "CREATE TABLE places ("
            + "id INTEGER PRIMARY KEY, lat REAL, lon REAL, name TEXT,"
            + "display_name TEXT, display_name_halfwidth TEXT,"
            + "township TEXT, street TEXT, area TEXT, lane TEXT, alley TEXT, number TEXT)");
    for (int i = 1; i <= 35; i++) {
      insert(db, i, "", "", i + "號", 24.1600 + i / 10000.0, 120.6470 + i / 10000.0);
    }
    insert(db, 90, "", "", "90號", 24.1700, 120.6570);
    insert(db, 92, "", "", "92號", 24.1702, 120.6572);
    insert(db, 96, "", "", "96號", 24.1706, 120.6576);
    insert(db, 99, "", "", "99號", 24.1709, 120.6579);
    insert(db, 101, "306巷", "39弄", "9號", 24.1000, 120.6000);
    facade = new SqliteAddressDatabase(db);
    draft = new TaiwanAddressParser().parse("臺中市西屯區臺灣大道三段9號", 1L, AddressInputMode.FULL);
  }

  @After
  public void tearDown() {
    if (facade != null) facade.close();
  }

  @Test
  public void everySqlPoolIsHardLimitedToTwentyRows() {
    Wgs84 anchor = new Wgs84(24.1609, 120.6479, 1L, Wgs84.Source.MAP_CENTRE);

    for (ForwardCandidatePool pool : ForwardCandidatePool.values()) {
      List<AddressCandidate> rows =
          facade.forwardCandidatePool(draft, StreetTextNormaliser.fold("臺灣大道"), anchor, pool, 200);

      assertThat(rows).hasSizeLessThanOrEqualTo(20);
    }
  }

  @Test
  public void textPrefixAndNumericPoolsUseThePrimaryAddressTail() {
    Wgs84 anchor = new Wgs84(24.1609, 120.6479, 1L, Wgs84.Source.MAP_CENTRE);

    List<AddressCandidate> text =
        facade.forwardCandidatePool(
            draft, StreetTextNormaliser.fold("臺灣大道"), anchor, ForwardCandidatePool.TEXT_PREFIX, 20);
    List<AddressCandidate> numeric =
        facade.forwardCandidatePool(
            draft,
            StreetTextNormaliser.fold("臺灣大道"),
            anchor,
            ForwardCandidatePool.NUMERIC_NEAREST,
            20);

    assertThat(text.get(0).number()).isEqualTo("9號");
    assertThat(text.subList(0, 4))
        .extracting(AddressCandidate::number)
        .containsExactly("9號", "90號", "92號", "96號");
    assertThat(numeric.subList(0, 4))
        .extracting(AddressCandidate::number)
        .containsExactly("9號", "8號", "10號", "7號");
    assertThat(text.get(0).displayAddress()).doesNotContain("巷", "弄");
  }

  private static void insert(
      SQLiteDatabase db, int id, String lane, String alley, String number, double lat, double lon) {
    String tail = lane + alley + number;
    String name = "臺灣大道三段" + tail;
    String display = "台中市西屯區惠來里" + name;
    db.execSQL(
        "INSERT INTO places"
            + "(id,lat,lon,name,display_name,display_name_halfwidth,township,street,area,lane,alley,number)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        new Object[] {
          id, lat, lon, name, display, display, "西屯區", "臺灣大道三段", "", lane, alley, number
        });
  }
}
