package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.ZipEntryClassifier.Classification;
import org.junit.Test;

/**
 * Feature 005 — JVM tests for {@link ZipEntryClassifier} per contracts/zip-extractor.md classifier
 * test plan (10 cases).
 *
 * <p>Pure JVM, no Robolectric needed — the classifier is regex + string pattern only.
 */
public class ZipEntryClassifierTest {

  private final ZipEntryClassifier classifier = new ZipEntryClassifier();

  // 1. places-taichung.sqlite → PLACES_COUNTY, county = "taichung"
  @Test
  public void placesTaichungClassifiedAsCountyWithCorrectGroup() {
    assertThat(classifier.classify("places-taichung.sqlite"))
        .isEqualTo(Classification.PLACES_COUNTY);
    assertThat(classifier.countyFromEntry("places-taichung.sqlite")).contains("taichung");
  }

  // 2. places-彰化縣.sqlite (CJK) → PLACES_COUNTY, county = "彰化縣"
  @Test
  public void placesCjkCountyAccepted() {
    assertThat(classifier.classify("places-彰化縣.sqlite")).isEqualTo(Classification.PLACES_COUNTY);
    assertThat(classifier.countyFromEntry("places-彰化縣.sqlite")).contains("彰化縣");
  }

  // 3. places-OSM.sqlite (case-mixed) → UNRECOGNIZED (lowercase-only contract per data-contract §2)
  @Test
  public void mixedCaseFilenameRejected() {
    assertThat(classifier.classify("Places-Taichung.SQLITE"))
        .isEqualTo(Classification.UNRECOGNIZED);
    assertThat(classifier.classify("places-Taichung.sqlite"))
        .isEqualTo(
            Classification
                .PLACES_COUNTY); // group="Taichung" — case-sensitive matches lowercase prefix only
  }

  // 4. townships.sqlite → SKIPPED_SUPPLEMENTARY
  @Test
  public void townshipsSupplementary() {
    assertThat(classifier.classify("townships.sqlite"))
        .isEqualTo(Classification.SKIPPED_SUPPLEMENTARY);
    assertThat(classifier.countyFromEntry("townships.sqlite")).isEmpty();
  }

  // 5. places-osm.sqlite → SKIPPED_SUPPLEMENTARY (specific name, not a county)
  @Test
  public void placesOsmIsSupplementaryNotCounty() {
    assertThat(classifier.classify("places-osm.sqlite"))
        .isEqualTo(Classification.SKIPPED_SUPPLEMENTARY);
    assertThat(classifier.countyFromEntry("places-osm.sqlite")).isEmpty();
  }

  // 6. timestamp.taichung → SKIPPED_SUPPLEMENTARY
  @Test
  public void timestampSidecarSupplementary() {
    assertThat(classifier.classify("timestamp.taichung"))
        .isEqualTo(Classification.SKIPPED_SUPPLEMENTARY);
    assertThat(classifier.classify("timestamp.base"))
        .isEqualTo(Classification.SKIPPED_SUPPLEMENTARY);
  }

  // 7. places-taichung.manifest.txt → SKIPPED_SUPPLEMENTARY (manifest suffix wins over places-
  // prefix)
  @Test
  public void manifestSidecarSupplementary() {
    assertThat(classifier.classify("places-taichung.manifest.txt"))
        .isEqualTo(Classification.SKIPPED_SUPPLEMENTARY);
    assertThat(classifier.classify("base.manifest.txt"))
        .isEqualTo(Classification.SKIPPED_SUPPLEMENTARY);
  }

  // 8. ../etc/passwd → UNRECOGNIZED (zip-slip defence)
  @Test
  public void zipSlipDotDotRejected() {
    assertThat(classifier.classify("../etc/passwd")).isEqualTo(Classification.UNRECOGNIZED);
    assertThat(classifier.classify("../../places-evil.sqlite"))
        .isEqualTo(Classification.UNRECOGNIZED);
  }

  // 9. /absolute/path/places-taipei.sqlite → UNRECOGNIZED (absolute path defence)
  @Test
  public void absolutePathRejected() {
    assertThat(classifier.classify("/absolute/path/places-taipei.sqlite"))
        .isEqualTo(Classification.UNRECOGNIZED);
    assertThat(classifier.classify("\\windows\\places-taipei.sqlite"))
        .isEqualTo(Classification.UNRECOGNIZED);
    assertThat(classifier.classify("C:places-taipei.sqlite"))
        .isEqualTo(Classification.UNRECOGNIZED); // colon (Windows drive) rejected too
  }

  // 10. places-.sqlite (empty county) → UNRECOGNIZED (regex requires ≥1 char in the county group)
  @Test
  public void emptyCountyRejected() {
    assertThat(classifier.classify("places-.sqlite")).isEqualTo(Classification.UNRECOGNIZED);
    assertThat(classifier.classify("places-.SQLITE")).isEqualTo(Classification.UNRECOGNIZED);
    assertThat(classifier.classify(".sqlite")).isEqualTo(Classification.UNRECOGNIZED);
    assertThat(classifier.classify("")).isEqualTo(Classification.UNRECOGNIZED);
    assertThat(classifier.classify(null)).isEqualTo(Classification.UNRECOGNIZED);
  }

  // Extra: countyFromEntry on a supplementary returns empty (defensive).
  @Test
  public void countyFromEntryEmptyForSupplementary() {
    assertThat(classifier.countyFromEntry("townships.sqlite")).isEmpty();
    assertThat(classifier.countyFromEntry("places-osm.sqlite")).isEmpty();
    assertThat(classifier.countyFromEntry("timestamp.base")).isEmpty();
    assertThat(classifier.countyFromEntry("base.manifest.txt")).isEmpty();
  }
}
