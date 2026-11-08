import sys, time
sys.path.insert(0, r"C:\tts\model-lab\bench")
from nllb_onnx import NllbOnnx
m = NllbOnnx(num_threads=4)
for text in ["Hello", "Help me.", "I need help. Please come to my location.", "Where are you?"]:
    t0 = time.time()
    try:
        out = m.translate(text, "en", "en", max_new=40)
        print("en->en  %r  =>  %r   (%.2fs)" % (text, out, time.time() - t0))
    except Exception as e:
        print("ERR", text, e)
