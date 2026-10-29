# White v18 — SPECTRA / Filament

Baseline: `main` / `56c6e7e73306ed11ef84c34bfff602ce165f2906`, prerelease `v0.2.0-dev.43`.

The existing packaging chain is retained: immutable bundled v9 → `build-white-v15-mobile.py` → `refine-white-v17.py` → `refine-white-v18.py`. Only the white packaged output changes. The black source, Filament renderer, lighting, bookmarks, full orbit, pinch gestures and telemetry code are unchanged.

## Implemented

- Replace six flat optical discs within the two large lens housings with curved optical annuli, recessed chambers and convex aspheric pupils. Existing housing centres and outer rings are retained. Move six small optical/coating surfaces inward by 0.235 mm to match the new chamber depth.
- Bake only the verified `LYTIA 50MP` lettering into the upper arc of the lower-left optical ring. Reference `3415.jpg` shows these characters; no uncertain lower-arc wording is invented. The photo's diagonal orientation is resolved to the phone's upright coordinates.
- Preserve the existing concentric ring geometry; adjust its metallic/roughness response.
- Limit the camera plateau's diffuse factor to keep its silver micrograin below clipping under SPECTRA's unchanged key/SH lighting with tone mapping bypassed.
- Add deterministic 512² base/roughness/normal maps to the silver camera plateau and a subtle 512×1024 etched-glass normal/roughness map to the rear. The rear base texture and UVs, including LEGION and Lenovo, remain exact.
- Match the two lower antenna inserts more closely to the light silver rail. Their vertices, faces and centres at ±5.9 mm from USB-C remain exact.
- Export normals for every mesh and explicit tangent frames for normal-mapped surfaces. Core glTF 2.0 PBR only: no transmission, custom shaders, network assets or lighting changes.

## Automated contracts

`check-white-v18.py` checks exact vertices/faces/normals for 89 protected meshes, shell dimensions (77.32 × 163.96 × 8.29 mm in X/Y/Z), welded closure, hard-edge shell normals, preserved logo pixels and UVs, unchanged rail material, antenna positions, finite unit normals, face winding, nondegenerate triangles, embedded textures, UV/tangent availability, PBR ranges and budgets.

Budget: 101 mesh primitives (same as v17), 81,700 triangles (v17: 69,796), GLB under 5 MiB. Normal and material maps add approximately 20 MiB of decoded texture data including mip levels. These are resource bounds, not measured Legion GPU/FPS claims.

Khronos glTF Validator is pinned to `2.0.0-dev.3.10`; errors and warnings fail CI. Informational notices for inherited non-power-of-two source images and empty nodes remain distinct from errors.

## Rendering evidence

`docs/evidence/white-v18/` contains real exported-GLB OpenGL/Mesa renders made by `render-white-comparison.py`. Cameras and lighting are identical before/after. These are not Filament screenshots and do not certify on-phone appearance. Source photos were inspected privately and are not republished.

The Android workflow separately loads v17 and v18 through the existing **FilamentDeviceView** on an API 35 emulator, reads back its TextureView and saves rear/camera/bottom/top screenshots. A non-exported debug-only host and a test-APK fixture keep this out of production UI. The model loader must succeed and each captured image must contain rendered geometry and background. This guards actual gltfio compatibility; visual judgement uses the captured files separately.

`White-v18-verification.zip` in Releases contains both generated GLBs, geometry/PBR and Khronos reports, and the real Android Filament screenshots when that workflow succeeds. CI also archives unit/instrumentation reports. The installed APK contains v18; v17 is confined to test fixtures/evidence.

## Reproduction

```bash
python3 -m pip install numpy==2.3.5 pillow==12.3.0 trimesh==4.11.1
mkdir -p build/model-evidence
python3 scripts/check-v9-models.py
python3 scripts/build-white-v15-mobile.py app/src/main/assets/models/legion_y70_white.glb build/model-evidence/white-v17.glb
python3 scripts/refine-white-v17.py build/model-evidence/white-v17.glb
python3 scripts/refine-white-v18.py build/model-evidence/white-v17.glb build/model-evidence/white-v18.glb
python3 scripts/check-white-v18.py build/model-evidence/white-v17.glb build/model-evidence/white-v18.glb --report build/model-evidence/v18-checks.json
```

For optional desktop renders, install `freetype-py imageio networkx pyglet PyOpenGL==3.1.10`, then `pyrender==0.1.45 --no-deps` (pyrender's historical PyOpenGL 3.1.0 pin does not support this Python/NumPy environment), and run:

```bash
python3 scripts/render-white-comparison.py build/model-evidence/white-v17.glb build/model-evidence/white-v18.glb build/model-evidence/renders
```

## Acceptance limits and recovery

- Local export checks and actual OpenGL renders are separate from Android CI and physical hardware acceptance.
- API 35 emulator validation does not confirm the Motorola-ported Android 16 driver or the real Legion's frame times, memory pressure or gesture feel.
- L5 remains pending until the exact published APK is installed on the white Legion Y70 and visually inspected, including ports, both colour presets, full rotation, zoom and resume.
- Existing `v0.2.0-dev.43` remains available for rollback. No data migrations, model preferences or user data are changed.
