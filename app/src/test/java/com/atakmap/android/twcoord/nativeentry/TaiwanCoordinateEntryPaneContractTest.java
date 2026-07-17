package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import android.content.Context;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import com.atakmap.android.gui.coordinateentry.CoordinateEntryPane;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class TaiwanCoordinateEntryPaneContractTest {

  private TaiwanCoordinateEntryPane pane;

  @Before
  public void setUp() {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanEntryController controller =
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {});
    pane = new TaiwanCoordinateEntryPane(context, controller, new TaiwanEntryFormatter());
  }

  @Test
  public void identityAndViewAreStableAndRootOwnsScrolling() {
    assertThat(pane.getUID()).isEqualTo("com.atakmap.android.twcoord.coordinateentry.taiwan");
    assertThat(pane.getName()).isNotBlank();
    assertThat(pane.getView()).isSameAs(pane.getView()).isInstanceOf(ScrollView.class);
    assertThat(pane.getView().getId()).isEqualTo(R.id.native_entry_root);
  }

  @Test
  public void activateAndResolveTaipowerRoundTrip() throws Exception {
    pane.onActivate(GeoPointMetaData.wrap(new GeoPoint(23.9932, 121.6012)), true);

    EditText input = pane.getView().findViewById(R.id.native_entry_input_taipower);
    assertThat(input.getText().toString()).matches("[A-X]\\d{4} [A-J]{2}\\d{4}");
    GeoPoint resolved = pane.getGeoPointMetaData().get();
    assertThat(resolved.getLatitude()).isCloseTo(23.9932, within(0.001));
    assertThat(resolved.getLongitude()).isCloseTo(121.6012, within(0.001));
  }

  @Test
  public void invalidInputThrowsCheckedCoordinateException() {
    EditText input = pane.getView().findViewById(R.id.native_entry_input_taipower);
    input.setText("bad");

    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
  }

  @Test
  public void onlyHumanEditNotifiesListener() {
    AtomicInteger changes = new AtomicInteger();
    pane.setOnChangedListener(ignored -> changes.incrementAndGet());

    pane.onActivate(GeoPointMetaData.wrap(new GeoPoint(25.033611, 121.564472)), true);
    assertThat(changes).hasValue(0);
    EditText input = pane.getView().findViewById(R.id.native_entry_input_taipower);
    input.append("1");

    assertThat(changes).hasValue(1);
  }

  @Test
  public void disposedLateCallbacksAreSafeAndIdempotent() {
    pane.dispose();
    pane.dispose();
    pane.onActivate(null, true);
    pane.autofill(null);

    assertThat(pane.format(null)).isNull();
    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
  }

  @Test
  public void twdSystemAndZoneControlsBindVisibleDrafts() {
    RadioButton twd97 = pane.getView().findViewById(R.id.native_entry_system_twd97);
    assertThat(twd97.isEnabled()).isTrue();
    twd97.performClick();

    EditText easting = pane.getView().findViewById(R.id.native_entry_twd97_easting);
    EditText northing = pane.getView().findViewById(R.id.native_entry_twd97_northing);
    easting.setText("306963");
    northing.setText("2769619");
    RadioButton zone121 = pane.getView().findViewById(R.id.native_entry_twd97_zone_121);
    assertThat(zone121.isChecked()).isTrue();
    assertThat(pane.getView().findViewById(R.id.native_entry_pane_twd97).getVisibility())
        .isEqualTo(android.view.View.VISIBLE);
  }

  @Test
  public void twd67Zone119ShowsAdvisoryAndInvalidSubmitShowsStatus() {
    RadioButton twd67 = pane.getView().findViewById(R.id.native_entry_system_twd67);
    twd67.performClick();
    RadioButton zone119 = pane.getView().findViewById(R.id.native_entry_twd67_zone_119);
    zone119.performClick();

    assertThat(pane.getView().findViewById(R.id.native_entry_twd67_advisory).getVisibility())
        .isEqualTo(android.view.View.VISIBLE);
    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
    TextView status = pane.getView().findViewById(R.id.native_entry_status);
    assertThat(status.getText().toString()).isNotBlank();
  }

  @Test
  public void eachLogicalHumanControlChangeNotifiesExactlyOnce() {
    AtomicInteger changes = new AtomicInteger();
    pane.setOnChangedListener(ignored -> changes.incrementAndGet());

    pane.getView().findViewById(R.id.native_entry_system_twd97).performClick();
    assertThat(changes).hasValue(1);
    pane.getView().findViewById(R.id.native_entry_twd97_zone_119).performClick();
    assertThat(changes).hasValue(2);
  }

  @Test
  public void hostAutofillClearAndCopyDoNotMutateThroughHumanEvents() throws Exception {
    AtomicInteger changes = new AtomicInteger();
    pane.setOnChangedListener(ignored -> changes.incrementAndGet());
    pane.getView().findViewById(R.id.native_entry_system_twd97).performClick();
    changes.set(0);

    GeoPointMetaData point = GeoPointMetaData.wrap(new GeoPoint(25.033611, 121.564472, 123.0));
    pane.autofill(point);
    EditText easting = pane.getView().findViewById(R.id.native_entry_twd97_easting);
    assertThat(easting.getText().toString()).isEqualTo("306963");
    String before = easting.getText().toString();
    assertThat(pane.format(point)).matches("TWD97 E=\\d+m N=\\d+m z121");
    assertThat(easting.getText().toString()).isEqualTo(before);
    assertThat(changes).hasValue(0);

    pane.onActivate(null, true);
    assertThat(easting.getText().toString()).isEmpty();
    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
    assertThat(changes).hasValue(0);
  }

  @Test
  public void returnedMetadataIsHorizontalOnlyAndDisposedControlsAreInert() throws Exception {
    AtomicInteger changes = new AtomicInteger();
    pane.setOnChangedListener(ignored -> changes.incrementAndGet());
    pane.onActivate(GeoPointMetaData.wrap(new GeoPoint(23.9932, 121.6012, 999.0)), true);
    GeoPoint resolved = pane.getGeoPointMetaData().get();
    assertThat(resolved.isAltitudeValid()).isFalse();

    EditText input = pane.getView().findViewById(R.id.native_entry_input_taipower);
    pane.dispose();
    input.append("1");
    assertThat(changes).hasValue(0);
    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
  }

  @Test
  public void readOnlyActivationDisablesControlsAndRestoresAttemptedProgrammaticEdits()
      throws Exception {
    AtomicInteger changes = new AtomicInteger();
    pane.setOnChangedListener(ignored -> changes.incrementAndGet());
    GeoPointMetaData point = GeoPointMetaData.wrap(new GeoPoint(25.033611, 121.564472));
    pane.onActivate(point, false);

    EditText taipower = pane.getView().findViewById(R.id.native_entry_input_taipower);
    RadioButton twd97 = pane.getView().findViewById(R.id.native_entry_system_twd97);
    String before = taipower.getText().toString();
    GeoPoint resolvedBefore = pane.getGeoPointMetaData().get();
    assertThat(taipower.isEnabled()).isFalse();
    assertThat(twd97.isEnabled()).isFalse();

    taipower.append("1");
    twd97.performClick();

    assertThat(taipower.getText().toString()).isEqualTo(before);
    assertThat(pane.getGeoPointMetaData().get()).isEqualTo(resolvedBefore);
    assertThat(pane.format(point)).matches("[A-X]\\d{4} [A-J]{2}\\d{4}");
    assertThat(changes).hasValue(0);
  }

  private static org.assertj.core.data.Offset<Double> within(double value) {
    return org.assertj.core.data.Offset.offset(value);
  }
}
