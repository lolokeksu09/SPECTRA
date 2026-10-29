# White v20 — matching rounded single keys

User request: give the opposite left key and the key below the volume rocker the same rounded form and silver finish as the v19 rocker. Keep their locations, dimensions and the already corrected phone unchanged.

## Change

- Replace only `064_power_button` and `V4_dedicated_smart_key` with continuous, rounded capsule solids. Reuse the existing v19 rocker loft with its groove disabled; mirror it for the opposite side. Both solids are watertight, consistently wound and connected.
- Retain each original **combined body + highlight envelope**. The power key remains 12.279386 mm long and 2.090344 mm wide across the side; the opposite key remains 10 mm long and 2.08 mm wide. No key is moved.
- Match the existing volume rocker's base colour, metalness and roughness. Remove the two obsolete rectangular highlight meshes/nodes: real curved geometry now produces the highlight. There is no central divider on these single keys.
- The supplied `3157(2).png` / `3160(2).png` references show the separate rounded power key below the divided volume rocker. The opposite key receives the requested matching treatment; this does not establish measured factory CAD curvature for that side.
- All 97 other meshes retain exact vertices/faces, matching normals, UVs, PBR factors and decoded texture pixels. These include the v19 volume rocker/divider, side antennas, cameras, logos, ports and chassis. Bottom USB-C antenna centres remain at ±5.9 mm. Body dimensions remain 163.96 × 77.32 × 8.29 mm, excluding protrusions.
- No production Kotlin, illumination, zoom/orbit or immutable v9 source assets change. The black asset retains its manifest hash.

## Build and checks

The existing pipeline is v9 → v15 → v17 → v18 → v19 → **v20**; no phone reconstruction. The optional zero-groove branch added to the v19 helper leaves its default v19 output byte-identical to the previous helper in the same local environment.

```bash
python3 scripts/refine-white-v20.py white-v19.glb white-v20.glb
python3 scripts/check-white-v20.py white-v19.glb white-v20.glb --report v20-checks.json
node scripts/validate-glb.cjs white-v20.glb gltf-validator.json
python3 scripts/render-white-v20-comparison.py white-v19.glb white-v20.glb renders
gradle --no-daemon testDebugUnitTest assembleDebug
bash scripts/run-filament-android-tests.sh
```

Local exported geometry: 99 meshes and 88,012 triangles (+3,920 / 4.7% vs v19), under the existing 90,000-triangle / 5 MiB limits. No texture is added. Two highlight draw primitives are removed. Local checker and Khronos validation results are in `docs/evidence/white-v20/`; actual CI exports and their own hashes are retained in `White-v20-verification.zip`.

The Android instrumentation compares its v19 test fixture with the packaged v20 using the unmodified production `FilamentDeviceView`: rear, cameras, bottom, top, both full sides, volume/power closeup and opposite-key closeup (16 frames). It preserves visible-frame/pose checks, screenshots, native frame-failure state and load/PSS observations. `run-filament-android-tests.sh` requires all 16 image files and metrics to survive test-app uninstall before publication.

OpenGL/Mesa renders are actual GLB renders; Android captures are actual Filament output. Neither uses image generation. The emulator uses API 35 / SwiftShader; load/PSS samples are observations, not controlled performance comparisons or hardware FPS. Physical Legion Y70 validation remains pending.

## Delivery / recovery

On a successful main-branch run, the existing workflow publishes the debug-signed APK and `White-v20-verification.zip` as a prerelease tied to the source commit. Its SHA, run URL and delivered artifact checksums identify the exact result; local and CI hashes must not be conflated. The unchanged previous `v0.2.0-dev.49` release remains available for comparison/recovery. A new revert commit can restore the preceding packaging pipeline without altering user data.
