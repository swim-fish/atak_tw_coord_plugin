package com.atakmap.android.twcoord.address.lookup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable bundled Chunghwa Post ordering reference for active locality selectors. */
public final class PostalLocalityCatalog {
  public static final class District {
    private final String name;
    private final String postalPrefix;
    private final int postalOrder;

    District(String name, String postalPrefix, int postalOrder) {
      this.name = requireText(name, "district name");
      if (postalPrefix == null || !postalPrefix.matches("\\d{3}")) {
        throw new IllegalArgumentException("postalPrefix must contain three digits");
      }
      if (postalOrder <= 0) throw new IllegalArgumentException("postalOrder must be positive");
      this.postalPrefix = postalPrefix;
      this.postalOrder = postalOrder;
    }

    public String name() {
      return name;
    }

    public String postalPrefix() {
      return postalPrefix;
    }

    public int postalOrder() {
      return postalOrder;
    }
  }

  public static final class County {
    private final String name;
    private final int selectorOrder;
    private final List<District> districts;
    private final Map<String, District> districtsByFoldedName;

    County(String name, int selectorOrder, List<District> districts) {
      this.name = requireText(name, "county name");
      if (selectorOrder <= 0) throw new IllegalArgumentException("selectorOrder must be positive");
      this.selectorOrder = selectorOrder;
      List<District> copy = new ArrayList<>(Objects.requireNonNull(districts, "districts"));
      Map<String, District> index = new LinkedHashMap<>();
      for (District district : copy) {
        String key = StreetTextNormaliser.fold(district.name());
        if (index.put(key, district) != null) {
          throw new IllegalArgumentException("duplicate district " + district.name());
        }
      }
      this.districts = Collections.unmodifiableList(copy);
      this.districtsByFoldedName = Collections.unmodifiableMap(index);
    }

    public String name() {
      return name;
    }

    public int selectorOrder() {
      return selectorOrder;
    }

    public List<District> districts() {
      return districts;
    }

    public District district(String name) {
      return districtsByFoldedName.get(StreetTextNormaliser.fold(name == null ? "" : name));
    }
  }

  private static final PostalLocalityCatalog UNAVAILABLE =
      new PostalLocalityCatalog("", "", Collections.emptyList(), false);

  private final String datasetId;
  private final String retrievedOn;
  private final List<County> counties;
  private final Map<String, County> countiesByFoldedName;
  private final boolean available;

  PostalLocalityCatalog(
      String datasetId, String retrievedOn, List<County> counties, boolean available) {
    this.datasetId = datasetId == null ? "" : datasetId;
    this.retrievedOn = retrievedOn == null ? "" : retrievedOn;
    List<County> copy = new ArrayList<>(Objects.requireNonNull(counties, "counties"));
    Map<String, County> index = new LinkedHashMap<>();
    java.util.Set<Integer> orders = new java.util.HashSet<>();
    for (County county : copy) {
      String key = StreetTextNormaliser.fold(county.name());
      if (index.put(key, county) != null || !orders.add(county.selectorOrder())) {
        throw new IllegalArgumentException("duplicate county name or selector order");
      }
    }
    this.counties = Collections.unmodifiableList(copy);
    this.countiesByFoldedName = Collections.unmodifiableMap(index);
    this.available = available;
  }

  public static PostalLocalityCatalog unavailable() {
    return UNAVAILABLE;
  }

  public static PostalLocalityCatalog testing(County... counties) {
    return new PostalLocalityCatalog("testing", "testing", Arrays.asList(counties), true);
  }

  public static County county(String name, int selectorOrder, District... districts) {
    return new County(name, selectorOrder, Arrays.asList(districts));
  }

  public static District district(String name, String postalPrefix, int postalOrder) {
    return new District(name, postalPrefix, postalOrder);
  }

  public boolean available() {
    return available;
  }

  public String datasetId() {
    return datasetId;
  }

  public String retrievedOn() {
    return retrievedOn;
  }

  public List<County> counties() {
    return counties;
  }

  public County county(String name) {
    return countiesByFoldedName.get(StreetTextNormaliser.fold(name == null ? "" : name));
  }

  private static String requireText(String value, String label) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label);
    return value.trim();
  }
}
