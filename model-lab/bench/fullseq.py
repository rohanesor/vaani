import numpy as np, onnxruntime as ort
from tokenizers import Tokenizer
d = r"C:\tts\model-lab\models\nllb"
tok = Tokenizer.from_file(d + r"\tokenizer.json"); tok.post_processor = None
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}
def so(n=4):
    s=ort.SessionOptions(); s.intra_op_num_threads=n; s.inter_op_num_threads=n
    return s
enc=ort.InferenceSession(d+r"\onnx\encoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
dec=ort.InferenceSession(d+r"\onnx\decoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
eos=tok.token_to_id("</s>")
def run(text,src,tgt,maxlen=30):
    ids=[tok.token_to_id(LANG[src])]+tok.encode(text).ids
    inp=np.array([ids[:256]],dtype=np.int64); attn=np.ones_like(inp)
    h=enc.run(None,{"input_ids":inp,"attention_mask":attn})[0]
    seq=[tok.token_to_id(LANG[tgt])]; out_s=[]
    for s in range(maxlen):
        out=dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([seq],dtype=np.int64)})
        log=out[0][0,-1]
        t=int(np.argmax(log))
        if t==eos: break
        out_s.append(tok.decode([t],skip_special_tokens=True))
        seq.append(t)
    return "".join(out_s)
for text,src,tgt in [("Hello","en","en"),("I need help. Please come to my location.","en","hi"),
                     ("मुझे मदद चाहिए।","hi","ta")]:
    print("[%s->%s] %r => %r"%(src,tgt,text,run(text,src,tgt)))
