package com.atakmap.android.twcoord.nativeentry;

import android.app.AlertDialog;
import android.content.Context;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorResult;
import com.atakmap.android.twcoord.address.lookup.LocalitySelectorSnapshot;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.log.Log;
import java.util.List;
import java.util.Objects;

/** Plugin-resource/window-context-safe chooser for one immutable locality snapshot. */
public final class AddressLocalityDialog {
  private static final String TAG = "AddressLocalityDialog";

  private final Context windowContext;
  private final Context pluginContext;
  private final AddressEntryController controller;
  private AlertDialog dialog;
  private boolean disposed;

  public AddressLocalityDialog(
      Context windowContext, Context pluginContext, AddressEntryController controller) {
    this.windowContext = Objects.requireNonNull(windowContext, "windowContext");
    this.pluginContext = Objects.requireNonNull(pluginContext, "pluginContext");
    this.controller = Objects.requireNonNull(controller, "controller");
  }

  public void show(LocalitySelectorSnapshot.Kind kind) {
    if (disposed || !controller.isEditable()) return;
    dismiss();
    controller.prepareLocalities(kind, result -> showPrepared(kind, result));
  }

  private void showPrepared(
      LocalitySelectorSnapshot.Kind expectedKind, LocalitySelectorResult result) {
    if (disposed || result == null) return;
    if (result.status() != LocalitySelectorResult.Status.READY || result.snapshot() == null) {
      showMessage(expectedKind, R.string.native_entry_address_locality_unavailable);
      return;
    }
    if (result.snapshot().kind() != expectedKind) return;
    if (result.snapshot().choices().isEmpty()) {
      showMessage(expectedKind, R.string.native_entry_address_locality_empty);
      return;
    }
    LocalitySelectorSnapshot snapshot = result.snapshot();
    List<LocalitySelectorSnapshot.Choice> choices = snapshot.choices();
    CharSequence[] rows = new CharSequence[choices.size()];
    for (int index = 0; index < choices.size(); index++) {
      LocalitySelectorSnapshot.Choice choice = choices.get(index);
      rows[index] =
          choice.promoted()
              ? pluginContext.getString(
                  R.string.native_entry_address_locality_map_choice, choice.name())
              : choice.name();
    }
    int title =
        expectedKind == LocalitySelectorSnapshot.Kind.COUNTY
            ? R.string.native_entry_address_county_selector_title
            : R.string.native_entry_address_district_selector_title;
    try {
      dialog =
          new AlertDialog.Builder(windowContext)
              .setTitle(pluginContext.getString(title))
              .setItems(
                  rows,
                  (ignored, which) -> {
                    if (disposed || which < 0 || which >= choices.size()) return;
                    controller.selectLocality(
                        expectedKind, choices.get(which).name(), result.identity(), true);
                  })
              .setNegativeButton(
                  pluginContext.getString(R.string.native_entry_address_locality_clear),
                  (ignored, which) ->
                      controller.selectLocality(expectedKind, "", result.identity(), true))
              .create();
      dialog.show();
    } catch (RuntimeException failure) {
      Log.w(TAG, "locality selector dialog show failed", failure);
      dialog = null;
    }
  }

  private void showMessage(LocalitySelectorSnapshot.Kind kind, int messageId) {
    int title =
        kind == LocalitySelectorSnapshot.Kind.COUNTY
            ? R.string.native_entry_address_county_selector_title
            : R.string.native_entry_address_district_selector_title;
    try {
      dialog =
          new AlertDialog.Builder(windowContext)
              .setTitle(pluginContext.getString(title))
              .setMessage(pluginContext.getString(messageId))
              .setPositiveButton(android.R.string.ok, null)
              .create();
      dialog.show();
    } catch (RuntimeException failure) {
      Log.w(TAG, "locality selector message show failed", failure);
      dialog = null;
    }
  }

  public void dismiss() {
    AlertDialog current = dialog;
    dialog = null;
    if (current == null) return;
    try {
      current.dismiss();
    } catch (RuntimeException failure) {
      Log.w(TAG, "locality selector dialog dismiss failed", failure);
    }
  }

  public void dispose() {
    if (disposed) return;
    disposed = true;
    dismiss();
  }

  AlertDialog dialogForTest() {
    return dialog;
  }
}
