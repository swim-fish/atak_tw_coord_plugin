package com.atakmap.android.twcoord.nativeentry;

/** Durable presentation modes for the native Taipower coordinate editor. */
public enum TaipowerInputMode {
  SINGLE_FIELD,
  SPLIT_FIELDS;

  public static TaipowerInputMode fromStoredValue(String value) {
    if (value == null || value.trim().isEmpty()) return SINGLE_FIELD;
    try {
      return valueOf(value);
    } catch (IllegalArgumentException failure) {
      return SINGLE_FIELD;
    }
  }
}
