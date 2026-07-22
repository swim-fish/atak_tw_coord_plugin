package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.lookup.AddressAvailability;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressLookupService;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.AddressResolution;
import com.atakmap.android.twcoord.address.lookup.DatasetIdentity;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressResult;
import com.atakmap.android.twcoord.address.lookup.LookupHandle;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressResult;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.Test;

public final class AddressEntryControllerConcurrencyTest {

  @Test
  public void oneHundredAlternatingReverseCompletionsAcceptOnlyLatestAndNeverSnap() {
    FakeService service = new FakeService();
    AddressEntryController controller = controller(service);
    AtomicInteger humanChanges = new AtomicInteger();
    controller.setOnHumanChange(humanChanges::incrementAndGet);
    Wgs84 latest = null;
    for (int index = 0; index < 100; index++) {
      latest =
          new Wgs84(
              index % 2 == 0 ? 25.033 : 24.147,
              index % 2 == 0 ? 121.565 : 120.673,
              index + 1L,
              Wgs84.Source.COT_TARGET);
      controller.activate(latest, true);
    }

    for (int index = 0; index < 99; index++) service.complete(index);
    assertThat(controller.resolution()).isNull();
    service.complete(99);

    AddressResolution resolution = controller.resolution();
    assertThat(resolution).isNotNull();
    assertThat(resolution.source()).isEqualTo(AddressResolution.Source.REVERSE_LABEL);
    assertThat(resolution.resolvedPoint()).isSameAs(latest);
    assertThat(resolution.recordPoint()).isNotEqualTo(latest);
    assertThat(humanChanges).hasValue(0);
  }

  @Test
  public void editAndDatasetRevisionFenceLateReverseCompletion() {
    FakeService service = new FakeService();
    AddressEntryController controller = controller(service);
    controller.activate(new Wgs84(25.033, 121.565, 1L, Wgs84.Source.COT_TARGET), true);
    controller.editFull("臺北市信義區市府路2號", true);
    service.complete(0);
    assertThat(controller.resolution()).isNull();

    controller.activate(new Wgs84(25.034, 121.566, 2L, Wgs84.Source.COT_TARGET), true);
    service.datasetRevision = 2L;
    service.complete(1);
    assertThat(controller.resolution()).isNull();
  }

  private static AddressEntryController controller(FakeService service) {
    return new AddressEntryController(
        service, new TaiwanAddressParser(), (runnable, delayMs) -> () -> {}, 20);
  }

  private static final class FakeService implements AddressLookupService {
    final List<ReverseAddressRequest> requests = new ArrayList<>();
    final List<Consumer<ReverseAddressResult>> callbacks = new ArrayList<>();
    long datasetRevision = 1L;

    @Override
    public LookupHandle forward(
        ForwardAddressRequest request, Consumer<ForwardAddressResult> callback) {
      return new Handle();
    }

    @Override
    public LookupHandle reverse(
        ReverseAddressRequest request, Consumer<ReverseAddressResult> callback) {
      requests.add(request);
      callbacks.add(callback);
      return new Handle();
    }

    void complete(int index) {
      ReverseAddressRequest request = requests.get(index);
      Wgs84 query = request.queryPoint();
      Wgs84 record =
          new Wgs84(
              query.latitudeDeg() + 0.0001,
              query.longitudeDeg() + 0.0001,
              query.timestampEpochMs(),
              Wgs84.Source.COT_TARGET);
      DatasetIdentity dataset = new DatasetIdentity("臺北市", "115-07", 3, "fixture-sha", "fixture");
      AddressCandidate candidate =
          new AddressCandidate(
              "candidate-" + index,
              "臺北市信義區市府路" + index + "號",
              "臺北市信義區市府路" + index + "號",
              record,
              AddressMatchKind.PARTIAL,
              12d,
              "臺北市",
              dataset);
      callbacks.get(index).accept(ReverseAddressResult.found(request.identity(), query, candidate));
    }

    @Override
    public AddressAvailability availability() {
      return new AddressAvailability(Collections.singleton("臺北市"), true, datasetRevision, false);
    }

    @Override
    public void addAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void removeAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void close() {}
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
