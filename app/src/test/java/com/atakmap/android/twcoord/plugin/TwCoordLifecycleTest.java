package com.atakmap.android.twcoord.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class TwCoordLifecycleTest {

  @Test
  public void publicToolbarItems_containsOnlyTwCoordinates() {
    assertThat(TwCoordLifecycle.publicToolbarItemTypes()).containsExactly(TwCoordTool.class);
  }
}
