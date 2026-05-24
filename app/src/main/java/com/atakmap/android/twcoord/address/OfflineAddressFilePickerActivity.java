package com.atakmap.android.twcoord.address;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.coremap.log.Log;

/**
 * Transparent shim Activity that hosts SAF ({@link Intent#ACTION_OPEN_DOCUMENT}) for the {@link
 * OfflineAddressReceiver}. The receiver itself extends {@link
 * com.atakmap.android.dropdown.DropDownReceiver}, which is not an {@link Activity} and cannot host
 * an Activity-result callback directly; this shim accepts the SAF result, broadcasts the picked URI
 * back via {@link AtakBroadcast}, and finishes immediately.
 *
 * <p>Registered in {@code AndroidManifest.xml} as:
 *
 * <pre>
 * &lt;activity android:name=".address.OfflineAddressFilePickerActivity"
 *           android:exported="false"
 *           android:theme="@android:style/Theme.Translucent.NoTitleBar"/&gt;
 * </pre>
 *
 * <p>All lifecycle callbacks are wrapped in {@code try/catch (Throwable)} per Constitution VI — the
 * receiver runs hosted in the ATAK process and an uncaught exception here would kill it.
 */
public final class OfflineAddressFilePickerActivity extends Activity {

  private static final String TAG = "OfflineAddressFilePicker";
  private static final int REQUEST_CODE_OPEN_DOCUMENT = 0x0A50;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("application/octet-stream");
      intent.putExtra(
          Intent.EXTRA_MIME_TYPES,
          new String[] {"application/octet-stream", "application/x-sqlite3"});
      startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT);
    } catch (Throwable t) {
      Log.w(TAG, "launching SAF threw", t);
      finish();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    try {
      super.onActivityResult(requestCode, resultCode, data);
      if (requestCode == REQUEST_CODE_OPEN_DOCUMENT
          && resultCode == Activity.RESULT_OK
          && data != null
          && data.getData() != null) {
        Uri picked = data.getData();
        Intent broadcast = new Intent(OfflineAddressIntents.ACTION_PICK_FILE_RESULT);
        broadcast.putExtra(OfflineAddressIntents.EXTRA_PICKED_URI, picked.toString());
        AtakBroadcast.getInstance().sendBroadcast(broadcast);
      }
      // RESULT_CANCELED: do nothing — the page stays in its current state.
    } catch (Throwable t) {
      Log.w(TAG, "handling SAF result threw", t);
    } finally {
      finish();
    }
  }
}
