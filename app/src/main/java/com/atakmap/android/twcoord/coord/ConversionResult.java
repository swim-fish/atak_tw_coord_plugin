package com.atakmap.android.twcoord.coord;

import java.util.Objects;

/** Algebraic data type capturing the spec's three readout states for a converted fix. */
public abstract class ConversionResult {

  private ConversionResult() {}

  public static <T> Ok<T> ok(T value, CoordinateUnit unit) {
    return new Ok<>(value, unit);
  }

  public static OutOfRange outOfRange(Wgs84 fix, CoordinateUnit unit) {
    return new OutOfRange(fix, unit);
  }

  public static NoFix noFix() {
    return NoFix.INSTANCE;
  }

  public boolean isOk() {
    return this instanceof Ok;
  }

  public boolean isOutOfRange() {
    return this instanceof OutOfRange;
  }

  public boolean isNoFix() {
    return this instanceof NoFix;
  }

  public static final class Ok<T> extends ConversionResult {
    private final T value;
    private final CoordinateUnit unit;

    private Ok(T value, CoordinateUnit unit) {
      this.value = Objects.requireNonNull(value, "value");
      this.unit = Objects.requireNonNull(unit, "unit");
    }

    public T value() {
      return value;
    }

    public CoordinateUnit unit() {
      return unit;
    }
  }

  public static final class OutOfRange extends ConversionResult {
    private final Wgs84 fix;
    private final CoordinateUnit unit;

    private OutOfRange(Wgs84 fix, CoordinateUnit unit) {
      this.fix = Objects.requireNonNull(fix, "fix");
      this.unit = Objects.requireNonNull(unit, "unit");
    }

    public Wgs84 fix() {
      return fix;
    }

    public CoordinateUnit unit() {
      return unit;
    }
  }

  public static final class NoFix extends ConversionResult {
    private static final NoFix INSTANCE = new NoFix();

    private NoFix() {}
  }
}
