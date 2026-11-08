import numpy as np, onnxruntime as ort, time
from tokenizers import Tokenizer
d = r"C:\tts\model-lab\models\nllb"
tok = Tokenizer.from_file(d + r"\tokenizer.json"); tok.post_processor = None
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}
def so(n=4):
    s=ort.SessionOptions(); s.intra_op_num_threads=n; s.inter_op_num_threads=n
    return s
enc=ort.InferenceSession(d+r"\onnx\encoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
dec=ort.InferenceSession(d+r"\onnx\decoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
decp=ort.InferenceSession(d+r"\onnx\decoder_with_past_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
def pm(s):
    m={}
    for o in s.get_outputs():
        if o.name.startswith("present."):
            p=o.name.split("."); m[o.name]="past_key_values.%s.%s.%s"%(p[1],p[2],p[3])
    return m
m1=pm(dec); m2=pm(decp)
eos=tok.token_to_id("</s>")
def gen(src_ids, decoder_start, maxlen=20):
    input_ids=np.array([src_ids],dtype=np.int64); attn=np.ones_like(input_ids)
    h=enc.run(None,{"input_ids":input_ids,"attention_mask":attn})[0]
    out=dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([[decoder_start]],dtype=np.int64)})
    pn=[o.name for o in dec.get_outputs() if o.name.startswith("present.")]
    pres={m1[n]:out[i+1] for i,n in enumerate(pn)}
    encp={k:v for k,v in pres.items() if ".encoder." in k}
    decp_={k:v for k,v in pres.items() if ".decoder." in k}
    seq=[decoder_start]
    for _ in range(maxlen):
        feed={"encoder_attention_mask":attn,"input_ids":np.array([[seq[-1]]],dtype=np.int64)}
        for inp in decp.get_inputs():
            if inp.name.startswith("past_key_values."):
                feed[inp.name]=encp[inp.name] if ".encoder." in inp.name else decp_[inp.name]
        out=decp.run(None,feed)
        pn2=[o.name for o in decp.get_outputs() if o.name.startswith("present.")]
        np2={m2[n]:out[i+1] for i,n in enumerate(pn2)}
        log=out[0][0,0].astype(np.float64)
        t=int(np.argmax(log))
        if t==eos: break
        seq.append(t)
        for k in np2:
            if k.endswith((".decoder.key",".decoder.value")): decp_[k]=np2[k]
    return tok.decode(seq[1:],skip_special_tokens=True)

text="Hello"; src="en"; tgt="en"
src_id=tok.token_to_id(LANG[src]); tgt_id=tok.token_to_id(LANG[tgt])
text_ids=tok.encode(text).ids
configs={
 "src+txt+eos | start=tgt": ([src_id]+text_ids+[eos], tgt_id),
 "src+txt     | start=tgt": ([src_id]+text_ids, tgt_id),
 "txt+eos     | start=tgt": (text_ids+[eos], tgt_id),
 "src+txt+eos | start=2 ": ([src_id]+text_ids+[eos], 2),
 "src+txt+eos | start=0 ": ([src_id]+text_ids+[eos], 0),
 "src+txt+eos | start=2,tgt": ([src_id]+text_ids+[eos], 2, tgt_id),
}
for name,cfg in configs.items():
    if len(cfg)==2:
        src_ids,start=cfg
        try: print("%-35s => %r"%(name, gen(src_ids,start)))
        except Exception as e: print("%-35s => ERR %r"%(name,e))
    else:
        src_ids,start,extra=cfg
        try:
            input_ids=np.array([src_ids],dtype=np.int64); attn=np.ones_like(input_ids)
            h=enc.run(None,{"input_ids":input_ids,"attention_mask":attn})[0]
            out=dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([start,extra])[None].astype(np.int64)})
            pn=[o.name for o in dec.get_outputs() if o.name.startswith("present.")]
            pres={m1[n]:out[i+1] for i,n in enumerate(pn)}
            encp={k:v for k,v in pres.items() if ".encoder." in k}; decp_={k:v for k,v in pres.items() if ".decoder." in k}
            seq=[start,extra]
            for _ in range(18):
                feed={"encoder_attention_mask":attn,"input_ids":np.array([[seq[-1]]],dtype=np.int64)}
                for inp in decp.get_inputs():
                    if inp.name.startswith("past_key_values."):
                        feed[inp.name]=encp[inp.name] if ".encoder." in inp.name else decp_[inp.name]
                o=decp.run(None,feed)
                pn2=[x.name for x in decp.get_outputs() if x.name.startswith("present.")]
                np2={m2[n]:o[i+1] for i,n in enumerate(pn2)}
                t=int(np.argmax(o[0][0,0]))
                if t==eos: break
                seq.append(t)
                for k in np2:
                    if k.endswith((".decoder.key",".decoder.value")): decp_[k]=np2[k]
            print("%-35s => %r"%(name, tok.decode(seq[2:],skip_special_tokens=True)))
        except Exception as e:
            print("%-35s => ERR %r"%(name,e))
