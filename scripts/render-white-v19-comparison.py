#!/usr/bin/env python3
"""Actual exported GLB renders focused on side antenna inserts and the shared volume rocker."""
import os
os.environ.setdefault('PYOPENGL_PLATFORM', 'egl')
import argparse
import importlib.util
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import pyrender
import trimesh

spec = importlib.util.spec_from_file_location('renders', Path(__file__).with_name('render-white-comparison.py'))
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


def main(before, after, output):
    output.mkdir(parents=True, exist_ok=True)
    presets = {
        'button-side': ((-.30, .0, .023), (-.038, .0, 0.), .089),
        'opposite-side': ((.30, .0, .023), (.038, .0, 0.), .089),
        'volume-closeup': ((-.14, .04942, .006), (-.039, .04942, 0.), .0105),
        'lower-antenna': ((-.14, -.041, .006), (-.0387, -.041, 0.), .009),
    }
    renderer = pyrender.OffscreenRenderer(960, 960)
    try:
        for version, glb in [('v18', before), ('v19', after)]:
            src = trimesh.load(glb, force='scene', process=False)
            for shot, (eye, target, mag) in presets.items():
                scene = pyrender.Scene.from_trimesh_scene(src, bg_color=[.035, .045, .06, 1.],
                                                          ambient_light=[.48, .48, .48])
                pose = helper.camera_pose(eye, target)
                scene.add(pyrender.OrthographicCamera(xmag=mag, ymag=mag, znear=.001, zfar=2.), pose=pose)
                light = helper.camera_pose(np.array(eye) + [0., .03, .04], target)
                scene.add(pyrender.DirectionalLight(color=np.ones(3), intensity=2.), pose=light)
                color, depth = renderer.render(scene)
                assert (depth > 0).sum() > 10000
                Image.fromarray(color).save(output / f'white-{version}-{shot}.png')
        font = ImageFont.truetype('DejaVuSans.ttf', 24)
        for shot in presets:
            sheet = Image.new('RGB', (1920, 1024), '#141b23')
            draw = ImageDraw.Draw(sheet)
            for index, version in enumerate(('v18', 'v19')):
                sheet.paste(Image.open(output / f'white-{version}-{shot}.png'), (index * 960, 64))
                draw.text((index * 960 + 25, 20), f'WHITE {version} / {shot}', fill='white', font=font)
            sheet.save(output / f'white-v18-v19-{shot}.jpg', quality=94)
    finally:
        renderer.delete()


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('before', type=Path)
    p.add_argument('after', type=Path)
    p.add_argument('output', type=Path)
    args = p.parse_args()
    main(args.before, args.after, args.output)
