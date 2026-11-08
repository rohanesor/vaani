#!/usr/bin/env bash
# Operator-only: publish verified non-English TTS packs for user-confirmed downloads.
# Usage: AWS_REGION=ap-south-1 scripts/publish-language-packs.sh PRIVATE_ORIGIN_BUCKET extracted/models
set -Eeuo pipefail
bucket=${1:?Usage: $0 PRIVATE_ORIGIN_BUCKET EXTRACTED_MODELS_DIRECTORY}
models=${2:?Usage: $0 PRIVATE_ORIGIN_BUCKET EXTRACTED_MODELS_DIRECTORY}
region=${AWS_REGION:?AWS_REGION is required}
command -v aws >/dev/null || { echo 'AWS CLI is required' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'python3 is required' >&2; exit 1; }
python3 - "$models" <<'PY'
import hashlib, json, pathlib, sys
root = pathlib.Path(sys.argv[1]); files = json.loads((root / 'manifest.json').read_text())['files']
for language in ('hin','guj','mar','kan','mal','tam','tel','ory','ben'):
    for name in ('model.onnx', 'tokens.txt'):
        relative = f'tts/{language}/{name}'; path = root / relative; expected = files[relative]
        actual = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
        if not path.is_file() or path.stat().st_size != expected['sizeBytes'] or actual != expected['sha256']:
            raise SystemExit(f'Invalid model pack file: {relative}')
PY
for lang in hin guj mar kan mal tam tel ory ben; do
  for name in model.onnx tokens.txt; do
    source="$models/tts/$lang/$name"
    test -s "$source" || { echo "Missing $source" >&2; exit 1; }
    aws s3 cp "$source" "s3://$bucket/models/v1.0.0/tts/vits-mms-$lang/$name" --region "$region" \
      --cache-control 'public,max-age=31536000,immutable'
  done
done
echo "Published verified language-pack objects to s3://$bucket/models/v1.0.0/tts/"
