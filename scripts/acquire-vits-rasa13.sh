#!/usr/bin/env bash
# Acquire the official gated AI4Bharat candidate into ignored research storage only.
# This is not a production-model staging script.
set -Eeuo pipefail

readonly MODEL_ID='ai4bharat/vits_rasa_13'
readonly REVISION='00b1590501b55708d5d66be51bae336b51bce1d2'
readonly EXPECTED_WEIGHTS_SHA256='0596e963e176aa71b4f581ea3e69d9deceff4ae20caa2752b6ffa970e721fc91'
readonly OUTPUT_ROOT="${1:-.research/tts-vits-rasa13/${REVISION}}"
readonly BASE_URL="https://huggingface.co/${MODEL_ID}/resolve/${REVISION}"
readonly API_URL="https://huggingface.co/api/models/${MODEL_ID}/tree/${REVISION}?recursive=true&expand=true"
readonly FILES=(
  .gitattributes README.md config.json configuration_vits.py model.safetensors
  modeling_vits.py special_tokens_map.json tokenization_vits.py tokenizer_config.json vocab.json
)

: "${HF_TOKEN:?Set HF_TOKEN to an approved Hugging Face token after accepting the official model gate.}"
command -v curl >/dev/null || { echo 'curl is required' >&2; exit 1; }
command -v sha256sum >/dev/null || { echo 'sha256sum is required' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'python3 is required' >&2; exit 1; }

umask 077
mkdir -p "$OUTPUT_ROOT"
headers=(-H "Authorization: Bearer ${HF_TOKEN}")

curl --fail --location --retry 2 --connect-timeout 30 --max-time 120 \
  "${headers[@]}" "$API_URL" -o "$OUTPUT_ROOT/source-tree.json"

for file in "${FILES[@]}"; do
  target="$OUTPUT_ROOT/$file"
  mkdir -p "$(dirname "$target")"
  curl --fail --location --retry 2 --connect-timeout 30 --max-time 900 \
    "${headers[@]}" "$BASE_URL/$file?download=true" -o "$target"
done

actual=$(sha256sum "$OUTPUT_ROOT/model.safetensors" | awk '{print $1}')
[[ "$actual" == "$EXPECTED_WEIGHTS_SHA256" ]] || {
  echo "model.safetensors SHA-256 mismatch: expected $EXPECTED_WEIGHTS_SHA256, got $actual" >&2
  exit 1
}

python3 - "$OUTPUT_ROOT" "$MODEL_ID" "$REVISION" <<'PY'
import datetime, hashlib, json, pathlib, sys
root, model_id, revision = map(str, sys.argv[1:])
root = pathlib.Path(root)
files = []
for path in sorted(p for p in root.rglob('*') if p.is_file() and p.name not in {'inventory.json'}):
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    files.append({'path': str(path.relative_to(root)), 'sizeBytes': path.stat().st_size, 'sha256': digest})
(root / 'inventory.json').write_text(json.dumps({
    'source': f'https://huggingface.co/{model_id}',
    'revision': revision,
    'downloadedAtUtc': datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat(),
    'files': files,
}, indent=2) + '\n', encoding='utf-8')
PY

echo "Acquired and verified ${MODEL_ID}@${REVISION} in ${OUTPUT_ROOT}"
echo 'Research artifacts are intentionally ignored and must not be copied into production assets.'
