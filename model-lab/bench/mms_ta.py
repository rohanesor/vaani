import sherpa_onnx, time, numpy as np, soundfile as sf, os
import psutil

D = r"C:\tts\model-lab\models\tts\mms-ta\tam"
OUT = r"C:\tts\model-lab\bench\out"
os.makedirs(OUT, exist_ok=True)

def rss(): return psutil.Process().memory_info().rss / (1024*1024)
base = rss()
cfg = sherpa_onnx.OfflineTtsConfig(
    model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(
            model=os.path.join(D, "model.onnx"),
            tokens=os.path.join(D, "tokens.txt"),
        ),
        num_threads=1, provider="cpu", debug=0),
    max_num_sentences=1)
print("config validate:", cfg.validate())
t0=time.time()
tts = sherpa_onnx.OfflineTts(cfg)
print("load time %.2fs  rss %.1fMB  sr=%d" % (time.time()-t0, rss(), tts.sample_rate))

phrases = ["எனக்கு உதவி வேண்டும்.",
           "நான் இங்கே இருக்கிறேன்.",
           "நீங்கள் எங்கே இருக்கிறீர்கள்?",
           "அவசர உதவி தேவை.",
           "தயவுசெய்து என்னைத் தொடர்பு கொள்ளுங்கள்."]
for i, ph in enumerate(phrases):
    g = sherpa_onnx.GenerationConfig(); g.sid=0; g.speed=1.0; g.silence_scale=0.2
    t0=time.time(); a = tts.generate(ph, g); dur=time.time()-t0
    s = np.asarray(a.samples, dtype=np.float32)
    ad = len(s)/a.sample_rate
    peak = float(np.max(np.abs(s))) if len(s) else 0
    clip = float(np.mean(np.abs(s)>=0.999)) if len(s) else 0
    fn="ta_mms_%d.wav"%i; sf.write(os.path.join(OUT,fn), s, a.sample_rate)
    print("%s\n  -> samples=%d dur=%.2fs gen=%.2fs RTF=%.3f peak=%.3f clip=%.5f rss=%.1fMB wav=%s"%(
        ph, len(s), ad, dur, dur/ad if ad else 0, peak, clip, rss(), fn))
print("peak rss %.1f MB" % rss())
