#!/usr/bin/env python3
"""Regression checks for the actual v18 -> v19 export, including seam visibility."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import numpy as np
import trimesh

spec = importlib.util.spec_from_file_location('v19', Path(__file__).with_name('refine-white-v19.py'))
v19 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v19)


def check(before_file, after_file):
    before = trimesh.load(before_file, force='scene', process=False)
    after = trimesh.load(after_file, force='scene', process=False)
    assert set(before.geometry) == set(after.geometry) and len(after.geometry) == 101
    protected = 0
    for name, original in before.geometry.items():
        mesh = after.geometry[name]
        assert np.isfinite(mesh.vertices).all() and np.isfinite(mesh.vertex_normals).all(), name
        assert np.min(mesh.area_faces) > 1e-16, (name, np.min(mesh.area_faces))
        assert np.allclose(np.linalg.norm(mesh.vertex_normals, axis=1), 1, atol=.002), name
        if name not in v19.CHANGED:
            assert np.array_equal(original.vertices, mesh.vertices), name
            assert np.array_equal(original.faces, mesh.faces), name
            assert np.allclose(original.vertex_normals, mesh.vertex_normals, atol=1e-6), name
            a, b = original.visual.material, mesh.visual.material
            for key in ('baseColorFactor', 'metallicFactor', 'roughnessFactor', 'alphaMode'):
                assert np.array_equal(getattr(a, key), getattr(b, key)), (name, key)
            for key in ('baseColorTexture', 'normalTexture', 'metallicRoughnessTexture'):
                a_img, b_img = getattr(a, key), getattr(b, key)
                assert (a_img is None) == (b_img is None)
                if a_img is not None:
                    assert a_img.tobytes() == b_img.tobytes(), (name, key)
            protected += 1
    shell = after.geometry[v19.CHASSIS]
    assert np.allclose(np.ptp(shell.vertices, axis=0) * 1000, [77.32, 163.96, 8.29], atol=.001)
    assert np.min(np.einsum('fij,fj->fi', shell.vertex_normals[shell.faces], shell.face_normals)) > .995
    body = after.geometry[v19.VOLUME]
    assert body.is_watertight and body.is_winding_consistent and body.volume > 0
    assert v19.one_connected(body), 'Volume + and - must share one connected body'
    expected = before.geometry[v19.VOLUME].bounds.copy()
    expected[0, 0] = before.geometry[v19.NOTCH].bounds[0, 0]
    assert np.allclose(body.bounds, expected, atol=2e-8), (body.bounds, expected)
    center_y = expected[:, 1].mean()
    centre = body.vertices[(np.abs(body.vertices[:, 1] - center_y) < 2e-8)]
    shoulders = body.vertices[(np.abs(np.abs(body.vertices[:, 1] - center_y) - .00030) < 2e-8)]
    assert len(centre) and len(shoulders)
    groove_depth = centre[:, 0].min() - shoulders[:, 0].min()
    assert .00011 < groove_depth < .00013, groove_depth
    for name in v19.BANDS:
        mesh = after.geometry[name]
        sign = -1 if 'left' in name else 1
        cy = v19.LOWER_Y if 'bottom' in name else before.geometry[name].bounds[:, 1].mean()
        assert abs(mesh.bounds[:, 1].mean() - cy) < 2e-8
        assert np.isclose(np.ptp(mesh.vertices[:, 1]), v19.BAND_WIDTH, atol=2e-8)
        assert np.min(mesh.face_normals[:, 0] * sign) > .1, name
        central = mesh.vertices[np.abs(mesh.vertices[:, 2]) < .001]
        assert len(central) and np.min(central[:, 0] * sign) > .038661, name
        assert np.ptp(mesh.vertices[:, 2]) > .0081, 'Insert must follow the side and both bevels'
        assert np.min(mesh.visual.material.baseColorFactor[:3]) > np.max(shell.visual.material.baseColorFactor[:3])
    for name, x in [('v16_bottom_antenna_separator_L', -.0059), ('v16_bottom_antenna_separator_R', .0059)]:
        assert np.array_equal(before.geometry[name].vertices, after.geometry[name].vertices)
        assert abs(after.geometry[name].bounds[:, 0].mean() - x) < 2e-7
    count = sum(len(g.faces) for g in after.geometry.values())
    assert count < 90000 and after_file.stat().st_size < 5 * 1024 * 1024
    manifest = json.loads((Path(__file__).resolve().parents[1] / 'scripts/model-manifest-v9.json').read_text())
    black = Path(__file__).resolve().parents[1] / 'app/src/main/assets/models/legion_y70_black.glb'
    assert hashlib.sha256(black.read_bytes()).hexdigest() == manifest[black.name]['sha256']
    return {'passed': True, 'version': 19, 'meshes': 101, 'triangles': count,
            'protected_meshes_exact': protected, 'rocker_connected': True,
            'rocker_groove_depth_mm': round(float(groove_depth * 1000), 4),
            'side_antenna_width_mm': .65, 'lower_side_antenna_y_mm': -41.,
            'side_antenna_placement': 'photo-derived approximation; upper centres retained',
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
