import numpy as np, onnxruntime as ort
from tokenizers import Tokenizer
d = r"C:\tts\model-lab\models\nllb"
tok = Tokenizer.from_file(d + r"\tokenizer.json"); tok.post_processor = None
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}
def so(n=4):
    s=ort.SessionOptions(); s.intra_op_num_threads=n; s.inter_op_num_threads=n
    return s
enc=ort.InferenceSession(d+r"\onnx\encoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
dec=ort.InferenceSession(d+r"\onnx\decoder_model_merged_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
eos=tok.token_to_id("</s>")
in_names=[i.name for i in dec.get_inputs()]
past_names=[n for n in in_names if n.startswith("past_key_values.")]
def run(text,src,tgt,maxlen=30):
    ids=[tok.token_to_id(LANG[src])]+tok.encode(text).ids
    inp=np.array([ids[:256]],dtype=np.int64); attn=np.ones_like(inp)
    h=enc.run(None,{"input_ids":inp,"attention_mask":attn})[0]
    seq=[tok.token_to_id(LANG[tgt])]; out_s=[]
    for s in range(maxlen):
        feed={"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([seq],dtype=np.int64)}
        for i in range(12):
            for part in ("decoder","encoder"):
                for k in ("key","value"):
                    feed["past_key_values.%d.%s.%s"%(i,part,k)]=np.zeros((1,16,0,64),dtype=np.float32)
        feed["use_cache_branch"]=np.array([False])
        out=dec.run(None,feed)
        log=out[0][0,-1]
        t=int(np.argmax(log))
        if t==eos: break
        out_s.append(tok.decode([t],skip_special_tokens=True)); seq.append(t)
    return "".join(out_s)
for text,src,tgt in [("Hello","en","en"),("I need help. Please come to my location.","en","hi"),
                     ("मुझे मदद चाहिए।","hi","ta")]:
    try: print("[%s->%s] %r => %r"%(src,tgt,text,run(text,src,tgt)))
    except Exception as e: print("[%s->%s] ERR %r"%(src,tgt,e))
