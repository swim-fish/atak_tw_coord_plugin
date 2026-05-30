package com.atakmap.android.twcoord;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.AddressRowState;
import org.junit.Test;

/**
 * Feature 004 — pure-logic unit tests for the address-row state-mapping helpers exposed by {@link
 * TwCoordWidget} ({@code addressTextFor} and {@code addressVisibleFor}). The full integration test
 * (attach to RootLayoutWidget + per-anchor stacking) is verified by Espresso Flow B in {@code
 * OfflineAddressFlowBCEspressoTest} — Robolectric coverage of those code paths would need extensive
 * ATAK-SDK mocking that adds little value over the on-device test.
 *
 * <p>Covered cases (mirrors {@code contracts/widget-address-rows.md §Test plan} where pure logic
 * permits):
 *
 * <ul>
 *   <li>State→text mapping for every variant.
 *   <li>State→visibility mapping for every variant.
 *   <li>{@link AddressRowState} equality, which drives the coalesce-on-equal optimisation in {@link
 *       TwCoordWidget#renderAddresses}.
 * </ul>
 */
public final class TwCoordWidgetAddressRowTest {

  @Test
  public void addressTextFor_textVariantReturnsValue() {
    String s = TwCoordWidget.addressTextFor(AddressRowState.text("台北市信義區"), "loading", "empty");
    assertThat(s).isEqualTo("台北市信義區");
  }

  @Test
  public void addressTextFor_loadingReturnsLoadingFallback() {
    String s = TwCoordWidget.addressTextFor(AddressRowState.loading(), "Loading…", "Empty");
    assertThat(s).isEqualTo("Loading…");
  }

  @Test
  public void addressTextFor_emptyStateReturnsEmptyFallback() {
    String s = TwCoordWidget.addressTextFor(AddressRowState.emptyState(), "Loading…", "No nearby");
    assertThat(s).isEqualTo("No nearby");
  }

  @Test
  public void addressTextFor_hiddenReturnsBlank() {
    String s = TwCoordWidget.addressTextFor(AddressRowState.hidden(), "Loading…", "No nearby");
    assertThat(s).isEmpty();
  }

  @Test
  public void addressVisibleFor_hiddenIsFalse() {
    assertThat(TwCoordWidget.addressVisibleFor(AddressRowState.hidden())).isFalse();
  }

  @Test
  public void addressVisibleFor_loadingIsTrue() {
    assertThat(TwCoordWidget.addressVisibleFor(AddressRowState.loading())).isTrue();
  }

  @Test
  public void addressVisibleFor_textIsTrue() {
    assertThat(TwCoordWidget.addressVisibleFor(AddressRowState.text("anything"))).isTrue();
  }

  @Test
  public void addressVisibleFor_emptyStateIsTrue() {
    assertThat(TwCoordWidget.addressVisibleFor(AddressRowState.emptyState())).isTrue();
  }

  @Test
  public void addressRowState_equalityDrivesCoalesce() {
    // Text variants with the same string are equal — coalesce will skip a second setText.
    assertThat(AddressRowState.text("X")).isEqualTo(AddressRowState.text("X"));
    assertThat(AddressRowState.text("X")).isNotEqualTo(AddressRowState.text("Y"));
    // Singletons are reference-equal (and value-equal).
    assertThat(AddressRowState.hidden()).isSameAs(AddressRowState.hidden());
    assertThat(AddressRowState.loading()).isSameAs(AddressRowState.loading());
    assertThat(AddressRowState.emptyState()).isSameAs(AddressRowState.emptyState());
  }

  @Test
  public void addressTextFor_nullArgsAreSafe() {
    // Defensive: helper never returns null and never throws on null inputs.
    assertThat(TwCoordWidget.addressTextFor(null, "L", "E")).isEmpty();
    assertThat(TwCoordWidget.addressTextFor(AddressRowState.loading(), null, "E")).isEmpty();
    assertThat(TwCoordWidget.addressTextFor(AddressRowState.emptyState(), "L", null)).isEmpty();
    assertThat(TwCoordWidget.addressVisibleFor(null)).isFalse();
  }
}
