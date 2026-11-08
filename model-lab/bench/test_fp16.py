import numpy as np, onnxruntime as ort, time
from tokenizers import Tokenizer
d = r"C:\tts\model-lab\models\nllb"
tok = Tokenizer.from_file(d + r"\tokenizer.json"); tok.post_processor = None
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}
def so(n=4):
    s=ort.SessionOptions(); s.intra_op_num_threads=n; s.inter_op_num_threads=n
    return s
sess_enc=ort.InferenceSession(d+r"\onnx\encoder_model_fp16.onnx",providers=["CPUExecutionProvider"],sess_options=so())
sess_dec=ort.InferenceSession(d+r"\onnx\decoder_model_fp16.onnx",providers=["CPUExecutionProvider"],sess_options=so())
sess_decp=ort.InferenceSession(d+r"\onnx\decoder_with_past_model_fp16.onnx",providers=["CPUExecutionProvider"],sess_options=so())
def pm(sess):
    m={}
    for o in sess.get_outputs():
        if o.name.startswith("present."):
            p=o.name.split("."); m[o.name]="past_key_values.%s.%s.%s"%(p[1],p[2],p[3])
    return m
m1=pm(sess_dec); m2=pm(sess_decp)

def translate(text,src,tgt,rep=1.3,nr=2,maxlen=64):
    ids=[tok.token_to_id(LANG[src])]+tok.encode(text).ids+[tok.token_to_id("</s>")]
    input_ids=np.array([ids[:256]],dtype=np.int64); attn=np.ones_like(input_ids)
    h=sess_enc.run(None,{"input_ids":input_ids,"attention_mask":attn})[0]
    tgt_id=tok.token_to_id(LANG[tgt])
    out=sess_dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([[tgt_id]],dtype=np.int64)})
    pn=[o.name for o in sess_dec.get_outputs() if o.name.startswith("present.")]
    pres={m1[n]:out[i+1] for i,n in enumerate(pn)}
    enc_past={k:v for k,v in pres.items() if ".encoder." in k}
    dec_past={k:v for k,v in pres.items() if ".decoder." in k}
    gen=[tgt_id]; ngrams=set(); seen={tgt_id}
    for _ in range(maxlen):
        feed={"encoder_attention_mask":attn,"input_ids":np.array([[gen[-1]]],dtype=np.int64)}
        for inp in sess_decp.get_inputs():
            if inp.name.startswith("past_key_values."):
                feed[inp.name]=enc_past[inp.name] if ".encoder." in inp.name else dec_past[inp.name]
        out=sess_decp.run(None,feed)
        pn2=[o.name for o in sess_decp.get_outputs() if o.name.startswith("present.")]
        np_={m2[n]:out[i+1] for i,n in enumerate(pn2)}
        logits=out[0][0,-1].astype(np.float64)
        for s in seen:
            logits[s]=logits[s]/rep if logits[s]>0 else logits[s]*rep
        if nr>1 and len(gen)>=nr:
            ctx=tuple(gen[-(nr-1):])
            for ng in ngrams:
                if ng[:-1]==ctx: logits[ng[-1]]=-1e30
        t=int(np.argmax(logits))
        if t==tok.token_to_id("</s>"): break
        gen.append(t); seen.add(t)
        if nr>1 and len(gen)>=nr: ngrams.add(tuple(gen[-nr:]))
        for k in np_:
            if k.endswith((".decoder.key",".decoder.value")): dec_past[k]=np_[k]
    return tok.decode(gen[1:],skip_special_tokens=True).strip()

tests=[("Help me.","en","hi"),
       ("I need help. Please come to my location.","en","hi"),
       ("मुझे मदद चाहिए। कृपया मेरे स्थान पर आएँ।","hi","en"),
       ("நான் உதவி வேண்டும். தயவுசெய்து என் இடத்திற்கு வாருங்கள்.","ta","en"),
       ("मुझे मदद चाहिए।","hi","ta"),
       ("நான் உதவி வேண்டும்।","ta","hi"),
       ("मैं खतरे में हूँ। मेरी मदद करो।","hi","ta")]
for text,s,t in tests:
    t0=time.time()
    try:
        print("[%s->%s] %r => %r  (%.2fs)"%(s,t,text,translate(text,s,t),time.time()-t0))
    except Exception as e:
        print("[%s->%s] ERROR %r"%(s,t,e))
