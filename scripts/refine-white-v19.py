#!/usr/bin/env python3
"""White v18 -> v19: connected volume rocker and visible side antenna inserts.
Photo-derived lower-band placement is approximate, not a measured factory CAD dimension.
No chassis, camera, port, logo, lighting or runtime-viewer reconstruction.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import numpy as np
import trimesh
from trimesh.visual.material import PBRMaterial
from trimesh.visual.texture import TextureVisuals

CHASSIS = '000_aluminium_silver_white_chassis'
VOLUME = '065_volume_button'
NOTCH = '067_volume_button_satin_highlight'  # Existing node retained, now a physical groove inset.
BANDS = ('068_left_top_antenna_band', '069_left_bottom_antenna_band',
         '070_right_top_antenna_band', '071_right_bottom_antenna_band')
CHANGED = (VOLUME, NOTCH, *BANDS)
BAND_WIDTH = .00065
LOWER_Y = -.041  # ~75% down the body in both supplied three-quarter reference views.
SURFACE_LIFT = .000008  # 8 microns, clear of the rail without a visibly raised strip.


def one_connected(mesh):
    # No scipy/rtree dependency in the existing offline build environment.
    parent = list(range(len(mesh.vertices)))
    def root(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i
    for a, b, c in mesh.faces:
        a, b, c = root(int(a)), root(int(b)), root(int(c))
        parent[b] = a
        parent[c] = a
    return len({root(i) for i in range(len(parent))}) == 1


def rocker(bounds, groove_depth=.00012):
    lo, hi = bounds
    center = (lo + hi) / 2
    radius = (hi[2] - lo[2]) / 2
    half_depth = (hi[0] - lo[0]) / 2
    # Rounded ends, with a shared connected base across the central shallow groove.
    low, high = lo[1] + radius, hi[1] - radius
    cap = np.linspace(.13, np.pi / 2, 9)
    sections = [(low - radius * np.cos(t), np.sin(t)) for t in cap]
    groove_sections = (center[1] + np.array([-.00030, -.00019, -.00010, 0, .00010, .00019, .00030])
                       if groove_depth else np.array([]))
    ys = np.concatenate((np.linspace(low, high, 15), groove_sections))
    sections += [(y, 1.) for y in sorted(set(ys)) if low <= y <= high]
    sections += [(high + radius * np.cos(t), np.sin(t)) for t in cap[::-1]]
    sections = sorted(set((round(y, 12), round(scale, 12)) for y, scale in sections))
    angles = np.arange(32) * 2 * np.pi / 32
    vertices = []
    for y, scale in sections:
        for angle in angles:
            c = np.cos(angle)
            px = np.sign(c) * abs(c) ** .40
            groove = groove_depth * max(0., 1 - abs(y - center[1]) / .00019)
            vertices.append((center[0] + half_depth * scale * px + groove * max(-px, 0) ** 4,
                             y, center[2] + radius * scale * np.sin(angle)))
    faces = []
    for j in range(len(sections) - 1):
        for k in range(32):
            a = j * 32 + k
            b = j * 32 + (k + 1) % 32
            c, d = a + 32, b + 32
            faces.extend(((a, d, b), (a, c, d)))
    for end, y in ((0, lo[1]), (1, hi[1])):
        pole = len(vertices)
        vertices.append((center[0], y, center[2]))
        start = 0 if end == 0 else (len(sections) - 1) * 32
        cap_faces = [(pole, start + k, start + (k + 1) % 32) for k in range(32)]
        faces += cap_faces if end == 0 else [f[::-1] for f in cap_faces]
    mesh = trimesh.Trimesh(vertices=vertices, faces=faces, process=False)
    assert mesh.is_watertight and mesh.is_winding_consistent and one_connected(mesh) and mesh.volume > 0
    if not groove_depth:
        return mesh, None
    # A narrow dark surface at the bottom of the groove, conforming to the rocker face.
    vertices, faces = [], []
    zs = np.linspace(-.83, .83, 25)
    for dy in (-.000055, .000055):
        groove = groove_depth * (1 - abs(dy) / .00019)
        for z in zs:
            px = -(1 - z * z) ** .20
            vertices.append((center[0] + half_depth * px + groove * (-px) ** 4 - .000004,
                             center[1] + dy, center[2] + radius * z))
    for k in range(len(zs) - 1):
        faces.extend(((k, k + len(zs), k + len(zs) + 1), (k, k + len(zs) + 1, k + 1)))
    inset = trimesh.Trimesh(vertices=vertices, faces=faces, process=False)
    if inset.face_normals[:, 0].mean() > 0:
        inset.invert()
    return mesh, inset


def chassis_profile(shell, y, sign):
    """Intersect the existing mesh, then follow its outer X/Z cross-section; no ray/rtree dependency."""
    segments = []
    for triangle in shell.triangles:
        cuts = []
        for a, b in zip(triangle, np.roll(triangle, -1, axis=0)):
            if (a[1] <= y < b[1]) or (b[1] <= y < a[1]):
                t = (y - a[1]) / (b[1] - a[1])
                cuts.append((a + t * (b - a))[[0, 2]])
        if len(cuts) == 2:
            segments.append(cuts)
    assert segments
    segments = np.asarray(segments)
    # Follow the side and its bevels, stopping before the independent glass panels.
    zs = sorted(set(np.linspace(-.00410, .00410, 33).tolist() +
                    [float(z) for z in shell.vertices[:, 2] if -.00410 < z < .00410]))
    result = []
    for z in zs:
        candidates = []
        for a, b in segments:
            if abs(a[1] - b[1]) < 1e-12:
                continue
            if min(a[1], b[1]) - 1e-10 <= z <= max(a[1], b[1]) + 1e-10:
                t = (z - a[1]) / (b[1] - a[1])
                candidates.append(float(a[0] + t * (b[0] - a[0])))
        assert candidates, (y, z)
        result.append((max(candidates) if sign > 0 else min(candidates), z))
    return np.array(result)


def antenna(shell, cy, sign):
    profile = chassis_profile(shell, cy, sign)
    # Discard redundant coplanar subdivisions (same surface, no extra draw calls).
    keep = [0]
    for i in range(1, len(profile) - 1):
        a, b = profile[i] - profile[keep[-1]], profile[i + 1] - profile[i]
        if abs(a[0] * b[1] - a[1] * b[0]) > 1e-12:
            keep.append(i)
    keep.append(len(profile) - 1)
    keep.append(int(np.argmin(np.abs(profile[:, 1]))))
    profile = profile[sorted(set(keep))]
    slope = np.gradient(profile[:, 0], profile[:, 1])
    normals = np.column_stack((np.full(len(profile), sign), np.zeros(len(profile)), -sign * slope))
    normals /= np.linalg.norm(normals, axis=1, keepdims=True)
    vertices = np.vstack([np.column_stack((profile[:, 0], np.full(len(profile), y), profile[:, 1])) +
                          SURFACE_LIFT * normals for y in (cy - BAND_WIDTH / 2, cy + BAND_WIDTH / 2)])
    faces = []
    count = len(profile)
    for i in range(count - 1):
        faces.extend(((i, i + count, i + count + 1), (i, i + count + 1, i + 1)))
    if sign < 0:
        faces = np.asarray(faces)[:, ::-1]
    return trimesh.Trimesh(vertices=vertices, faces=faces,
                           vertex_normals=np.vstack((normals, normals)), process=False)


def refine(source):
    scene = trimesh.load(source, force='scene', process=False)
    assert len(scene.geometry) == 101 and sum(len(g.faces) for g in scene.geometry.values()) == 81700
    bounds = np.array([scene.geometry[VOLUME].bounds[0], scene.geometry[VOLUME].bounds[1]])
    # Keep the combined old body/highlight envelope: the new rounded rocker has the same placement.
    bounds[0, 0] = scene.geometry[NOTCH].bounds[0, 0]
    body, inset = rocker(bounds)
    body.visual = TextureVisuals(material=scene.geometry[VOLUME].visual.material.copy())
    body.visual.material.name = 'White_v19_joined_satin_volume_rocker'
    inset.visual = TextureVisuals(material=PBRMaterial(name='White_v19_recessed_volume_divider',
        baseColorFactor=[88, 95, 103, 255], metallicFactor=.03, roughnessFactor=.72, doubleSided=False))
    scene.geometry[VOLUME], scene.geometry[NOTCH] = body, inset
    for name in BANDS:
        previous = scene.geometry[name]
        cy = LOWER_Y if 'bottom' in name else previous.bounds[:, 1].mean()
        sign = -1 if 'left' in name else 1
        mesh = antenna(scene.geometry[CHASSIS], cy, sign)
        mesh.visual = TextureVisuals(material=PBRMaterial(name='White_v19_flush_side_antenna_ceramic',
            baseColorFactor=[190, 194, 198, 255], metallicFactor=0., roughnessFactor=.84, doubleSided=False))
        scene.geometry[name] = mesh
    for mesh in scene.geometry.values():
        mesh.vertex_normals = mesh.vertex_normals
    return scene


def main(source, target):
    scene = refine(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    pending = target.with_suffix('.v19-pending.glb')
    try:
        scene.export(pending, file_type='glb')
        spec = importlib.util.spec_from_file_location('v18', Path(__file__).with_name('refine-white-v18.py'))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.explicit_tangents(pending)
        loaded = trimesh.load(pending, force='scene', process=False)
        assert len(loaded.geometry) == 101
        assert loaded.geometry[VOLUME].is_watertight and one_connected(loaded.geometry[VOLUME])
        assert pending.stat().st_size < 5 * 1024 * 1024
        os.replace(pending, target)
    finally:
        pending.unlink(missing_ok=True)
    print(json.dumps({'version': 19, 'meshes': 101,
        'triangles': sum(len(g.faces) for g in loaded.geometry.values()), 'bytes': target.stat().st_size,
        'sha256': hashlib.sha256(target.read_bytes()).hexdigest(), 'joined_rocker': True,
        'side_band_width_mm': BAND_WIDTH * 1000, 'lower_side_band_y_mm': LOWER_Y * 1000,
        'lower_usb_band_centres_mm': [-5.9, 5.9]}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('target', type=Path)
    args = parser.parse_args()
    main(args.source, args.target)
