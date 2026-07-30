package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Feature014TaipowerFixtures;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.plugin.R;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class NativeEntryFeature014RegressionTest {

  @Test
  public void sharedTaipowerFixtureCatalogCoversEveryPlannedBoundaryClass() {
    assertThat(Feature014TaipowerFixtures.TEN_METRE_CODE.replace(" ", "")).hasSize(9);
    assertThat(Feature014TaipowerFixtures.ONE_METRE_CODE.replace(" ", "")).hasSize(11);
    assertThat(Feature014TaipowerFixtures.ONE_METRE_RAW_VARIANTS).hasSizeGreaterThanOrEqualTo(8);
    assertThat(Feature014TaipowerFixtures.REPRESENTABLE_PARTIAL_RAW)
        .contains("", "H", "H7509", "H7509DB40");
    assertThat(Feature014TaipowerFixtures.VALID_EAST_WEST_LETTERS).isEqualTo("ABCDEFGH");
    assertThat(Feature014TaipowerFixtures.VALID_NORTH_SOUTH_LETTERS).isEqualTo("ABCDE");
    assertThat(Feature014TaipowerFixtures.INVALID_EAST_WEST_LETTERS).isEqualTo("IJ");
    assertThat(Feature014TaipowerFixtures.INVALID_NORTH_SOUTH_LETTERS).isEqualTo("FGHIJ");
    assertThat(Feature014TaipowerFixtures.PROVENANCE_VECTORS).hasSize(2);
    assertThat(Feature014TaipowerFixtures.ENCODER_WRAP_VECTORS).hasSize(4);
  }

  @Test
  public void layoutRetainsOneOuterScrollOwnerAndAllExistingSystemSurfaces() {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanCoordinateEntryPane pane =
        new TaiwanCoordinateEntryPane(
            context,
            new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {}),
            new TaiwanEntryFormatter());
    View root = pane.getView();

    assertThat(root).isInstanceOf(BoundedPaneScrollView.class);
    assertThat(countType(root, ScrollView.class)).isEqualTo(1);
    assertThat((View) root.findViewById(R.id.native_entry_system_taipower)).isNotNull();
    assertThat((View) root.findViewById(R.id.native_entry_system_twd97)).isNotNull();
    assertThat((View) root.findViewById(R.id.native_entry_system_twd67)).isNotNull();
    assertThat((View) root.findViewById(R.id.native_entry_system_address)).isNotNull();
    assertThat((View) root.findViewById(R.id.native_entry_address_full)).isNotNull();
    assertThat((View) root.findViewById(R.id.native_entry_address_road)).isNotNull();
    assertThat((View) root.findViewById(R.id.native_entry_address_tail)).isNotNull();
  }

  @Test
  public void activationAndAutofillPrepareAllCoordinatesWhileClearStaysActiveOnly() {
    AtomicInteger changes = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(CoordinateUnit.TWD97, ignored -> {});
    controller.setOnHumanChange(changes::incrementAndGet);
    controller.activate(point(25.033611, 121.564472), true);
    String taipowerBefore = controller.taipowerText();
    String twd67Before = controller.eastingText(CoordinateUnit.TWD67);

    controller.clear();

    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEmpty();
    assertThat(controller.taipowerText()).isEqualTo(taipowerBefore);
    assertThat(controller.eastingText(CoordinateUnit.TWD67)).isEqualTo(twd67Before);

    controller.autofill(point(23.9932, 121.6012));

    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isNotEmpty();
    assertThat(controller.taipowerText()).isNotEqualTo(taipowerBefore);
    assertThat(controller.eastingText(CoordinateUnit.TWD67)).isNotEqualTo(twd67Before);
    assertThat(changes).hasValue(0);
  }

  @Test
  public void addressTabDoesNotReplaceThePersistedCoordinateSystem() {
    AtomicInteger coordinateSelections = new AtomicInteger();
    AtomicInteger changes = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(
            CoordinateUnit.TWD67, ignored -> coordinateSelections.incrementAndGet());
    controller.setOnHumanChange(changes::incrementAndGet);

    controller.selectTab(NativeEntryTab.ADDRESS, true);

    assertThat(controller.activeTab()).isEqualTo(NativeEntryTab.ADDRESS);
    assertThat(controller.activeUnit()).isEqualTo(CoordinateUnit.TWD67);
    assertThat(coordinateSelections).hasValue(0);
    assertThat(changes).hasValue(1);
  }

  @Test
  public void manifestAndNativePaneRetainHostOwnedOfflineBoundary() throws IOException {
    String manifest = readMainFile("AndroidManifest.xml");
    String paneSource =
        readMainFile("java/com/atakmap/android/twcoord/nativeentry/TaiwanCoordinateEntryPane.java");
    String controllerSource =
        readMainFile("java/com/atakmap/android/twcoord/nativeentry/TaiwanEntryController.java");

    assertThat(manifest).doesNotContain("<uses-permission");
    assertThat(manifest).doesNotContain("android.permission.INTERNET");
    assertThat(manifest).containsOnlyOnce("<activity ");
    assertThat(manifest).contains("android:name=\"com.atakmap.app.component\"");
    assertThat(paneSource)
        .doesNotContain("CoordinateEntryCapability.showDialog")
        .doesNotContain("com.atakmap.app.R")
        .doesNotContain(".performClick(")
        .doesNotContain("java.net.");
    assertThat(controllerSource).doesNotContain("java.net.");
  }

  private static String readMainFile(String relative) throws IOException {
    Path modulePath = Path.of("src", "main").resolve(relative);
    Path repositoryPath = Path.of("app", "src", "main").resolve(relative);
    Path path = Files.exists(modulePath) ? modulePath : repositoryPath;
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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

  private static Wgs84 point(double latitude, double longitude) {
    return new Wgs84(latitude, longitude, 1L, Wgs84.Source.MAP_CENTRE);
  }
}
