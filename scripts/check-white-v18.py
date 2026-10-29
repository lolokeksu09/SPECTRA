#!/usr/bin/env python3
"""Regression contracts across packaged v17 -> v18, including protected hardware and PBR."""
import argparse
import hashlib
import importlib.util
import json
import struct
from pathlib import Path
import numpy as np
import trimesh

spec = importlib.util.spec_from_file_location('v18', Path(__file__).with_name('refine-white-v18.py'))
v18 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v18)


def glb_json(path):
    raw = path.read_bytes()
    assert raw[:4] == b'glTF' and struct.unpack_from('<II', raw, 4) == (2, len(raw))
    count, kind = struct.unpack_from('<I4s', raw, 12)
    assert kind == b'JSON'
    return json.loads(raw[20:20 + count])


def check(before_path, after_path):
    before = trimesh.load(before_path, force='scene', process=False)
    after = trimesh.load(after_path, force='scene', process=False)
    assert set(before.geometry) == set(after.geometry) and len(after.geometry) == 101
    protected = 0
    for name, original in before.geometry.items():
        updated = after.geometry[name]
        if name not in (*v18.OPTICS, *v18.DEPTH_ONLY):
            assert np.array_equal(original.vertices, updated.vertices), name
            assert np.array_equal(original.faces, updated.faces), name
            assert np.allclose(original.vertex_normals, updated.vertex_normals, atol=1e-6), name
            protected += 1
        assert np.isfinite(updated.vertices).all() and np.isfinite(updated.vertex_normals).all(), name
        assert np.allclose(np.linalg.norm(updated.vertex_normals, axis=1), 1., atol=.002), name
        assert updated.faces.min() >= 0 and updated.faces.max() < len(updated.vertices), name
        assert np.min(updated.area_faces) > 1e-15, name
    for name in v18.OPTICS:
        g = after.geometry[name]
        assert np.min(g.face_normals[:, 2]) > 0
        assert np.min(np.einsum('fij,fj->fi', g.vertex_normals[g.faces], g.face_normals)) > .90
    # Exact chassis dimensions, excluding camera protrusion and side buttons.
    shell = after.geometry[v18.CHASSIS]
    dims = np.ptp(shell.vertices, axis=0) * 1000
    assert np.allclose(dims, [77.32, 163.96, 8.29], atol=.001), dims
    hard = shell.vertex_normals[shell.faces]
    assert np.min(np.sum(hard * shell.face_normals[:, None, :], axis=2)) > .995
    welded = shell.copy()
    welded.merge_vertices()
    assert welded.is_watertight
    assert np.array_equal(before.geometry[v18.CHASSIS].visual.material.baseColorFactor,
                          shell.visual.material.baseColorFactor)
    assert shell.visual.material.metallicFactor == before.geometry[v18.CHASSIS].visual.material.metallicFactor
    assert shell.visual.material.roughnessFactor == before.geometry[v18.CHASSIS].visual.material.roughnessFactor
    rear = after.geometry[v18.REAR]
    assert np.array_equal(rear.visual.uv, before.geometry[v18.REAR].visual.uv)
    assert rear.visual.material.baseColorTexture.tobytes() == before.geometry[v18.REAR].visual.material.baseColorTexture.tobytes()
    for name, expected in (('v16_bottom_antenna_separator_L', -.0059),
                           ('v16_bottom_antenna_separator_R', .0059)):
        assert abs(after.geometry[name].bounds[:, 0].mean() - expected) < 2e-7
        assert np.max(abs(after.geometry[name].visual.material.baseColorFactor[:3].astype(int) -
                          shell.visual.material.baseColorFactor[:3].astype(int))) <= 9
    doc = glb_json(after_path)
    assert not doc.get('extensionsRequired'), 'Only core glTF PBR is required'
    assert all('uri' not in b for b in doc['buffers'])
    assert all('bufferView' in i for i in doc['images'])
    for m in doc['materials']:
        pbr = m.get('pbrMetallicRoughness', {})
        assert 0 <= pbr.get('metallicFactor', 1) <= 1
        assert 0 <= pbr.get('roughnessFactor', 1) <= 1
        for channel in pbr.get('baseColorFactor', [1, 1, 1, 1]):
            assert 0 <= channel <= 1
    primitives = [p for m in doc['meshes'] for p in m['primitives']]
    for p in primitives:
        assert 'NORMAL' in p['attributes']
        m = doc['materials'][p['material']]
        pbr = m.get('pbrMetallicRoughness', {})
        if 'normalTexture' in m or 'baseColorTexture' in pbr or 'metallicRoughnessTexture' in pbr:
            assert 'TEXCOORD_0' in p['attributes']
        if 'normalTexture' in m:
            assert 'TANGENT' in p['attributes']
    triangles = sum(len(g.faces) for g in after.geometry.values())
    assert triangles < 90000 and len(primitives) == 101 and after_path.stat().st_size < 5 * 1024 * 1024
    # The v9 source files and render/lighting/gesture code must remain untouched by this patch.
    root = Path(__file__).resolve().parents[1]
    manifest = json.loads((root / 'scripts/model-manifest-v9.json').read_text())
    for name, info in manifest.items():
        # During packaging the white path intentionally holds the v18 export; black must stay exact.
        if name.endswith('black.glb'):
            assert hashlib.sha256((root / 'app/src/main/assets/models' / name).read_bytes()).hexdigest() == info['sha256']
    return {'passed': True, 'protected_meshes_exact': protected, 'chassis_dimensions_mm': dims.tolist(),
            'meshes': len(primitives), 'triangles_before': 69796, 'triangles_after': triangles,
            'bytes': after_path.stat().st_size, 'sha256': hashlib.sha256(after_path.read_bytes()).hexdigest(),
            'rear_logo_pixels_exact': True, 'antenna_centres_mm': [-5.9, 5.9],
            'hardware_fps_measured': False, 'physical_device_tested': False}


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('before', type=Path)
    p.add_argument('after', type=Path)
    p.add_argument('--report', type=Path)
    a = p.parse_args()
    result = check(a.before, a.after)
    text = json.dumps(result, indent=2) + '\n'
    print(text, end='')
    if a.report:
        a.report.parent.mkdir(parents=True, exist_ok=True)
        a.report.write_text(text)
