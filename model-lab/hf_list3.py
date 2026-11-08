import urllib.request, json, sys
def walk(repo):
    try:
        with urllib.request.urlopen("https://huggingface.co/api/models/%s/tree/main?recursive=true"%repo, timeout=60) as r:
            data=json.load(r)
    except Exception as e:
        print("ERR",repo,e); return
    print("=== %s ==="%repo)
    for f in data:
        print("%s\t%s\t%s"%(f["type"],f["path"],f.get("size","")))
for repo in sys.argv[1:]:
    walk(repo)
