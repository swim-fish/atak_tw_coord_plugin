package com.atakmap.android.twcoord.coord;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Turns a {@link ConversionResult} into a {@link DisplayLine} the on-map widget renders. Pure Java,
 * thread-safe, no Android dependency. Per contracts/coordinate-formatter.md.
 */
public final class Formatter {

  /** String bundle resolved per locale by the caller. */
  public interface Strings {
    String labelMap();

    String labelMe();

    String labelTarget();

    String unitTagTaipower();

    String unitTagTwd97();

    String unitTagTwd67();

    String stateOutOfRange();

    String stateNoFix();

    String stateNoPermission();
  }

  private final ConcurrentMap<Locale, NumberFormat> intFormats = new ConcurrentHashMap<>();
  private final Locale locale;

  public Formatter() {
    this(Locale.getDefault());
  }

  public Formatter(Locale locale) {
    this.locale = Objects.requireNonNull(locale, "locale");
  }

  public DisplayLine format(
      Wgs84.Source source, ConversionResult result, CoordinateUnit unit, Strings strings) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(strings, "strings");

    String labelPrefix;
    switch (source) {
      case MAP_CENTRE:
        labelPrefix = strings.labelMap();
        break;
      case DEVICE_LOCATION:
        labelPrefix = strings.labelMe();
        break;
      case COT_TARGET:
        labelPrefix = strings.labelTarget();
        break;
      default:
        labelPrefix = "?";
    }
    String unitTag = unitTag(unit, strings);

    if (result.isNoFix()) {
      return new DisplayLine(labelPrefix, "", "", "", DisplayLine.State.NO_FIX);
    }
    if (result.isOutOfRange()) {
      ConversionResult.OutOfRange oor = (ConversionResult.OutOfRange) result;
      String fb = formatFallbackWgs84(oor.fix());
      return new DisplayLine(
          labelPrefix, unitTag, strings.stateOutOfRange(), fb, DisplayLine.State.OUT_OF_RANGE);
    }
    // OK
    @SuppressWarnings("rawtypes")
    ConversionResult.Ok ok = (ConversionResult.Ok) result;
    String value;
    switch (unit) {
      case TWD97:
        value = formatTwd97((Twd97Tm2) ok.value());
        break;
      case TWD67:
        value = formatTwd67((Twd67Tm2) ok.value());
        break;
      case TAIPOWER:
        value = formatTaipower((TaipowerCode) ok.value());
        break;
      default:
        throw new IllegalStateException("unhandled unit: " + unit);
    }
    return new DisplayLine(labelPrefix, unitTag, value, "", DisplayLine.State.OK);
  }

  /** Exact string that goes into the clipboard on tap (FR-015 / SC-008). */
  public String forClipboard(DisplayLine line) {
    Objects.requireNonNull(line, "line");
    switch (line.state()) {
      case OK:
        return line.labelPrefix() + " " + line.unitTag() + ": " + line.value();
      case OUT_OF_RANGE:
        return line.labelPrefix()
            + " "
            + line.unitTag()
            + ": "
            + line.value()
            + "\n("
            + line.fallback()
            + ")";
      case NO_FIX:
        // Use the formatter's no-fix wording from the line itself if present, else fall back.
        return line.labelPrefix() + ": " + (line.value().isEmpty() ? "no fix" : line.value());
      case NO_PERMISSION:
        return line.labelPrefix()
            + ": "
            + (line.value().isEmpty() ? "no permission" : line.value());
      default:
        throw new IllegalStateException("unhandled state: " + line.state());
    }
  }

  private String unitTag(CoordinateUnit unit, Strings strings) {
    switch (unit) {
      case TAIPOWER:
        return strings.unitTagTaipower();
      case TWD97:
        return strings.unitTagTwd97();
      case TWD67:
        return strings.unitTagTwd67();
      default:
        throw new IllegalStateException("unhandled unit: " + unit);
    }
  }

  private String formatTwd97(Twd97Tm2 v) {
    return intMetres(v.eastingMetres())
        + "m "
        + intMetres(v.northingMetres())
        + "m"
        + zoneSuffix(v.zone());
  }

  private String formatTwd67(Twd67Tm2 v) {
    return intMetres(v.eastingMetres())
        + "m "
        + intMetres(v.northingMetres())
        + "m"
        + zoneSuffix(v.zone());
  }

  /**
   * Zone 121 (Taiwan main island) is the implicit default and adds no suffix; zone 119 (Penghu /
   * 澎湖) appends " z119" so it is unmistakeable that the easting/northing belong to a different TM2
   * grid than the main island.
   */
  private static String zoneSuffix(int zone) {
    return zone == 121 ? "" : " z" + zone;
  }

  private String formatTaipower(TaipowerCode c) {
    if (!c.hasOneMetrePrecision()) {
      return String.format(
          Locale.ROOT,
          "%c%04d %c%c%d%d",
          c.region(),
          c.subRegion(),
          c.hundredMeterE(),
          c.hundredMeterN(),
          c.tenMeterE(),
          c.tenMeterN());
    }
    return String.format(
        Locale.ROOT,
        "%c%04d %c%c%d%d%d%d",
        c.region(),
        c.subRegion(),
        c.hundredMeterE(),
        c.hundredMeterN(),
        c.tenMeterE(),
        c.tenMeterN(),
        c.oneMeterE(),
        c.oneMeterN());
  }

  private String intMetres(double metres) {
    long rounded = Math.round(metres);
    NumberFormat nf = intFormats.computeIfAbsent(locale, NumberFormat::getIntegerInstance);
    return nf.format(rounded);
  }

  private String formatFallbackWgs84(Wgs84 fix) {
    return String.format(Locale.ROOT, "%.6f, %.6f", fix.latitudeDeg(), fix.longitudeDeg());
  }
}
