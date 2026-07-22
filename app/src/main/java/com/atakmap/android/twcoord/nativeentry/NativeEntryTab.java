package com.atakmap.android.twcoord.nativeentry;

import com.atakmap.android.twcoord.coord.CoordinateUnit;

/** Four UI tabs in the native Taiwan pane; Address is not a coordinate-system value. */
public enum NativeEntryTab {
  TAIPOWER,
  TWD97,
  TWD67,
  ADDRESS;

  public static NativeEntryTab fromCoordinateUnit(CoordinateUnit unit) {
    if (unit == CoordinateUnit.TWD97) return TWD97;
    if (unit == CoordinateUnit.TWD67) return TWD67;
    return TAIPOWER;
  }

  public CoordinateUnit coordinateUnitOrNull() {
    if (this == TWD97) return CoordinateUnit.TWD97;
    if (this == TWD67) return CoordinateUnit.TWD67;
    if (this == TAIPOWER) return CoordinateUnit.TAIPOWER;
    return null;
  }
}
