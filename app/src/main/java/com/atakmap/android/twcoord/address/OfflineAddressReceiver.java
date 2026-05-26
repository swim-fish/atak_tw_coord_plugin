package com.atakmap.android.twcoord.address;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.twcoord.plugin.R;
import com.atakmap.coremap.log.Log;
import java.io.File;
import java.io.FileInputStream;
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
 * <p>The Import button opens ATAK SDK's {@link ImportFileBrowserDialog}; the dialog runs in ATAK's
 * process so there is no cross-UID handoff. When the user picks a file, the dialog invokes {@link
 * ImportFileBrowserDialog.DialogDismissed#onFileSelected(File)} synchronously on the UI thread;
 * this receiver submits the import job to the injected executor and binds Success / Failure into
 * the page on the UI thread.
 *
 * <p>Every lifecycle / callback wraps in {@code try/catch (Throwable)} per Constitution VI — the
 * plugin lives inside the ATAK process; an escaping exception kills the host.
 *
 * <p><b>History</b>: An earlier design (commits 2ca5643 → d80d8bc) used a plugin-owned SAF picker
 * Activity that broadcast the picked {@code content://} URI back to ATAK. That handoff never worked
 * reliably across UIDs (plugin Activity UID 10544 vs ATAK uid 10515) on Android 14 + Samsung One
 * UI: ActivityManager dropped the broadcast even with explicit {@code setPackage} + {@code
 * <queries>} + {@code FLAG_GRANT_READ_URI_PERMISSION}. Switched to the ATAK-blessed in-process
 * dialog after finding the pattern in helloworld sample {@code
 * HelloWorldDropDownReceiver.sampleFileBrowser}.
 */
public final class OfflineAddressReceiver extends DropDownReceiver implements OnStateListener {

  private static final String TAG = "OfflineAddressReceiver";

  private final Context pluginContext;
  private final AddressBundleImporter importer;
  private final ExecutorService importExecutor;
  private final Handler ui;
  private final View view;

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
    /* no-op — no resources to release */
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
    /* no-op */
  }

  @Override
  public void onDropDownSelectionRemoved() {
    /* no-op */
  }

  @Override
  public void onDropDownClose() {
    try {
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
  // File picker (ATAK SDK in-process dialog) + import worker
  // ----------------------------------------------------------------------

  private void launchPicker() {
    if (importInFlight.get()) {
      Log.d(TAG, "import already in flight; ignoring Import button");
      return;
    }
    // Run on ATAK's host context — ImportFileBrowserDialog needs a UI-thread Activity context;
    // the plugin context lacks the Activity token. Using getMapView().getContext() matches the
    // helloworld sample (HelloWorldDropDownReceiver.sampleFileBrowser, line 3657).
    Context atakCtx = getMapView().getContext();
    ImportFileBrowserDialog dialog = new ImportFileBrowserDialog(atakCtx);
    dialog.setExtensionTypes("sqlite", "db");
    dialog.setTitle(pluginContext.getString(R.string.offline_address_button_import));
    dialog.setOnDismissListener(
        new ImportFileBrowserDialog.DialogDismissed() {
          @Override
          public void onFileSelected(File file) {
            try {
              if (file == null) {
                Log.w(TAG, "picker returned null file");
                return;
              }
              Log.i(TAG, "picker returned file=" + file.getAbsolutePath());
              startImport(file);
            } catch (Throwable t) {
              Log.w(TAG, "onFileSelected threw", t);
            }
          }

          @Override
          public void onDialogClosed() {
            /* user cancelled — no action */
          }
        });
    dialog.show();
  }

  /**
   * Run import on a picked {@link File}. Opens the {@link FileInputStream} on the calling thread,
   * then hands the already-open stream to the worker (mirrors the worker contract the SAF-broadcast
   * era used; the worker closes the stream via try-with-resources).
   */
  private void startImport(File file) {
    if (!importInFlight.compareAndSet(false, true)) {
      Log.d(TAG, "import already in flight; ignoring second pick");
      return;
    }
    clearError();
    showProgress(pluginContext.getString(R.string.offline_address_progress_copying, 0));
    final InputStream stream;
    try {
      stream = new FileInputStream(file);
    } catch (Throwable t) {
      Log.w(TAG, "FileInputStream(" + file + ") threw", t);
      importInFlight.set(false);
      hideProgress();
      showError(
          formatFailure(
              AddressBundleImporter.ImportResult.failure(
                  AddressBundleImporter.ImportResult.Reason.IO_ERROR,
                  t.getMessage() == null ? "open failed" : t.getMessage())));
      return;
    }
    Log.i(TAG, "opened FileInputStream; scheduling import worker");
    importExecutor.execute(
        () -> {
          AddressBundleImporter.ImportResult result;
          try (InputStream s = stream) {
            result = importer.importFrom(s, this::postProgress);
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
    // `details` is always supplied as arg 0; resource strings that don't reference %1$s simply
    // ignore the extra arg (Android Resources.getString → String.format tolerates trailing args).
    // Initialising args this way keeps every switch path satisfying lint's StringFormatMatches
    // requirement that the io / required-key / unsupported-schema / unexpected-columns strings
    // get a non-empty arg list.
    String details = fail.details() == null ? "" : fail.details();
    Object[] args = new Object[] {details};
    int resId;
    switch (fail.reason()) {
      case NOT_OPENABLE:
        resId = R.string.offline_address_error_not_openable;
        break;
      case IS_A_ZIP:
        resId = R.string.offline_address_error_is_zip;
        break;
      case MISSING_METADATA_TABLE:
        resId = R.string.offline_address_error_missing_metadata;
        break;
      case MISSING_REQUIRED_METADATA_KEY:
        resId = R.string.offline_address_error_missing_required_key;
        break;
      case UNSUPPORTED_SCHEMA_VERSION:
        resId = R.string.offline_address_error_unsupported_schema;
        break;
      case MISSING_PLACES_TABLE:
        resId = R.string.offline_address_error_missing_places;
        break;
      case UNEXPECTED_PLACES_COLUMNS:
        resId = R.string.offline_address_error_unexpected_columns;
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
