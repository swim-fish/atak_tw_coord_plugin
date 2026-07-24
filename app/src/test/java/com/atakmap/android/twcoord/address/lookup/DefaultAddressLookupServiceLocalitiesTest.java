package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.ActiveDatasetRegistry;
import com.atakmap.android.twcoord.address.AddressBundleImporter;
import com.atakmap.android.twcoord.address.AddressDatabaseFacade;
import com.atakmap.android.twcoord.address.AddressDataset;
import com.atakmap.android.twcoord.address.AddressRecord;
import com.atakmap.android.twcoord.address.CountyActiveDataset;
import com.atakmap.android.twcoord.address.FileSystem;
import com.atakmap.android.twcoord.address.GeneratorMetadata;
import com.atakmap.android.twcoord.address.ImportedManifest;
import com.atakmap.android.twcoord.address.MessageDigestShaCalculator;
import com.atakmap.android.twcoord.address.boundary.LocalityResult;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DefaultAddressLookupServiceLocalitiesTest {
  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void activeIntersectionPostalOrderAndStrictMapPromotionArePublished() throws Exception {
    TestFileSystem fs = new TestFileSystem(tmp.newFolder().toPath());
    AddressBundleImporter importer =
        new AddressBundleImporter(fs, new MessageDigestShaCalculator(), 3);
    AddressDatabaseFacade.Factory none = ignored -> null;
    ActiveDatasetRegistry registry = new ActiveDatasetRegistry(importer, none, () -> none, fs);
    registry.add(dataset("台中市", Arrays.asList("中區", "西屯區")));
    registry.add(dataset("新北市", Collections.singletonList("板橋區")));
    PostalLocalityCatalog catalog =
        PostalLocalityCatalog.testing(
            PostalLocalityCatalog.county("新北市", 1, PostalLocalityCatalog.district("板橋區", "220", 1)),
            PostalLocalityCatalog.county(
                "臺中市",
                2,
                PostalLocalityCatalog.district("中區", "400", 1),
                PostalLocalityCatalog.district("西屯區", "407", 2)));
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(
            registry,
            Runnable::run,
            DefaultAddressLookupService.QueryEngine.noData(),
            8,
            () -> catalog,
            anchor -> LocalityResult.full("臺中市", "西屯區"));
    try {
      Wgs84 anchor = new Wgs84(24.16, 120.64, 1L, Wgs84.Source.MAP_CENTRE);
      LocalitySelectorResult county =
          await(
              service,
              LocalitySelectorRequest.create(
                  identity(registry),
                  "county",
                  LookupPriority.NATIVE_INTERACTIVE,
                  LocalitySelectorSnapshot.Kind.COUNTY,
                  null,
                  anchor));
      assertThat(names(county.snapshot())).containsExactly("臺中市", "新北市");

      LocalitySelectorResult district =
          await(
              service,
              LocalitySelectorRequest.create(
                  identity(registry),
                  "district",
                  LookupPriority.NATIVE_INTERACTIVE,
                  LocalitySelectorSnapshot.Kind.DISTRICT,
                  "臺中市",
                  anchor));
      assertThat(names(district.snapshot())).containsExactly("西屯區", "中區");
      assertThat(district.snapshot().datasetRevision()).isEqualTo(registry.revision());
    } finally {
      service.close();
      registry.close();
    }
  }

  @Test
  public void cancelledLocalityCompletionIsSuppressed() throws Exception {
    TestFileSystem fs = new TestFileSystem(tmp.newFolder().toPath());
    AddressBundleImporter importer =
        new AddressBundleImporter(fs, new MessageDigestShaCalculator(), 3);
    AddressDatabaseFacade.Factory none = ignored -> null;
    ActiveDatasetRegistry registry = new ActiveDatasetRegistry(importer, none, () -> none, fs);
    registry.add(dataset("臺中市", Collections.singletonList("西屯區")));
    LinkedBlockingQueue<Runnable> completions = new LinkedBlockingQueue<>();
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(
            registry,
            completions::add,
            DefaultAddressLookupService.QueryEngine.noData(),
            8,
            PostalLocalityCatalog::unavailable,
            DefaultAddressLookupService.MapLocalityResolver.none());
    AtomicReference<LocalitySelectorResult> observed = new AtomicReference<>();
    try {
      LookupHandle handle =
          service.localities(
              LocalitySelectorRequest.create(
                  identity(registry),
                  "county",
                  LookupPriority.NATIVE_INTERACTIVE,
                  LocalitySelectorSnapshot.Kind.COUNTY,
                  null,
                  null),
              observed::set);
      Runnable completion = completions.poll(5, TimeUnit.SECONDS);
      assertThat(completion).isNotNull();

      handle.cancel();
      completion.run();

      assertThat(observed.get()).isNull();
    } finally {
      service.close();
      registry.close();
    }
  }

  @Test
  public void closeSuppressesQueuedCompletionAndRejectsNewLocalityWork() throws Exception {
    TestFileSystem fs = new TestFileSystem(tmp.newFolder().toPath());
    AddressBundleImporter importer =
        new AddressBundleImporter(fs, new MessageDigestShaCalculator(), 3);
    AddressDatabaseFacade.Factory none = ignored -> null;
    ActiveDatasetRegistry registry = new ActiveDatasetRegistry(importer, none, () -> none, fs);
    registry.add(dataset("臺中市", Collections.singletonList("西屯區")));
    LinkedBlockingQueue<Runnable> completions = new LinkedBlockingQueue<>();
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(
            registry,
            completions::add,
            DefaultAddressLookupService.QueryEngine.noData(),
            8,
            PostalLocalityCatalog::unavailable,
            DefaultAddressLookupService.MapLocalityResolver.none());
    LocalitySelectorRequest request =
        LocalitySelectorRequest.create(
            identity(registry),
            "county",
            LookupPriority.NATIVE_INTERACTIVE,
            LocalitySelectorSnapshot.Kind.COUNTY,
            null,
            null);
    AtomicReference<LocalitySelectorResult> observed = new AtomicReference<>();
    service.localities(request, observed::set);
    Runnable completion = completions.poll(5, TimeUnit.SECONDS);
    assertThat(completion).isNotNull();

    service.close();
    completion.run();

    assertThat(observed.get()).isNull();
    assertThat(service.availability().closed()).isTrue();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.localities(request, ignored -> {}))
        .isInstanceOf(IllegalStateException.class);
    registry.close();
  }

  private static LocalitySelectorResult await(
      DefaultAddressLookupService service, LocalitySelectorRequest request) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<LocalitySelectorResult> result = new AtomicReference<>();
    service.localities(
        request,
        value -> {
          result.set(value);
          latch.countDown();
        });
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    return result.get();
  }

  private static LookupIdentity identity(ActiveDatasetRegistry registry) {
    return new LookupIdentity("selector", 3L, 4L, registry.revision());
  }

  private CountyActiveDataset dataset(String county, List<String> districts) throws Exception {
    File root = tmp.newFolder();
    File database = new File(root, "places.sqlite");
    GeneratorMetadata metadata =
        new GeneratorMetadata(
            3, "fixture", county, "115-07", null, null, null, 1, Collections.emptyMap());
    AddressDataset dataset =
        new AddressDataset(
            root,
            database,
            metadata,
            new ImportedManifest(
                Instant.EPOCH,
                "1111111111111111111111111111111111111111111111111111111111111111",
                true,
                3));
    AddressDatabaseFacade facade =
        new AddressDatabaseFacade() {
          @Override
          public GeneratorMetadata readMetadata() {
            return metadata;
          }

          @Override
          public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
            return null;
          }

          @Override
          public List<String> localities(int limit) {
            return districts;
          }

          @Override
          public void close() {}
        };
    return new CountyActiveDataset(county, dataset, facade);
  }

  private static List<String> names(LocalitySelectorSnapshot snapshot) {
    java.util.ArrayList<String> values = new java.util.ArrayList<>();
    for (LocalitySelectorSnapshot.Choice choice : snapshot.choices()) values.add(choice.name());
    return values;
  }

  private static final class TestFileSystem implements FileSystem {
    private final Path root;

    TestFileSystem(Path root) {
      this.root = root;
    }

    @Override
    public Path getActiveDir() {
      return root.resolve("active");
    }

    @Override
    public Path createStagingDir() throws IOException {
      return Files.createTempDirectory(root, ".staging-");
    }

    @Override
    public OutputStream openWrite(Path path) throws IOException {
      Files.createDirectories(path.getParent());
      return Files.newOutputStream(path);
    }

    @Override
    public void atomicMove(Path source, Path destination) throws IOException {
      Files.createDirectories(destination.getParent());
      Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void deleteRecursively(Path path) {
      if (!Files.exists(path)) return;
      try (java.util.stream.Stream<Path> walk = Files.walk(path)) {
        walk.sorted(Comparator.reverseOrder())
            .forEach(
                item -> {
                  try {
                    Files.deleteIfExists(item);
                  } catch (IOException ignored) {
                    // Best-effort test fixture cleanup.
                  }
                });
      } catch (IOException ignored) {
        // Best-effort test fixture cleanup.
      }
    }

    @Override
    public boolean exists(Path path) {
      return Files.exists(path);
    }
  }
}
