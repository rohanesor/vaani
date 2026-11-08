import ctranslate2, time, json, os, statistics
import psutil
from tokenizers import Tokenizer

CT_DIR = r"C:\tts\model-lab\models\ct2\nllb600m"
TOK = r"C:\tts\model-lab\models\nllb\tokenizer.json"
OUT = r"C:\tts\model-lab\bench\out"
LANG = {"en": "eng_Latn", "hi": "hin_Deva", "ta": "tam_Taml"}

PHRASES = {
 "en": ["Help me.", "I need help.", "Please come to my location.", "Where are you?",
        "I need a doctor.", "Where is the hospital?", "I am in danger.", "I need water.",
        "I am at the meeting point.", "Call the police."],
 "hi": ["मुझे मदद करो।", "मुझे मदद चाहिए।", "कृपया मेरे स्थान पर आओ।", "आप कहाँ हैं?",
        "मुझे डॉक्टर चाहिए।", "अस्पताल कहाँ है?", "मैं खतरे में हूँ।", "मुझे पानी चाहिए।",
        "मैं मीटिंग पॉइंट पर हूँ।", "पुलिस को बुलाओ।"],
 "ta": ["எனக்கு உதவி செய்யுங்கள்.", "எனக்கு உதவி வேண்டும்.", "தயவுசெய்து என் இடத்திற்கு வாருங்கள்.",
        "நீங்கள் எங்கே இருக்கிறீர்கள்?", "எனக்கு மருத்துவர் வேண்டும்.", "மருத்துவமனை எங்கே இருக்கிறது?",
        "நான் ஆபத்தில் இருக்கிறேன்.", "எனக்கு தண்ணீர் வேண்டும்.", "நான் சந்திப்பிடத்தில் இருக்கிறேன்.",
        "போலீசை அழையுங்கள்."],
}
DIRS = [("en","hi"),("hi","en"),("en","ta"),("ta","en"),("hi","ta"),("ta","hi")]

def rss(): return psutil.Process().memory_info().rss/(1024*1024)

tok = Tokenizer.from_file(TOK); tok.post_processor = None
base = rss()
t0=time.time()
tr = ctranslate2.Translator(CT_DIR, device="cpu")
load = time.time()-t0
loaded = rss()

def translate(text, src, tgt, beam=4):
    source = [LANG[src]] + [tok.id_to_token(i) for i in tok.encode(text).ids] + ["</s>"]
    res = tr.translate_batch([source], target_prefix=[[LANG[tgt]]], beam_size=beam,
                             max_decoding_length=256, repetition_penalty=1.0)
    toks = res[0].hypotheses[0]
    if toks and toks[0] == LANG[tgt]: toks = toks[1:]
    ids = [tok.token_to_id(t) for t in toks if tok.token_to_id(t) is not None]
    return tok.decode(ids, skip_special_tokens=True)

# warmup
for s,t in DIRS:
    translate(PHRASES[s][0], s, t)

results = {"load_time_s": round(load,3), "base_rss_mb": round(base,1),
           "loaded_rss_mb": round(loaded,1), "peak_rss_mb": round(loaded,1), "dirs": {}}
peak = loaded
for s,t in DIRS:
    lat=[]; outs=[]
    for ph in PHRASES[s]:
        t0=time.time(); o=translate(ph,s,t); lat.append(time.time()-t0); outs.append(o)
        peak=max(peak,rss())
    results["dirs"]["%s->%s"%(s,t)] = {
        "median_s": round(statistics.median(lat),3),
        "p95_s": round(sorted(lat)[int(0.95*len(lat))-1],3),
        "latencies_s":[round(x,3) for x in lat],
        "outputs":[{"src":ph,"out":o} for ph,o in zip(PHRASES[s],outs)]}
    print("[%s->%s] median=%.3f p95=%.3f peak_rss=%.0fMB"%(s,t,statistics.median(lat),sorted(lat)[int(0.95*len(lat))-1],peak))
results["peak_rss_mb"]=round(peak,1)
os.makedirs(OUT,exist_ok=True)
json.dump(results, open(os.path.join(OUT,"trans_results.json"),"w",encoding="utf-8"), ensure_ascii=False, indent=2)
print("saved trans_results.json; model size MB=", round(os.path.getsize(os.path.join(CT_DIR,"model.bin"))/1e6,1))
