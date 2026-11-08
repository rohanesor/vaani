import sys, time
sys.path.insert(0, r"C:\tts\model-lab\bench")
from nllb_onnx import NllbOnnx
m = NllbOnnx(num_threads=4)
tests=[("I need help. Please come to my location.","en","hi"),
       ("Help me.","en","ta"),
       ("मुझे मदद चाहिए।","hi","ta"),
       ("நான் உதவி வேண்டும்।","ta","hi"),
       ("Where is the hospital?","en","hi")]
for rp, nr in [(1.05,3),(1.1,3),(1.0,0)]:
    print("=== rep=%s nr=%s ==="%(rp,nr))
    for text,src,tgt in tests:
        t0=time.time()
        out=m.translate(text,src,tgt,rep_penalty=rp,no_repeat_ngram=nr,max_new=48)
        print("  [%s->%s] => %r  (%.1fs)"%(src,tgt,out,time.time()-t0))
