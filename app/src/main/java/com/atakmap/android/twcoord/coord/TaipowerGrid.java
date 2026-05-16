package com.atakmap.android.twcoord.coord;

/**
 * Taipower grid arithmetic over TWD67 TM2 zone 121. Anchors and step sizes copied verbatim from
 * pwa_map src/coord/taipower.ts:82-150 — see ADR-0001 for provenance.
 *
 * <p>Coverage in v1: the 8 (north→south) × 3 (west→east) main-island letter grid, A..X. Letters Y
 * and Z (outer islands, Penghu / Lanyu) are NOT supported in v1 and trigger {@link
 * OutOfCoverageException}.
 */
public final class TaipowerGrid {

  public enum Precision {
    NINE_CHAR,
    ELEVEN_CHAR
  }

  public static final class OutOfCoverageException extends RuntimeException {
    public OutOfCoverageException(String message) {
      super(message);
    }
  }

  private static final double ANCHOR_E_WEST = 170_000;
  private static final double ANCHOR_N_SOUTH = 2_400_000;
  private static final double REGION_WIDTH = 80_000;
  private static final double REGION_HEIGHT = 50_000;
  private static final int ROWS = 8;
  private static final int COLS = 3;
  private static final double SUB_STEP_E = 800;
  private static final double SUB_STEP_N = 500;

  /**
   * Letter table: rowIdx 0 = northernmost row, rowIdx 7 = southernmost. Order verified against the
   * four golden vectors (Taipei 101 → B, Kaohsiung 85 → P, Taichung CH → G, Hualien Stn → H).
   */
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

  private TaipowerGrid() {}

  public static TaipowerCode fromTwd67(Twd67Tm2 t67, Precision precision) {
    double x = t67.eastingMetres();
    double y = t67.northingMetres();

    double dxFromWest = x - ANCHOR_E_WEST;
    double dyFromSouth = y - ANCHOR_N_SOUTH;
    if (dxFromWest < 0 || dyFromSouth < 0) {
      throw new OutOfCoverageException(
          "below SW anchor: (" + x + ", " + y + ") — Taipower v1 covers main-island only");
    }

    int colIdx = (int) Math.floor(dxFromWest / REGION_WIDTH);
    int geoRow = (int) Math.floor(dyFromSouth / REGION_HEIGHT);
    if (colIdx < 0 || colIdx >= COLS || geoRow < 0 || geoRow >= ROWS) {
      throw new OutOfCoverageException(
          "outside main-island grid: col=" + colIdx + " row=" + geoRow);
    }
    int rowIdx = ROWS - 1 - geoRow;
    char region = REGION_LETTERS[rowIdx][colIdx];

    double xBase = ANCHOR_E_WEST + colIdx * REGION_WIDTH;
    double yBase = ANCHOR_N_SOUTH + geoRow * REGION_HEIGHT;
    double dxInRegion = x - xBase;
    double dyInRegion = y - yBase;

    int xHundreds = (int) Math.floor(dxInRegion / SUB_STEP_E); // 0..99
    int yHundreds = (int) Math.floor(dyInRegion / SUB_STEP_N); // 0..99
    int subRegion = xHundreds * 100 + yHundreds;

    double dxIn800 = dxInRegion - xHundreds * SUB_STEP_E;
    double dyIn500 = dyInRegion - yHundreds * SUB_STEP_N;

    int letter5Idx = (int) Math.floor(dxIn800 / 100.0); // 0..9 (in [0, 800) → /100 → 0..7)
    int letter6Idx = (int) Math.floor(dyIn500 / 100.0); // 0..9 (in [0, 500) → /100 → 0..4)
    // Clamp to A..J range; pwa_map reference ranges 0..9 — values above the actual range come
    // from points near the upper boundary that round into the next sub-region, which our floor()
    // captures above. The clamp is belt-and-braces.
    if (letter5Idx < 0) letter5Idx = 0;
    if (letter5Idx > 9) letter5Idx = 9;
    if (letter6Idx < 0) letter6Idx = 0;
    if (letter6Idx > 9) letter6Idx = 9;
    char hmE = (char) ('A' + letter5Idx);
    char hmN = (char) ('A' + letter6Idx);

    double dxIn100 = dxIn800 - letter5Idx * 100.0;
    double dyIn100 = dyIn500 - letter6Idx * 100.0;
    int tenE = (int) Math.floor(dxIn100 / 10.0);
    int tenN = (int) Math.floor(dyIn100 / 10.0);
    if (tenE < 0) tenE = 0;
    if (tenE > 9) tenE = 9;
    if (tenN < 0) tenN = 0;
    if (tenN > 9) tenN = 9;

    Integer oneE = null;
    Integer oneN = null;
    if (precision == Precision.ELEVEN_CHAR) {
      int oe = (int) Math.round(dxIn100 - tenE * 10.0);
      int on = (int) Math.round(dyIn100 - tenN * 10.0);
      if (oe < 0) oe = 0;
      if (oe > 9) oe = 9;
      if (on < 0) on = 0;
      if (on > 9) on = 9;
      oneE = oe;
      oneN = on;
    }

    return new TaipowerCode(region, subRegion, hmE, hmN, tenE, tenN, oneE, oneN);
  }
}
