package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/** Feature 005 polish — covers preset-aware tilde decoration + corrupt-pref fallback. */
public class ConfidenceThresholdsTest {

  // ----------------------------------------------------------------------
  // decorate(...)
  // ----------------------------------------------------------------------

  @Test
  public void tightBucketBelowMediumReturnsUnchanged() {
    assertThat(ConfidenceThresholds.TIGHT.decorate("台中市北區", 5.0)).isEqualTo("台中市北區");
    assertThat(ConfidenceThresholds.TIGHT.decorate("台中市北區", 20.0)).isEqualTo("台中市北區");
  }

  @Test
  public void tightBucketBetweenMediumAndLowAddsSingleTilde() {
    assertThat(ConfidenceThresholds.TIGHT.decorate("台中市北區", 50.0)).isEqualTo("~ 台中市北區");
    assertThat(ConfidenceThresholds.TIGHT.decorate("台中市北區", 100.0)).isEqualTo("~ 台中市北區");
  }

  @Test
  public void tightBucketAboveLowAddsDoubleTilde() {
    assertThat(ConfidenceThresholds.TIGHT.decorate("台中市北區", 100.1)).isEqualTo("~~ 台中市北區");
    assertThat(ConfidenceThresholds.TIGHT.decorate("台中市北區", 500.0)).isEqualTo("~~ 台中市北區");
  }

  @Test
  public void standardBucketUses50And200() {
    assertThat(ConfidenceThresholds.STANDARD.decorate("彰化縣", 49.9)).isEqualTo("彰化縣");
    assertThat(ConfidenceThresholds.STANDARD.decorate("彰化縣", 50.0)).isEqualTo("彰化縣");
    assertThat(ConfidenceThresholds.STANDARD.decorate("彰化縣", 51.0)).isEqualTo("~ 彰化縣");
    assertThat(ConfidenceThresholds.STANDARD.decorate("彰化縣", 200.0)).isEqualTo("~ 彰化縣");
    assertThat(ConfidenceThresholds.STANDARD.decorate("彰化縣", 200.1)).isEqualTo("~~ 彰化縣");
  }

  @Test
  public void looseBucketUses100And500() {
    assertThat(ConfidenceThresholds.LOOSE.decorate("南投縣", 80.0)).isEqualTo("南投縣");
    assertThat(ConfidenceThresholds.LOOSE.decorate("南投縣", 200.0)).isEqualTo("~ 南投縣");
    assertThat(ConfidenceThresholds.LOOSE.decorate("南投縣", 600.0)).isEqualTo("~~ 南投縣");
  }

  @Test
  public void offBucketNeverAddsPrefix() {
    assertThat(ConfidenceThresholds.OFF.decorate("台北市", 0.0)).isEqualTo("台北市");
    assertThat(ConfidenceThresholds.OFF.decorate("台北市", 50.0)).isEqualTo("台北市");
    assertThat(ConfidenceThresholds.OFF.decorate("台北市", 9999.0)).isEqualTo("台北市");
  }

  @Test
  public void unknownDistanceReturnsUnchanged() {
    // -1 is the sentinel for "legacy single-active path didn't compute haversine".
    for (ConfidenceThresholds preset : ConfidenceThresholds.values()) {
      assertThat(preset.decorate("台南市", -1.0)).as("preset=%s", preset).isEqualTo("台南市");
    }
  }

  @Test
  public void nullDisplayNameYieldsEmpty() {
    assertThat(ConfidenceThresholds.TIGHT.decorate(null, 50.0)).isEqualTo("");
    assertThat(ConfidenceThresholds.OFF.decorate(null, -1.0)).isEqualTo("");
  }

  // ----------------------------------------------------------------------
  // fromPrefValue(...)
  // ----------------------------------------------------------------------

  @Test
  public void fromPrefValueRoundTrips() {
    for (ConfidenceThresholds preset : ConfidenceThresholds.values()) {
      assertThat(ConfidenceThresholds.fromPrefValue(preset.name())).isEqualTo(preset);
    }
  }

  @Test
  public void fromPrefValueNullFallsBackToTight() {
    assertThat(ConfidenceThresholds.fromPrefValue(null)).isEqualTo(ConfidenceThresholds.TIGHT);
  }

  @Test
  public void fromPrefValueUnknownFallsBackToTight() {
    assertThat(ConfidenceThresholds.fromPrefValue("SOMETHING_FROM_THE_FUTURE"))
        .isEqualTo(ConfidenceThresholds.TIGHT);
    assertThat(ConfidenceThresholds.fromPrefValue("")).isEqualTo(ConfidenceThresholds.TIGHT);
  }
}
