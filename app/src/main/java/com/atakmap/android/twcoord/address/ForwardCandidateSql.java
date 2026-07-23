package com.atakmap.android.twcoord.address;

import com.atakmap.android.twcoord.address.lookup.AddressDraft;
import com.atakmap.android.twcoord.address.lookup.ForwardCandidatePool;
import com.atakmap.android.twcoord.address.lookup.ForwardCandidateShortlist;
import com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cross-backend SQL builder for the bounded forward-address candidate pools. */
final class ForwardCandidateSql {

  private static final String LOCATOR = "COALESCE(NULLIF(p.street, ''), p.area)";
  private static final String TAIL = "SUBSTR(COALESCE(p.name, ''), LENGTH(" + LOCATOR + ") + 1)";
  private static final String DIRECT =
      "CASE WHEN COALESCE(p.lane, '') = '' AND COALESCE(p.alley, '') = ''" + " THEN 0 ELSE 1 END";
  private static final Pattern UNIT_NUMBER = Pattern.compile("(\\d+)(?=段|路|街)");

  private ForwardCandidateSql() {}

  static Query build(
      AddressDraft draft,
      String foldedStreetFragment,
      Wgs84 anchorPoint,
      ForwardCandidatePool pool,
      int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, ForwardCandidateShortlist.SQL_POOL_LIMIT));
    String fragment = foldedStreetFragment == null ? "" : foldedStreetFragment;
    String fragmentTai = StreetTextNormaliser.taiVariant(fragment);
    String road = draft.components().roadLocality();
    String roadChinese = chineseUnitNumbers(road);
    String roadTai = StreetTextNormaliser.taiVariant(road);
    String roadChineseTai = StreetTextNormaliser.taiVariant(roadChinese);
    String tail = storageTail(draft.components().tail());
    String tailAlternate = tail.replace('-', '之');
    String primaryDigits = primaryDigits(tail);

    List<String> args = new ArrayList<>();
    args.add(draft.components().districtTownship());
    args.add(likePrefix(fragment));
    args.add(likePrefix(fragmentTai));

    String sameStreet = "p.street IN (?, ?, ?, ?)";
    StringBuilder order = new StringBuilder(" ORDER BY ");
    switch (pool) {
      case EXACT:
        order
            .append("CASE WHEN ")
            .append(TAIL)
            .append(" IN (?, ?) THEN 0 ELSE 1 END, CASE WHEN ")
            .append(sameStreet)
            .append(" THEN 0 ELSE 1 END, ")
            .append(DIRECT)
            .append(", LENGTH(COALESCE(p.number, '')), p.id");
        args.add(tail);
        args.add(tailAlternate);
        addStreetArgs(args, road, roadChinese, roadTai, roadChineseTai);
        break;
      case TEXT_PREFIX:
        order
            .append("CASE WHEN ")
            .append(sameStreet)
            .append(" THEN 0 ELSE 1 END, CASE WHEN ")
            .append(TAIL)
            .append(" LIKE ? ESCAPE '\\' THEN 0 ELSE 1 END, ")
            .append(DIRECT)
            .append(", LENGTH(")
            .append(TAIL)
            .append("), ")
            .append(TAIL)
            .append(", p.id");
        addStreetArgs(args, road, roadChinese, roadTai, roadChineseTai);
        args.add(likePrefix(primaryDigits));
        break;
      case NUMERIC_NEAREST:
        order
            .append("CASE WHEN ")
            .append(sameStreet)
            .append(" THEN 0 ELSE 1 END, ")
            .append(DIRECT)
            .append(", CASE WHEN ")
            .append(TAIL)
            .append(" GLOB '[0-9]*' THEN ABS(CAST(")
            .append(TAIL)
            .append(" AS INTEGER) - ?) ELSE 2147483647 END, LENGTH(")
            .append(TAIL)
            .append("), p.id");
        addStreetArgs(args, road, roadChinese, roadTai, roadChineseTai);
        args.add(primaryDigits.isEmpty() ? "0" : primaryDigits);
        break;
      case DISTANCE:
        order
            .append("CASE WHEN ")
            .append(sameStreet)
            .append(" THEN 0 ELSE 1 END, ")
            .append("((p.lat - ?) * (p.lat - ?)" + " + (p.lon - ?) * (p.lon - ?) * ?), p.id");
        addStreetArgs(args, road, roadChinese, roadTai, roadChineseTai);
        double latitude = anchorPoint != null ? anchorPoint.latitudeDeg() : 0.0;
        double longitude = anchorPoint != null ? anchorPoint.longitudeDeg() : 0.0;
        double longitudeScale = Math.cos(Math.toRadians(latitude));
        longitudeScale *= longitudeScale;
        args.add(Double.toString(latitude));
        args.add(Double.toString(latitude));
        args.add(Double.toString(longitude));
        args.add(Double.toString(longitude));
        args.add(Double.toString(longitudeScale));
        break;
      case FALLBACK:
        order
            .append("CASE WHEN ")
            .append(sameStreet)
            .append(" THEN 0 ELSE 1 END, CASE WHEN ")
            .append(DIRECT)
            .append(" = 1 THEN 0 ELSE 1 END, LENGTH(")
            .append(TAIL)
            .append("), p.id");
        addStreetArgs(args, road, roadChinese, roadTai, roadChineseTai);
        break;
    }

    String sql =
        "SELECT p.lat, p.lon, p.display_name, p.display_name_halfwidth,"
            + " "
            + LOCATOR
            + " AS street, p.number"
            + " FROM places p"
            + " WHERE p.township = ?"
            + " AND ("
            + LOCATOR
            + " LIKE ? ESCAPE '\\' OR "
            + LOCATOR
            + " LIKE ? ESCAPE '\\')"
            + order
            + " LIMIT "
            + limit;
    return new Query(sql, args.toArray(new String[0]));
  }

  private static void addStreetArgs(
      List<String> args, String road, String roadChinese, String roadTai, String roadChineseTai) {
    args.add(road);
    args.add(roadChinese);
    args.add(roadTai);
    args.add(roadChineseTai);
  }

  private static String storageTail(String value) {
    return value == null ? "" : value.replace('之', '-');
  }

  private static String primaryDigits(String value) {
    if (value == null) return "";
    int index = 0;
    while (index < value.length() && !Character.isDigit(value.charAt(index))) index++;
    int start = index;
    while (index < value.length() && Character.isDigit(value.charAt(index))) index++;
    return start < index ? value.substring(start, index) : "";
  }

  private static String likePrefix(String value) {
    return escapeLike(value) + "%";
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static String chineseUnitNumbers(String value) {
    if (value == null || value.isEmpty()) return "";
    Matcher matcher = UNIT_NUMBER.matcher(value);
    StringBuffer output = new StringBuffer();
    while (matcher.find()) {
      int parsed;
      try {
        parsed = Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException ignored) {
        continue;
      }
      matcher.appendReplacement(output, Matcher.quoteReplacement(toChineseNumber(parsed)));
    }
    matcher.appendTail(output);
    return output.toString();
  }

  private static String toChineseNumber(int number) {
    if (number <= 0 || number > 999) return Integer.toString(number);
    String[] digits = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    StringBuilder output = new StringBuilder();
    int hundreds = number / 100;
    int tens = (number / 10) % 10;
    int ones = number % 10;
    if (hundreds > 0) {
      output.append(digits[hundreds]).append('百');
      if (tens == 0 && ones > 0) output.append('零');
    }
    if (tens > 0) {
      if (tens > 1 || hundreds > 0) output.append(digits[tens]);
      output.append('十');
    }
    if (ones > 0) output.append(digits[ones]);
    return output.toString();
  }

  static final class Query {
    private final String sql;
    private final String[] args;

    Query(String sql, String[] args) {
      this.sql = sql;
      this.args = args;
    }

    String sql() {
      return sql;
    }

    String[] args() {
      return args;
    }
  }
}
