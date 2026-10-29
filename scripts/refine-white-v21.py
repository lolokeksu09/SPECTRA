#!/usr/bin/env python3
"""Refine existing v20 side details using approximate proportions from real photographs."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import numpy as np
import trimesh
from trimesh.visual.material import PBRMaterial
from trimesh.visual.texture import TextureVisuals

def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name('refine-white-' + name + '.py'))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m

v19 = module('v19')
v18 = module('v18')
POWER = '064_power_button'
SMART = 'V4_dedicated_smart_key'
BANDS = v19.BANDS
CHANGED = {POWER, SMART, v19.VOLUME, v19.NOTCH, *BANDS}
POWER_LENGTH = .0104  # Approximate photo proportions, not measured CAD.
POWER_CENTER = .0134
VOLUME_LENGTH = .0195
VOLUME_CENTER = .0360
SMART_SHIFT = .0065   # Photo estimate, not a factory CAD measurement.
UPPER_SHIFT = -.004  # Photo estimate; lower side bands and bottom USB bands stay put.
BAND_COLOR = [128, 132, 137, 255]

def refine(source, target):
    scene = trimesh.load(source, force='scene', process=False)
    assert len(scene.geometry) == 99 and sum(len(g.faces) for g in scene.geometry.values()) == 88012
    old_volume = scene.geometry[v19.VOLUME]
    old_inset = scene.geometry[v19.NOTCH]
    bounds = old_volume.bounds.copy()
    bounds[:, 1] = [VOLUME_CENTER - VOLUME_LENGTH / 2, VOLUME_CENTER + VOLUME_LENGTH / 2]
    volume, inset = v19.rocker(bounds)
    volume.visual = TextureVisuals(material=old_volume.visual.material.copy())
    inset.visual = TextureVisuals(material=old_inset.visual.material.copy())
    scene.geometry[v19.VOLUME], scene.geometry[v19.NOTCH] = volume, inset
    old = scene.geometry[POWER]
    bounds = old.bounds.copy()
    center = POWER_CENTER
    bounds[:, 1] = [center - POWER_LENGTH / 2, center + POWER_LENGTH / 2]
    button, _ = v19.rocker(bounds, groove_depth=0.)
    button.visual = TextureVisuals(material=old.visual.material.copy())
    scene.geometry[POWER] = button
    scene.geometry[SMART].apply_translation([0, SMART_SHIFT, 0])
    for name in BANDS:
        mesh = scene.geometry[name]
        if 'top' in name:
            mesh.apply_translation([0, UPPER_SHIFT, 0])
        mesh.visual = TextureVisuals(material=PBRMaterial(name='White_v21_subtle_gray_antenna',
            baseColorFactor=BAND_COLOR, metallicFactor=0., roughnessFactor=.84, doubleSided=False))
    for mesh in scene.geometry.values():
        mesh.vertex_normals = mesh.vertex_normals
    target.parent.mkdir(parents=True, exist_ok=True)
    pending = target.with_suffix('.v21-pending.glb')
    try:
        scene.export(pending, file_type='glb')
        v18.explicit_tangents(pending)
        assert pending.stat().st_size < 5 * 1024 * 1024
        os.replace(pending, target)
    finally:
        pending.unlink(missing_ok=True)
    print(json.dumps({'version':21, 'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
                      'triangles':sum(len(g.faces) for g in scene.geometry.values()), 'bytes':target.stat().st_size}))

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('source', type=Path)
    p.add_argument('target', type=Path)
    args = p.parse_args()
    refine(args.source, args.target)
