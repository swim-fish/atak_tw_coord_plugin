package com.atakmap.android.twcoord.address;

import java.util.Objects;

/**
 * Feature 005 per-county wrapper around {@link AddressDataset} + an open {@link
 * AddressDatabaseFacade}. Each instance represents one active county dataset on disk; {@code
 * ActiveDatasetRegistry} keys an in-memory map by {@link #county()}.
 *
 * <p>The {@code facade} field is the run-time SQLite cursor opened by the registry — either {@code
 * AtakDatabasesAddressDatabase} (primary path; ATAK native; has R*Tree on the reference device) or
 * the {@code FallbackSqliteFactory} wrapper (Requery-backed; also has R*Tree). The facade's
 * lifetime is bound to the registry: opened at activation / replace, closed at remove.
 *
 * <p>Equality is keyed on {@link #county()} only — two datasets for the same county are considered
 * equal for {@code ConcurrentMap} purposes even if their underlying file SHA or generator metadata
 * differ. The registry's add/replace logic compares SHA via {@link #dataset()}{@code .imported()}
 * to decide whether a Replace is no-op vs a real refresh.
 */
public final class CountyActiveDataset {

  private final String county;
  private final AddressDataset dataset;
  private final AddressDatabaseFacade facade;

  public CountyActiveDataset(String county, AddressDataset dataset, AddressDatabaseFacade facade) {
    this.county = Objects.requireNonNull(county, "county");
    this.dataset = Objects.requireNonNull(dataset, "dataset");
    this.facade = Objects.requireNonNull(facade, "facade");
  }

  public String county() {
    return county;
  }

  public AddressDataset dataset() {
    return dataset;
  }

  public AddressDatabaseFacade facade() {
    return facade;
  }

  /** Convenience: the {@code places.sqlite} file under the per-county active directory. */
  public java.io.File placesFile() {
    return dataset.dbFile();
  }

  /** Convenience: the per-county active root directory ({@code active/<county>/}). */
  public java.io.File rootDir() {
    return dataset.rootDir();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CountyActiveDataset)) return false;
    CountyActiveDataset that = (CountyActiveDataset) o;
    return county.equals(that.county);
  }

  @Override
  public int hashCode() {
    return county.hashCode();
  }

  @Override
  public String toString() {
    return "CountyActiveDataset{county="
        + county
        + ", dataDate="
        + dataset.generator().dataDate()
        + ", rows="
        + dataset.generator().insertedRows()
        + ", sha="
        + (dataset.imported().fileSha256().length() > 10
            ? dataset.imported().fileSha256().substring(0, 10)
            : dataset.imported().fileSha256())
        + "}";
  }
}
