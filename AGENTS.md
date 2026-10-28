# SPECTRA engineering rules

- Scope: SPECTRA Android application only. Do not touch NOX, LocalDream, WrCommunity, or other repositories.
- Treat `Build.MODEL`, battery, RAM, storage, and thermal signals as measured device data; don't simulate CPU/GPU sensors or FPS.
- Missing data is `null` and displays "Недоступно", never `0` masquerading as a sensor reading.
- Never conflate Android thermal severity (0..6) with degrees Celsius.
- Keep a phone model generic unless geometry and component positions are verified against hardware.
- Preserve native system Back, Android window insets and AMOLED contrast; no mandatory network permissions or trackers.
- Run `testDebugUnitTest` and `assembleDebug` before declaring an APK working. Record build SHA, source revision, test and device evidence separately.
- For telemetry behavior changes, test unknown and invalid sensors, missing data, and lifecycle suspend/resume.
- Do not claim real-device tests from Compose previews or CI builds.

## Workflow Standard v1.3 — интеграция

- Перед многокомпонентной правкой проверь [PROJECT.md](PROJECT.md), исходный код и CI; не путай утверждённые решения и предположения.
- Для проверок и релизов используй [TESTING-LEVELS.md](TESTING-LEVELS.md). Записывай SHA, конкретные команды и ограничения в доказательствах.
- После UI-правок отдельно проверяй экран и жесты на новой сборке эмулятора/телефона. CI-билд сам по себе не является визуальной проверкой или L5.
- Повторяй проверку при существенном изменении, не удаляй пользовательские данные и не перезаписывай чужие изменения.
- Интеграция оригинального архива документирована в [docs/WORKFLOW-ADOPTION.md](docs/WORKFLOW-ADOPTION.md).
