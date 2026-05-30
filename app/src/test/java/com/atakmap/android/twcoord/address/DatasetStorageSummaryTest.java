package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Feature 007 US3 — {@link DatasetStorageSummary} over a real temp-dir {@link AtakFileSystem}. */
public class DatasetStorageSummaryTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private AtakFileSystem fs;
  private ActiveDatasetRegistry registry;

  @Before
  public void setUp() {
    fs = new AtakFileSystem(tmp.getRoot().toPath());
    @SuppressWarnings("unchecked")
    Supplier<AddressDatabaseFacade.Factory> fallback = () -> null;
    registry =
        new ActiveDatasetRegistry(
            mock(AddressBundleImporter.class),
            mock(AddressDatabaseFacade.Factory.class),
            fallback,
            fs);
  }

  private void writeFile(Path p, int bytes) throws Exception {
    Files.createDirectories(p.getParent());
    Files.write(p, new byte[bytes]);
  }

  private void addCounty(String county) {
    registry.add(
        new CountyActiveDataset(
            county, mock(AddressDataset.class), mock(AddressDatabaseFacade.class)));
  }

  @Test
  public void perCountySumsTheWholeCountyFolderIncludingSidecars() throws Exception {
    writeFile(fs.activeCountyDir("台中市").resolve("places.sqlite"), 1000);
    writeFile(fs.activeCountyDir("台中市").resolve("places.sqlite-wal"), 24);
    addCounty("台中市");

    assertThat(summary().perCounty())
        .hasSize(1)
        .first()
        .satisfies(
            c -> {
              assertThat(c.countyZh()).isEqualTo("台中市");
              assertThat(c.bytes()).isEqualTo(1024);
            });
  }

  @Test
  public void boundaryPresentSumsTheBoundaryFolder() throws Exception {
    writeFile(fs.boundaryDbFile(), 2048);
    DatasetStorageSummary.BoundaryStorage b = summary().boundary();
    assertThat(b.present()).isTrue();
    assertThat(b.bytes()).isEqualTo(2048);
  }

  @Test
  public void boundaryAbsentReportsNotPresentZero() {
    DatasetStorageSummary.BoundaryStorage b = summary().boundary();
    assertThat(b.present()).isFalse();
    assertThat(b.bytes()).isZero();
  }

  @Test
  public void noCountiesYieldsEmptyPerCounty() {
    assertThat(summary().perCounty()).isEmpty();
  }

  private DatasetStorageSummary summary() {
    return new DatasetStorageSummary(fs, registry);
  }
}
