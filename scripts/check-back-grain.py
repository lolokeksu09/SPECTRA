#!/usr/bin/env python3
"""Verify only the rear normal texture changes in a grain refinement."""
import argparse, hashlib, json
from pathlib import Path
import numpy as np
import trimesh
REAR='001_original_engraved_LEGION_pearlescent_white_back'
def check(src, dst):
    before=trimesh.load(src,force='scene',process=False)
    after=trimesh.load(dst,force='scene',process=False)
    assert set(before.geometry)==set(after.geometry) and len(before.geometry)==99
    for name, a in before.geometry.items():
        b=after.geometry[name]
        assert np.array_equal(a.vertices,b.vertices) and np.array_equal(a.faces,b.faces), name
        assert np.array_equal(a.visual.uv,b.visual.uv), name
        assert np.allclose(a.vertex_normals,b.vertex_normals,atol=1e-6),name
        am,bm=a.visual.material,b.visual.material
        for attr in ('baseColorFactor','metallicFactor','roughnessFactor'):
            assert np.array_equal(getattr(am,attr),getattr(bm,attr)), (name,attr)
        for attr in ('baseColorTexture','metallicRoughnessTexture','normalTexture'):
            x,y=getattr(am,attr),getattr(bm,attr)
            if name==REAR and attr=='normalTexture':
                assert x is not None and y is not None and x.size==y.size
                assert not np.array_equal(np.asarray(x),np.asarray(y))
            else:
                assert (x is None)==(y is None), (name,attr)
                if x is not None: assert np.array_equal(np.asarray(x),np.asarray(y)),(name,attr)
    return {'passed':True,'meshes':len(after.geometry),'triangles':sum(len(x.faces) for x in after.geometry.values()),'sha256':hashlib.sha256(dst.read_bytes()).hexdigest(),'physical_device_tested':False}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('before',type=Path);p.add_argument('after',type=Path);p.add_argument('--report',type=Path);a=p.parse_args()
    output=json.dumps(check(a.before,a.after),indent=2)+'\n';print(output,end='')
    if a.report: a.report.write_text(output)
