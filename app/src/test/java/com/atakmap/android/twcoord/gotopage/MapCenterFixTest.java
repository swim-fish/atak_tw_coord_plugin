package com.atakmap.android.twcoord.gotopage;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.Wgs84;
import org.junit.Test;

/**
 * JVM tests for the pure-Java parts of the Auto Fill pipeline. Verifies the per-tab "ok" flags
 * track the forward converter's behaviour exactly — so Auto Fill never offers a tab the readout
 * widget would consider "out of range".
 */
public class MapCenterFixTest {

  private final CoordinateConverter converter = new CoordinateConverter();

  private Wgs84 wgs84(double lat, double lon) {
    return new Wgs84(lat, lon, 1_000L, Wgs84.Source.MAP_CENTRE);
  }

  @Test
  public void taipeiCity_okForAllThreeTabs() {
    MapCenterFix fix = MapCenterFix.of(wgs84(25.037798, 121.564841), converter);
    assertThat(fix.taipowerOk()).isTrue();
    assertThat(fix.twd97Ok()).isTrue();
    assertThat(fix.twd67Ok()).isTrue();
  }

  @Test
  public void penghu_notOkForTaipower_okForTwd97AndTwd67() {
    // Penghu (zone 119 outer island) — Taipower is main-island only, but TWD97 / TWD67 cover it.
    MapCenterFix fix = MapCenterFix.of(wgs84(23.566, 119.578), converter);
    assertThat(fix.taipowerOk()).isFalse();
    assertThat(fix.twd97Ok()).isTrue();
    assertThat(fix.twd67Ok()).isTrue();
  }

  @Test
  public void tokyo_notOkForAnyTab() {
    MapCenterFix fix = MapCenterFix.of(wgs84(35.6762, 139.6503), converter);
    assertThat(fix.taipowerOk()).isFalse();
    assertThat(fix.twd97Ok()).isFalse();
    assertThat(fix.twd67Ok()).isFalse();
  }

  @Test
  public void nullWgs84_safelyReturnsAllFalse() {
    MapCenterFix fix = MapCenterFix.of(null, converter);
    assertThat(fix.taipowerOk()).isFalse();
    assertThat(fix.twd97Ok()).isFalse();
    assertThat(fix.twd67Ok()).isFalse();
    assertThat(fix.wgs84()).isNull();
  }
}
