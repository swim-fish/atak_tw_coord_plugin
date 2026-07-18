package com.atakmap.android.twcoord.nativeentry;

import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.TaipowerCode;
import com.atakmap.android.twcoord.coord.Twd67Tm2;
import com.atakmap.android.twcoord.coord.Twd97Tm2;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.gotopage.CoordinateParser;
import com.atakmap.android.twcoord.gotopage.ParseResult;
import java.util.Objects;
import java.util.function.Consumer;

/** Host-independent session/controller for ATAK's native Taiwan coordinate pane. */
public final class TaiwanEntryController {

  public enum Validation {
    EMPTY,
    INCOMPLETE,
    MALFORMED,
    BAD_ZONE,
    OUT_OF_COVERAGE,
    UNREPRESENTABLE,
    VALID,
    DISPOSED
  }

  private final Consumer<CoordinateUnit> selectionWriter;
  private final CoordinateParser parser = new CoordinateParser();
  private final CoordinateConverter converter = new CoordinateConverter();

  private CoordinateUnit activeUnit;
  private String taipowerText = "";
  private String twd97Easting = "";
  private String twd97Northing = "";
  private int twd97Zone = 121;
  private String twd67Easting = "";
  private String twd67Northing = "";
  private int twd67Zone = 121;
  private Validation validation = Validation.EMPTY;
  private Wgs84 resolved;
  private Runnable onHumanChange;
  private boolean editable = true;
  private boolean disposed;

  public TaiwanEntryController(
      CoordinateUnit initialUnit, Consumer<CoordinateUnit> selectionWriter) {
    this.activeUnit = initialUnit == null ? CoordinateUnit.TAIPOWER : initialUnit;
    this.selectionWriter = Objects.requireNonNull(selectionWriter, "selectionWriter");
    validation = validateActive();
  }

  public CoordinateUnit activeUnit() {
    return activeUnit;
  }

  public Validation validation() {
    return validation;
  }

  public String taipowerText() {
    return taipowerText;
  }

  public String eastingText(CoordinateUnit unit) {
    return unit == CoordinateUnit.TWD97 ? twd97Easting : twd67Easting;
  }

  public String northingText(CoordinateUnit unit) {
    return unit == CoordinateUnit.TWD97 ? twd97Northing : twd67Northing;
  }

  public int zone(CoordinateUnit unit) {
    return unit == CoordinateUnit.TWD97 ? twd97Zone : twd67Zone;
  }

  public Wgs84 resolvedOrNull() {
    return resolved;
  }

  public boolean isEditable() {
    return editable;
  }

  public boolean isDisposed() {
    return disposed;
  }

  public void setOnHumanChange(Runnable listener) {
    onHumanChange = listener;
  }

  public void selectSystem(CoordinateUnit unit, boolean human) {
    if (disposed || unit == null || (human && !editable)) return;
    activeUnit = unit;
    validation = validateActive();
    if (human) {
      selectionWriter.accept(unit);
      notifyHumanChange();
    }
  }

  public void setTaipowerText(String text, boolean human) {
    if (!acceptEdit(human)) return;
    taipowerText = text == null ? "" : text;
    if (activeUnit == CoordinateUnit.TAIPOWER) validation = validateActive();
    if (human) notifyHumanChange();
  }

  public void setTwdEasting(CoordinateUnit unit, String text, boolean human) {
    requireTwd(unit);
    if (!acceptEdit(human)) return;
    if (unit == CoordinateUnit.TWD97) twd97Easting = safeText(text);
    else twd67Easting = safeText(text);
    if (activeUnit == unit) validation = validateActive();
    if (human) notifyHumanChange();
  }

  public void setTwdNorthing(CoordinateUnit unit, String text, boolean human) {
    requireTwd(unit);
    if (!acceptEdit(human)) return;
    if (unit == CoordinateUnit.TWD97) twd97Northing = safeText(text);
    else twd67Northing = safeText(text);
    if (activeUnit == unit) validation = validateActive();
    if (human) notifyHumanChange();
  }

  public void setZone(CoordinateUnit unit, int zone, boolean human) {
    requireTwd(unit);
    if (!acceptEdit(human)) return;
    if (unit == CoordinateUnit.TWD97) twd97Zone = zone;
    else twd67Zone = zone;
    if (activeUnit == unit) validation = validateActive();
    if (human) notifyHumanChange();
  }

  public void activate(Wgs84 point, boolean editable) {
    if (disposed) return;
    this.editable = editable;
    populateFromHost(point);
  }

  public void autofill(Wgs84 point) {
    if (disposed) return;
    populateFromHost(point);
  }

  public void clear() {
    if (disposed) return;
    clearActiveDraft();
    resolved = null;
    validation = Validation.EMPTY;
  }

  public String format(Wgs84 point, TaiwanEntryFormatter formatter) {
    if (disposed) return null;
    return Objects.requireNonNull(formatter, "formatter").format(point, activeUnit);
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    resolved = null;
    validation = Validation.DISPOSED;
    onHumanChange = null;
  }

  private boolean acceptEdit(boolean human) {
    return !disposed && (!human || editable);
  }

  private void populateFromHost(Wgs84 point) {
    clearActiveDraft();
    resolved = null;
    validation = Validation.EMPTY;
    if (point == null) return;
    ConversionResult result = converter.convert(point, activeUnit);
    if (!result.isOk()) {
      validation = Validation.UNREPRESENTABLE;
      return;
    }
    Object value = ((ConversionResult.Ok<?>) result).value();
    switch (activeUnit) {
      case TAIPOWER:
        taipowerText = TaiwanEntryFormatter.formatTaipower((TaipowerCode) value);
        break;
      case TWD97:
        Twd97Tm2 t97 = (Twd97Tm2) value;
        twd97Easting = Long.toString(Math.round(t97.eastingMetres()));
        twd97Northing = Long.toString(Math.round(t97.northingMetres()));
        twd97Zone = t97.zone();
        break;
      case TWD67:
        Twd67Tm2 t67 = (Twd67Tm2) value;
        twd67Easting = Long.toString(Math.round(t67.eastingMetres()));
        twd67Northing = Long.toString(Math.round(t67.northingMetres()));
        twd67Zone = t67.zone();
        break;
      default:
        throw new IllegalStateException("Unhandled coordinate unit: " + activeUnit);
    }
    validation = validateActive();
  }

  private Validation validateActive() {
    resolved = null;
    if (activeUnit == CoordinateUnit.TAIPOWER) return validateTaipower();
    return validateTwd(activeUnit);
  }

  private Validation validateTaipower() {
    if (taipowerText.trim().isEmpty()) return Validation.EMPTY;
    return mapParseResult(parser.parseTaipower(taipowerText));
  }

  private Validation validateTwd(CoordinateUnit unit) {
    String easting = eastingText(unit);
    String northing = northingText(unit);
    if (easting.isEmpty() && northing.isEmpty()) return Validation.EMPTY;
    if (easting.isEmpty() || northing.isEmpty()) return Validation.INCOMPLETE;
    int zone = zone(unit);
    if (zone != 121 && zone != 119) return Validation.BAD_ZONE;
    if (!isAsciiUnsignedInteger(easting) || !isAsciiUnsignedInteger(northing)) {
      return Validation.MALFORMED;
    }
    final int eastingValue;
    final int northingValue;
    try {
      eastingValue = Integer.parseInt(easting);
      northingValue = Integer.parseInt(northing);
    } catch (NumberFormatException e) {
      return Validation.MALFORMED;
    }
    ParseResult result =
        unit == CoordinateUnit.TWD97
            ? parser.parseTwd97(eastingValue, northingValue, zone)
            : parser.parseTwd67(eastingValue, northingValue, zone);
    return mapParseResult(result);
  }

  private Validation mapParseResult(ParseResult result) {
    if (result.isOk()) {
      resolved = ((ParseResult.Ok) result).wgs84();
      return Validation.VALID;
    }
    if (result.isOutOfRange()) return Validation.OUT_OF_COVERAGE;
    ParseResult.Reason reason = ((ParseResult.Invalid) result).reason();
    if (reason == ParseResult.Reason.EMPTY) return Validation.EMPTY;
    if (reason == ParseResult.Reason.BAD_ZONE) return Validation.BAD_ZONE;
    return Validation.MALFORMED;
  }

  private void clearActiveDraft() {
    switch (activeUnit) {
      case TAIPOWER:
        taipowerText = "";
        break;
      case TWD97:
        twd97Easting = "";
        twd97Northing = "";
        break;
      case TWD67:
        twd67Easting = "";
        twd67Northing = "";
        break;
      default:
        throw new IllegalStateException("Unhandled coordinate unit: " + activeUnit);
    }
  }

  private static boolean isAsciiUnsignedInteger(String value) {
    if (value.isEmpty()) return false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') return false;
    }
    return true;
  }

  private static String safeText(String text) {
    return text == null ? "" : text;
  }

  private static void requireTwd(CoordinateUnit unit) {
    if (unit != CoordinateUnit.TWD97 && unit != CoordinateUnit.TWD67) {
      throw new IllegalArgumentException("Expected TWD97 or TWD67: " + unit);
    }
  }

  private void notifyHumanChange() {
    Runnable listener = onHumanChange;
    if (listener != null) listener.run();
  }
}
