package com.atakmap.android.twcoord.address.lookup;

/** Idempotent cancellation token for one asynchronous lookup. */
public interface LookupHandle {
  void cancel();

  boolean isCancelled();
}
