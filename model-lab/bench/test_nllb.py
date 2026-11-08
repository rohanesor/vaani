import time
from nllb_onnx import NllbOnnx

m = NllbOnnx(num_threads=4)
print("model loaded")
tests = [
    ("Help me.", "en", "hi"),
    ("I need help. Please come to my location.", "en", "hi"),
    ("मुझे मदद चाहिए। कृपया मेरे स्थान पर आएँ।", "hi", "en"),
    ("நான் உதவி வேண்டும். தயவுசெய்து என் இடத்திற்கு வாருங்கள்.", "ta", "en"),
    ("मुझे मदद चाहिए।", "hi", "ta"),
    ("நான் உதவி வேண்டும்.", "ta", "hi"),
    ("Hello", "en", "ta"),
]
for text, src, tgt in tests:
    t0 = time.time()
    try:
        out = m.translate(text, src, tgt)
        print("[%s->%s] %r => %r  (%.2fs)" % (src, tgt, text, out, time.time() - t0))
    except Exception as e:
        print("[%s->%s] %r => ERROR %r" % (src, tgt, text, e))
