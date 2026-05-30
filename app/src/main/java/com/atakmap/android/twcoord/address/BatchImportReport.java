package com.atakmap.android.twcoord.address;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Summary of a batch import session. One {@link Entry} per file/entry processed; the page renders
 * each as a row and the footer counts {@link #activatedCount()} + {@link #replacedCount()} + {@link
 * #skippedCount()} + {@link #failedCount()}.
 *
 * <p>Per data-model.md §2.4. Serialised to logcat per data-model.md §8 (one-line summary in {@link
 * #toString()}).
 */
public final class BatchImportReport {

  /** Status of one processed file/ZIP-entry. Mirrors data-model.md §2.4. */
  public enum Status {
    ACTIVATED, // new county added
    REPLACED, // existing county overwritten with new data_date
    SKIPPED_SUPPLEMENTARY, // townships / roads / osm / timestamp.* / *.manifest.txt
    SKIPPED_DUPLICATE, // same county appeared twice in same batch
    SKIPPED_COUNTY_MISMATCH, // Replace target's metadata.county didn't match
    FAILED // validation or extraction failure; see Entry.details
  }

  /**
   * One row of the report. Immutable once written; the coordinator constructs an Entry per
   * processed item.
   */
  public static final class Entry {
    private final String
        filename; // e.g. "places-taichung.sqlite" or "tw-central-full.zip/places-changhua.sqlite"
    private final String county; // nullable: null if the entry never reached county-extraction
    private final Status status;
    private final String details; // nullable; human-readable reason
    private final long durationMs;

    public Entry(String filename, String county, Status status, String details, long durationMs) {
      this.filename = Objects.requireNonNull(filename, "filename");
      this.county = county;
      this.status = Objects.requireNonNull(status, "status");
      this.details = details;
      this.durationMs = durationMs;
    }

    public String filename() {
      return filename;
    }

    public String county() {
      return county;
    }

    public Status status() {
      return status;
    }

    public String details() {
      return details;
    }

    public long durationMs() {
      return durationMs;
    }

    @Override
    public String toString() {
      return "Entry{"
          + filename
          + " county="
          + county
          + " status="
          + status
          + (details == null ? "" : " details=\"" + details + "\"")
          + " ms="
          + durationMs
          + "}";
    }
  }

  private final List<Entry> entries;

  public BatchImportReport(List<Entry> entries) {
    this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
  }

  public List<Entry> entries() {
    return entries;
  }

  public int activatedCount() {
    return count(Status.ACTIVATED);
  }

  public int replacedCount() {
    return count(Status.REPLACED);
  }

  public int skippedCount() {
    int n = 0;
    for (Entry e : entries) {
      if (e.status == Status.SKIPPED_SUPPLEMENTARY
          || e.status == Status.SKIPPED_DUPLICATE
          || e.status == Status.SKIPPED_COUNTY_MISMATCH) {
        n++;
      }
    }
    return n;
  }

  public int failedCount() {
    return count(Status.FAILED);
  }

  private int count(Status s) {
    int n = 0;
    for (Entry e : entries) {
      if (e.status == s) n++;
    }
    return n;
  }

  @Override
  public String toString() {
    return "BatchImportReport{activated="
        + activatedCount()
        + " replaced="
        + replacedCount()
        + " skipped="
        + skippedCount()
        + " failed="
        + failedCount()
        + " entries="
        + entries.size()
        + "}";
  }
}
