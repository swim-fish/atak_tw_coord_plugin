package com.atakmap.android.twcoord.address;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.atakmap.coremap.log.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Imports an operator-picked {@code places-&lt;county&gt;.sqlite} file into the active dataset
 * directory. End-to-end flow per {@code contracts/address-bundle-importer.md §Behavioural
 * contract}:
 *
 * <ol>
 *   <li>Create staging dir {@code .staging-&lt;UUID&gt;/} under the plugin's offline-address root.
 *   <li>Stream-copy the {@link InputStream} into the staging dir while computing SHA-256.
 *   <li>Validate the staged DB read-only: {@code metadata} table + required keys + matching {@code
 *       schema_version}; {@code places} table with the columns the plugin reads.
 *   <li>If {@code places_rtree} is absent, build it in-place (open read-write, run the SQL recipe
 *       from {@code data-model.md §1.5}, close).
 *   <li>Write {@code imported.manifest.txt} with the file's SHA-256 / import timestamp / etc.
 *   <li>Rename any pre-existing {@code active/} aside as {@code active-old-&lt;ts&gt;/}.
 *   <li>Atomic-move {@code .staging-&lt;UUID&gt;/} → {@code active/}.
 *   <li>Best-effort delete the old-active dir.
 * </ol>
 *
 * <p>Any failure rolls back: staging wiped, prior active dir restored. The importer MUST NOT throw
 * out of any public method per Constitution VI — every exception is mapped to {@link
 * ImportResult.Failure} and logged at {@code Log.w}.
 */
public final class AddressBundleImporter {

  private static final String TAG = "AddressBundleImporter";
  static final String DB_FILE_NAME = "places.sqlite";
  static final String MANIFEST_FILE_NAME = "imported.manifest.txt";

  /**
   * Columns the plugin actually reads from {@code places}. Per {@code data-model.md} the validation
   * is presence-based: additional generator columns (e.g. {@code neighbor}, {@code area}, {@code
   * osm_id}) are tolerated.
   */
  static final String[] REQUIRED_PLACES_COLUMNS = {
    "id", "lat", "lon", "display_name", "display_name_halfwidth"
  };

  /** Required {@code metadata} keys; the import is rejected if any is missing. */
  static final String[] REQUIRED_METADATA_KEYS = {"schema_version", "county", "data_date"};

  private static final int COPY_BUFFER = 64 * 1024;
  private static final long PROGRESS_TICK_MS = 100L;

  /** Lowest data-contract version the plugin understands. */
  private static final int MIN_SUPPORTED_SCHEMA_VERSION = 1;

  private final FileSystem fs;
  private final ShaCalculator shaCalculator;
  private final int maxSupportedSchemaVersion;

  /**
   * @param maxSupportedSchemaVersion the highest {@code metadata.schema_version} value the plugin
   *     accepts (inclusive). Imports with {@code schema_version} in {@code
   *     [MIN_SUPPORTED_SCHEMA_VERSION, maxSupportedSchemaVersion]} pass validation. Per {@code
   *     atak-tw-address-generator/docs/data-contract.md}: v1 (2026-05-24 morning) shipped bare
   *     {@code places} + {@code places_fts}; v2 (2026-05-24 evening) adds {@code places_rtree} for
   *     native nearest-address lookup. Production passes the latest known version (currently 2).
   */
  public AddressBundleImporter(
      FileSystem fs, ShaCalculator shaCalculator, int maxSupportedSchemaVersion) {
    this.fs = Objects.requireNonNull(fs, "fs");
    this.shaCalculator = Objects.requireNonNull(shaCalculator, "shaCalculator");
    if (maxSupportedSchemaVersion < MIN_SUPPORTED_SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "maxSupportedSchemaVersion < " + MIN_SUPPORTED_SCHEMA_VERSION);
    }
    this.maxSupportedSchemaVersion = maxSupportedSchemaVersion;
  }

  // ----------------------------------------------------------------------
  // Public API
  // ----------------------------------------------------------------------

  public ImportResult importFrom(InputStream picked, ProgressListener rawListener) {
    ProgressListener listener = rawListener != null ? rawListener : NULL_LISTENER;
    Path staging = null;
    try {
      // Phase 1 — copy with SHA.
      staging = fs.createStagingDir();
      Path stagedDb = staging.resolve(DB_FILE_NAME);
      String fileSha;
      try (OutputStream sink = fs.openWrite(stagedDb);
          ShaCalculator.Tap tap = shaCalculator.tap(sink)) {
        copyWithProgress(picked, tap.stream(), listener);
        tap.close();
        fileSha = tap.hex();
      }

      // Phase 1.5 — validate metadata + places columns (read-only).
      listener.onProgress(ProgressListener.Stage.VERIFYING_METADATA, 0, 1);
      ValidationOutcome outcome = validateStagedDb(stagedDb);
      if (outcome.failure != null) {
        cleanup(staging);
        return outcome.failure;
      }
      listener.onProgress(ProgressListener.Stage.VERIFYING_METADATA, 1, 1);

      // Phase 2 — build R*Tree if absent.
      boolean rtreeBuilt;
      try {
        rtreeBuilt = buildRtreeIfAbsent(stagedDb.toFile(), listener);
      } catch (Throwable t) {
        Log.w(TAG, "R*Tree build failed", t);
        cleanup(staging);
        return ImportResult.failure(
            ImportResult.Reason.RTREE_BUILD_FAILED, oneLine(t.getMessage()));
      }

      // Write imported.manifest.txt
      ImportedManifest imported =
          new ImportedManifest(Instant.now(), fileSha, rtreeBuilt, maxSupportedSchemaVersion);
      try {
        writeImportedManifest(staging.resolve(MANIFEST_FILE_NAME), imported);
      } catch (IOException e) {
        Log.w(TAG, "writing imported.manifest.txt failed", e);
        cleanup(staging);
        return ImportResult.failure(ImportResult.Reason.IO_ERROR, oneLine(e.getMessage()));
      }

      // Phase 3 — atomic activation.
      listener.onProgress(ProgressListener.Stage.ACTIVATING, 1, 1);
      Path active = fs.getActiveDir();
      Path oldActive = null;
      if (fs.exists(active)) {
        oldActive = active.resolveSibling("active-old-" + System.currentTimeMillis());
        try {
          fs.atomicMove(active, oldActive);
        } catch (IOException e) {
          Log.w(TAG, "renaming active aside failed", e);
          cleanup(staging);
          return ImportResult.failure(
              ImportResult.Reason.ACTIVATION_RENAME_FAILED, oneLine(e.getMessage()));
        }
      }
      try {
        fs.atomicMove(staging, active);
      } catch (IOException e) {
        Log.w(TAG, "moving staging -> active failed; rolling back", e);
        if (oldActive != null) {
          // Best-effort: put the old active back so prior dataset survives.
          try {
            fs.atomicMove(oldActive, active);
          } catch (IOException rollback) {
            Log.w(TAG, "rollback of active-old -> active also failed", rollback);
          }
        }
        cleanup(staging);
        return ImportResult.failure(
            ImportResult.Reason.ACTIVATION_RENAME_FAILED, oneLine(e.getMessage()));
      }
      if (oldActive != null) {
        fs.deleteRecursively(oldActive); // best-effort
      }

      AddressDataset dataset =
          new AddressDataset(
              active.toFile(), active.resolve(DB_FILE_NAME).toFile(), outcome.metadata, imported);
      return ImportResult.success(dataset);

    } catch (InterruptedIOException e) {
      Thread.currentThread().interrupt();
      cleanup(staging);
      return ImportResult.failure(ImportResult.Reason.CANCELLED, oneLine(e.getMessage()));
    } catch (Throwable t) {
      Log.w(TAG, "importFrom threw", t);
      cleanup(staging);
      return ImportResult.failure(ImportResult.Reason.IO_ERROR, oneLine(t.getMessage()));
    }
  }

  public void removeActive() {
    try {
      Path active = fs.getActiveDir();
      if (fs.exists(active)) {
        fs.deleteRecursively(active);
      }
    } catch (Throwable t) {
      Log.w(TAG, "removeActive threw", t);
    }
  }

  /**
   * Returns the currently-active {@link AddressDataset} or {@code null} if no usable dataset is
   * installed. The latter covers every documented graceful-fallback path for US4 (see {@code
   * specs/004-offline-address/quickstart.md §6.4 SC-005}):
   *
   * <ul>
   *   <li>active dir doesn't exist (clean install, or operator removed via the Offline Address
   *       page).
   *   <li>active dir exists but {@code places.sqlite} is missing (operator deleted via ADB).
   *   <li>active dir exists but {@code imported.manifest.txt} is missing or unparseable.
   *   <li>{@code places.sqlite} present but unopenable / missing metadata table / missing required
   *       metadata keys.
   * </ul>
   *
   * <p>Each branch logs at {@link Log#w} with a specific reason so a future operator can correlate
   * a hidden address row with the on-disk state. The active dir itself is left in place; the next
   * successful import overwrites it.
   *
   * <p>NEVER throws — Constitution VI entry point.
   */
  public AddressDataset activeOrNull() {
    try {
      Path active = fs.getActiveDir();
      if (!fs.exists(active)) {
        // No log — this is the clean-install / removed state; not a fault.
        return null;
      }
      Path dbPath = active.resolve(DB_FILE_NAME);
      Path manifestPath = active.resolve(MANIFEST_FILE_NAME);
      if (!fs.exists(dbPath)) {
        Log.w(TAG, "activeOrNull: places.sqlite missing under " + active);
        return null;
      }
      if (!fs.exists(manifestPath)) {
        Log.w(TAG, "activeOrNull: imported.manifest.txt missing under " + active);
        return null;
      }
      GeneratorMetadata metadata;
      try (SQLiteDatabase db = openReadOnly(dbPath.toFile())) {
        metadata = readMetadata(db);
        if (metadata == null) {
          Log.w(TAG, "activeOrNull: metadata table empty or missing required keys at " + dbPath);
          return null;
        }
      } catch (Throwable t) {
        Log.w(TAG, "activeOrNull: failed to open " + dbPath, t);
        return null;
      }
      ImportedManifest imported = readImportedManifest(manifestPath);
      if (imported == null) {
        Log.w(TAG, "activeOrNull: imported.manifest.txt unparseable at " + manifestPath);
        return null;
      }
      return new AddressDataset(active.toFile(), dbPath.toFile(), metadata, imported);
    } catch (Throwable t) {
      Log.w(TAG, "activeOrNull threw", t);
      return null;
    }
  }

  // ----------------------------------------------------------------------
  // Internals
  // ----------------------------------------------------------------------

  /** First 16 bytes of every valid SQLite 3 file. Cheaper sanity check than openDatabase. */
  private static final byte[] SQLITE_MAGIC =
      "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

  /** First 4 bytes of a ZIP local-file header ({@code PK\003\004}). */
  private static final byte[] ZIP_MAGIC = new byte[] {0x50, 0x4B, 0x03, 0x04};

  private static SQLiteDatabase openReadOnly(File dbFile) {
    return SQLiteDatabase.openDatabase(
        dbFile.getAbsolutePath(),
        null,
        SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
  }

  /**
   * Returns {@code true} iff {@code dbFile} starts with the SQLite 3 magic header. Used as a
   * pre-flight check ahead of {@link #openReadOnly} because some platform / shim SQLite
   * implementations open near-empty / wrong-format files silently (Robolectric's SQLiteDatabase
   * shadow does this for &lt; 16-byte files). The check is cheap (one read of 16 bytes) and gives a
   * clearer rejection reason than the downstream "metadata table missing" path.
   */
  private static boolean looksLikeSqlite(File dbFile) {
    return headerMatches(dbFile, SQLITE_MAGIC);
  }

  /**
   * Returns {@code true} iff {@code dbFile} starts with the ZIP local-file header ({@code
   * PK\003\004}). Used after {@link #looksLikeSqlite} returns false so the importer can return a
   * distinct {@link ImportResult.Reason#IS_A_ZIP} failure with an "extract the .sqlite first" hint
   * — operators tend to grab the generator's {@code .zip} bundle and try to feed it whole; v1 of
   * the plugin only consumes bare {@code .sqlite} files (per {@code spec.md} Clarifications Session
   * 2026-05-24 evening).
   */
  private static boolean looksLikeZip(File dbFile) {
    return headerMatches(dbFile, ZIP_MAGIC);
  }

  /** Shared pre-flight header-bytes check. */
  private static boolean headerMatches(File dbFile, byte[] magic) {
    if (dbFile == null || !dbFile.isFile() || dbFile.length() < magic.length) {
      return false;
    }
    byte[] header = new byte[magic.length];
    try (java.io.InputStream in = new java.io.FileInputStream(dbFile)) {
      int total = 0;
      while (total < header.length) {
        int n = in.read(header, total, header.length - total);
        if (n < 0) return false;
        total += n;
      }
    } catch (IOException e) {
      return false;
    }
    for (int i = 0; i < magic.length; i++) {
      if (header[i] != magic[i]) return false;
    }
    return true;
  }

  private void copyWithProgress(InputStream src, OutputStream sink, ProgressListener listener)
      throws IOException {
    byte[] buf = new byte[COPY_BUFFER];
    long total = -1L; // SAF rarely provides a Content-Length-equivalent; we treat as unknown
    long copied = 0L;
    long lastTick = System.currentTimeMillis();
    listener.onProgress(ProgressListener.Stage.COPYING, 0, total);
    int n;
    while ((n = src.read(buf)) > 0) {
      if (Thread.currentThread().isInterrupted()) {
        throw new InterruptedIOException("import cancelled");
      }
      sink.write(buf, 0, n);
      copied += n;
      long now = System.currentTimeMillis();
      if (now - lastTick >= PROGRESS_TICK_MS) {
        listener.onProgress(ProgressListener.Stage.COPYING, copied, total);
        lastTick = now;
      }
    }
    listener.onProgress(ProgressListener.Stage.COPYING, copied, copied);
  }

  /**
   * Open the staged DB read-only and verify the {@code metadata} and {@code places} schemas match
   * the plugin's expectations. Returns either a populated {@link ValidationOutcome} with the parsed
   * {@link GeneratorMetadata} or a populated {@code outcome.failure}.
   */
  private ValidationOutcome validateStagedDb(Path stagedDb) {
    File dbFile = stagedDb.toFile();
    if (!looksLikeSqlite(dbFile)) {
      if (looksLikeZip(dbFile)) {
        return ValidationOutcome.failure(
            ImportResult.Reason.IS_A_ZIP,
            "extract the .sqlite from the .zip first (v1 does not unzip)");
      }
      return ValidationOutcome.failure(
          ImportResult.Reason.NOT_OPENABLE, "file is not a valid SQLite database");
    }
    SQLiteDatabase db;
    try {
      db = openReadOnly(dbFile);
    } catch (Throwable t) {
      Log.w(TAG, "staged DB not openable", t);
      return ValidationOutcome.failure(ImportResult.Reason.NOT_OPENABLE, oneLine(t.getMessage()));
    }
    try {
      if (!tableExists(db, "metadata")) {
        return ValidationOutcome.failure(
            ImportResult.Reason.MISSING_METADATA_TABLE, "no `metadata` table");
      }
      GeneratorMetadata metadata = readMetadata(db);
      if (metadata == null) {
        // readMetadata returns null only when a required key is missing.
        return ValidationOutcome.failure(
            ImportResult.Reason.MISSING_REQUIRED_METADATA_KEY,
            "one of [" + String.join(", ", REQUIRED_METADATA_KEYS) + "] is missing");
      }
      if (metadata.schemaVersion() < MIN_SUPPORTED_SCHEMA_VERSION
          || metadata.schemaVersion() > maxSupportedSchemaVersion) {
        return ValidationOutcome.failure(
            ImportResult.Reason.UNSUPPORTED_SCHEMA_VERSION,
            "supported "
                + MIN_SUPPORTED_SCHEMA_VERSION
                + ".."
                + maxSupportedSchemaVersion
                + ", got "
                + metadata.schemaVersion());
      }
      if (!tableExists(db, "places")) {
        return ValidationOutcome.failure(
            ImportResult.Reason.MISSING_PLACES_TABLE, "no `places` table");
      }
      Set<String> placesCols = columnsOf(db, "places");
      Set<String> missing = new HashSet<>();
      for (String c : REQUIRED_PLACES_COLUMNS) {
        if (!placesCols.contains(c)) missing.add(c);
      }
      if (!missing.isEmpty()) {
        return ValidationOutcome.failure(
            ImportResult.Reason.UNEXPECTED_PLACES_COLUMNS,
            "places missing required columns: " + String.join(", ", missing));
      }
      return ValidationOutcome.success(metadata);
    } finally {
      try {
        db.close();
      } catch (Throwable ignored) {
        // best effort
      }
    }
  }

  /**
   * If the staged DB does not already contain a {@code places_rtree} virtual table, open it
   * read-write and populate one from {@code places}. Returns {@code true} iff the plugin built the
   * index (false if the generator already shipped one).
   */
  private boolean buildRtreeIfAbsent(File dbFile, ProgressListener listener) throws IOException {
    SQLiteDatabase probe = openReadOnly(dbFile);
    try {
      if (tableExists(probe, "places_rtree")) {
        return false;
      }
    } finally {
      try {
        probe.close();
      } catch (Throwable ignored) {
        // best effort
      }
    }

    listener.onProgress(ProgressListener.Stage.BUILDING_RTREE, 0, 1);
    SQLiteDatabase rw =
        SQLiteDatabase.openDatabase(
            dbFile.getAbsolutePath(),
            null,
            SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    try {
      rw.execSQL(
          "CREATE VIRTUAL TABLE IF NOT EXISTS places_rtree USING rtree("
              + "id, min_lat, max_lat, min_lon, max_lon)");
      rw.execSQL(
          "INSERT INTO places_rtree(id, min_lat, max_lat, min_lon, max_lon)"
              + " SELECT id, lat, lat, lon, lon FROM places"
              + " WHERE NOT EXISTS (SELECT 1 FROM places_rtree WHERE id = places.id)");
      rw.execSQL("ANALYZE places_rtree");
    } finally {
      try {
        rw.close();
      } catch (Throwable ignored) {
        // best effort
      }
    }
    listener.onProgress(ProgressListener.Stage.BUILDING_RTREE, 1, 1);
    return true;
  }

  private static boolean tableExists(SQLiteDatabase db, String name) {
    try (Cursor c =
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type IN ('table','virtual table') AND name=?",
            new String[] {name})) {
      return c.moveToFirst();
    } catch (Throwable t) {
      return false;
    }
  }

  private static Set<String> columnsOf(SQLiteDatabase db, String table) {
    Set<String> cols = new HashSet<>();
    try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      int nameIdx = c.getColumnIndex("name");
      while (c.moveToNext()) {
        cols.add(c.getString(nameIdx));
      }
    } catch (Throwable t) {
      // empty set
    }
    return cols;
  }

  /** Returns {@code null} if any required metadata key is missing. */
  private static GeneratorMetadata readMetadata(SQLiteDatabase db) {
    Map<String, String> raw = new LinkedHashMap<>();
    try (Cursor c = db.rawQuery("SELECT key, value FROM metadata", null)) {
      while (c.moveToNext()) {
        raw.put(c.getString(0), c.getString(1));
      }
    } catch (Throwable t) {
      return null;
    }
    for (String required : REQUIRED_METADATA_KEYS) {
      if (!raw.containsKey(required)) return null;
    }
    int schemaVersion;
    try {
      schemaVersion = Integer.parseInt(raw.get("schema_version").trim());
    } catch (NumberFormatException e) {
      // Mark schema_version "absent" so the importer reports MISSING_REQUIRED_METADATA_KEY;
      // an unparseable value is equivalent.
      return null;
    }
    long inserted = parseLongOrDefault(raw.get("inserted"), -1L);
    return new GeneratorMetadata(
        schemaVersion,
        raw.get("source"),
        raw.get("county"),
        raw.get("data_date"),
        raw.get("csv_sha256"),
        raw.get("csv_path"),
        raw.get("crs"),
        inserted,
        raw);
  }

  private static long parseLongOrDefault(String s, long defaultValue) {
    if (s == null) return defaultValue;
    try {
      return Long.parseLong(s.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static void writeImportedManifest(Path path, ImportedManifest m) throws IOException {
    try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
      w.write("imported_at=");
      w.write(m.importedAt().toString());
      w.newLine();
      w.write("file_sha256=");
      w.write(m.fileSha256());
      w.newLine();
      w.write("rtree_built=");
      w.write(Boolean.toString(m.rtreeBuilt()));
      w.newLine();
      w.write("plugin_schema_version=");
      w.write(Integer.toString(m.pluginSchemaVersion()));
      w.newLine();
    }
  }

  /**
   * Read the plugin-side {@code imported.manifest.txt}. Returns {@code null} on any parse / IO
   * failure — caller treats this as "no active dataset".
   */
  static ImportedManifest readImportedManifest(Path path) {
    try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      Map<String, String> kv = new LinkedHashMap<>();
      String line;
      while ((line = r.readLine()) != null) {
        int eq = line.indexOf('=');
        if (eq <= 0) continue;
        kv.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
      }
      String importedAtStr = kv.get("imported_at");
      String fileSha = kv.get("file_sha256");
      String rtreeBuiltStr = kv.get("rtree_built");
      String schemaVersionStr = kv.get("plugin_schema_version");
      if (importedAtStr == null
          || fileSha == null
          || rtreeBuiltStr == null
          || schemaVersionStr == null) {
        return null;
      }
      Instant importedAt;
      try {
        importedAt = Instant.parse(importedAtStr);
      } catch (DateTimeParseException e) {
        return null;
      }
      int schemaVersion;
      try {
        schemaVersion = Integer.parseInt(schemaVersionStr);
      } catch (NumberFormatException e) {
        return null;
      }
      return new ImportedManifest(
          importedAt, fileSha, Boolean.parseBoolean(rtreeBuiltStr), schemaVersion);
    } catch (NoSuchFileException e) {
      return null;
    } catch (Throwable t) {
      Log.w(TAG, "imported.manifest.txt unreadable at " + path, t);
      return null;
    }
  }

  private void cleanup(Path staging) {
    if (staging != null) {
      try {
        fs.deleteRecursively(staging);
      } catch (Throwable t) {
        Log.w(TAG, "staging cleanup threw", t);
      }
    }
  }

  private static String oneLine(String s) {
    if (s == null) return "";
    return s.replace('\n', ' ').replace('\r', ' ').trim();
  }

  // ----------------------------------------------------------------------
  // Result / progress types
  // ----------------------------------------------------------------------

  public interface ProgressListener {
    void onProgress(Stage stage, long completedBytes, long totalBytes);

    enum Stage {
      COPYING,
      VERIFYING_METADATA,
      BUILDING_RTREE,
      ACTIVATING
    }
  }

  private static final ProgressListener NULL_LISTENER = (stage, completed, total) -> {};

  public abstract static class ImportResult {
    private ImportResult() {}

    public static Success success(AddressDataset dataset) {
      return new Success(dataset);
    }

    public static Failure failure(Reason reason, String details) {
      return new Failure(reason, details);
    }

    public boolean isSuccess() {
      return this instanceof Success;
    }

    public boolean isFailure() {
      return this instanceof Failure;
    }

    public enum Reason {
      NOT_OPENABLE,
      /**
       * Operator picked a {@code .zip} bundle by mistake. v1 of the plugin expects a bare {@code
       * .sqlite} per {@code spec.md} Clarifications Session 2026-05-24 evening; full zip-bundle
       * unpacking is deferred to a follow-up feature.
       */
      IS_A_ZIP,
      MISSING_METADATA_TABLE,
      MISSING_REQUIRED_METADATA_KEY,
      UNSUPPORTED_SCHEMA_VERSION,
      MISSING_PLACES_TABLE,
      UNEXPECTED_PLACES_COLUMNS,
      RTREE_BUILD_FAILED,
      DISK_FULL,
      ACTIVATION_RENAME_FAILED,
      CANCELLED,
      IO_ERROR
    }

    public static final class Success extends ImportResult {
      private final AddressDataset dataset;

      private Success(AddressDataset dataset) {
        this.dataset = Objects.requireNonNull(dataset, "dataset");
      }

      public AddressDataset dataset() {
        return dataset;
      }
    }

    public static final class Failure extends ImportResult {
      private final Reason reason;
      private final String details;

      private Failure(Reason reason, String details) {
        this.reason = Objects.requireNonNull(reason, "reason");
        this.details = details == null ? "" : details;
      }

      public Reason reason() {
        return reason;
      }

      public String details() {
        return details;
      }
    }
  }

  private static final class ValidationOutcome {
    final GeneratorMetadata metadata;
    final ImportResult.Failure failure;

    private ValidationOutcome(GeneratorMetadata metadata, ImportResult.Failure failure) {
      this.metadata = metadata;
      this.failure = failure;
    }

    static ValidationOutcome success(GeneratorMetadata metadata) {
      return new ValidationOutcome(metadata, null);
    }

    static ValidationOutcome failure(ImportResult.Reason reason, String details) {
      return new ValidationOutcome(
          null, (ImportResult.Failure) ImportResult.failure(reason, details));
    }
  }
}
