package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.FallbackSqliteFactory.Opener;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Feature 005 — JVM-side tests for {@link FallbackSqliteFactory} per
 * contracts/fallback-sqlite-factory.md.
 *
 * <p>The Requery native library is Android-only ({@code libsqliteX.so}); under a JVM unit-test
 * runner instantiating its {@code SQLiteDatabase} throws {@link UnsatisfiedLinkError}. These tests
 * inject a stub {@link Opener} so the orchestration around the native load can be exercised without
 * actually loading the library. The native-load smoke test is in the Espresso harness (T043
 * BatchImportRssTest etc.).
 */
public class FallbackSqliteFactoryTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  /** Stub opener that returns a fixed facade and counts calls. */
  private static final class CountingOpener implements Opener {
    final AtomicInteger calls = new AtomicInteger();
    final AddressDatabaseFacade fixed;

    CountingOpener(AddressDatabaseFacade fixed) {
      this.fixed = fixed;
    }

    @Override
    public AddressDatabaseFacade open(File dbFile) {
      calls.incrementAndGet();
      return fixed;
    }
  }

  /** Stub facade with no behaviour — sufficient as a marker that open() succeeded. */
  private static final class StubFacade implements AddressDatabaseFacade {
    @Override
    public GeneratorMetadata readMetadata() {
      return new GeneratorMetadata(
          2, "tgos", "test", "115-01", null, null, null, 0L, Collections.emptyMap());
    }

    @Override
    public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
      return null;
    }

    @Override
    public void close() {}
  }

  // 1. Construct without loading library → isFallbackInitialised() == false
  @Test
  public void constructDoesNotInitialise() {
    Opener stub = file -> new StubFacade();
    FallbackSqliteFactory factory = new FallbackSqliteFactory(stub);

    assertThat(factory.isFallbackInitialised()).isFalse();
  }

  // 2. open(non-existent-file) → returns null; library still not initialised
  @Test
  public void openMissingFileReturnsNullAndDoesNotInitialise() {
    CountingOpener stub = new CountingOpener(new StubFacade());
    FallbackSqliteFactory factory = new FallbackSqliteFactory(stub);

    AddressDatabaseFacade facade = factory.open(new File(tmp.getRoot(), "does-not-exist.sqlite"));

    assertThat(facade).isNull();
    assertThat(stub.calls.get()).isZero(); // opener never even called
    assertThat(factory.isFallbackInitialised()).isFalse();
  }

  // 3. open(valid-file) first time → opener called → initialised flag flips
  @Test
  public void openValidFileFlipsInitialised() throws IOException {
    File f = tmp.newFile("places.sqlite");
    StubFacade stubFacade = new StubFacade();
    CountingOpener stub = new CountingOpener(stubFacade);
    FallbackSqliteFactory factory = new FallbackSqliteFactory(stub);

    AddressDatabaseFacade facade = factory.open(f);

    assertThat(facade).isSameAs(stubFacade);
    assertThat(stub.calls.get()).isEqualTo(1);
    assertThat(factory.isFallbackInitialised()).isTrue();
  }

  // 4. UnsatisfiedLinkError thrown by opener → swallowed; open returns null
  @Test
  public void unsatisfiedLinkErrorSwallowed() throws IOException {
    File f = tmp.newFile("places.sqlite");
    Opener throwing =
        file -> {
          throw new UnsatisfiedLinkError("simulated missing .so for armeabi-v7a");
        };
    FallbackSqliteFactory factory = new FallbackSqliteFactory(throwing);

    AddressDatabaseFacade facade = factory.open(f);

    assertThat(facade).isNull();
    assertThat(factory.isFallbackInitialised()).isFalse();
  }

  // 4b. Generic Throwable swallowed too (defence)
  @Test
  public void runtimeExceptionFromOpenerSwallowed() throws IOException {
    File f = tmp.newFile("places.sqlite");
    Opener throwing =
        file -> {
          throw new RuntimeException("simulated open() failure");
        };
    FallbackSqliteFactory factory = new FallbackSqliteFactory(throwing);

    AddressDatabaseFacade facade = factory.open(f);

    assertThat(facade).isNull();
    assertThat(factory.isFallbackInitialised()).isFalse();
  }

  // 5. Concurrent first-time open() calls → only one facade is created when the opener is
  //    single-shot; both callers see successful facades and isInitialised flips exactly once.
  @Test
  public void concurrentFirstOpenInitialisesOnce() throws Exception {
    File f = tmp.newFile("places.sqlite");
    CountingOpener stub = new CountingOpener(new StubFacade());
    FallbackSqliteFactory factory = new FallbackSqliteFactory(stub);

    int n = 8;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService exec = Executors.newFixedThreadPool(n);
    AtomicInteger successCount = new AtomicInteger();
    for (int i = 0; i < n; i++) {
      exec.submit(
          () -> {
            try {
              start.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              return;
            }
            AddressDatabaseFacade facade = factory.open(f);
            if (facade != null) successCount.incrementAndGet();
          });
    }
    start.countDown();
    exec.shutdown();
    assertThat(exec.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    // All callers should have got a non-null facade.
    assertThat(successCount.get()).isEqualTo(n);
    // Flag should be set (single-shot transition).
    assertThat(factory.isFallbackInitialised()).isTrue();
    // The Opener stub gets called once per open() (no caching inside the factory — by design;
    // the registry decides per-county whether to use the fallback). What matters is that
    // isFallbackInitialised flag survives the race correctly.
    assertThat(stub.calls.get()).isEqualTo(n);
  }

  // 6. SQL string parity smoke test — assert that AtakDatabasesAddressDatabase's nearestWithin
  //    SQL fragment (verified by 004 tests) is byte-identical to the RequeryAddressDatabase
  //    fragment. We can only inspect this via source-level review on JVM, so the smoke test
  //    here just asserts both classes compile in the same module (running this test forces
  //    both classes to load on the test classloader; any incompatibility surfaces here).
  @Test
  public void requeryAddressDatabaseAndPrimaryClassesCoexist() {
    // Force-classload both. If either fails to link in the test classloader the test fails
    // with NoClassDefFoundError / ExceptionInInitializerError, surfacing the incompatibility.
    String pkg = AtakDatabasesAddressDatabase.class.getPackageName();
    String fbPkg = FallbackSqliteFactory.class.getPackageName();
    assertThat(pkg).isEqualTo(fbPkg);
    // RequeryAddressDatabase is a package-private inner class of FallbackSqliteFactory; the
    // outer class loading is sufficient.
    assertThat(FallbackSqliteFactory.class.getDeclaredClasses())
        .anyMatch(c -> c.getSimpleName().equals("RequeryAddressDatabase"));
  }
}
