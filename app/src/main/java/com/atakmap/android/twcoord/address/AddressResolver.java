package com.atakmap.android.twcoord.address;

/**
 * Pure compute wrapper around {@link AddressDatabaseFacade#nearestWithin}. No threading, no caching
 * — {@link AddressSubsystem} owns those concerns. Two construction params:
 *
 * <ul>
 *   <li>{@code facade} — the database to query. {@code null} signals "no active dataset" and {@link
 *       #lookup} returns {@link AddressLookupResult.NoDataset}.
 *   <li>{@code radiusMeters} — the search radius. Default in production is 500 m per {@code
 *       research.md §R4}; tests may pass other values.
 * </ul>
 *
 * <p>Exceptions thrown by the facade are caught and mapped to {@link AddressLookupResult.Empty} so
 * a corrupt dataset cannot escape into the caller's catch-block (Constitution VI).
 */
public final class AddressResolver {

  private final AddressDatabaseFacade facade;
  private final double radiusMeters;

  public AddressResolver(AddressDatabaseFacade facade, double radiusMeters) {
    this.facade = facade;
    this.radiusMeters = radiusMeters;
  }

  public AddressLookupResult lookup(double lat, double lon) {
    if (facade == null) {
      return AddressLookupResult.noDataset();
    }
    AddressRecord record;
    try {
      record = facade.nearestWithin(lat, lon, radiusMeters);
    } catch (Throwable t) {
      return AddressLookupResult.empty();
    }
    if (record == null) {
      return AddressLookupResult.empty();
    }
    return AddressLookupResult.found(record);
  }

  /** Visible-for-test: the radius passed at construction. */
  double radiusMeters() {
    return radiusMeters;
  }
}
