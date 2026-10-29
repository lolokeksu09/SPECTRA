#!/usr/bin/env python3
"""Dependency-free validation of the full v9 GLBs for Termux + GitHub Actions."""
import hashlib,json,struct,sys
from pathlib import Path

THIS=Path(__file__).resolve()
ASSETS=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else THIS.parents[1]/'app/src/main/assets/models'
MANIFEST=Path(sys.argv[2]).resolve() if len(sys.argv)>2 else THIS.with_name('model-manifest-v9.json')

def check(path, expected):
    raw=path.read_bytes()
    assert hashlib.sha256(raw).hexdigest()==expected['sha256'], f'{path.name}: SHA256 mismatch'
    assert raw[:4]==b'glTF' and struct.unpack_from('<II',raw,4)==(2,len(raw)),f'{path.name}: bad GLB header'
    offset=12;chunks=[]
    while offset<len(raw):
        n,kind=struct.unpack_from('<I4s',raw,offset)
        assert n%4==0 and n>0 and offset+8+n<=len(raw)
        chunks.append((kind,raw[offset+8:offset+8+n]));offset+=8+n
    assert [c[0] for c in chunks]==[b'JSON',b'BIN\0'], 'Expected JSON and embedded BIN'
    data=json.loads(chunks[0][1])
    assert len(data['buffers'])==1 and 'uri' not in data['buffers'][0]
    assert len(data.get('meshes',[]))==91, 'Unexpected mesh count'
    assert len(data.get('images',[]))==6 and len(data.get('textures',[]))==6, 'Textures missing'
    assert any('Dolby' in m.get('name','') for m in data.get('materials',[])), 'Dolby material missing'
    prims=[p for m in data['meshes'] for p in m['primitives']]
    assert len(prims)==91 and all('NORMAL' in p['attributes'] for p in prims), 'Missing normal vectors'
    assert all(p.get('mode',4)==4 for p in prims), 'Non-triangle primitive'
    tris=sum(data['accessors'][p['indices']]['count']//3 for p in prims)
    assert tris==22156 and len(data.get('materials',[]))>=79, 'Geometry/materials mismatch'
    for image in data['images']:
        assert image.get('mimeType')=='image/png' and 'bufferView' in image, 'External image or unexpected format'
    print(f'OK: {path.name}, 91 meshes, {tris} triangles, 6 embedded textures, normals present')

def main():
    models=json.loads(MANIFEST.read_text())
    assert set(models)=={'legion_y70_white.glb','legion_y70_black.glb'}
    for name,expected in models.items():check(ASSETS/name, expected)

if __name__=='__main__':
    try:main()
    except (AssertionError, OSError, KeyError, ValueError, IndexError,struct.error) as ex:
        sys.exit('SPECTRA v9 validation failed: '+str(ex))
