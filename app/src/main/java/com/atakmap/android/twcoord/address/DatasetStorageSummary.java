package com.atakmap.android.twcoord.address;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Feature 007 US3 — computes the on-disk storage footprint shown on the TW Offline Addr page:
 * per-county dataset folder sizes plus the shared {@code _boundary} (townships.sqlite) folder size.
 * Pure logic over the {@link FileSystem} seam (uses {@link FileSystem#sizeOfDirectory}), so it is
 * unit-testable with a real temp-dir-backed {@code AtakFileSystem}. Best-effort — absent/partial
 * paths report {@code 0} rather than throwing.
 */
public final class DatasetStorageSummary {

  /** One county's on-disk footprint (the whole {@code active/<county>/} folder). */
  public static final class CountyStorage {
    private final String countyZh;
    private final long bytes;

    public CountyStorage(String countyZh, long bytes) {
      this.countyZh = countyZh;
      this.bytes = bytes;
    }

    public String countyZh() {
      return countyZh;
    }

    public long bytes() {
      return bytes;
    }
  }

  /** The shared boundary folder ({@code active/_boundary/}) footprint. */
  public static final class BoundaryStorage {
    private final boolean present;
    private final long bytes;

    public BoundaryStorage(boolean present, long bytes) {
      this.present = present;
      this.bytes = bytes;
    }

    /** True when {@code townships.sqlite} exists; false ⇒ render "未安裝". */
    public boolean present() {
      return present;
    }

    public long bytes() {
      return bytes;
    }
  }

  private final FileSystem fs;
  private final ActiveDatasetRegistry registry;

  public DatasetStorageSummary(FileSystem fs, ActiveDatasetRegistry registry) {
    this.fs = Objects.requireNonNull(fs, "fs");
    this.registry = Objects.requireNonNull(registry, "registry");
  }

  /** Per-county sizes in registry snapshot order. Empty when no counties are active. */
  public List<CountyStorage> perCounty() {
    List<CountyStorage> out = new ArrayList<>();
    for (CountyActiveDataset c : registry.snapshot().values()) {
      String county = c.county();
      long bytes = fs.sizeOfDirectory(fs.activeCountyDir(county));
      out.add(new CountyStorage(county, bytes));
    }
    return out;
  }

  /** Boundary-folder size; {@code present=false} when {@code townships.sqlite} is absent. */
  public BoundaryStorage boundary() {
    boolean present = fs.exists(fs.boundaryDbFile());
    long bytes = present ? fs.sizeOfDirectory(fs.boundaryDir()) : 0L;
    return new BoundaryStorage(present, bytes);
  }
}
