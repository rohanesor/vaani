import ctypes, os, sys
import ctypes.wintypes as wt
tl = r"C:\tts\model-lab\.venv\Lib\site-packages\torch\lib"
pydir = r"C:\Python312\tools"
os.add_dll_directory(tl)
os.add_dll_directory(pydir)
# find python312.dll
for p in [os.path.join(pydir,"python312.dll")]:
    if os.path.exists(p):
        print("preload", p)
        ctypes.WinDLL(p)
for d in ["c10.dll","torch_cpu.dll","torch.dll","torch_python.dll"]:
    p=os.path.join(tl,d)
    try:
        ctypes.WinDLL(p); print("OK", d)
    except OSError as e:
        print("FAIL", d, e)
