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
    AddressCandidate near = c("乙路", 100);
    AddressCandidate far = c("甲路", 500);
    List<AddressCandidate> out =
        StreetCandidateRanker.reorder(Arrays.asList(far, near), ResultOrdering.MOST_SIMILAR, "");
    assertThat(out).containsExactly(near, far);
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
