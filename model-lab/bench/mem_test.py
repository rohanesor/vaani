import sherpa_onnx, time, json
import psutil

TDIR = r"C:\tts\model-lab\models\tts"
def rss(): return psutil.Process().memory_info().rss / (1024*1024)
def cfg(model,tokens,datadir):
    return sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=model,tokens=tokens,data_dir=datadir),
        num_threads=1,provider="cpu",debug=0),max_num_sentences=1)

EN=(TDIR+r"\vits-piper-en_US-lessac-medium\en_US-lessac-medium.onnx",
    TDIR+r"\vits-piper-en_US-lessac-medium\tokens.txt",TDIR+r"\vits-piper-en_US-lessac-medium\espeak-ng-data")
HI=(TDIR+r"\vits-piper-hi_IN-priyamvada-medium\hi_IN-priyamvada-medium.onnx",
    TDIR+r"\vits-piper-hi_IN-priyamvada-medium\tokens.txt",TDIR+r"\vits-piper-hi_IN-priyamvada-medium\espeak-ng-data")

print("baseline_rss_mb=%.1f"%rss())
m0=sherpa_onnx.OfflineTts(cfg(*EN)); print("after EN load rss_mb=%.1f"%rss())
g=sherpa_onnx.GenerationConfig(); g.sid=0; g.speed=1.0
a=m0.generate("Help me.",g); sr=m0.sample_rate
print("EN synth ok, rss_mb=%.1f"%rss())
m1=sherpa_onnx.OfflineTts(cfg(*HI)); print("after HI load rss_mb=%.1f"%rss())
b=m1.generate("मुझे मदद करो।",g); print("HI synth ok, rss_mb=%.1f"%rss())
print("combined EN+HI TTS peak rss_mb=%.1f"%rss())
