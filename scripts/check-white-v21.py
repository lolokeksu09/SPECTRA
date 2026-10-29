#!/usr/bin/env python3
"""Validate requested side changes and protect every other v20 mesh."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import numpy as np
import trimesh

spec = importlib.util.spec_from_file_location('v21', Path(__file__).with_name('refine-white-v21.py'))
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)

def check(before_path, after_path):
    before = trimesh.load(before_path, force='scene', process=False)
    after = trimesh.load(after_path, force='scene', process=False)
    assert set(before.geometry) == set(after.geometry) and len(after.geometry) == 99
    protected = 0
    for name, mesh in after.geometry.items():
        old = before.geometry[name]
        assert np.isfinite(mesh.vertices).all() and np.isfinite(mesh.vertex_normals).all(), name
        assert np.min(mesh.area_faces) > 1e-16, name
        assert np.allclose(np.linalg.norm(mesh.vertex_normals, axis=1), 1, atol=.002), name
        if name not in v.CHANGED:
            assert np.array_equal(old.vertices, mesh.vertices), name
            assert np.array_equal(old.faces, mesh.faces), name
            assert np.allclose(old.vertex_normals, mesh.vertex_normals, atol=1e-6), name
            assert np.array_equal(old.visual.uv, mesh.visual.uv), name
            a, b = old.visual.material, mesh.visual.material
            for key in ('baseColorFactor', 'metallicFactor', 'roughnessFactor', 'alphaMode', 'doubleSided'):
                assert np.array_equal(getattr(a,key),getattr(b,key)), (name,key)
            for key in ('baseColorTexture','normalTexture','metallicRoughnessTexture'):
                x,y = getattr(a,key),getattr(b,key)
                assert (x is None)==(y is None)
                if x is not None: assert x.tobytes()==y.tobytes(), (name,key)
            protected += 1
    assert protected == 91
    power = after.geometry[v.POWER]
    assert power.is_watertight and power.is_winding_consistent and power.volume > 0 and v.v19.one_connected(power)
    assert abs(np.ptp(power.vertices[:,1])-v.POWER_LENGTH)<2e-8
    assert np.allclose(power.bounds[:,1].mean(), v.POWER_CENTER, atol=2e-8)
    volume = after.geometry[v.v19.VOLUME]
    assert volume.is_watertight and volume.is_winding_consistent and volume.volume > 0 and v.v19.one_connected(volume)
    assert abs(np.ptp(volume.vertices[:,1])-v.VOLUME_LENGTH)<2e-8
    assert abs(volume.bounds[:,1].mean()-v.VOLUME_CENTER)<2e-8
    notch = after.geometry[v.v19.NOTCH]
    assert abs(notch.bounds[:,1].mean()-v.VOLUME_CENTER)<2e-8
    assert power.bounds[1,1] < volume.bounds[0,1]
    delta = after.geometry[v.SMART].vertices-before.geometry[v.SMART].vertices
    assert np.allclose(delta,[0,v.SMART_SHIFT,0],atol=2e-8)
    for name in v.BANDS:
        mesh, old = after.geometry[name], before.geometry[name]
        assert np.allclose(mesh.vertices-old.vertices,[0,v.UPPER_SHIFT if 'top' in name else 0,0],atol=2e-8),name
        assert np.array_equal(mesh.faces,old.faces),name
        assert np.array_equal(mesh.visual.material.baseColorFactor, v.BAND_COLOR),name
    shell = after.geometry[v.v19.CHASSIS]
    assert np.allclose(np.ptp(shell.vertices,axis=0)*1000,[77.32,163.96,8.29],atol=.001)
    for name,x in [('v16_bottom_antenna_separator_L',-.0059),('v16_bottom_antenna_separator_R',.0059)]:
        assert abs(after.geometry[name].bounds[:,0].mean()-x)<2e-7
    count=sum(len(g.faces) for g in after.geometry.values())
    assert count<90000 and after_path.stat().st_size<5*1024*1024
    root=Path(__file__).resolve().parents[1]
    black=root/'app/src/main/assets/models/legion_y70_black.glb'
    manifest=json.loads((root/'scripts/model-manifest-v9.json').read_text())
    assert hashlib.sha256(black.read_bytes()).hexdigest()==manifest[black.name]['sha256']
    return {'passed':True, 'version':21, 'protected_meshes':protected, 'triangles':count,
            'power_length_mm':v.POWER_LENGTH*1000, 'volume_length_mm':v.VOLUME_LENGTH*1000, 'smart_shift_mm':v.SMART_SHIFT*1000,
            'upper_side_seams_shift_mm':v.UPPER_SHIFT*1000, 'sha256':hashlib.sha256(after_path.read_bytes()).hexdigest(),
            'physical_device_tested':False, 'placement':'approximate from photos'}

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('before',type=Path);p.add_argument('after',type=Path);p.add_argument('--report',type=Path)
    a=p.parse_args(); output=json.dumps(check(a.before,a.after),indent=2)+'\n';print(output,end='')
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(output)
