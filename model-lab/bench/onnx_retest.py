import sys, time
sys.path.insert(0, r"C:\tts\model-lab\bench")
import numpy as np
from tokenizers import Tokenizer
import onnxruntime as ort

d = r"C:\tts\model-lab\models\nllb"
tok = Tokenizer.from_file(d + r"\tokenizer.json"); tok.post_processor = None
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}
def so(n=4):
    s=ort.SessionOptions(); s.intra_op_num_threads=n; s.inter_op_num_threads=n
    return s
enc=ort.InferenceSession(d+r"\onnx\encoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
dec=ort.InferenceSession(d+r"\onnx\decoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
decp=ort.InferenceSession(d+r"\onnx\decoder_with_past_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
eos=tok.token_to_id("</s>")
def pm(s):
    m={}
    for o in s.get_outputs():
        if o.name.startswith("present."):
            p=o.name.split("."); m[o.name]="past_key_values.%s.%s.%s"%(p[1],p[2],p[3])
    return m
m1=pm(dec); m2=pm(decp)

def translate(text,src,tgt,rep=1.2,nr=2,maxlen=60):
    ids=[tok.token_to_id(LANG[src])]+tok.encode(text).ids+[eos]
    inp=np.array([ids[:256]],dtype=np.int64); attn=np.ones_like(inp)
    h=enc.run(None,{"input_ids":inp,"attention_mask":attn})[0]
    tgt_id=tok.token_to_id(LANG[tgt])
    out=dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([[tgt_id]],dtype=np.int64)})
    pn=[o.name for o in dec.get_outputs() if o.name.startswith("present.")]
    pres={m1[n]:out[i+1] for i,n in enumerate(pn)}
    encp={k:v for k,v in pres.items() if ".encoder." in k}; decp_={k:v for k,v in pres.items() if ".decoder." in k}
    gen=[tgt_id]; ngrams=set(); seen={tgt_id}
    for _ in range(maxlen):
        feed={"encoder_attention_mask":attn,"input_ids":np.array([[gen[-1]]],dtype=np.int64)}
        for i in decp.get_inputs():
            if i.name.startswith("past_key_values."):
                feed[i.name]=encp[i.name] if ".encoder." in i.name else decp_[i.name]
        out=decp.run(None,feed)
        pn2=[o.name for o in decp.get_outputs() if o.name.startswith("present.")]
        np2={m2[n]:out[i+1] for i,n in enumerate(pn2)}
        log=out[0][0,0].astype(np.float64)
        for s in seen: log[s]=log[s]/rep if log[s]>0 else log[s]*rep
        if nr>1 and len(gen)>=nr:
            ctx=tuple(gen[-(nr-1):])
            for ng in ngrams:
                if ng[:-1]==ctx: log[ng[-1]]=-1e30
        t=int(np.argmax(log))
        if t==eos: break
        gen.append(t); seen.add(t)
        if nr>1 and len(gen)>=nr: ngrams.add(tuple(gen[-nr:]))
        for k in np2:
            if k.endswith((".decoder.key",".decoder.value")): decp_[k]=np2[k]
    return tok.decode(gen[1:],skip_special_tokens=True)

for text,s,t in [("Help me.","en","hi"),("I need help.","en","hi"),("Where is the hospital?","en","ta"),
                 ("மुझे मदद चाहिए।","hi","ta")]:
    t0=time.time(); print("[%s->%s] %r => %r (%.2fs)"%(s,t,text,translate(text,s,t),time.time()-t0))
