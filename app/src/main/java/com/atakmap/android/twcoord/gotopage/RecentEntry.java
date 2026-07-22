package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.input.CoordinateInput;
import java.util.Objects;

/**
 * One historical successful submission, persisted in {@link RecentEntryStore}. Per
 * contracts/recent-store.md: capacity 10, FIFO eviction by {@link #timestampEpochMs}, dedup on
 * (unit, rawValue).
 */
public final class RecentEntry {

  private final CoordinateUnit unit;
  private final String rawValue;
  private final int easting; // 0 sentinel for Taipower
  private final int northing; // 0 sentinel for Taipower
  private final int zone; // 0 sentinel for Taipower, else 121 or 119
  private final long timestampEpochMs;

  public RecentEntry(
      CoordinateUnit unit,
      String rawValue,
      int easting,
      int northing,
      int zone,
      long timestampEpochMs) {
    this.unit = Objects.requireNonNull(unit, "unit");
    this.rawValue = Objects.requireNonNull(rawValue, "rawValue");
    this.easting = easting;
    this.northing = northing;
    this.zone = zone;
    this.timestampEpochMs = timestampEpochMs;
  }

  public static RecentEntry fromCoordinateInput(CoordinateInput input, long timestampEpochMs) {
    if (input instanceof CoordinateInput.Taipower) {
      return new RecentEntry(
          CoordinateUnit.TAIPOWER,
          ((CoordinateInput.Taipower) input).rawValue(),
          0,
          0,
          0,
          timestampEpochMs);
    }
    if (input instanceof CoordinateInput.Twd97) {
      CoordinateInput.Twd97 t = (CoordinateInput.Twd97) input;
      return new RecentEntry(
          CoordinateUnit.TWD97,
          t.displayString(),
          t.easting(),
          t.northing(),
          t.zone(),
          timestampEpochMs);
    }
    if (input instanceof CoordinateInput.Twd67) {
      CoordinateInput.Twd67 t = (CoordinateInput.Twd67) input;
      return new RecentEntry(
          CoordinateUnit.TWD67,
          t.displayString(),
          t.easting(),
          t.northing(),
          t.zone(),
          timestampEpochMs);
    }
    throw new IllegalStateException("unknown input subtype: " + input.getClass());
  }

  public CoordinateUnit unit() {
    return unit;
  }

  public String rawValue() {
    return rawValue;
  }

  public int easting() {
    return easting;
  }

  public int northing() {
    return northing;
  }

  public int zone() {
    return zone;
  }

  public long timestampEpochMs() {
    return timestampEpochMs;
  }

  /** Returns the {@link CoordinateInput} that would re-create this entry on the input page. */
  public CoordinateInput toInput() {
    switch (unit) {
      case TAIPOWER:
        return new CoordinateInput.Taipower(rawValue);
      case TWD97:
        return new CoordinateInput.Twd97(easting, northing, zone);
      case TWD67:
      default:
        return new CoordinateInput.Twd67(easting, northing, zone);
    }
  }
}
