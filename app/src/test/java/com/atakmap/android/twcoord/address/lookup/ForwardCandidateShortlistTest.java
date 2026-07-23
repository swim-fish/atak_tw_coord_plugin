package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class ForwardCandidateShortlistTest {

  @Test
  public void exactCandidatesShortCircuitAllOtherPools() {
    List<AddressCandidate> selected =
        ForwardCandidateShortlist.select(
            Arrays.asList(exact("exact-1"), exact("exact-2")),
            candidates("text-", 20),
            candidates("numeric-", 20),
            candidates("distance-", 20),
            candidates("fallback-", 20),
            20);

    assertThat(selected)
        .extracting(AddressCandidate::candidateId)
        .containsExactly("exact-1", "exact-2");
  }

  @Test
  public void quotasThenSemanticBackfillProduceTwentyDistinctCandidates() {
    List<AddressCandidate> selected =
        ForwardCandidateShortlist.select(
            Collections.emptyList(),
            candidates("text-", 20),
            candidates("numeric-", 20),
            candidates("distance-", 20),
            candidates("fallback-", 20),
            20);

    assertThat(selected).hasSize(20);
    assertThat(selected.subList(0, 6))
        .extracting(AddressCandidate::candidateId)
        .containsExactly("text-0", "text-1", "text-2", "text-3", "text-4", "text-5");
    assertThat(selected.subList(6, 14))
        .extracting(AddressCandidate::candidateId)
        .containsExactly(
            "numeric-0",
            "numeric-1",
            "numeric-2",
            "numeric-3",
            "numeric-4",
            "numeric-5",
            "numeric-6",
            "numeric-7");
    assertThat(selected.subList(14, 18))
        .extracting(AddressCandidate::candidateId)
        .containsExactly("distance-0", "distance-1", "distance-2", "distance-3");
    assertThat(selected.subList(18, 20))
        .extracting(AddressCandidate::candidateId)
        .containsExactly("fallback-0", "fallback-1");
  }

  @Test
  public void missingDistancePoolIsBackfilledWithoutDuplicates() {
    List<AddressCandidate> text = candidates("text-", 20);
    List<AddressCandidate> numeric = new ArrayList<>(text.subList(0, 4));
    numeric.addAll(candidates("numeric-", 20));

    List<AddressCandidate> selected =
        ForwardCandidateShortlist.select(
            Collections.emptyList(),
            text,
            numeric,
            Collections.emptyList(),
            candidates("fallback-", 20),
            20);

    assertThat(selected).hasSize(20);
    assertThat(selected)
        .extracting(AddressCandidate::candidateId)
        .doesNotHaveDuplicates()
        .contains("text-6", "numeric-0", "fallback-0");
  }

  @Test
  public void visibleLimitCannotExceedTwenty() {
    List<AddressCandidate> selected =
        ForwardCandidateShortlist.select(
            Collections.emptyList(),
            candidates("text-", 30),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            200);

    assertThat(selected).hasSize(20);
  }

  private static List<AddressCandidate> candidates(String prefix, int count) {
    List<AddressCandidate> candidates = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      candidates.add(candidate(prefix + i, AddressMatchKind.PARTIAL));
    }
    return candidates;
  }

  private static AddressCandidate exact(String id) {
    return candidate(id, AddressMatchKind.EXACT);
  }

  private static AddressCandidate candidate(String id, AddressMatchKind kind) {
    return new AddressCandidate(
        id, id, id, new Wgs84(24.0, 120.0, 1L, Wgs84.Source.COT_TARGET), kind, 1.0, "臺中市", null);
  }
}
