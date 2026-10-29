#!/usr/bin/env python3
"""Generate the Ice Soul White v14 mobile GLB from SPECTRA's bundled v9 source.
The v9 source is validated before generation; only the white asset is replaced
for packaging. Brighter matte silver-gray rails, correct hard-edge normals, 2K textures and lens rings.
Offline at runtime, Python dependencies needed only by GitHub Actions.
"""
import argparse, hashlib, json, os
from pathlib import Path
import numpy as np
from PIL import Image, ImageFilter
import trimesh
from trimesh.visual.material import PBRMaterial
from trimesh.visual.texture import TextureVisuals

REAR='001_original_engraved_LEGION_pearlescent_white_back'
FRONT='004_front_glass_with_photo_reference_LEGION_wallpaper'
CHASSIS='000_aluminium_silver_white_chassis'
RAILS={
 'Satin frost-silver perimeter':((.56,.59,.63,1),.04,.76),
 'Subtle silver trim':((.62,.64,.68,1),.05,.71),
 'Side buttons pearly silver':((.60,.63,.67,1),.05,.69),
 'Brushed silver highlight':((.66,.68,.71,1),.06,.65),
 'Antenna subtly darker silver':((.50,.53,.57,1),.03,.78),
 'v5_WHITE_antenna_insulator':((.70,.73,.76,1),.00,.92),
 'WHITE_V4_dedicated_smart_key':((.58,.60,.64,1),.04,.72),
 'WHITE_V4_smart_key_recess_outline':((.45,.48,.51,1),.02,.82)
}

def rear_texture(img):
    raw=np.asarray(img.convert('RGB'),dtype=np.float32)
    h,w=raw.shape[:2]
    shade=np.mean(raw[:,int(w*.82):int(w*.91)],axis=1)
    neutral=.82*shade[:,None,:]+.18*np.array([242.,243.,244.])[None,None,:]
    neutral=np.broadcast_to(neutral,(h,w,3))
    y=np.arange(h)[:,None,None]
    a=np.clip((.378*h-y)/(.055*h),0,1)
    image=raw*(1-a)+neutral*a
    x=np.arange(w)[None,:,None]
    edge=np.clip((.068*w-x)/(.052*w),0,.97)
    edge=np.maximum(edge,np.clip((x-.932*w)/(.052*w),0,.97))
    image=image*(1-edge)+neutral*edge
    image=np.uint8(np.clip(image*.96+10,0,255))
    return Image.fromarray(image).resize((1024,2048),Image.Resampling.LANCZOS).filter(
        ImageFilter.UnsharpMask(radius=1.1,percent=105,threshold=3))

def precision_ring(cx,cy,r):
    n,k=192,10
    theta=np.arange(n)*2*np.pi/n
    phi=np.arange(k)*2*np.pi/k
    rr=r*.945+.000047*np.cos(phi)[None,:]
    vertices=np.column_stack((
        np.broadcast_to(cx+rr*np.cos(theta)[:,None],(n,k)).ravel(),
        np.broadcast_to(cy+rr*np.sin(theta)[:,None],(n,k)).ravel(),
        np.broadcast_to(.005205+.000047*np.sin(phi)[None,:],(n,k)).ravel()))
    faces=[]
    for i in range(n):
        for j in range(k):
            a=i*k+j;b=i*k+(j+1)%k;c=((i+1)%n)*k+j;d=((i+1)%n)*k+(j+1)%k
            faces.extend(((a,c,d),(a,d,b)))
    return trimesh.Trimesh(vertices=vertices,faces=np.array(faces),process=False)


def shrink_sim_adjacent_slot(scene):
    """Shrink only the small rounded slot immediately beside the SIM tray.

    Resize both levels of its watertight chassis recess and its dark inset.
    Keep the slot centered; SIM eject hole, tray, USB-C and speakers untouched.
    """
    chassis = scene.geometry[CHASSIS]
    inset = scene.geometry['geometry_82']
    bounds = inset.bounds.copy()
    width = bounds[1, 0] - bounds[0, 0]
    height = bounds[1, 2] - bounds[0, 2]
    assert .00160 < width < .00170 and .00045 < height < .00049
    center = float((bounds[0, 0] + bounds[1, 0]) / 2)
    assert abs(center - .01190) < .00001

    length_factor, thickness_factor = .85, .90
    vertices = chassis.vertices.copy()
    selected = (
        (vertices[:, 0] > .0109) & (vertices[:, 0] < .0129) &
        (vertices[:, 1] < -.0807) & (np.abs(vertices[:, 2]) < .00038)
    )
    # Exactly the two existing cutout loops; fail if a source update moves
    # these vertices instead of silently deforming nearby hardware.
    assert int(np.count_nonzero(selected)) == 104
    assert set(np.round(vertices[selected, 1], 5)) == {-.08198, -.08088}
    vertices[selected, 0] = center + (vertices[selected, 0] - center) * length_factor
    vertices[selected, 2] *= thickness_factor
    chassis.vertices = vertices
    assert chassis.is_watertight, "slot correction opened the chassis"

    dark = inset.vertices.copy()
    dark[:, 0] = center + (dark[:, 0] - center) * length_factor
    dark[:, 2] *= thickness_factor
    inset.vertices = dark
    updated = inset.bounds
    assert np.isclose((updated[1, 0] - updated[0, 0]) / width, length_factor, atol=.002)
    assert np.isclose((updated[1, 2] - updated[0, 2]) / height, thickness_factor, atol=.002)

def build(source,target):
    if source.resolve()==target.resolve(): raise ValueError('Source and output must differ')
    scene=trimesh.load(source,force='scene',process=False)
    assert len(scene.geometry)==91
    assert sum(len(g.faces) for g in scene.geometry.values())==22156
    shrink_sim_adjacent_slot(scene)
    # The source was watertight, but its shared vertex normals smooth across
    # perpendicular USB-C / SIM / speaker edges. That creates dark wedges and
    # triangle-shaped shading artifacts in Filament. Split normal continuity
    # without altering a single face position, index winding or opening.
    chassis=scene.geometry[CHASSIS]
    assert chassis.is_watertight, "v9 source chassis must be a closed shell"
    chassis.unmerge_vertices()
    assert len(chassis.vertices)==3*len(chassis.faces)
    rear=scene.geometry[REAR].visual.material
    front=scene.geometry[FRONT].visual.material
    rear.baseColorTexture=rear_texture(rear.baseColorTexture)
    rear.baseColorFactor=np.uint8([255,255,255,255])
    rear.normalTexture=None
    rear.metallicFactor=.08;rear.roughnessFactor=.77
    front.baseColorTexture=front.baseColorTexture.convert('RGB').resize(
        (1024,2048),Image.Resampling.LANCZOS).filter(
        ImageFilter.UnsharpMask(radius=1.4,percent=135,threshold=3))
    front.metallicFactor=0.;front.roughnessFactor=.12
    materials={}
    for mesh in scene.geometry.values():
        mat=getattr(mesh.visual,'material',None)
        if mat is not None and mat.name in RAILS:
            materials[mat.name]=mat
    assert set(materials)==set(RAILS),set(RAILS)-set(materials)
    for name,(rgba,metal,rough) in RAILS.items():
        material=materials[name]
        material.baseColorFactor=np.uint8(np.round(np.array(rgba)*255))
        material.metallicFactor=metal
        material.roughnessFactor=rough
    for name in ('009_camera_island_flush_face','008_island_upper_satin_white'):
        mat=scene.geometry[name].visual.material
        mat.metallicFactor=.43;mat.roughnessFactor=.36
    cameras=[
      (-.02251124,.06607206,.00822,'main'),
      (-.02251124,.04517886,.00822,'ultrawide'),
      (-.00204648,.06596437,.00817,'flicker_sensor'),
      (-.00215419,.04517886,.00813,'flash')]
    for cx,cy,r,label in cameras:
        mesh=precision_ring(cx,cy,r)
        mesh.visual=TextureVisuals(material=PBRMaterial(
            name=f'{label}_hairline_PBR',baseColorFactor=np.uint8([242,244,246,255]),
            metallicFactor=.55,roughnessFactor=.25,doubleSided=True))
        name=f'HQ10_{label}_hairline_precision_ring'
        scene.add_geometry(mesh,geom_name=name,node_name=name)
    target.parent.mkdir(parents=True,exist_ok=True)
    tmp=target.with_suffix('.pending.glb')
    scene.export(tmp,file_type='glb')
    check=trimesh.load(tmp,force='scene',process=False)
    tris=sum(len(g.faces) for g in check.geometry.values())
    assert len(check.geometry)==95 and tris==37516
    assert tmp.stat().st_size<16*1024*1024
    chassis=check.geometry[CHASSIS].visual.material
    assert np.allclose(np.asarray(chassis.baseColorFactor)[:3]/255,[.56,.59,.63],atol=.006)
    assert chassis.metallicFactor <= .05, "perimeter must not turn dark without a cubemap"
    edges=check.geometry[CHASSIS]
    assert len(edges.vertices)==3*len(edges.faces)
    slot = check.geometry['geometry_82']
    assert .00139 < (slot.bounds[1, 0] - slot.bounds[0, 0]) < .00142
    assert .00042 < (slot.bounds[1, 2] - slot.bounds[0, 2]) < .00043
    assert edges.is_watertight, "exported phone body must stay closed"
    vertex_normals=edges.vertex_normals[edges.faces]
    face_normals=edges.face_normals[:,None,:]
    facing=np.sum(vertex_normals*face_normals,axis=2)
    assert np.min(facing)>.995, "hard-edge normals must match their face normals"
    assert check.geometry[REAR].visual.material.baseColorTexture.size==(1024,2048)
    os.replace(tmp,target)
    print(json.dumps({'file':str(target),'bytes':target.stat().st_size,
        'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'triangles':tris,
        'meshes':len(check.geometry),'gray_rails':True,'hard_edge_normals':True,
        'perimeter_rgb_linear':[.56,.59,.63]}))

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('source',type=Path)
    parser.add_argument('target',type=Path)
    args=parser.parse_args()
    build(args.source,args.target)
