from tokenizers import Tokenizer
import json
p = r"C:\tts\model-lab\models\nllb\tokenizer.json"
t = Tokenizer.from_file(p)
vocab = t.get_vocab()
print("vocab size", len(vocab))
for key in vocab:
    for code in ("Latn", "Deva", "Taml"):
        if code in key:
            print("LANGCODE", repr(key), vocab[key])
# special tokens
tp = t.post_processor
print("post_processor:", type(tp).__name__, getattr(tp, "__dict__", None))
# how does encode of plain text work?
enc = t.encode("Help me.")
print("encode ids", enc.ids)
print("tokens", enc.tokens)
