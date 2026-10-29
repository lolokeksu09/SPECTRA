#!/usr/bin/env python3
"""Round the two remaining white side keys using the existing v19 rocker builder.
Only the power/smart-key bodies and their obsolete box highlight overlays change.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import numpy as np
import trimesh
from trimesh.visual.texture import TextureVisuals

spec = importlib.util.spec_from_file_location('v19', Path(__file__).with_name('refine-white-v19.py'))
v19 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v19)
KEYS = {'064_power_button': '066_power_button_satin_highlight',
        'V4_dedicated_smart_key': 'V4_smart_key_recess_outline'}


def envelope(scene, name):
    return np.array([np.minimum(scene.geometry[name].bounds[0], scene.geometry[KEYS[name]].bounds[0]),
                     np.maximum(scene.geometry[name].bounds[1], scene.geometry[KEYS[name]].bounds[1])])


def refine(source):
    scene = trimesh.load(source, force='scene', process=False)
    assert len(scene.geometry) == 101 and sum(len(g.faces) for g in scene.geometry.values()) == 84092
    material = scene.geometry[v19.VOLUME].visual.material.copy()
    material.name = 'White_v20_matching_satin_single_keys'
    for name, overlay in KEYS.items():
        bounds = envelope(scene, name)
        mirrored = bounds[:, 0].mean() > 0
        local = bounds.copy()
        if mirrored:
            local[:, 0] = -bounds[::-1, 0]
        body, _ = v19.rocker(local, groove_depth=0.)
        if mirrored:
            body.apply_transform(np.diag([-1., 1., 1., 1.]))
        assert np.allclose(body.bounds, bounds, atol=2e-8)
        body.visual = TextureVisuals(material=material.copy())
        scene.geometry[name] = body
        # Rounded geometry now produces the highlights; old floating box decals are redundant.
        overlay_nodes = list(scene.graph.geometry_nodes[overlay])
        scene.delete_geometry(overlay)
        for node in overlay_nodes:
            scene.graph.transforms.remove_node(node)
    for mesh in scene.geometry.values():
        mesh.vertex_normals = mesh.vertex_normals
    return scene


def main(source, target):
    scene = refine(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    pending = target.with_suffix('.v20-pending.glb')
    try:
        scene.export(pending, file_type='glb')
        spec = importlib.util.spec_from_file_location('v18', Path(__file__).with_name('refine-white-v18.py'))
        v18 = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(v18)
        v18.explicit_tangents(pending)
        assert pending.stat().st_size < 5 * 1024 * 1024
        os.replace(pending, target)
    finally:
        pending.unlink(missing_ok=True)
    print(json.dumps({'version': 20, 'meshes': len(scene.geometry),
        'triangles': sum(len(g.faces) for g in scene.geometry.values()), 'bytes': target.stat().st_size,
        'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
        'rounded_single_keys': list(KEYS), 'volume_rocker_and_antenna_geometry': 'unchanged from v19'}))


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('source', type=Path)
    p.add_argument('target', type=Path)
    args = p.parse_args()
    main(args.source, args.target)
