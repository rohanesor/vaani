import urllib.request, json, sys

def list_repo(repo):
    url = "https://huggingface.co/api/models/%s/tree/main?recursive=true" % repo
    try:
        with urllib.request.urlopen(url, timeout=60) as r:
            data = json.load(r)
        return data
    except Exception as e:
        print("ERR %s %s" % (repo, e))
        return []

def walk(repo, only=None):
    top = list_repo(repo)
    print("=== %s ===" % repo)
    for f in top:
        p = f["path"]
        if only and not any(p.startswith(o) for o in only):
            continue
        print("%s\t%s\t%s" % (f["type"], p, f.get("size", "")))

if __name__ == "__main__":
    repo = sys.argv[1]
    only = sys.argv[2].split(",") if len(sys.argv) > 2 else None
    walk(repo, only)
