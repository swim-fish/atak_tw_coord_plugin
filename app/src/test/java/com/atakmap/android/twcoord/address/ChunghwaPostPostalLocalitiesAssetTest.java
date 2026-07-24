package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import android.content.Context;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class ChunghwaPostPostalLocalitiesAssetTest {

  private static final String ASSET = "address/chunghwa_post_postal_localities.json";

  private static final List<String> EXPECTED_COUNTY_ORDER =
      Arrays.asList(
          "基隆市", "臺北市", "新北市", "桃園市", "新竹市", "新竹縣", "苗栗縣", "臺中市", "彰化縣", "南投縣", "雲林縣", "嘉義市", "嘉義縣",
          "臺南市", "高雄市", "屏東縣", "臺東縣", "花蓮縣", "宜蘭縣", "澎湖縣", "金門縣", "連江縣");

  @Test
  public void assetRetainsTraceableOfficialSourceMetadata() throws Exception {
    JSONObject root = loadAsset();

    assertThat(root.getInt("schemaVersion")).isEqualTo(1);
    assertThat(root.getString("datasetId")).isEqualTo("chunghwa-post-taiwan-postal-localities");
    assertThat(root.getString("retrievedOn")).matches("\\d{4}-\\d{2}-\\d{2}");

    JSONObject sources = root.getJSONObject("sources");
    assertOfficialSource(sources.getJSONObject("countySelectorOrder"));
    assertOfficialSource(sources.getJSONObject("postalPrefixes"));
    assertOfficialSource(sources.getJSONObject("localityCenters"));
    assertThat(sources.getJSONObject("postalPrefixes").getString("sha256")).matches("[0-9a-f]{64}");
    assertThat(sources.getJSONObject("localityCenters").getString("sha256"))
        .matches("[0-9a-f]{64}");
  }

  @Test
  public void assetContainsCompleteStableCountyAndDistrictOrdering() throws Exception {
    JSONObject root = loadAsset();
    JSONArray counties = root.getJSONArray("counties");
    Set<String> localityNames = new HashSet<>();
    int districtCount = 0;

    assertThat(root.getInt("countyCount")).isEqualTo(EXPECTED_COUNTY_ORDER.size());
    assertThat(counties.length()).isEqualTo(EXPECTED_COUNTY_ORDER.size());

    for (int countyIndex = 0; countyIndex < counties.length(); countyIndex++) {
      JSONObject county = counties.getJSONObject(countyIndex);
      assertThat(county.getString("name")).isEqualTo(EXPECTED_COUNTY_ORDER.get(countyIndex));
      assertThat(county.getInt("selectorOrder")).isEqualTo(countyIndex + 1);

      JSONArray districts = county.getJSONArray("districts");
      assertThat(districts.length()).isPositive();
      int previousPostalPrefix = -1;
      for (int districtIndex = 0; districtIndex < districts.length(); districtIndex++) {
        JSONObject district = districts.getJSONObject(districtIndex);
        String name = district.getString("name");
        String postalPrefix = district.getString("postalPrefix");
        int numericPrefix = Integer.parseInt(postalPrefix);
        JSONObject center = district.getJSONObject("center");

        assertThat(name).isNotBlank();
        assertThat(postalPrefix).matches("\\d{3}");
        assertThat(numericPrefix).isGreaterThanOrEqualTo(previousPostalPrefix);
        assertThat(district.getInt("postalOrder")).isEqualTo(districtIndex + 1);
        // The official table also includes the Pratas, Spratly, and Diaoyutai entries.
        assertThat(center.getDouble("latitude")).isBetween(10.0, 27.0);
        assertThat(center.getDouble("longitude")).isBetween(115.0, 124.0);
        assertThat(localityNames.add(county.getString("name") + name)).isTrue();

        previousPostalPrefix = numericPrefix;
        districtCount++;
      }
    }

    assertThat(districtCount).isEqualTo(root.getInt("districtCount"));
    assertThat(districtCount).isEqualTo(371);
  }

  @Test
  public void representativePostalPrefixesMatchOfficialRows() throws Exception {
    JSONObject root = loadAsset();

    assertThat(postalPrefixOf(root, "臺北市", "中正區")).isEqualTo("100");
    assertThat(postalPrefixOf(root, "新北市", "板橋區")).isEqualTo("220");
    assertThat(postalPrefixOf(root, "臺中市", "西屯區")).isEqualTo("407");
    assertThat(postalPrefixOf(root, "新竹市", "東區")).isEqualTo("300");
    assertThat(postalPrefixOf(root, "嘉義市", "西區")).isEqualTo("600");
    assertThat(postalPrefixOf(root, "連江縣", "南竿鄉")).isEqualTo("209");
  }

  private static void assertOfficialSource(JSONObject source) throws Exception {
    assertThat(source.getString("authority")).isEqualTo("Chunghwa Post Co., Ltd.");
    assertThat(source.getString("url")).startsWith("https://");
  }

  private static JSONObject loadAsset() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    try (InputStream stream = context.getAssets().open(ASSET)) {
      return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static String postalPrefixOf(JSONObject root, String countyName, String districtName)
      throws Exception {
    JSONArray counties = root.getJSONArray("counties");
    for (int countyIndex = 0; countyIndex < counties.length(); countyIndex++) {
      JSONObject county = counties.getJSONObject(countyIndex);
      if (!countyName.equals(county.getString("name"))) continue;
      JSONArray districts = county.getJSONArray("districts");
      for (int districtIndex = 0; districtIndex < districts.length(); districtIndex++) {
        JSONObject district = districts.getJSONObject(districtIndex);
        if (districtName.equals(district.getString("name"))) {
          return district.getString("postalPrefix");
        }
      }
    }
    throw new AssertionError("Missing postal locality " + countyName + districtName);
  }
}
