package com.atakmap.android.twcoord.address;

import java.util.Objects;

/**
 * Discriminated outcome of a single reverse-lookup. Three variants: {@link Found} carries an {@link
 * AddressRecord}; {@link Empty} signals "no record within search radius"; {@link NoDataset} signals
 * "no active dataset" (distinct from Empty so the widget can omit the row rather than showing the
 * empty-state).
 *
 * <p>Follows the project's sealed-by-convention pattern (abstract + private constructor + final
 * nested classes) used by {@link com.atakmap.android.twcoord.gotopage.ParseResult}, instead of the
 * Java 17 {@code sealed} keyword. The properties are the same.
 */
public abstract class AddressLookupResult {

  private AddressLookupResult() {}

  public static Found found(AddressRecord record) {
    return new Found(record, -1.0);
  }

  /**
   * Feature 005 confidence indicator: the actual haversine distance (metres) from the query point
   * to the returned record. Lets the UI decorate the address row with a confidence marker when the
   * nearest record is more than a few tens of metres away. Pass {@code -1} (or use {@link
   * #found(AddressRecord)}) when distance is unknown.
   */
  public static Found found(AddressRecord record, double distanceMeters) {
    return new Found(record, distanceMeters);
  }

  public static Empty empty() {
    return Empty.INSTANCE;
  }

  public static NoDataset noDataset() {
    return NoDataset.INSTANCE;
  }

  /**
   * Feature 006: the boundary layer resolved a county/district but that county's address dataset is
   * not installed, so no house number is available. The widget shows the locality text as a
   * best-effort answer rather than an empty row (FR-015).
   */
  public static LocalityOnly localityOnly(String county, String district) {
    return new LocalityOnly(county, district);
  }

  public boolean isFound() {
    return this instanceof Found;
  }

  public boolean isEmpty() {
    return this instanceof Empty;
  }

  public boolean isNoDataset() {
    return this instanceof NoDataset;
  }

  public boolean isLocalityOnly() {
    return this instanceof LocalityOnly;
  }

  public static final class Found extends AddressLookupResult {
    private final AddressRecord record;
    private final double distanceMeters;

    private Found(AddressRecord record, double distanceMeters) {
      this.record = Objects.requireNonNull(record, "record");
      this.distanceMeters = distanceMeters;
    }

    public AddressRecord record() {
      return record;
    }

    /**
     * Haversine distance from the query point to {@link #record()} in metres; {@code -1} = unknown.
     */
    public double distanceMeters() {
      return distanceMeters;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Found)) return false;
      Found f = (Found) o;
      return record.equals(f.record) && Double.compare(distanceMeters, f.distanceMeters) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(record, distanceMeters);
    }
  }

  public static final class Empty extends AddressLookupResult {
    private static final Empty INSTANCE = new Empty();

    private Empty() {}
  }

  /** county + 鄉鎮市區 with no house-number dataset installed (FR-015). */
  public static final class LocalityOnly extends AddressLookupResult {
    private final String county;
    private final String district; // nullable

    private LocalityOnly(String county, String district) {
      this.county = Objects.requireNonNull(county, "county");
      this.district = district;
    }

    public String county() {
      return county;
    }

    /** 鄉鎮市區, or {@code null} when only the county is known. */
    public String district() {
      return district;
    }

    /** Best-effort display text, e.g. {@code "台中市西區"} or {@code "雲林縣"}. */
    public String localityText() {
      return district == null ? county : county + district;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof LocalityOnly)) return false;
      LocalityOnly that = (LocalityOnly) o;
      return county.equals(that.county) && Objects.equals(district, that.district);
    }

    @Override
    public int hashCode() {
      return Objects.hash(county, district);
    }
  }

  public static final class NoDataset extends AddressLookupResult {
    private static final NoDataset INSTANCE = new NoDataset();

    private NoDataset() {}
  }
}
