package com.vokie.translation

/** Narrow JNI boundary; CT2 tokenization and EOS construction remain native. */
class Ctranslate2Native {
    init { System.loadLibrary("vokie_ct2") }
    external fun nativeLoadModel(modelDirectory: String): Long
    external fun nativeTranslate(handle: Long, sourceLanguage: String, targetLanguage: String, text: String): String
    external fun nativeUnloadModel(handle: Long)
}
