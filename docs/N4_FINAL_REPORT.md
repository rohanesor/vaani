# N4 — Noise Robustness

## 1. Existing audio/STT architecture
**PASS — audited**

Production path observed in code:

`AudioRecord(VOICE_RECOGNITION) → MicrophoneAudioRecorder → EnergyVadEngine → SenseVoiceSttEngine → ContinuousTurnManager → sentence pipeline`.

- Capture: Android `AudioRecord`, `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- Format: mono PCM16, 16,000 Hz (`WHISPER_SAMPLE_RATE`), converted to normalized float PCM only after capture.
- Frame: 1,600 samples / 100 ms.
- Buffer: `max(getMinBufferSize(), 6,400 bytes)`.
- Ceiling: 30 s / 480,000 samples; minimum PTT capture: 300 ms.
- App-side gain/normalization: none during capture; `VOICE_RECOGNITION` platform source may apply device/OEM processing, which is not controlled or characterized here.
- VAD: RMS energy, first 200 ms ambient calibration; threshold `max(0.015, ambientRms × 2.2, 0.025)`; speech onset 200 ms; endpoint 750 ms silence.
- SenseVoice: offline sherpa-onnx recognizer, float PCM at 16 kHz, greedy decoding, four threads, one inference mutex, max 30 s input.
- Current preprocessing before VAD: none.

## 2. Current baseline behavior
**PARTIAL**

The baseline is the unmodified production capture path above. Unit-level signal/VAD behavior is validated. A real SenseVoice clean/noisy transcript baseline was not collected because the available physical device was thermally constrained and no recorded speech/noise corpus with ground-truth transcription was available.

## 3. Test corpus
**PASS — engineering validation only**

N4 deterministic signal corpus is generated offline in `AudioRobustnessTest`: clean sine-like speech proxy, DC-biased silence, low-amplitude deterministic background noise, speech+noise, malformed float data, and repeatability inputs.

Phrase-level intended corpus for later device testing: `Help me.`, `I need water.`, `I am inside the building.`, `Please send help.`, `I cannot find the exit.`, `Mujhe help चाहिए.`, `எனக்கு help வேண்டும்.`

This is **engineering validation**, not a statistically representative WER/CER benchmark.

## 4–6. Noise/SNR methodology
**PARTIAL**

No environmental recordings or field SNR measurements were fabricated. Synthetic test noise is deterministic amplitude mixing only; it is not labelled as crowd, wind, engine, machinery, music, or real dB SNR. Future Android validation must use recorded/controlled samples before reporting SNR, WER, CER, or field-equivalent conditions.

## 7. Baseline results
**PASS — deterministic signal/VAD checks**

- Silence remains non-speech.
- 200 ms ambient calibration followed by 200 ms low-background speech proxy reaches `SPEECH_DETECTED`.
- Noise-only proxy does not create a finalized utterance.

No SenseVoice transcript accuracy or latency comparison is claimed.

## 8–10. Candidate preprocessing and accuracy results
**PARTIAL — research candidate retained, not promoted**

Added `LightweightAudioPreprocessor` with modes:
- `BYPASS`: sample-exact baseline.
- `DC_BLOCKER`: stateful bounded DC blocker (`pole=0.995`) with non-finite sanitization and clipping.

Measured deterministic properties:
- DC-biased silence settles below absolute tail mean 0.002.
- Usable speech-proxy energy remains after settling.
- Output is finite, bounded, and repeatable.

This candidate is **not enabled in production**. It is not a crowd/wind/engine denoiser, has no real SenseVoice WER/CER benefit evidence, and therefore does not satisfy promotion criteria.

## 11–15. Latency, memory, CPU, thermal
**PARTIAL**

- Preprocessor is O(n), one `FloatArray` output, constant state; no Android CPU/memory profile was collected.
- No candidate-vs-baseline SenseVoice latency/RTF comparison was collected.
- Prior device observation: RMX3782 SKIN approximately 44.5°C, thermal status 3. Heavy/repeated microphone/STT experiments were not run under the thermal gate.

## 16. VAD interaction
**PASS — synthetic deterministic checks**

The DC blocker was tested independently with VAD-compatible PCM proxies. Existing VAD thresholds were not changed because no evidence showed they were the root cause. Production remains bypassed, so N4 cannot alter onset/endpoints today.

## 17–18. Hindi+English and Tamil+English
**PASS — non-regression design/test**

The candidate accepts PCM only and has no language/token branch. Its output is deterministic and identical for identical Hindi+English and Tamil+English proxy audio. N3 transcript/code-switch tests remain in the complete green suite. No mixed-speech ASR accuracy claim is made.

## 19. Android validation
**PARTIAL**

APK builds. No new heavy physical benchmark was run because thermal status was elevated. No second phone is required for microphone→SenseVoice N4 validation, but valid device conditions and controlled speech/noise playback are still required.

## 20. Frontend changes
**PASS — no change required**

The research candidate is not promoted, so no user-facing noise controls, diagnostics, dashboard, waveform, or technical UI was added. Existing STT failure behavior remains the user-facing fallback.

## 21. Tests added
**PASS**

`AudioRobustnessTest` adds eight deterministic tests: clean bypass, DC bias, speech energy preservation, malformed input, repeatability, low-background VAD speech, noise-only VAD, and language-agnostic N3 path behavior.

## 22. Full test result
**PASS**

`./gradlew :app:testDebugUnitTest --no-daemon` → **403 passed, 0 failed**.

## 23. Build result
**PASS**

`./gradlew :app:assembleDebug --no-daemon` succeeded.

APK SHA-256: `1e06da2053f2f05fa0e760ee24fe5515d8ed26b1c5530ac4f033a2d10314a853`

## 24. Production promotion decision
**NOT PASS — intentionally not promoted**

Baseline `VOICE_RECOGNITION → VAD → SenseVoice` is retained. The DC-blocking candidate remains research-only until controlled recorded audio and Android SenseVoice baseline-vs-candidate evidence shows noisy-condition benefit without clean-speech/VAD/latency regression.

## 25. Known limitations
- Synthetic signal tests are not real speech, field noise, WER/CER, or semantic-correctness evidence.
- Android `VOICE_RECOGNITION` behavior differs by OEM/device and may already apply unknown preprocessing.
- No environmental recordings, controlled SNR, RAM/CPU profile, or SenseVoice candidate benchmark was collected.
- No claim of general noise robustness is made.

## 26. Files changed
- `app/src/main/java/com/vokie/stt/AudioPreprocessor.kt`
- `app/src/test/java/com/vokie/stt/AudioRobustnessTest.kt`
- `docs/N4_FINAL_REPORT.md`

## 27. Git commit hash
**PASS** — implementation commit `1c37352`
