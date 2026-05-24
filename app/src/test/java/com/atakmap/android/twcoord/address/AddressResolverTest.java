package com.atakmap.android.twcoord.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

/**
 * Feature 004 — {@link AddressResolver} unit tests per {@code contracts/address-resolver.md §Test
 * plan}. Pure JVM (no Robolectric) — the resolver is a 30-line wrapper with no Android
 * dependencies; tests mock {@link AddressDatabaseFacade} directly.
 */
public final class AddressResolverTest {

  @Test
  public void lookup_returnsFoundForNearestRecord() {
    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    AddressRecord record = new AddressRecord(24.15, 120.65, "X", "X");
    when(facade.nearestWithin(24.15, 120.65, 500.0)).thenReturn(record);

    AddressResolver r = new AddressResolver(facade, 500.0);
    AddressLookupResult result = r.lookup(24.15, 120.65);

    assertThat(result.isFound()).isTrue();
    assertThat(((AddressLookupResult.Found) result).record()).isSameAs(record);
  }

  @Test
  public void lookup_returnsEmptyWhenFacadeReturnsNull() {
    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    when(facade.nearestWithin(anyDouble(), anyDouble(), anyDouble())).thenReturn(null);

    AddressResolver r = new AddressResolver(facade, 500.0);
    AddressLookupResult result = r.lookup(24.15, 120.65);

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  public void lookup_returnsNoDatasetWhenFacadeIsNull() {
    AddressResolver r = new AddressResolver(null, 500.0);
    AddressLookupResult result = r.lookup(24.15, 120.65);

    assertThat(result.isNoDataset()).isTrue();
  }

  @Test
  public void radiusDefaultsTo500m() {
    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    AddressResolver r = new AddressResolver(facade, 500.0);

    r.lookup(24.15, 120.65);
    verify(facade).nearestWithin(eq(24.15), eq(120.65), eq(500.0));
  }

  @Test
  public void radiusOverrideRespected() {
    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    AddressResolver r = new AddressResolver(facade, 100.0);

    r.lookup(24.15, 120.65);
    verify(facade).nearestWithin(eq(24.15), eq(120.65), eq(100.0));
    assertThat(r.radiusMeters()).isEqualTo(100.0);
  }

  @Test
  public void lookup_handlesFacadeThrowingCleanly() {
    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    when(facade.nearestWithin(anyDouble(), anyDouble(), anyDouble()))
        .thenThrow(new RuntimeException("boom"));

    AddressResolver r = new AddressResolver(facade, 500.0);
    AddressLookupResult result = r.lookup(24.15, 120.65);

    assertThat(result.isEmpty())
        .as("facade exception MUST map to Empty, not propagate (Constitution VI)")
        .isTrue();
  }

  @Test
  public void bboxCorrectionAtLatitude25() {
    // Sanity: the cos-latitude correction logic lives in SqliteAddressDatabase, not the
    // resolver — the resolver is a pure pass-through. This test asserts the resolver does
    // not try to second-guess radius semantics (e.g. by adjusting for latitude before the
    // facade call). The radius the facade sees MUST be exactly the construction param.
    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    AddressResolver r = new AddressResolver(facade, 500.0);

    r.lookup(25.0, 121.5);
    verify(facade).nearestWithin(eq(25.0), eq(121.5), eq(500.0));
  }

  @Test
  public void lookup_isPureNoCaching() {
    AddressDatabaseFacade facade = mock(AddressDatabaseFacade.class);
    AddressResolver r = new AddressResolver(facade, 500.0);

    r.lookup(24.15, 120.65);
    r.lookup(24.15, 120.65);

    verify(facade, times(2)).nearestWithin(24.15, 120.65, 500.0);
  }
}
