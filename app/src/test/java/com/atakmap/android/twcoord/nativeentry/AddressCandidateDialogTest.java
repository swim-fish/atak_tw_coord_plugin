package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.AlertDialog;
import com.atakmap.android.twcoord.address.lookup.AddressAvailability;
import com.atakmap.android.twcoord.address.lookup.AddressCandidate;
import com.atakmap.android.twcoord.address.lookup.AddressDraft;
import com.atakmap.android.twcoord.address.lookup.AddressInputMode;
import com.atakmap.android.twcoord.address.lookup.AddressLookupService;
import com.atakmap.android.twcoord.address.lookup.AddressMatchKind;
import com.atakmap.android.twcoord.address.lookup.AddressResolution;
import com.atakmap.android.twcoord.address.lookup.AddressValidation;
import com.atakmap.android.twcoord.address.lookup.DatasetIdentity;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ForwardAddressResult;
import com.atakmap.android.twcoord.address.lookup.LookupHandle;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressRequest;
import com.atakmap.android.twcoord.address.lookup.ReverseAddressResult;
import com.atakmap.android.twcoord.address.lookup.TaiwanAddressParser;
import com.atakmap.android.twcoord.coord.Wgs84;
import com.atakmap.android.twcoord.plugin.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class AddressCandidateDialogTest {

  @Test
  public void usesActivityWindowAndPluginResolvedBoundedDistinguishingRows() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    android.content.Context pluginContext = RuntimeEnvironment.getApplication();
    AddressEntryController controller = mock(AddressEntryController.class);
    when(controller.draft()).thenReturn(AddressDraft.empty(7L, AddressInputMode.FULL));
    when(controller.candidates()).thenReturn(candidates(25));
    AddressCandidateDialog chooser =
        new AddressCandidateDialog(activity, pluginContext, controller);

    chooser.show();

    AlertDialog dialog = chooser.dialogForTest();
    assertThat(dialog).isNotNull();
    assertThat(dialog.getContext()).isNotSameAs(pluginContext);
    assertThat(dialog.getListView().getAdapter().getCount()).isEqualTo(20);
    assertThat(dialog.getListView().getAdapter().getItem(0).toString()).contains("臺北市", "測試路0號");
    assertThat(org.robolectric.Shadows.shadowOf(dialog).getTitle().toString())
        .isEqualTo(pluginContext.getString(R.string.native_entry_address_candidates_title));
  }

  @Test
  public void rejectsSelectionAfterDraftRevisionChanges() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    AddressEntryController controller = mock(AddressEntryController.class);
    when(controller.candidates()).thenReturn(candidates(1));
    when(controller.draft())
        .thenReturn(
            AddressDraft.empty(7L, AddressInputMode.FULL),
            AddressDraft.empty(8L, AddressInputMode.FULL));
    AddressCandidateDialog chooser =
        new AddressCandidateDialog(activity, RuntimeEnvironment.getApplication(), controller);

    chooser.show();
    chooser.dialogForTest().getListView().performItemClick(null, 0, 0L);

    verify(controller, never()).selectCandidate("candidate-0", true);
  }

  @Test
  public void submitsSelectionWhenDraftRevisionIsCurrent() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    AddressEntryController controller = mock(AddressEntryController.class);
    when(controller.candidates()).thenReturn(candidates(1));
    when(controller.draft()).thenReturn(AddressDraft.empty(7L, AddressInputMode.FULL));
    AddressCandidateDialog chooser =
        new AddressCandidateDialog(activity, RuntimeEnvironment.getApplication(), controller);

    chooser.show();
    chooser.dialogForTest().getListView().performItemClick(null, 0, 0L);

    verify(controller).selectCandidate("candidate-0", true);
  }

  @Test
  public void identicalImeEventAfterOpenDoesNotBlockCandidateSelection() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    CandidateService service = new CandidateService();
    ImmediateDebouncer debouncer = new ImmediateDebouncer();
    AddressEntryController controller =
        new AddressEntryController(service, new TaiwanAddressParser(), debouncer, 20);
    String address = "臺北市信義區市府路1號";
    controller.editFull(address, true);
    service.complete(candidates(2));
    AddressCandidateDialog chooser =
        new AddressCandidateDialog(activity, RuntimeEnvironment.getApplication(), controller);

    chooser.show();
    controller.editFull(address, true);
    chooser.dialogForTest().getListView().performItemClick(null, 0, 0L);

    assertThat(controller.validation()).isEqualTo(AddressValidation.RESOLVED);
    assertThat(controller.resolution().source())
        .isEqualTo(AddressResolution.Source.OPERATOR_SELECTED);
    assertThat(controller.draft().rawAddress()).isEqualTo("測試路0號");
  }

  private static List<AddressCandidate> candidates(int count) {
    List<AddressCandidate> candidates = new ArrayList<>();
    DatasetIdentity dataset = new DatasetIdentity("臺北市", "2026-07-22", 1, "sha", "fixture");
    for (int index = 0; index < count; index++) {
      candidates.add(
          new AddressCandidate(
              "candidate-" + index,
              "測試路" + index + "號",
              "臺北市測試路" + index + "號",
              new Wgs84(25.0 + index / 10000.0, 121.5, index + 1L, Wgs84.Source.COT_TARGET),
              AddressMatchKind.PARTIAL,
              index,
              "臺北市",
              dataset));
    }
    return candidates;
  }

  private static final class ImmediateDebouncer implements AddressEntryController.Debouncer {
    @Override
    public AddressEntryController.Cancellable schedule(Runnable runnable, long delayMs) {
      runnable.run();
      return () -> {};
    }
  }

  private static final class CandidateService implements AddressLookupService {
    private ForwardAddressRequest request;
    private Consumer<ForwardAddressResult> callback;

    @Override
    public LookupHandle forward(
        ForwardAddressRequest request, Consumer<ForwardAddressResult> callback) {
      this.request = request;
      this.callback = callback;
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

    void complete(List<AddressCandidate> candidates) {
      callback.accept(ForwardAddressResult.candidates(request.identity(), candidates));
    }

    @Override
    public LookupHandle reverse(
        ReverseAddressRequest request, Consumer<ReverseAddressResult> callback) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AddressAvailability availability() {
      return new AddressAvailability(Collections.singleton("臺北市"), false, 1L, false);
    }

    @Override
    public void addAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void removeAvailabilityListener(AvailabilityListener listener) {}

    @Override
    public void close() {}
  }
}
