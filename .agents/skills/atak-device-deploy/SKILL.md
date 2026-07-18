---
name: atak-device-deploy
description: Build, install, reload, and collect safe acceptance evidence for this ATAK plugin on a connected Android device. Use for adb devices, install to device, ATAK restart, plugin reload, logcat smoke checks, or compatibility matrices.
---

# ATAK Device Deploy

Read the active feature `quickstart.md` and applicable compatibility ADR before
acting.

1. Run `adb devices -l`. If exactly one authorized device exists, use it; if
   multiple exist and the request does not identify one, ask before installing.
2. Build the requested variant without changing the declared compatibility.
3. Install with `adb -s <DEVICE_SERIAL> install -r <APK>`.
4. Explain that reinstall alone may not reload an active ATAK plugin; disable
   and re-enable it or fully restart ATAK as the acceptance plan requires.
5. Verify the installed plugin version and inspect a narrowly scoped logcat
   window for fatal/plugin-load/version-skew errors.
6. Record only device model, Android version, ATAK version, plugin version,
   scenario result, and date. Never commit serial numbers, owner identifiers,
   callsigns, precise locations, or raw logcat containing personal data.

Evidence from ATAK 5.7 does not satisfy an ATAK 5.5 release gate.
