package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atakmap.android.twcoord.address.ActiveDatasetRegistry;
import com.atakmap.android.twcoord.address.AddressBundleImporter;
import com.atakmap.android.twcoord.address.AddressDatabaseFacade;
import com.atakmap.android.twcoord.address.FileSystem;
import com.atakmap.android.twcoord.address.MessageDigestShaCalculator;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class AddressLookupServiceContractTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  @Test
  public void noDataServiceReportsAvailabilityAndForwardOutcome() throws Exception {
    AddressLookupService service = new NoDataAddressLookupService(Runnable::run);
    CountDownLatch done = new CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<ForwardAddressResult> result =
        new java.util.concurrent.atomic.AtomicReference<>();

    service.forward(
        ForwardAddressRequest.create(
            new LookupIdentity("request-1", 1L, 2L, 3L),
            "native",
            LookupPriority.NATIVE_INTERACTIVE,
            "臺中市南屯區黎明路2段130號",
            20),
        value -> {
          result.set(value);
          done.countDown();
        });

    assertThat(done.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(result.get().status()).isEqualTo(ForwardAddressResult.Status.NO_DATASET);
    assertThat(service.availability().counties()).isEmpty();
    service.close();
  }

  @Test
  public void cancelledWorkNeverDeliversCallback() throws Exception {
    ActiveDatasetRegistry registry = emptyRegistry();
    CountDownLatch engineEntered = new CountDownLatch(1);
    CountDownLatch engineRelease = new CountDownLatch(1);
    AtomicInteger callbacks = new AtomicInteger();
    DefaultAddressLookupService.QueryEngine engine =
        new DefaultAddressLookupService.QueryEngine() {
          @Override
          public ForwardAddressResult forward(
              ForwardAddressRequest request, ActiveDatasetRegistry.ReadSession session)
              throws InterruptedException {
            engineEntered.countDown();
            engineRelease.await(2, TimeUnit.SECONDS);
            return ForwardAddressResult.noMatch(request.identity());
          }

          @Override
          public ReverseAddressResult reverse(
              ReverseAddressRequest request, ActiveDatasetRegistry.ReadSession session) {
            return ReverseAddressResult.noMatch(request.identity(), request.queryPoint());
          }
        };
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(registry, Runnable::run, engine, 4);
    LookupHandle handle =
        service.forward(
            ForwardAddressRequest.create(
                new LookupIdentity("request-2", 1L, 1L, registry.revision()),
                "native",
                LookupPriority.NATIVE_INTERACTIVE,
                "test",
                5),
            ignored -> callbacks.incrementAndGet());

    assertThat(engineEntered.await(1, TimeUnit.SECONDS)).isTrue();
    handle.cancel();
    engineRelease.countDown();
    Thread.sleep(100L);

    assertThat(callbacks.get()).isZero();
    service.close();
    registry.close();
  }

  @Test
  public void closeIsMonotonicSuppressesCallbacksAndRejectsNewWork() {
    ActiveDatasetRegistry registry = emptyRegistry();
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(
            registry, Runnable::run, DefaultAddressLookupService.QueryEngine.noData(), 2);

    service.close();
    service.close();

    assertThat(service.availability().closed()).isTrue();
    assertThatThrownBy(
            () ->
                service.forward(
                    ForwardAddressRequest.create(
                        new LookupIdentity("late", 1L, 1L, registry.revision()),
                        "native",
                        LookupPriority.NATIVE_INTERACTIVE,
                        "test",
                        5),
                    ignored -> {}))
        .isInstanceOf(IllegalStateException.class);
    registry.close();
  }

  @Test
  public void closeSuppressesInFlightCallback() throws Exception {
    ActiveDatasetRegistry registry = emptyRegistry();
    CountDownLatch engineEntered = new CountDownLatch(1);
    CountDownLatch engineRelease = new CountDownLatch(1);
    AtomicInteger callbacks = new AtomicInteger();
    DefaultAddressLookupService.QueryEngine engine =
        new DefaultAddressLookupService.QueryEngine() {
          @Override
          public ForwardAddressResult forward(
              ForwardAddressRequest request, ActiveDatasetRegistry.ReadSession session)
              throws InterruptedException {
            engineEntered.countDown();
            engineRelease.await(2, TimeUnit.SECONDS);
            return ForwardAddressResult.noMatch(request.identity());
          }

          @Override
          public ReverseAddressResult reverse(
              ReverseAddressRequest request, ActiveDatasetRegistry.ReadSession session) {
            return ReverseAddressResult.noMatch(request.identity(), request.queryPoint());
          }
        };
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(registry, Runnable::run, engine, 2);
    service.forward(
        request("running", "native", LookupPriority.NATIVE_INTERACTIVE),
        ignored -> callbacks.incrementAndGet());

    assertThat(engineEntered.await(1, TimeUnit.SECONDS)).isTrue();
    service.close();
    engineRelease.countDown();
    Thread.sleep(100L);

    assertThat(callbacks.get()).isZero();
    registry.close();
  }

  @Test
  public void queueIsBoundedAndEvictsOldestBackgroundWork() throws Exception {
    ActiveDatasetRegistry registry = emptyRegistry();
    CountDownLatch engineEntered = new CountDownLatch(1);
    CountDownLatch engineRelease = new CountDownLatch(1);
    DefaultAddressLookupService.QueryEngine engine =
        new DefaultAddressLookupService.QueryEngine() {
          @Override
          public ForwardAddressResult forward(
              ForwardAddressRequest request, ActiveDatasetRegistry.ReadSession session)
              throws InterruptedException {
            if ("running".equals(request.identity().requestId())) {
              engineEntered.countDown();
              engineRelease.await(2, TimeUnit.SECONDS);
            }
            return ForwardAddressResult.noMatch(request.identity());
          }

          @Override
          public ReverseAddressResult reverse(
              ReverseAddressRequest request, ActiveDatasetRegistry.ReadSession session) {
            return ReverseAddressResult.noMatch(request.identity(), request.queryPoint());
          }
        };
    DefaultAddressLookupService service =
        new DefaultAddressLookupService(registry, Runnable::run, engine, 2);
    service.forward(
        request("running", "consumer-running", LookupPriority.WIDGET_BACKGROUND), ignored -> {});
    assertThat(engineEntered.await(1, TimeUnit.SECONDS)).isTrue();

    LookupHandle oldest =
        service.forward(
            request("queued-1", "consumer-1", LookupPriority.WIDGET_BACKGROUND), ignored -> {});
    service.forward(
        request("queued-2", "consumer-2", LookupPriority.WIDGET_BACKGROUND), ignored -> {});
    service.forward(
        request("queued-3", "consumer-3", LookupPriority.WIDGET_BACKGROUND), ignored -> {});

    assertThat(service.queuedWorkCount()).isEqualTo(2);
    assertThat(oldest.isCancelled()).isTrue();
    engineRelease.countDown();
    service.close();
    registry.close();
  }

  private ForwardAddressRequest request(
      String requestId, String consumerKey, LookupPriority priority) {
    return ForwardAddressRequest.create(
        new LookupIdentity(requestId, 1L, 1L, 0L), consumerKey, priority, "test", 5);
  }

  private ActiveDatasetRegistry emptyRegistry() {
    TestFileSystem fs = new TestFileSystem(tmp.getRoot().toPath());
    AddressBundleImporter importer =
        new AddressBundleImporter(fs, new MessageDigestShaCalculator(), 3);
    AddressDatabaseFacade.Factory none = ignored -> null;
    return new ActiveDatasetRegistry(importer, none, () -> none, fs);
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
                    // Best-effort test seam mirrors the production contract.
                  }
                });
      } catch (IOException ignored) {
        // Best-effort test seam mirrors the production contract.
      }
    }

    @Override
    public boolean exists(Path path) {
      return Files.exists(path);
    }
  }
}
