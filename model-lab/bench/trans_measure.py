import sys, time, json
sys.path.insert(0, r"C:\tts\model-lab\bench")
import psutil
from nllb_onnx import NllbOnnx

def rss():
    return psutil.Process().memory_info().rss / (1024 * 1024)

base = rss()
t0 = time.time()
m = NllbOnnx(num_threads=4)
_ = m.translate("warmup", "en", "hi", max_new=4)  # force load
load = time.time() - t0
loaded = rss()
peak = loaded
tests = [("Help me.","en","hi"), ("I need help. Please come to my location.","en","hi"),
         ("मुझे मदद चाहिए।","hi","ta"), ("நான் உதவி வேண்டும்।","ta","hi")]
results = []
for text,src,tgt in tests:
    times=[]
    outs=[]
    for _ in range(3):
        t0=time.time()
        o=m.translate(text,src,tgt,rep_penalty=1.1,no_repeat_ngram=3,max_new=30)
        times.append(time.time()-t0); outs.append(o); peak=max(peak,rss())
    results.append({"src":src,"tgt":tgt,"text":text,
                    "latencies_s":[round(x,3) for x in times],
                    "median_s":round(sorted(times)[len(times)//2],3),
                    "output":outs[0]})
print("base_rss_mb=%.1f loaded_rss_mb=%.1f peak_rss_mb=%.1f load_s=%.2f"%(base,loaded,peak,load))
print(json.dumps(results,ensure_ascii=False,indent=2))
