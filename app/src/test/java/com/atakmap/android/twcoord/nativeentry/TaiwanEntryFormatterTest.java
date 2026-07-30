package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Wgs84;
import org.junit.Test;

public final class TaiwanEntryFormatterTest {

  private final TaiwanEntryFormatter formatter = new TaiwanEntryFormatter();

  @Test
  public void taipower_isCanonicalElevenCharacterOneMetreOutput() {
    String value = formatter.format(point(23.9932, 121.6012), CoordinateUnit.TAIPOWER);

    assertThat(value).matches("[A-X]\\d{4} [A-H][A-E]\\d{4}");
  }

  @Test
  public void twdFormatsAlwaysIncludeAxesMetresAndExplicitZone() {
    assertThat(formatter.format(point(25.033611, 121.564472), CoordinateUnit.TWD97))
        .matches("TWD97 E=\\d+m N=\\d+m z121");
    assertThat(formatter.format(point(23.566, 119.566), CoordinateUnit.TWD67))
        .matches("TWD67 E=\\d+m N=\\d+m z119");
  }

  @Test
  public void nullAndUnrepresentableReturnNull() {
    assertThat(formatter.format(null, CoordinateUnit.TWD97)).isNull();
    assertThat(formatter.format(point(35.6586, 139.7454), CoordinateUnit.TAIPOWER)).isNull();
  }

  private static Wgs84 point(double latitude, double longitude) {
    return new Wgs84(latitude, longitude, 1L, Wgs84.Source.MAP_CENTRE);
  }
}
