package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.atakmap.android.twcoord.coord.CoordinateConverter;
import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class TaiwanEntryControllerTest {

  @Test
  public void firstUseStartsWithTaipowerAndEmptyDraft() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);

    assertThat(controller.activeUnit()).isSameAs(CoordinateUnit.TAIPOWER);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.EMPTY);
    assertThat(controller.resolvedOrNull()).isNull();
    assertThat(controller.activeTab()).isEqualTo(NativeEntryTab.TAIPOWER);
  }

  @Test
  public void addressTabIsSeparateFromPersistedCoordinateUnit() {
    AtomicInteger selections = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(CoordinateUnit.TWD97, ignored -> selections.incrementAndGet());

    controller.selectTab(NativeEntryTab.ADDRESS, true);

    assertThat(controller.activeTab()).isEqualTo(NativeEntryTab.ADDRESS);
    assertThat(controller.activeUnit()).isEqualTo(CoordinateUnit.TWD97);
    assertThat(selections).hasValue(0);
  }

  @Test
  public void validTaipowerDraftResolvesToCanonicalWgs84() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);

    controller.setTaipowerText("H7509 DB4016", true);

    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    assertThat(controller.resolvedOrNull().latitudeDeg()).isCloseTo(23.9932, within(0.001));
    assertThat(controller.resolvedOrNull().longitudeDeg()).isCloseTo(121.6012, within(0.001));
  }

  @Test
  public void editDiscardsPreviouslyValidPoint() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);
    controller.setTaipowerText("H7509 DB4016", true);
    assertThat(controller.resolvedOrNull()).isNotNull();

    controller.setTaipowerText("bad", true);

    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.MALFORMED);
    assertThat(controller.resolvedOrNull()).isNull();
  }

  @Test
  public void hostPointForwardRenderingIsProgrammatic() {
    AtomicInteger changes = new AtomicInteger();
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);
    controller.setOnHumanChange(changes::incrementAndGet);

    controller.activate(point(25.033611, 121.564472), true);

    assertThat(controller.taipowerText()).matches("[A-X]\\d{4} [A-H][A-E]\\d{4}");
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    assertThat(changes).hasValue(0);
  }

  @Test
  public void activationPrefillsEverySystemFromOneMainIslandPointWithoutNotification() {
    AtomicInteger changes = new AtomicInteger();
    AtomicInteger selections = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(CoordinateUnit.TAIPOWER, ignored -> selections.incrementAndGet());
    controller.setOnHumanChange(changes::incrementAndGet);

    controller.activate(point(25.033611, 121.564472), true);

    assertThat(controller.taipowerText()).matches("[A-X]\\d{4} [A-H][A-E]\\d{4}");
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    controller.selectSystem(CoordinateUnit.TWD97, false);
    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEqualTo("306963");
    assertThat(controller.northingText(CoordinateUnit.TWD97)).isEqualTo("2769619");
    assertThat(controller.zone(CoordinateUnit.TWD97)).isEqualTo(121);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    controller.selectSystem(CoordinateUnit.TWD67, false);
    assertThat(controller.eastingText(CoordinateUnit.TWD67)).isEqualTo("306132");
    assertThat(controller.northingText(CoordinateUnit.TWD67)).isEqualTo("2769823");
    assertThat(controller.zone(CoordinateUnit.TWD67)).isEqualTo(121);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    assertThat(changes).hasValue(0);
    assertThat(selections).hasValue(0);
  }

  @Test
  public void zone119ActivationKeepsBothTwdDraftsAndMarksOnlyTaipowerUnavailable() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);

    controller.activate(point(23.566, 119.566), true);

    assertThat(controller.taipowerText()).isEmpty();
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.UNREPRESENTABLE);
    controller.selectSystem(CoordinateUnit.TWD97, false);
    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isNotEmpty();
    assertThat(controller.northingText(CoordinateUnit.TWD97)).isNotEmpty();
    assertThat(controller.zone(CoordinateUnit.TWD97)).isEqualTo(119);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    controller.selectSystem(CoordinateUnit.TWD67, false);
    assertThat(controller.eastingText(CoordinateUnit.TWD67)).isNotEmpty();
    assertThat(controller.northingText(CoordinateUnit.TWD67)).isNotEmpty();
    assertThat(controller.zone(CoordinateUnit.TWD67)).isEqualTo(119);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
  }

  @Test
  public void alternatingActivationsReplaceEveryDraftWithoutStaleValues() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);
    Wgs84[] points = {point(25.033611, 121.564472), point(23.9932, 121.6012)};

    for (int i = 0; i < 100; i++) {
      Wgs84 expected = points[i % points.length];
      controller.activate(expected, true);
      for (CoordinateUnit unit : CoordinateUnit.values()) {
        controller.selectSystem(unit, false);
        assertThat(controller.validation())
            .as("activation %s unit %s", i, unit)
            .isSameAs(TaiwanEntryController.Validation.VALID);
        assertThat(controller.resolvedOrNull().latitudeDeg())
            .as("activation %s unit %s latitude", i, unit)
            .isCloseTo(
                expected.latitudeDeg(), within(unit == CoordinateUnit.TAIPOWER ? 0.001 : 0.0001));
        assertThat(controller.resolvedOrNull().longitudeDeg())
            .as("activation %s unit %s longitude", i, unit)
            .isCloseTo(
                expected.longitudeDeg(), within(unit == CoordinateUnit.TAIPOWER ? 0.001 : 0.0001));
      }
    }
  }

  @Test
  public void twd97Zone121ResolvesAndKeepsExplicitZone() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);
    controller.selectSystem(CoordinateUnit.TWD97, true);
    controller.setTwdEasting(CoordinateUnit.TWD97, "306963", true);
    controller.setTwdNorthing(CoordinateUnit.TWD97, "2769619", true);
    controller.setZone(CoordinateUnit.TWD97, 121, true);

    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    assertThat(controller.zone(CoordinateUnit.TWD97)).isEqualTo(121);
    assertThat(controller.resolvedOrNull().latitudeDeg()).isCloseTo(25.033611, within(0.00002));
    assertThat(controller.resolvedOrNull().longitudeDeg()).isCloseTo(121.564472, within(0.00002));
  }

  @Test
  public void twd67Zone121ResolvesWithinPublishedBudget() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);
    controller.selectSystem(CoordinateUnit.TWD67, true);
    controller.setTwdEasting(CoordinateUnit.TWD67, "306132", true);
    controller.setTwdNorthing(CoordinateUnit.TWD67, "2769823", true);
    controller.setZone(CoordinateUnit.TWD67, 121, true);

    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    assertThat(controller.resolvedOrNull().latitudeDeg()).isCloseTo(25.033611, within(0.00005));
    assertThat(controller.resolvedOrNull().longitudeDeg()).isCloseTo(121.564472, within(0.00005));
  }

  @Test
  public void zone119HostPointAutofillSelectsZone119ForBothTwdSystems() {
    TaiwanEntryController controller = controller(CoordinateUnit.TWD97);
    controller.activate(point(23.566, 119.566), true);
    assertThat(controller.zone(CoordinateUnit.TWD97)).isEqualTo(119);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);

    controller.selectSystem(CoordinateUnit.TWD67, true);
    controller.autofill(point(23.566, 119.566));
    assertThat(controller.zone(CoordinateUnit.TWD67)).isEqualTo(119);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
  }

  @Test
  public void partialBadZoneAndNonAsciiNumericFormsAreRejected() {
    TaiwanEntryController controller = controller(CoordinateUnit.TWD97);
    controller.setTwdEasting(CoordinateUnit.TWD97, "306963", true);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.INCOMPLETE);

    controller.setTwdNorthing(CoordinateUnit.TWD97, "2769619", true);
    controller.setZone(CoordinateUnit.TWD97, 120, true);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.BAD_ZONE);

    String[] malformed = {"306963.0", "+306963", "306,963", "３０６９６３"};
    for (String value : malformed) {
      controller.setZone(CoordinateUnit.TWD97, 121, false);
      controller.setTwdEasting(CoordinateUnit.TWD97, value, true);
      assertThat(controller.validation())
          .as(value)
          .isSameAs(TaiwanEntryController.Validation.MALFORMED);
    }
  }

  @Test
  public void systemSwitchRetainsSeparateDraftsAndIgnoresInactiveDraft() {
    TaiwanEntryController controller = controller(CoordinateUnit.TWD97);
    controller.setTwdEasting(CoordinateUnit.TWD97, "306963", true);
    controller.setTwdNorthing(CoordinateUnit.TWD97, "2769619", true);
    controller.selectSystem(CoordinateUnit.TWD67, true);
    controller.setTwdEasting(CoordinateUnit.TWD67, "bad", true);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.INCOMPLETE);

    controller.selectSystem(CoordinateUnit.TWD97, true);
    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEqualTo("306963");
    assertThat(controller.northingText(CoordinateUnit.TWD97)).isEqualTo("2769619");
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
  }

  @Test
  public void autofillReplacesDraftAndNullClearsItWithoutHumanNotification() {
    AtomicInteger changes = new AtomicInteger();
    TaiwanEntryController controller = controller(CoordinateUnit.TWD97);
    controller.setOnHumanChange(changes::incrementAndGet);
    controller.setTwdEasting(CoordinateUnit.TWD97, "123456", false);
    controller.setTwdNorthing(CoordinateUnit.TWD97, "1234567", false);

    controller.autofill(point(25.033611, 121.564472));
    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEqualTo("306963");
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    controller.autofill(null);

    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEmpty();
    assertThat(controller.northingText(CoordinateUnit.TWD97)).isEmpty();
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.EMPTY);
    assertThat(changes).hasValue(0);
  }

  @Test
  public void clearRemainsActiveOnlyAndAutofillRefreshesEveryCoordinateDraft() {
    AtomicInteger changes = new AtomicInteger();
    TaiwanEntryController controller = controller(CoordinateUnit.TWD97);
    controller.setOnHumanChange(changes::incrementAndGet);
    controller.activate(point(25.033611, 121.564472), true);
    String taipower = controller.taipowerText();
    String twd67Easting = controller.eastingText(CoordinateUnit.TWD67);

    controller.clear();

    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEmpty();
    assertThat(controller.taipowerText()).isEqualTo(taipower);
    assertThat(controller.eastingText(CoordinateUnit.TWD67)).isEqualTo(twd67Easting);

    controller.autofill(point(23.9932, 121.6012));

    assertThat(controller.activeUnit()).isSameAs(CoordinateUnit.TWD97);
    assertThat(controller.taipowerText()).isNotEqualTo(taipower);
    assertThat(controller.eastingText(CoordinateUnit.TWD67)).isNotEqualTo(twd67Easting);
    for (CoordinateUnit unit : CoordinateUnit.values()) {
      controller.selectSystem(unit, false);
      assertThat(controller.validation())
          .as(unit.toString())
          .isSameAs(TaiwanEntryController.Validation.VALID);
      assertThat(controller.resolvedOrNull().latitudeDeg())
          .as("%s latitude", unit)
          .isCloseTo(23.9932, within(unit == CoordinateUnit.TAIPOWER ? 0.001 : 0.0001));
      assertThat(controller.resolvedOrNull().longitudeDeg())
          .as("%s longitude", unit)
          .isCloseTo(121.6012, within(unit == CoordinateUnit.TAIPOWER ? 0.001 : 0.0001));
    }
    assertThat(changes).hasValue(0);
  }

  @Test
  public void clearAndUnrepresentableAutofillPreserveTheActiveTwdZone() {
    TaiwanEntryController controller = controller(CoordinateUnit.TWD97);
    controller.setZone(CoordinateUnit.TWD97, 119, false);

    controller.clear();

    assertThat(controller.zone(CoordinateUnit.TWD97)).isEqualTo(119);
    controller.autofill(point(0.0, 0.0));
    assertThat(controller.zone(CoordinateUnit.TWD97)).isEqualTo(119);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.UNREPRESENTABLE);
  }

  @Test
  public void unexpectedPreparationFailureInvalidatesEveryPreviousDraft() {
    CoordinateConverter delegate = new CoordinateConverter();
    AtomicBoolean failTwd97 = new AtomicBoolean();
    TaiwanEntryController controller =
        new TaiwanEntryController(
            CoordinateUnit.TAIPOWER,
            ignored -> {},
            (point, unit) -> {
              if (failTwd97.get() && unit == CoordinateUnit.TWD97) {
                throw new NoSuchMethodError("injected TWD97 linkage failure");
              }
              return delegate.convert(point, unit);
            });
    controller.activate(point(25.033611, 121.564472), true);
    failTwd97.set(true);

    assertThatThrownBy(() -> controller.activate(point(23.9932, 121.6012), true))
        .isInstanceOf(NoSuchMethodError.class);

    for (CoordinateUnit unit : CoordinateUnit.values()) {
      controller.selectSystem(unit, false);
      assertThat(controller.validation())
          .as(unit.toString())
          .isSameAs(TaiwanEntryController.Validation.UNREPRESENTABLE);
      assertThat(controller.resolvedOrNull()).isNull();
    }
    assertThat(controller.taipowerText()).isEmpty();
    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEmpty();
    assertThat(controller.eastingText(CoordinateUnit.TWD67)).isEmpty();
  }

  @Test
  public void unrepresentableTaipowerAutofillClearsPriorDraft() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);
    controller.setTaipowerText("H7509 DB4016", false);

    controller.autofill(point(23.566, 119.566));

    assertThat(controller.taipowerText()).isEmpty();
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.UNREPRESENTABLE);
    assertThat(controller.resolvedOrNull()).isNull();
  }

  @Test
  public void canonicalFormatIsPureAndIndependentOfDraft() {
    TaiwanEntryController controller = controller(CoordinateUnit.TWD97);
    controller.setTwdEasting(CoordinateUnit.TWD97, "bad", false);
    String before = controller.eastingText(CoordinateUnit.TWD97);

    String formatted = controller.format(point(25.033611, 121.564472), new TaiwanEntryFormatter());

    assertThat(formatted).matches("TWD97 E=\\d+m N=\\d+m z121");
    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEqualTo(before);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.INCOMPLETE);
  }

  @Test
  public void readOnlyActivationRendersPointButRejectsEveryHumanMutation() {
    AtomicInteger changes = new AtomicInteger();
    AtomicInteger selections = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(CoordinateUnit.TWD97, ignored -> selections.incrementAndGet());
    controller.setOnHumanChange(changes::incrementAndGet);
    controller.activate(point(25.033611, 121.564472), false);
    String easting = controller.eastingText(CoordinateUnit.TWD97);
    Wgs84 resolved = controller.resolvedOrNull();

    controller.setTwdEasting(CoordinateUnit.TWD97, "1", true);
    controller.setZone(CoordinateUnit.TWD97, 119, true);
    controller.selectSystem(CoordinateUnit.TWD67, true);

    assertThat(controller.eastingText(CoordinateUnit.TWD97)).isEqualTo(easting);
    assertThat(controller.zone(CoordinateUnit.TWD97)).isEqualTo(121);
    assertThat(controller.activeUnit()).isSameAs(CoordinateUnit.TWD97);
    assertThat(controller.resolvedOrNull()).isSameAs(resolved);
    assertThat(changes).hasValue(0);
    assertThat(selections).hasValue(0);
  }

  @Test
  public void readOnlyActivationStillPreparesEverySystemProgrammatically() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);

    controller.activate(point(25.033611, 121.564472), false);

    for (CoordinateUnit unit : CoordinateUnit.values()) {
      controller.selectSystem(unit, false);
      assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
      assertThat(controller.resolvedOrNull()).isNotNull();
    }
    assertThat(controller.isEditable()).isFalse();
  }

  @Test
  public void taipowerModeSwitchIsSilentAndPersistsOnlySuccessfulProjection() {
    AtomicInteger coordinateChanges = new AtomicInteger();
    AtomicInteger modeWrites = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(
            CoordinateUnit.TAIPOWER,
            ignored -> {},
            TaipowerInputMode.SINGLE_FIELD,
            ignored -> modeWrites.incrementAndGet());
    controller.setOnHumanChange(coordinateChanges::incrementAndGet);
    controller.setTaipowerText("  h7509 db4016  ", true);
    coordinateChanges.set(0);

    assertThat(controller.selectTaipowerInputMode(TaipowerInputMode.SPLIT_FIELDS, true)).isTrue();
    assertThat(controller.taipowerInputMode()).isSameAs(TaipowerInputMode.SPLIT_FIELDS);
    assertThat(controller.taipowerDraft().splitParts().joined()).isEqualTo("H7509DB4016");
    assertThat(coordinateChanges).hasValue(0);
    assertThat(modeWrites).hasValue(1);

    assertThat(controller.selectTaipowerInputMode(TaipowerInputMode.SINGLE_FIELD, true)).isTrue();
    assertThat(controller.taipowerText()).isEqualTo("  h7509 db4016  ");
    assertThat(coordinateChanges).hasValue(0);
    assertThat(modeWrites).hasValue(2);

    controller.setTaipowerText("H75-09", true);
    assertThat(controller.selectTaipowerInputMode(TaipowerInputMode.SPLIT_FIELDS, true)).isFalse();
    assertThat(controller.taipowerInputMode()).isSameAs(TaipowerInputMode.SINGLE_FIELD);
    assertThat(modeWrites).hasValue(2);
  }

  @Test
  public void eachAcceptedSplitEditNotifiesOnceAndBothModesResolveIdentically() {
    AtomicInteger changes = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(
            CoordinateUnit.TAIPOWER, ignored -> {}, TaipowerInputMode.SPLIT_FIELDS, ignored -> {});
    controller.setOnHumanChange(changes::incrementAndGet);

    controller.setTaipowerRegion("h", true);
    controller.setTaipowerSubregion("7509", true);
    controller.setTaipowerSubgrid("db", true);
    controller.setTaipowerPrecisionDigits("4016", true);

    assertThat(changes).hasValue(4);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    Wgs84 splitPoint = controller.resolvedOrNull();
    assertThat(controller.selectTaipowerInputMode(TaipowerInputMode.SINGLE_FIELD, true)).isTrue();
    assertThat(controller.taipowerText()).isEqualTo("H7509DB4016");
    assertThat(controller.resolvedOrNull()).isSameAs(splitPoint);
    assertThat(changes).hasValue(4);
  }

  @Test
  public void activationAutofillAndClearStageBothProjectionsWithoutChangingModeOrNotifying() {
    AtomicInteger changes = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(
            CoordinateUnit.TAIPOWER, ignored -> {}, TaipowerInputMode.SPLIT_FIELDS, ignored -> {});
    controller.setOnHumanChange(changes::incrementAndGet);

    controller.activate(point(23.9932, 121.6012), true);

    assertThat(controller.taipowerInputMode()).isSameAs(TaipowerInputMode.SPLIT_FIELDS);
    assertThat(controller.taipowerText().replace(" ", "")).hasSize(11);
    assertThat(controller.taipowerDraft().splitParts().joined()).hasSize(11);
    assertThat(controller.taipowerDraft().rawRevision())
        .isEqualTo(controller.taipowerDraft().splitRevision());
    assertThat(changes).hasValue(0);

    controller.autofill(point(25.033611, 121.564472));
    assertThat(controller.taipowerDraft().precision())
        .isSameAs(TaipowerEntryDraft.Precision.ONE_METRE);
    assertThat(changes).hasValue(0);

    controller.clear();
    assertThat(controller.taipowerText()).isEmpty();
    assertThat(controller.taipowerDraft().splitParts().joined()).isEmpty();
    assertThat(controller.taipowerInputMode()).isSameAs(TaipowerInputMode.SPLIT_FIELDS);
    assertThat(changes).hasValue(0);
  }

  @Test
  public void readOnlyAllowsLosslessPresentationSwitchAndDisposeRejectsEveryCallback() {
    AtomicInteger modeWrites = new AtomicInteger();
    TaiwanEntryController controller =
        new TaiwanEntryController(
            CoordinateUnit.TAIPOWER,
            ignored -> {},
            TaipowerInputMode.SINGLE_FIELD,
            ignored -> modeWrites.incrementAndGet());
    controller.activate(point(23.9932, 121.6012), false);
    String raw = controller.taipowerText();

    assertThat(controller.selectTaipowerInputMode(TaipowerInputMode.SPLIT_FIELDS, true)).isTrue();
    assertThat(modeWrites).hasValue(1);
    controller.setTaipowerRegion("A", true);
    assertThat(controller.taipowerText()).isEqualTo(raw);

    controller.dispose();
    controller.setTaipowerText("H7509DB4016", false);
    controller.setTaipowerRegion("H", false);
    assertThat(controller.selectTaipowerInputMode(TaipowerInputMode.SINGLE_FIELD, true)).isFalse();
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.DISPOSED);
    assertThat(controller.resolvedOrNull()).isNull();
  }

  @Test
  public void invalidCompleteSubgridLettersRemainVisibleAndUnresolvedInBothModes() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);
    controller.setTaipowerText("H7509 IB4016", true);

    assertThat(controller.taipowerText()).isEqualTo("H7509 IB4016");
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.MALFORMED);
    assertThat(controller.taipowerDraft().validationDetail())
        .isSameAs(TaipowerEntryDraft.ValidationDetail.EW_SUBGRID_OUT_OF_RANGE);
    assertThat(controller.resolvedOrNull()).isNull();

    assertThat(controller.selectTaipowerInputMode(TaipowerInputMode.SPLIT_FIELDS, true)).isTrue();
    controller.setTaipowerSubgrid("AF", true);

    assertThat(controller.taipowerDraft().splitParts().subgrid()).isEqualTo("AF");
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.MALFORMED);
    assertThat(controller.taipowerDraft().validationDetail())
        .isSameAs(TaipowerEntryDraft.ValidationDetail.NS_SUBGRID_OUT_OF_RANGE);
    assertThat(controller.resolvedOrNull()).isNull();
  }

  private static TaiwanEntryController controller(CoordinateUnit initial) {
    return new TaiwanEntryController(initial, ignored -> {});
  }

  private static Wgs84 point(double latitude, double longitude) {
    return new Wgs84(latitude, longitude, 1L, Wgs84.Source.MAP_CENTRE);
  }

  private static org.assertj.core.data.Offset<Double> within(double value) {
    return org.assertj.core.data.Offset.offset(value);
  }
}
