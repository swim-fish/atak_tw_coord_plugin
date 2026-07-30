package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.plugin.R;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class TaiwanSelectorPresentationTest {

  @Test
  public void namedDimensionsExpressExactTouchVisualAndInsetRelationship() {
    Context context = RuntimeEnvironment.getApplication();
    int touch =
        context.getResources().getDimensionPixelSize(R.dimen.native_entry_selector_touch_height);
    int visual =
        context.getResources().getDimensionPixelSize(R.dimen.native_entry_selector_visual_height);
    int inset =
        context.getResources().getDimensionPixelSize(R.dimen.native_entry_selector_vertical_inset);

    assertThat(touch).isEqualTo(dp(context, 48));
    assertThat(visual).isEqualTo(dp(context, 36));
    assertThat(inset).isEqualTo(dp(context, 6));
    assertThat(touch - 2 * inset).isEqualTo(visual);
  }

  @Test
  public void trackAndEveryOptionStateInsetToTheNamedVisualHeight() {
    Context context = RuntimeEnvironment.getApplication();
    int touch =
        context.getResources().getDimensionPixelSize(R.dimen.native_entry_selector_touch_height);
    int visual =
        context.getResources().getDimensionPixelSize(R.dimen.native_entry_selector_visual_height);

    Drawable track = context.getDrawable(R.drawable.native_entry_segment_track);
    track.setBounds(0, 0, dp(context, 200), touch);
    assertThat(track).isInstanceOf(InsetDrawable.class);
    assertThat(((InsetDrawable) track).getDrawable().getBounds().height()).isEqualTo(visual);

    StateListDrawable options =
        (StateListDrawable) context.getDrawable(R.drawable.native_entry_segment_option);
    for (int[] state :
        new int[][] {
          {android.R.attr.state_enabled, android.R.attr.state_checked},
          {-android.R.attr.state_enabled, android.R.attr.state_checked},
          {-android.R.attr.state_enabled},
          {android.R.attr.state_enabled}
        }) {
      options.setState(state);
      options.setBounds(0, 0, dp(context, 100), touch);
      assertThat(options.getCurrent()).isInstanceOf(InsetDrawable.class);
      assertThat(((InsetDrawable) options.getCurrent()).getDrawable().getBounds().height())
          .isEqualTo(visual);
    }
  }

  @Test
  public void threeSelectorGroupsKeepNonOverlappingFortyEightDpTargetsAtConservativeWidths() {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanCoordinateEntryPane pane =
        new TaiwanCoordinateEntryPane(
            context,
            new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {}),
            new TaiwanEntryFormatter());
    View root = pane.getView();

    assertGroupGeometry(
        context, root.findViewById(R.id.native_entry_system_group), dp(context, 480));
    assertGroupGeometry(
        context, root.findViewById(R.id.native_entry_twd97_zone_group), dp(context, 240));
    assertGroupGeometry(
        context, root.findViewById(R.id.native_entry_twd67_zone_group), dp(context, 240));
  }

  @Test
  public void selectorResourcesUseNamedInsetsAndLayoutUsesNamedTouchHeight() throws Exception {
    String track = readProjectFile("app/src/main/res/drawable/native_entry_segment_track.xml");
    String option = readProjectFile("app/src/main/res/drawable/native_entry_segment_option.xml");
    String layout = readProjectFile("app/src/main/res/layout/taiwan_coordinate_entry_pane.xml");

    assertThat(track).contains("android:insetTop=\"@dimen/native_entry_selector_vertical_inset\"");
    assertThat(track)
        .contains("android:insetBottom=\"@dimen/native_entry_selector_vertical_inset\"");
    assertThat(option.split("@dimen/native_entry_selector_vertical_inset", -1).length - 1)
        .isGreaterThanOrEqualTo(8);
    assertThat(layout.split("@dimen/native_entry_selector_touch_height", -1).length - 1)
        .isGreaterThanOrEqualTo(3);
  }

  private static void assertGroupGeometry(Context context, RadioGroup group, int width) {
    int touch =
        context.getResources().getDimensionPixelSize(R.dimen.native_entry_selector_touch_height);
    group.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(touch, View.MeasureSpec.EXACTLY));
    group.layout(0, 0, group.getMeasuredWidth(), group.getMeasuredHeight());

    assertThat(group.getMeasuredHeight()).isEqualTo(touch);
    assertThat(group.getPaddingTop()).isZero();
    assertThat(group.getPaddingBottom()).isZero();
    int previousRight = -1;
    for (int index = 0; index < group.getChildCount(); index++) {
      View child = group.getChildAt(index);
      assertThat(child.getMeasuredHeight()).isGreaterThanOrEqualTo(touch);
      assertThat(child.getMeasuredWidth()).isGreaterThanOrEqualTo(dp(context, 48));
      assertThat(child.getLeft()).isGreaterThanOrEqualTo(previousRight);
      assertThat(child.getRight()).isGreaterThan(child.getLeft());
      previousRight = child.getRight();
      assertThat(child.getLayoutParams().height).isEqualTo(ViewGroup.LayoutParams.MATCH_PARENT);
    }
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  private static String readProjectFile(String relative) throws Exception {
    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 4 && current != null; depth++, current = current.getParent()) {
      Path candidate = current.resolve(relative);
      if (Files.exists(candidate)) {
        return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
      }
    }
    throw new IllegalStateException("Unable to resolve " + relative);
  }
}
