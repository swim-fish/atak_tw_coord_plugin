package com.atakmap.android.twcoord;

import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Pure-Java debouncer + stale detector around the ATAK self-marker stream (T038). Takes inbound
 * fixes at whatever cadence the SDK fires them; emits "fresh fix" at most once per debounce window,
 * and "stale" exactly once when no inbound fix has arrived for {@code staleMs}.
 *
 * <p>The clock is injectable so unit tests can drive time deterministically.
 */
public final class SelfMarkerSubscriber {

  public interface Listener {
    void onFreshFix(Wgs84 fix);

    void onStale();
  }

  private final LongSupplier clock;
  private final long debounceMs;
  private final long staleMs;
  private final Listener listener;

  private long lastEmittedAt;
  private long lastReceivedAt;
  private boolean haveEmitted = false;
  private boolean haveReceived = false;
  private boolean staleSignalled = false;

  public SelfMarkerSubscriber(
      LongSupplier clock, long debounceMs, long staleMs, Listener listener) {
    this.clock = Objects.requireNonNull(clock, "clock");
    if (debounceMs < 0) throw new IllegalArgumentException("debounceMs must be >= 0");
    if (staleMs < debounceMs) {
      throw new IllegalArgumentException("staleMs must be >= debounceMs");
    }
    this.debounceMs = debounceMs;
    this.staleMs = staleMs;
    this.listener = Objects.requireNonNull(listener, "listener");
  }

  /** Called every time the ATAK self-marker reports an updated position. */
  public void onEvent(Wgs84 fix) {
    Objects.requireNonNull(fix, "fix");
    long now = clock.getAsLong();
    lastReceivedAt = now;
    haveReceived = true;
    staleSignalled = false;
    if (!haveEmitted || now - lastEmittedAt >= debounceMs) {
      lastEmittedAt = now;
      haveEmitted = true;
      listener.onFreshFix(fix);
    }
  }

  /**
   * Caller MUST invoke this periodically (e.g., once per second from a Handler.postDelayed loop on
   * the UI thread). If no fix has arrived for {@code staleMs}, emits {@link Listener#onStale()}
   * exactly once until the next fresh fix.
   */
  public void tickStaleCheck() {
    if (!haveReceived) return;
    long now = clock.getAsLong();
    if (now - lastReceivedAt >= staleMs && !staleSignalled) {
      staleSignalled = true;
      listener.onStale();
    }
  }
}
