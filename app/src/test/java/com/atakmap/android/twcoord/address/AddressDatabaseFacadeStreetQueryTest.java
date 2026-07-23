package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import android.database.sqlite.SQLiteDatabase;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressDraft;
import com.atakmap.android.twcoord.address.lookup.AddressInputMode;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Feature 006 T023 — district-scoped street query against the real {@code
 * places-taichung-fixture.sqlite} via the production {@link SqliteAddressDatabase#streetCandidates}
 * path (Robolectric SQLiteDatabase = xerial, R*Tree enabled).
 *
 * <p>大甲區 anchor (24.3486, 120.6225) for ranking.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class AddressDatabaseFacadeStreetQueryTest {

  private static final Path FIXTURE =
      Paths.get("src/test/resources/fixtures/places-taichung-fixture.sqlite");
  private static final double DAJIA_LAT = 24.3486;
  private static final double DAJIA_LON = 120.6225;

  private SqliteAddressDatabase facade;

  @Before
  public void setUp() {
    Assume.assumeTrue(
        "places-taichung-fixture.sqlite present (run scripts/build_test_fixtures.py)",
        Files.exists(FIXTURE));
    SQLiteDatabase db =
        SQLiteDatabase.openDatabase(
            FIXTURE.toAbsolutePath().toString(), null, SQLiteDatabase.OPEN_READONLY);
    facade = new SqliteAddressDatabase(db);
  }

  @After
  public void tearDown() {
    if (facade != null) facade.close();
  }

  private List<AddressCandidate> search(String fragment) {
    return facade.streetCandidates(
        "大甲區", StreetTextNormaliser.fold(fragment), DAJIA_LAT, DAJIA_LON, 50);
  }

  @Test
  public void zhongshanRoadScopedToDajiaIncludesSegments() {
    List<AddressCandidate> results = search("中山路");
    assertThat(results).isNotEmpty();
    // Every result's street is in the 中山路 family (incl. 一段/二段).
    assertThat(results).allMatch(c -> StreetTextNormaliser.fold(c.street()).startsWith("中山路"));
    boolean hasSegment = results.stream().anyMatch(c -> c.street().contains("段"));
    assertThat(hasSegment).isTrue();
  }

  @Test
  public void xiangShangRoadIsNonEmptyProvingSubstringNotEquals() {
    // 向上路 exists only as 一段…九段 — a bare `=` would return zero. (向上路 lives in 西區 in the
    // fixture, so query that district.)
    List<AddressCandidate> results =
        facade.streetCandidates("西區", StreetTextNormaliser.fold("向上路"), 24.146, 120.671, 50);
    assertThat(results).isNotEmpty();
    assertThat(results).allMatch(c -> StreetTextNormaliser.fold(c.street()).startsWith("向上路"));
  }

  @Test
  public void taiwanBoulevardGlyphFoldMatchesGazettedTai() {
    // Operator types 台灣大道; stored rows are 臺灣大道… — app re-fold must match (西區).
    List<AddressCandidate> results =
        facade.streetCandidates("西區", StreetTextNormaliser.fold("台灣大道"), 24.146, 120.671, 50);
    assertThat(results).isNotEmpty();
    assertThat(results).allMatch(c -> StreetTextNormaliser.fold(c.street()).startsWith("台灣大道"));
  }

  @Test
  public void wrongDistrictReturnsEmpty() {
    List<AddressCandidate> results =
        facade.streetCandidates("不存在區", "中山路", DAJIA_LAT, DAJIA_LON, 50);
    assertThat(results).isEmpty();
  }

  @Test
  public void resultsRankedByDistanceAndCapped() {
    List<AddressCandidate> results = facade.streetCandidates("大甲區", "中山路", DAJIA_LAT, DAJIA_LON, 5);
    assertThat(results.size()).isLessThanOrEqualTo(5);
    for (int i = 1; i < results.size(); i++) {
      assertThat(results.get(i).distanceMeters())
          .isGreaterThanOrEqualTo(results.get(i - 1).distanceMeters());
    }
  }

  @Test
  public void emptyDistrictReturnsEmpty() {
    assertThat(facade.streetCandidates(null, "中山路", DAJIA_LAT, DAJIA_LON, 50)).isEmpty();
    assertThat(facade.streetCandidates("", "中山路", DAJIA_LAT, DAJIA_LON, 50)).isEmpty();
  }

  @Test
  public void closedDbReturnsEmptyNoThrow() {
    facade.close();
    assertThat(search("中山路")).isEmpty(); // no throw
  }

  @Test
  public void fullAddressCandidatesClassifyExactnessWithoutNearestNumberPromotion() {
    TaiwanAddressParser parser = new TaiwanAddressParser();
    AddressDraft draft = parser.parse("臺中市大甲區中山路1號", 1L, AddressInputMode.FULL);

    List<AddressCandidate> results =
        facade.fullAddressCandidates(
            draft, new Wgs84(DAJIA_LAT, DAJIA_LON, 1L, Wgs84.Source.MAP_CENTRE), 20);

    assertThat(results).hasSizeLessThanOrEqualTo(20);
    assertThat(results)
        .allMatch(
            candidate ->
                candidate.matchKind() == AddressMatchKind.EXACT
                    || candidate.matchKind() == AddressMatchKind.PARTIAL);
    assertThat(results)
        .noneMatch(
            candidate ->
                candidate.matchKind() == AddressMatchKind.EXACT
                    && !parser
                        .normalize(candidate.displayAddress())
                        .equals(draft.normalizedAddress()));
  }

  @Test
  public void copiedReverseAddressFindsExactRecordThroughStreetAndNumberFunnel() {
    TaiwanAddressParser parser = new TaiwanAddressParser();
    AddressDraft draft = parser.parse("台中市大甲區大甲里中山路一段８４４之１號", 2L, AddressInputMode.FULL);

    List<AddressCandidate> results =
        facade.fullAddressCandidates(
            draft, new Wgs84(23.7, 120.9, 1L, Wgs84.Source.MAP_CENTRE), 20);

    assertThat(results)
        .anyMatch(
            candidate ->
                candidate.matchKind() == AddressMatchKind.EXACT
                    && parser
                        .normalize(candidate.displayAddress())
                        .equals(draft.normalizedAddress()));
  }

  @Test
  public void addressWithoutVillageStillReturnsStreetAndNumberCandidate() {
    TaiwanAddressParser parser = new TaiwanAddressParser();
    AddressDraft draft = parser.parse("台中市大甲區中山路一段８４４之１號", 3L, AddressInputMode.FULL);

    List<AddressCandidate> results =
        facade.fullAddressCandidates(
            draft, new Wgs84(23.7, 120.9, 1L, Wgs84.Source.MAP_CENTRE), 20);

    assertThat(results).hasSize(1);
    assertThat(results.get(0).displayAddress()).isEqualTo("台中市大甲區大甲里中山路一段８４４之１號");
    assertThat(results.get(0).matchKind()).isEqualTo(AddressMatchKind.EXACT);
  }

  @Test
  public void omittedVillageKeepsDuplicateSemanticMatchesAmbiguous() {
    AddressDatabaseFacade duplicateFacade =
        new AddressDatabaseFacade() {
          @Override
          public GeneratorMetadata readMetadata() {
            return null;
          }

          @Override
          public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
            return null;
          }

          @Override
          public List<AddressCandidate> streetCandidates(
              String district,
              String foldedFragment,
              double anchorLat,
              double anchorLon,
              int limit) {
            return Arrays.asList(
                new AddressCandidate(
                    24.1, 120.6, "台中市大甲區第一里中山路一段１號", "台中市大甲區第一里中山路一段1號", "中山路一段", "１號", 10),
                new AddressCandidate(
                    24.2, 120.7, "台中市大甲區第二里中山路一段１號", "台中市大甲區第二里中山路一段1號", "中山路一段", "１號", 20));
          }

          @Override
          public void close() {}
        };
    AddressDraft draft =
        new TaiwanAddressParser().parse("台中市大甲區中山路一段１號", 4L, AddressInputMode.FULL);

    List<AddressCandidate> results =
        duplicateFacade.fullAddressCandidates(
            draft, new Wgs84(23.7, 120.9, 1L, Wgs84.Source.MAP_CENTRE), 20);

    assertThat(results).hasSize(2);
    assertThat(results).allMatch(candidate -> candidate.matchKind() == AddressMatchKind.EXACT);
  }
}
