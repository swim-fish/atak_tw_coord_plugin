package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.Test;

public final class LocalitySelectorOrderingTest {

  @Test
  public void equalPostalPrefixUsesOfficialOrderBeforeNormalizedName() {
    PostalLocalityCatalog catalog =
        PostalLocalityCatalog.testing(
            PostalLocalityCatalog.county(
                "測試縣",
                1,
                PostalLocalityCatalog.district("乙區", "999", 1),
                PostalLocalityCatalog.district("甲區", "999", 2)));

    LocalitySelectorSnapshot snapshot =
        LocalitySelectorOrdering.districts(
            catalog, 1L, 1L, "測試縣", Arrays.asList("甲區", "乙區"), null, null);

    assertThat(snapshot.choices())
        .extracting(LocalitySelectorSnapshot.Choice::name)
        .containsExactly("乙區", "甲區");
  }

  @Test
  public void unavailableCatalogFallsBackToNormalizedNameWithoutDroppingActiveValues() {
    LocalitySelectorSnapshot snapshot =
        LocalitySelectorOrdering.counties(
            PostalLocalityCatalog.unavailable(), 1L, 1L, Arrays.asList("臺中市", "新北市"), null, null);

    assertThat(snapshot.postalCatalogAvailable()).isFalse();
    assertThat(snapshot.unmatchedPostalCount()).isEqualTo(2);
    assertThat(snapshot.choices())
        .extracting(LocalitySelectorSnapshot.Choice::name)
        .containsExactly("臺中市", "新北市");
  }
}
