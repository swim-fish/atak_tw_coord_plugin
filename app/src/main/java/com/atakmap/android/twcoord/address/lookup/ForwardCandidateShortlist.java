package com.atakmap.android.twcoord.address.lookup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministically composes bounded SQL pools into the candidate dialog's visible shortlist. */
public final class ForwardCandidateShortlist {

  public static final int SQL_POOL_LIMIT = 20;
  private static final int TEXT_PREFIX_QUOTA = 6;
  private static final int NUMERIC_NEAREST_QUOTA = 8;
  private static final int DISTANCE_QUOTA = 4;
  private static final int FALLBACK_QUOTA = 2;

  private ForwardCandidateShortlist() {}

  /**
   * Returns exact candidates exclusively when present. Otherwise fills the fixed category quotas,
   * deduplicates stable candidate identities, then backfills in semantic pool order until the
   * caller's visible limit is reached.
   */
  public static List<AddressCandidate> select(
      List<AddressCandidate> exact,
      List<AddressCandidate> textPrefix,
      List<AddressCandidate> numericNearest,
      List<AddressCandidate> distance,
      List<AddressCandidate> fallback,
      int limit) {
    if (limit <= 0) return new ArrayList<>();
    int effectiveLimit = Math.min(limit, SQL_POOL_LIMIT);

    Map<String, AddressCandidate> exactDistinct = new LinkedHashMap<>();
    add(exactDistinct, exact, effectiveLimit);
    if (!exactDistinct.isEmpty()) {
      return new ArrayList<>(exactDistinct.values());
    }

    Map<String, AddressCandidate> selected = new LinkedHashMap<>();
    add(selected, textPrefix, Math.min(TEXT_PREFIX_QUOTA, effectiveLimit));
    add(
        selected,
        numericNearest,
        Math.min(NUMERIC_NEAREST_QUOTA, effectiveLimit - selected.size()));
    add(selected, distance, Math.min(DISTANCE_QUOTA, effectiveLimit - selected.size()));
    add(selected, fallback, Math.min(FALLBACK_QUOTA, effectiveLimit - selected.size()));

    add(selected, textPrefix, effectiveLimit - selected.size());
    add(selected, numericNearest, effectiveLimit - selected.size());
    add(selected, distance, effectiveLimit - selected.size());
    add(selected, fallback, effectiveLimit - selected.size());
    return new ArrayList<>(selected.values());
  }

  private static void add(
      Map<String, AddressCandidate> target, List<AddressCandidate> candidates, int count) {
    if (candidates == null || count <= 0) return;
    int added = 0;
    for (AddressCandidate candidate : candidates) {
      if (candidate == null) continue;
      if (target.putIfAbsent(candidate.candidateId(), candidate) == null) {
        added++;
        if (added >= count) return;
      }
    }
  }
}
