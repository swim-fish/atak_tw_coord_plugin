package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public final class PostalLocalityCatalogTest {

  @Test
  public void bundledCatalogLoadsOfficialCountyAndDistrictOrder() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    PostalLocalityCatalog catalog;
    try (InputStream input =
        context.getAssets().open("address/chunghwa_post_postal_localities.json")) {
      catalog = PostalLocalityCatalogLoader.load(input);
    }

    assertThat(catalog.available()).isTrue();
    assertThat(catalog.counties()).hasSize(22);
    assertThat(catalog.counties().get(0).name()).isEqualTo("基隆市");
    PostalLocalityCatalog.County taichung = catalog.county("台中市");
    assertThat(taichung).isNotNull();
    assertThat(taichung.district("西屯區").postalPrefix()).isEqualTo("407");
  }

  @Test
  public void invalidCatalogIsRejectedWithoutPartialRows() {
    String invalid =
        "{\"schemaVersion\":1,\"datasetId\":\"x\",\"retrievedOn\":\"2026-07-24\","
            + "\"countyCount\":1,\"districtCount\":2,\"counties\":[{"
            + "\"name\":\"台中市\",\"selectorOrder\":1,\"districts\":["
            + "{\"name\":\"西屯區\",\"postalPrefix\":\"407\",\"postalOrder\":1},"
            + "{\"name\":\"西屯區\",\"postalPrefix\":\"40X\",\"postalOrder\":2}]}]}";

    assertThatThrownBy(
            () ->
                PostalLocalityCatalogLoader.load(
                    new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void orderingKeepsOnlyActiveRowsPromotesOneAndAppendsUnmatched() {
    PostalLocalityCatalog catalog =
        PostalLocalityCatalog.testing(
            PostalLocalityCatalog.county(
                "台中市",
                2,
                PostalLocalityCatalog.district("中區", "400", 1),
                PostalLocalityCatalog.district("西屯區", "407", 2)),
            PostalLocalityCatalog.county(
                "新北市", 1, PostalLocalityCatalog.district("板橋區", "220", 1)));

    LocalitySelectorSnapshot counties =
        LocalitySelectorOrdering.counties(
            catalog, 9L, 3L, Arrays.asList("臺中市", "自訂縣", "新北市"), "台中市", null);

    assertThat(names(counties)).containsExactly("台中市", "新北市", "自訂縣");
    assertThat(counties.choices().get(0).promoted()).isTrue();
    assertThat(counties.unmatchedPostalCount()).isEqualTo(1);

    LocalitySelectorSnapshot districts =
        LocalitySelectorOrdering.districts(
            catalog, 9L, 3L, "臺中市", Arrays.asList("西屯區", "自訂區", "中區"), "西屯區", null);

    assertThat(names(districts)).containsExactly("西屯區", "中區", "自訂區");
    assertThat(districts.choices().get(0).postalKey()).isEqualTo("407");
    assertThat(districts.unmatchedPostalCount()).isEqualTo(1);
  }

  private static java.util.List<String> names(LocalitySelectorSnapshot snapshot) {
    java.util.List<String> result = new java.util.ArrayList<>();
    for (LocalitySelectorSnapshot.Choice choice : snapshot.choices()) {
      result.add(choice.name());
    }
    return result;
  }
}
