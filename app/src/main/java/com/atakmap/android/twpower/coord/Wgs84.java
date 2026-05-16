package com.atakmap.android.twpower.coord;

import java.util.Objects;

/** Immutable WGS84 fix from either the map centre or the device's self-marker. */
public final class Wgs84 {

  public enum Source {
    MAP_CENTRE,
    DEVICE_LOCATION
  }

  private final double latitudeDeg;
  private final double longitudeDeg;
  private final long timestampEpochMs;
  private final Source source;

  public Wgs84(double latitudeDeg, double longitudeDeg, long timestampEpochMs, Source source) {
    if (latitudeDeg < -90.0 || latitudeDeg > 90.0) {
      throw new IllegalArgumentException("latitudeDeg out of range: " + latitudeDeg);
    }
    if (longitudeDeg < -180.0 || longitudeDeg > 180.0) {
      throw new IllegalArgumentException("longitudeDeg out of range: " + longitudeDeg);
    }
    if (timestampEpochMs <= 0) {
      throw new IllegalArgumentException("timestampEpochMs must be > 0");
    }
    this.latitudeDeg = latitudeDeg;
    this.longitudeDeg = longitudeDeg;
    this.timestampEpochMs = timestampEpochMs;
    this.source = Objects.requireNonNull(source, "source");
  }

  public double latitudeDeg() {
    return latitudeDeg;
  }

  public double longitudeDeg() {
    return longitudeDeg;
  }

  public long timestampEpochMs() {
    return timestampEpochMs;
  }

  public Source source() {
    return source;
  }
}
