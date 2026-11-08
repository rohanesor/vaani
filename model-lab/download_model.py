import urllib.request, hashlib, json, os, sys, time

MANIFEST = r"C:\tts\model-lab\models\MANIFEST.json"

manifest = {}
if os.path.exists(MANIFEST):
    with open(MANIFEST, "r", encoding="utf-8") as f:
        manifest = json.load(f)

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def download(url, dest, label="", expected_sha=None):
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    start = time.time()
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        print("[skip] %s already present (%d bytes)" % (label, os.path.getsize(dest)))
    else:
        print("[get ] %s from %s" % (label, url))
        req = urllib.request.Request(url, headers={"User-Agent": "model-lab/1.0"})
        with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as f:
            total = int(r.headers.get("Content-Length", 0))
            done = 0
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                f.write(chunk)
                done += len(chunk)
                if total:
                    pct = 100 * done / total
                    if int(pct) % 25 == 0 and pct - int(pct) < 0.01:
                        sys.stdout.write("\r     %d%% (%d/%d)" % (pct, done, total))
                        sys.stdout.flush()
        print("")
    digest = sha256(dest)
    entry = {
        "url": url,
        "size_bytes": os.path.getsize(dest),
        "sha256": digest,
        "downloaded_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(start))),
        "label": label,
    }
    manifest[label] = entry
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    print("     %s -> sha256=%s size=%d" % (label, digest, os.path.getsize(dest)))
    if expected_sha and digest != expected_sha:
        print("     !! SHA MISMATCH for %s" % label)
    return digest

if __name__ == "__main__":
    pairs = sys.argv[1:]
    for i in range(0, len(pairs), 2):
        url = pairs[i]
        dest = pairs[i + 1]
        label = os.path.basename(dest)
        download(url, dest, label)
