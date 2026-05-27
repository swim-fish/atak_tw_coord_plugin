package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.AddressBundleImporterTest.TempFileSystem;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Feature 005 — JVM tests for {@link AddressSubsystem#lookupAcrossAllCounties} multi-county fan-out
 * (T022). Uses the package-private accessor so the test doesn't need to drive the full executor /
 * debounce loop.
 *
 * <p>Coverage: two-county fan-out picks the geodetically nearest, zero-active returns NoDataset,
 * tie-break determinism, per-county throw is swallowed and others still win, single-active behaves
 * identically to 004 baseline. Per spec FR-009 + data-model §4.1.
 */
public class AddressSubsystemMultiCountyTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private TempFileSystem fs;
  private AddressBundleImporter importer;
  private AddressDatabaseFacade.Factory primary;
  private Supplier<AddressDatabaseFacade.Factory> fallbackSupplier;
  private ScheduledExecutorService executor;
  private AddressSubsystem subsystem;

  @Before
  public void setUp() throws IOException {
    fs = new TempFileSystem(tmp.getRoot().toPath());
    importer = new AddressBundleImporter(fs, new MessageDigestShaCalculator(), 2);
    primary = file -> null; // never called in this test — registry is pre-populated via add()
    fallbackSupplier = () -> null;
    executor = Executors.newSingleThreadScheduledExecutor();
    subsystem = new AddressSubsystem(importer, primary, executor, 0L, Runnable::run);
  }

  @After
  public void tearDown() {
    subsystem.close();
  }

  // (a) Two-county fan-out picks the geodetically-nearest
  @Test
  public void twoCountyFanOutPicksNearest() {
    // Taichung facade returns a record at (24.137, 120.685) — Taichung station.
    // Changhua facade returns a record at (24.08, 120.54) — Changhua city.
    // Query point (24.137, 120.685) is exactly Taichung's record; Changhua's record is
    // ~10 km away — outside the 500 m LOOKUP_RADIUS_M and thus filtered.
    ActiveDatasetRegistry registry = freshRegistry();
    registry.add(fakeDataset("台中市", new FixedFacade(24.137, 120.685, "台中市北區")));
    registry.add(fakeDataset("彰化縣", new FixedFacade(24.08, 120.54, "彰化縣彰化市")));
    subsystem.setRegistry(registry);

    AddressLookupResult result = subsystem.lookupAcrossAllCounties(24.137, 120.685);

    assertThat(result).isInstanceOf(AddressLookupResult.Found.class);
    AddressLookupResult.Found found = (AddressLookupResult.Found) result;
    assertThat(found.record().displayName()).isEqualTo("台中市北區");
  }

  // (b) Single-active behaves identically to 004 baseline
  @Test
  public void singleCountyActiveBehavesAsBefore() {
    ActiveDatasetRegistry registry = freshRegistry();
    registry.add(fakeDataset("台中市", new FixedFacade(24.137, 120.685, "台中市北區")));
    subsystem.setRegistry(registry);

    AddressLookupResult result = subsystem.lookupAcrossAllCounties(24.137, 120.685);

    assertThat(result).isInstanceOf(AddressLookupResult.Found.class);
    assertThat(((AddressLookupResult.Found) result).record().displayName()).isEqualTo("台中市北區");
  }

  // (c) Zero-active returns NoDataset
  @Test
  public void zeroCountyActiveReturnsNoDataset() {
    ActiveDatasetRegistry registry = freshRegistry();
    subsystem.setRegistry(registry);

    AddressLookupResult result = subsystem.lookupAcrossAllCounties(24.13, 120.68);

    assertThat(result).isInstanceOf(AddressLookupResult.NoDataset.class);
  }

  // (d) Tie-break determinism: equidistant records → iteration-order wins (ConcurrentHashMap's
  // insertion order on modern JDKs). The test only asserts determinism, not which one wins.
  @Test
  public void tieBreakIsDeterministic() {
    ActiveDatasetRegistry registry1 = freshRegistry();
    registry1.add(fakeDataset("AA", new FixedFacade(24.100, 120.500, "AA addr")));
    registry1.add(fakeDataset("BB", new FixedFacade(24.100, 120.500, "BB addr")));
    subsystem.setRegistry(registry1);

    AddressLookupResult r1 = subsystem.lookupAcrossAllCounties(24.100, 120.500);
    AddressLookupResult r2 = subsystem.lookupAcrossAllCounties(24.100, 120.500);

    assertThat(r1).isInstanceOf(AddressLookupResult.Found.class);
    assertThat(((AddressLookupResult.Found) r1).record().displayName())
        .isEqualTo(((AddressLookupResult.Found) r2).record().displayName());
  }

  // (e) Per-county throw is swallowed; other county still wins
  @Test
  public void perCountyThrowIsSwallowed() {
    ActiveDatasetRegistry registry = freshRegistry();
    registry.add(fakeDataset("壞縣", new ThrowingFacade()));
    registry.add(fakeDataset("台中市", new FixedFacade(24.137, 120.685, "台中市北區")));
    subsystem.setRegistry(registry);

    AddressLookupResult result = subsystem.lookupAcrossAllCounties(24.137, 120.685);

    assertThat(result).isInstanceOf(AddressLookupResult.Found.class);
    assertThat(((AddressLookupResult.Found) result).record().displayName()).isEqualTo("台中市北區");
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private ActiveDatasetRegistry freshRegistry() {
    return new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
  }

  private static CountyActiveDataset fakeDataset(String county, AddressDatabaseFacade facade) {
    GeneratorMetadata gen =
        new GeneratorMetadata(
            2, "tgos", county, "115-01", null, null, null, 0L, Collections.emptyMap());
    String fakeSha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    ImportedManifest im = new ImportedManifest(Instant.now(), fakeSha, false, 2);
    AddressDataset ds =
        new AddressDataset(
            new File("/tmp/fake/" + county),
            new File("/tmp/fake/" + county + "/places.sqlite"),
            gen,
            im);
    return new CountyActiveDataset(county, ds, facade);
  }

  /** Facade returning a single fixed record for every query (regardless of lat/lon/radius). */
  private static final class FixedFacade implements AddressDatabaseFacade {
    private final double lat;
    private final double lon;
    private final String displayName;

    FixedFacade(double lat, double lon, String displayName) {
      this.lat = lat;
      this.lon = lon;
      this.displayName = Objects.requireNonNull(displayName);
    }

    @Override
    public GeneratorMetadata readMetadata() {
      return new GeneratorMetadata(
          2, "tgos", "stub", "115-01", null, null, null, 0L, Collections.emptyMap());
    }

    @Override
    public AddressRecord nearestWithin(double qLat, double qLon, double radiusMeters) {
      double dMeters = AddressSubsystem.haversineMeters(qLat, qLon, lat, lon);
      if (dMeters > radiusMeters) return null;
      return new AddressRecord(lat, lon, displayName, displayName);
    }

    @Override
    public void close() {}
  }

  /** Facade that throws on every nearestWithin call. */
  private static final class ThrowingFacade implements AddressDatabaseFacade {
    @Override
    public GeneratorMetadata readMetadata() {
      return new GeneratorMetadata(
          2, "tgos", "stub", "115-01", null, null, null, 0L, Collections.emptyMap());
    }

    @Override
    public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
      throw new RuntimeException("simulated corrupt county");
    }

    @Override
    public void close() {}
  }
}
