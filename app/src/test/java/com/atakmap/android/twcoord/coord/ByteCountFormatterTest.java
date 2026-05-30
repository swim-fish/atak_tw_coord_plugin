package com.atakmap.android.twcoord.coord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/** Feature 007 US3 — {@link ByteCountFormatter} boundary + rounding cases. */
public class ByteCountFormatterTest {

  @Test
  public void zeroAndBytes() {
    assertThat(ByteCountFormatter.format(0)).isEqualTo("0 B");
    assertThat(ByteCountFormatter.format(1)).isEqualTo("1 B");
    assertThat(ByteCountFormatter.format(1023)).isEqualTo("1023 B");
  }

  @Test
  public void kilobytesUseOneDecimal() {
    assertThat(ByteCountFormatter.format(1024)).isEqualTo("1.0 KB");
    assertThat(ByteCountFormatter.format(1536)).isEqualTo("1.5 KB");
  }

  @Test
  public void megabytes() {
    assertThat(ByteCountFormatter.format(12_900_000L)).isEqualTo("12.3 MB");
    assertThat(ByteCountFormatter.format(324L * 1024 * 1024)).isEqualTo("324.0 MB");
  }

  @Test
  public void gigabytes() {
    assertThat(ByteCountFormatter.format(2L * 1024 * 1024 * 1024)).isEqualTo("2.0 GB");
  }

  @Test
  public void negativeClampsToZero() {
    assertThat(ByteCountFormatter.format(-5)).isEqualTo("0 B");
  }
}
