package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atakmap.android.gui.coordinateentry.CoordinateEntryPane;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class TaiwanCoordinateEntryPaneSafetyTest {

  @Test
  public void ordinaryHostCallbackFailuresAreContainedWithSafeResults() {
    TaiwanEntryController controller = configuredController();
    TaiwanCoordinateEntryPane pane = pane(controller, new RecordingTrace());

    doThrow(new IllegalStateException("activate")).when(controller).activate(any(), anyBoolean());
    assertThatCode(() -> pane.onActivate(point(), true)).doesNotThrowAnyException();

    doThrow(new IllegalStateException("autofill")).when(controller).autofill(any());
    assertThatCode(() -> pane.autofill(point())).doesNotThrowAnyException();

    doThrow(new IllegalStateException("format")).when(controller).format(any(), any());
    assertThat(pane.format(point())).isNull();

    doThrow(new IllegalStateException("resolve")).when(controller).resolvedOrNull();
    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);

    doThrow(new IllegalStateException("dispose")).when(controller).dispose();
    assertThatCode(pane::dispose).doesNotThrowAnyException();
  }

  @Test
  public void resourceFailureUsesStableFallbackName() {
    TaiwanCoordinateEntryPane pane =
        new TaiwanCoordinateEntryPane(
            RuntimeEnvironment.getApplication(),
            configuredController(),
            mock(TaiwanEntryFormatter.class),
            new RecordingTrace(),
            ignored -> {
              throw new IllegalStateException("resource");
            });

    assertThat(pane.getName()).isEqualTo("Taiwan");
  }

  @Test
  public void documentedListenerLinkageFailureIsContainedButFatalErrorIsRethrown() {
    TaiwanCoordinateEntryPane pane =
        new TaiwanCoordinateEntryPane(
            RuntimeEnvironment.getApplication(),
            new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {}),
            new TaiwanEntryFormatter(),
            new RecordingTrace());
    pane.setOnChangedListener(
        ignored -> {
          throw new NoSuchMethodError("host skew");
        });
    assertThatCode(
            () ->
                ((android.widget.EditText)
                        pane.getView().findViewById(R.id.native_entry_input_taipower))
                    .append("1"))
        .doesNotThrowAnyException();

    TaiwanEntryController fatalController = configuredController();
    doThrow(new ThreadDeath()).when(fatalController).activate(any(), anyBoolean());
    TaiwanCoordinateEntryPane fatalPane = pane(fatalController, new RecordingTrace());
    assertThatThrownBy(() -> fatalPane.onActivate(point(), true)).isInstanceOf(ThreadDeath.class);
  }

  @Test
  public void traceSectionsAreNamedAndBalancedAcrossNativeOperations() throws Exception {
    RecordingTrace trace = new RecordingTrace();
    TaiwanCoordinateEntryPane pane =
        new TaiwanCoordinateEntryPane(
            RuntimeEnvironment.getApplication(),
            new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {}),
            new TaiwanEntryFormatter(),
            trace);

    pane.onActivate(point(), true);
    pane.getView().findViewById(R.id.native_entry_system_twd97).performClick();
    pane.autofill(point());
    pane.getGeoPointMetaData();
    pane.onActivate(null, true);
    pane.format(point());

    assertThat(trace.sections)
        .contains(
            "TWCoord.native.activate",
            "TWCoord.native.render",
            "TWCoord.native.switch",
            "TWCoord.native.validate",
            "TWCoord.native.autofill",
            "TWCoord.native.clear",
            "TWCoord.native.format");
    assertThat(trace.open).isEmpty();
    assertThat(trace.begins).isEqualTo(trace.ends);
  }

  private static TaiwanCoordinateEntryPane pane(
      TaiwanEntryController controller, TaiwanCoordinateEntryPane.TraceSink trace) {
    return new TaiwanCoordinateEntryPane(
        RuntimeEnvironment.getApplication(), controller, mock(TaiwanEntryFormatter.class), trace);
  }

  private static TaiwanEntryController configuredController() {
    TaiwanEntryController controller = mock(TaiwanEntryController.class);
    when(controller.activeUnit()).thenReturn(CoordinateUnit.TAIPOWER);
    when(controller.taipowerText()).thenReturn("");
    when(controller.eastingText(any())).thenReturn("");
    when(controller.northingText(any())).thenReturn("");
    when(controller.zone(any())).thenReturn(121);
    when(controller.isEditable()).thenReturn(true);
    when(controller.validation()).thenReturn(TaiwanEntryController.Validation.EMPTY);
    return controller;
  }

  private static GeoPointMetaData point() {
    return GeoPointMetaData.wrap(new GeoPoint(25.033611, 121.564472));
  }

  private static final class RecordingTrace implements TaiwanCoordinateEntryPane.TraceSink {
    final Deque<String> open = new ArrayDeque<>();
    final List<String> sections = new ArrayList<>();
    int begins;
    int ends;

    @Override
    public void begin(String section) {
      sections.add(section);
      open.push(section);
      begins++;
    }

    @Override
    public void end() {
      open.pop();
      ends++;
    }
  }
}
