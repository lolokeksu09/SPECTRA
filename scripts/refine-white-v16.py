#!/usr/bin/env python3
"""Refine the validated SPECTRA v15 white GLB to v16 (offline/atomic)."""
import hashlib, json, os, sys
from pathlib import Path
import numpy as np
import trimesh
from trimesh.visual.material import PBRMaterial
from trimesh.visual.texture import TextureVisuals

CHASSIS='000_aluminium_silver_white_chassis'
REAR='001_original_engraved_LEGION_pearlescent_white_back'
SLOT='geometry_82'
PRESERVE=(CHASSIS,SLOT,'022_top_right_deep_lens','061_LED_warm_diffuser',
          '018_primary_micro_reflection_white','033_bottom_micro_reflection_white')

def ribbed_ring(cx,cy,radii,z,minor):
    count=96; sides=6
    verts=[]; faces=[]
    for rad in radii:
        start=len(verts)
        for i in range(count):
            angle=2*np.pi*i/count
            for j in range(sides):
                cross=2*np.pi*j/sides
                r=rad+minor*np.cos(cross)
                verts.append((cx+r*np.cos(angle),cy+r*np.sin(angle),z+minor*np.sin(cross)))
        for i in range(count):
            for j in range(sides):
                a=start+i*sides+j
                b=start+((i+1)%count)*sides+j
                c=start+i*sides+(j+1)%sides
                d=start+((i+1)%count)*sides+(j+1)%sides
                faces.extend(((a,b,d),(a,d,c)))
    return trimesh.Trimesh(vertices=np.array(verts),faces=np.array(faces),process=False)

def refine(src):
    s=trimesh.load(src,force='scene',process=False)
    assert len(s.geometry)==95
    assert sum(len(g.faces) for g in s.geometry.values())==37516
    previous={n:s.geometry[n].vertices.copy() for n in PRESERVE}
    camera=[
        ('v16_primary_engraved_ridges',-.02251124,.06607206,np.linspace(.00741,.00823,6),.005610,.000014),
        ('v16_ultrawide_engraved_ridges',-.02251124,.04517886,np.linspace(.00741,.00823,6),.005610,.000014),
        ('v16_sensor_machined_ridges',-.00204648,.06596437,np.linspace(.00486,.00760,8),.005270,.000012),
        ('v16_flash_machined_ridges',-.00215419,.04517886,np.linspace(.00486,.00760,8),.005270,.000012)]
    for name,cx,cy,radii,z,minor in camera:
        mesh=ribbed_ring(cx,cy,radii,z,minor)
        assert mesh.is_watertight
        mesh.visual=TextureVisuals(material=PBRMaterial(
            name='White_v16_satin_machined_rim',baseColorFactor=np.uint8([219,223,227,255]),
            metallicFactor=.42,roughnessFactor=.39,doubleSided=True))
        s.add_geometry(mesh,geom_name=name,node_name=name)
    for x,name in ((-.0127,'v16_bottom_antenna_separator_L'),(.0082,'v16_bottom_antenna_separator_R')):
        mesh=trimesh.creation.box(extents=(.00045,.000065,.0077))
        mesh.apply_translation((x,-.082004,0))
        mesh.visual=TextureVisuals(material=PBRMaterial(
            name='White_v16_lower_antenna_pearl',baseColorFactor=np.uint8([224,226,228,255]),
            metallicFactor=0.,roughnessFactor=.85,doubleSided=True))
        s.add_geometry(mesh,geom_name=name,node_name=name)
    face=s.geometry['009_camera_island_flush_face'].visual.material
    face.baseColorFactor=np.uint8([226,229,232,255]);face.metallicFactor=.26;face.roughnessFactor=.64
    edge=s.geometry['008_island_upper_satin_white'].visual.material
    edge.baseColorFactor=np.uint8([226,230,234,255]);edge.metallicFactor=.34;edge.roughnessFactor=.53
    assert len(s.geometry)==101
    for name,v in previous.items():assert np.array_equal(s.geometry[name].vertices,v),name
    return s

def main(file):
    source=Path(file)
    if not source.is_file():raise FileNotFoundError(source)
    scene=refine(source)
    tmp=source.with_suffix('.v16-pending.glb')
    try:
        scene.export(tmp,file_type='glb')
        read=trimesh.load(tmp,force='scene',process=False)
        triangles=sum(len(g.faces) for g in read.geometry.values())
        assert len(read.geometry)==101 and triangles==69796
        assert tmp.stat().st_size<16*1024*1024
        assert read.geometry[REAR].visual.material.baseColorTexture.size==(1024,2048)
        shell=read.geometry[CHASSIS].copy()
        shell.merge_vertices()
        assert shell.is_watertight
        width=np.ptp(read.geometry[SLOT].vertices[:,0]);height=np.ptp(read.geometry[SLOT].vertices[:,2])
        assert .00118<width<.00120 and .00037<height<.00039
        for name in PRESERVE:assert np.allclose(scene.geometry[name].vertices,read.geometry[name].vertices,rtol=0,atol=2e-8)
        for name in ('v16_primary_engraved_ridges','v16_ultrawide_engraved_ridges',
                     'v16_sensor_machined_ridges','v16_flash_machined_ridges'):
            assert read.geometry[name].is_watertight
        os.replace(tmp,source)
    finally:
        if tmp.exists():tmp.unlink()
    print(json.dumps({'file':str(source),'meshes':101,'triangles':triangles,'bytes':source.stat().st_size,
                      'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'old_holes_preserved':True}))

if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('Usage: refine-white-v16.py white-v15.glb')
    main(sys.argv[1])
