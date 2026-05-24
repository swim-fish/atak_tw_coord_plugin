package com.atakmap.android.twcoord.address;

import java.io.File;

/**
 * SDK seam over the runtime SQLite reader the resolver consumes. Wraps the {@code
 * android.database.sqlite.SQLiteDatabase} opened against the active dataset's {@code
 * places-&lt;county&gt;.sqlite} file. JVM unit tests inject a mock; production code uses {@code
 * SqliteAddressDatabase}.
 *
 * <p>Per {@code contracts/address-database-facade.md} the facade has two public methods plus {@link
 * AutoCloseable#close()}. Implementations MUST NOT throw out of any public method — IO / SQL
 * failures return safe defaults (null from {@link #nearestWithin}, an empty-but-valid {@link
 * GeneratorMetadata} from {@link #readMetadata}) after logging at {@code Log.w} — so a corrupt
 * dataset cannot crash the host process (Constitution VI).
 */
public interface AddressDatabaseFacade extends AutoCloseable {

  /** Read the {@code metadata} table verbatim. Used by the Offline Address page. */
  GeneratorMetadata readMetadata();

  /**
   * Return the single nearest address record within {@code radiusMeters} of {@code (lat, lon)}, or
   * {@code null} if no record falls inside the radius. Implementation runs the R*Tree bbox query
   * joined to {@code places} then refines by haversine distance (per {@code
   * contracts/address-database-facade.md}).
   */
  AddressRecord nearestWithin(double lat, double lon, double radiusMeters);

  @Override
  void close();

  /**
   * Opens fresh {@link AddressDatabaseFacade} instances for active datasets. {@link
   * AddressSubsystem} holds a {@link Factory} so it can re-open the facade on {@code
   * ACTION_DATASET_CHANGED} without importing {@code Context} directly.
   */
  interface Factory {
    /**
     * Open the DB file in read-only mode. Returns {@code null} if the file is missing or
     * unopenable.
     */
    AddressDatabaseFacade open(File dbFile);
  }
}
