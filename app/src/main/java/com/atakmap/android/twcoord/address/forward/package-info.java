/**
 * Feature 006 forward-search funnel: the county → 鄉鎮市區 → street → pin state machine ({@link
 * com.atakmap.android.twcoord.address.forward.ForwardSearchController}) plus its value types
 * ({@link com.atakmap.android.twcoord.address.forward.CountySource}, {@link
 * com.atakmap.android.twcoord.address.forward.ForwardSearchQuery}, {@link
 * com.atakmap.android.twcoord.address.forward.AddressCandidate}) and the street-text folding helper
 * ({@link com.atakmap.android.twcoord.address.forward.StreetTextNormaliser}).
 *
 * <p>Pure logic — the {@code ForwardSearchReceiver} DropDownReceiver is a thin view over the
 * controller. County selection defaults to the map centre; street matching is substring (incl. the
 * {@code 段} suffix) with 臺↔台 + fullwidth/halfwidth folding; results rank by distance to the
 * anchor. No place DB is opened until the street stage (FR-008 / SC-007).
 */
package com.atakmap.android.twcoord.address.forward;
