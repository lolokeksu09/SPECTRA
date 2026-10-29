#!/usr/bin/env python3
"""Tune camera-ring PBR response to SPECTRA's fixed Filament lighting."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import numpy as np
import trimesh

spec=importlib.util.spec_from_file_location('v18',Path(__file__).with_name('refine-white-v18.py'))
v18=importlib.util.module_from_spec(spec);spec.loader.exec_module(v18)
RIMS=('v16_primary_engraved_ridges','v16_ultrawide_engraved_ridges',
      'v16_sensor_machined_ridges','v16_flash_machined_ridges',
      'HQ10_main_hairline_precision_ring','HQ10_ultrawide_hairline_precision_ring',
      'HQ10_flicker_sensor_hairline_precision_ring','HQ10_flash_hairline_precision_ring',
      'V4_secondary_optic_and_flash_concentric_grooves')
RIGHT=('019_top_right_broad_matte_silver_recess','020_top_right_inner_satin_ring',
       '058_lower_right_satin_silver_light_flat_ring')
CHANGED=set(RIMS+RIGHT)

def main(source,target):
    scene=trimesh.load(source,force='scene',process=False)
    assert len(scene.geometry)==99 and sum(len(m.faces) for m in scene.geometry.values())==88012
    for name in RIMS+RIGHT:
        m=scene.geometry[name].visual.material.copy()
        if name in RIMS:
            m.name='White_v23_soft_machined_silver'
            m.baseColorFactor=np.uint8([160,164,168,255]);m.metallicFactor=.30;m.roughnessFactor=.58
        else:
            m.name='White_v23_flat_silver_ring'
            m.baseColorFactor=np.uint8([162,166,170,255]);m.metallicFactor=.15;m.roughnessFactor=.68
        scene.geometry[name].visual.material=m
    for mesh in scene.geometry.values():mesh.vertex_normals=mesh.vertex_normals
    target.parent.mkdir(parents=True,exist_ok=True);pending=target.with_suffix('.v23-pending.glb')
    try:
        scene.export(pending,file_type='glb');v18.explicit_tangents(pending)
        assert pending.stat().st_size<5*1024*1024
        os.replace(pending,target)
    finally:pending.unlink(missing_ok=True)
    print(json.dumps({'version':23,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'bytes':target.stat().st_size}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('target',type=Path)
    a=p.parse_args();main(a.source,a.target)
