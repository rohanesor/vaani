# Vokie ProGuard rules
-keep class com.vokie.data.local.** { *; }
# JNI names are resolved by whisper.cpp's exported Java_com_vokie_stt_* symbols.
-keep class com.vokie.stt.WhisperNative { *; }
# sherpa-onnx exposes native-backed model classes from its Android AAR.
-keep class com.k2fsa.** { *; }
