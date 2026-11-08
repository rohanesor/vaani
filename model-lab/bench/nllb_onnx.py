import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer

LANG = {"en": "eng_Latn", "hi": "hin_Deva", "ta": "tam_Taml"}

def _mk_so(n):
    so = ort.SessionOptions()
    so.intra_op_num_threads = n
    so.inter_op_num_threads = n
    so.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
    return so

def log_softmax(x):
    m = x.max()
    e = np.exp(x - m)
    s = e.sum()
    return x - m - np.log(s)

class _Beam:
    __slots__ = ("seq", "score", "dec_past", "enc_past")

class NllbOnnx:
    def __init__(self, model_dir=None, num_threads=1, max_new=256):
        if model_dir is None:
            model_dir = r"C:\tts\model-lab\models\nllb"
        self.max_new = max_new
        self.tok = Tokenizer.from_file(model_dir + r"\tokenizer.json")
        self.tok.post_processor = None
        self.eos = self.tok.token_to_id("</s>")
        self._lang = {k: self.tok.token_to_id(v) for k, v in LANG.items()}
        self._enc_path = model_dir + r"\onnx\encoder_model_quantized.onnx"
        self._dec_path = model_dir + r"\onnx\decoder_model_quantized.onnx"
        self._decp_path = model_dir + r"\onnx\decoder_with_past_model_quantized.onnx"
        self._enc_sess = self._dec_sess = self._decp_sess = None
        self.num_threads = num_threads

    def _load(self):
        self._enc_sess = ort.InferenceSession(self._enc_path, providers=["CPUExecutionProvider"],
                                              sess_options=_mk_so(self.num_threads))
        self._dec_sess = ort.InferenceSession(self._dec_path, providers=["CPUExecutionProvider"],
                                              sess_options=_mk_so(self.num_threads))
        self._decp_sess = ort.InferenceSession(self._decp_path, providers=["CPUExecutionProvider"],
                                               sess_options=_mk_so(self.num_threads))

    def _present_map(self, sess):
        out = {}
        for o in sess.get_outputs():
            if o.name.startswith("present."):
                p = o.name.split(".")
                out[o.name] = "past_key_values.%s.%s.%s" % (p[1], p[2], p[3])
        return out

    def _encode(self, text, src):
        ids = [self._lang[src]] + self.tok.encode(text).ids
        input_ids = np.array([ids[:256]], dtype=np.int64)
        attn = np.ones_like(input_ids)
        return input_ids, attn

    def translate(self, text, src, tgt, rep_penalty=1.3, no_repeat_ngram=2, max_new=None):
        if self._enc_sess is None:
            self._load()
        if max_new is None:
            max_new = self.max_new
        input_ids, attn = self._encode(text, src)
        hiddens = self._enc_sess.run(None, {"input_ids": input_ids, "attention_mask": attn})[0]
        tgt_id = self._lang[tgt]
        m1 = self._present_map(self._dec_sess)
        m2 = self._present_map(self._decp_sess)

        # initial decoder step (no past) -> encoder presents + first decoder present
        out = self._dec_sess.run(None, {
            "encoder_hidden_states": hiddens, "encoder_attention_mask": attn,
            "input_ids": np.array([[tgt_id]], dtype=np.int64),
        })
        pn = [o.name for o in self._dec_sess.get_outputs() if o.name.startswith("present.")]
        present = {m1[n]: out[i + 1] for i, n in enumerate(pn)}
        enc_past = {k: v for k, v in present.items() if ".encoder." in k}
        dec_past = {k: v for k, v in present.items() if ".decoder." in k}

        ngrams = set()
        gen = [tgt_id]
        seen_tokens = {tgt_id}
        for _ in range(max_new):
            last = gen[-1]
            feed = {"encoder_attention_mask": attn, "input_ids": np.array([[last]], dtype=np.int64)}
            for inp in self._decp_sess.get_inputs():
                if inp.name.startswith("past_key_values."):
                    if ".encoder." in inp.name:
                        feed[inp.name] = enc_past[inp.name]
                    else:
                        feed[inp.name] = dec_past[inp.name]
            out = self._decp_sess.run(None, feed)
            pn2 = [o.name for o in self._decp_sess.get_outputs() if o.name.startswith("present.")]
            np_ = {m2[n]: out[i + 1] for i, n in enumerate(pn2)}
            logits = out[0][0, 0].astype(np.float64)
            if rep_penalty > 1.0:
                for s in seen_tokens:
                    if logits[s] > 0:
                        logits[s] /= rep_penalty
                    else:
                        logits[s] *= rep_penalty
            if no_repeat_ngram > 1 and len(gen) >= no_repeat_ngram:
                context = tuple(gen[-(no_repeat_ngram - 1):])
                for ngram in ngrams:
                    if ngram[:-1] == context:
                        logits[ngram[-1]] = -float("inf")
            token = int(np.argmax(logits))
            if token == self.eos:
                break
            gen.append(token)
            seen_tokens.add(token)
            if no_repeat_ngram > 1 and len(gen) >= no_repeat_ngram:
                ngrams.add(tuple(gen[-no_repeat_ngram:]))
            for key in np_:
                if key.endswith((".decoder.key", ".decoder.value")):
                    dec_past[key] = np_[key]
        return self.tok.decode(gen[1:], skip_special_tokens=True).strip()
