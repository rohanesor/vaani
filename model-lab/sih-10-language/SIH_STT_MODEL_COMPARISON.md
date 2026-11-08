# STT Comparison

| Family | 10-language claim | Android ARM64 | Streaming | License gate | Decision |
|---|---|---|---|---|---|
| Current Whisper tiny Q5_1 + whisper.cpp | upstream multilingual codes; Vokie EN/HI/TA only | proven Vokie JNI | Vokie final utterance only | current artifact recorded; per-model record required | retain baseline |
| sherpa-onnx ASR | model dependent | runtime supports Android | model dependent | runtime/license separate from weights | candidate only |
| Vosk/Kaldi | model dependent | Android route available | designed for streaming | model rights separate | candidate only |
| MMS ASR | broad research coverage | no approved Vokie route | model dependent | exact weights/runtime unknown | insufficient evidence |

Accuracy evidence must be exact WER/CER + dataset + model revision. No candidate is selected merely for language coverage.
