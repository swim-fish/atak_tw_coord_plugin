package com.atakmap.android.twcoord.address.boundary;

import java.util.Objects;

/**
 * Outcome of resolving a coordinate against the township boundary layer (data-model §2.2). Four
 * states distinguished by which fields are present:
 *
 * <ul>
 *   <li><b>Full</b> — county≠null, district≠null, approx=false (the common case)
 *   <li><b>Snapped</b> — county≠null, district≠null, approx=true (coastal/reclaimed: district was
 *       snapped to the nearest polygon within tolerance, not strictly covering)
 *   <li><b>County-only</b> — county≠null, district=null (clipped area, no covering district + no
 *       snap hit)
 *   <li><b>None</b> — county=null (offshore / outside all installed boundaries)
 * </ul>
 */
public final class LocalityResult {

  private static final LocalityResult NONE = new LocalityResult(null, null, false);

  private final String county; // nullable
  private final String district; // nullable
  private final boolean approx;

  private LocalityResult(String county, String district, boolean approx) {
    this.county = county;
    this.district = district;
    this.approx = approx;
  }

  /** county + district strictly cover the point. */
  public static LocalityResult full(String county, String district) {
    return new LocalityResult(
        Objects.requireNonNull(county, "county"),
        Objects.requireNonNull(district, "district"),
        false);
  }

  /** county + nearest district within tolerance (coastal/reclaimed); {@code approx=true}. */
  public static LocalityResult snapped(String county, String district) {
    return new LocalityResult(
        Objects.requireNonNull(county, "county"),
        Objects.requireNonNull(district, "district"),
        true);
  }

  /** county known, no covering district. */
  public static LocalityResult countyOnly(String county) {
    return new LocalityResult(Objects.requireNonNull(county, "county"), null, false);
  }

  /** Outside every boundary. */
  public static LocalityResult none() {
    return NONE;
  }

  /** Parent 縣市, or {@code null} if outside all boundaries. */
  public String county() {
    return county;
  }

  /** 鄉鎮市區, or {@code null} if no covering/snapped district. */
  public String district() {
    return district;
  }

  /** {@code true} when {@link #district()} was snapped within tolerance rather than strictly covering. */
  public boolean approx() {
    return approx;
  }

  public boolean hasCounty() {
    return county != null;
  }

  public boolean hasDistrict() {
    return district != null;
  }

  public boolean isNone() {
    return county == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LocalityResult)) return false;
    LocalityResult that = (LocalityResult) o;
    return approx == that.approx
        && Objects.equals(county, that.county)
        && Objects.equals(district, that.district);
  }

  @Override
  public int hashCode() {
    return Objects.hash(county, district, approx);
  }

  @Override
  public String toString() {
    if (isNone()) return "LocalityResult{None}";
    return "LocalityResult{"
        + county
        + (district == null ? "" : " " + district)
        + (approx ? " ~approx" : "")
        + "}";
  }
}
