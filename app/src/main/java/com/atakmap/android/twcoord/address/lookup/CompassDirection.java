package com.atakmap.android.twcoord.address.lookup;

/**
 * Pure-logic compass helper for the forward-search result list: the initial-bearing from the
 * distance anchor to a candidate, quantised to one of the 16 compass points (N, NNE, NE, …) plus
 * the rotation an upward "↑" glyph needs to point that way. JVM-testable; no Android types.
 */
public final class CompassDirection {

  /** 16-point abbreviations, index 0 = N, clockwise in 22.5° steps. */
  private static final String[] POINTS_16 = {
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
  };

  /** 8-point Unicode arrow glyphs, index 0 = N (↑), clockwise in 45° steps. */
  private static final String[] ARROW_GLYPHS_8 = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

  private CompassDirection() {}

  /**
   * Initial bearing from (fromLat,fromLon) to (toLat,toLon), in degrees clockwise from true north,
   * normalised to [0,360). Returns 0 for a zero-distance / identical point.
   */
  public static double bearingDegrees(double fromLat, double fromLon, double toLat, double toLon) {
    double p1 = Math.toRadians(fromLat);
    double p2 = Math.toRadians(toLat);
    double dLon = Math.toRadians(toLon - fromLon);
    double y = Math.sin(dLon) * Math.cos(p2);
    double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dLon);
    if (y == 0.0 && x == 0.0) return 0.0;
    double deg = Math.toDegrees(Math.atan2(y, x));
    return (deg % 360.0 + 360.0) % 360.0;
  }

  /** 16-point index 0..15 (0 = N) for a bearing in degrees. */
  public static int point16Index(double bearingDegrees) {
    double norm = (bearingDegrees % 360.0 + 360.0) % 360.0;
    return (int) Math.round(norm / 22.5) % 16;
  }

  /** 16-point abbreviation (e.g. {@code "NNE"}) for a bearing in degrees. */
  public static String abbrev16(double bearingDegrees) {
    return POINTS_16[point16Index(bearingDegrees)];
  }

  /**
   * Rotation in degrees for an upward-pointing glyph ("↑", which points N at 0°) so it points along
   * the 16-point-quantised bearing. Equals {@code index * 22.5}.
   */
  public static float arrowRotation16(double bearingDegrees) {
    return point16Index(bearingDegrees) * 22.5f;
  }

  /**
   * A single Unicode arrow glyph pointing along {@code bearingDegrees}, quantised to the nearest of
   * 8 compass directions (N ↑, NE ↗, E →, SE ↘, S ↓, SW ↙, W ←, NW ↖). Plain-text readouts (the
   * on-map address row) can't rotate a glyph the way the forward-search list does, so 8 fixed
   * arrows are the closest a text line can get to the 16-point bearing.
   */
  public static String arrowGlyph(double bearingDegrees) {
    double norm = (bearingDegrees % 360.0 + 360.0) % 360.0;
    int idx = (int) Math.round(norm / 45.0) % 8;
    return ARROW_GLYPHS_8[idx];
  }
}
