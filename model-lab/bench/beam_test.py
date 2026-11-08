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
eos=tok.token_to_id("</s>")
def lsm(x):
    x=x-x.max(); e=np.exp(x); return x-np.log(e.sum())
def namesmap(s):
    m={}
    for o in s.get_outputs():
        if o.name.startswith("present."):
            p=o.name.split("."); m[o.name]="past_key_values.%s.%s.%s"%(p[1],p[2],p[3])
    return m

def beam(text,src,tgt,B=4,maxlen=30):
    ids=[tok.token_to_id(LANG[src])]+tok.encode(text).ids
    input_ids=np.array([ids[:256]],dtype=np.int64); attn=np.ones_like(input_ids)
    h=enc.run(None,{"input_ids":input_ids,"attention_mask":attn})[0]
    tgt_id=tok.token_to_id(LANG[tgt])
    out=dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([[tgt_id]],dtype=np.int64)})
    m1=namesmap(dec); m2=namesmap(decp)
    enc_ns=[v for v in m1.values() if ".encoder." in v][:len(m1)//2]
    dec_ns=[v for v in m1.values() if ".decoder." in v][:len(m1)//2]
    pn=[o.name for o in dec.get_outputs() if o.name.startswith("present.")]
    pres={m1[n]:out[i+1] for i,n in enumerate(pn)}
    enc_past={a:np.broadcast_to(pres[a],(B,)+pres[a].shape[1:]).copy() for a in enc_ns}
    dec_past={a:np.broadcast_to(pres[a],(B,)+pres[a].shape[1:]).copy() for a in dec_ns}
    lp=lsm(out[0][0,0].astype(np.float64)); idx=np.argsort(lp)[-B:][::-1]
    seqs=np.stack([np.array([tgt_id,int(t)]) for t in idx]).astype(np.int64)
    scores=np.array([lp[t] for t in idx])
    for _ in range(maxlen):
        feed={"encoder_attention_mask":attn,"input_ids":seqs[:,-1][:,None].astype(np.int64)}
        for a in enc_ns: feed[a]=enc_past[a]
        for a in dec_ns: feed[a]=dec_past[a]
        out=decp.run(None,feed)
        pn2=[o.name for o in decp.get_outputs() if o.name.startswith("present.")]
        np2={m2[n]:out[i+1] for i,n in enumerate(pn2)}
        logits=out[0][:,0,:]  # (B,V)
        vcand=lsm(logits.astype(np.float64))
        cands=[]
        for b in range(B):
            top=np.argsort(vcand[b])[-B:][::-1]
            for t in top:
                cands.append((float(scores[b]+vcand[b,t]), b, int(t)))
        cands.sort(key=lambda x:x[0],reverse=True)
        newseq=[]; newscores=[]; newsrc=[]; newtoks=[]
        for score,b,t in cands:
            newseq.append(seqs[b]); newscores.append(score); newsrc.append(b); newtoks.append(t)
            if len(newseq)>=B: break
        # build new sequences (append token), new dec past from per-beam source
        seq2=np.array([np.concatenate([seqs[newsrc[i]],[newtoks[i]]]) for i in range(B)]).astype(np.int64)
        # new dec past: for candidate i, past after appending token = dec_past_next[newsrc[i]]
        # collect source beams in candidate order
        srcs=newsrc[:B]
        seqs=seq2
        scores=np.array(newscores[:B])
        dec_past={}
        for a in dec_ns:
            dec_past[a]=np.stack([np2[a][s] for s in srcs])
        # stopping: if all top beams ended
        if np.all(seqs[:,-1]==eos): break
    best=int(np.argmax(scores/(np.array([len(s) for s in seqs])**0.7)))
    return tok.decode(seqs[best][1:],skip_special_tokens=True)

for text,src,tgt in [("Help me.","en","hi"),("I need help. Please come to my location.","en","hi"),
                     ("Hello","en","ta"),("Where is the hospital?","en","ta"),
                     ("मुझे मदद चाहिए।","hi","ta"),("நான் உதவி வேண்டும்।","ta","hi")]:
    t0=time.time()
    try: print("[%s->%s] %r => %r (%.1fs)"%(src,tgt,text,beam(text,src,tgt),time.time()-t0))
    except Exception as e: print("[%s->%s] ERR %r"%(src,tgt,e))
