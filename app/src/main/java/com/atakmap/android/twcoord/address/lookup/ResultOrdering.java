package com.atakmap.android.twcoord.address.lookup;

/**
 * Native Address candidate ordering, persisted by name in {@code PreferenceStore} under {@code
 * pref_search_result_ordering}.
 *
 * <ul>
 *   <li>{@link #DISTANCE} — nearest the anchor first. The default.
 *   <li>{@link #MOST_SIMILAR} — best textual match to the query first, ties broken by distance.
 * </ul>
 */
public enum ResultOrdering {
  MOST_SIMILAR,
  DISTANCE;

  /** Parse a persisted name, falling back to {@link #DISTANCE} on null/unknown (FR-003). */
  public static ResultOrdering fromName(String name) {
    if (name == null) return DISTANCE;
    try {
      return ResultOrdering.valueOf(name);
    } catch (IllegalArgumentException e) {
      return DISTANCE;
    }
  }
}
