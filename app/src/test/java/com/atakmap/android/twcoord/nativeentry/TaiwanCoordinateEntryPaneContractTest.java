package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
    assertThat(countEditTexts(addressPane)).isEqualTo(1);
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
  public void activateAndResolveTaipowerRoundTrip() throws Exception {
    pane.onActivate(GeoPointMetaData.wrap(new GeoPoint(23.9932, 121.6012)), true);

    EditText input = pane.getView().findViewById(R.id.native_entry_input_taipower);
    assertThat(input.getText().toString()).matches("[A-X]\\d{4} [A-J]{2}\\d{4}");
    GeoPoint resolved = pane.getGeoPointMetaData().get();
    assertThat(resolved.getLatitude()).isCloseTo(23.9932, within(0.001));
    assertThat(resolved.getLongitude()).isCloseTo(121.6012, within(0.001));
  }

  @Test
  public void oneActivationPrefillsEverySystemWithoutAutofill() throws Exception {
    GeoPointMetaData point = GeoPointMetaData.wrap(new GeoPoint(25.033611, 121.564472));

    pane.onActivate(point, true);

    EditText taipower = pane.getView().findViewById(R.id.native_entry_input_taipower);
    assertThat(taipower.getText().toString()).matches("[A-X]\\d{4} [A-J]{2}\\d{4}");

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
    RadioButton twd97Zone121 = pane.getView().findViewById(R.id.native_entry_twd97_zone_121);
    RadioButton twd97Zone119 = pane.getView().findViewById(R.id.native_entry_twd97_zone_119);
    RadioButton twd67Zone121 = pane.getView().findViewById(R.id.native_entry_twd67_zone_121);
    RadioButton twd67Zone119 = pane.getView().findViewById(R.id.native_entry_twd67_zone_119);

    pane.dispose();
    pane.dispose();
    pane.onActivate(null, true);
    pane.autofill(null);

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
