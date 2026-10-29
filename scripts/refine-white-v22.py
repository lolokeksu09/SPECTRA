#!/usr/bin/env python3
"""Differentiate the raised left optics and near-flush right elements on v21."""
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
PLATE_Z=.004927
LEFT_SCALE=1.35
RIGHT_SCALE=.20
LEFT={name for name in (
    'HQ10_main_hairline_precision_ring','HQ10_ultrawide_hairline_precision_ring',
    'V4_pair_of_primary_precision_ring_grooves','v16_primary_engraved_ridges','v16_ultrawide_engraved_ridges')}
RIGHT={'HQ10_flash_hairline_precision_ring','HQ10_flicker_sensor_hairline_precision_ring',
    'V4_secondary_optic_and_flash_concentric_grooves','v16_flash_machined_ridges','v16_sensor_machined_ridges'}
MIXED='V4_cameras_micro_cyan_optical_glints'

def side(name):
    try: idx=int(name[:3])
    except ValueError: idx=-1
    if name in LEFT or 10<=idx<=18 or 25<=idx<=57:return 'left'
    if name in RIGHT or 19<=idx<=24 or 58<=idx<=63:return 'right'
    if name==MIXED:return 'mixed'
    return None

def refine(source,target):
    scene=trimesh.load(source,force='scene',process=False)
    assert len(scene.geometry)==99 and sum(len(g.faces) for g in scene.geometry.values())==88012
    for name,mesh in scene.geometry.items():
        category=side(name)
        if category is None:continue
        vertices=mesh.vertices.copy()
        scale=(np.where(vertices[:,0]<-.012,LEFT_SCALE,RIGHT_SCALE) if category=='mixed'
               else LEFT_SCALE if category=='left' else RIGHT_SCALE)
        vertices[:,2]=PLATE_Z+(vertices[:,2]-PLATE_Z)*scale
        mesh.vertices=vertices
        mesh.vertex_normals=mesh.vertex_normals
    for mesh in scene.geometry.values(): mesh.vertex_normals=mesh.vertex_normals
    target.parent.mkdir(parents=True,exist_ok=True)
    pending=target.with_suffix('.v22-pending.glb')
    try:
        scene.export(pending,file_type='glb');v18.explicit_tangents(pending)
        assert pending.stat().st_size<5*1024*1024
        os.replace(pending,target)
    finally:pending.unlink(missing_ok=True)
    print(json.dumps({'version':22,'meshes':len(scene.geometry),'triangles':sum(len(g.faces) for g in scene.geometry.values()),
       'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('target',type=Path)
    a=p.parse_args();refine(a.source,a.target)
