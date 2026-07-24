package com.atakmap.android.twcoord.nativeentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.AlertDialog;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorOrdering;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorResult;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorSnapshot;
import com.atakmap.android.twcoord.address.lookup.LookupIdentity;
import com.atakmap.android.twcoord.address.lookup.PostalLocalityCatalog;
import com.atakmap.android.twcoord.plugin.R;
import java.util.Arrays;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, packageName = "com.atakmap.android.twcoord.plugin")
public final class AddressLocalityDialogTest {

  @Test
  public void usesActivityWindowAndMarksPromotedMapChoice() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    AddressEntryController controller = mock(AddressEntryController.class);
    when(controller.isEditable()).thenReturn(true);
    LocalitySelectorSnapshot snapshot =
        LocalitySelectorOrdering.counties(
            PostalLocalityCatalog.testing(
                PostalLocalityCatalog.county("新北市", 1), PostalLocalityCatalog.county("臺中市", 2)),
            7L,
            2L,
            Arrays.asList("新北市", "臺中市"),
            "臺中市",
            null);
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<LocalitySelectorResult> callback = invocation.getArgument(1);
              callback.accept(
                  LocalitySelectorResult.ready(
                      new LookupIdentity("selector", 2L, 3L, 7L), snapshot));
              return null;
            })
        .when(controller)
        .prepareLocalities(eq(LocalitySelectorSnapshot.Kind.COUNTY), any());
    AddressLocalityDialog chooser =
        new AddressLocalityDialog(activity, RuntimeEnvironment.getApplication(), controller);

    chooser.show(LocalitySelectorSnapshot.Kind.COUNTY);

    AlertDialog dialog = chooser.dialogForTest();
    assertThat(dialog).isNotNull();
    assertThat(dialog.getContext()).isNotSameAs(RuntimeEnvironment.getApplication());
    assertThat(dialog.getListView().getAdapter().getItem(0).toString())
        .contains("Map centre", "臺中市");
    assertThat(org.robolectric.Shadows.shadowOf(dialog).getTitle().toString())
        .isEqualTo(
            RuntimeEnvironment.getApplication()
                .getString(R.string.native_entry_address_county_selector_title));
  }

  @Test
  public void selectionCarriesSnapshotDatasetRevision() {
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    AddressEntryController controller = mock(AddressEntryController.class);
    when(controller.isEditable()).thenReturn(true);
    LocalitySelectorSnapshot snapshot =
        LocalitySelectorOrdering.counties(
            PostalLocalityCatalog.testing(PostalLocalityCatalog.county("臺中市", 1)),
            9L,
            2L,
            java.util.Collections.singletonList("臺中市"),
            null,
            null);
    LookupIdentity identity = new LookupIdentity("selector", 2L, 3L, 9L);
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<LocalitySelectorResult> callback = invocation.getArgument(1);
              callback.accept(LocalitySelectorResult.ready(identity, snapshot));
              return null;
            })
        .when(controller)
        .prepareLocalities(eq(LocalitySelectorSnapshot.Kind.COUNTY), any());
    AddressLocalityDialog chooser =
        new AddressLocalityDialog(activity, RuntimeEnvironment.getApplication(), controller);

    chooser.show(LocalitySelectorSnapshot.Kind.COUNTY);
    chooser.dialogForTest().getListView().performItemClick(null, 0, 0L);

    verify(controller).selectLocality(LocalitySelectorSnapshot.Kind.COUNTY, "臺中市", identity, true);
  }
}
