#!/usr/bin/env python3
"""Refine the existing packaged White v17; no chassis reconstruction or runtime shader changes.
Core glTF metallic/roughness + tangent-space normals work in SPECTRA's gltfio.
All texture maps are deterministic material data, never generated reference images.
"""
import argparse
import hashlib
import json
import os
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont
import trimesh
from trimesh.visual.material import PBRMaterial
from trimesh.visual.texture import TextureVisuals

CHASSIS = '000_aluminium_silver_white_chassis'
REAR = '001_original_engraved_LEGION_pearlescent_white_back'
CX = -.02251124
CY = (.06607206, .04517886)
# Only the six optical surfaces may be retopologised. Housing centres/footprints stay fixed.
OPTICS = ('013_primary_black_coated_face', '014_primary_dark_internal_vignette',
          '015_primary_central_lens', '028_bottom_black_coated_face',
          '029_bottom_dark_internal_vignette', '030_bottom_central_lens')
DEPTH_ONLY = ('016_primary_blue_coating', '017_primary_micro_reflection_blue',
              '018_primary_micro_reflection_white', '031_bottom_blue_coating',
              '032_bottom_micro_reflection_blue', '033_bottom_micro_reflection_white')


def planar_uv(mesh):
    lo, hi = mesh.bounds[:, :2]
    return (mesh.vertices[:, :2] - lo) / (hi - lo)


def normal_image(height, strength):
    # Image rows run down; glTF tangent +V runs up. No baked illumination.
    dy, dx = np.gradient(height)
    normals = np.stack((-dx * strength, dy * strength, np.ones_like(dx)), axis=-1)
    normals /= np.linalg.norm(normals, axis=-1, keepdims=True)
    return Image.fromarray(np.uint8(np.clip(normals * .5 + .5, 0, 1) * 255), 'RGB')


def metal_maps(size=512):
    rng = np.random.default_rng(1801)
    grain = rng.normal(0, 1, (size, size))
    base = np.clip(np.array([226, 229, 232]) + grain[..., None] * 1.3, 0, 255)
    packed = np.zeros((size, size, 3), dtype=np.uint8)
    packed[..., 0] = 255
    packed[..., 1] = np.uint8(np.clip(158 + grain * 5, 140, 178))
    packed[..., 2] = 255
    return Image.fromarray(base.astype('uint8')), normal_image(grain, .045), Image.fromarray(packed)


def rear_maps():
    rng = np.random.default_rng(1802)
    y, x = np.mgrid[0:1024, 0:512]
    # Subtle fine crossed fibre/etched-glass pattern, matching the photo's white finish.
    height = .30 * np.sin((x + y) * 2.1) + .24 * np.sin((x - y) * 2.05)
    height += rng.normal(0, .35, height.shape)
    packed = np.zeros((*height.shape, 3), dtype='uint8')
    packed[..., 0] = 255
    packed[..., 1] = np.uint8(np.clip(192 + height * 6, 178, 208))
    packed[..., 2] = 0
    return normal_image(height, .028), Image.fromarray(packed)


def radial_surface(cx, cy, radii, heights, segments=128):
    theta = np.arange(segments) * 2 * np.pi / segments
    v = np.array([(cx + r * np.cos(t), cy + r * np.sin(t), z)
                  for r, z in zip(radii, heights) for t in theta])
    f = []
    for j in range(len(radii) - 1):
        for i in range(segments):
            a = j * segments + i
            b = j * segments + (i + 1) % segments
            c = a + segments
            d = b + segments
            f.extend(((a, c, d), (a, d, b)))
    mesh = trimesh.Trimesh(vertices=v, faces=np.array(f), process=False)
    # Analytic normals prevent radial faceting and remain valid at an annulus boundary.
    slopes = np.gradient(np.asarray(heights), np.asarray(radii))
    normals = np.array([(-s * np.cos(t), -s * np.sin(t), 1.) for s in slopes for t in theta])
    normals /= np.linalg.norm(normals, axis=1, keepdims=True)
    mesh.vertex_normals = normals
    return mesh


def convex_pupil(cx, cy):
    radii = np.linspace(.000015, .00176, 9)
    heights = .00530 + .00012 * (1 - (radii / .00176) ** 2)
    mesh = radial_surface(cx, cy, radii, heights)
    # Cap the tiny centre without degenerate triangles.
    center = len(mesh.vertices)
    verts = np.vstack((mesh.vertices, [cx, cy, .00542]))
    faces = np.vstack((mesh.faces, [(center, i, (i + 1) % 128) for i in range(128)]))
    normals = np.vstack((mesh.vertex_normals, [0., 0., 1.]))
    return trimesh.Trimesh(vertices=verts, faces=faces, vertex_normals=normals, process=False)


def optic_texture(marked, size=1024):
    y, x = np.mgrid[:size, :size]
    radius = np.hypot(x - size / 2, y - size / 2) / (size / 2)
    # Neutral antireflection coating; light/reflections come from PBR, not a painted studio.
    tint = np.clip((radius - .55) * 4, 0, 1)
    rgb = np.stack((8 + tint * 3, 12 + tint * 6, 16 + tint * 7), axis=-1).astype('uint8')
    img = Image.fromarray(rgb)
    if marked:
        font = ImageFont.truetype('DejaVuSans.ttf', 40)
        text = 'LYTIA 50MP'
        # Top arc, left-to-right when the handset is upright, as in reference 3415.jpg.
        angles = np.linspace(-66, 66, len(text))
        for char, angle in zip(text, angles):
            tile = Image.new('RGBA', (84, 84))
            draw = ImageDraw.Draw(tile)
            draw.text((42, 42), char, font=font, anchor='mm', fill=(54, 62, 65, 255))
            tile = tile.rotate(-angle, resample=Image.Resampling.BICUBIC, expand=True)
            a = np.deg2rad(angle)
            px = size / 2 + .79 * size / 2 * np.sin(a)
            py = size / 2 - .79 * size / 2 * np.cos(a)
            img.paste(tile, (round(px - tile.width / 2), round(py - tile.height / 2)), tile)
    return img


def set_material(mesh, material, uv=None):
    mesh.visual = TextureVisuals(uv=uv, material=material)


def refine(source):
    scene = trimesh.load(source, force='scene', process=False)
    if len(scene.geometry) != 101 or sum(len(g.faces) for g in scene.geometry.values()) != 69796:
        raise ValueError('Expected the existing packaged White v17 (101 meshes / 69796 triangles)')
    original = {n: g.copy() for n, g in scene.geometry.items()}
    grain, normal, packed = metal_maps()
    plate = scene.geometry['009_camera_island_flush_face']
    set_material(plate, PBRMaterial(name='White_v18_micrograin_silver_plate',
        baseColorFactor=[255, 255, 255, 255], baseColorTexture=grain,
        normalTexture=normal, metallicRoughnessTexture=packed,
        metallicFactor=.30, roughnessFactor=1., doubleSided=False), planar_uv(plate))
    rear = scene.geometry[REAR].visual.material
    rear.normalTexture, rear.metallicRoughnessTexture = rear_maps()
    rear.metallicFactor = 0.
    rear.roughnessFactor = 1.
    # Keep the rear's exact base image and UVs: LEGION and Lenovo remain byte-identical.
    for name in ('v16_bottom_antenna_separator_L', 'v16_bottom_antenna_separator_R'):
        mesh = scene.geometry[name]
        material = mesh.visual.material.copy()
        material.name = 'White_v18_integrated_antenna_ceramic'
        material.baseColorFactor = np.uint8([151, 157, 166, 255])
        material.metallicFactor = .02
        material.roughnessFactor = .80
        mesh.visual.material = material
    # Keep every machined torus. Stronger but broad metallic reflections; no dark rail transfer.
    for name in ('v16_primary_engraved_ridges', 'v16_ultrawide_engraved_ridges',
                 'v16_sensor_machined_ridges', 'v16_flash_machined_ridges'):
        material = scene.geometry[name].visual.material.copy()
        material.name = 'White_v18_precision_cut_silver'
        material.metallicFactor = .58
        material.roughnessFactor = .30
        scene.geometry[name].visual.material = material
    for idx, (cy, prefix) in enumerate(zip(CY, ('primary', 'bottom'))):
        base = 13 if idx == 0 else 28
        radii = np.linspace(.00412, .00734, 13)
        t = (radii - radii[0]) / np.ptp(radii)
        heights = .00553 + .000115 * np.sin(t * np.pi) + .00004 * t
        mesh = radial_surface(CX, cy, radii, heights)
        set_material(mesh, PBRMaterial(name=f'White_v18_{prefix}_convex_optical_glass',
            baseColorFactor=[255, 255, 255, 255], baseColorTexture=optic_texture(idx == 1),
            metallicFactor=.04, roughnessFactor=.145, doubleSided=False),
            (mesh.vertices[:, :2] - [CX, cy]) / (.00734 * 2) + .5)
        scene.geometry[f'{base:03d}_{prefix}_black_coated_face'] = mesh
        # Real recessed chamber: an open annular surface rather than stacked flat discs.
        mesh = radial_surface(CX, cy, np.linspace(.00176, .00413, 9),
                              np.linspace(.00527, .005532, 9))
        set_material(mesh, PBRMaterial(name='White_v18_recessed_optical_barrel',
            baseColorFactor=[10, 15, 19, 255], metallicFactor=0., roughnessFactor=.42,
            doubleSided=False))
        scene.geometry[f'{base+1:03d}_{prefix}_dark_internal_vignette'] = mesh
        mesh = convex_pupil(CX, cy)
        set_material(mesh, PBRMaterial(name='White_v18_aspheric_pupil',
            baseColorFactor=[5, 16, 23, 255], metallicFactor=.12, roughnessFactor=.09,
            doubleSided=False))
        scene.geometry[f'{base+2:03d}_{prefix}_central_lens'] = mesh
    for name in DEPTH_ONLY:
        mesh = scene.geometry[name]
        v = mesh.vertices.copy()
        v[:, 2] -= .000235
        mesh.vertices = v
        material = mesh.visual.material.copy()
        if 'micro_reflection' in name:
            # Preserve source glints but reduce the old cyan dot look.
            material.baseColorFactor = np.uint8([42, 65, 73, 255] if 'white' in name else [16, 35, 44, 255])
        material.roughnessFactor = .12
        mesh.visual.material = material
    # Contract: all ports, shell, logos, small sensor, LED, housing positions and seams remain exact.
    for name, before in original.items():
        after = scene.geometry[name]
        if name not in (*OPTICS, *DEPTH_ONLY):
            assert np.array_equal(before.vertices, after.vertices), name
            assert np.array_equal(before.faces, after.faces), name
    # v17's trimesh cache did not serialize normals on several edited meshes.
    # Material/vertex assignment can invalidate that cache; explicitly emit every normal.
    for mesh in scene.geometry.values():
        mesh.vertex_normals = mesh.vertex_normals
    return scene


def explicit_tangents(path):
    """Write glTF tangent frames for normal-mapped surfaces instead of relying on loader variants."""
    raw = path.read_bytes()
    n = struct.unpack_from('<I', raw, 12)[0]
    doc = json.loads(raw[20:20 + n])
    binary = bytearray(raw[28 + n:])
    def array(index):
        a = doc['accessors'][index]
        b = doc['bufferViews'][a['bufferView']]
        dtype = {5126: '<f4', 5125: '<u4', 5123: '<u2'}[a['componentType']]
        width = {'VEC3': 3, 'VEC2': 2, 'SCALAR': 1}[a['type']]
        return np.frombuffer(binary, dtype=dtype, count=a['count'] * width,
            offset=b.get('byteOffset', 0) + a.get('byteOffset', 0)).reshape(a['count'], width).copy()
    for mesh in doc['meshes']:
        for p in mesh['primitives']:
            attrs = p['attributes']
            for index in attrs.values():
                doc['bufferViews'][doc['accessors'][index]['bufferView']]['target'] = 34962
            doc['bufferViews'][doc['accessors'][p['indices']]['bufferView']]['target'] = 34963
            if 'normalTexture' not in doc['materials'][p['material']]:
                continue
            vertices, normals, uv = (array(attrs[k]) for k in ('POSITION', 'NORMAL', 'TEXCOORD_0'))
            faces = array(p['indices']).reshape(-1, 3)
            tan = np.zeros_like(vertices, dtype=float)
            bitan = np.zeros_like(vertices, dtype=float)
            for face in faces:
                a, b, c = face
                e1, e2 = vertices[b] - vertices[a], vertices[c] - vertices[a]
                u1, u2 = uv[b] - uv[a], uv[c] - uv[a]
                det = u1[0] * u2[1] - u1[1] * u2[0]
                assert abs(det) > 1e-12, 'Degenerate normal-map UV'
                t = (e1 * u2[1] - e2 * u1[1]) / det
                bt = (e2 * u1[0] - e1 * u2[0]) / det
                tan[face] += t
                bitan[face] += bt
            tan -= normals * np.einsum('ij,ij->i', normals, tan)[:, None]
            tan /= np.linalg.norm(tan, axis=1, keepdims=True)
            sign = np.where(np.einsum('ij,ij->i', np.cross(normals, tan), bitan) < 0, -1., 1.)
            data = np.column_stack((tan, sign)).astype('<f4')
            offset = len(binary)
            binary.extend(data.tobytes())
            view = len(doc['bufferViews'])
            doc['bufferViews'].append({'buffer': 0, 'byteOffset': offset,
                                      'byteLength': data.nbytes, 'target': 34962})
            attrs['TANGENT'] = len(doc['accessors'])
            doc['accessors'].append({'bufferView': view, 'componentType': 5126,
                                    'count': len(data), 'type': 'VEC4'})
    doc['buffers'][0]['byteLength'] = len(binary)
    encoded = json.dumps(doc, separators=(',', ':')).encode()
    encoded += b' ' * (-len(encoded) % 4)
    binary.extend(b'\0' * (-len(binary) % 4))
    total = 12 + 8 + len(encoded) + 8 + len(binary)
    path.write_bytes(struct.pack('<4sII', b'glTF', 2, total) +
                     struct.pack('<I4s', len(encoded), b'JSON') + encoded +
                     struct.pack('<I4s', len(binary), b'BIN\0') + binary)


def main(source, target):
    scene = refine(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix('.v18-pending.glb')
    try:
        scene.export(temporary, file_type='glb')
        explicit_tangents(temporary)
        loaded = trimesh.load(temporary, force='scene', process=False)
        assert len(loaded.geometry) == 101
        assert temporary.stat().st_size < 5 * 1024 * 1024
        for name in OPTICS:
            g = loaded.geometry[name]
            assert np.all(np.isfinite(g.vertex_normals))
            assert np.min(np.linalg.norm(g.vertex_normals, axis=1)) > .99
            assert np.min(g.face_normals[:, 2]) > 0, name
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
    print(json.dumps({'file': str(target), 'version': 18, 'meshes': 101,
        'triangles': sum(len(g.faces) for g in loaded.geometry.values()),
        'bytes': target.stat().st_size, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
        'antenna_centres_mm': [-5.9, 5.9], 'lighting_runtime_unchanged': True}))


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('source', type=Path)
    p.add_argument('target', type=Path)
    args = p.parse_args()
    main(args.source, args.target)
