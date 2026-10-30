#!/usr/bin/env python3
"""Add fine relief only to the existing rear normal map, preserving print and geometry."""
import argparse
import importlib.util
import os
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter
import trimesh

spec = importlib.util.spec_from_file_location('v18', Path(__file__).with_name('refine-white-v18.py'))
v18 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v18)
REAR = '001_original_engraved_LEGION_pearlescent_white_back'

def refine(source, target, finish):
    scene = trimesh.load(source, force='scene', process=False)
    mat = scene.geometry[REAR].visual.material.copy()
    original = np.asarray(mat.normalTexture.convert('RGB'), dtype=np.float32)
    height, width = original.shape[:2]
    rng = np.random.default_rng(20261030)
    # Deterministic microscopic relief; independent from the printed base colour.
    noise = rng.integers(0, 256, (height, width), dtype=np.uint8)
    field = np.asarray(Image.fromarray(noise).filter(ImageFilter.GaussianBlur(.65)), dtype=np.float32) / 255
    dx = (np.roll(field, -1, axis=1) - np.roll(field, 1, axis=1))
    dy = (np.roll(field, -1, axis=0) - np.roll(field, 1, axis=0))
    strength = .28 if finish == 'white' else .46
    normals = original / 127.5 - 1.0
    normals[:, :, 0] -= strength * dx
    normals[:, :, 1] -= strength * dy
    normals /= np.linalg.norm(normals, axis=2, keepdims=True)
    pixels = np.clip(np.rint((normals + 1) * 127.5), 0, 255).astype(np.uint8)
    mat.normalTexture = Image.fromarray(pixels, 'RGB')
    mat.name = f'{finish}_fine_grain_rear'
    scene.geometry[REAR].visual.material = mat
    for mesh in scene.geometry.values():
        mesh.vertex_normals = mesh.vertex_normals
    pending = target.with_suffix('.grain-pending.glb')
    try:
        scene.export(pending, file_type='glb')
        v18.explicit_tangents(pending)
        os.replace(pending, target)
    finally:
        pending.unlink(missing_ok=True)

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('source', type=Path); p.add_argument('target', type=Path)
    p.add_argument('finish', choices=('white', 'black'))
    a = p.parse_args(); refine(a.source, a.target, a.finish)
