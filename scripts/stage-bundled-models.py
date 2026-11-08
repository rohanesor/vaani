#!/usr/bin/env python3
"""Stages a protected, verified model archive into Android assets.

The archive is never committed. It must contain models/manifest.json and exactly:
 stt/ggml-tiny-q5_1.bin; tts/{eng,hin,guj,mar,kan,mal,tam,tel,ory,ben}/{model.onnx,tokens.txt}
manifest.json maps every relative file path to its SHA-256, byte size, and is the
only source of TTS model metadata accepted by the production build.
"""
import hashlib, json, os, shutil, sys, tempfile, zipfile
from pathlib import Path

LANGUAGES = ('eng','hin','guj','mar','kan','mal','tam','tel','ory','ben')
REQUIRED = {'stt/ggml-tiny-q5_1.bin'} | {f'tts/{l}/{n}' for l in LANGUAGES for n in ('model.onnx','tokens.txt')}
# Default APK: base STT plus four controlled base voices. A research-only build may
# opt into all ten packs after local provenance review; it is never enabled implicitly.
BASE_BUNDLED = {'stt/ggml-tiny-q5_1.bin', 'tts/eng/model.onnx', 'tts/eng/tokens.txt', 'tts/hin/model.onnx', 'tts/hin/tokens.txt', 'tts/tam/model.onnx', 'tts/tam/tokens.txt', 'tts/guj/model.onnx', 'tts/guj/tokens.txt'}
ALL_TTS_BUNDLED = {'stt/ggml-tiny-q5_1.bin'} | {f'tts/{l}/{n}' for l in LANGUAGES for n in ('model.onnx', 'tokens.txt')}
BUNDLED = ALL_TTS_BUNDLED if os.environ.get('VOKIE_BUNDLE_ALL_TTS') == '1' else BASE_BUNDLED
# Immutable project pins; the protected manifest supplies the independently verified remaining MMS files.
PINNED_SHA256 = {
    'stt/ggml-tiny-q5_1.bin': '818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7',
    'tts/eng/model.onnx': 'e3a198f6a4473429bab138be040e7cd40d2cab7a31b6410ff0a94d5a7fbbc254',
    'tts/eng/tokens.txt': 'dff08580748be688d9112d62d6352422c56d372dfe34b24ea3f66fa1b75cfaa9',
}
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/src/main/assets/models'

def fail(message):
    raise SystemExit(f'Model staging failed: {message}')

def sha(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()

if len(sys.argv) != 2: fail('usage: stage-bundled-models.py EXTRACTED_ARCHIVE_DIRECTORY_OR_ZIP')
input_path = Path(sys.argv[1])
if not input_path.exists(): fail(f'archive input not found: {input_path}')
with tempfile.TemporaryDirectory(prefix='vokie-models-') as tmp:
    tmp = Path(tmp)
    if input_path.is_dir():
        source = input_path / 'models'
    else:
        with zipfile.ZipFile(input_path) as z:
            names = set(z.namelist())
            if any(n.startswith('/') or '..' in Path(n).parts for n in names): fail('unsafe ZIP path')
            z.extractall(tmp)
        source = tmp / 'models'
    manifest_file = source / 'manifest.json'
    if not manifest_file.is_file(): fail('models/manifest.json is missing')
    manifest = json.loads(manifest_file.read_text(encoding='utf-8'))
    files = manifest.get('files')
    if not isinstance(files, dict) or set(files) != REQUIRED: fail('manifest must describe exactly every required production model file')
    for relative, expected in files.items():
        path = source / relative
        if not path.is_file(): fail(f'missing {relative}')
        if set(expected) != {'sha256', 'sizeBytes'}: fail(f'invalid manifest entry for {relative}')
        if relative in PINNED_SHA256 and expected['sha256'].lower() != PINNED_SHA256[relative]: fail(f'manifest pin mismatch for {relative}')
        if path.stat().st_size != expected['sizeBytes'] or sha(path) != expected['sha256'].lower(): fail(f'checksum or size mismatch for {relative}')
    if DEST.exists(): shutil.rmtree(DEST)
    DEST.mkdir(parents=True)
    shutil.copy2(manifest_file, DEST / 'manifest.json')
    for relative in sorted(BUNDLED):
        destination = DEST / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / relative, destination)
print(f'Staged {len(BUNDLED)} base model files ({sum((DEST/p).stat().st_size for p in BUNDLED)} bytes); manifest covers {len(REQUIRED)} downloadable/verified files.)')
