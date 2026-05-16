package com.atakmap.android.twcoord.gotopage;

import com.atakmap.android.twcoord.coord.CoordinateUnit;
import com.atakmap.android.twcoord.prefs.PreferenceStore;
import com.atakmap.coremap.log.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Persists the operator's recent coordinate submissions in {@link PreferenceStore}. Wraps the
 * single {@code pref_goto_recent_json} key with capacity-10 FIFO eviction and dedup on (unit,
 * rawValue). Listener fires on every mutation.
 */
public final class RecentEntryStore {

  public interface Listener {
    void onRecentEntriesChanged(List<RecentEntry> newList);
  }

  private static final String TAG = "TwCoordRecentStore";
  private static final int CAPACITY = 10;

  private final PreferenceStore prefs;
  private final List<Listener> listeners = new CopyOnWriteArrayList<>();
  private List<RecentEntry> cache;

  public RecentEntryStore(PreferenceStore prefs) {
    this.prefs = prefs;
    this.cache = decode(prefs.getGotoRecentJson());
  }

  public synchronized List<RecentEntry> getAll() {
    return Collections.unmodifiableList(new ArrayList<>(cache));
  }

  public synchronized void append(RecentEntry entry) {
    if (entry == null) return;
    List<RecentEntry> next = new ArrayList<>(cache);
    // Dedup on (unit, rawValue): remove any existing entry that matches.
    next.removeIf(e -> e.unit() == entry.unit() && e.rawValue().equals(entry.rawValue()));
    next.add(0, entry); // newest first
    // Sort by timestamp descending for stability.
    next.sort(Comparator.comparingLong(RecentEntry::timestampEpochMs).reversed());
    // Trim to capacity.
    while (next.size() > CAPACITY) next.remove(next.size() - 1);
    persist(next);
  }

  public synchronized void removeAt(int index) {
    if (index < 0 || index >= cache.size()) return;
    List<RecentEntry> next = new ArrayList<>(cache);
    next.remove(index);
    persist(next);
  }

  public synchronized void clear() {
    persist(new ArrayList<>());
  }

  public void registerListener(Listener l) {
    if (l != null) listeners.add(l);
  }

  public void unregisterListener(Listener l) {
    listeners.remove(l);
  }

  private void persist(List<RecentEntry> next) {
    cache = next;
    prefs.setGotoRecentJson(encode(next));
    List<RecentEntry> snap = Collections.unmodifiableList(new ArrayList<>(next));
    for (Listener l : listeners) {
      try {
        l.onRecentEntriesChanged(snap);
      } catch (Exception e) {
        Log.w(TAG, "listener threw", e);
      }
    }
  }

  // === JSON encode / decode ===

  private static String encode(List<RecentEntry> entries) {
    JSONArray arr = new JSONArray();
    for (RecentEntry e : entries) {
      try {
        JSONObject o = new JSONObject();
        o.put("unit", e.unit().name());
        o.put("rawValue", e.rawValue());
        o.put("easting", e.easting());
        o.put("northing", e.northing());
        o.put("zone", e.zone());
        o.put("timestampEpochMs", e.timestampEpochMs());
        arr.put(o);
      } catch (JSONException ex) {
        // Should never happen for these scalar puts; log and skip.
        Log.w(TAG, "encode entry failed", ex);
      }
    }
    return arr.toString();
  }

  private static List<RecentEntry> decode(String json) {
    List<RecentEntry> out = new ArrayList<>();
    if (json == null || json.isEmpty()) return out;
    try {
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.optJSONObject(i);
        if (o == null) continue;
        String unitStr = o.optString("unit", "");
        CoordinateUnit unit;
        try {
          unit = CoordinateUnit.valueOf(unitStr);
        } catch (IllegalArgumentException ex) {
          // Unknown unit string — skip (forward-compat allowance).
          continue;
        }
        String rawValue = o.optString("rawValue", "");
        int easting = o.optInt("easting", 0);
        int northing = o.optInt("northing", 0);
        int zone = o.optInt("zone", 0);
        long ts = o.optLong("timestampEpochMs", 0L);
        out.add(new RecentEntry(unit, rawValue, easting, northing, zone, ts));
      }
    } catch (JSONException ex) {
      // Corrupt JSON — recover as empty list. Next mutation overwrites the bad value.
      Log.w(TAG, "decode failed, returning empty list", ex);
      return new ArrayList<>();
    }
    return out;
  }
}
