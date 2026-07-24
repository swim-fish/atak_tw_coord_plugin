package com.atakmap.android.twcoord.nativeentry;

import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressDraft;
import com.atakmap.android.twcoord.address.lookup.AddressInputMode;
import com.atakmap.android.twcoord.address.lookup.AddressLookupService;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.AddressResolution;
import com.atakmap.android.twcoord.address.lookup.AddressValidation;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressResult;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorRequest;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorResult;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorSnapshot;
import com.atakmap.android.twcoord.address.lookup.LookupHandle;
import com.atakmap.android.twcoord.address.lookup.LookupIdentity;
import com.atakmap.android.twcoord.address.lookup.LookupPriority;
import com.atakmap.android.twcoord.address.lookup.ResultOrdering;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressResult;
import com.atakmap.android.twcoord.address.lookup.StreetTextNormaliser;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import com.atakmap.android.twcoord.coord.Wgs84;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Host-independent full-address entry session with revision-fenced asynchronous lookup. */
public final class AddressEntryController {
  static final long DEBOUNCE_MS = 250L;
  static final double REVERSE_RADIUS_METERS = 500.0;

  public interface Cancellable {
    void cancel();
  }

  public interface Debouncer extends AutoCloseable {
    Cancellable schedule(Runnable runnable, long delayMs);

    @Override
    default void close() {}
  }

  private final AddressLookupService lookupService;
  private final TaiwanAddressParser parser;
  private final Debouncer debouncer;
  private final int candidateLimit;
  private final Supplier<ResultOrdering> orderingSupplier;
  private final Supplier<Wgs84> forwardAnchorSupplier;
  private final AddressLookupService.AvailabilityListener availabilityListener =
      ignored -> onAvailabilityChanged();

  private AddressDraft draft = AddressDraft.empty(0L, AddressInputMode.FULL);
  private List<AddressCandidate> candidates = Collections.emptyList();
  private AddressResolution resolution;
  private LookupHandle lookupHandle;
  private LookupHandle localityHandle;
  private LookupIdentity localityIdentity;
  private LookupIdentity currentIdentity;
  private Cancellable pendingDebounce;
  private Runnable onStateChanged;
  private Runnable onHumanChange;
  private long generation = 1L;
  private boolean editable = true;
  private boolean disposed;
  private Wgs84 reversePoint;
  private Wgs84 forwardAnchor;

  public AddressEntryController(AddressLookupService lookupService) {
    this(
        lookupService,
        new TaiwanAddressParser(),
        new ScheduledDebouncer(),
        20,
        () -> ResultOrdering.DISTANCE,
        () -> null);
  }

  public AddressEntryController(
      AddressLookupService lookupService, Supplier<ResultOrdering> orderingSupplier) {
    this(
        lookupService,
        new TaiwanAddressParser(),
        new ScheduledDebouncer(),
        20,
        orderingSupplier,
        () -> null);
  }

  public AddressEntryController(
      AddressLookupService lookupService,
      Supplier<ResultOrdering> orderingSupplier,
      Supplier<Wgs84> forwardAnchorSupplier) {
    this(
        lookupService,
        new TaiwanAddressParser(),
        new ScheduledDebouncer(),
        20,
        orderingSupplier,
        forwardAnchorSupplier);
  }

  AddressEntryController(
      AddressLookupService lookupService,
      TaiwanAddressParser parser,
      Debouncer debouncer,
      int candidateLimit) {
    this(
        lookupService,
        parser,
        debouncer,
        candidateLimit,
        () -> ResultOrdering.DISTANCE,
        () -> null);
  }

  AddressEntryController(
      AddressLookupService lookupService,
      TaiwanAddressParser parser,
      Debouncer debouncer,
      int candidateLimit,
      Supplier<ResultOrdering> orderingSupplier) {
    this(lookupService, parser, debouncer, candidateLimit, orderingSupplier, () -> null);
  }

  AddressEntryController(
      AddressLookupService lookupService,
      TaiwanAddressParser parser,
      Debouncer debouncer,
      int candidateLimit,
      Supplier<ResultOrdering> orderingSupplier,
      Supplier<Wgs84> forwardAnchorSupplier) {
    if (candidateLimit <= 0) throw new IllegalArgumentException("candidateLimit must be positive");
    this.lookupService = Objects.requireNonNull(lookupService, "lookupService");
    this.parser = Objects.requireNonNull(parser, "parser");
    this.debouncer = Objects.requireNonNull(debouncer, "debouncer");
    this.candidateLimit = candidateLimit;
    this.orderingSupplier = Objects.requireNonNull(orderingSupplier, "orderingSupplier");
    this.forwardAnchorSupplier =
        Objects.requireNonNull(forwardAnchorSupplier, "forwardAnchorSupplier");
    lookupService.addAvailabilityListener(availabilityListener);
  }

  public synchronized AddressDraft draft() {
    return draft;
  }

  public synchronized AddressValidation validation() {
    return disposed ? AddressValidation.DISPOSED : draft.validation();
  }

  public synchronized List<AddressCandidate> candidates() {
    return Collections.unmodifiableList(new ArrayList<>(candidates));
  }

  public synchronized AddressResolution resolution() {
    return disposed ? null : resolution;
  }

  public synchronized boolean isEditable() {
    return editable;
  }

  public synchronized boolean isDisposed() {
    return disposed;
  }

  public long datasetRevision() {
    return lookupService.availability().datasetRevision();
  }

  /**
   * Prepares one immutable locality snapshot on the shared lookup worker. The callback is already
   * delivered on the service completion dispatcher and is dropped when any session, draft, or
   * dataset identity changes.
   */
  public void prepareLocalities(
      LocalitySelectorSnapshot.Kind kind, Consumer<LocalitySelectorResult> callback) {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(callback, "callback");
    LocalitySelectorRequest request;
    synchronized (this) {
      if (disposed) return;
      if (kind == LocalitySelectorSnapshot.Kind.DISTRICT
          && draft.components().countyCity().isEmpty()) {
        return;
      }
      cancelLocalityLocked();
      LookupIdentity identity =
          new LookupIdentity(
              UUID.randomUUID().toString(),
              generation,
              draft.draftRevision(),
              lookupService.availability().datasetRevision());
      localityIdentity = identity;
      request =
          LocalitySelectorRequest.create(
              identity,
              kind == LocalitySelectorSnapshot.Kind.COUNTY
                  ? "native-address-county"
                  : "native-address-district",
              LookupPriority.NATIVE_INTERACTIVE,
              kind,
              kind == LocalitySelectorSnapshot.Kind.DISTRICT
                  ? draft.components().countyCity()
                  : null,
              currentForwardAnchor());
    }
    LookupHandle submitted;
    try {
      submitted =
          lookupService.localities(
              request,
              result -> {
                boolean accepted;
                synchronized (AddressEntryController.this) {
                  accepted =
                      !disposed
                          && result != null
                          && localityIdentity != null
                          && localityIdentity.equals(result.identity())
                          && result.identity().sessionGeneration() == generation
                          && result.identity().draftRevision() == draft.draftRevision()
                          && result.identity().datasetRevision()
                              == lookupService.availability().datasetRevision();
                  if (accepted) {
                    localityHandle = null;
                    localityIdentity = null;
                  }
                }
                if (accepted) callback.accept(result);
              });
    } catch (RuntimeException failure) {
      synchronized (this) {
        if (localityIdentity != null && localityIdentity.equals(request.identity())) {
          localityIdentity = null;
        }
      }
      callback.accept(LocalitySelectorResult.failure(request.identity(), failure));
      return;
    }
    synchronized (this) {
      if (disposed || localityIdentity == null || !localityIdentity.equals(request.identity())) {
        submitted.cancel();
      } else {
        localityHandle = submitted;
      }
    }
  }

  /** Applies a selector choice while preserving road/locality and tail correction text. */
  public void selectLocality(LocalitySelectorSnapshot.Kind kind, String value, boolean human) {
    selectLocality(kind, value, datasetRevision(), human);
  }

  public void selectLocality(
      LocalitySelectorSnapshot.Kind kind,
      String value,
      long expectedDatasetRevision,
      boolean human) {
    applyLocality(kind, value, null, expectedDatasetRevision, human);
  }

  public void selectLocality(
      LocalitySelectorSnapshot.Kind kind,
      String value,
      LookupIdentity expectedIdentity,
      boolean human) {
    Objects.requireNonNull(expectedIdentity, "expectedIdentity");
    applyLocality(kind, value, expectedIdentity, expectedIdentity.datasetRevision(), human);
  }

  private void applyLocality(
      LocalitySelectorSnapshot.Kind kind,
      String value,
      LookupIdentity expectedIdentity,
      long expectedDatasetRevision,
      boolean human) {
    Objects.requireNonNull(kind, "kind");
    String selected = valueOrEmpty(value);
    AddressDraft current;
    synchronized (this) {
      if (disposed
          || (human && !editable)
          || expectedDatasetRevision != lookupService.availability().datasetRevision()
          || (expectedIdentity != null
              && (expectedIdentity.sessionGeneration() != generation
                  || expectedIdentity.draftRevision() != draft.draftRevision()))) return;
      current = draft;
    }
    if (kind == LocalitySelectorSnapshot.Kind.COUNTY) {
      String retainedDistrict =
          !selected.isEmpty()
                  && StreetTextNormaliser.fold(selected)
                      .equals(StreetTextNormaliser.fold(current.components().countyCity()))
              ? current.components().districtTownship()
              : "";
      editStructured(
          selected,
          retainedDistrict,
          current.components().roadLocality(),
          current.structuredTail(),
          human);
    } else {
      editStructured(
          current.components().countyCity(),
          selected,
          current.components().roadLocality(),
          current.structuredTail(),
          human);
    }
  }

  public synchronized void setOnStateChanged(Runnable listener) {
    onStateChanged = listener;
  }

  public synchronized void setOnHumanChange(Runnable listener) {
    onHumanChange = listener;
  }

  public void editFull(String value, boolean human) {
    Runnable stateListener;
    Runnable humanListener;
    synchronized (this) {
      if (disposed || (human && !editable)) return;
      String next = valueOrEmpty(value);
      if (draft.mode() == AddressInputMode.FULL && next.equals(draft.rawAddress())) return;
      cancelPendingLocked();
      if (human) forwardAnchor = currentForwardAnchor();
      long revision = draft.draftRevision() + 1L;
      draft = parser.parse(next, revision, AddressInputMode.FULL);
      candidates = Collections.emptyList();
      resolution = null;
      currentIdentity = null;
      cancelLocalityLocked();
      reversePoint = null;
      if (draft.validation() == AddressValidation.READY_TO_LOOKUP) {
        pendingDebounce = debouncer.schedule(this::dispatchForward, DEBOUNCE_MS);
      }
      stateListener = onStateChanged;
      humanListener = human ? onHumanChange : null;
    }
    runListener(stateListener);
    runListener(humanListener);
  }

  public void editStructured(
      String countyCity, String districtTownship, String roadLocality, String tail, boolean human) {
    Runnable stateListener;
    Runnable humanListener;
    synchronized (this) {
      if (disposed || (human && !editable)) return;
      if (draft.mode() == AddressInputMode.STRUCTURED
          && valueOrEmpty(countyCity).equals(draft.components().countyCity())
          && valueOrEmpty(districtTownship).equals(draft.components().districtTownship())
          && valueOrEmpty(roadLocality).equals(draft.components().roadLocality())
          && valueOrEmpty(tail).equals(draft.structuredTail())) return;
      cancelPendingLocked();
      if (human) forwardAnchor = currentForwardAnchor();
      long revision = draft.draftRevision() + 1L;
      draft = parser.parseStructured(countyCity, districtTownship, roadLocality, tail, revision);
      candidates = Collections.emptyList();
      resolution = null;
      currentIdentity = null;
      cancelLocalityLocked();
      reversePoint = null;
      if (draft.validation() == AddressValidation.READY_TO_LOOKUP) {
        pendingDebounce = debouncer.schedule(this::dispatchForward, DEBOUNCE_MS);
      }
      stateListener = onStateChanged;
      humanListener = human ? onHumanChange : null;
    }
    runListener(stateListener);
    runListener(humanListener);
  }

  /** Switches only the visible projection; the semantic draft and lookup identity stay stable. */
  public void switchMode(AddressInputMode mode) {
    Runnable stateListener;
    synchronized (this) {
      if (disposed || mode == null || draft.mode() == mode) return;
      draft = draft.withMode(mode);
      stateListener = onStateChanged;
    }
    runListener(stateListener);
  }

  public void selectCandidate(String candidateId, boolean human) {
    Runnable stateListener;
    Runnable humanListener;
    synchronized (this) {
      if (disposed || (human && !editable) || candidateId == null || currentIdentity == null)
        return;
      AddressCandidate selected = null;
      for (AddressCandidate candidate : candidates) {
        if (candidateId.equals(candidate.candidateId())) {
          selected = candidate;
          break;
        }
      }
      if (selected == null) return;
      resolution =
          resolution(selected, AddressResolution.Source.OPERATOR_SELECTED, currentIdentity);
      reversePoint = null;
      draft =
          parser
              .parse(selected.displayAddress(), draft.draftRevision(), draft.mode())
              .withValidation(AddressValidation.RESOLVED);
      stateListener = onStateChanged;
      humanListener = human ? onHumanChange : null;
    }
    runListener(stateListener);
    runListener(humanListener);
  }

  public void clear(boolean human) {
    Runnable stateListener;
    Runnable humanListener;
    synchronized (this) {
      if (disposed || (human && !editable)) return;
      cancelPendingLocked();
      draft = AddressDraft.empty(draft.draftRevision() + 1L, draft.mode());
      candidates = Collections.emptyList();
      resolution = null;
      currentIdentity = null;
      reversePoint = null;
      forwardAnchor = null;
      stateListener = onStateChanged;
      humanListener = human ? onHumanChange : null;
    }
    runListener(stateListener);
    runListener(humanListener);
  }

  /** Starts asynchronous supplied-point labeling while retaining the exact host WGS84. */
  public void activate(Wgs84 point, boolean editable) {
    ReverseAddressRequest request = null;
    Runnable stateListener;
    synchronized (this) {
      if (disposed) return;
      generation++;
      cancelPendingLocked();
      this.editable = editable;
      long revision = draft.draftRevision() + 1L;
      AddressInputMode mode = draft.mode();
      candidates = Collections.emptyList();
      resolution = null;
      currentIdentity = null;
      reversePoint = point;
      forwardAnchor = null;
      if (point == null) {
        draft = AddressDraft.empty(revision, mode);
      } else {
        LookupIdentity identity =
            new LookupIdentity(
                UUID.randomUUID().toString(),
                generation,
                revision,
                lookupService.availability().datasetRevision());
        currentIdentity = identity;
        draft = AddressDraft.empty(revision, mode).withValidation(AddressValidation.LOOKUP_PENDING);
        request =
            new ReverseAddressRequest(
                identity,
                "native-address",
                LookupPriority.NATIVE_INTERACTIVE,
                point,
                REVERSE_RADIUS_METERS);
      }
      stateListener = onStateChanged;
    }
    runListener(stateListener);
    if (request != null) submitReverse(request);
  }

  public void autofill(Wgs84 point) {
    activate(point, isEditable());
  }

  public synchronized void setEditable(boolean editable) {
    if (disposed) return;
    this.editable = editable;
    if (!editable && resolution == null && draft.validation() != AddressValidation.LOOKUP_PENDING) {
      draft = draft.withValidation(AddressValidation.READ_ONLY);
    } else if (editable && draft.validation() == AddressValidation.READ_ONLY) {
      draft = parser.parse(draft.rawAddress(), draft.draftRevision(), draft.mode());
    }
  }

  public void dispose() {
    Runnable stateListener;
    synchronized (this) {
      if (disposed) return;
      disposed = true;
      generation++;
      cancelPendingLocked();
      draft = draft.withValidation(AddressValidation.DISPOSED);
      candidates = Collections.emptyList();
      resolution = null;
      currentIdentity = null;
      reversePoint = null;
      forwardAnchor = null;
      stateListener = onStateChanged;
      onStateChanged = null;
      onHumanChange = null;
    }
    runListener(stateListener);
    try {
      lookupService.removeAvailabilityListener(availabilityListener);
    } catch (RuntimeException ignored) {
      // Shared service teardown may already be in progress.
    }
    try {
      debouncer.close();
    } catch (Exception ignored) {
      // Teardown remains idempotent and failure-contained.
    }
  }

  private void dispatchForward() {
    ForwardAddressRequest request;
    Runnable stateListener;
    synchronized (this) {
      pendingDebounce = null;
      if (disposed || draft.validation() != AddressValidation.READY_TO_LOOKUP) return;
      LookupIdentity identity =
          new LookupIdentity(
              UUID.randomUUID().toString(),
              generation,
              draft.draftRevision(),
              lookupService.availability().datasetRevision());
      currentIdentity = identity;
      draft = draft.withValidation(AddressValidation.LOOKUP_PENDING);
      request =
          ForwardAddressRequest.create(
              identity,
              "native-address",
              LookupPriority.NATIVE_INTERACTIVE,
              draft.normalizedAddress(),
              forwardAnchor,
              currentOrdering(),
              candidateLimit);
      stateListener = onStateChanged;
    }
    runListener(stateListener);
    LookupHandle submitted = lookupService.forward(request, this::completeForward);
    synchronized (this) {
      if (disposed
          || currentIdentity == null
          || !currentIdentity.equals(request.identity())
          || draft.validation() != AddressValidation.LOOKUP_PENDING) {
        submitted.cancel();
      } else {
        lookupHandle = submitted;
      }
    }
  }

  private ResultOrdering currentOrdering() {
    ResultOrdering ordering = orderingSupplier.get();
    return ordering != null ? ordering : ResultOrdering.DISTANCE;
  }

  private Wgs84 currentForwardAnchor() {
    try {
      return forwardAnchorSupplier.get();
    } catch (RuntimeException | NoClassDefFoundError | NoSuchMethodError ignored) {
      return null;
    }
  }

  private void completeForward(ForwardAddressResult result) {
    Runnable stateListener;
    Runnable humanListener = null;
    synchronized (this) {
      if (disposed
          || result == null
          || currentIdentity == null
          || !currentIdentity.equals(result.identity())
          || result.identity().sessionGeneration() != generation
          || result.identity().draftRevision() != draft.draftRevision()
          || result.identity().datasetRevision() != lookupService.availability().datasetRevision())
        return;
      lookupHandle = null;
      candidates = Collections.emptyList();
      resolution = null;
      switch (result.status()) {
        case NO_DATASET:
          draft = draft.withValidation(AddressValidation.NO_DATASET);
          break;
        case NO_MATCH:
          draft = draft.withValidation(AddressValidation.NO_MATCH);
          break;
        case FAILURE:
          draft = draft.withValidation(AddressValidation.FAILURE);
          break;
        case CANDIDATES:
          candidates = Collections.unmodifiableList(new ArrayList<>(result.candidates()));
          AddressCandidate exact = uniqueExact(candidates);
          if (exact != null) {
            resolution =
                resolution(exact, AddressResolution.Source.UNIQUE_EXACT, result.identity());
            draft = draft.withValidation(AddressValidation.RESOLVED);
            humanListener = onHumanChange;
          } else {
            draft = draft.withValidation(AddressValidation.AMBIGUOUS);
          }
          break;
        default:
          draft = draft.withValidation(AddressValidation.FAILURE);
      }
      stateListener = onStateChanged;
    }
    runListener(stateListener);
    runListener(humanListener);
  }

  private void submitReverse(ReverseAddressRequest request) {
    LookupHandle submitted;
    try {
      submitted = lookupService.reverse(request, this::completeReverse);
    } catch (RuntimeException failure) {
      completeReverse(
          ReverseAddressResult.failure(request.identity(), request.queryPoint(), failure));
      return;
    }
    synchronized (this) {
      if (disposed
          || currentIdentity == null
          || !currentIdentity.equals(request.identity())
          || draft.validation() != AddressValidation.LOOKUP_PENDING) {
        submitted.cancel();
      } else {
        lookupHandle = submitted;
      }
    }
  }

  private void completeReverse(ReverseAddressResult result) {
    Runnable stateListener;
    synchronized (this) {
      if (disposed
          || result == null
          || currentIdentity == null
          || !currentIdentity.equals(result.identity())
          || result.identity().sessionGeneration() != generation
          || result.identity().draftRevision() != draft.draftRevision()
          || result.identity().datasetRevision() != lookupService.availability().datasetRevision())
        return;
      lookupHandle = null;
      candidates = Collections.emptyList();
      resolution = null;
      switch (result.status()) {
        case NO_DATASET:
          draft = draft.withValidation(AddressValidation.NO_DATASET);
          break;
        case NO_MATCH:
          draft = draft.withValidation(AddressValidation.NO_MATCH);
          break;
        case FAILURE:
          draft = draft.withValidation(AddressValidation.FAILURE);
          break;
        case FOUND:
          AddressCandidate candidate = result.candidate();
          AddressInputMode mode = draft.mode();
          draft =
              parser
                  .parse(candidate.displayAddress(), draft.draftRevision(), mode)
                  .withValidation(AddressValidation.RESOLVED);
          resolution =
              new AddressResolution(
                  candidate.displayAddress(),
                  candidate.normalizedAddress(),
                  result.queryPoint(),
                  candidate.recordPoint(),
                  AddressResolution.Source.REVERSE_LABEL,
                  Objects.requireNonNull(candidate.datasetIdentity(), "candidate dataset identity"),
                  result.identity());
          break;
        default:
          draft = draft.withValidation(AddressValidation.FAILURE);
      }
      stateListener = onStateChanged;
    }
    runListener(stateListener);
  }

  private void onAvailabilityChanged() {
    Wgs84 retryReverse = null;
    Runnable stateListener = null;
    synchronized (this) {
      if (disposed
          || currentIdentity == null
          || currentIdentity.datasetRevision() == lookupService.availability().datasetRevision())
        return;
      cancelPendingLocked();
      currentIdentity = null;
      candidates = Collections.emptyList();
      resolution = null;
      if (reversePoint != null) {
        retryReverse = reversePoint;
      } else {
        draft = parser.parse(draft.rawAddress(), draft.draftRevision() + 1L, draft.mode());
        if (draft.validation() == AddressValidation.READY_TO_LOOKUP) {
          pendingDebounce = debouncer.schedule(this::dispatchForward, DEBOUNCE_MS);
        }
        stateListener = onStateChanged;
      }
    }
    runListener(stateListener);
    if (retryReverse != null) activate(retryReverse, isEditable());
  }

  private synchronized void cancelPendingLocked() {
    if (pendingDebounce != null) {
      pendingDebounce.cancel();
      pendingDebounce = null;
    }
    if (lookupHandle != null) {
      lookupHandle.cancel();
      lookupHandle = null;
    }
    cancelLocalityLocked();
  }

  private void cancelLocalityLocked() {
    if (localityHandle != null) {
      localityHandle.cancel();
      localityHandle = null;
    }
    localityIdentity = null;
  }

  private static AddressCandidate uniqueExact(List<AddressCandidate> candidates) {
    AddressCandidate exact = null;
    for (AddressCandidate candidate : candidates) {
      if (candidate.matchKind() != AddressMatchKind.EXACT) continue;
      if (exact != null) return null;
      exact = candidate;
    }
    return exact;
  }

  private static AddressResolution resolution(
      AddressCandidate candidate, AddressResolution.Source source, LookupIdentity identity) {
    return new AddressResolution(
        candidate.displayAddress(),
        candidate.normalizedAddress(),
        candidate.recordPoint(),
        candidate.recordPoint(),
        source,
        Objects.requireNonNull(candidate.datasetIdentity(), "candidate dataset identity"),
        identity);
  }

  private static void runListener(Runnable listener) {
    if (listener == null) return;
    try {
      listener.run();
    } catch (RuntimeException ignored) {
      // One listener cannot corrupt session state or suppress another transition.
    }
  }

  private static String valueOrEmpty(String value) {
    return value != null ? value : "";
  }

  private static final class ScheduledDebouncer implements Debouncer {
    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "twcoord-address-debounce");
              thread.setDaemon(true);
              return thread;
            });

    @Override
    public Cancellable schedule(Runnable runnable, long delayMs) {
      java.util.concurrent.ScheduledFuture<?> future =
          executor.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
      return () -> future.cancel(false);
    }

    @Override
    public void close() {
      executor.shutdownNow();
    }
  }
}
