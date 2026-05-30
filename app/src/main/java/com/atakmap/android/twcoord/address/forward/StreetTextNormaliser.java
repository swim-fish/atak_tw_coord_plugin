package com.atakmap.android.twcoord.address.forward;

/**
 * Folds operator-typed street text into the canonical form used for matching (FR-010):
 *
 * <ul>
 *   <li>{@code 臺} → {@code 台} (gazetted road names keep 臺, e.g. 臺灣大道; operators type 台)
 *   <li>fullwidth digits {@code ０-９} → halfwidth {@code 0-9}
 *   <li>{@code 之} → {@code -} (TGOS house-number separator)
 *   <li>trim leading/trailing whitespace
 * </ul>
 *
 * Applied to BOTH the query fragment and the candidate {@code street} before comparison, so a typed
 * {@code 台灣大道} matches the stored {@code 臺灣大道}. Idempotent. Never throws on {@code null}
 * (returns {@code ""}).
 */
public final class StreetTextNormaliser {

  private StreetTextNormaliser() {}

  public static String fold(String s) {
    if (s == null) return "";
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '臺') {
        b.append('台');
      } else if (c == '之') {
        b.append('-');
      } else if (c >= '０' && c <= '９') {
        b.append((char) ('0' + (c - '０')));
      } else {
        b.append(c);
      }
    }
    return b.toString().trim();
  }

  /**
   * The gazetted {@code 臺}-variant of a folded ({@code 台}-form) fragment, for building a SQL
   * {@code LIKE} that also matches stored gazetted road names ({@code 臺灣大道…}). SQLite {@code
   * LIKE} cannot fold glyphs, so the facade queries both {@link #fold}'s output and this variant;
   * {@link StreetCandidateRanker} then re-folds to confirm. Returns the input unchanged when it has
   * no {@code 台}.
   */
  public static String taiVariant(String foldedFragment) {
    if (foldedFragment == null || foldedFragment.indexOf('台') < 0) {
      return foldedFragment == null ? "" : foldedFragment;
    }
    return foldedFragment.replace('台', '臺');
  }
}
