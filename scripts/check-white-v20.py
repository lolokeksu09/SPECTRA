#!/usr/bin/env python3
"""Protect v19 and verify only the two requested single keys changed after export."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import numpy as np
import trimesh

spec = importlib.util.spec_from_file_location('v20', Path(__file__).with_name('refine-white-v20.py'))
v20 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v20)


def check(before_file, after_file):
    before = trimesh.load(before_file, force='scene', process=False)
    after = trimesh.load(after_file, force='scene', process=False)
    assert len(before.geometry) == 101 and len(after.geometry) == 99
    assert set(before.geometry) - set(after.geometry) == set(v20.KEYS.values())
    protected = 0
    dimensions = {}
    for name, mesh in after.geometry.items():
        original = before.geometry[name]
        assert np.isfinite(mesh.vertices).all() and np.isfinite(mesh.vertex_normals).all(), name
        assert np.min(mesh.area_faces) > 1e-16, name
        assert np.allclose(np.linalg.norm(mesh.vertex_normals, axis=1), 1, atol=.002), name
        if name in v20.KEYS:
            bounds = v20.envelope(before, name)
            assert np.allclose(mesh.bounds, bounds, atol=2e-8), name
            assert mesh.is_watertight and mesh.is_winding_consistent and mesh.volume > 0, name
            assert v20.v19.one_connected(mesh), name
            assert len(mesh.vertices) > 500, name
            # In the middle of a single key, the exposed face stays continuous (no volume divider).
            cy = bounds[:, 1].mean()
            sign = np.sign(bounds[:, 0].mean())
            front = mesh.vertices[np.abs(mesh.vertices[:, 1] - cy) < .001]
            assert len(front) > 0
            assert abs(np.max(front[:, 0] * sign) - np.max(bounds[:, 0] * sign)) < 2e-8
            reference = before.geometry[v20.v19.VOLUME].visual.material
            for key in ('baseColorFactor', 'metallicFactor', 'roughnessFactor', 'alphaMode', 'doubleSided'):
                assert np.array_equal(getattr(mesh.visual.material, key), getattr(reference, key)), (name, key)
            dimensions[name] = [round(float(x), 6) for x in np.ptp(mesh.vertices, axis=0) * 1000]
        else:
            assert np.array_equal(original.vertices, mesh.vertices), name
            assert np.array_equal(original.faces, mesh.faces), name
            assert np.allclose(original.vertex_normals, mesh.vertex_normals, atol=1e-6), name
            a, b = original.visual.material, mesh.visual.material
            for key in ('baseColorFactor', 'metallicFactor', 'roughnessFactor', 'alphaMode', 'doubleSided'):
                assert np.array_equal(getattr(a, key), getattr(b, key)), (name, key)
            assert np.array_equal(original.visual.uv, mesh.visual.uv), name
            for key in ('baseColorTexture', 'normalTexture', 'metallicRoughnessTexture'):
                a_img, b_img = getattr(a, key), getattr(b, key)
                assert (a_img is None) == (b_img is None)
                if a_img is not None:
                    assert a_img.tobytes() == b_img.tobytes(), (name, key)
            protected += 1
    assert protected == 97
    shell = after.geometry[v20.v19.CHASSIS]
    xyz = np.ptp(shell.vertices, axis=0) * 1000
    assert np.allclose(xyz, [77.32, 163.96, 8.29], atol=.001)
    for name, x in [('v16_bottom_antenna_separator_L', -.0059), ('v16_bottom_antenna_separator_R', .0059)]:
        assert abs(after.geometry[name].bounds[:, 0].mean() - x) < 2e-7
    count = sum(len(g.faces) for g in after.geometry.values())
    assert count < 90000 and after_file.stat().st_size < 5 * 1024 * 1024
    root = Path(__file__).resolve().parents[1]
    manifest = json.loads((root / 'scripts/model-manifest-v9.json').read_text())
    black = root / 'app/src/main/assets/models/legion_y70_black.glb'
    assert hashlib.sha256(black.read_bytes()).hexdigest() == manifest[black.name]['sha256']
    return {'passed': True, 'version': 20, 'meshes': len(after.geometry), 'triangles': count,
            'protected_meshes_exact': protected, 'rounded_keys_envelope_xyz_mm': dimensions,
            'chassis_xyz_mm': [round(float(x), 6) for x in xyz],
            'usb_antenna_centres_mm': [-5.9, 5.9], 'bytes': after_file.stat().st_size,
            'sha256': hashlib.sha256(after_file.read_bytes()).hexdigest(),
            'physical_device_tested': False, 'hardware_fps_measured': False}


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('before', type=Path)
    p.add_argument('after', type=Path)
    p.add_argument('--report', type=Path)
    args = p.parse_args()
    output = json.dumps(check(args.before, args.after), indent=2) + '\n'
    print(output, end='')
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(output)
