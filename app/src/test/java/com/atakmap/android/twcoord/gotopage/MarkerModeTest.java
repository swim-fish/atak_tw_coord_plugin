package com.atakmap.android.twcoord.gotopage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.Test;

/**
 * Sanity contract for {@link MarkerMode} after the ADR-0011 D8 (Option B) refactor: the speculative
 * {@code CUSTOM_ICON} enum value was removed and custom-icon drops are now handled out-of-band by
 * the ATAK-picker delegation button. Exactly 7 of the 8 surviving modes drop a marker (every value
 * except {@link MarkerMode#MOVE_ONLY}).
 */
public final class MarkerModeTest {

  @Test
  public void enumValueCount_isEight() {
    assertThat(MarkerMode.values()).hasSize(8);
  }

  @Test
  public void dropsMarker_isTrueForEveryNonMoveOnlyMode() {
    for (MarkerMode m : MarkerMode.values()) {
      if (m == MarkerMode.MOVE_ONLY) {
        assertThat(m.dropsMarker()).as("%s.dropsMarker()", m).isFalse();
        assertThat(m.cotType()).as("%s.cotType()", m).isNull();
      } else {
        assertThat(m.dropsMarker()).as("%s.dropsMarker()", m).isTrue();
        assertThat(m.cotType()).as("%s.cotType()", m).isNotEmpty();
      }
    }
  }

  @Test
  public void dropsMarker_countIsSeven() {
    long droppingModes = Arrays.stream(MarkerMode.values()).filter(MarkerMode::dropsMarker).count();
    assertThat(droppingModes).isEqualTo(7L);
  }

  @Test
  public void missionPointCotType_matchesAtakNativeGoToTool() {
    // ADR-0011 D6: MISSION_POINT uses b-m-p-w-GOTO so the dropped marker is indistinguishable
    // from one created by ATAK's native GoToMapTool. Regressions here would degrade the UX.
    assertThat(MarkerMode.MISSION_POINT.cotType()).isEqualTo("b-m-p-w-GOTO");
  }
}
