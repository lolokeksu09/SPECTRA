#!/usr/bin/env python3
"""Render the exported GLBs in OpenGL (Mesa EGL), identical cameras and lights.
These are real geometry/PBR renders, not Filament screenshots or generated images.
"""
import os
os.environ.setdefault('PYOPENGL_PLATFORM', 'egl')
import argparse
import json
import time
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
import pyrender
import trimesh


def camera_pose(eye, target, up=(0., 1., 0.)):
    z = np.asarray(eye) - np.asarray(target)
    z /= np.linalg.norm(z)
    x = np.cross(up, z)
    x /= np.linalg.norm(x)
    y = np.cross(z, x)
    pose = np.eye(4)
    pose[:3, :3] = np.column_stack((x, y, z))
    pose[:3, 3] = eye
    return pose


def render(glb, output):
    source = trimesh.load(glb, force='scene', process=False)
    presets = {
        'rear': ((.050, -.024, .260), (0., 0., 0.), .097, (0., 1., 0.)),
        'cameras': ((-.007, .049, .130), (-.013, .056, .005), .029, (0., 1., 0.)),
        'bottom': ((.004, -.160, .033), (0., -.081, 0.), .046, (0., 0., 1.)),
        'top': ((-.004, .160, .031), (0., .081, 0.), .046, (0., 0., 1.)),
    }
    renderer = pyrender.OffscreenRenderer(960, 960)
    stats = {}
    try:
        for name, (eye, target, mag, up) in presets.items():
            scene = pyrender.Scene.from_trimesh_scene(source, bg_color=[.035, .045, .06, 1.],
                                                      ambient_light=[.48, .48, .48])
            pose = camera_pose(eye, target, up)
            scene.add(pyrender.OrthographicCamera(xmag=mag, ymag=mag, znear=.001, zfar=2), pose=pose)
            # Identical direct specular lighting for both exports. No artificial reflection cards.
            light = pose.copy()
            light[:3, :3] = camera_pose(np.array(eye) + [0., .03, .04], target, up)[:3, :3]
            scene.add(pyrender.DirectionalLight(color=np.ones(3), intensity=2.), pose=light)
            start = time.perf_counter()
            color, depth = renderer.render(scene)
            ms = (time.perf_counter() - start) * 1000
            assert (depth > 0).sum() > 10000
            Image.fromarray(color).save(output / f'{glb.stem}-{name}.png')
            stats[name] = {'software_render_ms': round(ms, 2), 'visible_pixels': int((depth > 0).sum())}
    finally:
        renderer.delete()
    return stats


def main(before, after, output):
    output.mkdir(parents=True, exist_ok=True)
    stats = {p.stem: render(p, output) for p in (before, after)}
    for shot in ('rear', 'cameras', 'bottom', 'top'):
        sheet = Image.new('RGB', (1920, 1024), '#141b23')
        d = ImageDraw.Draw(sheet)
        for index, (p, label) in enumerate(((before, 'WHITE v17 / BEFORE'), (after, 'WHITE v18 / AFTER'))):
            sheet.paste(Image.open(output / f'{p.stem}-{shot}.png').convert('RGB'), (index * 960, 64))
            d.text((index * 960 + 26, 22), f'{label} / {shot.upper()}', fill='white', font_size=24)
        sheet.save(output / f'white-v17-v18-{shot}.jpg', quality=94)
    (output / 'opengl-render-metrics.json').write_text(json.dumps(stats, indent=2) + '\n')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('before', type=Path)
    p.add_argument('after', type=Path)
    p.add_argument('output', type=Path)
    a = p.parse_args()
    main(a.before, a.after, a.output)
