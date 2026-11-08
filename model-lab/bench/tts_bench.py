import sherpa_onnx, time, os, json, subprocess, sys
import numpy as np
import psutil

TDIR = r"C:\tts\model-lab\models\tts"

def build_config(model, tokens, data_dir, threads=1):
    return sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=model, tokens=tokens, data_dir=data_dir),
            num_threads=threads,
            provider="cpu",
            debug=0,
        ),
        rule_fsts="", max_num_sentences=1,
    )

class Tts:
    def __init__(self, model, tokens, data_dir, threads=1):
        cfg = build_config(model, tokens, data_dir, threads)
        if not cfg.validate():
            raise ValueError("config invalid")
        self.tts = sherpa_onnx.OfflineTts(cfg)
        self.sr = self.tts.sample_rate

    def synth(self, text, speed=1.0):
        g = sherpa_onnx.GenerationConfig()
        g.sid = 0
        g.speed = speed
        g.silence_scale = 0.2
        audio = self.tts.generate(text, g)
        samples = np.asarray(audio.samples, dtype=np.float32)
        return samples, audio.sample_rate if hasattr(audio, "sample_rate") else self.sr

def rss_mb():
    return psutil.Process().memory_info().rss / (1024 * 1024)

if __name__ == "__main__":
    # smoke test EN + HI
    for name, model, tokens, td in [
        ("en-lessac-medium", TDIR + r"\vits-piper-en_US-lessac-medium\en_US-lessac-medium.onnx",
         TDIR + r"\vits-piper-en_US-lessac-medium\tokens.txt", TDIR + r"\vits-piper-en_US-lessac-medium\espeak-ng-data"),
        ("hi-priyamvada-medium", TDIR + r"\vits-piper-hi_IN-priyamvada-medium\hi_IN-priyamvada-medium.onnx",
         TDIR + r"\vits-piper-hi_IN-priyamvada-medium\tokens.txt", TDIR + r"\vits-piper-hi_IN-priyamvada-medium\espeak-ng-data"),
    ]:
        t = Tts(model, tokens, td)
        for text in ["Help me.", "I need help. Please come to my location."]:
            t0 = time.time()
            s, sr = t.synth(text)
            dur = time.time() - t0
            audio_dur = len(s) / sr
            print("%s  %r -> samples=%d  sr=%d  audio_dur=%.2fs  gen=%.2fs  RTF=%.3f" % (
                name, text, len(s), sr, audio_dur, dur, dur / audio_dur))
