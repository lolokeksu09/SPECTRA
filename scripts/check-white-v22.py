#!/usr/bin/env python3
"""Verify depth changes and preserve all unrelated model assets."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import numpy as np
import trimesh
spec=importlib.util.spec_from_file_location('v22',Path(__file__).with_name('refine-white-v22.py'))
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)

def check(before_file,after_file):
    before=trimesh.load(before_file,force='scene',process=False)
    after=trimesh.load(after_file,force='scene',process=False)
    assert set(before.geometry)==set(after.geometry) and len(after.geometry)==99
    protected=changed=0
    for name,old in before.geometry.items():
        mesh=after.geometry[name];category=v.side(name)
        assert np.isfinite(mesh.vertices).all() and np.isfinite(mesh.vertex_normals).all(),name
        assert np.allclose(np.linalg.norm(mesh.vertex_normals,axis=1),1,atol=.002),name
        assert np.array_equal(mesh.faces,old.faces),name
        assert np.array_equal(mesh.visual.uv,old.visual.uv),name
        a,b=old.visual.material,mesh.visual.material
        for k in ('baseColorFactor','metallicFactor','roughnessFactor','alphaMode','doubleSided'):
            assert np.array_equal(getattr(a,k),getattr(b,k)),(name,k)
        for k in ('baseColorTexture','normalTexture','metallicRoughnessTexture'):
            x,y=getattr(a,k),getattr(b,k)
            assert (x is None)==(y is None)
            if x is not None:assert x.tobytes()==y.tobytes(),(name,k)
        if category:
            assert np.array_equal(old.vertices[:,:2],mesh.vertices[:,:2]),name
            scale=(np.where(old.vertices[:,0]<-.012,v.LEFT_SCALE,v.RIGHT_SCALE) if category=='mixed'
                   else v.LEFT_SCALE if category=='left' else v.RIGHT_SCALE)
            assert np.allclose(mesh.vertices[:,2],v.PLATE_Z+(old.vertices[:,2]-v.PLATE_Z)*scale,atol=1e-8),name
            assert np.min(mesh.area_faces)>1e-20,name
            changed+=1
        else:
            assert np.array_equal(old.vertices,mesh.vertices),name
            assert np.allclose(old.vertex_normals,mesh.vertex_normals,atol=1e-6),name
            protected+=1
    plane=after.geometry['009_camera_island_flush_face'].bounds[0,2]
    left=max(after.geometry[n].bounds[1,2] for n in ('013_primary_black_coated_face','028_bottom_black_coated_face'))
    right=max(after.geometry[n].bounds[1,2] for n in ('024_top_right_specular','063_LED_soft_glint','v16_flash_machined_ridges'))
    assert left-plane>.0009 and 0<right-plane<.00009
    assert np.allclose(np.ptp(after.geometry['000_aluminium_silver_white_chassis'].vertices,axis=0)*1000,[77.32,163.96,8.29],atol=.001)
    for n,x in [('v16_bottom_antenna_separator_L',-.0059),('v16_bottom_antenna_separator_R',.0059)]:
        assert abs(after.geometry[n].bounds[:,0].mean()-x)<2e-7
    triangles=sum(len(m.faces) for m in after.geometry.values());assert triangles<90000 and after_file.stat().st_size<5*1024*1024
    root=Path(__file__).resolve().parents[1];black=root/'app/src/main/assets/models/legion_y70_black.glb'
    assert hashlib.sha256(black.read_bytes()).hexdigest()==json.loads((root/'scripts/model-manifest-v9.json').read_text())[black.name]['sha256']
    return {'passed':True,'version':22,'protected_meshes':protected,'depth_changed_meshes':changed,
       'left_max_above_plate_mm':round(float((left-plane)*1000),4),'right_max_above_plate_mm':round(float((right-plane)*1000),4),
       'triangles':triangles,'sha256':hashlib.sha256(after_file.read_bytes()).hexdigest(),'physical_device_tested':False}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('before',type=Path);p.add_argument('after',type=Path);p.add_argument('--report',type=Path)
    a=p.parse_args();out=json.dumps(check(a.before,a.after),indent=2)+'\n';print(out,end='')
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(out)
