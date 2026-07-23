package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;

import com.atakmap.android.twcoord.address.lookup.AddressAvailability;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressInputMode;
import com.atakmap.android.twcoord.address.lookup.AddressLookupService;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.AddressResolution;
import com.atakmap.android.twcoord.address.lookup.AddressValidation;
import com.atakmap.android.twcoord.address.lookup.DatasetIdentity;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressResult;
import com.atakmap.android.twcoord.address.lookup.LookupHandle;
import com.atakmap.android.twcoord.address.lookup.ResultOrdering;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressResult;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Test;

public final class AddressEntryControllerTest {

  @Test
  public void fullEditDebouncesFor250MsAndNotifiesImmediateHumanChange() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    AtomicInteger changes = new AtomicInteger();
    controller.setOnHumanChange(changes::incrementAndGet);

    controller.editFull("臺中市南屯區黎明路2段130號", true);

    assertThat(controller.draft().mode()).isEqualTo(AddressInputMode.FULL);
    assertThat(controller.validation()).isEqualTo(AddressValidation.READY_TO_LOOKUP);
    assertThat(debouncer.delayMs).isEqualTo(250L);
    assertThat(service.forwardRequest).isNull();
    assertThat(changes).hasValue(1);

    debouncer.runPending();
    assertThat(service.forwardRequest).isNotNull();
    assertThat(controller.validation()).isEqualTo(AddressValidation.LOOKUP_PENDING);
  }

  @Test
  public void forwardRequestCapturesCurrentOrderingPreference() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AtomicReference<ResultOrdering> ordering = new AtomicReference<>(ResultOrdering.MOST_SIMILAR);
    AddressEntryController controller =
        new AddressEntryController(
            service, new TaiwanAddressParser(), debouncer, 20, ordering::get);

    controller.editFull("臺中市南屯區黎明路2段130號", true);
    debouncer.runPending();

    assertThat(service.forwardRequest.ordering()).isEqualTo(ResultOrdering.MOST_SIMILAR);

    ordering.set(ResultOrdering.DISTANCE);
    controller.editFull("臺中市南屯區黎明路2段132號", true);
    debouncer.runPending();

    assertThat(service.forwardRequest.ordering()).isEqualTo(ResultOrdering.DISTANCE);
  }

  @Test
  public void forwardRequestCapturesCurrentMapAnchor() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    Wgs84 mapAnchor = new Wgs84(24.161989237766, 120.647296501898, 1L, Wgs84.Source.MAP_CENTRE);
    AddressEntryController controller =
        new AddressEntryController(
            service,
            new TaiwanAddressParser(),
            debouncer,
            20,
            () -> ResultOrdering.DISTANCE,
            () -> mapAnchor);

    controller.editFull("臺中市西屯區臺灣大道三段9", true);
    debouncer.runPending();

    assertThat(service.forwardRequest.anchorPoint()).isEqualTo(mapAnchor);
  }

  @Test
  public void uniqueExactCommitsResolutionThenNotifiesHumanListener() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    StringBuilder order = new StringBuilder();
    controller.setOnStateChanged(() -> order.append('S'));
    controller.setOnHumanChange(() -> order.append('H'));
    controller.editFull("臺中市南屯區黎明路2段130號", true);
    order.setLength(0);
    debouncer.runPending();

    service.complete(
        ForwardAddressResult.candidates(
            service.forwardRequest.identity(), Collections.singletonList(exact("130號"))));

    assertThat(controller.validation()).isEqualTo(AddressValidation.RESOLVED);
    assertThat(controller.resolution()).isNotNull();
    assertThat(controller.resolution().source()).isEqualTo(AddressResolution.Source.UNIQUE_EXACT);
    assertThat(order.toString()).endsWith("SH");
  }

  @Test
  public void partialOrMultipleCandidatesRemainAmbiguousUntilSelection() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    controller.editFull("臺中市南屯區黎明路2段130號", true);
    debouncer.runPending();
    AddressCandidate partial = candidate("132號", AddressMatchKind.PARTIAL, 2);

    service.complete(
        ForwardAddressResult.candidates(
            service.forwardRequest.identity(),
            Arrays.asList(partial, candidate("134號", AddressMatchKind.PARTIAL, 3))));

    assertThat(controller.validation()).isEqualTo(AddressValidation.AMBIGUOUS);
    assertThat(controller.resolution()).isNull();
    assertThat(controller.candidates()).hasSize(2);

    long revision = controller.draft().draftRevision();
    controller.selectCandidate(partial.candidateId(), true);
    assertThat(controller.validation()).isEqualTo(AddressValidation.RESOLVED);
    assertThat(controller.resolution().source())
        .isEqualTo(AddressResolution.Source.OPERATOR_SELECTED);
    assertThat(controller.draft().rawAddress()).isEqualTo(partial.displayAddress());
    assertThat(controller.draft().draftRevision()).isEqualTo(revision);
    assertThat(controller.candidates()).hasSize(2);
  }

  @Test
  public void structuredSelectionProjectsChosenFullAddressWithoutNewRevision() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    controller.editStructured("臺中市", "南屯區", "黎明路2段", "130號", true);
    debouncer.runPending();
    AddressCandidate selected = candidate("132號", AddressMatchKind.PARTIAL, 2);
    service.complete(
        ForwardAddressResult.candidates(
            service.forwardRequest.identity(),
            Arrays.asList(selected, candidate("134號", AddressMatchKind.PARTIAL, 3))));
    long revision = controller.draft().draftRevision();

    controller.selectCandidate(selected.candidateId(), true);

    assertThat(controller.draft().mode()).isEqualTo(AddressInputMode.STRUCTURED);
    assertThat(controller.draft().rawAddress()).isEqualTo(selected.displayAddress());
    assertThat(controller.draft().components().countyCity()).isEqualTo("臺中市");
    assertThat(controller.draft().components().districtTownship()).isEqualTo("南屯區");
    assertThat(controller.draft().components().roadLocality()).isEqualTo("黎明路2段");
    assertThat(controller.draft().structuredTail()).isEqualTo("132號");
    assertThat(controller.draft().draftRevision()).isEqualTo(revision);
  }

  @Test
  public void structuredBareNumberRemainsUnmodifiedWhileEditing() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);

    controller.editStructured("臺中市", "西屯區", "惠來里臺灣大道3段", "9", true);

    assertThat(controller.draft().rawAddress()).isEqualTo("臺中市西屯區惠來里臺灣大道3段9");
    assertThat(controller.draft().structuredTail()).isEqualTo("9");
    assertThat(controller.draft().components().tail()).isEmpty();
    assertThat(controller.draft().unclassifiedText()).isEqualTo("9");
    assertThat(controller.validation()).isEqualTo(AddressValidation.READY_TO_LOOKUP);
  }

  @Test
  public void identicalImeCommitDoesNotInvalidateOpenCandidates() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    String address = "臺中市南屯區黎明路2段130號";
    controller.editFull(address, true);
    debouncer.runPending();
    AddressCandidate first = candidate("132號", AddressMatchKind.PARTIAL, 2);
    AddressCandidate second = candidate("134號", AddressMatchKind.PARTIAL, 3);
    service.complete(
        ForwardAddressResult.candidates(
            service.forwardRequest.identity(), Arrays.asList(first, second)));
    long revision = controller.draft().draftRevision();

    controller.editFull(address, true);

    assertThat(controller.draft().draftRevision()).isEqualTo(revision);
    assertThat(controller.validation()).isEqualTo(AddressValidation.AMBIGUOUS);
    assertThat(controller.candidates()).containsExactly(first, second);
    assertThat(debouncer.pending).isNull();
  }

  @Test
  public void identicalStructuredImeCommitDoesNotInvalidateOpenCandidates() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    controller.editStructured("臺中市", "南屯區", "黎明路2段", "130號", true);
    debouncer.runPending();
    AddressCandidate first = candidate("132號", AddressMatchKind.PARTIAL, 2);
    AddressCandidate second = candidate("134號", AddressMatchKind.PARTIAL, 3);
    service.complete(
        ForwardAddressResult.candidates(
            service.forwardRequest.identity(), Arrays.asList(first, second)));
    long revision = controller.draft().draftRevision();

    controller.editStructured("臺中市", "南屯區", "黎明路2段", "130號", true);

    assertThat(controller.draft().draftRevision()).isEqualTo(revision);
    assertThat(controller.validation()).isEqualTo(AddressValidation.AMBIGUOUS);
    assertThat(controller.candidates()).containsExactly(first, second);
    assertThat(debouncer.pending).isNull();
  }

  @Test
  public void multipleExactCandidatesRemainAmbiguousUntilSelection() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    controller.editFull("臺中市大甲區中山路1段1號", true);
    debouncer.runPending();

    service.complete(
        ForwardAddressResult.candidates(
            service.forwardRequest.identity(), Arrays.asList(exact("1號"), exact("1號之二"))));

    assertThat(controller.validation()).isEqualTo(AddressValidation.AMBIGUOUS);
    assertThat(controller.resolution()).isNull();
    assertThat(controller.candidates()).hasSize(2);
  }

  @Test
  public void editCancelsAndInvalidatesResolutionAndLateResultCannotWin() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    controller.editFull("臺中市南屯區黎明路2段130號", true);
    debouncer.runPending();
    Consumer<ForwardAddressResult> staleCallback = service.forwardCallback;
    ForwardAddressRequest staleRequest = service.forwardRequest;

    controller.editFull("臺中市南屯區黎明路2段132號", true);
    staleCallback.accept(
        ForwardAddressResult.candidates(
            staleRequest.identity(), Collections.singletonList(exact("130號"))));

    assertThat(controller.resolution()).isNull();
    assertThat(controller.draft().normalizedAddress()).endsWith("132號");
  }

  @Test
  public void failureIsContainedAndDisposeIsTerminal() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    controller.editFull("臺中市南屯區黎明路2段130號", true);
    debouncer.runPending();
    service.complete(
        ForwardAddressResult.failure(
            service.forwardRequest.identity(), new IllegalStateException("db")));
    assertThat(controller.validation()).isEqualTo(AddressValidation.FAILURE);

    controller.dispose();
    controller.dispose();
    controller.editFull("臺北市松山區八德路4段1號", true);
    assertThat(controller.validation()).isEqualTo(AddressValidation.DISPOSED);
    assertThat(controller.resolution()).isNull();
  }

  @Test
  public void modeSwitchIsPureProjectionAndDoesNotRelookupOrNotifyHost() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    AtomicInteger humanChanges = new AtomicInteger();
    controller.setOnHumanChange(humanChanges::incrementAndGet);
    controller.editFull("臺中市南屯區黎明路2段130號A棟", true);
    long revision = controller.draft().draftRevision();
    int schedules = debouncer.scheduleCount;
    humanChanges.set(0);

    for (int index = 0; index < 10; index++) {
      controller.switchMode(index % 2 == 0 ? AddressInputMode.STRUCTURED : AddressInputMode.FULL);
    }

    assertThat(controller.draft().draftRevision()).isEqualTo(revision);
    assertThat(controller.draft().composeStructured()).isEqualTo("臺中市南屯區黎明路2段130號A棟");
    assertThat(debouncer.scheduleCount).isEqualTo(schedules);
    assertThat(humanChanges).hasValue(0);
  }

  @Test
  public void structuredEditRecombinesCanonicalDraftAndSchedulesOneLookup() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    controller.switchMode(AddressInputMode.STRUCTURED);

    controller.editStructured("臺中市", "南屯區", "黎明路2段", "132號A棟", true);

    assertThat(controller.draft().mode()).isEqualTo(AddressInputMode.STRUCTURED);
    assertThat(controller.draft().rawAddress()).isEqualTo("臺中市南屯區黎明路2段132號A棟");
    assertThat(controller.draft().structuredTail()).isEqualTo("132號A棟");
    assertThat(debouncer.scheduleCount).isEqualTo(1);
  }

  @Test
  public void readOnlyAllowsModeProjectionButRejectsTextMutation() {
    FakeService service = new FakeService();
    ManualDebouncer debouncer = new ManualDebouncer();
    AddressEntryController controller = controller(service, debouncer);
    controller.editFull("臺中市南屯區黎明路2段130號", false);
    String before = controller.draft().rawAddress();
    long revision = controller.draft().draftRevision();
    controller.setEditable(false);

    controller.switchMode(AddressInputMode.STRUCTURED);
    controller.editStructured("臺北市", "信義區", "市府路", "1號", true);

    assertThat(controller.draft().mode()).isEqualTo(AddressInputMode.STRUCTURED);
    assertThat(controller.draft().rawAddress()).isEqualTo(before);
    assertThat(controller.draft().draftRevision()).isEqualTo(revision);
  }

  private static AddressEntryController controller(FakeService service, ManualDebouncer debouncer) {
    return new AddressEntryController(service, new TaiwanAddressParser(), debouncer, 20);
  }

  private static AddressCandidate exact(String number) {
    return candidate(number, AddressMatchKind.EXACT, 1);
  }

  private static AddressCandidate candidate(String number, AddressMatchKind kind, double distance) {
    DatasetIdentity dataset =
        new DatasetIdentity(
            "臺中市",
            "115-07",
            3,
            "1111111111111111111111111111111111111111111111111111111111111111",
            "fixture");
    String display = "臺中市南屯區黎明路2段" + number;
    return new AddressCandidate(
        number,
        display,
        display,
        new Wgs84(24.15, 120.65, 1L, Wgs84.Source.COT_TARGET),
        kind,
        distance,
        "臺中市",
        dataset);
  }

  private static final class ManualDebouncer implements AddressEntryController.Debouncer {
    Runnable pending;
    long delayMs;
    int scheduleCount;

    @Override
    public AddressEntryController.Cancellable schedule(Runnable runnable, long delayMs) {
      this.pending = runnable;
      this.delayMs = delayMs;
      scheduleCount++;
      AtomicBoolean cancelled = new AtomicBoolean();
      return () -> cancelled.set(true);
    }

    void runPending() {
      Runnable runnable = pending;
      pending = null;
      runnable.run();
    }
  }

  private static final class FakeService implements AddressLookupService {
    ForwardAddressRequest forwardRequest;
    Consumer<ForwardAddressResult> forwardCallback;

    @Override
    public LookupHandle forward(
        ForwardAddressRequest request, Consumer<ForwardAddressResult> callback) {
      forwardRequest = request;
      forwardCallback = callback;
      AtomicBoolean cancelled = new AtomicBoolean();
      return new LookupHandle() {
        @Override
        public void cancel() {
          cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
          return cancelled.get();
        }
      };
    }

    void complete(ForwardAddressResult result) {
      forwardCallback.accept(result);
    }

    @Override
    public LookupHandle reverse(
        ReverseAddressRequest request, Consumer<ReverseAddressResult> callback) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AddressAvailability availability() {
      return new AddressAvailability(Collections.singleton("臺中市"), false, 1L, false);
    }

    @Override
    public void addAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void removeAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void close() {}
  }
}
