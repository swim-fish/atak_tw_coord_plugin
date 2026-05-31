package com.atakmap.android.twcoord.address.forward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Feature 007 US1 — {@link StreetCandidateRanker#reorder} ordering semantics. */
public class StreetCandidateReorderTest {

  private static AddressCandidate c(String street, double distM) {
    return new AddressCandidate(0, 0, street, street, street, "", distM);
  }

  /**
   * A candidate with a distinct house number — for the house-number-aware MOST_SIMILAR overload.
   */
  private static AddressCandidate cn(String street, String number, double distM) {
    String display = street + number;
    return new AddressCandidate(0, 0, display, display, street, number, distM);
  }

  @Test
  public void distanceOrderingIsAscendingByDistance() {
    AddressCandidate near = c("乙路", 100);
    AddressCandidate far = c("甲路", 500);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(Arrays.asList(far, near), ResultOrdering.DISTANCE, "路");
    assertThat(out).containsExactly(near, far);
  }

  @Test
  public void mostSimilarRanksExactThenPrefixThenSubstringThenNone() {
    AddressCandidate exact = c("中山路", 900);
    AddressCandidate prefix = c("中山路一段", 800);
    AddressCandidate substring = c("新中山路", 700);
    AddressCandidate none = c("民生東路", 100);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(
            Arrays.asList(none, substring, prefix, exact), ResultOrdering.MOST_SIMILAR, "中山路");
    assertThat(out).containsExactly(exact, prefix, substring, none);
  }

  @Test
  public void mostSimilarBreaksTiesByDistance() {
    AddressCandidate exactFar = c("中山路", 500);
    AddressCandidate exactNear = c("中山路", 100);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(
            Arrays.asList(exactFar, exactNear), ResultOrdering.MOST_SIMILAR, "中山路");
    assertThat(out).containsExactly(exactNear, exactFar);
  }

  @Test
  public void mostSimilarFoldsTaiAndWidthVariants() {
    AddressCandidate gazetted = c("臺灣大道", 900); // stored 臺
    AddressCandidate other = c("民生路", 100);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(
            Arrays.asList(other, gazetted), ResultOrdering.MOST_SIMILAR, "台灣大道"); // typed 台
    assertThat(out).containsExactly(gazetted, other);
  }

  @Test
  public void blankFragmentDegradesToDistance() {
    // The nearer candidate has the LONGER name so a stray leftover-length tiebreak would order it
    // AFTER the farther short-named one — this fixture fails unless empty fragment is pure
    // distance.
    AddressCandidate near = c("中山一路", 100); // closer, longer name
    AddressCandidate far = c("甲路", 500); // farther, shorter name
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(Arrays.asList(far, near), ResultOrdering.MOST_SIMILAR, "");
    assertThat(out).containsExactly(near, far);
  }

  @Test
  public void blankFragmentAfterTrimDegradesToDistance() {
    // Whitespace-only fragment trims to empty → same pure-distance contract.
    AddressCandidate near = c("中山一路", 100);
    AddressCandidate far = c("甲路", 500);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(Arrays.asList(far, near), ResultOrdering.MOST_SIMILAR, "  ");
    assertThat(out).containsExactly(near, far);
  }

  @Test
  public void mostSimilarFloatsNumericallyClosestHouseNumberFirst() {
    // Real-world repro: 五權西路 + 2號 — every candidate shares the street segment, so the street-only
    // bands tie and MOST_SIMILAR used to degrade to distance (the "no effect" bug). With the typed
    // house number it must surface the numerically-closest number, regardless of distance.
    AddressCandidate n2c = cn("五權西路一段", "2C號", 800); // leading 2 — closest, but farthest
    AddressCandidate n12 = cn("五權西路一段", "12號", 200);
    AddressCandidate n20 = cn("五權西路一段", "20號", 100);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(
            Arrays.asList(n20, n12, n2c), ResultOrdering.MOST_SIMILAR, "五權西路", "2號");
    assertThat(out).containsExactly(n2c, n12, n20);
  }

  @Test
  public void mostSimilarEqualHouseNumberBreaksTieByDistance() {
    // Two segments both have a number-2 address → equal numeric proximity → nearer wins.
    AddressCandidate seg1Far = cn("五權西路一段", "2C號", 500);
    AddressCandidate seg2Near = cn("五權西路二段", "2號", 100);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(
            Arrays.asList(seg1Far, seg2Near), ResultOrdering.MOST_SIMILAR, "五權西路", "2號");
    assertThat(out).containsExactly(seg2Near, seg1Far);
  }

  @Test
  public void blankHouseNumberKeepsStreetOnlyBehaviour() {
    // 4-arg overload with a blank number must match the 3-arg street-only ranking exactly.
    AddressCandidate exact = cn("中山路", "1號", 900);
    AddressCandidate prefix = cn("中山路一段", "1號", 100);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(
            Arrays.asList(prefix, exact), ResultOrdering.MOST_SIMILAR, "中山路", "");
    assertThat(out).containsExactly(exact, prefix); // exact street match still wins over prefix
  }

  @Test
  public void nullOrderingTreatedAsDistanceAndInputNotMutated() {
    AddressCandidate near = c("乙路", 100);
    AddressCandidate far = c("甲路", 500);
    List<AddressCandidate> in = Arrays.asList(far, near);
    List<AddressCandidate> out = StreetCandidateRanker.reorder(in, null, "路");
    assertThat(out).containsExactly(near, far);
    assertThat(in).containsExactly(far, near); // input untouched
  }
}
