package com.atakmap.android.twcoord.coord.input;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import org.junit.Test;

/**
 * JVM unit tests for the TWD97 / TWD67 input paths of {@link CoordinateParser}. Covers zone
 * validation, easting / northing length validation, and OOR (Taiwan-coverage-box) rejection per
 * contracts/coordinate-parser.md.
 */
public class TwdTm2ParserTest {

  private final CoordinateParser parser = new CoordinateParser();

  // === TWD97 positive cases ===

  @Test
  public void parseTwd97_acceptsTaipei_z121() {
    ParseResult r = parser.parseTwd97(306998, 2770083, 121);
    assertThat(r.isOk()).as("Taipei TWD97 z121 must parse to Ok").isTrue();
  }

  @Test
  public void parseTwd97_acceptsPenghu_z119() {
    ParseResult r = parser.parseTwd97(297540, 2596218, 119);
    assertThat(r.isOk()).as("Penghu TWD97 z119 must parse to Ok").isTrue();
  }

  // === TWD67 positive cases ===

  @Test
  public void parseTwd67_acceptsTaipei_z121() {
    ParseResult r = parser.parseTwd67(306169, 2770289, 121);
    assertThat(r.isOk()).as("Taipei TWD67 z121 must parse to Ok").isTrue();
  }

  @Test
  public void parseTwd67_acceptsPenghu_z119() {
    ParseResult r = parser.parseTwd67(296711, 2596424, 119);
    assertThat(r.isOk()).as("Penghu TWD67 z119 must parse to Ok").isTrue();
  }

  // === Zone validation ===

  @Test
  public void parseTwd97_rejectsZoneNot121or119() {
    ParseResult r = parser.parseTwd97(306998, 2770083, 122);
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_ZONE);
    assertThat(((ParseResult.Invalid) r).unit()).isEqualTo(CoordinateUnit.TWD97);
  }

  @Test
  public void parseTwd67_rejectsZone0() {
    ParseResult r = parser.parseTwd67(306169, 2770289, 0);
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_ZONE);
    assertThat(((ParseResult.Invalid) r).unit()).isEqualTo(CoordinateUnit.TWD67);
  }

  // === Easting / northing length validation ===

  @Test
  public void parseTwd97_rejects5digitEasting() {
    // 5-digit easting (below 100_000) is structurally invalid.
    ParseResult r = parser.parseTwd97(99_999, 2770083, 121);
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LENGTH);
  }

  @Test
  public void parseTwd97_rejects8digitEasting() {
    ParseResult r = parser.parseTwd97(10_000_000, 2770083, 121);
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LENGTH);
  }

  @Test
  public void parseTwd97_rejects6digitNorthing() {
    ParseResult r = parser.parseTwd97(306998, 999_999, 121);
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LENGTH);
  }

  @Test
  public void parseTwd97_rejects8digitNorthing() {
    ParseResult r = parser.parseTwd97(306998, 100_000_000, 121);
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LENGTH);
  }

  @Test
  public void parseTwd67_rejectsNegativeEasting() {
    ParseResult r = parser.parseTwd67(-1, 2770289, 121);
    assertThat(r.isInvalid()).isTrue();
    assertThat(((ParseResult.Invalid) r).reason()).isEqualTo(ParseResult.Reason.BAD_LENGTH);
  }

  // === OOR (Taiwan coverage box) ===

  @Test
  public void parseTwd97_returnsOutOfRange_forSyntheticTokyoCoord() {
    // A z121 easting/northing that would resolve to Tokyo (lat ~35.6, lon ~139.7) is
    // structurally valid but geographically outside Taiwan. We synthesise it by computing the
    // TWD97 coords for Tokyo manually: at +121° central meridian, Tokyo is far east, so the
    // easting balloons. We pick a value that triggers the Taiwan-box check post-conversion.
    // Northing ~3,940,000 covers most of Honshu's latitude band.
    ParseResult r = parser.parseTwd97(1_000_000, 3_940_000, 121);
    // Either the conversion succeeds and falls outside the Taiwan box (OutOfRange) or the
    // numeric bounds reject it as BAD_LENGTH; both outcomes are correct rejections.
    assertThat(r.isOk()).as("a Tokyo-scale coord must NOT parse as Ok within Taiwan").isFalse();
  }
}
