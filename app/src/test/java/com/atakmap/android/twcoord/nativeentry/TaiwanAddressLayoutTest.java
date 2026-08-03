package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.plugin.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class TaiwanAddressLayoutTest {

  @Test
  public void structuredModeHasTwoEqualDdSizedRowsOneScrollOwnerAndFortyEightDpControl() {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanCoordinateEntryPane pane = pane(context);
    View root = pane.getView();
    root.findViewById(R.id.native_entry_system_address).performClick();
    Button mode = root.findViewById(R.id.native_entry_address_mode);

    assertThat(countType(root, ScrollView.class)).isEqualTo(1);
    assertThat(root).isInstanceOf(BoundedPaneScrollView.class);
    assertThat(root.findViewById(R.id.native_entry_system_group).getLayoutParams().height)
        .isEqualTo(dp(context, 48));
    assertThat(((RadioButton) root.findViewById(R.id.native_entry_system_taipower)).getTextSize())
        .isEqualTo(context.getResources().getDimension(R.dimen.native_entry_tab_font));
    assertThat(mode.getLayoutParams().height).isEqualTo(dp(context, 48));
    LinearLayout addressPane = root.findViewById(R.id.native_entry_pane_address);
    LinearLayout addressBody = root.findViewById(R.id.native_entry_address_body);
    LinearLayout addressContent = root.findViewById(R.id.native_entry_address_content);
    LinearLayout addressActions = root.findViewById(R.id.native_entry_address_actions);
    assertThat(addressPane.indexOfChild(addressBody)).isEqualTo(0);
    assertThat(addressBody.indexOfChild(addressContent)).isEqualTo(0);
    assertThat(addressBody.indexOfChild(addressActions)).isEqualTo(1);
    assertThat(((LinearLayout.LayoutParams) addressContent.getLayoutParams()).weight).isEqualTo(8f);
    assertThat(((LinearLayout.LayoutParams) addressActions.getLayoutParams()).weight).isEqualTo(2f);
    assertThat(addressActions.getGravity()).isEqualTo(Gravity.TOP | Gravity.END);
    assertThat(root.findViewById(R.id.native_entry_address_full_row).getVisibility())
        .isEqualTo(View.VISIBLE);
    assertThat(root.findViewById(R.id.native_entry_address_structured).getVisibility())
        .isEqualTo(View.GONE);

    mode.performClick();

    assertThat(root.findViewById(R.id.native_entry_address_full_row).getVisibility())
        .isEqualTo(View.GONE);
    assertThat(root.findViewById(R.id.native_entry_address_structured).getVisibility())
        .isEqualTo(View.VISIBLE);
    LinearLayout structured = root.findViewById(R.id.native_entry_address_structured);
    LinearLayout localityRow = findNamedLayout(root, context, "native_entry_address_locality_row");
    LinearLayout streetRow = findNamedLayout(root, context, "native_entry_address_street_row");
    assertThat(structured.getChildCount()).isEqualTo(2);
    assertThat(structured.indexOfChild(localityRow)).isEqualTo(0);
    assertThat(structured.indexOfChild(streetRow)).isEqualTo(1);
    assertEqualPairRow(
        localityRow, R.id.native_entry_address_county_row, R.id.native_entry_address_district_row);
    assertEqualPairRow(
        streetRow, R.id.native_entry_address_road_row, R.id.native_entry_address_tail_row);
    assertCompactFieldGroup(root, R.id.native_entry_address_county_row);
    assertCompactFieldGroup(root, R.id.native_entry_address_district_row);
    assertCompactFieldGroup(root, R.id.native_entry_address_road_row);
    assertCompactFieldGroup(root, R.id.native_entry_address_tail_row);
    EditText county = root.findViewById(R.id.native_entry_address_county);
    EditText district = root.findViewById(R.id.native_entry_address_district);
    EditText road = root.findViewById(R.id.native_entry_address_road);
    assertThat(county.isFocusable()).isFalse();
    assertThat(county.isClickable()).isTrue();
    assertThat(district.isFocusable()).isFalse();
    assertThat(district.isClickable()).isFalse();
    assertThat(district.isEnabled()).isFalse();
    assertThat(road.getNextFocusDownId()).isEqualTo(R.id.native_entry_address_tail);
    assertThat(road.getNextFocusForwardId()).isEqualTo(R.id.native_entry_address_tail);
  }

  @Test
  public void paneShrinkWrapsShortSystemsAndCompactStructuredAddressBelowCap() {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanCoordinateEntryPane pane = pane(context);
    View root = pane.getView();
    int width = dp(context, 900);
    int maxHeight =
        context.getResources().getDimensionPixelSize(R.dimen.native_entry_pane_max_height);

    measure(root, width);
    assertThat(root.getMeasuredHeight()).isLessThan(maxHeight);

    root.findViewById(R.id.native_entry_system_address).performClick();
    root.findViewById(R.id.native_entry_address_mode).performClick();
    measure(root, width);

    assertThat(root.getMeasuredHeight()).isLessThan(maxHeight);
    assertThat(((ViewGroup) root).getChildAt(0).getMeasuredHeight()).isLessThan(maxHeight);
  }

  @Test
  public void largeFontProjectionRemainsWrapContentAndModeReachable() {
    Context base = RuntimeEnvironment.getApplication();
    Configuration configuration = new Configuration(base.getResources().getConfiguration());
    configuration.fontScale = 2.0f;
    Context scaled = base.createConfigurationContext(configuration);
    TaiwanCoordinateEntryPane pane = pane(scaled);
    View root = pane.getView();
    root.findViewById(R.id.native_entry_system_address).performClick();
    root.findViewById(R.id.native_entry_address_mode).performClick();

    EditText tail = root.findViewById(R.id.native_entry_address_tail);
    assertThat(tail.getLayoutParams().height).isEqualTo(ViewGroup.LayoutParams.WRAP_CONTENT);
    assertThat(tail.getTextSize()).isGreaterThan(0f);
    assertThat(root.findViewById(R.id.native_entry_address_mode).getVisibility())
        .isEqualTo(View.VISIBLE);
    assertThat(root.findViewById(R.id.native_entry_address_mode).isEnabled()).isTrue();

    measure(root, dp(scaled, 600));
    LinearLayout localityRow = findNamedLayout(root, scaled, "native_entry_address_locality_row");
    LinearLayout streetRow = findNamedLayout(root, scaled, "native_entry_address_street_row");
    assertEqualMeasuredWidths(localityRow);
    assertEqualMeasuredWidths(streetRow);
  }

  @Test
  public void modeSwitchUsesReadableAtakPanelTextColors() {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanCoordinateEntryPane pane = pane(context);
    Button mode = pane.getView().findViewById(R.id.native_entry_address_mode);

    assertThat(mode.getCurrentTextColor()).isEqualTo(0xFFFFFFFF);
    assertThat(mode.getTextColors().getColorForState(new int[] {-android.R.attr.state_enabled}, 0))
        .isEqualTo(0x99FFFFFF);
  }

  private static TaiwanCoordinateEntryPane pane(Context context) {
    return new TaiwanCoordinateEntryPane(
        context,
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {}),
        new TaiwanEntryFormatter());
  }

  private static LinearLayout findNamedLayout(View root, Context context, String name) {
    int id = context.getResources().getIdentifier(name, "id", context.getPackageName());
    assertThat(id).as(name + " resource id").isNotZero();
    View view = root.findViewById(id);
    assertThat(view).as(name).isInstanceOf(LinearLayout.class);
    return (LinearLayout) view;
  }

  private static void assertEqualPairRow(LinearLayout row, int firstId, int secondId) {
    assertThat(row.getOrientation()).isEqualTo(LinearLayout.HORIZONTAL);
    assertThat(row.getChildCount()).isEqualTo(2);
    assertThat(row.getChildAt(0).getId()).isEqualTo(firstId);
    assertThat(row.getChildAt(1).getId()).isEqualTo(secondId);
    for (int index = 0; index < row.getChildCount(); index++) {
      LinearLayout.LayoutParams params =
          (LinearLayout.LayoutParams) row.getChildAt(index).getLayoutParams();
      assertThat(params.width).isZero();
      assertThat(params.weight).isEqualTo(1f);
    }
  }

  private static void assertCompactFieldGroup(View root, int rowId) {
    LinearLayout row = root.findViewById(rowId);
    assertThat(row.getChildCount()).isEqualTo(2);
    assertThat(((LinearLayout.LayoutParams) row.getChildAt(0).getLayoutParams()).weight)
        .isEqualTo(3f);
    assertThat(((LinearLayout.LayoutParams) row.getChildAt(1).getLayoutParams()).weight)
        .isEqualTo(7f);
    assertThat(row.getChildAt(0)).isInstanceOf(TextView.class);
    assertThat(row.getChildAt(1)).isInstanceOf(EditText.class);
    assertThat(((TextView) row.getChildAt(0)).getLabelFor()).isEqualTo(row.getChildAt(1).getId());
    assertThat(row.getChildAt(1).getLayoutParams().height)
        .isEqualTo(ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private static void assertEqualMeasuredWidths(LinearLayout row) {
    assertThat(
            Math.abs(row.getChildAt(0).getMeasuredWidth() - row.getChildAt(1).getMeasuredWidth()))
        .isLessThanOrEqualTo(1);
    assertThat(row.getChildAt(0).getMeasuredWidth()).isGreaterThan(0);
  }

  private static int countType(View view, Class<?> type) {
    int count = type.isInstance(view) ? 1 : 0;
    if (!(view instanceof ViewGroup)) return count;
    ViewGroup group = (ViewGroup) view;
    for (int index = 0; index < group.getChildCount(); index++) {
      count += countType(group.getChildAt(index), type);
    }
    return count;
  }

  private static void measure(View view, int width) {
    view.measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }
}
