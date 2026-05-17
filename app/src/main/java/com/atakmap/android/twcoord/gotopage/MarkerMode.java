package com.atakmap.android.twcoord.gotopage;

/**
 * Operator-selectable mode for the Submit action on the GoTo input page.
 *
 * <p>{@link #MOVE_ONLY} (the default) only pans the camera to the resolved point. The other
 * variants additionally drop an ATAK marker of the corresponding CoT type at that point, using
 * ATAK's standard {@code PlacePointTool} placement path so the resulting marker has the same
 * long-press → radial menu behaviour the operator already knows.
 *
 * <p>CoT type codes match ATAK's standard radial-menu drop-pin types. They are deliberately the
 * generic "ground" subtype for each affiliation so the operator can refine via the standard CoT
 * details dialog after placement if desired.
 */
public enum MarkerMode {
  MOVE_ONLY(null),
  // b-m-p-s-m = "Spot Map" — the canonical ATAK user-placed generic pin type, used by the
  // helloworld SDK sample's drop-pin path. b-m-p-w is a route waypoint and is NOT independently
  // removable (it belongs to ATAK's Route Manager), so we deliberately avoid it for "Waypoint"
  // here. The operator can still convert this marker to a Route waypoint via the standard radial
  // menu's "Add to route" affordance after placement.
  WAYPOINT("b-m-p-s-m"),
  FRIENDLY("a-f-G"),
  HOSTILE("a-h-G"),
  NEUTRAL("a-n-G"),
  UNKNOWN("a-u-G"),
  SPI("b-m-p-s-p-i"),
  // Enum value retains the legacy name MISSION_POINT to avoid churn across layout ids and
  // existing references; the user-facing label is now "GoTo Pin / 目的地 / 目的地ピン" and the
  // CoT type is b-m-p-w-GOTO — exactly what ATAK's native GoToMapTool uses for its destination
  // marker (cf. javap of com.atakmap.android.routes.GoToMapTool, line 18 of createPoint). This
  // makes the resulting marker indistinguishable from ATAK's own native GoTo destination pin,
  // including the auto-generated `S.NN.HHmmss` callsign produced by PlacePointTool.
  MISSION_POINT("b-m-p-w-GOTO"),
  // Feature 003: operator-picked custom icon from any installed iconset. CoT type is
  // b-m-p-s-m (Spot Map, identical to WAYPOINT) so the marker carries no affiliation
  // semantics; identity is fully expressed by the iconset path applied via
  // PlacePointTool.MarkerCreator.setIconPath at placement time. See ADR-0010 D4 and
  // contracts/marker-mode-v2.md.
  CUSTOM_ICON("b-m-p-s-m");

  private final String cotType;

  MarkerMode(String cotType) {
    this.cotType = cotType;
  }

  /** Returns the CoT type code to pass into {@code PlacePointTool.MarkerCreator.setType(...)}. */
  public String cotType() {
    return cotType;
  }

  /** True for every mode except {@link #MOVE_ONLY}. */
  public boolean dropsMarker() {
    return cotType != null;
  }

  /**
   * True only for {@link #CUSTOM_ICON}. Submit-path branching uses this to decide whether to append
   * a {@code .setIconPath(...)} call to the {@code MarkerCreator} chain.
   */
  public boolean requiresIconPath() {
    return this == CUSTOM_ICON;
  }

  /** Convenience alias for {@code requiresIconPath()} — readability in view-layer switch arms. */
  public boolean isCustomIcon() {
    return this == CUSTOM_ICON;
  }
}
