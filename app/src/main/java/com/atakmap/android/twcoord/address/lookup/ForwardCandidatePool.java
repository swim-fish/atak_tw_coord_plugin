package com.atakmap.android.twcoord.address.lookup;

/** Bounded database retrieval categories used to compose one forward-address shortlist. */
public enum ForwardCandidatePool {
  EXACT,
  TEXT_PREFIX,
  NUMERIC_NEAREST,
  DISTANCE,
  FALLBACK
}
