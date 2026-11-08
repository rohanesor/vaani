import numpy as np, onnxruntime as ort
from tokenizers import Tokenizer
d = r"C:\tts\model-lab\models\nllb"
tok = Tokenizer.from_file(d + r"\tokenizer.json"); tok.post_processor = None
LANG = {"en":"eng_Latn","hi":"hin_Deva","ta":"tam_Taml"}
def so(n=4):
    s=ort.SessionOptions(); s.intra_op_num_threads=n; s.inter_op_num_threads=n
    return s
enc = ort.InferenceSession(d+r"\onnx\encoder_model_quantized.onnx", providers=["CPUExecutionProvider"], sess_options=so())
dec = ort.InferenceSession(d+r"\onnx\decoder_model_quantized.onnx", providers=["CPUExecutionProvider"], sess_options=so())
decp = ort.InferenceSession(d+r"\onnx\decoder_with_past_model_quantized.onnx", providers=["CPUExecutionProvider"], sess_options=so())
def pm(sess):
    m={}
    for o in sess.get_outputs():
        if o.name.startswith("present."):
            p=o.name.split("."); m[o.name]="past_key_values.%s.%s.%s"%(p[1],p[2],p[3])
    return m
m1=pm(dec); m2=pm(decp)
text="Help me."; src="en"; tgt="hi"
src_id=tok.token_to_id(LANG[src]); tgt_id=tok.token_to_id(LANG[tgt]); eos=tok.token_to_id("</s>")
ids=[src_id]+tok.encode(text).ids+[eos]
input_ids=np.array([ids],dtype=np.int64); attn=np.ones_like(input_ids)
h=enc.run(None,{"input_ids":input_ids,"attention_mask":attn})[0]
out=dec.run(None,{"encoder_hidden_states":h,"encoder_attention_mask":attn,"input_ids":np.array([[tgt_id]],dtype=np.int64)})
pn=[o.name for o in dec.get_outputs() if o.name.startswith("present.")]
past={m1[n]:out[i+1] for i,n in enumerate(pn)}
token=int(np.argmax(out[0][0,0]))
print("step0 top:", [tok.decode([int(x)], skip_special_tokens=True) for x in np.argsort(out[0][0,0])[-5:][::-1]])
for step in range(6):
    feed={"encoder_attention_mask":attn,"input_ids":np.array([[token]],dtype=np.int64)}
    for inp in decp.get_inputs():
        if inp.name.startswith("past_key_values."):
            feed[inp.name]=past[inp.name]
    out=decp.run(None,feed)
    logits=out[0]
    top=np.argsort(logits[0,0])[-4:][::-1]
    print("step%d feed_token=%d=%r  top:%r"%(step,token,tok.decode([token],skip_special_tokens=True),[tok.decode([int(x)],skip_special_tokens=True) for x in top]))
    pn2=[o.name for o in decp.get_outputs() if o.name.startswith("present.")]
    np_= {m2[n]:out[i+1] for i,n in enumerate(pn2)}
    token=int(np.argmax(logits[0,0]))
    if token==eos: break
    for n in np_:
        if n.endswith((".decoder.key",".decoder.value")): past[n]=np_[n]
