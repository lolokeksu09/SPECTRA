# White v19 — volume rocker and side antennas

Baseline: White v18 in `v0.2.0-dev.47`, source `99d73840ca1fb9d1b1d1a0ad683ae11b41b58c0f`.
The existing v9 → v15 → v17 → v18 chain is unchanged; `refine-white-v19.py` refines its packaged output.

## Confirmed defects and bounded correction

The supplied rear/front three-quarter reference images (`3157(2).png`, `3160(2).png`) show a shared elongated volume-control outline with a transverse centre division, plus light side-antenna inserts. In v18 the volume control had an unrounded box silhouette with no division. The side inserts were darker than the rail and their outer X coordinate was 0.045 mm below the chassis surface.

- Rebuild only the existing volume-control body and its highlight node as one watertight connected rocker with rounded ends and a shallow V groove. The highlight node becomes the conforming dark groove inset. The combined previous envelope and location are retained. The nominal groove depth is 0.12 mm; this is a modelling choice, not a claimed hardware measurement.
- Reuse the four existing side-antenna nodes. Each is a light nonmetallic ceramic ribbon following the **existing** chassis cross-section and both bevels, offset 8 microns to avoid occlusion/z-fighting. Its nominal width is 0.65 mm.
- Retain upper side-antenna Y centres. Move the lower side inserts to Y = −41 mm, approximately three-quarters down the 163.96 mm body, guided by the two reference views. This is a photo-derived approximation; the reference perspective does not establish an exact factory CAD dimension.
- Do not alter the power key, other keys, chassis vertices, cameras, rear/logo pixels or materials, ports, speakers, top seams, bottom USB-C separators at ±5.9 mm, the black asset, lighting or runtime gesture/viewer source.

The output still contains 101 mesh primitives. Local export: 84,092 triangles (v18: 81,700) and about 4.58 MiB; no added textures. The change adds approximately 2.9% triangles and 60 KB. These are resource counts, not target-device FPS measurements.

## Verification

`check-white-v19.py` compares v18/v19 exports: 95 protected meshes retain exact vertices and faces, matching normals, PBR values and decoded texture pixels. It also checks the joined watertight rocker, actual groove depth, retained envelope, visible outer seam coordinates, bevel coverage, seam widths/positions, chassis dimensions, hard-edge normals, bottom USB-C separators, black asset hash and export budgets. No SciPy, rtree or boolean-mesh dependency is introduced.

Khronos glTF Validator checks the final packaged v19 with zero errors/warnings as a CI gate. Existing v18 regression checks still run before v19 is produced.

The Android API 35/SwiftShader test compares v18 and v19 using the unmodified `FilamentDeviceView`. It captures rear/cameras/top/bottom plus both side faces and a volume-control closeup: **14 actual Android images**. Screenshot shape checks and a native presentation wait prevent an old camera frame being accepted as a side/end view. Evidence is saved through MediaStore Downloads, surviving Gradle test-app uninstall, and all 14 images must be recovered before publication.

The release includes `White-v19-verification.zip` with baseline/final GLBs, export and Khronos reports, load/PSS metrics, native logs and Android screenshots. Unit and instrumentation HTML reports are in the Actions artifact. `docs/evidence/white-v19/` contains local OpenGL renders of actual exports, explicitly separate from Android captures.

## Local reproduction

After generating v18 with the existing scripts:

```bash
python3 scripts/refine-white-v19.py build/model-evidence/white-v18.glb build/model-evidence/white-v19.glb
python3 scripts/check-white-v19.py build/model-evidence/white-v18.glb build/model-evidence/white-v19.glb --report build/model-evidence/v19-checks.json
python3 scripts/render-white-v19-comparison.py build/model-evidence/white-v18.glb build/model-evidence/white-v19.glb build/model-evidence/renders
```

The optional render dependency setup is documented in `WHITE-V18.md`; the build/refinement itself uses the already pinned numpy/Pillow/trimesh dependencies.

The physical Legion Y70 / Motorola-ported Android 16 driver, hardware FPS and touch feel remain unverified until tested on that device. Previous releases remain available for rollback; no user-data migrations or deletion are involved.
