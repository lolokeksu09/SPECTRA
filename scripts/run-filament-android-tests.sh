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
# Public MediaStore Downloads survive Gradle's automatic test-app uninstall.
if [ "$spectra_test_status" -eq 0 ]; then
    adb pull /sdcard/Download/SPECTRA-v19 build/model-evidence/filament || exit 1
    python3 - <<'PY_CHECK'
from pathlib import Path
from PIL import Image
root = Path('build/model-evidence/filament')
for version in ('v18', 'v19'):
    for shot in ('rear', 'cameras', 'bottom', 'top', 'button-side', 'opposite-side', 'volume-closeup'):
        file = root / f'white-{version}-{shot}.png'
        with Image.open(file) as image:
            assert image.width >= 300 and image.height >= 300, file
assert (root / 'android-load-metrics.json').is_file()
print('All 14 real Android Filament screenshots and load metrics recovered')
PY_CHECK
    [ "$?" -eq 0 ] || exit 1
else
    adb pull /sdcard/Download/SPECTRA-v19 build/model-evidence/filament || true
fi
adb exec-out screencap -p > build/model-evidence/emulator-screen.png
exit "$spectra_test_status"
