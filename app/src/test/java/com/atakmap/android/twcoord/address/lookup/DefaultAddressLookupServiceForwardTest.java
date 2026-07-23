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
import com.atakmap.android.twcoord.coord.Wgs84;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class DefaultAddressLookupServiceForwardTest {
  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void uniqueExactCarriesExplicitMatchAndDatasetProvenance() throws Exception {
    AddressCandidate raw = raw("臺中市南屯區黎明路2段130號", 24.15, 120.65, "黎明路2段", "130號", 20);
    Harness harness = harness(Collections.singletonList(raw));

    ForwardAddressResult result = harness.lookup("臺中市南屯區黎明路2段130號", 20);

    assertThat(result.status()).isEqualTo(ForwardAddressResult.Status.CANDIDATES);
    assertThat(result.candidates()).hasSize(1);
    AddressCandidate candidate = result.candidates().get(0);
    assertThat(candidate.matchKind()).isEqualTo(AddressMatchKind.EXACT);
    assertThat(candidate.datasetIdentity()).isNotNull();
    assertThat(candidate.datasetIdentity().county()).isEqualTo("臺中市");
    assertThat(candidate.datasetIdentity().source()).isEqualTo("fixture");
    harness.close();
  }

  @Test
  public void taiVariantDatasetKeyDoesNotReportNoDataset() throws Exception {
    AddressCandidate raw = raw("台中市西屯區協和里工業區三十八路２０８號", 24.17, 120.62, "工業區三十八路", "２０８號", 20);
    Harness harness = harness(Collections.singletonList(raw), "台中市");

    ForwardAddressResult result = harness.lookup("台中市西屯區協和里工業區三十八路２０８號", 20);

    assertThat(result.status()).isEqualTo(ForwardAddressResult.Status.CANDIDATES);
    assertThat(result.candidates()).hasSize(1);
    assertThat(result.candidates().get(0).matchKind()).isEqualTo(AddressMatchKind.EXACT);
    harness.close();
  }

  @Test
  public void duplicateStableRecordsAreDeduplicatedAndLimitIsBounded() throws Exception {
    AddressCandidate first = raw("臺中市南屯區黎明路2段130號", 24.15, 120.65, "黎明路2段", "130號", 20);
    AddressCandidate duplicate = raw("臺中市南屯區黎明路2段130號", 24.15, 120.65, "黎明路2段", "130號", 20);
    AddressCandidate other = raw("臺中市南屯區黎明路2段132號", 24.16, 120.66, "黎明路2段", "132號", 30);
    Harness harness = harness(Arrays.asList(other, duplicate, first));

    ForwardAddressResult result = harness.lookup("臺中市南屯區黎明路2段130號", 1);

    assertThat(result.candidates()).hasSize(1);
    assertThat(result.candidates().get(0).displayAddress()).isEqualTo("臺中市南屯區黎明路2段130號");
    harness.close();
  }

  @Test
  public void nearestPartialCandidateIsNeverPromotedToExact() throws Exception {
    Harness harness =
        harness(
            Collections.singletonList(raw("臺中市南屯區黎明路2段132號", 24.15, 120.65, "黎明路2段", "132號", 1)));

    ForwardAddressResult result = harness.lookup("臺中市南屯區黎明路2段130號", 20);

    assertThat(result.candidates()).hasSize(1);
    assertThat(result.candidates().get(0).matchKind()).isEqualTo(AddressMatchKind.PARTIAL);
    harness.close();
  }

  @Test
  public void exactFullAddressWinsBeforeEitherConfiguredOrdering() throws Exception {
    AddressCandidate nearPartial = raw("臺中市南屯區黎明路1段999號", 24.15, 120.65, "黎明路1段", "999號", 5);
    AddressCandidate fartherExact = raw("臺中市南屯區黎明路2段130號", 24.16, 120.66, "黎明路2段", "130號", 80);
    Harness harness = harness(Arrays.asList(fartherExact, nearPartial));

    ForwardAddressResult distance = harness.lookup("臺中市南屯區黎明路2段130號", ResultOrdering.DISTANCE, 20);
    ForwardAddressResult mostSimilar =
        harness.lookup("臺中市南屯區黎明路2段130號", ResultOrdering.MOST_SIMILAR, 20);

    assertThat(distance.candidates())
        .extracting(AddressCandidate::displayAddress)
        .containsExactly("臺中市南屯區黎明路2段130號");
    assertThat(mostSimilar.candidates())
        .extracting(AddressCandidate::displayAddress)
        .containsExactly("臺中市南屯區黎明路2段130號");
    harness.close();
  }

  @Test
  public void numberedQueryUsesNormalizedStreetTextForEverySetting() throws Exception {
    AddressCandidate nearButTextuallyWrong =
        raw("臺中市西屯區協和里完全不同路208號", 24.15, 120.65, "完全不同路", "208號", 5)
            .withMatch("", AddressMatchKind.PARTIAL);
    AddressCandidate fartherTextualMatch =
        raw("臺中市西屯區協和里工業區三十八路207號", 24.16, 120.66, "工業區三十八路", "207號", 80)
            .withMatch("", AddressMatchKind.PARTIAL);
    Harness harness = harness(Arrays.asList(nearButTextuallyWrong, fartherTextualMatch));
    String query = "臺中市西屯區協和里工業區38路208號";

    ForwardAddressResult distance = harness.lookup(query, ResultOrdering.DISTANCE, 20);
    ForwardAddressResult mostSimilar = harness.lookup(query, ResultOrdering.MOST_SIMILAR, 20);

    assertThat(distance.candidates().get(0).displayAddress()).isEqualTo("臺中市西屯區協和里工業區三十八路207號");
    assertThat(mostSimilar.candidates().get(0).displayAddress()).isEqualTo("臺中市西屯區協和里工業區三十八路207號");
    harness.close();
  }

  @Test
  public void addressSemanticsReorderBeforeFacadeCandidateLimitForEverySetting() throws Exception {
    AddressCandidate nearbyWrongSegment =
        raw("臺中市西屯區何南里臺灣大道二段607號", 24.16, 120.65, "臺灣大道二段", "607號", 5);
    AddressCandidate fartherRequestedAddress =
        raw("臺中市西屯區惠來里臺灣大道三段99號", 24.17, 120.64, "臺灣大道三段", "99號", 500);
    Harness harness =
        harness(Arrays.asList(nearbyWrongSegment, fartherRequestedAddress), "臺中市", true);

    ForwardAddressResult distance = harness.lookup("台中市西屯區惠來里臺灣大道三段９９", ResultOrdering.DISTANCE, 1);
    ForwardAddressResult mostSimilar =
        harness.lookup("台中市西屯區惠來里臺灣大道三段９９", ResultOrdering.MOST_SIMILAR, 1);

    assertThat(distance.candidates())
        .extracting(AddressCandidate::displayAddress)
        .containsExactly("臺中市西屯區惠來里臺灣大道三段99號");
    assertThat(mostSimilar.candidates())
        .extracting(AddressCandidate::displayAddress)
        .containsExactly("臺中市西屯區惠來里臺灣大道三段99號");
    harness.close();
  }

  private Harness harness(List<AddressCandidate> rows) throws Exception {
    return harness(rows, "臺中市");
  }

  private Harness harness(List<AddressCandidate> rows, String registryCounty) throws Exception {
    return harness(rows, registryCounty, false);
  }

  private Harness harness(
      List<AddressCandidate> rows, String registryCounty, boolean facadeHonorsLimit)
      throws Exception {
    TestFileSystem fs = new TestFileSystem(tmp.newFolder().toPath());
    AddressBundleImporter importer =
        new AddressBundleImporter(fs, new MessageDigestShaCalculator(), 3);
    AddressDatabaseFacade.Factory none = ignored -> null;
    ActiveDatasetRegistry registry = new ActiveDatasetRegistry(importer, none, () -> none, fs);
    File root = tmp.newFolder();
    File database = new File(root, "places.sqlite");
    Files.write(database.toPath(), new byte[] {1});
    AddressDataset dataset =
        new AddressDataset(
            root,
            database,
            new GeneratorMetadata(
                3,
                "fixture",
                "臺中市",
                "115-07",
                null,
                null,
                null,
                rows.size(),
                Collections.emptyMap()),
            new ImportedManifest(
                Instant.EPOCH,
                "1111111111111111111111111111111111111111111111111111111111111111",
                true,
                3));
    AddressDatabaseFacade facade =
        new AddressDatabaseFacade() {
          @Override
          public GeneratorMetadata readMetadata() {
            return dataset.generator();
          }

          @Override
          public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
            return null;
          }

          @Override
          public List<AddressCandidate> fullAddressCandidates(
              AddressDraft draft, Wgs84 anchorPoint, int limit) {
            if (!facadeHonorsLimit || rows.size() <= limit) return rows;
            return new ArrayList<>(rows.subList(0, limit));
          }

          @Override
          public List<AddressCandidate> forwardCandidatePool(
              AddressDraft draft,
              String foldedStreetFragment,
              Wgs84 anchorPoint,
              ForwardCandidatePool pool,
              int limit) {
            return StreetCandidateRanker.reorderForPool(
                rows,
                pool,
                foldedStreetFragment,
                draft.components().tail(),
                anchorPoint != null,
                facadeHonorsLimit ? limit : Math.max(limit, rows.size()));
          }

          @Override
          public void close() {}
        };
    registry.add(new CountyActiveDataset(registryCounty, dataset, facade));
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(
            registry,
            Runnable::run,
            new DefaultAddressLookupService.RegistryQueryEngine(new TaiwanAddressParser()),
            8);
    return new Harness(registry, service);
  }

  private static AddressCandidate raw(
      String display, double lat, double lon, String street, String number, double distance) {
    return new AddressCandidate(lat, lon, display, display, street, number, distance);
  }

  private static final class Harness implements AutoCloseable {
    final ActiveDatasetRegistry registry;
    final DefaultAddressLookupService service;

    Harness(ActiveDatasetRegistry registry, DefaultAddressLookupService service) {
      this.registry = registry;
      this.service = service;
    }

    ForwardAddressResult lookup(String address, int limit) throws Exception {
      return lookup(address, ResultOrdering.DISTANCE, limit);
    }

    ForwardAddressResult lookup(String address, ResultOrdering ordering, int limit)
        throws Exception {
      CountDownLatch done = new CountDownLatch(1);
      AtomicReference<ForwardAddressResult> result = new AtomicReference<>();
      service.forward(
          ForwardAddressRequest.create(
              new LookupIdentity("request", 1L, 1L, registry.revision()),
              "native",
              LookupPriority.NATIVE_INTERACTIVE,
              address,
              ordering,
              limit),
          value -> {
            result.set(value);
            done.countDown();
          });
      assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
      return result.get();
    }

    @Override
    public void close() {
      service.close();
      registry.close();
    }
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
    public void atomicMove(Path src, Path dst) throws IOException {
      Files.createDirectories(dst.getParent());
      Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
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
                    // Best-effort test seam.
                  }
                });
      } catch (IOException ignored) {
        // Best-effort test seam.
      }
    }

    @Override
    public boolean exists(Path path) {
      return Files.exists(path);
    }
  }
}
