package com.atakmap.android.twcoord.coord;

public enum CoordinateUnit {
  TAIPOWER("unit_tag_taipower"),
  TWD97("unit_tag_twd97"),
  TWD67("unit_tag_twd67");

  private final String unitTagKey;

  CoordinateUnit(String unitTagKey) {
    this.unitTagKey = unitTagKey;
  }

  public String unitTagKey() {
    return unitTagKey;
  }
}
