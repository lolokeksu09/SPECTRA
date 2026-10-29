#!/usr/bin/env python3
"""Check Black v2 preserves Black v1 geometry and the existing rear print."""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
import trimesh

REAR = '001_original_engraved_LEGION_pearlescent_white_back'

def check(before, after):
    old = trimesh.load(before, force='scene', process=False)
    new = trimesh.load(after, force='scene', process=False)
    assert len(old.geometry) == len(new.geometry) == 99
    assert set(old.geometry) == set(new.geometry)
    for name, mesh in old.geometry.items():
        updated = new.geometry[name]
        assert np.array_equal(mesh.vertices, updated.vertices), name
        assert np.array_equal(mesh.faces, updated.faces), name
        assert np.array_equal(mesh.visual.uv, updated.visual.uv), name
        assert np.allclose(mesh.vertex_normals, updated.vertex_normals, atol=1e-6), name
        assert np.isfinite(updated.vertex_normals).all(), name
    src = np.asarray(old.geometry[REAR].visual.material.baseColorTexture.convert('RGBA'))
    dst = np.asarray(new.geometry[REAR].visual.material.baseColorTexture.convert('RGBA'))
    assert np.array_equal(dst[:, :, :3], np.clip(np.rint(src[:, :, :3].astype(np.float32) * 1.55), 0, 255).astype(np.uint8))
    assert np.array_equal(src[:, :, 3], dst[:, :, 3])
    chassis = new.geometry['000_aluminium_silver_white_chassis']
    assert np.allclose(np.ptp(chassis.vertices, axis=0) * 1000, [77.32, 163.96, 8.29], atol=.001)
    for name, x in [('v16_bottom_antenna_separator_L', -.0059), ('v16_bottom_antenna_separator_R', .0059)]:
        assert abs(new.geometry[name].bounds[:, 0].mean() - x) < 2e-7
    assert after.stat().st_size < 7 * 1024 * 1024
    return {'passed': True, 'version': 'black-v2', 'shared_unchanged_meshes': 99,
            'printed_texture_uniformly_brightened': True,
            'triangles': sum(len(g.faces) for g in new.geometry.values()),
            'sha256': hashlib.sha256(after.read_bytes()).hexdigest(), 'physical_device_tested': False}

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('before', type=Path)
    p.add_argument('after', type=Path)
    p.add_argument('--report', type=Path)
    a = p.parse_args()
    output = json.dumps(check(a.before, a.after), indent=2) + '\n'
    print(output, end='')
    if a.report:
        a.report.parent.mkdir(parents=True, exist_ok=True)
        a.report.write_text(output)
