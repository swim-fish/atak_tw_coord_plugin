package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import java.util.Locale;
import java.util.Objects;

/**
 * The structured input being edited on the input page. Three variants, one per unit.
 *
 * <p>Discriminated union (sealed-style without the Java 17 keyword): the three static-nested final
 * classes are the only subtypes; their constructors are package-public so the parsers can build
 * them after validation. Tests verify only valid instances reach the parser facade.
 */
public abstract class CoordinateInput {

  private CoordinateInput() {}

  public abstract CoordinateUnit unit();

  /** Display string for the Recent list and confirmation toast. */
  public abstract String displayString();

  /** Taipower 9 / 11-char code, post-normalisation. */
  public static final class Taipower extends CoordinateInput {
    private final String rawValue;

    public Taipower(String rawValue) {
      this.rawValue = Objects.requireNonNull(rawValue, "rawValue");
    }

    public String rawValue() {
      return rawValue;
    }

    @Override
    public CoordinateUnit unit() {
      return CoordinateUnit.TAIPOWER;
    }

    @Override
    public String displayString() {
      return rawValue;
    }
  }

  /** TWD97 easting/northing in metres, zone 121 or 119. */
  public static final class Twd97 extends CoordinateInput {
    private final int easting;
    private final int northing;
    private final int zone;

    public Twd97(int easting, int northing, int zone) {
      if (zone != 121 && zone != 119) {
        throw new IllegalArgumentException("zone must be 121 or 119: " + zone);
      }
      this.easting = easting;
      this.northing = northing;
      this.zone = zone;
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

    @Override
    public CoordinateUnit unit() {
      return CoordinateUnit.TWD97;
    }

    @Override
    public String displayString() {
      return String.format(Locale.US, "%d / %d", easting, northing);
    }
  }

  /** TWD67 easting/northing in metres, zone 121 or 119. */
  public static final class Twd67 extends CoordinateInput {
    private final int easting;
    private final int northing;
    private final int zone;

    public Twd67(int easting, int northing, int zone) {
      if (zone != 121 && zone != 119) {
        throw new IllegalArgumentException("zone must be 121 or 119: " + zone);
      }
      this.easting = easting;
      this.northing = northing;
      this.zone = zone;
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

    @Override
    public CoordinateUnit unit() {
      return CoordinateUnit.TWD67;
    }

    @Override
    public String displayString() {
      return String.format(Locale.US, "%d / %d", easting, northing);
    }
  }
}
