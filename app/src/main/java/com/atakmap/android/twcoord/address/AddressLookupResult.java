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
    return new Found(record);
  }

  public static Empty empty() {
    return Empty.INSTANCE;
  }

  public static NoDataset noDataset() {
    return NoDataset.INSTANCE;
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

  public static final class Found extends AddressLookupResult {
    private final AddressRecord record;

    private Found(AddressRecord record) {
      this.record = Objects.requireNonNull(record, "record");
    }

    public AddressRecord record() {
      return record;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Found)) return false;
      return record.equals(((Found) o).record);
    }

    @Override
    public int hashCode() {
      return record.hashCode();
    }
  }

  public static final class Empty extends AddressLookupResult {
    private static final Empty INSTANCE = new Empty();

    private Empty() {}
  }

  public static final class NoDataset extends AddressLookupResult {
    private static final NoDataset INSTANCE = new NoDataset();

    private NoDataset() {}
  }
}
