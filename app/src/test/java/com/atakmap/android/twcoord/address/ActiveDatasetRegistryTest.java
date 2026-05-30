package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atakmap.android.twcoord.address.ActiveDatasetRegistry.Change;
import com.atakmap.android.twcoord.address.AddressBundleImporterTest.TempFileSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Feature 005 — Robolectric tests for {@link ActiveDatasetRegistry} per
 * contracts/active-dataset-registry.md (11 cases).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class ActiveDatasetRegistryTest {

  @Rule public final TemporaryFolder tmp = new TemporaryFolder();

  private TempFileSystem fs;
  private AddressBundleImporter importer;
  private StubFactory primary;
  private CountingSupplier<AddressDatabaseFacade.Factory> fallbackSupplier;

  @Before
  public void setUp() throws IOException {
    fs = new TempFileSystem(tmp.getRoot().toPath());
    importer = new AddressBundleImporter(fs, new MessageDigestShaCalculator(), 2);
    primary = new StubFactory();
    fallbackSupplier = new CountingSupplier<>(() -> new StubFactory());
  }

  // 1. initFromDisk on empty active/ → snapshot empty; no listener fires
  @Test
  public void initFromDiskEmpty() {
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
    CountingListener listener = new CountingListener();
    r.addListener(listener);

    r.initFromDisk();

    assertThat(r.snapshot()).isEmpty();
    assertThat(listener.events).isEmpty();
  }

  // 2. initFromDisk with 2 valid county dirs → snapshot has 2; 2 ADDED events
  @Test
  public void initFromDiskTwoValidCounties() throws Exception {
    importer.importFromInto(new ByteArrayInputStream(buildFixture("台中市")), "台中市", null);
    importer.importFromInto(new ByteArrayInputStream(buildFixture("彰化縣")), "彰化縣", null);
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
    CountingListener listener = new CountingListener();
    r.addListener(listener);

    r.initFromDisk();

    assertThat(r.snapshot()).containsOnlyKeys("台中市", "彰化縣");
    assertThat(listener.events)
        .extracting(e -> e.change)
        .containsExactlyInAnyOrder(Change.ADDED, Change.ADDED);
  }

  // 3. initFromDisk with 1 valid + 1 corrupt → valid added; corrupt skipped + logged
  @Test
  public void initFromDiskSkipsCorruptCounty() throws Exception {
    importer.importFromInto(new ByteArrayInputStream(buildFixture("台中市")), "台中市", null);
    // Plant a corrupt county dir (places.sqlite missing) under active/.
    Path corruptDir = fs.activeCountyDir("假縣");
    Files.createDirectories(corruptDir);
    // (no places.sqlite — importer.activeForCounty returns null)
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);

    r.initFromDisk();

    assertThat(r.snapshot()).containsOnlyKeys("台中市");
  }

  // 4. add(台中市) then add(彰化縣) → snapshot has 2; 2 ADDED events
  @Test
  public void addTwoCountiesYieldsTwoEvents() throws Exception {
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
    CountingListener listener = new CountingListener();
    r.addListener(listener);

    r.add(fakeDataset("台中市"));
    r.add(fakeDataset("彰化縣"));

    assertThat(r.snapshot()).containsOnlyKeys("台中市", "彰化縣");
    assertThat(listener.events).hasSize(2);
    assertThat(listener.events).allMatch(e -> e.change == Change.ADDED);
  }

  // 5. add(台中市) then replace(台中市) → snapshot has 1; old facade closed; 1 ADDED + 1 REPLACED
  @Test
  public void replaceClosesPreviousFacade() {
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
    CountingFacade first = new CountingFacade();
    CountingFacade second = new CountingFacade();
    r.add(fakeDataset("台中市", first));
    r.replace(fakeDataset("台中市", second));

    assertThat(r.snapshot()).containsOnlyKeys("台中市");
    assertThat(first.closeCount.get()).isEqualTo(1);
    assertThat(second.closeCount.get()).isZero();
    assertThat(r.snapshot().get("台中市").facade()).isSameAs(second);
  }

  // 6. remove(台中市) after add → snapshot empty; facade closed; 1 REMOVED event
  @Test
  public void removeClosesFacadeAndFiresEvent() {
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
    CountingFacade facade = new CountingFacade();
    r.add(fakeDataset("台中市", facade));
    CountingListener listener = new CountingListener();
    r.addListener(listener);

    r.remove("台中市");

    assertThat(r.snapshot()).isEmpty();
    assertThat(facade.closeCount.get()).isEqualTo(1);
    assertThat(listener.events).hasSize(1);
    assertThat(listener.events.get(0).change).isEqualTo(Change.REMOVED);
  }

  // 7. Primary factory fails, fallback succeeds → fallback supplier invoked once
  @Test
  public void primaryFailureEscalatesToFallback() throws Exception {
    importer.importFromInto(new ByteArrayInputStream(buildFixture("台中市")), "台中市", null);
    // Primary returns null for any open.
    StubFactory failingPrimary = new StubFactory();
    failingPrimary.returnNull = true;
    ActiveDatasetRegistry r =
        new ActiveDatasetRegistry(importer, failingPrimary, fallbackSupplier, fs);

    r.initFromDisk();

    assertThat(r.snapshot()).containsKey("台中市");
    assertThat(fallbackSupplier.calls.get()).isEqualTo(1);
    assertThat(r.isFallbackInitialised()).isTrue();
  }

  // 8. Primary fails, fallback also fails → county not added; no events
  @Test
  public void bothPrimaryAndFallbackFailLeavesCountyOut() throws Exception {
    importer.importFromInto(new ByteArrayInputStream(buildFixture("台中市")), "台中市", null);
    StubFactory failingPrimary = new StubFactory();
    failingPrimary.returnNull = true;
    StubFactory failingFallback = new StubFactory();
    failingFallback.returnNull = true;
    CountingSupplier<AddressDatabaseFacade.Factory> fbSupplier =
        new CountingSupplier<>(() -> failingFallback);

    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, failingPrimary, fbSupplier, fs);
    CountingListener listener = new CountingListener();
    r.addListener(listener);

    r.initFromDisk();

    assertThat(r.snapshot()).isEmpty();
    assertThat(listener.events).isEmpty();
  }

  // 9. Listener throws → exception caught; other listeners still called; map mutation succeeds
  @Test
  public void listenerThrowsDoesNotBreakOthers() {
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
    AtomicInteger goodCalls = new AtomicInteger();
    r.addListener(
        (county, change) -> {
          throw new RuntimeException("boom");
        });
    r.addListener((county, change) -> goodCalls.incrementAndGet());

    r.add(fakeDataset("台中市"));

    assertThat(r.snapshot()).containsKey("台中市");
    assertThat(goodCalls.get()).isEqualTo(1);
  }

  // 10. deregisterOnTamper called twice → first removes; second is no-op (one event total)
  @Test
  public void deregisterOnTamperIdempotent() {
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
    r.add(fakeDataset("台中市"));
    CountingListener listener = new CountingListener();
    r.addListener(listener);

    r.deregisterOnTamper("台中市");
    r.deregisterOnTamper("台中市"); // idempotent

    assertThat(r.snapshot()).doesNotContainKey("台中市");
    assertThat(listener.events).hasSize(1);
    assertThat(listener.events.get(0).change).isEqualTo(Change.TAMPERED);
  }

  // 11. snapshot is unmodifiable
  @Test
  public void snapshotIsUnmodifiable() {
    ActiveDatasetRegistry r = new ActiveDatasetRegistry(importer, primary, fallbackSupplier, fs);
    r.add(fakeDataset("台中市"));

    assertThatThrownBy(() -> r.snapshot().put("彰化縣", fakeDataset("彰化縣")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ----------------------------------------------------------------------
  // Fixture + test doubles
  // ----------------------------------------------------------------------

  /**
   * Builds a minimal v2 SQLite fixture for the given county. (Importer is called with this so it
   * lands in active/<county>/places.sqlite via the real on-disk path.)
   */
  private byte[] buildFixture(String county) throws Exception {
    Path fixture = tmp.newFile("fx-" + county + "-" + UUID.randomUUID() + ".sqlite").toPath();
    Files.delete(fixture);
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + fixture)) {
      try (Statement s = c.createStatement()) {
        s.executeUpdate("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        s.executeUpdate("INSERT INTO metadata VALUES ('schema_version','2')");
        s.executeUpdate("INSERT INTO metadata VALUES ('source','tgos')");
        s.executeUpdate("INSERT INTO metadata VALUES ('county','" + county + "')");
        s.executeUpdate("INSERT INTO metadata VALUES ('data_date','115-01')");
        s.executeUpdate(
            "CREATE TABLE places (id INTEGER PRIMARY KEY, source TEXT NOT NULL, osm_id INTEGER,"
                + " lat REAL NOT NULL, lon REAL NOT NULL,"
                + " name TEXT, display_name TEXT NOT NULL, display_name_halfwidth TEXT NOT NULL,"
                + " district_code TEXT NOT NULL, county TEXT NOT NULL, township TEXT NOT NULL,"
                + " village TEXT, neighbor TEXT, street TEXT, area TEXT,"
                + " lane TEXT, alley TEXT, number TEXT)");
        s.executeUpdate(
            "CREATE VIRTUAL TABLE places_rtree USING rtree("
                + "id, min_lat, max_lat, min_lon, max_lon)");
      }
    }
    return Files.readAllBytes(fixture);
  }

  private static CountyActiveDataset fakeDataset(String county) {
    return fakeDataset(county, new CountingFacade());
  }

  private static CountyActiveDataset fakeDataset(String county, AddressDatabaseFacade facade) {
    GeneratorMetadata gen =
        new GeneratorMetadata(
            2, "tgos", county, "115-01", null, null, null, 0L, Collections.emptyMap());
    String fakeSha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    ImportedManifest im = new ImportedManifest(java.time.Instant.now(), fakeSha, false, 2);
    AddressDataset ds =
        new AddressDataset(
            new java.io.File("/tmp/fake/" + county),
            new java.io.File("/tmp/fake/" + county + "/places.sqlite"),
            gen,
            im);
    return new CountyActiveDataset(county, ds, facade);
  }

  /** AddressDatabaseFacade.Factory stub returning a per-call StubFacade (or null on flag). */
  private static final class StubFactory implements AddressDatabaseFacade.Factory {
    boolean returnNull = false;

    @Override
    public AddressDatabaseFacade open(java.io.File dbFile) {
      return returnNull ? null : new CountingFacade();
    }
  }

  /** AddressDatabaseFacade stub that counts close() invocations. */
  private static final class CountingFacade implements AddressDatabaseFacade {
    final AtomicInteger closeCount = new AtomicInteger();

    @Override
    public GeneratorMetadata readMetadata() {
      return new GeneratorMetadata(
          2, "tgos", "stub", "115-01", null, null, null, 0L, Collections.emptyMap());
    }

    @Override
    public AddressRecord nearestWithin(double lat, double lon, double radiusMeters) {
      return null;
    }

    @Override
    public void close() {
      closeCount.incrementAndGet();
    }
  }

  /** Captures listener events for assertion. */
  private static final class CountingListener implements ActiveDatasetRegistry.Listener {
    final List<Event> events = new ArrayList<>();

    @Override
    public void onChange(String county, Change change) {
      events.add(new Event(county, change));
    }

    static final class Event {
      final String county;
      final Change change;

      Event(String county, Change change) {
        this.county = county;
        this.change = change;
      }
    }
  }

  /** Supplier wrapper that counts how many times {@link #get()} was invoked. */
  private static final class CountingSupplier<T> implements Supplier<T> {
    private final Supplier<T> delegate;
    final AtomicInteger calls = new AtomicInteger();

    CountingSupplier(Supplier<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T get() {
      calls.incrementAndGet();
      return delegate.get();
    }
  }
}
