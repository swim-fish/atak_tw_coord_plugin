package com.atakmap.android.twcoord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import android.content.BroadcastReceiver;
import android.content.Intent;
import com.atakmap.android.twcoord.address.AddressSubsystem;
import com.atakmap.android.twcoord.coord.DisplayLine;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TwCoordMapComponentTargetDismissTest {

  @Test
  public void hostHideDetails_clearsOnlySelectedTargetState() throws Exception {
    TwCoordMapComponent component = new TwCoordMapComponent();
    DisplayLine map = line("MAP", "map");
    DisplayLine me = line("ME", "me");
    DisplayLine target = line("TGT", "target");
    set(component, "lastMapLine", map);
    set(component, "lastMeLine", me);
    set(component, "lastTargetLine", target);

    BroadcastReceiver receiver = (BroadcastReceiver) get(component, "targetDismissReceiver");
    receiver.onReceive(null, new Intent("com.atakmap.android.maps.HIDE_DETAILS"));

    assertThat(get(component, "lastMapLine")).isSameAs(map);
    assertThat(get(component, "lastMeLine")).isSameAs(me);
    assertThat(get(component, "lastTargetLine")).isNull();
  }

  @Test
  public void hostHideDetails_containsOrdinaryTargetCleanupFailure() throws Exception {
    TwCoordMapComponent component = new TwCoordMapComponent();
    AddressSubsystem subsystem = mock(AddressSubsystem.class);
    doThrow(new IllegalStateException("ordinary plugin failure"))
        .when(subsystem)
        .clearRow(AddressSubsystem.Row.TGT);
    set(component, "addressSubsystem", subsystem);
    set(component, "lastTargetLine", line("TGT", "target"));

    assertThatCode(() -> dismissTarget(component)).doesNotThrowAnyException();
    assertThat(get(component, "lastTargetLine")).isNull();
  }

  @Test
  public void hostHideDetails_rethrowsFatalAddressCleanupFailure() throws Exception {
    TwCoordMapComponent component = new TwCoordMapComponent();
    AddressSubsystem subsystem = mock(AddressSubsystem.class);
    doThrow(new TestVirtualMachineError()).when(subsystem).clearRow(AddressSubsystem.Row.TGT);
    set(component, "addressSubsystem", subsystem);

    assertThatThrownBy(() -> dismissTarget(component)).isInstanceOf(TestVirtualMachineError.class);
  }

  @Test
  public void hostHideDetails_rethrowsFatalWidgetCleanupFailure() throws Exception {
    TwCoordMapComponent component = new TwCoordMapComponent();
    TwCoordWidget widget = mock(TwCoordWidget.class);
    doThrow(new ThreadDeath()).when(widget).clearTarget();
    set(component, "widget", widget);

    assertThatThrownBy(() -> dismissTarget(component)).isInstanceOf(ThreadDeath.class);
  }

  private static void dismissTarget(TwCoordMapComponent component) throws Exception {
    BroadcastReceiver receiver = (BroadcastReceiver) get(component, "targetDismissReceiver");
    receiver.onReceive(null, new Intent("com.atakmap.android.maps.HIDE_DETAILS"));
  }

  private static DisplayLine line(String label, String value) {
    return new DisplayLine(label, "TPC", value, "", DisplayLine.State.OK);
  }

  private static Object get(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  private static void set(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static final class TestVirtualMachineError extends VirtualMachineError {}
}
