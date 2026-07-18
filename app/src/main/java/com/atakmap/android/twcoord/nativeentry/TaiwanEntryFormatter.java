package com.atakmap.android.twcoord.nativeentry;

import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.TaipowerCode;
import com.atakmap.android.twcoord.coord.Twd67Tm2;
import com.atakmap.android.twcoord.coord.Twd97Tm2;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Locale;
import java.util.Objects;

/** Pure native-entry formatter. It does not read or mutate pane/controller state. */
public final class TaiwanEntryFormatter {

  private final CoordinateConverter converter;

  public TaiwanEntryFormatter() {
    this(new CoordinateConverter());
  }

  TaiwanEntryFormatter(CoordinateConverter converter) {
    this.converter = Objects.requireNonNull(converter, "converter");
  }

  public String format(Wgs84 point, CoordinateUnit unit) {
    if (point == null || unit == null) return null;
    ConversionResult result = converter.convert(point, unit);
    if (!result.isOk()) return null;

    Object value = ((ConversionResult.Ok<?>) result).value();
    switch (unit) {
      case TAIPOWER:
        return formatTaipower((TaipowerCode) value);
      case TWD97:
        Twd97Tm2 t97 = (Twd97Tm2) value;
        return formatTm2("TWD97", t97.eastingMetres(), t97.northingMetres(), t97.zone());
      case TWD67:
        Twd67Tm2 t67 = (Twd67Tm2) value;
        return formatTm2("TWD67", t67.eastingMetres(), t67.northingMetres(), t67.zone());
      default:
        throw new IllegalStateException("Unhandled coordinate unit: " + unit);
    }
  }

  static String formatTaipower(TaipowerCode code) {
    if (!code.hasOneMetrePrecision()) {
      return String.format(
          Locale.ROOT,
          "%c%04d %c%c%d%d",
          code.region(),
          code.subRegion(),
          code.hundredMeterE(),
          code.hundredMeterN(),
          code.tenMeterE(),
          code.tenMeterN());
    }
    return String.format(
        Locale.ROOT,
        "%c%04d %c%c%d%d%d%d",
        code.region(),
        code.subRegion(),
        code.hundredMeterE(),
        code.hundredMeterN(),
        code.tenMeterE(),
        code.tenMeterN(),
        code.oneMeterE(),
        code.oneMeterN());
  }

  private static String formatTm2(String system, double easting, double northing, int zone) {
    return String.format(
        Locale.ROOT, "%s E=%dm N=%dm z%d", system, Math.round(easting), Math.round(northing), zone);
  }
}
