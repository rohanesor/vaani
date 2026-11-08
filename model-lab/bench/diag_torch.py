import ctypes, os
tl = r"C:\tts\model-lab\.venv\Lib\site-packages\torch\lib"
dlls = ["c10.dll", "uv.dll", "torch_cpu.dll", "torch.dll", "torch_python.dll"]
for d in dlls:
    p = os.path.join(tl, d)
    if not os.path.exists(p):
        print("%s MISSING" % d); continue
    try:
        ctypes.WinDLL(p)
        print("%s OK" % d)
    except OSError as e:
        print("%s FAIL: %s" % (d, e))
print("python dlls:")
for d in ["vcruntime140.dll", "vcruntime140_1.dll", "msvcp140.dll", "msvcp140_1.dll", "msvcp140_2.dll", "vcomp140.dll"]:
    p = os.path.join(tl, d)
    print("%s in torch\\lib: %s" % (d, os.path.exists(p)))
