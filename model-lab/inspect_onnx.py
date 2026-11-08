import onnxruntime as ort, sys, json
d = r"C:\tts\model-lab\models\nllb"
for name in ["encoder_model_quantized.onnx", "decoder_with_past_model_quantized.onnx"]:
    p = d + "\\" + name
    sess = ort.InferenceSession(p, providers=["CPUExecutionProvider"])
    print("=== %s ===" % name)
    print("  inputs:")
    for i in sess.get_inputs():
        print("    %s  shape=%s  type=%s" % (i.name, i.shape, i.type))
    print("  outputs:")
    for o in sess.get_outputs():
        print("    %s  shape=%s  type=%s" % (o.name, o.shape, o.type))
