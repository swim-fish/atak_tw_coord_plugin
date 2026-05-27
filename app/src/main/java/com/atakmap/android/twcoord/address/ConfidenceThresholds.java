package com.atakmap.android.twcoord.address;

/**
 * Feature 005 polish (Phase 7) — operator-selectable preset for the confidence indicator that
 * decorates address text on the widget. Each preset names two distance buckets in meters:
 *
 * <ul>
 *   <li>{@code mediumMeters}: above this distance the row is prefixed with {@code "~ "} (medium
 *       confidence; "same block / nearby building")
 *   <li>{@code lowMeters}: above this distance the row is prefixed with {@code "~~ "} (low
 *       confidence; "the nearest record we have, nothing closer")
 * </ul>
 *
 * <p>Below {@code mediumMeters} no prefix is added (high confidence; operator is essentially on the
 * record). {@link #OFF} disables the indicator entirely and always returns the original name.
 *
 * <p>An unknown distance ({@code distanceMeters < 0}) — used by the legacy single-active code path
 * that does not compute haversine — returns the original name regardless of preset.
 */
public enum ConfidenceThresholds {
  /** Indicator disabled; the row always renders without a tilde prefix. */
  OFF(0.0, 0.0),
  /** Default — tight buckets tuned for TGOS urban density (city-area records ~20 m apart). */
  TIGHT(20.0, 100.0),
  /** Looser thresholds: comfortable for mixed urban/suburban coverage. */
  STANDARD(50.0, 200.0),
  /** Most permissive: suppresses the marker until the nearest record is quite far away. */
  LOOSE(100.0, 500.0);

  private final double mediumMeters;
  private final double lowMeters;

  ConfidenceThresholds(double mediumMeters, double lowMeters) {
    this.mediumMeters = mediumMeters;
    this.lowMeters = lowMeters;
  }

  public double mediumMeters() {
    return mediumMeters;
  }

  public double lowMeters() {
    return lowMeters;
  }

  /**
   * Returns {@code displayName} optionally prefixed with a tilde marker. {@link #OFF} always
   * returns the original name; negative {@code distanceMeters} (unknown) also returns unchanged.
   */
  public String decorate(String displayName, double distanceMeters) {
    if (displayName == null) return "";
    if (this == OFF) return displayName;
    if (distanceMeters < 0) return displayName;
    if (distanceMeters <= mediumMeters) return displayName;
    if (distanceMeters <= lowMeters) return "~ " + displayName;
    return "~~ " + displayName;
  }

  /**
   * Parses a SharedPreferences string back into a preset. Returns {@link #TIGHT} for {@code null}
   * or any unknown value — TIGHT preserves the 2026-05-27 device-verified behaviour for
   * out-of-the-box installs and corrupted prefs.
   */
  public static ConfidenceThresholds fromPrefValue(String value) {
    if (value == null) return TIGHT;
    try {
      return valueOf(value);
    } catch (IllegalArgumentException e) {
      return TIGHT;
    }
  }
}
