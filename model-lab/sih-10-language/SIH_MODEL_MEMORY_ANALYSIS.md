# Model Memory / Coexistence Analysis

Known model flash is not APK size or RAM: Whisper tiny Q5_1 is 32 MiB; CT2 NLLB model.bin is 619,704,329 bytes. Existing Whisper code declares approximate RAM of 273 MiB. TTS final figures are unavailable until exact approved voices exist.

Worst case is STT + CT2 + one TTS voice + Compose + Room + transport. Do not preload all ten TTS voices. Low-end strategy: lazy-load only active STT and selected TTS, serialize heavy inference, release idle contexts under memory pressure, and measure PSS/thermal. Mid-range may retain selected contexts only after device evidence. Native runtime/APK/storage/PSS must be separately measured.
