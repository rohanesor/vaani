import sys, os, hashlib, json, time
from huggingface_hub import hf_hub_download

MANIFEST = r"C:\tts\model-lab\models\MANIFEST.json"
manifest = json.load(open(MANIFEST, "r", encoding="utf-8")) if os.path.exists(MANIFEST) else {}

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for c in iter(lambda: f.read(1 << 20), b""):
            h.update(c)
    return h.hexdigest()

def grab(repo, rel, rev, label, local_dir):
    p = hf_hub_download(repo_id=repo, filename=rel, revision=rev, local_dir=local_dir)
    size = os.path.getsize(p)
    dig = sha256(p)
    manifest[label] = {"repo": repo, "revision": rev, "file": rel, "url": "hf://%s@%s/%s" % (repo, rev, rel),
                       "size_bytes": size, "sha256": dig,
                       "downloaded_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    print("%s  size=%d  sha256=%s" % (label, size, dig[:32]))
    return p

# CT2 NLLB
CT_REPO = "osa911/nllb-200-distilled-600M-ct2-int8"
CT_REV = "46858753dbaf8eb5e21bb6f0037c3b90851e090a"
CT_DIR = r"C:\tts\model-lab\models\ct2\nllb600m"
os.makedirs(CT_DIR, exist_ok=True)
for rel in ["model.bin", "config.json", "shared_vocabulary.json", "sentencepiece.bpe.model"]:
    grab(CT_REPO, rel, CT_REV, "ct2_nllb_%s" % os.path.basename(rel), CT_DIR)

# MMS Tamil TTS
MM_REPO = "willwade/mms-tts-multilingual-models-onnx"
MM_DIR = r"C:\tts\model-lab\models\tts\mms-ta"
os.makedirs(MM_DIR, exist_ok=True)
import urllib.request
d = json.load(urllib.request.urlopen("https://huggingface.co/api/models/%s" % MM_REPO))
mm_rev = d.get("sha", "main")
grab(MM_REPO, "tam/model.onnx", mm_rev, "mms_ta_model.onnx", MM_DIR)
grab(MM_REPO, "tam/tokens.txt", mm_rev, "mms_ta_tokens.txt", MM_DIR)
grab(MM_REPO, "tam/sample.wav", mm_rev, "mms_ta_sample.wav", MM_DIR)

json.dump(manifest, open(MANIFEST, "w", encoding="utf-8"), indent=2)
print("manifest updated")
