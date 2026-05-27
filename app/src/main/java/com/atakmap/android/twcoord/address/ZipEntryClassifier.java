package com.atakmap.android.twcoord.address;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies a ZIP entry name into one of three buckets so {@code ZipExtractor} knows what to do
 * with the bytes:
 *
 * <ul>
 *   <li>{@link Classification#PLACES_COUNTY} — matches {@code places-<county>.sqlite}; the
 *       extractor streams it into a per-county staging directory.
 *   <li>{@link Classification#SKIPPED_SUPPLEMENTARY} — {@code townships.sqlite}, {@code
 *       roads.sqlite}, {@code places-osm.sqlite}, any {@code timestamp.*} sidecar, any {@code
 *       *.manifest.txt} sidecar. v1.0.6 does not consume these; feature 006+ will.
 *   <li>{@link Classification#UNRECOGNIZED} — anything else (logged at {@code Log.w} by the
 *       extractor; not counted as a failure).
 * </ul>
 *
 * <p>The classifier also defends against zip-slip: entries with {@code ..}, leading {@code /} or
 * {@code \\}, or absolute paths are classified UNRECOGNIZED (the extractor will refuse to write
 * them). See data-model.md §6 and contracts/zip-extractor.md.
 *
 * <p>Matching is case-sensitive — the generator's data-contract §2 specifies lowercase filenames
 * only. Future generator changes that introduce mixed case should update this contract first.
 */
public final class ZipEntryClassifier {

  public enum Classification {
    PLACES_COUNTY,
    SKIPPED_SUPPLEMENTARY,
    UNRECOGNIZED
  }

  // ^places-(.+)\.sqlite$ where the group is the county name; >=1 char to reject `places-.sqlite`.
  private static final Pattern PLACES_COUNTY_PATTERN = Pattern.compile("^places-(.+)\\.sqlite$");

  // Exact supplementary names that v1.0.6 silently ignores.
  private static final java.util.Set<String> SUPPLEMENTARY_EXACT =
      java.util.Set.of("townships.sqlite", "roads.sqlite", "places-osm.sqlite");

  public Classification classify(String entryName) {
    if (entryName == null || entryName.isEmpty()) return Classification.UNRECOGNIZED;
    // Zip-slip / absolute-path defence (data-model.md §6 + research R2 + R6).
    if (entryName.contains("..")
        || entryName.startsWith("/")
        || entryName.startsWith("\\")
        || entryName.contains(":")) {
      return Classification.UNRECOGNIZED;
    }
    if (SUPPLEMENTARY_EXACT.contains(entryName)) return Classification.SKIPPED_SUPPLEMENTARY;
    if (entryName.startsWith("timestamp.")) return Classification.SKIPPED_SUPPLEMENTARY;
    if (entryName.endsWith(".manifest.txt")) return Classification.SKIPPED_SUPPLEMENTARY;
    if (PLACES_COUNTY_PATTERN.matcher(entryName).matches()) return Classification.PLACES_COUNTY;
    return Classification.UNRECOGNIZED;
  }

  /**
   * Extract the county portion of {@code places-<county>.sqlite}. Returns empty for anything that
   * doesn't match the PLACES_COUNTY pattern (including names that are also explicitly listed in
   * {@code SUPPLEMENTARY_EXACT}; that path has already short-circuited in {@link #classify}).
   */
  public Optional<String> countyFromEntry(String entryName) {
    if (entryName == null) return Optional.empty();
    if (SUPPLEMENTARY_EXACT.contains(entryName)) return Optional.empty();
    Matcher m = PLACES_COUNTY_PATTERN.matcher(entryName);
    if (!m.matches()) return Optional.empty();
    return Optional.of(m.group(1));
  }
}
