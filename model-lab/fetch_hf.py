import sys, os, hashlib, json, time
from huggingface_hub import hf_hub_download

REPO = "Xenova/nllb-200-distilled-600M"
REV = "261c31d1a5732c67cdd16d80e8d6088507c7ccea"
DEST = r"C:\tts\model-lab\models\nllb"
MANIFEST = r"C:\tts\model-lab\models\MANIFEST.json"

repo_files = [
    "onnx/encoder_model_quantized.onnx",
    "onnx/decoder_model_quantized.onnx",
    "onnx/decoder_with_past_model_quantized.onnx",
    "tokenizer.json",
    "sentencepiece.bpe.model",
]
# re-fetch decoder_model_quantized specifically (was truncated)
targets = sys.argv[1:] if len(sys.argv) > 1 else repo_files

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

manifest = {}
if os.path.exists(MANIFEST):
    manifest = json.load(open(MANIFEST, "r", encoding="utf-8"))

for rel in targets:
    start = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    p = hf_hub_download(repo_id=REPO, filename=rel, revision=REV, local_dir=DEST)
    size = os.path.getsize(p)
    dig = sha256(p)
    label = os.path.basename(rel)
    manifest[label] = {"url": "hf://%s@%s/%s" % (REPO, REV, rel), "revision": REV,
                       "repo": REPO, "size_bytes": size, "sha256": dig,
                       "downloaded_utc": start, "label": label}
    print("%s  size=%d  sha256=%s" % (label, size, dig))
    json.dump(manifest, open(MANIFEST, "w", encoding="utf-8"), indent=2)
