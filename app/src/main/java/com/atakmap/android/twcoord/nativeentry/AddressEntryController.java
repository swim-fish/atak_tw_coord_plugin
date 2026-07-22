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
import com.atakmap.android.twcoord.address.lookup.LookupHandle;
import com.atakmap.android.twcoord.address.lookup.LookupIdentity;
import com.atakmap.android.twcoord.address.lookup.LookupPriority;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Host-independent full-address entry session with revision-fenced asynchronous lookup. */
public final class AddressEntryController {
  static final long DEBOUNCE_MS = 250L;

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

  private AddressDraft draft = AddressDraft.empty(0L, AddressInputMode.FULL);
  private List<AddressCandidate> candidates = Collections.emptyList();
  private AddressResolution resolution;
  private LookupHandle lookupHandle;
  private LookupIdentity currentIdentity;
  private Cancellable pendingDebounce;
  private Runnable onStateChanged;
  private Runnable onHumanChange;
  private long generation = 1L;
  private boolean editable = true;
  private boolean disposed;

  public AddressEntryController(AddressLookupService lookupService) {
    this(lookupService, new TaiwanAddressParser(), new ScheduledDebouncer(), 20);
  }

  AddressEntryController(
      AddressLookupService lookupService,
      TaiwanAddressParser parser,
      Debouncer debouncer,
      int candidateLimit) {
    if (candidateLimit <= 0) throw new IllegalArgumentException("candidateLimit must be positive");
    this.lookupService = Objects.requireNonNull(lookupService, "lookupService");
    this.parser = Objects.requireNonNull(parser, "parser");
    this.debouncer = Objects.requireNonNull(debouncer, "debouncer");
    this.candidateLimit = candidateLimit;
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
      cancelPendingLocked();
      long revision = draft.draftRevision() + 1L;
      draft = parser.parse(value, revision, AddressInputMode.FULL);
      candidates = Collections.emptyList();
      resolution = null;
      currentIdentity = null;
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
      cancelPendingLocked();
      long revision = draft.draftRevision() + 1L;
      draft = parser.parseStructured(countyCity, districtTownship, roadLocality, tail, revision);
      candidates = Collections.emptyList();
      resolution = null;
      currentIdentity = null;
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
      draft = draft.withValidation(AddressValidation.RESOLVED);
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
      stateListener = onStateChanged;
      humanListener = human ? onHumanChange : null;
    }
    runListener(stateListener);
    runListener(humanListener);
  }

  public synchronized void setEditable(boolean editable) {
    if (disposed) return;
    this.editable = editable;
    if (!editable && resolution == null) draft = draft.withValidation(AddressValidation.READ_ONLY);
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
      stateListener = onStateChanged;
      onStateChanged = null;
      onHumanChange = null;
    }
    runListener(stateListener);
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

  private void completeForward(ForwardAddressResult result) {
    Runnable stateListener;
    Runnable humanListener = null;
    synchronized (this) {
      if (disposed
          || result == null
          || currentIdentity == null
          || !currentIdentity.equals(result.identity())
          || result.identity().sessionGeneration() != generation
          || result.identity().draftRevision() != draft.draftRevision()) return;
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

  private synchronized void cancelPendingLocked() {
    if (pendingDebounce != null) {
      pendingDebounce.cancel();
      pendingDebounce = null;
    }
    if (lookupHandle != null) {
      lookupHandle.cancel();
      lookupHandle = null;
    }
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
