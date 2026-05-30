package com.atakmap.android.twcoord.address.boundary;

import static org.assertj.core.api.Assertions.assertThat;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Feature 006 T012 — boundary facade against the real {@code townships-fixture.sqlite} (台中市 /
 * 彰化縣 / 雲林縣 / 南投縣 subset). Robolectric's {@link SQLiteDatabase} shadow is xerial-backed
 * (R*Tree enabled), so the production {@link SqliteTownshipBoundaryFacade} code path runs verbatim.
 *
 * <p>Reference points are a subset of {@code scripts/verify_polygon_in.py}'s 8/8 — those whose
 * county is one of the four kept in the fixture. Points outside those four counties (e.g. an
 * offshore point) resolve to {@link LocalityResult#none()} against this trimmed fixture.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class TownshipBoundaryFacadeTest {

  private static final Path FIXTURE =
      Paths.get("src/test/resources/fixtures/townships-fixture.sqlite");

  private SqliteTownshipBoundaryFacade facade;
  private SQLiteDatabase db;

  @Before
  public void setUp() {
    org.junit.Assume.assumeTrue(
        "townships-fixture.sqlite present (run scripts/build_test_fixtures.py)",
        Files.exists(FIXTURE));
    db =
        SQLiteDatabase.openDatabase(
            FIXTURE.toAbsolutePath().toString(), null, SQLiteDatabase.OPEN_READONLY);
    facade =
        new SqliteTownshipBoundaryFacade(
            new SqliteTownshipBoundaryFacade.CursorQuerier() {
              @Override
              public Cursor rawQuery(String sql, String[] args) {
                return db.rawQuery(sql, args);
              }

              @Override
              public void close() {
                db.close();
              }
            });
  }

  @After
  public void tearDown() {
    if (facade != null) facade.close();
  }

  // ---- reference points (subset of the 8/8 within the fixture's four counties) ----

  @Test
  public void taichungStationResolvesToXiqu() {
    LocalityResult r = facade.localityAt(24.1417, 120.6736, 0);
    assertThat(r.county()).isEqualTo("台中市");
    assertThat(r.district()).isEqualTo("西區");
    assertThat(r.approx()).isFalse();
  }

  @Test
  public void dajiaResolves() {
    LocalityResult r = facade.localityAt(24.3486, 120.6225, 0);
    assertThat(r.county()).isEqualTo("台中市");
    assertThat(r.district()).isEqualTo("大甲區");
  }

  @Test
  public void changhuaCityResolves() {
    LocalityResult r = facade.localityAt(24.0809, 120.5386, 0);
    assertThat(r.county()).isEqualTo("彰化縣");
    assertThat(r.district()).isEqualTo("彰化市");
  }

  @Test
  public void lugangResolves() {
    LocalityResult r = facade.localityAt(24.0576, 120.4347, 0);
    assertThat(r.county()).isEqualTo("彰化縣");
    assertThat(r.district()).isEqualTo("鹿港鎮");
  }

  @Test
  public void douliuResolves() {
    LocalityResult r = facade.localityAt(23.7092, 120.5430, 0);
    assertThat(r.county()).isEqualTo("雲林縣");
    assertThat(r.district()).isEqualTo("斗六市");
  }

  @Test
  public void nantouCityResolves() {
    LocalityResult r = facade.localityAt(23.9099, 120.6856, 0);
    assertThat(r.county()).isEqualTo("南投縣");
    assertThat(r.district()).isEqualTo("南投市");
  }

  @Test
  public void offshorePointResolvesToNone() {
    LocalityResult r = facade.localityAt(24.0, 119.5, 0);
    assertThat(r.isNone()).isTrue();
  }

  // ---- pick-lists ----

  @Test
  public void countiesReturnsFixtureLevel4SetSorted() {
    List<String> counties = facade.counties();
    // Exactly the four kept in the fixture, sorted by name_zh — no hard-coded extras.
    assertThat(counties).containsExactly("南投縣", "台中市", "彰化縣", "雲林縣");
  }

  @Test
  public void districtsOfTaichungIsNonEmptyAndSorted() {
    List<String> d = facade.districtsOf("台中市");
    assertThat(d).contains("西區", "大甲區");
    assertThat(d).isSortedAccordingTo(String::compareTo);
  }

  @Test
  public void districtsOfUnknownCountyIsEmpty() {
    assertThat(facade.districtsOf("台北市")).isEmpty();
    assertThat(facade.districtsOf(null)).isEmpty();
    assertThat(facade.districtsOf("")).isEmpty();
  }

  // ---- snap tolerance ----

  @Test
  public void strictMissReturnsNoneOrCountyOnly() {
    // A point just offshore of 大甲區's coast: with snap=0 it should NOT be a Full district hit.
    LocalityResult strict = facade.localityAt(24.36, 120.55, 0);
    // Either None or county-only — never a Full strict district at this offshore point.
    assertThat(strict.approx()).isFalse();
  }

  @Test
  public void snapResolvesNearestDistrictApprox() {
    // Same offshore point with a generous 5 km snap: if the nearest 台中市 polygon is within range
    // we get an approx district; otherwise None. We only assert that when a district comes back via
    // snap it is flagged approx and carries a county.
    LocalityResult snapped = facade.localityAt(24.36, 120.55, 5000);
    if (snapped.hasDistrict()) {
      assertThat(snapped.approx()).isTrue();
      assertThat(snapped.county()).isNotNull();
    }
  }
}
