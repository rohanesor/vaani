import ctranslate2, sherpa_onnx, time, os, json
import numpy as np, psutil
from tokenizers import Tokenizer

CT_DIR = r"C:\tts\model-lab\models\ct2\nllb600m"
TOK = r"C:\tts\model-lab\models\nllb\tokenizer.json"
HI = r"C:\tts\model-lab\models\tts\vits-piper-hi_IN-priyamvada-medium"
TA = r"C:\tts\model-lab\models\tts\mms-ta\tam"
OUT = r"C:\tts\model-lab\bench\out"
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}

def rss(): return psutil.Process().memory_info().rss/(1024*1024)
def tts_cfg(d, model_name="model.onnx"):
    return sherpa_onnx.OfflineTtsConfig(model=sherpa_onnx.OfflineTtsModelConfig(
        vits=sherpa_onnx.OfflineTtsVitsModelConfig(model=os.path.join(d,model_name),
            tokens=os.path.join(d,"tokens.txt"),
            data_dir=os.path.join(d,"espeak-ng-data") if os.path.exists(os.path.join(d,"espeak-ng-data")) else ""),
        num_threads=1,provider="cpu",debug=0),max_num_sentences=1)

tok = Tokenizer.from_file(TOK); tok.post_processor=None
print("loading translation...", flush=True)
tr = ctranslate2.Translator(CT_DIR, device="cpu")
def translate(text,src,tgt):
    source=[LANG[src]]+[tok.id_to_token(i) for i in tok.encode(text).ids]+["</s>"]
    res=tr.translate_batch([source],target_prefix=[[LANG[tgt]]],beam_size=4,max_decoding_length=256)
    toks=res[0].hypotheses[0]
    if toks and toks[0]==LANG[tgt]: toks=toks[1:]
    ids=[tok.token_to_id(t) for t in toks if tok.token_to_id(t) is not None]
    return tok.decode(ids,skip_special_tokens=True)

print("loading HI TTS...", flush=True)
hi_tts = sherpa_onnx.OfflineTts(tts_cfg(HI, "hi_IN-priyamvada-medium.onnx"))
print("loading TA TTS...", flush=True)
ta_tts = sherpa_onnx.OfflineTts(tts_cfg(TA))
print("peak combined rss %.0f MB" % rss(), flush=True)

def synth(tts,text):
    g=sherpa_onnx.GenerationConfig(); g.sid=0; g.speed=1.0; g.silence_scale=0.2
    t0=time.time(); a=tts.generate(text,g); dt=time.time()-t0
    s=np.asarray(a.samples,dtype=np.float32); return a.sample_rate,len(s)/a.sample_rate,dt

results={}
def pipe(name,src,tgt,srctext,tts):
    t0=time.time(); out=translate(srctext,src,tgt); ttrans=time.time()-t0
    sr,ad,dt=synth(tts,out)
    results[name]={"source":srctext,"translated":out,"translate_s":round(ttrans,3),
                   "tts_s":round(dt,3),"tts_audio_dur_s":round(ad,3),"combined_peak_rss_mb":round(rss(),1)}
    print("[%s] %r ->[%s->%s]-> %r  (trans %.2fs + tts %.2fs = %.2fs)"%(name,srctext,src,tgt,out,ttrans,dt,ttrans+dt),flush=True)

# Text-level end-to-end (STT=Whisper pre-existing, text given)
pipe("ta->hi->HI-TTS","ta","hi","எனக்கு உதவி வேண்டும்.",hi_tts)
pipe("hi->ta->TA-TTS","hi","ta","मुझे मदद चाहिए।",ta_tts)
pipe("en->hi->HI-TTS","en","hi","I need help. Please come to my location.",hi_tts)
pipe("en->ta->TA-TTS","en","ta","I am in danger.",ta_tts)
# Same-language bypass (no translation)
for name,(txt,tts) in {"ta->ta TA-TTS(bc)":("நான் இங்கே இருக்கிறேன்.",ta_tts),
                       "hi->hi HI-TTS(bc)":("मुझे मदद चाहिए।",hi_tts),
                       "en->en HI-TTS(note)":("Help me.",hi_tts)}.items():
    sr,ad,dt=synth(tts,txt)
    results[name]={"source":txt,"bypass":True,"tts_s":round(dt,3),"tts_audio_dur_s":round(ad,3)}
    print("[%s] (bypass TTS only) %r -> %.2fs"%(", ".join(name.split('(')[0].split(' ')[0:3]),txt,dt),flush=True)

results["combined_peak_rss_mb"]=round(rss(),1)
json.dump(results,open(os.path.join(OUT,"e2e_results.json"),"w",encoding="utf-8"),ensure_ascii=False,indent=2)
print("peak combined rss %.0f MB"%rss())
