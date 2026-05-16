package com.atakmap.android.twpower.coord;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.Test;

public class FormatterTest {

  private static final Formatter.Strings EN =
      new TestStrings(
          "MAP", "ME", "TPC", "TWD97", "TWD67", "out of range", "no fix", "no permission");
  private static final Formatter.Strings ZH_TW =
      new TestStrings("地圖", "我", "台電", "TWD97", "TWD67", "超出範圍", "無定位", "無權限");
  private static final Formatter.Strings JA =
      new TestStrings("地図", "自機", "台電", "TWD97", "TWD67", "範囲外", "測位不可", "権限なし");

  private final Formatter formatter = new Formatter(Locale.ROOT);
  private final CoordinateConverter conv = new CoordinateConverter();

  @Test
  public void ok_twd97_formats_with_unit_tag_and_metres() {
    Wgs84 fix =
        new Wgs84(
            GoldenVectors.TAIPEI_101.latDeg,
            GoldenVectors.TAIPEI_101.lonDeg,
            1L,
            Wgs84.Source.MAP_CENTRE);
    ConversionResult r = conv.convert(fix, CoordinateUnit.TWD97);
    DisplayLine line = formatter.format(Wgs84.Source.MAP_CENTRE, r, CoordinateUnit.TWD97, EN);

    assertThat(line.state()).isEqualTo(DisplayLine.State.OK);
    assertThat(line.labelPrefix()).isEqualTo("MAP");
    assertThat(line.unitTag()).isEqualTo("TWD97");
    // contracts/coordinate-formatter.md allows locale-aware grouping; under Locale.ROOT,
    // NumberFormat groups with commas by default, so we assert on the grouped form.
    assertThat(line.value()).contains("306,963m").contains("2,769,619m");
  }

  @Test
  public void ok_taipower_9char_matches_golden() {
    Wgs84 fix =
        new Wgs84(
            GoldenVectors.TAIPEI_101.latDeg,
            GoldenVectors.TAIPEI_101.lonDeg,
            1L,
            Wgs84.Source.MAP_CENTRE);
    ConversionResult r = conv.convert(fix, CoordinateUnit.TAIPOWER);
    DisplayLine line = formatter.format(Wgs84.Source.MAP_CENTRE, r, CoordinateUnit.TAIPOWER, ZH_TW);

    assertThat(line.state()).isEqualTo(DisplayLine.State.OK);
    assertThat(line.unitTag()).isEqualTo("台電");
    assertThat(line.value()).isEqualTo("B7039 BD32");
  }

  @Test
  public void out_of_range_carries_localised_state_and_wgs84_fallback() {
    Wgs84 fix = new Wgs84(40.0, 121.0, 1L, Wgs84.Source.MAP_CENTRE);
    ConversionResult r = conv.convert(fix, CoordinateUnit.TWD97);
    DisplayLine line = formatter.format(Wgs84.Source.MAP_CENTRE, r, CoordinateUnit.TWD97, JA);

    assertThat(line.state()).isEqualTo(DisplayLine.State.OUT_OF_RANGE);
    assertThat(line.value()).isEqualTo("範囲外");
    assertThat(line.fallback()).isEqualTo("40.000000, 121.000000");
  }

  @Test
  public void no_fix_emits_no_fix_state_with_empty_value() {
    DisplayLine line =
        formatter.format(
            Wgs84.Source.DEVICE_LOCATION, ConversionResult.noFix(), CoordinateUnit.TWD97, EN);
    assertThat(line.state()).isEqualTo(DisplayLine.State.NO_FIX);
    assertThat(line.value()).isEmpty();
  }

  /** SC-008 clipboard equality: the clipboard payload contains exactly what is displayed. */
  @Test
  public void clipboard_for_ok_round_trips_via_display() {
    Wgs84 fix =
        new Wgs84(
            GoldenVectors.KAOHSIUNG_85.latDeg,
            GoldenVectors.KAOHSIUNG_85.lonDeg,
            1L,
            Wgs84.Source.MAP_CENTRE);
    for (CoordinateUnit u : CoordinateUnit.values()) {
      for (Formatter.Strings s : new Formatter.Strings[] {EN, ZH_TW, JA}) {
        ConversionResult r = conv.convert(fix, u);
        DisplayLine line = formatter.format(Wgs84.Source.MAP_CENTRE, r, u, s);
        String clip = formatter.forClipboard(line);
        // Format: "<label> <unitTag>: <value>"
        assertThat(clip)
            .as("unit=%s strings=%s", u, s)
            .startsWith(line.labelPrefix() + " " + line.unitTag() + ": " + line.value());
      }
    }
  }

  @Test
  public void clipboard_for_out_of_range_includes_wgs84_fallback_line() {
    Wgs84 fix = new Wgs84(40.0, 121.0, 1L, Wgs84.Source.MAP_CENTRE);
    ConversionResult r = conv.convert(fix, CoordinateUnit.TWD97);
    DisplayLine line = formatter.format(Wgs84.Source.MAP_CENTRE, r, CoordinateUnit.TWD97, EN);
    String clip = formatter.forClipboard(line);
    assertThat(clip).contains("out of range").contains("(40.000000, 121.000000)");
  }

  private static final class TestStrings implements Formatter.Strings {
    private final String labelMap, labelMe, tpc, twd97, twd67, oor, noFix, noPerm;

    TestStrings(
        String labelMap,
        String labelMe,
        String tpc,
        String twd97,
        String twd67,
        String oor,
        String noFix,
        String noPerm) {
      this.labelMap = labelMap;
      this.labelMe = labelMe;
      this.tpc = tpc;
      this.twd97 = twd97;
      this.twd67 = twd67;
      this.oor = oor;
      this.noFix = noFix;
      this.noPerm = noPerm;
    }

    @Override
    public String labelMap() {
      return labelMap;
    }

    @Override
    public String labelMe() {
      return labelMe;
    }

    @Override
    public String unitTagTaipower() {
      return tpc;
    }

    @Override
    public String unitTagTwd97() {
      return twd97;
    }

    @Override
    public String unitTagTwd67() {
      return twd67;
    }

    @Override
    public String stateOutOfRange() {
      return oor;
    }

    @Override
    public String stateNoFix() {
      return noFix;
    }

    @Override
    public String stateNoPermission() {
      return noPerm;
    }

    @Override
    public String toString() {
      return labelMap + "/" + labelMe;
    }
  }
}
