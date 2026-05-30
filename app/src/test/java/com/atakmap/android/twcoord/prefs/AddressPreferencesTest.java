package com.atakmap.android.twcoord.prefs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.SharedPreferences;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

/**
 * Feature 004 — three per-row address-display SwitchPreference accessors on {@link
 * PreferenceStore}, plus the snapshot extension in {@link UserPreference}. Uses a Mockito-mocked
 * {@link SharedPreferences} so the test runs on the JVM without Android / Robolectric, sharing the
 * seam {@link PreferenceStoreCustomIconTest} already exercises (the package-private {@code
 * PreferenceStore(SharedPreferences)} constructor — hence this test lives in the {@code prefs/}
 * package rather than {@code address/}; plan's structure listed it under {@code address/} but the
 * seam constraint dictates the actual location).
 */
public final class AddressPreferencesTest {

  private SharedPreferences sp;
  private SharedPreferences.Editor editor;
  private PreferenceStore prefs;
  private AtomicReference<SharedPreferences.OnSharedPreferenceChangeListener> registered;

  @Before
  public void setUp() {
    sp = mock(SharedPreferences.class);
    editor = mock(SharedPreferences.Editor.class);
    when(sp.edit()).thenReturn(editor);
    when(editor.putString(anyString(), anyString())).thenReturn(editor);
    when(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor);
    when(editor.putLong(anyString(), anyLong())).thenReturn(editor);

    // Capture the listener PreferenceStore registers in its constructor so we can simulate a
    // pref change in spListenerFiresFireAllOnNewKeys.
    registered = new AtomicReference<>();
    doAnswer(
            inv -> {
              registered.set(inv.getArgument(0));
              return null;
            })
        .when(sp)
        .registerOnSharedPreferenceChangeListener(
            any(SharedPreferences.OnSharedPreferenceChangeListener.class));

    // Default stubs for snapshot()'s read{Unit,Language,Stale} so any test that triggers
    // fireAll() (which calls snapshot()) does not NPE on unstubbed SP reads. Per-test stubs
    // can override these.
    when(sp.getString(eq(PreferenceStore.KEY_COORD_UNIT), anyString())).thenReturn("TWD97");
    when(sp.getString(eq(PreferenceStore.KEY_UI_LANGUAGE), anyString())).thenReturn("SYSTEM");
    when(sp.getLong(eq(PreferenceStore.KEY_STALE_THRESHOLD), anyLong())).thenReturn(10_000L);

    prefs = new PreferenceStore(sp);
  }

  @Test
  public void defaultsAreAllFalse() {
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_ME), eq(false))).thenReturn(false);
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_TARGET), eq(false))).thenReturn(false);
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_MAP), eq(false))).thenReturn(false);

    assertThat(prefs.getAddressRowMe()).isFalse();
    assertThat(prefs.getAddressRowTarget()).isFalse();
    assertThat(prefs.getAddressRowMap()).isFalse();
  }

  @Test
  public void gettersAndSettersRoundTrip() {
    // Round-trip ME = true.
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_ME), eq(false))).thenReturn(true);
    assertThat(prefs.getAddressRowMe()).isTrue();
    prefs.setAddressRowMe(true);
    verify(editor).putBoolean(PreferenceStore.KEY_ADDRESS_ROW_ME, true);

    // Round-trip TGT = true.
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_TARGET), eq(false))).thenReturn(true);
    assertThat(prefs.getAddressRowTarget()).isTrue();
    prefs.setAddressRowTarget(true);
    verify(editor).putBoolean(PreferenceStore.KEY_ADDRESS_ROW_TARGET, true);

    // Round-trip MAP = false (explicit write-through, even when value matches the default).
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_MAP), eq(false))).thenReturn(false);
    assertThat(prefs.getAddressRowMap()).isFalse();
    prefs.setAddressRowMap(false);
    verify(editor).putBoolean(PreferenceStore.KEY_ADDRESS_ROW_MAP, false);
  }

  @Test
  public void spListenerFiresFireAllOnNewKeys() {
    AtomicInteger count = new AtomicInteger();
    prefs.registerOnChange(snap -> count.incrementAndGet());

    SharedPreferences.OnSharedPreferenceChangeListener inner = registered.get();
    assertThat(inner)
        .as("PreferenceStore must register an OnSharedPreferenceChangeListener")
        .isNotNull();

    inner.onSharedPreferenceChanged(sp, PreferenceStore.KEY_ADDRESS_ROW_ME);
    inner.onSharedPreferenceChanged(sp, PreferenceStore.KEY_ADDRESS_ROW_TARGET);
    inner.onSharedPreferenceChanged(sp, PreferenceStore.KEY_ADDRESS_ROW_MAP);

    assertThat(count.get()).as("fireAll() must fire once per address pref change").isEqualTo(3);

    // An unrelated key MUST NOT fire fireAll().
    inner.onSharedPreferenceChanged(sp, "pref_some_unrelated_thing");
    assertThat(count.get()).isEqualTo(3);
  }

  @Test
  public void userPreferenceSnapshotCarriesNewBooleans() {
    when(sp.getString(eq(PreferenceStore.KEY_COORD_UNIT), anyString())).thenReturn("TWD97");
    when(sp.getString(eq(PreferenceStore.KEY_UI_LANGUAGE), anyString())).thenReturn("SYSTEM");
    when(sp.getLong(eq(PreferenceStore.KEY_STALE_THRESHOLD), anyLong())).thenReturn(10_000L);
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_ME), eq(false))).thenReturn(true);
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_TARGET), eq(false))).thenReturn(false);
    when(sp.getBoolean(eq(PreferenceStore.KEY_ADDRESS_ROW_MAP), eq(false))).thenReturn(true);

    UserPreference snap = prefs.snapshot();
    assertThat(snap.addressRowMe()).isTrue();
    assertThat(snap.addressRowTarget()).isFalse();
    assertThat(snap.addressRowMap()).isTrue();
  }
}
