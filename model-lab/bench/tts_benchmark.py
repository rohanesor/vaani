import sherpa_onnx, time, json, os, statistics
import numpy as np
import psutil, soundfile as sf

TDIR = r"C:\tts\model-lab\models\tts"
OUT = r"C:\tts\model-lab\bench\out"
N_WARM = 5
N_RUNS = 12

MESSAGES = {
    "en": [
        "Help me.",
        "I need help. Please come to my location.",
        "I am near the meeting point and need assistance.",
        "I need a doctor.",
        "Where is the hospital?",
        "I am in danger.",
    ],
    "hi": [
        "मुझे मदद करो।",
        "मुझे मदद चाहिए। कृपया मेरे स्थान पर आएँ।",
        "मैं मीटिंग पॉइंट के पास हूँ और मुझे सहायता चाहिए।",
        "मुझे डॉक्टर चाहिए।",
        "अस्पताल कहाँ है?",
        "मैं खतरे में हूँ।",
    ],
}

MODELS = {
    "en": (TDIR + r"\vits-piper-en_US-lessac-medium\en_US-lessac-medium.onnx",
           TDIR + r"\vits-piper-en_US-lessac-medium\tokens.txt",
           TDIR + r"\vits-piper-en_US-lessac-medium\espeak-ng-data"),
    "hi": (TDIR + r"\vits-piper-hi_IN-priyamvada-medium\hi_IN-priyamvada-medium.onnx",
           TDIR + r"\vits-piper-hi_IN-priyamvada-medium\tokens.txt",
           TDIR + r"\vits-piper-hi_IN-priyamvada-medium\espeak-ng-data"),
}

def build_cfg(model, tokens, data_dir, threads):
    return sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=model, tokens=tokens, data_dir=data_dir),
            num_threads=threads, provider="cpu", debug=0),
        max_num_sentences=1)

def rss():
    return psutil.Process().memory_info().rss / (1024 * 1024)

def synth(tts, text):
    g = sherpa_onnx.GenerationConfig(); g.sid = 0; g.speed = 1.0; g.silence_scale = 0.2
    return tts.generate(text, g)

def analyze(samples, sr):
    a = np.asarray(samples, dtype=np.float32)
    dur = len(a) / sr
    rms = float(np.sqrt(np.mean(a ** 2))) if len(a) else 0.0
    peak = float(np.max(np.abs(a))) if len(a) else 0.0
    clip = float(np.mean(np.abs(a) >= 0.999)) if len(a) else 0.0
    return dict(duration_s=round(dur, 3), n_samples=int(len(a)), sample_rate=int(sr),
                rms=round(rms, 5), peak=round(peak, 5), clip_frac=round(clip, 5))

def bench_lang(lang):
    model, tokens, data_dir = MODELS[lang]
    base_rss = rss()
    t0 = time.time()
    cfg = build_cfg(model, tokens, data_dir, threads=1)
    tts = sherpa_onnx.OfflineTts(cfg)
    load_time = time.time() - t0
    loaded_rss = rss()
    sr = tts.sample_rate
    results = {"language": lang, "load_time_s": round(load_time, 3),
               "base_rss_mb": round(base_rss, 1), "loaded_rss_mb": round(loaded_rss, 1),
               "sample_rate": int(sr), "messages": {}}
    os.makedirs(OUT, exist_ok=True)
    for text in MESSAGES[lang]:
        # cold/first synth
        t0 = time.time(); a1 = synth(tts, text); first = time.time() - t0
        s1 = np.asarray(a1.samples, dtype=np.float32)
        # warmups
        for _ in range(N_WARM):
            synth(tts, text)
        # timed runs
        lats = []
        peak = rss()
        for _ in range(N_RUNS):
            t0 = time.time(); a = synth(tts, text); lats.append(time.time() - t0)
            peak = max(peak, rss())
        s = np.asarray(a.samples, dtype=np.float32)
        med = statistics.median(lats); p95 = sorted(lats)[int(0.95 * len(lats)) - 1]
        dur = len(s) / sr
        meta = analyze(s, sr)
        fn = "%s_%s.wav" % (lang, MESSAGES[lang].index(text))
        wav = os.path.join(OUT, fn)
        sf.write(wav, s, sr)
        meta.update({"first_synth_s": round(first, 3), "median_s": round(med, 3),
                     "p95_s": round(p95, 3), "rtf": round(med / dur, 3) if dur else None,
                     "peak_rss_mb": round(peak, 1), "wav": fn,
                     "wav_bytes": int(os.path.getsize(wav)), "text": text})
        results["messages"][text] = meta
    results["peak_rss_mb"] = round(rss(), 1)
    return results

if __name__ == "__main__":
    allres = {}
    for lang in MODELS:
        allres[lang] = bench_lang(lang)
        print(json.dumps(allres[lang], ensure_ascii=False, indent=2))
    json.dump(allres, open(os.path.join(OUT, "tts_results.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("saved tts_results.json")
