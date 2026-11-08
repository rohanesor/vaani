package com.vokie.stt

import android.content.Context

/** Reads the automatic, APK-bundled Whisper extraction. It has no import or network path. */
class SttModelStore(private val context: Context, private val model: SttModel) {
    fun isInstalled(): Boolean = model.localFile(context).let { it.isFile && it.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES }

    private companion object {
        const val MIN_MODEL_BYTES = 10L * 1024 * 1024
        const val MAX_MODEL_BYTES = 300L * 1024 * 1024
    }
}
