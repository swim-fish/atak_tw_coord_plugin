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
  }

  @Test
  public void outside_taiwan_north_returns_out_of_range_for_all_units() {
    Wgs84 fix = new Wgs84(40.0, 121.0, 1L, Wgs84.Source.MAP_CENTRE);
    for (CoordinateUnit u : CoordinateUnit.values()) {
      assertThat(conv.convert(fix, u).isOutOfRange()).as("unit=%s", u).isTrue();
    }
  }

  @Test
  public void outside_taiwan_west_returns_out_of_range_for_all_units() {
    Wgs84 fix = new Wgs84(22.0, 100.0, 1L, Wgs84.Source.MAP_CENTRE);
    for (CoordinateUnit u : CoordinateUnit.values()) {
      assertThat(conv.convert(fix, u).isOutOfRange()).as("unit=%s", u).isTrue();
    }
  }

  /**
   * Penghu (lon 119.6) is outside TM2-z121 bounds in v1; it would also belong to TWD97 zone 119
   * which we have not enabled yet. All three units MUST report out-of-range here.
   */
  @Test
  public void penghu_returns_out_of_range_for_all_units() {
    Wgs84 fix = new Wgs84(23.5, 119.6, 1L, Wgs84.Source.MAP_CENTRE);
    for (CoordinateUnit u : CoordinateUnit.values()) {
      assertThat(conv.convert(fix, u).isOutOfRange()).as("unit=%s", u).isTrue();
    }
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
