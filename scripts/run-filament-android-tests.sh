#!/usr/bin/env bash
# Preserve native logs and screenshots even when a render assertion fails.
set -uo pipefail
mkdir -p build/model-evidence
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c
gradle --no-daemon connectedDebugAndroidTest
spectra_test_status=$?
adb logcat -d > build/model-evidence/android-logcat.txt
adb pull /sdcard/Android/data/com.spectra.twin/files/white-v18-evidence build/model-evidence/filament || true
adb exec-out screencap -p > build/model-evidence/emulator-screen.png
exit "$spectra_test_status"
