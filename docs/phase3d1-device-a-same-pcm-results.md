# Phase 3D.1 — Device A same-PCM Whisper benchmark

Device A: realme RMX3782 / narzo 60x 5G, Android 15/API 35, arm64-v8a, MediaTek MT6835, 8 online CPUs.

All comparison runs below replayed one in-memory 16 kHz PCM capture of the spoken English phrase **“How are you?”**: 24,320 samples / **1,520 ms**. Thread count was 4 and the model was Whisper tiny multilingual Q5_1.

| Configuration | Runs (ms) | Median | Median RTF | Language / transcript | Decision |
|---|---:|---:|---:|---|---|
| Fixed 1500, AUTO, timestamps on | 6634, 6462, 6074 | 6462 | 4.25 | English / “How are you?” all runs | retain production baseline |
| Dynamic 76, AUTO, timestamps on | 46471, 44950, 44383 | 44950 | 29.57 | repetition/hallucination; one visible output was “How are you? How are you? How are you how are your mountains and mountains by the Almighty and NoahGOGO” | reject |
| Fixed 1500, AUTO, timestamps off | 6593, 6380, 6653 | 6593 | 4.34 | English / “How are you?” all runs | reject; slower by 2.0% |
| Fixed 1500, explicit EN, timestamps on | 3079, 3102, 3119 | 3102 | 2.04 | explicit English / “How are you?” all runs | explicit mode remains available; do not replace AUTO default |

Dynamic context used `audio_ctx=76` (`ceil(24320 / 320)`). It caused severe transcription degradation and slower processing, so no dynamic combined candidate was tested.

## AUTO cost

The same PCM shows a 3,360 ms median difference between AUTO (6462 ms) and explicit EN (3102 ms), a 52.0% reduction relative to AUTO. This is Device-A and this utterance only. AUTO remains the product default to preserve language detection.

## Stability

Fixed 1500 / AUTO / timestamps on was replayed 11 times consecutively:

```text
3549, 6034, 6513, 6453, 6109, 6496, 6469, 6334, 6433, 6359, 6288 ms
min 3549 ms; max 6513 ms; mean 6094 ms; median 6359 ms
```

All observed stability runs produced 12 characters, Auto-detected English, and had no application crash, ANR, or memory-related error. RAM, CPU percentage, battery, and thermal sensors were not measured. The first 3549 ms run is a fast outlier; no real-time claim follows from it.

No production inference configuration was changed. Portable ARM64 NEON, 4 threads, AUTO, timestamps enabled, and fixed 1500 context remain selected.
