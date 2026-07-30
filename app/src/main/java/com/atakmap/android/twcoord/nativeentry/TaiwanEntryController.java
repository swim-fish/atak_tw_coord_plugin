package com.atakmap.android.twcoord.nativeentry;

import com.atakmap.android.twcoord.coord.ConversionResult;
import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.TaipowerCode;
import com.atakmap.android.twcoord.coord.Twd67Tm2;
import com.atakmap.android.twcoord.coord.Twd97Tm2;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.coord.input.CoordinateParser;
import com.atakmap.android.twcoord.coord.input.ParseResult;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.BiFunction;
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
  private final Consumer<TaipowerInputMode> taipowerModeWriter;
  private final CoordinateParser parser = new CoordinateParser();
  private final BiFunction<Wgs84, CoordinateUnit, ConversionResult> converter;

  private CoordinateUnit activeUnit;
  private NativeEntryTab activeTab;
  private TaipowerInputMode taipowerInputMode;
  private TaipowerEntryDraft taipowerDraft;
  private EnumMap<CoordinateUnit, Draft> drafts;
  private Runnable onHumanChange;
  private boolean editable = true;
  private boolean disposed;

  public TaiwanEntryController(
      CoordinateUnit initialUnit, Consumer<CoordinateUnit> selectionWriter) {
    this(
        initialUnit,
        selectionWriter,
        new CoordinateConverter()::convert,
        TaipowerInputMode.SINGLE_FIELD,
        ignored -> {});
  }

  public TaiwanEntryController(
      CoordinateUnit initialUnit,
      Consumer<CoordinateUnit> selectionWriter,
      TaipowerInputMode initialTaipowerMode,
      Consumer<TaipowerInputMode> taipowerModeWriter) {
    this(
        initialUnit,
        selectionWriter,
        new CoordinateConverter()::convert,
        initialTaipowerMode,
        taipowerModeWriter);
  }

  TaiwanEntryController(
      CoordinateUnit initialUnit,
      Consumer<CoordinateUnit> selectionWriter,
      BiFunction<Wgs84, CoordinateUnit, ConversionResult> converter) {
    this(initialUnit, selectionWriter, converter, TaipowerInputMode.SINGLE_FIELD, ignored -> {});
  }

  private TaiwanEntryController(
      CoordinateUnit initialUnit,
      Consumer<CoordinateUnit> selectionWriter,
      BiFunction<Wgs84, CoordinateUnit, ConversionResult> converter,
      TaipowerInputMode initialTaipowerMode,
      Consumer<TaipowerInputMode> taipowerModeWriter) {
    activeUnit = initialUnit == null ? CoordinateUnit.TAIPOWER : initialUnit;
    activeTab = NativeEntryTab.fromCoordinateUnit(activeUnit);
    this.selectionWriter = Objects.requireNonNull(selectionWriter, "selectionWriter");
    this.converter = Objects.requireNonNull(converter, "converter");
    taipowerInputMode =
        initialTaipowerMode == null ? TaipowerInputMode.SINGLE_FIELD : initialTaipowerMode;
    this.taipowerModeWriter = Objects.requireNonNull(taipowerModeWriter, "taipowerModeWriter");
    taipowerDraft = TaipowerEntryDraft.empty();
    drafts = emptyDrafts(Validation.EMPTY);
  }

  public CoordinateUnit activeUnit() {
    return activeUnit;
  }

  public NativeEntryTab activeTab() {
    return activeTab;
  }

  public Validation validation() {
    if (disposed) return Validation.DISPOSED;
    return activeUnit == CoordinateUnit.TAIPOWER
        ? taipowerDraft.validation()
        : activeDraft().validation;
  }

  public String taipowerText() {
    return taipowerDraft.rawText();
  }

  public TaipowerEntryDraft taipowerDraft() {
    return taipowerDraft;
  }

  public TaipowerInputMode taipowerInputMode() {
    return taipowerInputMode;
  }

  public String eastingText(CoordinateUnit unit) {
    requireTwd(unit);
    return draft(unit).eastingText;
  }

  public String northingText(CoordinateUnit unit) {
    requireTwd(unit);
    return draft(unit).northingText;
  }

  public int zone(CoordinateUnit unit) {
    requireTwd(unit);
    return draft(unit).zone;
  }

  public Wgs84 resolvedOrNull() {
    if (disposed) return null;
    return activeUnit == CoordinateUnit.TAIPOWER
        ? taipowerDraft.resolvedOrNull()
        : activeDraft().resolved;
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
    activeTab = NativeEntryTab.fromCoordinateUnit(unit);
    if (human) {
      selectionWriter.accept(unit);
      notifyHumanChange();
    }
  }

  public void selectTab(NativeEntryTab tab, boolean human) {
    if (disposed || tab == null || (human && !editable)) return;
    CoordinateUnit coordinateUnit = tab.coordinateUnitOrNull();
    activeTab = tab;
    if (coordinateUnit != null) activeUnit = coordinateUnit;
    if (human && coordinateUnit != null) selectionWriter.accept(coordinateUnit);
    if (human) notifyHumanChange();
  }

  public void setTaipowerText(String text, boolean human) {
    if (!acceptEdit(human)) return;
    replaceTaipowerDraft(taipowerDraft.editRaw(safeText(text)), human);
  }

  public void setTaipowerRegion(String text, boolean human) {
    if (!acceptEdit(human)) return;
    replaceTaipowerDraft(taipowerDraft.editRegion(text), human);
  }

  public void setTaipowerSubregion(String text, boolean human) {
    if (!acceptEdit(human)) return;
    replaceTaipowerDraft(taipowerDraft.editSubregion(text), human);
  }

  public void setTaipowerSubgrid(String text, boolean human) {
    if (!acceptEdit(human)) return;
    replaceTaipowerDraft(taipowerDraft.editSubgrid(text), human);
  }

  public void setTaipowerPrecisionDigits(String text, boolean human) {
    if (!acceptEdit(human)) return;
    replaceTaipowerDraft(taipowerDraft.editPrecisionDigits(text), human);
  }

  public boolean selectTaipowerInputMode(TaipowerInputMode mode, boolean human) {
    if (disposed || mode == null || !taipowerDraft.canProject(mode)) return false;
    if (mode == taipowerInputMode) return true;
    taipowerInputMode = mode;
    if (human) taipowerModeWriter.accept(mode);
    return true;
  }

  public void setTwdEasting(CoordinateUnit unit, String text, boolean human) {
    requireTwd(unit);
    if (!acceptEdit(human)) return;
    Draft draft = draft(unit);
    draft.eastingText = safeText(text);
    validateDraft(unit, draft);
    if (human) notifyHumanChange();
  }

  public void setTwdNorthing(CoordinateUnit unit, String text, boolean human) {
    requireTwd(unit);
    if (!acceptEdit(human)) return;
    Draft draft = draft(unit);
    draft.northingText = safeText(text);
    validateDraft(unit, draft);
    if (human) notifyHumanChange();
  }

  public void setZone(CoordinateUnit unit, int zone, boolean human) {
    requireTwd(unit);
    if (!acceptEdit(human)) return;
    Draft draft = draft(unit);
    draft.zone = zone;
    validateDraft(unit, draft);
    if (human) notifyHumanChange();
  }

  public void activate(Wgs84 point, boolean editable) {
    if (disposed) return;
    this.editable = editable;
    if (point == null) {
      clear();
      return;
    }
    populateAllFromHost(point);
  }

  void invalidateActivation(boolean editable) {
    if (disposed) return;
    this.editable = editable;
    taipowerDraft = TaipowerEntryDraft.unavailable();
    drafts = emptyDrafts(Validation.UNREPRESENTABLE);
  }

  public void autofill(Wgs84 point) {
    if (disposed) return;
    if (point == null) {
      clear();
      return;
    }
    populateAllFromHost(point, true);
  }

  public void clear() {
    if (disposed) return;
    if (activeUnit == CoordinateUnit.TAIPOWER) {
      taipowerDraft = TaipowerEntryDraft.empty();
      return;
    }
    Draft previous = activeDraft();
    Draft replacement = Draft.empty(activeUnit, Validation.EMPTY);
    preserveTwdZone(activeUnit, previous, replacement);
    drafts.put(activeUnit, replacement);
  }

  public String format(Wgs84 point, TaiwanEntryFormatter formatter) {
    if (disposed) return null;
    return Objects.requireNonNull(formatter, "formatter").format(point, activeUnit);
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    taipowerDraft = TaipowerEntryDraft.disposed();
    drafts = emptyDrafts(Validation.DISPOSED);
    onHumanChange = null;
  }

  private boolean acceptEdit(boolean human) {
    return !disposed && (!human || editable);
  }

  private void populateAllFromHost(Wgs84 point) {
    populateAllFromHost(point, false);
  }

  private void populateAllFromHost(Wgs84 point, boolean preserveUnavailableZones) {
    EnumMap<CoordinateUnit, Draft> staged = new EnumMap<>(CoordinateUnit.class);
    try {
      TaipowerEntryDraft stagedTaipower = taipowerDraftFromHost(point);
      for (CoordinateUnit unit :
          new CoordinateUnit[] {CoordinateUnit.TWD97, CoordinateUnit.TWD67}) {
        Draft replacement = draftFromHost(point, unit);
        if (preserveUnavailableZones) {
          preserveZoneWhenUnavailable(unit, draft(unit), replacement);
        }
        staged.put(unit, replacement);
      }
      drafts = staged;
      taipowerDraft = stagedTaipower;
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      taipowerDraft = TaipowerEntryDraft.unavailable();
      drafts = emptyDrafts(Validation.UNREPRESENTABLE);
      throw e;
    }
  }

  private TaipowerEntryDraft taipowerDraftFromHost(Wgs84 point) {
    ConversionResult result = converter.apply(point, CoordinateUnit.TAIPOWER);
    if (!result.isOk()) return TaipowerEntryDraft.unavailable();
    String formatted =
        TaiwanEntryFormatter.formatTaipower(
            (TaipowerCode) ((ConversionResult.Ok<?>) result).value());
    return TaipowerEntryDraft.empty().editRaw(formatted);
  }

  private Draft draftFromHost(Wgs84 point, CoordinateUnit unit) {
    requireTwd(unit);
    ConversionResult result = converter.apply(point, unit);
    if (!result.isOk()) return Draft.empty(unit, Validation.UNREPRESENTABLE);

    Draft draft = Draft.empty(unit, Validation.EMPTY);
    Object value = ((ConversionResult.Ok<?>) result).value();
    switch (unit) {
      case TWD97:
        Twd97Tm2 t97 = (Twd97Tm2) value;
        draft.eastingText = Long.toString(Math.round(t97.eastingMetres()));
        draft.northingText = Long.toString(Math.round(t97.northingMetres()));
        draft.zone = t97.zone();
        break;
      case TWD67:
        Twd67Tm2 t67 = (Twd67Tm2) value;
        draft.eastingText = Long.toString(Math.round(t67.eastingMetres()));
        draft.northingText = Long.toString(Math.round(t67.northingMetres()));
        draft.zone = t67.zone();
        break;
      default:
        throw new IllegalStateException("Unhandled coordinate unit: " + unit);
    }
    validateDraft(unit, draft);
    return draft;
  }

  private static void preserveZoneWhenUnavailable(
      CoordinateUnit unit, Draft previous, Draft replacement) {
    if (replacement.validation == Validation.UNREPRESENTABLE) {
      preserveTwdZone(unit, previous, replacement);
    }
  }

  private static void preserveTwdZone(CoordinateUnit unit, Draft previous, Draft replacement) {
    if (unit == CoordinateUnit.TWD97 || unit == CoordinateUnit.TWD67) {
      replacement.zone = previous.zone;
    }
  }

  private void validateDraft(CoordinateUnit unit, Draft draft) {
    draft.resolved = null;
    validateTwd(unit, draft);
  }

  private void validateTwd(CoordinateUnit unit, Draft draft) {
    if (draft.eastingText.isEmpty() && draft.northingText.isEmpty()) {
      draft.validation = Validation.EMPTY;
      return;
    }
    if (draft.eastingText.isEmpty() || draft.northingText.isEmpty()) {
      draft.validation = Validation.INCOMPLETE;
      return;
    }
    if (draft.zone != 121 && draft.zone != 119) {
      draft.validation = Validation.BAD_ZONE;
      return;
    }
    if (!isAsciiUnsignedInteger(draft.eastingText) || !isAsciiUnsignedInteger(draft.northingText)) {
      draft.validation = Validation.MALFORMED;
      return;
    }

    final int eastingValue;
    final int northingValue;
    try {
      eastingValue = Integer.parseInt(draft.eastingText);
      northingValue = Integer.parseInt(draft.northingText);
    } catch (NumberFormatException e) {
      draft.validation = Validation.MALFORMED;
      return;
    }
    ParseResult result =
        unit == CoordinateUnit.TWD97
            ? parser.parseTwd97(eastingValue, northingValue, draft.zone)
            : parser.parseTwd67(eastingValue, northingValue, draft.zone);
    mapParseResult(draft, result);
  }

  private static void mapParseResult(Draft draft, ParseResult result) {
    if (result.isOk()) {
      draft.resolved = ((ParseResult.Ok) result).wgs84();
      draft.validation = Validation.VALID;
      return;
    }
    if (result.isOutOfRange()) {
      draft.validation = Validation.OUT_OF_COVERAGE;
      return;
    }
    ParseResult.Reason reason = ((ParseResult.Invalid) result).reason();
    if (reason == ParseResult.Reason.EMPTY) draft.validation = Validation.EMPTY;
    else if (reason == ParseResult.Reason.BAD_ZONE) draft.validation = Validation.BAD_ZONE;
    else draft.validation = Validation.MALFORMED;
  }

  private Draft activeDraft() {
    if (activeUnit == CoordinateUnit.TAIPOWER) {
      throw new IllegalStateException("Taipower uses TaipowerEntryDraft");
    }
    return draft(activeUnit);
  }

  private Draft draft(CoordinateUnit unit) {
    return drafts.get(unit);
  }

  private static EnumMap<CoordinateUnit, Draft> emptyDrafts(Validation validation) {
    EnumMap<CoordinateUnit, Draft> result = new EnumMap<>(CoordinateUnit.class);
    for (CoordinateUnit unit : CoordinateUnit.values()) {
      result.put(unit, Draft.empty(unit, validation));
    }
    return result;
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

  private void replaceTaipowerDraft(TaipowerEntryDraft replacement, boolean human) {
    if (replacement == taipowerDraft) return;
    taipowerDraft = replacement;
    if (human) notifyHumanChange();
  }

  private static final class Draft {
    String eastingText = "";
    String northingText = "";
    int zone = 121;
    Validation validation;
    Wgs84 resolved;

    static Draft empty(CoordinateUnit unit, Validation validation) {
      Objects.requireNonNull(unit, "unit");
      Draft draft = new Draft();
      draft.validation = Objects.requireNonNull(validation, "validation");
      return draft;
    }
  }
}
