package com.atakmap.android.twcoord.nativeentry;

import android.content.Context;
import android.os.Trace;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import com.atakmap.android.gui.coordinateentry.CoordinateEntryPane;
import com.atakmap.android.twcoord.address.lookup.AddressComponents;
import com.atakmap.android.twcoord.address.lookup.AddressDraft;
import com.atakmap.android.twcoord.address.lookup.AddressInputMode;
import com.atakmap.android.twcoord.address.lookup.AddressLookupService;
import com.atakmap.android.twcoord.address.lookup.AddressResolution;
import com.atakmap.android.twcoord.address.lookup.AddressValidation;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorSnapshot;
import com.atakmap.android.twcoord.address.lookup.NoDataAddressLookupService;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.android.twcoord.prefs.PreferenceStore;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Plugin-owned implementation of ATAK's public native coordinate-entry pane contract. */
public final class TaiwanCoordinateEntryPane implements CoordinateEntryPane {

  interface TraceSink {
    void begin(String section);

    void end();
  }

  interface StringResolver {
    String get(int resourceId);
  }

  public static final String UID = "com.atakmap.android.twcoord.coordinateentry.taiwan";
  public static final String META_ADDRESS_DISPLAY = "twcoord.address.display";
  public static final String META_ADDRESS_NORMALIZED = "twcoord.address.normalized";
  public static final String META_ADDRESS_SOURCE = "twcoord.address.resolution_source";
  public static final String META_DATASET_COUNTY = "twcoord.address.dataset.county";
  public static final String META_DATASET_DATE = "twcoord.address.dataset.data_date";
  public static final String META_DATASET_SCHEMA = "twcoord.address.dataset.schema_version";
  public static final String META_DATASET_SHA = "twcoord.address.dataset.sha256";
  public static final String META_RECORD_LAT = "twcoord.address.record.latitude";
  public static final String META_RECORD_LON = "twcoord.address.record.longitude";
  private static final String TAG = "TaiwanCoordinatePane";
  private static final TraceSink ANDROID_TRACE =
      new TraceSink() {
        @Override
        public void begin(String section) {
          Trace.beginSection(section);
        }

        @Override
        public void end() {
          Trace.endSection();
        }
      };

  private final TaiwanEntryController controller;
  private final AddressEntryController addressController;
  private final AddressCandidateDialog candidateDialog;
  private final AddressLocalityDialog localityDialog;
  private final TaiwanEntryFormatter formatter;
  private final TraceSink trace;
  private final StringResolver strings;
  private final View root;
  private final RadioGroup systemGroup;
  private final RadioGroup twd97ZoneGroup;
  private final RadioGroup twd67ZoneGroup;
  private final RadioButton taipowerButton;
  private final RadioButton twd97Button;
  private final RadioButton twd67Button;
  private final RadioButton addressButton;
  private final View taipowerPane;
  private final View twd97Pane;
  private final View twd67Pane;
  private final View addressPane;
  private final EditText taipowerInput;
  private final EditText twd97Easting;
  private final EditText twd97Northing;
  private final EditText twd67Easting;
  private final EditText twd67Northing;
  private final EditText addressInput;
  private final View addressFullRow;
  private final View addressStructured;
  private final EditText addressCounty;
  private final EditText addressDistrict;
  private final EditText addressRoad;
  private final EditText addressTail;
  private final Button addressMode;
  private final Button addressChoose;
  private final RadioButton twd97Zone121;
  private final RadioButton twd97Zone119;
  private final RadioButton twd67Zone121;
  private final RadioButton twd67Zone119;
  private final TextView twd67Advisory;
  private final TextView status;
  private final Map<EditText, TextWatcher> watchers = new LinkedHashMap<>();

  private OnChangedListener changedListener;
  private boolean rendering;
  private boolean disposed;
  private Runnable addressManagerNavigator = () -> {};

  public TaiwanCoordinateEntryPane(Context pluginContext, PreferenceStore preferences) {
    this(pluginContext, pluginContext, preferences, new NoDataAddressLookupService(Runnable::run));
  }

  public TaiwanCoordinateEntryPane(
      Context pluginContext,
      Context windowContext,
      PreferenceStore preferences,
      AddressLookupService lookupService) {
    this(pluginContext, windowContext, preferences, lookupService, () -> null);
  }

  private TaiwanCoordinateEntryPane(
      Context pluginContext,
      Context windowContext,
      PreferenceStore preferences,
      AddressLookupService lookupService,
      Supplier<Wgs84> forwardAnchorSupplier) {
    this(
        pluginContext,
        windowContext,
        new TaiwanEntryController(
            Objects.requireNonNull(preferences, "preferences").getNativeEntryLastUnit(),
            preferences::setNativeEntryLastUnit),
        new AddressEntryController(
            Objects.requireNonNull(lookupService, "lookupService"),
            preferences::getResultOrdering,
            Objects.requireNonNull(forwardAnchorSupplier, "forwardAnchorSupplier")),
        new TaiwanEntryFormatter());
  }

  public TaiwanCoordinateEntryPane(
      Context pluginContext,
      Context windowContext,
      PreferenceStore preferences,
      AddressLookupService lookupService,
      Runnable addressManagerNavigator) {
    this(
        pluginContext,
        windowContext,
        preferences,
        lookupService,
        addressManagerNavigator,
        () -> null);
  }

  public TaiwanCoordinateEntryPane(
      Context pluginContext,
      Context windowContext,
      PreferenceStore preferences,
      AddressLookupService lookupService,
      Runnable addressManagerNavigator,
      Supplier<Wgs84> forwardAnchorSupplier) {
    this(pluginContext, windowContext, preferences, lookupService, forwardAnchorSupplier);
    this.addressManagerNavigator =
        Objects.requireNonNull(addressManagerNavigator, "addressManagerNavigator");
  }

  TaiwanCoordinateEntryPane(
      Context context, TaiwanEntryController controller, TaiwanEntryFormatter formatter) {
    this(
        context,
        context,
        controller,
        new AddressEntryController(new NoDataAddressLookupService(Runnable::run)),
        formatter,
        ANDROID_TRACE,
        context::getString);
  }

  TaiwanCoordinateEntryPane(
      Context context,
      TaiwanEntryController controller,
      TaiwanEntryFormatter formatter,
      TraceSink trace) {
    this(
        context,
        context,
        controller,
        new AddressEntryController(new NoDataAddressLookupService(Runnable::run)),
        formatter,
        trace,
        context::getString);
  }

  TaiwanCoordinateEntryPane(
      Context context,
      TaiwanEntryController controller,
      TaiwanEntryFormatter formatter,
      TraceSink trace,
      StringResolver strings) {
    this(
        context,
        context,
        controller,
        new AddressEntryController(new NoDataAddressLookupService(Runnable::run)),
        formatter,
        trace,
        strings);
  }

  TaiwanCoordinateEntryPane(
      Context pluginContext,
      Context windowContext,
      TaiwanEntryController controller,
      AddressEntryController addressController,
      TaiwanEntryFormatter formatter) {
    this(
        pluginContext,
        windowContext,
        controller,
        addressController,
        formatter,
        ANDROID_TRACE,
        pluginContext::getString);
  }

  private TaiwanCoordinateEntryPane(
      Context context,
      Context windowContext,
      TaiwanEntryController controller,
      AddressEntryController addressController,
      TaiwanEntryFormatter formatter,
      TraceSink trace,
      StringResolver strings) {
    Objects.requireNonNull(context, "context");
    this.controller = Objects.requireNonNull(controller, "controller");
    this.addressController = Objects.requireNonNull(addressController, "addressController");
    this.formatter = Objects.requireNonNull(formatter, "formatter");
    this.trace = Objects.requireNonNull(trace, "trace");
    this.strings = Objects.requireNonNull(strings, "strings");
    root = LayoutInflater.from(context).inflate(R.layout.taiwan_coordinate_entry_pane, null, false);
    systemGroup = requireView(R.id.native_entry_system_group);
    twd97ZoneGroup = requireView(R.id.native_entry_twd97_zone_group);
    twd67ZoneGroup = requireView(R.id.native_entry_twd67_zone_group);
    taipowerButton = requireView(R.id.native_entry_system_taipower);
    twd97Button = requireView(R.id.native_entry_system_twd97);
    twd67Button = requireView(R.id.native_entry_system_twd67);
    addressButton = requireView(R.id.native_entry_system_address);
    taipowerPane = requireView(R.id.native_entry_pane_taipower);
    twd97Pane = requireView(R.id.native_entry_pane_twd97);
    twd67Pane = requireView(R.id.native_entry_pane_twd67);
    addressPane = requireView(R.id.native_entry_pane_address);
    taipowerInput = requireView(R.id.native_entry_input_taipower);
    twd97Easting = requireView(R.id.native_entry_twd97_easting);
    twd97Northing = requireView(R.id.native_entry_twd97_northing);
    twd67Easting = requireView(R.id.native_entry_twd67_easting);
    twd67Northing = requireView(R.id.native_entry_twd67_northing);
    addressInput = requireView(R.id.native_entry_address_full);
    addressFullRow = requireView(R.id.native_entry_address_full_row);
    addressStructured = requireView(R.id.native_entry_address_structured);
    addressCounty = requireView(R.id.native_entry_address_county);
    addressDistrict = requireView(R.id.native_entry_address_district);
    addressRoad = requireView(R.id.native_entry_address_road);
    addressTail = requireView(R.id.native_entry_address_tail);
    addressMode = requireView(R.id.native_entry_address_mode);
    addressChoose = requireView(R.id.native_entry_address_choose);
    twd97Zone121 = requireView(R.id.native_entry_twd97_zone_121);
    twd97Zone119 = requireView(R.id.native_entry_twd97_zone_119);
    twd67Zone121 = requireView(R.id.native_entry_twd67_zone_121);
    twd67Zone119 = requireView(R.id.native_entry_twd67_zone_119);
    twd67Advisory = requireView(R.id.native_entry_twd67_advisory);
    status = requireView(R.id.native_entry_status);
    candidateDialog = new AddressCandidateDialog(windowContext, context, addressController);
    localityDialog = new AddressLocalityDialog(windowContext, context, addressController);

    addWatcher(taipowerInput, value -> controller.setTaipowerText(value, true));
    addWatcher(twd97Easting, value -> controller.setTwdEasting(CoordinateUnit.TWD97, value, true));
    addWatcher(
        twd97Northing, value -> controller.setTwdNorthing(CoordinateUnit.TWD97, value, true));
    addWatcher(twd67Easting, value -> controller.setTwdEasting(CoordinateUnit.TWD67, value, true));
    addWatcher(
        twd67Northing, value -> controller.setTwdNorthing(CoordinateUnit.TWD67, value, true));
    addWatcher(addressInput, value -> addressController.editFull(value, true));
    addWatcher(addressRoad, ignored -> editStructuredFromViews());
    addWatcher(addressTail, ignored -> editStructuredFromViews());

    systemGroup.setOnCheckedChangeListener(
        (group, checkedId) -> {
          if (rendering || disposed) return;
          boolean tracing = beginTrace("TWCoord.native.switch");
          try {
            NativeEntryTab tab = tabForButton(checkedId);
            controller.selectTab(tab, true);
            renderControllerState();
          } finally {
            endTrace(tracing);
          }
        });
    twd97ZoneGroup.setOnCheckedChangeListener(
        (group, checkedId) -> {
          if (rendering || disposed || checkedId == View.NO_ID) return;
          boolean tracing = beginTrace("TWCoord.native.switch");
          try {
            controller.setZone(
                CoordinateUnit.TWD97,
                checkedId == R.id.native_entry_twd97_zone_119 ? 119 : 121,
                true);
            renderControllerState();
          } finally {
            endTrace(tracing);
          }
        });
    twd67ZoneGroup.setOnCheckedChangeListener(
        (group, checkedId) -> {
          if (rendering || disposed || checkedId == View.NO_ID) return;
          boolean tracing = beginTrace("TWCoord.native.switch");
          try {
            controller.setZone(
                CoordinateUnit.TWD67,
                checkedId == R.id.native_entry_twd67_zone_119 ? 119 : 121,
                true);
            renderControllerState();
          } finally {
            endTrace(tracing);
          }
        });
    controller.setOnHumanChange(this::notifyHostChanged);
    addressController.setOnHumanChange(this::notifyHostChanged);
    addressController.setOnStateChanged(() -> root.post(this::renderControllerState));
    addressChoose.setOnClickListener(ignored -> candidateDialog.show());
    addressCounty.setOnClickListener(
        ignored -> localityDialog.show(LocalitySelectorSnapshot.Kind.COUNTY));
    addressDistrict.setOnClickListener(
        ignored -> {
          if (!addressController.draft().components().countyCity().isEmpty()) {
            localityDialog.show(LocalitySelectorSnapshot.Kind.DISTRICT);
          }
        });
    status.setOnClickListener(
        ignored -> {
          if (disposed || addressController.validation() != AddressValidation.NO_DATASET) return;
          try {
            addressManagerNavigator.run();
          } catch (RuntimeException failure) {
            Log.w(TAG, "address manager navigation failed", failure);
          }
        });
    addressMode.setOnClickListener(
        ignored -> {
          if (disposed) return;
          AddressInputMode current = addressController.draft().mode();
          addressController.switchMode(
              current == AddressInputMode.FULL
                  ? AddressInputMode.STRUCTURED
                  : AddressInputMode.FULL);
          renderControllerState();
        });
    renderControllerState();
  }

  @SuppressWarnings("unchecked")
  private <T extends View> T requireView(int id) {
    T view = root.findViewById(id);
    if (view == null) throw new IllegalStateException("Missing native-entry view id " + id);
    return view;
  }

  private void addWatcher(EditText editText, Consumer<String> consumer) {
    TextWatcher watcher =
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence value, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable value) {
            if (rendering || disposed) return;
            consumer.accept(value == null ? "" : value.toString());
            renderStatus(false);
          }
        };
    editText.addTextChangedListener(watcher);
    watchers.put(editText, watcher);
  }

  @Override
  public String getUID() {
    return UID;
  }

  @Override
  public String getName() {
    try {
      return strings.get(R.string.native_entry_taiwan);
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "CoordinateEntryPane name lookup failed", e);
      return "Taiwan";
    }
  }

  @Override
  public View getView() {
    return root;
  }

  @Override
  public void onActivate(GeoPointMetaData currentPoint, boolean editable) {
    if (disposed) return;
    String section = currentPoint == null ? "TWCoord.native.clear" : "TWCoord.native.activate";
    boolean tracing = beginTrace(section);
    try {
      if (currentPoint == null && controller.activeTab() == NativeEntryTab.ADDRESS) {
        addressController.setEditable(editable);
        addressController.clear(false);
      } else {
        Wgs84 point = toWgs84(currentPoint);
        controller.activate(point, editable);
        addressController.setEditable(editable);
        if (point != null) addressController.activate(point, editable);
      }
      renderControllerState();
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "CoordinateEntryPane activation failed", e);
      invalidateActivationState(editable);
      renderFailedOperationState("activation");
    } finally {
      endTrace(tracing);
    }
  }

  @Override
  public GeoPointMetaData getGeoPointMetaData() throws CoordinateException {
    boolean tracing = beginTrace("TWCoord.native.validate");
    try {
      if (disposed) throw coordinateException(TaiwanEntryController.Validation.DISPOSED);
      if (controller.activeTab() == NativeEntryTab.ADDRESS) {
        AddressResolution address = addressController.resolution();
        if (address == null) {
          renderStatus(true);
          throw addressCoordinateException();
        }
        GeoPointMetaData metadata =
            GeoPointMetaData.wrap(
                new GeoPoint(
                    address.resolvedPoint().latitudeDeg(), address.resolvedPoint().longitudeDeg()));
        metadata.setMetaValue(META_ADDRESS_DISPLAY, address.displayAddress());
        metadata.setMetaValue(META_ADDRESS_NORMALIZED, address.normalizedAddress());
        metadata.setMetaValue(META_ADDRESS_SOURCE, address.source().name());
        metadata.setMetaValue(META_DATASET_COUNTY, address.datasetIdentity().county());
        setNonEmptyMetadata(metadata, META_DATASET_DATE, address.datasetIdentity().dataDate());
        metadata.setMetaValue(META_DATASET_SCHEMA, address.datasetIdentity().schemaVersion());
        setNonEmptyMetadata(metadata, META_DATASET_SHA, address.datasetIdentity().fileSha256());
        metadata.setMetaValue(META_RECORD_LAT, address.recordPoint().latitudeDeg());
        metadata.setMetaValue(META_RECORD_LON, address.recordPoint().longitudeDeg());
        return metadata;
      }
      Wgs84 resolved = controller.resolvedOrNull();
      if (resolved == null) {
        renderStatus(true);
        throw coordinateException(controller.validation());
      }
      return GeoPointMetaData.wrap(new GeoPoint(resolved.latitudeDeg(), resolved.longitudeDeg()));
    } catch (CoordinateException e) {
      throw e;
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "CoordinateEntryPane validation failed", e);
      throw new CoordinateException("Taiwan coordinate unavailable", asException(e));
    } finally {
      endTrace(tracing);
    }
  }

  @Override
  public void autofill(GeoPointMetaData point) {
    if (disposed) return;
    boolean tracing = beginTrace("TWCoord.native.autofill");
    try {
      if (controller.activeTab() == NativeEntryTab.ADDRESS) {
        addressController.autofill(toWgs84(point));
      } else {
        controller.autofill(toWgs84(point));
      }
      renderControllerState();
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "CoordinateEntryPane Auto Fill failed", e);
      renderFailedOperationState("Auto Fill");
    } finally {
      endTrace(tracing);
    }
  }

  @Override
  public String format(GeoPointMetaData point) {
    if (disposed) return null;
    boolean tracing = beginTrace("TWCoord.native.format");
    try {
      if (controller.activeTab() == NativeEntryTab.ADDRESS) {
        if (point == null) return null;
        Object display = point.getMetaData(META_ADDRESS_DISPLAY);
        return display instanceof String ? (String) display : null;
      }
      return controller.format(toWgs84(point), formatter);
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "CoordinateEntryPane formatting failed", e);
      return null;
    } finally {
      endTrace(tracing);
    }
  }

  @Override
  public void setOnChangedListener(OnChangedListener listener) {
    if (!disposed) changedListener = listener;
  }

  @Override
  public void dispose() {
    if (disposed) return;
    disposed = true;
    changedListener = null;
    safeDisposeStep("address dialog", candidateDialog::dispose);
    safeDisposeStep("locality dialog", localityDialog::dispose);
    safeDisposeStep("address controller", addressController::dispose);
    safeDisposeStep("controller", controller::dispose);
    safeDisposeStep("system listener", () -> systemGroup.setOnCheckedChangeListener(null));
    safeDisposeStep("TWD97 listener", () -> twd97ZoneGroup.setOnCheckedChangeListener(null));
    safeDisposeStep("TWD67 listener", () -> twd67ZoneGroup.setOnCheckedChangeListener(null));
    for (Map.Entry<EditText, TextWatcher> entry : watchers.entrySet()) {
      safeDisposeStep(
          "text listener", () -> entry.getKey().removeTextChangedListener(entry.getValue()));
      safeDisposeStep("input", () -> entry.getKey().setEnabled(false));
    }
    safeDisposeStep("Taipower selector", () -> taipowerButton.setEnabled(false));
    safeDisposeStep("TWD97 selector", () -> twd97Button.setEnabled(false));
    safeDisposeStep("TWD67 selector", () -> twd67Button.setEnabled(false));
    safeDisposeStep("Address selector", () -> addressButton.setEnabled(false));
    safeDisposeStep("Address mode", () -> addressMode.setEnabled(false));
    safeDisposeStep("Address chooser", () -> addressChoose.setEnabled(false));
    safeDisposeStep("TWD97 zone 121", () -> twd97Zone121.setEnabled(false));
    safeDisposeStep("TWD97 zone 119", () -> twd97Zone119.setEnabled(false));
    safeDisposeStep("TWD67 zone 121", () -> twd67Zone121.setEnabled(false));
    safeDisposeStep("TWD67 zone 119", () -> twd67Zone119.setEnabled(false));
  }

  private void renderControllerState() {
    boolean tracing = beginTrace("TWCoord.native.render");
    rendering = true;
    try {
      NativeEntryTab active = controller.activeTab();
      if (active == null) {
        active = NativeEntryTab.fromCoordinateUnit(controller.activeUnit());
      }
      systemGroup.check(buttonForTab(active));
      taipowerPane.setVisibility(active == NativeEntryTab.TAIPOWER ? View.VISIBLE : View.GONE);
      twd97Pane.setVisibility(active == NativeEntryTab.TWD97 ? View.VISIBLE : View.GONE);
      twd67Pane.setVisibility(active == NativeEntryTab.TWD67 ? View.VISIBLE : View.GONE);
      addressPane.setVisibility(active == NativeEntryTab.ADDRESS ? View.VISIBLE : View.GONE);

      setText(taipowerInput, controller.taipowerText());
      setText(twd97Easting, controller.eastingText(CoordinateUnit.TWD97));
      setText(twd97Northing, controller.northingText(CoordinateUnit.TWD97));
      setText(twd67Easting, controller.eastingText(CoordinateUnit.TWD67));
      setText(twd67Northing, controller.northingText(CoordinateUnit.TWD67));
      AddressDraft addressDraft = addressController.draft();
      AddressComponents components = addressDraft.components();
      setText(addressInput, addressDraft.rawAddress());
      setText(addressCounty, components.countyCity());
      setText(addressDistrict, components.districtTownship());
      setText(addressRoad, components.roadLocality());
      setText(addressTail, addressDraft.structuredTail());
      boolean fullAddressMode = addressDraft.mode() == AddressInputMode.FULL;
      addressFullRow.setVisibility(fullAddressMode ? View.VISIBLE : View.GONE);
      addressStructured.setVisibility(fullAddressMode ? View.GONE : View.VISIBLE);
      addressMode.setText(
          safeString(
              fullAddressMode
                  ? R.string.native_entry_address_mode_structured
                  : R.string.native_entry_address_mode_full,
              fullAddressMode ? "Structured fields" : "Single field"));
      twd97ZoneGroup.check(
          controller.zone(CoordinateUnit.TWD97) == 119
              ? R.id.native_entry_twd97_zone_119
              : R.id.native_entry_twd97_zone_121);
      twd67ZoneGroup.check(
          controller.zone(CoordinateUnit.TWD67) == 119
              ? R.id.native_entry_twd67_zone_119
              : R.id.native_entry_twd67_zone_121);
      twd67Advisory.setVisibility(
          active == NativeEntryTab.TWD67 && controller.zone(CoordinateUnit.TWD67) == 119
              ? View.VISIBLE
              : View.GONE);
      addressChoose.setVisibility(
          active == NativeEntryTab.ADDRESS
                  && addressController.validation() == AddressValidation.AMBIGUOUS
              ? View.VISIBLE
              : View.GONE);
      setEditable(controller.isEditable() && !disposed);
      renderStatus(false);
    } finally {
      rendering = false;
      endTrace(tracing);
    }
  }

  private void setEditable(boolean editable) {
    taipowerButton.setEnabled(editable);
    twd97Button.setEnabled(editable);
    twd67Button.setEnabled(editable);
    addressButton.setEnabled(editable);
    taipowerInput.setEnabled(editable);
    twd97Easting.setEnabled(editable);
    twd97Northing.setEnabled(editable);
    twd67Easting.setEnabled(editable);
    twd67Northing.setEnabled(editable);
    addressInput.setEnabled(editable);
    addressCounty.setEnabled(editable);
    boolean districtSelectable =
        editable && !addressController.draft().components().countyCity().isEmpty();
    addressDistrict.setEnabled(districtSelectable);
    addressDistrict.setClickable(districtSelectable);
    addressRoad.setEnabled(editable);
    addressTail.setEnabled(editable);
    addressMode.setEnabled(!disposed);
    addressChoose.setEnabled(editable);
    twd97Zone121.setEnabled(editable);
    twd97Zone119.setEnabled(editable);
    twd67Zone121.setEnabled(editable);
    twd67Zone119.setEnabled(editable);
  }

  private void editStructuredFromViews() {
    addressController.editStructured(
        addressCounty.getText().toString(),
        addressDistrict.getText().toString(),
        addressRoad.getText().toString(),
        addressTail.getText().toString(),
        true);
  }

  private void renderFailedOperationState(String operation) {
    try {
      renderControllerState();
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError renderFailure) {
      Log.w(
          TAG, "CoordinateEntryPane failed to render safe state after " + operation, renderFailure);
    }
  }

  private void invalidateActivationState(boolean editable) {
    try {
      controller.invalidateActivation(editable);
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError invalidationFailure) {
      Log.w(TAG, "CoordinateEntryPane failed to invalidate activation state", invalidationFailure);
    }
  }

  private void renderStatus(boolean checked) {
    if (controller.activeTab() == NativeEntryTab.ADDRESS) {
      AddressValidation validation = addressController.validation();
      if (!checked
          && validation != AddressValidation.LOOKUP_PENDING
          && validation != AddressValidation.NO_DATASET
          && validation != AddressValidation.NO_MATCH
          && validation != AddressValidation.AMBIGUOUS
          && validation != AddressValidation.FAILURE) {
        status.setText("");
        status.setVisibility(View.GONE);
        return;
      }
      status.setText(addressMessageFor(validation));
      status.setVisibility(View.VISIBLE);
      return;
    }
    TaiwanEntryController.Validation validation = controller.validation();
    if (!checked && validation != TaiwanEntryController.Validation.UNREPRESENTABLE) {
      status.setText("");
      status.setVisibility(View.GONE);
      return;
    }
    status.setText(messageFor(validation));
    status.setVisibility(View.VISIBLE);
  }

  private CoordinateException coordinateException(TaiwanEntryController.Validation validation) {
    return new CoordinateException(
        messageFor(validation), new IllegalArgumentException("Native entry state: " + validation));
  }

  private CoordinateException addressCoordinateException() {
    AddressValidation validation = addressController.validation();
    return new CoordinateException(
        addressMessageFor(validation),
        new IllegalArgumentException("Native address state: " + validation));
  }

  private String addressMessageFor(AddressValidation validation) {
    switch (validation) {
      case LOOKUP_PENDING:
        return strings.get(R.string.native_entry_address_loading);
      case NO_DATASET:
        return strings.get(R.string.native_entry_address_no_dataset);
      case NO_MATCH:
        return strings.get(R.string.native_entry_address_no_match);
      case AMBIGUOUS:
        return strings.get(R.string.native_entry_address_ambiguous);
      case FAILURE:
        return strings.get(R.string.native_entry_address_failure);
      case DISPOSED:
        return strings.get(R.string.native_entry_address_disposed);
      case READ_ONLY:
        return strings.get(R.string.native_entry_address_read_only);
      case EMPTY:
      case PARTIAL:
      case READY_TO_LOOKUP:
      case RESOLVED:
      default:
        return strings.get(R.string.native_entry_address_unresolved);
    }
  }

  private String messageFor(TaiwanEntryController.Validation validation) {
    switch (validation) {
      case EMPTY:
        return strings.get(R.string.native_entry_error_empty);
      case INCOMPLETE:
        return strings.get(R.string.native_entry_error_incomplete);
      case BAD_ZONE:
        return strings.get(R.string.native_entry_error_bad_zone);
      case OUT_OF_COVERAGE:
        return strings.get(R.string.native_entry_error_out_of_coverage);
      case UNREPRESENTABLE:
        return strings.get(R.string.native_entry_error_unrepresentable);
      case DISPOSED:
        return strings.get(R.string.native_entry_error_disposed);
      case MALFORMED:
      case VALID:
      default:
        return strings.get(R.string.native_entry_error_malformed);
    }
  }

  private void notifyHostChanged() {
    OnChangedListener listener = changedListener;
    if (listener == null || disposed) return;
    try {
      listener.onChange(this);
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "CoordinateEntryPane listener threw", e);
    }
  }

  private boolean beginTrace(String section) {
    try {
      trace.begin(section);
      return true;
    } catch (RuntimeException e) {
      Log.w(TAG, "Trace begin failed for " + section, e);
      return false;
    }
  }

  private void endTrace(boolean tracing) {
    if (!tracing) return;
    try {
      trace.end();
    } catch (RuntimeException e) {
      Log.w(TAG, "Trace end failed", e);
    }
  }

  private static void safeDisposeStep(String step, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError e) {
      Log.w(TAG, "CoordinateEntryPane dispose failed at " + step, e);
    }
  }

  private static Exception asException(Throwable failure) {
    return failure instanceof Exception ? (Exception) failure : new Exception(failure);
  }

  private static void setText(EditText view, String value) {
    if (!value.contentEquals(view.getText())) view.setText(value);
  }

  private static void setNonEmptyMetadata(GeoPointMetaData metadata, String key, String value) {
    if (value != null && !value.isEmpty()) metadata.setMetaValue(key, value);
  }

  private String safeString(int resourceId, String fallback) {
    try {
      return strings.get(resourceId);
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError failure) {
      Log.w(TAG, "native entry string unavailable", failure);
      return fallback;
    }
  }

  private static int buttonForTab(NativeEntryTab tab) {
    switch (tab) {
      case TWD97:
        return R.id.native_entry_system_twd97;
      case TWD67:
        return R.id.native_entry_system_twd67;
      case ADDRESS:
        return R.id.native_entry_system_address;
      case TAIPOWER:
      default:
        return R.id.native_entry_system_taipower;
    }
  }

  private static NativeEntryTab tabForButton(int id) {
    if (id == R.id.native_entry_system_twd97) return NativeEntryTab.TWD97;
    if (id == R.id.native_entry_system_twd67) return NativeEntryTab.TWD67;
    if (id == R.id.native_entry_system_address) return NativeEntryTab.ADDRESS;
    return NativeEntryTab.TAIPOWER;
  }

  private static Wgs84 toWgs84(GeoPointMetaData metadata) {
    if (metadata == null || metadata.get() == null) return null;
    GeoPoint point = metadata.get();
    if (!GeoPoint.isValid(point.getLatitude(), point.getLongitude())) return null;
    return new Wgs84(
        point.getLatitude(),
        point.getLongitude(),
        System.currentTimeMillis(),
        Wgs84.Source.MAP_CENTRE);
  }
}
