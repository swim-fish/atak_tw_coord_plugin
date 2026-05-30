package com.atakmap.android.twcoord.address.forward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.Test;

/** Pure-logic checks for {@link CompassDirection} (bearing → 16-point + arrow rotation). */
public final class CompassDirectionTest {

  @Test
  public void dueNorthEastSouthWest() {
    // From the equator/prime-meridian origin, step a small amount each way.
    assertThat(CompassDirection.abbrev16(CompassDirection.bearingDegrees(0, 0, 1, 0)))
        .isEqualTo("N");
    assertThat(CompassDirection.abbrev16(CompassDirection.bearingDegrees(0, 0, 0, 1)))
        .isEqualTo("E");
    assertThat(CompassDirection.abbrev16(CompassDirection.bearingDegrees(0, 0, -1, 0)))
        .isEqualTo("S");
    assertThat(CompassDirection.abbrev16(CompassDirection.bearingDegrees(0, 0, 0, -1)))
        .isEqualTo("W");
  }

  @Test
  public void intercardinalIsNE() {
    double b = CompassDirection.bearingDegrees(0, 0, 1, 1); // NE-ish near the equator
    assertThat(CompassDirection.abbrev16(b)).isEqualTo("NE");
    assertThat(b).isCloseTo(45.0, within(1.0));
  }

  @Test
  public void point16IndexAndRotationWrap() {
    assertThat(CompassDirection.point16Index(0)).isEqualTo(0); // N
    assertThat(CompassDirection.point16Index(22.5)).isEqualTo(1); // NNE
    assertThat(CompassDirection.point16Index(360)).isEqualTo(0); // wraps back to N
    assertThat(CompassDirection.point16Index(-22.5)).isEqualTo(15); // NNW
    assertThat(CompassDirection.arrowRotation16(90)).isEqualTo(90f); // E → 4*22.5
  }

  @Test
  public void identicalPointIsZeroNoThrow() {
    assertThat(CompassDirection.bearingDegrees(24.1, 120.6, 24.1, 120.6)).isEqualTo(0.0);
    assertThat(CompassDirection.abbrev16(0)).isEqualTo("N");
  }
}
