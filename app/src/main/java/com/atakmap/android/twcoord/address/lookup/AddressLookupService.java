package com.atakmap.android.twcoord.address.lookup;

import java.util.function.Consumer;

/** UI-independent asynchronous owner of all runtime address lookup work. */
public interface AddressLookupService extends AutoCloseable {
  interface AvailabilityListener {
    void onAvailabilityChanged(AddressAvailability availability);
  }

  LookupHandle forward(ForwardAddressRequest request, Consumer<ForwardAddressResult> callback);

  LookupHandle reverse(ReverseAddressRequest request, Consumer<ReverseAddressResult> callback);

  AddressAvailability availability();

  void addAvailabilityListener(AvailabilityListener listener);

  void removeAvailabilityListener(AvailabilityListener listener);

  @Override
  void close();
}
