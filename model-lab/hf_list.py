import urllib.request, json, sys

def list_repo(repo, path=""):
    url = "https://huggingface.co/api/models/%s/tree/main/%s?recursive=true" % (repo, path)
    try:
        with urllib.request.urlopen(url, timeout=60) as r:
            data = json.load(r)
        return data
    except Exception as e:
        print("ERR %s %s" % (repo, e))
        return []

def walk(repo):
    # top-level dirs
    top = list_repo(repo, "")
    print("=== %s top-level ===" % repo)
    for f in top:
        print(" ", f["type"], f["path"], f.get("size", ""))
    return top

if __name__ == "__main__":
    walk("rhasspy/piper-voices")
