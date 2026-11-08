import urllib.request, json, io, zipfile, os
pkgs = ["Vcruntime140", "NtvLibs.MSVCP.msvcp140.runtime.win-x64", "NtvLibs.MSVCP.msvcp140_1.runtime.win-x64", "NtvLibs.MSVCP.msvcp140_2.runtime.win-x64", "NtvLibs.MSVCP.vcomp140.runtime.win-x64"]
outdir = r"C:\tts\model-lab\msvc_crt"
os.makedirs(outdir, exist_ok=True)
for p in pkgs:
    try:
        idx = json.load(urllib.request.urlopen("https://api.nuget.org/v3-flatcontainer/%s/index.json" % p.lower()))
        ver = idx["versions"][-1]
        url = "https://api.nuget.org/v3-flatcontainer/%s/%s/%s.%s.nupkg" % (p.lower(), ver, p.lower(), ver)
        data = urllib.request.urlopen(url).read()
        z = zipfile.ZipFile(io.BytesIO(data))
        for n in z.namelist():
            base = os.path.basename(n)
            if base.lower().endswith(".dll"):
                with open(os.path.join(outdir, base), "wb") as f:
                    f.write(z.read(n))
                print("%s <= %s" % (base, p))
    except Exception as e:
        print("%s: ERROR %s" % (p, e))
