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
text="Help me."; src="en"; tgt="hi"
src_id=tok.token_to_id(LANG[src]); tgt_id=tok.token_to_id(LANG[tgt]); eos=tok.token_to_id("</s>")
ids=[src_id]+tok.encode(text).ids+[eos]
input_ids=np.array([ids],dtype=np.int64); attn=np.ones_like(input_ids)
h=enc.run(None,{"input_ids":input_ids,"attention_mask":attn})[0]
seq=[tgt_id]
for step in range(12):
    out=dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([seq],dtype=np.int64)})
    logits=out[0]
    token=int(np.argmax(logits[0,-1]))
    print(step, "->", repr(tok.decode([token],skip_special_tokens=True)), "seq", repr(tok.decode(seq,skip_special_tokens=True)))
    if token==eos: break
    seq.append(token)
print("RESULT", repr(tok.decode(seq,skip_special_tokens=True)))
