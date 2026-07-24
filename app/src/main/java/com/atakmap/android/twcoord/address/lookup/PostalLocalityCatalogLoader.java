package com.atakmap.android.twcoord.address.lookup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Strict parser for the bundled postal-locality ordering asset. */
public final class PostalLocalityCatalogLoader {
  private PostalLocalityCatalogLoader() {}

  public static PostalLocalityCatalog load(InputStream input) throws IOException {
    if (input == null) throw new IllegalArgumentException("input");
    try {
      JSONObject root = new JSONObject(readUtf8(input));
      if (root.getInt("schemaVersion") != 1) {
        throw new IllegalArgumentException("unsupported postal catalog schema");
      }
      requireText(root.getString("datasetId"), "datasetId");
      String retrievedOn = requireText(root.getString("retrievedOn"), "retrievedOn");
      if (!retrievedOn.matches("\\d{4}-\\d{2}-\\d{2}")) {
        throw new IllegalArgumentException("retrievedOn must use YYYY-MM-DD");
      }
      JSONObject sources = root.getJSONObject("sources");
      requireSource(sources.getJSONObject("countySelectorOrder"), false);
      requireSource(sources.getJSONObject("postalPrefixes"), true);
      requireSource(sources.getJSONObject("localityCenters"), true);
      JSONArray countyRows = root.getJSONArray("counties");
      List<PostalLocalityCatalog.County> counties = new ArrayList<>();
      int districtCount = 0;
      for (int countyIndex = 0; countyIndex < countyRows.length(); countyIndex++) {
        JSONObject countyRow = countyRows.getJSONObject(countyIndex);
        JSONArray districtRows = countyRow.getJSONArray("districts");
        List<PostalLocalityCatalog.District> districts = new ArrayList<>();
        for (int districtIndex = 0; districtIndex < districtRows.length(); districtIndex++) {
          JSONObject districtRow = districtRows.getJSONObject(districtIndex);
          validateOptionalCentre(districtRow);
          districts.add(
              new PostalLocalityCatalog.District(
                  districtRow.getString("name"),
                  districtRow.getString("postalPrefix"),
                  districtRow.getInt("postalOrder")));
          districtCount++;
        }
        districts.sort(
            Comparator.comparing(PostalLocalityCatalog.District::postalPrefix)
                .thenComparingInt(PostalLocalityCatalog.District::postalOrder)
                .thenComparing(district -> StreetTextNormaliser.fold(district.name())));
        counties.add(
            new PostalLocalityCatalog.County(
                countyRow.getString("name"), countyRow.getInt("selectorOrder"), districts));
      }
      counties.sort(Comparator.comparingInt(PostalLocalityCatalog.County::selectorOrder));
      if (root.getInt("countyCount") != counties.size()
          || root.getInt("districtCount") != districtCount) {
        throw new IllegalArgumentException("postal catalog declared counts do not match rows");
      }
      return new PostalLocalityCatalog(root.getString("datasetId"), retrievedOn, counties, true);
    } catch (JSONException e) {
      throw new IllegalArgumentException("invalid postal catalog", e);
    }
  }

  private static void validateOptionalCentre(JSONObject district) throws JSONException {
    JSONObject centre = district.optJSONObject("center");
    if (centre == null) return;
    double latitude = centre.getDouble("latitude");
    double longitude = centre.getDouble("longitude");
    if (!Double.isFinite(latitude)
        || !Double.isFinite(longitude)
        || latitude < -90.0
        || latitude > 90.0
        || longitude < -180.0
        || longitude > 180.0) {
      throw new IllegalArgumentException("postal locality centre is out of range");
    }
  }

  private static void requireSource(JSONObject source, boolean requireHash) throws JSONException {
    requireText(source.getString("authority"), "source authority");
    requireText(source.getString("title"), "source title");
    requireText(source.getString("url"), "source URL");
    if (requireHash) {
      String hash = requireText(source.getString("sha256"), "source SHA-256");
      if (!hash.matches("[0-9a-fA-F]{64}")) {
        throw new IllegalArgumentException("source SHA-256 must contain 64 hex characters");
      }
    }
  }

  private static String requireText(String value, String label) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(label);
    return value.trim();
  }

  private static String readUtf8(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    return new String(output.toByteArray(), StandardCharsets.UTF_8);
  }
}
