package com.atakmap.android.twcoord.address;

import java.io.File;
import java.util.Objects;

/**
 * The active offline-address dataset on disk — a directory holding the generator's {@code
 * places-&lt;county&gt;.sqlite} file plus the plugin-side {@code imported.manifest.txt} companion.
 *
 * <p>Exactly zero or one {@link AddressDataset} is active at any time. Construction implies
 * activation has completed atomically (per {@code research.md §R8}); rolled-back imports never
 * produce an {@link AddressDataset} instance.
 */
public final class AddressDataset {

  private final File rootDir;
  private final File dbFile;
  private final GeneratorMetadata generator;
  private final ImportedManifest imported;

  public AddressDataset(
      File rootDir, File dbFile, GeneratorMetadata generator, ImportedManifest imported) {
    this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
    this.dbFile = Objects.requireNonNull(dbFile, "dbFile");
    this.generator = Objects.requireNonNull(generator, "generator");
    this.imported = Objects.requireNonNull(imported, "imported");
  }

  public File rootDir() {
    return rootDir;
  }

  public File dbFile() {
    return dbFile;
  }

  public GeneratorMetadata generator() {
    return generator;
  }

  public ImportedManifest imported() {
    return imported;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AddressDataset)) return false;
    AddressDataset that = (AddressDataset) o;
    return rootDir.equals(that.rootDir)
        && dbFile.equals(that.dbFile)
        && generator.equals(that.generator)
        && imported.equals(that.imported);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rootDir, dbFile, generator, imported);
  }
}
