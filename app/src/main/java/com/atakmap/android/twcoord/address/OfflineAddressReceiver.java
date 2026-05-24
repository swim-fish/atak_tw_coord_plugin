package com.atakmap.android.twcoord.address;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.log.Log;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tools-menu DropDown for the Offline Address page. Two visual states:
 *
 * <ul>
 *   <li><b>State A</b> — no dataset installed → empty-state copy + Import button.
 *   <li><b>State B</b> — active dataset → metadata fields + Replace / Remove buttons.
 * </ul>
 *
 * <p>The Import button launches {@link OfflineAddressFilePickerActivity} (transparent SAF shim);
 * the shim broadcasts {@link OfflineAddressIntents#ACTION_PICK_FILE_RESULT} carrying the picked
 * URI; this receiver consumes the URI, submits the import job to the injected executor, and binds
 * Success / Failure into the page on the UI thread.
 *
 * <p>Every lifecycle / broadcast callback wraps in {@code try/catch (Throwable)} per Constitution
 * VI — the plugin lives inside the ATAK process; an escaping exception kills the host.
 */
public final class OfflineAddressReceiver extends DropDownReceiver implements OnStateListener {

  private static final String TAG = "OfflineAddressReceiver";

  private final Context pluginContext;
  private final AddressBundleImporter importer;
  private final ExecutorService importExecutor;
  private final Handler ui;
  private final View view;

  // Lifecycle: registered in onDropDownVisible(true), unregistered in onDropDownClose. Hold a
  // reference so we can unregister even if the receiver instance outlives the visible state.
  private BroadcastReceiver pickResultReceiver;

  // Re-entrancy guard for the buttons (Constitution VI §AtomicBoolean rule).
  private final AtomicBoolean importInFlight = new AtomicBoolean(false);

  // ---- inflated view refs ----
  private final TextView progressView;
  private final TextView errorView;
  private final View stateAGroup;
  private final Button stateAImportBtn;
  private final View stateBGroup;
  private final TextView valueCounty;
  private final TextView valueDataDate;
  private final TextView valueSource;
  private final TextView valueRows;
  private final TextView valueCsvSha;
  private final TextView valueImportedAt;
  private final TextView valueFileSha;
  private final TextView valueRtreeBuilt;
  private final Button stateBReplaceBtn;
  private final Button stateBRemoveBtn;

  public OfflineAddressReceiver(
      MapView mapView,
      Context pluginContext,
      AddressBundleImporter importer,
      ExecutorService importExecutor) {
    super(mapView);
    this.pluginContext = pluginContext;
    this.importer = importer;
    this.importExecutor = importExecutor;
    this.ui = new Handler(Looper.getMainLooper());
    LayoutInflater inflater = LayoutInflater.from(pluginContext);
    this.view = inflater.inflate(R.layout.offline_address_page, null);

    this.progressView = view.findViewById(R.id.offline_address_progress);
    this.errorView = view.findViewById(R.id.offline_address_error);
    this.stateAGroup = view.findViewById(R.id.offline_address_state_a);
    this.stateAImportBtn = view.findViewById(R.id.offline_address_state_a_import);
    this.stateBGroup = view.findViewById(R.id.offline_address_state_b);
    this.valueCounty = view.findViewById(R.id.offline_address_value_county);
    this.valueDataDate = view.findViewById(R.id.offline_address_value_data_date);
    this.valueSource = view.findViewById(R.id.offline_address_value_source);
    this.valueRows = view.findViewById(R.id.offline_address_value_rows);
    this.valueCsvSha = view.findViewById(R.id.offline_address_value_csv_sha);
    this.valueImportedAt = view.findViewById(R.id.offline_address_value_imported_at);
    this.valueFileSha = view.findViewById(R.id.offline_address_value_file_sha);
    this.valueRtreeBuilt = view.findViewById(R.id.offline_address_value_rtree_built);
    this.stateBReplaceBtn = view.findViewById(R.id.offline_address_state_b_replace);
    this.stateBRemoveBtn = view.findViewById(R.id.offline_address_state_b_remove);

    if (stateAImportBtn != null) {
      stateAImportBtn.setOnClickListener(v -> safeRun(this::launchPicker));
    }
    if (stateBReplaceBtn != null) {
      stateBReplaceBtn.setOnClickListener(v -> safeRun(this::confirmReplace));
    }
    if (stateBRemoveBtn != null) {
      stateBRemoveBtn.setOnClickListener(v -> safeRun(this::confirmRemove));
    }
  }

  // ----------------------------------------------------------------------
  // DropDownReceiver lifecycle
  // ----------------------------------------------------------------------

  @Override
  public void disposeImpl() {
    unregisterPickReceiver();
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    try {
      if (intent == null) return;
      if (!OfflineAddressIntents.ACTION_SHOW_OFFLINE_ADDRESS.equals(intent.getAction())) return;
      if (isVisible()) return; // idempotent
      bindFromActiveDataset();
      showDropDown(view, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, this);
    } catch (Throwable t) {
      Log.w(TAG, "onReceive threw", t);
    }
  }

  @Override
  public void onDropDownVisible(boolean visible) {
    try {
      if (visible) {
        registerPickReceiver();
      } else {
        unregisterPickReceiver();
      }
    } catch (Throwable t) {
      Log.w(TAG, "onDropDownVisible threw", t);
    }
  }

  @Override
  public void onDropDownSelectionRemoved() {
    /* no-op */
  }

  @Override
  public void onDropDownClose() {
    try {
      unregisterPickReceiver();
      clearError();
      hideProgress();
    } catch (Throwable t) {
      Log.w(TAG, "onDropDownClose threw", t);
    }
  }

  @Override
  public void onDropDownSizeChanged(double width, double height) {
    /* no-op */
  }

  // ----------------------------------------------------------------------
  // Binding (State A vs State B)
  // ----------------------------------------------------------------------

  private void bindFromActiveDataset() {
    AddressDataset active = importer.activeOrNull();
    if (active == null) {
      bindStateA();
    } else {
      bindStateB(active);
    }
  }

  private void bindStateA() {
    if (stateAGroup != null) stateAGroup.setVisibility(View.VISIBLE);
    if (stateBGroup != null) stateBGroup.setVisibility(View.GONE);
  }

  private void bindStateB(AddressDataset active) {
    if (stateAGroup != null) stateAGroup.setVisibility(View.GONE);
    if (stateBGroup != null) stateBGroup.setVisibility(View.VISIBLE);
    GeneratorMetadata gm = active.generator();
    ImportedManifest im = active.imported();
    if (valueCounty != null) valueCounty.setText(nonNull(gm.county()));
    if (valueDataDate != null) valueDataDate.setText(nonNull(gm.dataDate()));
    if (valueSource != null) valueSource.setText(nonNull(gm.source()));
    if (valueRows != null) {
      valueRows.setText(gm.insertedRows() >= 0 ? Long.toString(gm.insertedRows()) : "—");
    }
    if (valueCsvSha != null) valueCsvSha.setText(nonNull(gm.csvSha256()));
    if (valueImportedAt != null) valueImportedAt.setText(im.importedAt().toString());
    if (valueFileSha != null) valueFileSha.setText(im.fileSha256());
    if (valueRtreeBuilt != null) valueRtreeBuilt.setText(Boolean.toString(im.rtreeBuilt()));
  }

  // ----------------------------------------------------------------------
  // SAF picker + import worker
  // ----------------------------------------------------------------------

  private void registerPickReceiver() {
    if (pickResultReceiver != null) return;
    pickResultReceiver =
        new BroadcastReceiver() {
          @Override
          public void onReceive(Context ctx, Intent intent) {
            try {
              if (intent == null) return;
              if (!OfflineAddressIntents.ACTION_PICK_FILE_RESULT.equals(intent.getAction())) return;
              String uriStr = intent.getStringExtra(OfflineAddressIntents.EXTRA_PICKED_URI);
              if (uriStr == null || uriStr.isEmpty()) return;
              startImport(Uri.parse(uriStr));
            } catch (Throwable t) {
              Log.w(TAG, "pickResultReceiver.onReceive threw", t);
            }
          }
        };
    AtakBroadcast.DocumentedIntentFilter f = new AtakBroadcast.DocumentedIntentFilter();
    f.addAction(OfflineAddressIntents.ACTION_PICK_FILE_RESULT);
    AtakBroadcast.getInstance().registerReceiver(pickResultReceiver, f);
  }

  private void unregisterPickReceiver() {
    if (pickResultReceiver == null) return;
    try {
      AtakBroadcast.getInstance().unregisterReceiver(pickResultReceiver);
    } catch (IllegalArgumentException ignored) {
      // Receiver was never registered or already unregistered.
    } finally {
      pickResultReceiver = null;
    }
  }

  private void launchPicker() {
    if (!importInFlight.get()) {
      Intent i = new Intent(pluginContext, OfflineAddressFilePickerActivity.class);
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      pluginContext.startActivity(i);
    }
  }

  private void startImport(Uri picked) {
    if (!importInFlight.compareAndSet(false, true)) {
      Log.d(TAG, "import already in flight; ignoring second SAF result");
      return;
    }
    clearError();
    showProgress(pluginContext.getString(R.string.offline_address_progress_copying, 0));
    importExecutor.execute(
        () -> {
          AddressBundleImporter.ImportResult result;
          try (InputStream stream = openUri(picked)) {
            if (stream == null) {
              result =
                  AddressBundleImporter.ImportResult.failure(
                      AddressBundleImporter.ImportResult.Reason.IO_ERROR, "cannot open URI");
            } else {
              result = importer.importFrom(stream, this::postProgress);
            }
          } catch (Throwable t) {
            Log.w(TAG, "import worker threw", t);
            result =
                AddressBundleImporter.ImportResult.failure(
                    AddressBundleImporter.ImportResult.Reason.IO_ERROR,
                    t.getMessage() == null ? "unknown" : t.getMessage());
          }
          final AddressBundleImporter.ImportResult finalResult = result;
          ui.post(
              () -> {
                try {
                  importInFlight.set(false);
                  hideProgress();
                  if (finalResult.isSuccess()) {
                    bindFromActiveDataset();
                    AtakBroadcast.getInstance()
                        .sendBroadcast(new Intent(OfflineAddressIntents.ACTION_DATASET_CHANGED));
                  } else {
                    AddressBundleImporter.ImportResult.Failure fail =
                        (AddressBundleImporter.ImportResult.Failure) finalResult;
                    showError(formatFailure(fail));
                    // Keep prior dataset bound; the importer leaves it untouched on failure.
                    bindFromActiveDataset();
                  }
                } catch (Throwable t) {
                  Log.w(TAG, "post-import UI bind threw", t);
                }
              });
        });
  }

  private InputStream openUri(Uri uri) {
    try {
      ContentResolver cr = pluginContext.getContentResolver();
      return cr.openInputStream(uri);
    } catch (Throwable t) {
      Log.w(TAG, "openInputStream(" + uri + ") threw", t);
      return null;
    }
  }

  private void postProgress(
      AddressBundleImporter.ProgressListener.Stage stage, long completed, long total) {
    ui.post(
        () -> {
          try {
            String text = renderProgress(stage, completed, total);
            showProgress(text);
          } catch (Throwable t) {
            Log.w(TAG, "postProgress threw", t);
          }
        });
  }

  private String renderProgress(
      AddressBundleImporter.ProgressListener.Stage stage, long completed, long total) {
    int pct = total > 0 ? (int) Math.min(100, (completed * 100L) / total) : 0;
    switch (stage) {
      case COPYING:
        return pluginContext.getString(R.string.offline_address_progress_copying, pct);
      case VERIFYING_METADATA:
        return pluginContext.getString(R.string.offline_address_progress_verifying);
      case BUILDING_RTREE:
        return pluginContext.getString(R.string.offline_address_progress_building_index, pct);
      case ACTIVATING:
        return pluginContext.getString(R.string.offline_address_progress_activating);
      default:
        return "";
    }
  }

  private String formatFailure(AddressBundleImporter.ImportResult.Failure fail) {
    int resId;
    Object[] args = new Object[0];
    switch (fail.reason()) {
      case NOT_OPENABLE:
        resId = R.string.offline_address_error_not_openable;
        break;
      case MISSING_METADATA_TABLE:
        resId = R.string.offline_address_error_missing_metadata;
        break;
      case MISSING_REQUIRED_METADATA_KEY:
        resId = R.string.offline_address_error_missing_required_key;
        args = new Object[] {fail.details()};
        break;
      case UNSUPPORTED_SCHEMA_VERSION:
        resId = R.string.offline_address_error_unsupported_schema;
        args = new Object[] {fail.details()};
        break;
      case MISSING_PLACES_TABLE:
        resId = R.string.offline_address_error_missing_places;
        break;
      case UNEXPECTED_PLACES_COLUMNS:
        resId = R.string.offline_address_error_unexpected_columns;
        args = new Object[] {fail.details()};
        break;
      case RTREE_BUILD_FAILED:
        resId = R.string.offline_address_error_rtree_failed;
        break;
      case DISK_FULL:
        resId = R.string.offline_address_error_disk_full;
        break;
      case ACTIVATION_RENAME_FAILED:
        resId = R.string.offline_address_error_activation_failed;
        break;
      case CANCELLED:
      case IO_ERROR:
      default:
        resId = R.string.offline_address_error_io;
        args = new Object[] {fail.details()};
        break;
    }
    return pluginContext.getString(resId, args);
  }

  // ----------------------------------------------------------------------
  // Replace + Remove flows
  // ----------------------------------------------------------------------

  private void confirmReplace() {
    AddressDataset active = importer.activeOrNull();
    if (active == null) {
      // Edge case: state-B button pressed but active disappeared between bind and tap.
      bindStateA();
      return;
    }
    String msg =
        pluginContext.getString(
            R.string.offline_address_confirm_replace, nonNull(active.generator().county()));
    new AlertDialog.Builder(pluginContext)
        .setTitle(R.string.offline_address_button_replace)
        .setMessage(msg)
        .setPositiveButton(
            android.R.string.ok,
            (d, w) -> safeRun(this::launchPicker)) // launchPicker, then import path replaces
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void confirmRemove() {
    AddressDataset active = importer.activeOrNull();
    if (active == null) {
      bindStateA();
      return;
    }
    String msg =
        pluginContext.getString(
            R.string.offline_address_confirm_remove, nonNull(active.generator().county()));
    new AlertDialog.Builder(pluginContext)
        .setTitle(R.string.offline_address_button_remove)
        .setMessage(msg)
        .setPositiveButton(android.R.string.ok, (d, w) -> safeRun(this::performRemove))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void performRemove() {
    importExecutor.execute(
        () -> {
          try {
            importer.removeActive();
          } catch (Throwable t) {
            Log.w(TAG, "removeActive threw", t);
          }
          ui.post(
              () -> {
                try {
                  bindFromActiveDataset();
                  AtakBroadcast.getInstance()
                      .sendBroadcast(new Intent(OfflineAddressIntents.ACTION_DATASET_CHANGED));
                } catch (Throwable t) {
                  Log.w(TAG, "post-remove UI bind threw", t);
                }
              });
        });
  }

  // ----------------------------------------------------------------------
  // UI helpers
  // ----------------------------------------------------------------------

  private void showProgress(String text) {
    if (progressView == null) return;
    progressView.setText(text == null ? "" : text);
    progressView.setVisibility(View.VISIBLE);
  }

  private void hideProgress() {
    if (progressView == null) return;
    progressView.setVisibility(View.GONE);
  }

  private void showError(String text) {
    if (errorView == null) return;
    errorView.setText(text == null ? "" : text);
    errorView.setVisibility(View.VISIBLE);
  }

  private void clearError() {
    if (errorView == null) return;
    errorView.setVisibility(View.GONE);
    errorView.setText("");
  }

  private static String nonNull(String s) {
    return s == null ? "—" : s;
  }

  /** Runs the given block, catching any Throwable per Constitution VI. */
  private static void safeRun(Runnable r) {
    try {
      r.run();
    } catch (Throwable t) {
      Log.w(TAG, "safeRun threw", t);
    }
  }
}
