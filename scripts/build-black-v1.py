#!/usr/bin/env python3
"""Retain the finished shared handset geometry and apply the original black finish."""
import argparse,hashlib,importlib.util,json,os
from pathlib import Path
import numpy as np
import trimesh
from trimesh.visual.material import PBRMaterial
from trimesh.visual.texture import TextureVisuals

spec=importlib.util.spec_from_file_location('v18',Path(__file__).with_name('refine-white-v18.py'))
v18=importlib.util.module_from_spec(spec);spec.loader.exec_module(v18)
REAR='001_original_engraved_LEGION_pearlescent_white_back'
PLATE='009_camera_island_flush_face'
OPTICS={'013_primary_black_coated_face','014_primary_dark_internal_vignette','015_primary_central_lens',
        '028_bottom_black_coated_face','029_bottom_dark_internal_vignette','030_bottom_central_lens'}
NEW_RINGS={'HQ10_main_hairline_precision_ring','HQ10_ultrawide_hairline_precision_ring',
           'HQ10_flicker_sensor_hairline_precision_ring','HQ10_flash_hairline_precision_ring',
           'v16_primary_engraved_ridges','v16_ultrawide_engraved_ridges',
           'v16_sensor_machined_ridges','v16_flash_machined_ridges'}
BANDS={'068_left_top_antenna_band','069_left_bottom_antenna_band','070_right_top_antenna_band',
       '071_right_bottom_antenna_band','v16_bottom_antenna_separator_L','v16_bottom_antenna_separator_R'}
def material(name,color,metal,rough):
    return PBRMaterial(name=name,baseColorFactor=color,metallicFactor=metal,roughnessFactor=rough,doubleSided=False)
def build(white_path,black_path,target):
    scene=trimesh.load(white_path,force='scene',process=False)
    black=trimesh.load(black_path,force='scene',process=False)
    assert len(scene.geometry)==99 and len(black.geometry)==91
    for name,mesh in scene.geometry.items():
        if name in OPTICS:continue  # Keep the corrected optical glass and the LYTIA marking.
        if name==PLATE:
            m=mesh.visual.material.copy();m.name='Black_v1_fine_grain_gunmetal_plate'
            m.baseColorFactor=np.uint8([82,84,86,255]);m.metallicFactor=.22;m.roughnessFactor=1.
        elif name==REAR:
            assert np.array_equal(mesh.visual.uv,black.geometry[name].visual.uv)
            m=black.geometry[name].visual.material.copy()
            m.name='Black_v1_textured_back_original_legion_lenovo'
            m.normalTexture=mesh.visual.material.normalTexture
            m.metallicRoughnessTexture=mesh.visual.material.metallicRoughnessTexture
            m.metallicFactor=0.;m.roughnessFactor=1.
        elif name in NEW_RINGS:
            m=material('Black_v1_machined_graphite_rings',[62,65,68,255],.3,.58)
        elif name in BANDS:
            m=material('Black_v1_integrated_antenna',[49,51,54,255],0.,.84)
        elif name=='067_volume_button_satin_highlight':
            m=material('Black_v1_volume_divider',[15,17,19,255],.03,.72)
        elif name in black.geometry:
            m=black.geometry[name].visual.material.copy()
            if m.baseColorTexture is not None:
                assert np.array_equal(mesh.visual.uv,black.geometry[name].visual.uv),name
        else:raise ValueError('Missing black material mapping: '+name)
        mesh.visual.material=m
    for mesh in scene.geometry.values():mesh.vertex_normals=mesh.vertex_normals
    target.parent.mkdir(parents=True,exist_ok=True);tmp=target.with_suffix('.black-v1-pending.glb')
    try:
        scene.export(tmp,file_type='glb');v18.explicit_tangents(tmp)
        assert tmp.stat().st_size<7*1024*1024
        os.replace(tmp,target)
    finally:tmp.unlink(missing_ok=True)
    print(json.dumps({'black_version':1,'meshes':len(scene.geometry),'triangles':sum(len(g.faces) for g in scene.geometry.values()),
       'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}))
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('white',type=Path);p.add_argument('black',type=Path);p.add_argument('target',type=Path)
    a=p.parse_args();build(a.white,a.black,a.target)
