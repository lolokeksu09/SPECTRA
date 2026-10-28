# SPECTRA 0.1 — Android Digital Twin

Native privacy-first Android diagnostics and interactive OpenGL ES 2.0 phone model.
Built for Android 10+; primary physical test target: Lenovo Legion Y70 (Motorola ROM, Android 16).

## Features

- Touch-rotatable, pinch-zoomable **3D device representation** (stylized generic phone, not an exact PCB/case replica).
- Real battery level, condition, temperature, voltage, charge current **when reported by the OS**.
- Android memory and data partition storage stats, reported display Hz.
- Android Thermal API status (not CPU/GPU sensor temperature).
- Local, user-initiated diagnostic snapshots; newest-first timeline. No network permission.
- AMOLED-first Compose UI with system navigation and accessibility labels.

## Build

Open project in Android Studio with SDK 36 and JDK 17, or:

```sh
gradle --no-daemon testDebugUnitTest assembleDebug
```

Gradle 8.13 / Android Gradle Plugin 8.13.2 / Kotlin & Compose compiler 2.2.20. Requires a locally installed Gradle, or the GitHub Actions workflow (automatically provisions Gradle). We intentionally do **not** ship a fake/broken `gradlew` without its official wrapper JAR.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Each successful push to main uploads `SPECTRA-0.1-debug` under Actions > Artifacts and automatically publishes a traceable **debug-signed prerelease APK** under Releases (`v0.1.0-dev.<run_number>`). Pushing a `v0.1.0` tag publishes a separately named prerelease. It is not a signed production release.

## Architecture

- `core/TelemetryReader.kt`: Android API probes with unavailable values kept as null.
- `core/TelemetryModels.kt`: immutable measurements and tested formatting.
- `core/SnapshotStore.kt`: device-local atomic JSON snapshot storage.
- `render/DeviceGLView.kt`: native OpenGL ES interactive 3D representation (prototype). No model claiming actual handset internal geometry.
- `ui/`: Compose Material 3 dashboard, state, navigation.

## Privacy and limitations

SPECTRA 0.1 does not request INTERNET, location or root. No background service; telemetry refreshes when app is visible. It does not access CPU/GPU temperatures or other apps' FPS. Battery current and some optional data might be missing on custom firmware. `ThermalStatus` is system-level thermal severity, not a thermometer. Snapshot history is local and intentionally opt-in.

## Roadmap

0.2: optional device-accurate verified 3D assets and advanced PBR renderer (Filament), device model packs. 0.3: session recording, graphs and Room persistence. 1.0: validated telemetry/device tests, accessibility and performance budget, release signing.

## Verification evidence

See `VERIFICATION.md` for local checks and known limitations. A GitHub Actions build is not equivalent to hardware/device testing.
