package com.atakmap.android.twcoord.address;

import java.util.Objects;

/**
 * Derived UI state for one address row in {@code TwCoordWidget}. The widget consumes a state per
 * row (ME / TGT / MAP) and renders accordingly; {@link AddressSubsystem} owns the state machine.
 *
 * <p>Variants:
 *
 * <ul>
 *   <li>{@link Hidden} — the row's per-row preference is off OR there is no active dataset. The
 *       widget hides the row.
 *   <li>{@link Loading} — the row is enabled and a lookup is in flight (transitional, normally only
 *       on cold start or after dataset activation).
 *   <li>{@link Text} — the row carries an address string.
 *   <li>{@link EmptyState} — the row is enabled but the lookup returned no record within radius.
 * </ul>
 *
 * <p>Follows the project's sealed-by-convention pattern (abstract + private constructor + final
 * nested classes) used by {@link com.atakmap.android.twcoord.coord.input.ParseResult}.
 */
public abstract class AddressRowState {

  private AddressRowState() {}

  public static Hidden hidden() {
    return Hidden.INSTANCE;
  }

  public static Loading loading() {
    return Loading.INSTANCE;
  }

  public static Text text(String value) {
    return new Text(value);
  }

  public static EmptyState emptyState() {
    return EmptyState.INSTANCE;
  }

  public boolean isHidden() {
    return this instanceof Hidden;
  }

  public boolean isLoading() {
    return this instanceof Loading;
  }

  public boolean isText() {
    return this instanceof Text;
  }

  public boolean isEmptyState() {
    return this instanceof EmptyState;
  }

  public static final class Hidden extends AddressRowState {
    private static final Hidden INSTANCE = new Hidden();

    private Hidden() {}
  }

  public static final class Loading extends AddressRowState {
    private static final Loading INSTANCE = new Loading();

    private Loading() {}
  }

  public static final class Text extends AddressRowState {
    private final String value;

    private Text(String value) {
      this.value = Objects.requireNonNull(value, "value");
    }

    public String value() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Text)) return false;
      return value.equals(((Text) o).value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  public static final class EmptyState extends AddressRowState {
    private static final EmptyState INSTANCE = new EmptyState();

    private EmptyState() {}
  }
}
