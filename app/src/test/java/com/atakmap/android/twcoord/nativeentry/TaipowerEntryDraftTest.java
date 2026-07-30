package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.coord.Feature014TaipowerFixtures;
import java.util.List;
import org.junit.Test;

public final class TaipowerEntryDraftTest {

  @Test
  public void rawTextIsExactAndSafePrefixesProjectWithoutChangingRevision() {
    for (String raw : Feature014TaipowerFixtures.ONE_METRE_RAW_VARIANTS) {
      TaipowerEntryDraft draft = TaipowerEntryDraft.empty().editRaw(raw);

      assertThat(draft.rawText()).isEqualTo(raw);
      assertThat(draft.source()).isSameAs(TaipowerEntryDraft.Source.RAW);
      assertThat(draft.rawRevision()).isEqualTo(draft.revision());
      assertThat(draft.splitRevision()).isEqualTo(draft.revision());
      assertThat(draft.splitParts().joined()).isEqualTo("H7509DB4016");
      assertThat(draft.precision()).isSameAs(TaipowerEntryDraft.Precision.ONE_METRE);
    }

    for (String raw : Feature014TaipowerFixtures.REPRESENTABLE_PARTIAL_RAW) {
      TaipowerEntryDraft draft = TaipowerEntryDraft.empty().editRaw(raw);
      assertThat(draft.canProject(TaipowerInputMode.SPLIT_FIELDS)).as(raw).isTrue();
      assertThat(draft.rawText()).isEqualTo(raw);
    }
  }

  @Test
  public void unprojectableRawAndSplitGapsRemainLossless() {
    TaipowerEntryDraft raw = TaipowerEntryDraft.empty().editRaw("H75-09 DB");
    assertThat(raw.rawText()).isEqualTo("H75-09 DB");
    assertThat(raw.canProject(TaipowerInputMode.SPLIT_FIELDS)).isFalse();
    assertThat(raw.projectionFailure())
        .isSameAs(TaipowerEntryDraft.ProjectionFailure.RAW_NOT_POSITIONAL);

    TaipowerEntryDraft split = TaipowerEntryDraft.empty().editSubgrid("DB");
    assertThat(split.splitParts().subgrid()).isEqualTo("DB");
    assertThat(split.rawText()).isEmpty();
    assertThat(split.canProject(TaipowerInputMode.SINGLE_FIELD)).isFalse();
    assertThat(split.projectionFailure())
        .isSameAs(TaipowerEntryDraft.ProjectionFailure.SPLIT_HAS_GAP);
  }

  @Test
  public void precisionTailAcceptsZeroOneTwoThreeOrFourDigitsAndRejectsExtraCharacters() {
    TaipowerEntryDraft prefix =
        TaipowerEntryDraft.empty().editRegion("h").editSubregion("7509").editSubgrid("db");

    assertThat(prefix.splitParts().region()).isEqualTo("H");
    assertThat(prefix.splitParts().subgrid()).isEqualTo("DB");
    assertThat(prefix.precision()).isSameAs(TaipowerEntryDraft.Precision.INCOMPLETE);

    List<TaipowerEntryDraft.Precision> expected =
        List.of(
            TaipowerEntryDraft.Precision.INCOMPLETE,
            TaipowerEntryDraft.Precision.INCOMPLETE,
            TaipowerEntryDraft.Precision.TEN_METRE,
            TaipowerEntryDraft.Precision.INCOMPLETE,
            TaipowerEntryDraft.Precision.ONE_METRE);
    for (int length = 0; length <= 4; length++) {
      String digits = "4016".substring(0, length);
      assertThat(prefix.editPrecisionDigits(digits).precision()).isSameAs(expected.get(length));
    }

    TaipowerEntryDraft complete = prefix.editPrecisionDigits("4016");
    assertThat(complete.editPrecisionDigits("40167")).isSameAs(complete);
    assertThat(complete.editSubgrid("DBZ")).isSameAs(complete);
    assertThat(complete.editSubregion("7509X")).isSameAs(complete);
  }

  @Test
  public void acceptedEditsIncrementOnceWhileModeProjectionIsRevisionNeutral() {
    TaipowerEntryDraft draft = TaipowerEntryDraft.empty();
    long emptyRevision = draft.revision();

    draft = draft.editRaw("H7509 DB40");
    assertThat(draft.revision()).isEqualTo(emptyRevision + 1);
    assertThat(draft.editRaw("H7509 DB40")).isSameAs(draft);
    long completeRevision = draft.revision();

    assertThat(draft.canProject(TaipowerInputMode.SPLIT_FIELDS)).isTrue();
    assertThat(draft.revision()).isEqualTo(completeRevision);
    assertThat(draft.precision()).isSameAs(TaipowerEntryDraft.Precision.TEN_METRE);
    assertThat(draft.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    assertThat(draft.resolvedOrNull()).isNotNull();
  }

  @Test
  public void oneHundredRawSplitRawRoundTripsPreserveExactRawTextAndState() {
    for (int index = 0; index < 100; index++) {
      String raw =
          index % 2 == 0
              ? "  h7509 db4016  "
              : Feature014TaipowerFixtures.REPRESENTABLE_PARTIAL_RAW.get(
                  index % Feature014TaipowerFixtures.REPRESENTABLE_PARTIAL_RAW.size());
      TaipowerEntryDraft draft = TaipowerEntryDraft.empty().editRaw(raw);
      long revision = draft.revision();
      TaiwanEntryController.Validation validation = draft.validation();

      assertThat(draft.canProject(TaipowerInputMode.SPLIT_FIELDS)).isTrue();
      assertThat(draft.canProject(TaipowerInputMode.SINGLE_FIELD)).isTrue();
      assertThat(draft.rawText()).isEqualTo(raw);
      assertThat(draft.revision()).isEqualTo(revision);
      assertThat(draft.validation()).isSameAs(validation);
    }
  }
}
