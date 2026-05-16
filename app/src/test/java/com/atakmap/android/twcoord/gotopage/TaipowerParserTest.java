package com.atakmap.android.twcoord.gotopage;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import org.junit.Test;

/**
 * JVM unit tests for the Taipower-input path of {@link CoordinateParser}, covering normalisation
 * (case-insensitive, whitespace-tolerant, surrounding-paren-tolerant) and the validation reason
 * codes from contracts/coordinate-parser.md.
 *
 * <p>Constitution Principle II — TDD NON-NEGOTIABLE: these tests are authored before {@link
 * CoordinateParser#parseTaipower(String)} has a real implementation, so they MUST be observed
 * failing before T021/T022 (Phase 3 US1 implementation) lands.
 */
public class TaipowerParserTest {

  private final CoordinateParser parser = new CoordinateParser();

  // ------- positive parses -------

  @Test
  public void parseTaipower_acceptsCanonicalForm_hualienStation_11char() {
    ParseResult r = parser.parseTaipower("H7509 DB4016");
    assertThat(r.isOk()).as("Taipower H7509 DB4016 must round-trip").isTrue();
  }

  @Test
  public void parseTaipower_acceptsNoSpaceForm() {
    ParseResult r = parser.parseTaipower("H7509DB4016");
    assertThat(r.isOk()).isTrue();
  }

  @Test
  public void parseTaipower_acceptsLowerCase() {
    ParseResult r = parser.parseTaipower("h7509 db4016");
    assertThat(r.isOk()).isTrue();
  }

  @Test
  public void parseTaipower_acceptsMixedCase() {
    ParseResult r = parser.parseTaipower("h7509 Db4016");
    assertThat(r.isOk()).isTrue();
  }

  @Test
  public void parseTaipower_acceptsLeadingTrailingWhitespace() {
    ParseResult r = parser.parseTaipower("   H7509 DB4016\n");
    assertThat(r.isOk()).isTrue();
  }

  @Test
  public void parseTaipower_acceptsSurroundingParentheses() {
    ParseResult r = parser.parseTaipower("(H7509 DB4016)");
    assertThat(r.isOk()).isTrue();
  }

  @Test
  public void parseTaipower_acceptsDoubleInternalSpace() {
    ParseResult r = parser.parseTaipower("H7509  DB4016");
    assertThat(r.isOk()).isTrue();
  }

  @Test
  public void parseTaipower_accepts9CharForm() {
    // 9-char form drops the last two digits (10 m precision).
    ParseResult r = parser.parseTaipower("H7509 DB40");
    assertThat(r.isOk()).as("9-char Taipower must parse (10 m precision)").isTrue();
  }

  // ------- negative parses with specific reason codes -------

  @Test
  public void parseTaipower_rejectsEmptyString() {
    ParseResult r = parser.parseTaipower("");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.EMPTY);
  }

  @Test
  public void parseTaipower_rejectsWhitespaceOnly() {
    ParseResult r = parser.parseTaipower("   \t\n");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.EMPTY);
  }

  @Test
  public void parseTaipower_rejects8CharLength() {
    ParseResult r = parser.parseTaipower("H7509DB4");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LENGTH);
  }

  @Test
  public void parseTaipower_rejects10CharLength() {
    ParseResult r = parser.parseTaipower("H7509DB401");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LENGTH);
  }

  @Test
  public void parseTaipower_rejects12CharLength() {
    ParseResult r = parser.parseTaipower("H7509DB401612");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LENGTH);
  }

  @Test
  public void parseTaipower_rejectsZReservedRegionLetter() {
    ParseResult r = parser.parseTaipower("Z7509 DB4016");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.RESERVED_LETTER_YZ);
  }

  @Test
  public void parseTaipower_rejectsYReservedRegionLetter() {
    ParseResult r = parser.parseTaipower("Y7509 DB4016");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.RESERVED_LETTER_YZ);
  }

  @Test
  public void parseTaipower_rejectsHundredMetreLetterOutsideAJ() {
    // 'K' is beyond 'J' for the hundred-metre letter slot.
    ParseResult r = parser.parseTaipower("H7509 KB4016");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LETTER);
  }

  @Test
  public void parseTaipower_rejectsRegionLetterOutsideAX() {
    // '@' is not A..X / Y / Z; should be BAD_LETTER, not RESERVED.
    ParseResult r = parser.parseTaipower("@7509 DB4016");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LETTER);
  }

  @Test
  public void parseTaipower_rejectsNonDigitInSubRegion() {
    ParseResult r = parser.parseTaipower("H75X9 DB4016");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.NON_DIGIT);
  }

  @Test
  public void parseTaipower_rejectsNonDigitInTrailingDigits() {
    ParseResult r = parser.parseTaipower("H7509 DB40X6");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.NON_DIGIT);
  }

  @Test
  public void parseTaipower_invalidResultCarriesTaipowerUnit() {
    ParseResult r = parser.parseTaipower("");
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).unit()).isEqualTo(CoordinateUnit.TAIPOWER);
  }
}
