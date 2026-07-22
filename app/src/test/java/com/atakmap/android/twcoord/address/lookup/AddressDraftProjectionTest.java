package com.atakmap.android.twcoord.address.lookup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public final class AddressDraftProjectionTest {
  private final TaiwanAddressParser parser = new TaiwanAddressParser();

  @Test
  public void allCorpusRowsRoundTripWithoutLossDuplicationOrReordering() throws Exception {
    List<String[]> rows = fixtureRows();
    assertThat(rows).hasSize(100);

    for (String[] row : rows) {
      AddressDraft full = parser.parse(row[1], 9L, AddressInputMode.FULL);
      AddressDraft structured = full.withMode(AddressInputMode.STRUCTURED);
      AddressDraft projectedBack = structured.withMode(AddressInputMode.FULL);

      assertThat(structured.draftRevision()).as("row %s revision", row[0]).isEqualTo(9L);
      assertThat(structured.composeStructured())
          .as("row %s structured projection", row[0])
          .isEqualTo(row[2]);
      assertThat(structured.structuredTail())
          .as("row %s visible tail", row[0])
          .isEqualTo(row[6] + row[7]);
      assertThat(projectedBack.normalizedAddress())
          .as("row %s full projection", row[0])
          .isEqualTo(row[2]);
    }
  }

  @Test
  public void structuredEditRecombinesInFieldOrderAndKeepsUnknownTextOnce() {
    AddressDraft edited = parser.parseStructured("臺中市", "南屯區", "黎明路2段", "132號A棟", 10L);

    assertThat(edited.rawAddress()).isEqualTo("臺中市南屯區黎明路2段132號A棟");
    assertThat(edited.components().tail()).isEqualTo("132號");
    assertThat(edited.unclassifiedText()).isEqualTo("A棟");
    assertThat(edited.structuredTail()).isEqualTo("132號A棟");
    assertThat(edited.composeStructured()).isEqualTo(edited.normalizedAddress());
  }

  private static List<String[]> fixtureRows() throws Exception {
    InputStream stream =
        AddressDraftProjectionTest.class
            .getClassLoader()
            .getResourceAsStream("fixtures/native_address_entry_corpus.csv");
    assertThat(stream).isNotNull();
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        String body = line.substring(1, line.length() - 1);
        rows.add(body.split("\",\"", -1));
      }
    }
    return rows;
  }
}
