import numpy as np, onnxruntime as ort
from tokenizers import Tokenizer
d = r"C:\tts\model-lab\models\nllb"
tok = Tokenizer.from_file(d + r"\tokenizer.json")
tok.post_processor = None
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}
enc_sess = ort.InferenceSession(d+r"\onnx\encoder_model_quantized.onnx", providers=["CPUExecutionProvider"])
dec_sess = ort.InferenceSession(d+r"\onnx\decoder_model_quantized.onnx", providers=["CPUExecutionProvider"])
decp_sess = ort.InferenceSession(d+r"\onnx\decoder_with_past_model_quantized.onnx", providers=["CPUExecutionProvider"])

text="Help me."; src="en"; tgt="hi"
src_id=tok.token_to_id(LANG[src]); tgt_id=tok.token_to_id(LANG[tgt]); eos=tok.token_to_id("</s>")
ids=[src_id]+tok.encode(text).ids+[eos]
input_ids=np.array([ids],dtype=np.int64); attn=np.ones_like(input_ids)
print("src ids", ids, "tgt_id", tgt_id, "eos", eos)
h=enc_sess.run(None,{"input_ids":input_ids,"attention_mask":attn})[0]
print("encoder hidden shape", h.shape)
out=dec_sess.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([[tgt_id]],dtype=np.int64)})
logits=out[0]
print("logits shape", logits.shape)
topk=np.argsort(logits[0,0])[-8:][::-1]
print("top8 token ids", topk.tolist())
print("top8 decoded", [tok.decode([int(x)], skip_special_tokens=True) for x in topk])
pnames=[o.name for o in dec_sess.get_outputs() if o.name.startswith("present.")]
for n in pnames:
    if ".encoder." in n:
        i=pnames.index(n); print(n, out[i+1].shape)
        break
print("num present outputs", len(pnames))
