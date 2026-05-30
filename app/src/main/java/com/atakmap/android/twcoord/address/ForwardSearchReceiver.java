package com.atakmap.android.twcoord.address;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade;
import com.atakmap.android.twcoord.address.forward.AddressCandidate;
import com.atakmap.android.twcoord.address.forward.CountySource;
import com.atakmap.android.twcoord.address.forward.ForwardSearchController;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.CameraController;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Feature 006 — the county-scoped forward-search page (FR-016 glove UX). A thin {@link
 * DropDownReceiver} view over {@link ForwardSearchController}: it reads the map-centre / self-marker
 * anchors, drives the county → 鄉鎮市區 → street → pin funnel, and on confirm pans the map via the
 * same {@code CameraController.Programmatic.panTo} call {@code TwCoordGotoView} uses (no auto-pan —
 * GoTo only on the explicit button, FR-013).
 *
 * <p>Every host-callable callback wraps {@link Throwable} → {@code Log.w} (Constitution VI). The
 * controller never throws either, so a corrupt dataset degrades to an empty list, not a crash.
 */
public final class ForwardSearchReceiver extends DropDownReceiver implements OnStateListener {

  private static final String TAG = "ForwardSearchReceiver";
  private static final int CANDIDATE_LIMIT = 30;

  private final Context pluginContext;
  private final MapView mapView;
  private final Supplier<TownshipBoundaryFacade> boundarySupplier;
  private final Supplier<ActiveDatasetRegistry> registrySupplier;
  private final Supplier<ConfidenceThresholds> confidenceSupplier;
  private final View view;

  // ---- inflated refs ----
  private final TextView boundaryMissing;
  private final TextView countyChip;
  private final Button btnSelf;
  private final Button btnMapCenter;
  private final Button btnList;
  private final LinearLayout countyList;
  private final TextView districtLabel;
  private final LinearLayout districtList;
  private final TextView streetLabel;
  private final LinearLayout streetRow;
  private final EditText streetInput;
  private final Button btnSearch;
  private final TextView houseValue;
  private final GridLayout keypad;
  private final TextView emptyState;
  private final LinearLayout candidateList;
  private final Button btnGoto;

  private final AtomicBoolean gotoInFlight = new AtomicBoolean(false);

  private ForwardSearchController controller;
  private AddressCandidate selected;
  private final StringBuilder houseNumber = new StringBuilder();

  public ForwardSearchReceiver(
      MapView mapView,
      Context pluginContext,
      Supplier<TownshipBoundaryFacade> boundarySupplier,
      Supplier<ActiveDatasetRegistry> registrySupplier,
      Supplier<ConfidenceThresholds> confidenceSupplier) {
    super(mapView);
    this.mapView = mapView;
    this.pluginContext = pluginContext;
    this.boundarySupplier = boundarySupplier;
    this.registrySupplier = registrySupplier;
    this.confidenceSupplier = confidenceSupplier;
    LayoutInflater inflater = LayoutInflater.from(pluginContext);
    this.view = inflater.inflate(R.layout.forward_search_page, null);

    boundaryMissing = view.findViewById(R.id.fs_boundary_missing);
    countyChip = view.findViewById(R.id.fs_county_chip);
    btnSelf = view.findViewById(R.id.fs_btn_self);
    btnMapCenter = view.findViewById(R.id.fs_btn_mapcenter);
    btnList = view.findViewById(R.id.fs_btn_list);
    countyList = view.findViewById(R.id.fs_county_list);
    districtLabel = view.findViewById(R.id.fs_stage_district_label);
    districtList = view.findViewById(R.id.fs_district_list);
    streetLabel = view.findViewById(R.id.fs_stage_street_label);
    streetRow = view.findViewById(R.id.fs_street_row);
    streetInput = view.findViewById(R.id.fs_street_input);
    btnSearch = view.findViewById(R.id.fs_btn_search);
    houseValue = view.findViewById(R.id.fs_house_value);
    keypad = view.findViewById(R.id.fs_keypad);
    emptyState = view.findViewById(R.id.fs_empty_state);
    candidateList = view.findViewById(R.id.fs_candidate_list);
    btnGoto = view.findViewById(R.id.fs_btn_goto);

    wireStaticButtons();
    buildKeypad();
  }

  // ----------------------------------------------------------------------
  // DropDownReceiver lifecycle
  // ----------------------------------------------------------------------

  @Override
  public void onReceive(Context context, Intent intent) {
    try {
      if (intent == null
          || !ForwardSearchIntents.ACTION_SHOW_FORWARD_SEARCH.equals(intent.getAction())) {
        return;
      }
      if (isVisible()) return;
      startSession();
      showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT);
    } catch (Throwable t) {
      Log.w(TAG, "onReceive threw", t);
    }
  }

  @Override
  public void disposeImpl() {
    controller = null;
  }

  @Override
  public void onDropDownSelectionRemoved() {}

  @Override
  public void onDropDownClose() {}

  @Override
  public void onDropDownVisible(boolean visible) {}

  @Override
  public void onDropDownSizeChanged(double width, double height) {}

  // ----------------------------------------------------------------------
  // Session + funnel
  // ----------------------------------------------------------------------

  private void startSession() {
    TownshipBoundaryFacade boundary = safeGet(boundarySupplier);
    Function<String, AddressDatabaseFacade> facadeForCounty =
        county -> {
          ActiveDatasetRegistry reg = safeGet(registrySupplier);
          if (reg == null) return null;
          CountyActiveDataset ds = reg.snapshot().get(county);
          return ds == null ? null : ds.facade();
        };
    controller = new ForwardSearchController(boundary, facadeForCounty);
    selected = null;
    houseNumber.setLength(0);

    if (boundary == null) {
      boundaryMissing.setVisibility(View.VISIBLE);
      hideFromStage(1);
      return;
    }
    boundaryMissing.setVisibility(View.GONE);

    double[] mc = mapCentre();
    double[] self = selfMarker();
    controller.seedCounty(mc[0], mc[1], box(self, 0), box(self, 1));
    renderCountyChip();
    // Reset downstream stages.
    countyList.setVisibility(View.GONE);
    candidateList.removeAllViews();
    emptyState.setVisibility(View.GONE);
    btnGoto.setVisibility(View.GONE);
    if (controller.state().county() != null) {
      onCountyChosen();
    } else {
      hideFromStage(2);
      // Prompt the operator to pick from the list.
      showCountyList();
    }
  }

  private void wireStaticButtons() {
    btnMapCenter.setOnClickListener(v -> safeRun(() -> {
      double[] mc = mapCentre();
      chooseCountyFromCoord(mc[0], mc[1], CountySource.MAP_CENTER);
    }));
    btnSelf.setOnClickListener(v -> safeRun(() -> {
      double[] s = selfMarker();
      if (s == null) return;
      chooseCountyFromCoord(s[0], s[1], CountySource.SELF);
    }));
    btnList.setOnClickListener(v -> safeRun(this::showCountyList));
    btnSearch.setOnClickListener(v -> safeRun(this::runSearch));
    btnGoto.setOnClickListener(v -> safeRun(this::doGoto));
  }

  private void chooseCountyFromCoord(double lat, double lon, CountySource source) {
    TownshipBoundaryFacade b = safeGet(boundarySupplier);
    if (b == null || controller == null) return;
    com.atakmap.android.twcoord.address.boundary.LocalityResult loc = b.localityAt(lat, lon, 1000.0);
    if (loc.county() == null) return; // offshore — leave as-is
    controller.chooseCounty(loc.county(), source);
    renderCountyChip();
    onCountyChosen();
  }

  private void showCountyList() {
    if (controller == null) return;
    countyList.removeAllViews();
    List<String> counties = controller.countyList();
    for (String c : counties) {
      countyList.addView(bigRow(c, () -> {
        controller.chooseCounty(c, CountySource.LIST);
        renderCountyChip();
        countyList.setVisibility(View.GONE);
        onCountyChosen();
      }));
    }
    countyList.setVisibility(counties.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void onCountyChosen() {
    countyList.setVisibility(View.GONE);
    districtLabel.setVisibility(View.VISIBLE);
    districtList.removeAllViews();
    List<String> districts = controller.districts();
    String suggested = controller.suggestedDistrict();
    for (String d : districts) {
      boolean isSuggested = d.equals(suggested);
      districtList.addView(
          bigRow((isSuggested ? "▶ " : "") + d, () -> onDistrictChosen(d)));
    }
    districtList.setVisibility(districts.isEmpty() ? View.GONE : View.VISIBLE);
    // hide stages 3/4 until a district is picked
    streetLabel.setVisibility(View.GONE);
    streetRow.setVisibility(View.GONE);
    houseValue.setVisibility(View.GONE);
    keypad.setVisibility(View.GONE);
    candidateList.removeAllViews();
    emptyState.setVisibility(View.GONE);
    btnGoto.setVisibility(View.GONE);
  }

  private void onDistrictChosen(String district) {
    controller.chooseDistrict(district);
    streetLabel.setVisibility(View.VISIBLE);
    streetRow.setVisibility(View.VISIBLE);
    candidateList.removeAllViews();
    emptyState.setVisibility(View.GONE);
    btnGoto.setVisibility(View.GONE);
  }

  private void runSearch() {
    if (controller == null) return;
    houseNumber.setLength(0);
    houseValue.setText("");
    String fragment = streetInput.getText() == null ? "" : streetInput.getText().toString();
    List<AddressCandidate> results = controller.search(fragment, CANDIDATE_LIMIT);
    renderCandidates(results);
    // Reveal the house-number keypad once a street search has run.
    houseValue.setVisibility(View.VISIBLE);
    keypad.setVisibility(View.VISIBLE);
  }

  private void renderCandidates(List<AddressCandidate> results) {
    candidateList.removeAllViews();
    selected = null;
    btnGoto.setEnabled(false);
    btnGoto.setVisibility(View.GONE);
    if (results == null || results.isEmpty()) {
      emptyState.setVisibility(View.VISIBLE);
      return;
    }
    emptyState.setVisibility(View.GONE);
    ConfidenceThresholds ct = safeGet(confidenceSupplier);
    if (ct == null) ct = ConfidenceThresholds.TIGHT;
    for (AddressCandidate c : results) {
      // FR-018: reuse the confidence-tilde decorator on the address text.
      String decorated = ct.decorate(c.displayName(), c.distanceMeters());
      String label =
          pluginContext.getString(
              R.string.fs_candidate_format, decorated, (long) c.distanceMeters());
      candidateList.addView(
          bigRow(
              label,
              () -> {
                selected = c;
                btnGoto.setEnabled(true);
              }));
    }
    btnGoto.setVisibility(View.VISIBLE);
  }

  private void doGoto() {
    if (selected == null) return;
    if (!gotoInFlight.compareAndSet(false, true)) return; // re-entrancy guard (Constitution VI)
    try {
      GeoPoint dest = new GeoPoint(selected.lat(), selected.lon());
      CameraController.Programmatic.panTo(mapView.getRenderer3(), dest, /*animate*/ false);
      closeDropDown();
    } catch (Throwable t) {
      Log.w(TAG, "GoTo panTo threw", t);
    } finally {
      gotoInFlight.set(false);
    }
  }

  // ----------------------------------------------------------------------
  // Numeric keypad (FR-016: large digit buttons, no system IME)
  // ----------------------------------------------------------------------

  private void buildKeypad() {
    String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "之", "0", "⌫"};
    for (String k : keys) {
      Button b = new Button(pluginContext);
      b.setText(k);
      b.setTextSize(20f);
      GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
      lp.width = 0;
      lp.height = (int) (56 * pluginContext.getResources().getDisplayMetrics().density);
      lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      b.setLayoutParams(lp);
      b.setOnClickListener(v -> safeRun(() -> onKeypad(k)));
      keypad.addView(b);
    }
  }

  private void onKeypad(String k) {
    if (controller == null) return;
    if ("⌫".equals(k)) {
      if (houseNumber.length() > 0) houseNumber.setLength(houseNumber.length() - 1);
    } else {
      houseNumber.append(k);
    }
    houseValue.setText(houseNumber.toString());
    List<AddressCandidate> results =
        controller.withHouseNumber(houseNumber.toString(), CANDIDATE_LIMIT);
    renderCandidates(results);
  }

  // ----------------------------------------------------------------------
  // Rendering helpers
  // ----------------------------------------------------------------------

  private void renderCountyChip() {
    String county = controller != null && controller.state() != null
        ? controller.state().county()
        : null;
    if (county == null) {
      countyChip.setText(R.string.fs_county_none);
      return;
    }
    String district = controller.suggestedDistrict();
    countyChip.setText(
        pluginContext.getString(
            R.string.fs_county_confirm_format, county, district == null ? "" : district));
  }

  private TextView bigRow(String text, Runnable onTap) {
    TextView tv = new TextView(pluginContext);
    tv.setText(text);
    tv.setTextSize(17f);
    tv.setMinHeight((int) (52 * pluginContext.getResources().getDisplayMetrics().density));
    tv.setGravity(Gravity.CENTER_VERTICAL);
    int pad = (int) (12 * pluginContext.getResources().getDisplayMetrics().density);
    tv.setPadding(pad, pad / 2, pad, pad / 2);
    tv.setClickable(true);
    tv.setOnClickListener(v -> safeRun(onTap));
    tv.setLayoutParams(
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    return tv;
  }

  private void hideFromStage(int stage) {
    if (stage <= 1) {
      countyChip.setVisibility(View.GONE);
      btnSelf.setVisibility(View.GONE);
      btnMapCenter.setVisibility(View.GONE);
      btnList.setVisibility(View.GONE);
    }
    countyList.setVisibility(View.GONE);
    districtLabel.setVisibility(View.GONE);
    districtList.setVisibility(View.GONE);
    streetLabel.setVisibility(View.GONE);
    streetRow.setVisibility(View.GONE);
    houseValue.setVisibility(View.GONE);
    keypad.setVisibility(View.GONE);
    candidateList.removeAllViews();
    emptyState.setVisibility(View.GONE);
    btnGoto.setVisibility(View.GONE);
  }

  // ----------------------------------------------------------------------
  // Anchors + small utils
  // ----------------------------------------------------------------------

  private double[] mapCentre() {
    try {
      GeoPointMetaData c = mapView.getCenterPoint();
      if (c != null && c.get() != null) {
        GeoPoint p = c.get();
        return new double[] {p.getLatitude(), p.getLongitude()};
      }
    } catch (Throwable t) {
      Log.w(TAG, "mapCentre threw", t);
    }
    return new double[] {0, 0};
  }

  private double[] selfMarker() {
    try {
      Marker self = mapView.getSelfMarker();
      if (self != null) {
        GeoPoint p = self.getPoint();
        if (p != null && (Math.abs(p.getLatitude()) > 1e-6 || Math.abs(p.getLongitude()) > 1e-6)) {
          return new double[] {p.getLatitude(), p.getLongitude()};
        }
      }
    } catch (Throwable t) {
      Log.w(TAG, "selfMarker threw", t);
    }
    return null;
  }

  private static Double box(double[] arr, int idx) {
    return arr == null ? null : arr[idx];
  }

  private static <T> T safeGet(Supplier<T> s) {
    try {
      return s == null ? null : s.get();
    } catch (Throwable t) {
      Log.w(TAG, "supplier threw", t);
      return null;
    }
  }

  private static void safeRun(Runnable r) {
    try {
      if (r != null) r.run();
    } catch (Throwable t) {
      Log.w(TAG, "ui handler threw", t);
    }
  }
}
