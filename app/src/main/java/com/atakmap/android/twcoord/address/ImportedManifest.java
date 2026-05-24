package com.atakmap.android.twcoord.address;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable view of the plugin-side {@code imported.manifest.txt} companion file written into the
 * active dataset directory immediately before atomic activation. Distinct from the generator's
 * in-DB {@code metadata} table ({@link GeneratorMetadata}) — this file carries the plugin's own
 * provenance for the imported file (when, what hash, whether the plugin built the R*Tree, which
 * plugin version performed the import).
 *
 * <p>On-disk schema (key=value, one pair per line, UTF-8) per {@code research.md §R5}:
 *
 * <pre>
 * imported_at=2026-05-24T15:30:00Z
 * file_sha256=&lt;hex 64&gt;
 * rtree_built=true
 * plugin_schema_version=1
 * </pre>
 */
public final class ImportedManifest {

  private final Instant importedAt;
  private final String fileSha256;
  private final boolean rtreeBuilt;
  private final int pluginSchemaVersion;

  public ImportedManifest(
      Instant importedAt, String fileSha256, boolean rtreeBuilt, int pluginSchemaVersion) {
    this.importedAt = Objects.requireNonNull(importedAt, "importedAt");
    this.fileSha256 = Objects.requireNonNull(fileSha256, "fileSha256");
    if (fileSha256.length() != 64) {
      throw new IllegalArgumentException(
          "fileSha256 must be 64 lowercase hex chars, got " + fileSha256.length());
    }
    this.rtreeBuilt = rtreeBuilt;
    this.pluginSchemaVersion = pluginSchemaVersion;
  }

  public Instant importedAt() {
    return importedAt;
  }

  public String fileSha256() {
    return fileSha256;
  }

  public boolean rtreeBuilt() {
    return rtreeBuilt;
  }

  public int pluginSchemaVersion() {
    return pluginSchemaVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ImportedManifest)) return false;
    ImportedManifest that = (ImportedManifest) o;
    return rtreeBuilt == that.rtreeBuilt
        && pluginSchemaVersion == that.pluginSchemaVersion
        && importedAt.equals(that.importedAt)
        && fileSha256.equals(that.fileSha256);
  }

  @Override
  public int hashCode() {
    return Objects.hash(importedAt, fileSha256, rtreeBuilt, pluginSchemaVersion);
  }
}
