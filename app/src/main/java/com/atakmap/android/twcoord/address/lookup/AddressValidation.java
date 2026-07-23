package com.atakmap.android.twcoord.address.lookup;

/** Address input and lookup lifecycle state. */
public enum AddressValidation {
  EMPTY,
  PARTIAL,
  READY_TO_LOOKUP,
  LOOKUP_PENDING,
  NO_DATASET,
  NO_MATCH,
  AMBIGUOUS,
  RESOLVED,
  READ_ONLY,
  FAILURE,
  DISPOSED
}
