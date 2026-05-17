package com.atakmap.android.twcoord.gotopage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.Test;

/**
 * v2 enum contract per [contracts/marker-mode-v2.md § Test contract]. CUSTOM_ICON is the 9th value;
 * eight existing values remain bound to their feature-002 CoT types.
 */
public final class MarkerModeV2Test {

  @Test
  public void customIcon_cotType_isGenericSpotMapPin() {
    assertThat(MarkerMode.CUSTOM_ICON.cotType()).isEqualTo("b-m-p-s-m");
  }

  @Test
  public void customIcon_dropsMarker() {
    assertThat(MarkerMode.CUSTOM_ICON.dropsMarker()).isTrue();
  }

  @Test
  public void customIcon_requiresIconPath() {
    assertThat(MarkerMode.CUSTOM_ICON.requiresIconPath()).isTrue();
    for (MarkerMode m : MarkerMode.values()) {
      if (m != MarkerMode.CUSTOM_ICON) {
        assertThat(m.requiresIconPath()).as("%s.requiresIconPath()", m).isFalse();
      }
    }
  }

  @Test
  public void customIcon_isCustomIcon() {
    assertThat(MarkerMode.CUSTOM_ICON.isCustomIcon()).isTrue();
    for (MarkerMode m : MarkerMode.values()) {
      if (m != MarkerMode.CUSTOM_ICON) {
        assertThat(m.isCustomIcon()).as("%s.isCustomIcon()", m).isFalse();
      }
    }
  }

  @Test
  public void persistedNameRoundTrip() {
    assertThat(MarkerMode.valueOf("CUSTOM_ICON")).isSameAs(MarkerMode.CUSTOM_ICON);
  }

  @Test
  public void dropsMarker_count_now_eight() {
    long droppingModes = Arrays.stream(MarkerMode.values()).filter(MarkerMode::dropsMarker).count();
    assertThat(droppingModes).isEqualTo(8L);
  }
}
