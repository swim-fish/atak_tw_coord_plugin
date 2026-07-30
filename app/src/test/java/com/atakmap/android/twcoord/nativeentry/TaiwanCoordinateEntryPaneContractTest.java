package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import com.atakmap.android.gui.coordinateentry.CoordinateEntryPane;
import com.atakmap.android.twcoord.address.lookup.AddressAvailability;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressLookupService;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.DatasetIdentity;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressResult;
import com.atakmap.android.twcoord.address.lookup.LookupHandle;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressResult;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
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
  public void layoutUsesAtakDdCompactRowsAndBoundedControls() {
    Context context = RuntimeEnvironment.getApplication();
    View root = pane.getView();
    LinearLayout content = root.findViewById(R.id.native_entry_content);
    RadioGroup systemGroup = root.findViewById(R.id.native_entry_system_group);
    RadioButton taipowerButton = root.findViewById(R.id.native_entry_system_taipower);
    LinearLayout taipowerRow = root.findViewById(R.id.native_entry_taipower_row);
    EditText taipowerInput = root.findViewById(R.id.native_entry_input_taipower);
    RadioGroup twd97ZoneGroup = root.findViewById(R.id.native_entry_twd97_zone_group);
    RadioGroup twd67ZoneGroup = root.findViewById(R.id.native_entry_twd67_zone_group);
    TextView status = root.findViewById(R.id.native_entry_status);

    assertThat(content.getPaddingLeft()).isZero();
    assertThat(content.getPaddingTop()).isEqualTo(dp(context, 2));
    assertThat(content.getPaddingRight()).isZero();
    assertThat(content.getPaddingBottom()).isZero();
    assertThat(systemGroup.getLayoutParams().height).isEqualTo(dp(context, 48));
    assertThat(systemGroup.getPaddingTop()).isZero();
    assertThat(systemGroup.getPaddingBottom()).isZero();
    assertThat(systemGroup.getPaddingLeft()).isEqualTo(dp(context, 2));
    assertThat(systemGroup.getPaddingRight()).isEqualTo(dp(context, 2));
    assertThat(taipowerButton.getLayoutParams().height)
        .isEqualTo(ViewGroup.LayoutParams.MATCH_PARENT);
    systemGroup.measure(
        View.MeasureSpec.makeMeasureSpec(dp(context, 600), View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(dp(context, 48), View.MeasureSpec.EXACTLY));
    assertThat(taipowerButton.getMeasuredHeight()).isEqualTo(dp(context, 48));
    assertThat(twd97ZoneGroup.getLayoutParams().height).isEqualTo(dp(context, 48));
    assertThat(twd97ZoneGroup.getPaddingTop()).isZero();
    assertThat(twd97ZoneGroup.getPaddingBottom()).isZero();
    assertThat(twd67ZoneGroup.getLayoutParams().height).isEqualTo(dp(context, 48));
    assertThat(twd67ZoneGroup.getPaddingTop()).isZero();
    assertThat(twd67ZoneGroup.getPaddingBottom()).isZero();
    assertThat(taipowerInput.getLayoutParams().height)
        .isEqualTo(ViewGroup.LayoutParams.WRAP_CONTENT);
    assertThat(taipowerInput.getTextSize())
        .isEqualTo(context.getResources().getDimension(R.dimen.native_entry_title_font));
    assertThat(((LinearLayout.LayoutParams) taipowerRow.getChildAt(0).getLayoutParams()).weight)
        .isEqualTo(3.0f);
    assertThat(((LinearLayout.LayoutParams) taipowerInput.getLayoutParams()).weight)
        .isEqualTo(7.0f);
    assertThat(status.getVisibility()).isEqualTo(View.GONE);
  }

  @Test
  public void fourthAddressTabUsesEqualSelectorWeightAndOneCompactFullField() {
    View root = pane.getView();
    RadioGroup group = root.findViewById(R.id.native_entry_system_group);
    RadioButton address = root.findViewById(R.id.native_entry_system_address);
    ViewGroup addressPane = root.findViewById(R.id.native_entry_pane_address);

    assertThat(group.getChildCount()).isEqualTo(4);
    assertThat(((LinearLayout.LayoutParams) address.getLayoutParams()).weight).isEqualTo(1.0f);
    assertThat(address.getLayoutParams().height).isEqualTo(ViewGroup.LayoutParams.MATCH_PARENT);
    address.performClick();
    assertThat(addressPane.getVisibility()).isEqualTo(View.VISIBLE);
    assertThat(countEditTexts(addressPane)).isEqualTo(5);
    assertThat(root.findViewById(R.id.native_entry_address_structured).getVisibility())
        .isEqualTo(View.GONE);
    LinearLayout row = root.findViewById(R.id.native_entry_address_full_row);
    EditText input = root.findViewById(R.id.native_entry_address_full);
    assertThat(((LinearLayout.LayoutParams) row.getChildAt(0).getLayoutParams()).weight)
        .isEqualTo(3.0f);
    assertThat(((LinearLayout.LayoutParams) input.getLayoutParams()).weight).isEqualTo(7.0f);
  }

  @Test
  public void addressGetterIsSynchronousAndFormatterReadsOnlyResolutionMetadata() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanEntryController coordinateController =
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {});
    AddressEntryController addressController =
        new AddressEntryController(
            new ResolvedAddressLookupService(),
            new com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser(),
            new ImmediateDebouncer(),
            20);
    TaiwanCoordinateEntryPane addressPane =
        new TaiwanCoordinateEntryPane(
            context, context, coordinateController, addressController, new TaiwanEntryFormatter());
    addressPane.getView().findViewById(R.id.native_entry_system_address).performClick();

    assertThatThrownBy(addressPane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
    EditText input = addressPane.getView().findViewById(R.id.native_entry_address_full);
    input.setText("臺北市信義區市府路1號");
    String before = input.getText().toString();

    GeoPointMetaData resolved = addressPane.getGeoPointMetaData();
    assertThat(resolved.get().getLatitude()).isEqualTo(25.033d);
    assertThat(resolved.getMetaData("twcoord.address.display")).isEqualTo("臺北市信義區市府路1號");
    assertThat(addressPane.format(resolved)).isEqualTo("臺北市信義區市府路1號");
    assertThat(input.getText().toString()).isEqualTo(before);

    GeoPointMetaData plain = GeoPointMetaData.wrap(new GeoPoint(25.033, 121.565));
    assertThat(addressPane.format(plain)).isNull();
    assertThat(input.getText().toString()).isEqualTo(before);
  }

  @Test
  public void suppliedPointPreparesAllTabsAndReverseAddressNeverSnapsOrNotifiesHuman()
      throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanEntryController coordinateController =
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {});
    AddressEntryController addressController =
        new AddressEntryController(
            new ResolvedAddressLookupService(),
            new com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser(),
            new ImmediateDebouncer(),
            20);
    TaiwanCoordinateEntryPane suppliedPane =
        new TaiwanCoordinateEntryPane(
            context, context, coordinateController, addressController, new TaiwanEntryFormatter());
    AtomicInteger changes = new AtomicInteger();
    suppliedPane.setOnChangedListener(ignored -> changes.incrementAndGet());
    GeoPointMetaData host = GeoPointMetaData.wrap(new GeoPoint(25.033, 121.565));

    suppliedPane.onActivate(host, true);
    suppliedPane.getView().findViewById(R.id.native_entry_system_address).performClick();
    changes.set(0);
    GeoPointMetaData resolved = suppliedPane.getGeoPointMetaData();

    assertThat(resolved.get().getLatitude()).isEqualTo(25.033);
    assertThat(resolved.get().getLongitude()).isEqualTo(121.565);
    assertThat(resolved.getMetaData(TaiwanCoordinateEntryPane.META_RECORD_LAT)).isEqualTo(25.0332d);
    assertThat(suppliedPane.format(resolved)).isEqualTo("臺北市信義區市府路1號");
    assertThat(changes).hasValue(0);

    EditText address = suppliedPane.getView().findViewById(R.id.native_entry_address_full);
    suppliedPane.onActivate(null, true);
    assertThat(address.getText().toString()).isEmpty();
    suppliedPane.getView().findViewById(R.id.native_entry_system_twd97).performClick();
    EditText easting = suppliedPane.getView().findViewById(R.id.native_entry_twd97_easting);
    assertThat(easting.getText().toString()).isNotEmpty();
  }

  @Test
  public void addressAutofillAndReadOnlyKeepExactPointWithPureModeProjection() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanEntryController coordinateController =
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {});
    AddressEntryController addressController =
        new AddressEntryController(
            new ResolvedAddressLookupService(),
            new com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser(),
            new ImmediateDebouncer(),
            20);
    TaiwanCoordinateEntryPane suppliedPane =
        new TaiwanCoordinateEntryPane(
            context, context, coordinateController, addressController, new TaiwanEntryFormatter());
    suppliedPane.getView().findViewById(R.id.native_entry_system_address).performClick();
    GeoPointMetaData host = GeoPointMetaData.wrap(new GeoPoint(24.147, 120.673));

    suppliedPane.onActivate(host, false);

    EditText address = suppliedPane.getView().findViewById(R.id.native_entry_address_full);
    assertThat(address.isEnabled()).isFalse();
    assertThat(suppliedPane.getView().findViewById(R.id.native_entry_address_mode).isEnabled())
        .isTrue();
    assertThat(suppliedPane.getGeoPointMetaData().get()).isEqualTo(host.get());
  }

  @Test
  public void activateAndResolveTaipowerRoundTrip() throws Exception {
    pane.onActivate(GeoPointMetaData.wrap(new GeoPoint(23.9932, 121.6012)), true);

    EditText input = pane.getView().findViewById(R.id.native_entry_input_taipower);
    assertThat(input.getText().toString()).matches("[A-X]\\d{4} [A-H][A-E]\\d{4}");
    GeoPoint resolved = pane.getGeoPointMetaData().get();
    assertThat(resolved.getLatitude()).isCloseTo(23.9932, within(0.001));
    assertThat(resolved.getLongitude()).isCloseTo(121.6012, within(0.001));
  }

  @Test
  public void oneActivationPrefillsEverySystemWithoutAutofill() throws Exception {
    GeoPointMetaData point = GeoPointMetaData.wrap(new GeoPoint(25.033611, 121.564472));

    pane.onActivate(point, true);

    EditText taipower = pane.getView().findViewById(R.id.native_entry_input_taipower);
    assertThat(taipower.getText().toString()).matches("[A-X]\\d{4} [A-H][A-E]\\d{4}");

    pane.getView().findViewById(R.id.native_entry_system_twd97).performClick();
    EditText twd97Easting = pane.getView().findViewById(R.id.native_entry_twd97_easting);
    EditText twd97Northing = pane.getView().findViewById(R.id.native_entry_twd97_northing);
    assertThat(twd97Easting.getText().toString()).isEqualTo("306963");
    assertThat(twd97Northing.getText().toString()).isEqualTo("2769619");
    assertThat(pane.format(point)).matches("TWD97 E=\\d+m N=\\d+m z121");
    GeoPoint twd97Resolved = pane.getGeoPointMetaData().get();
    assertThat(twd97Resolved.getLatitude()).isCloseTo(25.033611, within(0.00002));
    assertThat(twd97Resolved.getLongitude()).isCloseTo(121.564472, within(0.00002));

    pane.getView().findViewById(R.id.native_entry_system_twd67).performClick();
    EditText twd67Easting = pane.getView().findViewById(R.id.native_entry_twd67_easting);
    EditText twd67Northing = pane.getView().findViewById(R.id.native_entry_twd67_northing);
    assertThat(twd67Easting.getText().toString()).isEqualTo("306132");
    assertThat(twd67Northing.getText().toString()).isEqualTo("2769823");
    assertThat(pane.format(point)).matches("TWD67 E=\\d+m N=\\d+m z121");
  }

  @Test
  public void zone119ActivationShowsUnavailableTaipowerAndPreparedTwdTabs() {
    pane.onActivate(GeoPointMetaData.wrap(new GeoPoint(23.566, 119.566)), true);

    EditText taipower = pane.getView().findViewById(R.id.native_entry_input_taipower);
    TextView status = pane.getView().findViewById(R.id.native_entry_status);
    assertThat(taipower.getText().toString()).isEmpty();
    assertThat(status.getVisibility()).isEqualTo(View.VISIBLE);

    pane.getView().findViewById(R.id.native_entry_system_twd97).performClick();
    EditText twd97Easting = pane.getView().findViewById(R.id.native_entry_twd97_easting);
    assertThat(twd97Easting.getText().toString()).isNotEmpty();
    RadioButton twd97Zone119 = pane.getView().findViewById(R.id.native_entry_twd97_zone_119);
    assertThat(twd97Zone119.isChecked()).isTrue();

    pane.getView().findViewById(R.id.native_entry_system_twd67).performClick();
    EditText twd67Easting = pane.getView().findViewById(R.id.native_entry_twd67_easting);
    assertThat(twd67Easting.getText().toString()).isNotEmpty();
    RadioButton twd67Zone119 = pane.getView().findViewById(R.id.native_entry_twd67_zone_119);
    assertThat(twd67Zone119.isChecked()).isTrue();
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
    Button taipowerMode = pane.getView().findViewById(R.id.native_entry_taipower_mode);
    RadioButton twd97Zone121 = pane.getView().findViewById(R.id.native_entry_twd97_zone_121);
    RadioButton twd97Zone119 = pane.getView().findViewById(R.id.native_entry_twd97_zone_119);
    RadioButton twd67Zone121 = pane.getView().findViewById(R.id.native_entry_twd67_zone_121);
    RadioButton twd67Zone119 = pane.getView().findViewById(R.id.native_entry_twd67_zone_119);

    pane.dispose();
    pane.dispose();
    pane.onActivate(null, true);
    pane.autofill(null);

    assertThat(taipowerMode.isEnabled()).isFalse();
    assertThat(twd97Zone121.isEnabled()).isFalse();
    assertThat(twd97Zone119.isEnabled()).isFalse();
    assertThat(twd67Zone121.isEnabled()).isFalse();
    assertThat(twd67Zone119.isEnabled()).isFalse();
    assertThat(pane.format(null)).isNull();
    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class)
        .hasMessage(
            RuntimeEnvironment.getApplication().getString(R.string.native_entry_error_disposed));
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
  public void oneHostAutofillRefreshesEveryTaiwanPageWithoutHumanEvents() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanEntryController coordinateController =
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {});
    AddressEntryController addressController =
        new AddressEntryController(
            new ResolvedAddressLookupService(),
            new com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser(),
            new ImmediateDebouncer(),
            20);
    TaiwanCoordinateEntryPane suppliedPane =
        new TaiwanCoordinateEntryPane(
            context, context, coordinateController, addressController, new TaiwanEntryFormatter());
    AtomicInteger changes = new AtomicInteger();
    suppliedPane.setOnChangedListener(ignored -> changes.incrementAndGet());
    suppliedPane.onActivate(GeoPointMetaData.wrap(new GeoPoint(25.033611, 121.564472)), true);
    suppliedPane.getView().findViewById(R.id.native_entry_system_twd97).performClick();
    changes.set(0);
    GeoPointMetaData replacement = GeoPointMetaData.wrap(new GeoPoint(23.9932, 121.6012, 123.0));

    suppliedPane.autofill(replacement);

    assertThat(changes).hasValue(0);
    assertThat(coordinateController.activeTab()).isSameAs(NativeEntryTab.TWD97);
    for (CoordinateUnit unit : CoordinateUnit.values()) {
      coordinateController.selectSystem(unit, false);
      assertThat(coordinateController.validation())
          .as(unit.toString())
          .isSameAs(TaiwanEntryController.Validation.VALID);
      assertThat(coordinateController.resolvedOrNull().latitudeDeg())
          .as("%s latitude", unit)
          .isCloseTo(23.9932, within(unit == CoordinateUnit.TAIPOWER ? 0.001 : 0.0001));
      assertThat(coordinateController.resolvedOrNull().longitudeDeg())
          .as("%s longitude", unit)
          .isCloseTo(121.6012, within(unit == CoordinateUnit.TAIPOWER ? 0.001 : 0.0001));
    }

    suppliedPane.getView().findViewById(R.id.native_entry_system_address).performClick();
    GeoPointMetaData address = suppliedPane.getGeoPointMetaData();
    assertThat(address.get().getLatitude()).isEqualTo(23.9932);
    assertThat(address.get().getLongitude()).isEqualTo(121.6012);
    assertThat(address.get().isAltitudeValid()).isFalse();

    changes.set(0);
    GeoPointMetaData outerIsland = GeoPointMetaData.wrap(new GeoPoint(23.566, 119.566));
    suppliedPane.autofill(outerIsland);

    assertThat(changes).hasValue(0);
    assertThat(coordinateController.activeTab()).isSameAs(NativeEntryTab.ADDRESS);
    GeoPointMetaData outerAddress = suppliedPane.getGeoPointMetaData();
    assertThat(outerAddress.get().getLatitude()).isEqualTo(23.566);
    assertThat(outerAddress.get().getLongitude()).isEqualTo(119.566);
    coordinateController.selectSystem(CoordinateUnit.TAIPOWER, false);
    assertThat(coordinateController.validation())
        .isSameAs(TaiwanEntryController.Validation.UNREPRESENTABLE);
    for (CoordinateUnit unit : new CoordinateUnit[] {CoordinateUnit.TWD97, CoordinateUnit.TWD67}) {
      coordinateController.selectSystem(unit, false);
      assertThat(coordinateController.validation())
          .as(unit.toString())
          .isSameAs(TaiwanEntryController.Validation.VALID);
      assertThat(coordinateController.zone(unit)).isEqualTo(119);
    }
    coordinateController.selectTab(NativeEntryTab.ADDRESS, false);
    String twd97BeforeAddressClear = coordinateController.eastingText(CoordinateUnit.TWD97);
    changes.set(0);

    suppliedPane.autofill(null);

    assertThat(changes).hasValue(0);
    assertThatThrownBy(suppliedPane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
    assertThat(coordinateController.eastingText(CoordinateUnit.TWD97))
        .isEqualTo(twd97BeforeAddressClear);
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
    Button taipowerMode = pane.getView().findViewById(R.id.native_entry_taipower_mode);
    RadioButton twd97 = pane.getView().findViewById(R.id.native_entry_system_twd97);
    String before = taipower.getText().toString();
    GeoPoint resolvedBefore = pane.getGeoPointMetaData().get();
    assertThat(taipower.isEnabled()).isFalse();
    assertThat(taipowerMode.isEnabled()).isTrue();
    assertThat(twd97.isEnabled()).isFalse();

    taipower.append("1");
    taipowerMode.performClick();
    twd97.performClick();

    assertThat(taipower.getText().toString()).isEqualTo(before);
    assertThat(
            pane.getView().findViewById(R.id.native_entry_taipower_split_container).getVisibility())
        .isEqualTo(View.VISIBLE);
    assertThat(pane.getGeoPointMetaData().get()).isEqualTo(resolvedBefore);
    assertThat(pane.format(point)).matches("[A-X]\\d{4} [A-H][A-E]\\d{4}");
    assertThat(changes).hasValue(0);
  }

  @Test
  public void taipowerUsesRightSideModeActionForExactlyTwoExclusiveLayouts() {
    View root = pane.getView();
    LinearLayout body = root.findViewById(R.id.native_entry_taipower_body);
    LinearLayout content = root.findViewById(R.id.native_entry_taipower_content);
    LinearLayout actions = root.findViewById(R.id.native_entry_taipower_actions);
    Button mode = root.findViewById(R.id.native_entry_taipower_mode);
    Button addressMode = root.findViewById(R.id.native_entry_address_mode);
    View rawContainer = root.findViewById(R.id.native_entry_taipower_raw_container);
    View splitContainer = root.findViewById(R.id.native_entry_taipower_split_container);

    assertThat(body.getOrientation()).isEqualTo(LinearLayout.HORIZONTAL);
    assertThat(mode.getParent()).isSameAs(actions);
    assertThat(actions.getGravity()).isEqualTo(android.view.Gravity.TOP | android.view.Gravity.END);
    assertThat(((LinearLayout.LayoutParams) content.getLayoutParams()).weight).isEqualTo(8f);
    assertThat(((LinearLayout.LayoutParams) actions.getLayoutParams()).weight).isEqualTo(2f);
    assertThat(mode.getLayoutParams().height).isEqualTo(addressMode.getLayoutParams().height);
    assertThat(mode.getContentDescription()).isNotBlank();
    assertThat(mode.getText())
        .isEqualTo(root.getContext().getString(R.string.native_entry_taipower_mode_split));
    assertThat(rawContainer.getVisibility()).isEqualTo(View.VISIBLE);
    assertThat(splitContainer.getVisibility()).isEqualTo(View.GONE);

    mode.performClick();

    assertThat(mode.getText())
        .isEqualTo(root.getContext().getString(R.string.native_entry_taipower_mode_single));
    assertThat(rawContainer.getVisibility()).isEqualTo(View.GONE);
    assertThat(splitContainer.getVisibility()).isEqualTo(View.VISIBLE);

    EditText region = root.findViewById(R.id.native_entry_taipower_region);
    EditText subregion = root.findViewById(R.id.native_entry_taipower_subregion);
    EditText subgrid = root.findViewById(R.id.native_entry_taipower_subgrid);
    EditText precision = root.findViewById(R.id.native_entry_taipower_precision);
    assertEditorContract(region, 1, EditorInfo.IME_ACTION_NEXT);
    assertEditorContract(subregion, 4, EditorInfo.IME_ACTION_NEXT);
    assertEditorContract(subgrid, 2, EditorInfo.IME_ACTION_NEXT);
    assertEditorContract(precision, 4, EditorInfo.IME_ACTION_DONE);
    assertThat(region.getNextFocusForwardId()).isEqualTo(subregion.getId());
    assertThat(subregion.getNextFocusForwardId()).isEqualTo(subgrid.getId());
    assertThat(subgrid.getNextFocusForwardId()).isEqualTo(precision.getId());
  }

  @Test
  public void guidedFiltersUppercaseAsciiAndRetainRangeInvalidLettersForCorrection() {
    pane.getView().findViewById(R.id.native_entry_taipower_mode).performClick();
    EditText region = pane.getView().findViewById(R.id.native_entry_taipower_region);
    EditText subregion = pane.getView().findViewById(R.id.native_entry_taipower_subregion);
    EditText subgrid = pane.getView().findViewById(R.id.native_entry_taipower_subgrid);
    EditText precision = pane.getView().findViewById(R.id.native_entry_taipower_precision);

    region.setText("hz");
    subregion.setText("7509x");
    subgrid.setText("ifz");
    precision.setText("40167");

    assertThat(region.getText().toString()).isEqualTo("H");
    assertThat(subregion.getText().toString()).isEqualTo("7509");
    assertThat(subgrid.getText().toString()).isEqualTo("IF");
    assertThat(precision.getText().toString()).isEqualTo("4016");
    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
    assertThat(
            ((TextView) pane.getView().findViewById(R.id.native_entry_status)).getText().toString())
        .isNotBlank();
  }

  @Test
  public void invalidSubgridAttemptsStayVisibleWithPositionSpecificFeedback() {
    View root = pane.getView();
    EditText raw = root.findViewById(R.id.native_entry_input_taipower);
    TextView status = root.findViewById(R.id.native_entry_status);
    raw.setText("H7509 IB4016");

    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
    assertThat(raw.getText().toString()).isEqualTo("H7509 IB4016");
    assertThat(status.getText())
        .isEqualTo(root.getContext().getString(R.string.native_entry_taipower_error_ew_letter));

    root.findViewById(R.id.native_entry_taipower_mode).performClick();
    EditText subgrid = root.findViewById(R.id.native_entry_taipower_subgrid);
    subgrid.setText("AF");

    assertThatThrownBy(pane::getGeoPointMetaData)
        .isInstanceOf(CoordinateEntryPane.CoordinateException.class);
    assertThat(subgrid.getText().toString()).isEqualTo("AF");
    assertThat(status.getText())
        .isEqualTo(root.getContext().getString(R.string.native_entry_taipower_error_ns_letter));
  }

  @Test
  public void guidedFixedGroupsAutoAdvanceAndFinalTwoDigitsAcceptContinuation() {
    pane.getView().findViewById(R.id.native_entry_taipower_mode).performClick();
    EditText region = pane.getView().findViewById(R.id.native_entry_taipower_region);
    EditText subregion = pane.getView().findViewById(R.id.native_entry_taipower_subregion);
    EditText subgrid = pane.getView().findViewById(R.id.native_entry_taipower_subgrid);
    EditText precision = pane.getView().findViewById(R.id.native_entry_taipower_precision);

    region.requestFocus();
    region.setText("H");
    assertThat(subregion.hasFocus()).isTrue();
    subregion.setText("7509");
    assertThat(subgrid.hasFocus()).isTrue();
    subgrid.setText("DB");
    assertThat(precision.hasFocus()).isTrue();
    precision.setText("40");
    assertThat(precision.hasFocus()).isTrue();
    precision.append("16");
    assertThat(precision.getText().toString()).isEqualTo("4016");
  }

  @Test
  public void focusedModeSwitchHandsOffLocallyAndProjectionErrorsKeepCurrentMode() {
    View root = pane.getView();
    EditText raw = root.findViewById(R.id.native_entry_input_taipower);
    raw.setText("  h7509 db4016  ");
    raw.requestFocus();

    Button mode = root.findViewById(R.id.native_entry_taipower_mode);
    mode.performClick();
    EditText region = root.findViewById(R.id.native_entry_taipower_region);
    assertThat(region.hasFocus()).isTrue();

    mode.performClick();
    assertThat(raw.hasFocus()).isTrue();
    assertThat(raw.getText().toString()).isEqualTo("  h7509 db4016  ");

    raw.setText("H75-09");
    mode.performClick();
    assertThat(mode.getText())
        .isEqualTo(root.getContext().getString(R.string.native_entry_taipower_mode_split));
    assertThat(root.findViewById(R.id.native_entry_taipower_raw_container).getVisibility())
        .isEqualTo(View.VISIBLE);
    assertThat(raw.getText().toString()).isEqualTo("H75-09");
  }

  @Test
  public void taipowerModeAndGuidedResourceKeysExistInEverySupportedLocale() throws Exception {
    String[] keys = {
      "native_entry_taipower_mode_single",
      "native_entry_taipower_mode_split",
      "native_entry_taipower_region_hint",
      "native_entry_taipower_subregion_hint",
      "native_entry_taipower_subgrid_hint",
      "native_entry_taipower_precision_hint",
      "native_entry_taipower_projection_error",
      "native_entry_taipower_error_ew_letter",
      "native_entry_taipower_error_ns_letter",
      "native_entry_a11y_taipower_mode",
      "native_entry_a11y_taipower_region",
      "native_entry_a11y_taipower_subregion",
      "native_entry_a11y_taipower_subgrid",
      "native_entry_a11y_taipower_precision"
    };
    for (String directory : new String[] {"values", "values-zh-rTW", "values-ja"}) {
      Path file = projectPath("app", "src", "main", "res", directory, "strings.xml");
      String xml = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      for (String key : keys) {
        assertThat(xml).as("%s/%s", directory, key).contains("<string name=\"" + key + "\">");
      }
    }
  }

  @Test
  public void transparentSelectorBandsAreClickableAndNotifyExactlyOnce() {
    View root = pane.getView();
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    activity.setContentView(root);
    shadowOf(Looper.getMainLooper()).idle();
    AtomicInteger changes = new AtomicInteger();
    pane.setOnChangedListener(ignored -> changes.incrementAndGet());
    RadioGroup systems = root.findViewById(R.id.native_entry_system_group);
    layoutGroup(systems, dp(root.getContext(), 480));

    RadioButton twd97 = root.findViewById(R.id.native_entry_system_twd97);
    tapBand(twd97, dp(root.getContext(), 2));
    assertThat(twd97.isChecked()).isTrue();
    assertThat(changes).hasValue(1);

    RadioGroup zones = root.findViewById(R.id.native_entry_twd97_zone_group);
    layoutGroup(zones, dp(root.getContext(), 240));
    RadioButton zone119 = root.findViewById(R.id.native_entry_twd97_zone_119);
    tapBand(zone119, dp(root.getContext(), 46));
    assertThat(zone119.isChecked()).isTrue();
    assertThat(changes).hasValue(2);
  }

  @Test
  public void programmaticChecksStaySilentAndReadOnlyKeepsCheckedDisabledSemantics() {
    Context context = RuntimeEnvironment.getApplication();
    TaiwanEntryController controller =
        new TaiwanEntryController(CoordinateUnit.TWD67, ignored -> {});
    TaiwanCoordinateEntryPane readOnlyPane =
        new TaiwanCoordinateEntryPane(context, controller, new TaiwanEntryFormatter());
    AtomicInteger changes = new AtomicInteger();
    readOnlyPane.setOnChangedListener(ignored -> changes.incrementAndGet());

    readOnlyPane.onActivate(GeoPointMetaData.wrap(new GeoPoint(23.566, 119.566)), false);

    RadioButton system = readOnlyPane.getView().findViewById(R.id.native_entry_system_twd67);
    RadioButton zone = readOnlyPane.getView().findViewById(R.id.native_entry_twd67_zone_119);
    assertThat(system.isChecked()).isTrue();
    assertThat(system.isEnabled()).isFalse();
    assertThat(zone.isChecked()).isTrue();
    assertThat(zone.isEnabled()).isFalse();
    assertThat(changes).hasValue(0);
  }

  @Test
  public void selectorLabelsAndAccessibilityNamesStaySingleLineAcrossLocalesAndFontScales() {
    for (Locale locale : new Locale[] {Locale.ENGLISH, Locale.TAIWAN, Locale.JAPANESE}) {
      for (float fontScale : new float[] {1.0f, 2.0f}) {
        TaiwanCoordinateEntryPane localizedPane = localizedPane(locale, fontScale);
        RadioGroup systems = localizedPane.getView().findViewById(R.id.native_entry_system_group);
        layoutGroup(systems, dp(localizedPane.getView().getContext(), 480));
        for (int index = 0; index < systems.getChildCount(); index++) {
          RadioButton option = (RadioButton) systems.getChildAt(index);
          assertThat(option.getText()).as("%s/%s", locale, fontScale).isNotBlank();
          assertThat(option.getContentDescription()).as("%s/%s", locale, fontScale).isNotBlank();
          assertThat(option.getMaxLines()).isEqualTo(1);
          assertThat(option.getEllipsize()).isNull();
          float available =
              option.getMeasuredWidth() - option.getPaddingLeft() - option.getPaddingRight();
          assertThat(option.getPaint().measureText(option.getText().toString()))
              .as("%s/%s/%s", locale, fontScale, option.getText())
              .isLessThanOrEqualTo(available);
        }
      }
    }
  }

  @Test
  public void compactSelectorsRetainOneScrollOwnerAndDoNotRetargetAddressControls() {
    View root = pane.getView();
    assertThat(countViews(root, ScrollView.class)).isEqualTo(1);
    assertThat(root.findViewById(R.id.native_entry_address_county).isFocusable()).isFalse();
    assertThat(root.findViewById(R.id.native_entry_address_district).isFocusable()).isFalse();
    assertThat(root.findViewById(R.id.native_entry_address_county).isClickable()).isTrue();
  }

  private static TaiwanCoordinateEntryPane localizedPane(Locale locale, float fontScale) {
    Context base = RuntimeEnvironment.getApplication();
    Configuration configuration = new Configuration(base.getResources().getConfiguration());
    configuration.setLocale(locale);
    configuration.fontScale = fontScale;
    Context localized = base.createConfigurationContext(configuration);
    return new TaiwanCoordinateEntryPane(
        localized,
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> {}),
        new TaiwanEntryFormatter());
  }

  private static void layoutGroup(RadioGroup group, int width) {
    int height = dp(group.getContext(), 48);
    group.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    group.layout(0, 0, group.getMeasuredWidth(), group.getMeasuredHeight());
  }

  private static void tapBand(View target, float y) {
    int inset = dp(target.getContext(), 6);
    assertThat(y < inset || y >= target.getHeight() - inset).isTrue();
    assertThat(y).isBetween(0f, (float) target.getHeight());
    float x = target.getWidth() / 2f;
    long downTime = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
    MotionEvent up = MotionEvent.obtain(downTime, downTime + 1, MotionEvent.ACTION_UP, x, y, 0);
    try {
      assertThat(target.dispatchTouchEvent(down)).isTrue();
      assertThat(target.dispatchTouchEvent(up)).isTrue();
      shadowOf(Looper.getMainLooper()).idle();
    } finally {
      down.recycle();
      up.recycle();
    }
  }

  private static int countViews(View view, Class<?> type) {
    int count = type.isInstance(view) ? 1 : 0;
    if (!(view instanceof ViewGroup)) return count;
    ViewGroup group = (ViewGroup) view;
    for (int index = 0; index < group.getChildCount(); index++) {
      count += countViews(group.getChildAt(index), type);
    }
    return count;
  }

  private static void assertEditorContract(EditText editor, int maxLength, int action) {
    editor.setText("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
    assertThat(editor.getText()).hasSize(maxLength);
    assertThat(editor.getImeOptions() & EditorInfo.IME_MASK_ACTION).isEqualTo(action);
    assertThat(editor.getImeOptions() & EditorInfo.IME_FLAG_NO_FULLSCREEN).isNotZero();
    assertThat(editor.getImeOptions() & EditorInfo.IME_FLAG_NO_EXTRACT_UI).isNotZero();
  }

  private static Path projectPath(String... segments) {
    Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 4 && current != null; depth++, current = current.getParent()) {
      Path candidate = current;
      for (String segment : segments) candidate = candidate.resolve(segment);
      if (Files.exists(candidate)) return candidate;
    }
    throw new IllegalStateException("Unable to resolve project path");
  }

  private static org.assertj.core.data.Offset<Double> within(double value) {
    return org.assertj.core.data.Offset.offset(value);
  }

  private static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  private static int countEditTexts(View view) {
    int count = view instanceof EditText ? 1 : 0;
    if (!(view instanceof ViewGroup)) return count;
    ViewGroup group = (ViewGroup) view;
    for (int index = 0; index < group.getChildCount(); index++) {
      count += countEditTexts(group.getChildAt(index));
    }
    return count;
  }

  private static final class ImmediateDebouncer implements AddressEntryController.Debouncer {
    @Override
    public AddressEntryController.Cancellable schedule(Runnable runnable, long delayMs) {
      assertThat(delayMs).isEqualTo(250L);
      runnable.run();
      return () -> {};
    }
  }

  private static final class ResolvedAddressLookupService implements AddressLookupService {
    private final AddressAvailability availability =
        new AddressAvailability(Collections.singleton("臺北市"), true, 3L, false);

    @Override
    public LookupHandle forward(
        ForwardAddressRequest request, Consumer<ForwardAddressResult> callback) {
      DatasetIdentity dataset =
          new DatasetIdentity("臺北市", "2026-07-22", 1, "fixture-sha", "fixture");
      AddressCandidate candidate =
          new AddressCandidate(
              "fixture-1",
              "臺北市信義區市府路1號",
              request.normalizedAddress(),
              new Wgs84(25.033, 121.565, 1L, Wgs84.Source.COT_TARGET),
              AddressMatchKind.EXACT,
              0d,
              "臺北市",
              dataset);
      callback.accept(
          ForwardAddressResult.candidates(
              request.identity(), Collections.singletonList(candidate)));
      return new TestHandle();
    }

    @Override
    public LookupHandle reverse(
        ReverseAddressRequest request, Consumer<ReverseAddressResult> callback) {
      DatasetIdentity dataset =
          new DatasetIdentity("臺北市", "2026-07-22", 1, "fixture-sha", "fixture");
      AddressCandidate candidate =
          new AddressCandidate(
              "reverse-fixture",
              "臺北市信義區市府路1號",
              "臺北市信義區市府路1號",
              new Wgs84(25.0332, 121.5653, 1L, Wgs84.Source.COT_TARGET),
              AddressMatchKind.PARTIAL,
              20d,
              "臺北市",
              dataset);
      callback.accept(
          ReverseAddressResult.found(request.identity(), request.queryPoint(), candidate));
      return new TestHandle();
    }

    @Override
    public AddressAvailability availability() {
      return availability;
    }

    @Override
    public void addAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void removeAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void close() {}
  }

  private static final class TestHandle implements LookupHandle {
    private boolean cancelled;

    @Override
    public void cancel() {
      cancelled = true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }
  }
}
