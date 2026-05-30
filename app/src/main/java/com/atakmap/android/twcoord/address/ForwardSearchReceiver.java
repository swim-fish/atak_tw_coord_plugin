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
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.twcoord.address.boundary.TownshipBoundaryFacade;
import com.atakmap.android.twcoord.address.forward.AddressCandidate;
import com.atakmap.android.twcoord.address.forward.CompassDirection;
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
  private final Button btnReset;
  private final GridLayout countyList;
  private final TextView districtLabel;
  private final GridLayout districtList;
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

  // District cells from the last onCountyChosen() render, so a 地圖中心 / 所在地 tap can auto-select
  // the resolved district (or re-select 全部).
  private TextView districtAllCell;
  private final java.util.Map<String, TextView> districtCells = new java.util.HashMap<>();

  // Map-follow: while the page is open and the county is still map-driven, re-seed the funnel when
  // the map settles over a NEW county. Detached on close.
  private boolean mapFollowAttached = false;
  private final MapEventDispatcher.MapEventDispatchListener mapSettleListener =
      e -> safeRun(this::onMapSettled);

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
    btnReset = view.findViewById(R.id.fs_btn_reset);
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
    detachMapFollow();
    controller = null;
  }

  @Override
  public void onDropDownSelectionRemoved() {}

  @Override
  public void onDropDownClose() {
    detachMapFollow();
  }

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
    // Restore the stage-1 controls: a prior open with no boundary hid them via
    // hideFromStage(1); now that base data exists (e.g. imported mid-session and
    // re-mounted lazily) they must come back, or the page renders blank.
    showStage1();

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
    // Follow the map: re-seed the county/district list when the map pans into a new county.
    attachMapFollow();
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
    btnGoto.setOnClickListener(v -> safeRun(() -> panTo(selected)));
    if (btnReset != null) btnReset.setOnClickListener(v -> safeRun(this::resetFunnel));
  }

  /** Reset the whole funnel back to the map-centre default — clears district/street/house/results. */
  private void resetFunnel() {
    if (streetInput != null) streetInput.setText("");
    houseNumber.setLength(0);
    if (houseValue != null) houseValue.setText("");
    // startSession() rebuilds the controller, re-seeds the county from the map centre, and hides
    // every downstream stage — exactly the "start over" state.
    startSession();
  }

  private void chooseCountyFromCoord(double lat, double lon, CountySource source) {
    TownshipBoundaryFacade b = safeGet(boundarySupplier);
    if (b == null || controller == null) return;
    com.atakmap.android.twcoord.address.boundary.LocalityResult loc = b.localityAt(lat, lon, 1000.0);
    if (loc.county() == null) return; // offshore — leave as-is
    // Re-point the distance anchor to the tapped reference (地圖中心 / 所在地) so subsequent candidate
    // distances are measured from here, not the session-start position.
    boolean wasAll = controller.isAllDistricts(); // chooseCounty resets this — capture first
    controller.setAnchor(lat, lon);
    controller.chooseCounty(loc.county(), source);
    renderCountyChip();
    onCountyChosen();
    if (wasAll) {
      // 全部 was selected — keep whole-county mode, don't switch to the resolved district.
      selectAllDistrictsCell();
    } else {
      // Auto-select the district the coordinate falls in, if this county has it (FR: 地圖中心 →
      // pre-pick the district so the operator drops straight to the street stage).
      autoSelectDistrict(loc.district());
    }
  }

  /** Programmatically pick {@code district} (as if tapped), if it exists in the current grid. */
  private void autoSelectDistrict(String district) {
    TextView cell = district == null ? null : districtCells.get(district);
    if (cell == null) return; // coordinate's district isn't in this county / unresolved
    markSelected(districtList, cell);
    onDistrictChosen(district);
  }

  /** Re-select the 全部 cell (as if tapped). */
  private void selectAllDistrictsCell() {
    if (districtAllCell == null) return;
    markSelected(districtList, districtAllCell);
    onAllDistrictsChosen();
  }

  // ----------------------------------------------------------------------
  // Map-follow: auto re-seed the county when the map settles over a new one
  // ----------------------------------------------------------------------

  private void attachMapFollow() {
    if (mapFollowAttached) return;
    try {
      // MAP_SETTLED only (fires once the pan stops) — avoids running point-in-polygon per frame
      // during a continuous MAP_SCROLL.
      mapView.getMapEventDispatcher().addMapEventListener(MapEvent.MAP_SETTLED, mapSettleListener);
      mapFollowAttached = true;
    } catch (Throwable t) {
      Log.w(TAG, "attachMapFollow threw", t);
    }
  }

  private void detachMapFollow() {
    if (!mapFollowAttached) return;
    try {
      mapView
          .getMapEventDispatcher()
          .removeMapEventListener(MapEvent.MAP_SETTLED, mapSettleListener);
    } catch (Throwable t) {
      Log.w(TAG, "detachMapFollow threw", t);
    }
    mapFollowAttached = false;
  }

  /**
   * The map settled. While the county is still map-driven (source {@code MAP_CENTER}, or none chosen
   * yet), re-seed the funnel to the new map-centre county — but only when it actually changes, and
   * never once the operator has manually picked a county via 清單… / 所在地 (those flip the source to
   * LIST / SELF and opt out of map-follow).
   */
  private void onMapSettled() {
    if (controller == null || !isVisible()) return;
    String currentCounty = controller.state() == null ? null : controller.state().county();
    CountySource src = controller.state() == null ? null : controller.state().countySource();
    if (currentCounty != null && src != CountySource.MAP_CENTER) return; // manual choice — respect it
    TownshipBoundaryFacade b = safeGet(boundarySupplier);
    if (b == null) return;
    double[] mc = mapCentre();
    com.atakmap.android.twcoord.address.boundary.LocalityResult loc =
        b.localityAt(mc[0], mc[1], 1000.0);
    String newCounty = loc.county();
    if (newCounty == null) return; // offshore / unresolved — keep the current funnel
    if (newCounty.equals(currentCounty)) return; // same county — list already correct
    // New county under the map centre → re-anchor distances to it as well, matching the 地圖中心 tap.
    controller.setAnchor(mc[0], mc[1]);
    controller.chooseCounty(newCounty, CountySource.MAP_CENTER);
    renderCountyChip();
    onCountyChosen();
  }

  private void showCountyList() {
    if (controller == null) return;
    countyList.removeAllViews();
    List<String> counties = controller.countyList();
    for (String cc : counties) {
      final String c = cc;
      TextView cell = gridCell(c, null);
      cell.setOnClickListener(
          v ->
              safeRun(
                  () -> {
                    markSelected(countyList, cell);
                    controller.chooseCounty(c, CountySource.LIST);
                    renderCountyChip();
                    countyList.setVisibility(View.GONE);
                    onCountyChosen();
                  }));
      countyList.addView(cell);
    }
    countyList.setVisibility(counties.isEmpty() ? View.GONE : View.VISIBLE);
  }

  private void onCountyChosen() {
    countyList.setVisibility(View.GONE);
    districtLabel.setVisibility(View.VISIBLE);
    districtList.removeAllViews();
    districtCells.clear();
    districtAllCell = null;
    List<String> districts = controller.districts();
    String suggested = controller.suggestedDistrict();
    // "全部" — search the whole county when the operator doesn't know the 鄉鎮市區.
    if (!districts.isEmpty()) {
      TextView allCell =
          gridCell(pluginContext.getString(R.string.fs_district_all), null);
      allCell.setOnClickListener(
          v ->
              safeRun(
                  () -> {
                    markSelected(districtList, allCell);
                    onAllDistrictsChosen();
                  }));
      districtList.addView(allCell);
      districtAllCell = allCell;
    }
    for (String dd : districts) {
      final String d = dd;
      boolean isSuggested = d.equals(suggested);
      TextView cell = gridCell((isSuggested ? "▶ " : "") + d, null);
      cell.setOnClickListener(
          v ->
              safeRun(
                  () -> {
                    markSelected(districtList, cell);
                    onDistrictChosen(d);
                  }));
      districtList.addView(cell);
      districtCells.put(d, cell);
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
    revealStreetStage();
  }

  private void onAllDistrictsChosen() {
    controller.chooseAllDistricts();
    revealStreetStage();
  }

  private void revealStreetStage() {
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
    double anchorLat = controller.state() != null ? controller.state().anchorLat() : 0;
    double anchorLon = controller.state() != null ? controller.state().anchorLon() : 0;
    for (AddressCandidate c : results) {
      // FR-018: reuse the confidence-tilde decorator on the address text.
      String decorated = ct.decorate(c.displayName(), c.distanceMeters());
      String label =
          pluginContext.getString(
              R.string.fs_candidate_format, decorated, (long) c.distanceMeters());
      // 16-point compass arrow from the distance anchor to this candidate.
      double bearing = CompassDirection.bearingDegrees(anchorLat, anchorLon, c.lat(), c.lon());
      candidateList.addView(
          candidateRow(
              CompassDirection.arrowRotation16(bearing),
              CompassDirection.abbrev16(bearing),
              label,
              () -> {
                // Tapping a result pans the map straight to it (no separate confirm). The page
                // stays open so the operator can pick another row or Reset.
                selected = c;
                btnGoto.setEnabled(true);
                panTo(c);
              }));
    }
    btnGoto.setVisibility(View.VISIBLE);
  }

  /**
   * A candidate row: a 16-point compass arrow (an upward "↑" rotated to the quantised bearing from
   * the distance anchor) + its abbreviation, then the address text. White on the dark panel.
   */
  private View candidateRow(float arrowDeg, String abbrev, String text, Runnable onTap) {
    float d = pluginContext.getResources().getDisplayMetrics().density;
    LinearLayout row = new LinearLayout(pluginContext);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setMinimumHeight((int) (52 * d));
    row.setClickable(true);
    row.setOnClickListener(v -> safeRun(onTap));
    row.setLayoutParams(
        new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    TextView arrow = new TextView(pluginContext);
    arrow.setText("↑");
    arrow.setTextSize(22f);
    arrow.setTextColor(0xFF66CCFF);
    arrow.setRotation(arrowDeg); // ↑ = north at 0°; rotate clockwise to the bearing
    arrow.setGravity(Gravity.CENTER);
    arrow.setLayoutParams(
        new LinearLayout.LayoutParams((int) (32 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
    row.addView(arrow);

    TextView dir = new TextView(pluginContext);
    dir.setText(abbrev);
    dir.setTextSize(11f);
    dir.setTextColor(0xFF99CCEE);
    dir.setGravity(Gravity.CENTER);
    dir.setLayoutParams(
        new LinearLayout.LayoutParams((int) (34 * d), ViewGroup.LayoutParams.WRAP_CONTENT));
    row.addView(dir);

    TextView tv = new TextView(pluginContext);
    tv.setText(text);
    tv.setTextSize(17f);
    tv.setTextColor(0xFFFFFFFF);
    int pad = (int) (8 * d);
    tv.setPadding(pad, (int) (6 * d), pad, (int) (6 * d));
    tv.setLayoutParams(
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(tv);
    return row;
  }

  /**
   * Pan the map to {@code c} (no auto-close — the page stays so the operator can pick another result
   * or Reset). Re-entrancy-guarded per Constitution VI.
   */
  private void panTo(AddressCandidate c) {
    if (c == null) return;
    if (!gotoInFlight.compareAndSet(false, true)) return;
    try {
      GeoPoint dest = new GeoPoint(c.lat(), c.lon());
      CameraController.Programmatic.panTo(mapView.getRenderer3(), dest, /*animate*/ false);
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
    // 3-column grid. Digits + 之 (the - separator) plus 巷/弄/號 so a glove operator can narrow an
    // address tail like "30巷5弄7號" without summoning the system IME (the filter matches these
    // against the full display name, see ForwardSearchController.withHouseNumber).
    String[] keys = {
      "1", "2", "3", "4", "5", "6", "7", "8", "9", "巷", "0", "弄", "號", "之", "⌫"
    };
    float d = pluginContext.getResources().getDisplayMetrics().density;
    for (String k : keys) {
      Button b = new Button(pluginContext);
      b.setText(k);
      b.setTextSize(20f);
      b.setTextColor(0xFFFFFFFF);
      // Explicit dark cell background so white digits are legible — the platform Button background
      // is light in ATAK's theme, which washed the white text out. Matches the grid cells.
      b.setBackgroundResource(R.drawable.fs_grid_cell_bg);
      b.setStateListAnimator(null); // drop the material elevation shadow on the flat cell bg
      GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
      lp.width = 0;
      lp.height = (int) (56 * d);
      lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
      int m = (int) (2 * d);
      lp.setMargins(m, m, m, m);
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

  /**
   * A single tappable cell for the 3-column county / district {@link GridLayout} (FR-016 glove
   * targets): centred white text, ≥48dp tall, even thirds via column-weight. Wrapping every 3 cells
   * is the GridLayout's {@code columnCount=3} doing the "每 3 個一列往下排" layout.
   */
  private TextView gridCell(String text, Runnable onTap) {
    float d = pluginContext.getResources().getDisplayMetrics().density;
    TextView tv = new TextView(pluginContext);
    tv.setText(text);
    tv.setTextSize(16f);
    tv.setTextColor(0xFFFFFFFF);
    tv.setGravity(Gravity.CENTER);
    tv.setMinHeight((int) (50 * d));
    int pad = (int) (6 * d);
    tv.setPadding(pad, pad, pad, pad);
    tv.setClickable(true);
    tv.setBackgroundResource(R.drawable.fs_grid_cell_bg); // button look + pressed/selected states
    tv.setOnClickListener(v -> safeRun(onTap));
    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
    lp.width = 0; // share each row evenly across the 3 columns
    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
    lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
    int m = (int) (3 * d);
    lp.setMargins(m, m, m, m);
    tv.setLayoutParams(lp);
    return tv;
  }

  /** Highlight {@code chosen} (selected state) and clear the rest of {@code grid}'s cells. */
  private void markSelected(GridLayout grid, View chosen) {
    if (grid == null) return;
    for (int i = 0; i < grid.getChildCount(); i++) {
      View child = grid.getChildAt(i);
      child.setSelected(child == chosen);
    }
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

  /** Restore the stage-1 county controls hidden by a prior {@link #hideFromStage}(1). */
  private void showStage1() {
    if (countyChip != null) countyChip.setVisibility(View.VISIBLE);
    if (btnSelf != null) btnSelf.setVisibility(View.VISIBLE);
    if (btnMapCenter != null) btnMapCenter.setVisibility(View.VISIBLE);
    if (btnList != null) btnList.setVisibility(View.VISIBLE);
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
