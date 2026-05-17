package com.atakmap.android.twcoord.prefs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import com.atakmap.android.twcoord.gotopage.MarkerMode;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the feature-003 additions to {@link PreferenceStore}: marker-mode persistence and
 * iconset-path persistence + atomic clear. Uses a Mockito-mocked {@link SharedPreferences} so the
 * test runs on the JVM without Android/Robolectric.
 *
 * <p>The {@link PreferenceStore} package-private constructor accepting a {@code SharedPreferences}
 * directly is the test seam — production code still calls {@code new PreferenceStore(Context)}.
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
        .thenReturn("CUSTOM_ICON");

    assertThat(prefs.getGotoMarkerMode()).isSameAs(MarkerMode.CUSTOM_ICON);

    prefs.setGotoMarkerMode(MarkerMode.WAYPOINT);
    verify(editor).putString(PreferenceStore.KEY_GOTO_MARKER_MODE, "WAYPOINT");
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

  @Test
  public void iconsetPath_roundTrip() {
    when(sp.getString(eq(PreferenceStore.KEY_GOTO_LAST_ICONSET_PATH), eq(null)))
        .thenReturn("uid-X/grp/icon.png");

    assertThat(prefs.getGotoLastIconsetPath()).isEqualTo("uid-X/grp/icon.png");

    prefs.setGotoLastIconsetPath("uid-Y/grp2/other.png");
    verify(editor).putString(PreferenceStore.KEY_GOTO_LAST_ICONSET_PATH, "uid-Y/grp2/other.png");
  }

  @Test
  public void iconsetPath_defaultsToNull() {
    when(sp.getString(eq(PreferenceStore.KEY_GOTO_LAST_ICONSET_PATH), eq(null))).thenReturn(null);

    assertThat(prefs.getGotoLastIconsetPath()).isNull();
  }

  @Test
  public void clearCustomIconSelectionAtomic_writesMoveOnlyAndRemovesPath_inOneCommit() {
    prefs.clearCustomIconSelectionAtomic();

    // Both mutations issued on the same editor instance, then commit() / apply() called once.
    verify(editor).putString(PreferenceStore.KEY_GOTO_MARKER_MODE, "MOVE_ONLY");
    verify(editor).remove(PreferenceStore.KEY_GOTO_LAST_ICONSET_PATH);
    verify(editor, times(1)).apply();
  }
}
