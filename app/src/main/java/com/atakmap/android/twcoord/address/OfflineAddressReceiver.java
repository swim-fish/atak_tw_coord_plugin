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
import com.atakmap.android.twcoord.coord.ByteCountFormatter;
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
  // Feature 005 — State B "Import…" button so the operator can add more counties without
  // having to Remove first. Same target as the State A Import button.
  private final Button stateBImportBtn;
  // Legacy single-active container views (hidden in the multi-county production path).
  private final View legacyTable;
  private final View legacyActions;
  // Feature 007 US3 — page-level _boundary size row (outside the State A/B groups so it stays
  // visible regardless of how many county datasets are active).
  private final TextView boundaryRowView;

  /**
   * Feature 005 hook: bound after ctor via {@link #setBatchCoordinator(BatchImportCoordinator)}.
   * When non-null, the Import button routes through {@link BatchImportCoordinator#enqueue} instead
   * of the legacy single-file {@code importer.importFrom} path. The legacy path stays available as
   * a fallback if the coordinator is null (e.g. JVM tests).
   */
  private BatchImportCoordinator batchCoordinator;

  /** Last-seen batch session report (rebuilt on every {@code onBatchComplete} listener fire). */
  private BatchImportReport lastBatchReport;

  /**
   * Set when any entry in the current batch was rejected for a county mismatch (per-county Replace
   * picked the wrong county). Keeps the progress chip visible so the operator notices, since a
   * mismatch counts as "skipped" rather than "failed" in the batch summary.
   */
  private boolean sawCountyMismatch;

  /**
   * County the operator is replacing in the current per-county Replace flow, captured when the
   * picker is launched so the mismatch error can name both the expected and the picked county. Null
   * for a plain Import.
   */
  private String pendingReplaceCounty;

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
    this.stateBImportBtn = view.findViewById(R.id.offline_address_state_b_import);
    this.legacyTable = view.findViewById(R.id.offline_address_state_b_legacy_table);
    this.legacyActions = view.findViewById(R.id.offline_address_state_b_legacy_actions);
    this.boundaryRowView = view.findViewById(R.id.offline_address_boundary_row);

    if (stateAImportBtn != null) {
      stateAImportBtn.setOnClickListener(v -> safeRun(this::launchPicker));
    }
    if (stateBImportBtn != null) {
      stateBImportBtn.setOnClickListener(v -> safeRun(this::launchPicker));
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
    // Detach the batch listener so an in-flight import can't keep posting UI work into a disposed
    // receiver, and drop any pending delayed callbacks (e.g. the auto-hide-progress runnable).
    if (batchCoordinator != null) {
      try {
        batchCoordinator.removeListener(batchListener);
      } catch (Throwable t) {
        Log.w(TAG, "removeListener on dispose threw", t);
      }
      batchCoordinator = null;
    }
    try {
      ui.removeCallbacksAndMessages(null);
    } catch (Throwable t) {
      Log.w(TAG, "ui.removeCallbacksAndMessages on dispose threw", t);
    }
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

  /** Feature 005: bound after ctor — when non-null the page renders the multi-county list. */
  private ActiveDatasetRegistry registry;

  public void setRegistry(ActiveDatasetRegistry registry) {
    this.registry = registry;
  }

  /**
   * Feature 007 US3: bound after ctor (from TwCoordMapComponent). When non-null, each county row
   * shows its on-disk folder size and a distinct {@code _boundary} (townships.sqlite) size row is
   * appended. Null in JVM tests / legacy paths — the size annotations are simply skipped.
   */
  private FileSystem fileSystem;

  public void setFileSystem(FileSystem fileSystem) {
    this.fileSystem = fileSystem;
  }

  /**
   * Container for the dynamic per-county row list. Resolved lazily on first bind so the receiver
   * still works against the legacy offline_address_page.xml that doesn't have the container.
   */
  private android.widget.LinearLayout countyList;

  private void bindFromActiveDataset() {
    if (registry != null) {
      java.util.Map<String, CountyActiveDataset> snap = registry.snapshot();
      if (snap.isEmpty()) {
        bindStateA();
      } else {
        bindStateBMultiCounty(snap);
      }
      renderBoundaryRow();
      return;
    }
    // Legacy single-active fallback (no registry bound — e.g. JVM tests).
    AddressDataset active = importer.activeOrNull();
    if (active == null) {
      bindStateA();
    } else {
      bindStateB(active);
    }
    renderBoundaryRow();
  }

  private void bindStateBMultiCounty(java.util.Map<String, CountyActiveDataset> snap) {
    if (stateAGroup != null) stateAGroup.setVisibility(View.GONE);
    if (stateBGroup != null) stateBGroup.setVisibility(View.VISIBLE);
    // Defensive: ensure the legacy single-active block is hidden when rendering the per-county
    // list (an earlier bindStateB call before the registry was bound can leave it visible).
    if (legacyTable != null) legacyTable.setVisibility(View.GONE);
    if (legacyActions != null) legacyActions.setVisibility(View.GONE);
    if (countyList == null) {
      countyList = view.findViewById(R.id.offline_address_state_b_list);
    }
    if (countyList == null) {
      // Layout doesn't have the new container (very-old layout cache); fall back to single-active
      // rendering of the first entry so the user still sees something useful.
      java.util.Iterator<CountyActiveDataset> it = snap.values().iterator();
      if (it.hasNext()) bindStateB(it.next().dataset());
      return;
    }
    countyList.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(pluginContext);
    for (CountyActiveDataset entry : snap.values()) {
      try {
        View row = inflater.inflate(R.layout.offline_address_county_row, countyList, false);
        TextView nameView = row.findViewById(R.id.offline_address_county_name);
        TextView summaryView = row.findViewById(R.id.offline_address_county_summary);
        Button replaceBtn = row.findViewById(R.id.offline_address_county_replace);
        Button removeBtn = row.findViewById(R.id.offline_address_county_remove);
        GeneratorMetadata gm = entry.dataset().generator();
        if (nameView != null) nameView.setText(nonNull(gm.county()));
        if (summaryView != null) {
          String summary =
              pluginContext.getString(
                  R.string.pref_address_active_dataset_row_format,
                  nonNull(gm.dataDate()),
                  gm.insertedRows() >= 0 ? gm.insertedRows() : 0L);
          // Feature 007 US3 — append the county folder's on-disk size (FR-012).
          if (fileSystem != null) {
            long bytes = fileSystem.sizeOfDirectory(fileSystem.activeCountyDir(entry.county()));
            summary = summary + " · " + ByteCountFormatter.format(bytes);
          }
          summaryView.setText(summary);
        }
        final String county = entry.county();
        if (replaceBtn != null) {
          replaceBtn.setOnClickListener(v -> safeRun(() -> confirmReplaceCounty(county)));
        }
        if (removeBtn != null) {
          removeBtn.setOnClickListener(v -> safeRun(() -> confirmRemoveCounty(county)));
        }
        countyList.addView(row);
      } catch (Throwable t) {
        Log.w(TAG, "inflate county row " + entry.county() + " threw", t);
      }
    }
  }

  /**
   * Feature 007 US3 (FR-013) — render the distinct {@code _boundary} (townships.sqlite) size row at
   * the page level (outside State A/B), so the boundary size stays visible whether or not any
   * county datasets are active (C2: the boundary exists independently of counties). Shows the
   * folder total, or "未安裝" when no boundary is installed (FR-015). Best-effort: any failure is
   * logged and the row is left as-is rather than crashing the page.
   */
  private void renderBoundaryRow() {
    if (boundaryRowView == null) return;
    if (fileSystem == null) {
      boundaryRowView.setVisibility(View.GONE);
      return;
    }
    try {
      boolean present = fileSystem.exists(fileSystem.boundaryDbFile());
      String value =
          present
              ? ByteCountFormatter.format(fileSystem.sizeOfDirectory(fileSystem.boundaryDir()))
              : pluginContext.getString(R.string.offline_address_not_installed);
      boundaryRowView.setText(
          pluginContext.getString(R.string.offline_address_boundary_row_format, value));
      boundaryRowView.setVisibility(View.VISIBLE);
    } catch (Throwable t) {
      Log.w(TAG, "renderBoundaryRow threw", t);
    }
  }

  private void bindStateA() {
    if (stateAGroup != null) stateAGroup.setVisibility(View.VISIBLE);
    if (stateBGroup != null) stateBGroup.setVisibility(View.GONE);
  }

  private void bindStateB(AddressDataset active) {
    if (stateAGroup != null) stateAGroup.setVisibility(View.GONE);
    if (stateBGroup != null) stateBGroup.setVisibility(View.VISIBLE);
    // Legacy single-active path — flip the gone-by-default metadata table + button row visible.
    if (legacyTable != null) legacyTable.setVisibility(View.VISIBLE);
    if (legacyActions != null) legacyActions.setVisibility(View.VISIBLE);
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

  /**
   * Feature 005: bind the batch coordinator after ctor (called from TwCoordMapComponent).
   * Idempotent — re-binding (tests, hot-reload, future rewiring) first detaches {@link
   * #batchListener} from the previous coordinator so a stale coordinator can't keep posting UI work
   * through this receiver.
   */
  public void setBatchCoordinator(BatchImportCoordinator coordinator) {
    if (this.batchCoordinator == coordinator) return;
    if (this.batchCoordinator != null) {
      try {
        this.batchCoordinator.removeListener(batchListener);
      } catch (Throwable t) {
        Log.w(TAG, "removeListener on previous coordinator threw", t);
      }
    }
    this.batchCoordinator = coordinator;
    if (coordinator != null) {
      coordinator.addListener(batchListener);
    }
  }

  private void launchPicker() {
    launchPicker(null);
  }

  /**
   * Open the file picker. When {@code expectedCounty} is non-null (per-county Replace), the picked
   * file is enqueued with that constraint so a county-mismatched dataset is rejected rather than
   * silently replacing the wrong county.
   */
  private void launchPicker(String expectedCounty) {
    if (importInFlight.get()) {
      Log.d(TAG, "import already in flight; ignoring Import button");
      return;
    }
    // Remember the expected county (null for a plain Import) so a mismatch error can name it.
    this.pendingReplaceCounty = expectedCounty;
    // Run on ATAK's host context — ImportFileBrowserDialog needs a UI-thread Activity context;
    // the plugin context lacks the Activity token. Using getMapView().getContext() matches the
    // helloworld sample (HelloWorldDropDownReceiver.sampleFileBrowser, line 3657).
    Context atakCtx = getMapView().getContext();
    ImportFileBrowserDialog dialog = new ImportFileBrowserDialog(atakCtx);
    // Feature 005: accept .zip in addition to .sqlite / .db. The BatchImportCoordinator
    // dispatches each picked file based on its extension.
    dialog.setExtensionTypes("sqlite", "db", "zip");
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
              startImport(file, expectedCounty);
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

  /** Listener that fans batch-progress events back to the page UI thread. */
  private final BatchImportCoordinator.Listener batchListener =
      new BatchImportCoordinator.Listener() {
        @Override
        public void onEntryStarted(BatchImportReport.Entry entry) {
          ui.post(() -> renderInflight(entry));
        }

        @Override
        public void onEntryFinished(BatchImportReport.Entry entry) {
          ui.post(() -> renderEntryFinished(entry));
        }

        @Override
        public void onBatchComplete(BatchImportReport report) {
          ui.post(
              () -> {
                lastBatchReport = report;
                importInFlight.set(false);
                renderBatchSummary(report);
                bindFromActiveDataset();
                // Auto-hide the progress chip after the summary has been visible briefly,
                // BUT only when the batch fully succeeded. Failures and county mismatches stay
                // visible so the operator notices them. 3 s is roughly the time it takes to read
                // the summary line.
                if (report.failedCount() == 0 && !sawCountyMismatch) {
                  ui.postDelayed(this::hideProgressFromBatchComplete, 3000L);
                }
              });
        }

        private void hideProgressFromBatchComplete() {
          try {
            hideProgress();
          } catch (Throwable t) {
            Log.w(TAG, "hideProgress (delayed) threw", t);
          }
        }
      };

  private void renderInflight(BatchImportReport.Entry entry) {
    if (progressView == null) return;
    String county = entry.county() != null ? entry.county() : entry.filename();
    progressView.setText(
        pluginContext.getString(R.string.offline_address_entry_status_extracting) + " — " + county);
    progressView.setVisibility(View.VISIBLE);
  }

  private void renderEntryFinished(BatchImportReport.Entry entry) {
    if (progressView == null) return;
    String county = entry.county() != null ? entry.county() : entry.filename();
    int strId;
    switch (entry.status()) {
      case ACTIVATED:
        strId = R.string.offline_address_entry_status_activated;
        break;
      case REPLACED:
        strId = R.string.offline_address_entry_status_activated;
        break;
      case SKIPPED_SUPPLEMENTARY:
        strId = R.string.offline_address_entry_status_skipped_supplementary;
        break;
      case SKIPPED_DUPLICATE:
        strId = R.string.offline_address_entry_status_skipped_duplicate;
        break;
      case SKIPPED_COUNTY_MISMATCH:
        sawCountyMismatch = true;
        strId = R.string.offline_address_entry_status_county_mismatch;
        // Surface as an inline error too — a mismatch is an operator mistake worth flagging.
        // %1$s = picked file's county, %2$s = the row's expected county.
        showError(
            pluginContext.getString(
                R.string.offline_address_error_county_mismatch_format,
                county,
                pendingReplaceCounty != null ? pendingReplaceCounty : county));
        break;
      case FAILED:
      default:
        strId = R.string.offline_address_entry_status_failed;
        break;
    }
    progressView.setText(pluginContext.getString(strId) + " — " + county);
  }

  private void renderBatchSummary(BatchImportReport report) {
    if (progressView == null) return;
    String summary =
        pluginContext.getString(
            R.string.offline_address_batch_done,
            report.activatedCount(),
            report.replacedCount(),
            report.skippedCount(),
            report.failedCount());
    progressView.setText(summary);
    if (report.failedCount() > 0) {
      progressView.setVisibility(View.VISIBLE);
    }
  }

  /**
   * Run import on a picked {@link File}. Opens the {@link FileInputStream} on the calling thread,
   * then hands the already-open stream to the worker (mirrors the worker contract the SAF-broadcast
   * era used; the worker closes the stream via try-with-resources).
   */
  private void startImport(File file, String expectedCounty) {
    if (!importInFlight.compareAndSet(false, true)) {
      Log.d(TAG, "import already in flight; ignoring second pick");
      return;
    }
    clearError();
    sawCountyMismatch = false;
    // Feature 005: prefer the batch coordinator (handles .zip + multi-county + bare .sqlite).
    if (batchCoordinator != null) {
      showProgress(pluginContext.getString(R.string.offline_address_entry_status_extracting));
      try {
        batchCoordinator.enqueue(file, expectedCounty);
        batchCoordinator.finishBatch();
      } catch (Throwable t) {
        Log.w(TAG, "batchCoordinator.enqueue threw", t);
        importInFlight.set(false);
        hideProgress();
        showError(t.getMessage() == null ? "enqueue failed" : t.getMessage());
      }
      return;
    }
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

  /**
   * Feature 005 US2 per-county Replace. Opens the picker; on success the BatchImportCoordinator
   * imports the file with strict county-match against {@code countyExpected}; on mismatch the
   * coordinator's BatchImportReport surfaces SKIPPED_COUNTY_MISMATCH which the listener renders as
   * an inline error.
   */
  private void confirmReplaceCounty(String countyExpected) {
    if (countyExpected == null) return;
    String msg = pluginContext.getString(R.string.offline_address_confirm_replace, countyExpected);
    new AlertDialog.Builder(getMapView().getContext())
        .setTitle(pluginContext.getString(R.string.offline_address_button_replace))
        .setMessage(msg)
        .setPositiveButton(
            android.R.string.ok,
            // picker → coordinator with expectedCounty → SKIPPED_COUNTY_MISMATCH on mismatch.
            (d, w) -> safeRun(() -> launchPicker(countyExpected)))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  /** Feature 005 US2 per-county Remove. */
  private void confirmRemoveCounty(String county) {
    if (county == null) return;
    String msg = pluginContext.getString(R.string.offline_address_confirm_remove, county);
    new AlertDialog.Builder(getMapView().getContext())
        .setTitle(pluginContext.getString(R.string.offline_address_button_remove))
        .setMessage(msg)
        .setPositiveButton(
            android.R.string.ok,
            (d, w) ->
                safeRun(
                    () -> {
                      importExecutor.execute(
                          () -> {
                            try {
                              // Delete via the registry's atomic remove, which closes the open
                              // SQLite facade BEFORE deleting active/<county>/. Calling
                              // importer.removeActive() first (the previous behaviour) unlinks the
                              // DB while the native connection is still open; on-device that leaves
                              // WAL/SHM sidecars locked and the connection's checkpoint-on-close
                              // resurrects places.sqlite, so the directory survives and the county
                              // reappears after restart. registry.remove() does close-then-delete,
                              // so the dir is gone for good. Fall back to the importer only when no
                              // registry is bound (JVM tests / legacy single-active).
                              if (registry != null) {
                                registry.remove(county);
                              } else {
                                importer.removeActive(county);
                              }
                            } catch (Throwable t) {
                              Log.w(TAG, "removeActive(" + county + ") threw", t);
                            }
                            ui.post(
                                () -> {
                                  try {
                                    bindFromActiveDataset();
                                    AtakBroadcast.getInstance()
                                        .sendBroadcast(
                                            new Intent(
                                                OfflineAddressIntents.ACTION_DATASET_CHANGED));
                                  } catch (Throwable t) {
                                    Log.w(TAG, "post-remove UI bind threw", t);
                                  }
                                });
                          });
                    }))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

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
    // Dialog needs an Activity context (window token) — the plugin's ApplicationContext has
    // token=null and Android throws BadTokenException. Same reason ImportFileBrowserDialog uses
    // getMapView().getContext() in launchPicker().
    new AlertDialog.Builder(getMapView().getContext())
        .setTitle(pluginContext.getString(R.string.offline_address_button_replace))
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
    new AlertDialog.Builder(getMapView().getContext())
        .setTitle(pluginContext.getString(R.string.offline_address_button_remove))
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
