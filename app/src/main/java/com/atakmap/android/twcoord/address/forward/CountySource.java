package com.atakmap.android.twcoord.address.forward;

/**
 * Provenance of a county selection in the forward-search funnel (data-model §2.3). Drives whether
 * the district stage pre-highlights the operator's own district (only for SELF / MAP_CENTER, since
 * only those carry a detected locality).
 */
public enum CountySource {
  /** From the map centre coordinate — the default seed when SELF and MAP_CENTER disagree. */
  MAP_CENTER,
  /** From the self-marker (GPS) coordinate. */
  SELF,
  /** Picked from the manual list (read from the boundary data, never hard-coded). */
  LIST
}
