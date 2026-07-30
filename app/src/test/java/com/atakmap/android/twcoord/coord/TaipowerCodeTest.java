package com.atakmap.android.twcoord.coord;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public final class TaipowerCodeTest {

  @Test
  public void constructorAcceptsCanonicalAaAndHeBoundaries() {
    assertThatCode(() -> code('A', 'A')).doesNotThrowAnyException();
    assertThatCode(() -> code('H', 'E')).doesNotThrowAnyException();
  }

  @Test
  public void constructorRejectsEastWestIAndJ() {
    for (char value : Feature014TaipowerFixtures.INVALID_EAST_WEST_LETTERS.toCharArray()) {
      assertThatThrownBy(() -> code(value, 'A'))
          .as("east-west %s", value)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void constructorRejectsNorthSouthFThroughJ() {
    for (char value : Feature014TaipowerFixtures.INVALID_NORTH_SOUTH_LETTERS.toCharArray()) {
      assertThatThrownBy(() -> code('A', value))
          .as("north-south %s", value)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private static TaipowerCode code(char eastWest, char northSouth) {
    return new TaipowerCode('H', 7509, eastWest, northSouth, 4, 0, 1, 6);
  }
}
