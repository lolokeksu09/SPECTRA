#!/usr/bin/env python3
"""Refine the Black v1 finish while retaining all shared handset geometry."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path

import numpy as np
import trimesh
from PIL import Image

spec = importlib.util.spec_from_file_location('v18', Path(__file__).with_name('refine-white-v18.py'))
v18 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v18)
REAR = '001_original_engraved_LEGION_pearlescent_white_back'
PLATE = '009_camera_island_flush_face'
RIDGES = {'v16_primary_engraved_ridges', 'v16_ultrawide_engraved_ridges',
          'v16_sensor_machined_ridges', 'v16_flash_machined_ridges'}
HAIRLINES = {'HQ10_main_hairline_precision_ring', 'HQ10_ultrawide_hairline_precision_ring',
             'HQ10_flicker_sensor_hairline_precision_ring', 'HQ10_flash_hairline_precision_ring'}


def build(source, target):
    scene = trimesh.load(source, force='scene', process=False)
    rear = scene.geometry[REAR].visual.material.copy()
    # Lift the existing texture uniformly, preserving every printed mark and its position.
    # The original contains baked gradients, so do not flatten it into a solid colour.
    rgba = np.asarray(rear.baseColorTexture.convert('RGBA')).copy()
    rgba[:, :, :3] = np.clip(np.rint(rgba[:, :, :3].astype(np.float32) * 1.55), 0, 255).astype(np.uint8)
    rear.baseColorTexture = Image.fromarray(rgba, 'RGBA')
    rear.name = 'Black_v2_graphite_back_original_marks'
    scene.geometry[REAR].visual.material = rear

    plate = scene.geometry[PLATE].visual.material.copy()
    plate.name = 'Black_v2_satin_gunmetal_plate'
    plate.roughnessFactor = .76
    plate.metallicFactor = .18
    scene.geometry[PLATE].visual.material = plate
    for name in RIDGES | HAIRLINES:
        material = scene.geometry[name].visual.material.copy()
        material.name = 'Black_v2_soft_graphite_engraving'
        material.baseColorFactor = np.uint8([43, 45, 48, 255])
        material.metallicFactor = .05
        material.roughnessFactor = .9
        scene.geometry[name].visual.material = material
    for mesh in scene.geometry.values():
        mesh.vertex_normals = mesh.vertex_normals
    target.parent.mkdir(parents=True, exist_ok=True)
    pending = target.with_suffix('.black-v2-pending.glb')
    try:
        scene.export(pending, file_type='glb')
        v18.explicit_tangents(pending)
        if pending.stat().st_size >= 7 * 1024 * 1024:
            raise ValueError('Black v2 exceeds the asset size budget')
        os.replace(pending, target)
    finally:
        pending.unlink(missing_ok=True)
    print(json.dumps({'version': 'black-v2', 'bytes': target.stat().st_size,
                      'sha256': hashlib.sha256(target.read_bytes()).hexdigest()}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('target', type=Path)
    args = parser.parse_args()
    build(args.source, args.target)
