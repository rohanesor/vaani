import numpy as np, onnxruntime as ort, sys
from tokenizers import Tokenizer
d = r"C:\tts\model-lab\models\nllb"
tok = Tokenizer.from_file(d + r"\tokenizer.json"); tok.post_processor = None
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}
def so(n=4):
    s=ort.SessionOptions(); s.intra_op_num_threads=n; s.inter_op_num_threads=n
    return s
enc=ort.InferenceSession(d+r"\onnx\encoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
dec=ort.InferenceSession(d+r"\onnx\decoder_model_quantized.onnx",providers=["CPUExecutionProvider"],sess_options=so())
text="Help me."; src="en"; tgt="hi"
src_id=tok.token_to_id(LANG[src]); tgt_id=tok.token_to_id(LANG[tgt]); eos=tok.token_to_id("</s>")
text_ids=tok.encode(text).ids
variants={
 "src+txt+eos":[src_id]+text_ids+[eos],
 "src+txt":[src_id]+text_ids,
 "txt+eos":text_ids+[eos],
 "txt":text_ids,
}
def greedy(input_ids, maxlen=12):
    attn=np.ones_like(input_ids)
    h=enc.run(None,{"input_ids":input_ids,"attention_mask":attn})[0]
    seq=[tgt_id]; outstr=""
    for s in range(maxlen):
        out=dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([seq],dtype=np.int64)})
        token=int(np.argmax(out[0][0,-1]))
        if token==eos: break
        outstr+=tok.decode([token],skip_special_tokens=True)
        seq.append(token)
    return outstr
for name,ids in variants.items():
    try:
        res=greedy(np.array([ids],dtype=np.int64))
        print("%-12s -> %r"%(name,res))
    except Exception as e:
        print("%-12s -> ERR %r"%(name,e))
