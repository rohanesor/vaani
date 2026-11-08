#!/usr/bin/env python3
"""Verify all ten TTS packs are present in an APK and match assets/models/manifest.json."""
import hashlib, json, sys, zipfile
from pathlib import Path
LANGS={'eng','hin','guj','mar','kan','mal','tam','tel','ory','ben'}
if len(sys.argv)!=2: raise SystemExit('usage: validate-10-language-packaging.py APK')
apk=Path(sys.argv[1]); manifest=json.loads(zipfile.ZipFile(apk).read('assets/models/manifest.json'))['files']
with zipfile.ZipFile(apk) as z:
  missing=[]
  for lang in sorted(LANGS):
    for suffix in ('model.onnx','tokens.txt'):
      rel=f'tts/{lang}/{suffix}'; name=f'assets/models/{rel}'
      if rel not in manifest or name not in z.namelist(): missing.append(rel); continue
      data=z.read(name); spec=manifest[rel]
      if len(data)!=spec['sizeBytes'] or hashlib.sha256(data).hexdigest()!=spec['sha256'].lower(): raise SystemExit(f'FAIL checksum: {rel}')
      print(f'PASS {rel} {len(data)} bytes')
  if missing: raise SystemExit('FAIL missing: '+', '.join(missing))
print('TEN_LANGUAGE_TTS_PACKAGING=PASS')
