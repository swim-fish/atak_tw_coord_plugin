package com.atakmap.android.twcoord.prefs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.nativeentry.TaipowerInputMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public final class PreferenceStoreNativeEntryTest {

  private SharedPreferences sharedPreferences;
  private SharedPreferences.Editor editor;
  private PreferenceStore store;

  @Before
  public void setUp() {
    sharedPreferences = mock(SharedPreferences.class);
    editor = mock(SharedPreferences.Editor.class);
    when(sharedPreferences.edit()).thenReturn(editor);
    when(editor.putString(anyString(), anyString())).thenReturn(editor);
    store = new PreferenceStore(sharedPreferences);
  }

  @Test
  public void nativeEntryUnit_defaultsToTaipower() {
    when(sharedPreferences.getString(eq(PreferenceStore.KEY_NATIVE_ENTRY_LAST_UNIT), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));

    assertThat(store.getNativeEntryLastUnit()).isSameAs(CoordinateUnit.TAIPOWER);
  }

  @Test
  public void nativeEntryUnit_corruptValueFallsBackToTaipower() {
    when(sharedPreferences.getString(eq(PreferenceStore.KEY_NATIVE_ENTRY_LAST_UNIT), anyString()))
        .thenReturn("NOT_A_COORDINATE_UNIT");

    assertThat(store.getNativeEntryLastUnit()).isSameAs(CoordinateUnit.TAIPOWER);
  }

  @Test
  public void nativeEntryUnit_writesOnlyItsOwnKey() {
    store.setNativeEntryLastUnit(CoordinateUnit.TWD67);

    verify(editor).putString(PreferenceStore.KEY_NATIVE_ENTRY_LAST_UNIT, "TWD67");
    verify(editor, never()).putString(eq(PreferenceStore.KEY_GOTO_LAST_UNIT), anyString());
    verify(editor).apply();
  }

  @Test
  public void taipowerInputModeDefaultsAndCorruptValuesFallBackToSingleField() {
    when(sharedPreferences.getString(
            eq(PreferenceStore.KEY_NATIVE_ENTRY_TAIPOWER_MODE), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    assertThat(store.getNativeEntryTaipowerMode()).isSameAs(TaipowerInputMode.SINGLE_FIELD);

    when(sharedPreferences.getString(
            eq(PreferenceStore.KEY_NATIVE_ENTRY_TAIPOWER_MODE), anyString()))
        .thenReturn("");
    assertThat(store.getNativeEntryTaipowerMode()).isSameAs(TaipowerInputMode.SINGLE_FIELD);

    when(sharedPreferences.getString(
            eq(PreferenceStore.KEY_NATIVE_ENTRY_TAIPOWER_MODE), anyString()))
        .thenReturn("FUTURE_MODE");
    assertThat(store.getNativeEntryTaipowerMode()).isSameAs(TaipowerInputMode.SINGLE_FIELD);
  }

  @Test
  public void taipowerInputModeSavesAndReloadsOnlyItsPluginOwnedKey() {
    store.setNativeEntryTaipowerMode(TaipowerInputMode.SPLIT_FIELDS);

    verify(editor).putString(PreferenceStore.KEY_NATIVE_ENTRY_TAIPOWER_MODE, "SPLIT_FIELDS");
    verify(editor, never()).putString(eq("coordview.formattedMGRS"), anyString());
    verify(editor, never()).putString(eq(PreferenceStore.KEY_NATIVE_ENTRY_LAST_UNIT), anyString());
    verify(editor, never()).putString(eq(PreferenceStore.KEY_GOTO_LAST_TAIPOWER), anyString());
    verify(editor).apply();

    when(sharedPreferences.getString(
            eq(PreferenceStore.KEY_NATIVE_ENTRY_TAIPOWER_MODE), anyString()))
        .thenReturn("SPLIT_FIELDS");
    assertThat(new PreferenceStore(sharedPreferences).getNativeEntryTaipowerMode())
        .isSameAs(TaipowerInputMode.SPLIT_FIELDS);
  }

  @Test
  public void nativeSelectionPreservesAdvancedGotoUpgradeFixtureByteForByte() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(PreferenceStore.KEY_GOTO_LAST_UNIT, "TWD67");
    values.put(PreferenceStore.KEY_GOTO_LAST_TAIPOWER, "A1234AB5678");
    values.put(PreferenceStore.KEY_GOTO_LAST_TWD97_E, "307123");
    values.put(PreferenceStore.KEY_GOTO_LAST_TWD97_N, "2654123");
    values.put(PreferenceStore.KEY_GOTO_LAST_TWD97_ZONE, "119");
    values.put(PreferenceStore.KEY_GOTO_LAST_TWD67_E, "306321");
    values.put(PreferenceStore.KEY_GOTO_LAST_TWD67_N, "2653921");
    values.put(PreferenceStore.KEY_GOTO_LAST_TWD67_ZONE, "121");
    values.put(
        PreferenceStore.KEY_GOTO_RECENT_JSON,
        "[\"r0\",\"r1\",\"r2\",\"r3\",\"r4\",\"r5\",\"r6\",\"r7\",\"r8\",\"r9\"]");
    values.put(PreferenceStore.KEY_GOTO_MARKER_MODE, "CREATE_AND_MOVE");
    Map<String, String> before = new LinkedHashMap<>(values);
    when(sharedPreferences.getString(anyString(), anyString()))
        .thenAnswer(
            invocation ->
                values.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
    org.mockito.Mockito.doAnswer(
            invocation -> {
              values.put(invocation.getArgument(0), invocation.getArgument(1));
              return editor;
            })
        .when(editor)
        .putString(anyString(), anyString());

    store.setNativeEntryLastUnit(CoordinateUnit.TWD97);

    assertThat(values).containsAllEntriesOf(before);
    assertThat(values.get(PreferenceStore.KEY_NATIVE_ENTRY_LAST_UNIT)).isEqualTo("TWD97");
  }
}
