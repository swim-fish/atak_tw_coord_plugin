package com.atakmap.android.twpower.coord;

import java.util.Objects;

/** One row of the on-map widget: a localised label, a value, and an optional WGS84 fallback. */
public final class DisplayLine {

  public enum State {
    OK,
    OUT_OF_RANGE,
    NO_FIX,
    NO_PERMISSION
  }

  private final String labelPrefix;
  private final String unitTag;
  private final String value;
  private final String fallback;
  private final State state;

  public DisplayLine(
      String labelPrefix, String unitTag, String value, String fallback, State state) {
    this.labelPrefix = Objects.requireNonNull(labelPrefix, "labelPrefix");
    this.unitTag = unitTag == null ? "" : unitTag;
    this.value = value == null ? "" : value;
    this.fallback = fallback == null ? "" : fallback;
    this.state = Objects.requireNonNull(state, "state");
  }

  public String labelPrefix() {
    return labelPrefix;
  }

  public String unitTag() {
    return unitTag;
  }

  public String value() {
    return value;
  }

  public String fallback() {
    return fallback;
  }

  public State state() {
    return state;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DisplayLine other)) return false;
    return labelPrefix.equals(other.labelPrefix)
        && unitTag.equals(other.unitTag)
        && value.equals(other.value)
        && fallback.equals(other.fallback)
        && state == other.state;
  }

  @Override
  public int hashCode() {
    return Objects.hash(labelPrefix, unitTag, value, fallback, state);
  }
}
