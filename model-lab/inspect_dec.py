import onnxruntime as ort
p = r"C:\tts\model-lab\models\nllb\decoder_model_quantized.onnx"
sess = ort.InferenceSession(p, providers=["CPUExecutionProvider"])
print("inputs:")
for i in sess.get_inputs():
    print("  ", i.name, i.shape, i.type)
print("outputs:")
for o in sess.get_outputs():
    print("  ", o.name, o.shape, o.type)
