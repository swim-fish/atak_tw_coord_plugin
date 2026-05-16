package com.atakmap.android.twpower;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twpower.coord.Wgs84;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.junit.Test;

public class SelfMarkerSubscriberTest {

  private static final long DEBOUNCE_MS = 1_000;
  private static final long STALE_MS = 10_000;

  private final long[] now = {0L};
  private final LongSupplier clock = () -> now[0];

  private static class Captor implements SelfMarkerSubscriber.Listener {
    final List<Wgs84> fresh = new ArrayList<>();
    int staleCount = 0;

    @Override
    public void onFreshFix(Wgs84 fix) {
      fresh.add(fix);
    }

    @Override
    public void onStale() {
      staleCount++;
    }
  }

  /** 5 Hz inbound (every 200 ms) over 2 s should emit ~2 fresh-fix events at 1 Hz debounce. */
  @Test
  public void inbound_at_5hz_emits_at_1hz_debounce() {
    Captor c = new Captor();
    SelfMarkerSubscriber sub = new SelfMarkerSubscriber(clock, DEBOUNCE_MS, STALE_MS, c);

    for (int i = 0; i < 11; i++) {
      now[0] = i * 200L;
      sub.onEvent(fix(25.0 + 0.0001 * i, 121.0 + 0.0001 * i));
    }

    // Events at t=0 and t=1000 should fire (after 1s debounce), t=2000 also.
    // t=0 fires immediately (lastEmittedAt is MIN_VALUE so diff is huge), then 1000, then 2000.
    assertThat(c.fresh).hasSize(3);
  }

  @Test
  public void no_event_for_10s_then_tick_emits_stale_once() {
    Captor c = new Captor();
    SelfMarkerSubscriber sub = new SelfMarkerSubscriber(clock, DEBOUNCE_MS, STALE_MS, c);

    now[0] = 0L;
    sub.onEvent(fix(25.0, 121.0));
    assertThat(c.fresh).hasSize(1);
    assertThat(c.staleCount).isZero();

    now[0] = 9_999L;
    sub.tickStaleCheck();
    assertThat(c.staleCount).isZero();

    now[0] = 10_000L;
    sub.tickStaleCheck();
    assertThat(c.staleCount).isEqualTo(1);

    // Subsequent ticks while still stale must NOT re-emit.
    now[0] = 20_000L;
    sub.tickStaleCheck();
    assertThat(c.staleCount).isEqualTo(1);
  }

  @Test
  public void fresh_event_after_stale_resets_stale_flag() {
    Captor c = new Captor();
    SelfMarkerSubscriber sub = new SelfMarkerSubscriber(clock, DEBOUNCE_MS, STALE_MS, c);

    now[0] = 0L;
    sub.onEvent(fix(25.0, 121.0));
    now[0] = 11_000L;
    sub.tickStaleCheck();
    assertThat(c.staleCount).isEqualTo(1);

    // Recovery: a fresh inbound fix at t=12s should fire onFreshFix and clear the stale flag.
    now[0] = 12_000L;
    sub.onEvent(fix(25.001, 121.001));
    assertThat(c.fresh).hasSize(2);

    // If GPS stops again, after another 10s of silence we should see onStale fire AGAIN.
    now[0] = 22_001L;
    sub.tickStaleCheck();
    assertThat(c.staleCount).isEqualTo(2);
  }

  @Test
  public void tick_before_any_event_does_nothing() {
    Captor c = new Captor();
    SelfMarkerSubscriber sub = new SelfMarkerSubscriber(clock, DEBOUNCE_MS, STALE_MS, c);
    now[0] = 100_000L;
    sub.tickStaleCheck();
    assertThat(c.staleCount).isZero();
  }

  private static Wgs84 fix(double lat, double lon) {
    return new Wgs84(lat, lon, 1L, Wgs84.Source.DEVICE_LOCATION);
  }
}
