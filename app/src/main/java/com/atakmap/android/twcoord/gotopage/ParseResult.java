package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;

/**
 * Output of the inverse-converter pipeline. Mirrors feature 001's {@link
 * com.atakmap.android.twcoord.coord.ConversionResult} but in the opposite direction: an operator's
 * Taiwan-coord input either resolves to a valid {@link Wgs84}, fails validation, or succeeds
 * numerically but lands outside Taiwan's coverage box.
 *
 * <p>Discriminated union with three variants (Ok / Invalid / OutOfRange). The factory methods are
 * the only construction path; the static-nested classes are final and private-constructor so the
 * "sealed" property holds without the Java 17 {@code sealed} keyword.
 */
public abstract class ParseResult {

  private ParseResult() {}

  public static Ok ok(Wgs84 wgs84, CoordinateInput input) {
    return new Ok(wgs84, input);
  }

  public static Invalid invalid(CoordinateUnit unit, Reason reason) {
    return new Invalid(unit, reason);
  }

  public static OutOfRange outOfRange(CoordinateUnit unit, Wgs84 attempted) {
    return new OutOfRange(unit, attempted);
  }

  public boolean isOk() {
    return this instanceof Ok;
  }

  public boolean isInvalid() {
    return this instanceof Invalid;
  }

  public boolean isOutOfRange() {
    return this instanceof OutOfRange;
  }

  /** Reasons surfaced by {@link Invalid}. Each value maps to a localised string key. */
  public enum Reason {
    EMPTY,
    BAD_LENGTH,
    BAD_LETTER,
    RESERVED_LETTER_YZ,
    BAD_ZONE,
    NON_DIGIT
  }

  public static final class Ok extends ParseResult {
    private final Wgs84 wgs84;
    private final CoordinateInput input;

    private Ok(Wgs84 wgs84, CoordinateInput input) {
      this.wgs84 = Objects.requireNonNull(wgs84, "wgs84");
      this.input = Objects.requireNonNull(input, "input");
    }

    public Wgs84 wgs84() {
      return wgs84;
    }

    public CoordinateInput input() {
      return input;
    }
  }

  public static final class Invalid extends ParseResult {
    private final CoordinateUnit unit;
    private final Reason reason;

    private Invalid(CoordinateUnit unit, Reason reason) {
      this.unit = Objects.requireNonNull(unit, "unit");
      this.reason = Objects.requireNonNull(reason, "reason");
    }

    public CoordinateUnit unit() {
      return unit;
    }

    public Reason reason() {
      return reason;
    }
  }

  public static final class OutOfRange extends ParseResult {
    private final CoordinateUnit unit;
    private final Wgs84 attemptedWgs84;

    private OutOfRange(CoordinateUnit unit, Wgs84 attemptedWgs84) {
      this.unit = Objects.requireNonNull(unit, "unit");
      this.attemptedWgs84 = Objects.requireNonNull(attemptedWgs84, "attemptedWgs84");
    }

    public CoordinateUnit unit() {
      return unit;
    }

    public Wgs84 attemptedWgs84() {
      return attemptedWgs84;
    }
  }
}
