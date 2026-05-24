# Contract: Address-display preferences (`PreferenceStore` + `TwCoordPreferenceFragment`)

**Modified classes**:
- `com.atakmap.android.twcoord.prefs.PreferenceStore`
- `com.atakmap.android.twcoord.TwCoordPreferenceFragment`
- `res/xml/preferences.xml`

**Source of truth for**: the three independent per-row Settings toggles plus the
dataset-presence hint logic.

## `PreferenceStore` additions

New constants and accessors (mirror the existing `KEY_GOTO_MARKER_MODE` / `getGotoMarkerMode`
pattern):

```java
public static final String KEY_ADDRESS_ROW_ME     = "pref_address_row_me";
public static final String KEY_ADDRESS_ROW_TARGET = "pref_address_row_target";
public static final String KEY_ADDRESS_ROW_MAP    = "pref_address_row_map";

public boolean getAddressRowMe();
public boolean getAddressRowTarget();
public boolean getAddressRowMap();
public void setAddressRowMe(boolean enabled);
public void setAddressRowTarget(boolean enabled);
public void setAddressRowMap(boolean enabled);
```

All three default to **false**. The accessors read via `sp.getBoolean(KEY, false)`; the
setters write via `.apply()`.

### Listener fan-out

`PreferenceStore`'s existing `spListener` MUST be extended to fire `fireAll()` when any of the
three new keys changes — so existing prefs subscribers (`TwCoordMapComponent`) wake up and ask
the `AddressSubsystem` to flip per-row toggles. The pattern is one new `||` clause in the
condition in `spListener.onSharedPreferenceChanged(...)`.

The `UserPreference` snapshot record gains three boolean fields:

```java
public record UserPreference(
    CoordinateUnit coordUnit,
    LanguageOverride uiLanguage,
    long staleFixThresholdMs,
    boolean addressRowMe,
    boolean addressRowTarget,
    boolean addressRowMap
) {
    public static UserPreference defaults() {
        return new UserPreference(
            CoordinateUnit.TWD97, LanguageOverride.SYSTEM, 10_000L,
            false, false, false);
    }
}
```

## `preferences.xml` additions

Inserted **after** the existing accuracy notice category:

```xml
<PreferenceCategory
    android:layout="@layout/pref_category"
    android:key="pref_address_header_key"
    android:title="@string/pref_address_header"/>

<SwitchPreference
    android:layout="@layout/pref_item"
    android:key="pref_address_row_me"
    android:title="@string/pref_address_row_me_title"
    android:summary="@string/pref_address_row_me_summary"
    android:defaultValue="false"/>

<SwitchPreference
    android:layout="@layout/pref_item"
    android:key="pref_address_row_target"
    android:title="@string/pref_address_row_target_title"
    android:summary="@string/pref_address_row_target_summary"
    android:defaultValue="false"/>

<SwitchPreference
    android:layout="@layout/pref_item"
    android:key="pref_address_row_map"
    android:title="@string/pref_address_row_map_title"
    android:summary="@string/pref_address_row_map_summary"
    android:defaultValue="false"/>

<Preference
    android:layout="@layout/pref_item"
    android:key="pref_address_dataset_status"
    android:title="@string/pref_address_dataset_status_title"/>
```

## `TwCoordPreferenceFragment` additions

In `refreshAllSummaries()`, after the existing rows:

```java
setPreferenceTitle("pref_address_header_key", wrapped.getString(R.string.pref_address_header));
refreshAddressSwitchTitles(wrapped);     // re-title the three SwitchPreferences
refreshAddressDatasetStatus(wrapped);    // see below
```

`refreshAddressDatasetStatus(...)` MUST set the summary of `pref_address_dataset_status` to
one of:

| Condition | Summary text |
|---|---|
| all three switches off | (row hidden via `pref.setEnabled(false)` + `pref.setSelectable(false)` — invisible to operator) |
| at least one switch on, no dataset active | "No dataset installed — tap to open Offline Address" (clickable; OnClickPreferenceListener broadcasts `ACTION_SHOW_OFFLINE_ADDRESS`) |
| at least one switch on, dataset active | "Active: <county> · <data_date>" (clickable; same broadcast — operator can inspect / replace / remove) |

The fragment learns dataset state by querying `AddressBundleImporter.activeOrNull()` lazily on
`onResume` (the importer is a singleton owned by `TwCoordMapComponent`; the fragment retrieves
it via a static holder pattern the project already uses for `pluginContext`).

## Test plan (`AddressPreferencesTest`, JVM)

| # | Test name | What it asserts |
|---|---|---|
| 1 | `defaultsAreAllFalse` | A fresh `SharedPreferences` reads false for all three keys. |
| 2 | `gettersAndSettersRoundTrip` | Setting each key true → getter returns true; setting back to false → false. |
| 3 | `spListenerFiresFireAllOnNewKeys` | Changing `pref_address_row_me` causes `fireAll()` to invoke registered listeners exactly once. |
| 4 | `userPreferenceSnapshotCarriesNewBooleans` | After setting two of the three, the snapshot record exposes them correctly. |

Fragment behaviour (status row, hint text, dataset-presence) is covered by the
`OfflineAddressReceiverTest` and a thin `TwCoordPreferenceFragmentAddressTest` (Robolectric;
optional — added if the fragment logic grows beyond mechanical title-setting).
