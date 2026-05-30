package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.AddressBundleImporterTest.TempFileSystem;
import com.atakmap.android.twcoord.address.boundary.LocalityResult;
import com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * Feature 006 T033 — reverse-path county scoping. Asserts the boundary-first path queries only the
 * detected county (and equals the old fan-out result for in-county points), falls back to the
 * fan-out when boundary is null / county unknown, and returns LocalityOnly when the county has no
 * dataset.
 */
public class AddressSubsystemReverseScopingTest {

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
    primary = file -> null;
    fallbackSupplier = () -> null;
    executor = Executors.newSingleThreadScheduledExecutor();
    subsystem = new AddressSubsystem(importer, primary, executor, 0L, Runnable::run);
  }

  @After
  public void tearDown() {
    subsystem.close();
  }

  // (1) in-county: only that facade queried; result == fan-out result.
  @Test
  public void inCountyScopedResultEqualsFanOutAndQueriesOnlyOneCounty() {
    ActiveDatasetRegistry registry = freshRegistry();
    CountingFacade taichung = new CountingFacade(24.137, 120.685, "台中市北區");
    CountingFacade changhua = new CountingFacade(24.08, 120.54, "彰化縣彰化市");
    registry.add(fakeDataset("台中市", taichung));
    registry.add(fakeDataset("彰化縣", changhua));
    subsystem.setRegistry(registry);
    subsystem.setBoundaryFacade(boundaryReturning("台中市", "北區"));

    AddressLookupResult result = subsystem.lookupScoped(24.137, 120.685);

    assertThat(result).isInstanceOf(AddressLookupResult.Found.class);
    assertThat(((AddressLookupResult.Found) result).record().displayName()).isEqualTo("台中市北區");
    assertThat(taichung.queries).isEqualTo(1);
    assertThat(changhua.queries).isEqualTo(0); // Changhua facade NOT queried
  }

  // (2) boundary null → exact fan-out behaviour.
  @Test
  public void nullBoundaryFallsBackToFanOut() {
    ActiveDatasetRegistry registry = freshRegistry();
    registry.add(fakeDataset("台中市", new CountingFacade(24.137, 120.685, "台中市北區")));
    subsystem.setRegistry(registry);
    // no setBoundaryFacade → null

    AddressLookupResult result = subsystem.lookupScoped(24.137, 120.685);

    assertThat(result).isInstanceOf(AddressLookupResult.Found.class);
    assertThat(((AddressLookupResult.Found) result).record().displayName()).isEqualTo("台中市北區");
  }

  // (3) county detected but dataset absent → LocalityOnly.
  @Test
  public void countyWithoutDatasetReturnsLocalityOnly() {
    ActiveDatasetRegistry registry = freshRegistry();
    registry.add(fakeDataset("台中市", new CountingFacade(24.137, 120.685, "台中市北區")));
    subsystem.setRegistry(registry);
    subsystem.setBoundaryFacade(boundaryReturning("雲林縣", "斗六市"));

    AddressLookupResult result = subsystem.lookupScoped(23.71, 120.54);

    assertThat(result).isInstanceOf(AddressLookupResult.LocalityOnly.class);
    AddressLookupResult.LocalityOnly lo = (AddressLookupResult.LocalityOnly) result;
    assertThat(lo.localityText()).isEqualTo("雲林縣斗六市");
  }

  // (4) offshore (boundary returns None) → fan-out fallback.
  @Test
  public void offshoreFallsBackToFanOut() {
    ActiveDatasetRegistry registry = freshRegistry();
    registry.add(fakeDataset("台中市", new CountingFacade(24.137, 120.685, "台中市北區")));
    subsystem.setRegistry(registry);
    subsystem.setBoundaryFacade(boundaryNone());

    AddressLookupResult result = subsystem.lookupScoped(24.0, 119.5);

    // Fan-out runs; the Taichung record is ~far so likely Empty — assert it did NOT throw and is a
    // valid result type (Found/Empty), i.e. the fallback path executed.
    assertThat(result).isNotNull();
    assertThat(result.isLocalityOnly()).isFalse();
  }

  // (5) boundary throws → caught, fan-out, no crash.
  @Test
  public void boundaryThrowFallsBackToFanOut() {
    ActiveDatasetRegistry registry = freshRegistry();
    registry.add(fakeDataset("台中市", new CountingFacade(24.137, 120.685, "台中市北區")));
    subsystem.setRegistry(registry);
    subsystem.setBoundaryFacade(boundaryThrowing());

    AddressLookupResult result = subsystem.lookupScoped(24.137, 120.685);

    assertThat(result).isInstanceOf(AddressLookupResult.Found.class);
    assertThat(((AddressLookupResult.Found) result).record().displayName()).isEqualTo("台中市北區");
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private ActiveDatasetRegistry freshRegistry() {
    return new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
  }

  private static TownshipBoundaryFacade boundaryReturning(String county, String district) {
    return new TownshipBoundaryFacade() {
      @Override
      public LocalityResult localityAt(double lat, double lon, double snapMeters) {
        return LocalityResult.full(county, district);
      }

      @Override
      public List<String> counties() {
        return new ArrayList<>();
      }

      @Override
      public List<String> districtsOf(String c) {
        return new ArrayList<>();
      }

      @Override
      public void close() {}
    };
  }

  private static TownshipBoundaryFacade boundaryNone() {
    return new TownshipBoundaryFacade() {
      @Override
      public LocalityResult localityAt(double lat, double lon, double snapMeters) {
        return LocalityResult.none();
      }

      @Override
      public List<String> counties() {
        return new ArrayList<>();
      }

      @Override
      public List<String> districtsOf(String c) {
        return new ArrayList<>();
      }

      @Override
      public void close() {}
    };
  }

  private static TownshipBoundaryFacade boundaryThrowing() {
    return new TownshipBoundaryFacade() {
      @Override
      public LocalityResult localityAt(double lat, double lon, double snapMeters) {
        throw new RuntimeException("simulated boundary fault");
      }

      @Override
      public List<String> counties() {
        return new ArrayList<>();
      }

      @Override
      public List<String> districtsOf(String c) {
        return new ArrayList<>();
      }

      @Override
      public void close() {}
    };
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

  /** Facade that counts queries and returns one fixed record within radius. */
  private static final class CountingFacade implements AddressDatabaseFacade {
    private final double lat;
    private final double lon;
    private final String displayName;
    int queries = 0;

    CountingFacade(double lat, double lon, String displayName) {
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
      queries++;
      double d = AddressSubsystem.haversineMeters(qLat, qLon, lat, lon);
      if (d > radiusMeters) return null;
      return new AddressRecord(lat, lon, displayName, displayName);
    }

    @Override
    public void close() {}
  }
}
