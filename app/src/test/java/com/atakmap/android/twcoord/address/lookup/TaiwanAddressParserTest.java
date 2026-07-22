package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class TaiwanAddressParserTest {

  private final TaiwanAddressParser parser = new TaiwanAddressParser();

  @Test
  public void corpusNormalizesAndSplitsAllOneHundredPinnedAddresses() throws Exception {
    List<String[]> rows = fixtureRows();
    assertThat(rows).hasSize(100);

    for (String[] row : rows) {
      AddressDraft draft = parser.parse(row[1], 1L, AddressInputMode.FULL);

      assertThat(draft.normalizedAddress()).as("row %s normalized", row[0]).isEqualTo(row[2]);
      assertThat(draft.components().countyCity()).as("row %s county", row[0]).isEqualTo(row[3]);
      assertThat(draft.components().districtTownship())
          .as("row %s district", row[0])
          .isEqualTo(row[4]);
      assertThat(draft.components().roadLocality()).as("row %s road", row[0]).isEqualTo(row[5]);
      assertThat(draft.components().tail()).as("row %s tail", row[0]).isEqualTo(row[6]);
      assertThat(draft.unclassifiedText()).as("row %s unclassified", row[0]).isEqualTo(row[7]);
    }
  }

  @Test
  public void unitAdjacentNumeralsChangeButProperNamesRemainText() {
    assertThat(parser.normalize("台北市中山區八德路四段130號")).isEqualTo("臺北市中山區八德路4段130號");
    assertThat(parser.normalize("臺北市中山區三元街二段1號")).isEqualTo("臺北市中山區三元街2段1號");
    assertThat(parser.normalize("臺北市中山區敬業一路1號")).isEqualTo("臺北市中山區敬業1路1號");
  }

  @Test
  public void longestPrefixDoesNotSplitNewCityDistrictAtInnerCityCharacter() {
    AddressDraft draft = parser.parse("臺南市新市區中正路1段130號", 3L, AddressInputMode.FULL);

    assertThat(draft.components().countyCity()).isEqualTo("臺南市");
    assertThat(draft.components().districtTownship()).isEqualTo("新市區");
    assertThat(draft.components().roadLocality()).isEqualTo("中正路1段");
  }

  @Test
  public void unknownSuffixIsPreservedExactlyOnce() {
    AddressDraft draft = parser.parse("臺中市南屯區黎明路二段130號A棟", 7L, AddressInputMode.FULL);

    assertThat(draft.components().tail()).isEqualTo("130號");
    assertThat(draft.unclassifiedText()).isEqualTo("A棟");
    assertThat(draft.composeStructured()).isEqualTo(draft.normalizedAddress());
    assertThat(draft.draftRevision()).isEqualTo(7L);
  }

  private static List<String[]> fixtureRows() throws Exception {
    InputStream stream =
        TaiwanAddressParserTest.class
            .getClassLoader()
            .getResourceAsStream("fixtures/native_address_entry_corpus.csv");
    assertThat(stream).isNotNull();
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) rows.add(parseCsvLine(line));
    }
    return rows;
  }

  private static String[] parseCsvLine(String line) {
    String body = line.substring(1, line.length() - 1);
    return body.split("\",\"", -1);
  }
}
