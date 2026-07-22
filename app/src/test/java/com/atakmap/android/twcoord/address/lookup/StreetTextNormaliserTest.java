package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/** Feature 006 T020 — street-text folding (臺→台, fullwidth digits, 之→-, trim, idempotence). */
public class StreetTextNormaliserTest {

  @Test
  public void foldsTaiToTai() {
    assertThat(StreetTextNormaliser.fold("臺灣大道")).isEqualTo("台灣大道");
  }

  @Test
  public void foldsFullwidthDigits() {
    assertThat(StreetTextNormaliser.fold("中山路２段")).isEqualTo("中山路2段");
    assertThat(StreetTextNormaliser.fold("０１２３４５６７８９")).isEqualTo("0123456789");
  }

  @Test
  public void foldsZhiToHyphen() {
    assertThat(StreetTextNormaliser.fold("２之３")).isEqualTo("2-3");
  }

  @Test
  public void trimsWhitespace() {
    assertThat(StreetTextNormaliser.fold("  中山路  ")).isEqualTo("中山路");
  }

  @Test
  public void isIdempotent() {
    String once = StreetTextNormaliser.fold("臺灣大道２之３號");
    assertThat(StreetTextNormaliser.fold(once)).isEqualTo(once);
    assertThat(once).isEqualTo("台灣大道2-3號");
  }

  @Test
  public void nullSafe() {
    assertThat(StreetTextNormaliser.fold(null)).isEqualTo("");
  }

  @Test
  public void taiVariantRestoresGazettedGlyph() {
    assertThat(StreetTextNormaliser.taiVariant("台灣大道")).isEqualTo("臺灣大道");
    assertThat(StreetTextNormaliser.taiVariant("中山路")).isEqualTo("中山路"); // no 台 → unchanged
  }
}
