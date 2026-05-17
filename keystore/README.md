# Release Keystore

The release keystore (`release.keystore`) lives in this directory and is
gitignored via `.gitignore`'s `*.keystore` rule. The password lives in
`local.properties` (also gitignored). **Neither the keystore nor its password
should ever land in git.**

## Current keystore (recorded for reference)

| Field | Value |
| --- | --- |
| DN | `CN=Taichung City Citizen Corps Association, O=Taichung City Citizen Corps Association, L=Taichung, ST=Taiwan, C=TW` |
| Algorithm | SHA384withRSA, 4096-bit RSA |
| Validity | 2026-05-17 → 2094-10-27 (25,000 days) |
| Alias | `twcoord-release` |
| SHA1 fingerprint | `A2:F5:B4:55:05:A6:1E:23:C8:81:63:96:D6:46:99:40:09:39:3D:64` |
| SHA256 fingerprint | `B7:86:BA:83:E7:D9:C3:04:08:5A:35:A9:16:0E:C5:1D:FB:BC:50:CA:96:B1:28:40:28:75:A5:E1:99:D6:20:C9` |

## Regenerate

```sh
keytool -genkeypair \
  -keystore keystore/release.keystore \
  -alias twcoord-release \
  -keyalg RSA -keysize 4096 -validity 25000 \
  -storepass <password> -keypass <password> \
  -dname "CN=Taichung City Citizen Corps Association, O=Taichung City Citizen Corps Association, L=Taichung, ST=Taiwan, C=TW"
```

Then update `releaseStorePassword` and `releaseKeyPassword` in `local.properties`
(both can be the same value).

## Verify

```sh
keytool -list -v \
  -keystore keystore/release.keystore \
  -storepass <password> \
  -alias twcoord-release
```

## Rotation impact

If the keystore is regenerated (different key material), end users who
installed the previous version will need to **uninstall the plugin before
installing the new one**. Android refuses to install an APK signed with a
different key over an existing package of the same `packageName`. Record the
new SHA256 in this README when rotating.

## Why a separate keystore vs. the inherited "WinTec Arrowmaker" demo cert

The earlier `app/build/android_keystore` file (alias `wintec_mapping`,
password `tnttnt`) is a community-shared demo keystore used by
[meshtastic/ATAK-Plugin](https://github.com/meshtastic/ATAK-Plugin), the
ATAK plugin template, and others. Convenient for first-pass dev — but if
multiple plugins ship signed with the same key, Android can't distinguish
who built what, and rotating it requires the whole community to coordinate.
This dedicated `twcoord-release` keystore exists so this plugin owns its own
signature.

## TAK Third Party Pipeline (TPP)

When submitting to https://tak.gov/user_builds, TPP signs the output APK with
the Government third-party cert; the input signing config is ignored. So
either an unsigned release APK or a release APK signed with this keystore is
acceptable as TPP input — but end users will only ever see the TPP signature.
