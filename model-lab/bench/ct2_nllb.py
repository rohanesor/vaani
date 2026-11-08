import ctranslate2, json, time
from tokenizers import Tokenizer

CT_DIR = r"C:\tts\model-lab\models\ct2\nllb600m"
TOK = r"C:\tts\model-lab\models\nllb\tokenizer.json"
LANG = {"en": "eng_Latn", "hi": "hin_Deva", "ta": "tam_Taml"}

tok = Tokenizer.from_file(TOK); tok.post_processor = None
tr = ctranslate2.Translator(CT_DIR, device="cpu")

def translate(text, src, tgt, beam=4):
    subs = [tok.id_to_token(i) for i in tok.encode(text).ids]
    source = [LANG[src]] + subs + ["</s>"]
    res = tr.translate_batch([source], target_prefix=[[LANG[tgt]]], beam_size=beam,
                             max_decoding_length=256, repetition_penalty=1.0)
    toks = res[0].hypotheses[0]
    if toks and toks[0] == LANG[tgt]:
        toks = toks[1:]
    ids = []
    for t in toks:
        i = tok.token_to_id(t)
        if i is not None:
            ids.append(i)
    return tok.decode(ids, skip_special_tokens=True)

if __name__ == "__main__":
    tests = [("Help me.", "en", "hi"),
             ("I need help. Please come to my location.", "en", "hi"),
             ("Hello", "en", "ta"),
             ("मुझे मदद चाहिए।", "hi", "ta"),
             ("நான் உதவி வேண்டும்।", "ta", "hi"),
             ("Where is the hospital?", "en", "ta"),
             ("मैं खतरे में हूँ।", "hi", "en")]
    for text, s, t in tests:
        t0 = time.time()
        try:
            out = translate(text, s, t)
            print("[%s->%s] %r => %r  (%.2fs)" % (s, t, text, out, time.time() - t0))
        except Exception as e:
            print("[%s->%s] %r => ERROR %r" % (s, t, text, e))
