package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.android.twcoord.address.boundary.LocalityResult;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic intersection and ordering of active values with the postal reference. */
public final class LocalitySelectorOrdering {
  private LocalitySelectorOrdering() {}

  public static LocalitySelectorSnapshot counties(
      PostalLocalityCatalog catalog,
      long datasetRevision,
      long generation,
      List<String> activeCounties,
      String promotedCounty,
      Wgs84 anchor) {
    Map<String, String> active = unique(activeCounties);
    List<Ranked> ranked = new ArrayList<>();
    int unmatched = 0;
    for (String name : active.values()) {
      PostalLocalityCatalog.County matched = catalog.county(name);
      if (matched == null) unmatched++;
      ranked.add(
          new Ranked(
              matched == null ? name : matched.name(),
              matched == null ? null : Integer.toString(matched.selectorOrder()),
              matched == null ? Integer.MAX_VALUE : matched.selectorOrder(),
              matched == null ? Integer.MAX_VALUE : matched.selectorOrder(),
              isSame(name, promotedCounty)));
    }
    ranked.sort(rankedComparator());
    return snapshot(
        LocalitySelectorSnapshot.Kind.COUNTY,
        datasetRevision,
        generation,
        null,
        anchor,
        promotedCounty == null ? null : LocalityResult.countyOnly(promotedCounty),
        ranked,
        catalog.available(),
        unmatched);
  }

  public static LocalitySelectorSnapshot districts(
      PostalLocalityCatalog catalog,
      long datasetRevision,
      long generation,
      String selectedCounty,
      List<String> activeDistricts,
      String promotedDistrict,
      Wgs84 anchor) {
    PostalLocalityCatalog.County county = catalog.county(selectedCounty);
    Map<String, String> active = unique(activeDistricts);
    List<Ranked> ranked = new ArrayList<>();
    int unmatched = 0;
    for (String name : active.values()) {
      PostalLocalityCatalog.District matched = county == null ? null : county.district(name);
      if (matched == null) unmatched++;
      ranked.add(
          new Ranked(
              matched == null ? name : matched.name(),
              matched == null ? null : matched.postalPrefix(),
              matched == null ? Integer.MAX_VALUE : Integer.parseInt(matched.postalPrefix()),
              matched == null ? Integer.MAX_VALUE : matched.postalOrder(),
              isSame(name, promotedDistrict)));
    }
    ranked.sort(rankedComparator());
    LocalityResult locality =
        promotedDistrict == null ? null : LocalityResult.full(selectedCounty, promotedDistrict);
    return snapshot(
        LocalitySelectorSnapshot.Kind.DISTRICT,
        datasetRevision,
        generation,
        selectedCounty,
        anchor,
        locality,
        ranked,
        catalog.available(),
        unmatched);
  }

  private static LocalitySelectorSnapshot snapshot(
      LocalitySelectorSnapshot.Kind kind,
      long revision,
      long generation,
      String selectedCounty,
      Wgs84 anchor,
      LocalityResult locality,
      List<Ranked> ranked,
      boolean catalogAvailable,
      int unmatched) {
    List<LocalitySelectorSnapshot.Choice> choices = new ArrayList<>();
    for (Ranked row : ranked) {
      choices.add(new LocalitySelectorSnapshot.Choice(row.name, row.postalKey, row.promoted));
    }
    return new LocalitySelectorSnapshot(
        kind,
        revision,
        generation,
        selectedCounty,
        anchor,
        locality,
        choices,
        catalogAvailable,
        unmatched);
  }

  private static Comparator<Ranked> rankedComparator() {
    return Comparator.comparing((Ranked row) -> !row.promoted)
        .thenComparingInt(row -> row.order)
        .thenComparingInt(row -> row.officialOrder)
        .thenComparing(row -> StreetTextNormaliser.fold(row.name));
  }

  private static Map<String, String> unique(List<String> names) {
    Map<String, String> result = new LinkedHashMap<>();
    if (names == null) return Collections.emptyMap();
    for (String name : names) {
      if (name == null || name.trim().isEmpty()) continue;
      result.putIfAbsent(StreetTextNormaliser.fold(name.trim()), name.trim());
    }
    return result;
  }

  private static boolean isSame(String first, String second) {
    return second != null
        && StreetTextNormaliser.fold(first).equals(StreetTextNormaliser.fold(second));
  }

  private static final class Ranked {
    final String name;
    final String postalKey;
    final int order;
    final int officialOrder;
    final boolean promoted;

    Ranked(String name, String postalKey, int order, int officialOrder, boolean promoted) {
      this.name = name;
      this.postalKey = postalKey;
      this.order = order;
      this.officialOrder = officialOrder;
      this.promoted = promoted;
    }
  }
}
