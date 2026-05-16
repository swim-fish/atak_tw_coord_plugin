package com.atakmap.android.twpower.coord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.data.Offset;
import org.junit.Test;

public class CoordinateConverterTest {

  private final CoordinateConverter conv = new CoordinateConverter();

  @Test
  public void in_range_twd97_returns_ok() {
    Wgs84 fix = wgs(GoldenVectors.TAIPEI_101);
    ConversionResult r = conv.convert(fix, CoordinateUnit.TWD97);
    assertThat(r.isOk()).isTrue();
    @SuppressWarnings("unchecked")
    Twd97Tm2 v = ((ConversionResult.Ok<Twd97Tm2>) r).value();
    assertThat(v.eastingMetres())
        .isCloseTo(GoldenVectors.TAIPEI_101.twd97E, Offset.offset(GoldenVectors.TOL_TWD97_M));
  }

  @Test
  public void in_range_twd67_returns_ok() {
    ConversionResult r = conv.convert(wgs(GoldenVectors.KAOHSIUNG_85), CoordinateUnit.TWD67);
    assertThat(r.isOk()).isTrue();
    @SuppressWarnings("unchecked")
    Twd67Tm2 v = ((ConversionResult.Ok<Twd67Tm2>) r).value();
    assertThat(v.eastingMetres())
        .isCloseTo(GoldenVectors.KAOHSIUNG_85.twd67E, Offset.offset(GoldenVectors.TOL_TWD67_M));
  }

  @Test
  public void in_range_taipower_returns_ok() {
    ConversionResult r = conv.convert(wgs(GoldenVectors.TAICHUNG_CH), CoordinateUnit.TAIPOWER);
    assertThat(r.isOk()).isTrue();
    @SuppressWarnings("unchecked")
    TaipowerCode v = ((ConversionResult.Ok<TaipowerCode>) r).value();
    assertThat(v.region()).isEqualTo('G');
    // 11-char precision is now the default; confirm the 1 m digits are populated.
    assertThat(v.hasOneMetrePrecision()).isTrue();
  }

  /** Naha (Okinawa) is north of Taiwan's TM2 box — all three units MUST report out-of-range. */
  @Test
  public void naha_okinawa_returns_out_of_range_for_all_units() {
    Wgs84 fix = wgs(GoldenVectors.NAHA_OKINAWA);
    for (CoordinateUnit u : CoordinateUnit.values()) {
      assertThat(conv.convert(fix, u).isOutOfRange()).as("Naha, unit=%s", u).isTrue();
    }
  }

  /** Hong Kong is west of Penghu — out of Taiwan box for all three units. */
  @Test
  public void hong_kong_returns_out_of_range_for_all_units() {
    Wgs84 fix = wgs(GoldenVectors.HONG_KONG_IFC);
    for (CoordinateUnit u : CoordinateUnit.values()) {
      assertThat(conv.convert(fix, u).isOutOfRange()).as("Hong Kong, unit=%s", u).isTrue();
    }
  }

  /** Tokyo — north + east of Taiwan, deep out-of-range. */
  @Test
  public void tokyo_returns_out_of_range_for_all_units() {
    Wgs84 fix = wgs(GoldenVectors.TOKYO_TOWER);
    for (CoordinateUnit u : CoordinateUnit.values()) {
      assertThat(conv.convert(fix, u).isOutOfRange()).as("Tokyo, unit=%s", u).isTrue();
    }
  }

  /**
   * Penghu (lon 119.6) is supported via TM2 zone 119 for TWD97 / TWD67. Taipower grid still rejects
   * (Y/Z letters not implemented; main-island grid only — ADR-0001).
   */
  @Test
  public void magong_penghu_returns_ok_for_twd97_zone_119() {
    Wgs84 fix = wgs(GoldenVectors.MAGONG_PENGHU);
    ConversionResult r = conv.convert(fix, CoordinateUnit.TWD97);
    assertThat(r.isOk()).as("TWD97 in Penghu").isTrue();
    @SuppressWarnings("unchecked")
    Twd97Tm2 v = ((ConversionResult.Ok<Twd97Tm2>) r).value();
    assertThat(v.zone()).isEqualTo(119);
    // Sanity: TWD97 z119 false-easting is 250 000 m; Magong (~119.57°E) is close to centre.
    assertThat(v.eastingMetres()).isBetween(290_000d, 360_000d);
    assertThat(v.northingMetres()).isBetween(2_600_000d, 2_650_000d);
  }

  @Test
  public void magong_penghu_returns_ok_for_twd67() {
    Wgs84 fix = wgs(GoldenVectors.MAGONG_PENGHU);
    ConversionResult r = conv.convert(fix, CoordinateUnit.TWD67);
    assertThat(r.isOk()).as("TWD67 in Penghu").isTrue();
    @SuppressWarnings("unchecked")
    Twd67Tm2 v = ((ConversionResult.Ok<Twd67Tm2>) r).value();
    assertThat(v.zone()).isEqualTo(119);
  }

  /** Taipower grid remains main-island-only — ADR-0001 caveat. */
  @Test
  public void magong_penghu_returns_out_of_range_for_taipower() {
    Wgs84 fix = wgs(GoldenVectors.MAGONG_PENGHU);
    assertThat(conv.convert(fix, CoordinateUnit.TAIPOWER).isOutOfRange()).isTrue();
  }

  @Test
  public void null_fix_throws_npe() {
    assertThatThrownBy(() -> conv.convert(null, CoordinateUnit.TWD97))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void null_unit_throws_npe() {
    assertThatThrownBy(() -> conv.convert(wgs(GoldenVectors.TAIPEI_101), null))
        .isInstanceOf(NullPointerException.class);
  }

  private static Wgs84 wgs(GoldenVectors.Point p) {
    return new Wgs84(p.latDeg, p.lonDeg, 1L, Wgs84.Source.MAP_CENTRE);
  }
}
