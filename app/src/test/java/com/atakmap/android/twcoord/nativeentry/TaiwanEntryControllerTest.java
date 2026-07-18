package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public final class TaiwanEntryControllerTest {

  @Test
  public void firstUseStartsWithTaipowerAndEmptyDraft() {
    TaiwanEntryController controller = controller(CoordinateUnit.TAIPOWER);

    assertThat(controller.activeUnit()).isSameAs(CoordinateUnit.TAIPOWER);
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.EMPTY);
    assertThat(controller.resolvedOrNull()).isNull();
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

    assertThat(controller.taipowerText()).matches("[A-X]\\d{4} [A-J]{2}\\d{4}");
    assertThat(controller.validation()).isSameAs(TaiwanEntryController.Validation.VALID);
    assertThat(changes).hasValue(0);
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
