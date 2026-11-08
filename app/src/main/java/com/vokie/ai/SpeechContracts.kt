package com.vokie.ai

/** Cloud speech boundary; existing offline engines remain independent of this optional path. */
data class SpeechAudio(val bytes: ByteArray, val sampleRateHz: Int, val channels: Int, val mimeType: String)
data class SpeechTranscript(val text: String, val language: String, val isFinal: Boolean)
data class SynthesizedSpeech(val pcm: ByteArray, val sampleRateHz: Int)

interface SpeechRecognitionProvider { suspend fun transcribe(audio: SpeechAudio, language: String): SpeechTranscript }
interface SpeechSynthesisProvider { suspend fun synthesize(text: String, language: String): SynthesizedSpeech }
interface LanguageProcessingProvider { suspend fun detectLanguage(text: String): String }
