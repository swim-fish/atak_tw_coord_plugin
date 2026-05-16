package com.atakmap.android.twpower.coord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class TaipowerGridTest {

  @Test
  public void taipower_code_from_twd67_matches_golden_9char_codes() {
    for (GoldenVectors.Point p : GoldenVectors.ALL) {
      TaipowerCode code =
          TaipowerGrid.fromTwd67(
              new Twd67Tm2(p.twd67E, p.twd67N), TaipowerGrid.Precision.NINE_CHAR);
      String formatted = formatNineChar(code);
      assertThat(formatted).as("Taipower 9-char for %s", p.name).isEqualTo(p.taipower9Char);
    }
  }

  @Test
  public void out_of_eastern_easting_band_throws() {
    Twd67Tm2 outsideEast = new Twd67Tm2(420_000, 2_500_000); // > 410_000 m easting
    assertThatThrownBy(() -> TaipowerGrid.fromTwd67(outsideEast, TaipowerGrid.Precision.NINE_CHAR))
        .isInstanceOf(TaipowerGrid.OutOfCoverageException.class);
  }

  @Test
  public void out_of_southern_northing_band_throws() {
    Twd67Tm2 outsideSouth = new Twd67Tm2(200_000, 2_300_000); // < 2_400_000 m northing
    assertThatThrownBy(() -> TaipowerGrid.fromTwd67(outsideSouth, TaipowerGrid.Precision.NINE_CHAR))
        .isInstanceOf(TaipowerGrid.OutOfCoverageException.class);
  }

  /** ADR-0001 caveat: Y/Z letters (Penghu / Lanyu) are out of coverage in v1. */
  @Test
  public void letter_table_excludes_y_z_for_v1() {
    for (GoldenVectors.Point p : GoldenVectors.ALL) {
      TaipowerCode code =
          TaipowerGrid.fromTwd67(
              new Twd67Tm2(p.twd67E, p.twd67N), TaipowerGrid.Precision.NINE_CHAR);
      char r = code.region();
      assertThat(r).isNotIn('Y', 'Z');
      assertThat(r).isBetween('A', 'X');
    }
  }

  /** "B7039 BD32" — 4-digit sub-region, single space, 2-letter 100 m, 2-digit 10 m. */
  static String formatNineChar(TaipowerCode c) {
    return String.format(
        "%c%04d %c%c%d%d",
        c.region(),
        c.subRegion(),
        c.hundredMeterE(),
        c.hundredMeterN(),
        c.tenMeterE(),
        c.tenMeterN());
  }
}
