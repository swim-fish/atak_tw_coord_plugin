package com.atakmap.android.twcoord.address.lookup;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic Taiwan full-address normalizer and conservative component parser. */
public final class TaiwanAddressParser {
  private static final String CHINESE_NUMERALS = "零〇一二兩三四五六七八九十百千";
  private static final Pattern UNIT_NUMBER =
      Pattern.compile("[" + CHINESE_NUMERALS + "]+(?=(?:鄰|段|路|街|巷|弄|號|樓|室))");
  private static final Pattern POST_SUBNUMBER =
      Pattern.compile("(?<=之)[" + CHINESE_NUMERALS + "]+(?=$|號|樓|室)");
  private static final Pattern EQUIVALENT_ALIAS =
      Pattern.compile("([\\p{IsHan}]{1,4})\\(([\\p{IsHan}]{1,4})\\)(?=[縣市])");
  private static final Pattern ROAD = Pattern.compile("^(.+?(?:大道|路|街)(?:\\d+段)?)");
  private static final Pattern SUPPORTED_TAIL =
      Pattern.compile("^((?:(?:\\d+(?:之\\d+)?)(?:巷|弄|號|樓|室)|之\\d+)+)");

  private static final Set<Character> DISTRICT_SUFFIXES =
      new LinkedHashSet<>(Arrays.asList('區', '鄉', '鎮', '市'));
  private static final String[] DEFAULT_COUNTIES = {
    "臺北市", "新北市", "桃園市", "臺中市", "臺南市", "高雄市",
    "基隆市", "新竹市", "嘉義市", "新竹縣", "苗栗縣", "彰化縣",
    "南投縣", "雲林縣", "嘉義縣", "屏東縣", "宜蘭縣", "花蓮縣",
    "臺東縣", "澎湖縣", "金門縣", "連江縣"
  };

  private final String[] counties;

  public TaiwanAddressParser() {
    this(Arrays.asList(DEFAULT_COUNTIES));
  }

  public TaiwanAddressParser(Collection<String> knownCounties) {
    counties =
        knownCounties.stream()
            .filter(value -> value != null && !value.isEmpty())
            .map(this::normalize)
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toArray(String[]::new);
  }

  public AddressDraft parse(String raw, long revision, AddressInputMode mode) {
    String normalized = normalize(raw);
    if (normalized.isEmpty()) return AddressDraft.empty(revision, mode);

    String remainder = normalized;
    String county = longestPrefix(remainder, counties);
    if (!county.isEmpty()) remainder = remainder.substring(county.length());

    String district = districtPrefix(remainder);
    if (!district.isEmpty()) remainder = remainder.substring(district.length());

    String road = "";
    Matcher roadMatcher = ROAD.matcher(remainder);
    if (roadMatcher.find()) {
      road = roadMatcher.group(1);
      remainder = remainder.substring(road.length());
    }

    String tail = "";
    Matcher tailMatcher = SUPPORTED_TAIL.matcher(remainder);
    if (tailMatcher.find()) {
      tail = tailMatcher.group(1);
      remainder = remainder.substring(tail.length());
    }

    AddressComponents components = new AddressComponents(county, district, road, tail);
    AddressValidation validation =
        !county.isEmpty() && !district.isEmpty() && !road.isEmpty()
            ? AddressValidation.READY_TO_LOOKUP
            : AddressValidation.PARTIAL;
    return new AddressDraft(raw, normalized, components, remainder, mode, revision, validation);
  }

  public String normalize(String raw) {
    if (raw == null) return "";
    String value = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().replace('台', '臺');
    value = value.replaceAll("[\\s,，、]+", "");
    Matcher aliasMatcher = EQUIVALENT_ALIAS.matcher(value);
    StringBuffer aliases = new StringBuffer();
    while (aliasMatcher.find()) {
      String replacement =
          aliasMatcher.group(1).equals(aliasMatcher.group(2))
              ? aliasMatcher.group(1)
              : aliasMatcher.group();
      aliasMatcher.appendReplacement(aliases, Matcher.quoteReplacement(replacement));
    }
    aliasMatcher.appendTail(aliases);
    value = aliases.toString().replaceAll("(?<=\\d)[-~～–—](?=\\d)", "之");
    value = replaceChineseNumbers(value, UNIT_NUMBER);
    value = replaceChineseNumbers(value, POST_SUBNUMBER);
    value = value.replaceAll("(\\d+)號之(\\d+)", "$1之$2號");
    return value.replaceAll("[?？。]+$", "");
  }

  private static String replaceChineseNumbers(String input, Pattern pattern) {
    Matcher matcher = pattern.matcher(input);
    StringBuffer out = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(out, Integer.toString(chineseNumber(matcher.group())));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static int chineseNumber(String value) {
    boolean hasUnit = value.indexOf('十') >= 0 || value.indexOf('百') >= 0 || value.indexOf('千') >= 0;
    if (!hasUnit) {
      StringBuilder digits = new StringBuilder();
      for (int i = 0; i < value.length(); i++) digits.append(chineseDigit(value.charAt(i)));
      return Integer.parseInt(digits.toString());
    }
    int total = 0;
    int current = 0;
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      int unit = chineseUnit(character);
      if (unit == 0) current = chineseDigit(character);
      else {
        total += (current == 0 ? 1 : current) * unit;
        current = 0;
      }
    }
    return total + current;
  }

  private static int chineseDigit(char value) {
    switch (value) {
      case '零':
      case '〇':
        return 0;
      case '一':
        return 1;
      case '二':
      case '兩':
        return 2;
      case '三':
        return 3;
      case '四':
        return 4;
      case '五':
        return 5;
      case '六':
        return 6;
      case '七':
        return 7;
      case '八':
        return 8;
      case '九':
        return 9;
      default:
        throw new IllegalArgumentException("Unsupported Chinese digit: " + value);
    }
  }

  private static int chineseUnit(char value) {
    if (value == '十') return 10;
    if (value == '百') return 100;
    if (value == '千') return 1000;
    return 0;
  }

  private static String longestPrefix(String text, String[] values) {
    for (String value : values) if (text.startsWith(value)) return value;
    return "";
  }

  private static String districtPrefix(String remainder) {
    int max = Math.min(remainder.length(), 7);
    for (int i = 1; i <= max; i++) {
      char value = remainder.charAt(i - 1);
      if (!DISTRICT_SUFFIXES.contains(value)) continue;
      if (i < remainder.length() && remainder.charAt(i) == '區') continue;
      return remainder.substring(0, i);
    }
    return "";
  }
}
