package com.atakmap.android.twcoord.prefs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import com.atakmap.android.twcoord.gotopage.MarkerMode;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the feature-003 marker-mode persistence additions to {@link PreferenceStore}. Uses
 * a Mockito-mocked {@link SharedPreferences} so the test runs on the JVM without
 * Android/Robolectric.
 *
 * <p>The {@link PreferenceStore} package-private constructor accepting a {@code SharedPreferences}
 * directly is the test seam — production code still calls {@code new PreferenceStore(Context)}.
 *
 * <p>Pre-Option-B (ADR-0011 D8) this class also covered the {@code KEY_GOTO_LAST_ICONSET_PATH}
 * accessors + the {@code clearCustomIconSelectionAtomic} helper. Those were removed alongside
 * {@link com.atakmap.android.twcoord.gotopage.MarkerMode#values() the CUSTOM_ICON enum value} and
 * the in-page picker dialog when the custom-icon flow was redirected to ATAK's native
 * EnterLocationDropDownReceiver.
 */
public final class PreferenceStoreCustomIconTest {

  private SharedPreferences sp;
  private SharedPreferences.Editor editor;
  private PreferenceStore prefs;

  @Before
  public void setUp() {
    sp = mock(SharedPreferences.class);
    editor = mock(SharedPreferences.Editor.class);
    when(sp.edit()).thenReturn(editor);
    when(editor.putString(anyString(), anyString())).thenReturn(editor);
    when(editor.remove(anyString())).thenReturn(editor);
    prefs = new PreferenceStore(sp);
  }

  @Test
  public void markerMode_roundTrip() {
    when(sp.getString(eq(PreferenceStore.KEY_GOTO_MARKER_MODE), anyString()))
        .thenReturn("WAYPOINT");

    assertThat(prefs.getGotoMarkerMode()).isSameAs(MarkerMode.WAYPOINT);

    prefs.setGotoMarkerMode(MarkerMode.MISSION_POINT);
    verify(editor).putString(PreferenceStore.KEY_GOTO_MARKER_MODE, "MISSION_POINT");
  }

  @Test
  public void markerMode_defaultsToMoveOnly_whenKeyAbsent() {
    when(sp.getString(eq(PreferenceStore.KEY_GOTO_MARKER_MODE), anyString()))
        .thenAnswer(inv -> inv.getArgument(1));

    assertThat(prefs.getGotoMarkerMode()).isSameAs(MarkerMode.MOVE_ONLY);
  }

  @Test
  public void markerMode_corruptValueFallsBackToMoveOnly() {
    when(sp.getString(eq(PreferenceStore.KEY_GOTO_MARKER_MODE), anyString()))
        .thenReturn("NOT_A_REAL_MODE");

    assertThat(prefs.getGotoMarkerMode()).isSameAs(MarkerMode.MOVE_ONLY);
  }

  /**
   * Forward-compatibility: an SP value of "CUSTOM_ICON" left behind by a pre-Option-B install must
   * fall back to MOVE_ONLY instead of throwing, since the enum value was removed in ADR-0011 D8.
   */
  @Test
  public void markerMode_legacyCustomIconValueFallsBackToMoveOnly() {
    when(sp.getString(eq(PreferenceStore.KEY_GOTO_MARKER_MODE), anyString()))
        .thenReturn("CUSTOM_ICON");

    assertThat(prefs.getGotoMarkerMode()).isSameAs(MarkerMode.MOVE_ONLY);
  }
}
