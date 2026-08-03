package com.atakmap.android.twcoord;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.AddressRowState;
import com.atakmap.android.twcoord.coord.DisplayLine;
import com.atakmap.android.widgets.TextWidget;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TwCoordWidgetTargetDismissTest {

  @Test
  public void clearTarget_hidesOnlyTargetRowsAndAllowsNextSelection() throws Exception {
    TwCoordWidget widget = new TwCoordWidget(null);
    TextWidget mapRow = row();
    TextWidget meRow = row();
    TextWidget targetRow = row();
    TextWidget mapAddressRow = row();
    TextWidget meAddressRow = row();
    TextWidget targetAddressRow = row();
    set(widget, "mapRow", mapRow);
    set(widget, "meRow", meRow);
    set(widget, "targetRow", targetRow);
    set(widget, "mapAddrRow", mapAddressRow);
    set(widget, "meAddrRow", meAddressRow);
    set(widget, "targetAddrRow", targetAddressRow);

    widget.render(line("MAP", "map"), line("ME", "me"), line("TGT", "old target"));
    widget.renderAddresses(
        AddressRowState.text("map address"),
        AddressRowState.text("me address"),
        AddressRowState.text("old target address"));

    widget.clearTarget();

    assertThat(mapRow.isVisible()).isTrue();
    assertThat(meRow.isVisible()).isTrue();
    assertThat(mapAddressRow.isVisible()).isTrue();
    assertThat(meAddressRow.isVisible()).isTrue();
    assertThat(targetRow.isVisible()).isFalse();
    assertThat(targetAddressRow.isVisible()).isFalse();
    assertThat(targetRow.getText()).isEmpty();

    widget.render(null, null, line("TGT", "new target"));
    widget.renderAddresses(null, null, AddressRowState.text("new target address"));

    assertThat(targetRow.isVisible()).isTrue();
    assertThat(targetAddressRow.isVisible()).isTrue();
    assertThat(targetRow.getText()).contains("new target");
    assertThat(targetAddressRow.getText()).isEqualTo("new target address");
  }

  private static TextWidget row() {
    return new TextWidget("", 2);
  }

  private static DisplayLine line(String label, String value) {
    return new DisplayLine(label, "TPC", value, "", DisplayLine.State.OK);
  }

  private static void set(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
