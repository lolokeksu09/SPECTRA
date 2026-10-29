#!/usr/bin/env python3
"""Check PBR refinements without touching geometry, UVs or texture images."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import numpy as np
import trimesh
spec=importlib.util.spec_from_file_location('v23',Path(__file__).with_name('refine-white-v23.py'))
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)

def check(src,dst):
    old=trimesh.load(src,force='scene',process=False);new=trimesh.load(dst,force='scene',process=False)
    assert set(old.geometry)==set(new.geometry) and len(new.geometry)==99
    for name,a in old.geometry.items():
        b=new.geometry[name];assert np.array_equal(a.vertices,b.vertices),name
        assert np.array_equal(a.faces,b.faces) and np.allclose(a.vertex_normals,b.vertex_normals,atol=1e-6),name
        assert np.array_equal(a.visual.uv,b.visual.uv),name
        for k in ('baseColorTexture','normalTexture','metallicRoughnessTexture'):
            x,y=getattr(a.visual.material,k),getattr(b.visual.material,k)
            assert (x is None)==(y is None)
            if x is not None:assert x.tobytes()==y.tobytes(),(name,k)
        if name not in v.CHANGED:
            for k in ('baseColorFactor','metallicFactor','roughnessFactor','alphaMode','doubleSided'):
                assert np.array_equal(getattr(a.visual.material,k),getattr(b.visual.material,k)),(name,k)
        else:
            assert b.visual.material.roughnessFactor>=.58 and max(b.visual.material.baseColorFactor[:3])<=170,name
    plane=new.geometry['009_camera_island_flush_face'].bounds[0,2]
    left=max(new.geometry[n].bounds[1,2] for n in ('013_primary_black_coated_face','028_bottom_black_coated_face'))
    right=max(new.geometry[n].bounds[1,2] for n in ('024_top_right_specular','063_LED_soft_glint','v16_flash_machined_ridges'))
    assert left-plane>.0009 and 0<right-plane<.00009
    assert np.allclose(np.ptp(new.geometry['000_aluminium_silver_white_chassis'].vertices,axis=0)*1000,[77.32,163.96,8.29],atol=.001)
    root=Path(__file__).resolve().parents[1];black=root/'app/src/main/assets/models/legion_y70_black.glb'
    assert hashlib.sha256(black.read_bytes()).hexdigest()==json.loads((root/'scripts/model-manifest-v9.json').read_text())[black.name]['sha256']
    assert dst.stat().st_size<5*1024*1024
    return {'passed':True,'version':23,'geometry_unchanged_meshes':99,'adjusted_materials':len(v.CHANGED),
        'triangles':sum(len(g.faces) for g in new.geometry.values()),'bytes':dst.stat().st_size,
        'sha256':hashlib.sha256(dst.read_bytes()).hexdigest(),'physical_device_tested':False}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('before',type=Path);p.add_argument('after',type=Path);p.add_argument('--report',type=Path)
    a=p.parse_args();out=json.dumps(check(a.before,a.after),indent=2)+'\n';print(out,end='')
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(out)
