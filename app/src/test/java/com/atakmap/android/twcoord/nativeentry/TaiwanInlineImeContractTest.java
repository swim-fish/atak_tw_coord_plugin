package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.plugin.R;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class TaiwanInlineImeContractTest {

  @Test
  public void everyEditableFieldRequestsInlineSingleLinePresentation() {
    View root = pane().getView();

    for (int id : editableIds()) {
      EditText editor = root.findViewById(id);
      assertThat(editor).as("editor %s", id).isNotNull();
      assertThat(editor.isSingleLine()).as("single line %s", id).isTrue();
      assertThat(editor.getImeOptions() & EditorInfo.IME_FLAG_NO_FULLSCREEN)
          .as("NO_FULLSCREEN %s", id)
          .isNotZero();
      assertThat(editor.getImeOptions() & EditorInfo.IME_FLAG_NO_EXTRACT_UI)
          .as("NO_EXTRACT_UI %s", id)
          .isNotZero();
    }
  }

  @Test
  public void forceAsciiIsLimitedToTaipowerAlphanumericEditors() {
    View root = pane().getView();

    assertThat(options(root, R.id.native_entry_input_taipower) & EditorInfo.IME_FLAG_FORCE_ASCII)
        .isNotZero();
    assertThat(options(root, R.id.native_entry_taipower_region) & EditorInfo.IME_FLAG_FORCE_ASCII)
        .isNotZero();
    assertThat(options(root, R.id.native_entry_taipower_subgrid) & EditorInfo.IME_FLAG_FORCE_ASCII)
        .isNotZero();
    for (int id :
        List.of(
            R.id.native_entry_taipower_subregion,
            R.id.native_entry_taipower_precision,
            R.id.native_entry_twd97_easting,
            R.id.native_entry_twd97_northing,
            R.id.native_entry_twd67_easting,
            R.id.native_entry_twd67_northing,
            R.id.native_entry_address_full,
            R.id.native_entry_address_road,
            R.id.native_entry_address_tail)) {
      assertThat(options(root, id) & EditorInfo.IME_FLAG_FORCE_ASCII).as("editor %s", id).isZero();
    }
  }

  @Test
  public void currentFieldsExposeTheRequiredActionAndPluginOwnedFocusMatrix() {
    View root = pane().getView();

    assertAction(root, R.id.native_entry_input_taipower, EditorInfo.IME_ACTION_DONE);
    assertAction(root, R.id.native_entry_taipower_region, EditorInfo.IME_ACTION_NEXT);
    assertAction(root, R.id.native_entry_taipower_subregion, EditorInfo.IME_ACTION_NEXT);
    assertAction(root, R.id.native_entry_taipower_subgrid, EditorInfo.IME_ACTION_NEXT);
    assertAction(root, R.id.native_entry_taipower_precision, EditorInfo.IME_ACTION_DONE);
    assertAction(root, R.id.native_entry_twd97_easting, EditorInfo.IME_ACTION_NEXT);
    assertAction(root, R.id.native_entry_twd97_northing, EditorInfo.IME_ACTION_DONE);
    assertAction(root, R.id.native_entry_twd67_easting, EditorInfo.IME_ACTION_NEXT);
    assertAction(root, R.id.native_entry_twd67_northing, EditorInfo.IME_ACTION_DONE);
    assertAction(root, R.id.native_entry_address_full, EditorInfo.IME_ACTION_SEARCH);
    assertAction(root, R.id.native_entry_address_road, EditorInfo.IME_ACTION_NEXT);
    assertAction(root, R.id.native_entry_address_tail, EditorInfo.IME_ACTION_SEARCH);

    assertNext(root, R.id.native_entry_twd97_easting, R.id.native_entry_twd97_northing);
    assertNext(root, R.id.native_entry_twd67_easting, R.id.native_entry_twd67_northing);
    assertNext(root, R.id.native_entry_address_road, R.id.native_entry_address_tail);
    assertNext(root, R.id.native_entry_taipower_region, R.id.native_entry_taipower_subregion);
    assertNext(root, R.id.native_entry_taipower_subregion, R.id.native_entry_taipower_subgrid);
    assertNext(root, R.id.native_entry_taipower_subgrid, R.id.native_entry_taipower_precision);
  }

  private static void assertAction(View root, int id, int expected) {
    assertThat(options(root, id) & EditorInfo.IME_MASK_ACTION)
        .as("action %s", id)
        .isEqualTo(expected);
  }

  private static void assertNext(View root, int fromId, int toId) {
    EditText from = root.findViewById(fromId);
    assertThat(from.getNextFocusForwardId()).isEqualTo(toId);
    assertThat(from.getNextFocusDownId()).isEqualTo(toId);
    assertThat((View) root.findViewById(toId)).isNotNull();
  }

  private static int options(View root, int id) {
    EditText editor = root.findViewById(id);
    return editor.getImeOptions();
  }

  private static int[] editableIds() {
    return new int[] {
      R.id.native_entry_input_taipower,
      R.id.native_entry_taipower_region,
      R.id.native_entry_taipower_subregion,
      R.id.native_entry_taipower_subgrid,
      R.id.native_entry_taipower_precision,
      R.id.native_entry_twd97_easting,
      R.id.native_entry_twd97_northing,
      R.id.native_entry_twd67_easting,
      R.id.native_entry_twd67_northing,
      R.id.native_entry_address_full,
      R.id.native_entry_address_road,
      R.id.native_entry_address_tail
    };
  }

  private static TaiwanCoordinateEntryPane pane() {
    Context context = RuntimeEnvironment.getApplication();
    return new TaiwanCoordinateEntryPane(
        context,
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {}),
        new TaiwanEntryFormatter());
  }
}
