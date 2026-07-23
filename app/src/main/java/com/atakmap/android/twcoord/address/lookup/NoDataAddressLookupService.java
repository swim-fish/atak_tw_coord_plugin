package com.atakmap.android.twcoord.address.lookup;

import com.atakmap.coremap.log.Log;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Closed-over no-dataset implementation used before the address subsystem is mounted. */
public final class NoDataAddressLookupService implements AddressLookupService {
  private static final String TAG = "NoDataAddressLookup";

  private final Executor completionDispatcher;
  private final List<AvailabilityListener> listeners = new CopyOnWriteArrayList<>();
  private final AtomicBoolean closed = new AtomicBoolean();

  public NoDataAddressLookupService(Executor completionDispatcher) {
    this.completionDispatcher =
        Objects.requireNonNull(completionDispatcher, "completionDispatcher");
  }

  @Override
  public LookupHandle forward(
      ForwardAddressRequest request, Consumer<ForwardAddressResult> callback) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(callback, "callback");
    ensureOpen();
    Handle handle = new Handle();
    dispatch(handle, () -> callback.accept(ForwardAddressResult.noDataset(request.identity())));
    return handle;
  }

  @Override
  public LookupHandle reverse(
      ReverseAddressRequest request, Consumer<ReverseAddressResult> callback) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(callback, "callback");
    ensureOpen();
    Handle handle = new Handle();
    dispatch(
        handle,
        () ->
            callback.accept(
                ReverseAddressResult.noDataset(request.identity(), request.queryPoint())));
    return handle;
  }

  @Override
  public AddressAvailability availability() {
    return new AddressAvailability(Collections.emptySet(), false, 0L, closed.get());
  }

  @Override
  public void addAvailabilityListener(AvailabilityListener listener) {
    if (listener == null) return;
    synchronized (listeners) {
      if (!closed.get()) listeners.add(listener);
    }
  }

  @Override
  public void removeAvailabilityListener(AvailabilityListener listener) {
    if (listener != null) listeners.remove(listener);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    synchronized (listeners) {
      listeners.clear();
    }
  }

  private void ensureOpen() {
    if (closed.get()) throw new IllegalStateException("service is closed");
  }

  private void dispatch(Handle handle, Runnable callback) {
    completionDispatcher.execute(
        () -> {
          if (closed.get() || handle.isCancelled()) return;
          try {
            callback.run();
          } catch (RuntimeException e) {
            Log.w(TAG, "completion callback threw", e);
          }
        });
  }

  private static final class Handle implements LookupHandle {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    @Override
    public void cancel() {
      cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
      return cancelled.get();
    }
  }
}
