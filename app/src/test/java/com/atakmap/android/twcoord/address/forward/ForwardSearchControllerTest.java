package com.atakmap.android.twcoord.address.forward;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.AddressDatabaseFacade;
import com.atakmap.android.twcoord.address.AddressRecord;
import com.atakmap.android.twcoord.address.GeneratorMetadata;
import com.atakmap.android.twcoord.address.boundary.LocalityResult;
import com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * Feature 006 T027 / T037 — the funnel controller. Uses a hand-built {@link TownshipBoundaryFacade}
 * (no SQLite) and a facade-open spy to assert the place DB is not opened until {@link
 * ForwardSearchController#search}.
 */
public class ForwardSearchControllerTest {

  // ---- map-centre default (US2 / FR-005) ----

  @Test
  public void mapCentreDefaultWhenSelfAndMapCentreDisagree() {
    FakeBoundary b = new FakeBoundary();
    b.put(24.08, 120.54, "彰化縣", "彰化市"); // map centre
    b.put(24.15, 120.68, "台中市", "西區"); // self
    ForwardSearchController c = new ForwardSearchController(b, county -> null);

    CountySeed seed = c.seedCounty(24.08, 120.54, 24.15, 120.68);

    assertThat(seed.defaultCounty()).isEqualTo("彰化縣");
    assertThat(seed.defaultSource()).isEqualTo(CountySource.MAP_CENTER);
    assertThat(seed.selfCounty()).isEqualTo("台中市");
    assertThat(c.state().county()).isEqualTo("彰化縣");
  }

  @Test
  public void sameCountyNoConflict() {
    FakeBoundary b = new FakeBoundary();
    b.put(24.14, 120.68, "台中市", "西區");
    b.put(24.15, 120.69, "台中市", "北區");
    ForwardSearchController c = new ForwardSearchController(b, county -> null);

    CountySeed seed = c.seedCounty(24.14, 120.68, 24.15, 120.69);

    assertThat(seed.defaultCounty()).isEqualTo("台中市");
    assertThat(seed.defaultSource()).isEqualTo(CountySource.MAP_CENTER);
  }

  @Test
  public void offshoreSeedHasNoDefault() {
    FakeBoundary b = new FakeBoundary(); // resolves nothing
    ForwardSearchController c = new ForwardSearchController(b, county -> null);

    CountySeed seed = c.seedCounty(24.0, 119.5, null, null);

    assertThat(seed.hasDefault()).isFalse();
    assertThat(c.state().county()).isNull();
  }

  // ---- county list from data (US2 / FR-006) ----

  @Test
  public void countyListComesFromBoundary() {
    FakeBoundary b = new FakeBoundary();
    b.counties = Arrays.asList("南投縣", "台中市", "彰化縣", "雲林縣");
    ForwardSearchController c = new ForwardSearchController(b, county -> null);

    assertThat(c.countyList()).containsExactly("南投縣", "台中市", "彰化縣", "雲林縣");
  }

  // ---- district pre-highlight (US1 / FR-007) ----

  @Test
  public void districtPreHighlightedForMapCentreSource() {
    FakeBoundary b = new FakeBoundary();
    b.put(24.14, 120.68, "台中市", "西區");
    b.districts.put("台中市", Arrays.asList("中區", "北區", "西區", "大甲區"));
    ForwardSearchController c = new ForwardSearchController(b, county -> null);

    c.seedCounty(24.14, 120.68, null, null);

    assertThat(c.suggestedDistrict()).isEqualTo("西區");
    assertThat(c.districts()).contains("西區", "大甲區");
  }

  @Test
  public void noSuggestedDistrictForListSource() {
    FakeBoundary b = new FakeBoundary();
    b.districts.put("台中市", Arrays.asList("西區", "大甲區"));
    ForwardSearchController c = new ForwardSearchController(b, county -> null);

    c.chooseCounty("台中市", CountySource.LIST);

    assertThat(c.suggestedDistrict()).isNull();
  }

  // ---- place DB not opened until search (FR-008 / SC-007) ----

  @Test
  public void placeDbNotOpenedUntilSearch() {
    FakeBoundary b = new FakeBoundary();
    b.put(24.34, 120.62, "台中市", "大甲區");
    b.districts.put("台中市", Arrays.asList("大甲區"));
    AtomicInteger opens = new AtomicInteger(0);
    StubFacade facade = new StubFacade();
    ForwardSearchController c =
        new ForwardSearchController(
            b,
            county -> {
              opens.incrementAndGet();
              return facade;
            });

    c.seedCounty(24.34, 120.62, null, null); // ①
    c.districts(); // ② list
    c.chooseDistrict("大甲區");
    assertThat(opens.get()).isEqualTo(0); // no place DB opened through ①②

    c.search("中山路", 10); // ③
    assertThat(opens.get()).isEqualTo(1); // opened exactly once at search
  }

  // ---- search returns the facade's candidates; empty/throw safe ----

  @Test
  public void searchReturnsFacadeCandidates() {
    FakeBoundary b = new FakeBoundary();
    b.districts.put("台中市", Arrays.asList("大甲區"));
    StubFacade facade = new StubFacade();
    facade.candidates =
        Arrays.asList(
            new AddressCandidate(24.34, 120.62, "台中市大甲區中山路一段1號", "", "中山路一段", "1號", 10),
            new AddressCandidate(24.35, 120.63, "台中市大甲區中山路二段5號", "", "中山路二段", "5號", 50));
    ForwardSearchController c = new ForwardSearchController(b, county -> facade);
    c.chooseCounty("台中市", CountySource.LIST);
    c.chooseDistrict("大甲區");

    List<AddressCandidate> r = c.search("中山路", 10);

    assertThat(r).hasSize(2);
    assertThat(facade.lastDistrict).isEqualTo("大甲區");
    assertThat(facade.lastFolded).isEqualTo("中山路");
  }

  @Test
  public void blankFragmentReturnsEmptyAndDoesNotOpenFacade() {
    FakeBoundary b = new FakeBoundary();
    AtomicInteger opens = new AtomicInteger(0);
    ForwardSearchController c =
        new ForwardSearchController(
            b,
            county -> {
              opens.incrementAndGet();
              return new StubFacade();
            });
    c.chooseCounty("台中市", CountySource.LIST);
    c.chooseDistrict("大甲區");

    assertThat(c.search("   ", 10)).isEmpty();
    assertThat(opens.get()).isEqualTo(0);
  }

  @Test
  public void facadeNullReturnsEmptyNoThrow() {
    FakeBoundary b = new FakeBoundary();
    ForwardSearchController c = new ForwardSearchController(b, county -> null);
    c.chooseCounty("台中市", CountySource.LIST);
    c.chooseDistrict("大甲區");

    assertThat(c.search("中山路", 10)).isEmpty(); // facade null → empty, no throw
  }

  // ---- house number (US4 / T037) ----

  @Test
  public void houseNumberNarrowsToMatchingNumber() {
    FakeBoundary b = new FakeBoundary();
    StubFacade facade = new StubFacade();
    facade.candidates =
        Arrays.asList(
            new AddressCandidate(24.34, 120.62, "向上路一段1號", "", "向上路一段", "1號", 10),
            new AddressCandidate(24.35, 120.63, "向上路一段123號", "", "向上路一段", "123號", 50),
            new AddressCandidate(24.36, 120.64, "向上路二段5號", "", "向上路二段", "5號", 90));
    ForwardSearchController c = new ForwardSearchController(b, county -> facade);
    c.chooseCounty("台中市", CountySource.LIST);
    c.chooseDistrict("西區");
    c.search("向上路", 0);

    List<AddressCandidate> r = c.withHouseNumber("123", 10);

    assertThat(r).hasSize(1);
    assertThat(r.get(0).number()).isEqualTo("123號");
  }

  @Test
  public void blankHouseNumberFallsBackToNearest() {
    FakeBoundary b = new FakeBoundary();
    StubFacade facade = new StubFacade();
    facade.candidates =
        Arrays.asList(
            new AddressCandidate(24.34, 120.62, "向上路一段1號", "", "向上路一段", "1號", 10),
            new AddressCandidate(24.35, 120.63, "向上路二段5號", "", "向上路二段", "5號", 50));
    ForwardSearchController c = new ForwardSearchController(b, county -> facade);
    c.chooseCounty("台中市", CountySource.LIST);
    c.chooseDistrict("西區");
    c.search("向上路", 0);

    List<AddressCandidate> r = c.withHouseNumber("", 10);

    assertThat(r).hasSize(2); // nearest-by-distance list
    assertThat(r.get(0).distanceMeters()).isLessThanOrEqualTo(r.get(1).distanceMeters());
  }

  @Test
  public void unmatchedHouseNumberFallsBackToNearest() {
    FakeBoundary b = new FakeBoundary();
    StubFacade facade = new StubFacade();
    facade.candidates =
        Collections.singletonList(
            new AddressCandidate(24.34, 120.62, "向上路一段1號", "", "向上路一段", "1號", 10));
    ForwardSearchController c = new ForwardSearchController(b, county -> facade);
    c.chooseCounty("台中市", CountySource.LIST);
    c.chooseDistrict("西區");
    c.search("向上路", 0);

    List<AddressCandidate> r = c.withHouseNumber("99999", 10);

    assertThat(r).hasSize(1); // no exact hit → fall back to the base list
  }

  // ----------------------------------------------------------------------
  // Test doubles
  // ----------------------------------------------------------------------

  /** Hand-built boundary facade keyed by exact (lat,lon) → (county,district). */
  private static final class FakeBoundary implements TownshipBoundaryFacade {
    private final List<double[]> pts = new ArrayList<>();
    private final List<String[]> vals = new ArrayList<>();
    List<String> counties = new ArrayList<>();
    final java.util.Map<String, List<String>> districts = new java.util.HashMap<>();

    void put(double lat, double lon, String county, String district) {
      pts.add(new double[] {lat, lon});
      vals.add(new String[] {county, district});
    }

    @Override
    public LocalityResult localityAt(double lat, double lon, double snapMeters) {
      for (int i = 0; i < pts.size(); i++) {
        if (Math.abs(pts.get(i)[0] - lat) < 1e-6 && Math.abs(pts.get(i)[1] - lon) < 1e-6) {
          return LocalityResult.full(vals.get(i)[0], vals.get(i)[1]);
        }
      }
      return LocalityResult.none();
    }

    @Override
    public List<String> counties() {
      return counties;
    }

    @Override
    public List<String> districtsOf(String county) {
      return districts.getOrDefault(county, Collections.emptyList());
    }

    @Override
    public void close() {}
  }

  /** Records the last streetCandidates args + returns a canned list. */
  private static final class StubFacade implements AddressDatabaseFacade {
    List<AddressCandidate> candidates = new ArrayList<>();
    String lastDistrict;
    String lastFolded;

    @Override
    public GeneratorMetadata readMetadata() {
      return new GeneratorMetadata(
          2, "tgos", "stub", "115-01", null, null, null, 0L, Collections.emptyMap());
    }

    @Override
    public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
      return null;
    }

    @Override
    public List<AddressCandidate> streetCandidates(
        String district, String foldedFragment, double anchorLat, double anchorLon, int limit) {
      lastDistrict = district;
      lastFolded = foldedFragment;
      return new ArrayList<>(candidates);
    }

    @Override
    public void close() {}
  }
}
