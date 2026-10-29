#!/usr/bin/env python3
"""Verify shared corrected geometry and preserved source branding in the exported black asset."""
import argparse,hashlib,json
from pathlib import Path
import numpy as np
import trimesh
def check(white_path,source_path,target_path):
    w=trimesh.load(white_path,force='scene',process=False)
    s=trimesh.load(source_path,force='scene',process=False)
    b=trimesh.load(target_path,force='scene',process=False)
    assert len(w.geometry)==len(b.geometry)==99 and set(w.geometry)==set(b.geometry)
    for name,wm in w.geometry.items():
        bm=b.geometry[name]
        assert np.array_equal(wm.vertices,bm.vertices),name
        assert np.array_equal(wm.faces,bm.faces),name
        assert np.allclose(wm.vertex_normals,bm.vertex_normals,atol=1e-6),name
        assert np.array_equal(wm.visual.uv,bm.visual.uv),name
        assert np.isfinite(bm.vertex_normals).all() and np.allclose(np.linalg.norm(bm.vertex_normals,axis=1),1,atol=.002),name
    rear='001_original_engraved_LEGION_pearlescent_white_back'
    assert b.geometry[rear].visual.material.baseColorTexture.tobytes()==s.geometry[rear].visual.material.baseColorTexture.tobytes()
    for n in ('004_front_glass_with_photo_reference_LEGION_wallpaper','V5_TOP_Dolby_Atmos_print','geometry_92'):
        assert b.geometry[n].visual.material.baseColorTexture.tobytes()==s.geometry[n].visual.material.baseColorTexture.tobytes(),n
    for n in ('013_primary_black_coated_face','028_bottom_black_coated_face'):
        assert b.geometry[n].visual.material.baseColorTexture.tobytes()==w.geometry[n].visual.material.baseColorTexture.tobytes()
    chassis=b.geometry['000_aluminium_silver_white_chassis']
    assert np.allclose(np.ptp(chassis.vertices,axis=0)*1000,[77.32,163.96,8.29],atol=.001)
    assert max(chassis.visual.material.baseColorFactor[:3])<80
    for n,x in [('v16_bottom_antenna_separator_L',-.0059),('v16_bottom_antenna_separator_R',.0059)]:
        assert abs(b.geometry[n].bounds[:,0].mean()-x)<2e-7
    assert sum(len(g.faces) for g in b.geometry.values())<90000 and target_path.stat().st_size<7*1024*1024
    return {'passed':True,'version':'black-v1','same_geometry_meshes':99,'source_branding_preserved':True,
            'triangles':sum(len(g.faces) for g in b.geometry.values()),'bytes':target_path.stat().st_size,
            'sha256':hashlib.sha256(target_path.read_bytes()).hexdigest(),'physical_device_tested':False}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('white',type=Path);p.add_argument('source',type=Path);p.add_argument('target',type=Path);p.add_argument('--report',type=Path)
    a=p.parse_args();out=json.dumps(check(a.white,a.source,a.target),indent=2)+'\n';print(out,end='')
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(out)
