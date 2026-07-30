package com.atakmap.android.twcoord.nativeentry;

import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.coord.input.CoordinateParser;
import com.atakmap.android.twcoord.coord.input.ParseResult;
import java.util.Locale;
import java.util.Objects;

/**
 * One lossless Taipower coordinate draft with raw and guided projections.
 *
 * <p>The exact raw editor value is retained until a guided field changes. A presentation switch
 * never creates a new coordinate revision.
 */
public final class TaipowerEntryDraft {

  public enum Source {
    RAW,
    SPLIT
  }

  public enum Precision {
    NONE,
    INCOMPLETE,
    TEN_METRE,
    ONE_METRE
  }

  public enum ProjectionFailure {
    RAW_NOT_POSITIONAL,
    SPLIT_HAS_GAP,
    SPLIT_INVALID_CHARACTER
  }

  public enum ValidationDetail {
    EW_SUBGRID_OUT_OF_RANGE,
    NS_SUBGRID_OUT_OF_RANGE
  }

  public static final class SplitParts {
    private final String region;
    private final String subregion;
    private final String subgrid;
    private final String precisionDigits;

    private SplitParts(String region, String subregion, String subgrid, String precisionDigits) {
      this.region = region;
      this.subregion = subregion;
      this.subgrid = subgrid;
      this.precisionDigits = precisionDigits;
    }

    public String region() {
      return region;
    }

    public String subregion() {
      return subregion;
    }

    public String subgrid() {
      return subgrid;
    }

    public String precisionDigits() {
      return precisionDigits;
    }

    public String joined() {
      return region + subregion + subgrid + precisionDigits;
    }

    private SplitParts withRegion(String value) {
      return new SplitParts(value, subregion, subgrid, precisionDigits);
    }

    private SplitParts withSubregion(String value) {
      return new SplitParts(region, value, subgrid, precisionDigits);
    }

    private SplitParts withSubgrid(String value) {
      return new SplitParts(region, subregion, value, precisionDigits);
    }

    private SplitParts withPrecisionDigits(String value) {
      return new SplitParts(region, subregion, subgrid, value);
    }

    private boolean sameContent(SplitParts other) {
      return region.equals(other.region)
          && subregion.equals(other.subregion)
          && subgrid.equals(other.subgrid)
          && precisionDigits.equals(other.precisionDigits);
    }
  }

  private static final CoordinateParser PARSER = new CoordinateParser();
  private static final SplitParts EMPTY_PARTS = new SplitParts("", "", "", "");

  private final long revision;
  private final Source source;
  private final String rawText;
  private final long rawRevision;
  private final SplitParts splitParts;
  private final long splitRevision;
  private final TaiwanEntryController.Validation validation;
  private final ParseResult.Reason parseReason;
  private final ValidationDetail validationDetail;
  private final Precision precision;
  private final Wgs84 resolved;
  private final ProjectionFailure projectionFailure;

  private TaipowerEntryDraft(
      long revision,
      Source source,
      String rawText,
      long rawRevision,
      SplitParts splitParts,
      long splitRevision,
      TaiwanEntryController.Validation validation,
      ParseResult.Reason parseReason,
      ValidationDetail validationDetail,
      Precision precision,
      Wgs84 resolved,
      ProjectionFailure projectionFailure) {
    this.revision = revision;
    this.source = Objects.requireNonNull(source, "source");
    this.rawText = Objects.requireNonNull(rawText, "rawText");
    this.rawRevision = rawRevision;
    this.splitParts = Objects.requireNonNull(splitParts, "splitParts");
    this.splitRevision = splitRevision;
    this.validation = Objects.requireNonNull(validation, "validation");
    this.parseReason = parseReason;
    this.validationDetail = validationDetail;
    this.precision = Objects.requireNonNull(precision, "precision");
    this.resolved = resolved;
    this.projectionFailure = projectionFailure;
  }

  public static TaipowerEntryDraft empty() {
    return lifecycle(TaiwanEntryController.Validation.EMPTY);
  }

  static TaipowerEntryDraft unavailable() {
    return lifecycle(TaiwanEntryController.Validation.UNREPRESENTABLE);
  }

  static TaipowerEntryDraft disposed() {
    return lifecycle(TaiwanEntryController.Validation.DISPOSED);
  }

  private static TaipowerEntryDraft lifecycle(TaiwanEntryController.Validation validation) {
    return new TaipowerEntryDraft(
        0, Source.RAW, "", 0, EMPTY_PARTS, 0, validation, null, null, Precision.NONE, null, null);
  }

  public long revision() {
    return revision;
  }

  public Source source() {
    return source;
  }

  public String rawText() {
    return rawText;
  }

  public long rawRevision() {
    return rawRevision;
  }

  public SplitParts splitParts() {
    return splitParts;
  }

  public long splitRevision() {
    return splitRevision;
  }

  public TaiwanEntryController.Validation validation() {
    return validation;
  }

  public ParseResult.Reason parseReason() {
    return parseReason;
  }

  public ValidationDetail validationDetail() {
    return validationDetail;
  }

  public Precision precision() {
    return precision;
  }

  public Wgs84 resolvedOrNull() {
    return resolved;
  }

  public ProjectionFailure projectionFailure() {
    return projectionFailure;
  }

  public boolean canProject(TaipowerInputMode mode) {
    Objects.requireNonNull(mode, "mode");
    return mode == TaipowerInputMode.SINGLE_FIELD
        ? rawRevision == revision
        : splitRevision == revision;
  }

  public TaipowerEntryDraft editRaw(String value) {
    String exact = value == null ? "" : value;
    if (source == Source.RAW && rawRevision == revision && rawText.equals(exact)) return this;
    long nextRevision = revision + 1;
    String positional = normalizeRaw(exact);
    boolean projectable = isPositionalPrefix(positional);
    SplitParts projected = projectable ? split(positional) : splitParts;
    Evaluation evaluation = evaluate(positional, projectable, false);
    return new TaipowerEntryDraft(
        nextRevision,
        Source.RAW,
        exact,
        nextRevision,
        projected,
        projectable ? nextRevision : splitRevision,
        evaluation.validation,
        evaluation.parseReason,
        evaluation.validationDetail,
        evaluation.precision,
        evaluation.resolved,
        projectable ? null : ProjectionFailure.RAW_NOT_POSITIONAL);
  }

  public TaipowerEntryDraft editRegion(String value) {
    String accepted = acceptLetters(value, 1);
    return accepted == null ? this : editSplit(splitParts.withRegion(accepted));
  }

  public TaipowerEntryDraft editSubregion(String value) {
    String accepted = acceptDigits(value, 4);
    return accepted == null ? this : editSplit(splitParts.withSubregion(accepted));
  }

  public TaipowerEntryDraft editSubgrid(String value) {
    String accepted = acceptLetters(value, 2);
    return accepted == null ? this : editSplit(splitParts.withSubgrid(accepted));
  }

  public TaipowerEntryDraft editPrecisionDigits(String value) {
    String accepted = acceptDigits(value, 4);
    return accepted == null ? this : editSplit(splitParts.withPrecisionDigits(accepted));
  }

  private TaipowerEntryDraft editSplit(SplitParts nextParts) {
    if (source == Source.SPLIT && splitRevision == revision && splitParts.sameContent(nextParts)) {
      return this;
    }
    if (splitParts.sameContent(nextParts)) return this;
    long nextRevision = revision + 1;
    ProjectionFailure failure = splitFailure(nextParts);
    boolean projectable = failure == null;
    String joined = nextParts.joined();
    Evaluation evaluation = evaluate(joined, projectable, true);
    return new TaipowerEntryDraft(
        nextRevision,
        Source.SPLIT,
        projectable ? joined : rawText,
        projectable ? nextRevision : rawRevision,
        nextParts,
        nextRevision,
        evaluation.validation,
        evaluation.parseReason,
        evaluation.validationDetail,
        evaluation.precision,
        evaluation.resolved,
        failure);
  }

  private static Evaluation evaluate(String positional, boolean projectable, boolean splitSource) {
    if (!projectable) {
      return new Evaluation(
          splitSource
              ? TaiwanEntryController.Validation.INCOMPLETE
              : TaiwanEntryController.Validation.MALFORMED,
          splitSource ? null : ParseResult.Reason.BAD_LENGTH,
          null,
          positional.isEmpty() ? Precision.NONE : Precision.INCOMPLETE,
          null);
    }
    if (positional.isEmpty()) {
      return new Evaluation(
          TaiwanEntryController.Validation.EMPTY,
          ParseResult.Reason.EMPTY,
          null,
          Precision.NONE,
          null);
    }

    Precision precision = precision(positional);
    ValidationDetail detail = validationDetail(positional);
    if (detail != null) {
      return new Evaluation(
          TaiwanEntryController.Validation.MALFORMED,
          ParseResult.Reason.BAD_LETTER,
          detail,
          precision,
          null);
    }
    if (positional.length() != 9 && positional.length() != 11) {
      return new Evaluation(
          TaiwanEntryController.Validation.INCOMPLETE,
          ParseResult.Reason.BAD_LENGTH,
          null,
          precision,
          null);
    }
    ParseResult result = PARSER.parseTaipower(positional);
    if (result.isOk()) {
      return new Evaluation(
          TaiwanEntryController.Validation.VALID,
          null,
          null,
          precision,
          ((ParseResult.Ok) result).wgs84());
    }
    if (result.isOutOfRange()) {
      return new Evaluation(
          TaiwanEntryController.Validation.OUT_OF_COVERAGE, null, null, precision, null);
    }
    ParseResult.Reason reason = ((ParseResult.Invalid) result).reason();
    return new Evaluation(
        reason == ParseResult.Reason.EMPTY
            ? TaiwanEntryController.Validation.EMPTY
            : TaiwanEntryController.Validation.MALFORMED,
        reason,
        null,
        precision,
        null);
  }

  private static Precision precision(String positional) {
    if (positional.isEmpty()) return Precision.NONE;
    if (positional.length() == 9) return Precision.TEN_METRE;
    if (positional.length() == 11) return Precision.ONE_METRE;
    return Precision.INCOMPLETE;
  }

  private static ValidationDetail validationDetail(String positional) {
    if (positional.length() > 5) {
      char eastWest = positional.charAt(5);
      if (eastWest < 'A' || eastWest > 'H') return ValidationDetail.EW_SUBGRID_OUT_OF_RANGE;
    }
    if (positional.length() > 6) {
      char northSouth = positional.charAt(6);
      if (northSouth < 'A' || northSouth > 'E') return ValidationDetail.NS_SUBGRID_OUT_OF_RANGE;
    }
    return null;
  }

  private static String normalizeRaw(String raw) {
    String value = raw.trim();
    if (value.length() >= 2 && value.charAt(0) == '(' && value.charAt(value.length() - 1) == ')') {
      value = value.substring(1, value.length() - 1).trim();
    }
    value = value.replace("\r", "").replace("\n", "");
    StringBuilder normalized = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!Character.isWhitespace(character)) normalized.append(character);
    }
    return normalized.toString().toUpperCase(Locale.ROOT);
  }

  private static boolean isPositionalPrefix(String value) {
    if (value.length() > 11) return false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (index == 0 || index == 5 || index == 6) {
        if (character < 'A' || character > 'Z') return false;
      } else if (character < '0' || character > '9') {
        return false;
      }
    }
    return true;
  }

  private static SplitParts split(String positional) {
    return new SplitParts(
        slice(positional, 0, 1),
        slice(positional, 1, 5),
        slice(positional, 5, 7),
        slice(positional, 7, 11));
  }

  private static String slice(String value, int start, int end) {
    if (value.length() <= start) return "";
    return value.substring(start, Math.min(value.length(), end));
  }

  private static ProjectionFailure splitFailure(SplitParts parts) {
    if (!validLetters(parts.region, 1)
        || !validDigits(parts.subregion, 4)
        || !validLetters(parts.subgrid, 2)
        || !validDigits(parts.precisionDigits, 4)) {
      return ProjectionFailure.SPLIT_INVALID_CHARACTER;
    }
    if ((!parts.subregion.isEmpty() && parts.region.length() != 1)
        || (!parts.subgrid.isEmpty() && parts.subregion.length() != 4)
        || (!parts.precisionDigits.isEmpty() && parts.subgrid.length() != 2)) {
      return ProjectionFailure.SPLIT_HAS_GAP;
    }
    return null;
  }

  private static String acceptLetters(String value, int limit) {
    String safe = value == null ? "" : value;
    if (!validLetters(safe, limit)) return null;
    return safe.toUpperCase(Locale.ROOT);
  }

  private static String acceptDigits(String value, int limit) {
    String safe = value == null ? "" : value;
    return validDigits(safe, limit) ? safe : null;
  }

  private static boolean validLetters(String value, int limit) {
    if (value.length() > limit) return false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z'))) {
        return false;
      }
    }
    return true;
  }

  private static boolean validDigits(String value, int limit) {
    if (value.length() > limit) return false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character < '0' || character > '9') return false;
    }
    return true;
  }

  private static final class Evaluation {
    final TaiwanEntryController.Validation validation;
    final ParseResult.Reason parseReason;
    final ValidationDetail validationDetail;
    final Precision precision;
    final Wgs84 resolved;

    Evaluation(
        TaiwanEntryController.Validation validation,
        ParseResult.Reason parseReason,
        ValidationDetail validationDetail,
        Precision precision,
        Wgs84 resolved) {
      this.validation = validation;
      this.parseReason = parseReason;
      this.validationDetail = validationDetail;
      this.precision = precision;
      this.resolved = resolved;
    }
  }
}
