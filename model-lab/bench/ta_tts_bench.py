import sherpa_onnx, time, os, statistics, json
import numpy as np, soundfile as sf, psutil

D = r"C:\tts\model-lab\models\tts\mms-ta\tam"
OUT = r"C:\tts\model-lab\bench\out"
os.makedirs(OUT, exist_ok=True)
N_RUNS = 6

def rss(): return psutil.Process().memory_info().rss/(1024*1024)
base = rss()
cfg = sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
    vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=os.path.join(D,"model.onnx"),tokens=os.path.join(D,"tokens.txt")),
    num_threads=1,provider="cpu",debug=0),max_num_sentences=1)
t0=time.time(); tts=sherpa_onnx.OfflineTts(cfg); load=time.time()-t0
loaded=rss(); sr=tts.sample_rate

phrases=["எனக்கு உதவி வேண்டும்.","நான் இங்கே இருக்கிறேன்.","நீங்கள் எங்கே இருக்கிறீர்கள்?",
         "அவசர உதவி தேவை.","தயவுசெய்து என்னைத் தொடர்பு கொள்ளுங்கள்."]
res={"load_time_s":round(load,3),"base_rss_mb":round(base,1),"loaded_rss_mb":round(loaded,1),"sample_rate":int(sr),"msgs":{}}
peak=loaded
for i,p in enumerate(phrases):
    # first
    g=sherpa_onnx.GenerationConfig(); g.sid=0; g.speed=1.0; g.silence_scale=0.2
    t0=time.time(); a=tts.generate(p,g); fst=time.time()-t0
    for _ in range(5): tts.generate(p,g)
    lats=[]; 
    for _ in range(N_RUNS):
        t0=time.time(); a=tts.generate(p,g); lats.append(time.time()-t0); peak=max(peak,rss())
    s=np.asarray(a.samples,dtype=np.float32); ad=len(s)/sr
    med=statistics.median(lats); p95=sorted(lats)[int(0.95*len(lats))-1]
    peakA=float(np.max(np.abs(s))); clip=float(np.mean(np.abs(s)>=0.999))
    fn="ta_mms_%d.wav"%i; sf.write(os.path.join(OUT,fn),s,sr)
    res["msgs"][p]={"first_syn_s":round(fst,3),"median_s":round(med,3),"p95_s":round(p95,3),
        "rtf":round(med/ad,3) if ad else None,"duration_s":round(ad,3),"peak":round(peakA,3),
        "clip_frac":round(clip,6),"peak_rss_mb":round(peak,1),"wav":fn,"wav_bytes":int(os.path.getsize(os.path.join(OUT,fn)))}
    print("%s\tdur=%.2f  med=%.3f  p95=%.3f  RTF=%.3f  peak=%.3f clip=%.5f rss=%.0fMB"%(
        p,ad,med,p95,med/ad if ad else 0,peakA,clip,peak))
res["peak_rss_mb"]=round(peak,1)
json.dump(res, open(os.path.join(OUT,"ta_tts_results.json"),"w",encoding="utf-8"),ensure_ascii=False,indent=2)
print("peak rss %.0f MB, model size %.1f MB"%(peak, os.path.getsize(os.path.join(D,"model.onnx"))/1e6))
