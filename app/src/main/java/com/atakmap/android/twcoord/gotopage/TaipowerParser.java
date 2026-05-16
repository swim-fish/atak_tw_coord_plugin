package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.twcoord.coord.Twd67Tm2;
import java.util.Locale;

/**
 * Decodes a Taipower grid string into the underlying {@link Twd67Tm2} coordinate. Inverse of {@link
 * com.atakmap.android.twcoord.coord.TaipowerGrid#fromTwd67(Twd67Tm2,
 * com.atakmap.android.twcoord.coord.TaipowerGrid.Precision)}.
 *
 * <p>Package-private — callers go through {@link CoordinateParser#parseTaipower(String)}.
 *
 * <p>The arithmetic constants (anchors, step sizes, letter table) MUST match {@link
 * com.atakmap.android.twcoord.coord.TaipowerGrid} exactly; this is the inverse of those constants
 * applied on the reverse direction. Duplicating them here instead of exposing them on TaipowerGrid
 * keeps feature 001's class unmodified.
 */
final class TaipowerParser {

  private TaipowerParser() {}

  // Constants shadowed from TaipowerGrid (intentional duplication — see class Javadoc).
  private static final double ANCHOR_E_WEST = 170_000;
  private static final double ANCHOR_N_SOUTH = 2_400_000;
  private static final double REGION_WIDTH = 80_000;
  private static final double REGION_HEIGHT = 50_000;
  private static final double SUB_STEP_E = 800;
  private static final double SUB_STEP_N = 500;
  private static final int ROWS = 8;
  private static final int COLS = 3;

  private static final char[][] REGION_LETTERS = {
    {'A', 'B', 'C'},
    {'D', 'E', 'F'},
    {'G', 'H', 'I'},
    {'J', 'K', 'L'},
    {'M', 'N', 'O'},
    {'P', 'Q', 'R'},
    {'S', 'T', 'U'},
    {'V', 'W', 'X'},
  };

  /** Output of {@link #parse(String)}: either Ok(Twd67) or a {@link ParseResult.Reason}. */
  static final class Outcome {
    final Twd67Tm2 ok;
    final ParseResult.Reason invalid;

    private Outcome(Twd67Tm2 ok, ParseResult.Reason invalid) {
      this.ok = ok;
      this.invalid = invalid;
    }

    static Outcome ok(Twd67Tm2 t) {
      return new Outcome(t, null);
    }

    static Outcome invalid(ParseResult.Reason reason) {
      return new Outcome(null, reason);
    }
  }

  /** Holds the normalised string alongside the Outcome so callers can build a CoordinateInput. */
  static final class ParseAttempt {
    final String normalised;
    final Outcome outcome;

    ParseAttempt(String normalised, Outcome outcome) {
      this.normalised = normalised;
      this.outcome = outcome;
    }
  }

  /**
   * Normalises and decodes a Taipower string. Normalisation rules: trim outer whitespace, strip
   * surrounding parentheses, strip embedded CR/LF, uppercase, remove ALL internal whitespace
   * (single or multiple spaces all collapse, so {@code H7509 DB4016} and {@code H7509DB4016} both
   * become {@code H7509DB4016}).
   *
   * <p>After normalisation the string must be exactly 9 or 11 chars; positions and content per
   * contracts/coordinate-parser.md.
   */
  static ParseAttempt parse(String rawValue) {
    String n = normalise(rawValue);
    if (n.isEmpty()) {
      return new ParseAttempt(n, Outcome.invalid(ParseResult.Reason.EMPTY));
    }

    boolean elevenChar;
    if (n.length() == 9) {
      elevenChar = false;
    } else if (n.length() == 11) {
      elevenChar = true;
    } else {
      return new ParseAttempt(n, Outcome.invalid(ParseResult.Reason.BAD_LENGTH));
    }

    char region = n.charAt(0);
    if (region == 'Y' || region == 'Z') {
      return new ParseAttempt(n, Outcome.invalid(ParseResult.Reason.RESERVED_LETTER_YZ));
    }
    if (region < 'A' || region > 'X') {
      return new ParseAttempt(n, Outcome.invalid(ParseResult.Reason.BAD_LETTER));
    }

    // Positions 1..4 must be digits (sub-region 4-digit).
    for (int i = 1; i <= 4; i++) {
      if (!isDigit(n.charAt(i))) {
        return new ParseAttempt(n, Outcome.invalid(ParseResult.Reason.NON_DIGIT));
      }
    }
    int subRegion =
        (n.charAt(1) - '0') * 1000
            + (n.charAt(2) - '0') * 100
            + (n.charAt(3) - '0') * 10
            + (n.charAt(4) - '0');

    // Positions 5, 6 must be hundred-metre letters A..J.
    char hmE = n.charAt(5);
    char hmN = n.charAt(6);
    if (hmE < 'A' || hmE > 'J' || hmN < 'A' || hmN > 'J') {
      return new ParseAttempt(n, Outcome.invalid(ParseResult.Reason.BAD_LETTER));
    }
    int letter5Idx = hmE - 'A';
    int letter6Idx = hmN - 'A';

    // Positions 7..end must all be digits (ten-metre + optional one-metre).
    for (int i = 7; i < n.length(); i++) {
      if (!isDigit(n.charAt(i))) {
        return new ParseAttempt(n, Outcome.invalid(ParseResult.Reason.NON_DIGIT));
      }
    }
    int tenE = n.charAt(7) - '0';
    int tenN = n.charAt(8) - '0';
    int oneE = elevenChar ? (n.charAt(9) - '0') : 5; // 9-char: assume middle of 10 m cell.
    int oneN = elevenChar ? (n.charAt(10) - '0') : 5;

    // Decode region letter → (rowIdx, colIdx).
    int rowIdx = -1, colIdx = -1;
    outer:
    for (int r = 0; r < ROWS; r++) {
      for (int c = 0; c < COLS; c++) {
        if (REGION_LETTERS[r][c] == region) {
          rowIdx = r;
          colIdx = c;
          break outer;
        }
      }
    }
    if (rowIdx < 0) {
      // Region letter passed A..X check but not in table — defensive guard, should be unreachable.
      return new ParseAttempt(n, Outcome.invalid(ParseResult.Reason.BAD_LETTER));
    }
    int geoRow = ROWS - 1 - rowIdx;

    int xHundreds = subRegion / 100;
    int yHundreds = subRegion % 100;

    double xBase = ANCHOR_E_WEST + colIdx * REGION_WIDTH;
    double yBase = ANCHOR_N_SOUTH + geoRow * REGION_HEIGHT;
    double dxInRegion = xHundreds * SUB_STEP_E + letter5Idx * 100.0 + tenE * 10.0 + oneE;
    double dyInRegion = yHundreds * SUB_STEP_N + letter6Idx * 100.0 + tenN * 10.0 + oneN;

    double e = xBase + dxInRegion;
    double n67 = yBase + dyInRegion;
    return new ParseAttempt(n, Outcome.ok(new Twd67Tm2(e, n67, 121)));
  }

  private static String normalise(String raw) {
    if (raw == null) return "";
    // Strip outer whitespace, embedded newlines, surrounding parens; uppercase; remove all
    // internal whitespace.
    String s = raw.trim();
    // Strip a single leading '(' and trailing ')' pair.
    if (s.length() >= 2 && s.charAt(0) == '(' && s.charAt(s.length() - 1) == ')') {
      s = s.substring(1, s.length() - 1).trim();
    }
    // Remove CR/LF anywhere (operators paste from web tools).
    s = s.replace("\r", "").replace("\n", "");
    // Remove ALL whitespace — both `H7509 DB4016` and `H7509DB4016` become the same canonical form.
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (!Character.isWhitespace(c)) out.append(c);
    }
    return out.toString().toUpperCase(Locale.ROOT);
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }
}
